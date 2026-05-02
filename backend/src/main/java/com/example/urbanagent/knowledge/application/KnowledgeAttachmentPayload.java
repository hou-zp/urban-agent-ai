package com.example.urbanagent.knowledge.application;

public record KnowledgeAttachmentPayload(
        byte[] bytes,
        String fileName,
        String mimeType,
        long sizeBytes
) {
}
