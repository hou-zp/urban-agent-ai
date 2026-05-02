<template>
  <div :class="['risk-warning', `risk-warning--${riskLevel}`]">
    <div class="risk-warning__header">
      <span class="risk-warning__icon">{{ icon }}</span>
      <span class="risk-warning__title">{{ title }}</span>
      <span v-if="requiresReview" class="risk-warning__badge">待审核</span>
    </div>

    <p v-if="message" class="risk-warning__message">{{ message }}</p>

    <div v-if="riskCategories && riskCategories.length > 0" class="risk-warning__categories">
      <span v-for="cat in riskCategories" :key="cat" class="risk-warning__category">
        {{ cat }}
      </span>
    </div>

    <div v-if="reviewStatus" class="risk-warning__review-status">
      <span>审核状态：{{ reviewStatusText }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RiskLevel } from '@/types/api'

const props = defineProps<{
  riskLevel: RiskLevel
  riskCategories?: string[]
  message?: string
  requiresReview?: boolean
  reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | null
}>()

const icon = computed(() => {
  switch (props.riskLevel) {
    case 'LOW': return 'ℹ️'
    case 'MEDIUM': return '⚠️'
    case 'HIGH': return '🚨'
    case 'CRITICAL': return '⛔'
    default: return '❓'
  }
})

const title = computed(() => {
  switch (props.riskLevel) {
    case 'LOW': return '低风险'
    case 'MEDIUM': return '中风险'
    case 'HIGH': return '高风险'
    case 'CRITICAL': return '极高风险'
    default: return '未知风险'
  }
})

const reviewStatusText = computed(() => {
  switch (props.reviewStatus) {
    case 'PENDING': return '待审核'
    case 'APPROVED': return '已通过'
    case 'REJECTED': return '已驳回'
    default: return '未知'
  }
})
</script>

<style scoped>
.risk-warning {
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 12px;
}

.risk-warning--LOW {
  background: #ecfdf5;
  border: 1px solid #6ee7b7;
  color: #065f46;
}

.risk-warning--MEDIUM {
  background: #fef9c3;
  border: 1px solid #fcd34d;
  color: #854d0e;
}

.risk-warning--HIGH {
  background: #fef2f2;
  border: 1px solid #fca5a5;
  color: #991b1b;
}

.risk-warning--CRITICAL {
  background: #7f1d1d;
  border: 1px solid #991b1b;
  color: #fef2f2;
}

.risk-warning__header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.risk-warning__icon {
  font-size: 14px;
}

.risk-warning__title {
  font-weight: 600;
}

.risk-warning__badge {
  margin-left: auto;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.1);
  font-size: 11px;
}

.risk-warning__message {
  margin: 0 0 6px 0;
  line-height: 1.5;
}

.risk-warning__categories {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.risk-warning__category {
  padding: 2px 6px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.08);
  font-size: 11px;
}

.risk-warning__review-status {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed currentColor;
  font-size: 11px;
  opacity: 0.8;
}
</style>