package com.iam.dao;

import com.iam.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcRoleDao implements RoleDao {

    private static final String RULE_BASE =
            "SELECT DISTINCT r.role_id, r.role_name FROM role_rules rr "
          + "JOIN departments d ON d.dept_id = rr.dept_id "
          + "JOIN roles r ON r.role_id = rr.role_id WHERE d.name = ? ";

    @Override
    public List<Role> findForEmployee(Connection c, String employeeId) throws SQLException {
        String sql = "SELECT r.role_id, r.role_name FROM employee_roles er "
                   + "JOIN roles r ON r.role_id = er.role_id WHERE er.employee_id = ? ORDER BY r.role_name";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeId);
            return read(ps);
        }
    }

    @Override
    public List<Role> findDepartmentRoles(Connection c, String department) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(RULE_BASE + "AND rr.job_title IS NULL ORDER BY r.role_name")) {
            ps.setString(1, department);
            return read(ps);
        }
    }

    @Override
    public List<Role> findTitleRoles(Connection c, String department, String jobTitle) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(RULE_BASE + "AND rr.job_title = ? ORDER BY r.role_name")) {
            ps.setString(1, department);
            ps.setString(2, jobTitle);
            return read(ps);
        }
    }

    @Override
    public void grant(Connection c, String employeeId, int roleId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO employee_roles (employee_id, role_id) VALUES (?, ?)")) {
            ps.setString(1, employeeId);
            ps.setInt(2, roleId);
            ps.executeUpdate();
        }
    }

    @Override
    public void revoke(Connection c, String employeeId, int roleId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM employee_roles WHERE employee_id = ? AND role_id = ?")) {
            ps.setString(1, employeeId);
            ps.setInt(2, roleId);
            ps.executeUpdate();
        }
    }

    private List<Role> read(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Role> list = new ArrayList<>();
            while (rs.next()) list.add(new Role(rs.getInt(1), rs.getString(2)));
            return list;
        }
    }
}
