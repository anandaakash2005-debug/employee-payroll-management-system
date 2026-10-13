# Production schema repair

No production connection, migration, deployment, credential edit, or authorization change was performed. Schema evidence is the CREATE TABLE definitions in `payroll_db_before_v3_20261011.sql`, not a fresh inspection of Aiven. This runbook supersedes the migration commands in DEPLOYMENT.md.

## Findings

V3 already adds `employee_bonuses.created_by`; the fatal Render error is consistent with migrations never having run. Keep Flyway disabled at application startup and Hibernate `ddl-auto=validate`.

Compared with the supplied backup, V3 addresses these mapped schema differences:

- `employees.active`: absent; added as non-null boolean default true.
- Full-time `monthly_salary` and part-time `hourly_rate`: DOUBLE becomes DECIMAL(12,2), after lossless-conversion checks.
- `salary_increments`: adds `status`, `created_by`, `applied_at`, `cancelled_at`, `cancelled_by`; `pay_basis` ENUM becomes VARCHAR(255).
- `employee_bonuses`: adds nullable `created_by`; `status` ENUM becomes VARCHAR(255).
- `payroll_records`: absent; V3 creates all mapped columns, foreign key, unique employee/month constraint, checks and index. No historical payroll rows are invented.

Legacy `created_at` TIMESTAMP columns are compatible with Hibernate's timestamp mapping and remain unchanged. `users.role` remains ENUM. Legacy keys/indexes/checks are retained; the new names do not collide with the backup's definitions. Tests use its exact legacy enums, timestamp types, index names and check expressions on MySQL 8.4.

Legacy increments remain APPLIED with original effective dates, rates and timestamps; unknown actors/application times remain NULL. Future-dated changes, decreases or inconsistent history, duplicate effective dates, invalid subtypes/orphans, negative hours and lossy/overflowing rates stop migration for business review. Do not invent data to bypass checks.

## Safety

The runner refuses automatic baselining. Explicit baseline requires the confirmation flag, a nonempty database, absent history and a recognized legacy schema with valid data. Baseline version **0** ensures V1–V3 all run. Empty databases migrate without baselining. Incomplete/unknown schemas and signs of partially applied V3 are rejected.

Existing history must be a successful contiguous V1–V3 prefix, optionally preceded by baseline 0. Empty/failed/gapped/unexpected history and wrong baselines are refused. Applied checksums validate; only pending migrations are allowed during preflight. Schema/data checks run again before V3 even when V2 is recorded. An advisory lock serializes the utility, but does not stop application writers. No clean or repair command is exposed.

V2's Java checksum changes from 1 to 2 with its strengthened validation. The confirmed production database has no history. Other installations with the old V2 recorded intentionally fail validation and require a separate reviewed upgrade path; do not repair checksums or edit history. V1 and V3 SQL are unchanged.

Boot's Flyway 9.22.3 warns that MySQL 8.4 exceeds its tested support range. This repair tests the actual sequence on MySQL 8.4; empirical compatibility is not vendor-certified support. A Flyway major upgrade is outside this repair.

