# Web API 契约（后端 ↔ 前端）

> 文档首页：[`index.md`](index.md) › 契约

定义 Spring Boot 后端对网页前端暴露的 HTTP API 形状。与「模组 ↔ 后端」契约同构：后端转发模组数据时**透传字段、不重命名**，前端只依赖本页。指针副本在仓库根目录 [`contracts/web-api.md`](../../contracts/web-api.md)。

## 状态

- **v0（草案，2026-11）**：字段形状冻结到「网络列表 + 单网络快照」两个只读端点；前端按本契约内置 mock 数据（`web/frontend/src/api/mock/`）。
- **v0.1（2026-11）**：后端转发已实现——只读端点 + 合成命令端点均透传模组响应（字段不重命名）；前端设置 `VITE_USE_MOCK=false` 切换真实请求，响应形状与 mock 一致。
- **v0.2（2026-11）**：引入**账号制用户认证**（Sa-Token + H2 账户表）：新增认证端点三则；除登录端点外所有 `/api/**` 端点要求已登录，未登录返回 `401 {"error":"unauthorized"}`。模组端零改动，模组契约不变。
- **v0.3（2026-11）**：引入**网格密码**（第二层访问控制，密码在游戏内 GUI 设置）：网格摘要新增 `hasPassword` / `unlocked`；新增 `POST /grids/{id}/unlock`；有密码且未解锁的网格，其 snapshot / craft / commands 端点返回 `403 {"error":"grid_locked"}`。
- **v0.4（2026-11）**：新增**实时事件端点** `GET /api/events`（SSE 推送，与 v0.3 完全向后兼容）：只推变更提示，前端收到后 refetch；轮询保留作兜底。
- **v0.5（2026-11）**：**术语统一为 grid**（与模组 0.0.8、后端 `Grid*` 类同步）：URL `/api/networks`→`/api/grids`、JSON key `networks`→`grids`、错误码 `network_locked`→`grid_locked`、`network_not_found`→`grid_not_found`。仅术语改名，字段值与 `accessId` 不变。
- **v0.6（2026-11）**：**物品中文名称**（与模组 0.0.9、roadmap.md §4 同步）：快照 `inventory[]` / `patterns[]` 各 stack 条目新增**可选** `nameKey`（物品翻译键，前端查中文语言表）；旧版模组/旧快照无该字段，Web 响应省略（向后兼容）。

## 通用约定

- Base URL：`/api`（后端统一前缀）。
- 请求与响应均为 JSON，`Content-Type: application/json; charset=utf-8`。
- 时间戳为 epoch 毫秒。
- 错误响应使用 `{"error":"machine_readable_code"}` + 对应 HTTP 状态码。错误码沿用模组契约（`unauthorized`、`method_not_allowed`、`not_found`、`invalid_access_id`、`grid_not_found`、`snapshot_not_found`、`invalid_json`、`missing_key_or_amount`、`invalid_amount`、`invalid_key`、`internal_error`），后端另新增：
  - `mod_unreachable`（503）：后端无法连接模组本地 API（游戏未启动 / 数据口未开启 / 超时）；
  - `mod_unavailable`（503）：模组拒绝了后端的共享密钥（`X-Shared-Secret` 不匹配，需检查后端 `app.mod.shared-secret` 配置）；
  - `invalid_password`（401，v0.3）：网格密码校验失败；
  - `grid_locked`（403，v0.3）：网格设有密码且当前账号未解锁，snapshot / craft / commands 拒绝访问。
- `amount` 以 JSON number 透传（Java 侧为 long；超过 2^53 的值理论上丢精度，实际库存量达不到）。
- **用户认证（账号制，v0.2 起）**：密码是**后端账号密码**，与 Minecraft/AE2 无关；登录后可见全部已注册网格。
  - 会话凭证：登录成功后，后端通过响应头 `Set-Cookie` 下发 `satoken` Cookie（HttpOnly），并在响应体返回 token 值；后续请求**同源自动携带 Cookie**，非 Cookie 客户端可用请求头 `satoken: <tokenValue>` 等价传递。
  - 未登录 / 会话过期访问受保护端点 → `401 {"error":"unauthorized"}`；登录页凭此错误码跳转。
  - `unauthorized` 同时用于登录失败（用户名或密码错误）。

## 认证端点

### `POST /api/auth/login`

