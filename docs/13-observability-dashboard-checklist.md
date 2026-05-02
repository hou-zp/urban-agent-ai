# 可观测性告警与仪表盘清单

## 1. 目标

本清单用于支撑试点和生产环境的日常值守，覆盖以下四类问题：

- 模型不可用、回答失败、工具执行失败
- SQL 拒绝、权限误配、问数延迟升高
- 法制审核积压、知识索引失败、异步任务堆积
- 敏感操作异常增长、附件下载异常、系统整体可用性下降

当前项目已经具备以下观测基础：

- `health / info` 健康检查
- JSON 结构化日志
- `audit_log` 统一审计追溯
- `model_call_record` 模型调用记录
- `legal_review` 法审记录
- `query_record` 问数执行记录

本清单定义的是运维必须接入的指标、告警和仪表盘，不要求本轮全部变成后端内建 Micrometer 指标。

## 2. 数据来源

| 来源 | 用途 |
| --- | --- |
| `audit_log` | 统计运行、工具、查询、知识附件、风险事件成功率与失败率 |
| `model_call_record` | 统计模型调用成功率、耗时、失败原因、模型维度分布 |
| `legal_review` | 统计待审数量、超时数量、处理时长 |
| `query_record` | 统计问数预览/执行量、拒绝量、执行耗时、口径分布 |
| `knowledge_document` | 统计知识文档总量、失效量、索引失败量、附件覆盖率 |
| 应用日志 | 定位异常请求、脱敏验证、追踪 traceId / spanId |
| Actuator `health` | 存活性、就绪性、依赖可用性 |

## 3. 核心告警指标

### 3.1 模型与智能体

| 指标名 | 说明 | 建议阈值 | 数据来源 |
| --- | --- | --- | --- |
| `agent_run_fail_rate_5m` | 近 5 分钟智能体运行失败率 | `> 5%` 持续 10 分钟告警 | `audit_log` 中 `agent.run.fail / start` |
| `agent_run_timeout_count_15m` | 近 15 分钟运行超时次数 | `>= 3` 告警 | `audit_log`、运行状态 |
| `model_call_fail_rate_5m` | 近 5 分钟模型调用失败率 | `> 10%` 告警 | `model_call_record` |
| `model_call_p95_ms_15m` | 模型调用 P95 时延 | `> 8000ms` 告警 | `model_call_record` |
| `model_unavailable_count_15m` | 模型不可用次数 | `>= 1` 立即告警 | `model_call_record`、错误码 `MODEL_UNAVAILABLE` |

### 3.2 工具与计划执行

| 指标名 | 说明 | 建议阈值 | 数据来源 |
| --- | --- | --- | --- |
| `tool_call_fail_rate_5m` | 工具失败率 | `> 5%` 告警 | `audit_log` 中 `tool.call` 与失败动作 |
| `plan_dependency_recover_count_1h` | 计划自动恢复次数 | `> 20` 预警 | `audit_log` 中 `plan.dependency_recover` |
| `plan_dependency_autorun_count_1h` | 计划自动补跑次数 | `> 30` 预警 | `audit_log` 中 `plan.dependency_autorun` |
| `plan_step_fail_count_15m` | 计划步骤失败次数 | `>= 5` 告警 | `audit_log` 中 `plan.step.fail` |

### 3.3 数据问数

| 指标名 | 说明 | 建议阈值 | 数据来源 |
| --- | --- | --- | --- |
| `query_preview_reject_rate_15m` | 问数预览拒绝率 | `> 20%` 预警 | `query_record` 状态、`audit_log` |
| `query_execute_reject_rate_15m` | 问数执行拒绝率 | `> 10%` 预警 | `query_record` 状态、`audit_log` |
| `sql_permission_denied_count_15m` | SQL 权限拒绝次数 | `>= 10` 告警 | 错误码 `SQL_PERMISSION_DENIED` |
| `sql_validation_failed_count_15m` | SQL 校验失败次数 | `>= 10` 预警 | 错误码 `SQL_VALIDATION_FAILED` |
| `query_execute_p95_ms_15m` | 问数执行 P95 时延 | `> 3000ms` 告警 | `query_record.duration` 或审计耗时 |
| `rate_limited_count_15m` | 限流命中次数 | `>= 20` 预警 | 错误码 `RATE_LIMITED` |

### 3.4 风险与法审

