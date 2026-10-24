-- Seed data. Run AFTER schema.sql.
-- Employees are copied from corp_hr_directory.staff_records (same MySQL server),
-- so the app and SailPoint IIQ share one source of truth.
-- NOTE: for the test database, create iam_platform_test with the same schema and run this too.

USE iam_platform;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Departments and employees from the HR source
INSERT INTO departments (name)
SELECT DISTINCT division FROM corp_hr_directory.staff_records ORDER BY division;

INSERT INTO employees (employee_id, given_name, family_name, email, dept_id, job_title,
                       supervisor_id, status, joined_on, exited_on)
SELECT s.staff_id, s.given_name, s.family_name, s.work_email, d.dept_id, s.designation,
       s.supervisor_id,
       CASE s.employment_state WHEN 'TERMINATED' THEN 'TERMINATED' ELSE 'ACTIVE' END,
       s.joined_on, s.exited_on
FROM corp_hr_directory.staff_records s
JOIN departments d ON d.name = s.division
ORDER BY s.staff_id;

SET FOREIGN_KEY_CHECKS = 1;

-- 2. Roles
INSERT INTO roles (role_name, description) VALUES
 ('ENG_BASE','Baseline engineering access'),
 ('ENG_DEVELOPER','Backend developer access'),
 ('ENG_QA','QA engineer access'),
 ('ENG_DEVOPS','DevOps / pipeline access'),
 ('ENG_LEADERSHIP','Engineering leadership approvals'),
 ('LEGAL_BASE','Baseline legal access'),
 ('LEGAL_ASSOCIATE','Legal associate: contract editing'),
 ('LEGAL_COMPLIANCE','Compliance officer: audit views'),
 ('LEGAL_COUNSEL','General counsel'),
 ('SUPPORT_BASE','Baseline support access'),
 ('SUPPORT_LEAD','Support lead'),
 ('SUPPORT_ESCALATION','Escalation specialist'),
 ('OPS_BASE','Baseline operations access'),
 ('OPS_PROCUREMENT','Procurement: vendor creation'),
 ('OPS_HEAD','Operations head: payment approval'),
 ('OPS_LOGISTICS','Logistics: inventory');

-- 3. Entitlements
INSERT INTO entitlements (ent_name, application, description) VALUES
 ('GIT_REPO_READ','GitLab','Read source repositories'),
 ('JIRA_ACCESS','Jira','Project tracking access'),
 ('DEVELOPER_PROD_WRITE','Prod-DB','Write to production'),
 ('QA_ENV_ACCESS','TestEnv','QA environment access'),
 ('CI_PIPELINE_ADMIN','Jenkins','Administer CI pipelines'),
 ('PROD_DEPLOY_APPROVER','Jenkins','Approve production deployments'),
 ('LEGAL_DOCS_READ','DocVault','Read legal documents'),
 ('CONTRACT_EDIT','DocVault','Edit contracts'),
 ('COMPLIANCE_AUDIT_VIEW','GRC','View audit evidence'),
 ('HELPDESK_TICKETS','Zendesk','Work tickets'),
 ('TICKET_REASSIGN','Zendesk','Reassign tickets'),
 ('ESCALATION_QUEUE','Zendesk','Escalation queue access'),
 ('ERP_VIEW','ERP','View ERP records'),
 ('CREATE_VENDOR','ERP','Create vendors'),
 ('APPROVE_PAYMENT','ERP','Approve payments'),
 ('INVENTORY_MANAGE','ERP','Manage inventory');

-- 4. Role -> entitlements
INSERT INTO role_entitlements (role_id, ent_id)
SELECT r.role_id, e.ent_id FROM (
  SELECT 'ENG_BASE' r, 'GIT_REPO_READ' e UNION ALL SELECT 'ENG_BASE','JIRA_ACCESS'
  UNION ALL SELECT 'ENG_DEVELOPER','DEVELOPER_PROD_WRITE'
  UNION ALL SELECT 'ENG_QA','QA_ENV_ACCESS'
  UNION ALL SELECT 'ENG_DEVOPS','CI_PIPELINE_ADMIN'
  UNION ALL SELECT 'ENG_LEADERSHIP','PROD_DEPLOY_APPROVER'
  UNION ALL SELECT 'LEGAL_BASE','LEGAL_DOCS_READ'
  UNION ALL SELECT 'LEGAL_ASSOCIATE','CONTRACT_EDIT'
  UNION ALL SELECT 'LEGAL_COMPLIANCE','COMPLIANCE_AUDIT_VIEW'
  UNION ALL SELECT 'LEGAL_COUNSEL','CONTRACT_EDIT'
  UNION ALL SELECT 'LEGAL_COUNSEL','COMPLIANCE_AUDIT_VIEW'
  UNION ALL SELECT 'SUPPORT_BASE','HELPDESK_TICKETS'
  UNION ALL SELECT 'SUPPORT_LEAD','TICKET_REASSIGN'
  UNION ALL SELECT 'SUPPORT_ESCALATION','ESCALATION_QUEUE'
  UNION ALL SELECT 'OPS_BASE','ERP_VIEW'
  UNION ALL SELECT 'OPS_PROCUREMENT','CREATE_VENDOR'
  UNION ALL SELECT 'OPS_HEAD','APPROVE_PAYMENT'
  UNION ALL SELECT 'OPS_LOGISTICS','INVENTORY_MANAGE'
) m
JOIN roles r ON r.role_name = m.r
JOIN entitlements e ON e.ent_name = m.e;

