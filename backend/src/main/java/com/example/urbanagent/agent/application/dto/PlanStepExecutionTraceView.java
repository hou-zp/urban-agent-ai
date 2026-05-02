package com.example.urbanagent.agent.application.dto;

import java.time.Instant;

public record PlanStepExecutionTraceView(
        String triggerMode,
        String triggerLabel,
        Integer systemActionCount,
        Instant lastActionAt,
        String resultRef,
        String resultLabel
) {
}
