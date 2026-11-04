import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { onUnauthorized } from './api/index.js'
import { useAuth } from './composables/useAuth.js'

// 401 统一接线：任何请求返回未登录（会话过期等）时清空登录态并跳登录页（带回跳地址）
onUnauthorized(() => {
  useAuth().clearSession()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
})

/** 应用入口：挂载根组件并接通路由。 */
createApp(App).use(router).mount('#app')
