package com.example.urbanagent.agent.application.dto;

public record ParsedIntent(
        IntentType intentType,
        boolean mandatory,
        String reason,
        double confidence
) {
}
