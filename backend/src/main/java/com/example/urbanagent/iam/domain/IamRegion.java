package com.example.urbanagent.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "iam_region")
public class IamRegion {

    @Id
    @Column(length = 64)
    private String regionCode;

    @Column(nullable = false, length = 120)
    private String regionName;

    @Column(length = 64)
    private String parentRegionCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    protected IamRegion() {
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getRegionName() {
        return regionName;
    }

    public String getParentRegionCode() {
        return parentRegionCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
