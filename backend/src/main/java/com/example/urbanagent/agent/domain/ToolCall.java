package com.example.urbanagent.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_call")
public class ToolCall {

    @Id
    private String id;

    @Column(nullable = false)
    private String runId;

    @Column(nullable = false)
    private String toolName;

    @Column(nullable = false, columnDefinition = "text")
    private String inputSummary;

    @Column(nullable = false, columnDefinition = "text")
    private String outputSummary;

    @Column(nullable = false)
    private Instant createdAt;

    protected ToolCall() {
    }

    public ToolCall(String runId, String toolName, String inputSummary, String outputSummary) {
        this.runId = runId;
        this.toolName = toolName;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
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

    public String getToolName() {
        return toolName;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void complete(String outputSummary) {
        this.outputSummary = outputSummary;
    }
}
