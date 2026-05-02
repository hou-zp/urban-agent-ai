# 后端结构设计

## 1. 后端目标

后端提供智能体编排、知识库、智能问数、风险控制、权限和审计能力。MVP 采用 Spring Boot 模块化单体，按领域划分包和数据库表，保留未来拆分为微服务的边界。

## 2. 分层约定

每个领域模块建议采用以下分层：

```text
module/
├── controller/      # REST/SSE 接口
├── application/     # 应用服务、事务边界、用例编排
├── domain/          # 领域对象、枚举、规则
├── repository/      # 数据访问
├── integration/     # 外部系统、模型、工具适配
└── dto/             # 请求、响应、内部传输对象
```

规则：

- Controller 只做鉴权、参数校验和调用应用服务。
- Application Service 承担事务边界和跨领域编排。
- Domain 放领域规则，例如风险分级、文档状态流转、计划状态流转。
- Repository 不暴露给其他领域模块。
- Integration 不把第三方 SDK 类型泄露到应用层。

## 3. 包结构

```text
com.example.urbanagent
├── common
│   ├── api
│   ├── error
│   ├── config
│   └── security
├── agent
├── knowledge
├── query
├── risk
├── audit
├── iam
└── admin
```

## 4. 统一响应和错误码

响应格式：

```json
{
  "code": 0,
  "data": {},
  "message": "success"
}
```

错误码建议：

| 范围 | 含义 |
| --- | --- |
| 0 | 成功 |
| 10000-10999 | 通用参数、认证、权限错误 |
| 20000-20999 | 智能体会话和运行错误 |
| 30000-30999 | 知识库错误 |
| 40000-40999 | 智能问数和 SQL 错误 |
| 50000-50999 | 风险、审核和审计错误 |
| 90000-90999 | 外部模型或工具错误 |

OpenAPI：

- 通过 springdoc 暴露 `/api-docs` 和 `/swagger-ui.html`。
- `OpenApiConfig` 提供 API 标题、版本、描述和 MVP 用户上下文请求头说明。
- Controller 使用 `@Tag`、`@Operation` 和关键路径参数说明补充接口文档。

## 5. 核心实体

### 5.0 IAM

- `iam_role`：角色编码、角色名称、说明、启停状态。
- `iam_region`：区域编码、区域名称、上级区域、启停状态。
- `iam_user`：用户 ID、显示名称、默认角色、默认区域、启停状态。

MVP 阶段仍使用请求头模拟统一认证接入：

- `X-User-Id`：当前用户，缺省为 `demo-user`。
- `X-User-Role`：当前角色，缺省使用 `iam_user` 的默认角色。
- `X-User-Region`：当前区域，缺省使用 `iam_user` 的默认区域。

后端会校验角色和区域必须存在且启用。试点阶段允许请求头覆盖角色和区域，便于演示不同角色权限；正式接入 OIDC 后，应由认证网关或用户中心下发可信身份。

### 5.1 Agent

- `agent_session`：会话 ID、用户 ID、租户 ID、标题、状态、上下文摘要、创建时间。
- `agent_message`：消息 ID、会话 ID、角色、内容、结构化数据、引用来源、创建时间。
- `agent_run`：运行 ID、会话 ID、用户问题、模型、状态、风险等级、耗时、错误信息。
- `plan`：计划 ID、runId、目标、状态、确认状态。
- `plan_step`：步骤 ID、planId、名称、目标、依赖、状态、产出摘要。
- `tool_call`：工具调用 ID、runId、工具名、参数摘要、结果摘要、耗时、风险等级、错误信息。

状态枚举：

- Run：`running`、`completed`、`failed`、`cancelled`、`pending_review`
- PlanStep：`todo`、`in_progress`、`completed`、`failed`、`abandoned`

### 5.2 Knowledge