账号登录，为唯一免认证端点。请求体：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `username` | string | 用户名（非空） |
| `password` | string | 密码（非空，明文传输依赖部署层 TLS；后端只存 BCrypt 哈希） |

响应 `200`：

| 字段 | 说明 |
| --- | --- |
| `tokenName` | token 请求头/Cookie 名，固定 `satoken` |
| `tokenValue` | 本次会话 token（同时已写入 `satoken` Cookie） |
| `username` | 登录成功的用户名（回显） |

错误：缺失字段或非 JSON 体 → `400 {"error":"invalid_json"}`；用户名或密码错误 → `401 {"error":"unauthorized"}`。

### `POST /api/auth/logout`

注销当前会话：需已登录；成功使当前 token 失效并清除 Cookie。响应 `200`，空对象 `{}`。

### `GET /api/auth/me`

查询当前登录账号：需已登录。响应 `200 {"username":"..."}`；未登录 → `401 {"error":"unauthorized"}`。

> 前端在应用启动时调用本端点恢复会话；`401` 即跳转登录页。

## 只读端点

### `GET /api/grids`

返回所有已注册网格及其快照摘要。响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `grids` | array | 网格摘要列表 |
| `grids[].accessId` | string (UUID) | 网格接入 ID，后续端点的路径参数 |
| `grids[].displayName` | string | 显示名（游戏内注册时命名） |
| `grids[].dimension` | string | 控制器所在维度，如 `minecraft:overworld` |
| `grids[].controllerX/Y/Z` | number | ME 控制器坐标 |
| `grids[].online` | boolean | 最近一次快照的在线态 |
| `grids[].capturedAt` | number | 最近一次快照采样时间；从未采样为 `0` |
| `grids[].inventoryEntries` | number | 库存物品种类数（非数量） |
| `grids[].cpuCount` | number | 合成 CPU 数量 |
| `grids[].hasPassword` | boolean | 是否在游戏内设置了网格密码（v0.3；旧版模组恒 `false`） |
| `grids[].unlocked` | boolean | 当前账号是否可访问：未设密码恒 `true`；设了密码则为该账号是否已解锁（v0.3，账号维度持久记忆） |

### `GET /api/grids/{accessId}/snapshot`

