# 阶段 4：生产化

## 目标
完善运维能力、监控告警和部署文档。

## 范围

### 4.1 完善监控指标
- ai_request_total：AI 请求总数
- ai_request_latency：端到端耗时
- llm_call_latency：模型调用耗时
- tool_call_latency：工具调用耗时
- metric_query_count：指标查询次数
- blocked_answer_count：防造假拦截次数
- no_permission_count：权限不足次数
- rag_hit_rate：政策法规检索命中率

### 4.2 完善日志脱敏
- 禁止记录完整身份证号、手机号、投诉人详细住址
- 记录 requestId、userId、intent、taskId、queryId、evidenceId

### 4.3 完善运维文档
- 部署手册（Docker/K8s）
- 回滚手册
- 监控告警配置
- 常见问题处理

### 4.4 安全加固
- 敏感字段脱敏规则完善
- SQL 注入防护
- 请求限流

## 交付物清单

| # | 文件 | 类型 | 说明 |
|---|-----|------|------|
| 1 | `docs/14-production-rollback-manual.md` | 文档 | 部署和回滚手册 |
| 2 | `docs/13-observability-dashboard-checklist.md` | 文档 | 监控检查清单 |
| 3 | `application-metrics.yml` | 配置 | 指标定义 |
| 4 | `LogbackMaskingTest` | 测试 | 日志脱敏测试 |
| 5 | 部署脚本 | 脚本 | Docker/K8s 部署 |

## 执行步骤

### Step 1: 完善监控指标定义

### Step 2: 完善日志脱敏

### Step 3: 完善运维文档

### Step 4: 安全加固

### Step 5: 测试验证

## 验收标准

- [x] 监控指标覆盖核心链路 ✅
- [x] 日志脱敏规则完整 ✅
- [x] 部署手册完整 ✅
- [x] 回滚步骤明确 ✅
- [x] 单元测试通过 ✅

## 风险与回滚

- 风险：监控指标变更可能影响现有 Dashboard
- 缓解：增量添加，不修改现有指标名称
- 回滚：可通过配置回滚

## 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-05-03 | 阶段 4 完成，监控指标和日志脱敏增强 |