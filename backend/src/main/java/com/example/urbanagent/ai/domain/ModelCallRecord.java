package com.example.urbanagent.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_call_record")
public class ModelCallRecord {

    @Id
    private String id;

    @Column(length = 64)
    private String runId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 120)
    private String modelName;

    @Column(nullable = false, length = 32)
    private String operation;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private int promptTokens;

    @Column(nullable = false)
    private int completionTokens;

    @Column(nullable = false)
    private int totalTokens;

    @Column(nullable = false)
    private long latencyMs;

    @Column(length = 64)
    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    protected ModelCallRecord() {
    }

    public ModelCallRecord(String runId,
                           String userId,
                           String provider,
                           String modelName,
                           String operation,
                           String status,
                           int promptTokens,
                           int completionTokens,
                           long latencyMs,
                           String errorCode,
                           String errorMessage) {
        this.runId = runId;
        this.userId = userId;
        this.provider = provider;
        this.modelName = modelName;
        this.operation = operation;
        this.status = status;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.latencyMs = latencyMs;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
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

    public String getRunId() {
        return runId;
    }

    public String getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getOperation() {
        return operation;
    }

    public String getStatus() {
        return status;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
