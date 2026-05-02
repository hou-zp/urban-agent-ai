package com.example.urbanagent.query.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_record")
public class QueryRecord {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(columnDefinition = "text")
    private String candidateSql;

    @Column(columnDefinition = "text")
    private String executedSqlSummary;

    @Column(columnDefinition = "text")
    private String permissionRewrite;

    @Column(columnDefinition = "text")
    private String resultSummary;

    @Column(length = 64)
    private String metricCode;

    @Column(length = 120)
    private String metricName;

    @Column(length = 255)
    private String sourceSummary;

    @Column(columnDefinition = "text")
    private String scopeSummary;

    @Column
    private Instant dataUpdatedAt;

    @Column(length = 64)
    private String caliberVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QueryRecordStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected QueryRecord() {
    }

    public QueryRecord(String userId, String question, QueryRecordStatus status) {
        this.userId = userId;
        this.question = question;
        this.status = status;
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

    public void markPreview(String candidateSql, String permissionRewrite, String resultSummary) {
        this.candidateSql = candidateSql;
        this.permissionRewrite = permissionRewrite;
        this.resultSummary = resultSummary;
        this.status = QueryRecordStatus.PREVIEWED;
    }

    public void markExecuted(String candidateSql, String executedSqlSummary, String permissionRewrite, String resultSummary) {
        this.candidateSql = candidateSql;
        this.executedSqlSummary = executedSqlSummary;
        this.permissionRewrite = permissionRewrite;
        this.resultSummary = resultSummary;
        this.status = QueryRecordStatus.EXECUTED;
    }

    public void applyStatementMetadata(String metricCode,
                                       String metricName,
                                       String sourceSummary,
                                       String scopeSummary,
                                       Instant dataUpdatedAt,
                                       String caliberVersion) {
        this.metricCode = metricCode;
        this.metricName = metricName;
        this.sourceSummary = sourceSummary;
        this.scopeSummary = scopeSummary;
        this.dataUpdatedAt = dataUpdatedAt;
        this.caliberVersion = caliberVersion;
    }

    public void markRejected(String candidateSql, String resultSummary) {
        this.candidateSql = candidateSql;
        this.resultSummary = resultSummary;
        this.status = QueryRecordStatus.REJECTED;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getQuestion() {
        return question;
    }

    public String getCandidateSql() {
        return candidateSql;
    }

    public String getExecutedSqlSummary() {
        return executedSqlSummary;
    }

    public String getPermissionRewrite() {
        return permissionRewrite;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public QueryRecordStatus getStatus() {
        return status;
    }

    public String getMetricCode() {
        return metricCode;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getSourceSummary() {
        return sourceSummary;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public Instant getDataUpdatedAt() {
        return dataUpdatedAt;
    }

    public String getCaliberVersion() {
        return caliberVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
