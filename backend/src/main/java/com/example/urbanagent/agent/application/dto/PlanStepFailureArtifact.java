package com.example.urbanagent.agent.application.dto;

public record PlanStepFailureArtifact(
        PlanStepFailureCode errorCode,
        String errorMessage
) {
}
