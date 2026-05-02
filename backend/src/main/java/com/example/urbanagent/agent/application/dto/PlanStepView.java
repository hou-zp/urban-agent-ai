package com.example.urbanagent.agent.application.dto;

import java.time.Instant;

public record PlanStepView(
        String id,
        Integer stepOrder,
        String taskCode,
        String taskType,
        String name,
        String goal,
        String status,
        String dependencyStepIds,
        boolean mandatory,
        String outputSummary,
        String resultRef,
        PlanStepExecutionTraceView executionTrace,
        PlanStepFailureView failureDetail,
        PlanStepRetryView retryAdvice,
        java.util.List<PlanStepSystemActionView> systemActions,
        PlanStepArtifact outputPayload,
        Instant createdAt,
        Instant updatedAt
) {
}
