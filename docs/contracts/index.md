# 数据契约

> 文档首页：[`index.md`](../index.md) › 组件 › 契约

定义模组与后端之间交换数据的**版本化 schema**。这是两个组件的唯一集成面。

## 状态

- **初版本机 API 已实现**：模组提供 `/health`、`/grids`、`/grids/{id}/snapshot`、`/grids/{id}/craft`、`/grids/{id}/commands`。
- **Web API v0.1 已实现**：后端对前端的 `/api/grids`、`/api/grids/{id}/snapshot`、`/api/grids/{id}/craft`、`/api/grids/{id}/commands` 均为对模组的透传转发，形状见 [web-api.md](web-api.md)；前端默认真实请求，`VITE_USE_MOCK=true` 切 mock。
- **网格密码已实现**（模组 0.0.6）：`GET /grids` 摘要新增 `hasPassword`；新增 `POST /grids/{id}/verify-password` 供后端校验解锁。密码在游戏内 GUI 设置，模组只存 PBKDF2 哈希，明文不出模组；旧版模组无这些字段时后端按「无密码=公开」处理，向后兼容。
- **displayName 可改名**（模组 0.0.7）：接入方块 GUI 支持修改显示名（C2S payload 写身份表 + 身份镜像缓存即时刷新）；HTTP `GET /grids` 的 `displayName` 字段随之变化，schema 不变。
- **实时推送（SSE）已实现**（后端 v0.4，模组零改动）：`GET /api/events` 推送变更提示，见 [web-api.md](web-api.md)「实时事件端点」；本页（模组↔后端契约）无新增端点。
- **术语统一为 grid**（模组 0.0.8，契约 v0.5）：模组端点 `/networks`→`/grids`、JSON key `networks`→`grids`、错误码 `network_not_found`→`grid_not_found`；后端 `/api/networks`→`/api/grids`、`Grid*` 类与错误码 `network_locked`→`grid_locked`。`SavedData` 文件名与 NBT、`accessId` 均不变。
- **物品中文名称**（模组 0.0.9，契约 v0.6）：快照中 `inventory[]` / `patterns[]` 各 stack 条目新增**可选** `nameKey`（物品翻译键，如 `block.minecraft.stone` / `item.ae2.controller`，roadmap.md §4）；前端按官方中文语言表查名。旧版模组无该字段 → 后端按 null 处理并在 Web 响应省略，向后兼容。`SavedData` NBT 中该字段同样可空（旧存档读出为 null）。
- **物品图标（模组 0.0.10 + 后端/前端，契约 v0.7；0.0.11 分辨率升级 64×64）**：模组 `GET /icons/{namespace}/{path}.png` 图标静态文件端点（64×64 PNG，客户端离屏渲染产物（16×16 GUI 单位逻辑画布 4× 超采样），只读磁盘不现算；路径段仅允许 `[a-z0-9_.-]`，缺失 404 + 惰性补齐）；后端 `GET /api/icons/{namespace}/{path}.png` 纯字节转发（透传 `Content-Type` 与 `Cache-Control`，仅要求登录，不自建缓存）；前端 `iconUrlOf()` 按 key id 构造 URL，`ItemSlot` 懒加载 + 失败回退占位方块并有限重试。详见 [web-api.md](web-api.md)「图标转发端点」。
- **CPU 名称字段移除（模组 0.0.12，Web 契约 v0.8）**：AE2 合成 CPU 无法命名，快照 `craftingCpus[]` 不再携带 `name`——模组采集 / `SavedData` NBT / HTTP JSON、后端 DTO 透传、前端展示全链路同步移除；网页按列表序号显示 `#N`，模组按游戏内同序（协处理器↓、存储↓）排序，编号与游戏内未命名 CPU 一致。
- **命令记录删除与历史自动清理（模组 0.0.13，Web 契约 v0.9）**：新增 `DELETE /grids/{accessId}/commands/{commandId}`——**物理删除**命令记录：删除 `PENDING` 记录即撤销（不会提交给 AE2，含取消在途计算），删除 `SUBMITTED`/`FAILED` 记录仅清理历史（不影响 AE2 已受理的任务）。删除在模组主线程原子执行（与模拟/提交串行化，杜绝「已删命令仍被提交」的竞态），响应即最终结果。另：每个网格自动保留最近 `commandHistoryLimit`（模组配置，默认 50）条终态记录，超出删除最旧；`PENDING` 不参与自动清理。详见下方「合成命令 API」。
- **`lastSeenAt` 随快照回写（模组 0.0.14，Web 契约 v0.10）**：快照循环对**在线**网格回写身份表 `lastSeenAt`（离线轮不更新），该字段由「建档时间」变为可信的「最近一次在线时间」，随 `SavedData` 持久化、跨重启正确；`GET /grids` 摘要新增 `lastSeenAt` 字段，旧版模组（<0.0.14）无该字段时后端省略（向后兼容）。
- 完整 schema、JSON Schema/OpenAPI 仍待定义；字段变更前先更新本页。
- 目录：`contracts/`（当前以文档形式维护初版 API 形状）。

