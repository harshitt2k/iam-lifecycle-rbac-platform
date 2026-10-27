# REST API (v2)

Base URL when deployed on the IAM API Tomcat: `http://localhost:8081/iam-api/api`

All bodies are JSON. Add an `X-Actor` header to record who performed an action in the audit log (default `api`).

| Method | Path | Purpose | Success |
|---|---|---|---|
| GET | `/health` | Liveness check | 200 |
| GET | `/employees` | All employees | 200 |
| POST | `/employees` | **Joiner**: hire, assign roles by rule | 201 |
| GET | `/employees/{id}` | Employee with roles and entitlements | 200 |
| POST | `/employees/{id}/move` | **Mover**: new department and title | 200 |
| POST | `/employees/{id}/leave` | **Leaver**: revoke everything, mark TERMINATED | 200 |
| POST | `/employees/{id}/entitlements` | Grant a DIRECT entitlement (SoD-checked) | 201 |
| DELETE | `/employees/{id}/entitlements/{name}` | Remove a DIRECT entitlement | 200 |
| GET | `/reports/access` | "Who has access to what" as CSV (`?format=json` for JSON) | 200 |
| GET | `/reports/sod` | Full SoD scan | 200 |
| GET | `/reports/excessive` | Excessive-access and terminated-with-access findings | 200 |
| GET | `/audit` | Recent audit entries (`?employeeId=`, `?limit=1..500`) | 200 |

## Status codes

| Code | Meaning |
|---|---|
| 400 | Bad or missing input, unknown department, supervisor or entitlement |
| 404 | Employee not found, or no such endpoint |
| 405 | Wrong HTTP method (the `Allow` header lists the right ones) |
| 409 | Separation-of-duties conflict, duplicate employee, or the employee is already TERMINATED |
| 500 | Server or database error |

Errors look like `{"error": "..."}`. An SoD refusal also carries the conflicting pairs:

```json
{
  "error": "SoD conflict: S403 CREATE_VENDOR + APPROVE_PAYMENT",
  "violations": [
    { "employeeId": "S403", "entitlementA": "CREATE_VENDOR", "entitlementB": "APPROVE_PAYMENT",
      "description": "Same person must not create vendors and approve payments" }
  ]
}
```

## Examples

Hire (Joiner):

```
POST /employees
{ "employeeId": "T100", "givenName": "Asha", "familyName": "Rao", "email": "asha.rao@lab-corp.test",
  "department": "Engineering", "jobTitle": "Backend Engineer", "supervisorId": "S101" }
```

Move (Mover):

```
POST /employees/T100/move
{ "department": "Operations", "jobTitle": "Procurement Officer" }
```

Terminate (Leaver); `exitedOn` is optional and defaults to today:

```
POST /employees/T100/leave
{ "exitedOn": "2026-10-26" }
```

Grant outside the role model; `reason` is optional:

```
POST /employees/T100/entitlements
{ "entitlement": "JIRA_ACCESS", "reason": "Cross-team project" }
```

Employee response shape (joiner, move, leave, grant and revoke all return it):

```json
{
  "employee": { "id": "T100", "givenName": "Asha", "familyName": "Rao", "department": "Engineering",
                "jobTitle": "Backend Engineer", "status": "ACTIVE", "joinedOn": "2026-10-25", "exitedOn": null },
  "roles": ["ENG_BASE", "ENG_DEVELOPER"],
  "entitlements": [ { "name": "GIT_REPO_READ", "source": "ROLE" } ]
}
```