- `knowledge_document`：文档 ID、标题、发文机关、文号、生效日期、失效日期、适用区域、密级、状态。
- `knowledge_chunk`：分块 ID、文档 ID、章节、内容、来源定位、主题标签。
- `knowledge_chunk_embedding`：分块 ID、文档 ID、embedding 模型、向量维度、向量内容、创建时间。

文档状态：

- `draft`
- `indexing`
- `active`
- `expired`
- `abolished`
- `failed`

### 5.3 Query

- `data_source`：数据源 ID、名称、类型、连接配置引用、只读标记。
- `data_table`：表 ID、数据源 ID、表名、业务名称、权限标签。
- `data_field`：字段 ID、表 ID、字段名、业务名称、数据类型、敏感级别。
- `metric_definition`：指标编码、指标名称、统计口径、默认时间字段、常用维度。
- `query_record`：问题、候选 SQL、执行 SQL 摘要、结果摘要、权限改写、执行状态。

### 5.4 Risk / Audit

- `risk_event`：风险类型、风险等级、命中规则、处理结果、关联 runId。
- `legal_review`：reviewId、runId、草稿答案、审核状态、审核人、审核意见。
- `audit_log`：用户、动作、资源、摘要、结果、时间。
- `model_call_record`：模型、输入 token、输出 token、耗时、费用估算、状态。

法制审核约束：

- 高风险回答进入 `PENDING` 审核后，业务链路不直接输出具体处罚、强制、拆除等执法建议。
- 法制审核列表、详情和处理接口仅允许 `LEGAL` 或 `ADMIN` 角色访问。
- 审核状态变更只允许作用于 `PENDING` 记录；已通过、驳回、修订或要求补充事实的记录不可重复处理。
- 审核动作统一记录审核人、审核意见、审核时间和修订后的回答内容。

### 5.5 AI Model

- `ModelProvider` 是业务侧统一模型接口，提供 `chat`、`structuredChat`、`streamChat`、`embed`。
- `RoutingModelProvider` 根据 `urban-agent.model.provider` 选择本地 mock 或 OpenAI-compatible 模型；生产默认使用 OpenAI-compatible。
- `OpenAICompatibleModelClient` 通过配置化 `base-url`、`chat-path`、`embeddings-path` 接入兼容服务，密钥仅从环境变量或外部配置读取；结构化输出会携带 JSON Schema 提示并请求 JSON 格式响应。
- 当模型服务不可用时返回 `MODEL_UNAVAILABLE`，不自动降级到 mock 或本地模板回答。
- `ModelCallRecordService` 记录每次模型调用的 provider、模型名、操作类型、粗略 token、耗时、状态和错误信息；审计接口 `GET /api/v1/audit/model-calls` 可查看最近调用，当前操作类型包含 `CHAT`、`STRUCTURED_CHAT`、`EMBED`。
- `KnowledgeApplicationService` 在文档索引时写入 `knowledge_chunk` 和 `knowledge_chunk_embedding`；重新索引会先清理旧 embedding。
- `KnowledgeNativeVectorStore` 在主索引事务提交后，检测 PostgreSQL 和 `embedding_vector_pg` 原生列是否可用；可用时同步 pgvector 原生列，不可用时保留通用文本向量链路。
- `KnowledgeSearchService` 检索时融合关键词得分和 embedding 余弦相似度。当前 API 层使用应用层相似度计算以兼容 H2；PostgreSQL profile 已具备 pgvector 原生列和 HNSW 索引，后续可将 TopK 召回下推到 SQL。
- `UrbanManagementAgent` 在模型回答前执行来源约束：政策、法规、执法流程、处罚依据等问题需要先获得知识检索命中；无命中时直接返回统一无来源提示，命中时把引用来源写入 `agent_message.citations_json`。

## 6. 工具系统

工具接口应统一：

```text
ToolDefinition
- name
- description
- inputSchema
- outputSchema
- permissionTags
- riskLevel
- timeout
- auditPolicy
```

MVP 工具：

