/**
 * Mock 数据源：按契约形状提供网络列表与快照读取，模拟 120–300ms 网络延迟。
 * 在线网络每次读取会「推进采样」：刷新 capturedAt 并让少量库存数量小幅漂移，
 * 用于演示轮询刷新效果；离线网络原样返回最后快照。
 */
import { NETWORKS, SNAPSHOTS } from './fixtures.js'

/** 会发生数量漂移的物品 id（模拟网络存取活动）。 */
const DRIFT_IDS = new Set(['minecraft:stone', 'minecraft:coal', 'minecraft:wheat'])

/** 模拟网络延迟，让 loading 态可见。 */
async function latency() {
  await new Promise((resolve) => setTimeout(resolve, 120 + Math.random() * 180))
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

/** GET /api/networks（mock）：摘要统计从快照推导，保证与详情页一致。 */
export async function fetchNetworks() {
  await latency()
  const now = Date.now()
  return {
    networks: NETWORKS.map((network) => {
      const snapshot = SNAPSHOTS[network.accessId]
      const capturedAt = network.online ? now : snapshot.capturedAt
      return {
        ...network,
        capturedAt,
        inventoryEntries: snapshot.inventory.length,
        cpuCount: snapshot.craftingCpus.length,
      }
    }),
  }
}

/** GET /api/networks/{accessId}/snapshot（mock）：未知 accessId 抛 network_not_found。 */
export async function fetchSnapshot(accessId) {
  await latency()
  const snapshot = SNAPSHOTS[accessId]
  if (!snapshot) {
    throw new Error('network_not_found')
  }
  if (!snapshot.online) {
    return structuredClone(snapshot)
  }
  return simulateTick(structuredClone(snapshot))
}
