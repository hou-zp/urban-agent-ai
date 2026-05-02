package com.example.urbanagent.knowledge.application;

public interface KnowledgeAttachmentObjectStorage {

    String store(String documentId, String fileName, byte[] bytes);

    byte[] read(String attachmentRef);
}
