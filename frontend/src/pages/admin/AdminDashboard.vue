<template>
  <div class="admin-dashboard">
    <div class="page-header">
      <div class="page-header-copy">
        <h2 class="page-title">管理概览</h2>
        <p class="page-description">后台系统整体运行状态一览</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid stat-grid-4">
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="对话会话总数" :value="stats.sessions" />
        <div class="stat-footnote">系统历史会话累计</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="注册用户数" :value="stats.users" />
        <div class="stat-footnote">当前平台有效账号</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="知识文档" :value="stats.knowledge" />
        <div class="stat-footnote">已入库政策法规文件</div>
      </a-card>
      <a-card class="stat-card" :bordered="false">
        <a-statistic title="风险预警数" :value="stats.risks">
          <template #suffix>
            <span class="stat-unit">起</span>
          </template>
        </a-statistic>
        <div class="stat-footnote">近 30 天高风险事件</div>
      </a-card>
    </div>

    <!-- 最近活动 -->
    <div class="admin-grid-2">
      <a-card class="section-card" :bordered="false" title="近期会话">
        <template #extra>
          <router-link to="/admin/audit" class="card-link">查看全部</router-link>
        </template>
        <a-table
          :columns="sessionColumns"
          :data-source="recentSessions"
          :pagination="false"
          :row-key="(_, i) => String(i)"
          size="small"
        >
          <template #bodyCell="{ text, column }">
            <template v-if="column.key === 'status'">
              <a-tag :color="text === 'COMPLETED' ? 'success' : text === 'FAILED' ? 'error' : 'processing'">
                {{ text === 'COMPLETED' ? '已完成' : text === 'FAILED' ? '异常' : '进行中' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'time'">
              {{ formatTime(text) }}
            </template>
          </template>
        </a-table>
      </a-card>

      <a-card class="section-card" :bordered="false" title="系统操作日志">
        <template #extra>
          <router-link to="/admin/logs" class="card-link">查看全部</router-link>
        </template>
        <a-list
          :data-source="recentLogs"
          size="small"
          :loading="loadingLogs"
        >
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta :title="item.action" :description="item.detail" />
              <template #extra>
                <span class="log-time">{{ formatTime(item.time) }}</span>
              </template>
            </a-list-item>
          </template>
        </a-list>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listAgentRunAudits } from '@/api/audit'

const stats = ref({
  sessions: 0,
  users: 0,
  knowledge: 0,
  risks: 0,
})
const recentSessions = ref<Array<{ id: string; question: string; status: string; createdAt: string }>>([])
const recentLogs = ref<Array<{ id: string; action: string; detail: string; time: string }>>([])
const loadingLogs = ref(false)

const sessionColumns = [
  { title: '问题摘要', dataIndex: 'question', key: 'question' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '时间', dataIndex: 'createdAt', key: 'time', width: 140 },
]

onMounted(async () => {
  try {
    const audits = await listAgentRunAudits()
    recentSessions.value = audits.slice(0, 8).map((a) => ({
      id: a.runId ?? a.id ?? '',
      question: a.question ?? '—',
      status: a.status ?? 'PENDING',
      createdAt: a.createdAt ?? '',
    }))
    stats.value.sessions = audits.length

    recentLogs.value = audits.slice(0, 6).map((a, i) => ({
      id: `log-${i}`,
      action: a.status === 'COMPLETED' ? '会话完成' : a.status === 'FAILED' ? '运行异常' : '会话开始',
      detail: (a.question ?? a.summary ?? '—').slice(0, 40),
      time: a.createdAt ?? new Date().toISOString(),
    }))
    loadingLogs.value = false
  } catch {
    loadingLogs.value = false
  }
})

function formatTime(iso: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.stat-unit {
  font-size: var(--text-sm);
  font-weight: var(--font-normal);
  color: var(--text-secondary);
  margin-left: 4px;
}

.card-link {
  font-size: var(--text-sm);
  color: var(--color-primary);
}

.log-time {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  white-space: nowrap;
}
</style>