MySQL DDL commits implicitly. On failure, stop for DBA review of schema/history and backup; do not blindly retry, rebaseline, repair history or delete records. See [MySQL implicit commits](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html) and [Flyway baseline semantics](https://documentation.red-gate.com/flyway/reference/commands/baseline).

## Ordered PowerShell commands

Run from the repository root in a fresh PowerShell session without transcription/debug logging. Never print environment variables or pass database secrets on command lines.

1. Review and build with the intended Java 17 runtime. Docker must run; verify MySQL tests execute rather than skip. This machine's newer Docker requires the API override below.

```powershell
$ErrorActionPreference = 'Stop'
git diff --check
java -version
mvn.cmd -version
mvn.cmd -B '-Dapi.version=1.44' test package
if ($LASTEXITCODE -ne 0) { throw 'Build/test failure: stop' }
```

2. Rehearse the existing backup locally before importing production credentials. The helper creates a uniquely named local MySQL 8.4 container, restores only there, runs the JAR under Java 17, and compares every original column/row in all six tables in memory. It never reads `.env` or prints record values. Database settings are overridden with isolated-container settings and restored afterward. The container is retained for review, even on failure; stop/remove only the exact printed container after review.

```powershell
docker pull mysql:8.4
if ($LASTEXITCODE -ne 0) { throw 'MySQL image unavailable' }
docker pull eclipse-temurin:17-jre
if ($LASTEXITCODE -ne 0) { throw 'Java 17 image unavailable' }
& ./scripts/Test-PayrollRestore.ps1 -Backup ./payroll_db_before_v3_20261011.sql
```

The supplied backup has a UTF-16 BOM (Windows PowerShell output). The helper creates a temporary UTF-8 copy for MySQL and removes that copy afterward; it never changes the original. Require restore, V1–V3, Hibernate validation and legacy-data comparison success. This establishes this file's restorability, not its freshness.

3. Operator-only read-only production inspection. These commands connect to Aiven and have NOT been run by the agent. The importer reads selected keys literally, never executes or modifies `.env`, and sets process-local values. It supports plain/quoted single-line values, not interpolation, multiline values or inline comments. Do not rewrite secrets to accommodate an unsupported format.

```powershell
. ./scripts/Import-PayrollEnv.ps1
& ./scripts/Invoke-PayrollMigration.ps1 info
& ./scripts/Invoke-PayrollMigration.ps1 preflight
```

`info` reports history presence/status; `preflight` checks metadata and financial data without DDL. If actual schema/data differs, stop for review. Unknown/incomplete schemas are deliberately rejected.

4. Stop ALL application/scheduler writers and prevent Render auto-deploy/restarts from launching writers. Capture a fresh consistent production backup through the existing secured Aiven/DBA procedure. Rehearse that new file while writers stay stopped. Record backup/JAR checksums and private data reconciliation. The October 11 file alone is not a current rollback point.

```powershell
# Replace this example path with the actual fresh backup. This is LOCAL rehearsal.
& ./scripts/Test-PayrollRestore.ps1 -Backup 'C:\secure-backups\payroll_fresh.sql'
Get-FileHash -Algorithm SHA256 'C:\secure-backups\payroll_fresh.sql'
Get-FileHash -Algorithm SHA256 ./target/payroll-api-1.0.0.jar
. ./scripts/Import-PayrollEnv.ps1
& ./scripts/Invoke-PayrollMigration.ps1 info
& ./scripts/Invoke-PayrollMigration.ps1 preflight
```

5. **STOP FOR OPERATOR AUTHORIZATION.** Only after reviewed diff, verified fresh backup, stopped writers and target verification, explicitly authorize production writes. Baseline below is ONLY for a nonempty database with ABSENT history. If history exists, omit baseline and investigate its status; never rebaseline.

```powershell
& ./scripts/Invoke-PayrollMigration.ps1 baseline-reviewed-backup '--confirm-reviewed-backup-and-stopped-writers'
& ./scripts/Invoke-PayrollMigration.ps1 migrate '--confirm-stopped-writers'
& ./scripts/Invoke-PayrollMigration.ps1 info
& ./scripts/Invoke-PayrollMigration.ps1 validate
& ./scripts/Invoke-PayrollMigration.ps1 validate-schema
```

Stop after any failure. `validate` checks history/checksums; `validate-schema` also invokes Hibernate against every mapped entity, without starting Spring or the scheduler. Require both and reconcile legacy records against the fresh backup before releasing writers.

6. Manually deploy the reviewed revision to Render only after migration validation. Keep Java 17, Hibernate validation, Flyway disabled and all existing secret values. Check startup logs, `/ready`, login and existing employee/bonus/increment records. Old application writers must stay stopped. Code rollback does not roll back the schema.

```powershell
# Clear process-local database values after finishing; .env is unchanged.
'DB_URL','DB_USERNAME','DB_PASSWORD','PAYROLL_TIMEZONE' | ForEach-Object {
    [Environment]::SetEnvironmentVariable($_, $null, 'Process')
}
```

Production remains gated on fresh backup, live preflight, explicit migration authorization and manual deployment. No production-startup success is claimed before those steps.

## Verification performed on 2026-10-13

- Full backend suite: 161 reported, 120 executed/passed, 41 skipped, zero failures/errors. Skips are the explicitly opt-in fixed-port local MySQL variants. The Docker MySQL 8.4 tests did execute: 44 compensation cases, four existing migration cases, and 16 production-schema/safety cases. The remaining 56 executed cases use H2.
- Final migration-focused package build: `mvn.cmd -B '-Dapi.version=1.44' '-Dtest=MigrationPreflightTests,ProductionMigrationTests' package`: 17 passed, zero failures/errors/skips; BUILD SUCCESS. This includes the final case-insensitive index collision and foreign-key guards. See local `target/production-migration-tests.log` and `target/surefire-reports/`.
- Supplied backup: restored into a new isolated MySQL 8.4 container after lossless UTF-16-to-UTF-8 text conversion. Original file unchanged. Java 17 ran explicit baseline 0, V1–V3, Flyway validation and Hibernate validation successfully. All original columns of all rows in all six tables compared equal before/after migration, including user password hashes/roles and original timestamps. No record values were printed.
- Packaged application started successfully under Java 17 against that migrated copy, with scheduling/seeding disabled. The smoke application was stopped. This does not verify Aiven connectivity or Render deployment.
- Synthetic `.env` loader checks passed: literal dollar/hash/equals/backslash characters preserved; duplicate keys refused. Real `.env` was not read or modified.
- Final diff review and `git diff --check` completed. Unrelated pre-existing untracked `package.json` and `package-lock.json` were left untouched. No commit/push/deployment was performed.

The successful local restore is retained as `payroll-restore-d27a8cf2a5ef`; the two failed initialization/encoding attempts are `payroll-restore-fb9c4088a14d` and `payroll-restore-cfcbc35b542d`. These containers were stopped after review, not deleted. They contain private backup data; do not export/share them. The smoke container is `payroll-repair-java17-smoke`, also stopped. Testcontainers manages its separate synthetic test instances.

## Exact changed files

- `src/main/java/com/akash/payroll/migration/MigrationRunner.java`
- `src/main/java/com/akash/payroll/migration/LegacySchemaGuard.java` (new)
- `src/main/java/com/akash/payroll/migration/SchemaValidator.java` (new)
- `src/main/java/db/migration/V2__validate_legacy_data.java`
- `src/test/java/com/akash/payroll/MigrationPreflightTests.java`
- `src/test/java/com/akash/payroll/MySqlMigrationTests.java`
- `src/test/java/com/akash/payroll/MySqlCompensationTests.java`
- `src/test/java/com/akash/payroll/ProductionMigrationTests.java` (new)
- `src/test/resources/migration/production-legacy.sql` (new; schema only, no real records)
- `src/test/resources/migration/.gitignore` (new; makes the schema fixture trackable)
- `scripts/Import-PayrollEnv.ps1` (new)
- `scripts/Invoke-PayrollMigration.ps1` (new)
- `scripts/Test-PayrollRestore.ps1` (new)
- `docs/backend/PRODUCTION_REPAIR.md` (new)
- `docs/backend/DEPLOYMENT.md`
- `README.md`
