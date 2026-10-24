# Lifecycle & Access Rules

## 1. Role assignment (department + job title -> role)
A rule with no job title applies to the whole department. Roles are cumulative.

| Department | Job title | Role | Entitlements |
|---|---|---|---|
| Engineering | (all) | ENG_BASE | GIT_REPO_READ, JIRA_ACCESS |
| Engineering | Backend Engineer | ENG_DEVELOPER | DEVELOPER_PROD_WRITE |
| Engineering | QA Engineer | ENG_QA | QA_ENV_ACCESS |
| Engineering | DevOps Engineer | ENG_DEVOPS | CI_PIPELINE_ADMIN |
| Engineering | Engineering Director | ENG_LEADERSHIP | PROD_DEPLOY_APPROVER |
| Legal | (all) | LEGAL_BASE | LEGAL_DOCS_READ |
| Legal | Legal Associate | LEGAL_ASSOCIATE | CONTRACT_EDIT |
| Legal | Compliance Officer | LEGAL_COMPLIANCE | COMPLIANCE_AUDIT_VIEW |
| Legal | General Counsel | LEGAL_COUNSEL | CONTRACT_EDIT, COMPLIANCE_AUDIT_VIEW |
| Support | (all) | SUPPORT_BASE | HELPDESK_TICKETS |
| Support | Support Lead | SUPPORT_LEAD | TICKET_REASSIGN |
| Support | Escalation Specialist | SUPPORT_ESCALATION | ESCALATION_QUEUE |
| Operations | (all) | OPS_BASE | ERP_VIEW |
| Operations | Procurement Officer | OPS_PROCUREMENT | CREATE_VENDOR |
| Operations | Operations Head | OPS_HEAD | APPROVE_PAYMENT |
| Operations | Logistics Analyst | OPS_LOGISTICS | INVENTORY_MANAGE |

## 2. Lifecycle events
- **Joiner**: insert employee (ACTIVE) -> assign roles by rules -> grant role entitlements -> audit each grant. One transaction.
- **Mover**: compute roles for the new dept/title; revoke roles not in the new set, grant new ones; update employee row; audit each change. One transaction. DIRECT entitlements are NOT silently removed; they are flagged by the excessive-access scan.
- **Leaver**: revoke all roles and all entitlements (ROLE and DIRECT), set status TERMINATED and exited_on. The employee row is never deleted. One transaction.

## 3. Separation of Duties
| Entitlement A | Entitlement B | Why |
|---|---|---|
| CREATE_VENDOR | APPROVE_PAYMENT | Create-and-pay fraud |
| DEVELOPER_PROD_WRITE | PROD_DEPLOY_APPROVER | Self-approved production changes |

The checker runs on every grant (violation => SoDViolationException, transaction rolled back, SOD_BLOCKED audit entry written separately) and in a full scan.

## 4. Excessive access
An entitlement is excessive when the employee holds it with source = DIRECT, or holds it with no current role that grants it. Any access held by a TERMINATED employee is also reported.

## 5. Seeded issues (test fixtures)
| Employee | Issue | Expected detection |
|---|---|---|
| S106 (TERMINATED) | Still holds role-based access | terminated-with-access |
| S304 (TERMINATED) | Leftover DIRECT ESCALATION_QUEUE | terminated-with-access + DIRECT |
| S402 | DIRECT APPROVE_PAYMENT + CREATE_VENDOR via role | SoD + excessive |
| S103 | DIRECT PROD_DEPLOY_APPROVER + DEVELOPER_PROD_WRITE via role | SoD + excessive |
| S305 | DIRECT CI_PIPELINE_ADMIN | excessive only |

## 6. How this maps to IIQ (draft)
| This project | SailPoint IIQ |
|---|---|
| employees (from HR) | Authoritative app CORP_HR_DIRECTORY -> identities |
| supervisor_id | Manager correlation |
| role_rules | Birthright roles / assignment rules |
| Joiner / Mover / Leaver | Lifecycle events and Joiner/Mover/Leaver workflows |
| sod_rules | Policies (SoD) |
| DIRECT entitlements / excessive scan | Access reviews / certifications |
| audit_log | Audit events |
