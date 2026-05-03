-- 安全修复：清除所有预置账号的密码哈希
-- 此迁移确保生产环境必须通过运维注入真实密码
-- 不删除账号，保留用户记录，只清除密码
UPDATE iam_user SET password_hash = NULL WHERE id IN (
    'demo-user', 'officer-a', 'window-a', 'manager-a',
    'legal-user', 'auditor-user', 'rate-limit-user'
);