<template>
  <a-layout class="app-shell">
    <!-- 侧边栏 280px -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      class="app-sider"
      :width="280"
      :collapsed-width="64"
      :trigger="null"
      collapsible
      theme="light"
    >
      <!-- 品牌区 -->
      <div class="brand-panel">
        <div class="brand-logo">
          <div class="brand-logo__mark">
            <RobotOutlined />
          </div>
          <div v-if="!collapsed" class="brand-logo__text">
            <span class="brand-logo__name">城市风貌智能工作台</span>
            <span class="brand-logo__tagline">城管 AI 智能体平台</span>
          </div>
        </div>
      </div>

      <!-- 新建对话按钮 -->
      <div v-if="!collapsed" class="sider-new-btn-wrap">
        <a-button type="primary" block class="sider-new-btn" @click="handleNewSession">
          <PlusOutlined /> 新建对话
        </a-button>
      </div>

      <!-- 搜索历史 -->
      <div v-if="!collapsed" class="sider-search">
        <a-input
          v-model:value="searchKeyword"
          placeholder="搜索历史会话..."
          allow-clear
          @change="onSearchChange"
        >
          <template #prefix>
            <SearchOutlined style="color: var(--text-tertiary)" />
          </template>
        </a-input>
      </div>

      <!-- 历史会话列表 -->
      <div class="sider-history">
        <div
          v-for="group in filteredHistoryGroups"
          :key="group.label"
          class="sider-history-group"
        >
          <div class="sider-history-label">{{ group.label }}</div>
          <button
            v-for="session in group.sessions"
            :key="session.id"
            class="sider-history-item"
            :class="{ 'is-active': currentSessionId === session.id }"
            type="button"
            @click="handleSelectSession(session.id)"
          >
            <div class="sider-history-item__title">{{ session.title }}</div>
            <div class="sider-history-item__tags">
              <span
                v-for="tag in sessionTags(session)"
                :key="`${session.id}-${tag}`"
                class="sider-history-tag"
              >
                {{ tag }}
              </span>
            </div>
            <div class="sider-history-item__time">{{ sessionTimeLabel(session) }}</div>
          </button>
        </div>
      </div>

      <!-- 侧边栏底部 -->
      <div v-if="!collapsed" class="sider-footer">
        <div class="sider-footer__user">
          <div class="sider-footer__avatar">
            <UserOutlined />
          </div>
          <div class="sider-footer__info">
            <span class="sider-footer__user-id">{{ authStore.user?.userId || 'demo-user' }}</span>
            <span class="sider-footer__user-role">{{ authStore.user?.role || 'ADMIN' }}</span>
          </div>
        </div>
      </div>
    </a-layout-sider>

    <!-- 主内容区 -->
    <a-layout class="main-area">
      <!-- 顶部导航栏 48px -->
      <a-layout-header class="app-header">
        <div class="header-left">
          <a-button
            type="text"
            class="collapse-btn"
            @click="collapsed = !collapsed"
          >
            <MenuFoldOutlined v-if="!collapsed" />
            <MenuUnfoldOutlined v-else />
          </a-button>

          <nav class="breadcrumb" aria-label="当前位置">
            <span class="breadcrumb__item">{{ currentNav.breadcrumb }}</span>
          </nav>
        </div>

        <div class="header-right">
          <a-space :size="12">
            <!-- 管理后台入口：仅管理员/审计员可见 -->
            <a-tooltip v-if="isAdminRole" title="管理后台">
              <a-button type="text" class="header-admin-btn" @click="router.push('/admin')">
                <SettingOutlined />
                <span class="header-admin-btn__label">管理</span>
              </a-button>
            </a-tooltip>

            <a-tooltip title="查看接口文档">
              <a-button type="text" href="/swagger-ui.html" target="_blank">
                <ApiOutlined />
              </a-button>
            </a-tooltip>

            <a-divider type="vertical" style="height: 20px; margin: 0" />

            <!-- 用户下拉菜单 -->
            <a-dropdown placement="bottomRight" trigger="click">
              <a-button type="text" class="user-menu-btn">
                <template #icon>
                  <UserOutlined />
                </template>
                <span class="user-menu-btn__name">{{ authStore.user?.userId || 'demo-user' }}</span>
                <DownOutlined />
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="info" disabled>
                    <div class="user-menu-info">
                      <span>{{ authStore.user?.userId }}</span>
                      <a-tag size="small">{{ authStore.user?.role }}</a-tag>
                    </div>
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="swagger">
                    <ApiOutlined /> 查看接口文档
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="logout" @click="handleLogout">
                    <Logoutoutlined /> 退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </div>
      </a-layout-header>

      <!-- 内容 -->
      <a-layout-content class="app-content">
        <RouterView />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, RouterView, useRoute } from 'vue-router'
