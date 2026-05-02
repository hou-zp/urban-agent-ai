package com.example.urbanagent.agent.domain;

import com.example.urbanagent.agent.application.dto.TaskType;
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
@Table(name = "plan_step")
public class PlanStep {

    @Id
    private String id;

    @Column(nullable = false)
    private String planId;

    @Column(nullable = false)
    private Integer stepOrder;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 64)
    private String taskCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TaskType taskType;

    @Column(nullable = false, columnDefinition = "text")
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlanStatus status;

    @Column(length = 500)
    private String dependencyStepIds;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(columnDefinition = "text")
    private String outputSummary;

    @Column(length = 128)
    private String resultRef;

    @Column(columnDefinition = "text")
    private String outputPayloadJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PlanStep() {
    }

    public PlanStep(String planId,
                    Integer stepOrder,
                    String taskCode,
                    TaskType taskType,
                    String name,
                    String goal,
                    String dependencyStepIds,
                    boolean mandatory) {
        this.planId = planId;
        this.stepOrder = stepOrder;
        this.taskCode = taskCode;
        this.taskType = taskType;
        this.name = name;
        this.goal = goal;
        this.dependencyStepIds = dependencyStepIds;
        this.mandatory = mandatory;
        this.status = PlanStatus.TODO;
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
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public void start() {
        this.status = PlanStatus.IN_PROGRESS;
    }

    public void complete(String outputSummary) {
        complete(outputSummary, null);
    }

    public void complete(String outputSummary, String resultRef) {
        complete(outputSummary, resultRef, null);
    }

    public void complete(String outputSummary, String resultRef, String outputPayloadJson) {
        this.status = PlanStatus.COMPLETED;
        this.outputSummary = outputSummary;
        this.resultRef = resultRef;
        this.outputPayloadJson = outputPayloadJson;
    }

    public void fail(String outputSummary) {
        fail(outputSummary, null);
    }

    public void fail(String outputSummary, String resultRef) {
        fail(outputSummary, resultRef, null);
    }

    public void fail(String outputSummary, String resultRef, String outputPayloadJson) {
        this.status = PlanStatus.FAILED;
        this.outputSummary = outputSummary;
        this.resultRef = resultRef;
        this.outputPayloadJson = outputPayloadJson;
    }

    public void abandon(String outputSummary) {
        this.status = PlanStatus.ABANDONED;
        this.outputSummary = outputSummary;
    }

    public String getId() {
        return id;
    }

    public String getPlanId() {
        return planId;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public String getName() {
        return name;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public String getGoal() {
        return goal;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public String getDependencyStepIds() {
        return dependencyStepIds;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public String getResultRef() {
        return resultRef;
    }

    public String getOutputPayloadJson() {
        return outputPayloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
