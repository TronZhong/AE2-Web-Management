# 数据契约

> 文档首页：[`index.md`](../index.md) › 组件 › 契约

定义模组与后端之间交换数据的**版本化 schema**。这是两个组件的唯一集成面。

## 状态

- **预留**：尚未定义任何 schema，也尚未选择格式与工具（JSON Schema / OpenAPI 等）。
- 目录：`contracts/`（当前仅有 [`README.md`](../../contracts/README.md) 指针）。

## 原则

- 保持技术中立：模组与后端各自独立实现契约，不共享实现类。
- 契约带版本号；变更需兼容或显式升级版本。
- 双方只通过契约约定的消息/字段通信，不依赖对方内部类。

## 计划涉及的契约主题（后续定义）

- 网络标识（`networkAccessId`）与在线网络列表。
- 库存快照（基于 AE2 `getCachedInventory()` 的序列化结果）。
- 合成 CPU 状态。
- 合成命令提交与结果回传。

## 相关文档

- [架构总览](../architecture.md)（数据流与通信方式）
- [模组](../mod/index.md)
- [后端](../backend/index.md)