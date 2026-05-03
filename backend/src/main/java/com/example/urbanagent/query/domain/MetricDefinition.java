package com.example.urbanagent.query.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "metric_definition")
public class MetricDefinition {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String metricCode;

    @Column(nullable = false, length = 120)
    private String metricName;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 255)
    private String aggregationExpr;

    @Column(nullable = false, length = 120)
    private String defaultTimeField;

    @Column(nullable = false, length = 255)
    private String commonDimensions;

    @Column(nullable = false, length = 120)
    private String tableName;

    @Column(name = "data_source_code", length = 64)
    private String dataSourceCode;

    @Column(length = 64)
    private String regionCode;

    @Column(nullable = false, length = 64)
    private String caliberVersion;

    @Column(nullable = false, length = 32)
    private String dataQuality;

    @Column(nullable = false, length = 120)
    private String applicableRegion;

    @Column(nullable = false)
    private Instant dataUpdatedAt;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MetricDefinition() {
    }

    public MetricDefinition(String metricCode,
                            String metricName,
                            String description,
                            String aggregationExpr,
                            String defaultTimeField,
                            String commonDimensions,
                            String tableName,
                            String dataSourceCode,
                            String regionCode,
                            String caliberVersion,
                            String dataQuality,
                            String applicableRegion,
                            Instant dataUpdatedAt,
                            boolean enabled) {
        this.metricCode = metricCode;
        this.metricName = metricName;
        this.description = description;
        this.aggregationExpr = aggregationExpr;
        this.defaultTimeField = defaultTimeField;
        this.commonDimensions = commonDimensions;
        this.tableName = tableName;
        this.dataSourceCode = dataSourceCode;
        this.regionCode = regionCode;
        this.caliberVersion = caliberVersion;
        this.dataQuality = dataQuality;
        this.applicableRegion = applicableRegion;
        this.dataUpdatedAt = dataUpdatedAt;
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

    public String getMetricCode() {
        return metricCode;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getDescription() {
        return description;
    }

    public String getAggregationExpr() {
        return aggregationExpr;
    }

    public String getDefaultTimeField() {
        return defaultTimeField;
    }

    public String getCommonDimensions() {
        return commonDimensions;
    }

    public String getTableName() {
        return tableName;
    }

    public String getDataSourceCode() {
        return dataSourceCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getCaliberVersion() {
        return caliberVersion;
    }

    public String getDataQuality() {
        return dataQuality;
    }

    public String getApplicableRegion() {
        return applicableRegion;
    }

    public Instant getDataUpdatedAt() {
        return dataUpdatedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String resolveCaliberVersion() {
        if (caliberVersion == null || caliberVersion.isBlank()) {
            return updatedAt == null ? "metric-v1" : "metric-" + updatedAt.toEpochMilli();
        }
        return caliberVersion;
    }

    public String resolveDataQuality() {
        if (dataQuality == null || dataQuality.isBlank()) {
            return "verified";
        }
        return dataQuality.toLowerCase(Locale.ROOT);
    }

    public String resolveApplicableRegion() {
        if (applicableRegion != null && !applicableRegion.isBlank()) {
            return applicableRegion;
        }
        return regionCode == null || regionCode.isBlank() ? "未说明" : regionCode;
    }

    public Instant resolveDataUpdatedAt() {
        return dataUpdatedAt == null ? updatedAt : dataUpdatedAt;
    }
}
