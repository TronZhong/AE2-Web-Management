<script setup>
/**
 * 样板卡（可合成列表）：MC 风格格子布局展示各样板的主输出，
 * 点击格子弹出「计划合成」卡片（含输入需求明细）。
 * 口径：仅样板（IPatternDetails）主输出，不含 AE2 可发射物品（见 docs/design.md §7）。
 */
import { computed, ref } from 'vue'

import BaseCard from '../base/BaseCard.vue'
import EmptyState from '../base/EmptyState.vue'
import ItemSlot from '../base/ItemSlot.vue'
import CraftPlanModal from './CraftPlanModal.vue'
import { friendlyName, keyId } from '../../lib/format.js'

const props = defineProps({
  /** 快照 patterns 数组。 */
  patterns: { type: Array, required: true },
})

const keyword = ref('')

/** 当前选中（弹出计划卡）的样板；null 表示关闭。 */
const selected = ref(null)

/** 按主输出友好名 / id 过滤。 */
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return props.patterns
  }
  return props.patterns.filter(
    (pattern) =>
      friendlyName(pattern.primaryOutput.key).toLowerCase().includes(kw) ||
      keyId(pattern.primaryOutput.key).toLowerCase().includes(kw),
  )
})
</script>

<template>
  <BaseCard title="样板 · 可合成">
    <template #actions>
      <div class="flex items-center gap-3">
        <span class="font-mono text-[11px] tabular-nums text-ink-faint">
          {{ filtered.length }}/{{ patterns.length }} 条
        </span>
        <input
          v-model="keyword"
          type="search"
          placeholder="搜索产物…"
          class="w-44 rounded-md border border-line bg-panel-2 px-2 py-1 font-mono text-xs text-ink placeholder:text-ink-faint focus:border-accent/60 focus:outline-none"
        />
      </div>
    </template>

    <EmptyState v-if="!patterns.length" text="该网络没有编码样板" />
    <template v-else>
      <div
        class="grid"
        :style="{ gridTemplateColumns: `repeat(auto-fill, ${48}px)`, gap: '6px' }"
      >
        <button
          v-for="(pattern, index) in filtered"
          :key="keyId(pattern.primaryOutput.key) + ':' + index"
          type="button"
          class="size-12 cursor-pointer"
          :title="friendlyName(pattern.primaryOutput.key)"
          @click="selected = pattern"
        >
          <ItemSlot :item="pattern.primaryOutput" />
        </button>
      </div>
      <p class="mt-3 font-mono text-[11px] text-ink-faint">
        口径：仅样板主输出（不含可发射物品）· 点击格子查看计划合成
      </p>
    </template>

    <CraftPlanModal v-if="selected" :pattern="selected" @close="selected = null" />
  </BaseCard>
</template>
