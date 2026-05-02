package com.example.urbanagent.common.async;

public record KnowledgeDocumentIndexTaskPayload(String documentId,
                                                String title,
                                                String category,
                                                String documentNumber,
                                                String regionCode,
                                                String sourceOrg) implements AsyncTaskPayload {
}
