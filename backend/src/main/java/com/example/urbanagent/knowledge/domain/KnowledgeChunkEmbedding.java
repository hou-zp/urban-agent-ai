package com.example.urbanagent.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_chunk_embedding")
public class KnowledgeChunkEmbedding {

    @Id
    private String chunkId;

    @Column(nullable = false)
    private String documentId;

    @Column(nullable = false, length = 120)
    private String embeddingModel;

    @Column(nullable = false)
    private Integer vectorDimension;

    @Column(nullable = false, columnDefinition = "text")
    private String embeddingVector;

    @Column(nullable = false)
    private Instant createdAt;

    protected KnowledgeChunkEmbedding() {
    }

    public KnowledgeChunkEmbedding(String chunkId,
                                   String documentId,
                                   String embeddingModel,
                                   Integer vectorDimension,
                                   String embeddingVector) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.embeddingModel = embeddingModel;
        this.vectorDimension = vectorDimension;
        this.embeddingVector = embeddingVector;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Integer getVectorDimension() {
        return vectorDimension;
    }

    public String getEmbeddingVector() {
        return embeddingVector;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
