package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.knowledge.application.dto.KnowledgeDocumentView;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHit;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHitView;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class KnowledgeViewMapper {

    public KnowledgeDocumentView toView(KnowledgeDocument document) {
        return new KnowledgeDocumentView(
                document.getId(),
                document.getTitle(),
                toApiValue(document.getCategory().name()),
                document.getSourceOrg(),
                document.getDocumentNumber(),
                toApiValue(document.getSecurityLevel().name()),
                toApiValue(document.getStatus().name()),
                document.getEffectiveFrom(),
                document.getEffectiveTo(),
                document.getRegionCode(),
                document.getSummary(),
                document.getAttachmentRef(),
                document.getSourceUrl(),
                document.getFileName(),
                document.getMimeType(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getIndexedAt(),
                document.getFailedReason()
        );
    }

    public KnowledgeSearchHitView toView(KnowledgeSearchHit hit) {
        return new KnowledgeSearchHitView(
                hit.documentId(),
                hit.documentTitle(),
                hit.fileName(),
                toApiValue(hit.category().name()),
                hit.sourceOrg(),
                hit.documentNumber(),
                toApiValue(hit.securityLevel()),
                hit.regionCode(),
                hit.sourceUrl(),
                hit.sectionTitle(),
                hit.snippet(),
                hit.score(),
                hit.effectiveFrom(),
                hit.effectiveTo()
        );
    }

    private String toApiValue(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
