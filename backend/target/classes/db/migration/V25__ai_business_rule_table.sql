-- V25: 业务规则配置表
-- 用于存储场景处置模板、流程、责任单位和升级规则

CREATE TABLE ai_business_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(128) UNIQUE NOT NULL,
    scene_code VARCHAR(64) NOT NULL,          -- 关联治理场景编码
    rule_type VARCHAR(64) NOT NULL,          -- FLOW / RESPONSIBILITY / EVIDENCE / SLA / ESCALATION
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    region_code VARCHAR(32),                  -- 适用地区编码，NULL 表示全局
    version VARCHAR(32),
    status VARCHAR(32) DEFAULT 'EFFECTIVE',  -- EFFECTIVE / EXPIRED / DRAFT
    effective_date DATE,
    expiry_date DATE,
    metadata JSONB,                         -- 扩展字段（流程节点、责任单位等）
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_business_rule_scene ON ai_business_rule(scene_code);
CREATE INDEX idx_business_rule_type ON ai_business_rule(rule_type);
CREATE INDEX idx_business_rule_region ON ai_business_rule(region_code);
CREATE INDEX idx_business_rule_status ON ai_business_rule(status);

COMMENT ON TABLE ai_business_rule IS '业务规则配置表，用于存储场景处置模板和流程定义';