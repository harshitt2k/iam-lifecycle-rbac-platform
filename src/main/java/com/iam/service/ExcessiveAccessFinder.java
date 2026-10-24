package com.iam.service;

import com.iam.dao.EmployeeDao;
import com.iam.dao.EntitlementDao;
import com.iam.dao.RoleDao;
import com.iam.model.AccessFinding;
import com.iam.model.Employee;
import com.iam.model.EmployeeEntitlement;
import com.iam.model.Entitlement;
import com.iam.model.FindingType;
import com.iam.model.Role;
import com.iam.model.Source;
import com.iam.model.Status;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flags access that the role model does not explain:
 *  - DIRECT entitlements,
 *  - entitlements not reachable through the employee's current roles,
 *  - any access still held by a TERMINATED employee.
 */
public class ExcessiveAccessFinder {

    private final EmployeeDao employeeDao;
    private final RoleDao roleDao;
    private final EntitlementDao entitlementDao;

    public ExcessiveAccessFinder(EmployeeDao employeeDao, RoleDao roleDao, EntitlementDao entitlementDao) {
        this.employeeDao = employeeDao;
        this.roleDao = roleDao;
        this.entitlementDao = entitlementDao;
    }

    public List<AccessFinding> findAll(Connection c) throws SQLException {
        List<AccessFinding> out = new ArrayList<>();
        for (Employee e : employeeDao.findAll(c)) {
            List<EmployeeEntitlement> held = entitlementDao.findForEmployee(c, e.id());

            Set<Integer> coveredByRoles = new HashSet<>();
            for (Role r : roleDao.findForEmployee(c, e.id())) {
                for (Entitlement en : entitlementDao.findForRole(c, r.id())) {
                    coveredByRoles.add(en.id());
                }
            }

            if (e.status() == Status.TERMINATED && !held.isEmpty()) {
                String names = held.stream().map(EmployeeEntitlement::entName).distinct()
                        .collect(Collectors.joining(", "));
                out.add(new AccessFinding(e.id(), FindingType.TERMINATED_WITH_ACCESS,
                        held.size() + " entitlement(s) still held: " + names));
            }
            for (EmployeeEntitlement h : held) {
                if (h.source() == Source.DIRECT) {
                    out.add(new AccessFinding(e.id(), FindingType.DIRECT_ACCESS, h.entName()));
                } else if (!coveredByRoles.contains(h.entId())) {
                    out.add(new AccessFinding(e.id(), FindingType.NOT_COVERED_BY_ROLES, h.entName()));
                }
            }
        }
        return out;
    }
}
