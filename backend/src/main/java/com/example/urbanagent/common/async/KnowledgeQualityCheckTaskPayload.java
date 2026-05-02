package com.example.urbanagent.common.async;

public record KnowledgeQualityCheckTaskPayload(String documentId,
                                               String securityLevel,
                                               String regionCode,
                                               String triggerReason) implements AsyncTaskPayload {
}
