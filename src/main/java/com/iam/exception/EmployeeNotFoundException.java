package com.iam.exception;

public class EmployeeNotFoundException extends Exception {
    public EmployeeNotFoundException(String employeeId) {
        super("Employee not found: " + employeeId);
    }
}