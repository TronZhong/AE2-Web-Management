<script setup>
/**
 * 登录页：账号制认证入口（语义 v0.11：账号只能在游戏内接入方块 GUI 注册，
 * 用户名即游戏内玩家名）。
 * 提交 POST /login；unauthorized 显示「用户名或密码错误」，其余错误码原样透出。
 * 成功后回跳 ?redirect= 指向的页面（默认首页）；已登录访问会被路由守卫送回首页。
 */
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BaseButton from '../components/base/BaseButton.vue'
import { USE_MOCK } from '../api/index.js'
import { useAuth } from '../composables/useAuth.js'

const route = useRoute()
const router = useRouter()
const { login } = useAuth()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')

/** 契约错误码 → 登录页文案；未收录的原样展示。 */
const ERROR_TEXT = {
  unauthorized: '用户名或密码错误',
  invalid_json: '请输入用户名和密码',
  network_error: '无法连接后端服务',
}

/** 提交登录；成功后跳回 redirect 参数指定的页面（默认首页）。 */
async function submit() {
  if (submitting.value) {
    return
  }
  if (!username.value.trim() || !password.value) {
    error.value = 'invalid_json'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await login(username.value.trim(), password.value)
    router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex flex-1 items-center justify-center py-10">
    <form
      class="w-full max-w-sm rounded-card border border-line bg-panel p-6"
      @submit.prevent="submit"
    >
      <h1 class="text-lg font-medium text-ink">登录 ae2web</h1>
      <p class="mt-1.5 font-mono text-[11px] text-ink-faint">
        游戏内玩家名登录 · 登录后可见全部已注册网格
      </p>

      <div class="mt-6 space-y-4">
        <div>
          <label class="mb-1 block font-mono text-[10px] uppercase tracking-widest text-ink-faint" for="login-username">
            用户名（游戏内玩家名）
          </label>
          <input
            id="login-username"
            v-model="username"
            type="text"
            autocomplete="username"
            autofocus
            class="w-full rounded-md border border-line bg-panel-2 px-2.5 py-1.5 font-mono text-xs text-ink placeholder:text-ink-faint focus:border-accent/60 focus:outline-none"
            placeholder="Steve"
          />
        </div>
        <div>
          <label class="mb-1 block font-mono text-[10px] uppercase tracking-widest text-ink-faint" for="login-password">
            密码
          </label>
          <input
            id="login-password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            class="w-full rounded-md border border-line bg-panel-2 px-2.5 py-1.5 font-mono text-xs text-ink placeholder:text-ink-faint focus:border-accent/60 focus:outline-none"
            placeholder="••••••••"
          />
        </div>
      </div>

      <p v-if="error" class="mt-4 rounded-md border border-err/30 bg-err/10 px-3 py-2 font-mono text-xs text-err">
        {{ ERROR_TEXT[error] ?? `登录失败：${error}` }}
      </p>

      <BaseButton variant="primary" class="mt-6 w-full justify-center" :disabled="submitting" type="submit">
        {{ submitting ? '登录中…' : '登录' }}
      </BaseButton>

      <p class="mt-4 font-mono text-[10px] leading-4 text-ink-faint">
        {{ USE_MOCK
          ? 'MOCK 模式：任意非空用户名/密码均可登录'
          : '账号只能在游戏内接入方块 GUI 注册（密码 + 确认）；网页端无注册口' }}
      </p>
    </form>
  </div>
</template>
