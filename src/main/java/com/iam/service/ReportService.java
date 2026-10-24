package com.iam.service;

import com.iam.dao.EmployeeDao;
import com.iam.dao.EntitlementDao;
import com.iam.dao.RoleDao;
import com.iam.model.Employee;
import com.iam.model.EmployeeEntitlement;
import com.iam.model.Role;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** "Who has access to what": a CSV for everyone, and a readable view for one employee. */
public class ReportService {

    private final EmployeeDao employeeDao;
    private final RoleDao roleDao;
    private final EntitlementDao entitlementDao;

    public ReportService(EmployeeDao employeeDao, RoleDao roleDao, EntitlementDao entitlementDao) {
        this.employeeDao = employeeDao;
        this.roleDao = roleDao;
        this.entitlementDao = entitlementDao;
    }

    public void writeAccessCsv(Connection c, Writer w) throws SQLException, IOException {
        w.write("employee_id,name,department,job_title,status,roles,entitlement,source\n");
        for (Employee e : employeeDao.findAll(c)) {
            String roles = roleDao.findForEmployee(c, e.id()).stream()
                    .map(Role::name).collect(Collectors.joining(";"));
            List<EmployeeEntitlement> held = entitlementDao.findForEmployee(c, e.id());
            if (held.isEmpty()) {
                row(w, e, roles, "", "");
            } else {
                for (EmployeeEntitlement h : held) {
                    row(w, e, roles, h.entName(), h.source().name());
                }
            }
        }
    }

    public Optional<String> employeeView(Connection c, String id) throws SQLException {
        Optional<Employee> found = employeeDao.findById(c, id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Employee e = found.get();
        StringBuilder sb = new StringBuilder();
        sb.append(e.id()).append("  ").append(e.fullName()).append("  |  ")
          .append(e.department()).append(" / ").append(e.jobTitle()).append("  |  ")
          .append(e.status()).append("  |  manager: ")
          .append(e.supervisorId() == null ? "-" : e.supervisorId()).append('\n');
        List<Role> roles = roleDao.findForEmployee(c, id);
        sb.append("Roles: ").append(roles.isEmpty() ? "(none)"
                : roles.stream().map(Role::name).collect(Collectors.joining(", "))).append('\n');
        sb.append("Entitlements:\n");
        List<EmployeeEntitlement> held = entitlementDao.findForEmployee(c, id);
        if (held.isEmpty()) {
            sb.append("  (none)\n");
        }
        for (EmployeeEntitlement h : held) {
            sb.append(String.format("  %-24s %s%n", h.entName(), h.source()));
        }
        return Optional.of(sb.toString());
    }

    private void row(Writer w, Employee e, String roles, String ent, String source) throws IOException {
        w.write(String.join(",", csv(e.id()), csv(e.fullName()), csv(e.department()), csv(e.jobTitle()),
                csv(e.status().name()), csv(roles), csv(ent), csv(source)));
        w.write("\n");
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
