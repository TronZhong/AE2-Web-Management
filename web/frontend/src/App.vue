<script setup>
/**
 * 应用外壳：顶栏（站点标识 + 数据源徽标 + 当前账号）、路由出口与页脚。
 * 数据源徽标按 USE_MOCK 显示 MOCK / 实时，避免误把演示数据当真实状态。
 * 登出：清会话后立即跳回登录页（不带 redirect，主动登出不回跳，roadmap 已完成项）。
 */
import { RouterLink, RouterView, useRouter } from 'vue-router'

import { USE_MOCK } from './api/index.js'
import ItemTooltip from './components/base/ItemTooltip.vue'
import { useAuth } from './composables/useAuth.js'

const router = useRouter()
const { username, logout } = useAuth()

/** 登出：先调用 useAuth.logout()（清会话），再立即跳回登录页（不带 redirect）。 */
async function onLogout() {
  await logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="flex min-h-dvh flex-col">
    <header class="sticky top-0 z-10 border-b border-line bg-bg/90 backdrop-blur">
      <div class="mx-auto flex h-12 w-full max-w-6xl items-center justify-between px-4">
        <RouterLink to="/" class="font-mono text-sm text-ink transition-colors hover:text-accent">
          <span class="mr-1.5 text-accent">◆</span>ae2web
        </RouterLink>
        <div class="flex items-center gap-3">
          <span
            class="rounded-full border px-2 py-0.5 font-mono text-[10px]"
            :class="USE_MOCK ? 'border-accent/30 bg-accent/10 text-accent' : 'border-ok/30 bg-ok/10 text-ok'"
          >
            {{ USE_MOCK ? 'MOCK 数据' : '实时数据' }}
          </span>
          <span v-if="username" class="font-mono text-[11px] text-ink-dim">{{ username }}</span>
          <button
            v-if="username"
            type="button"
            class="font-mono text-[11px] text-ink-faint transition-colors hover:text-ink"
            @click="onLogout"
          >
            [登出]
          </button>
        </div>
      </div>
    </header>

    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
      <RouterView />
    </main>

    <!-- 全局物品悬浮提示（ItemSlot hover 时显示） -->
    <ItemTooltip />

    <footer class="border-t border-line">
      <p class="mx-auto w-full max-w-6xl px-4 py-3 font-mono text-[10px] text-ink-faint">
        ae2web · 只读视图 · 快照来自游戏内 4s 采样
      </p>
    </footer>
  </div>
</template>
