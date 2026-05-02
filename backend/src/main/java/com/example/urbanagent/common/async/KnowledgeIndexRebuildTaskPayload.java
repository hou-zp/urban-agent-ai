package com.example.urbanagent.common.async;

public record KnowledgeIndexRebuildTaskPayload(String category,
                                               String regionCode,
                                               String triggerReason) implements AsyncTaskPayload {
}
