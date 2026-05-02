package com.example.urbanagent.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "legal_review")
public class LegalReview {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String riskEventId;

    @Column(nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String draftAnswer;

    @Column(columnDefinition = "text")
    private String reviewedAnswer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LegalReviewStatus status;

    @Column(length = 64)
    private String reviewerId;

    @Column(columnDefinition = "text")
    private String reviewComment;

    private Instant reviewedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected LegalReview() {
    }

    public LegalReview(String riskEventId, String runId, String sessionId, String question, String draftAnswer) {
        this.riskEventId = riskEventId;
        this.runId = runId;
        this.sessionId = sessionId;
        this.question = question;
        this.draftAnswer = draftAnswer;
        this.status = LegalReviewStatus.PENDING;
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (status == null) {
            status = LegalReviewStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public void approve(String reviewerId, String reviewComment, String reviewedAnswer) {
        this.status = LegalReviewStatus.APPROVED;
        this.reviewerId = reviewerId;
        this.reviewComment = reviewComment;
        this.reviewedAnswer = reviewedAnswer;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public void reject(String reviewerId, String reviewComment) {
        this.status = LegalReviewStatus.REJECTED;
        this.reviewerId = reviewerId;
        this.reviewComment = reviewComment;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public void revise(String reviewerId, String reviewComment, String reviewedAnswer) {
        this.status = LegalReviewStatus.REVISED;
        this.reviewerId = reviewerId;
        this.reviewComment = reviewComment;
        this.reviewedAnswer = reviewedAnswer;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public void requestMoreFacts(String reviewerId, String reviewComment) {
        this.status = LegalReviewStatus.NEED_MORE_FACTS;
        this.reviewerId = reviewerId;
        this.reviewComment = reviewComment;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public String getId() {
        return id;
    }

    public String getRiskEventId() {
        return riskEventId;
    }

    public String getRunId() {
        return runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public String getDraftAnswer() {
        return draftAnswer;
    }

    public String getReviewedAnswer() {
        return reviewedAnswer;
    }

    public LegalReviewStatus getStatus() {
        return status;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
