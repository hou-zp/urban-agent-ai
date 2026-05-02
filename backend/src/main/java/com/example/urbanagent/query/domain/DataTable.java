package com.example.urbanagent.query.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_table")
public class DataTable {

    @Id
    private String id;

    @Column(nullable = false)
    private String dataSourceId;

    @Column(nullable = false, length = 120)
    private String tableName;

    @Column(nullable = false, length = 120)
    private String businessName;

    @Column(nullable = false, length = 64)
    private String permissionTag;

    @Column(length = 64)
    private String regionCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DataTable() {
    }

    public DataTable(String dataSourceId,
                     String tableName,
                     String businessName,
                     String permissionTag,
                     String regionCode,
                     boolean enabled) {
        this.dataSourceId = dataSourceId;
        this.tableName = tableName;
        this.businessName = businessName;
        this.permissionTag = permissionTag;
        this.regionCode = regionCode;
        this.enabled = enabled;
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

    public String getDataSourceId() {
        return dataSourceId;
    }

    public String getTableName() {
        return tableName;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getPermissionTag() {
        return permissionTag;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
