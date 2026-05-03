<template>
  <div class="page-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="page-header-copy">
        <div class="page-kicker">Knowledge Base</div>
        <h2 class="page-title">知识文档</h2>
        <p class="page-description">
          管理政策法规、执法规范与热线处置资料，支持文档录入、索引状态维护和相似片段检索。
        </p>
      </div>
      <div class="page-actions">
        <a-button :loading="loading" @click="loadDocuments">
          <ReloadOutlined /> 刷新列表
        </a-button>
      </div>
    </div>

    <div class="stat-grid stat-grid-4">
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="文档总数" :value="documents.length" />
        <div class="stat-footnote">当前库内已登记的文档条目</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="可检索文档" :value="activeDocumentCount" />
        <div class="stat-footnote">可直接参与引用和问答召回</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="索引处理中" :value="indexingDocumentCount" />
        <div class="stat-footnote">等待完成向量化或内容索引</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="检索命中" :value="searchResults.length" />
        <div class="stat-footnote">本轮检索返回的候选片段数量</div>
      </a-card>
    </div>

    <div class="knowledge-grid">
      <a-card class="section-card" :bordered="false" title="文档录入">
        <div class="notice-stack">
          <a-alert v-if="notice" type="success" show-icon :message="notice" />
          <a-alert v-if="error" type="error" show-icon :message="error" />
        </div>

        <a-form layout="vertical" style="margin-top: 16px">
          <a-row :gutter="16">
            <a-col :span="24">
              <a-form-item label="标题">
                <a-input v-model:value="form.title" :maxlength="160" placeholder="例如：城市管理执法事项办理规范" />
              </a-form-item>
            </a-col>

            <a-col :span="12">
              <a-form-item label="知识分类">
                <a-select v-model:value="form.category" :options="categoryOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="适用区域">
                <a-input v-model:value="form.regionCode" :maxlength="64" placeholder="city / district-a" />
              </a-form-item>
            </a-col>

            <a-col :span="12">
              <a-form-item label="发文机关">
                <a-input v-model:value="form.sourceOrg" :maxlength="120" placeholder="发文机关" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="文号">
                <a-input v-model:value="form.documentNumber" :maxlength="80" placeholder="文号" />
              </a-form-item>
            </a-col>

            <a-col :span="12">
              <a-form-item label="生效日期">
                <a-input v-model:value="form.effectiveFrom" :maxlength="20" placeholder="YYYY-MM-DD" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="失效日期">
                <a-input v-model:value="form.effectiveTo" :maxlength="20" placeholder="YYYY-MM-DD" />
              </a-form-item>
            </a-col>

            <a-col :span="24">
              <a-form-item label="摘要">
                <a-textarea
                  v-model:value="form.summary"
                  :rows="4"
                  :maxlength="500"
                  placeholder="用于列表快速识别，不替代正文索引"
                />
              </a-form-item>
            </a-col>

            <a-col :span="24">
              <a-form-item label="正文文件">
                <a-upload-dragger
                  accept=".txt,.md,.pdf,.doc,.docx"
                  :before-upload="beforeUpload"
                  :multiple="false"
                  :show-upload-list="false"
                >
                  <p class="ant-upload-drag-icon">
                    <InboxOutlined />
                  </p>
                  <p class="ant-upload-text">{{ selectedFile ? selectedFile.name : '点击或拖拽政策、法规、业务文档到此处' }}</p>
                  <p class="ant-upload-hint">上传后可在列表中触发索引，供回答引用和问数知识扩展使用。</p>
                </a-upload-dragger>
                <a-space v-if="selectedFile" style="margin-top: 12px">
                  <a-tag color="blue">{{ selectedFile.name }}</a-tag>
                  <a-button type="link" @click="clearSelectedFile">清空</a-button>
                </a-space>
              </a-form-item>
            </a-col>
          </a-row>

          <a-space>
            <a-button type="primary" :loading="uploading" :disabled="!canUpload" @click="handleUpload">上传文档</a-button>
            <a-button :disabled="uploading" @click="resetForm">重置表单</a-button>
          </a-space>
        </a-form>
      </a-card>

      <a-card class="section-card" :bordered="false" title="检索调试">
        <div class="search-toolbar">
          <a-select v-model:value="searchCategory" :options="searchCategoryOptions" />
          <a-input-search
            v-model:value="searchQuery"
            placeholder="输入政策问题或关键词"
            enter-button="检索"
            :loading="searching"
            @search="handleSearch"
          />
        </div>

        <div style="margin-top: 16px">
          <template v-if="searchResults.length">
            <div class="search-result-list">
              <div v-for="hit in searchResults" :key="`${hit.documentId}-${hit.sectionTitle}`" class="result-card">
                <div class="detail-section-title">
                  <strong>{{ hit.documentTitle }}</strong>
                  <a-tag color="geekblue">{{ formatSearchScore(hit.score) }}</a-tag>
                </div>
                <div class="status-chip-row">
                  <a-tag color="blue">{{ categoryLabel(hit.category) }}</a-tag>
                  <a-tag>{{ hit.sourceOrg || '未知机关' }}</a-tag>
                  <a-tag>{{ hit.documentNumber || '未标注文号' }}</a-tag>
                </div>
                <p>{{ hit.snippet }}</p>
              </div>
            </div>
          </template>
          <div v-else class="empty-shell">
            <a-empty description="检索结果会展示可引用片段、来源机关和相似度。" />
          </div>
        </div>
      </a-card>
    </div>

    <a-card class="section-card" :bordered="false" title="文档台账">
      <template #extra>
        <a-tag color="blue">{{ loading ? '加载中' : `${documents.length} 份` }}</a-tag>
      </template>

      <a-table
        :data-source="documents"
        row-key="id"
        :pagination="{ pageSize: 6, showSizeChanger: false }"
        :scroll="{ x: 1280 }"
      >
        <a-table-column title="标题" key="title">
          <template #default="{ record }">
            <div>
              <div><strong>{{ record.title }}</strong></div>
              <div class="card-subtitle">{{ record.fileName || '未记录文件名' }}</div>
            </div>
          </template>
        </a-table-column>

        <a-table-column title="发文机关" key="sourceOrg">
          <template #default="{ record }">
            {{ record.sourceOrg || '-' }}
          </template>
        </a-table-column>

        <a-table-column title="文号" key="documentNumber">
          <template #default="{ record }">
            {{ record.documentNumber || '-' }}
          </template>
        </a-table-column>

        <a-table-column title="分类" key="category">
          <template #default="{ record }">
            <a-tag color="blue">{{ categoryLabel(record.category) }}</a-tag>
          </template>
        </a-table-column>

        <a-table-column title="适用区域" key="regionCode">
          <template #default="{ record }">
            {{ record.regionCode || '-' }}
          </template>
        </a-table-column>

        <a-table-column title="生效 / 失效" key="effectiveRange">
          <template #default="{ record }">
            {{ formatDate(record.effectiveFrom) }} / {{ formatDate(record.effectiveTo) }}
          </template>
        </a-table-column>

        <a-table-column title="状态" key="status">
          <template #default="{ record }">
            <a-space direction="vertical" :size="4">
              <a-tag :color="documentStatusColor(record.status)">{{ documentStatusLabel(record.status) }}</a-tag>
              <span v-if="record.failedReason" class="card-subtitle">{{ record.failedReason }}</span>
            </a-space>
          </template>
        </a-table-column>

        <a-table-column title="更新时间" key="updatedAt">
          <template #default="{ record }">
            {{ formatDateTime(record.updatedAt) }}
          </template>
        </a-table-column>

        <a-table-column title="操作" key="actions" :width="240">
          <template #default="{ record }">
            <a-space wrap size="small">
              <a-button type="link" :loading="actingId === record.id" @click="handleIndex(record.id)">索引</a-button>
              <a-button type="link" :disabled="actingId === record.id" @click="handleStatus(record.id, 'ACTIVE')">启用</a-button>
              <a-button type="link" danger :disabled="actingId === record.id" @click="handleStatus(record.id, 'EXPIRED')">失效</a-button>
              <a-button type="link" danger :disabled="actingId === record.id" @click="handleStatus(record.id, 'ABOLISHED')">废止</a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { UploadProps } from 'ant-design-vue'
import { InboxOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  indexKnowledgeDocument,
  listKnowledgeDocuments,
  searchKnowledge,
  updateKnowledgeDocumentStatus,
  uploadKnowledgeDocument,
} from '@/api/knowledge'
import type {
  KnowledgeCategory,
  KnowledgeDocumentStatus,
  KnowledgeDocumentStatusValue,
  KnowledgeDocumentView,
  KnowledgeSearchHitView,
} from '@/types/api'

const categoryOptions: Array<{ label: string; value: KnowledgeCategory }> = [
  { label: '政策文件', value: 'POLICY' },
  { label: '法律法规', value: 'LAW' },
  { label: '业务规范', value: 'BUSINESS' },
]

const searchCategoryOptions = [{ label: '全部分类', value: '' }, ...categoryOptions]

const documents = ref<KnowledgeDocumentView[]>([])
const searchResults = ref<KnowledgeSearchHitView[]>([])
const selectedFile = ref<File | null>(null)
const loading = ref(false)
const uploading = ref(false)
const searching = ref(false)
const actingId = ref('')
const error = ref('')
const notice = ref('')
const searchQuery = ref('')
const searchCategory = ref<KnowledgeCategory | ''>('')
const form = ref({
  title: '',
  category: 'POLICY' as KnowledgeCategory,
  sourceOrg: '',
  documentNumber: '',
  effectiveFrom: '',
  effectiveTo: '',
  regionCode: 'city',
  summary: '',
})

