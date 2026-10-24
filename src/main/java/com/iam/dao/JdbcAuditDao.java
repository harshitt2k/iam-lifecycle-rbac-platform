package com.iam.dao;

import com.iam.model.AuditEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcAuditDao implements AuditDao {

    private static final String SELECT =
            "SELECT id, employee_id, action, target, performed_by, ts, reason FROM audit_log ";

    @Override
    public void log(Connection c, String employeeId, String action, String target,
                    String performedBy, String reason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_log (employee_id, action, target, performed_by, reason) VALUES (?,?,?,?,?)")) {
            ps.setString(1, employeeId);
            ps.setString(2, action);
            ps.setString(3, cut(target, 100));
            ps.setString(4, performedBy);
            ps.setString(5, cut(reason, 255));
            ps.executeUpdate();
        }
    }

    @Override
    public List<AuditEntry> findByEmployee(Connection c, String employeeId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE employee_id = ? ORDER BY id")) {
            ps.setString(1, employeeId);
            return read(ps);
        }
    }

    @Override
    public List<AuditEntry> findRecent(Connection c, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            return read(ps);
        }
    }

    private List<AuditEntry> read(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<AuditEntry> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new AuditEntry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getObject(6, LocalDateTime.class), rs.getString(7)));
            }
            return list;
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
