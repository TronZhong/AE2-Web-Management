# 快速开始

> 文档首页：[`index.md`](index.md) › 入门

本页说明如何准备环境、构建并运行各组件。

## 环境要求

| 组件 | 要求 |
| --- | --- |
| 全部 | JDK 21（LTS） |
| 模组 | NeoForge（Gradle 通过 wrapper 自动下载） |
| 后端 | Maven Wrapper（`web/backend/mvnw.cmd`，无需手动装 Maven） |
| IDE | 推荐 IntelliJ IDEA（社区版即可） |

### 本机已装环境（备忘）

- JDK 21 位于 `D:\Jvav\Java\jdk-21`，`java` 命令可用。
- 但 **`JAVA_HOME` 未配置全局**，直接跑 `mvnw.cmd` 会报 `JAVA_HOME is not defined`。建议在系统环境变量中把 `JAVA_HOME` 设为 `D:\Jvav\Java\jdk-21`；或在每次命令行会话开头临时设置：

```powershell
$env:JAVA_HOME = "D:\Jvav\Java\jdk-21"
```

## 构建与运行

### 模组

在**仓库根目录**执行：

```powershell
.\mod\gradlew.bat -p .\mod build
```

开发运行（NeoGradle 提供 run 配置，也可在 IDEA 里点）时，可运行 `client`（进游戏）或 `server`（专用服）。

### 后端

在 `web/backend` 目录执行：

```powershell
cd .\web\backend
.\mvnw.cmd test        # 构建并运行上下文启动测试
.\mvnw.cmd spring-boot:run   # 本地启动后端（默认 8080 端口）
```

后端首次启动会：
- 在 `web/backend/data/` 生成 H2 文件数据库（已加入 `.gitignore`）。
- 读取 `src/main/resources/application.yaml` 中的 `app.*` 配置。

## 相关文档

- [架构总览](architecture.md)
- [模组](mod/index.md)
- [后端](backend/index.md)
- [定位指南与源码映射](navigation.md)