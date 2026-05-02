package com.example.urbanagent.audit.application.dto;

import com.example.urbanagent.query.domain.QueryRecord;

import java.time.Instant;

public record QueryRecordAuditView(
        String id,
        String userId,
        String question,
        String candidateSql,
        String executedSqlSummary,
        String permissionRewrite,
        String resultSummary,
        String status,
        Instant createdAt
) {

    public static QueryRecordAuditView from(QueryRecord record) {
        return new QueryRecordAuditView(
                record.getId(),
                record.getUserId(),
                record.getQuestion(),
                record.getCandidateSql(),
                record.getExecutedSqlSummary(),
                record.getPermissionRewrite(),
                record.getResultSummary(),
                record.getStatus().name(),
                record.getCreatedAt()
        );
    }
}
