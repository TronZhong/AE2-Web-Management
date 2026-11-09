<script setup>
/**
 * 截断文本悬浮详情：触发区内单行截断展示，末尾（省略号处）带一个向下小箭头，
 * 仅在文本真的溢出时出现；悬停在箭头上弹出完整内容的信息窗口。
 * 信息窗口 teleport 到 body 并用 fixed 定位，避免被滚动容器裁剪；
 * 窗口左上角对齐鼠标（物理坐标按 --app-zoom 换算回布局像素），使鼠标恰好位于窗口内，
 * 移入窗口保持展开，移出窗口（或移出箭头且未进入窗口）即关闭。
 * 触发区仅为箭头本身（外层衬垫扩大命中区），悬浮截断文本不弹出。
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  /** 完整文本：触发区内单行截断展示，信息窗口内完整换行展示。 */
  text: { type: String, default: '' },
})

/** 触发区文本元素：测量 scrollWidth/clientWidth 判断是否溢出。 */
const textEl = ref(null)
/** 文本是否被截断；仅截断时显示向下展开箭头。 */
const overflowing = ref(false)
/** 信息窗口是否展开。 */
const open = ref(false)
/** 信息窗口元素：展开后测量实际尺寸用于视口边缘内收。 */
const panelEl = ref(null)
/** 窗口 fixed 定位（布局像素）。 */
const position = ref({ left: '0px', top: '0px' })

/** 延迟关闭计时器：给鼠标从触发区移入窗口留出间隙。 */
let hideTimer = null

/** 读取 html 的生效缩放系数（--app-zoom）；未设置时按 1 处理。 */
function zoomFactor() {
  return Number.parseFloat(getComputedStyle(document.documentElement).zoom) || 1
}

/** 测量文本是否溢出触发区宽度（留 1px 容差）。 */
function measure() {
  const el = textEl.value
  overflowing.value = !!el && el.scrollWidth > el.clientWidth + 1
}

/** 清除延迟关闭计时器。 */
function clearHideTimer() {
  if (hideTimer !== null) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
}

/**
 * 展开信息窗口：窗口左上角对齐鼠标，保证鼠标恰在窗口内；
 * 渲染后按窗口实际尺寸向右/下边缘内收，避免超出视口。
 */
function show(event) {
  if (!props.text) {
    return
  }
  clearHideTimer()
  const zoom = zoomFactor()
  position.value = { left: `${event.clientX / zoom}px`, top: `${event.clientY / zoom}px` }
  open.value = true
  nextTick(() => {
    const el = panelEl.value
    if (!el) {
      return
    }
    const maxLeft = Math.max(0, (window.innerWidth - el.offsetWidth - 8) / zoom)
    const maxTop = Math.max(0, (window.innerHeight - el.offsetHeight - 8) / zoom)
    position.value = {
      left: `${Math.min(event.clientX / zoom, maxLeft)}px`,
      top: `${Math.min(event.clientY / zoom, maxTop)}px`,
    }
  })
}

/** 立即关闭信息窗口（移出窗口时调用）。 */
function hide() {
  clearHideTimer()
  open.value = false
}

/** 离开箭头：延迟关闭；若期间移入窗口，由窗口的 mouseenter 取消。 */
function scheduleHide() {
  clearHideTimer()
  hideTimer = setTimeout(hide, 120)
}

/** 文本变化后重新测量溢出（如命令状态翻转导致回执文案变化）。 */
watch(
  () => props.text,
  () => nextTick(measure),
)

let resizeObserver = null
onMounted(() => {
  measure()
  resizeObserver = new ResizeObserver(measure)
  if (textEl.value) {
    resizeObserver.observe(textEl.value)
  }
})
onBeforeUnmount(() => {
  clearHideTimer()
  resizeObserver?.disconnect()
})
</script>

<template>
  <span class="flex min-w-0 items-center gap-1">
    <span ref="textEl" class="min-w-0 truncate">{{ text }}</span>
    <!-- 向下小展开头：仅文本溢出（出现省略号）时显示；仅箭头处触发悬浮详情，
         p-1 扩大命中区、-m-1 抵消占位使视觉位置不变 -->
    <span
      v-if="overflowing"
      class="-m-1 shrink-0 cursor-pointer p-1 text-ink-faint transition-colors hover:text-ink"
      @mouseenter="show"
      @mouseleave="scheduleHide"
    >
      <svg
        viewBox="0 0 12 12"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        class="block size-3"
        aria-hidden="true"
      >
        <path d="M2.5 4.5 6 8l3.5-3.5" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </span>
    <Teleport to="body">
      <div
        v-if="open"
        ref="panelEl"
        class="fixed z-50 max-h-48 max-w-[26rem] overflow-y-auto rounded-md border border-line bg-panel-2 px-3 py-2 shadow-xl shadow-black/50"
        :style="position"
        @mouseenter="clearHideTimer"
        @mouseleave="hide"
      >
        <p class="break-words font-mono text-xs leading-relaxed text-ink">{{ text }}</p>
      </div>
    </Teleport>
  </span>
</template>
