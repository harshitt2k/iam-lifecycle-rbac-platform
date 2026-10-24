package com.iam.rules;

import com.iam.dao.RoleDao;
import com.iam.model.Employee;
import com.iam.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Extra roles for a specific job title inside a department (mappings come from role_rules). */
public class JobTitleRule implements RoleAssignmentRule {

    private final RoleDao roleDao;

    public JobTitleRule(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    @Override
    public String name() {
        return "JOB_TITLE";
    }

    @Override
    public List<Role> rolesFor(Connection c, Employee e) throws SQLException {
        return roleDao.findTitleRoles(c, e.department(), e.jobTitle());
    }
}
