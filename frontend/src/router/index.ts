import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/auth/Login.vue') },
    { path: '/register', name: 'register', component: () => import('@/views/auth/Register.vue') },
    {
      path: '/',
      component: () => import('@/layouts/Layout.vue'),
      children: [
        { path: '', redirect: '/knowledge' },
        { path: 'knowledge', name: 'knowledge', component: () => import('@/views/knowledge/List.vue') },
        { path: 'knowledge/:id', name: 'knowledge-detail', component: () => import('@/views/knowledge/Detail.vue') },
        { path: 'tree', name: 'knowledge-tree', component: () => import('@/views/knowledge/Tree.vue') },
        { path: 'graph', name: 'knowledge-graph', component: () => import('@/views/knowledge/Graph.vue') },
        { path: 'chat', name: 'chat', component: () => import('@/views/chat/Chat.vue') },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/admin/Admin.vue'),
          meta: { requiresAdmin: true },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue') },
  ],
})

// 登录守卫:未登录跳登录页;管理端路由额外校验白名单身份
router.beforeEach(async (to) => {
  const token = getToken()
  if (!token && to.name !== 'login' && to.name !== 'register') {
    return { name: 'login' }
  }
  if (token && (to.name === 'login' || to.name === 'register')) {
    return { name: 'knowledge' }
  }
  if (to.meta.requiresAdmin) {
    // 动态导入:避免 router -> store -> api/request -> router 的循环依赖
    const { useUserStore } = await import('@/stores/user')
    const userStore = useUserStore()
    await userStore.ensureProfile()
    if (!userStore.isAdmin) {
      return { name: 'knowledge' }
    }
  }
  return true
})

export default router