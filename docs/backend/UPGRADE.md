# PayrollPro backend upgrade

## Audit and scope

The baseline was branch `main`; only `package.json` and `package-lock.json` were untracked. They were left untouched. No frontend, Swing GUI, GitHub Pages, account roles, credentials, or production data was changed. The original Maven suite ran successfully: 11 tests.

The error `Effective date cannot be in the future` originated in `SalaryIncrementRequest.effectiveDate` (`@PastOrPresent`). The controller validated that DTO before calling `CompensationService`, which immediately updated the employee's current rate and inserted history. History had no lifecycle, administrator attribution, or locking. Full-time employees store `monthlySalary`; part-time employees store `hourlyRate` and undated `hoursWorked`. Those meanings are preserved.

Other findings: compensation fields and summary arithmetic used doubles; timestamps depended on database defaults; method-access failures could fall through to HTTP 500; JWT rejection logs included exception text; two CORS configurations disagreed; Hibernate automatically modified production schema; demo seeding was enabled for an empty database. `/health` only indicated that the application was running.

The backend had employee CRUD/search, JWT login/registration, a current-pay summary, salary-history creation/listing and pending-bonus creation/listing. It had **no persisted payroll period, payroll finalization, payment processing, approval workflow, tax calculation or issued payslip**. The frontend's payslip is an explicitly illustrative estimate with frontend-only percentages. It is unchanged.

The repository SQL dumps contain only legacy employee/user tables and destructive restore statements. They are not migration scripts. Actual Aiven tables were **not inspected remotely**; migration requires checking the actual schema against a restored backup. Never run either legacy dump on production as an upgrade.

## Implemented behavior

`PUT /api/employees/{employeeId}/salary-increments` and the existing GET route keep their names, methods, request fields and existing response fields. `newRate` remains monthly compensation for full-time employees and an hourly rate for part-time employees.

- Positive amounts, at most 10 integer digits and two fractional digits; excess scale is rejected, not rounded. Reasons are required and limited to 500 characters. Dates must parse as ISO dates.
- Business dates use an injected `Clock`, default `Asia/Kolkata`. Set `PAYROLL_TIMEZONE` consistently across instances and migration tooling; do not change it casually after scheduling records.
- Future changes are `SCHEDULED`, without changing the current compensation. Today/past changes are `APPLIED` if they pass historical-period and timeline checks.
- Responses add `status`, `createdBy`, `appliedAt`, `cancelledAt`, and `cancelledBy`. `previousRate` for a scheduled record is its projected predecessor; insertion/cancellation can update that projection. Applied history is immutable through the API.
- Each employee/date is unique, including cancelled entries. Retrying creation at the same date returns HTTP 409; read history to resolve an uncertain network outcome.
- Multiple future changes are allowed. Inserting an earlier scheduled change is allowed when its amount exceeds its predecessor and is lower than the next scheduled amount. The existing increase-only policy is retained; this endpoint is not a salary-reduction workflow.
- Inserting before a due/applied change is rejected rather than rewriting its audit chain. Any effective date on/before an employee's latest finalized payroll end is rejected with HTTP 409 and an adjustment-workflow explanation.
- `DELETE /api/employees/{employeeId}/salary-increments/{incrementId}` cancels only a future scheduled record; repeating cancellation is safe. Correction means cancellation plus a replacement on an unused valid date. Reusing a cancelled date and editing applied records require a future audited correction workflow.
- `PUT /api/employees/{employeeId}/deactivate` marks an employee inactive and cancels remaining future changes. Already-due changes reconcile first. Existing employee GET responses add `active`; inactive employees remain visible for audit. The live summary excludes inactive employees. Deactivation is separate from existing DELETE; foreign keys prevent deleting employees with compensation/payroll history.

Every salary mutation, deactivation, ordinary employee update/delete and payroll finalization locks the employee row. Activation changes history and current compensation in one transaction. Competing instances re-read under that lock. There is no in-memory claim state and no blind write retry. The scheduler pages employee IDs in batches of 100 and uses a separate transaction for each employee, isolating failures.

The scheduler reconciles at application readiness and every 60 seconds. Employee reads and the live summary also reconcile compensation, so a waking instance does not serve an overdue current rate. Render sleep still prevents execution while the process is stopped; no exact-midnight guarantee is made. Calculation uses effective dates even if activation was delayed. Monitor reconciliation failures and `/ready`; use an always-running deployment when activation latency matters. List reads currently incur per-employee reconciliation queries; pagination/bulk read optimization remains a scaling task.

