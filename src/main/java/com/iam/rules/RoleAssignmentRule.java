package com.iam.rules;

import com.iam.model.Employee;
import com.iam.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** One business rule that decides which roles an employee should hold. */
public interface RoleAssignmentRule {
    String name();
    List<Role> rolesFor(Connection c, Employee employee) throws SQLException;
}
