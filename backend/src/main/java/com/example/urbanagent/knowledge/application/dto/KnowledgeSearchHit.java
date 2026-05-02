package com.example.urbanagent.knowledge.application.dto;

import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.knowledge.domain.KnowledgeCategory;

import java.time.LocalDate;

public record KnowledgeSearchHit(
        String documentId,
        String documentTitle,
        String fileName,
        KnowledgeCategory category,
        String sourceOrg,
        String documentNumber,
        String securityLevel,
        String regionCode,
        String sourceUrl,
        String sectionTitle,
        String snippet,
        String content,
        double score,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {

    public MessageCitationView toCitation() {
        return new MessageCitationView(
                documentId,
                documentTitle,
                fileName,
                category.name(),
                sourceOrg,
                documentNumber,
                sourceUrl,
                content == null || content.isBlank() ? snippet : content,
                sectionTitle,
                effectiveFrom,
                effectiveTo
        );
    }
}
