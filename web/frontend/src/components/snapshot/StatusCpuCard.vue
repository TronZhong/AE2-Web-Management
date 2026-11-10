<script setup>
/**
 * 状态 + 合成 CPU + 合成命令合并卡（详情页首行左卡，与样板卡横向对半分）：
 * 上半部分为快照总体状态（在线态、数据截至、控制器、accessId），
 * 下半部分上下堆叠——上为逐个 CPU 的忙闲/存储/协处理器，下为合成命令列表；
 * 两个列表均最多完整显示 2 条，超出向下滚动（命令列表见 CommandList）。
 * 过长的控制器位置 / accessId 文本经 HoverDetail 在箭头处悬浮展开完整内容。
 * AE2 合成 CPU 无法命名，与游戏内 GUI 一致按列表序号显示为「#N」。
 */
import BaseCard from '../base/BaseCard.vue'
import EmptyState from '../base/EmptyState.vue'
import HoverDetail from '../base/HoverDetail.vue'
import StatusBadge from '../base/StatusBadge.vue'
import CommandList from './CommandList.vue'
import { formatStorage, formatTime } from '../../lib/format.js'

defineProps({
  /** 快照对象，形状见 contracts/web-api.md 的 GET /api/grids/{id}/snapshot。 */
  snapshot: { type: Object, required: true },
  /** 网格摘要（GET /api/grids 条目），用于控制器位置展示。 */
  grid: { type: Object, default: null },
  /** 命令条目数组（GET /commands），透传给右半栏命令列表。 */
  commands: { type: Array, required: true },
  /** 命令列表拉取失败的错误码；为空表示正常。 */
  commandsError: { type: String, default: '' },
})

/** 向页面冒泡命令列表的 delete 事件（契约 v0.9 删除命令记录）。 */
defineEmits(['delete'])
</script>

<template>
  <BaseCard title="状态 · 合成 CPU">
    <template #actions>
      <span class="font-mono text-[11px] tabular-nums text-ink-faint">命令 {{ commands.length }} 条</span>
    </template>

    <div class="grid gap-x-8 gap-y-4 font-mono text-xs sm:grid-cols-2">
      <div>
        <p class="mb-1.5 text-[10px] uppercase tracking-widest text-ink-faint">网格状态</p>
        <StatusBadge :label="snapshot.online ? '在线' : '离线'" :variant="snapshot.online ? 'ok' : 'neutral'" />
      </div>
      <div>
        <p class="mb-1.5 text-[10px] uppercase tracking-widest text-ink-faint">数据截至</p>
        <p class="tabular-nums text-ink">{{ formatTime(snapshot.capturedAt) }}</p>
      </div>
      <div class="min-w-0">
        <p class="mb-1.5 text-[10px] uppercase tracking-widest text-ink-faint">控制器</p>
        <HoverDetail
          v-if="grid"
          class="text-ink"
          :text="`${grid.dimension} @ (${grid.controllerX}, ${grid.controllerY}, ${grid.controllerZ})`"
        />
        <p v-else class="text-ink-faint">—</p>
      </div>
      <div class="min-w-0">
        <p class="mb-1.5 text-[10px] uppercase tracking-widest text-ink-faint">accessId</p>
        <HoverDetail class="text-ink-dim" :text="snapshot.accessId" />
      </div>
    </div>

    <!-- 上下堆叠：合成 CPU 在上、合成命令在下，两段之间用分隔线 -->
    <div class="mt-4 border-t border-line pt-3">
      <section class="min-w-0">
        <p class="mb-2 text-[10px] uppercase tracking-widest text-ink-faint">
          合成 CPU（{{ snapshot.craftingCpus.length }}）
        </p>
        <EmptyState v-if="!snapshot.craftingCpus.length" text="该网格没有合成 CPU" />
        <!-- 视口固定 91px：恰好完整容纳 2 行（首行 41px + 次行 49px + 1 条分隔线），超出滚动 -->
        <ul v-else class="max-h-[91px] divide-y divide-line overflow-y-auto pr-1">
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
      </section>
      <section class="mt-4 min-w-0 border-t border-line pt-3">
        <p class="mb-2 text-[10px] uppercase tracking-widest text-ink-faint">合成命令（{{ commands.length }} 条）</p>
        <CommandList :commands="commands" :error="commandsError" @delete="$emit('delete', $event)" />
      </section>
    </div>
  </BaseCard>
</template>
