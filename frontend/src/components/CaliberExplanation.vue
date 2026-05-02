<template>
  <div class="caliber-explanation">
    <div class="caliber-explanation__header">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
        <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.5"/>
        <path d="M7 4v3.5M7 9.5v.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
      <span>口径说明</span>
    </div>

    <div class="caliber-explanation__content">
      <slot>
        <p class="caliber-explanation__text">{{ text }}</p>
      </slot>
    </div>

    <div v-if="showMeta" class="caliber-explanation__footer">
      <span v-if="dataSource" class="caliber-explanation__meta">
        来源：{{ dataSource }}
      </span>
      <span v-if="updatedAt" class="caliber-explanation__meta">
        更新：{{ formatDate(updatedAt) }}
      </span>
      <span v-if="scope" class="caliber-explanation__meta">
        范围：{{ scope }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  text?: string
  dataSource?: string
  updatedAt?: string
  scope?: string
  showMeta?: boolean
}

withDefaults(defineProps<Props>(), {
  text: '',
  dataSource: '',
  updatedAt: '',
  scope: '',
  showMeta: true
})

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  try {
    const date = new Date(dateStr)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
  } catch {
    return dateStr
  }
}
</script>

<style scoped>
.caliber-explanation {
  padding: 10px 12px;
  border: 1px dashed #d1d5db;
  border-radius: 6px;
  background: #fefce8;
  font-size: 12px;
}

.caliber-explanation__header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: #854d0e;
  font-weight: 500;
}

.caliber-explanation__content {
  color: #713f12;
  line-height: 1.5;
}

.caliber-explanation__text {
  margin: 0;
}

.caliber-explanation__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed #fde68a;
}

.caliber-explanation__meta {
  color: #a16207;
  font-size: 11px;
}
</style>