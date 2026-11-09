<script setup>
/**
 * 合成命令卡：展示该网格的命令历史（POST /craft 受理后的状态跟踪）。
 * 数据为模组全量历史（PENDING / SUBMITTED / FAILED），展示按提交时间倒序（最新在上）。
 * 命令由模组队列调度，前端只读展示，不提供取消操作（取消 API 为后续迭代）。
 */
import { computed } from 'vue'

import BaseCard from '../base/BaseCard.vue'
import EmptyState from '../base/EmptyState.vue'
import ItemSlot from '../base/ItemSlot.vue'
import StatusBadge from '../base/StatusBadge.vue'
import { iconUrlOf } from '../../api/index.js'
import { formatAmount, formatTime, keyId } from '../../lib/format.js'
import { displayName } from '../../lib/itemName.js'

const props = defineProps({
  /** 命令条目数组（GET /commands 的 commands[]，模组升序返回）。 */
  commands: { type: Array, required: true },
  /** 拉取失败的错误码；为空表示正常。 */
  error: { type: String, default: '' },
})

/** 命令状态 → 徽标文案与配色。 */
const STATE_META = {
  PENDING: { label: '排队中', variant: 'accent' },
  SUBMITTED: { label: '已提交', variant: 'ok' },
  FAILED: { label: '失败', variant: 'err' },
}

/** 展示顺序：最新提交在前。 */
const sorted = computed(() => [...props.commands].sort((left, right) => right.submittedAt - left.submittedAt))

/** 命令条目的次行说明：已提交为模组回执（含 craftingId），失败为原因，排队为等待提示。 */
function secondaryLine(command) {
  if (command.state === 'SUBMITTED') {
    return { text: command.result || '已提交', class: 'text-ok' }
  }
  if (command.state === 'FAILED') {
    return { text: command.result || '未知原因', class: 'text-err' }
  }
  return { text: '等待网格上线后提交', class: 'text-ink-faint' }
}

/** 状态徽标元数据兜底：未知状态按中性灰展示。 */
function metaOf(state) {
  return STATE_META[state] ?? { label: state, variant: 'neutral' }
}
</script>

<template>
  <BaseCard title="合成命令">
    <template #actions>
      <span class="font-mono text-[11px] tabular-nums text-ink-faint">共 {{ commands.length }} 条</span>
    </template>

    <p
      v-if="error"
      class="mb-3 rounded-md border border-err/30 bg-err/10 px-3 py-2 font-mono text-xs text-err"
    >
      加载失败：{{ error }}
    </p>

    <EmptyState v-if="!commands.length" text="暂无合成命令 · 在样板卡点击产物可下达" />
    <template v-else>
      <div class="max-h-64 space-y-0.5 overflow-y-auto pr-1">
        <div
          v-for="command in sorted"
          :key="command.commandId"
          class="flex items-center gap-3 rounded-md px-1 py-1.5"
        >
          <div class="size-10 shrink-0">
            <ItemSlot :item="{ key: command.target, amount: command.amount }" :icon-url="iconUrlOf(command.target)" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-baseline gap-2">
              <p class="min-w-0 truncate text-sm text-ink">{{ displayName({ key: command.target }) }}</p>
              <span class="shrink-0 font-mono text-[11px] tabular-nums text-ink-dim">
                ×{{ formatAmount(command.amount) }}
              </span>
            </div>
            <p class="truncate font-mono text-[10px] text-ink-faint" :title="keyId(command.target)">
              {{ keyId(command.target) }}
            </p>
            <p class="truncate font-mono text-[10px]" :class="secondaryLine(command).class" :title="secondaryLine(command).text">
              {{ secondaryLine(command).text }}
            </p>
          </div>
          <div class="flex shrink-0 flex-col items-end gap-1">
            <StatusBadge :label="metaOf(command.state).label" :variant="metaOf(command.state).variant" />
            <span class="font-mono text-[10px] tabular-nums text-ink-faint" :title="command.commandId">
              {{ formatTime(command.submittedAt) }}
            </span>
          </div>
        </div>
      </div>
      <p class="mt-3 font-mono text-[11px] text-ink-faint">
        命令由模组队列调度：网格在线后提交给 AE2 · 倒序展示最新在上
      </p>
    </template>
  </BaseCard>
</template>
