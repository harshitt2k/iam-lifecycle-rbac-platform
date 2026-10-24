package com.iam.dao;

import com.iam.exception.EmployeeNotFoundException;
import com.iam.model.Employee;
import com.iam.model.Status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcEmployeeDao implements EmployeeDao {

    private static final String SELECT =
            "SELECT e.employee_id, e.given_name, e.family_name, e.email, d.name, e.job_title, "
          + "e.supervisor_id, e.status, e.joined_on, e.exited_on "
          + "FROM employees e JOIN departments d ON d.dept_id = e.dept_id ";

    @Override
    public List<Employee> findAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY e.employee_id");
             ResultSet rs = ps.executeQuery()) {
            List<Employee> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            return list;
        }
    }

    @Override
    public Optional<Employee> findById(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE e.employee_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public void insert(Connection c, Employee e) throws SQLException {
        String sql = "INSERT INTO employees (employee_id, given_name, family_name, email, dept_id, "
                   + "job_title, supervisor_id, status, joined_on) "
                   + "VALUES (?,?,?,?,(SELECT dept_id FROM departments WHERE name = ?),?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.id());
            ps.setString(2, e.givenName());
            ps.setString(3, e.familyName());
            ps.setString(4, e.email());
            ps.setString(5, e.department());
            ps.setString(6, e.jobTitle());
            ps.setString(7, e.supervisorId());
            ps.setString(8, e.status().name());
            ps.setObject(9, e.joinedOn());
            ps.executeUpdate();
        }
    }

    @Override
    public void updateDepartmentAndTitle(Connection c, String id, String department, String jobTitle)
            throws SQLException, EmployeeNotFoundException {
        String sql = "UPDATE employees SET dept_id = (SELECT dept_id FROM departments WHERE name = ?), "
                   + "job_title = ? WHERE employee_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, department);
            ps.setString(2, jobTitle);
            ps.setString(3, id);
            if (ps.executeUpdate() == 0) throw new EmployeeNotFoundException(id);
        }
    }

    @Override
    public void markTerminated(Connection c, String id, LocalDate exitedOn)
            throws SQLException, EmployeeNotFoundException {
        String sql = "UPDATE employees SET status = 'TERMINATED', exited_on = ? WHERE employee_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, exitedOn);
            ps.setString(2, id);
            if (ps.executeUpdate() == 0) throw new EmployeeNotFoundException(id);
        }
    }

    private Employee map(ResultSet rs) throws SQLException {
        return new Employee(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7),
                Status.valueOf(rs.getString(8)),
                rs.getObject(9, LocalDate.class), rs.getObject(10, LocalDate.class));
    }
}
