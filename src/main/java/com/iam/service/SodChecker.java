package com.iam.service;

import com.iam.dao.EmployeeDao;
import com.iam.dao.EntitlementDao;
import com.iam.exception.SoDViolationException;
import com.iam.model.Employee;
import com.iam.model.EmployeeEntitlement;
import com.iam.model.SodRule;
import com.iam.model.SodViolation;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Separation-of-duties checks: on every grant, and as a full scan. */
public class SodChecker {

    private final EntitlementDao entitlementDao;
    private final EmployeeDao employeeDao;

    public SodChecker(EntitlementDao entitlementDao, EmployeeDao employeeDao) {
        this.entitlementDao = entitlementDao;
        this.employeeDao = employeeDao;
    }

    public List<SodViolation> findViolations(String employeeId, Collection<String> held, List<SodRule> rules) {
        Set<String> names = new HashSet<>(held);
        List<SodViolation> out = new ArrayList<>();
        for (SodRule r : rules) {
            if (names.contains(r.entitlementA()) && names.contains(r.entitlementB())) {
                out.add(new SodViolation(employeeId, r.entitlementA(), r.entitlementB(), r.description()));
            }
        }
        return out;
    }

    /** Throws when the given set of entitlement names would break an SoD rule. */
    public void assertNoViolation(Connection c, String employeeId, Collection<String> resultingNames)
            throws SQLException, SoDViolationException {
        List<SodViolation> v = findViolations(employeeId, resultingNames, entitlementDao.findSodRules(c));
        if (!v.isEmpty()) {
            throw new SoDViolationException(v);
        }
    }

    public List<SodViolation> scanAll(Connection c) throws SQLException {
        List<SodRule> rules = entitlementDao.findSodRules(c);
        List<SodViolation> out = new ArrayList<>();
        for (Employee e : employeeDao.findAll(c)) {
            Set<String> names = new HashSet<>();
            for (EmployeeEntitlement h : entitlementDao.findForEmployee(c, e.id())) {
                names.add(h.entName());
            }
            out.addAll(findViolations(e.id(), names, rules));
        }
        return out;
    }
}
