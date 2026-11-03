<script setup>
/**
 * 应用外壳：顶栏（站点标识 + 数据源徽标）、路由出口与页脚。
 * 数据源徽标按 USE_MOCK 显示 MOCK / 实时，避免误把演示数据当真实状态。
 */
import { RouterLink, RouterView } from 'vue-router'

import { USE_MOCK } from './api/index.js'
import ItemTooltip from './components/base/ItemTooltip.vue'
</script>

<template>
  <div class="flex min-h-dvh flex-col">
    <header class="sticky top-0 z-10 border-b border-line bg-bg/90 backdrop-blur">
      <div class="mx-auto flex h-12 w-full max-w-6xl items-center justify-between px-4">
        <RouterLink to="/" class="font-mono text-sm text-ink transition-colors hover:text-accent">
          <span class="mr-1.5 text-accent">◆</span>ae2web
        </RouterLink>
        <span
          class="rounded-full border px-2 py-0.5 font-mono text-[10px]"
          :class="USE_MOCK ? 'border-accent/30 bg-accent/10 text-accent' : 'border-ok/30 bg-ok/10 text-ok'"
        >
          {{ USE_MOCK ? 'MOCK 数据' : '实时数据' }}
        </span>
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
