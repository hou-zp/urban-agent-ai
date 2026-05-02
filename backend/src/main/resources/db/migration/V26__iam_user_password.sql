-- 添加用户密码哈希字段
ALTER TABLE iam_user ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);

-- 为预置账号设置默认密码（请在生产环境立即修改）
-- 默认密码：Urban2026!
-- BCrypt hash for: Urban2026!
UPDATE iam_user SET password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.aWj5qDl2WcPjqz5V5e'
WHERE password_hash IS NULL;

-- 创建索引（可选）
CREATE INDEX IF NOT EXISTS idx_iam_user_enabled ON iam_user(enabled);