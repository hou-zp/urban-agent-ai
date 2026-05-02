<template>
  <a-layout class="app-shell">
    <a-layout-sider
      v-model:collapsed="collapsed"
      class="app-sider"
      :width="272"
      :collapsed-width="88"
      breakpoint="lg"
      theme="light"
    >
      <div class="brand-panel">
        <div class="brand-mark">
          <ClusterOutlined />
        </div>
        <div v-if="!collapsed" class="brand-copy">
          <span class="brand-kicker">Urban Agent</span>
          <h1>城管 AI 智能体平台</h1>
          <p>面向城管业务场景的智能体、知识、问数与审计工作台。</p>
        </div>
      </div>

      <a-menu mode="inline" :selected-keys="[currentNav.to]" class="nav-menu">
        <a-menu-item v-for="item in navItems" :key="item.to" @click="handleMenuClick(item.to)">
          <template #icon>
            <component :is="item.icon" />
          </template>
          <span>{{ item.label }}</span>
        </a-menu-item>
      </a-menu>

      <div v-if="!collapsed" class="sider-meta">
        <div class="meta-block">
          <span>演示用户</span>
          <strong>demo-user</strong>
        </div>
        <div class="meta-block">
          <span>授权角色</span>
          <strong>ADMIN / 绍兴市柯桥区</strong>
        </div>
        <a-button block type="default" href="/swagger-ui.html" target="_blank">
          查看接口文档
        </a-button>
      </div>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="app-header">
        <div class="header-left">
          <a-button class="collapse-button" type="text" @click="collapsed = !collapsed">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </a-button>
          <div>
            <div class="header-title">{{ currentNav.label }}</div>
            <div class="header-subtitle">{{ currentNav.desc }}</div>
          </div>
        </div>

        <a-space :size="12" wrap>
          <a-tag color="blue">演示环境</a-tag>
          <a-tag color="geekblue">ADMIN</a-tag>
          <a-tag color="green">绍兴市柯桥区</a-tag>
        </a-space>
      </a-layout-header>

      <a-layout-content class="app-content">
        <RouterView />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import {
  AuditOutlined,
  BookOutlined,
  ClusterOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)

const navItems = [
  { to: '/', label: '智能体对话', desc: '会话协作、流式响应、风险审核', icon: MessageOutlined },
  { to: '/knowledge', label: '知识文档', desc: '政策入库、检索调试、索引管理', icon: BookOutlined },
  { to: '/audit', label: '审计简表', desc: '运行记录、工具轨迹、模型调用', icon: AuditOutlined },
]

const currentNav = computed(() => navItems.find((item) => item.to === route.path) ?? navItems[0])

function handleMenuClick(path: string) {
  if (path !== route.path) {
    void router.push(path)
  }
}
</script>
