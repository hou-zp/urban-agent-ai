package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import com.example.urbanagent.knowledge.domain.KnowledgeDocumentStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class KnowledgeApplicabilityService {

    private final Clock clock;

    public KnowledgeApplicabilityService() {
        this(Clock.systemDefaultZone());
    }

    KnowledgeApplicabilityService(Clock clock) {
        this.clock = clock;
    }

    public boolean isSearchable(KnowledgeDocument document, String query) {
        return isApplicable(document, query, false);
    }

    public boolean isCitable(KnowledgeDocument document, String query) {
        return isApplicable(document, query, true);
    }

    private boolean isApplicable(KnowledgeDocument document, String query, boolean requireActiveStatus) {
        if (document == null) {
            return false;
        }
        if (document.getStatus() == KnowledgeDocumentStatus.ABOLISHED
                || document.getStatus() == KnowledgeDocumentStatus.FAILED
                || document.getStatus() == KnowledgeDocumentStatus.DRAFT
                || document.getStatus() == KnowledgeDocumentStatus.INDEXING) {
            return false;
        }
        if (requireActiveStatus && document.getStatus() != KnowledgeDocumentStatus.ACTIVE) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        if (document.getEffectiveFrom() != null && document.getEffectiveFrom().isAfter(today)) {
            return false;
        }
        if (document.getEffectiveTo() != null && document.getEffectiveTo().isBefore(today)) {
            return false;
        }
        return matchesRegion(document.getRegionCode(), UserContextHolder.get());
    }

    private boolean matchesRegion(String documentRegion, UserContext userContext) {
        String normalizedDocumentRegion = normalize(documentRegion);
        if (normalizedDocumentRegion.isBlank() || "city".equals(normalizedDocumentRegion)) {
            return true;
        }
        String userRegion = userContext == null ? "" : normalize(userContext.region());
        if ("ADMIN".equalsIgnoreCase(userContext == null ? null : userContext.role())) {
            return true;
        }
        if (userRegion.isBlank() || "city".equals(userRegion)) {
            return true;
        }
        return normalizedDocumentRegion.equals(userRegion);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
