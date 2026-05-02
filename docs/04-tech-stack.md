# 技术栈与基础设施

## 1. 技术栈总览

| 层级 | 技术 | 用途 |
| --- | --- | --- |
| 前端 | Vue 3、Vite、TypeScript | MVP 轻量交互页面 |
| 前端状态 | Pinia | 会话、用户、页面状态 |
| 前端路由 | Vue Router | 页面路由 |
| 后端 | Java 17、Spring Boot 3.x | 核心 API 和领域服务 |
| 构建 | Maven | 后端依赖和构建 |
| 智能体 | AgentScope Java 1.0.11 或后续稳定版本 | ReActAgent、PlanNotebook、工具调用 |
| 数据库 | PostgreSQL | 元数据、审计、会话、权限 |
| 向量检索 | pgvector | 知识分块向量检索 |
| 缓存 | Redis，可选 | 会话缓存、任务状态、限流 |
| 模型接入 | `ModelProvider` 抽象 | 兼容 OpenAI-compatible、DashScope、私有模型 |
| 观测 | OpenTelemetry、Prometheus、Grafana | 链路追踪、指标、告警 |
| 部署 | Nginx + Spring Boot jar | 前后端分离部署 |

## 2. 前端技术选择

选择 Vue 3 + Vite + TypeScript。

理由：

- 适合快速建设业务型管理界面。
- Vite 启动快，工程复杂度低。
- Vue 3 Composition API 便于组织对话流、SSE、表格和表单状态。
- TypeScript 可以约束 API 响应、SSE 事件和表格数据结构。

建议依赖：

- `vue`
- `vue-router`
- `pinia`
- `axios` 或原生 `fetch`
- UI 组件库可选 Element Plus；如引入，统一使用其表单、表格、上传、消息提示。

## 3. 后端技术选择

选择 Java 17 + Spring Boot 3.x + Maven。

理由：

- 符合 Java 生产级后端和政务系统常见技术栈。
- Spring Boot 生态覆盖 Web、Validation、Security、Data、Actuator、Observability。
- Maven 适合企业环境依赖管理和 CI。
- AgentScope Java 可承接 ReAct、PlanNotebook、工具调用、人机协同和观测能力。

建议依赖方向：

- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa` 或 MyBatis，根据团队习惯选择一种
- `spring-boot-starter-actuator`
- PostgreSQL JDBC Driver
- Flyway
- AgentScope Java
- OpenTelemetry SDK / Spring Boot Starter
- SQL Parser，建议 JSqlParser 或 Apache Calcite

当前 OpenAPI 配置：

- Springdoc UI 路径：`/swagger-ui.html`。
- OpenAPI JSON 路径：`/api-docs`。
- 文档包含智能体会话、知识库、数据目录、智能问数、法制审核、审计接口。
- MVP 请求头 `X-User-Id`、`X-User-Role`、`X-User-Region` 已作为公共参数说明。

## 4. 数据库与存储

MVP 默认 PostgreSQL + pgvector。

PostgreSQL 存储：

- 用户、角色、区域、租户。
- 会话、消息、运行记录、计划、工具调用。
- 文档元数据、知识分块元数据。
- 数据目录、指标定义、问数记录。
- 风险事件、法制审核、审计日志。

pgvector 存储：

- 知识分块 embedding 独立存储在 `knowledge_chunk_embedding`，包含分块 ID、文档 ID、embedding 模型、向量维度、向量内容和创建时间。
- MVP 为兼容 H2 自动化测试，向量内容先以序列化文本存储，并在应用层计算余弦相似度。
- PostgreSQL profile 会额外加载 `classpath:db/postgresql-migration`，执行 `V10__pgvector_native_embedding.sql`：启用 `vector` 扩展、增加 `embedding_vector_pg vector` 原生列、回填历史向量，并创建 HNSW + `vector_cosine_ops` 索引。
- 文档索引提交后，后端会在 PostgreSQL 环境下异步于主事务同步原生 `vector` 列；若原生列不可用，不影响通用文本向量检索。
- 检索先按文档状态、分类和权限过滤，再融合关键词得分与向量相似度得分。

对象存储：

- MVP 可先使用本地受控目录保存原始文档。
- 生产建议接入 MinIO、政务云对象存储或文件存储。

## 5. 模型接入

后端定义统一 `ModelProvider`：

- `chat`：普通对话和 ReAct 推理。
- `streamChat`：流式回答。
- `embed`：文档和查询向量化。
- `structuredChat`：结构化输出和 NL2SQL。

适配目标：

- OpenAI-compatible API。
- DashScope。
- 私有化模型服务。

MVP 当前实现：

- `urban-agent.model.provider=openai-compatible`：默认通过 `/v1/chat/completions` 和 `/v1/embeddings` 兼容接口接入模型服务，结构化输出使用 chat completions 的 JSON 响应约束。
- `urban-agent.model.provider=mock`：仅用于明确指定的离线开发和自动化测试场景。
- `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`、`OPENAI_EMBEDDING_MODEL` 从环境变量读取，配置缺失或模型不可用时直接返回 `MODEL_UNAVAILABLE`，不自动切换到 mock。
- 模型调用写入 `model_call_record`，记录 provider、模型名、操作类型、粗略 token、耗时、状态和错误信息；操作类型包含 `CHAT`、`STRUCTURED_CHAT`、`EMBED`。
- 知识文档索引和知识检索都会通过 `embed` 生成向量，失败时索引流程进入文档失败状态，检索流程保留关键词召回能力。

要求：

- 模型密钥从环境变量或密钥系统读取。
- 记录模型名称、输入 token、输出 token、耗时和费用估算。
- 支持主备模型配置。
- 模型不可用时返回清晰错误提示，不生成本地模板回答。

## 6. SQL 与数据安全

NL2SQL 不得直接执行模型输出。

执行前必须经过：

1. 数据目录约束。
2. SQL AST 解析。
3. 只读语句校验。
4. 表、字段、函数白名单。
5. 租户、区域、部门权限改写。
6. 敏感字段脱敏。
7. 超时和行数限制。
8. 审计记录。

数据库账号必须为只读账号。禁止 DDL、DML、存储过程和未授权跨库访问。

## 7. 暂不引入的组件

MVP 暂不默认引入：

- Milvus：数据规模扩大后再替换或补充 pgvector。
- Elasticsearch/OpenSearch：关键词检索先由 PostgreSQL 文本检索或简单索引支撑，后续按检索质量升级。
- Kafka/RocketMQ：MVP 异步任务可先用数据库状态和线程池，复杂事件流后续引入消息队列。
- 完整微服务治理：MVP 单体内保留边界，试点稳定后拆分。
- Kubernetes：可容器化但不是 MVP 的必要前提。

## 8. 环境规划

| 环境 | 用途 | 特点 |
| --- | --- | --- |
| dev | 本地开发 | 可使用 mock 模型和 seed 数据 |
| test | 集成测试 | 使用测试数据库和测试模型配置 |
| staging | 试点预发 | 接近生产配置，验证权限、审计、性能 |
| prod | 生产 | 内网部署、审计保留、密钥管理、备份灾备 |

PostgreSQL profile 启动要求：

- 使用 `spring.profiles.active=postgres`。
- 数据库需安装 pgvector 扩展，或应用账号具备 `CREATE EXTENSION vector` 权限。
- `application-postgres.yml` 会同时加载通用迁移和 PostgreSQL 专属迁移；默认 H2 profile 不加载 pgvector SQL。