## 通用约定

- 仅绑定本机回环地址；部署时不得把模组端口暴露到局域网或公网。
- 除共享密钥为空外，请求必须带 `X-Shared-Secret`。
- 响应为 JSON，`Content-Type` 为 `application/json; charset=utf-8`。
- 错误响应使用 `{"error":"machine_readable_code"}`。
- 时间戳为 epoch 毫秒。

## 查询 API

| 端点 | 用途 |
| --- | --- |
| `GET /health` | 健康检查，返回 `{"ok":true}` |
| `GET /grids` | 网格列表，含 `accessId`、显示名、控制器维度/坐标、在线态、快照摘要、`hasPassword` 与 `lastSeenAt`（0.0.14 起） |
| `GET /grids/{accessId}/snapshot` | 单网格快照，含 `inventory`、`craftingCpus` 和 `patterns` |
| `GET /icons/{namespace}/{path}.png` | 物品/流体图标静态文件（64×64 PNG，模组 0.0.11 起；16 GUI 单位逻辑画布 4× 超采样）。`namespace`/`path` 来自快照 key 的 `id`（如 `minecraft/iron_block.png`），仅允许 `[a-z0-9_.-]`；文件缺失返回 `404 {"error":"icon_not_found"}` 并惰性补齐（客户端后台渲染，下一次请求大概率命中）；非法路径段返回 `400 {"error":"invalid_icon_path"}`。响应 `Content-Type: image/png` + `Cache-Control: public, max-age=86400`（图标一经渲染即静态） |

`GET /grids` 摘要中的 `lastSeenAt`（0.0.14 起）为该网格**最近一次在线时间**（epoch 毫秒）：快照循环发现网格在线时回写身份表，离线轮保持不变；网格从未在线过时等于建档时间。旧版模组无该字段，响应省略。

快照中的 `inventory[].key` 是 AE2 `AEKey` 通用 NBT 转换后的 JSON；`amount` 为 long。
`inventory[]` / `patterns[]` 的每个 stack 条目（`primaryOutput`、`outputs[]`、`inputs[].possibleInputs[]`）均可能携带**可选** `nameKey`：物品翻译键（如 `block.minecraft.stone` / `item.ae2.controller`，取自 `Item#getDescriptionId()`；流体为 `fluid_type.*`），供前端查中文语言表（roadmap.md §4）。无翻译键时字段省略（旧版模组 / 自定义名称物品）。
`craftingCpus[]` 不含名称字段：AE2 合成 CPU 无法命名，按列表序号在网页上显示为 `#N`（模组 0.0.12 起，与游戏内一致；列表按协处理器数量降序、剩余存储降序排序）。

`patterns[]` 是样板摘要的声明框架，字段来自 AE2 `IPatternDetails`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `primaryOutput` | object | 主输出 `{key, amount}`，可直接作为 `POST /craft` 的 `key` |
| `outputs` | array | 全部样板输出，均为 `{key, amount}` |
| `inputs` | array | 样板输入槽列表 |
| `inputs[].possibleInputs` | array | 该输入槽可用的候选物品，均为 `{key, amount}` |
| `inputs[].multiplier` | number | 该输入槽的倍率 |

