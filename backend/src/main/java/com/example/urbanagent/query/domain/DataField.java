package com.example.urbanagent.query.domain;

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
@Table(name = "data_field")
public class DataField {

    @Id
    private String id;

    @Column(nullable = false)
    private String tableId;

    @Column(nullable = false, length = 120)
    private String fieldName;

    @Column(nullable = false, length = 120)
    private String businessName;

    @Column(nullable = false, length = 32)
    private String dataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SensitiveLevel sensitiveLevel;

    @Column(nullable = false, length = 255)
    private String visibleRoles;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DataField() {
    }

    public DataField(String tableId,
                     String fieldName,
                     String businessName,
                     String dataType,
                     SensitiveLevel sensitiveLevel,
                     String visibleRoles) {
        this.tableId = tableId;
        this.fieldName = fieldName;
        this.businessName = businessName;
        this.dataType = dataType;
        this.sensitiveLevel = sensitiveLevel;
        this.visibleRoles = visibleRoles;
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

    public String getId() {
        return id;
    }

    public String getTableId() {
        return tableId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getDataType() {
        return dataType;
    }

    public SensitiveLevel getSensitiveLevel() {
        return sensitiveLevel;
    }

    public String getVisibleRoles() {
        return visibleRoles;
    }
}
