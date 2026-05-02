package com.example.urbanagent.agent.application.dto;

import com.example.urbanagent.agent.domain.AgentSession;

import java.time.Instant;
import java.util.List;

public record SessionView(
        String id,
        String title,
        String status,
        Instant createdAt,
        List<MessageView> messages
) {

    public static SessionView from(AgentSession session, List<MessageView> messages) {
        return new SessionView(
                session.getId(),
                session.getTitle(),
                session.getStatus().name(),
                session.getCreatedAt(),
                messages
        );
    }
}
