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

## 计划涉及的契约主题（对照 [技术设计](../design.md) 第 6 节）

- 网络列表（含 `accessId`、名称、在线态）。
- 库存 + 合成 CPU 状态快照（含采样时间戳与 `online` 标记）。
- 合成命令投递（存意图：物品种类 + 数量）与结果回传。
- 机器级认证头 `X-Shared-Secret`（与后端 `app.mod.shared-secret` 对应）。

## 相关文档

- [技术设计](../design.md)（契约的权威来源）
- [架构总览](../architecture.md)（数据流与通信方式）
- [模组](../mod/index.md)
- [后端](../backend/index.md)