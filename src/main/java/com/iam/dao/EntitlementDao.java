package com.iam.dao;

import com.iam.model.EmployeeEntitlement;
import com.iam.model.Entitlement;
import com.iam.model.SodRule;
import com.iam.model.Source;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface EntitlementDao {
    Optional<Entitlement> findByName(Connection c, String name) throws SQLException;
    List<Entitlement> findForRole(Connection c, int roleId) throws SQLException;
    List<EmployeeEntitlement> findForEmployee(Connection c, String employeeId) throws SQLException;
    void grant(Connection c, String employeeId, int entId, Source source) throws SQLException;
    void revoke(Connection c, String employeeId, int entId, Source source) throws SQLException;
    List<SodRule> findSodRules(Connection c) throws SQLException;
}