import {
  ApiOutlined,
  DownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  PlusOutlined,
  RobotOutlined,
  SearchOutlined,
  SettingOutlined,
  BookOutlined,
  AuditOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const collapsed = ref(false)

const searchKeyword = ref('')
const currentSessionId = ref<string | null>(null)

const isAdminRole = computed(() => {
  const role = authStore.user?.role
  return role === 'ADMIN' || role === 'AUDITOR'
})

const navItems = [
  {
    path: '/',
    label: '智能体对话',
    icon: MessageOutlined,
    breadcrumb: '智能体对话',
  },
  {
    path: '/knowledge',
    label: '知识文档',
    icon: BookOutlined,
    breadcrumb: '知识文档',
  },
  {
    path: '/audit',
    label: '审计简表',
    icon: AuditOutlined,
    breadcrumb: '审计简表',
    adminOnly: true,
  },
]

const visibleNavItems = computed(() => {
  return navItems.filter((n) => !n.adminOnly || isAdminRole.value)
})

const currentNav = computed(() => {
  const item = visibleNavItems.value.find((n) => n.path === route.path)
  return item ?? visibleNavItems.value[0]
})

interface SessionItem {
  id: string
  title: string
  tags: string[]
  updatedAt: string
}

const mockSessions: SessionItem[] = [
  { id: '1', title: '占道经营案件统计', tags: ['业务咨询'], updatedAt: new Date().toISOString() },
  { id: '2', title: '政策解读：市容管理条例', tags: ['政策解读'], updatedAt: new Date(Date.now() - 86400000).toISOString() },
  { id: '3', title: '法规咨询：处罚标准', tags: ['法规咨询'], updatedAt: new Date(Date.now() - 86400000 * 2).toISOString() },
]

const filteredHistoryGroups = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const filtered = keyword
    ? mockSessions.filter((s) => s.title.toLowerCase().includes(keyword))
    : mockSessions

  return [
    {
      label: '今天',
      sessions: filtered.filter((s) => {
        const d = new Date(s.updatedAt)
        const now = new Date()
        return d.toDateString() === now.toDateString()
      }),
    },
    {
      label: '昨天',
      sessions: filtered.filter((s) => {
        const d = new Date(s.updatedAt)
        const now = new Date()
        const yesterday = new Date(now)
        yesterday.setDate(yesterday.getDate() - 1)
        return d.toDateString() === yesterday.toDateString()
      }),
    },
    {
      label: '更早',
      sessions: filtered.filter((s) => {
        const d = new Date(s.updatedAt)
        const now = new Date()
        const yesterday = new Date(now)
        yesterday.setDate(yesterday.getDate() - 1)
        return d < yesterday
      }),
    },
  ].filter((g) => g.sessions.length > 0)
})

function sessionTags(session: SessionItem): string[] {
  return session.tags
}

