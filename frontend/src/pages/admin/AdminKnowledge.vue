<template>
  <div class="admin-knowledge">
    <div class="page-header">
      <div class="page-header-copy">
        <h2 class="page-title">知识库管理</h2>
        <p class="page-description">政策法规文件入库、检索测试与反馈修正</p>
      </div>
      <div class="page-actions">
        <a-button :loading="loadingDocs" @click="loadDocuments">刷新</a-button>
      </div>
    </div>

    <!-- 统计 -->
    <div class="stat-grid stat-grid-3" style="margin-bottom: var(--space-5)">
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="文档总数" :value="documents.length" />
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="已启用" :value="documents.filter(d => d.status === 'ACTIVE').length" />
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="草稿/待审" :value="documents.filter(d => d.status !== 'ACTIVE').length" />
      </a-card>
    </div>

    <!-- 文档列表 -->
    <a-card class="section-card" :bordered="false" title="文档列表">
      <template #extra>
        <a-tag color="blue">{{ documents.length }} 个</a-tag>
      </template>
      <a-table
        :columns="docColumns"
        :data-source="documents"
        :loading="loadingDocs"
        :pagination="{ pageSize: 10, showSizeChanger: false }"
        :row-key="(d) => d.id ?? Math.random().toString(36)"
        size="small"
      >
        <template #bodyCell="{ text, column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="text === 'ACTIVE' ? 'success' : text === 'DRAFT' ? 'warning' : 'default'">
              {{ text === 'ACTIVE' ? '已启用' : text === 'DRAFT' ? '草稿' : '已下架' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'version'">
            <span class="mono-text">v{{ text }}</span>
          </template>
          <template v-else-if="column.key === 'effectiveDate'">
            {{ formatDate(text) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="testRecall(record)">检索测试</a-button>
              <a-button type="link" size="small" @click="editDoc(record)">编辑</a-button>
            </a-space>
          </template>
          <template v-else-if="column.key === 'title'">
            <span class="title-cell" :title="text">{{ text }}</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 检索测试抽屉 -->
    <a-drawer
      v-model:open="recallOpen"
      title="向量检索测试"
      placement="right"
      :width="560"
    >
      <div class="recall-shell">
        <a-alert
          v-if="recallError"
          type="error"
          show-icon
          :message="recallError"
          style="margin-bottom: var(--space-4)"
        />
        <a-input-search
          v-model:value="recallQuery"
          placeholder="输入测试问题，查看召回的知识片段"
          size="large"
          @search="runRecallTest"
        >
          <template #enterButton>
            <a-button type="primary">测试召回</a-button>
          </template>
        </a-input-search>

        <a-divider />

        <div v-if="recallResults.length" class="recall-results">
          <div class="recall-results__title">召回结果 ({{ recallResults.length }} 条)</div>
          <div v-for="(hit, i) in recallResults" :key="i" class="recall-hit">
            <div class="recall-hit__header">
              <a-tag color="cyan">#{{ i + 1 }}</a-tag>
              <span class="recall-hit__doc">{{ hit.documentTitle }}</span>
              <span class="recall-hit__score">相似度 {{ ((hit.score ?? 0) * 100).toFixed(1) }}%</span>
            </div>
            <div class="recall-hit__text">{{ hit.excerpt ?? hit.content ?? '—' }}</div>
          </div>
        </div>
        <a-empty v-else description="输入问题后点击「测试召回」查看结果" />
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listKnowledgeDocuments, recallKnowledge } from '@/api/knowledge'
import type { KnowledgeDocumentView } from '@/types/api'

const loadingDocs = ref(false)
const documents = ref<KnowledgeDocumentView[]>([])
const recallOpen = ref(false)
const recallQuery = ref('')
const recallResults = ref<Array<{ documentTitle: string; excerpt: string; content?: string; score: number }>>([])
const recallError = ref('')
const currentDoc = ref<KnowledgeDocumentView | null>(null)

const docColumns = [
  { title: '文档标题', dataIndex: 'title', key: 'title' },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '生效日期', dataIndex: 'effectiveDate', key: 'effectiveDate', width: 110 },
  { title: '操作', key: 'action', width: 140 },
]

async function loadDocuments() {
  loadingDocs.value = true
  try {
    documents.value = await listKnowledgeDocuments()
  } finally {
    loadingDocs.value = false
  }
}

function testRecall(doc: KnowledgeDocumentView) {
  currentDoc.value = doc
  recallQuery.value = ''
  recallResults.value = []
  recallError.value = ''
  recallOpen.value = true
}

async function runRecallTest() {
  if (!recallQuery.value.trim()) return
  recallError.value = ''
  try {
    const results = await recallKnowledge(recallQuery.value.trim())
    recallResults.value = results.map((r: { documentTitle?: string; excerpt?: string; content?: string; score?: number }) => ({
      documentTitle: r.documentTitle ?? '—',
      excerpt: r.excerpt ?? r.content ?? '—',
      score: r.score ?? 0,
    }))
  } catch (err) {
    recallError.value = err instanceof Error ? err.message : '检索失败'
  }
}

function editDoc(_doc: KnowledgeDocumentView) {
  // 未来接入富文本编辑
}

function formatDate(d?: string) {
  if (!d) return '—'
  return d.slice(0, 10)
}

onMounted(loadDocuments)
</script>

<style scoped>
.mono-text {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.title-cell {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.recall-shell {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.recall-results__title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--text-secondary);
  margin-bottom: var(--space-3);
}

.recall-hit {
  padding: var(--space-3);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-3);
}

.recall-hit__header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.recall-hit__doc {
  flex: 1;
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recall-hit__score {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  font-family: var(--font-mono);
}

.recall-hit__text {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  font-family: var(--font-mono);
  line-height: var(--leading-relaxed);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}
</style>