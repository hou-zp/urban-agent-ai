package com.example.urbanagent.agent.application.dto;

import java.time.Instant;
import java.util.List;

public record MessageView(
        String id,
        String role,
        String content,
        List<MessageCitationView> citations,
        ComposedAnswer composedAnswer,
        String riskLevel,
        String reviewId,
        String reviewStatus,
        Instant createdAt,
        String runId,
        String planId
) {
}
