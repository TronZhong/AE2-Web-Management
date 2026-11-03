/**
 * 展示格式化工具：数量、存储字节、时间与 AEKey 友好名。
 * 纯函数集合，不依赖组件与网络层。
 */

/** 千分位格式化数量（快照 amount 为 long，安全整数范围内直接展示）。 */
export function formatAmount(value) {
  return new Intl.NumberFormat('en-US').format(value)
}

/**
 * 字节数格式化（AE2 存储以字节计）：1024 进制，保留一位小数并去掉多余的 .0。
 */
export function formatStorage(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '?'
  }
  const units = ['B', 'k', 'M', 'G', 'T']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  const rounded = unit === 0 ? String(value) : value.toFixed(1).replace(/\.0$/, '')
  return `${rounded}${units[unit]}`
}

/** epoch 毫秒 → HH:mm:ss；0 或无效值返回「—」。 */
export function formatTime(epochMs) {
  if (!epochMs) {
    return '—'
  }
  const date = new Date(epochMs)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

/** 紧凑数量（物品格子角标用）：万以下原样，之上 k/M/G 一位小数并去掉 .0。 */
export function formatCompact(value) {
  if (value < 10_000) {
    return String(value)
  }
  const trim = (number_) => number_.toFixed(1).replace(/\.0$/, '')
  if (value < 1_000_000) {
    return `${trim(value / 1_000)}k`
  }
  if (value < 1_000_000_000) {
    return `${trim(value / 1_000_000)}M`
  }
  return `${trim(value / 1_000_000_000)}G`
}

/** 从 AEKey JSON 中取物品 id（如 "minecraft:stone"）；无 id 返回 "?"。 */
export function keyId(key) {
  return typeof key?.id === 'string' ? key.id : '?'
}

/**
 * 物品 id → 友好名："minecraft:dark_oak_planks" → "Dark Oak Planks"。
 * 无可靠汉化数据源，先做英文词首大写；id 始终在次要行展示原文。
 */
export function friendlyName(key) {
  const id = keyId(key)
  const path = id.includes(':') ? id.slice(id.indexOf(':') + 1) : id
  return path
    .split('_')
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ') || id
}
