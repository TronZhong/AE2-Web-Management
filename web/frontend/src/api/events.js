/**
 * 实时事件订阅（SSE）：封装 EventSource 连接到 GET /api/events。
 * 契约：docs/contracts/web-api.md「实时事件」。
 * USE_MOCK 时 no-op（mock 数据源没有后端事件流，轮询继续充当数据源）。
 * EventSource 浏览器自带重连；未登录时后端返回 401，连接失败自动降级到轮询。
 */
import { USE_MOCK } from './index.js'

/**
 * 订阅后端实时事件流。
 * @param handlers 事件回调：{ onGridsUpdated, onGridSnapshotUpdated }
 *                 - onGridsUpdated(): 网络列表级变化（新增/移除/改名/改密）
 *                 - onGridSnapshotUpdated(accessId): 某网络快照被刷新
 * @returns 取消订阅函数（组件卸载时调用）；USE_MOCK 时返回 no-op
 */
export function subscribeGridEvents({ onGridsUpdated, onGridSnapshotUpdated } = {}) {
  if (USE_MOCK) {
    return () => {}
  }

  const source = new EventSource('/api/events')

  // SSE 默认以 data: 行承载整段 JSON（服务端用 SseEmitter.event().data() 发送）
  source.onmessage = (event) => {
    let payload
    try {
      payload = JSON.parse(event.data)
    } catch {
      return // 非 JSON（如心跳注释行）直接忽略
    }
    if (payload.type === 'grids-updated') {
      onGridsUpdated?.()
    } else if (payload.type === 'grid-snapshot-updated' && payload.accessId) {
      onGridSnapshotUpdated?.(payload.accessId)
    }
  }

  // 断线/401 由浏览器自动重连；不在这里处理登录态，靠既有 5s/10s 轮询兜底
  source.onerror = () => {}

  return () => source.close()
}
