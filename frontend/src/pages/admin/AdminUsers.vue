<template>
  <div class="admin-users">
    <div class="page-header">
      <div class="page-header-copy">
        <h2 class="page-title">用户管理</h2>
        <p class="page-description">平台用户账号管理、角色分配与权限矩阵配置</p>
      </div>
      <div class="page-actions">
        <a-button type="primary" @click="openUserModal(null)">新增用户</a-button>
      </div>
    </div>

    <!-- 用户列表 -->
    <a-card class="section-card" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="users"
        :loading="loading"
        :pagination="{ pageSize: 12, showSizeChanger: false }"
        :row-key="(u) => u.userId"
      >
        <template #bodyCell="{ text, column, record }">
          <template v-if="column.key === 'role'">
            <a-select
              :value="text"
              :options="roleOptions"
              style="width: 110px"
              @change="(val) => updateRole(record.userId, val)"
            />
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="text === 'ACTIVE' ? 'success' : text === 'DISABLED' ? 'error' : 'warning'">
              {{ text === 'ACTIVE' ? '正常' : text === 'DISABLED' ? '禁用' : '待激活' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openUserModal(record)">编辑</a-button>
              <a-button type="link" size="small" danger @click="deleteUser(record.userId)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 用户编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingUser ? '编辑用户' : '新增用户'"
      :confirm-loading="saving"
      @ok="saveUser"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="用户 ID" :rules="[{ required: true, message: '请输入用户 ID' }]">
          <a-input v-model:value="form.userId" :disabled="!!editingUser" placeholder="登录账号" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="form.role" :options="roleOptions" placeholder="选择角色" />
        </a-form-item>
        <a-form-item label="数据区域">
          <a-input v-model:value="form.region" placeholder="例如 shaoxing-keqiao" />
        </a-form-item>
        <a-form-item v-if="!editingUser" label="初始密码">
          <a-input-password v-model:value="form.password" placeholder="留空则生成随机密码" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import { computed, onMounted, ref } from 'vue'
import { listUsers, createUser, updateUser, deleteUser as apiDeleteUser } from '@/api/admin'
import type { UserInfo } from '@/stores/auth'

const loading = ref(false)
const users = ref<UserInfo[]>([])
const modalOpen = ref(false)
const saving = ref(false)
const editingUser = ref<UserInfo | null>(null)

interface UserForm {
  userId: string
  role: string
  region: string
  password: string
}

const form = ref<UserForm>({ userId: '', role: 'USER', region: '', password: '' })

const roleOptions = [
  { label: '普通用户', value: 'USER' },
  { label: '审计员', value: 'AUDITOR' },
  { label: '管理员', value: 'ADMIN' },
]

const columns = [
  { title: '用户 ID', dataIndex: 'userId', key: 'userId' },
  { title: '角色', dataIndex: 'role', key: 'role', width: 130 },
  { title: '数据区域', dataIndex: 'region', key: 'region', width: 180 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 140 },
]

async function loadUsers() {
  loading.value = true
  try {
    users.value = await listUsers()
  } finally {
    loading.value = false
  }
}

function openUserModal(user: UserInfo | null) {
  editingUser.value = user
  if (user) {
    form.value = { userId: user.userId, role: user.role, region: user.region ?? '', password: '' }
  } else {
    form.value = { userId: '', role: 'USER', region: '', password: '' }
  }
  modalOpen.value = true
}

async function saveUser() {
  saving.value = true
  try {
    if (editingUser.value) {
      await updateUser(form.value)
      message.success('用户更新成功')
    } else {
      await createUser(form.value)
      message.success('用户创建成功')
    }
    modalOpen.value = false
    await loadUsers()
  } catch (err) {
    message.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    saving.value = false
  }
}

async function updateRole(userId: string, role: string) {
  try {
    await updateUser({ userId, role, region: '', password: '' })
    message.success('角色已更新')
  } catch (err) {
    message.error(err instanceof Error ? err.message : '更新失败')
  }
}

async function deleteUser(userId: string) {
  try {
    await apiDeleteUser(userId)
    message.success('用户已删除')
    await loadUsers()
  } catch (err) {
    message.error(err instanceof Error ? err.message : '删除失败')
  }
}

onMounted(loadUsers)
</script>

<style scoped>
</style>