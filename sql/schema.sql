-- IAM Lifecycle & RBAC Platform - schema
-- Run in phpMyAdmin (SQL tab) or: mysql -u root < schema.sql
-- Separate from IIQ's schemas and from corp_hr_directory.

DROP DATABASE IF EXISTS iam_platform;
CREATE DATABASE iam_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE iam_platform;

CREATE TABLE departments (
  dept_id     INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE employees (
  employee_id   VARCHAR(10) PRIMARY KEY,          -- same as staff_id in HR / IIQ
  given_name    VARCHAR(60) NOT NULL,
  family_name   VARCHAR(60) NOT NULL,
  email         VARCHAR(120) NOT NULL UNIQUE,
  dept_id       INT NOT NULL,
  job_title     VARCHAR(80) NOT NULL,
  supervisor_id VARCHAR(10) NULL,
  status        ENUM('ACTIVE','ON_LEAVE','TERMINATED') NOT NULL DEFAULT 'ACTIVE',
  joined_on     DATE NOT NULL,
  exited_on     DATE NULL,
  CONSTRAINT fk_emp_dept FOREIGN KEY (dept_id) REFERENCES departments(dept_id),
  CONSTRAINT fk_emp_sup  FOREIGN KEY (supervisor_id) REFERENCES employees(employee_id)
) ENGINE=InnoDB;

CREATE TABLE roles (
  role_id     INT AUTO_INCREMENT PRIMARY KEY,
  role_name   VARCHAR(60) NOT NULL UNIQUE,
  description VARCHAR(200)
) ENGINE=InnoDB;

CREATE TABLE entitlements (
  ent_id      INT AUTO_INCREMENT PRIMARY KEY,
  ent_name    VARCHAR(60) NOT NULL UNIQUE,
  application VARCHAR(60) NOT NULL,
  description VARCHAR(200)
) ENGINE=InnoDB;

CREATE TABLE role_entitlements (
  role_id INT NOT NULL,
  ent_id  INT NOT NULL,
  PRIMARY KEY (role_id, ent_id),
  FOREIGN KEY (role_id) REFERENCES roles(role_id),
  FOREIGN KEY (ent_id)  REFERENCES entitlements(ent_id)
) ENGINE=InnoDB;

-- Business rules: department (+ optional job title) -> role.  job_title NULL = whole department.
CREATE TABLE role_rules (
  rule_id   INT AUTO_INCREMENT PRIMARY KEY,
  dept_id   INT NOT NULL,
  job_title VARCHAR(80) NULL,
  role_id   INT NOT NULL,
  FOREIGN KEY (dept_id) REFERENCES departments(dept_id),
  FOREIGN KEY (role_id) REFERENCES roles(role_id)
) ENGINE=InnoDB;

CREATE TABLE employee_roles (
  employee_id VARCHAR(10) NOT NULL,
  role_id     INT NOT NULL,
  assigned_on DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (employee_id, role_id),
  FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
  FOREIGN KEY (role_id)     REFERENCES roles(role_id)
) ENGINE=InnoDB;

-- source = ROLE (granted via a role) or DIRECT (granted outside the role model => suspect)
CREATE TABLE employee_entitlements (
  employee_id VARCHAR(10) NOT NULL,
  ent_id      INT NOT NULL,
  source      ENUM('ROLE','DIRECT') NOT NULL,
  granted_on  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (employee_id, ent_id, source),
  FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
  FOREIGN KEY (ent_id)      REFERENCES entitlements(ent_id)
) ENGINE=InnoDB;

CREATE TABLE sod_rules (
  sod_id      INT AUTO_INCREMENT PRIMARY KEY,
  ent_a       INT NOT NULL,
  ent_b       INT NOT NULL,
  description VARCHAR(200),
  FOREIGN KEY (ent_a) REFERENCES entitlements(ent_id),
  FOREIGN KEY (ent_b) REFERENCES entitlements(ent_id),
  CHECK (ent_a <> ent_b)
) ENGINE=InnoDB;

-- Append-only audit trail
CREATE TABLE audit_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_id  VARCHAR(10) NOT NULL,
  action       VARCHAR(30) NOT NULL,     -- GRANT_ROLE, REVOKE_ROLE, GRANT_ENT, REVOKE_ENT, JOINER, MOVER, LEAVER, SOD_BLOCKED
  target       VARCHAR(100),
  performed_by VARCHAR(60) NOT NULL,
  ts           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason       VARCHAR(255),
  INDEX idx_audit_emp (employee_id),
  INDEX idx_audit_ts (ts)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_log
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_log is append-only';
END$$
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_log
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_log is append-only';
END$$
DELIMITER ;
