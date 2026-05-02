<template>
  <section class="dashboard-page">
    <div class="page-hero">
      <div class="page-hero-copy">
        <p class="page-kicker">Data Query</p>
        <h2 class="page-title">智能问数</h2>
        <p class="page-description">
          输入业务问题后直接生成结果，系统会自动完成指标识别、授权范围处理和只读查询。您只需要关注口径说明、风险提示和最终数据。
        </p>
      </div>
      <div class="hero-actions">
        <a-button :loading="loadingCatalog" @click="loadCatalog">刷新目录</a-button>
        <a-button type="primary" :loading="syncing" @click="handleSyncCatalog">同步演示目录</a-button>
      </div>
    </div>

    <div class="stat-grid stat-grid-4">
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="指标口径" :value="metrics.length" />
        <div class="stat-footnote">当前可用于自然语言识别的指标定义</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="授权数据表" :value="tables.length" />
        <div class="stat-footnote">当前用户可访问的业务数据表</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="风险提示" :value="preview?.warnings.length ?? 0" />
        <div class="stat-footnote">识别阶段发现的风险与越权提醒</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="结果行数" :value="result?.rowCount ?? 0" />
        <div class="stat-footnote">{{ result ? `执行时间 ${formatDateTime(result.executedAt)}` : '尚未执行查询' }}</div>
      </a-card>
    </div>

    <div class="query-grid">
      <a-card class="section-card" :bordered="false" title="自然语言问数">
        <div class="notice-stack">
          <a-alert v-if="notice" type="success" show-icon :message="notice" />
          <a-alert v-if="error" type="error" show-icon :message="error" />
        </div>

        <div style="margin-top: 16px" class="section-stack">
          <a-textarea
            v-model:value="question"
            :rows="6"
            :maxlength="1000"
            placeholder="例如：统计本月各区占道经营案件数量，并按数量降序排列"
          />
          <a-button type="primary" :loading="running" :disabled="!question.trim()" @click="handleRunQuery">直接生成结果</a-button>
        </div>
      </a-card>

      <a-card class="section-card" :bordered="false" title="识别结果">
        <template #extra>
          <a-tag :color="result ? 'success' : preview ? 'processing' : 'warning'">
            {{ result ? '已完成' : preview ? '识别完成' : '待执行' }}
          </a-tag>
        </template>

        <template v-if="preview">
          <div class="section-stack">
            <a-descriptions :column="1" size="small" bordered>
              <a-descriptions-item label="识别指标">
                {{ preview.metricName || '未匹配指标' }}
                <span class="card-subtitle" v-if="preview.metricCode">（{{ preview.metricCode }}）</span>
              </a-descriptions-item>
              <a-descriptions-item label="统计口径">{{ preview.summary }}</a-descriptions-item>
              <a-descriptions-item label="授权范围">{{ preview.permissionRewrite || '未发生权限改写' }}</a-descriptions-item>
            </a-descriptions>

            <a-alert
              v-for="warning in preview.warnings"
              :key="warning"
              type="warning"
              show-icon
              :message="warning"
            />

            <a-alert
              v-if="!preview.validatedSql"
              type="info"
              show-icon
              message="当前问题暂未匹配到可直接执行的数据口径，请换个说法再试。"
            />
          </div>
        </template>
        <div v-else class="empty-shell">
          <a-empty description="完成一次问数后，识别指标、统计口径和授权范围会显示在这里。" />
        </div>
      </a-card>
    </div>

    <div class="catalog-grid">
      <a-card class="section-card" :bordered="false" title="指标口径">
        <template #extra>
          <a-tag color="blue">{{ metrics.length }} 个</a-tag>
        </template>

        <template v-if="metrics.length">
          <div class="metric-list">
            <div v-for="metric in metrics" :key="metric.metricCode" class="metric-item">
              <div class="detail-section-title">
                <strong>{{ metric.metricName }}</strong>
                <a-tag color="geekblue">{{ metric.metricCode }}</a-tag>
              </div>
              <div class="metric-item-meta">{{ metric.tableName }}</div>
              <p>{{ metric.description }}</p>
              <div class="field-tags">
                <span class="field-tag">{{ metric.aggregationExpr }}</span>
                <span class="field-tag">{{ metric.commonDimensions }}</span>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="empty-shell">
          <a-empty description="暂无指标，点击“同步演示目录”初始化。" />
        </div>
      </a-card>

      <a-card class="section-card" :bordered="false" title="授权数据表">
        <template #extra>
          <a-tag color="cyan">{{ tables.length }} 张</a-tag>
        </template>

        <template v-if="tables.length">
          <div class="table-tiles">
            <div v-for="table in tables" :key="table.tableName" class="table-tile">
              <div class="detail-section-title">
                <strong>{{ table.businessName }}</strong>
                <a-tag color="blue">{{ table.regionCode }}</a-tag>
              </div>
              <div class="table-tile-meta">{{ table.tableName }} · {{ table.permissionTag }}</div>
              <div class="field-tags">
                <span v-for="field in table.fields.slice(0, 6)" :key="field.fieldName" class="field-tag">
                  {{ field.businessName }}
                </span>
                <span v-if="table.fields.length > 6" class="field-tag">+{{ table.fields.length - 6 }}</span>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="empty-shell">
          <a-empty description="当前用户暂无授权数据表。" />
        </div>
      </a-card>
    </div>

    <a-card class="section-card" :bordered="false" title="查询结果">
      <template #extra>
        <a-tag v-if="result" color="success">{{ result.rowCount }} 行</a-tag>
      </template>

      <template v-if="result">
        <div class="section-stack">
          <a-alert type="info" show-icon :message="result.summary" />
          <a-table
            v-if="result.rows.length"
            :columns="resultTableColumns"
            :data-source="result.rows"
            :pagination="{ pageSize: 8, showSizeChanger: false }"
            :row-key="resultRowKey"
            :scroll="{ x: 'max-content' }"
          >
            <template #bodyCell="{ text }">
              {{ formatCell(text) }}
            </template>
          </a-table>
          <a-empty v-else description="当前条件下没有查询到数据。" />
        </div>
      </template>
      <div v-else class="empty-shell">
        <a-empty description="执行完成后会在这里展示结果表格和摘要说明。" />
      </div>
    </a-card>

    <a-card class="section-card" :bordered="false" title="业务明细结果">
      <template #extra>
        <a-space :size="8">
          <a-tag v-if="recordResult" color="purple">{{ recordResult.rows.length }} 行</a-tag>
          <a-tag v-if="recordResult?.recordQueryId" color="blue">Record {{ shortId(recordResult.recordQueryId) }}</a-tag>
        </a-space>
      </template>

      <div class="record-query-shell">
        <div class="record-query-grid">
          <a-select v-model:value="recordType" :options="recordTypeOptions" />
          <a-input v-model:value="recordKeyword" placeholder="关键字，例如 柯香、齐贤、办理中" />
          <a-input v-model:value="recordStreetName" placeholder="街道名称，可选" />
          <a-select v-model:value="recordStatus" allow-clear :options="recordStatusOptions" placeholder="状态过滤" />
          <a-input v-model:value="recordTimeRange" placeholder="时间范围，例如 2026-04 或 2026-04-01~2026-04-30" />
          <a-input-number v-model:value="recordLimit" :min="1" :max="50" :step="5" style="width: 100%" />
        </div>

        <div class="record-query-actions">
          <a-button
            type="primary"
            :loading="runningRecords"
            @click="handleRunRecordQuery"
          >
            查询业务明细
          </a-button>
          <span class="card-subtitle">明细字段完全以服务端返回为准，页面不自行做脱敏处理。</span>
        </div>

        <a-alert v-if="recordError" type="error" show-icon :message="recordError" />

        <template v-if="recordResult">
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="业务类型">{{ recordResult.businessName }}</a-descriptions-item>
            <a-descriptions-item label="来源表">{{ recordResult.tableName }}</a-descriptions-item>
            <a-descriptions-item label="授权标签">{{ recordResult.permissionTag }}</a-descriptions-item>
            <a-descriptions-item label="区域范围">{{ recordResult.regionCode || '全域' }}</a-descriptions-item>
          </a-descriptions>

          <PointMapPreview
            v-if="recordResult.recordType === 'POINT' && recordResult.recordQueryId"
            :record-query-id="recordResult.recordQueryId"
            :rows="recordResult.rows"
          />

          <div v-if="recordResult.maskedFields.length" class="field-tags">
            <span
              v-for="fieldName in recordResult.maskedFields"
              :key="fieldName"
              class="field-tag field-tag-warning"
            >
              已脱敏：{{ recordFieldLabel(fieldName) }}
            </span>
          </div>

          <a-table
            v-if="recordResult.rows.length"
            :columns="recordTableColumns"
            :data-source="recordResult.rows"
            :pagination="{ pageSize: 8, showSizeChanger: false }"
            :row-key="resultRowKey"
            :scroll="{ x: 'max-content' }"
          >
            <template #bodyCell="{ text, column }">
              {{ formatRecordCell(text, String(column.dataIndex)) }}
            </template>
          </a-table>
          <a-empty v-else description="当前条件下没有查询到业务明细。" />
        </template>
        <a-empty v-else description="按业务类型、关键字和状态筛选后，可在这里查看受控明细。" />
      </div>
    </a-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { executeDataQuery, listAuthorizedTables, listMetrics, previewDataQuery, queryBusinessRecords, syncDataCatalog } from '@/api/query'
