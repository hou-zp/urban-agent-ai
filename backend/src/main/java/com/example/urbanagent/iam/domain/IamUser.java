package com.example.urbanagent.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "iam_user")
public class IamUser {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 32)
    private String roleCode;

    @Column(nullable = false, length = 64)
    private String regionCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    protected IamUser() {
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
