# 阶段 2：答案协议强化

## 目标
落实"无查询不出数、无来源不定性、无权限不展示"的后端硬闸门。

## 范围

### 2.1 升级 FinalGuardrailService
- 支持 AnswerDraft 结构验证
- 拦截无 queryId 的数据声明
- 拦截无 evidenceId 的政策法规结论

### 2.2 新增 DataClaimDetector
- 从答案文本中提取数据声明
- 关联到 queryId
- 检测未验证声明

### 2.3 完善图表绑定
- ChartSpec 必须绑定 queryId
- dataset 必须来自业务查询结果

### 2.4 增强答案拦截策略
- 无数据源时返回安全答案模板
- 无法规来源时返回限制说明

## 交付物清单

| # | 文件 | 类型 | 说明 |
|---|-----|------|------|
| 1 | `agent/application/FinalGuardrailService.java` | 增强 | 支持 AnswerDraft 验证 |
| 2 | `agent/application/DataClaimDetector.java` | 新增 | 数据声明检测 |
| 3 | `agent/application/dto/GuardrailResult.java` | 新增 | 闸门检查结果 |
| 4 | `agent/application/dto/ChartSpec.java` | 新增 | 图表规范 |
| 5 | `agent/application/AnswerComposer.java` | 增强 | 集成数据声明检测 |
| 6 | 单元测试 | JUnit | 闸门逻辑验证 |

## 执行步骤

### Step 1: 创建 GuardrailResult 和 ChartSpec

### Step 2: 创建 DataClaimDetector

### Step 3: 升级 FinalGuardrailService

### Step 4: 集成到 AnswerComposer

### Step 5: 测试验证

## 验收标准

- [x] FinalGuardrailService 支持 AnswerDraft 验证 ✅
- [x] 无 queryId 的数据声明被拦截 ✅
- [x] 无 evidenceId 的法规结论被拦截 ✅
- [x] ChartSpec 绑定 queryId ✅
- [x] 单元测试覆盖闸门逻辑（15 个测试通过）✅

## 风险与回滚

- 风险：过度拦截影响用户体验
- 缓解：提供 safeAnswer 模板，明确说明限制
- 回滚：可通过 git revert 恢复

## 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-05-03 | 阶段 2 完成，全部测试通过 |