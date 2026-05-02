package com.example.urbanagent.knowledge.application.dto;

public record KnowledgeAttachmentView(
        String documentId,
        String attachmentRef,
        String fileName,
        String mimeType,
        long sizeBytes
) {
}
