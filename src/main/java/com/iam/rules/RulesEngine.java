package com.iam.rules;

import com.iam.dao.RoleDao;
import com.iam.model.Employee;
import com.iam.model.Role;
import com.iam.model.Status;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Runs every registered rule and unions the result. Terminated employees never get roles. */
public class RulesEngine {

    private final List<RoleAssignmentRule> rules;

    public RulesEngine(List<RoleAssignmentRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static RulesEngine standard(RoleDao roleDao) {
        return new RulesEngine(List.of(new DepartmentBaseRule(roleDao), new JobTitleRule(roleDao)));
    }

    public Set<Role> evaluate(Connection c, Employee employee) throws SQLException {
        Set<Role> result = new LinkedHashSet<>();
        if (employee.status() == Status.TERMINATED) {
            return result;
        }
        for (RoleAssignmentRule rule : rules) {
            result.addAll(rule.rolesFor(c, employee));
        }
        return result;
    }
}