| 指标名 | 说明 | 建议阈值 | 数据来源 |
| --- | --- | --- | --- |
| `risk_prompt_guard_count_15m` | 提示词攻击拦截次数 | `>= 5` 预警 | `audit_log` 中 `risk.prompt_guard` |
| `legal_review_pending_count` | 当前待法审数量 | `> 20` 告警 | `legal_review` |
| `legal_review_pending_over_2h_count` | 待法审超过 2 小时数量 | `>= 1` 告警 | `legal_review.created_at` |
| `legal_review_p95_minutes_1d` | 法审处理 P95 时长 | `> 60 分钟` 预警 | `legal_review` |

### 3.5 知识库与附件

| 指标名 | 说明 | 建议阈值 | 数据来源 |
| --- | --- | --- | --- |
| `knowledge_index_fail_count_1h` | 知识索引失败数量 | `>= 3` 告警 | `knowledge_document.status=FAILED`、`audit_log` |
| `knowledge_attachment_upload_fail_count_1h` | 附件上传失败数量 | `>= 3` 预警 | 接口日志、错误码 `KNOWLEDGE_ATTACHMENT_REJECTED` |
| `knowledge_attachment_download_denied_count_1h` | 附件下载拒绝次数 | `>= 10` 预警 | `ACCESS_DENIED`、附件审计 |
| `knowledge_document_expired_count` | 已过期文档数量 | 仅展示，无默认告警 | `knowledge_document` |

### 3.6 基础可用性

| 指标名 | 说明 | 建议阈值 | 数据来源 |
| --- | --- | --- | --- |
| `app_health_status` | 应用健康状态 | `DOWN` 立即告警 | `/actuator/health` |
| `jvm_heap_usage_ratio` | JVM 堆使用率 | `> 85%` 持续 10 分钟告警 | JVM 指标 |
| `http_5xx_rate_5m` | 5xx 比例 | `> 2%` 告警 | 接入层或应用指标 |
| `http_p95_ms_5m` | 接口 P95 时延 | `> 2000ms` 告警 | 网关、APM 或应用指标 |

## 4. 仪表盘分组

### 4.1 总览页

- 应用健康状态
- 近 1 小时请求量、错误量、5xx 比率
- 智能体运行成功率
- 模型调用成功率
- 问数执行成功率
- 法审待办数量

### 4.2 智能体与模型页

- `agent_run_fail_rate_5m`
- `agent_run_timeout_count_15m`
- `model_call_fail_rate_5m`
- `model_call_p95_ms_15m`
- 按模型名称分组的调用量/失败量

### 4.3 问数与权限页

- `query_preview_reject_rate_15m`
- `query_execute_reject_rate_15m`
- `sql_permission_denied_count_15m`
- `sql_validation_failed_count_15m`
- `query_execute_p95_ms_15m`
- 按区域、角色、指标编码分组的执行量

### 4.4 风险与法审页

- `risk_prompt_guard_count_15m`
- `legal_review_pending_count`
- `legal_review_pending_over_2h_count`
- `legal_review_p95_minutes_1d`
- 按风险等级分组的事件趋势

### 4.5 知识库与附件页

- `knowledge_index_fail_count_1h`
- 文档总量、有效量、过期量、废止量
- `knowledge_attachment_upload_fail_count_1h`
- `knowledge_attachment_download_denied_count_1h`
- 异步索引、重建、质量巡检事件量

## 5. 告警分级

| 等级 | 触发场景 | 处理要求 |
| --- | --- | --- |
| P0 | 应用不可用、模型整体不可用、法审严重积压 | 立即通知值班人和研发负责人 |
| P1 | 问数拒绝率持续升高、工具失败率升高、知识索引持续失败 | 30 分钟内介入排查 |
| P2 | 限流偏高、自动补跑偏高、附件拒绝偏高 | 当日内分析原因并调整配置 |

## 6. 推荐落地顺序

1. 先用 `audit_log + model_call_record + legal_review` 建立 SQL 版指标看板。
2. 再把稳定的核心指标沉到 Prometheus / Micrometer。
3. 最后补告警路由、值班群通知和值守 SOP。

## 7. 交付检查项

- 已有 Grafana 面板命名与本清单一致。
- 每个 P0 / P1 指标都有明确阈值。
- 每个告警都有值班负责人。
- 仪表盘能按环境、区域、角色筛选。
- 排障时能从指标跳到日志和审计记录。
