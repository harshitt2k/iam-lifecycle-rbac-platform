# IAM Lifecycle & RBAC Platform

A mini enterprise identity and access management application written in plain Java 17, JDBC and MySQL. It manages employees through the joiner / mover / leaver lifecycle, assigns access from business rules, detects separation-of-duties (SoD) conflicts and excessive access, and keeps an append-only audit trail.

> Status: v1 (console app) in progress. v2 adds servlet API on Tomcat, v3 adds the dashboard.

## Lifecycle rules

| # | Rule | Behaviour |
|---|------|-----------|
| 1 | **Role assignment** | Department + job title map to roles through the `role_rules` table. A rule with no job title applies to everyone in that department. Example: Engineering + Backend Engineer gives `ENG_BASE` and `ENG_DEVELOPER`. |
| 2 | **Joiner** | Insert the employee, assign roles from the rules, grant the role entitlements (`source = ROLE`), write audit entries. |
| 3 | **Mover** | Compute roles for the new department and title, revoke roles that are no longer mapped, grant the new ones, and write audit entries. Entitlements follow the roles. |
| 4 | **Leaver** | Revoke every role and entitlement, set `status = TERMINATED` and `exited_on`. The employee row is never deleted. |
| 5 | **SoD conflict** | An employee must not hold both entitlements of a pair in `sod_rules`. Checked on every grant (the grant is blocked and audited) and in a full scan. |
| 6 | **Excessive access** | Any entitlement that is `DIRECT`, or that is not reachable through the employee's current roles. |
| 7 | **Terminated with access** | Any `TERMINATED` employee who still holds roles or entitlements. |
| 8 | **Atomicity** | Each JML workflow runs in one JDBC transaction. Any failure rolls everything back. |
| 9 | **Audit** | Every grant and revoke is written to `audit_log`, which is append-only (triggers block UPDATE and DELETE). |

### SoD pairs

| Entitlement A | Entitlement B | Reason |
|---|---|---|
| `CREATE_VENDOR` | `APPROVE_PAYMENT` | Creating a vendor and approving its payments must be separate people. |
| `DEVELOPER_PROD_WRITE` | `PROD_DEPLOY_APPROVER` | Writing to production and approving the deployment must be separate people. |

### Seeded problems (used by the detection tests)

| Employee | Problem |
|---|---|
| S304 Pranav Hegde (TERMINATED) | Still holds `CRM_READ` and `TICKET_ADMIN` |
| S402 Kavya Rajan (Procurement Officer) | Holds `CREATE_VENDOR` via role and `APPROVE_PAYMENT` directly: SoD violation and excessive access |
| S103 Lavanya Krishnan (Backend Engineer) | Holds `DEVELOPER_PROD_WRITE` via role and `PROD_DEPLOY_APPROVER` directly: SoD violation and excessive access |
| S203 Diya Saxena (Legal Associate) | Holds `COMPLIANCE_REPORTS` directly: excessive access only |

## Schema

Tables: `departments`, `employees`, `roles`, `entitlements`, `role_entitlements`, `role_rules`, `employee_roles`, `employee_entitlements` (with `source` = ROLE or DIRECT), `sod_rules`, `audit_log`.

*(Add a schema diagram image here, for example exported from phpMyAdmin's Designer tab.)*

## Setup

1. Install Java 17 and Maven. Start MySQL from XAMPP.
2. Create the databases in phpMyAdmin (or the mysql client):
   ```sql
   CREATE DATABASE iam_platform      CHARACTER SET utf8mb4;
   CREATE DATABASE iam_platform_test CHARACTER SET utf8mb4;
   ```
3. In `iam_platform`, run `sql/01_schema.sql`, then `sql/02_seed.sql`, then `sql/03_audit_triggers.sql`.
   The seed copies the 20 sample employees from `corp_hr_directory.staff_records`.
4. Edit `src/main/resources/db.properties` with your MySQL user and password.
5. Run the console app:
   ```
   mvn compile exec:java
   ```
6. Run the tests (uses `iam_platform_test`):
   ```
   mvn test
   ```

## Project layout (planned)

```
src/main/java/com/iam/
  model/      POJOs: Employee, Role, Entitlement, AuditEntry, SoDRule
  dao/        DAO interfaces and JDBC implementations
  rules/      RoleAssignmentRule interface + implementations
  service/    JmlService (joiner, mover, leaver), SoDChecker, AccessAnalyzer
  exception/  EmployeeNotFoundException, SoDViolationException
  App.java    Menu-driven CLI
sql/          schema, seed, audit triggers
```

## How this maps to SailPoint IdentityIQ

| This project | IdentityIQ equivalent |
|---|---|
| `corp_hr_directory.staff_records` | Authoritative JDBC application (HR source) |
| `employees` table | Identity cube |
| Employee `manager_id` | Manager correlation (`supervisor_id` -> `staff_Id`) |
| `role_rules` | Birthright roles / role assignment rules |
| `roles`, `role_entitlements` | Business and IT roles with entitlements |
| `employee_entitlements.source = DIRECT` | Entitlements outside any role (additional access) |
| `sod_rules` | Policies (SoD policy) and policy violations |
| Excessive-access finder | Access review / certification candidates |
| JML services | Lifecycle events and the Joiner / Mover / Leaver workflows |
| `audit_log` | Audit events and identity history |

## Roadmap

- [x] Schema, seed data, rules documented
- [ ] Model and DAO layer
- [ ] Rules engine
- [ ] JML services with transactions
- [ ] SoD and excessive-access detection
- [ ] CLI and CSV access report
- [ ] JUnit tests
- [ ] v2: servlet API on Tomcat 9 (port 8081) + Postman collection
- [ ] v3: HTML/JS dashboard
