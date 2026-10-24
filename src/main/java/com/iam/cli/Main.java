package com.iam.cli;

import com.iam.dao.EmployeeDao;
import com.iam.dao.JdbcEmployeeDao;
import com.iam.model.Employee;
import com.iam.util.DbConnection;

import java.sql.Connection;

public class Main {
    public static void main(String[] args) throws Exception {
        EmployeeDao dao = new JdbcEmployeeDao();
        try (Connection c = DbConnection.get()) {
            for (Employee e : dao.findAll(c)) {
                System.out.printf("%-5s %-16s %-12s %-22s %s%n",
                        e.id(), e.fullName(), e.department(), e.jobTitle(), e.status());
            }
            System.out.println(dao.findById(c, "S102").map(Employee::email).orElse("not found"));
            System.out.println(dao.findById(c, "S999").map(Employee::email).orElse("not found"));
        }
    }
}