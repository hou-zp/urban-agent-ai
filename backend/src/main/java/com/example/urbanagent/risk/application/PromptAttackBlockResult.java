package com.example.urbanagent.risk.application;

import com.example.urbanagent.risk.domain.RiskLevel;

public record PromptAttackBlockResult(
        RiskLevel riskLevel,
        String assistantMessage
) {
}
