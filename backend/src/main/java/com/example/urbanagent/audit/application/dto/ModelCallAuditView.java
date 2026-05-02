package com.example.urbanagent.audit.application.dto;

import com.example.urbanagent.ai.domain.ModelCallRecord;

import java.time.Instant;

public record ModelCallAuditView(
        String id,
        String runId,
        String userId,
        String provider,
        String modelName,
        String operation,
        String status,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {

    public static ModelCallAuditView from(ModelCallRecord record) {
        return new ModelCallAuditView(
                record.getId(),
                record.getRunId(),
                record.getUserId(),
                record.getProvider(),
                record.getModelName(),
                record.getOperation(),
                record.getStatus(),
                record.getPromptTokens(),
                record.getCompletionTokens(),
                record.getTotalTokens(),
                record.getLatencyMs(),
                record.getErrorCode(),
                record.getErrorMessage(),
                record.getCreatedAt()
        );
    }
}
