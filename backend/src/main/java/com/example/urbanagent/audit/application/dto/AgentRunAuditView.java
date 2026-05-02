package com.example.urbanagent.audit.application.dto;

import com.example.urbanagent.agent.domain.AgentRun;

import java.time.Instant;

public record AgentRunAuditView(
        String id,
        String sessionId,
        String userId,
        String question,
        String status,
        String modelName,
        Instant createdAt,
        Instant completedAt
) {

    public static AgentRunAuditView from(AgentRun run) {
        return new AgentRunAuditView(
                run.getId(),
                run.getSessionId(),
                run.getUserId(),
                run.getQuestion(),
                run.getStatus().name(),
                run.getModelName(),
                run.getCreatedAt(),
                run.getCompletedAt()
        );
    }
}
