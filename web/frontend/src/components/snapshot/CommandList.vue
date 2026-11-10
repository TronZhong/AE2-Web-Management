<script setup>
/**
 * 合成命令列表（嵌入视图）：由「状态 · 合成 CPU」卡右半栏内嵌使用。
 * 不渲染外层卡片外壳——栏目标题与计数由宿主提供；本组件只输出
 * 错误提示 / 空状态 / 可滚动列表。数据为模组全量历史（PENDING / SUBMITTED / FAILED），
 * 展示按提交时间倒序（最新在上）；每行提供删除按钮（契约 v0.9）：
 * 删除 PENDING 记录即撤销（不会提交 AE2），删终态记录仅清理历史；
 * 按钮为两段式确认（点一次变「确认删除」再点一次才 emit），避免误触。
 * 列表视口固定高度、最多完整显示 2 条，超出向下滚动；
 * 过长的模组回执（含 craftingId）与物品 id 经 HoverDetail 悬浮展开完整内容。
 */
import { computed, ref } from 'vue'

import EmptyState from '../base/EmptyState.vue'
import HoverDetail from '../base/HoverDetail.vue'
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

const emit = defineEmits(['delete'])

/** 待二次确认的命令 ID：点一次进入确认态，再点一次才真正 emit；换行点击会重置。 */
const confirmingId = ref('')

/**
 * 删除按钮点击：两段式确认——第一次点进入「确认删除」态，第二次点才发出 delete 事件。
 * @param command 目标命令条目
 */
function onDeleteClick(command) {
  if (confirmingId.value === command.commandId) {
    confirmingId.value = ''
    emit('delete', command)
    return
  }
  confirmingId.value = command.commandId
}

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
  <div class="min-w-0">
    <p
      v-if="error"
      class="mb-3 rounded-md border border-err/30 bg-err/10 px-3 py-2 font-mono text-xs text-err"
    >
      加载失败：{{ error }}
    </p>

    <EmptyState v-if="!commands.length" text="暂无合成命令 · 在样板卡点击产物可下达" />
    <!-- 视口固定 126px：恰好完整容纳 2 条（每条 62px + 1 个 2px 间隔），超出滚动 -->
    <div v-else class="max-h-[126px] space-y-0.5 overflow-y-auto pr-1">
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
          <HoverDetail class="font-mono text-[10px] text-ink-faint" :text="keyId(command.target)" />
          <HoverDetail
            class="font-mono text-[10px]"
            :class="secondaryLine(command).class"
            :text="secondaryLine(command).text"
          />
        </div>
        <div class="flex shrink-0 flex-col items-end gap-1">
          <div class="flex items-center gap-1.5">
            <StatusBadge :label="metaOf(command.state).label" :variant="metaOf(command.state).variant" />
            <button
              type="button"
              class="rounded px-1 font-mono text-[10px] transition-colors"
              :class="confirmingId === command.commandId ? 'text-err hover:text-err' : 'text-ink-faint hover:text-ink'"
              :title="confirmingId === command.commandId
                ? '再点一次确认删除'
                : (command.state === 'PENDING'
                  ? '删除记录（删除排队中的命令即撤销，不会提交给 AE2）'
                  : '删除记录（仅清理历史，不影响 AE2 已受理的任务）')"
              @click="onDeleteClick(command)"
              @blur="confirmingId === command.commandId && (confirmingId = '')"
            >
              {{ confirmingId === command.commandId ? '确认删除' : '删除' }}
            </button>
          </div>
          <span class="font-mono text-[10px] tabular-nums text-ink-faint" :title="command.commandId">
            {{ formatTime(command.submittedAt) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
