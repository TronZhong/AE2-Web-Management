<script setup>
/** 网格选择页：列出全部已注册网格卡片；10s 轮询 + SSE 事件即时刷新 + 手动刷新。 */
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

import BaseButton from '../components/base/BaseButton.vue'
import EmptyState from '../components/base/EmptyState.vue'
import GridCard from '../components/grid/GridCard.vue'
import { subscribeGridEvents } from '../api/events.js'
import { useGrids } from '../composables/useGrids.js'
import { usePolling } from '../composables/usePolling.js'

const router = useRouter()
const { grids, loading, error, refresh } = useGrids()

// 网格列表变化频率低，用 10s 轮询；start() 会立即执行首次加载
usePolling(refresh, 10_000).start()

// 后端推送「列表级变化」时立即刷新，不必干等下一次轮询（roadmap 已完成项：SSE 实时推送）
let unsubscribeEvents = null
onMounted(() => {
  unsubscribeEvents = subscribeGridEvents({ onGridsUpdated: () => refresh() })
})
onUnmounted(() => unsubscribeEvents?.())

/** 进入所选网格的详情页。 */
function enter(grid) {
  router.push({ name: 'grid-detail', params: { accessId: grid.accessId } })
}
</script>

<template>
  <div>
    <div class="mb-6 flex items-end justify-between gap-4">
      <div>
        <h1 class="text-lg font-medium text-ink">选择网格</h1>
        <p class="mt-1 font-mono text-xs text-ink-faint">
          来自游戏内接入方块注册 · 共 {{ grids.length }} 个网格
        </p>
      </div>
      <BaseButton :disabled="loading" @click="refresh">{{ loading ? '刷新中…' : '刷新' }}</BaseButton>
    </div>

    <p
      v-if="error"
      class="mb-4 rounded-md border border-err/30 bg-err/10 px-3 py-2 font-mono text-xs text-err"
    >
      加载失败：{{ error }}
    </p>

    <div v-if="loading && !grids.length" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div v-for="i in 3" :key="i" class="h-56 animate-pulse rounded-card border border-line bg-panel"></div>
    </div>

    <EmptyState v-else-if="!grids.length" text="还没有已注册的网格：在游戏内放置接入方块并注册" />

    <div v-else class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <GridCard v-for="grid in grids" :key="grid.accessId" :grid="grid" @enter="enter(grid)" />
    </div>
  </div>
</template>
