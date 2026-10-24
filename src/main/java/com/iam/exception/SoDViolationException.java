package com.iam.exception;

import com.iam.model.SodViolation;

import java.util.List;
import java.util.stream.Collectors;

public class SoDViolationException extends Exception {

    private final List<SodViolation> violations;

    public SoDViolationException(List<SodViolation> violations) {
        super(describe(violations));
        this.violations = List.copyOf(violations);
    }

    public List<SodViolation> getViolations() {
        return violations;
    }

    private static String describe(List<SodViolation> v) {
        return "SoD conflict: " + v.stream()
                .map(x -> x.employeeId() + " " + x.entitlementA() + " + " + x.entitlementB())
                .collect(Collectors.joining("; "));
    }
}
