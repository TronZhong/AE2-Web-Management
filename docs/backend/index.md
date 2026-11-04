# 后端（Spring Boot）

> 文档首页：[`index.md`](../index.md) › 组件 › 后端

对网页暴露 API，负责登录/认证、授权、持久化，并把对游戏数据的请求转发给模组本地 API。当前为**分层骨架 + 已实现的转发层**（只读查询与合成命令均透传模组），认证未接入。

## 概览

| 项 | 值 |
| --- | --- |
| 框架 | Spring Boot 4.1.2（MVC，非 WebFlux） |
| Java | 21 |
| 构建 | Maven（`web/backend/mvnw.cmd`） |
| 基础包 | `com.ae2webmanagement.backend` |
| 数据源 | H2 文件模式（`web/backend/data/ae2web`） |

## 分层结构与源码映射

| 路径 | 说明 |
| --- | --- |
| [`../../web/backend/pom.xml`](../../web/backend/pom.xml) | 依赖清单（data-jpa / validation / webmvc / h2） |
| [`../../web/backend/src/main/resources/application.yaml`](../../web/backend/src/main/resources/application.yaml) | 运行配置：数据源、端口、`app.*` |
| [`../../web/backend/src/main/java/com/ae2webmanagement/backend/BackendApplication.java`](../../web/backend/src/main/java/com/ae2webmanagement/backend/BackendApplication.java) | 启动类（含 `@ConfigurationPropertiesScan`） |
| `.../backend/config/AppProperties.java` | 绑定 `app.*` 配置（模组端点、共享密钥、CORS） |
| `.../backend/config/ModClientConfig.java` | 组装访问模组的 `RestClient` bean（带 `X-Shared-Secret` 头；连接 2s / 读取 5s 超时） |
| `.../backend/config/CorsConfig.java` | 全局 CORS |
| `.../backend/client/ModApiClient.java` | 访问模组的唯一入口：`listNetworks` / `getSnapshot` / `submitCraft` / `getCommands`；非 2xx 抛 `ModApiException` |
| `.../backend/client/ModApiException.java` | 模组调用链路的统一业务异常（HTTP 状态码 + 机器可读错误码） |
| `.../backend/service/NetworkService.java` | 用例编排与错误翻译（accessId 校验、`mod_unreachable` / `mod_unavailable`、其余错误码透传） |
| `.../backend/controller/NetworkController.java` | `GET /api/networks`、`GET /api/networks/{accessId}/snapshot` |
| `.../backend/controller/CraftController.java` | `POST /api/networks/{accessId}/craft`（校验请求体）、`GET /api/networks/{accessId}/commands` |
| `.../backend/controller/GlobalExceptionHandler.java` | 全局异常兜底：统一输出 `{"error":"code"}`，兜底 `500 internal_error` |
| `.../backend/dto/` | 契约 DTO（record，模组响应与前端响应共用一套；AEKey `key` 以 `JsonNode` 透传） |
| `.../backend/repository/` | 持久化层（占位，本轮零表） |
| `.../backend/domain/` | JPA 实体（占位，留给认证阶段） |

> 说明：`config/` 与 `client/` 下省略的完整路径为
> `web/backend/src/main/java/com/ae2webmanagement/backend/`，各层职责见各自 `package-info.java`。

分层职责边界：

- `controller` 只做协议适配（参数绑定/校验、转 DTO），不含业务；异常一律上抛给 `GlobalExceptionHandler`。
- `service` 编排用例、事务边界；访问数据走 `repository`，访问模组走 `client`。
- `dto` 不携带持久化注解、不依赖实体内部实现。

## 配置（application.yaml）

| 键 | 当前值 | 说明 |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:h2:file:./data/ae2web` | H2 文件库，重启不丢数据 |
| `server.port` | `8080` | 后端对外端口 |
| `app.mod.base-url` | `http://127.0.0.1:8090` | 模组本地 API 地址（占位） |
| `app.mod.shared-secret` | `change-me` | 后端↔模组机器级认证密钥，**部署前必须替换** |
| `app.cors.allowed-origins` | `http://localhost:5173` | 允许的前端来源（占位） |

## 构建与运行

```powershell
cd .\web\backend
.\mvnw.cmd test              # 构建 + 上下文启动测试
.\mvnw.cmd spring-boot:run   # 本地运行
```

## 当前状态与后续

- [x] 分层骨架、CORS、模组 `RestClient`（含 2s/5s 超时）、H2 文件库、配置绑定已就绪。
- [x] **P1 只读转发 + P2 合成命令转发已实现**：`/api/networks`、`/api/networks/{id}/snapshot`、`/api/networks/{id}/craft`、`/api/networks/{id}/commands` 均透传模组响应；错误翻译见 `NetworkService`，兜底见 `GlobalExceptionHandler`。
- [x] 测试：`ModApiClientTests`（MockRestServiceServer）、`ModClientConfigTests`（超时）、`NetworkServiceTests`（错误翻译）、`NetworkControllerTests` / `CraftControllerTests`（MockMvc 字段对齐）。
- [ ] **未加认证**：语义已拍板**账号制先行**（Sa-Token + H2 账户表，登录后可见全部网络；每网络配对令牌为后续增强），决策与模组侧配套见 [`../roadmap.md`](../roadmap.md) §2。接入时需实测 `sa-token-spring-boot4-starter` 与 Spring Boot 4.1 的兼容性。
- [ ] `repository/domain` 仍为空（本轮零表），留给认证阶段。

## 相关文档

- [架构总览](../architecture.md)
- [后续迭代路线](../roadmap.md)（认证语义已拍板：账号制先行）
- [数据契约](../contracts/index.md)
- [模组](../mod/index.md)
- [快速开始](../getting-started.md)