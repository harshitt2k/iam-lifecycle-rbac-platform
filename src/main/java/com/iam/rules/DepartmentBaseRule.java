package com.iam.rules;

import com.iam.dao.RoleDao;
import com.iam.model.Employee;
import com.iam.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Everyone in a department gets that department's base roles (mappings come from role_rules). */
public class DepartmentBaseRule implements RoleAssignmentRule {

    private final RoleDao roleDao;

    public DepartmentBaseRule(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    @Override
    public String name() {
        return "DEPARTMENT_BASE";
    }

    @Override
    public List<Role> rolesFor(Connection c, Employee e) throws SQLException {
        return roleDao.findDepartmentRoles(c, e.department());
    }
}
