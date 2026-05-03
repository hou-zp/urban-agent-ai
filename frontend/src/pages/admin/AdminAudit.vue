<template>
  <div class="admin-audit">
    <div class="page-header">
      <div class="page-header-copy">
        <h2 class="page-title">对话审计</h2>
        <p class="page-description">完整追溯每次会话的意图解析、子任务执行与回答溯源</p>
      </div>
      <div class="page-actions">
        <a-space>
          <a-range-picker v-model:value="dateRange" :placeholder="['开始日期', '结束日期']" />
          <a-input v-model:value="keyword" placeholder="搜索问题摘要..." allow-clear style="width: 200px" />
          <a-button :loading="loading" @click="loadAudits">查询</a-button>
        </a-space>
      </div>
    </div>

    <!-- 列表 -->
    <a-table
      :columns="columns"
      :data-source="audits"
      :loading="loading"
      :pagination="{ pageSize: 12, showSizeChanger: false }"
      :row-key="(record) => record.runId ?? record.id ?? Math.random().toString(36)"
      @change="handleTableChange"
    >
      <template #bodyCell="{ text, column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(text)">{{ statusLabel(text) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'feedback'">
          <span v-if="record.feedbackStatus === 'liked'" class="feedback-badge feedback-badge--like">
            <LikeOutlined /> 满意
          </span>
          <span v-else-if="record.feedbackStatus === 'disliked'" class="feedback-badge feedback-badge--dislike">
            <DislikeOutlined /> 不满意
          </span>
          <span v-else class="text-tertiary">—</span>
        </template>
        <template v-else-if="column.key === 'time'">
          {{ formatTime(text) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openDetail(record)">查看详情</a-button>
        </template>
        <template v-else-if="column.key === 'question'">
          <span class="question-cell" :title="text">{{ text }}</span>
        </template>
      </template>
    </a-table>

    <!-- 详情抽屉 -->
    <a-drawer
      v-model:open="detailOpen"
      :title="`审计详情 · ${currentRecord?.question?.slice(0, 30) ?? ''}...`"
      placement="right"
      :width="680"
    >
      <template v-if="currentRecord">
        <div class="detail-section">
          <div class="detail-title">原始问题</div>
          <div class="detail-mono">{{ currentRecord.question }}</div>
        </div>

        <div class="detail-section">
          <div class="detail-title">会话状态</div>
          <a-tag :color="statusColor(currentRecord.status ?? '')">{{ statusLabel(currentRecord.status ?? '') }}</a-tag>
          <span class="detail-meta">Run ID: {{ currentRecord.runId ?? currentRecord.id ?? '—' }}</span>
        </div>

        <div class="detail-section" v-if="currentRecord.intentTree">
          <div class="detail-title">意图解析</div>
          <pre class="detail-json">{{ formatJson(currentRecord.intentTree) }}</pre>
        </div>

        <div class="detail-section" v-if="currentRecord.promptText">
          <div class="detail-title">Prompt 原文</div>
          <pre class="detail-mono detail-scroll">{{ currentRecord.promptText }}</pre>
        </div>

        <div class="detail-section" v-if="currentRecord.sqlText">
          <div class="detail-title">执行的 SQL</div>
          <pre class="detail-mono detail-scroll">{{ currentRecord.sqlText }}</pre>
        </div>

        <div class="detail-section" v-if="currentRecord.answer">
          <div class="detail-title">最终回答</div>
          <div class="detail-text">{{ currentRecord.answer }}</div>
        </div>

        <div class="detail-section" v-if="currentRecord.citations?.length">
          <div class="detail-title">引用溯源</div>
          <div v-for="(cit, i) in currentRecord.citations" :key="i" class="detail-citation">
            <strong>{{ cit.sectionTitle }}</strong>
            <div class="detail-citation__doc">{{ cit.documentTitle }}</div>
            <div class="detail-citation__excerpt">{{ cit.excerpt }}</div>
          </div>
        </div>

        <div class="detail-section" v-if="currentRecord.feedbackStatus">
          <div class="detail-title">用户反馈</div>
          <a-tag :color="currentRecord.feedbackStatus === 'liked' ? 'success' : 'error'">
            {{ currentRecord.feedbackStatus === 'liked' ? '满意' : '不满意' }}
          </a-tag>
          <span v-if="currentRecord.feedbackReason" class="detail-meta">{{ currentRecord.feedbackReason }}</span>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { LikeOutlined, DislikeOutlined } from '@ant-design/icons-vue'
import { listAgentRunAudits } from '@/api/audit'
import type { AgentRunAuditView } from '@/types/api'

const loading = ref(false)
const audits = ref<AgentRunAuditView[]>([])
const dateRange = ref<[string, string] | null>(null)
const keyword = ref('')
const detailOpen = ref(false)
const currentRecord = ref<AgentRunAuditView | null>(null)

const columns = [
  { title: '问题摘要', dataIndex: 'question', key: 'question', ellipsis: true },
  { title: '用户', dataIndex: 'userId', key: 'userId', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '反馈', key: 'feedback', width: 90 },
  { title: '时间', dataIndex: 'createdAt', key: 'time', width: 140 },
  { title: '操作', key: 'action', width: 90 },
]

async function loadAudits() {
  loading.value = true
  try {
    const data = await listAgentRunAudits()
    let result = data
    if (keyword.value.trim()) {
      const kw = keyword.value.trim().toLowerCase()
      result = result.filter((a) => (a.question ?? '').toLowerCase().includes(kw))
    }
    audits.value = result
  } finally {
    loading.value = false
  }
}

function openDetail(record: AgentRunAuditView) {
  currentRecord.value = record
  detailOpen.value = true
}

function handleTableChange() {
  // pagination handled by ant table
}

function statusColor(s: string) {
  return s === 'COMPLETED' ? 'success' : s === 'FAILED' ? 'error' : 'processing'
}

function statusLabel(s: string) {
  return s === 'COMPLETED' ? '已完成' : s === 'FAILED' ? '异常' : '进行中'
}

function formatTime(iso?: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('zh-CN', { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function formatJson(obj: unknown) {
  try { return JSON.stringify(obj, null, 2) } catch { return String(obj) }
}

onMounted(loadAudits)
</script>

<style scoped>
.question-cell {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.feedback-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: var(--text-xs);
  padding: 1px 6px;
  border-radius: var(--radius-full);
}

.feedback-badge--like {
  color: var(--color-success);
  background: var(--color-success-bg);
}

.feedback-badge--dislike {
  color: var(--color-error);
  background: var(--color-error-bg);
}

.text-tertiary {
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

.detail-section {
  margin-bottom: var(--space-5);
}

.detail-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--text-secondary);
  margin-bottom: var(--space-2);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.detail-mono {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  background: var(--bg-inset);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-all;
}

.detail-scroll {
  max-height: 240px;
  overflow-y: auto;
}

.detail-json {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  background: var(--bg-inset);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  color: var(--color-primary-text);
  overflow: auto;
  max-height: 300px;
}

.detail-text {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--text-primary);
}

.detail-meta {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  margin-left: var(--space-2);
}

.detail-citation {
  padding: var(--space-3);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-2);
}

.detail-citation__doc {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  margin: 2px 0;
}

.detail-citation__excerpt {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>