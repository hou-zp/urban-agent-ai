<template>
  <div class="admin-shell">
    <!-- 侧边菜单 220px 深色 -->
    <aside class="admin-sider" :class="{ 'is-collapsed': collapsed }">
      <!-- 品牌区 -->
      <div class="admin-brand">
        <div class="admin-brand__mark">
          <SettingOutlined />
        </div>
        <div v-if="!collapsed" class="admin-brand__text">
          <span class="admin-brand__title">管理后台</span>
          <span class="admin-brand__sub">城市风貌智能工作台</span>
        </div>
      </div>

      <!-- 侧边菜单 -->
      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="inline"
        theme="dark"
        class="admin-nav-menu"
        @click="({ key }) => handleNav(key)"
      >
        <a-menu-item key="dashboard">
          <template #icon><DashboardOutlined /></template>
          <span>管理概览</span>
        </a-menu-item>
        <a-menu-item key="audit">
          <template #icon><AuditOutlined /></template>
          <span>对话审计</span>
        </a-menu-item>
        <a-menu-item key="knowledge">
          <template #icon><BookOutlined /></template>
          <span>知识库管理</span>
        </a-menu-item>
        <a-menu-item key="users">
          <template #icon><TeamOutlined /></template>
          <span>用户管理</span>
        </a-menu-item>
        <a-menu-item key="config">
          <template #icon><SettingOutlined /></template>
          <span>系统配置</span>
        </a-menu-item>
        <a-menu-item key="logs">
          <template #icon><FileTextOutlined /></template>
          <span>操作日志</span>
        </a-menu-item>
      </a-menu>

      <!-- 底部返回工作台 -->
      <div class="admin-sider-footer">
        <a-button
          type="text"
          class="admin-back-btn"
          @click="router.push('/')"
        >
          <ArrowLeftOutlined />
          <span v-if="!collapsed">返回工作台</span>
        </a-button>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="admin-main">
      <!-- 顶部栏 -->
      <header class="admin-topbar">
        <div class="admin-topbar__left">
          <a-button
            type="text"
            class="admin-collapse-btn"
            @click="collapsed = !collapsed"
          >
            <MenuFoldOutlined v-if="!collapsed" />
            <MenuUnfoldOutlined v-else />
          </a-button>
          <span class="admin-topbar__page-title">{{ currentTitle }}</span>
        </div>
        <div class="admin-topbar__right">
          <a-dropdown placement="bottomRight" trigger="click">
            <a-button type="text" class="admin-user-btn">
              <UserOutlined />
              <span>{{ authStore.user?.userId || 'admin' }}</span>
              <DownOutlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile" @click="router.push('/profile')">
                  <UserOutlined /> 个人中心
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">
                  <LogoutOutlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="admin-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, RouterView, useRoute } from 'vue-router'
import {
  ArrowLeftOutlined,
  AuditOutlined,
  BookOutlined,
  DashboardOutlined,
  DownOutlined,
  FileTextOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const collapsed = ref(false)

const selectedKeys = ref<string[]>([route.name as string ?? 'dashboard'])

const titleMap: Record<string, string> = {
  dashboard: '管理概览',
  audit: '对话审计',
  knowledge: '知识库管理',
  users: '用户管理',
  config: '系统配置',
  logs: '操作日志',
}

const currentTitle = computed(() => titleMap[route.name as string] ?? '管理后台')

function handleNav(key: string) {
  selectedKeys.value = [key]
  router.push({ name: key })
}

function handleLogout() {
  authStore.logout()
}
</script>

<style scoped>
/* ===== 外壳 ===== */
.admin-shell {
  display: flex;
  min-height: 100vh;
  background: var(--bg-base);
}

/* ===== 侧边栏 220px 深色 ===== */
.admin-sider {
  width: 220px;
  min-height: 100vh;
  background: #1e293b;
  display: flex;
  flex-direction: column;
  transition: width var(--duration-normal) var(--ease-default);
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.admin-sider.is-collapsed {
  width: 64px;
}

/* ===== 品牌区 ===== */
.admin-brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  min-height: 60px;
  overflow: hidden;
}

.admin-brand__mark {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  color: var(--text-inverse);
  display: grid;
  place-items: center;
  font-size: var(--text-base);
  flex: 0 0 auto;
}

.admin-brand__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.admin-brand__title {
  font-size: var(--text-md);
  font-weight: var(--font-semibold);
  color: #f8fafc;
  line-height: var(--leading-tight);
  white-space: nowrap;
}

.admin-brand__sub {
  font-size: var(--text-xs);
  color: #94a3b8;
  margin-top: 2px;
  white-space: nowrap;
}

/* ===== 导航菜单 ===== */
.admin-nav-menu {
  flex: 1;
  background: transparent !important;
  border-inline-end: 0 !important;
  padding: var(--space-2);
}

.admin-nav-menu :deep(.ant-menu-item) {
  height: 40px;
  line-height: 40px;
  margin-block: 2px;
  border-radius: var(--radius-md);
  color: #cbd5e1 !important;
  font-size: var(--text-sm);
  transition: background var(--duration-fast) var(--ease-default),
              color var(--duration-fast) var(--ease-default);
}

.admin-nav-menu :deep(.ant-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #f8fafc !important;
}

.admin-nav-menu :deep(.ant-menu-item-selected) {
  background: var(--color-primary) !important;
  color: #ffffff !important;
}

.admin-nav-menu :deep(.ant-menu-item-selected .anticon) {
  color: #ffffff !important;
}

/* ===== 侧边栏底部 ===== */
.admin-sider-footer {
  padding: var(--space-3) var(--space-2);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.admin-back-btn {
  width: 100%;
  height: 36px;
  color: #94a3b8 !important;
  font-size: var(--text-sm);
  justify-content: flex-start;
  padding-left: var(--space-4);
  gap: var(--space-2);
}

.admin-back-btn:hover {
  color: #f8fafc !important;
  background: rgba(255, 255, 255, 0.06) !important;
}

/* ===== 主区域 ===== */
.admin-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* ===== 顶部栏 ===== */
.admin-topbar {
  height: 52px;
  padding: 0 var(--space-5);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  box-shadow: var(--shadow-xs);
}

.admin-topbar__left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.admin-collapse-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.admin-collapse-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.admin-topbar__page-title {
  font-size: var(--text-md);
  font-weight: var(--font-semibold);
  color: var(--text-primary);
}

.admin-topbar__right {
  display: flex;
  align-items: center;
}

.admin-user-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 32px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  color: var(--text-primary);
  font-size: var(--text-sm);
}

.admin-user-btn:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-bg);
}

/* ===== 内容区 ===== */
.admin-content {
  flex: 1;
  padding: var(--space-5);
  overflow-y: auto;
}
</style>