package com.example.urbanagent.risk.application;

public record PromptAttackAssessment(
        boolean blocked,
        String triggerReason
) {
}
