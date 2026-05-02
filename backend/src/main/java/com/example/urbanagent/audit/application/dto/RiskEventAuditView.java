package com.example.urbanagent.audit.application.dto;

import com.example.urbanagent.risk.domain.RiskEvent;

import java.time.Instant;

public record RiskEventAuditView(
        String id,
        String runId,
        String sessionId,
        String userId,
        String question,
        String riskLevel,
        String riskCategories,
        String triggerReason,
        boolean reviewRequired,
        Instant createdAt
) {

    public static RiskEventAuditView from(RiskEvent event) {
        return new RiskEventAuditView(
                event.getId(),
                event.getRunId(),
                event.getSessionId(),
                event.getUserId(),
                event.getQuestion(),
                event.getRiskLevel().name(),
                event.getRiskCategories(),
                event.getTriggerReason(),
                event.isReviewRequired(),
                event.getCreatedAt()
        );
    }
}
