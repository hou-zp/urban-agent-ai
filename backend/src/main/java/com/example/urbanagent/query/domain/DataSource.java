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
@Table(name = "data_source")
public class DataSource {

    @Id
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataSourceType type;

    @Column(nullable = false, length = 120)
    private String connectionRef;

    @Column(nullable = false)
    private boolean readOnly;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DataSource() {
    }

    public DataSource(String name, DataSourceType type, String connectionRef, boolean readOnly) {
        this.name = name;
        this.type = type;
        this.connectionRef = connectionRef;
        this.readOnly = readOnly;
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

    public String getName() {
        return name;
    }

    public DataSourceType getType() {
        return type;
    }

    public String getConnectionRef() {
        return connectionRef;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
