<script setup>
/**
 * 物品格子：MC 风格槽位，供库存/样板/计划合成弹窗复用。
 * - hover 通过 useItemTooltip 单例在全局固定层显示名称/id/数量，
 *   并携带响应式取值函数，数据刷新（如 mock 漂移、真实库存变化）时提示跟随最新值；
 * - 图标当前用按 id 稳定着色的「方块」占位，iconUrl 传入后切换为贴图
 *   （贴图资源就绪时的接入点，pixelated 渲染保持像素风）；
 * - 数量 > 1 时右下角显示紧凑角标（MC 样式白字黑影）。
 */
import { computed, onBeforeUnmount } from 'vue'

import { formatCompact, keyId } from '../../lib/format.js'
import { displayName } from '../../lib/itemName.js'
import { hideTooltip, moveTooltip, showTooltip, useItemTooltip } from '../../composables/useItemTooltip.js'

const props = defineProps({
  /** 条目对象：{ key, amount, nameKey? }，key 为 AEKey JSON；amount 也可以传"需求量"等派生值。 */
  item: { type: Object, required: true },
  /** 物品贴图 URL；为空时使用方块占位。 */
  iconUrl: { type: String, default: '' },
})

/** 由 id 计算稳定色相：同一物品永远同色，便于视觉区分。 */
function hueOf(id) {
  let hash = 0
  for (const char of id) {
    hash = (hash * 31 + char.charCodeAt(0)) | 0
  }
  return Math.abs(hash) % 360
}

/** 占位方块的着色：亮暗渐变 + 高光/阴影模拟方块立体感。 */
const blockStyle = computed(() => {
  const hue = hueOf(keyId(props.item.key))
  return {
    background: `linear-gradient(145deg, hsl(${hue} 42% 44%) 0%, hsl(${hue} 48% 27%) 100%)`,
    boxShadow: `inset 0 1px 0 hsl(${hue} 55% 62% / 0.5), inset 0 -2px 0 rgb(0 0 0 / 0.35)`,
  }
})

/** 提示内容（响应式）：props 更新时随之变化，传给全局提示层跟随渲染。 */
const info = computed(() => ({
  name: displayName(props.item),
  sub: keyId(props.item.key),
  amount: props.item.amount,
}))

// 注意：必须传普通函数而不是 computed ref——ref 存入 reactive 会在读取时被
// 自动解包成值，提示层将无法调用；函数则原样透传，调用时才求值并追踪依赖。
const sourceGetter = () => info.value

/** hover 进入：显示提示并挂上取值函数（数据刷新时提示跟随）。 */
function onEnter(event) {
  showTooltip({ ...info.value, source: sourceGetter }, event)
}

/** 卸载时若提示仍指向本格子（如虚拟滚动窗口移出），立即隐藏避免悬挂。 */
onBeforeUnmount(() => {
  if (tooltipState.source === sourceGetter) {
    hideTooltip()
  }
})

const tooltipState = useItemTooltip()
</script>

<template>
  <div
    class="group relative size-full rounded-[3px] border border-line bg-[#0d0d0f] shadow-[inset_0_1px_0_rgb(255_255_255/0.04),inset_0_-1px_0_rgb(0_0_0/0.4)]"
    @mouseenter="onEnter"
    @mousemove="moveTooltip"
    @mouseleave="hideTooltip"
  >
    <div class="absolute inset-0 flex items-center justify-center">
      <img
        v-if="iconUrl"
        :src="iconUrl"
        :alt="displayName(item)"
        class="size-8 rounded-[3px] [image-rendering:pixelated]"
      />
      <div v-else class="size-8 rounded-[3px]" :style="blockStyle"></div>
    </div>
    <!-- MC 式 hover 高亮层 -->
    <div class="pointer-events-none absolute inset-0 rounded-[3px] bg-white/0 transition-colors group-hover:bg-white/15"></div>
    <span
      v-if="item.amount > 1"
      class="absolute bottom-0 right-0.5 font-mono text-[11px] font-semibold leading-4 text-white [text-shadow:1px_1px_0_rgb(0_0_0/0.9)]"
    >
      {{ formatCompact(item.amount) }}
    </span>
  </div>
</template>
