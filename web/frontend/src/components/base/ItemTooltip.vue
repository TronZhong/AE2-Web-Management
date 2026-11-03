<script setup>
/**
 * 物品悬浮提示：MC 风格暗紫底、跟随鼠标，teleport 到 body。
 * fixed 定位避免被库存/样板的滚动容器裁剪；页面按 --app-zoom 放缩，
 * 鼠标事件坐标是物理像素，定位前需除以缩放系数换算回布局像素。
 */
import { computed } from 'vue'

import { formatAmount } from '../../lib/format.js'
import { useItemTooltip } from '../../composables/useItemTooltip.js'

const state = useItemTooltip()

/** 读取 html 的生效缩放系数；未设置时按 1 处理。 */
function zoomFactor() {
  return Number.parseFloat(getComputedStyle(document.documentElement).zoom) || 1
}

/** 提示框位置：跟随鼠标、右/下边缘内收，全部换算到缩放空间。 */
const position = computed(() => {
  const zoom = zoomFactor()
  const left = Math.min((state.x + 14) / zoom, Math.max(0, (window.innerWidth - 270) / zoom))
  const top = Math.min((state.y + 16) / zoom, Math.max(0, (window.innerHeight - 90) / zoom))
  return { left: `${left}px`, top: `${top}px` }
})

/**
 * 提示内容：优先调用来源格子的响应式取值函数（跟随最新数据，
 * 数量被轮询刷新时提示实时变化），无来源时退回 hover 瞬间的快照。
 */
const content = computed(() => {
  if (state.source) {
    return state.source()
  }
  return { name: state.name, sub: state.sub, amount: state.amount }
})
</script>

<template>
  <Teleport to="body">
    <div
      v-show="state.visible"
      class="pointer-events-none fixed z-50 min-w-[170px] max-w-[270px] rounded-[3px] border border-[#4b2a79] bg-[#12081c]/95 px-2.5 py-1.5 shadow-xl shadow-black/60"
      :style="position"
    >
      <p class="text-xs leading-4 text-ink">{{ content.name }}</p>
      <p class="font-mono text-[10px] leading-4 text-[#9a86c4]">{{ content.sub }}</p>
      <p v-if="content.amount !== null" class="font-mono text-[10px] leading-4 text-ink-dim">
        数量 {{ formatAmount(content.amount) }}
      </p>
    </div>
  </Teleport>
</template>
