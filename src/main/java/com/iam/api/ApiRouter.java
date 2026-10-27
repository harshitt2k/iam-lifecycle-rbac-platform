package com.iam.api;

import com.iam.cli.AppContext;
import com.iam.exception.EmployeeNotFoundException;
import com.iam.exception.SoDViolationException;
import com.iam.model.AccessFinding;
import com.iam.model.AuditEntry;
import com.iam.model.Employee;
import com.iam.model.EmployeeEntitlement;
import com.iam.model.Role;
import com.iam.model.Source;
import com.iam.model.SodViolation;
import com.iam.model.Status;
import com.iam.util.DbConnection;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The whole REST API as plain Java: (method, path, query, body) in, ApiResponse out.
 * The servlet is a thin adapter, so every endpoint can be tested without a web server.
 */
public class ApiRouter {

    private static final String JSON = "application/json; charset=UTF-8";

    private final AppContext app;

    public ApiRouter(AppContext app) {
        this.app = app;
    }

    public ApiResponse handle(String method, String path, Map<String, String> query, String actorHeader, String body) {
        String actor = cleanActor(actorHeader);
        Map<String, String> q = query == null ? Map.of() : query;
        try {
            return route(method == null ? "" : method.toUpperCase(Locale.ROOT),
                    segments(path), q, actor, body == null ? "" : body);
        } catch (EmployeeNotFoundException e) {
            return error(404, e.getMessage());
        } catch (SoDViolationException e) {
            return json(409, obj("error", e.getMessage(), "violations", e.getViolations()));
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return error(409, e.getMessage());
        } catch (SQLException e) {
            return sqlError(e);
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // DbConnection could not read its config file: -Diam.config is missing or wrong
            e.printStackTrace();
            return error(500, "Server configuration error: cannot read the database config "
                    + "(is -Diam.config set to the full path of config.properties?)");
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, "Unexpected server error");
        }
    }

    // ------------------------------------------------------------------ routing

    private ApiResponse route(String m, String[] p, Map<String, String> q, String actor, String body)
            throws Exception {
        if (p.length == 0) {
            return json(200, obj("name", "IAM Lifecycle & RBAC API", "endpoints", List.of(
                    "GET  /health",
                    "GET  /employees",
                    "POST /employees",
                    "GET  /employees/{id}",
                    "POST /employees/{id}/move",
                    "POST /employees/{id}/leave",
                    "POST /employees/{id}/entitlements",
                    "DELETE /employees/{id}/entitlements/{name}",
                    "GET  /reports/access[?format=json]",
                    "GET  /reports/sod",
                    "GET  /reports/excessive",
                    "GET  /audit[?employeeId=&limit=]")));
        }
        switch (p[0]) {
            case "health":
                if (p.length != 1) break;
                if (!m.equals("GET")) return methodNotAllowed("GET");
                return json(200, obj("status", "ok"));
            case "employees":
                return employees(m, p, q, actor, body);
            case "reports":
                if (p.length != 2) break;
                if (!m.equals("GET")) return methodNotAllowed("GET");
                return reports(p[1], q);
            case "audit":
                if (p.length != 1) break;
                if (!m.equals("GET")) return methodNotAllowed("GET");
                return audit(q);
            default:
                break;
        }
        return error(404, "No such endpoint: /" + String.join("/", p));
    }

    private ApiResponse employees(String m, String[] p, Map<String, String> q, String actor, String body)
            throws Exception {
        if (p.length == 1) {
            switch (m) {
                case "GET":
                    try (Connection c = DbConnection.get()) {
                        return json(200, app.employees.findAll(c));
                    }
                case "POST":
                    return joiner(body, actor);
                default:
                    return methodNotAllowed("GET, POST");
            }
        }
        String id = p[1];
        if (p.length == 2) {
            if (!m.equals("GET")) return methodNotAllowed("GET");
            return json(200, detail(id));
        }
        switch (p[2]) {
            case "move":
                if (p.length != 3) break;
                if (!m.equals("POST")) return methodNotAllowed("POST");
                return move(id, body, actor);
            case "leave":
                if (p.length != 3) break;
                if (!m.equals("POST")) return methodNotAllowed("POST");
                return leave(id, body, actor);
            case "entitlements":
                if (p.length == 3) {
                    if (!m.equals("POST")) return methodNotAllowed("POST");
                    return grant(id, body, actor);
                }
                if (p.length == 4) {
                    if (!m.equals("DELETE")) return methodNotAllowed("DELETE");
                    return revoke(id, p[3], q, actor);
                }
                break;
            default:
                break;
        }
        return error(404, "No such endpoint: /" + String.join("/", p));
    }

    // ---------------------------------------------------------------- employees

    private ApiResponse joiner(String body, String actor) throws Exception {
        Map<String, Object> b = parseBody(body);
        String id = required(b, "employeeId");
        if (!id.matches("[A-Za-z0-9_-]{1,10}")) {
            throw new IllegalArgumentException("employeeId must be 1-10 letters, digits, '-' or '_'");
        }
        String email = required(b, "email");
        if (!email.contains("@")) {
            throw new IllegalArgumentException("email is not valid");
        }
        Employee e = new Employee(id, required(b, "givenName"), required(b, "familyName"), email,
                required(b, "department"), required(b, "jobTitle"), optional(b, "supervisorId"),
                Status.ACTIVE, optionalDate(b, "joinedOn", LocalDate.now()), null);
        app.lifecycle.joiner(e, actor);
        return json(201, detail(id));
    }

    private ApiResponse move(String id, String body, String actor) throws Exception {
        Map<String, Object> b = parseBody(body);
        app.lifecycle.mover(id, required(b, "department"), required(b, "jobTitle"), actor);
        return json(200, detail(id));
    }

    private ApiResponse leave(String id, String body, String actor) throws Exception {
        Map<String, Object> b = parseBody(body);
        app.lifecycle.leaver(id, optionalDate(b, "exitedOn", LocalDate.now()), actor);
        return json(200, detail(id));
    }

    private ApiResponse grant(String id, String body, String actor) throws Exception {
        Map<String, Object> b = parseBody(body);
        String reason = optional(b, "reason");
        app.lifecycle.grantDirect(id, required(b, "entitlement"), actor, reason == null ? "API request" : reason);
        return json(201, detail(id));
    }

    private ApiResponse revoke(String id, String entitlement, Map<String, String> q, String actor) throws Exception {
        try (Connection c = DbConnection.get()) {
            app.employees.findById(c, id).orElseThrow(() -> new EmployeeNotFoundException(id));
            boolean heldDirect = app.entitlements.findForEmployee(c, id).stream()
                    .anyMatch(h -> h.source() == Source.DIRECT && h.entName().equals(entitlement));
            if (!heldDirect) {
                return error(404, id + " has no DIRECT entitlement " + entitlement);
            }
        }
        String reason = q.get("reason");
        app.lifecycle.revokeDirect(id, entitlement, actor,
                reason == null || reason.isBlank() ? "API request" : reason);
        return json(200, detail(id));
    }

    private Map<String, Object> detail(String id) throws SQLException, EmployeeNotFoundException {
        try (Connection c = DbConnection.get()) {
            Employee e = app.employees.findById(c, id).orElseThrow(() -> new EmployeeNotFoundException(id));
            List<String> roles = new ArrayList<>();
            for (Role r : app.roles.findForEmployee(c, id)) roles.add(r.name());
            List<Map<String, Object>> ents = new ArrayList<>();
            for (EmployeeEntitlement h : app.entitlements.findForEmployee(c, id)) {
                ents.add(obj("name", h.entName(), "source", h.source()));
            }
            return obj("employee", e, "roles", roles, "entitlements", ents);
        }
    }

    // ------------------------------------------------------------------ reports

    private ApiResponse reports(String name, Map<String, String> q) throws Exception {
        try (Connection c = DbConnection.get()) {
            switch (name) {
                case "access":
                    if ("json".equalsIgnoreCase(q.get("format"))) {
                        List<Map<String, Object>> rows = new ArrayList<>();
                        for (Employee e : app.employees.findAll(c)) {
                            List<String> roles = new ArrayList<>();
                            for (Role r : app.roles.findForEmployee(c, e.id())) roles.add(r.name());
                            List<Map<String, Object>> ents = new ArrayList<>();
                            for (EmployeeEntitlement h : app.entitlements.findForEmployee(c, e.id())) {
                                ents.add(obj("name", h.entName(), "source", h.source()));
                            }
                            rows.add(obj("employeeId", e.id(), "name", e.fullName(), "department", e.department(),
                                    "jobTitle", e.jobTitle(), "status", e.status(),
                                    "roles", roles, "entitlements", ents));
                        }
                        return json(200, rows);
                    }
                    StringWriter w = new StringWriter();
                    app.reports.writeAccessCsv(c, w);
                    return new ApiResponse(200, "text/csv; charset=UTF-8", w.toString());
                case "sod":
                    List<SodViolation> v = app.sod.scanAll(c);
                    return json(200, obj("count", v.size(), "violations", v));
                case "excessive":
                    List<AccessFinding> f = app.finder.findAll(c);
                    return json(200, obj("count", f.size(), "findings", f));
                default:
                    return error(404, "No such report: " + name);
            }
        }
    }

    private ApiResponse audit(Map<String, String> q) throws Exception {
        int limit = 20;
        if (q.get("limit") != null) {
            try {
                limit = Integer.parseInt(q.get("limit").trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("limit must be a number");
            }
            limit = Math.max(1, Math.min(500, limit));
        }
        String employeeId = q.get("employeeId");
        try (Connection c = DbConnection.get()) {
            List<AuditEntry> entries = employeeId == null || employeeId.isBlank()
                    ? app.audit.findRecent(c, limit)
                    : app.audit.findByEmployee(c, employeeId.trim());
            return json(200, obj("count", entries.size(), "entries", entries));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String[] segments(String path) {
        if (path == null) return new String[0];
        List<String> out = new ArrayList<>();
        for (String s : path.split("/")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out.toArray(new String[0]);
    }

    private static String cleanActor(String header) {
        if (header == null || header.isBlank()) return "api";
        String a = header.trim();
        return a.length() > 60 ? a.substring(0, 60) : a;
    }

    private static Map<String, Object> parseBody(String body) {
        return body.isBlank() ? new LinkedHashMap<>() : Json.parseObject(body);
    }

    private static String required(Map<String, Object> b, String key) {
        String v = optional(b, key);
        if (v == null) throw new IllegalArgumentException("Missing field: " + key);
        return v;
    }

    private static String optional(Map<String, Object> b, String key) {
        Object v = b.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDate optionalDate(Map<String, Object> b, String key, LocalDate fallback) {
        String s = optional(b, key);
        if (s == null) return fallback;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(key + " must be a date like 2026-10-26");
        }
    }

    private static Map<String, Object> obj(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    private static ApiResponse json(int status, Object body) {
        return new ApiResponse(status, JSON, Json.write(body));
    }

    private static ApiResponse error(int status, String message) {
        return json(status, obj("error", message));
    }

    private static ApiResponse methodNotAllowed(String allow) {
        return new ApiResponse(405, JSON, Json.write(obj("error", "Method not allowed; use " + allow)), allow);
    }

    private static ApiResponse sqlError(SQLException e) {
        switch (e.getErrorCode()) {
            case 1062:
                return error(409, "Already exists (duplicate employee id or email)");
            case 1048:
            case 1452:
                return error(400, "Unknown department or supervisor");
            default:
                e.printStackTrace();
                return error(500, "Database error");
        }
    }
}
