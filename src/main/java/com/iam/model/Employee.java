package com.iam.model;

import java.time.LocalDate;

public record Employee(
        String id,
        String givenName,
        String familyName,
        String email,
        String department,
        String jobTitle,
        String supervisorId,
        Status status,
        LocalDate joinedOn,
        LocalDate exitedOn) {

    public String fullName() {
        return givenName + " " + familyName;
    }

    public Employee withDepartmentAndTitle(String newDepartment, String newTitle) {
        return new Employee(id, givenName, familyName, email, newDepartment, newTitle,
                supervisorId, status, joinedOn, exitedOn);
    }
}
