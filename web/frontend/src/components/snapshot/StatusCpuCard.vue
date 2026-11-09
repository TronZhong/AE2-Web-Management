<script setup>
/**
 * 状态 + 合成 CPU 合并卡：上半部分为快照总体状态（在线态、数据截至、
 * 控制器、统计、accessId），下半部分逐个 CPU 展示忙闲/存储/协处理器。
 * AE2 合成 CPU 无法命名，与游戏内 GUI 一致按列表序号显示为「#N」。
 */
import BaseCard from '../base/BaseCard.vue'
import EmptyState from '../base/EmptyState.vue'
import StatusBadge from '../base/StatusBadge.vue'
import { formatStorage, formatTime } from '../../lib/format.js'

defineProps({
  /** 快照对象，形状见 contracts/web-api.md 的 GET /api/grids/{id}/snapshot。 */
  snapshot: { type: Object, required: true },
  /** 网格摘要（GET /api/grids 条目），用于控制器位置展示。 */
  grid: { type: Object, default: null },
})
</script>

<template>
  <BaseCard title="状态 · 合成 CPU">
    <div class="grid gap-x-8 gap-y-4 font-mono text-xs sm:grid-cols-2">
      <div>
        <p class="mb-1.5 text-[10px] uppercase tracking-widest text-ink-faint">网格状态</p>
        <StatusBadge :label="snapshot.online ? '在线' : '离线'" :variant="snapshot.online ? 'ok' : 'neutral'" />
      </div>
      <div>
        <p class="mb-1.5 text-[10px] uppercase tracking-widest text-ink-faint">数据截至</p>
        <p class="tabular-nums text-ink">{{ formatTime(snapshot.capturedAt) }}</p>
      </div>
      <div>
        <p class="mb-1.5 text-[10px] uppercase tracking-widest text-ink-faint">控制器</p>
        <p
          v-if="grid"
          class="truncate text-ink"
          :title="`${grid.dimension} @ (${grid.controllerX}, ${grid.controllerY}, ${grid.controllerZ})`"
        >
          {{ grid.dimension }} @ ({{ grid.controllerX }}, {{ grid.controllerY }},
          {{ grid.controllerZ }})
        </p>
        <p v-else class="text-ink-faint">—</p>
      </div>
      <div>
        <p class="mb-1.5 text-[10px] uppercase tracking-widest text-ink-faint">accessId</p>
        <p class="truncate text-ink-dim" :title="snapshot.accessId">{{ snapshot.accessId }}</p>
      </div>
    </div>

    <div class="mt-4 border-t border-line pt-3">
      <p class="mb-2 text-[10px] uppercase tracking-widest text-ink-faint">
        合成 CPU（{{ snapshot.craftingCpus.length }}）
      </p>
      <EmptyState v-if="!snapshot.craftingCpus.length" text="该网格没有合成 CPU" />
      <ul v-else class="divide-y divide-line">
        <li
          v-for="(cpu, index) in snapshot.craftingCpus"
          :key="index"
          class="flex items-center justify-between gap-3 py-2 first:pt-0 last:pb-0"
        >
          <div class="min-w-0">
            <p class="font-mono text-xs text-ink">#{{ index + 1 }}</p>
            <p class="mt-0.5 font-mono text-[10px] text-ink-faint">
              存储 {{ formatStorage(cpu.availableStorage) }} · 协处理器 ×{{ cpu.coProcessors }}
            </p>
          </div>
          <StatusBadge :label="cpu.busy ? '忙碌' : '空闲'" :variant="cpu.busy ? 'accent' : 'neutral'" />
        </li>
      </ul>
    </div>
  </BaseCard>
</template>
