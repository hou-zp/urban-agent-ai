package com.example.urbanagent.audit.application.dto;

public record AuditLogQuery(
        String runId,
        String taskId,
        String toolCallId,
        String queryId,
        String evidenceId,
        int limit
) {

    public AuditLogQuery {
        limit = Math.max(1, Math.min(limit, 100));
    }
}
