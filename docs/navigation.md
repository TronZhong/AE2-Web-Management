# 定位指南与源码映射

> 文档首页：[`index.md`](index.md) › 入门

本页回答「我要找 X，代码在哪个文件」。按概念反查代码位置。

## 定位流程

1. 先确认你找的东西属于哪个组件（模组 / 后端 / 契约 / 前端）。
2. 进入该组件的 `index.md`（见下方链接），看「源码映射」表格逐文件定位。
3. 组件间关系看 [`architecture.md`](architecture.md)。

## 组件入口

| 组件 | 入口文档 |
| --- | --- |
| 模组（NeoForge） | [`mod/index.md`](mod/index.md) |
| 后端（Spring Boot） | [`backend/index.md`](backend/index.md) |
| 数据契约 | [`contracts/index.md`](contracts/index.md) |
| 前端 | [`frontend/index.md`](frontend/index.md) |
| 技术设计（跨组件） | [`design.md`](design.md) |
| 后续迭代路线（规划） | [`roadmap.md`](roadmap.md) |

## 概念 → 位置速查

| 概念 | 位置 |
| --- | --- |
| 后端启动类 | `web/backend/src/main/java/com/ae2webmanagement/backend/BackendApplication.java` |
| 后端配置项（app.*）定义 | `web/backend/src/main/java/com/ae2webmanagement/backend/config/AppProperties.java` |
| 后端 → 模组通信客户端 | `web/backend/src/main/java/com/ae2webmanagement/backend/client/ModApiClient.java` |
| 后端实时推送（SSE 事件枢纽） | `web/backend/src/main/java/com/ae2webmanagement/backend/service/RealtimeEventHub.java` |
| 后端实时推送（指纹轮询与广播） | `web/backend/src/main/java/com/ae2webmanagement/backend/service/GridStateSyncService.java` |
| 后端实时事件端点 | `web/backend/src/main/java/com/ae2webmanagement/backend/controller/RealtimeController.java` |
| 后端错误码翻译（mod_unreachable / mod_unavailable） | `web/backend/src/main/java/com/ae2webmanagement/backend/service/GridService.java`（直连转发路径的兜底在 `controller/GlobalExceptionHandler.java`） |
| 后端账号同步（游戏内账号入库） | `web/backend/src/main/java/com/ae2webmanagement/backend/service/AccountSyncService.java` |
| 后端 PBKDF2 密码本地校验 | `web/backend/src/main/java/com/ae2webmanagement/backend/service/PasswordHashes.java` |
| 后端物品图标转发端点 | `web/backend/src/main/java/com/ae2webmanagement/backend/controller/IconController.java` |
| 前端图标 URL 构造 | `web/frontend/src/api/index.js`（`iconUrlOf()`） |
| 后端 Web 端点（/api/**） | `web/backend/src/main/java/com/ae2webmanagement/backend/controller/` |
| 后端契约 DTO | `web/backend/src/main/java/com/ae2webmanagement/backend/dto/` |
| 后端 CORS、模组 RestClient（2s/5s 超时） | `web/backend/src/main/java/com/ae2webmanagement/backend/config/` |
| 后端运行配置（端口、数据源、app.*） | `web/backend/src/main/resources/application.yaml` |
| 模组主类 / 方块物品注册 | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagement.java` |
| 模组配置 | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/Config.java` |
| 网格身份表（SavedData） | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/GridIdentityStore.java` |
| 模组账号表（SavedData，游戏内注册） | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/AccountStore.java` |
| 模组账号镜像缓存（HTTP 线程读） | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/account/AccountManager.java` |
| 接入方块 GUI 权限状态机 | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessBlockEntity.java`（`resolveView` 与写操作校验） |
| 快照表（SavedData） | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/SnapshotStore.java` |
| 快照采集与缓存 | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/SnapshotManager.java` |
| 样板摘要 record | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/Snapshot.java` |
| 本地 HTTP API | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/http/LocalDataServer.java` |
| 图标导出网关（公共侧） | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/icon/IconExporter.java` |
| 图标缓存目录 | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/icon/IconPaths.java` |
| 客户端图标离屏渲染 | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/client/icon/IconService.java` |
| 合成命令队列与调度 | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CommandManager.java` |
| 合成命令持久化 | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/CommandStore.java` |
| 接入方块 GUI | `mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessMenu.java` |
| 模组 id / 版本 / 依赖版本 | `mod/gradle.properties` |
| 模组构建脚本 | `mod/build.gradle` |
| 模组元数据（mods.toml） | `mod/src/main/templates/META-INF/neoforge.mods.toml` |
| 后端依赖清单 | `web/backend/pom.xml` |

## 包名约定

- 后端基础包：`com.ae2webmanagement.backend`，子包分层：`config` / `client` / `controller` / `service` / `repository` / `domain` / `dto`。
- 模组基础包：`com.ae2webmanagement.ae2webmanagementmod`。
- 两个组件**不同名、不共享任何类**，仅通过 `contracts/` 中定义的 schema 在协议层对齐。

## 相关文档

- [技术设计](design.md)
- [架构总览](architecture.md)
- [快速开始](getting-started.md)
