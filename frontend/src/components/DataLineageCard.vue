<template>
  <div class="data-lineage-card">
    <div class="data-lineage-card__header">
      <div class="data-lineage-card__icon">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M2 4h12M2 8h8M2 12h10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </div>
      <span class="data-lineage-card__title">数据溯源</span>
      <span v-if="lineage.queryId" class="data-lineage-card__query-id">
        查询编号：{{ lineage.queryId }}
      </span>
    </div>

    <div class="data-lineage-card__body">
      <div class="data-lineage-card__row">
        <span class="data-lineage-card__label">数据来源</span>
        <span class="data-lineage-card__value">{{ lineage.dataSourceName }}</span>
      </div>

      <div class="data-lineage-card__row">
        <span class="data-lineage-card__label">统计口径</span>
        <span class="data-lineage-card__value">{{ lineage.caliber }}</span>
      </div>

      <div class="data-lineage-card__row">
        <span class="data-lineage-card__label">更新时间</span>
        <span class="data-lineage-card__value">{{ formatDateTime(lineage.dataUpdatedAt) }}</span>
      </div>

      <div v-if="lineage.permissionStatus" class="data-lineage-card__row">
        <span class="data-lineage-card__label">权限状态</span>
        <span :class="['data-lineage-card__value', 'data-lineage-card__permission', permissionClass]">
          {{ lineage.permissionStatus }}
        </span>
      </div>

      <div v-if="lineage.rowCount !== undefined" class="data-lineage-card__row">
        <span class="data-lineage-card__label">结果行数</span>
        <span class="data-lineage-card__value">{{ lineage.rowCount }} 条</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DataLineage } from '@/types/api'

const props = defineProps<{
  lineage: DataLineage
}>()

const permissionClass = computed(() => {
  const status = props.lineage.permissionStatus || ''
  if (status.includes('授权') || status.includes('AUTHORIZED')) {
    return 'data-lineage-card__permission--authorized'
  }
  if (status.includes('受限') || status.includes('LIMITED')) {
    return 'data-lineage-card__permission--limited'
  }
  if (status.includes('无权限') || status.includes('DENIED')) {
    return 'data-lineage-card__permission--denied'
  }
  return ''
})

function formatDateTime(dateStr: string): string {
  if (!dateStr) return '未知'
  try {
    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return dateStr
  }
}
</script>

<style scoped>
.data-lineage-card {
  padding: 12px 14px;
  border: 1px solid #e7edf4;
  border-radius: 8px;
  background: #f8fafc;
}

.data-lineage-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e7edf4;
}

.data-lineage-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2563eb;
}

.data-lineage-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #172b4d;
}

.data-lineage-card__query-id {
  margin-left: auto;
  font-size: 11px;
  color: #6b7785;
  font-family: monospace;
}

.data-lineage-card__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.data-lineage-card__row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
}

.data-lineage-card__label {
  flex-shrink: 0;
  width: 60px;
  color: #6b7785;
}

.data-lineage-card__value {
  color: #334155;
  word-break: break-word;
}

.data-lineage-card__permission--authorized {
  color: #059669;
}

.data-lineage-card__permission--limited {
  color: #d97706;
}

.data-lineage-card__permission--denied {
  color: #dc2626;
}
</style>