返回单个网格的完整快照。网格不存在返回 `404 {"error":"grid_not_found"}`；从未采样返回 `404 {"error":"snapshot_not_found"}`。响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessId` | string (UUID) | 归属网格 |
| `capturedAt` | number | 采样时间，页面显示「数据截至 xx」 |
| `online` | boolean | 采样时控制器区块是否加载 |
| `inventory` | array | 库存列表，见下 |
| `craftingCpus` | array | 合成 CPU 列表，见下 |
| `patterns` | array | 样板摘要列表，见下 |

`inventory[]`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | object | AEKey 通用 NBT 的 JSON，物品形如 `{"id":"minecraft:stone"}`；含组件时附加字段，前端只读 `id` 做展示 |
| `amount` | number (long) | 聚合数量 |
| `nameKey` | string（可选，v0.6） | 物品翻译键（如 `block.minecraft.stone` / `item.ae2.controller`），前端查中文语言表；旧版模组/无翻译键的条目省略该字段 |

`craftingCpus[]`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | string | CPU 名称；空字符串表示未命名 |
| `busy` | boolean | 是否正在执行合成任务 |
| `availableStorage` | number (long) | 剩余可存储字节数 |
| `coProcessors` | number | 协处理器数量 |

`patterns[]`（口径：AE2 `IPatternDetails`，**不含**可发射物品，见 `docs/design.md` §7）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `primaryOutput` | object | 主输出 `{key, amount[, nameKey]}`，可直接作为 `POST /craft` 的 `key` |
| `outputs` | array | 全部样板输出，均为 `{key, amount[, nameKey]}` |
| `inputs` | array | 样板输入槽列表 |
| `inputs[].possibleInputs` | array | 该槽候选物品，均为 `{key, amount[, nameKey]}` |
| `inputs[].multiplier` | number | 槽位倍率；实际需求量 = `possibleInputs[i].amount × multiplier` |

> 各 stack 条目的 `nameKey`（可选，v0.6）同 `inventory[]`：物品翻译键，前端查中文语言表（roadmap.md §4）。

## 合成命令端点

### `POST /api/grids/{accessId}/craft`

投递合成命令，后端透传模组 `POST /grids/{accessId}/craft`。请求体：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | object | AEKey 通用 NBT 的 JSON，可复用快照 `inventory[].key` / `patterns[].primaryOutput.key` |
| `amount` | number | 要合成的数量，必须大于 0 |

请求体校验与模组一致：缺失 `key`/`amount` → `400 missing_key_or_amount`；`amount ≤ 0` → `400 invalid_amount`；`key` 不是非空 object → `400 invalid_key`。

成功返回 `202`：

| 字段 | 说明 |
| --- | --- |
| `commandId` | 本次命令的 UUID |
| `state` | 当前状态，受理时为 `PENDING` |
| `submittedAt` | 受理时间戳（epoch 毫秒） |

### `GET /api/grids/{accessId}/commands`

查询命令状态，后端透传模组 `GET /grids/{accessId}/commands`。返回 `{commands: [...]}`，每项字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `commandId` | string (UUID) | 命令 ID |
| `accessId` | string (UUID) | 归属网格 |
| `state` | string | `PENDING` / `SUBMITTED` / `FAILED` |
| `submittedAt` | number | 受理时间戳（epoch 毫秒） |
| `result` | string \| null | `SUBMITTED` 时为 AE2 `craftingId`，`FAILED` 时为原因，`PENDING` 时为 `null` |
| `amount` | number | 请求合成的数量 |
| `target` | object | 目标 AEKey 的 JSON（同 `inventory[].key` 形状） |

> 后端为纯转发，命令由模组队列调度：入队后主线程持久化，网格在线后异步重算并提交给 AE2。

认证：上述只读与合成端点自 v0.2 起均要求已登录（见「认证端点」与「通用约定」）。

## 网格访问端点

### `POST /api/grids/{accessId}/unlock`

解锁设有网格密码的网格（v0.3）。密码由网格所有者在游戏内接入方块 GUI 设置；
解锁状态按**账号**持久记忆（后端 H2），成功后该账号访问此网格不再需要密码。
请求体：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `password` | string | 网格密码（非空） |

响应 `204`（无响应体）。错误：缺失 `password` → `400 {"error":"invalid_json"}`；
密码不匹配 → `401 {"error":"invalid_password"}`；网格不存在 → `404 {"error":"grid_not_found"}`；
模组不可达 → `503 mod_unreachable` / `mod_unavailable`。
网格未设密码时调用同样返回 `204`（幂等，直接记录解锁）。

## 网格访问控制（v0.3）

- 网格列表对所有已登录账号可见（含未解锁的锁定网格，卡片显示锁定态）。
- `GET /snapshot`、`POST /craft`、`GET /commands` 对 `hasPassword && !unlocked` 的网格
  返回 `403 {"error":"grid_locked"}`；解锁后正常。
- 访问控制在后端执行（转发模组前检查），模组数据口继续只信任共享密钥。

## 实时事件端点（v0.4，SSE）

### `GET /api/events`

订阅实时事件流。响应为 `text/event-stream`（SSE），连接建立后服务端主动推送**变更提示**，
前端收到后再 refetch 对应数据；长超时 + 心跳保活，浏览器 EventSource 自带重连。

认证：需已登录（Sa-Token Cookie / 请求头 `satoken` 均可），未登录返回
`401 {"error":"unauthorized"}`。

事件形状（每行 `data:` 为一个 JSON）：

| 事件 | 含义 | 前端动作 |
| --- | --- | --- |
| `{"type":"grids-updated"}` | 网格列表级变化（新增/移除/改名/改密） | 刷新网格列表 |
| `{"type":"grid-snapshot-updated","accessId":"..."}` | 某网格快照被刷新（在线态/采样时间变化） | 刷新该网格快照与命令 |

> 说明：
> - SSE 为全局广播，事件不含账号相关的 `unlocked`（解锁状态由业务端点响应驱动）。
> - 事件只做「变更提示」，不做数据透传；前端现有 5s/10s 轮询保留作兜底
>   （SSE 断线 / 未登录时自动降级，不影响可用性）。

## 原则

- 前端只与本契约通信，不直连模组 `127.0.0.1:8090`。
- 字段与「模组 ↔ 后端」契约保持同名同义；模组侧字段变更先改本页与 [`index.md`](index.md)，再动实现。

## 相关文档

- [模组 ↔ 后端契约](index.md)
- [技术设计](../design.md)
- [前端](../frontend/index.md)
