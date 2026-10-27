package com.iam;

import com.iam.api.ApiResponse;
import com.iam.api.ApiRouter;
import com.iam.api.Json;
import com.iam.cli.AppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises every endpoint through ApiRouter against the *_test database (no web server needed). */
class ApiRouterTest {

    private ApiRouter api;

    @BeforeEach
    void setUp() throws Exception {
        TestDb.reset();
        api = new ApiRouter(new AppContext());
    }

    private ApiResponse call(String method, String path, String body) {
        return api.handle(method, path, Map.of(), "test", body);
    }

    private ApiResponse call(String method, String path, Map<String, String> query) {
        return api.handle(method, path, query, "test", "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(ApiResponse r) {
        return Json.parseObject(r.body());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private static final String NEW_HIRE = "{\"employeeId\":\"T100\",\"givenName\":\"Asha\",\"familyName\":\"Rao\","
            + "\"email\":\"asha.rao@lab-corp.test\",\"department\":\"Engineering\",\"jobTitle\":\"Backend Engineer\","
            + "\"supervisorId\":\"S101\"}";

    @Test
    void healthAndIndexNeedNoData() {
        assertEquals(200, call("GET", "/health", "").status());
        assertEquals("ok", obj(call("GET", "/health", "")).get("status"));
        assertEquals(200, call("GET", "/", "").status());
        assertEquals(200, call("GET", null, "").status());
    }

    @Test
    void unknownRoutesAndMethods() {
        assertEquals(404, call("GET", "/nothing", "").status());
        assertEquals(404, call("GET", "/employees/S101/dance", "").status());
        ApiResponse r = call("DELETE", "/employees", "");
        assertEquals(405, r.status());
        assertEquals("GET, POST", r.allow());
        assertEquals(405, call("POST", "/health", "").status());
    }

    @Test
    void listsAndShowsEmployees() {
        ApiResponse all = call("GET", "/employees", "");
        assertEquals(200, all.status());
        assertEquals(20, list(Json.parse(all.body())).size());

        Map<String, Object> d = obj(call("GET", "/employees/S402", ""));
        assertEquals("S402", map(d.get("employee")).get("id"));
        assertTrue(list(d.get("roles")).contains("OPS_PROCUREMENT"));
        boolean direct = list(d.get("entitlements")).stream()
                .map(x -> map(x))
                .anyMatch(e -> "APPROVE_PAYMENT".equals(e.get("name")) && "DIRECT".equals(e.get("source")));
        assertTrue(direct);

        assertEquals(404, call("GET", "/employees/X999", "").status());
    }

    @Test
    void joinerCreatesEmployeeWithRuleBasedAccess() {
        ApiResponse r = call("POST", "/employees", NEW_HIRE);
        assertEquals(201, r.status(), r.body());
        Map<String, Object> d = obj(r);
        assertEquals(Set.of("ENG_BASE", "ENG_DEVELOPER"), new HashSet<>(list(d.get("roles"))));
        assertEquals(3, list(d.get("entitlements")).size());

        assertEquals(409, call("POST", "/employees", NEW_HIRE).status());   // same id and email again
    }

    @Test
    void joinerValidatesInput() {
        assertEquals(400, call("POST", "/employees", "{not json").status());
        assertEquals(400, call("POST", "/employees", "{\"employeeId\":\"T100\"}").status());
        assertEquals(400, call("POST", "/employees", NEW_HIRE.replace("asha.rao@lab-corp.test", "nope")).status());
        assertEquals(400, call("POST", "/employees", NEW_HIRE.replace("T100", "WAY_TOO_LONG_ID")).status());

        ApiResponse badDept = call("POST", "/employees", NEW_HIRE.replace("Engineering", "Piracy"));
        assertEquals(400, badDept.status(), badDept.body());
        assertEquals(404, call("GET", "/employees/T100", "").status());      // nothing was created
    }

    @Test
    void moverChangesDepartmentAndAccess() {
        ApiResponse r = call("POST", "/employees/S102/move", "{\"department\":\"Operations\",\"jobTitle\":\"Procurement Officer\"}");
        assertEquals(200, r.status(), r.body());
        Map<String, Object> d = obj(r);
        assertEquals(Set.of("OPS_BASE", "OPS_PROCUREMENT"), new HashSet<>(list(d.get("roles"))));
        assertEquals("Operations", map(d.get("employee")).get("department"));

        assertEquals(404, call("POST", "/employees/X999/move", "{\"department\":\"Legal\",\"jobTitle\":\"Legal Associate\"}").status());
        assertEquals(400, call("POST", "/employees/S102/move", "{\"department\":\"Legal\"}").status());
        assertEquals(400, call("POST", "/employees/S102/move", "{\"department\":\"Piracy\",\"jobTitle\":\"Captain\"}").status());
    }

    @Test
    void leaverRemovesAllAccess() {
        ApiResponse r = call("POST", "/employees/S105/leave", "{\"exitedOn\":\"2026-10-26\"}");
        assertEquals(200, r.status(), r.body());
        Map<String, Object> d = obj(r);
        assertEquals("TERMINATED", map(d.get("employee")).get("status"));
        assertEquals("2026-10-26", map(d.get("employee")).get("exitedOn"));
        assertTrue(list(d.get("roles")).isEmpty());
        assertTrue(list(d.get("entitlements")).isEmpty());

        assertEquals(400, call("POST", "/employees/S104/leave", "{\"exitedOn\":\"tomorrow\"}").status());
        assertEquals(404, call("POST", "/employees/X999/leave", "").status());
    }

    @Test
    void directGrantRevokeAndSodBlock() {
        ApiResponse ok = call("POST", "/employees/S403/entitlements", "{\"entitlement\":\"CREATE_VENDOR\",\"reason\":\"cover\"}");
        assertEquals(201, ok.status(), ok.body());

        ApiResponse blocked = call("POST", "/employees/S403/entitlements", "{\"entitlement\":\"APPROVE_PAYMENT\"}");
        assertEquals(409, blocked.status());
        assertEquals(1, list(obj(blocked).get("violations")).size());
        Map<String, Object> audit = obj(call("GET", "/audit", Map.of("employeeId", "S403")));
        assertTrue(list(audit.get("entries")).stream().anyMatch(e -> "SOD_BLOCKED".equals(map(e).get("action"))));

        assertEquals(400, call("POST", "/employees/S403/entitlements", "{\"entitlement\":\"NOPE\"}").status());
        assertEquals(400, call("POST", "/employees/S403/entitlements", "{}").status());
        assertEquals(409, call("POST", "/employees/S106/entitlements", "{\"entitlement\":\"ERP_VIEW\"}").status()); // terminated

        ApiResponse gone = api.handle("DELETE", "/employees/S403/entitlements/CREATE_VENDOR",
                Map.of("reason", "done"), "test", "");
        assertEquals(200, gone.status(), gone.body());
        assertEquals(404, call("DELETE", "/employees/S403/entitlements/CREATE_VENDOR", "").status()); // already gone
        assertEquals(404, call("DELETE", "/employees/S403/entitlements/ERP_VIEW", "").status());      // ROLE, not DIRECT
    }

    @Test
    void reportsMatchTheSeededProblems() {
        Map<String, Object> sod = obj(call("GET", "/reports/sod", ""));
        assertEquals(2L, sod.get("count"));

        Map<String, Object> ex = obj(call("GET", "/reports/excessive", ""));
        assertEquals(6L, ex.get("count"));

        ApiResponse csv = call("GET", "/reports/access", "");
        assertEquals(200, csv.status());
        assertTrue(csv.contentType().startsWith("text/csv"));
        assertTrue(csv.body().startsWith("employee_id,name,department,job_title,status,roles,entitlement,source"));
        assertTrue(csv.body().contains("S402,Kavya Rajan,Operations,Procurement Officer,ACTIVE,OPS_BASE;OPS_PROCUREMENT,APPROVE_PAYMENT,DIRECT"));

        ApiResponse js = call("GET", "/reports/access", Map.of("format", "json"));
        assertEquals(20, list(Json.parse(js.body())).size());

        assertEquals(404, call("GET", "/reports/nothing", "").status());
    }

    @Test
    void auditFiltersAndLimits() {
        call("POST", "/employees/S105/leave", "");
        Map<String, Object> mine = obj(call("GET", "/audit", Map.of("employeeId", "S105")));
        assertTrue(list(mine.get("entries")).stream().anyMatch(e -> "LEAVER".equals(map(e).get("action"))));

        Map<String, Object> recent = obj(call("GET", "/audit", Map.of("limit", "3")));
        assertEquals(3L, recent.get("count"));
        assertEquals(400, call("GET", "/audit", Map.of("limit", "many")).status());
    }
}
