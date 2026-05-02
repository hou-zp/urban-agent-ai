package com.example.urbanagent.audit.application.dto;

import com.example.urbanagent.audit.domain.AuditLog;

import java.time.Instant;

public record AuditLogView(
        String id,
        String userId,
        String actionType,
        String resourceType,
        String resourceId,
        String sessionId,
        String runId,
        String taskId,
        String toolCallId,
        String queryId,
        String evidenceId,
        String riskEventId,
        String sqlSummary,
        String riskLevel,
        String status,
        Long durationMs,
        String detailJson,
        Instant createdAt
) {

    public static AuditLogView from(AuditLog auditLog) {
        return new AuditLogView(
                auditLog.getId(),
                auditLog.getUserId(),
                auditLog.getActionType(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getSessionId(),
                auditLog.getRunId(),
                auditLog.getTaskId(),
                auditLog.getToolCallId(),
                auditLog.getQueryId(),
                auditLog.getEvidenceId(),
                auditLog.getRiskEventId(),
                auditLog.getSqlSummary(),
                auditLog.getRiskLevel(),
                auditLog.getStatus(),
                auditLog.getDurationMs(),
                auditLog.getDetailJson(),
                auditLog.getCreatedAt()
        );
    }
}
