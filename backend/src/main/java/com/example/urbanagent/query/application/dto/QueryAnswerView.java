package com.example.urbanagent.query.application.dto;

import com.example.urbanagent.agent.application.dto.MessageCitationView;

import java.util.List;

public record QueryAnswerView(
        String mode,
        String answer,
        List<String> warnings,
        List<DataStatement> dataStatements,
        List<QueryCardView> queryCards,
        List<MessageCitationView> citations
) {
}
