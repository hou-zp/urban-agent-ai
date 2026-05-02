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
      component: () => import('@/pages/ChatPage.vue'),
    },
    {
      path: '/',
      component: () => import('@/components/WorkbenchLayout.vue'),
      children: [
        {
          path: 'knowledge',
          component: () => import('@/pages/KnowledgePage.vue'),
        },
        {
          path: 'query',
          redirect: (to) => ({
            path: '/',
            query: {
              ...to.query,
            },
          }),
        },
        {
          path: 'audit',
          component: () => import('@/pages/AuditPage.vue'),
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
  return true
})

export default router