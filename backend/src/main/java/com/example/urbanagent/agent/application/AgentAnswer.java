package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.ComposedAnswer;
import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.risk.domain.LegalReviewStatus;
import com.example.urbanagent.risk.domain.RiskLevel;

import java.util.List;

public record AgentAnswer(
        String content,
        List<MessageCitationView> citations,
        RiskLevel riskLevel,
        String reviewId,
        LegalReviewStatus reviewStatus,
        ComposedAnswer composedAnswer
) {
}
