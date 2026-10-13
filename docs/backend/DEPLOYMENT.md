# Backend deployment runbook

For the populated Aiven database repair, use [PRODUCTION_REPAIR.md](PRODUCTION_REPAIR.md). It supersedes the migration commands below with explicit confirmation flags, safe `.env` loading, MySQL 8.4/Java 17 backup rehearsal, and separate Hibernate schema validation. Do not bypass the new runner guards.

Nothing has been pushed, deployed, or migrated on Aiven. Follow this runbook only after reviewing the diff and authorizing a production migration with a verified backup.

## Before production

1. Restore a current Aiven backup to a separate MySQL instance. Verify the restore and record row counts. Inspect actual `SHOW CREATE TABLE` output, MySQL version and indexes, particularly compensation tables absent from the old repository dumps.
2. Run `database/migrations/preflight.sql` read-only on the restored database. Inspect every result. Check effective dates in the configured business timezone. Compare the schema to V1; do not baseline an unknown schema merely to get a green deployment.
3. Rehearse all migrations and startup with `ddl-auto=validate` against the restore. Do not alter existing admin roles, passwords or JWT secrets. V2 intentionally refuses duplicate dates, lossy decimal conversions, premature legacy changes or inconsistent chronological history. Investigate failures manually; never remove financial records to bypass them.
4. Arrange a maintenance window and stop **all old application writers** before migration. The old application uses doubles and lacks scheduling states, so do not run old and new versions simultaneously. MySQL DDL implicitly commits; a partial failure needs DBA review against the backup, not a blind rerun or automatic Flyway repair.
5. Confirm Java 17 and run tests. Docker-backed tests run automatically when a Docker daemon is available; otherwise JUnit reports skips. The optional local-MySQL suites use only port 13317 and fixed disposable schemas, not DB_URL.

```powershell
java -version
mvn test
mvn clean verify
```

## Versioned migrations

- `V1__legacy_schema.sql`: creates missing legacy tables only, without sample data or replacing populated tables.
- `V2__validate_legacy_data.java`: fails before conversion when existing financial data is ambiguous or cannot convert losslessly.
- `V3__scheduled_compensation_and_payroll.sql`: converts compensation to DECIMAL(12,2), adds employee activity and scheduling/audit fields, indexes and constraints, and creates the immutable payroll table. Existing increments become APPLIED; unknown legacy actors/activation timestamps stay null rather than fabricated.

Ordinary application startup does **not** migrate. Production `spring.flyway.enabled=false` and `spring.jpa.hibernate.ddl-auto=validate` mean an unmigrated schema fails startup. Use the packaged migration utility, which does not start Spring, a web server, demo seeding or the scheduler.

In a trusted operator shell supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` through the environment. The utility does not read `.env`. Do not embed credentials in a URL, shell history, Git, documentation or command arguments. Keep the existing configured Aiven URL and use the established TLS configuration. Evaluate CA verification separately with the Aiven CA; this change does not rotate secrets or alter the production URL.

```powershell
java '-Dloader.main=com.akash.payroll.migration.MigrationRunner' -cp target/payroll-api-1.0.0.jar org.springframework.boot.loader.launch.PropertiesLauncher info
```

Only for an **existing, reviewed nonempty database without Flyway history**, after verified backup and authorization, explicitly baseline at version **0**. Do not use the default Flyway baseline version 1: V1 must run to create potentially absent compensation tables.

```powershell
java '-Dloader.main=com.akash.payroll.migration.MigrationRunner' -cp target/payroll-api-1.0.0.jar org.springframework.boot.loader.launch.PropertiesLauncher baseline-reviewed-backup
```

Do not baseline a fresh empty database, or an installation that already has Flyway history. After the appropriate preparation:

```powershell
java '-Dloader.main=com.akash.payroll.migration.MigrationRunner' -cp target/payroll-api-1.0.0.jar org.springframework.boot.loader.launch.PropertiesLauncher migrate
java '-Dloader.main=com.akash.payroll.migration.MigrationRunner' -cp target/payroll-api-1.0.0.jar org.springframework.boot.loader.launch.PropertiesLauncher validate
```

Check migration history, row counts, decimal values and history status. The utility has no clean command. Never apply V3 alone and bypass the preflight Java migration.

## Git and Render

The repository was on `main`. Review your current branch before pushing. These commands stage only backend work and its documentation, leaving the pre-existing package files untouched:

```powershell
git status --short
git diff --check
git add -- Dockerfile pom.xml src/main/java src/main/resources/application.properties src/main/resources/db/migration src/test/java src/test/resources/application.properties database/migrations docs/backend
git diff --cached --stat
git diff --cached --name-only
git commit -m "Fix effective-dated compensation and add immutable payroll ledger"
git push origin main
```

Review the staged files for credentials and unintended files before committing. Pushing may trigger Render auto-deploy; coordinate the migration window or disable auto-deploy first. No force-push is needed. Do not include `.env`, SQL backups, target output or package files.

The existing Docker build architecture is preserved: Maven on Temurin 17 builds with `mvn -B clean package -DskipTests`; runtime is Temurin 17 with `exec java -Dserver.port=${PORT:-8080} -jar app.jar`. The added `exec` lets the JVM receive container shutdown signals. Because Docker skips tests, the pre-deployment `clean verify` above is required. Render supplies PORT. Verify existing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` and optional `JWT_EXPIRATION` are present. Pool size defaults to 5 per instance; budget Aiven connections across all instances. `PAYROLL_TIMEZONE` defaults to Asia/Kolkata.

