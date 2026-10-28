# 技术设计（已收敛）

> 文档首页：[`index.md`](index.md) › 设计

本页把多轮讨论后**收敛定稿**的技术方案固定下来，作为后续写代码的权威依据。文中涉及的 AE2 机制，均已在 `D:\ae2\Applied-Energistics-2` 源码中核实（见文末「源码对应」），不会凭记忆。

## 1. 一句话方案

模组在游戏内提供四样东西：

1. 一个**一次性注册器方块**：给所在（含 ME 控制器）的网络发放一个存档内专属 ID，并以 ME 控制器为锚点记录；
2. 一个**4 秒快照循环**：网络在线时把库存/合成状态抄进只读缓存，离线时标记 offline；
3. 一个**本机 HTTP 数据口**（仅 127.0.0.1）：供后端读取快照、投递合成命令；
4. 一个**待办命令队列**：离线时收到的合成命令持久化，等网络在线后由主线程提交。

后端与前端只通过这个数据口工作，全程不接触 AE2。

## 2. 核心决策（已定，勿再翻案）

| # | 决策 | 理由 |
| --- | --- | --- |
| D1 | 网络身份 = **ME 控制器锚点** `(维度, 坐标)` | AE2 网络无持久 ID；控制器位置稳定、数据持久在自己的 NBT |
| D2 | 只允许接入**含 ME 控制器**的网络 | 会做合成的场景本就在控制器阶段；且锚点自然存在 |
| D3 | 注册器方块**可拆除**；身份存 `SavedData`，不存方块 NBT | 身份与方块解耦，拆除不丢 |
| D4 | 库存用 `getCachedInventory()` **聚合值**，不做节点级对账 | 卸载即消失的语义与游戏本身一致；做细投入产出比差 |
| D5 | **离线** = 读缓存快照 + 排队命令；**在线**时才真正提交 | AE2 离线时既无法查询、也无法提交合成 |
| D6 | 一切触世界/网络的逻辑走**主线程**（`MinecraftServer#execute`） | 从其他线程访问世界会死锁 |

## 3. 数据流（更新版）

```
浏览器前端
   │  HTTP (JSON, 用户令牌)
   ▼
Spring Boot 后端 ── 登录/授权 ── 数据库（账户、授权）
   │  RestClient + X-Shared-Secret（机器级，仅 127.0.0.1）
   ▼
模组本地数据口（JDK HttpServer, 127.0.0.1:8090）
   ├── 读：只读快照缓存
   └── 写：命令队列（线程安全）
               │ 主线程消费
               ▼
        AE2 网络（锚点 = ME 控制器）
```

## 4. 数据模型（全部存 `SavedData`，挂 Overworld）

### 4.1 网络接入记录（一个 `networkAccessId` 一条）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessId` | UUID | 存档内专属标识（由模组生成） |
| `controllerKey` | `ResourceKey<Level>` + `BlockPos` | 锚点：ME 控制器的维度 + 坐标 |
| `token` | String | 网页访问令牌（生成时随机，后端持有） |
| `displayName` | String | 显示名 |
| `createdAt` / `lastSeenAt` | long（时间戳） | 建档时间 / 最近一次在线时间 |

### 4.2 库存/合成快照（缓存）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessId` | UUID | 归属哪个网络 |
| `capturedAt` | long | 采样时间（网页据此显示"数据截至 xx"） |
| `online` | boolean | 采样时控制器区块是否加载、`getGrid()` 是否非 null |
| `inventory` | `AEKey → long` 列表 | 物品种类 → 数量（聚合值，无节点来源） |
| `craftingCpus` | 列表 | 合成 CPU 数量与忙闲状态 |

### 4.3 待办命令（队列，持久化）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUID | 命令 ID（用于回传结果） |
| `accessId` | UUID | 目标网络 |
| `type` | 枚举 | 现阶段仅 `CRAFT` |
| `payload` | `AEKey` + `long` | 要合成的物品种类 + 数量（存**意图**，非预计算计划） |
| `state` | 枚举 | `PENDING` / `SUBMITTED` / `FAILED` |
| `submittedAt` / `result` | long / 文本 | 提交时间与结果说明 |

> 命令存的是"要合成什么"，不是预计算好的计划。因为离线时算不了；即便算了，上线时库存已变，必须**重新模拟**（见 5.4）。

## 5. 运行时流程

### 5.1 注册流程（建立身份）

1. 玩家把注册器方块放到某个 AE2 网络旁。
2. 注册器**临时**接入相邻网络（或扫描邻居获取网格引用），定位到 ME 控制器，取其坐标。
3. 生成 `accessId` + `token`，写一条「网络接入记录」进 `SavedData`。
4. 注册完成；注册器方块随后可被拆除，身份不受影响。

