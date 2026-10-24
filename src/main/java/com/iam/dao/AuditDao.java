package com.iam.dao;

import com.iam.model.AuditEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface AuditDao {
    void log(Connection c, String employeeId, String action, String target,
             String performedBy, String reason) throws SQLException;
    List<AuditEntry> findByEmployee(Connection c, String employeeId) throws SQLException;
    List<AuditEntry> findRecent(Connection c, int limit) throws SQLException;
}
