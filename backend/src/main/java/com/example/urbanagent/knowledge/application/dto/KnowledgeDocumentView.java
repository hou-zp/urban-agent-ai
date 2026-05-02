package com.example.urbanagent.knowledge.application.dto;

import java.time.Instant;
import java.time.LocalDate;

public record KnowledgeDocumentView(
        String id,
        String title,
        String category,
        String sourceOrg,
        String documentNumber,
        String securityLevel,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String regionCode,
        String summary,
        String attachmentRef,
        String sourceUrl,
        String fileName,
        String mimeType,
        Instant createdAt,
        Instant updatedAt,
        Instant indexedAt,
        String failedReason
) {
}
