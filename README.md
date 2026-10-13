# Employee Payroll Management System

A Java Programming Laboratory WCA project using a Swing desktop interface, a Spring Boot REST API, and MySQL persistence.

## Project Overview

The application manages full-time and part-time employees, calculates their salaries, and displays payroll totals. Users register and log in through the desktop GUI; authenticated requests reach the backend through HTTP and JSON. Employee records and user accounts persist in MySQL.

This README documents the implementation inspected on **9 October 2026**. It supports the laboratory demonstration and setup but does **not** replace the college's separate PDF report, screenshot evidence, database backup submission, or individual contribution records. The actual laboratory notice is not included in this checkout; confirm submission details with the faculty.

**Quick navigation:** [Setup](#installation-and-setup) · [Configuration](#environment-variables-and-env-configuration) · [Backend](#how-to-run-the-backend) · [Swing](#how-to-run-the-swing-desktop-gui) · [Tests](#testing-instructions-and-verified-test-results) · [Contributions](#group-members-and-individual-contributions)

## Problem Statement

Maintaining employee details and calculating salaries manually can lead to inconsistent records and calculation errors. This project provides a persistent employee register with basic salary calculations, search, and summary output through a Java desktop application.

## Project Objectives

- Implement meaningful Java modules for authentication, employee management, and payroll summaries.
- Demonstrate OOP through an employee hierarchy and type-specific salary calculations.
- Separate the desktop UI, REST endpoints, business logic, and persistence responsibilities.
- Validate input, handle errors, and store records in a relational database.
- Provide reproducible setup instructions and evidence from automated tests.

## Main Features and Functional Modules

- **Registration and login:** unique usernames, BCrypt password hashing, and JWT issuance. Swing registration returns to the login screen.
- **Dashboard:** current username, employee counts, total payroll, average salary, and navigation buttons.
- **Employee management:** add, list, view a selected record, update, delete with confirmation, and refresh.
- **Search:** case-insensitive partial name matching in Swing and the API. The API also supports case-insensitive exact department matching and filtering by employee type; these filters have client service methods but no dedicated Swing controls.
- **Salary calculation:** full-time salary is the stored monthly salary; part-time salary is `hoursWorked * hourlyRate`.
- **Payroll summary:** total employees, full-time count, part-time count, sum of calculated salaries, and average salary (zero when there are no employees). Swing summary amounts display the rupee symbol.
- **Logout:** clears the desktop's in-memory token and username and returns to login.

These are basic calculations over current records. Tax deductions, payslip generation, salary disbursement, and payroll-period history are not implemented in the documented Java application.

## Technology Stack

- **Java:** compilation target 17 (`java.version` and compiler `release` in [pom.xml](pom.xml)); Swing/AWT for desktop controls and Java `HttpClient` for API calls.
- **Spring Boot 3.2.3:** Spring Web REST controllers and embedded server.
- **Spring Data JPA / Hibernate:** entity mapping, repositories, and transactions.
- **MySQL / Connector/J:** JDBC connectivity to local MySQL or Aiven.
- **Spring Security and JJWT 0.12.6:** BCrypt authentication, stateless JWT verification, and HS256 token signing.
- **Jakarta Bean Validation:** request constraints checked through `@Valid`.
- **Jackson:** backend JSON conversion and explicit `ObjectMapper` use in Swing client/services.
- **Lombok 1.18.48:** generated accessors and constructors; configured as an annotation processor.
- **Maven:** compiler plugin 3.11.0, Spring Boot plugin, and exec plugin 3.3.0.
- **Tests:** JUnit Jupiter, Spring Boot Test, MockMvc, Spring Security Test, TestRestTemplate, and H2.

Node.js is not required to build or run the Java application.

## System Architecture

```text
Java Swing GUI
  -> gui.services / ApiClient (HTTP + JSON, Bearer JWT)
  -> Spring Security JWT filter
  -> Spring Boot REST API / EmployeeController
  -> PayrollService
  -> EmployeeRepository / JPA / Hibernate
  -> MySQL Connector/J (JDBC)
  -> MySQL database
```

Employee requests follow this layered flow. Authentication uses `AuthController`, Spring's authentication manager, `CustomUserDetailsService`, `UserRepository`, `PasswordEncoder`, and `JwtUtil`; there is no separate backend authentication service class. Swing's `gui.services.AuthService` is an HTTP client wrapper.

`/api/auth/**` is public. Other `/api/**` requests require authentication. Registration assigns `USER`; `USER` and `ADMIN` exist in the model, but employee endpoints do not enforce different permissions for these roles. Records are shared across authenticated accounts.

`JwtUtil` signs tokens with HS256 using a Base64-decoded secret and checks signatures, usernames, and expiration. The default lifetime is 24 hours. `SessionManager` holds the token only in memory; `ApiClient` adds `Authorization: Bearer <token>` to protected requests. Logout does not revoke a token at the server. There is no refresh-token endpoint.

### REST API Routes

Paths are relative to the backend origin. All employee endpoints require a valid bearer token; request/response bodies use JSON.

| Method | Path | Implemented behavior |
|---|---|---|
| POST | `/api/auth/register` | Register; return token, username, role, message (201); duplicate username gives 409 |
| POST | `/api/auth/login` | Authenticate; return token, username, role, message (200) |
| GET | `/api/employees` | List all employees |
| GET | `/api/employees/{id}` | Get one employee; missing ID gives 404 |
| GET | `/api/employees/fulltime` | List full-time employees |
| GET | `/api/employees/parttime` | List part-time employees |
| GET | `/api/employees/department?name=Engineering` | Exact department match, ignoring case |
| GET | `/api/employees/search?name=sample` | Partial name match, ignoring case |
| GET | `/api/employees/summary` | Employee counts, total payroll, average salary |
| POST | `/api/employees/fulltime` | Create full-time employee (201) |
| POST | `/api/employees/parttime` | Create part-time employee (201) |
| PUT | `/api/employees/{id}/fulltime` | Update existing full-time employee (200) |
| PUT | `/api/employees/{id}/parttime` | Update existing part-time employee (200) |
| DELETE | `/api/employees/{id}` | Delete employee (204, no response body) |

Authentication request fields are `username` and `password`. Full-time create/update fields are `name`, `department`, and `monthlySalary`. Part-time fields are `name`, `department`, `hoursWorked` (integer), and `hourlyRate`. PUT requires all fields for that type and cannot convert an employee to the other type.

`EmployeeResponse` includes `id`, `name`, `department`, `employeeType`, calculated `salary`, and the applicable salary inputs. Summary fields are `totalEmployees`, `fullTimeCount`, `partTimeCount`, `totalPayroll`, and `averageSalary`.

## Project Directory Structure

```text
.
|-- pom.xml
|-- README.md
|-- .gitignore
|-- .env                              # local configuration; ignored
|-- database/payroll_db.sql            # optional dump; UTF-16 LE
|-- src/main/java/com/akash/payroll/
|   |-- PayrollApplication.java       # Spring Boot entry point
|   |-- DataLoader.java               # conditional employee seed
|   |-- controller/                   # AuthController, EmployeeController
|   |-- dto/                          # backend request/response objects
|   |-- exception/                    # advice and missing employee exception
|   |-- model/                        # Employee hierarchy and User
|   |-- repository/                   # EmployeeRepository, UserRepository
|   |-- security/                     # security config, JWT, user lookup
|   |-- service/                      # PayrollService
|   `-- gui/
|       |-- Main.java                 # Swing entry point
|       |-- ApiClient.java, ApiException.java, SessionManager.java
|       |-- LoginFrame.java, RegisterFrame.java, DashboardFrame.java
|       |-- EmployeeManagementFrame.java, AddEmployeeDialog.java
|       |-- EmployeeTableModel.java, SummaryPanel.java
|       |-- models/                   # desktop authentication payloads
|       `-- services/                 # AuthService, EmployeeService
|-- src/main/resources/application.properties
|-- src/test/java/com/akash/payroll/PayrollApplicationTests.java
|-- src/test/resources/application.properties
|-- target/                           # generated output; existing test reports
|-- DAILY_STARTUP_GUIDE.md             # older guide; see References
|-- PROJECT_EXPLANATION_GUIDE.md       # older guide; see References
|-- index.html                        # separate browser interface artifact
|-- package.json, package-lock.json   # JavaScript dependency artifacts
`-- frontend/                        # only node_modules in this checkout
```

No screenshot directory or PDF report was found. Root `package.json` has dependencies but no run scripts. The browser artifact sends employee requests without bearer authentication; it is not the startup path documented here.

## Java and OOP Concepts Demonstrated

| Concept | Concrete implementation |
|---|---|
| Classes and objects | `PayrollService` creates `FullTimeEmployee` and `PartTimeEmployee` objects; Swing `Main` creates a `LoginFrame`. |
| Encapsulation | `Employee` and `User` have private fields and Lombok-generated accessors. Desktop `LoginRequest` has private final fields and explicit getters. Accessors alone do not enforce valid data; validation is separate. |
| Constructors | Subclass constructors call `Employee(String name, String department)` through `super(...)`; no-argument constructors are generated for JPA. |
| Inheritance | `FullTimeEmployee` and `PartTimeEmployee` extend abstract `Employee`; GUI frames extend `JFrame`. |
| Abstraction | `Employee.calculateSalary()` defines an abstract contract. `EmployeeRepository` and `UserRepository` extend `JpaRepository`. |
| Runtime polymorphism | `PayrollService.getPayrollSummary()` uses `Employee::calculateSalary` across a `List<Employee>`; `EmployeeResponse.from()` also calls the overridden method. |
| Method overriding | Both employee subclasses override `calculateSalary()`; `EmployeeTableModel` overrides methods such as `getValueAt()`. |
| Overloading | `JwtUtil.generateToken(UserDetails)` delegates to `generateToken(Map<String, Object>, UserDetails)`. `EmployeeManagementFrame` has one- and two-argument constructors. |
| Interfaces | `CustomUserDetailsService` implements `UserDetailsService`; `DataLoader` implements `CommandLineRunner`. |
| Collections and streams | `EmployeeTableModel` uses `ArrayList<EmployeeResponse>`; exception handling uses `Map`/`HashMap`; payroll summaries use streams and `mapToDouble().sum()`. |
| Exception handling | `EmployeeNotFoundException` extends `RuntimeException`; checked `ApiException` extends `Exception`; `GlobalExceptionHandler` maps exceptions to HTTP responses. |
| Packages | `com.akash.payroll` separates model, repository, service, controller, DTO, security, exception, and GUI responsibilities. |

Swing launch uses `SwingUtilities.invokeLater`. Login, registration, list/search, deletion, and summary loading use `SwingWorker`; employee saving currently makes synchronous calls on the UI thread.

## Database Design and Configuration

### Entities and Relationships

`Employee` uses JPA `InheritanceType.JOINED` with discriminator column `employee_type`:

```text
employees (id PK, name, department, employee_type)
  |-- full_time_employees (id PK/FK -> employees.id, monthly_salary)
  `-- part_time_employees (id PK/FK -> employees.id, hours_worked, hourly_rate)

users (id PK, username UNIQUE, password, role)
```

Each employee has a parent row and a corresponding subtype row (`FULL_TIME` or `PART_TIME`). IDs use identity generation. `users` is independent: there is no user-to-employee ownership relationship or department entity. Registered passwords are stored as BCrypt hashes. Salary totals are calculated on demand; there is no payroll-history table.

### Initialization

[application.properties](src/main/resources/application.properties) uses `spring.jpa.hibernate.ddl-auto=validate`, with automatic Flyway execution disabled. Apply reviewed V1–V3 migrations explicitly before startup. For an existing populated database, follow the [production repair runbook](docs/backend/PRODUCTION_REPAIR.md), including backup restoration, explicit baseline confirmation and Hibernate validation. Do not switch to Hibernate auto-update to bypass missing columns.

`DataLoader.run()` inserts five demo employees (three full-time, two part-time) when the employee repository is empty, and skips insertion otherwise. It runs at every backend startup, so restarting after deleting all employees reintroduces demo records. It creates no login accounts. The seed total is 261000 salary units and the average is 52200.

### Optional SQL Backup

[database/payroll_db.sql](database/payroll_db.sql) contains MySQL schema statements, five employee parent rows, three full-time rows, two part-time rows, and an **empty users table**. Register an account instead of assuming a supplied login. Spring does not automatically import this file.

The dump contains `CREATE DATABASE`, `USE payroll_db`, and `DROP TABLE` statements. Restore only into a disposable local database: it targets `payroll_db` and replaces its tables. It uses the MySQL 8-specific collation `utf8mb4_0900_ai_ci`.

The supplied file is **UTF-16 LE with a BOM**. Before restoring, use an editor to save a separate **UTF-8 copy without BOM**, for example `C:/Temp/payroll_db.utf8.sql`, leaving the original unchanged. Start the MySQL client with a password prompt:

```powershell
mysql --host=127.0.0.1 --port=3306 --user=root --password
```

Then, inside that client, use the actual converted file path:

```sql
SOURCE C:/Temp/payroll_db.utf8.sql;
```

Restore was not executed during this audit. An empty database initialized by Hibernate and `DataLoader` is sufficient for the normal demonstration.

## Prerequisites

- A JDK supporting the Java 17 target; use JDK 17 as the baseline. Configure `JAVA_HOME` and `PATH` consistently.
- Maven on `PATH`; no Maven wrapper is supplied.
- A graphical desktop environment for Swing.
- Access to local MySQL or Aiven MySQL, with an existing `payroll_db` database (or one selected through `DB_URL`) and schema-management permissions.
- Internet access for uncached Maven dependencies and, if used, the hosted database.
- A MySQL client or administration tool for database creation or optional restore.

## Installation and Setup

1. Extract or clone the project and open the directory containing `pom.xml`. For this checkout:

   ```powershell
   Set-Location 'C:\Users\AKASH\Downloads\Employee-Payroll-System-main\Employee-Payroll-System-main'
   java -version
   mvn -v
   ```

2. Choose the database. For local MySQL, start the server and create the database using an authorized database account:

   ```sql
   CREATE DATABASE IF NOT EXISTS payroll_db;
   ```

   For Aiven, ensure the database exists in the service. JDBC uses port **12421**, not MySQLX port 12425. A local MySQL server is unnecessary when using Aiven.

3. Configure the backend below. The default connection targets Aiven; explicitly set `DB_URL` and `DB_USERNAME` for local MySQL.
4. Build and run tests:

   ```powershell
   mvn clean install
   ```

   This compiles, tests, packages, and installs `com.akash:payroll-api:1.0.0` into the local Maven repository. It does not deploy the application. Tests use H2, not production MySQL.

5. Start the backend, then Swing in a second terminal. Keep both running during use.

## Environment Variables and `.env` Configuration

The backend explicitly imports an optional file:

```properties
spring.config.import=optional:file:.env[.properties]
```

It reads `.env` from the process working directory as **Java properties**, not through a dotenv library or shell script. Launch from the project root; set the IDE working directory there too. Use `KEY=value`, without `export`, `$env:`, or surrounding quotes. Java-properties escaping rules apply, including backslash escaping. Operating-system environment variables override file values. Missing `.env` is allowed, but required values still need another configuration source.

| Variable | Consumer | Purpose / default |
|---|---|---|
| `PORT` | Backend | HTTP port; default `8080` |
| `DB_URL` | Backend | JDBC URL; default configured Aiven host, port `12421`, database `payroll_db`, `sslMode=REQUIRED`, `serverTimezone=UTC` |
| `DB_USERNAME` | Backend | Database user; configured default `avnadmin` |
| `DB_PASSWORD` | Backend | Required database password; no default |
| `JWT_SECRET` | Backend | Required Base64 signing key; no default; use at least 32 random decoded bytes for HS256 |
| `JWT_EXPIRATION` | Backend | Token lifetime in milliseconds; default `86400000` (24 hours) |
| `PAYROLL_API_URL` | Swing process | Backend HTTP(S) origin; default `http://localhost:8080` |

Create a local `.env` beside `pom.xml` if absent, or privately update the existing file. This is a **placeholder template embedded in the README**, not a runnable credential set or a new `.env.example` file:

```properties
# Local MySQL example: replace all REPLACE_ME values privately.
PORT=8080
DB_URL=jdbc:mysql://localhost:3306/payroll_db?serverTimezone=UTC
DB_USERNAME=REPLACE_ME_LOCAL_DATABASE_USER
DB_PASSWORD=REPLACE_ME_DATABASE_PASSWORD
JWT_SECRET=REPLACE_ME_BASE64_OF_AT_LEAST_32_RANDOM_BYTES
JWT_EXPIRATION=86400000
```

For Aiven, use the service's host and user; the URL shape is:

```text
jdbc:mysql://YOUR_AIVEN_HOST:12421/payroll_db?sslMode=REQUIRED&serverTimezone=UTC
```

Generate the signing key privately with a cryptographically secure generator or secret manager. Do not use a memorable password as the key or put credentials in command arguments, source code, screenshots, or submission files.

[.gitignore](.gitignore) contains `.env` and `.env.*`, with the exception `!.env.example`. These filenames are configured to be ignored; an ignore rule does not untrack already committed files. This checkout has no `.git` metadata, so actual tracked-file status could not be checked. The local `.env` was not displayed during this audit.

**Swing does not load `.env`.** Set `PAYROLL_API_URL` in the GUI terminal environment or use JVM property `payroll.api.url`, which takes precedence. The client accepts an HTTP(S) origin with no credentials, query, fragment, or path other than an optional trailing slash. It needs no database password or JWT signing secret.

## How to Run the Backend

In terminal 1, from the project root:

```powershell
mvn spring-boot:run
```

The Spring Boot entry point is `com.akash.payroll.PayrollApplication`. The default origin is `http://localhost:8080`, with the port overridden by `PORT`. Wait for successful startup before opening Swing. A database connection/schema error means the backend is not ready.

The Spring Boot Maven plugin is declared in `pom.xml`. This runs the API and does not open a desktop window. An unauthenticated request to `/api/employees` should return **401**, not employee JSON. No dedicated health endpoint is implemented.

## How to Run the Swing Desktop GUI

In terminal 2, from the same project root after building:

```powershell
mvn exec:java
```

The exec plugin explicitly selects `com.akash.payroll.gui.Main`, which opens `LoginFrame`. `exec:java` does not compile automatically; after source changes or `mvn clean`, use `mvn compile exec:java` or rebuild first.

For a backend on port 8081, set the GUI origin in terminal 2:

```powershell
$env:PAYROLL_API_URL = 'http://localhost:8081'
mvn exec:java
```

Alternatively, the system property takes precedence:

```powershell
mvn "-Dpayroll.api.url=http://localhost:8081" exec:java
```

Set `PORT=8081` separately in the backend configuration when using this example. For a remote backend, use its HTTPS origin. Database port 12421 is never the GUI API port. Stop the backend with `Ctrl+C`; use the GUI's Exit action to close the desktop application.

## Basic Application Usage

1. Start the configured database and backend, then open Swing.
2. Select **Register**, enter a unique username and a password of at least six characters, and submit. Return to **Login** and sign in.
3. Review the dashboard metrics. An empty database receives five demo employees at backend startup.
4. Open **Employee Management** and **Add**. Enter name, department, and type. For full-time employees, enter **Monthly Salary**; **Additional Value** is unused. For part-time employees, enter integer **Hours Worked** and put the hourly rate in **Additional Value**.
5. For example, monthly salary `50000` produces salary `50000`; hours `40` and rate `500` produce `20000`. Totals include every stored employee.
6. Search using part of a name; **Clear** restores the list. Select a row before **View**, **Update**, or **Delete**. Keep its existing type when updating; confirm deletion when prompted.
7. Refresh the employee list and dashboard after changes to see current totals.
8. Use **Logout** when finished or **Exit** to close the application.

## Input Validation and Exception Handling

Request DTOs enforce nonblank names/departments and credentials. Registration requires a password of at least six characters. Monthly salary, part-time hours, and hourly rate must be present and positive (`@NotNull`, `@Positive`); hours use `Integer`. Controllers apply `@Valid`. Entity constraints and database null/unique constraints provide additional checks. Duplicate employee names are allowed; usernames must be unique.

Swing checks required fields, password length, numeric parsing, positive salary inputs, and row selection. Backend validation also applies when requests bypass Swing.

`GlobalExceptionHandler` handles:

- **400:** validation failures, unreadable JSON, argument type mismatches, and illegal arguments such as updating the wrong employee subtype.
- **401:** bad credentials and authentication exceptions handled by the controller advice.
- **404:** `EmployeeNotFoundException`.
- **409:** data-integrity conflicts; duplicate registration also returns 409 directly from `AuthController`.
- **500:** unexpected exceptions, logged server-side with a generic client message.

Advice responses contain `timestamp`, `status`, `error`, and `message`; validation responses also contain an `errors` field map. Security-filter/entry-point failures and direct controller responses do not necessarily use that same JSON shape.

`ApiClient` converts failed requests to checked `ApiException`, maps I/O failures to a backend/network message, and restores the interrupt flag after interruption. GUI dialogs/status labels display errors. The client currently prioritizes a general response `message` over field-level validation details.

## Testing Instructions and Verified Test Results

Run from the project root:

```powershell
mvn clean test
```

`mvn clean test` compiles and executes tests; `mvn clean install` also packages and installs locally. Both are supported by the Maven configuration. A running production backend or Aiven database is not required.

[PayrollApplicationTests.java](src/test/java/com/akash/payroll/PayrollApplicationTests.java) uses `@SpringBootTest` with a random server port, MockMvc, and Spring Security Test. [Test configuration](src/test/resources/application.properties) selects H2 in memory and `ddl-auto=create-drop`, with a test-only signing key. Setup clears employees and users between test methods.

The **existing Surefire report inspected on 9 October 2026** records:

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
Time elapsed: 7.847 s
```

Evidence: `target/surefire-reports/com.akash.payroll.PayrollApplicationTests.txt` and corresponding `TEST-com.akash.payroll.PayrollApplicationTests.xml`. These generated, Git-ignored files may be absent from a fresh download or removed by `mvn clean`.

This is a **previously recorded result, not a fresh test run for this README edit**. Tests were not rerun to preserve the README-only file-change scope. Inspection confirmed 11 test methods and their assertions, covering:

- Registration/login token issuance and BCrypt password storage.
- Embedded server startup, unauthenticated access rejection, and invalid JWT rejection.
- Full-time and part-time creation and salary output.
- Blank-name rejection and missing-employee handling.
- Listing, name search, and payroll summary totals.
- Full-time update, rejection of an incompatible subtype update, and deletion.

Most employee endpoint tests use `@WithMockUser`. They do not prove the complete valid-JWT desktop flow, GUI behavior, live Aiven connectivity, MySQL-specific schema behavior, or dump restoration. The GUI and live database were not launched during this audit. Capture fresh test and manual demonstration evidence for final submission.

## Screenshots

No project screenshots were found, so no image links are fabricated. **Group action required:** capture actual login, registration, dashboard, employee list/search, add/update/delete, payroll summary, and validation/error screens. Add relative Markdown links once those files exist. Exclude passwords, signing keys, and tokens.

## Group Members and Individual Contributions

No verified group names, roll numbers, or individual contribution records were found. Demo employee names are not evidence of group membership. Replace these placeholders with actual details; adjust the number of rows to match the group.

| Member name | Roll number | Actual module/work contributed | Evidence (files, tests, or report pages) |
|---|---|---|---|
| [Member 1 name] | [Roll number] | [Actual contribution] | [Evidence] |
| [Member 2 name] | [Roll number] | [Actual contribution] | [Evidence] |
| [Member 3 name] | [Roll number] | [Actual contribution] | [Evidence] |

Each member should explain their implementation, relevant OOP concepts, validation, database interactions, and testing. Complete any separate individual contribution submission required by the college.

## Known Limitations

- Payroll uses current inputs only: no tax, deductions, attendance integration, payment processing, payslips, or historical pay periods. Money uses `double`, not decimal monetary arithmetic.
- All authenticated users can modify shared employee data. Roles do not restrict operations; there is no account administration, password reset, token refresh, or server-side logout revocation.
- Update endpoints cannot change employee type, although the dialog permits selecting another type and then receives a backend error.
- Part-time hourly rate is labelled **Additional Value**. Dashboard metrics need manual refresh; saves may block the UI while waiting for the API.
- Results are not paginated, employee names are not unique, and department/type filters have no desktop controls.
- Production uses Hibernate validation and disables demo seeding. Versioned migrations and an isolated backup rehearsal utility are described in the [production repair runbook](docs/backend/PRODUCTION_REPAIR.md).
- Tests use H2 and do not include desktop automation or live MySQL/Aiven verification.
- Older web-oriented guides and browser artifacts differ from the current Java workflow.

## Future Enhancements

Possible future work, **not completed features**:

- Payroll periods, deductions, payslips, and decimal-based monetary calculations.
- Role-based permissions, account management, and improved token/session lifecycle.
- Department/type filters in Swing, clearer form labels, asynchronous saves, and automatic summary refresh.
- Pagination, sorting, appropriate employee uniqueness rules, and audit history.
- Versioned database migrations, controlled demo seeding, and backup/restore verification.
- Broader validation, valid-JWT tests, MySQL integration tests, and automated GUI checks.

## Troubleshooting

- **Missing `DB_PASSWORD` or `JWT_SECRET`:** populate them privately in the backend environment or root `.env`; check the working directory and Java-properties syntax. Quoted dotenv values are not shell-unquoted by this loader.
- **Database connection failure / unknown database:** check database existence, credentials, network access, and `DB_URL`. Aiven uses JDBC port 12421 and the configured TLS option; local MySQL commonly uses 3306.
- **JWT decoding or weak-key error:** use valid Base64 encoding of at least 32 cryptographically random bytes; placeholder text is not a working key.
- **Port already in use:** stop the specific conflicting application or choose another `PORT`, then match the GUI origin. Avoid terminating every Java process.
- **Backend unavailable in Swing:** start the backend first and check `PAYROLL_API_URL` in the GUI terminal. Swing does not read it from `.env`. Supply an origin without `/api`.
- **401 / Request failed:** log in again if the token expired, or supply a valid bearer token for direct API calls. Opening `/api/employees` in a browser without authentication is expected to fail.
- **Swing main class not found:** run `mvn compile exec:java` from the directory containing `pom.xml`. A graphical desktop is required.
- **Java or Lombok build errors:** check the JDK shown by `mvn -v`, use JDK 17 as the baseline, and enable IDE annotation processing. A newer installed JDK does not change the configured target.
- **SQL import encoding error:** convert a separate UTF-16 dump copy to UTF-8 before importing. Use the MySQL client's `SOURCE` command, not PowerShell `<` redirection, and a disposable database.
- **Stale totals / sample records reappear:** refresh the dashboard; `DataLoader` seeds an empty employee database at backend startup.

## References

The implementation is the primary reference for this README:

- [Maven dependencies, compiler target, and launch plugins](pom.xml).
- [Backend configuration](src/main/resources/application.properties) and [Git ignore rules](.gitignore).
- [Employee routes](src/main/java/com/akash/payroll/controller/EmployeeController.java), [authentication routes](src/main/java/com/akash/payroll/controller/AuthController.java), and [payroll service](src/main/java/com/akash/payroll/service/PayrollService.java).
- [Employee model](src/main/java/com/akash/payroll/model/Employee.java), [user model](src/main/java/com/akash/payroll/model/User.java), and [database dump](database/payroll_db.sql).
- [Security configuration](src/main/java/com/akash/payroll/security/SecurityConfig.java), [JWT implementation](src/main/java/com/akash/payroll/security/JwtUtil.java), and [Swing API client](src/main/java/com/akash/payroll/gui/ApiClient.java).
- [Validation/error handling](src/main/java/com/akash/payroll/exception/GlobalExceptionHandler.java) and [integration tests](src/test/java/com/akash/payroll/PayrollApplicationTests.java).

[DAILY_STARTUP_GUIDE.md](DAILY_STARTUP_GUIDE.md) and [PROJECT_EXPLANATION_GUIDE.md](PROJECT_EXPLANATION_GUIDE.md) are older supporting documents. Their React/Vite startup instructions, claims of an unsecured API, and references to `@Min` do not match this implementation. Current constraints use `@Positive`; the Java client is Swing and the API is JWT-protected. Follow this README and the source where those guides conflict.
