package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.knowledge.application.KnowledgeCitationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgePlanContextService {

    private final KnowledgeCitationService knowledgeCitationService;

    public KnowledgePlanContextService(KnowledgeCitationService knowledgeCitationService) {
        this.knowledgeCitationService = knowledgeCitationService;
    }

    public List<MessageCitationView> loadPersistedCitations(List<String> documentIds) {
        return knowledgeCitationService.loadByDocumentIds(documentIds);
    }
}