> 待定细节：注册器如何从邻居拿到网格并定位控制器——做成"临时网格节点"最稳（复用 AE2 连通关系）；具体用哪个公开 API 定位控制器节点，实现时以源码为准确认。

### 5.2 快照循环（每 4 秒）

1. 主线程上，对每个 `accessId`：检查控制器所在区块是否加载、控制器方块的 `getGrid()` 是否非 null。
2. 在线：读 `getCachedInventory()` + `ICraftingService.getCpus()` → 组快照，`online=true`。
3. 离线：保留上一份快照，置 `online=false`。
4. 快照写入线程安全缓存（供 HTTP 口读）+ 落盘到 `SavedData`（供重启后离线展示）。

### 5.3 在线/离线判定

| 控制器区块加载且 `getGrid()!=null` | 网页显示 |
| --- | --- |
| 是 | 快照 + "截至 hh:mm:ss" |
| 否 | 最后快照 + "网络离线，数据截至 hh:mm:ss" |

> 就这两态 + 一个时间戳，不做任何节点级对账。

### 5.4 命令提交流程

1. 网页 → 后端 → 模组 HTTP 口，命令写入线程安全队列（`state=PENDING`），立即返回"已受理"，并持久化到 `SavedData`。
2. 主线程发现目标网络的控制器在线后，逐个取出 `PENDING` 命令：
   - 重新 `beginCraftingCalculation(...)`（用**当时**库存重新模拟）；
   - 模拟成功后 `submitJob(...)`；
   - 回写 `SUBMITTED` 或 `FAILED` 与结果。
3. 网页通过快照/结果查询接口读取命令结果。

## 6. 模组 ↔ 后端契约（端点形状，具体 schema 在 `contracts/` 定义）

| 端点（示意） | 方向 | 用途 |
| --- | --- | --- |
| `GET /networks` | 后端→模组 | 列出在线/离线网络（含 `accessId`、名称、在线态） |
| `GET /networks/{id}/snapshot` | 后端→模组 | 读库存 + 合成状态快照 |
| `POST /networks/{id}/craft` | 后端→模组 | 投递合成命令（返回受理 ID） |
| `GET /networks/{id}/commands` | 后端→模组 | 查命令执行结果 |

认证：模组 HTTP 口只绑定 127.0.0.1，后端请求带 `X-Shared-Secret` 头（与 `application.yaml` 的 `app.mod.shared-secret` 一致）。

## 7. 明确不做（简化边界）

- **不做节点级库存归属**（不拆"每个容器有多少"）。
- **不自造网络身份 ID**（AE2 没有，就用控制器锚点）。
- **不做跨服务器聚合**（单存档 = 单后端实例）。
- **模组不内置完整 Web 服务器 / 认证**（只有本地数据口；登录授权全在后端）。

## 8. 线程与安全约束

1. 查询库存、提交合成、读控制器状态：**必须** `MinecraftServer#execute` 到主线程。
2. HTTP/定时线程：只读写"只读快照缓存"和"命令队列"，绝不碰世界对象。
3. 机器级认证（后端↔模组）用共享密钥；用户级认证在后端；两者独立。

## 9. 源码对应（AE2 / MC 关键 API）

| 设计概念 | 对应 API | 源码位置 |
| --- | --- | --- |
| 网络对象（**无持久 ID**，仅调试 `serialNumber`） | `IGrid` / `Grid` | `appeng/me/Grid.java` |
| 网格节点生命周期（创建/销毁/卸载） | `IManagedGridNode` | `appeng/api/networking/IManagedGridNode.java` |
| 判断在线 | `IManagedGridNode.getGrid()` 是否 null | 同上 |
| 库存缓存（每 tick 至多更新一次，聚合值） | `IStorageService.getCachedInventory()` | `appeng/me/service/StorageService.java` |
| 合成 CPU 列表 | `ICraftingService.getCpus()` | `appeng/me/service/CraftingService.java` |
| 合成模拟 | `ICraftingService.beginCraftingCalculation()` | `appeng/api/networking/crafting/ICraftingService.java` |
| 合成提交 | `ICraftingService.submitJob()` | 同上 |
| 存档内持久数据 | Minecraft `SavedData`（挂 Overworld） | `net.minecraft.world.level.saveddata.SavedData` |

## 相关文档

- [架构总览](architecture.md)
- [模组](mod/index.md) · [后端](backend/index.md) · [数据契约](contracts/index.md)
- [定位指南与源码映射](navigation.md)