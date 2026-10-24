package com.iam.service;

import com.iam.dao.AuditDao;
import com.iam.dao.EmployeeDao;
import com.iam.dao.EntitlementDao;
import com.iam.dao.RoleDao;
import com.iam.exception.EmployeeNotFoundException;
import com.iam.exception.SoDViolationException;
import com.iam.model.Employee;
import com.iam.model.EmployeeEntitlement;
import com.iam.model.Entitlement;
import com.iam.model.Role;
import com.iam.model.Source;
import com.iam.model.Status;
import com.iam.rules.RulesEngine;
import com.iam.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Joiner / Mover / Leaver workflows. Each public method is ONE JDBC transaction:
 * if anything fails, nothing is committed, so access can never be half-granted.
 */
public class LifecycleService {

    private final ConnectionFactory connections;
    private final EmployeeDao employees;
    private final RoleDao roles;
    private final EntitlementDao entitlements;
    private final AuditDao audit;
    private final RulesEngine rules;
    private final SodChecker sod;

    public LifecycleService(ConnectionFactory connections, EmployeeDao employees, RoleDao roles,
                            EntitlementDao entitlements, AuditDao audit, RulesEngine rules, SodChecker sod) {
        this.connections = connections;
        this.employees = employees;
        this.roles = roles;
        this.entitlements = entitlements;
        this.audit = audit;
        this.rules = rules;
        this.sod = sod;
    }

    // ------------------------------------------------------------------ JOINER

