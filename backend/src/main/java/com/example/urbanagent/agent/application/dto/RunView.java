package com.example.urbanagent.agent.application.dto;

import com.example.urbanagent.agent.domain.AgentRun;

import java.time.Instant;

public record RunView(
        String id,
        String sessionId,
        String userId,
        String question,
        String status,
        String modelName,
        Instant createdAt,
        Instant completedAt
) {

    public static RunView from(AgentRun run) {
        return new RunView(
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
