# 快速开始

> 文档首页：[`index.md`](index.md) › 入门

本页说明如何准备环境、构建并运行各组件。

## 环境要求

| 组件 | 要求 |
| --- | --- |
| 全部 | JDK 21（LTS） |
| 模组 | NeoForge（Gradle 通过 wrapper 自动下载） |
| 后端 | Maven Wrapper（`web/backend/mvnw.cmd`，无需手动装 Maven） |
| 前端 | Node.js / npm（Vue 3 + Vite） |
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
- 账户表为空时引导创建初始管理员（`app.auth.admin-username` / `app.auth.admin-password`，默认 `admin` / `change-me`，密码只以 BCrypt 哈希入库；部署前务必替换配置）。

### 登录

打开前端任意页面会先跳转登录页；除 `POST /api/auth/login` 外所有 `/api/**` 端点均要求已登录（`satoken` Cookie 会话，契约 v0.2）。

### 前端

在 `web/frontend` 目录执行：

```powershell
cd .\web\frontend
npm install        # 首次安装依赖
npm run dev        # 本地开发，默认 5173（实时数据，需后端 8080）
npm run dev:mock   # mock 数据源演示模式
npm run build      # 生产构建
```

前端已接入选择页、详情页（只读 + 合成命令）与登录页；默认走真实请求（dev 代理 `/api` → 8080），`VITE_USE_MOCK=true` 启用 mock。

## 相关文档

- [架构总览](architecture.md)
- [模组](mod/index.md)
- [后端](backend/index.md)
- [前端](frontend/index.md)
- [定位指南与源码映射](navigation.md)