- `knowledge_search`
- `law_clause_search`
- `case_reference_search`
- `data_catalog_search`
- `nl2sql_generate`
- `sql_validate`
- `readonly_sql_query`
- `metric_calculate`
- `report_generate`
- `risk_assess`
- `user_confirm`

工具要求：

- 工具异常必须转为结构化错误。
- 工具结果必须短小、结构化。
- 高风险工具调用前必须经过 Hook。
- 所有工具调用必须写入 `tool_call`。

## 7. SQL 校验与权限改写

执行链路：

1. 用户问题解析。
2. 查询授权数据目录。
3. 生成候选 SQL。
4. AST 解析。
5. 校验只读语句。
6. 表、字段、函数白名单校验，字段权限覆盖 `SELECT`、`WHERE`、`GROUP BY`、`ORDER BY`、`HAVING` 子句。
7. 注入租户、区域、部门权限条件。
8. 敏感字段脱敏或拒绝。
9. 添加 limit 和 timeout。
10. 只读账号执行。

禁止：

- `INSERT`、`UPDATE`、`DELETE`、`MERGE`
- DDL
- 存储过程
- 未授权跨库查询
- 未限制明确时间范围的大表明细扫描，事实表日期字段必须使用 `between`、`=`、`>=`、`<=`、`>`、`<` 等过滤条件
- 绕过权限条件的子查询或函数

## 8. 安全和配置

- 密钥通过环境变量或密钥管理系统注入。
- 日志默认脱敏。
- 生产禁用未审批动态工具加载。
- 系统提示词、工具权限和模型密钥不得通过接口暴露。
- 接口预留 OIDC 鉴权适配，MVP 可使用内置用户和角色。
- 运行时限流通过 `urban-agent.runtime.*` 配置，当前覆盖聊天、问数预览和问数执行，按用户维度计数，超限返回 `RATE_LIMITED`。
- 普通请求、SSE 流式请求和 SQL 执行均有独立超时配置；异步任务会传播用户上下文和日志 MDC，避免流式运行退回默认身份。

## 9. 观测与审计

必须记录：

- sessionId
- runId
- toolCallId
- userId
- tenantId
- modelName
- token 使用量
- SQL 摘要
- 风险等级
- 错误码和耗时

健康与日志：

- Actuator 仅对外暴露 `/actuator/health` 和 `/actuator/info`；健康检查启用 liveness 探针 `/actuator/health/liveness`。
- 未暴露的 Actuator 端点和未匹配资源统一返回 `{ code, data, message }` 格式的 404 响应。
- 日志通过 `logback-spring.xml` 输出 JSON，包含 timestamp、level、logger、thread、message、application、MDC 和 stacktrace。
- `UserContextFilter` 写入 `userId`、`role`、`region`；Agent 执行链路写入 `runId`、`sessionId`。
- Micrometer Tracing 使用 OpenTelemetry bridge，OTLP endpoint 通过 `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` 配置。

指标：

- 请求量、错误率、P95 延迟。
- 模型调用耗时和失败率。
- 工具调用耗时和失败率。
- 检索命中率。
- SQL 拒绝率。
- 审计写入失败率。

## 10. 计划查询接口

- `GET /api/v1/agent/sessions/runs/{runId}/plan`：查询综合分析任务的计划和步骤状态。
- `POST /api/v1/agent/sessions/runs/{runId}/plan/execute-next`：执行下一步计划，当前支持数据查询预览、只读查询执行和综合结论生成。
- `POST /api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute`：执行指定计划步骤，用于前端按步骤推进或重试失败步骤。
- 已完成或已废弃步骤重复执行时直接返回当前计划状态，不推进后续步骤，不重复生成报告消息。
- 步骤执行失败时，后端会将步骤和计划置为 `FAILED`，记录 `plan.step_failed` 工具调用；修复权限、数据源或配置后，可再次调用指定步骤接口进行重试。
- 当综合结论步骤完成后，后端会把生成的报告写入当前会话的 assistant 消息，前端可通过会话详情接口刷新展示。
