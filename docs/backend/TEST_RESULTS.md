# Verification results

All execution used isolated databases. No test used Aiven, production credentials or the production database. The installed JVM was Java 26.0.2; Maven compiled with `--release 17`. The packaged application's class-file major version was checked as 61 (Java 17). The existing Docker build/runtime still use Temurin 17; a direct Java 17 runtime execution was not performed here.

## Executed

- Before changes: `mvn test -B` — **11 tests passed**.
- Expanded H2 suite and migration preflight: **56 executed tests passed** (44 compensation/payroll/API cases including parameterized cases, 11 original tests, 1 Java migration-preflight test).
- Real MySQL 8.0.43 on a newly initialized, loopback-only instance at `127.0.0.1:13317`: **44 behavioral tests and 4 migration tests passed**. The suite validates the migrated schema with Hibernate, rather than allowing Hibernate to create it.
- Combined final command: `$env:PAYROLL_LOCAL_MYSQL_TESTS='true'; mvn clean verify -B` — **BUILD SUCCESS**, 2026-10-11 02:18 IST. Maven reported 145 tests, **104 executed and passed**, 41 Docker-only skips, zero failures/errors. The executable JAR was packaged successfully.
- Packaged migration utility was run with `validate` against the temporary MySQL behavior database: **3 migrations validated**.
- The executable JAR started with production-style configuration and the temporary MySQL database, `.env` loading explicitly disabled: `/health` returned UP, `/ready` returned UP, anonymous `/api/employees` returned **401**. The smoke-test process was stopped afterward.
- Git diff whitespace check; frontend/Swing path diff checks; migration destructive-statement scan; credential-URL scan; generated Java bytecode version check.

The MySQL migration cases cover an empty schema and no-op rerun, an unversioned populated schema explicitly baselined at zero, preservation of employee compensation and existing ADMIN role/password hash, populated salary-history migration, uniqueness enforcement, and refusal of lossy legacy decimal conversion. H2 also exercises the Java preflight's duplicate/precision checks.

Behavioral coverage includes today/past/future salary dates; scheduled/current separation; clock advancement; idempotence; concurrent activation/submission/payroll finalization; multiple ordered schedules; insertion/cancellation; inactive employees; monthly/hourly separation; duplicate/precision validation; immutable closed payroll; before/after/prorated monthly pay; dated hourly attendance and rounding; pending/duplicate bonus recording; absence of approval/payment transitions; principal attribution; JWT/auth regression tests; ADMIN restrictions; CORS; malformed JSON/date/blank reasons; request-size limits; readiness; and foreign-key preservation of employee history.

## Skipped and limits

Docker was not running. The equivalent Testcontainers classes were therefore skipped: 37 behavioral test methods (parameterized methods count once when skipped before expansion) and 4 migration methods. The same behavior/migration code **was executed against local MySQL**; these skips do not mean that all MySQL testing was omitted. Container image/environment behavior itself remains unverified.

Local MySQL tests are explicitly opt-in. They use hard-coded loopback port 13317, `payroll_behavior_test` and `payroll_migration_test`, and a synthetic test-only account. They never accept `DB_URL`. The migration fixture clears only its disposable schema. Do not enable them against an existing non-disposable installation at that port. The private MySQL instance created for this task was separate from any installed system service and was shut down after verification.

Production Aiven schema/data, network/TLS, backup restoration, migration duration, Render deployment, Java 17 runtime startup, sustained load and live authenticated API writes were **not verified**. No deployment or production migration was performed. Statutory payroll compliance, banking and bonus-payment processing are not implemented.

## Failures encountered and resolved

The baseline sandbox could not access Maven's cache; rerunning with authorized cache access succeeded. The scheduler's initial nested-enum JPQL literal failed startup and was replaced with a typed parameter. A MockMvc/service-security test needed its direct service assertion before MockMvc cleared the security context. Real MySQL exposed Hibernate's native-enum expectation; explicit VARCHAR mappings and migration normalization fixed it. A packaging attempt collided with the running smoke-test JAR on Windows; that process was stopped before final clean verification. These intermediate failures are not counted as passing checks.
