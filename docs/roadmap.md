# 后续迭代路线（规划）

> 文档首页：[`index.md`](index.md) › 设计

本页只收录**待办迭代项**与**已识别的问题/隐患**，按优先级从高到低排列；已完成项已移除，完成情况与交接记录见 git 历史及各组件文档。与实现冲突时以代码为准并回写修正。动手实现某一节前，先按仓库规范同步受影响的契约与组件文档；动模组代码的批次统一把 `mod_version` 递增（当前 `0.0.12`）。

## 1. 优先级总览

| 优先级 | 事项 | 类型 | 状态 |
| --- | --- | --- | --- |
| P1 | 物品中文名称（`nameKey`） | 待办 · UX | ✅ 已完成（0.0.9） |
| P2 | 物品图标（客户端渲染导出） | 待办 · UX | ✅ 已完成（模组 0.0.10 + 后端/前端，契约 v0.7） |
| P2 | 命令记录删除与历史自动清理 | 待办 | ✅ 已完成（0.0.13，契约 v0.9） |
| P2 | `lastSeenAt` 随快照回写 | 待办 | ✅ 已完成（0.0.14，契约 v0.10） |
| P2 | 附魔属性展示（方案 A：前端解析 `key.components`） | 待办 · UX | 已拍板 |
| P3 | 前端小改进（列表排序 + 回到顶部） | 待办 · UX | 已拍板（小改动） |
| P3 | 每网络配对令牌 | 隐患 · 安全 | 暂缓（实施前再确认场景） |
| P3 | 语言表外部配置化（模块化加载） | 待办 · 工程 | 暂缓（出现第三方模组语言表需求再实施） |

> 历史已完成的 P1–P2 各节均动过模组代码，按批次合并提交，每批一个主题、`mod_version` 递增 1；新拍板的 §7 / §10 为**纯前端批次**，不动模组与契约。

## 2. 问题与隐患清单

已识别、尚未消除的隐患，处理见对应待办：

| 隐患 | 影响 | 处理 |
| --- | --- | --- |
| 服务端无多语言，物品名无中文 | 模组端 `getHoverName()` 恒为英文；前端只能英文友好名/占位，UX 差 | ✅ §4 已实施（0.0.9）：快照传 `nameKey` + 前端官方中文语言表 |
| 前端物品无图标 | `ItemSlot.vue` 只有按 id 着色的占位方块 | ✅ §5 已实施（模组 0.0.10 + 后端/前端 v0.7）：`/api/icons` 字节转发 + `ItemSlot` 懒加载贴图，失败/未渲染回退占位方块 |
| 附魔书等组件物品只显示基础名称 | 锋利 V 附魔书与普通附魔书在网页上无法区分；附魔数据已在 `key.components` 中随快照传输，仅前端未解析（§7 已核实链路） | §7 方案 A |
| 库存条目 `:key` 只用物品 id | 同 id 不同组件（附魔书 / 改名 / 不同耐久）撞 Vue key，虚拟滚动复用有渲染错乱风险 | §7 一并修复（id + 组件摘要） |
| PENDING 命令无法撤销 | 命令一直等待网络上线后自动提交，误发无法撤回，前端不敢提供取消按钮 | ✅ §6 已实施（0.0.13）：DELETE 删除 PENDING 记录即撤销 |
| `lastSeenAt` 不随快照回写 | 在线/离线判定与展示失实 | ✅ §6 已实施（0.0.14）：快照循环在线轮回写并随 `GET /grids` 暴露 |
| 授权仅网格密码，无按网络隔离 | 多玩家/对外部署权限粒度不足；token 语义暂无需求 | §8 暂缓，实施前确认场景 |
| 模组无自动化测试 | CI 只构建模组，游戏内行为靠手动复现，回归风险高 | 后续补 NeoForge GameTest |

## 3. P1 · 术语统一为 grid ✅ 已完成（0.0.8）

> 已按下方清单执行完毕：模组端点 `/grids`、后端 `Grid*` 类与 `/api/grids`、前端 `fetchGrids`/`useGrids`、文档同步；`SavedData` 与 `accessId` 保持不变。实现记录见 git 历史。

### 现状与隐患

