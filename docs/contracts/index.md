# 数据契约

> 文档首页：[`index.md`](../index.md) › 组件 › 契约

定义模组与后端之间交换数据的**版本化 schema**。这是两个组件的唯一集成面。

## 状态

- **初版本机 API 已实现**：模组提供 `/health`、`/networks`、`/networks/{id}/snapshot`、`/networks/{id}/craft`、`/networks/{id}/commands`。
- 完整 schema、JSON Schema/OpenAPI 仍待定义；字段变更前先更新本页。
- 目录：`contracts/`（当前仅有 [`README.md`](../../contracts/README.md) 指针）。

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
| `GET /networks` | 网络列表，含 `accessId`、显示名、控制器维度/坐标、在线态和快照摘要 |
| `GET /networks/{accessId}/snapshot` | 单网络快照，含 `inventory` 和 `craftingCpus` |

快照中的 `inventory[].key` 是 AE2 `AEKey` 通用 NBT 转换后的 JSON；`amount` 为 long。

## 合成命令 API

### `POST /networks/{accessId}/craft`

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

### `GET /networks/{accessId}/commands`

返回 `commands` 数组，每项包含 `commandId`、`accessId`、`state`、`submittedAt`、`result`、`amount`、`target`。

命令状态当前为：

| 状态 | 说明 |
| --- | --- |
| `PENDING` | 已入队；等待主线程持久化、重算或网络在线 |
| `SUBMITTED` | 已成功提交给 AE2，`result` 含 AE2 `craftingId` |
| `FAILED` | 重算或提交失败，`result` 含原因 |

> `POST` 返回 `202` 表示已进入线程安全队列；主线程下一 tick 会持久化命令，
> 网络在线后异步重算并提交给 AE2。

## 原则

- 保持技术中立：模组与后端各自独立实现契约，不共享实现类。
- 契约带版本号；变更需兼容或显式升级版本。
- 双方只通过契约约定的消息/字段通信，不依赖对方内部类。

## 相关文档

- [技术设计](../design.md)（契约的权威来源）
- [架构总览](../architecture.md)（数据流与通信方式）
- [模组](../mod/index.md)
- [后端](../backend/index.md)