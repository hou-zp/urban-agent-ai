package com.example.urbanagent.agent.application.dto;

import java.time.Instant;

public record PlanStepSystemActionView(
        String action,
        Integer dependencyStepOrder,
        String dependencyStepName,
        String summary,
        Instant createdAt
) {
}
