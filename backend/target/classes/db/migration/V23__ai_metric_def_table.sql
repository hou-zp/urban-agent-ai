-- V23: 指标定义表
-- 用于存储业务指标的元数据、口径、维度和刷新频率

CREATE TABLE ai_metric_def (
    id BIGSERIAL PRIMARY KEY,
    metric_code VARCHAR(128) UNIQUE NOT NULL,
    metric_name VARCHAR(256) NOT NULL,
    scene_code VARCHAR(64),                     -- 关联治理场景编码
    description TEXT,
    data_source_code VARCHAR(128) NOT NULL,    -- 关联数据源编码
    fact_table VARCHAR(128),                    -- 事实表名
    aggregation_type VARCHAR(32) NOT NULL,      -- COUNT / SUM / AVG / RATE / CUSTOM
    measure_column VARCHAR(128),                -- 度量列名
    time_column VARCHAR(128),                   -- 时间列名
    default_filters JSONB,                      -- 默认过滤条件
    allowed_dimensions JSONB,                  -- 允许的维度列表
    caliber TEXT NOT NULL,                      -- 统计口径说明
    refresh_frequency VARCHAR(64),             -- 刷新频率：实时/日/周/月
    owner_department VARCHAR(128),              -- 负责部门
    sensitive_level VARCHAR(32) DEFAULT 'NORMAL', -- NORMAL / SENSITIVE / HIGHLY_SENSITIVE
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_metric_def_scene ON ai_metric_def(scene_code);
CREATE INDEX idx_metric_def_source ON ai_metric_def(data_source_code);
CREATE INDEX idx_metric_def_enabled ON ai_metric_def(enabled);

COMMENT ON TABLE ai_metric_def IS '业务指标定义表，用于智能问数的指标语义层';