const canUpload = computed(() => Boolean(form.value.title.trim() && form.value.category && selectedFile.value))
const activeDocumentCount = computed(() => documents.value.filter((item) => item.status.toUpperCase() === 'ACTIVE').length)
const indexingDocumentCount = computed(() => documents.value.filter((item) => item.status.toUpperCase() === 'INDEXING').length)

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  selectedFile.value = file as unknown as File
  return false
}

onMounted(() => {
  void loadDocuments()
})

async function loadDocuments() {
  loading.value = true
  error.value = ''
  try {
    documents.value = await listKnowledgeDocuments()
  } catch (err) {
    error.value = toMessage(err)
  } finally {
    loading.value = false
  }
}

async function handleUpload() {
  if (!selectedFile.value || !canUpload.value) {
    return
  }

  uploading.value = true
  error.value = ''
  notice.value = ''
  try {
    await uploadKnowledgeDocument({
      ...form.value,
      file: selectedFile.value,
    })
    notice.value = '文档已上传，可在台账中触发索引'
    resetForm()
    await loadDocuments()
  } catch (err) {
    error.value = toMessage(err)
  } finally {
    uploading.value = false
  }
}

async function handleIndex(documentId: string) {
  await runDocumentAction(documentId, () => indexKnowledgeDocument(documentId), '索引已提交')
}

async function handleStatus(documentId: string, status: KnowledgeDocumentStatus) {
  await runDocumentAction(documentId, () => updateKnowledgeDocumentStatus(documentId, status), '状态已更新')
}

async function handleSearch() {
  if (!searchQuery.value.trim()) {
    return
  }

  searching.value = true
  error.value = ''
  try {
    searchResults.value = await searchKnowledge(searchQuery.value.trim(), searchCategory.value || undefined)
  } catch (err) {
    error.value = toMessage(err)
  } finally {
    searching.value = false
  }
}

async function runDocumentAction(documentId: string, action: () => Promise<KnowledgeDocumentView>, message: string) {
  actingId.value = documentId
  error.value = ''
  notice.value = ''
  try {
    await action()
    notice.value = message
    await loadDocuments()
  } catch (err) {
    error.value = toMessage(err)
  } finally {
    actingId.value = ''
  }
}

function clearSelectedFile() {
  selectedFile.value = null
}

function resetForm() {
  form.value = {
    title: '',
    category: 'POLICY',
    sourceOrg: '',
    documentNumber: '',
    effectiveFrom: '',
    effectiveTo: '',
    regionCode: 'city',
    summary: '',
  }
  clearSelectedFile()
}

function categoryLabel(value: string) {
  const normalized = value.toUpperCase() as KnowledgeCategory
  return categoryOptions.find((option) => option.value === normalized)?.label ?? value
}

function documentStatusLabel(value: KnowledgeDocumentStatusValue) {
  const labels: Record<KnowledgeDocumentStatus, string> = {
    DRAFT: '草稿',
    INDEXING: '索引中',
    ACTIVE: '可检索',
    EXPIRED: '已失效',
    ABOLISHED: '已废止',
    FAILED: '索引失败',
  }
  return labels[value.toUpperCase() as KnowledgeDocumentStatus] ?? value
}

function documentStatusColor(value: KnowledgeDocumentStatusValue) {
  const colors: Record<KnowledgeDocumentStatus, string> = {
    DRAFT: 'default',
    INDEXING: 'processing',
    ACTIVE: 'success',
    EXPIRED: 'warning',
    ABOLISHED: 'error',
    FAILED: 'error',
  }
  return colors[value.toUpperCase() as KnowledgeDocumentStatus] ?? 'default'
}

function formatDate(value: string | null) {
  return value || '-'
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

function formatSearchScore(value: number) {
  if (value <= 1) {
    return `${Math.round(value * 100)}%`
  }
  return value.toFixed(2)
}

function toMessage(err: unknown) {
  return err instanceof Error ? err.message : '未知错误'
}
</script>
