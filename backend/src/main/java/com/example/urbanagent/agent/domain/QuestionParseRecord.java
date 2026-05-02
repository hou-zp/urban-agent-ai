package com.example.urbanagent.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "question_parse_record")
public class QuestionParseRecord {

    @Id
    private String runId;

    @Column(nullable = false, columnDefinition = "text")
    private String originalQuestion;

    private String primaryIntent;

    @Column(nullable = false)
    private double overallConfidence;

    @Column(nullable = false)
    private boolean requiresCitation;

    @Column(nullable = false)
    private boolean requiresDataQuery;

    @Column(nullable = false, columnDefinition = "text")
    private String intentsJson;

    @Column(nullable = false, columnDefinition = "text")
    private String scenesJson;

    @Column(nullable = false, columnDefinition = "text")
    private String slotsJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected QuestionParseRecord() {
    }

    public QuestionParseRecord(String runId,
                               String originalQuestion,
                               String primaryIntent,
                               double overallConfidence,
                               boolean requiresCitation,
                               boolean requiresDataQuery,
                               String intentsJson,
                               String scenesJson,
                               String slotsJson) {
        this.runId = runId;
        this.originalQuestion = originalQuestion;
        this.primaryIntent = primaryIntent;
        this.overallConfidence = overallConfidence;
        this.requiresCitation = requiresCitation;
        this.requiresDataQuery = requiresDataQuery;
        this.intentsJson = intentsJson;
        this.scenesJson = scenesJson;
        this.slotsJson = slotsJson;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getRunId() {
        return runId;
    }

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public String getPrimaryIntent() {
        return primaryIntent;
    }

    public double getOverallConfidence() {
        return overallConfidence;
    }

    public boolean isRequiresCitation() {
        return requiresCitation;
    }

    public boolean isRequiresDataQuery() {
        return requiresDataQuery;
    }

    public String getIntentsJson() {
        return intentsJson;
    }

    public String getScenesJson() {
        return scenesJson;
    }

    public String getSlotsJson() {
        return slotsJson;
    }
}