import type {
  BusinessRecordQueryView,
  BusinessRecordType,
  DataTableView,
  MetricDefinitionView,
  QueryExecuteView,
  QueryPreviewView,
} from '@/types/api'
import { formatFriendlyCell } from '@/utils/queryPresentation'
import PointMapPreview from '@/components/PointMapPreview.vue'

const question = ref('请统计本月各区占道经营案件数量，并按数量降序排列')
const metrics = ref<MetricDefinitionView[]>([])
const tables = ref<DataTableView[]>([])
const preview = ref<QueryPreviewView | null>(null)
const result = ref<QueryExecuteView | null>(null)
const loadingCatalog = ref(false)
const syncing = ref(false)
const running = ref(false)
const runningRecords = ref(false)
const error = ref('')
const notice = ref('')
const recordError = ref('')
const recordResult = ref<BusinessRecordQueryView | null>(null)
const recordType = ref<BusinessRecordType>('MERCHANT')
const recordKeyword = ref('柯香')
const recordStreetName = ref('')
const recordStatus = ref<string | undefined>('online')
const recordTimeRange = ref('')
const recordLimit = ref(10)

const recordTypeOptions = [
  { label: '商户明细', value: 'MERCHANT' },
  { label: '案件明细', value: 'CASE' },
  { label: '点位巡查明细', value: 'POINT' },
  { label: '工单明细', value: 'WORK_ORDER' },
  { label: '地块明细', value: 'LAND_PLOT' },
] satisfies Array<{ label: string; value: BusinessRecordType }>

