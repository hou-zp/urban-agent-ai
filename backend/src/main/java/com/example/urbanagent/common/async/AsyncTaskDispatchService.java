package com.example.urbanagent.common.async;

import com.example.urbanagent.common.config.AsyncTaskProperties;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AsyncTaskDispatchService {

    private static final String SOURCE = "urban-agent-backend";

    private final AsyncTaskEventPublisher publisher;
    private final AsyncTaskProperties properties;

    public AsyncTaskDispatchService(AsyncTaskEventPublisher publisher, AsyncTaskProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    public void publishKnowledgeDocumentIndexRequested(KnowledgeDocument document) {
        publish(AsyncTaskEventType.KNOWLEDGE_DOCUMENT_INDEX_REQUESTED,
                "knowledge_document",
                document.getId(),
                new KnowledgeDocumentIndexTaskPayload(
                        document.getId(),
                        document.getTitle(),
                        document.getCategory().name(),
                        document.getDocumentNumber(),
                        document.getRegionCode(),
                        document.getSourceOrg()
                ));
    }

    public void publishKnowledgeIndexRebuildRequested(String category, String regionCode, String triggerReason) {
        publish(AsyncTaskEventType.KNOWLEDGE_INDEX_REBUILD_REQUESTED,
                "knowledge_index",
                normalizeResourceId(category, regionCode),
                new KnowledgeIndexRebuildTaskPayload(category, regionCode, triggerReason));
    }

    public void publishKnowledgeQualityCheckRequested(String documentId, String securityLevel, String regionCode, String triggerReason) {
        publish(AsyncTaskEventType.KNOWLEDGE_QUALITY_CHECK_REQUESTED,
                "knowledge_document",
                documentId,
                new KnowledgeQualityCheckTaskPayload(documentId, securityLevel, regionCode, triggerReason));
    }

    public void publishAiEvaluationRequested(String evaluationSet, String scenario, String runId, Integer sampleSize, String triggerReason) {
        publish(AsyncTaskEventType.AI_EVALUATION_REQUESTED,
                "ai_evaluation",
                runId == null || runId.isBlank() ? normalizeResourceId(evaluationSet, scenario) : runId,
                new AiEvaluationTaskPayload(evaluationSet, scenario, runId, sampleSize, triggerReason));
    }

    private void publish(AsyncTaskEventType type, String resourceType, String resourceId, AsyncTaskPayload payload) {
        publisher.publish(new AsyncTaskEvent<>(
                UUID.randomUUID().toString(),
                type,
                properties.getExchange(),
                properties.routingKey(type),
                resourceType,
                resourceId,
                payload,
                resolveUserId(),
                Instant.now(),
                MDC.get("traceId"),
                SOURCE
        ));
    }

    private String resolveUserId() {
        UserContext userContext = UserContextHolder.currentOrNull();
        if (userContext == null || userContext.userId() == null || userContext.userId().isBlank()) {
            return "system";
        }
        return userContext.userId();
    }

    private String normalizeResourceId(String left, String right) {
        String normalizedLeft = left == null || left.isBlank() ? "all" : left;
        String normalizedRight = right == null || right.isBlank() ? "all" : right;
        return normalizedLeft + ":" + normalizedRight;
    }
}
