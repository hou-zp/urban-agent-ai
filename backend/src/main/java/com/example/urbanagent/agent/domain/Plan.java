package com.example.urbanagent.agent.domain;

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
@Table(name = "plan")
public class Plan {

    @Id
    private String id;

    @Column(nullable = false)
    private String runId;

    @Column(nullable = false, columnDefinition = "text")
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlanStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlanConfirmStatus confirmStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Plan() {
    }

    public Plan(String runId, String goal) {
        this.runId = runId;
        this.goal = goal;
        this.status = PlanStatus.TODO;
        this.confirmStatus = PlanConfirmStatus.NOT_REQUIRED;
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

    public void complete() {
        this.status = PlanStatus.COMPLETED;
    }

    public void fail() {
        this.status = PlanStatus.FAILED;
    }

    public void abandon() {
        this.status = PlanStatus.ABANDONED;
    }

    public void requestConfirmation() {
        this.confirmStatus = PlanConfirmStatus.PENDING;
    }

    public void approveConfirmation() {
        this.confirmStatus = PlanConfirmStatus.APPROVED;
    }

    public void rejectConfirmation() {
        this.confirmStatus = PlanConfirmStatus.REJECTED;
    }

    public String getId() {
        return id;
    }

    public String getRunId() {
        return runId;
    }

    public String getGoal() {
        return goal;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public PlanConfirmStatus getConfirmStatus() {
        return confirmStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
