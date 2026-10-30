# 模组（NeoForge）

> 文档首页：[`index.md`](../index.md) › 组件 › 模组

游戏内唯一能读写 AE2 状态的部分。本工程是 NeoForge 1.21.1，已接入 AE2 19.2.18，具备接入方块、控制器定位、Overworld 身份表、4 秒快照采集、本机 HTTP API 和合成命令队列。

## 概览

| 项 | 值 |
| --- | --- |
| 加载器 | NeoForge |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| AE2 | 19.2.18（Modrinth Maven，`maven.modrinth:ae2`） |
| Gradle 插件 | NeoGradle ModDev 2.0.148 |
| 映射 | Parchment `2024.11.17`（MC 1.21.1） |
| Java | 21 |
| 版本 | `0.0.4` |
| mod_id / 包名 | `ae2webmanagement` / `com.ae2webmanagement.ae2webmanagementmod` |

## 目录结构与源码映射

| 路径 | 说明 |
| --- | --- |
| [`../../mod/build.gradle`](../../mod/build.gradle) | 构建脚本：Modrinth 仓库、AE2 依赖声明、运行配置、jar 元数据 |
| [`../../mod/gradle.properties`](../../mod/gradle.properties) | 所有版本号与 mod 元数据（含 `ae2_version`） |
| [`../../mod/settings.gradle`](../../mod/settings.gradle) | Gradle 插件仓库与工具链解析 |
| [`../../mod/src/main/java/.../AE2WebManagement.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagement.java) | 模组主类：注册配置、接入方块，启动/停止快照、命令和本地 API |
| [`../../mod/src/main/java/.../Config.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/Config.java) | 运行时配置（HTTP 监听地址/端口、共享密钥、快照间隔） |
| [`../../mod/src/main/java/.../AE2WebManagementClient.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagementClient.java) | 仅客户端：配置界面注册（专用服不加载） |
| **`grid/`** — AE2 网格工具（可复用） | |
| [`../../mod/src/main/java/.../grid/ControllerAnchor.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/grid/ControllerAnchor.java) | 控制器位置锚点（值对象：`levelKey` + `pos`） |
| [`../../mod/src/main/java/.../grid/ControllerLocator.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/grid/ControllerLocator.java) | 从 `IGrid` 定位 ME 控制器（`getOwner() instanceof ControllerBlockEntity`） |
| **`access/`** — 接入方块（网格节点 + 控制器定位） | |
| [`../../mod/src/main/java/.../access/AccessBlock.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessBlock.java) | 接入方块的方块类，右击打开 GUI |
| [`../../mod/src/main/java/.../access/AccessBlockEntity.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessBlockEntity.java) | 接入方块的方块实体：托管网格节点、定位控制器、缓存锚点 |
| [`../../mod/src/main/java/.../access/AccessMenu.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessMenu.java) | 最小注册菜单：同步状态，只保留注册按钮 |
| [`../../mod/src/main/java/.../access/client/AccessScreen.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/client/AccessScreen.java) | 客户端界面：标题、状态、注册按钮，无物品栏 |
| [`../../mod/src/main/java/.../access/AccessRegistration.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessRegistration.java) | 方块/物品/方块实体注册、AE2 capability 注册、创造标签页 |
| **`network/`** — 网络身份 | |
| [`../../mod/src/main/java/.../network/NetworkIdentity.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/network/NetworkIdentity.java) | 网络接入身份（数据记录：`accessId` + 控制器锚点 + token） |
| [`../../mod/src/main/java/.../data/NetworkIdentityStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/NetworkIdentityStore.java) | Overworld 网络身份表（`ae2web_identities.dat`） |
| **`snapshot/`** — 快照 | |
| [`../../mod/src/main/java/.../snapshot/Snapshot.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/Snapshot.java) | 库存、合成 CPU、采样时间和在线态 |
| [`../../mod/src/main/java/.../snapshot/SnapshotManager.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/SnapshotManager.java) | 主线程 4 秒采样、内存缓存、离线保留 |
| [`../../mod/src/main/java/.../data/SnapshotStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/SnapshotStore.java) | Overworld 快照表（`ae2web_snapshots.dat`） |
| **`command/`** — 合成命令 | |
| [`../../mod/src/main/java/.../command/CraftCommand.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CraftCommand.java) | 合成意图记录：目标 `AEKey`、数量、状态、结果 |
| [`../../mod/src/main/java/.../command/CommandState.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CommandState.java) | 命令状态枚举（PENDING/SUBMITTED/FAILED） |
| [`../../mod/src/main/java/.../command/CommandManager.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CommandManager.java) | HTTP 入队与主线程调度：持久化、AE2 异步重算、提交 |
| [`../../mod/src/main/java/.../data/CommandStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/CommandStore.java) | Overworld 命令表（`ae2web_commands.dat`） |
| **`http/`** — 本地数据 API | |
| [`../../mod/src/main/java/.../http/LocalDataServer.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/http/LocalDataServer.java) | 仅回环绑定；`/health`、网络/快照查询、合成命令投递与查询 |
| **配置/元数据** | |
| [`../../mod/src/main/templates/META-INF/neoforge.mods.toml`](../../mod/src/main/templates/META-INF/neoforge.mods.toml) | 模组元数据模板，含 AE2 `required` 依赖 |
| [`../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json`](../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json) | 语言文件（含方块名与配置项翻译） |

