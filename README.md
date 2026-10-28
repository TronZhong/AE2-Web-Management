# AE2 Web Management

通过网页访问 AE2 网络、查看库存与合成状态、下达合成命令的整合项目。

本项目由**相互独立**的三个部分 + 一份共享契约组成，只通过版本化的数据契约和网络通信集成：

- **模组**（NeoForge）：游戏内唯一能读写 AE2 状态的部分，充当数据出口。
- **后端**（Spring Boot）：对网页暴露 API，负责登录、授权与请求编排。
- **前端**（浏览器）：只与后端通信，不直接接触 Minecraft/AE2。
- **契约**（contracts）：模组与后端之间交换数据的版本化约定。

> 当前状态：骨架阶段。模组尚未接入 AE2，后端已搭建分层框架，`contracts` 与 `frontend` 为占位。

## 文档在哪里

**所有权威文档集中在 [`docs/`](docs/index.md)。** 后续任何会话请从 [`docs/index.md`](docs/index.md) 进入，按组件索引和源码映射直接定位代码位置，不要在各子目录的 README 里找内容（它们只提供指针）。

| 想了解 / 定位 | 入口 |
| --- | --- |
| 文档总目录 / 从哪里开始 | [`docs/index.md`](docs/index.md) |
| 环境搭建、构建、运行 | [`docs/getting-started.md`](docs/getting-started.md) |
| 架构与职责边界 | [`docs/architecture.md`](docs/architecture.md) |
| 如何快速定位某段代码 | [`docs/navigation.md`](docs/navigation.md) |
| 模组 | [`docs/mod/`](docs/mod/index.md) |
| 后端 | [`docs/backend/`](docs/backend/index.md) |
| 前端（预留） | [`docs/frontend/`](docs/frontend/index.md) |
| 数据契约（预留） | [`docs/contracts/`](docs/contracts/index.md) |

## 目录结构

```
ae2_web/
├── docs/               # 权威文档（从这里的 index.md 进入）
├── mod/                # NeoForge 模组工程
├── web/
│   ├── backend/        # Spring Boot 后端
│   └── frontend/       # 前端（预留）
└── contracts/          # 数据契约（预留）
```

## 快速构建

```powershell
# 构建模组（在仓库根目录）
.\mod\gradlew.bat -p .\mod build

# 构建并测试后端（在后端目录）
cd .\web\backend
.\mvnw.cmd test
```

详细信息见 [`docs/getting-started.md`](docs/getting-started.md)。