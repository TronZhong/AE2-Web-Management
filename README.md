# AE2 Web Management

本仓库包含相互独立的 Minecraft 模组与网页应用项目。两者保持职责分离，未来仅通过版本化的数据契约和网络通信进行集成。

[English](docs/README_en.md)

## 项目结构

- [`mod/`](mod/)：NeoForge 模组工程，负责 Minecraft 与 AE2 集成。
- [`web/backend/`](web/backend/)：Spring Boot 后端骨架，后续负责 Web API、访问控制和数据持久化。
- [`web/frontend/`](web/frontend/)：网页前端预留目录。
- [`contracts/`](contracts/)：模组与后端之间的 API 和消息契约预留目录。这里不放置任一项目的实现代码。
- [`docs/README.en.md`](docs/README.en.md)：本文档的英文版。

前端、后端和模组可独立构建与运行。目前尚未实现 AE2 集成、API 或数据交换功能。

## 构建

在仓库根目录构建模组：

```powershell
.\mod\gradlew.bat -p .\mod build
```

在后端工程目录构建并运行测试：

```powershell
cd .\web\backend
.\mvnw.cmd test
```

## 职责边界

- 只有模组读取或修改游戏内的 AE2 状态。
- 后端负责协调请求、访问控制和面向网页的数据。
- 前端只与后端通信，不直接连接 Minecraft 服务器或 AE2 网络。
- 模组与后端通过有文档说明、带版本的数据契约交换数据；双方不应依赖对方的内部类。
