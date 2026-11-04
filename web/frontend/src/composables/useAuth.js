import { ref } from 'vue'

import { fetchMe, login as apiLogin, logout as apiLogout } from '../api/index.js'

/**
 * 账号登录态（模块级单例，所有组件共享同一份状态）。
 * - `ensureReady()`：应用启动时（路由守卫中）调用一次，GET /me 恢复会话；
 * - `login()` / `logout()`：对应认证端点；401 统一跳转在 main.js 接线，
 *   本组合式函数不依赖 router，避免循环引用。
 */
const username = ref('')
const ready = ref(false)

/** 进行中的 ensureReady Promise：并发调用只发一次 /me 请求。 */
let initializing = null

/** 应用启动恢复会话：/me 成功记下用户名，unauthorized 视为未登录。 */
async function ensureReady() {
  if (ready.value) {
    return
  }
  if (!initializing) {
    initializing = (async () => {
      try {
        const me = await fetchMe()
        username.value = me.username
      } catch {
        username.value = ''
      } finally {
        ready.value = true
      }
    })()
  }
  await initializing
}

/** 登录：成功后记录用户名；失败（unauthorized 等）原样上抛给登录页展示。 */
async function login(name, password) {
  const response = await apiLogin(name, password)
  username.value = response.username
}

/** 登出：无论请求结果如何都清空本地登录态（幂等）。 */
async function logout() {
  try {
    await apiLogout()
  } finally {
    username.value = ''
  }
}

/** 清空登录态：401 统一处理回调使用（会话过期时由 main.js 调用）。 */
function clearSession() {
  username.value = ''
}

export function useAuth() {
  return { username, ready, ensureReady, login, logout, clearSession }
}
