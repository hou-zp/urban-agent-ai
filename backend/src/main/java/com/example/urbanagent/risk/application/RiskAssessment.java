package com.example.urbanagent.risk.application;

import com.example.urbanagent.risk.domain.RiskCategory;
import com.example.urbanagent.risk.domain.RiskLevel;

import java.util.List;

public record RiskAssessment(
        RiskLevel riskLevel,
        List<RiskCategory> categories,
        boolean reviewRequired,
        String triggerReason,
        String responseAdvice
) {
}
