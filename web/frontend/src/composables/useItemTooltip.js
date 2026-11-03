import { reactive } from 'vue'

/**
 * 物品悬浮提示的全局单例状态：ItemSlot 写入（hover 事件），ItemTooltip 渲染。
 * 用单例而不是组件 props，保证滚动容器外的固定层提示随任意格子联动。
 *
 * `source` 是来源格子的取值函数（普通函数，调用时求值并追踪响应依赖）：
 * 底层数据被轮询刷新（数量变化）时，提示内容跟随最新值，而不是停留在 hover 瞬间的快照。
 * 注意不要把 computed ref 直接存进来——ref 在 reactive 中读取时会被自动解包。
 */
const state = reactive({
  visible: false,
  name: '',
  sub: '',
  amount: null,
  x: 0,
  y: 0,
  source: null,
})

/** 记录鼠标物理坐标（供 ItemTooltip 换算缩放后定位）。 */
function applyPosition(event) {
  state.x = event.clientX
  state.y = event.clientY
}

/** 悬浮进入格子：写入条目信息并显示提示；info.source 为可选的响应式取值函数。 */
export function showTooltip(info, event) {
  state.visible = true
  state.name = info.name
  state.sub = info.sub ?? ''
  state.amount = info.amount ?? null
  state.source = info.source ?? null
  applyPosition(event)
}

/** 悬浮移动：仅更新坐标。 */
export function moveTooltip(event) {
  applyPosition(event)
}

/** 离开格子：隐藏提示并解除数据源引用。 */
export function hideTooltip() {
  state.visible = false
  state.source = null
}

/** 供 ItemTooltip 读取的共享状态。 */
export function useItemTooltip() {
  return state
}