const recordStatusOptions = [
  { label: '异常/待处理', value: 'abnormal' },
  { label: '办理中', value: 'pending' },
  { label: '已办结/已恢复', value: 'resolved' },
  { label: '在线', value: 'online' },
  { label: '离线', value: 'offline' },
] satisfies Array<{ label: string; value: string }>

const resultColumns = computed(() => {
  const rows = result.value?.rows ?? []
  return Array.from(rows.reduce((columns, row) => {
    Object.keys(row).forEach((key) => columns.add(key))
    return columns
  }, new Set<string>()))
})
const resultTableColumns = computed(() => {
  return resultColumns.value.map((column) => ({
    title: column,
    dataIndex: column,
    key: column,
    width: 180,
  }))
})
const recordTableColumns = computed(() => {
  return (recordResult.value?.fields ?? []).map((field) => ({
    title: field.businessName,
    dataIndex: field.fieldName.toUpperCase(),
    key: field.fieldName,
    width: 180,
  }))
})

onMounted(() => {
  void loadCatalog()
})

async function loadCatalog() {
  loadingCatalog.value = true
  error.value = ''
  try {
    const [metricList, tableList] = await Promise.all([listMetrics(), listAuthorizedTables()])
    metrics.value = metricList
    tables.value = tableList
  } catch (err) {
    error.value = toMessage(err)
  } finally {
    loadingCatalog.value = false
  }
}

async function handleSyncCatalog() {
  syncing.value = true
  error.value = ''
  notice.value = ''
  try {
    const syncResult = await syncDataCatalog()
    notice.value = `已同步 ${syncResult.tableCount} 张表、${syncResult.metricCount} 个指标`
    await loadCatalog()
  } catch (err) {
    error.value = toMessage(err)
  } finally {
    syncing.value = false
  }
}

async function handleRunQuery() {
  if (!question.value.trim()) {
    return
  }

  running.value = true
  error.value = ''
  notice.value = ''
  preview.value = null
  result.value = null
  try {
    const previewResult = await previewDataQuery(question.value.trim())
    preview.value = previewResult

    if (!previewResult.validatedSql) {
      notice.value = '已完成口径识别，但当前问题暂未匹配到可直接执行的数据口径。'
      return
    }

    result.value = await executeDataQuery(question.value.trim(), previewResult.validatedSql)
    notice.value = '已自动完成指标识别、授权处理和结果生成。'
  } catch (err) {
    error.value = toMessage(err)
  } finally {
    running.value = false
  }
}

async function handleRunRecordQuery() {
  runningRecords.value = true
  recordError.value = ''
  try {
    recordResult.value = await queryBusinessRecords({
      recordType: recordType.value,
      keyword: recordKeyword.value.trim() || undefined,
      streetName: recordStreetName.value.trim() || undefined,
      status: recordStatus.value || undefined,
      timeRange: recordTimeRange.value.trim() || undefined,
      limit: recordLimit.value || 20,
    })
  } catch (err) {
    recordError.value = toMessage(err)
    recordResult.value = null
  } finally {
    runningRecords.value = false
  }
}

function formatCell(value: unknown) {
  if (value === null || value === undefined) {
    return '-'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function formatRecordCell(value: unknown, fieldName: string) {
  return formatFriendlyCell(value, fieldName)
}

function recordFieldLabel(fieldName: string) {
  const field = recordResult.value?.fields.find((item) => item.fieldName === fieldName)
  return field?.businessName || fieldName
}

function resultRowKey(_: Record<string, unknown>, index: number) {
  return String(index)
}

function shortId(value: string) {
  return value.length > 8 ? value.slice(0, 8) : value
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function toMessage(err: unknown) {
  return err instanceof Error ? err.message : '未知错误'
}
</script>

<style scoped>
.record-query-shell {
  display: grid;
  gap: 16px;
}

.record-query-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.record-query-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.field-tag-warning {
  background: #fff7ed;
  color: #b45309;
}

@media (max-width: 1080px) {
  .record-query-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .record-query-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .record-query-actions {
    align-items: flex-start;
  }
}
</style>
