# 后续迭代路线（规划）

> 文档首页：[`index.md`](index.md) › 设计

本页收录**已拍板、待实施**的迭代项与决策理由；已完成项已移除，完成情况见各组件文档与 git 历史，最近一轮收尾的交接备忘见 §7。与交接会话记录冲突时，以本页 + 实际代码为准并回写修正。动手实现某一节前，先按仓库规范同步受影响的契约与组件文档；动模组代码的批次统一把 `mod_version` 递增（当前 `0.0.7`）。

## 1. 迭代顺序总览

| # | 主题 | 改动面 | 状态 |
| --- | --- | --- | --- |
| 1 | 术语统一为 grid（契约 v0.4） | 模组 + 契约 + 后端 + 前端 | 已拍板（2026-11） |
| 2 | 物品中文名称（`nameKey`） | 模组 + 契约 + 后端 DTO + 前端 | 已拍板 |
| 3 | 物品图标（客户端渲染导出） | 模组 + 契约 + 后端 + 前端 | 已拍板 |
| 4 | 模组其他遗留（命令取消、`lastSeenAt`） | 模组 + 契约 | 已拍板 |
| 5 | 每网络配对令牌 | 模组 + 契约 + 后端 | 暂缓（实施前再确认场景） |

> #1–#4 都动模组代码，可按批次合并提交，每批一个主题、`mod_version` 递增 1。

## 2. 术语统一为 grid

### 背景

AE2 代码层把"网络"称为 `IGrid`/`Grid`（design.md §9）。此前工程术语分裂：对外契约层（后端 `Network*` 类、`/api/networks`、模组 `/networks` 端点）用 network，而前端页面/组件、后端访问控制领域表（`GridAccess`）与模组 `grid/` 包用 grid。

### 决策：全站统一到 grid

| 端 | 改动 |
| --- | --- |
| 模组（0.0.8） | `LocalDataServer` 端点 `/networks`→`/grids`、JSON key `networks`→`grids`；`network/` 包改名 `grid/`（`GridIdentity` / `GridIdentityStore`）；错误码 `network_not_found`→`grid_not_found` |
| 后端（契约 v0.4） | `NetworkController`→`GridController`、`NetworkService`→`GridService`、`Network*Response`→`Grid*Response`、`ModApiClient.listNetworks`→`listGrids`；URL `/api/networks`→`/api/grids`；错误码 `network_locked`→`grid_locked` |
| 前端 | `fetchNetworks`→`fetchGrids`、`useNetworks` 读 `data.grids`、mock 常量/返回 key 同步、`network_locked`→`grid_locked` |
| 契约/文档 | `docs/contracts/index.md`、`web-api.md`（v0.3→v0.4）、`design.md` §6、各组件 `index.md`、`navigation.md`、`architecture.md` 同步 |

**兼容注意**：只改 Java 类名与 HTTP/JSON 术语；`SavedData` 文件名（`ae2web_identities.dat`）与 NBT 结构不变，不丢存档数据。`accessId` 保留（接入 ID，与 grid/network 之争无关）。

## 3. 物品中文名称

### 背景

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

## 4. 物品图标

### 背景

前端 `ItemSlot.vue` 已预留 `iconUrl` prop 与 `pixelated` 渲染分支，当前用按 id 着色的方块占位；只差贴图来源。

### 决策：模组客户端渲染导出（契合"模组是数据出口"架构）

1. 模组客户端（`AE2WebManagementClient`）遍历快照中出现过的物品 key，用 `Minecraft.getInstance().getItemRenderer()` 离屏渲染 16×16 PNG，缓存到 `config/ae2webmanagement/icons/<namespace>/<path>.png`（方块物品得到真实物品栏 3D 图标）；
2. `LocalDataServer` 增加 `GET /icons/{namespace}/{path}.png`（仅回环，与现有 API 同级）；
3. 后端增加图标转发端点（如 `GET /api/icons/{namespace}/{path}.png`，可不做 per-network）；
4. 前端把 `iconUrl` 接到该地址，实现前同步更新契约文档。

### 备选（不推荐，仅记录理由）

