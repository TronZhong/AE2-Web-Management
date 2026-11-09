<script setup>
/**
 * 物品格子：MC 风格槽位，供库存/样板/计划合成弹窗复用。
 * - hover 通过 useItemTooltip 单例在全局固定层显示名称/id/数量，
 *   并携带响应式取值函数，数据刷新（如 mock 漂移、真实库存变化）时提示跟随最新值；
 * - iconUrl 传入时渲染模组客户端渲染导出的 64×64 PNG 贴图（roadmap.md §5，
 *   经后端 /api/icons 字节转发；loading="lazy" 且失败回退占位方块并有限重试，
 *   配合模组端 404 惰性补齐渐进出现），为空时用按 id 着色的「方块」占位；
 * - 数量 > 1 时右下角显示紧凑角标（MC 样式白字黑影）。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { formatCompact, keyId } from '../../lib/format.js'
import { displayName } from '../../lib/itemName.js'
import { hideTooltip, moveTooltip, showTooltip, useItemTooltip } from '../../composables/useItemTooltip.js'

const props = defineProps({
  /** 条目对象：{ key, amount, nameKey? }，key 为 AEKey JSON；amount 也可以传"需求量"等派生值。 */
  item: { type: Object, required: true },
  /** 物品贴图 URL；为空时使用方块占位。 */
  iconUrl: { type: String, default: '' },
})

/** 图标重试参数：模组 404 表示图标尚未渲染（已惰性入队），稍后重试大概率命中。 */
const ICON_RETRY_DELAY_MS = 5000
const ICON_RETRY_LIMIT = 2

/** 图标加载失败标记：true 时回退占位方块。 */
const iconFailed = ref(false)

/** 已发出的图标重试次数；重试 URL 追加 ?retry=N 绕开任何被缓存的失败响应。 */
const iconRetry = ref(0)

/** 图标重试定时器（组件卸载 / URL 变化时清理）。 */
let iconRetryTimer = null

/** 清掉待执行的重试定时器。 */
function clearIconRetryTimer() {
  if (iconRetryTimer) {
    clearTimeout(iconRetryTimer)
    iconRetryTimer = null
  }
}

/** 实际下发的图标地址：失败期间为空串（渲染占位方块），重试时附加序号参数。 */
const iconSrc = computed(() => {
  if (!props.iconUrl || iconFailed.value) {
    return ''
  }
  return iconRetry.value ? `${props.iconUrl}?retry=${iconRetry.value}` : props.iconUrl
})

/**
 * img @error：先回退占位方块，再按有界次数安排重试（防止专用服务器等
 * 图标恒 404 的场景无限重试）。iconUrl 变化时重置全部重试状态。
 */
function onIconError() {
  clearIconRetryTimer()
  iconFailed.value = true
  if (iconRetry.value >= ICON_RETRY_LIMIT || !props.iconUrl) {
    return
  }
  iconRetry.value += 1
  iconRetryTimer = setTimeout(() => {
    iconRetryTimer = null
    iconFailed.value = false
  }, ICON_RETRY_DELAY_MS)
}

// iconUrl 变化（数据刷新 / 复用格子换物品）时重置失败与重试计数，重新走一次加载。
watch(
  () => props.iconUrl,
  () => {
    clearIconRetryTimer()
    iconFailed.value = false
    iconRetry.value = 0
  },
)

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

/** 卸载时若提示仍指向本格子（如虚拟滚动窗口移出），立即隐藏避免悬挂；并清掉图标重试定时器。 */
onBeforeUnmount(() => {
  clearIconRetryTimer()
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
        v-if="iconSrc"
        :src="iconSrc"
        :alt="displayName(item)"
        loading="lazy"
        class="size-8 rounded-[3px] [image-rendering:pixelated]"
        @error="onIconError"
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
