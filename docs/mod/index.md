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
| 版本 | `0.0.6` |
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
| [`../../mod/src/main/java/.../access/AccessBlockEntity.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessBlockEntity.java) | 接入方块的方块实体：托管网格节点、定位控制器、缓存锚点；`setGridPassword`/`isPasswordSet` 网格密码读写 |
| [`../../mod/src/main/java/.../access/AccessMenu.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessMenu.java) | 注册菜单：`containerData[0]` 注册状态、`containerData[1]` 密码标记；`handleSetPassword` 接收 payload |
| [`../../mod/src/main/java/.../access/GridPasswordPayload.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/GridPasswordPayload.java) | C2S 自定义 payload：GUI 提交网格密码（空串 = 清除），服务端主线程处理并校验 containerId |
| [`../../mod/src/main/java/.../access/client/AccessScreen.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/client/AccessScreen.java) | 客户端界面：标题、状态、注册按钮 + 密码区（EditBox 掩码、设置/清除按钮），无物品栏 |
| [`../../mod/src/main/java/.../access/AccessRegistration.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessRegistration.java) | 方块/物品/方块实体注册、AE2 capability 注册、C2S payload 注册、创造标签页 |
| **`network/`** — 网络身份 | |
| [`../../mod/src/main/java/.../network/NetworkIdentity.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/network/NetworkIdentity.java) | 网络接入身份（数据记录：`accessId` + 控制器锚点 + token + `passwordHash`） |
| [`../../mod/src/main/java/.../data/NetworkIdentityStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/NetworkIdentityStore.java) | Overworld 网络身份表（`ae2web_identities.dat`），`setPassword` 写密码哈希 |
| **`snapshot/`** — 快照 | |
| [`../../mod/src/main/java/.../snapshot/Snapshot.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/Snapshot.java) | 库存、合成 CPU、样板摘要、采样时间和在线态 |
| [`../../mod/src/main/java/.../snapshot/SnapshotManager.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/SnapshotManager.java) | 主线程 4 秒采样、内存缓存、离线保留；密码哈希镜像（`getPasswordHash`/`updatePasswordHash`，HTTP 线程可读） |
| [`../../mod/src/main/java/.../data/SnapshotStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/SnapshotStore.java) | Overworld 快照表（`ae2web_snapshots.dat`），含库存、CPU 和样板摘要 |
| **`command/`** — 合成命令 | |
| [`../../mod/src/main/java/.../command/CraftCommand.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CraftCommand.java) | 合成意图记录：目标 `AEKey`、数量、状态、结果 |
| [`../../mod/src/main/java/.../command/CommandState.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CommandState.java) | 命令状态枚举（PENDING/SUBMITTED/FAILED） |
| [`../../mod/src/main/java/.../command/CommandManager.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CommandManager.java) | HTTP 入队与主线程调度：持久化、AE2 异步重算、提交 |
| [`../../mod/src/main/java/.../data/CommandStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/CommandStore.java) | Overworld 命令表（`ae2web_commands.dat`） |
| [`../../mod/src/main/java/.../data/GridPasswords.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/GridPasswords.java) | 网格密码 PBKDF2 哈希工具（纯 JDK；`pbkdf2:<迭代>:<盐B64>:<哈希B64>`，上限 64 字符） |
| **`http/`** — 本地数据 API | |
| [`../../mod/src/main/java/.../http/LocalDataServer.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/http/LocalDataServer.java) | 仅回环绑定；`/health`、网络/快照查询（含 `hasPassword`）、合成命令投递与查询、`verify-password` 密码校验 |
| **配置/元数据** | |
| [`../../mod/src/main/templates/META-INF/neoforge.mods.toml`](../../mod/src/main/templates/META-INF/neoforge.mods.toml) | 模组元数据模板，含 AE2 `required` 依赖 |
| [`../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json`](../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json) | 语言文件（含方块名、GUI 文案与配置项翻译） |
| [`../../mod/src/main/resources/assets/ae2webmanagement/lang/zh_cn.json`](../../mod/src/main/resources/assets/ae2webmanagement/lang/zh_cn.json) | 简体中文语言文件 |

## 配置（gradle.properties 关键项）

| 键 | 当前值 | 说明 |
| --- | --- | --- |
| `minecraft_version` | `1.21.1` | 目标 MC 版本 |
| `neo_version` | `21.1.265` | 必须与 MC 版本一致 |
| `ae2_version` | `19.2.18` | AE2 依赖版本（`build.gradle` 引用） |
| `mod_version` | `0.0.6` | 每次提交递增补丁号 |
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
- [x] **样板摘要采集**：`SnapshotManager.capturePatterns` 遍历 `ICraftingProvider` 采集、按 `IPatternDetails` 去重，随快照缓存/持久化/HTTP 透传。
- [x] **方块与 GUI**：方块使用默认缺失纹理渲染；GUI 含标题、状态、注册按钮与网格密码区（设置/清除，掩码输入）。
- [x] **本机 API**：仅绑定回环地址，支持健康检查、网络列表（含 `hasPassword`）、快照查询、命令投递、命令查询与 `verify-password` 密码校验。
- [x] **命令队列**：HTTP 只入队，主线程持久化并用 AE2 异步重算后提交。
- [x] **后端代理**：Spring Boot 已把本机 API 的网络列表 / 快照 / 合成命令转发给网页（`web/backend/`，见 [后端](../backend/index.md)）。
- [x] **网格密码（0.0.6）**：游戏内 GUI 设密（PBKDF2 哈希入身份表）、`verify-password` 校验端点、C2S payload 通道；旧版后端/前端可无缝忽略。

- [ ] **GUI 迭代（`roadmap.md` §3）**：展示/修改 `NetworkIdentity.displayName`（现注册时写死 `"Network @ x,y,z"`）；`token` 字段仍闲置。
- [ ] **`nameKey` 字段（`roadmap.md` §3）**：快照 stack 条目附带物品翻译键（服务端无多语言，不能直接出中文名，由前端按翻译键查中文语言表）。
- [ ] **物品图标导出（`roadmap.md` §4）**：客户端离屏渲染已见物品为 16×16 PNG 并经本地 API 提供给后端。
- [ ] **命令取消 API 与 `lastSeenAt` 回写（`roadmap.md` §5）**：PENDING 命令目前无法撤销；`lastSeenAt` 尚未随快照回写。

> 实现顺序与完整方案见 [技术设计](../design.md)：锚点身份（控制器 + `SavedData`）→ 注册器方块 → 4 秒快照循环 → 命令队列 → 本地 127.0.0.1 数据口。

## 相关文档

- [技术设计](../design.md)（定稿方案）
- [后续迭代路线](../roadmap.md)（GUI 迭代、`nameKey`、图标导出等待实施项）
- [架构总览](../architecture.md)（线程安全、缓存、存档 ID 等设计约束）
- [数据契约](../contracts/index.md)
- [后端](../backend/index.md)
- [快速开始](../getting-started.md)