<script setup>
/**
 * 库存卡：MC 风格格子布局展示物品，支持按友好名 / id 过滤。
 * 大型网格物品种类可达数千，按「行 × 列」窗口化渲染（虚拟滚动），
 * 列数随容器宽度自适应；格子的悬浮名称与角标由 ItemSlot 负责。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import BaseCard from '../base/BaseCard.vue'
import EmptyState from '../base/EmptyState.vue'
import ItemSlot from '../base/ItemSlot.vue'
import { friendlyName, keyId } from '../../lib/format.js'

const props = defineProps({
  /** 快照 inventory 数组，条目为 {key, amount}。 */
  items: { type: Array, required: true },
})

/** 格子布局参数：48px 槽 + 6px 间距 = 54px 行列节距；视口高度固定便于窗口计算。 */
const SLOT = 48
const GAP = 6
const PITCH = SLOT + GAP
const VIEWPORT_HEIGHT = 480
const ROW_OVERSCAN = 2

const keyword = ref('')
const viewport = ref(null)
const scrollTop = ref(0)
const cols = ref(1)

let resizeObserver = null

/** 按容器宽度计算列数（clientWidth 与 scrollTop 同属元素自身坐标空间）。 */
function measure() {
  const width = viewport.value?.clientWidth ?? 0
  cols.value = Math.max(1, Math.floor((width + GAP) / PITCH))
}

onMounted(() => {
  measure()
  resizeObserver = new ResizeObserver(measure)
  if (viewport.value) {
    resizeObserver.observe(viewport.value)
  }
})
onBeforeUnmount(() => resizeObserver?.disconnect())

/** 过滤后的条目：友好名或 id 命中关键字（忽略大小写）。 */
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return props.items
  }
  return props.items.filter(
    (entry) =>
      friendlyName(entry.key).toLowerCase().includes(kw) || keyId(entry.key).toLowerCase().includes(kw),
  )
})

/** 总行数与当前滚动窗口内应渲染的行（每行 items 为切片）。 */
const rowCount = computed(() => Math.ceil(filtered.value.length / cols.value))

const visibleRows = computed(() => {
  const first = Math.floor(scrollTop.value / PITCH)
  const count = Math.ceil(VIEWPORT_HEIGHT / PITCH)
  const start = Math.max(0, first - ROW_OVERSCAN)
  const end = Math.min(rowCount.value, first + count + ROW_OVERSCAN)
  const rows = []
  for (let row = start; row < end; row += 1) {
    rows.push({ index: row, items: filtered.value.slice(row * cols.value, (row + 1) * cols.value) })
  }
  return rows
})

/** 过滤条件变化时回到列表顶部。 */
watch(keyword, () => {
  scrollTop.value = 0
  if (viewport.value) {
    viewport.value.scrollTop = 0
  }
})

/** 滚动事件：仅记录位置，渲染交给 visibleRows 计算。 */
function onScroll(event) {
  scrollTop.value = event.target.scrollTop
}
</script>

<template>
  <BaseCard title="库存">
    <template #actions>
      <div class="flex items-center gap-3">
        <span class="font-mono text-[11px] tabular-nums text-ink-faint">
          {{ filtered.length }}/{{ items.length }} 项
        </span>
        <input
          v-model="keyword"
          type="search"
          placeholder="搜索物品…"
          class="w-44 rounded-md border border-line bg-panel-2 px-2 py-1 font-mono text-xs text-ink placeholder:text-ink-faint focus:border-accent/60 focus:outline-none"
        />
      </div>
    </template>

    <EmptyState v-if="!items.length" text="快照中没有库存记录" />
    <template v-else>
      <div
        ref="viewport"
        class="overflow-y-auto"
        :style="{ height: VIEWPORT_HEIGHT + 'px' }"
        @scroll.passive="onScroll"
      >
        <div class="relative" :style="{ height: rowCount * PITCH + 'px' }">
          <div
            v-for="row in visibleRows"
            :key="row.index"
            class="absolute left-0 flex"
            :style="{ top: row.index * PITCH + 'px', gap: GAP + 'px' }"
          >
            <div v-for="entry in row.items" :key="keyId(entry.key)" class="size-12">
              <ItemSlot :item="entry" />
            </div>
          </div>
        </div>
      </div>
      <p class="mt-2 text-right font-mono text-[11px] text-ink-faint">
        数量为网格聚合值（getCachedInventory）· 悬浮查看名称
      </p>
    </template>
  </BaseCard>
</template>
