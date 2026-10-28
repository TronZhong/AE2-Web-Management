# 模组（NeoForge）

> 文档首页：[`index.md`](../index.md) › 组件 › 模组

游戏内唯一能读写 AE2 状态的部分。本工程是 NeoForge 1.21.1 模板，尚未接入 AE2，仅登记了示例方块/物品以验证环境。

## 概览

| 项 | 值 |
| --- | --- |
| 加载器 | NeoForge |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| Gradle 插件 | NeoGradle ModDev 2.0.148 |
| 映射 | Parchment `2024.11.17`（MC 1.21.1） |
| Java | 21 |
| mod_id / 包名 | `ae2webmanagement` / `com.ae2webmanagement.ae2webmanagementmod` |

## 目录结构与源码映射

| 路径 | 说明 |
| --- | --- |
| [`../../mod/build.gradle`](../../mod/build.gradle) | 构建脚本：运行配置、jar 元数据、发布等 |
| [`../../mod/gradle.properties`](../../mod/gradle.properties) | 所有版本号与 mod 元数据（mod_id、版本、依赖版本） |
| [`../../mod/settings.gradle`](../../mod/settings.gradle) | Gradle 插件仓库与工具链解析 |
| [`../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagement.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagement.java) | 模组主类：注册方块/物品/创造标签页、事件总线 |
| [`../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/Config.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/Config.java) | 示例配置（`ModConfigSpec`） |
| [`../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagementClient.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagementClient.java) | 仅客户端类：配置界面注册（专用服不加载） |
| [`../../mod/src/main/templates/META-INF/neoforge.mods.toml`](../../mod/src/main/templates/META-INF/neoforge.mods.toml) | 模组元数据模板，构建时由 `generateModMetadata` 填充 |
| [`../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json`](../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json) | 语言文件 |

## 配置（gradle.properties 关键项）

| 键 | 当前值 | 说明 |
| --- | --- | --- |
| `minecraft_version` | `1.21.1` | 目标 MC 版本 |
| `neo_version` | `21.1.265` | 必须与 MC 版本一致 |
| `mod_id` | `ae2webmanagement` | 必须与主类 `MODID` 一致 |
| `mod_name` | `AE2 Web Management` | 展示名 |
| `mod_group_id` | `com.ae2webmanagement.ae2webmanagementmod` | 应与源码基础包一致 |

## 构建

```powershell
# 在仓库根目录
.\mod\gradlew.bat -p .\mod build
```

## 当前状态与后续

- [ ] **未接入 AE2**：尚未引入 AE2 API 依赖，未实现网格节点、库存快照、合成命令。
- [ ] **无本地数据 API**：计划的 127.0.0.1 端点（供后端 `RestClient` 调用）尚未实现。
- [ ] 示例方块/物品/标签页/配置为模板残留，接入真实功能前需清理。

> 实现顺序与完整方案见 [技术设计](../design.md)：锚点身份（控制器 + `SavedData`）→ 注册器方块 → 4 秒快照循环 → 命令队列 → 本地 127.0.0.1 数据口。

## 相关文档

- [技术设计](../design.md)（定稿方案）
- [架构总览](../architecture.md)（线程安全、缓存、存档 ID 等设计约束）
- [数据契约](../contracts/index.md)
- [后端](../backend/index.md)
- [快速开始](../getting-started.md)