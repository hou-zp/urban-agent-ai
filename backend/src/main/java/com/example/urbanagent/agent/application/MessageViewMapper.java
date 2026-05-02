package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.ComposedAnswer;
import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.agent.application.dto.MessageView;
import com.example.urbanagent.agent.domain.AgentMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.urbanagent.risk.repository.LegalReviewRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageViewMapper {

    private static final TypeReference<List<MessageCitationView>> CITATION_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final LegalReviewRepository legalReviewRepository;

    public MessageViewMapper(ObjectMapper objectMapper, LegalReviewRepository legalReviewRepository) {
        this.objectMapper = objectMapper;
        this.legalReviewRepository = legalReviewRepository;
    }

    public MessageView toView(AgentMessage message) {
        return toView(message, null, null);
    }

    public MessageView toView(AgentMessage message, String runId, String planId) {
        String reviewStatus = message.getReviewId() == null ? null : legalReviewRepository.findById(message.getReviewId())
                .map(review -> review.getStatus().name())
                .orElse(null);
        return new MessageView(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                parseCitations(message.getCitationsJson()),
                parseStructuredAnswer(message.getStructuredAnswerJson()),
                message.getRiskLevel() == null ? null : message.getRiskLevel().name(),
                message.getReviewId(),
                reviewStatus,
                message.getCreatedAt(),
                runId,
                planId
        );
    }

    private List<MessageCitationView> parseCitations(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citationsJson, CITATION_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ComposedAnswer parseStructuredAnswer(String structuredAnswerJson) {
        if (structuredAnswerJson == null || structuredAnswerJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(structuredAnswerJson, ComposedAnswer.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
