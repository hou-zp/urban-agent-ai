package com.example.urbanagent.risk.domain;

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
@Table(name = "risk_event")
public class RiskEvent {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RiskLevel riskLevel;

    @Column(nullable = false, length = 255)
    private String riskCategories;

    @Column(nullable = false, columnDefinition = "text")
    private String triggerReason;

    @Column(nullable = false)
    private boolean reviewRequired;

    @Column(nullable = false)
    private Instant createdAt;

    protected RiskEvent() {
    }

    public RiskEvent(String runId,
                     String sessionId,
                     String userId,
                     String question,
                     RiskLevel riskLevel,
                     String riskCategories,
                     String triggerReason,
                     boolean reviewRequired) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.question = question;
        this.riskLevel = riskLevel;
        this.riskCategories = riskCategories;
        this.triggerReason = triggerReason;
        this.reviewRequired = reviewRequired;
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

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getQuestion() {
        return question;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getRiskCategories() {
        return riskCategories;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
