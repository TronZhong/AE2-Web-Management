/**
 * Mock 数据源：按契约形状提供网格列表、快照读取与合成命令，模拟 120–300ms 网络延迟。
 * 在线网格每次读取会「推进采样」：刷新 capturedAt 并让少量库存数量小幅漂移，
 * 用于演示轮询刷新效果；离线网格原样返回最后快照。
 * 命令部分模拟模组命令表语义：全量历史按 submittedAt 升序返回；
 * 在线网格的 PENDING 命令经延时后转为 SUBMITTED，离线网格保持排队。
 * 网格密码（契约 v0.5）：主基地网格设有密码，锁定网格的 snapshot/craft/commands
 * 抛 grid_locked，unlockGrid 校验 fixtures.GRID_PASSWORDS 并按会话记住解锁。
 */
import { GRIDS, GRID_PASSWORDS, SNAPSHOTS } from './fixtures.js'

/** 会发生数量漂移的物品 id（模拟网格存取活动）。 */
const DRIFT_IDS = new Set(['minecraft:stone', 'minecraft:coal', 'minecraft:wheat'])

/** 在线网格的 PENDING 命令等待多少毫秒后转为 SUBMITTED（模拟模组队列调度）。 */
const SUBMIT_DELAY_MS = 6_000

/** 模块级命令历史：accessId → 命令数组（按 submittedAt 升序），对应模组 CommandStore。 */
const COMMAND_HISTORY = new Map()

/** 模块级已解锁网格集合（当前 mock 会话内有效，模拟后端 account_grid_access 表）。 */
const UNLOCKED_GRIDS = new Set()

/**
 * 判断某网格对当前会话是否可访问：未设密码恒可访问；设了密码则须已解锁。
 * @param grid 网格摘要对象（含 hasPassword）
 */
function isUnlocked(grid) {
  return !grid.hasPassword || UNLOCKED_GRIDS.has(grid.accessId)
}

/** 模拟网络延迟，让 loading 态可见。 */
async function latency() {
  await new Promise((resolve) => setTimeout(resolve, 120 + Math.random() * 180))
}

/** 构造命令条目（模组契约同构；result 在 PENDING 时为 null）。 */
function makeCommand(accessId, target, amount, state, submittedAt, result) {
  return {
    commandId: crypto.randomUUID(),
    accessId,
    state,
    submittedAt,
    result,
    amount,
    target,
  }
}

/**
 * 取某网格的命令历史（懒初始化）：预置两条历史记录（一条已提交、一条失败），
 * 让命令列表在首次进入时就具备可演示的数据。
 */
function ensureHistory(accessId) {
  if (!COMMAND_HISTORY.has(accessId)) {
    const now = Date.now()
    const seedPattern = SNAPSHOTS[accessId]?.patterns?.[0]?.primaryOutput
    const history = []
    if (seedPattern) {
      history.push(
        makeCommand(accessId, seedPattern.key, seedPattern.amount * 8, 'SUBMITTED', now - 30 * 60_000, 'submitted to AE2; craftingId=8f3c2a10-5d41-4b7e-9c2f-6a8b0d1e3f52'),
        makeCommand(accessId, seedPattern.key, seedPattern.amount * 999_999, 'FAILED', now - 25 * 60_000, 'AE2 rejected job: MISSING_INGREDIENT: missing minecraft:x x999'),
      )
    }
    COMMAND_HISTORY.set(accessId, history)
  }
  return COMMAND_HISTORY.get(accessId)
}

/** 推进在线网格的 PENDING 命令：超过等待时长后置为 SUBMITTED 并写入模组同格式回执；离线网格保持排队。 */
function advanceCommands(history, online) {
  if (!online) {
    return
  }
  const now = Date.now()
  for (const command of history) {
    if (command.state === 'PENDING' && now - command.submittedAt >= SUBMIT_DELAY_MS) {
      command.state = 'SUBMITTED'
      command.result = `submitted to AE2; craftingId=${command.commandId}`
    }
  }
}

/** 对在线快照推进一次采样：刷新 capturedAt，漂移物品数量 ±40 内且不低于 0。 */
function simulateTick(snapshot) {
  for (const entry of snapshot.inventory) {
    if (DRIFT_IDS.has(entry.key.id)) {
      entry.amount = Math.max(0, entry.amount + Math.floor((Math.random() - 0.45) * 80))
    }
  }
  snapshot.capturedAt = Date.now()
  return snapshot
}

