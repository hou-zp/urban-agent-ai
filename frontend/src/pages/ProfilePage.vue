<template>
  <div class="page-container">
    <div class="page-header">
      <div class="page-header-copy">
        <div class="page-kicker">Profile</div>
        <h2 class="page-title">个人中心</h2>
        <p class="page-description">
          查看个人信息、修改登录密码，以及查看近期操作日志。
        </p>
      </div>
    </div>

    <div class="profile-grid">
      <!-- 个人信息卡片 -->
      <a-card class="section-card" :bordered="false" title="基本信息">
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="用户 ID">
            {{ profile?.userId ?? '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="角色">
            <a-tag :color="roleColor">{{ profile?.role ?? '—' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="数据区域">
            {{ profile?.region ?? '—' }}
          </a-descriptions-item>
        </a-descriptions>
      </a-card>

      <!-- 修改密码 -->
      <a-card class="section-card" :bordered="false" title="修改密码">
        <a-alert
          v-if="notice"
          type="success"
          show-icon
          :message="notice"
          style="margin-bottom: var(--space-4)"
        />
        <a-alert
          v-if="pwdError"
          type="error"
          show-icon
          :message="pwdError"
          style="margin-bottom: var(--space-4)"
        />

        <a-form
          ref="pwdFormRef"
          layout="vertical"
          :model="pwdForm"
          :rules="pwdRules"
          @finish="handleChangePassword"
        >
          <a-form-item label="当前密码" name="oldPassword">
            <a-input-password
              v-model:value="pwdForm.oldPassword"
              placeholder="请输入当前密码"
              autocomplete="current-password"
            />
          </a-form-item>
          <a-form-item label="新密码" name="newPassword">
            <a-input-password
              v-model:value="pwdForm.newPassword"
              placeholder="请输入新密码（至少 6 位）"
              autocomplete="new-password"
            />
          </a-form-item>
          <a-form-item label="确认新密码" name="confirmPassword">
            <a-input-password
              v-model:value="pwdForm.confirmPassword"
              placeholder="请再次输入新密码"
              autocomplete="new-password"
            />
          </a-form-item>
          <a-space>
            <a-button type="primary" :loading="changingPwd" html-type="submit">
              确认修改
            </a-button>
            <a-button @click="resetPwdForm">重置</a-button>
          </a-space>
        </a-form>
      </a-card>
    </div>

    <!-- 操作日志 -->
    <a-card class="section-card" :bordered="false" title="操作日志">
      <template #extra>
        <a-tag color="blue">{{ logs.length }} 条</a-tag>
      </template>

      <template v-if="loadingLogs">
        <a-spin />
      </template>
      <template v-else-if="logs.length">
        <a-table
          :columns="logColumns"
          :data-source="logs"
          :pagination="{ pageSize: 8, showSizeChanger: false }"
          :row-key="(_, i) => String(i)"
          size="small"
        >
          <template #bodyCell="{ text, column }">
            <template v-if="column.key === 'type'">
              <a-tag :color="logTypeColor(text)">{{ text }}</a-tag>
            </template>
            <template v-else-if="column.key === 'time'">
              {{ formatTime(text) }}
            </template>
          </template>
        </a-table>
      </template>
      <a-empty v-else description="暂无操作日志" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { getCurrentUser, updatePassword } from '@/api/profile'
import { listAgentRunAudits } from '@/api/audit'
import type { UserInfo } from '@/stores/auth'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'

const profile = ref<UserInfo | null>(null)
const loadingLogs = ref(false)
const changingPwd = ref(false)
const notice = ref('')
const pwdError = ref('')
const pwdFormRef = ref<FormInstance | null>(null)

interface PwdForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const pwdForm = ref<PwdForm>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules: Record<string, Rule[]> = {
  oldPassword: [{ required: true, message: '请输入当前密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, message: '新密码至少 6 位' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码' },
    {
      validator: (_: unknown, value: string) => {
        if (value !== pwdForm.value.newPassword) {
          return Promise.reject(new Error('两次输入的密码不一致'))
        }
        return Promise.resolve()
      },
      trigger: 'change',
    },
  ],
}

interface LogItem {
  id: string
  type: string
  description: string
  time: string
}

const logs = ref<LogItem[]>([])

const logColumns = [
  { title: '操作类型', dataIndex: 'type', key: 'type', width: 120 },
  { title: '操作描述', dataIndex: 'description', key: 'description' },
  { title: '时间', dataIndex: 'time', key: 'time', width: 160 },
]

const roleColor = computed(() => {
  const map: Record<string, string> = {
    ADMIN: 'blue',
    AUDITOR: 'purple',
    USER: 'cyan',
  }
  return map[profile.value?.role ?? ''] ?? 'default'
})

onMounted(async () => {
  try {
    profile.value = await getCurrentUser()
  } catch {
    profile.value = null
  }
  await loadLogs()
})

async function loadLogs() {
  loadingLogs.value = true
  try {
    const audits = await listAgentRunAudits()
    logs.value = audits.slice(0, 50).map((a) => ({
      id: a.runId ?? a.id ?? Math.random().toString(36),
      type: a.status === 'COMPLETED' ? '会话完成' : a.status === 'FAILED' ? '运行异常' : '会话开始',
      description: a.question ?? a.summary ?? '—',
      time: a.createdAt ?? new Date().toISOString(),
    }))
  } catch {
    logs.value = []
  } finally {
    loadingLogs.value = false
  }
}

async function handleChangePassword() {
  changingPwd.value = true
  pwdError.value = ''
  notice.value = ''
  try {
    await updatePassword({
      oldPassword: pwdForm.value.oldPassword,
      newPassword: pwdForm.value.newPassword,
    })
    notice.value = '密码修改成功，请重新登录。'
    message.success('密码修改成功')
    resetPwdForm()
    setTimeout(() => {
      window.location.href = '/login'
    }, 1500)
  } catch (err) {
    pwdError.value = err instanceof Error ? err.message : '修改失败，请检查当前密码是否正确。'
  } finally {
    changingPwd.value = false
  }
}

function resetPwdForm() {
  pwdFormRef.value?.resetFields()
}

function logTypeColor(type: string) {
  const map: Record<string, string> = {
    会话完成: 'success',
    运行异常: 'error',
    会话开始: 'blue',
  }
  return map[type] ?? 'default'
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: var(--space-5);
  margin-bottom: var(--space-5);
}
</style>