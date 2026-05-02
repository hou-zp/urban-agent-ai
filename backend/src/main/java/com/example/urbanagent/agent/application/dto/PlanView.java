package com.example.urbanagent.agent.application.dto;

import java.time.Instant;
import java.util.List;

public record PlanView(
        String id,
        String runId,
        String goal,
        String status,
        String confirmStatus,
        PlanSystemSummaryView systemSummary,
        List<PlanStepView> steps,
        Instant createdAt,
        Instant updatedAt
) {
}
