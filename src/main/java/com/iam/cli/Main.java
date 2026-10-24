package com.iam.cli;

import com.iam.exception.EmployeeNotFoundException;
import com.iam.exception.SoDViolationException;
import com.iam.model.AccessFinding;
import com.iam.model.AuditEntry;
import com.iam.model.Employee;
import com.iam.model.SodViolation;
import com.iam.model.Status;
import com.iam.util.DbConnection;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/** Menu-driven console app for the lifecycle platform. */
public class Main {

    private static final String ACTOR = "admin.cli";

    private final AppContext app = new AppContext();
    private final Scanner in = new Scanner(System.in);

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        while (true) {
            printMenu();
            String choice = ask("Choose");
            try {
                switch (choice) {
                    case "1" -> listEmployees();
                    case "2" -> viewEmployee();
                    case "3" -> joiner();
                    case "4" -> mover();
                    case "5" -> leaver();
                    case "6" -> grantDirect();
                    case "7" -> revokeDirect();
                    case "8" -> sodScan();
                    case "9" -> excessiveScan();
                    case "10" -> exportCsv();
                    case "11" -> showAudit();
                    case "0" -> {
                        System.out.println("Bye.");
                        return;
                    }
                    default -> System.out.println("Unknown option.");
                }
            } catch (EmployeeNotFoundException | SoDViolationException e) {
                System.out.println("REFUSED: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("DATABASE ERROR (nothing was changed): " + e.getMessage());
            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println("""

                ===== IAM Lifecycle & RBAC Platform =====
                 1  List employees
                 2  View one employee's access
                 3  Joiner   (hire)
                 4  Mover    (department / title change)
                 5  Leaver   (terminate)
                 6  Grant a DIRECT entitlement (SoD-checked)
                 7  Revoke a DIRECT entitlement
                 8  Scan: SoD violations
                 9  Scan: excessive access and terminated-with-access
                10  Export "who has access to what" (CSV)
                11  Audit log
                 0  Exit""");
    }

    private void listEmployees() throws SQLException {
        try (Connection c = DbConnection.get()) {
            for (Employee e : app.employees.findAll(c)) {
                System.out.printf("%-5s %-18s %-12s %-24s %s%n",
                        e.id(), e.fullName(), e.department(), e.jobTitle(), e.status());
            }
        }
    }

    private void viewEmployee() throws SQLException {
        String id = ask("Employee id");
        try (Connection c = DbConnection.get()) {
            System.out.println(app.reports.employeeView(c, id).orElse("Employee not found: " + id));
        }
    }

    private void joiner() throws SQLException, SoDViolationException {
        System.out.println("Departments: Engineering, Legal, Operations, Support");
        String sup = ask("Supervisor id (blank for none)");
        Employee e = new Employee(
                ask("New employee id (e.g. S106x)"), ask("Given name"), ask("Family name"), ask("Email"),
                ask("Department"), ask("Job title"), sup.isBlank() ? null : sup,
                Status.ACTIVE, LocalDate.now(), null);
        app.lifecycle.joiner(e, ACTOR);
        System.out.println("Joiner complete.");
        viewQuiet(e.id());
    }

    private void mover() throws SQLException, EmployeeNotFoundException, SoDViolationException {
        String id = ask("Employee id");
        app.lifecycle.mover(id, ask("New department"), ask("New job title"), ACTOR);
        System.out.println("Mover complete.");
        viewQuiet(id);
    }

    private void leaver() throws SQLException, EmployeeNotFoundException {
        String id = ask("Employee id");
        if (!ask("Type YES to terminate " + id).equals("YES")) {
            System.out.println("Cancelled.");
            return;
        }
        app.lifecycle.leaver(id, LocalDate.now(), ACTOR);
        System.out.println("Leaver complete: all access revoked, status TERMINATED.");
        viewQuiet(id);
    }

    private void grantDirect() throws SQLException, EmployeeNotFoundException, SoDViolationException {
        String id = ask("Employee id");
        app.lifecycle.grantDirect(id, ask("Entitlement name"), ACTOR, ask("Reason"));
        System.out.println("Granted.");
        viewQuiet(id);
    }

    private void revokeDirect() throws SQLException, EmployeeNotFoundException {
        String id = ask("Employee id");
        app.lifecycle.revokeDirect(id, ask("Entitlement name"), ACTOR, ask("Reason"));
        System.out.println("Revoked.");
        viewQuiet(id);
    }

    private void sodScan() throws SQLException {
        try (Connection c = DbConnection.get()) {
            List<SodViolation> v = app.sod.scanAll(c);
            System.out.println(v.size() + " SoD violation(s)");
            for (SodViolation x : v) {
                System.out.printf("  %-5s %s + %s  (%s)%n",
                        x.employeeId(), x.entitlementA(), x.entitlementB(), x.description());
            }
        }
    }

    private void excessiveScan() throws SQLException {
        try (Connection c = DbConnection.get()) {
            List<AccessFinding> f = app.finder.findAll(c);
            System.out.println(f.size() + " finding(s)");
            for (AccessFinding x : f) {
                System.out.printf("  %-5s %-24s %s%n", x.employeeId(), x.type(), x.detail());
            }
        }
    }

    private void exportCsv() throws SQLException, IOException {
        Path file = Path.of("reports", "access-report.csv");
        Files.createDirectories(file.getParent());
        try (Connection c = DbConnection.get(); Writer w = Files.newBufferedWriter(file)) {
            app.reports.writeAccessCsv(c, w);
        }
        System.out.println("Written: " + file.toAbsolutePath());
    }

    private void showAudit() throws SQLException {
        String id = ask("Employee id (blank = latest 20 entries)");
        try (Connection c = DbConnection.get()) {
            List<AuditEntry> list = id.isBlank() ? app.audit.findRecent(c, 20) : app.audit.findByEmployee(c, id);
            for (AuditEntry a : list) {
                System.out.printf("%5d  %s  %-5s %-12s %-40s by %s  [%s]%n", a.id(), a.timestamp(),
                        a.employeeId(), a.action(), a.target(), a.performedBy(), a.reason());
            }
        }
    }

    private void viewQuiet(String id) throws SQLException {
        try (Connection c = DbConnection.get()) {
            app.reports.employeeView(c, id).ifPresent(System.out::println);
        }
    }

    private String ask(String prompt) {
        System.out.print(prompt + ": ");
        return in.hasNextLine() ? in.nextLine().trim() : "0";
    }
}
