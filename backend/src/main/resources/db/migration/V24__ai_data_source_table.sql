-- V24: 数据源定义表
-- 用于存储业务数据源的连接配置、安全级别和更新频率

CREATE TABLE ai_data_source (
    id BIGSERIAL PRIMARY KEY,
    data_source_code VARCHAR(128) UNIQUE NOT NULL,
    data_source_name VARCHAR(256) NOT NULL,
    source_type VARCHAR(64) NOT NULL,         -- JDBC / REST / DATA_PLATFORM / MOCK_ONLY_DEV
    connection_config VARCHAR(4000),                   -- 连接配置（JSON，含 host/port/database 等）
    owner_department VARCHAR(128),             -- 负责部门
    update_frequency VARCHAR(64),             -- 更新频率：实时/日/周/月
    security_level VARCHAR(32),               -- PUBLIC / INTERNAL / CONFIDENTIAL
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_data_source_type ON ai_data_source(source_type);
CREATE INDEX idx_data_source_enabled ON ai_data_source(enabled);

COMMENT ON TABLE ai_data_source IS '业务数据源定义表，用于连接管理和权限控制';