AE2 代码层把"网络"称为 `IGrid`/`Grid`（design.md §9）。对外契约层（后端 `Network*` 类、`/api/networks`、模组 `/networks` 端点）用 network，而前端页面/组件、后端访问控制领域表（`GridAccess`）与模组 `grid/` 包用 grid。

### 决策：全站统一到 grid

| 端 | 改动 |
| --- | --- |
| 模组（0.0.8） | `LocalDataServer` 端点 `/networks`→`/grids`、JSON key `networks`→`grids`；`network/` 包改名 `grid/`（`GridIdentity` / `GridIdentityStore`）；错误码 `network_not_found`→`grid_not_found` |
| 后端（契约 v0.4） | `NetworkController`→`GridController`、`NetworkService`→`GridService`、`Network*Response`→`Grid*Response`、`ModApiClient.listNetworks`→`listGrids`；URL `/api/networks`→`/api/grids`；错误码 `network_locked`→`grid_locked` |
| 前端 | `fetchNetworks`→`fetchGrids`、`useNetworks` 读 `data.grids`、mock 常量/返回 key 同步、`network_locked`→`grid_locked` |
| 契约/文档 | `docs/contracts/index.md`、`web-api.md`（v0.3→v0.4）、`design.md` §6、各组件 `index.md`、`navigation.md`、`architecture.md` 同步 |

**兼容注意**：只改 Java 类名与 HTTP/JSON 术语；`SavedData` 文件名（`ae2web_identities.dat`）与 NBT 结构不变，不丢存档数据。`accessId` 保留（接入 ID，与 grid/network 之争无关）。

## 4. P1 · 物品中文名称 ✅ 已完成（0.0.9）

> 已按下方清单执行完毕：模组快照 stack 条目附可选 `nameKey`（物品 `Item#getDescriptionId()`、流体 `FluidType#getDescriptionId()`）、后端 `StackEntry` DTO 透传（Web 响应省略 null）、前端引入官方中文语言表（minecraft 取自 client.jar，ae2 取自其 Modrinth jar，仅保留 `block.*` / `item.*` / `fluid_type.*` 键）并实现「`nameKey` 查表 → id 启发式 → 英文友好名」三级回退；契约 v0.6（模组 0.0.9）。实现记录见 git 历史。

### 现状与隐患

- 模组快照只传 AEKey 通用 NBT（如 `{"#t":"ae2:i","id":"minecraft:iron_block"}`）；前端 `friendlyName()`（`src/lib/format.js`）只做英文 id 首字母大写，无中文数据源。
- **关键技术事实**：Minecraft 服务端没有多语言——`Language.getInstance()` 只加载 en_us，模组端调 `getHoverName().getString()` 等永远解析出英文，"模组直接传出中文名"不可行。

### 决策：模组传 `nameKey`（翻译键），前端查中文语言表

| 端 | 改动 |
| --- | --- |
| 模组（0.0.9） | `SnapshotManager.nameKeyOf()` 采集处生成翻译键：物品取 `Item#getDescriptionId()`（纯字符串拼接，如 `block.minecraft.stone` / `item.ae2.controller`），流体取 `FluidType#getDescriptionId()`（`fluid_type.*`，按 AE2 源码 `AEFluidKey.computeDisplayName()` → `FluidStack#getHoverName()` 确认），其他 AEKey 类型从显示名组件提取翻译键。`Snapshot.StackEntry` 增加可选 `nameKey` 字段并随 `SnapshotStore` NBT 持久化、HTTP JSON 输出 |
| 后端 | `StackEntry` DTO 透传加字段（`JsonNode key` 不变）；`@JsonInclude(NON_NULL)` 使 Web 响应省略 null（向后兼容） |
| 前端 | 引入官方 `zh_cn.json` 语言表（minecraft 取自 client.jar，ae2 取自其 Modrinth jar；入库前过滤为 `block.*` / `item.*` / `fluid_type.*` 键），`src/lib/itemName.js` 新模块 `displayName()` 按 `nameKey` 查表得中文名；查不到回退启发式与现有英文友好名；组件与 mock fixtures 同步接线 |
| 契约（v0.6） | 已更新 `docs/contracts/index.md`、`docs/contracts/web-api.md`：`inventory[]` / `patterns[]` 各 stack 条目新增可选 `nameKey`（向后兼容） |

