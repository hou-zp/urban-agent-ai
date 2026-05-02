package com.example.urbanagent.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunk {

    @Id
    private String id;

    @Column(nullable = false)
    private String documentId;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(length = 255)
    private String sectionTitle;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, columnDefinition = "text")
    private String keywordText;

    @Column(nullable = false)
    private Instant createdAt;

    protected KnowledgeChunk() {
    }

    public KnowledgeChunk(String documentId,
                          Integer chunkIndex,
                          String sectionTitle,
                          String content,
                          String keywordText) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.sectionTitle = sectionTitle;
        this.content = content;
        this.keywordText = keywordText;
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getContent() {
        return content;
    }

    public String getKeywordText() {
        return keywordText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
