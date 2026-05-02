<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <div class="jwt-auth-entry">
      <a-space size="small">
        <a-tag :color="authState.tokenSaved ? 'green' : 'default'">
          {{ authState.tokenSaved ? 'JWT 已启用' : '使用演示身份' }}
        </a-tag>
        <a-button type="primary" size="small" @click="drawerOpen = true">
          {{ authState.tokenSaved ? '管理 JWT' : '配置 JWT' }}
        </a-button>
      </a-space>
    </div>
    <a-drawer
      v-model:open="drawerOpen"
      title="本地验收身份"
      placement="right"
      :width="420"
    >
      <a-space direction="vertical" size="middle" style="width: 100%">
        <a-alert
          type="info"
          show-icon
          message="填写 JWT 后，页面会改为 Bearer 鉴权；清空后恢复演示请求头。"
        />
        <a-input
          v-model:value="form.userId"
          placeholder="演示模式用户 ID"
          :disabled="authState.tokenSaved"
        />
        <a-input
          v-model:value="form.role"
          placeholder="演示模式角色，例如 ADMIN"
          :disabled="authState.tokenSaved"
        />
        <a-input
          v-model:value="form.region"
          placeholder="演示模式区域，例如 shaoxing-keqiao"
          :disabled="authState.tokenSaved"
        />
        <a-textarea
          v-model:value="form.token"
          :rows="8"
          placeholder="粘贴 JWT token"
        />
        <a-space>
          <a-button type="primary" @click="saveAuth">
            保存并刷新
          </a-button>
          <a-button @click="clearAuth">
            清空并恢复演示身份
          </a-button>
        </a-space>
      </a-space>
    </a-drawer>
    <RouterView />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterView } from 'vue-router'
import { message } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import theme from 'ant-design-vue/es/theme'
import { STORAGE_KEYS } from '@/api/client'

const themeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#1f6feb',
    colorSuccess: '#1f8f5f',
    colorWarning: '#c9871a',
    colorError: '#c94b49',
    colorInfo: '#1f6feb',
    borderRadius: 8,
    fontFamily: '"PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif',
    colorBgLayout: '#f4f7fb',
    colorBgContainer: '#ffffff',
    colorText: '#182230',
  },
}

const drawerOpen = ref(false)
const form = reactive({
  token: readStorage(STORAGE_KEYS.token),
  userId: readStorage(STORAGE_KEYS.userId) || 'demo-user',
  role: readStorage(STORAGE_KEYS.role) || 'ADMIN',
  region: readStorage(STORAGE_KEYS.region) || 'shaoxing-keqiao',
})

const authState = computed(() => ({
  tokenSaved: Boolean(readStorage(STORAGE_KEYS.token)),
}))

function openAuthDrawer() {
  drawerOpen.value = true
}

onMounted(() => {
  if (typeof window === 'undefined') {
    return
  }
  window.addEventListener('urban-agent:open-auth-drawer', openAuthDrawer)
})

onBeforeUnmount(() => {
  if (typeof window === 'undefined') {
    return
  }
  window.removeEventListener('urban-agent:open-auth-drawer', openAuthDrawer)
})

function readStorage(key: string): string {
  if (typeof window === 'undefined') {
    return ''
  }
  return window.localStorage.getItem(key)?.trim() ?? ''
}

function writeStorage(key: string, value: string) {
  if (typeof window === 'undefined') {
    return
  }
  if (value.trim()) {
    window.localStorage.setItem(key, value.trim())
  } else {
    window.localStorage.removeItem(key)
  }
}

function saveAuth() {
  writeStorage(STORAGE_KEYS.token, form.token)
  writeStorage(STORAGE_KEYS.userId, form.userId)
  writeStorage(STORAGE_KEYS.role, form.role)
  writeStorage(STORAGE_KEYS.region, form.region)
  message.success('身份配置已保存，正在刷新页面')
  window.setTimeout(() => window.location.reload(), 250)
}

function clearAuth() {
  form.token = ''
  writeStorage(STORAGE_KEYS.token, '')
  writeStorage(STORAGE_KEYS.userId, form.userId)
  writeStorage(STORAGE_KEYS.role, form.role)
  writeStorage(STORAGE_KEYS.region, form.region)
  message.success('已恢复演示身份，正在刷新页面')
  window.setTimeout(() => window.location.reload(), 250)
}
</script>

<style scoped>
.jwt-auth-entry {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 1100;
}
</style>
