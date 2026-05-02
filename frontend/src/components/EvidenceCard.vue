<template>
  <article :class="['evidence-card', `evidence-card--${sourceType}`]">
    <div class="evidence-card__header">
      <div class="evidence-card__title-group">
        <div class="evidence-card__type-badge">
          <span class="evidence-card__type-icon">{{ typeIcon }}</span>
          <span class="evidence-card__type-label">{{ typeLabel }}</span>
        </div>
        <strong>{{ displayFileName }}</strong>
        <span v-if="citation.documentTitle && citation.documentTitle !== displayFileName">{{ citation.documentTitle }}</span>
      </div>
      <a
        v-if="citation.sourceUrl"
        class="evidence-card__link"
        :href="citation.sourceUrl"
        target="_blank"
        rel="noreferrer"
      >
        标准原文
      </a>
    </div>

    <div class="evidence-card__meta">
      <span v-if="citation.documentNumber">文号：{{ citation.documentNumber }}</span>
      <span v-if="citation.sectionTitle">章节：{{ citation.sectionTitle }}</span>
      <span>有效期：{{ effectivePeriod }}</span>
      <span>{{ citation.sourceOrg || '来源机关未标注' }}</span>
    </div>

    <p>{{ sanitizedSnippet }}</p>

    <div v-if="!effectiveAtQueryTime" class="evidence-card__expired-notice">
      ⚠️ 该文件已过期，请以最新版本为准
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageCitationView } from '@/types/api'

const props = defineProps<{
  citation: MessageCitationView
  sourceType?: 'POLICY' | 'LAW' | 'BUSINESS_RULE'
}>()

const citation = computed(() => props.citation)
const displayFileName = computed(() => citation.value.fileName || citation.value.documentTitle || '未命名依据')
const effectivePeriod = computed(() => {
  const start = citation.value.effectiveFrom || '未标注起始'
  const end = citation.value.effectiveTo || '长期有效'
  return `${start} 至 ${end}`
})
const effectiveAtQueryTime = computed(() => {
  if (!citation.value.effectiveTo) return true
  return new Date(citation.value.effectiveTo) > new Date()
})

const sourceType = computed(() => props.sourceType || inferSourceType())
const typeIcon = computed(() => {
  switch (sourceType.value) {
    case 'POLICY': return '📋'
    case 'LAW': return '⚖️'
    case 'BUSINESS_RULE': return '📖'
    default: return '📄'
  }
})
const typeLabel = computed(() => {
  switch (sourceType.value) {
    case 'POLICY': return '政策文件'
    case 'LAW': return '法律法规'
    case 'BUSINESS_RULE': return '业务规则'
    default: return '文档'
  }
})

function inferSourceType(): string {
  const category = citation.value.category?.toUpperCase() || ''
  if (category.includes('POLICY') || category.includes('政策')) return 'POLICY'
  if (category.includes('LAW') || category.includes('法规')) return 'LAW'
  if (category.includes('BUSINESS') || category.includes('业务')) return 'BUSINESS_RULE'
  return 'POLICY'
}

const sanitizedSnippet = computed(() => {
  return citation.value.snippet
    .replace(/https?:\/\/\S+/g, '')
    .replace(/[#*_`>]/g, '')
    .replace(/文件信息\s*-\s*/g, '')
    .replace(/来源链接：\s*/g, '')
    .replace(/参考来源：\s*/g, '')
    .replace(/\.{3}|…/g, '')
    .replace(/\s+/g, ' ')
    .trim()
})
</script>

<style scoped>
.evidence-card {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #e7edf4;
  border-radius: 8px;
  background: #ffffff;
}

.evidence-card--POLICY {
  border-left: 3px solid #2563eb;
}

.evidence-card--LAW {
  border-left: 3px solid #7c3aed;
}

.evidence-card--BUSINESS_RULE {
  border-left: 3px solid #059669;
}

.evidence-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.evidence-card__title-group {
  display: grid;
  gap: 4px;
}

.evidence-card__type-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f1f5f9;
  font-size: 11px;
  color: #64748b;
}

.evidence-card__type-icon {
  font-size: 12px;
}

.evidence-card__title-group strong {
  font-size: 14px;
  line-height: 1.5;
  color: #172b4d;
  word-break: break-word;
}

.evidence-card__title-group span {
  font-size: 12px;
  line-height: 1.5;
  color: #6b7785;
  word-break: break-word;
}

.evidence-card__link {
  flex-shrink: 0;
  font-size: 12px;
  color: #2563eb;
  text-decoration: none;
}

.evidence-card__link:hover {
  color: #1d4ed8;
}

.evidence-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: #526176;
}

.evidence-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}

.evidence-card__expired-notice {
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 4px;
  background: #fef3c7;
  color: #92400e;
  font-size: 12px;
}
</style>
