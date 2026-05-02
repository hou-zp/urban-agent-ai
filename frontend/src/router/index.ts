import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
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

export default router
