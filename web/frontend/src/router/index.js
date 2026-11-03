import { createRouter, createWebHistory } from 'vue-router'

/**
 * 应用路由：
 * - `/` 网格选择页（列表卡片 + 预留密码栏位）
 * - `/networks/:accessId` 网格详情页（状态 / CPU / 库存 / 样板卡片）
 * 页面组件懒加载，首屏只打包选择页。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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

export default router
