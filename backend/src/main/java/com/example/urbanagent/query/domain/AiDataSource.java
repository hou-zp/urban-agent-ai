package com.example.urbanagent.query.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 业务数据源实体，对应 ai_data_source 表。
 *
 * <p>用于存储业务数据源的连接配置、安全级别和更新频率。
 * 与 {@link com.example.urbanagent.query.domain.MetricDefinition} 通过 dataSourceCode 关联。
 *
 * @see com.example.urbanagent.query.repository.AiDataSourceRepository
 */
@Entity
@Table(name = "ai_data_source")
public class AiDataSource {

    @Id
    @Column(name = "data_source_code")
    private String dataSourceCode;

    @Column(name = "data_source_name", nullable = false, length = 256)
    private String dataSourceName;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "connection_config", length = 4000)
    private String connectionConfig;

    @Column(name = "owner_department", length = 128)
    private String ownerDepartment;

    @Column(name = "update_frequency", length = 64)
    private String updateFrequency;

    @Column(name = "security_level", length = 32)
    private String securityLevel;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AiDataSource() {
    }

    public AiDataSource(String dataSourceCode, String dataSourceName, String sourceType,
                       String connectionConfig, String ownerDepartment, String updateFrequency,
                       String securityLevel, boolean enabled) {
        this.dataSourceCode = dataSourceCode;
        this.dataSourceName = dataSourceName;
        this.sourceType = sourceType;
        this.connectionConfig = connectionConfig;
        this.ownerDepartment = ownerDepartment;
        this.updateFrequency = updateFrequency;
        this.securityLevel = securityLevel;
        this.enabled = enabled;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getDataSourceCode() {
        return dataSourceCode;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getConnectionConfig() {
        return connectionConfig;
    }

    public String getOwnerDepartment() {
        return ownerDepartment;
    }

    public String getUpdateFrequency() {
        return updateFrequency;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String resolveDisplayName() {
        return dataSourceName != null ? dataSourceName : dataSourceCode;
    }

    public String resolveConnectionRef() {
        // 从 connectionConfig JSON 中提取 connection_ref 或返回默认值
        if (connectionConfig == null || connectionConfig.isBlank()) {
            return dataSourceCode;
        }
        try {
            int idx = connectionConfig.indexOf("\"connection_ref\"");
            if (idx < 0) return dataSourceCode;
            int start = connectionConfig.indexOf("\"", idx + 17);
            int end = connectionConfig.indexOf("\"", start + 1);
            return connectionConfig.substring(start + 1, end);
        } catch (Exception e) {
            return dataSourceCode;
        }
    }
}