## 配置（gradle.properties 关键项）

| 键 | 当前值 | 说明 |
| --- | --- | --- |
| `minecraft_version` | `1.21.1` | 目标 MC 版本 |
| `neo_version` | `21.1.265` | 必须与 MC 版本一致 |
| `ae2_version` | `19.2.18` | AE2 依赖版本（`build.gradle` 引用） |
| `mod_version` | `0.0.4` | 每次提交递增补丁号 |
| `mod_id` | `ae2webmanagement` | 必须与主类 `MODID` 一致 |
| `mod_name` | `AE2 Web Management` | 展示名 |
| `mod_group_id` | `com.ae2webmanagement.ae2webmanagementmod` | 应与源码基础包一致 |

> 运行时配置（`Config.java`）：`httpBindAddress`（默认 127.0.0.1）、`httpPort`（默认 8090）、`sharedSecret`（默认空 = 关闭校验）、`snapshotIntervalSeconds`（默认 4）。

## 构建

```powershell
# 首次需确保 JAVA_HOME 指向 JDK 21
$env:JAVA_HOME = "D:\Jvav\Java\jdk-21"
# 在仓库根目录
.\mod\gradlew.bat -p .\mod build
# 只验证编译可跑
.\mod\gradlew.bat -p .\mod compileJava
```

## 当前状态与后续

- [x] **AE2 接入与身份表**：接入方块作为网格节点定位控制器，身份表统一存 Overworld。
- [x] **快照循环**：每 4 秒读取 `getCachedInventory()` 与 `getCpus()`，离线时保留最后数据。
- [x] **方块与 GUI**：方块使用默认缺失纹理渲染；GUI 只包含标题、状态和注册按钮。
- [x] **本机 API**：仅绑定回环地址，支持健康检查、网络列表、快照查询、命令投递和命令查询。
- [x] **命令队列**：HTTP 只入队，主线程持久化并用 AE2 异步重算后提交。
- [ ] **后端代理与用户授权**：Spring Boot 尚未把本机 API 转发给网页用户。

> 实现顺序与完整方案见 [技术设计](../design.md)：锚点身份（控制器 + `SavedData`）→ 注册器方块 → 4 秒快照循环 → 命令队列 → 本地 127.0.0.1 数据口。

## 相关文档

- [技术设计](../design.md)（定稿方案）
- [架构总览](../architecture.md)（线程安全、缓存、存档 ID 等设计约束）
- [数据契约](../contracts/index.md)
- [后端](../backend/index.md)
- [快速开始](../getting-started.md)