/**
 * 后端 Web API 客户端（前端唯一的网络出口，组件不得自行 fetch）。
 * 契约：docs/contracts/web-api.md（v0）。
 * 后端转发未实现期间默认使用 mock 数据源；设置 VITE_USE_MOCK=false 切换真实请求。
 */
import * as mock from './mock/index.js'

/** 是否使用 mock 数据源（默认 true，直至后端 /api 转发上线）。 */
export const USE_MOCK = (import.meta.env?.VITE_USE_MOCK ?? 'true') !== 'false'

/**
 * 统一响应处理：正常时返回 JSON；异常时把契约错误体 {"error": code}
 * 归一化为带机器可读码的 Error（如 network_not_found），供页面直接展示。
 */
async function unwrap(response) {
  if (response.ok) {
    return response.json()
  }
  let code = `http_${response.status}`
  try {
    const body = await response.json()
    if (body && typeof body.error === 'string') {
      code = body.error
    }
  } catch {
    // 响应体不是 JSON 时保留状态码错误
  }
  throw new Error(code)
}

/** GET /api/networks → { networks: NetworkSummary[] }，见契约「网络列表」。 */
export async function fetchNetworks() {
  if (USE_MOCK) {
    return mock.fetchNetworks()
  }
  return unwrap(await fetch('/api/networks'))
}

/**
 * GET /api/networks/{accessId}/snapshot → 快照对象，见契约「单网络快照」。
 * 网络不存在 / 从未采样时抛出 network_not_found / snapshot_not_found。
 */
export async function fetchSnapshot(accessId) {
  if (USE_MOCK) {
    return mock.fetchSnapshot(accessId)
  }
  return unwrap(await fetch(`/api/networks/${encodeURIComponent(accessId)}/snapshot`))
}
