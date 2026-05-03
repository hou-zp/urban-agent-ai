<template>
  <a-layout class="app-shell">
    <!-- 侧边栏 -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      class="app-sider"
      :width="sidebarWidth"
      :collapsed-width="collapsedWidth"
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
            <span class="brand-logo__name">Urban Agent</span>
            <span class="brand-logo__tagline">城管 AI 智能体平台</span>
          </div>
        </div>
      </div>

      <!-- 导航 -->
      <a-menu
        mode="inline"
        :selected-keys="[currentNav.path]"
        class="nav-menu"
        @click="({ key }) => router.push(key)"
      >
        <a-menu-item
          v-for="nav in navItems"
          :key="nav.path"
          class="nav-item"
        >
          <template #icon>
            <component :is="nav.icon" />
          </template>
          <span class="nav-item__label">{{ nav.label }}</span>
          <span v-if="nav.badge" class="nav-item__badge">{{ nav.badge }}</span>
        </a-menu-item>
      </a-menu>

      <!-- 侧边栏底部用户信息 -->
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
      <!-- 顶部栏 -->
      <a-layout-header class="app-header">
        <div class="header-left">
          <!-- 折叠按钮 -->
          <a-button
            type="text"
            class="collapse-btn"
            @click="collapsed = !collapsed"
          >
            <MenuFoldOutlined v-if="!collapsed" />
            <MenuUnfoldOutlined v-else />
          </a-button>

          <!-- 面包屑 -->
          <nav class="breadcrumb" aria-label="当前位置">
            <span class="breadcrumb__item">{{ currentNav.breadcrumb }}</span>
          </nav>
        </div>

        <div class="header-right">
          <!-- 快捷入口 -->
          <a-space :size="12">
            <a-tooltip title="查看接口文档">
              <a-button type="text" href="/swagger-ui.html" target="_blank">
                <ApiOutlined />
              </a-button>
            </a-tooltip>
            <a-divider type="vertical" style="height: 20px; margin: 0" />
            <!-- 用户菜单 -->
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
  AuditOutlined,
  BookOutlined,
  DownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const collapsed = ref(false)

const sidebarWidth = 248
const collapsedWidth = 64

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
  },
]

const currentNav = computed(() => {
  const item = navItems.find((n) => n.path === route.path)
  return item ?? navItems[0]
})

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
/* ===== 布局 ===== */
.app-shell {
  min-height: 100vh;
}

/* ===== 侧边栏 ===== */
.app-sider.ant-layout-sider {
  background: var(--bg-surface) !important;
  border-inline-end: 1px solid var(--border-color) !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 品牌区 ===== */
.brand-panel {
  padding: var(--space-4);
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
}

.brand-logo__tagline {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  line-height: var(--leading-normal);
  margin-top: 2px;
}

/* ===== 导航菜单 ===== */
.nav-menu {
  flex: 1;
  border-inline-end: 0 !important;
  background: transparent !important;
  padding: var(--space-2) var(--space-3);
  overflow-y: auto;
}

.nav-item {
  height: 40px !important;
  line-height: 40px !important;
  margin-block: 2px !important;
  border-radius: var(--radius-md) !important;
  font-size: var(--text-base) !important;
  display: flex;
  align-items: center;
  gap: var(--space-3) !important;
  transition: background var(--duration-fast) var(--ease-default),
              color var(--duration-fast) var(--ease-default);
}

.nav-item .anticon {
  font-size: 16px;
  flex: 0 0 auto;
}

.nav-item__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item__badge {
  background: var(--color-error);
  color: var(--text-inverse);
  font-size: var(--text-xs);
  padding: 0 6px;
  border-radius: var(--radius-full);
  line-height: 18px;
  flex: 0 0 auto;
}

/* ===== 侧边栏底部 ===== */
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

/* ===== 顶部栏 ===== */
.app-header.ant-layout-header {
  height: var(--header-height);
  padding: 0 var(--space-6);
  background: var(--bg-surface) !important;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  box-shadow: var(--shadow-xs);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
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
  gap: var(--space-4);
}

/* ===== 用户菜单 ===== */
.user-menu-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  height: 36px;
  background: var(--bg-surface);
  color: var(--text-primary);
  font-size: var(--text-sm);
}

.user-menu-btn:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-bg);
}

.user-menu-btn__name {
  max-width: 120px;
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
  padding: var(--space-6);
  min-height: calc(100vh - var(--header-height));
  background: var(--bg-base);
}
</style>