### 过渡方案（已作为前端二级回退）

前端按 id 启发式拼翻译键（`block.<ns>.<path>` / `item.<ns>.<path>` 双尝试）+ 语言表查询。vanilla 几乎全对、模组物品大多遵循约定，但存在反例；仅作过渡与回退，最终以模组 `nameKey` 为准。

## 5. P2 · 物品图标 ✅ 已完成（模组 0.0.10 + 后端/前端 v0.7）

> **已按下方「细化方案」全链路实施完毕**：模组客户端 `IconService` 离屏渲染、
> `LocalDataServer` 新增 `GET /icons/{namespace}/{path}.png` 静态文件服务（含路径穿越防护、
> 一天缓存头、404 惰性补齐）、`Config` 新增 `iconExportEnabled` / `iconRenderPerFrame`（模组 0.0.10，
> 渲染挂点经源码核实为 NeoForge 1.21.1 的 **`RenderFrameEvent.Post`**，该版本无 `RenderTickEvent`）；
> 后端 `GET /api/icons/{namespace}/{path}.png` 纯字节转发（`ModApiClient.fetchIcon` 不做 JSON 解析、
> `IconController` 校验字符集后透传 `Content-Type` 与 `Cache-Control`）与前端 `iconUrlOf()` +
> `ItemSlot` 懒加载/失败回退接线（契约 v0.7）。实现记录见 git 历史。

### 现状与隐患（实施前）

前端 `ItemSlot.vue` 已预留 `iconUrl` prop 与 `pixelated` 渲染分支，此前用按 id 着色的方块占位；只差贴图来源。

### 决策：模组客户端渲染导出（契合"模组是数据出口"架构）

1. 模组客户端（`AE2WebManagementClient`）遍历快照中出现过的物品 key，用 `Minecraft.getInstance().getItemRenderer()` 离屏渲染 64×64 PNG（0.0.11 起；16×16 GUI 单位逻辑画布 4× 超采样），缓存到 `config/ae2webmanagement/icons/<分辨率>/<namespace>/<path>.png`（方块物品得到真实物品栏 3D 图标）；
2. `LocalDataServer` 增加 `GET /icons/{namespace}/{path}.png`（仅回环，与现有 API 同级）；
3. 后端增加图标转发端点（如 `GET /api/icons/{namespace}/{path}.png`，可不做 per-network）；
4. 前端把 `iconUrl` 接到该地址，实现前同步更新契约文档。

### 细化方案（含性能设计）

> 本节为实施蓝图。核心分工：**渲染（客户端侧完成，产物是磁盘 PNG）与 HTTP 服务（服务端侧，只读静态文件）是两条独立路径**——HTTP 层不渲染、只读文件；渲染层不接 HTTP、只写文件，互不触碰，因此 HTTP 线程永不接触 Minecraft 状态。

#### 模组端：`IconService`（客户端专用，性能核心）

新增 `mod/.../client/icon/IconService`，仅在客户端侧初始化（`Minecraft.getInstance() != null`）。核心机制是「作业队列 + 每帧预算」，让任意规模的网络都只产生渐进式补齐、不产生单帧卡顿：

- **作业队列 + 每帧预算**：HTTP 线程 / 快照观察只把 key 入队（`ConcurrentLinkedQueue`，只存 AEKey 或 NBT 串，不碰任何 Minecraft 状态）；客户端线程在帧间事件（**已验证挂点：NeoForge 1.21.1 的 `RenderFrameEvent.Post`**）每帧最多消费 N 个（默认 2）。数万物品也只是几十秒后台渐进渲染，任何一帧不掉帧。
- **复用单个 `Framebuffer`**：一次性创建 64×64（如需更平滑可放大渲染再缩小）离屏画布全程复用，绑定后调 `ItemRenderer`（借游戏的物品渲染管线），画完 `NativeImage.downloadTexture` 读回像素并**立即恢复默认 framebuffer 与 `RenderSystem` 状态**（projection / blit offset / color / shader），避免污染下一帧。玩家画面完全不受影响（不截屏、不叠加）。
- **去重**：内存 `Set<String>`（已入队）+ 磁盘存在性检查（已渲染）。已存在的 PNG 永不重渲染；快照循环只在出现**新 key** 时入队，不每 4 秒全量重扫。
- **IO 下移**：读回像素后，PNG 编码（`NativeImage.writePng`）与文件写入交给单线程 daemon executor，渲染线程不碰磁盘。
- **快照 key 来源与节流**：单人模式（integrated server）下 `AE2WebManagement.SNAPSHOT_MANAGER` 是同一 JVM 的共享静态，客户端线程直接读其缓存；观察节流（如每 40 tick 一次或仅 key 集合变化时），只 diff 出新增 key。
- **专用服务器（dedicated）**：无客户端 → `IconService` 不激活、零开销、不崩溃；`/icons` 一律 404，前端落回占位方块。特性天然服务于「自建单人存档 + Web 面板」用法，文档需写明该边界。