/** GET /api/grids（mock）：摘要统计从快照推导，保证与详情页一致；unlocked 按 v0.5 计算。 */
export async function fetchGrids() {
  await latency()
  const now = Date.now()
  return {
    grids: GRIDS.map((grid) => {
      const snapshot = SNAPSHOTS[grid.accessId]
      const capturedAt = grid.online ? now : snapshot.capturedAt
      return {
        ...grid,
        capturedAt,
        inventoryEntries: snapshot.inventory.length,
        cpuCount: snapshot.craftingCpus.length,
        unlocked: isUnlocked(grid),
      }
    }),
  }
}

/** GET /api/grids/{accessId}/snapshot（mock）：未知 accessId 抛 grid_not_found，锁定网格抛 grid_locked。 */
export async function fetchSnapshot(accessId) {
  await latency()
  const snapshot = SNAPSHOTS[accessId]
  if (!snapshot) {
    throw new Error('grid_not_found')
  }
  const grid = GRIDS.find((item) => item.accessId === accessId)
  if (grid && !isUnlocked(grid)) {
    throw new Error('grid_locked')
  }
  if (!snapshot.online) {
    return structuredClone(snapshot)
  }
  return simulateTick(structuredClone(snapshot))
}

/**
 * POST /api/grids/{accessId}/craft（mock）：校验规则与后端/模组对齐，
 * 入队 PENDING 命令并返回 202 受理响应 {commandId, state, submittedAt}。
 * 锁定网格抛 grid_locked（契约 v0.5 访问控制）。
 */
export async function submitCraft(accessId, key, amount) {
  await latency()
  if (!SNAPSHOTS[accessId]) {
    throw new Error('grid_not_found')
  }
  const grid = GRIDS.find((item) => item.accessId === accessId)
  if (grid && !isUnlocked(grid)) {
    throw new Error('grid_locked')
  }
  if (key == null || amount == null) {
    throw new Error('missing_key_or_amount')
  }
  if (!Number.isInteger(amount) || amount <= 0) {
    throw new Error('invalid_amount')
  }
  if (typeof key !== 'object' || Array.isArray(key) || Object.keys(key).length === 0) {
    throw new Error('invalid_key')
  }
  const history = ensureHistory(accessId)
  // key 可能携带 Vue 响应式代理（ref 深层 reactive 化），structuredClone 无法克隆 Proxy，
  // 先经 JSON 往返退化为普通对象（AEKey 为纯 JSON 数据，无损）
  const command = makeCommand(accessId, JSON.parse(JSON.stringify(key)), amount, 'PENDING', Date.now(), null)
  history.push(command)
  return { commandId: command.commandId, state: command.state, submittedAt: command.submittedAt }
}

/** GET /api/grids/{accessId}/commands（mock）：先推进状态，再按升序返回全量历史；锁定网格抛 grid_locked。 */
export async function fetchCommands(accessId) {
  await latency()
  const snapshot = SNAPSHOTS[accessId]
  if (!snapshot) {
    throw new Error('grid_not_found')
  }
  const grid = GRIDS.find((item) => item.accessId === accessId)
  if (grid && !isUnlocked(grid)) {
    throw new Error('grid_locked')
  }
  const history = ensureHistory(accessId)
  advanceCommands(history, snapshot.online)
  return { commands: structuredClone(history) }
}

/**
 * POST /api/grids/{accessId}/unlock（mock）：校验 fixtures.GRID_PASSWORDS 并
 * 按会话记住解锁（对应后端按账号持久记忆的 account_grid_access 表）。
 * 网格不存在抛 grid_not_found；密码不匹配抛 invalid_password；
 * 未设密码的网格幂等返回成功。
 */
export async function unlockGrid(accessId, password) {
  await latency()
  const grid = GRIDS.find((item) => item.accessId === accessId)
  if (!grid) {
    throw new Error('grid_not_found')
  }
  const expected = GRID_PASSWORDS[accessId]
  if (expected !== undefined && password !== expected) {
    throw new Error('invalid_password')
  }
  UNLOCKED_GRIDS.add(accessId)
  return null
}

/** 模块级 mock 会话：登录后记住用户名，供 fetchMe 恢复（仅内存，刷新页面即失效）。 */
let mockSession = null

/**
 * POST /api/auth/login（mock）：接受任意非空用户名/密码，
 * 返回形状与真实契约一致；空凭证抛 unauthorized 模拟登录失败。
 */
export async function login(username, password) {
  await latency()
  if (!username || !password) {
    throw new Error('unauthorized')
  }
  mockSession = { username }
  return { tokenName: 'satoken', tokenValue: 'mock-token', username }
}

/** POST /api/auth/logout（mock）：清空 mock 会话，幂等。 */
export async function logout() {
  await latency()
  mockSession = null
  return {}
}

/** GET /api/auth/me（mock）：已登录返回用户名，否则抛 unauthorized。 */
export async function fetchMe() {
  await latency()
  if (!mockSession) {
    throw new Error('unauthorized')
  }
  return { ...mockSession }
}