    public Employee joiner(Employee e, String actor) throws SQLException, SoDViolationException {
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                employees.insert(c, e);
                audit.log(c, e.id(), "JOINER", e.department() + " / " + e.jobTitle(), actor, "New hire");

                Set<Role> target = rules.evaluate(c, e);
                Map<Integer, Entitlement> targetEnts = entitlementsOf(c, target);
                sod.assertNoViolation(c, e.id(), names(targetEnts));

                for (Role r : target) {
                    roles.grant(c, e.id(), r.id());
                    audit.log(c, e.id(), "GRANT_ROLE", r.name(), actor, "Joiner rule");
                }
                for (Entitlement en : targetEnts.values()) {
                    entitlements.grant(c, e.id(), en.id(), Source.ROLE);
                    audit.log(c, e.id(), "GRANT_ENT", en.name(), actor, "Via role");
                }
                c.commit();
                return e;
            } catch (SoDViolationException ex) {
                c.rollback();
                logBlocked(e.id(), ex, actor);
                throw ex;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    // ------------------------------------------------------------------- MOVER

    public void mover(String id, String newDepartment, String newTitle, String actor)
            throws SQLException, EmployeeNotFoundException, SoDViolationException {
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                Employee old = employees.findById(c, id).orElseThrow(() -> new EmployeeNotFoundException(id));
                requireNotTerminated(old);
                Employee updated = old.withDepartmentAndTitle(newDepartment, newTitle);

                Set<Role> target = rules.evaluate(c, updated);
                Map<Integer, Entitlement> targetEnts = entitlementsOf(c, target);
                List<EmployeeEntitlement> held = entitlements.findForEmployee(c, id);

                // SoD on the RESULT: new role-based entitlements + any DIRECT ones we keep
                Set<String> finalNames = new HashSet<>(names(targetEnts));
                held.stream().filter(h -> h.source() == Source.DIRECT).forEach(h -> finalNames.add(h.entName()));
                sod.assertNoViolation(c, id, finalNames);

                employees.updateDepartmentAndTitle(c, id, newDepartment, newTitle);
                audit.log(c, id, "MOVER",
                        old.department() + "/" + old.jobTitle() + " -> " + newDepartment + "/" + newTitle,
                        actor, "Department or title change");

                Set<Integer> targetRoleIds = target.stream().map(Role::id).collect(Collectors.toSet());
                List<Role> current = roles.findForEmployee(c, id);
                Set<Integer> currentRoleIds = current.stream().map(Role::id).collect(Collectors.toSet());

                for (Role r : current) {
                    if (!targetRoleIds.contains(r.id())) {
                        roles.revoke(c, id, r.id());
                        audit.log(c, id, "REVOKE_ROLE", r.name(), actor, "Mover: no longer applies");
                    }
                }
                for (Role r : target) {
                    if (!currentRoleIds.contains(r.id())) {
                        roles.grant(c, id, r.id());
                        audit.log(c, id, "GRANT_ROLE", r.name(), actor, "Mover: new department/title");
                    }
                }

                Set<Integer> heldRoleEntIds = new HashSet<>();
                for (EmployeeEntitlement h : held) {
                    if (h.source() != Source.ROLE) continue;
                    heldRoleEntIds.add(h.entId());
                    if (!targetEnts.containsKey(h.entId())) {
                        entitlements.revoke(c, id, h.entId(), Source.ROLE);
                        audit.log(c, id, "REVOKE_ENT", h.entName(), actor, "Mover: no longer applies");
                    }
                }
                for (Entitlement en : targetEnts.values()) {
                    if (!heldRoleEntIds.contains(en.id())) {
                        entitlements.grant(c, id, en.id(), Source.ROLE);
                        audit.log(c, id, "GRANT_ENT", en.name(), actor, "Mover: via role");
                    }
                }
                c.commit();
            } catch (SoDViolationException ex) {
                c.rollback();
                logBlocked(id, ex, actor);
                throw ex;
            } catch (SQLException | EmployeeNotFoundException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    // ------------------------------------------------------------------ LEAVER

    public void leaver(String id, LocalDate exitedOn, String actor)
            throws SQLException, EmployeeNotFoundException {
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                employees.findById(c, id).orElseThrow(() -> new EmployeeNotFoundException(id));

                for (Role r : roles.findForEmployee(c, id)) {
                    roles.revoke(c, id, r.id());
                    audit.log(c, id, "REVOKE_ROLE", r.name(), actor, "Leaver");
                }
                for (EmployeeEntitlement h : entitlements.findForEmployee(c, id)) {
                    entitlements.revoke(c, id, h.entId(), h.source());
                    audit.log(c, id, "REVOKE_ENT", h.entName() + " (" + h.source() + ")", actor, "Leaver");
                }
                employees.markTerminated(c, id, exitedOn);   // the row is never deleted
                audit.log(c, id, "LEAVER", "TERMINATED on " + exitedOn, actor, "Employment ended");
                c.commit();
            } catch (SQLException | EmployeeNotFoundException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    // ------------------------------------------------- DIRECT (out-of-role) GRANTS

    /** Grants an entitlement outside the role model (source = DIRECT). SoD is checked first. */
    public void grantDirect(String id, String entitlementName, String actor, String reason)
            throws SQLException, EmployeeNotFoundException, SoDViolationException {
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                Employee emp = employees.findById(c, id).orElseThrow(() -> new EmployeeNotFoundException(id));
                requireNotTerminated(emp);
                Entitlement en = entitlements.findByName(c, entitlementName)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown entitlement: " + entitlementName));

                Set<String> resulting = entitlements.findForEmployee(c, id).stream()
                        .map(EmployeeEntitlement::entName).collect(Collectors.toCollection(HashSet::new));
                resulting.add(en.name());
                sod.assertNoViolation(c, id, resulting);

                entitlements.grant(c, id, en.id(), Source.DIRECT);
                audit.log(c, id, "GRANT_ENT", en.name() + " (DIRECT)", actor, reason);
                c.commit();
            } catch (SoDViolationException ex) {
                c.rollback();
                logBlocked(id, ex, actor);
                throw ex;
            } catch (SQLException | EmployeeNotFoundException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /** Remediation: removes a DIRECT entitlement. */
    public void revokeDirect(String id, String entitlementName, String actor, String reason)
            throws SQLException, EmployeeNotFoundException {
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                employees.findById(c, id).orElseThrow(() -> new EmployeeNotFoundException(id));
                Entitlement en = entitlements.findByName(c, entitlementName)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown entitlement: " + entitlementName));
                entitlements.revoke(c, id, en.id(), Source.DIRECT);
                audit.log(c, id, "REVOKE_ENT", en.name() + " (DIRECT)", actor, reason);
                c.commit();
            } catch (SQLException | EmployeeNotFoundException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    // ----------------------------------------------------------------- helpers

    private Map<Integer, Entitlement> entitlementsOf(Connection c, Set<Role> roleSet) throws SQLException {
        Map<Integer, Entitlement> map = new LinkedHashMap<>();
        for (Role r : roleSet) {
            for (Entitlement en : entitlements.findForRole(c, r.id())) {
                map.putIfAbsent(en.id(), en);
            }
        }
        return map;
    }

    private static Set<String> names(Map<Integer, Entitlement> ents) {
        Set<String> out = new LinkedHashSet<>();
        ents.values().forEach(e -> out.add(e.name()));
        return out;
    }

    private static void requireNotTerminated(Employee e) {
        if (e.status() == Status.TERMINATED) {
            throw new IllegalStateException(e.id() + " is TERMINATED; no access can be granted or changed");
        }
    }

    /** The blocked attempt is audited in its OWN transaction, after the main one was rolled back. */
    private void logBlocked(String id, SoDViolationException ex, String actor) {
        try (Connection c = connections.get()) {
            audit.log(c, id, "SOD_BLOCKED", ex.getMessage(), actor, "Grant rejected by SoD policy");
        } catch (SQLException ignored) {
            // best effort: the rollback already protected the data
        }
    }
}
