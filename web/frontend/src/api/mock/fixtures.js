/**
 * Mock 固定数据：按 contracts/web-api.md 的形状构造三个示例网格。
 * 字段与模组端 LocalDataServer 的 JSON 完全一致（accessId 为固定 UUID）。
 */

/** 模拟「3 小时前」的离线快照时间，体现离线网格「数据截至」效果。 */
const STALE_TIME = Date.now() - 3 * 60 * 60 * 1000

/**
 * Mock 网格密码表（仅 mock 演示用，真实密码校验在模组端）：
 * 「主基地 ME 网格」设有密码，解锁面板提示用。
 */
export const GRID_PASSWORDS = {
  '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001': '1234',
}

/** 网格列表 fixture：online 等摘要字段与快照保持一致，由 mock 层推导。 */
export const GRIDS = [
  {
    accessId: '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001',
    displayName: '主基地 ME 网格',
    dimension: 'minecraft:overworld',
    controllerX: 128,
    controllerY: 64,
    controllerZ: -256,
    online: true,
    hasPassword: true,
  },
  {
    accessId: '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a002',
    displayName: '农场子网格',
    dimension: 'minecraft:overworld',
    controllerX: 512,
    controllerY: 70,
    controllerZ: 384,
    online: true,
    hasPassword: false,
  },
  {
    accessId: '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a003',
    displayName: '仓库网格',
    dimension: 'minecraft:the_nether',
    controllerX: -64,
    controllerY: 40,
    controllerZ: 128,
    online: false,
    hasPassword: false,
  },
]

/** AEKey JSON 快捷构造：物品 key 形如 {"id":"minecraft:stone"}。 */
function item(id) {
  return { id }
}

/** 库存条目快捷构造。 */
function stack(id, amount) {
  return { key: item(id), amount }
}

/** 样板输入槽快捷构造：candidates 为 [物品id, 数量] 数组。 */
function input(candidates, multiplier) {
  return {
    possibleInputs: candidates.map(([id, amount]) => stack(id, amount)),
    multiplier,
  }
}

/** 样板摘要快捷构造。 */
function pattern(outputId, outputAmount, inputs) {
  return {
    primaryOutput: stack(outputId, outputAmount),
    outputs: [stack(outputId, outputAmount)],
    inputs,
  }
}

/** 三个网格的完整快照，key 为 accessId；仓库网格为离线（保留最后快照）。 */
export const SNAPSHOTS = {
  '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001': {
    accessId: '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001',
    capturedAt: 0,
    online: true,
    inventory: [
      stack('minecraft:stone', 12844),
      stack('minecraft:cobblestone', 95210),
      stack('minecraft:oak_log', 342),
      stack('minecraft:oak_planks', 1204),
      stack('minecraft:iron_ingot', 512),
      stack('minecraft:raw_iron', 87),
      stack('minecraft:gold_ingot', 96),
      stack('minecraft:diamond', 64),
      stack('minecraft:redstone', 2310),
      stack('minecraft:coal', 4120),
      stack('minecraft:stick', 890),
      stack('minecraft:glass', 640),
      stack('minecraft:sand', 2210),
      stack('minecraft:wheat', 330),
      stack('minecraft:bread', 12),
      stack('ae2:certus_quartz_crystal', 256),
      stack('ae2:charged_certus_quartz_crystal', 118),
    ],
    craftingCpus: [
      { name: '', busy: false, availableStorage: 16384, coProcessors: 0 },
      { name: '并行核', busy: true, availableStorage: 1024, coProcessors: 16 },
    ],
    patterns: [
      pattern('minecraft:crafting_table', 1, [input([['minecraft:oak_planks', 1]], 4)]),
      pattern('minecraft:furnace', 1, [input([['minecraft:cobblestone', 1]], 8)]),
      pattern('minecraft:chest', 1, [
        input([['minecraft:oak_planks', 1], ['minecraft:birch_planks', 1], ['minecraft:spruce_planks', 1]], 8),
      ]),
      pattern('minecraft:glass', 1, [input([['minecraft:sand', 1]], 1)]),
      pattern('minecraft:iron_ingot', 1, [input([['minecraft:raw_iron', 1]], 1)]),
      pattern('minecraft:stick', 4, [input([['minecraft:oak_planks', 2]], 1)]),
      pattern('minecraft:torch', 4, [
        input([['minecraft:stick', 1]], 1),
        input([['minecraft:coal', 1]], 1),
      ]),
      pattern('minecraft:bread', 1, [input([['minecraft:wheat', 3]], 1)]),
    ],
  },
  '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a002': {
    accessId: '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a002',
    capturedAt: 0,
    online: true,
    inventory: [
      stack('minecraft:wheat', 1200),
      stack('minecraft:wheat_seeds', 340),
      stack('minecraft:hay_block', 96),
      stack('minecraft:apple', 64),
      stack('minecraft:bone_meal', 88),
      stack('minecraft:egg', 12),
    ],
    craftingCpus: [{ name: '农场 CPU', busy: true, availableStorage: 512, coProcessors: 4 }],
    patterns: [
      pattern('minecraft:bread', 1, [input([['minecraft:wheat', 3]], 1)]),
      pattern('minecraft:hay_block', 1, [input([['minecraft:wheat', 9]], 1)]),
      pattern('minecraft:glass', 1, [input([['minecraft:sand', 1]], 1)]),
    ],
  },
  '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a003': {
    accessId: '0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a003',
    capturedAt: STALE_TIME,
    online: false,
    inventory: [
      stack('minecraft:cobblestone', 4096),
      stack('minecraft:dirt', 1024),
      stack('minecraft:sand', 512),
      stack('minecraft:stick', 64),
      stack('minecraft:torch', 128),
    ],
    craftingCpus: [{ name: '', busy: false, availableStorage: 8192, coProcessors: 0 }],
    patterns: [
      pattern('minecraft:chest', 1, [input([['minecraft:oak_planks', 1]], 8)]),
      pattern('minecraft:stick', 4, [input([['minecraft:oak_planks', 2]], 1)]),
    ],
  },
}