## Bonus policy

Existing bonus POST/GET routes and fields are preserved. Creation is admin-only at controller and service layers, validates against the business clock, records the verified principal and timestamp, and creates only `PENDING`. Identical employee/date/amount/trimmed-reason requests are rejected as duplicates under the employee lock. A different reason is a distinct business record; this is not a universal idempotency-key API.

The existing enum contains `PENDING`, `PAID`, `CANCELLED`, but there were no transition endpoints. No approval, rejection or payment endpoint has been invented. Client-supplied status/actor fields cannot change the lifecycle or attribution. Legacy statuses are preserved. **All bonuses remain excluded from base pay, summary and finalized payroll**, including legacy PAID entries, to avoid claiming payment processing or double-counting recorded payments. Approved-bonus inclusion is not implemented because no approval lifecycle exists.

## Minimal payroll ledger

New admin-only endpoints:

```text
POST /api/employees/{employeeId}/payroll
GET  /api/employees/{employeeId}/payroll
```

Full-time example:

```json
{"periodStart":"2026-09-01","periodEnd":"2026-09-30"}
```

Creation finalizes exactly one **completed calendar month**. It records employee name/department, pay basis, month, gross base pay, daily rate/units calculation details, finalizer and timestamp. There is no draft state or update/delete API. A database unique constraint plus the employee lock prevents duplicates. Records are immutable snapshots; later employee name/rate changes do not change them. The POST returns 201; duplicate generation returns 409.

Monthly pay is the sum of each day's applicable monthly rate divided by calendar days in the month. Division and HALF_UP rounding to two decimals happen once at the final monthly total. The earliest noncancelled history entry's `previousRate` supplies the prior baseline. Without history, existing compensation is the baseline. Legacy chronological inconsistencies must be resolved before migration rather than guessed.

For hourly employees, the body must also contain `hours`, one `{ "date":"YYYY-MM-DD", "hours":8.00 }` entry for **every day** in that month, including explicit zero-hour days. Missing, repeated, out-of-month dates, more than two decimals, negative hours or more than 24 hours/day are rejected. Daily hours multiply that day's applicable hourly rate; the sum is rounded HALF_UP once. Undated employee `hoursWorked` remains only for the legacy live summary and employee response.

No hire date, termination date, attendance-import system, or employment eligibility history existed. A finalized record asserts that an administrator has checked eligibility and supplied the entire month's attendance. Full-time partial-employment proration is not implemented. Inactive employees may still have historical months finalized. Do not use this feature to assert statutory payroll compliance.

The existing frontend does not invoke the new ledger and does not display the added salary-history status field. Its request/response parsing remains compatible, but its historical payslip estimates remain estimates. Backend changes alone cannot turn that UI into an issued-payslip workflow.

## Security and operational changes

JWT verification, existing signing-key format, BCrypt hashes and database roles are preserved. New writes and compensation-history reads enforce ADMIN at the service boundary as well as controllers. Attribution comes from Spring Security, never the request body. Existing authenticated-user employee reads are preserved because there is no user-to-employee ownership model; adding confidential self-service access requires that model first.

The GitHub Pages origin remains `https://anandaakash2005-debug.github.io`. The conflicting wildcard MVC CORS bean was removed. Validation returns structured 400s; missing resources 404; access violations 401/403; financial conflicts 409; unsupported methods 405; oversized bodies 413; transient database failures 503. Request bodies are bounded to 64 KiB, including chunked requests. No JWT text is logged on rejection. Registration refuses passwords beyond BCrypt's 72-byte limit without changing existing hashes.

`/health` remains liveness only. New public `/ready` obtains and validates a database connection, returning only UP or DOWN (503). Production uses schema validation, disables automatic migration and demo seeding, disables SQL output and Open Session in View, bounds connection pooling/timeouts, and enables graceful shutdown. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` remain environment-provided. Existing Render DB_URL and secrets were not changed.

## Migration and deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for exact commands and checks, [TEST_RESULTS.md](TEST_RESULTS.md) for executed verification, and [CHANGED_FILES.md](CHANGED_FILES.md) for the exact file inventory.

The implementation uses Spring Data's transactional pessimistic locking support ([official locking documentation](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)). Flyway is pinned through the Spring Boot dependency BOM and includes its MySQL module ([official MySQL support documentation](https://github.com/flyway/flyway/blob/main/documentation/Reference/Database%20Driver%20Reference/MySQL.md)).
