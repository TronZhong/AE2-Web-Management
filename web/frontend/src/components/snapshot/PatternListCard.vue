<script setup>
/**
 * 样板卡（可合成列表）：MC 风格格子布局展示各样板的主输出，
 * 点击格子弹出「计划合成」卡片（含输入需求明细）。
 * 格子区窄屏固定 440px、桌面端随卡片拉伸填满（fill），数量超出时向下滚动。
 * 口径：仅样板（IPatternDetails）主输出，不含 AE2 可发射物品（见 docs/design.md §7）。
 */
import { computed, ref } from 'vue'

import BaseCard from '../base/BaseCard.vue'
import EmptyState from '../base/EmptyState.vue'
import ItemSlot from '../base/ItemSlot.vue'
import CraftPlanModal from './CraftPlanModal.vue'
import { iconUrlOf } from '../../api/index.js'
import { keyId } from '../../lib/format.js'
import { displayName } from '../../lib/itemName.js'

const props = defineProps({
  /** 目标网格 accessId：透传给计划合成弹窗用于提交命令。 */
  accessId: { type: String, required: true },
  /** 快照 patterns 数组。 */
  patterns: { type: Array, required: true },
})

const emit = defineEmits(['submitted'])

const keyword = ref('')

/** 当前选中（弹出计划卡）的样板；null 表示关闭。 */
const selected = ref(null)

/** 按主输出中文/友好名 / id 过滤。 */
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return props.patterns
  }
  return props.patterns.filter(
    (pattern) =>
      displayName(pattern.primaryOutput).toLowerCase().includes(kw) ||
      keyId(pattern.primaryOutput.key).toLowerCase().includes(kw),
  )
})
</script>

<template>
  <BaseCard title="样板 · 可合成" fill>
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

    <EmptyState v-if="!patterns.length" text="该网格没有编码样板" />
    <template v-else>
      <!-- 格子区视口：窄屏固定 440px；桌面端（fill 模式）拉伸填满卡片剩余高度，
           与同行的状态·CPU·命令合并卡底边对齐。滚动区绝对定位填充——
           绝对定位不参与卡片内在高度计算，样板再多也不会把卡片/整行撑高，
           超出部分在固定视口内滚动 -->
      <div class="relative h-[440px] lg:h-auto lg:min-h-0 lg:flex-1">
        <div class="absolute inset-0 overflow-y-auto pr-1">
          <div
            class="grid"
            :style="{ gridTemplateColumns: `repeat(auto-fill, ${48}px)`, gap: '6px' }"
          >
            <button
              v-for="(pattern, index) in filtered"
              :key="keyId(pattern.primaryOutput.key) + ':' + index"
              type="button"
              class="size-12 cursor-pointer"
              :title="displayName(pattern.primaryOutput)"
              @click="selected = pattern"
            >
              <ItemSlot :item="pattern.primaryOutput" :icon-url="iconUrlOf(pattern.primaryOutput.key)" />
            </button>
          </div>
        </div>
      </div>
      <p class="mt-3 shrink-0 font-mono text-[11px] text-ink-faint">
        口径：仅样板主输出（不含可发射物品）· 点击格子查看计划合成
      </p>
    </template>

    <CraftPlanModal
      v-if="selected"
      :access-id="accessId"
      :pattern="selected"
      @close="selected = null"
      @submitted="emit('submitted')"
    />
  </BaseCard>
</template>