#### `LocalDataServer`：静态文件服务（绝不现算）

- `GET /icons/{namespace}/{path}.png` 命中磁盘直接回读字节（64×64 PNG 数百字节，单线程 HTTP executor 无压力）。
- **路径穿越防护**：namespace/path 只允许 `[a-z0-9_.-]`，拼接根目录后 `normalize` 并校验前缀，非法 400/404。
- **缓存头**（与 JSON 端点的 `no-store` 不同）：`Content-Type: image/png` + `Cache-Control: public, max-age=86400`（图标一经渲染即静态，一天后浏览器自然重验证）。
- **缺失 → 404 + 惰性补齐**：文件不存在时返回 404，同时把 key 入队，下一次请求大概率已渲染完成；前端 404 时回退占位方块并重试。

#### 后端：纯字节代理

- `ModApiClient` 增加按 `byte[]` 取图标的转发（**不做 JSON 解析**、不进 service 业务层）；`IconController`（或并入 `GridController`）校验字符集后转发，透传 `Content-Type` 与 `Cache-Control`。
- 模组不可达 → 502/503，前端 `@error` 回退占位。后端不做自建缓存，浏览器缓存承担主要去重，保持纯代理。

#### 前端：懒加载 + 错误回退

- `ItemSlot.vue` 加 `loading="lazy"`（虚拟滚动窗口外不加载）与 `@error` 回退到占位方块（现有 `v-else` 分支）。
- `iconUrl` 计算为 `/api/icons/{ns}/{path}.png`；同一物品多条库存共用同一 URL，浏览器按 URL 去重，每个图标一次下载。
- 不预加载：配合模组端惰性渲染，图标渐进出现即可。

#### 配置与契约

- 模组 `Config` 新增（0.0.10 已实现）：`iconExportEnabled`（默认 true，弱机器/专用服务器可关）、`iconRenderPerFrame`（默认 2）。
- 契约升级至 v0.7（已实施）：`docs/contracts/index.md` 记录模组侧 `GET /icons/{namespace}/{path}.png`（0.0.10 落地）；`docs/contracts/web-api.md`「图标转发端点」记录后端 `GET /api/icons/{namespace}/{path}.png` 字节转发（本批落地）。模组代码未再改动，`mod_version` 保持 `0.0.10`。

#### 取舍与边界

- **NBT 变体图标**：缓存文件名 `<ns>/<path>.png` 忽略 NBT，第一个见到的变体决定图标（AE2 库存多为普通物品，可接受）。若需精确，可在文件名追加 tag 哈希（key 集合有界，代价可控）。先做 canonical-first。
- **流体图标**：`ItemRenderer` 渲染不了流体，单独走 `IClientFluidTypeExtensions.getStillTexture()`（NeoForge 1.21.1 的流体贴图客户端扩展，`FluidType` 本身已无该方法）贴图 blit 路径；其他 AEKey 类型跳过（前端占位）。
- **缓存失效**：图标一经渲染即不可变；分辨率编入缓存目录（0.0.11 起 `icons/<分辨率>/`），调整 `IconPaths.ICON_RESOLUTION` 后旧缓存自动失效、新目录惰性重渲染；更换资源包仍需手动删 `config/ae2webmanagement/icons/`，`max-age` 保证一天内自愈。

### 备选（不推荐，仅记录理由）

