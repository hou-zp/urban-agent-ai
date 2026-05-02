package com.example.urbanagent.knowledge.application.dto;

import java.time.LocalDate;

public record KnowledgeSearchHitView(
        String documentId,
        String documentTitle,
        String fileName,
        String category,
        String sourceOrg,
        String documentNumber,
        String securityLevel,
        String regionCode,
        String sourceUrl,
        String sectionTitle,
        String snippet,
        double score,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
