import { createRouter, createWebHistory } from 'vue-router'

import { useAuth } from '../composables/useAuth.js'

/**
 * 应用路由：
 * - `/login` 登录页（账号制认证，唯一免登录页面）
 * - `/` 网格选择页（列表卡片）
 * - `/networks/:accessId` 网格详情页（状态 / CPU / 样板 / 命令 / 库存卡片）
 * 页面组件懒加载，首屏只打包登录与选择页；未登录访问一律重定向到登录页并带回跳地址。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../pages/LoginPage.vue'),
    },
    {
      path: '/',
      name: 'grid-select',
      component: () => import('../pages/GridSelectPage.vue'),
    },
    {
      path: '/networks/:accessId',
      name: 'grid-detail',
      component: () => import('../pages/GridDetailPage.vue'),
      props: true,
    },
  ],
})

/** 全局守卫：首次导航先恢复会话（GET /me）；未登录重定向登录页，已登录访问登录页回首页。 */
router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.ensureReady()
  if (to.name !== 'login' && !auth.username.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.username.value) {
    return { name: 'grid-select' }
  }
})

export default router
