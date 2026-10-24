package com.iam.dao;

import com.iam.model.EmployeeEntitlement;
import com.iam.model.Entitlement;
import com.iam.model.SodRule;
import com.iam.model.Source;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcEntitlementDao implements EntitlementDao {

    @Override
    public Optional<Entitlement> findByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT ent_id, ent_name, application FROM entitlements WHERE ent_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Entitlement(rs.getInt(1), rs.getString(2), rs.getString(3)))
                        : Optional.empty();
            }
        }
    }

    @Override
    public List<Entitlement> findForRole(Connection c, int roleId) throws SQLException {
        String sql = "SELECT e.ent_id, e.ent_name, e.application FROM role_entitlements re "
                   + "JOIN entitlements e ON e.ent_id = re.ent_id WHERE re.role_id = ? ORDER BY e.ent_name";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Entitlement> list = new ArrayList<>();
                while (rs.next()) list.add(new Entitlement(rs.getInt(1), rs.getString(2), rs.getString(3)));
                return list;
            }
        }
    }

    @Override
    public List<EmployeeEntitlement> findForEmployee(Connection c, String employeeId) throws SQLException {
        String sql = "SELECT ee.employee_id, e.ent_id, e.ent_name, ee.source FROM employee_entitlements ee "
                   + "JOIN entitlements e ON e.ent_id = ee.ent_id WHERE ee.employee_id = ? "
                   + "ORDER BY e.ent_name, ee.source";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                List<EmployeeEntitlement> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new EmployeeEntitlement(rs.getString(1), rs.getInt(2), rs.getString(3),
                            Source.valueOf(rs.getString(4))));
                }
                return list;
            }
        }
    }

    @Override
    public void grant(Connection c, String employeeId, int entId, Source source) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO employee_entitlements (employee_id, ent_id, source) VALUES (?, ?, ?)")) {
            ps.setString(1, employeeId);
            ps.setInt(2, entId);
            ps.setString(3, source.name());
            ps.executeUpdate();
        }
    }

    @Override
    public void revoke(Connection c, String employeeId, int entId, Source source) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM employee_entitlements WHERE employee_id = ? AND ent_id = ? AND source = ?")) {
            ps.setString(1, employeeId);
            ps.setInt(2, entId);
            ps.setString(3, source.name());
            ps.executeUpdate();
        }
    }

    @Override
    public List<SodRule> findSodRules(Connection c) throws SQLException {
        String sql = "SELECT s.sod_id, a.ent_name, b.ent_name, s.description FROM sod_rules s "
                   + "JOIN entitlements a ON a.ent_id = s.ent_a JOIN entitlements b ON b.ent_id = s.ent_b";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<SodRule> list = new ArrayList<>();
            while (rs.next()) list.add(new SodRule(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            return list;
        }
    }
}
