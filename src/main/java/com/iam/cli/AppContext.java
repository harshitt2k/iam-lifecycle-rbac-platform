package com.iam.cli;

import com.iam.dao.AuditDao;
import com.iam.dao.EmployeeDao;
import com.iam.dao.EntitlementDao;
import com.iam.dao.JdbcAuditDao;
import com.iam.dao.JdbcEmployeeDao;
import com.iam.dao.JdbcEntitlementDao;
import com.iam.dao.JdbcRoleDao;
import com.iam.dao.RoleDao;
import com.iam.rules.RulesEngine;
import com.iam.service.ExcessiveAccessFinder;
import com.iam.service.LifecycleService;
import com.iam.service.ReportService;
import com.iam.service.SodChecker;
import com.iam.util.DbConnection;

/** Wires DAOs and services together (plain Java, no framework). */
public class AppContext {

    public final EmployeeDao employees = new JdbcEmployeeDao();
    public final RoleDao roles = new JdbcRoleDao();
    public final EntitlementDao entitlements = new JdbcEntitlementDao();
    public final AuditDao audit;
    public final RulesEngine rules;
    public final SodChecker sod;
    public final ExcessiveAccessFinder finder;
    public final ReportService reports;
    public final LifecycleService lifecycle;

    public AppContext() {
        this(new JdbcAuditDao());
    }

    public AppContext(AuditDao audit) {
        this.audit = audit;
        this.rules = RulesEngine.standard(roles);
        this.sod = new SodChecker(entitlements, employees);
        this.finder = new ExcessiveAccessFinder(employees, roles, entitlements);
        this.reports = new ReportService(employees, roles, entitlements);
        this.lifecycle = new LifecycleService(DbConnection::get, employees, roles, entitlements, audit, rules, sod);
    }
}
