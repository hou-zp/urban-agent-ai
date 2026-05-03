import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: () => import('@/pages/LoginPage.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/components/WorkbenchLayout.vue'),
      children: [
        {
          path: '',
          component: () => import('@/pages/ChatPage.vue'),
        },
        {
          path: 'knowledge',
          component: () => import('@/pages/KnowledgePage.vue'),
        },
        {
          path: 'query',
          component: () => import('@/pages/QueryPage.vue'),
        },
        {
          path: 'audit',
          component: () => import('@/pages/AuditPage.vue'),
        },
        {
          path: 'profile',
          component: () => import('@/pages/ProfilePage.vue'),
        },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/components/AdminLayout.vue'),
      meta: { requiresAdmin: true },
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/pages/admin/AdminDashboard.vue'),
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('@/pages/admin/AdminAudit.vue'),
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/pages/admin/AdminKnowledge.vue'),
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/pages/admin/AdminUsers.vue'),
        },
        {
          path: 'config',
          name: 'config',
          component: () => import('@/pages/admin/AdminConfig.vue'),
        },
        {
          path: 'logs',
          name: 'logs',
          component: () => import('@/pages/admin/AdminLogs.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  const auth = useAuthStore()
  if (!auth.isLoggedIn) {
    return '/login'
  }
  if (to.meta.requiresAdmin) {
    const role = auth.user?.role
    if (role !== 'ADMIN' && role !== 'AUDITOR') {
      return '/'
    }
  }
  return true
})

export default router