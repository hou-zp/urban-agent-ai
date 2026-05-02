# 阶段 1 详细实施方案

## 目标
扩展场景枚举至手册要求的 16 种，补齐缺失数据结构，完善答案协议基础。

## 范围

### 1.1 扩展场景枚举
- 现状：9 种场景
- 目标：16 种场景（覆盖手册要求的全部治理场景）

### 1.2 扩展 TaskType 枚举
- 现状：5 种任务类型
- 目标：11 种任务类型（覆盖工具调度、风险复核、闸门检查）

### 1.3 新增 AnswerDraft 数据结构
- 支持数据声明追踪
- 支持证据引用绑定
- 支持限制说明

### 1.4 补齐缺失数据库表
- ai_metric_def（指标定义）
- ai_data_source（数据源定义）
- ai_business_rule（业务规则配置）

### 1.5 升级 AnswerComposer
- 支持六段式答案结构
- 数据片段与 queryId 绑定

## 交付物清单

| # | 文件 | 类型 | 说明 |
|---|-----|------|------|
| 1 | `agent/application/dto/UrbanScene.java` | 枚举 | 16 种场景枚举 |
| 2 | `agent/application/dto/TaskType.java` | 枚举 | 11 种任务类型 |
| 3 | `agent/application/dto/AnswerDraft.java` | 记录 | 答案草稿结构 |
| 4 | `agent/application/dto/DataClaim.java` | 记录 | 数据声明结构 |
| 5 | `agent/application/dto/EvidenceRef.java` | 记录 | 证据引用结构 |
| 6 | `db/migration/V025__add_metric_def_table.sql` | SQL | 指标定义表 |
| 7 | `db/migration/V026__add_data_source_table.sql` | SQL | 数据源定义表 |
| 8 | `db/migration/V027__add_business_rule_table.sql` | SQL | 业务规则表 |
| 9 | `agent/application/AnswerComposer.java` | 增强 | 六段式答案生成 |
| 10 | `agent/application/TaskPlanner.java` | 增强 | 支持新 TaskType |
| 11 | `agent/application/dto/TaskType.java` | 增强 | 新增任务类型 |
| 12 | 测试用例 | JUnit | 场景枚举、TaskType、AnswerDraft 覆盖 |

## 执行步骤

### Step 1: 扩展 UrbanScene 枚举
1. 读取当前 `UrbanScene.java`
2. 新增 7 个场景
3. 验证编译通过

### Step 2: 扩展 TaskType 枚举
1. 读取当前 `TaskType.java`
2. 新增 6 种任务类型
3. 更新 `TaskPlanner` 支持新类型

### Step 3: 新增数据结构
1. 创建 `AnswerDraft.java`
2. 创建 `DataClaim.java`
3. 创建 `EvidenceRef.java`
4. 更新相关服务引用

### Step 4: 创建数据库迁移
1. 创建 `V025__add_metric_def_table.sql`
2. 创建 `V026__add_data_source_table.sql`
3. 创建 `V027__add_business_rule_table.sql`

### Step 5: 升级 AnswerComposer
1. 读取当前 `AnswerComposer.java`
2. 支持六段式结构
3. 绑定 queryId 和 evidenceId

### Step 6: 测试验证
1. 单元测试场景枚举
2. 单元测试 TaskType
3. 单元测试 AnswerDraft 序列化

## 验收标准

- [x] UrbanScene 从 9 种扩展至 18 种 ✅
- [x] TaskType 从 5 种扩展至 13 种 ✅
- [x] AnswerDraft 可正常序列化 ✅
- [x] 3 张数据库表创建成功 ✅
- [x] AnswerComposer 支持六段式输出 ✅
- [x] 单元测试覆盖新结构（26 个测试全部通过）✅

## 风险与回滚

- 风险：枚举变更可能影响现有代码引用
- 缓解：先添加新枚举，不删除旧枚举；使用 @Deprecated 标记待移除项
- 回滚：可通过 git revert 恢复；数据库迁移不可逆，需手动清理

## 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-05-03 | 阶段 1 完成，全部测试通过 |