- 前端静态贴图包（构建期从 jar 抽 PNG）：vanilla 方块只有平面贴图、观感打折，modded 需手工维护；
- 第三方贴图 CDN：只覆盖 vanilla、依赖外网，不可控。

## 6. P2 · 模组其他遗留 ✅ 已完成

> 两项均已按拍板方案实施完毕，实现记录见 git 历史：命令删除为模组 0.0.13 + 契约 v0.9，`lastSeenAt` 回写为模组 0.0.14 + 契约 v0.10。

- **命令记录删除/撤销与历史自动清理** ✅ 已完成（0.0.13，契约 v0.9）
  > 已按拍板方案实施完毕：`DELETE /grids/{accessId}/commands/{commandId}` **物理删除**记录（不引入 CANCELLED 状态）——删 PENDING 即撤销（主线程原子执行，`MinecraftServer#execute` 同步等待响应即结果，与模拟/提交串行化并取消在途计算），删 SUBMITTED/FAILED 仅清理历史；每网格自动保留最近 `commandHistoryLimit`（默认 50，模组配置）条终态记录，PENDING 不自动清理；后端 `CraftController`/`GridService`/`ModApiClient` 纯转发（新增 `invalid_command_id`/`command_not_found`），前端 `CommandList` 每行两段式确认删除按钮。实现记录见 git 历史。
- **`lastSeenAt` 随快照回写** ✅ 已完成（0.0.14，契约 v0.10）
  > `SnapshotManager.tick` 对在线网格调 `GridIdentityStore.touchLastSeen` 回写最近在线时间（离线轮不更新，返回的新身份覆盖回镜像缓存），随 `SavedData` 持久化跨重启正确；`GET /grids` 摘要新增 `lastSeenAt`（`ModGridSummaryResponse` 可空承接旧版模组 → `GridSummaryResponse` `@JsonInclude(NON_NULL)` 输出），前端离线网格卡片显示「最后在线」。

## 7. P2 · 附魔属性展示（方案 A：前端解析 `key.components`）

> 已拍板方案 A。目标：附魔书等组件物品在网页可读出附魔明细（如「锋利 V」），并顺带修复库存条目撞 key 隐患。纯前端批次，零契约 / 模组 / 后端改动。

### 现状（2026-11 已核实）

- **附魔数据已随快照传输**：模组采集用 `AEKey.toTagGeneric()`（`SnapshotManager.capture` → `stack.getKey().toTagGeneric(registryAccess)`）；对物品即 `AEItemKey.CODEC` = Minecraft 1.21.1 的 ItemStack **完整组件序列化**（经 javap 反编译 AE2 19.2.18 核实，且 `AEItemKey` 的相等性判断就是 `isSameItemSameComponents`——组件本身是物品身份的一部分）；`LocalDataServer.toJson()` 以 `NbtOps → JsonOps` 无损转 JSON。契约 `docs/contracts/web-api.md` 的 `key` 字段本就注明「含组件时附加字段」——该口子正是为此类场景预留。
- 锋利 V 附魔书在前端实际收到的 key 预期形状：

```json
{
  "id": "minecraft:enchanted_book",
  "count": 1,
  "components": {
    "minecraft:stored_enchantments": {
      "levels": { "minecraft:sharpness": 5 },
      "show_in_tooltip": 1
    }
  }
}
```

- **显示缺口在展示层**：`nameKeyOf()` 恒取 `item.minecraft.enchanted_book` → 前端只显示「附魔书」。名称不带附魔与游戏 / AE2 终端行为一致（游戏内附魔明细也是 tooltip 行而非名称），属前端未解析组件，不是数据丢失。
- **注意点**：附魔书用 `minecraft:stored_enchantments` 组件，普通附魔工具 / 武器是 `minecraft:enchantments`，两者结构相同（`levels` map + `show_in_tooltip`），解析时要都处理。
- **语言表缺口**：`src/data/lang/minecraft.zh_cn.json` 子集只保留 `block.*` / `item.*` / `fluid_type.*`（2822 键），`enchantment.*` 为 0 键，附魔中文名无处可查。
- **已识别 bug**：`InventoryCard.vue` 条目 `:key="keyId(entry.key)"` 只取 id——同 id 不同组件的两本附魔书（改名、不同耐久同理）会撞 Vue key，虚拟滚动复用有渲染错乱风险；`PatternListCard.vue` 以 `keyId + ':' + index` 规避（样板列表按 key 排序稳定，index 方案可用，可不动）。

