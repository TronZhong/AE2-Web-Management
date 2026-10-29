# 模组（NeoForge）

> 文档首页：[`index.md`](../index.md) › 组件 › 模组

游戏内唯一能读写 AE2 状态的部分。本工程是 NeoForge 1.21.1，已接入 AE2 19.2.18 依赖，示例代码已清理，业务代码包结构就绪。

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
| mod_id / 包名 | `ae2webmanagement` / `com.ae2webmanagement.ae2webmanagementmod` |

## 目录结构与源码映射

| 路径 | 说明 |
| --- | --- |
| [`../../mod/build.gradle`](../../mod/build.gradle) | 构建脚本：Modrinth 仓库、AE2 依赖声明、运行配置、jar 元数据 |
| [`../../mod/gradle.properties`](../../mod/gradle.properties) | 所有版本号与 mod 元数据（含 `ae2_version`） |
| [`../../mod/settings.gradle`](../../mod/settings.gradle) | Gradle 插件仓库与工具链解析 |
| [`../../mod/src/main/java/.../AE2WebManagement.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagement.java) | 模组主类：注册配置、调用 AccessRegistration、服务端启停钩子 |
| [`../../mod/src/main/java/.../Config.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/Config.java) | 运行时配置（HTTP 监听地址/端口、共享密钥、快照间隔） |
| [`../../mod/src/main/java/.../AE2WebManagementClient.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagementClient.java) | 仅客户端：配置界面注册（专用服不加载） |
| **`grid/`** — AE2 网格工具（可复用） | |
| [`../../mod/src/main/java/.../grid/ControllerAnchor.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/grid/ControllerAnchor.java) | 控制器位置锚点（值对象：`levelKey` + `pos`） |
| [`../../mod/src/main/java/.../grid/ControllerLocator.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/grid/ControllerLocator.java) | 从 `IGrid` 定位 ME 控制器（`getOwner() instanceof ControllerBlockEntity`） |
| **`access/`** — 接入方块（网格节点 + 控制器定位） | |
| [`../../mod/src/main/java/.../access/AccessBlock.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessBlock.java) | 接入方块的方块类 |
| [`../../mod/src/main/java/.../access/AccessBlockEntity.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessBlockEntity.java) | 接入方块的方块实体：托管网格节点、定位控制器、缓存锚点 |
| [`../../mod/src/main/java/.../access/AccessRegistration.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessRegistration.java) | 方块/物品/方块实体注册、AE2 capability 注册、创造标签页 |
| **`network/`** — 网络身份（占位） | |
| [`../../mod/src/main/java/.../network/NetworkIdentity.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/network/NetworkIdentity.java) | 网络接入身份（数据记录：`accessId` + 控制器锚点 + token） |
| **`snapshot/`** — 快照（占位） | |
| [`../../mod/src/main/java/.../snapshot/Snapshot.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/Snapshot.java) | 库存/合成快照（数据记录占位） |
| **`command/`** — 命令（占位） | |
| [`../../mod/src/main/java/.../command/CraftCommand.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CraftCommand.java) | 待办合成命令（数据记录） |
| [`../../mod/src/main/java/.../command/CommandState.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CommandState.java) | 命令状态枚举（PENDING/SUBMITTED/FAILED） |
| **配置/元数据** | |
| [`../../mod/src/main/templates/META-INF/neoforge.mods.toml`](../../mod/src/main/templates/META-INF/neoforge.mods.toml) | 模组元数据模板，含 AE2 `required` 依赖 |
| [`../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json`](../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json) | 语言文件（含方块名与配置项翻译） |

## 配置（gradle.properties 关键项）

| 键 | 当前值 | 说明 |
| --- | --- | --- |
| `minecraft_version` | `1.21.1` | 目标 MC 版本 |
| `neo_version` | `21.1.265` | 必须与 MC 版本一致 |
| `ae2_version` | `19.2.18` | AE2 依赖版本（`build.gradle` 引用） |
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

- [x] **AE2 依赖已接入**：`maven.modrinth:ae2:19.2.18` 已解析，`compileJava` 通过。
- [x] **示例代码已清理**：移除示例方块/物品/标签页，入口类仅保留配置、注册器与服务端启停钩子。
- [x] **数据记录骨架已建**：`network`/`snapshot`/`command` 包与对应 record 就绪。
- [ ] **未接入 AE2 API / 业务逻辑**：待实现网格节点、快照循环、命令队列、SavedData 身份表。
- [ ] **无本地数据 API**：计划的 127.0.0.1 端点（供后端 `RestClient` 调用）尚未实现。

> 实现顺序与完整方案见 [技术设计](../design.md)：锚点身份（控制器 + `SavedData`）→ 注册器方块 → 4 秒快照循环 → 命令队列 → 本地 127.0.0.1 数据口。

## 相关文档

- [技术设计](../design.md)（定稿方案）
- [架构总览](../architecture.md)（线程安全、缓存、存档 ID 等设计约束）
- [数据契约](../contracts/index.md)
- [后端](../backend/index.md)
- [快速开始](../getting-started.md)