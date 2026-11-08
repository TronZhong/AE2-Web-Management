# 后续迭代路线（规划）

> 文档首页：[`index.md`](index.md) › 设计

本页只收录**待办迭代项**与**已识别的问题/隐患**，按优先级从高到低排列；已完成项已移除，完成情况与交接记录见 git 历史及各组件文档。与实现冲突时以代码为准并回写修正。动手实现某一节前，先按仓库规范同步受影响的契约与组件文档；动模组代码的批次统一把 `mod_version` 递增（当前 `0.0.7`）。

## 1. 优先级总览

| 优先级 | 事项 | 类型 | 状态 |
| --- | --- | --- | --- |
| P1 | 术语统一为 grid | 待办 · 一致性重构（契约 v0.4） | 已拍板 |
| P1 | 物品中文名称（`nameKey`） | 待办 · UX | 已拍板 |
| P2 | 物品图标（客户端渲染导出） | 待办 · UX | 已拍板 |
| P2 | 模组其他遗留（命令取消、`lastSeenAt`） | 待办 | 已拍板 |
| P3 | 每网络配对令牌 | 隐患 · 安全 | 暂缓（实施前再确认场景） |

> P1–P2 各节都动模组代码，可按批次合并提交，每批一个主题、`mod_version` 递增 1。

## 2. 问题与隐患清单

已识别、尚未消除的隐患，处理见对应待办：

| 隐患 | 影响 | 处理 |
| --- | --- | --- |
| 术语 network/grid 分裂 | 契约层（`Network*` 类、`/api/networks`、模组 `/networks`）与 `grid` 包/前端命名不一致，越晚改成本越高 | §3 全站统一到 grid |
| 服务端无多语言，物品名无中文 | 模组端 `getHoverName()` 恒为英文；前端只能英文友好名/占位，UX 差 | §4 传 `nameKey` + 前端语言表 |
| 前端物品无图标 | `ItemSlot.vue` 只有按 id 着色的占位方块 | §5 客户端渲染导出 |
| PENDING 命令无法撤销 | 命令一直等待网络上线后自动提交，误发无法撤回，前端不敢提供取消按钮 | §6 命令取消 API |
| `lastSeenAt` 不随快照回写 | 在线/离线判定与展示失实 | §6 快照循环回写 |
| 授权仅网格密码，无按网络隔离 | 多玩家/对外部署权限粒度不足；token 语义暂无需求 | §7 暂缓，实施前确认场景 |
| 模组无自动化测试 | CI 只构建模组，游戏内行为靠手动复现，回归风险高 | 后续补 NeoForge GameTest |

## 3. P1 · 术语统一为 grid

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

## 4. P1 · 物品中文名称

### 现状与隐患

- 模组快照只传 AEKey 通用 NBT（如 `{"#t":"ae2:i","id":"minecraft:iron_block"}`）；前端 `friendlyName()`（`src/lib/format.js`）只做英文 id 首字母大写，无中文数据源。
- **关键技术事实**：Minecraft 服务端没有多语言——`Language.getInstance()` 只加载 en_us，模组端调 `getHoverName().getString()` 等永远解析出英文，"模组直接传出中文名"不可行。

### 决策：模组传 `nameKey`（翻译键），前端查中文语言表

| 端 | 改动 |
| --- | --- |
| 模组 | `SnapshotManager` 采集处对物品 key 取对应 `Item#getDescriptionId()`（纯字符串拼接，如 `block.minecraft.stone` / `item.ae2.controller`），`Snapshot.StackEntry` 增加可选 `nameKey` 字段并在 HTTP JSON 输出；流体等其他 AEKey 类型的翻译键来源实施时按 AE2 源码确认 |
| 后端 | `StackEntry` DTO 透传加字段（`JsonNode key` 不变） |
| 前端 | 引入官方 `zh_cn.json` 语言表（minecraft 的取自 client.jar，ae2 的取自其 Modrinth jar），按 `nameKey` 查表得中文名；查不到回退现有英文友好名 |
| 契约 | **实现前先更新** `docs/contracts/index.md`：`inventory[]` / `patterns[]` 各 stack 条目新增可选 `nameKey`（向后兼容） |

### 过渡方案（可选，零模组改动）

前端按 id 启发式拼翻译键（`block.<ns>.<path>` / `item.<ns>.<path>` 双尝试）+ 语言表查询。vanilla 几乎全对、模组物品大多遵循约定，但存在反例；仅作过渡，最终以模组 `nameKey` 为准。

## 5. P2 · 物品图标

### 现状与隐患

前端 `ItemSlot.vue` 已预留 `iconUrl` prop 与 `pixelated` 渲染分支，当前用按 id 着色的方块占位；只差贴图来源。

### 决策：模组客户端渲染导出（契合"模组是数据出口"架构）

1. 模组客户端（`AE2WebManagementClient`）遍历快照中出现过的物品 key，用 `Minecraft.getInstance().getItemRenderer()` 离屏渲染 16×16 PNG，缓存到 `config/ae2webmanagement/icons/<namespace>/<path>.png`（方块物品得到真实物品栏 3D 图标）；
2. `LocalDataServer` 增加 `GET /icons/{namespace}/{path}.png`（仅回环，与现有 API 同级）；
3. 后端增加图标转发端点（如 `GET /api/icons/{namespace}/{path}.png`，可不做 per-network）；
4. 前端把 `iconUrl` 接到该地址，实现前同步更新契约文档。

### 备选（不推荐，仅记录理由）

- 前端静态贴图包（构建期从 jar 抽 PNG）：vanilla 方块只有平面贴图、观感打折，modded 需手工维护；
- 第三方贴图 CDN：只覆盖 vanilla、依赖外网，不可控。

## 6. P2 · 模组其他遗留

- **命令删除/取消 API**：`DELETE /grids/{accessId}/commands/{commandId}`。当前 PENDING 命令无法撤销、会一直等网络上线后自动提交；有了它前端命令列表才敢提供取消按钮。实现前更新契约文档。
- **`lastSeenAt` 随快照回写**：design.md §4.1 已标注，快照循环 `tick` 时更新身份表。

## 7. P3 · 每网络配对令牌（暂缓）

- 适用场景：多玩家 / 对外部署，需要按网络隔离授权；网格密码已覆盖个人自用与小型多人场景，随机 token 语义暂无需求。
- 若实施：`AccessScreen` 展示已注册网络的 `accessId` + token（一键复制），token 字符串同步需要新的通道（`AccessMenu` 仍为 `SimpleContainerData(2)`，只能传 int，token 需自定义 payload，可复用 `GridPasswordPayload` / `GridIdentityPayload` 的模式）；「重置 token」按钮 + 后端侧同步失效。
- token 流向约定：模组 `/grids` 可把 token 输出给后端（模组口仅 127.0.0.1 + 共享密钥可达，泄漏面可控），由**后端持有并校验，不下发给前端**。
- displayName 改名已在 GUI 迭代中实现（模组 0.0.7），token 展示如再实施可与后续 GUI 改动同批提交。

## 相关文档

- [技术设计](design.md)（已收敛的权威设计）
- [架构总览](architecture.md)
- [数据契约](../contracts/index.md)
- [模组](mod/index.md) · [后端](backend/index.md) · [前端](frontend/index.md)
