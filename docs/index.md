# 文档首页

这里是本项目**全部权威文档**的入口。后续任何会话，请先从这里开始，再按下方目录跳到对应组件。

## 如何使用本文档

1. 先读 [`getting-started.md`](getting-started.md) 了解环境与构建。
2. 读 [`architecture.md`](architecture.md) 弄清各组件职责与数据流。
3. 要定位某段代码 / 某个配置，直接看 [`navigation.md`](navigation.md) 的源码映射，或进入对应组件的 `index.md` 查源码映射表。
4. 每个组件文档末尾都有「相关文档」交叉链接。

> 约定：文档语言为中文；每个组件目录的 `index.md` 是该组件的入口；源码映射表中的路径为相对仓库根目录、可点击的链接。

## 目录

### 入门

- [快速开始](getting-started.md) — 环境准备、构建、运行。
- [架构总览](architecture.md) — 组件职责、数据流、关键设计决策、当前实现状态。
- [定位指南与源码映射](navigation.md) — 如何从概念反查代码位置。

### 设计

- [技术设计（已收敛）](design.md) — 控制器锚点身份、快照缓存、命令队列、在线判定、契约形状。

### 组件

- [模组（NeoForge）](mod/index.md) — 游戏内数据出口：接入方块、身份表、快照、命令队列和本机 API。
- [后端（Spring Boot）](backend/index.md) — Web API、认证、授权、持久化，以及到模组的数据转发。
- [前端](frontend/index.md) — 浏览器 UI（预留）。
- [数据契约](contracts/index.md) — 模组与后端之间的初版本机 API 约定。

## 快速定位速查

| 要找什么 | 去这里 |
| --- | --- |
| 项目整体结构 | [README.md](../README.md)（根目录） |
| 网络身份/快照/命令队列怎么设计 | [design.md](design.md) |
| 某个后端包/类的职责 | [backend/index.md](backend/index.md) 的源码映射 |
| 某个模组文件/配置的职责 | [mod/index.md](mod/index.md) 的源码映射 |
| 后端配置项含义 | [backend/index.md](backend/index.md) › 配置 |
| 模组与后端如何通信 | [architecture.md](architecture.md) › 数据流 |
| 构建/运行命令 | [getting-started.md](getting-started.md) |