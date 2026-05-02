package com.example.urbanagent.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_run")
public class AgentRun {

    @Id
    private String id;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    protected AgentRun() {
    }

    public AgentRun(String sessionId, String userId, String question, String modelName) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.question = question;
        this.modelName = modelName;
        this.status = RunStatus.RUNNING;
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = RunStatus.RUNNING;
        }
    }

    public void complete() {
        this.status = RunStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void pendingLegalReview() {
        this.status = RunStatus.PENDING_LEGAL_REVIEW;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        this.status = RunStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    public void fail() {
        this.status = RunStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getQuestion() {
        return question;
    }

    public RunStatus getStatus() {
        return status;
    }

    public String getModelName() {
        return modelName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