-- 5. Business rules: department (+ title) -> role
INSERT INTO role_rules (dept_id, job_title, role_id)
SELECT d.dept_id, m.title, r.role_id FROM (
  SELECT 'Engineering' dept, NULL title, 'ENG_BASE' role
  UNION ALL SELECT 'Engineering','Backend Engineer','ENG_DEVELOPER'
  UNION ALL SELECT 'Engineering','QA Engineer','ENG_QA'
  UNION ALL SELECT 'Engineering','DevOps Engineer','ENG_DEVOPS'
  UNION ALL SELECT 'Engineering','Engineering Director','ENG_LEADERSHIP'
  UNION ALL SELECT 'Legal',NULL,'LEGAL_BASE'
  UNION ALL SELECT 'Legal','Legal Associate','LEGAL_ASSOCIATE'
  UNION ALL SELECT 'Legal','Compliance Officer','LEGAL_COMPLIANCE'
  UNION ALL SELECT 'Legal','General Counsel','LEGAL_COUNSEL'
  UNION ALL SELECT 'Support',NULL,'SUPPORT_BASE'
  UNION ALL SELECT 'Support','Support Lead','SUPPORT_LEAD'
  UNION ALL SELECT 'Support','Escalation Specialist','SUPPORT_ESCALATION'
  UNION ALL SELECT 'Operations',NULL,'OPS_BASE'
  UNION ALL SELECT 'Operations','Procurement Officer','OPS_PROCUREMENT'
  UNION ALL SELECT 'Operations','Operations Head','OPS_HEAD'
  UNION ALL SELECT 'Operations','Logistics Analyst','OPS_LOGISTICS'
) m
JOIN departments d ON d.name = m.dept
JOIN roles r ON r.role_name = m.role;

-- 6. SoD rules
INSERT INTO sod_rules (ent_a, ent_b, description)
SELECT a.ent_id, b.ent_id, m.d FROM (
  SELECT 'CREATE_VENDOR' a, 'APPROVE_PAYMENT' b, 'Same person must not create vendors and approve payments' d
  UNION ALL SELECT 'DEVELOPER_PROD_WRITE','PROD_DEPLOY_APPROVER','Developer must not approve own production deployments'
) m
JOIN entitlements a ON a.ent_name = m.a
JOIN entitlements b ON b.ent_name = m.b;

-- 7. Current-state assignments, as the joiner rules would have produced them.
--    S304 is skipped on purpose (see seeded issues below).
INSERT INTO employee_roles (employee_id, role_id)
SELECT e.employee_id, rr.role_id
FROM employees e
JOIN role_rules rr ON rr.dept_id = e.dept_id AND (rr.job_title IS NULL OR rr.job_title = e.job_title)
WHERE e.employee_id <> 'S304';

INSERT INTO employee_entitlements (employee_id, ent_id, source)
SELECT DISTINCT er.employee_id, re.ent_id, 'ROLE'
FROM employee_roles er JOIN role_entitlements re ON re.role_id = er.role_id;

-- 8. SEEDED ISSUES for the detectors and tests
--    S106 (TERMINATED)   keeps all role-based access            -> "terminated employee still has access"
--    S304 (TERMINATED)   leftover DIRECT ticket-queue access    -> terminated + DIRECT
--    S402 (Procurement)  DIRECT APPROVE_PAYMENT                 -> SoD violation (+ excessive)
--    S103 (Backend Eng)  DIRECT PROD_DEPLOY_APPROVER            -> SoD violation (+ excessive)
--    S305 (Escalation)   DIRECT CI_PIPELINE_ADMIN               -> excessive access only
INSERT INTO employee_entitlements (employee_id, ent_id, source)
SELECT m.emp, e.ent_id, 'DIRECT' FROM (
  SELECT 'S304' emp, 'ESCALATION_QUEUE' ent
  UNION ALL SELECT 'S402','APPROVE_PAYMENT'
  UNION ALL SELECT 'S103','PROD_DEPLOY_APPROVER'
  UNION ALL SELECT 'S305','CI_PIPELINE_ADMIN'
) m JOIN entitlements e ON e.ent_name = m.ent;

-- 9. Seed audit trail
INSERT INTO audit_log (employee_id, action, target, performed_by, reason)
SELECT employee_id, 'JOINER', 'initial seed', 'seed.sql', 'Bootstrap from HR directory' FROM employees;
