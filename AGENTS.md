# Repository Guidelines

> 本文件面向参与本仓库开发的人员与 AI Agent。开始修改代码前，请先阅读 [`docs/index.md`](docs/index.md)；组件职责、数据流与技术设计均以 `docs/` 为准。

## 回答与验证

功能新增或行为修改完成后，最终回答必须包含“验证方法”小节：

- 列出已执行的命令、测试或检查，并说明实际结果。
- 提供可复现的手动验证步骤与预期结果；无法自动验证时说明原因。
- 若未执行验证，明确写出原因及下一步应运行的命令。
- 纯文档或格式调整可注明“无需运行构建”，但仍需说明已检查的内容。
- 不只写“测试通过”；验证信息应足以让另一位开发者独立复现。

## 项目结构

| 路径 | 职责 |
| --- | --- |
| `mod/` | NeoForge 1.21.1 模组（Java 21）；负责读写 AE2 网络状态，仅暴露本机数据口。 |
| `web/backend/` | Spring Boot 4.1 MVC 后端；负责 Web API、认证、授权、持久化与请求转发。 |
| `web/frontend/` | 预留的浏览器 UI；只与后端通信。 |
| `contracts/` | 预留的版本化模组↔后端契约。 |
| `docs/` | 权威文档；项目结构、源码映射与设计决策均从这里查。 |

后端按 `controller` / `service` / `repository` / `domain` / `dto` / `client` / `config` 分层。模组与后端不得共享实现类，只能通过契约对齐。

## 构建与运行

需要 JDK 21。Maven 运行前设置 `JAVA_HOME`；Unix 环境将 `gradlew.bat` / `mvnw.cmd` 换成 `./gradlew` / `./mvnw`。

```powershell
.\mod\gradlew.bat -p .\mod build      # 构建并测试模组
.\mod\gradlew.bat -p .\mod runClient  # 启动开发客户端

cd .\web\backend
.\mvnw.cmd test                       # 编译并运行后端测试
.\mvnw.cmd spring-boot:run            # 启动后端，默认 8080
```

> 注意：CI 目前只构建模组，配置见 `.github/workflows/build-mod.yml`；后端测试需在本地执行。

## 代码风格

- Java 21、UTF-8，左大括号与声明同行。
- 模组源码使用 4 个空格；后端生成源码使用 Tab。修改时优先跟随所在文件。
- 类型使用 `PascalCase`，方法与字段使用 `camelCase`，包名全小写。
- JSON 使用 2 个空格缩进。
- 保持后端分层：Controller 只做协议适配，Service 编排用例，Repository 负责持久化；DTO 不携带持久化注解。
- 仓库未配置格式化或 Lint 工具，以编译和测试结果作为最低验证。

## 测试约定

- 后端使用 JUnit 5 与 Spring Boot Test。测试类放在 `web/backend/src/test/java/` 对应包下，以 `*Tests` 命名。
- 模组使用 NeoForge GameTest；当前尚无模组测试，新增游戏逻辑时应同步补充。
- 未配置覆盖率门槛。新行为应添加聚焦测试，并运行 `.\mvnw.cmd test` 或对应 Gradle 任务。

## 提交与 PR

- 沿用现有 Conventional Commits：`feat:`、`fix:`、`docs:`、`build:`、`test:`、`refactor:`，后接简短说明，例如 `feat: 支持网络库存查询`。
- 一次提交只处理一个主题；PR 说明应包含变更范围、行为变化、验证命令和相关 Issue。
- UI 变更附截图；API、契约、配置或依赖变更必须单独说明。
- 合并前确保模组 CI 与受影响的本地测试通过。

## 架构与安全

```
前端（预留）:5173 → 后端:8080 → 模组 127.0.0.1:8090
```

后端通过 `X-Shared-Secret` 调用模组；模组数据口不得对外暴露。所有 Minecraft/AE2 状态访问必须经 `MinecraftServer#execute` 调度到主线程。部署前替换 `change-me` 和空共享密钥，禁止提交数据库、运行目录或凭证。

## 相关文档

- [文档首页](docs/index.md)
- [快速开始](docs/getting-started.md)
- [架构总览](docs/architecture.md)
- [定位指南与源码映射](docs/navigation.md)
