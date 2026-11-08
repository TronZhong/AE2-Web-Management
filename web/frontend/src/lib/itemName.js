/**
 * 物品中文名解析：按翻译键 {@code nameKey} 查官方简体中文语言表，
 * 查不到时回退「id → 翻译键」启发式，最终回退英文友好名。
 *
 * <p>数据源（均为官方文件「物品名相关键」子集——只保留 {@code block.*} / {@code item.*} /
 * {@code fluid_type.*} 前缀，正文未作任何转写）：</p>
 * <ul>
 *   <li>{@code minecraft.zh_cn.json}：取自 Minecraft 1.21.1 client.jar 的
 *       {@code assets/minecraft/lang/zh_cn.json}；</li>
 *   <li>{@code ae2.zh_cn.json}：取自 AE2 19.2.18（Modrinth jar）的
 *       {@code assets/ae2/lang/zh_cn.json}。</li>
 * </ul>
 *
 * <p>对应 docs/roadmap.md §4：模组快照传播 {@code nameKey}（翻译键），
 * 前端查表得中文名——Minecraft 服务端不加载多语言，不能直接出中文名。</p>
 */
import minecraftZh from '../data/lang/minecraft.zh_cn.json'
import ae2Zh from '../data/lang/ae2.zh_cn.json'
import { friendlyName, keyId } from './format.js'

/** 合并后的中文语言表：两个命名空间无键冲突，直接展开。 */
const ZH_TABLE = { ...minecraftZh, ...ae2Zh }

/**
 * 按翻译键查中文语言表。
 *
 * @param nameKey 物品翻译键（如 {@code block.minecraft.stone}）
 * @returns 中文名；表内不存在（或传入空）返回 null
 */
export function lookupZh(nameKey) {
  if (typeof nameKey !== 'string' || !nameKey) {
    return null
  }
  const value = ZH_TABLE[nameKey]
  return typeof value === 'string' && value ? value : null
}

/**
 * 过渡启发式翻译键（docs/roadmap.md §4「过渡方案」）：
 * 按 id 拼 {@code block.<ns>.<path>} / {@code item.<ns>.<path>} 双尝试。
 * 语言表本身起判别作用（方块键命中的是方块、物品键命中的是物品）；
 * 绝大多数 vanilla/模组物品命中，存在少量反例，最终以模组 {@code nameKey} 为准。
 *
 * @param id 物品 id，如 {@code minecraft:raw_iron}
 * @returns 查表命中的中文名；都未命中返回 null
 */
export function heuristicName(id) {
  if (typeof id !== 'string' || !id.includes(':')) {
    return null
  }
  const [namespace, path] = id.split(/:(.*)/s)
  if (!path) {
    return null
  }
  return lookupZh(`block.${namespace}.${path}`) ?? lookupZh(`item.${namespace}.${path}`)
}

/**
 * 条目展示名：优先模组 {@code nameKey} 查表 → 启发式查表 → 英文友好名。
 *
 * @param stack 快照条目对象（{@code {key, amount, nameKey?}}）或含
 *              {@code key} 的对象（如命令 {@code target}，无 nameKey）
 * @returns 用于展示的名称
 */
export function displayName(stack) {
  const key = stack?.key
  if (key) {
    const fromKey = lookupZh(stack.nameKey) ?? heuristicName(keyId(key))
    if (fromKey) {
      return fromKey
    }
  }
  return friendlyName(key)
}