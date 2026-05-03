# 城管 AI 智能体平台文档索引

本文档集用于指导 MVP 研发、联调、测试和试点验收。现有根目录 `PRD-urban-management-ai-agent.md` 保留为完整 PRD 原稿，本目录按研发落地视角拆分为独立文档。

## 文档列表

| 文档 | 用途 | 主要读者 |
| --- | --- | --- |
| [01-product-requirements.md](01-product-requirements.md) | 产品需求、范围、验收指标 | 产品、业务、测试、研发 |
| [02-user-flows.md](02-user-flows.md) | 用户流程和异常流程 | 产品、前端、后端、测试 |
| [03-implementation-architecture.md](03-implementation-architecture.md) | 总体实现方案和系统架构 | 架构、后端、前端、运维 |
| [04-tech-stack.md](04-tech-stack.md) | 技术栈、基础设施和选型边界 | 架构、研发、运维 |
| [05-frontend-guidelines.md](05-frontend-guidelines.md) | 前端页面、交互、状态和代码规范 | 前端、测试、产品 |
| [06-backend-architecture.md](06-backend-architecture.md) | 后端模块、接口、数据模型和安全边界 | 后端、测试、架构 |
| [07-implementation-plan.md](07-implementation-plan.md) | 阶段计划、交付物和验收标准 | 项目经理、研发、测试 |
| [08-task-breakdown.md](08-task-breakdown.md) | 可分派研发任务清单、依赖和验收标准 | 项目经理、研发、测试 |
| [09-acceptance-test-set.md](09-acceptance-test-set.md) | 验收测试集结构、模板和判定规则 | 测试、产品、业务、研发 |
| [10-security-test-report.md](10-security-test-report.md) | 安全测试范围、结果和当前边界 | 测试、安全、研发 |
| [11-performance-test-report.md](11-performance-test-report.md) | 本地压测结果和是否达到 P95 指标 | 测试、运维、研发 |
| [12-pilot-demo-acceptance.md](12-pilot-demo-acceptance.md) | 试点演示流程、验收判定和纪要结论 | 产品、业务、法制、数据、安全、运维 |
| [13-observability-dashboard-checklist.md](13-observability-dashboard-checklist.md) | 告警指标、阈值和仪表盘清单 | 运维、研发、测试 |
| [14-production-rollback-manual.md](14-production-rollback-manual.md) | 生产发布失败后的版本、配置、数据库回滚手册 | 运维、研发、值班负责人 |

## 已确认的关键决策

- 完整实现 Vue 3 前端：对话、知识文档、智能问数、审计简表、登录认证。
- 前端采用 Vue 3 + Vite + TypeScript，前后端分离部署。
- 后端采用 Java 17 + Spring Boot 3.x + AgentScope Java，模块化单体。
- 数据底座采用 PostgreSQL + pgvector。
- 智能问数支持完整 NL2SQL，SQL 必须经过数据目录约束、AST 校验、权限改写、只读执行和审计。
- 图表能力接入，查询结果自动推断并返回图表规格（折线/柱/饼/表格）。
- 认证权限支持 JWT + Header 认证，预留 OAuth2/OIDC。
- 智能体支持结构化计划执行、依赖补跑、法制审核链路、运行时 Guardrail。
- 全链路审计、OpenTelemetry 追踪、结构化日志、脱敏。
- 已通过生产级安全评审和 Adversarial Review。