- 前端静态贴图包（构建期从 jar 抽 PNG）：vanilla 方块只有平面贴图、观感打折，modded 需手工维护；
- 第三方贴图 CDN：只覆盖 vanilla、依赖外网，不可控。

## 5. 模组其他遗留

- **命令删除/取消 API**：`DELETE /grids/{accessId}/commands/{commandId}`。当前 PENDING 命令无法撤销、会一直等网络上线后自动提交；有了它前端命令列表才敢提供取消按钮。实现前更新契约文档。
- **`lastSeenAt` 随快照回写**：design.md §4.1 已标注，快照循环 `tick` 时更新身份表。

## 6. 每网络配对令牌（暂缓，实施前与用户再确认场景）

- 适用场景：多玩家 / 对外部署，需要按网络隔离授权；网格密码已覆盖个人自用与小型多人场景，随机 token 语义暂无需求。
- 若实施：`AccessScreen` 展示已注册网络的 `accessId` + token（一键复制），token 字符串同步需要新的通道（`AccessMenu` 仍为 `SimpleContainerData(2)`，只能传 int，token 需自定义 payload，可复用 `GridPasswordPayload` / `GridIdentityPayload` 的模式）；「重置 token」按钮 + 后端侧同步失效。
- token 流向约定：模组 `/grids` 可把 token 输出给后端（模组口仅 127.0.0.1 + 共享密钥可达，泄漏面可控），由**后端持有并校验，不下发给前端**。
- displayName 改名已在 GUI 迭代中实现（模组 0.0.7），token 展示如再实施可与后续 GUI 改动同批提交。

## 7. 交接记录（2026-11 批次：#2 SSE / #3 登出 / #4 GUI 迭代）

> 供下一会话接续，不参与 §1 迭代顺序；细节见对应 commit 与组件文档。

本轮已完成并提交，按主题分三个批次：

| 项 | 内容 | 批次 |
| --- | --- | --- |
| #2 SSE 实时推送 | 后端 `RealtimeEventHub` + `GridStateSyncService`（`@Scheduled` 2s 指纹比对，首次拉取仅建基线不广播）+ `RealtimeController`（`GET /api/events`，`text/event-stream`，15s 心跳）；前端 `src/api/events.js`，`GridSelectPage` / `GridDetailPage` 订阅 `grids-updated` / `grid-snapshot-updated` 即时刷新，保留 5s/10s 轮询兜底 | A |
| #3 登出跳登录页 | `App.vue` 登出改 `logout()` 后 `router.push({name:'login'})`，不带 redirect；`useAuth` 保持不依赖 router | A |
| #4 接入方块 GUI 迭代（裁剪范围） | 不做 token 展示/复制（重置 token 仍暂缓）；`AccessMenu` 每 tick 刷新 containerData + `handleRename`；新增 `GridIdentityPayload`（S2C）/ `GridRenamePayload`（C2S）；`AccessBlockEntity.getIdentity/renameGrid`；`NetworkIdentityStore.rename`；`SnapshotManager.updateIdentity`；`AccessScreen` 身份展示 + 改名区；中英文 lang 各 +3 键 | B |

批次划分：**A** = #2 + #3（web 端代码 + 测试，一个 commit）；**B** = #4（模组代码，`mod_version` 0.0.7，一个 commit）；**C** = 文档/契约同步（本页 + 各组件文档 + `contracts/`，一个 commit）。

接续要点：

- 模组端游戏内行为（GUI 状态实时刷新、改名、网页列表即时更新）需 build 后手动装入游戏复现；CI 只构建模组。
- 后端 61 项测试、前端 `npm run build`、模组 `gradlew build` 均已通过，改动均已提交、未推送。
- 下一迭代建议从 §2「术语统一为 grid」继续（契约 v0.4 已含 SSE 端点，`mod_version` 升 0.0.8）。

## 相关文档

- [技术设计](design.md)（已收敛的权威设计）
- [架构总览](architecture.md)
- [数据契约](../contracts/index.md)
- [模组](mod/index.md) · [后端](backend/index.md) · [前端](frontend/index.md)
