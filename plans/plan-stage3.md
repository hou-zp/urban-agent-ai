# 阶段 3：前端增强

## 目标
完善前端证据展示和数据溯源能力。

## 范围

### 3.1 DataLineageCard 组件
- 数据溯源卡片，展示查询编号、数据来源、口径、更新时间
- 支持 queryId 可点击跳转

### 3.2 口径说明组件
- 展示统计口径说明
- 包含数据更新时间和权限状态

### 3.3 EvidenceCard 增强
- 支持 evidenceId 展示
- 支持来源类型区分（POLICY / LAW / BUSINESS_RULE）
- 支持文号、发文机关展示

### 3.4 ChatMessage 增强
- 集成 DataLineageCard
- 集成 EvidenceCard
- 展示风险等级提示

## 交付物清单

| # | 文件 | 类型 | 说明 |
|---|-----|------|------|
| 1 | `components/DataLineageCard.vue` | 组件 | 数据溯源卡片 |
| 2 | `components/CaliberExplanation.vue` | 组件 | 口径说明组件 |
| 3 | `components/EvidenceCard.vue` | 增强 | 增强证据卡片 |
| 4 | `components/RiskWarning.vue` | 组件 | 风险提示组件 |
| 5 | `components/ChatMessage.vue` | 增强 | 增强对话消息 |
| 6 | `types/agent.ts` | 类型 | 增强类型定义 |
| 7 | 单元测试 | Vitest | 组件测试 |

## 执行步骤

### Step 1: 定义增强类型

### Step 2: 开发 DataLineageCard 组件

### Step 3: 开发 CaliberExplanation 组件

### Step 4: 增强 EvidenceCard

### Step 5: 增强 ChatMessage

### Step 6: 测试验证

## 验收标准

- [x] DataLineageCard 展示查询编号、数据来源、口径、更新时间 ✅
- [x] EvidenceCard 支持来源类型和文号展示 ✅
- [x] RiskWarning 展示风险等级提示 ✅
- [x] 类型定义完成 ✅
- [x] ChatPage.vue 集成所有卡片组件 ✅
- [ ] 单元测试覆盖新组件（Vitest 环境配置待解决）

## 风险与回滚

- 风险：样式变更可能影响现有布局
- 缓解：保持向后兼容，提供 props 控制展示
- 回滚：可通过 git revert 恢复

## 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-05-03 | 阶段 3 组件开发完成 |
| 2026-05-03 | ChatPage.vue 集成 DataLineageCard、RiskWarning、CaliberExplanation |