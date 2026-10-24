package com.iam;

import com.iam.cli.AppContext;
import com.iam.dao.JdbcAuditDao;
import com.iam.exception.EmployeeNotFoundException;
import com.iam.exception.SoDViolationException;
import com.iam.model.AuditEntry;
import com.iam.model.Employee;
import com.iam.model.EmployeeEntitlement;
import com.iam.model.Role;
import com.iam.model.Source;
import com.iam.model.Status;
import com.iam.util.DbConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleServiceTest {

    private AppContext app;

    @BeforeEach
    void setUp() throws Exception {
        TestDb.reset();
        app = new AppContext();
    }

    private Set<String> roleNames(String id) throws SQLException {
        try (Connection c = DbConnection.get()) {
            return app.roles.findForEmployee(c, id).stream().map(Role::name).collect(Collectors.toSet());
        }
    }

    private List<EmployeeEntitlement> ents(String id) throws SQLException {
        try (Connection c = DbConnection.get()) {
            return app.entitlements.findForEmployee(c, id);
        }
    }

    private Set<String> entNames(String id) throws SQLException {
        return ents(id).stream().map(EmployeeEntitlement::entName).collect(Collectors.toSet());
    }

    private Set<String> auditActions(String id) throws SQLException {
        try (Connection c = DbConnection.get()) {
            return app.audit.findByEmployee(c, id).stream().map(AuditEntry::action).collect(Collectors.toSet());
        }
    }

    private Employee newHire(String id, String dept, String title) {
        return new Employee(id, "Test", "User", id.toLowerCase() + "@lab-corp.test", dept, title, "S101",
                Status.ACTIVE, LocalDate.of(2026, 10, 26), null);
    }

    @Test
    void joinerGetsRolesAndEntitlementsFromRules() throws Exception {
        app.lifecycle.joiner(newHire("T001", "Engineering", "Backend Engineer"), "test");

        assertEquals(Set.of("ENG_BASE", "ENG_DEVELOPER"), roleNames("T001"));
        assertEquals(Set.of("GIT_REPO_READ", "JIRA_ACCESS", "DEVELOPER_PROD_WRITE"), entNames("T001"));
        assertTrue(ents("T001").stream().allMatch(e -> e.source() == Source.ROLE));
        assertTrue(auditActions("T001").containsAll(Set.of("JOINER", "GRANT_ROLE", "GRANT_ENT")));
    }

    @Test
    void moverSwapsOldDepartmentAccessForNew() throws Exception {
        assertTrue(entNames("S102").contains("DEVELOPER_PROD_WRITE"));

        app.lifecycle.mover("S102", "Operations", "Procurement Officer", "test");

        assertEquals(Set.of("OPS_BASE", "OPS_PROCUREMENT"), roleNames("S102"));
        assertEquals(Set.of("ERP_VIEW", "CREATE_VENDOR"), entNames("S102"));
        assertTrue(auditActions("S102").containsAll(Set.of("MOVER", "REVOKE_ROLE", "GRANT_ROLE", "REVOKE_ENT")));
        try (Connection c = DbConnection.get()) {
            Employee e = app.employees.findById(c, "S102").orElseThrow();
            assertEquals("Operations", e.department());
            assertEquals("Procurement Officer", e.jobTitle());
        }
    }

    @Test
    void leaverLosesAllAccessButRowIsKept() throws Exception {
        app.lifecycle.leaver("S105", LocalDate.of(2026, 10, 26), "test");

        assertTrue(roleNames("S105").isEmpty());
        assertTrue(ents("S105").isEmpty());
        try (Connection c = DbConnection.get()) {
            Employee e = app.employees.findById(c, "S105").orElseThrow();   // still there
            assertEquals(Status.TERMINATED, e.status());
            assertEquals(LocalDate.of(2026, 10, 26), e.exitedOn());
            assertTrue(app.finder.findAll(c).stream().noneMatch(f -> f.employeeId().equals("S105")));
        }
        assertTrue(auditActions("S105").containsAll(Set.of("LEAVER", "REVOKE_ROLE", "REVOKE_ENT")));
    }

    @Test
    void unknownEmployeeIsRejected() {
        assertThrows(EmployeeNotFoundException.class,
                () -> app.lifecycle.leaver("X999", LocalDate.now(), "test"));
        assertThrows(EmployeeNotFoundException.class,
                () -> app.lifecycle.mover("X999", "Legal", "General Counsel", "test"));
    }

    @Test
    void sodViolationBlocksTheGrantAndIsAudited() throws Exception {
        app.lifecycle.grantDirect("S403", "CREATE_VENDOR", "test", "business need");   // allowed alone

        SoDViolationException ex = assertThrows(SoDViolationException.class,
                () -> app.lifecycle.grantDirect("S403", "APPROVE_PAYMENT", "test", "should be blocked"));

        assertEquals(1, ex.getViolations().size());
        assertFalse(entNames("S403").contains("APPROVE_PAYMENT"));       // nothing was granted
        assertTrue(entNames("S403").contains("CREATE_VENDOR"));          // earlier grant is untouched
        assertTrue(auditActions("S403").contains("SOD_BLOCKED"));
    }

    @Test
    void failureMidWorkflowRollsEverythingBack() throws Exception {
        // an audit DAO that fails on its 3rd write, i.e. after the employee and first role were written
        AppContext failing = new AppContext(new JdbcAuditDao() {
            private int calls = 0;

            @Override
            public void log(Connection c, String id, String action, String target, String by, String reason)
                    throws SQLException {
                if (++calls == 3) throw new SQLException("simulated failure");
                super.log(c, id, action, target, by, reason);
            }
        });

        assertThrows(SQLException.class,
                () -> failing.lifecycle.joiner(newHire("T002", "Engineering", "Backend Engineer"), "test"));

        try (Connection c = DbConnection.get()) {
            assertTrue(app.employees.findById(c, "T002").isEmpty(), "employee row must be rolled back");
        }
        assertTrue(roleNames("T002").isEmpty());
        assertTrue(ents("T002").isEmpty());
        assertTrue(auditActions("T002").isEmpty());
    }
}