> `patterns` 由模组每 4 秒全量采集自 AE2 `ICraftingProvider`（同一编码样板在多个
> 样板供应器间按 `IPatternDetails` 去重，并按主输出排序）。输入槽的实际需求量 =
> `possibleInputs[i].amount × multiplier`。网格离线时保留最近一次在线的样板值；
> 从未在线（无任何快照）时为空数组。

## 合成命令 API

### `POST /grids/{accessId}/craft`

请求体字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | JSON object | `AEKey` 通用 NBT 转换后的 JSON，可复用快照 `inventory[].key` |
| `amount` | number | 要合成的数量，必须大于 0 |

成功返回 `202`：

| 字段 | 说明 |
| --- | --- |
| `commandId` | 本次命令的 UUID |
| `state` | 当前状态，受理时为 `PENDING` |
| `submittedAt` | 受理时间戳 |

### `GET /grids/{accessId}/commands`

返回 `commands` 数组，每项包含 `commandId`、`accessId`、`state`、`submittedAt`、`result`、`amount`、`target`。

命令状态当前为：

| 状态 | 说明 |
| --- | --- |
| `PENDING` | 已入队；等待主线程持久化、重算或网格在线 |
| `SUBMITTED` | 已成功提交给 AE2，`result` 含 AE2 `craftingId` |
| `FAILED` | 重算或提交失败，`result` 含原因 |

> `POST` 返回 `202` 表示已进入线程安全队列；主线程下一 tick 会持久化命令，
> 网格在线后异步重算并提交给 AE2。

### `DELETE /grids/{accessId}/commands/{commandId}`

删除一条命令记录（模组 0.0.13 起）。**物理删除**，语义按记录当前状态：

| 被删记录状态 | 效果 |
| --- | --- |
| `PENDING` | **撤销**：记录移除，永远不会提交给 AE2（若 AE2 计算正在进行，一并取消）；这是误发撤回的入口 |
| `SUBMITTED` / `FAILED` | 仅清理历史条目；AE2 已受理的合成任务**不受影响**（模组不跟踪 AE2 链接，无法也不试图撤回） |

删除在模组主线程原子执行（与命令的模拟/提交串行化），**响应即最终结果**：

| 响应 | 说明 |
| --- | --- |
| `204` | 记录已删除 |
| `400 {"error":"invalid_access_id"}` / `{"error":"invalid_command_id"}` | 路径参数不是合法 UUID |
| `404 {"error":"grid_not_found"}` | 网格不存在 |
| `404 {"error":"command_not_found"}` | 命令记录不存在（含已被删除；重复删除不幂等） |
| `500 {"error":"internal_error"}` | 主线程不可用或等待超时（服务器关闭中），可重试 |

> 自动清理：每个网格只保留最近 `commandHistoryLimit`（模组配置，默认 50）条
> 终态（`SUBMITTED`/`FAILED`）记录，超出时按 `submittedAt` 从旧到新自动删除，
> `GET /commands` 的历史因此可能变短；`PENDING` 记录永不自动清理。
> 删除按钮交互约定见 Web 契约 v0.9。

## 网格密码 API

### `POST /grids/{accessId}/verify-password`

校验网格访问密码（后端在账号「解锁网格」时转发调用）。请求体：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `password` | string | 用户输入的网格密码 |

响应：

- `204`：网格**未设密码**（公开网格，无需校验）**或**密码匹配；
- `401 {"error":"invalid_password"}`：密码不匹配；
- `404 {"error":"grid_not_found"}`：网格不存在。

> 密码只能在游戏内接入方块 GUI 设置/清除（C2S 自定义 payload，主线程写入
> 身份表）；模组身份表只存 PBKDF2 哈希（`pbkdf2:<迭代>:<盐B64>:<哈希B64>`），
> 明文不出模组，HTTP 层无设置/清除端点。

## 原则

- 保持技术中立：模组与后端各自独立实现契约，不共享实现类。
- 契约带版本号；变更需兼容或显式升级版本。
- 双方只通过契约约定的消息/字段通信，不依赖对方内部类。

## 相关文档

- [Web API 契约（后端 ↔ 前端）](web-api.md)
- [技术设计](../design.md)（契约的权威来源）
- [架构总览](../architecture.md)（数据流与通信方式）
- [模组](../mod/index.md)
- [后端](../backend/index.md)
