package com.example.urbanagent.agent.application.dto;

import java.time.Instant;

public record PlanSystemSummaryView(
        int autorunCount,
        int recoverCount,
        int affectedStepCount,
        Instant lastActionAt,
        String summary
) {
}
