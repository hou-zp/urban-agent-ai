package com.example.urbanagent.risk.application;

import com.example.urbanagent.risk.domain.LegalReviewStatus;
import com.example.urbanagent.risk.domain.RiskLevel;

public record RiskHandlingResult(
        RiskLevel riskLevel,
        String reviewId,
        LegalReviewStatus reviewStatus,
        String assistantMessage
) {
}
