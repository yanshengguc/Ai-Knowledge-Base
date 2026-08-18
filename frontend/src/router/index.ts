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
        { path: 'chat', name: 'chat', component: () => import('@/views/chat/Chat.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/knowledge' },
  ],
})

// 登录守卫:未登录跳登录页
router.beforeEach((to) => {
  const token = getToken()
  if (!token && to.name !== 'login' && to.name !== 'register') {
    return { name: 'login' }
  }
  if (token && (to.name === 'login' || to.name === 'register')) {
    return { name: 'knowledge' }
  }
  return true
})

export default router
