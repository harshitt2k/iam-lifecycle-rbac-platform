package com.iam;

import com.iam.cli.AppContext;
import com.iam.model.AccessFinding;
import com.iam.model.FindingType;
import com.iam.model.SodViolation;
import com.iam.util.DbConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DetectionTest {

    private AppContext app;

    @BeforeEach
    void setUp() throws Exception {
        TestDb.reset();
        app = new AppContext();
    }

    @Test
    void fullScanFindsExactlyTheSeededSodViolations() throws Exception {
        try (Connection c = DbConnection.get()) {
            List<SodViolation> v = app.sod.scanAll(c);
            assertEquals(Set.of("S402", "S103"),
                    v.stream().map(SodViolation::employeeId).collect(Collectors.toSet()));
            assertEquals(2, v.size());
        }
    }

    @Test
    void excessiveAccessFinderFlagsDirectGrantsAndTerminatedStaff() throws Exception {
        try (Connection c = DbConnection.get()) {
            List<AccessFinding> f = app.finder.findAll(c);

            Set<String> direct = f.stream().filter(x -> x.type() == FindingType.DIRECT_ACCESS)
                    .map(x -> x.employeeId() + ":" + x.detail()).collect(Collectors.toSet());
            assertEquals(Set.of("S304:ESCALATION_QUEUE", "S402:APPROVE_PAYMENT",
                    "S103:PROD_DEPLOY_APPROVER", "S305:CI_PIPELINE_ADMIN"), direct);

            Set<String> terminated = f.stream().filter(x -> x.type() == FindingType.TERMINATED_WITH_ACCESS)
                    .map(AccessFinding::employeeId).collect(Collectors.toSet());
            assertEquals(Set.of("S106", "S304"), terminated);

            assertTrue(f.stream().noneMatch(x -> x.employeeId().equals("S102")), "S102 is clean");
        }
    }

    @Test
    void revokingTheDirectGrantClearsTheFinding() throws Exception {
        app.lifecycle.revokeDirect("S305", "CI_PIPELINE_ADMIN", "test", "remediation");
        try (Connection c = DbConnection.get()) {
            assertTrue(app.finder.findAll(c).stream().noneMatch(x -> x.employeeId().equals("S305")));
        }
    }

    @Test
    void accessCsvListsEveryEmployee() throws Exception {
        StringWriter out = new StringWriter();
        try (Connection c = DbConnection.get()) {
            app.reports.writeAccessCsv(c, out);
        }
        String csv = out.toString();
        assertTrue(csv.startsWith("employee_id,name,department,job_title,status,roles,entitlement,source"));
        for (String id : List.of("S101", "S106", "S304", "S405")) {
            assertTrue(csv.contains(id + ","), id + " missing from report");
        }
        assertTrue(csv.contains("S402,Kavya Rajan,Operations,Procurement Officer,ACTIVE,OPS_BASE;OPS_PROCUREMENT,APPROVE_PAYMENT,DIRECT"));
    }
}
