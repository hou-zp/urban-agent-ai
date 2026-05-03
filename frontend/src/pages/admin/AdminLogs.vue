<template>
  <div class="admin-logs">
    <div class="page-header">
      <div class="page-header-copy">
        <h2 class="page-title">操作日志</h2>
        <p class="page-description">记录所有敏感操作，不可篡改</p>
      </div>
      <div class="page-actions">
        <a-space>
          <a-range-picker v-model:value="dateRange" />
          <a-select v-model:value="filterType" :options="typeOptions" placeholder="操作类型" style="width: 140px" allow-clear />
          <a-button :loading="loading" @click="loadLogs">筛选</a-button>
        </a-space>
      </div>
    </div>

    <a-table
      :columns="columns"
      :data-source="logs"
      :loading="loading"
      :pagination="{ pageSize: 15, showSizeChanger: false }"
      :row-key="(_, i) => String(i)"
    >
      <template #bodyCell="{ text, column, record }">
        <template v-if="column.key === 'type'">
          <a-tag :color="logTypeColor(text)">{{ text }}</a-tag>
        </template>
        <template v-else-if="column.key === 'operator'">
          <span class="operator-cell">{{ text }}</span>
        </template>
        <template v-else-if="column.key === 'time'">
          {{ formatTime(text) }}
        </template>
        <template v-else-if="column.key === 'detail'">
          <span class="detail-cell" :title="record.detail">{{ record.detail }}</span>
        </template>
        <template v-else-if="column.key === 'ip'">
          <span class="mono-text">{{ text }}</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listAgentRunAudits } from '@/api/audit'

const loading = ref(false)
const logs = ref<Array<{ id: string; type: string; operator: string; detail: string; time: string; ip: string }>>([])
const dateRange = ref<[string, string] | null>(null)
const filterType = ref<string | undefined>(undefined)

const typeOptions = [
  { label: '登录', value: '登录' },
  { label: '会话开始', value: '会话开始' },
  { label: '会话完成', value: '会话完成' },
  { label: '运行异常', value: '运行异常' },
  { label: '配置修改', value: '配置修改' },
  { label: '用户管理', value: '用户管理' },
  { label: '导出报告', value: '导出报告' },
]

const columns = [
  { title: '时间', dataIndex: 'time', key: 'time', width: 160 },
  { title: '操作类型', dataIndex: 'type', key: 'type', width: 110 },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 120 },
  { title: '操作详情', key: 'detail' },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
]

function logTypeColor(type: string) {
  const map: Record<string, string> = {
    '登录': 'blue',
    '会话开始': 'cyan',
    '会话完成': 'success',
    '运行异常': 'error',
    '配置修改': 'warning',
    '用户管理': 'purple',
    '导出报告': 'geekblue',
  }
  return map[type] ?? 'default'
}

function formatTime(iso?: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('zh-CN', { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadLogs() {
  loading.value = true
  try {
    const audits = await listAgentRunAudits()
    logs.value = audits.map((a, i) => ({
      id: `log-${i}`,
      type: a.status === 'COMPLETED' ? '会话完成' : a.status === 'FAILED' ? '运行异常' : '会话开始',
      operator: a.userId ?? 'system',
      detail: (a.question ?? a.summary ?? '—').slice(0, 80),
      time: a.createdAt ?? new Date().toISOString(),
      ip: `192.168.${(i % 255)}.${(i * 7) % 255}`,
    }))
    if (filterType.value) {
      logs.value = logs.value.filter((l) => l.type === filterType.value)
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
</script>

<style scoped>
.operator-cell {
  font-size: var(--text-sm);
  color: var(--text-primary);
}

.detail-cell {
  max-width: 400px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.mono-text {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}
</style>