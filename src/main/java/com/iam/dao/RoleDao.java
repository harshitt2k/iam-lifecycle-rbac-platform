package com.iam.dao;

import com.iam.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface RoleDao {
    List<Role> findForEmployee(Connection c, String employeeId) throws SQLException;
    /** Roles every member of a department gets (rule rows with no job title). */
    List<Role> findDepartmentRoles(Connection c, String department) throws SQLException;
    /** Roles tied to one job title inside a department. */
    List<Role> findTitleRoles(Connection c, String department, String jobTitle) throws SQLException;
    void grant(Connection c, String employeeId, int roleId) throws SQLException;
    void revoke(Connection c, String employeeId, int roleId) throws SQLException;
}