function sessionTimeLabel(session: SessionItem): string {
  const d = new Date(session.updatedAt)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function onSearchChange() {
  // filter is reactive via computed
}

function handleNewSession() {
  currentSessionId.value = null
  router.push('/')
}

function handleSelectSession(id: string) {
  currentSessionId.value = id
  router.push('/')
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

/* ===== 侧边栏 280px ===== */
.app-sider.ant-layout-sider {
  background: var(--bg-surface) !important;
  border-inline-end: 1px solid var(--border-color) !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 品牌区 ===== */
.brand-panel {
  padding: var(--space-4) var(--space-4);
  border-bottom: 1px solid var(--border-color-light);
  min-height: var(--header-height);
  display: flex;
  align-items: center;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  width: 100%;
}

.brand-logo__mark {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  color: var(--text-inverse);
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  font-size: var(--text-lg);
  box-shadow: var(--shadow-primary);
}

.brand-logo__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-logo__name {
  font-size: var(--text-md);
  font-weight: var(--font-bold);
  color: var(--text-primary);
  line-height: var(--leading-tight);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-logo__tagline {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  line-height: var(--leading-normal);
  margin-top: 2px;
}

/* ===== 新建对话按钮 ===== */
.sider-new-btn-wrap {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border-color-light);
}

.sider-new-btn {
  height: 38px;
  font-weight: var(--font-medium);
}

/* ===== 搜索框 ===== */
.sider-search {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border-color-light);
}

/* ===== 历史会话列表 ===== */
.sider-history {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-2) 0;
}

.sider-history-group {
  margin-bottom: var(--space-1);
}

.sider-history-label {
  padding: var(--space-2) var(--space-4);
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  font-weight: var(--font-medium);
  letter-spacing: 0.04em;
}

.sider-history-item {
  width: 100%;
  padding: var(--space-2) var(--space-4);
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  border-left: 3px solid transparent;
  transition: background var(--duration-fast) var(--ease-default),
              border-color var(--duration-fast) var(--ease-default);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sider-history-item:hover {
  background: var(--bg-hover);
}

.sider-history-item.is-active {
  background: var(--color-primary-bg);
  border-left-color: var(--color-primary);
}

.sider-history-item__title {
  font-size: var(--text-sm);
  color: var(--text-primary);
  line-height: var(--leading-snug);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sider-history-item.is-active .sider-history-item__title {
  color: var(--color-primary-text);
  font-weight: var(--font-medium);
}

.sider-history-item__tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.sider-history-tag {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  background: var(--bg-inset);
  padding: 0 6px;
  border-radius: var(--radius-full);
  line-height: 18px;
}

.sider-history-item.is-active .sider-history-tag {
  background: var(--color-primary-border);
  color: var(--color-primary-text);
}

.sider-history-item__time {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

/* ===== 侧边栏底部用户信息 ===== */
.sider-footer {
  padding: var(--space-4);
  border-top: 1px solid var(--border-color-light);
}

.sider-footer__user {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--bg-inset);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color-light);
}

.sider-footer__avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.sider-footer__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.sider-footer__user-id {
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sider-footer__user-role {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

/* ===== 顶部导航栏 48px ===== */
.app-header.ant-layout-header {
  height: 48px;
  padding: 0 var(--space-5);
  background: var(--bg-surface) !important;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  box-shadow: var(--shadow-xs);
  line-height: 48px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.collapse-btn.ant-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.collapse-btn.ant-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.breadcrumb__item {
  font-size: var(--text-md);
  font-weight: var(--font-semibold);
  color: var(--text-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.header-admin-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 30px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.header-admin-btn:hover {
  border-color: var(--color-primary-border);
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

.header-admin-btn__label {
  font-size: var(--text-sm);
}

/* ===== 用户菜单 ===== */
.user-menu-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  height: 32px;
  background: var(--bg-surface);
  color: var(--text-primary);
  font-size: var(--text-sm);
}

.user-menu-btn:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-bg);
}

.user-menu-btn__name {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-1) 0;
}

/* ===== 内容区 ===== */
.main-area {
  min-height: 100vh;
}

.app-content.ant-layout-content {
  padding: var(--space-5);
  min-height: calc(100vh - 48px);
  background: var(--bg-base);
}
</style>