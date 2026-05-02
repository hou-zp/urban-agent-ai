package com.example.urbanagent.audit.application.dto;

import com.example.urbanagent.agent.domain.ToolCall;

import java.time.Instant;

public record ToolCallAuditView(
        String id,
        String runId,
        String toolName,
        String inputSummary,
        String outputSummary,
        Instant createdAt
) {

    public static ToolCallAuditView from(ToolCall toolCall) {
        return new ToolCallAuditView(
                toolCall.getId(),
                toolCall.getRunId(),
                toolCall.getToolName(),
                toolCall.getInputSummary(),
                toolCall.getOutputSummary(),
                toolCall.getCreatedAt()
        );
    }
}
