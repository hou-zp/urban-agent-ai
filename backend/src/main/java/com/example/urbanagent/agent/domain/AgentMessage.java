package com.example.urbanagent.agent.domain;

import com.example.urbanagent.risk.domain.RiskLevel;
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
@Table(name = "agent_message")
public class AgentMessage {

    @Id
    private String id;

    @Column(nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String citationsJson;

    @Column(columnDefinition = "text")
    private String structuredAnswerJson;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RiskLevel riskLevel;

    @Column(length = 64)
    private String reviewId;

    @Column(nullable = false)
    private Instant createdAt;

    protected AgentMessage() {
    }

    public AgentMessage(String sessionId, MessageRole role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

    public AgentMessage(String sessionId, MessageRole role, String content, String citationsJson) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.citationsJson = citationsJson;
    }

    public AgentMessage(String sessionId,
                        MessageRole role,
                        String content,
                        String citationsJson,
                        String structuredAnswerJson,
                        RiskLevel riskLevel,
                        String reviewId) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.citationsJson = citationsJson;
        this.structuredAnswerJson = structuredAnswerJson;
        this.riskLevel = riskLevel;
        this.reviewId = reviewId;
    }

    public AgentMessage(String sessionId,
                        MessageRole role,
                        String content,
                        String citationsJson,
                        RiskLevel riskLevel,
                        String reviewId) {
        this(sessionId, role, content, citationsJson, null, riskLevel, reviewId);
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

    public String getSessionId() {
        return sessionId;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public String getStructuredAnswerJson() {
        return structuredAnswerJson;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getReviewId() {
        return reviewId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
