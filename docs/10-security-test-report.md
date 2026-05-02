# 安全测试报告

本文档对应任务清单 `QA-402`，记录城管智能体 MVP 当前后端安全测试范围、执行方式和结果。

## 1. 测试对象

- 服务对象：Spring Boot 后端服务。
- 数据库：H2 集成测试库。
- 重点链路：ReAct 对话入口、智能问数 SQL 校验、知识库上传、法制审核、运行时限流、Actuator 运维端点、部署脚本敏感配置。
- 执行日期：2026-05-01。

## 2. 自动化覆盖范围

| 类别 | 覆盖项 | 验证方式 |
| --- | --- | --- |
| 智能问数 SQL 安全 | DML/DDL、JOIN、非白名单函数、缺少具体时间范围的事实表查询 | SQL 预览与执行接口集成测试 |
| 数据权限 | 非授权字段访问、跨区域数据访问、敏感字段出现在 `SELECT/WHERE/GROUP BY/ORDER BY/HAVING` | 用户上下文集成测试 |
| 提示词安全 | 提示词注入、系统提示词泄露、试图绕过规则的对话输入 | 对话接口集成测试 |
| 知识库安全 | 不允许的文件类型、超限文件、二进制载荷、注入型知识内容 | 知识上传接口集成测试 |
| 引用与法规可信度 | 无来源内容拒答、废止文档不得作为有效依据 | 知识检索与回答链路测试 |
| 法制审核权限 | 仅 `LEGAL/ADMIN` 可审核，且只能处理 `PENDING` 记录 | 法制审核接口集成测试 |
| 运行时保护 | 单次计划步数、单工具调用超时、并发流式请求用户上下文隔离 | 运行时保护集成测试 |
| 运维暴露面 | Actuator 仅暴露 health/info，未知路径返回统一 404 | 观测端点集成测试 |
| 部署敏感配置 | 默认数据库口令不得固化在部署配置和初始化 SQL 中 | 安全脚本扫描 |
| 验收安全题库 | 安全类验收问题数量、编号和分类有效性 | CSV 校验脚本 |

## 3. 自动化测试文件

- `backend/src/test/java/com/example/urbanagent/SecurityIntegrationTest.java`
- `backend/src/test/java/com/example/urbanagent/KnowledgeSecurityIntegrationTest.java`
- `backend/src/test/java/com/example/urbanagent/CitationRequirementIntegrationTest.java`
- `backend/src/test/java/com/example/urbanagent/LegalReviewIntegrationTest.java`
- `backend/src/test/java/com/example/urbanagent/RuntimeGuardIntegrationTest.java`
- `backend/src/test/java/com/example/urbanagent/ObservabilityIntegrationTest.java`

核心断言包括：

- `DELETE/UPDATE/INSERT/ALTER/DROP` 等危险 SQL 不得执行。
- `JOIN`、危险函数和无时间范围事实表查询必须在预览阶段拒绝。
- 非管理员不得读取 `reporter_phone` 等敏感字段。
- 区域用户不得访问其他区域巡查数据。
- 提示词注入和系统提示词泄露请求必须拒绝并记录风险事件。
- 不合规知识文件必须在上传阶段拒绝。
- 无来源回答必须拒答，废止文件不得作为有效引用。
- 非法制审核角色不得审核，非待审核记录不得再次流转。
- 运行时限流和超时必须返回统一错误码。
- Actuator 暴露面必须收敛，业务 404 必须返回统一响应结构。

## 4. 复验脚本

安全复验入口：

```bash
qa/security/verify_security_checks.sh
```

脚本会执行以下检查：

1. 扫描部署脚本、初始化 SQL、文档和 QA 目录，确认不存在已知默认数据库口令。
2. 校验 430 条验收测试集，其中安全类问题不少于 50 条。
3. 运行后端安全相关集成测试集合。

## 5. 本轮执行结果

| 项目 | 结果 |
| --- | --- |
| 危险 SQL 拦截 | 通过 |
| 敏感字段和跨区域访问拦截 | 通过 |
| 提示词注入拦截 | 通过 |
| 恶意知识上传拦截 | 通过 |
| 引用可信度约束 | 通过 |
| 法制审核权限约束 | 通过 |
| 运行时限流和用户上下文隔离 | 通过 |
| Actuator 暴露范围控制 | 通过 |
| 默认数据库口令清理扫描 | 通过 |
| 验收安全题库校验 | 通过 |

## 6. 已知边界

- 当前安全测试以后端接口为主，不含前端交互层验证。
- 当前测试库为 H2，不替代 staging PostgreSQL 的网络、账号和最小权限验证。
- 当前结论来自自动化安全回归，不等同于第三方渗透测试报告。

## 7. 结论

当前后端已经满足本轮 `QA-402` 的核心验收目标：危险 SQL、越权数据访问、提示词注入、恶意知识上传和关键运维暴露面均具备自动化拦截与复验入口。