### 决策：方案 A——模组传原始身份，前端解析展示（与 `nameKey` 同哲学）

| 改动点 | 内容 |
| --- | --- |
| `src/lib/` 组件解析（新增 `lib/components.js` 或并入 `itemName.js`） | 读 `key.components["minecraft:stored_enchantments"]` / `["minecraft:enchantments"]` 的 `levels`，产出 `[{id, lvl}]`；组件缺失 / 形状不符 / 解析异常一律返回空数组，优雅降级为现状 |
| 语言表子集 | `minecraft.zh_cn.json` 扩充 `enchantment.*`（含 `enchantment.level.1`~`enchantment.level.10` 罗马数字键）；`ae2.zh_cn.json` 同步扩充 AE2 附魔键；等级 >10 或键缺失兜底直接显示数字 |
| tooltip（`ItemSlot.vue` / `useItemTooltip.js` / `ItemTooltip.vue`） | 名称下新增附魔行（仿 AE2：青 / 灰紫色「锋利 V」），多附魔多行 |
| 搜索 | `InventoryCard` / `PatternListCard` 关键字过滤顺带匹配附魔名（中 / 英文），搜「锋利」可命中对应附魔书 |
| 撞 key 修复 | `InventoryCard` 条目 key 改为「id + 组件摘要」（组件 JSON 序列化或简单哈希）；无组件物品退化为纯 id，行为不变 |
| 契约 / 文档 | 零契约改动（数据本就在 `key` 内）；`docs/frontend/index.md` 源码映射与已完成项同步；`contracts/` 不动 |

### 边界与决策点（实施时定）

- **显示位置**：推荐**仅 tooltip**（仿 AE2 / 原版，名称仍为「附魔书」）；备选名称后缀「附魔书（锋利 V）」——列表可读性更好但名称变长。
- **覆盖范围**：本期只解析附魔类组件；药水效果、旗帜图案、成书页面等复杂组件**不做**（tooltip 生成复杂度高），需要时再评估方案 B。
- **`show_in_tooltip: 0`**：跟随原版语义隐藏附魔行；注意 JsonOps 转换后为数字 `1` / `0` 而非布尔。
- **实施前快照确认**：对真实快照（模组口 `127.0.0.1:8090/grids` 免登录）确认 `components` 在位；旧存档残留快照等下一个采样周期即可。

### 备选（不采纳，仅记录理由）

**方案 B（模组结构化抽取 + 契约 v0.9）**：模组从组件提取 `enchantments:[{id,lvl}]`（或更通用的 `tooltipLines`）随快照 NBT / HTTP 透传。前端不依赖组件格式、未来其他组件可统一入口；但需动模组 + `SnapshotStore` NBT + 契约 + 前端，工程量大，且与 `key` 内已有数据重复传输。当前目标方案 A 已达成，B 仅在未来要系统化展示多类组件时重启。

## 8. P3 · 每网络配对令牌（暂缓）

- 适用场景：多玩家 / 对外部署，需要按网络隔离授权；网格密码已覆盖个人自用与小型多人场景，随机 token 语义暂无需求。
- 若实施：`AccessScreen` 展示已注册网络的 `accessId` + token（一键复制），token 字符串同步需要新的通道（`AccessMenu` 仍为 `SimpleContainerData(2)`，只能传 int，token 需自定义 payload，可复用 `GridPasswordPayload` / `GridIdentityPayload` 的模式）；「重置 token」按钮 + 后端侧同步失效。
- token 流向约定：模组 `/grids` 可把 token 输出给后端（模组口仅 127.0.0.1 + 共享密钥可达，泄漏面可控），由**后端持有并校验，不下发给前端**。
- displayName 改名已在 GUI 迭代中实现（模组 0.0.7），token 展示如再实施可与后续 GUI 改动同批提交。

## 9. P3 · 语言表外部配置化（模块化加载，暂缓）

