package com.example.urbanagent.common.config;

import com.example.urbanagent.common.async.AsyncTaskEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "urban-agent.async")
public class AsyncTaskProperties {

    @NotBlank(message = "publisher must not be blank")
    private String publisher = "none";

    @NotBlank(message = "exchange must not be blank")
    private String exchange = "urban-agent.async";

    @Valid
    private Routing routing = new Routing();

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public Routing getRouting() {
        return routing;
    }

    public void setRouting(Routing routing) {
        this.routing = routing;
    }

    public String routingKey(AsyncTaskEventType eventType) {
        return switch (eventType) {
            case KNOWLEDGE_DOCUMENT_INDEX_REQUESTED -> routing.getKnowledgeDocumentIndexRequested();
            case KNOWLEDGE_INDEX_REBUILD_REQUESTED -> routing.getKnowledgeIndexRebuildRequested();
            case KNOWLEDGE_QUALITY_CHECK_REQUESTED -> routing.getKnowledgeQualityCheckRequested();
            case AI_EVALUATION_REQUESTED -> routing.getAiEvaluationRequested();
        };
    }

    public static class Routing {

        @NotBlank(message = "knowledgeDocumentIndexRequested must not be blank")
        private String knowledgeDocumentIndexRequested = "knowledge.document.index.requested";

        @NotBlank(message = "knowledgeIndexRebuildRequested must not be blank")
        private String knowledgeIndexRebuildRequested = "knowledge.index.rebuild.requested";

        @NotBlank(message = "knowledgeQualityCheckRequested must not be blank")
        private String knowledgeQualityCheckRequested = "knowledge.quality.check.requested";

        @NotBlank(message = "aiEvaluationRequested must not be blank")
        private String aiEvaluationRequested = "ai.evaluation.requested";

        public String getKnowledgeDocumentIndexRequested() {
            return knowledgeDocumentIndexRequested;
        }

        public void setKnowledgeDocumentIndexRequested(String knowledgeDocumentIndexRequested) {
            this.knowledgeDocumentIndexRequested = knowledgeDocumentIndexRequested;
        }

        public String getKnowledgeIndexRebuildRequested() {
            return knowledgeIndexRebuildRequested;
        }

        public void setKnowledgeIndexRebuildRequested(String knowledgeIndexRebuildRequested) {
            this.knowledgeIndexRebuildRequested = knowledgeIndexRebuildRequested;
        }

        public String getKnowledgeQualityCheckRequested() {
            return knowledgeQualityCheckRequested;
        }

        public void setKnowledgeQualityCheckRequested(String knowledgeQualityCheckRequested) {
            this.knowledgeQualityCheckRequested = knowledgeQualityCheckRequested;
        }

        public String getAiEvaluationRequested() {
            return aiEvaluationRequested;
        }

        public void setAiEvaluationRequested(String aiEvaluationRequested) {
            this.aiEvaluationRequested = aiEvaluationRequested;
        }
    }
}
