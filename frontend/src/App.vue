<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <!-- JWT 认证入口浮在右上角 -->
    <div class="jwt-auth-widget" :class="{ 'is-minimal': authState.tokenSaved }">
      <a-tooltip title="身份配置">
        <a-button
          :type="authState.tokenSaved ? 'primary' : 'default'"
          size="small"
          shape="circle"
          @click="drawerOpen = true"
        >
          <template #icon>
            <KeyOutlined />
          </template>
        </a-button>
      </a-tooltip>
    </div>

    <!-- JWT 配置抽屉 -->
    <a-drawer
      v-model:open="drawerOpen"
      title="身份认证配置"
      placement="right"
      :width="400"
    >
      <a-alert
        type="info"
        show-icon
        message="填写 JWT 后使用 Bearer 鉴权；清空后恢复演示请求头。"
        style="margin-bottom: var(--space-5)"
      />
      <a-space direction="vertical" size="middle" style="width: 100%">
        <a-form layout="vertical">
          <a-form-item label="JWT Token">
            <a-textarea
              v-model:value="form.token"
              :rows="5"
              placeholder="粘贴 JWT token"
            />
          </a-form-item>
          <a-form-item label="用户 ID">
            <a-input v-model:value="form.userId" placeholder="演示模式用户 ID" />
          </a-form-item>
          <a-form-item label="角色">
            <a-input v-model:value="form.role" placeholder="例如 ADMIN" />
          </a-form-item>
          <a-form-item label="区域">
            <a-input v-model:value="form.region" placeholder="例如 shaoxing-keqiao" />
          </a-form-item>
        </a-form>
        <a-space>
          <a-button type="primary" block @click="saveAuth">保存并刷新</a-button>
          <a-button block @click="clearAuth">清空</a-button>
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
import { KeyOutlined } from '@ant-design/icons-vue'
import { STORAGE_KEYS } from '@/api/client'

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

const themeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#1a56db',
    colorSuccess: '#059669',
    colorWarning: '#d97706',
    colorError: '#dc2626',
    colorInfo: '#1a56db',
    borderRadius: 8,
    fontFamily: '"PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif',
    colorBgLayout: '#f0f4f8',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorText: '#0f172a',
    colorTextSecondary: '#475569',
    colorBorder: '#dde3ed',
    colorBorderSecondary: '#e8eef5',
    boxShadowRadius: 8,
    boxShadow: '0 1px 3px rgba(15, 23, 42, 0.08), 0 1px 2px rgba(15, 23, 42, 0.04)',
    boxShadowSecondary: '0 4px 6px rgba(15, 23, 42, 0.05), 0 2px 4px rgba(15, 23, 42, 0.04)',
  },
}

function readStorage(key: string): string {
  return window.localStorage.getItem(key)?.trim() ?? ''
}

function writeStorage(key: string, value: string) {
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
  message.success('身份配置已保存，正在刷新')
  window.setTimeout(() => window.location.reload(), 800)
}

function clearAuth() {
  form.token = ''
  Object.values(STORAGE_KEYS).forEach((key) => {
    if (key !== STORAGE_KEYS.userId && key !== STORAGE_KEYS.role && key !== STORAGE_KEYS.region) {
      window.localStorage.removeItem(key)
    }
  })
  message.success('已清空 JWT 认证')
  drawerOpen.value = false
}
</script>

<style scoped>
.jwt-auth-widget {
  position: fixed;
  top: calc(var(--header-height) + var(--space-4));
  right: var(--space-4);
  z-index: var(--z-drawer);
}

.jwt-auth-widget .ant-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  transition: all var(--duration-normal) var(--ease-default);
}

.jwt-auth-widget .ant-btn:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary-border);
}

.jwt-auth-widget.is-minimal .ant-btn {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--text-inverse);
}
</style>