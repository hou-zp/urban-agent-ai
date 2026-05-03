<template>
  <div class="page-container">
    <div class="page-header">
      <div class="page-header-copy">
        <div class="page-kicker">Audit Board</div>
        <h2 class="page-title">审计简表</h2>
        <p class="page-description">
          查看最近运行、工具调用、问数记录、风险事件和模型调用，适合日常巡检和交付验收回放。
        </p>
      </div>
      <div class="page-actions">
        <a-button type="primary" :loading="loading" @click="loadAudits">
          <ReloadOutlined /> 刷新审计
        </a-button>
      </div>
    </div>

    <div class="stat-grid stat-grid-4">
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="审计总量" :value="totalCount" />
        <div class="stat-footnote">运行、工具、问数、风险、模型合并统计</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="运行审计" :value="runs.length" />
        <div class="stat-footnote">智能体运行记录</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="问数审计" :value="dataAccess.length" />
        <div class="stat-footnote">自然语言问数与执行摘要</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="模型调用" :value="models.length" />
        <div class="stat-footnote">模型侧 token、耗时和状态</div>
      </a-card>
    </div>

    <a-card class="section-card" :bordered="false" title="筛选条件">
      <div class="filter-grid">
        <a-input v-model:value="filters.keyword" placeholder="搜索用户、问题、工具、错误摘要" />
        <a-select v-model:value="filters.status" :options="statusOptions" />
        <a-select v-model:value="filters.riskLevel" :options="riskOptions" />
      </div>
    </a-card>

    <a-alert v-if="error" type="error" show-icon :message="error" />

    <a-card class="section-card audit-tabs" :bordered="false">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="`${tab.label} ${tab.count}`" />
      </a-tabs>

      <a-table
        :columns="currentColumns"
        :data-source="activeRows"
        :row-key="rowKey"
        :pagination="{ pageSize: 8, showSizeChanger: false }"
        :scroll="{ x: 1180 }"
      >
        <template #bodyCell="{ column, record, text, index }">
          <template v-if="column.key === 'id'">
            {{ shortId(String(text ?? '')) }}
          </template>

          <template v-else-if="column.key === 'runId'">
            {{ shortId(String(text ?? '')) }}
          </template>

          <template v-else-if="column.key === 'providerModel'">
            {{ 'provider' in record ? `${record.provider} / ${record.modelName}` : '-' }}
          </template>

          <template v-else-if="column.key === 'resultSummary'">
            {{ 'resultSummary' in record ? record.resultSummary || record.executedSqlSummary || '-' : '-' }}
          </template>

          <template v-else-if="column.key === 'latencyMs'">
            {{ 'latencyMs' in record ? `${record.latencyMs}ms` : '-' }}
          </template>

          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor('status' in record ? record.status : '')">
              {{ statusLabel('status' in record ? record.status : '') }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'riskLevel'">
            <a-tag :color="riskColor('riskLevel' in record ? record.riskLevel : '')">
              {{ riskLabel('riskLevel' in record ? record.riskLevel : '') }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'reviewRequired'">
            {{ 'reviewRequired' in record ? (record.reviewRequired ? '需要' : '不需要') : '-' }}
          </template>

          <template v-else-if="column.key === 'createdAt' || column.key === 'completedAt'">
            {{ formatDateTime(text ? String(text) : null) }}
          </template>

          <template v-else-if="column.key === 'errorMessage'">
            {{ 'errorMessage' in record ? record.errorMessage || record.errorCode || '-' : '-' }}
          </template>

          <template v-else-if="column.key === 'index'">
            {{ index + 1 }}
          </template>

          <template v-else>
            {{ text ?? '-' }}
          </template>
        </template>
      </a-table>

      <div v-if="activeRows.length === 0 && !loading" class="empty-shell">
        <a-empty description="没有匹配的审计记录。" />
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  listAgentRunAudits,
  listDataAccessAudits,
  listModelCallAudits,
  listRiskEventAudits,
  listToolCallAudits,
} from '@/api/audit'
import { ReloadOutlined } from '@ant-design/icons-vue'
import type {
  AgentRunAuditView,
  ModelCallAuditView,
  QueryRecordAuditView,
  RiskEventAuditView,
  ToolCallAuditView,
} from '@/types/api'

type AuditTab = 'runs' | 'tools' | 'data' | 'risks' | 'models'
type AuditRow = AgentRunAuditView | ToolCallAuditView | QueryRecordAuditView | RiskEventAuditView | ModelCallAuditView

const runs = ref<AgentRunAuditView[]>([])
const tools = ref<ToolCallAuditView[]>([])
const dataAccess = ref<QueryRecordAuditView[]>([])
const risks = ref<RiskEventAuditView[]>([])
const models = ref<ModelCallAuditView[]>([])
const activeTab = ref<AuditTab>('runs')
const loading = ref(false)
const error = ref('')
const filters = ref({
  keyword: '',
  status: '',
  riskLevel: '',
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '成功', value: 'SUCCESS' },
]

const riskOptions = [
  { label: '全部风险', value: '' },
  { label: '低风险', value: 'LOW' },
  { label: '中风险', value: 'MEDIUM' },
  { label: '高风险', value: 'HIGH' },
]

const filteredRuns = computed(() => filterRows(runs.value))
const filteredTools = computed(() => filterRows(tools.value))
const filteredDataAccess = computed(() => filterRows(dataAccess.value))
const filteredRisks = computed(() => filterRows(risks.value))
const filteredModels = computed(() => filterRows(models.value))
const totalCount = computed(() => runs.value.length + tools.value.length + dataAccess.value.length + risks.value.length + models.value.length)

const tabs = computed<Array<{ key: AuditTab; label: string; count: number }>>(() => [
  { key: 'runs', label: '运行', count: filteredRuns.value.length },
  { key: 'tools', label: '工具', count: filteredTools.value.length },
  { key: 'data', label: '问数', count: filteredDataAccess.value.length },
  { key: 'risks', label: '风险', count: filteredRisks.value.length },
  { key: 'models', label: '模型', count: filteredModels.value.length },
])

const activeRows = computed<AuditRow[]>(() => {
  const rowMap: Record<AuditTab, AuditRow[]> = {
    runs: filteredRuns.value,
    tools: filteredTools.value,
    data: filteredDataAccess.value,
    risks: filteredRisks.value,
    models: filteredModels.value,
  }
  return rowMap[activeTab.value]
})

const currentColumns = computed(() => {
  const columns: Record<AuditTab, Array<Record<string, unknown>>> = {
    runs: [
      { title: '运行编号', dataIndex: 'id', key: 'id', width: 120 },
      { title: '用户', dataIndex: 'userId', key: 'userId', width: 120 },
      { title: '问题摘要', dataIndex: 'question', key: 'question' },
      { title: '模型', dataIndex: 'modelName', key: 'modelName', width: 180 },
      { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
      { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
      { title: '完成时间', dataIndex: 'completedAt', key: 'completedAt', width: 150 },
    ],
    tools: [
      { title: '工具调用', dataIndex: 'toolName', key: 'toolName', width: 160 },
      { title: '运行编号', dataIndex: 'runId', key: 'runId', width: 120 },
      { title: '参数摘要', dataIndex: 'inputSummary', key: 'inputSummary' },
      { title: '结果摘要', dataIndex: 'outputSummary', key: 'outputSummary' },
      { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
    ],
    data: [
      { title: '问数编号', dataIndex: 'id', key: 'id', width: 120 },
      { title: '用户', dataIndex: 'userId', key: 'userId', width: 120 },
      { title: '问题', dataIndex: 'question', key: 'question' },
      { title: '授权改写', dataIndex: 'permissionRewrite', key: 'permissionRewrite' },
      { title: '结果摘要', dataIndex: 'resultSummary', key: 'resultSummary' },
      { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
      { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
    ],
    risks: [
      { title: '风险编号', dataIndex: 'id', key: 'id', width: 120 },
      { title: '用户', dataIndex: 'userId', key: 'userId', width: 120 },
      { title: '问题摘要', dataIndex: 'question', key: 'question' },
      { title: '风险等级', dataIndex: 'riskLevel', key: 'riskLevel', width: 120 },
      { title: '分类', dataIndex: 'riskCategories', key: 'riskCategories' },
      { title: '触发原因', dataIndex: 'triggerReason', key: 'triggerReason' },
      { title: '审核', dataIndex: 'reviewRequired', key: 'reviewRequired', width: 100 },
      { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
    ],
    models: [
      { title: '调用编号', dataIndex: 'id', key: 'id', width: 120 },
      { title: '用户', dataIndex: 'userId', key: 'userId', width: 120 },
      { title: '模型', key: 'providerModel', width: 200 },
      { title: '操作', dataIndex: 'operation', key: 'operation', width: 160 },
      { title: 'Token', dataIndex: 'totalTokens', key: 'totalTokens', width: 100 },
      { title: '耗时', dataIndex: 'latencyMs', key: 'latencyMs', width: 100 },
      { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
      { title: '错误摘要', key: 'errorMessage' },
      { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
    ],
  }
  return columns[activeTab.value]
})

onMounted(() => {
  void loadAudits()
})

async function loadAudits() {
  loading.value = true
  error.value = ''
  try {
    const [runList, toolList, dataList, riskList, modelList] = await Promise.all([
      listAgentRunAudits(),
      listToolCallAudits(),
      listDataAccessAudits(),
      listRiskEventAudits(),
      listModelCallAudits(),
    ])
    runs.value = runList
    tools.value = toolList
    dataAccess.value = dataList
    risks.value = riskList
    models.value = modelList
  } catch (err) {
    error.value = err instanceof Error ? err.message : '未知错误'
  } finally {
    loading.value = false
  }
}

function filterRows<T extends AuditRow>(rows: T[]) {
  const keyword = filters.value.keyword.trim().toLowerCase()
  const status = filters.value.status
  const riskLevel = filters.value.riskLevel

  return rows.filter((row) => {
    const textMatched = !keyword || Object.values(row).some((value) => String(value ?? '').toLowerCase().includes(keyword))
    const rowStatus = 'status' in row ? row.status : ''
    const statusMatched = !status || rowStatus === status
    const rowRisk = 'riskLevel' in row ? row.riskLevel : ''
    const riskMatched = !riskLevel || rowRisk === riskLevel
    return textMatched && statusMatched && riskMatched
  })
}

function rowKey(record: AuditRow) {
  return record.id
}

function shortId(value: string | null) {
  if (!value) {
    return '-'
  }
  return value.length > 8 ? value.slice(0, 8) : value
}

function statusColor(value: string) {
  if (['COMPLETED', 'SUCCESS'].includes(value)) {
    return 'success'
  }
  if (['FAILED', 'CANCELLED', 'ERROR'].includes(value)) {
    return 'error'
  }
  if (['RUNNING', 'PENDING'].includes(value)) {
    return 'processing'
  }
  return 'default'
}

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    RUNNING: '运行中',
    COMPLETED: '已完成',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消',
    PENDING: '等待中',
  }
  return labels[value] ?? value
}

function riskColor(value: string) {
  if (value === 'HIGH') {
    return 'error'
  }
  if (value === 'MEDIUM') {
    return 'warning'
  }
  return 'success'
}

function riskLabel(value: string) {
  const labels: Record<string, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
  }
  return labels[value] ?? value
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>