- 现状：`src/data/lang/` 的 `zh_cn.json` 由 `itemName.js` **构建期静态 import** 合并进 `ZH_TABLE`；新增第三方模组语言表需改源码 import 并重新构建，不够"放入即用"。
- 目标：语言表改为外部配置式——文件放进指定目录即可被加载，不触碰源码。
- 候选方案（实施前再定）：
  1. **运行时加载（推荐方向）**：语言表由后端静态目录提供（如新增 `GET /lang/*.json` 端点，与"前端只与后端通信"架构一致）或前端 `public/lang/`；应用启动时预载全部表到 `ZH_TABLE` 缓存，`displayName()` 保持同步纯函数不变（预载后查缓存），现有组件零改动。新增模组只丢一个 json，连构建都不需要；加载失败静默回退英文友好名。实现方案 1 需新增后端契约端点并同步契约文档。
  2. **Vite `import.meta.glob`（构建期自动收集）**：`itemName.js` 一次性改为 glob 导入 `data/lang/*.json`，之后加文件免改源码，但仍需重新构建。
  3. 维持现状（源码 import）。
- 兼容注意：`nameKey` 查表语义与「查表 → 启发式 → 英文友好名」回退链保持不变，仅"表从哪来"变化。
- 优先级：低。当前仅 minecraft/ae2 两张官方表，源码 import 成本可接受；待第三方模组语言表需求出现再实施。

## 10. P3 · 前端小改进（列表排序 + 回到顶部）

> 已拍板。两个互相独立的小改动，均为纯前端批次，零契约改动；建议拆两批提交（每批一个主题）。

### 现状

- 库存卡（`InventoryCard.vue`）与样板卡（`PatternListCard.vue`）只有关键字过滤、**无排序**；物品种类多时只能靠搜索定位，浏览无固定顺序。
- 可滚动区域（库存 480px 固定视口、样板 440px / flex-1、CPU 列表 91px、命令列表 126px）滚下去之后没有回到顶部的入口。

### 决策 1：列表排序（复刻 AE2 终端语义）

AE2 终端排序为 `SortOrder{NAME, AMOUNT, MOD} × SortDir{ASC, DESC}`（经 javap 反编译 AE2 19.2.18 核实：`appeng.api.config.SortOrder`、`appeng.client.gui.me.common.KeySorters`——NAME 按 `AEKey.getDisplayName().getString()` **本地化名**排，AMOUNT 在 `Repo.updateView()` 内按存量排，MOD 按模组来源排）。Java 实现不可直接复用，把语义搬到前端：

| 卡 | 排序项 | 说明 |
| --- | --- | --- |
| 库存卡 | 名称 / 数量 × 升 / 降 | 名称按 `displayName()` 中文名 `localeCompare('zh')`（拼音序，体验优于码点序）；数量按 `entry.amount`，同数量回退名称序 |
| 样板卡 | 名称 × 升 / 降 | `primaryOutput.amount` 是单次输出量（通常恒 1），数量排序无意义，不做 |
| 控件 | 卡片右上角搜索框旁 | 循环切换按钮或小下拉（默认名称升序）；实现在现有 `filtered` computed 末尾追加排序即可，虚拟滚动 `rowCount / visibleRows` 逻辑不动 |

### 决策 2：回到顶部（方向感知）

- 行为：**向上滚动**（往回看）时在滚动容器右下角浮现「回到顶部」小图标；**向下滚动即隐藏**；点击平滑回顶（`viewport.scrollTo({top: 0, behavior: 'smooth'})`）。
- 实现：可复用组合式函数 `useBackToTop(viewportRef)`（记录上次 scrollTop 判断滚动方向，到顶后自动隐藏）+ `BackToTopButton.vue`（绝对定位于滚动容器内，样式沿用设计令牌）。
- 接入范围：只接**库存、样板**两个大视口；CPU（91px）/ 命令（126px）列表视口仅两行余，回顶成本极低，不加图标避免视觉噪音（后续有反馈再放开）。

### 改动范围

仅 `web/frontend`（两张卡 + 新增组合式函数与按钮组件），零契约改动；完成后同步 `docs/frontend/index.md` 源码映射与本节状态。

## 相关文档

- [技术设计](design.md)（已收敛的权威设计）
- [架构总览](architecture.md)
- [数据契约](../contracts/index.md)
- [模组](mod/index.md) · [后端](backend/index.md) · [前端](frontend/index.md)
