package com.example.urbanagent.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String actionType;

    @Column(nullable = false, length = 64)
    private String resourceType;

    @Column(length = 64)
    private String resourceId;

    @Column(length = 64)
    private String sessionId;

    @Column(length = 64)
    private String runId;

    @Column(length = 64)
    private String taskId;

    @Column(length = 64)
    private String toolCallId;

    @Column(length = 64)
    private String queryId;

    @Column(length = 64)
    private String evidenceId;

    @Column(length = 64)
    private String riskEventId;

    @Column(columnDefinition = "text")
    private String sqlSummary;

    @Column(length = 32)
    private String riskLevel;

    @Column(length = 32)
    private String status;

    private Long durationMs;

    @Column(columnDefinition = "text")
    private String detailJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(String userId,
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
                    String detailJson) {
        this.userId = userId;
        this.actionType = actionType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.sessionId = sessionId;
        this.runId = runId;
        this.taskId = taskId;
        this.toolCallId = toolCallId;
        this.queryId = queryId;
        this.evidenceId = evidenceId;
        this.riskEventId = riskEventId;
        this.sqlSummary = sqlSummary;
        this.riskLevel = riskLevel;
        this.status = status;
        this.durationMs = durationMs;
        this.detailJson = detailJson;
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getActionType() {
        return actionType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getQueryId() {
        return queryId;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public String getRiskEventId() {
        return riskEventId;
    }

    public String getSqlSummary() {
        return sqlSummary;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getStatus() {
        return status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
