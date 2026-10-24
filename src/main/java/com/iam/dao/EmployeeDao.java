package com.iam.dao;

import com.iam.exception.EmployeeNotFoundException;
import com.iam.model.Employee;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeDao {
    List<Employee> findAll(Connection c) throws SQLException;
    Optional<Employee> findById(Connection c, String id) throws SQLException;
    void insert(Connection c, Employee e) throws SQLException;
    void updateDepartmentAndTitle(Connection c, String id, String department, String jobTitle)
            throws SQLException, EmployeeNotFoundException;
    void markTerminated(Connection c, String id, LocalDate exitedOn)
            throws SQLException, EmployeeNotFoundException;
}
