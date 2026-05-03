-- 补充 knowledge_document 表缺失字段
-- 补充 metric_definition 表缺失字段
-- 这些字段在 Entity 中定义但原 V2 迁移未创建

ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS issuing_authority VARCHAR(120);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS law_level VARCHAR(32);
ALTER TABLE metric_definition ADD COLUMN IF NOT EXISTS data_source_code VARCHAR(64);