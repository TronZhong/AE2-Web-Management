# Web API 契约（后端 ↔ 前端）

> 文档首页：[`index.md`](index.md) › 契约

定义 Spring Boot 后端对网页前端暴露的 HTTP API 形状。与「模组 ↔ 后端」契约同构：后端转发模组数据时**透传字段、不重命名**，前端只依赖本页。指针副本在仓库根目录 [`contracts/web-api.md`](../../contracts/web-api.md)。

## 状态

- **v0（草案，2026-11）**：字段形状冻结到「网络列表 + 单网络快照」两个只读端点；前端按本契约内置 mock 数据（`web/frontend/src/api/mock/`）。
- **v0.1（2026-11）**：后端转发已实现——只读端点 + 合成命令端点均透传模组响应（字段不重命名）；前端设置 `VITE_USE_MOCK=false` 切换真实请求，响应形状与 mock 一致。
- 认证为本期**未实现**项（语义未定，见文末），所有端点暂不要求凭据。

## 通用约定

- Base URL：`/api`（后端统一前缀）。
- 请求与响应均为 JSON，`Content-Type: application/json; charset=utf-8`。
- 时间戳为 epoch 毫秒。
- 错误响应使用 `{"error":"machine_readable_code"}` + 对应 HTTP 状态码。错误码沿用模组契约（`unauthorized`、`method_not_allowed`、`not_found`、`invalid_access_id`、`network_not_found`、`snapshot_not_found`、`invalid_json`、`missing_key_or_amount`、`invalid_amount`、`invalid_key`、`internal_error`），后端另新增两个：
  - `mod_unreachable`（503）：后端无法连接模组本地 API（游戏未启动 / 数据口未开启 / 超时）；
  - `mod_unavailable`（503）：模组拒绝了后端的共享密钥（`X-Shared-Secret` 不匹配，需检查后端 `app.mod.shared-secret` 配置）。
- `amount` 以 JSON number 透传（Java 侧为 long；超过 2^53 的值理论上丢精度，实际库存量达不到）。
- 用户级认证**语义未定**（全局账号 / 每网络令牌二选一，见 `docs/design.md` 4.1 的 `token` 字段），本版本所有端点暂不要求凭据；请求/响应均预留认证扩展位，不改动既有字段。

## 只读端点

### `GET /api/networks`

返回所有已注册网络及其快照摘要。响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `networks` | array | 网络摘要列表 |
| `networks[].accessId` | string (UUID) | 网络接入 ID，后续端点的路径参数 |
| `networks[].displayName` | string | 显示名（游戏内注册时命名） |
| `networks[].dimension` | string | 控制器所在维度，如 `minecraft:overworld` |
| `networks[].controllerX/Y/Z` | number | ME 控制器坐标 |
| `networks[].online` | boolean | 最近一次快照的在线态 |
| `networks[].capturedAt` | number | 最近一次快照采样时间；从未采样为 `0` |
| `networks[].inventoryEntries` | number | 库存物品种类数（非数量） |
| `networks[].cpuCount` | number | 合成 CPU 数量 |

### `GET /api/networks/{accessId}/snapshot`

返回单个网络的完整快照。网络不存在返回 `404 {"error":"network_not_found"}`；从未采样返回 `404 {"error":"snapshot_not_found"}`。响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessId` | string (UUID) | 归属网络 |
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
| `primaryOutput` | object | 主输出 `{key, amount}`，可直接作为 `POST /craft` 的 `key` |
| `outputs` | array | 全部样板输出，均为 `{key, amount}` |
| `inputs` | array | 样板输入槽列表 |
| `inputs[].possibleInputs` | array | 该槽候选物品，均为 `{key, amount}` |
| `inputs[].multiplier` | number | 槽位倍率；实际需求量 = `possibleInputs[i].amount × multiplier` |

## 合成命令端点

### `POST /api/networks/{accessId}/craft`

投递合成命令，后端透传模组 `POST /networks/{accessId}/craft`。请求体：

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

### `GET /api/networks/{accessId}/commands`

查询命令状态，后端透传模组 `GET /networks/{accessId}/commands`。返回 `{commands: [...]}`，每项字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `commandId` | string (UUID) | 命令 ID |
| `accessId` | string (UUID) | 归属网络 |
| `state` | string | `PENDING` / `SUBMITTED` / `FAILED` |
| `submittedAt` | number | 受理时间戳（epoch 毫秒） |
| `result` | string \| null | `SUBMITTED` 时为 AE2 `craftingId`，`FAILED` 时为原因，`PENDING` 时为 `null` |
| `amount` | number | 请求合成的数量 |
| `target` | object | 目标 AEKey 的 JSON（同 `inventory[].key` 形状） |

> 后端为纯转发，命令由模组队列调度：入队后主线程持久化，网络在线后异步重算并提交给 AE2。

认证扩展位：将来引入用户级认证时，预计以请求头携带凭据（具体头名与语义待定），不改变上述字段。

## 原则

- 前端只与本契约通信，不直连模组 `127.0.0.1:8090`。
- 字段与「模组 ↔ 后端」契约保持同名同义；模组侧字段变更先改本页与 [`index.md`](index.md)，再动实现。

## 相关文档

- [模组 ↔ 后端契约](index.md)
- [技术设计](../design.md)
- [前端](../frontend/index.md)
