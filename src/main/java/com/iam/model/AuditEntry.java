package com.iam.model;

import java.time.LocalDateTime;

public record AuditEntry(long id, String employeeId, String action, String target,
                         String performedBy, LocalDateTime timestamp, String reason) { }