Render's configured health path remains `/health`. Check `/ready` separately (or explicitly change the dashboard health check to `/ready` if dependency health should govern routing). The application cannot run its scheduler while Render sleeps. Startup, recurring sweeps and read reconciliation catch up; use an always-on service if timely midnight activation is required.

## Verify after deployment

Use read-only checks first. Cold starts may be slow:

```powershell
$api = 'https://employee-payroll-api-gtbc.onrender.com'
Invoke-RestMethod "$api/health"
Invoke-RestMethod "$api/ready"
```

Confirm logs show successful schema validation, no migration or scheduler errors, and the expected commit/version. Verify anonymous employee requests return 401, a USER cannot change compensation (403), and the existing frontend origin passes preflight while an unrelated origin fails. Log in through the existing `/api/auth/login`; keep the resulting token in memory, not source files or committed request collections.

Test financial writes first in staging with disposable employees and an administrator token. Verify future scheduling, unchanged current rate, history status, restart catch-up, duplicate conflicts, cancellation, pending bonuses and finalized-period rejection. Changing the machine clock or production salary just to exercise activation is inappropriate. Use fixed-clock tests for the time jump.

## Salary requests: today, past, future

The following dates are examples relative to **2026-10-11** in Asia/Kolkata; choose dates relative to the actual test day. Use **three different staging employees**, each with a current rate below 65000 and no conflicting history/finalized periods. These are alternatives, not three requests to the same employee.

```http
PUT /api/employees/{employeeId}/salary-increments
Authorization: Bearer <admin-token>
Content-Type: application/json

{"newRate":65000.00,"effectiveDate":"2026-10-11","reason":"Current review"}
```

Expected: APPLIED. Historical example:

```json
{"newRate":65000.00,"effectiveDate":"2026-10-01","reason":"Historical review for open period"}
```

Expected: APPLIED if it does not precede applied/due history or affect finalized payroll; otherwise 409 with explanation. Future example:

```json
{"newRate":65000.00,"effectiveDate":"2026-11-01","reason":"Annual performance review"}
```

Expected: SCHEDULED with unchanged current compensation and null `appliedAt`. GET the salary-history route to confirm. Full-time interprets 65000 as monthly pay; for part-time use an appropriate revised hourly rate with the same `newRate` field.

Bonus request remains:

```http
POST /api/employees/{employeeId}/bonuses
Authorization: Bearer <admin-token>
Content-Type: application/json

{"amount":5000.00,"bonusDate":"2026-11-01","reason":"Project completion bonus"}
```

Expected: PENDING, creator audit, no base-salary change and no transfer of money.

## Remaining release gates and absent features

Verify the actual populated Aiven schema, backup restore, database privileges, TLS/CA policy, Java 17 runtime startup, Render resources and production migration/deployment. These are not established merely by a successful build. The live deployment has not been verified by this task.

Not implemented: closed-period adjustment/correction accounting, statutory taxes/deductions/contributions, bonus approval/payment, banking integration, legally issued payslips, attendance import, hire/termination eligibility and full-time partial-employment proration, employee self-service ownership mapping, or a guaranteed scheduler while Render is asleep. The existing Spring Boot version is preserved; a separate dependency/security-update review is still needed.
