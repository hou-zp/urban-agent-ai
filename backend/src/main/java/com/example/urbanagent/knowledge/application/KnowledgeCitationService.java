package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import com.example.urbanagent.knowledge.domain.KnowledgeDocumentStatus;
import com.example.urbanagent.knowledge.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeCitationService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeApplicabilityService knowledgeApplicabilityService;

    public KnowledgeCitationService(KnowledgeDocumentRepository knowledgeDocumentRepository,
                                    KnowledgeApplicabilityService knowledgeApplicabilityService) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeApplicabilityService = knowledgeApplicabilityService;
    }

    @Transactional(readOnly = true)
    public List<MessageCitationView> loadByDocumentIds(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        Map<String, KnowledgeDocument> documentsById = new LinkedHashMap<>();
        for (KnowledgeDocument document : knowledgeDocumentRepository.findAllById(documentIds)) {
            if ((document.getStatus() == KnowledgeDocumentStatus.ACTIVE
                    || document.getStatus() == KnowledgeDocumentStatus.EXPIRED)
                    && knowledgeApplicabilityService.isCitable(document, document.getTitle())) {
                documentsById.put(document.getId(), document);
            }
        }
        return documentIds.stream()
                .distinct()
                .map(documentsById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toCitation)
                .toList();
    }

    private MessageCitationView toCitation(KnowledgeDocument document) {
        return new MessageCitationView(
                document.getId(),
                document.getTitle(),
                document.getFileName(),
                document.getCategory().name(),
                document.getSourceOrg(),
                document.getDocumentNumber(),
                document.getSourceUrl(),
                buildSnippet(document),
                null,
                document.getEffectiveFrom(),
                document.getEffectiveTo()
        );
    }

    private String buildSnippet(KnowledgeDocument document) {
        String summary = document.getSummary();
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        String content = document.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replace('\n', ' ').trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }
}
