package com.example.urbanagent.agent.application.dto;

import java.time.LocalDate;

public record MessageCitationView(
        String documentId,
        String documentTitle,
        String fileName,
        String category,
        String sourceOrg,
        String documentNumber,
        String sourceUrl,
        String snippet,
        String sectionTitle,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
