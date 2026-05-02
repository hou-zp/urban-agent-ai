package com.example.urbanagent.common.async;

public sealed interface AsyncTaskPayload permits KnowledgeDocumentIndexTaskPayload,
        KnowledgeIndexRebuildTaskPayload,
        KnowledgeQualityCheckTaskPayload,
        AiEvaluationTaskPayload {
}
