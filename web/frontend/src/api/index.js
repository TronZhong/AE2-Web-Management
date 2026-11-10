/**
 * 后端 Web API 客户端（前端唯一的网络出口，组件不得自行 fetch）。
 * 契约：docs/contracts/web-api.md（v0.7：认证端点 + 受保护的只读/命令端点 + 图标转发）。
 * 默认走真实请求（实时数据）；设置 VITE_USE_MOCK=true 启用 mock 数据源（或 npm run dev:mock）。
 */
import * as mock from './mock/index.js'

/** 是否使用 mock 数据源（默认 false 走实时数据；仅显式 VITE_USE_MOCK=true 时启用 mock）。 */
export const USE_MOCK = (import.meta.env?.VITE_USE_MOCK ?? 'false') === 'true'

/** 401 统一处理回调（由 main.js 注册：清除会话并跳转登录页）。 */
let unauthorizedHandler = null

/**
 * 注册 401（未登录 / 会话过期）的统一处理回调；契约错误码 unauthorized。
 * @param handler 收到 401 时调用的回调
 */
export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

/**
 * 统一响应处理：正常时返回 JSON；异常时把契约错误体 {"error": code}
 * 归一化为带机器可读码的 Error（如 grid_not_found），供页面直接展示。
 * 401 会额外触发 onUnauthorized 注册的回调。
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
  if (response.status === 401) {
    unauthorizedHandler?.(code)
  }
  throw new Error(code)
}

/** GET /api/grids → { grids: GridSummary[] }，见契约「网格列表」。 */
export async function fetchGrids() {
  if (USE_MOCK) {
    return mock.fetchGrids()
  }
  return unwrap(await fetch('/api/grids'))
}

/**
 * GET /api/grids/{accessId}/snapshot → 快照对象，见契约「单网格快照」。
 * 网格不存在 / 从未采样时抛出 grid_not_found / snapshot_not_found。
 */
export async function fetchSnapshot(accessId) {
  if (USE_MOCK) {
    return mock.fetchSnapshot(accessId)
  }
  return unwrap(await fetch(`/api/grids/${encodeURIComponent(accessId)}/snapshot`))
}

/**
 * POST /api/grids/{accessId}/craft → 202 受理响应 {commandId, state, submittedAt}，
 * 见契约「合成命令端点」。校验失败抛 missing_key_or_amount / invalid_amount / invalid_key。
 * @param accessId 网格接入 ID
 * @param key      目标 AEKey JSON（复用快照 key 形状，不重命名）
 * @param amount   合成数量，必须为正整数
 */
export async function submitCraft(accessId, key, amount) {
  if (USE_MOCK) {
    return mock.submitCraft(accessId, key, amount)
  }
  return unwrap(
    await fetch(`/api/grids/${encodeURIComponent(accessId)}/craft`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ key, amount }),
    }),
  )
}

/**
 * GET /api/grids/{accessId}/commands → { commands: CommandEntry[] }，
 * 见契约「合成命令端点」。模组返回该网格全量命令历史（按 submittedAt 升序）。
 */
export async function fetchCommands(accessId) {
  if (USE_MOCK) {
    return mock.fetchCommands(accessId)
  }
  return unwrap(await fetch(`/api/grids/${encodeURIComponent(accessId)}/commands`))
}

/**
 * POST /api/grids/{accessId}/unlock → 204：解锁设有网格密码的网格（契约 v0.5）。
 * 密码不匹配抛 invalid_password；网格不存在抛 grid_not_found；
 * 成功后解锁状态按账号持久记忆，访问 snapshot/craft/commands 不再需要密码。
 * @param accessId 网格接入 ID
 * @param password 网格密码（游戏内 GUI 设置的那个）
 */
export async function unlockGrid(accessId, password) {
  if (USE_MOCK) {
    return mock.unlockGrid(accessId, password)
  }
  const response = await fetch(`/api/grids/${encodeURIComponent(accessId)}/unlock`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ password }),
  })
  // 204 无响应体，直接成功返回；其余走统一错误归一化
  if (response.status === 204) {
    return null
  }
  return unwrap(response)
}

/**
 * DELETE /api/grids/{accessId}/commands/{commandId} → 204：删除命令记录（契约 v0.9）。
 * 删除 PENDING 记录即撤销（不会提交给 AE2，误发撤回入口）；删除 SUBMITTED/FAILED
 * 记录仅清理历史，不影响 AE2 已受理的任务。记录不存在（含已删除，重复点击/竞态）
 * 抛 command_not_found；网格不存在抛 grid_not_found；锁定网格抛 grid_locked。
 * @param accessId  网格接入 ID
 * @param commandId 命令 ID
 */
export async function deleteCommand(accessId, commandId) {
  if (USE_MOCK) {
    return mock.deleteCommand(accessId, commandId)
  }
  const response = await fetch(
    `/api/grids/${encodeURIComponent(accessId)}/commands/${encodeURIComponent(commandId)}`,
    { method: 'DELETE' },
  )
  // 204 无响应体，直接成功返回；其余走统一错误归一化
  if (response.status === 204) {
    return null
  }
  return unwrap(response)
}

/**
 * 由 AEKey JSON 构造物品图标 URL（契约 v0.7「图标转发端点」）：
 * {@code key.id}（如 {@code "minecraft:iron_block"}）→ {@code /api/icons/minecraft/iron_block.png}。
 *
 * <p>仅在 id 两段都匹配模组路径字符集 {@code [a-z0-9_.-]} 时生成（与模组
 * {@code GET /icons} 的校验一致，避免必然 404/400 的请求）；非物品 key
 * （无字符串 id）、异常 id 或 mock 模式（无图标来源）返回空串，组件回退
 * 占位方块。同一物品多条库存共用同一 URL，浏览器按 URL 去重，每图标一次下载。</p>
 *
 * @param key AEKey JSON（复用快照 key 形状，不重命名）
 * @returns 图标 URL 或空串
 */
export function iconUrlOf(key) {
  if (USE_MOCK) {
    return ''
  }
  const id = typeof key?.id === 'string' ? key.id : ''
  const sep = id.indexOf(':')
  if (sep <= 0 || sep === id.length - 1) {
    return ''
  }
  const namespace = id.slice(0, sep)
  const path = id.slice(sep + 1)
  const segment = /^[a-z0-9_.-]+$/
  if (
    !segment.test(namespace) ||
    !segment.test(path) ||
    namespace === '.' || namespace === '..' ||
    path === '.' || path === '..'
  ) {
    return ''
  }
  return `/api/icons/${namespace}/${path}.png`
}

/**
 * POST /api/auth/login → {tokenName, tokenValue, username}，见契约「认证端点」。
 * 凭证错误抛 unauthorized；satoken Cookie 由后端 Set-Cookie 下发，同源自动携带。
 * @param username 用户名
 * @param password 密码
 */
export async function login(username, password) {
  if (USE_MOCK) {
    return mock.login(username, password)
  }
  return unwrap(
    await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ username, password }),
    }),
  )
}

/** POST /api/auth/logout → {}：使当前会话失效，幂等。 */
export async function logout() {
  if (USE_MOCK) {
    return mock.logout()
  }
  return unwrap(await fetch('/api/auth/logout', { method: 'POST' }))
}

/**
 * GET /api/auth/me → {username}：查询当前登录账号。
 * 未登录 / 会话过期抛 unauthorized（应用启动时用于恢复会话）。
 */
export async function fetchMe() {
  if (USE_MOCK) {
    return mock.fetchMe()
  }
  return unwrap(await fetch('/api/auth/me'))
}
