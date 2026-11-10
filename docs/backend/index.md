# 后端（Spring Boot）

> 文档首页：[`index.md`](../index.md) › 组件 › 后端

对网页暴露 API，负责登录/认证、授权、持久化，并把对游戏数据的请求转发给模组本地 API。当前为**分层骨架 + 转发层 + 账号认证 + 网格密码访问控制 + 实时推送（SSE）**（只读查询与合成命令均透传模组，全部要求登录；设有网格密码的网格另需按账号解锁；状态变化经 SSE 主动推给前端）。

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
| [`../../web/backend/pom.xml`](../../web/backend/pom.xml) | 依赖清单（data-jpa / validation / webmvc / h2 / sa-token-spring-boot4-starter / spring-security-crypto） |
| [`../../web/backend/src/main/resources/application.yaml`](../../web/backend/src/main/resources/application.yaml) | 运行配置：数据源、端口、`app.*`、`sa-token.*` |
| [`../../web/backend/src/main/java/com/ae2webmanagement/backend/BackendApplication.java`](../../web/backend/src/main/java/com/ae2webmanagement/backend/BackendApplication.java) | 启动类（含 `@ConfigurationPropertiesScan` + `@EnableScheduling`） |
| `.../backend/config/AppProperties.java` | 绑定 `app.*` 配置（模组端点、共享密钥、CORS、认证门控与初始管理员） |
| `.../backend/config/ModClientConfig.java` | 组装访问模组的 `RestClient` bean（带 `X-Shared-Secret` 头；连接 2s / 读取 5s 超时） |
| `.../backend/config/CorsConfig.java` | 全局 CORS（`allowCredentials(true)` 以便跨域携带 satoken Cookie） |
| `.../backend/config/SaTokenConfig.java` | Sa-Token 登录拦截器：拦截 `/api/**`、放行 `/api/auth/login`；`app.auth.enabled=false` 关闭（仅测试切片） |
| `.../backend/client/ModApiClient.java` | 访问模组的唯一入口：`listGrids` / `getSnapshot` / `submitCraft` / `getCommands` / `deleteCommand`（v0.9 删除命令记录）/ `verifyPassword`（v0.3 密码校验）/ `fetchIcon`（v0.7 图标字节转发，不做 JSON 解析）；非 2xx 抛 `ModApiException` |
| `.../backend/client/ModApiException.java` | 模组调用链路的统一业务异常（HTTP 状态码 + 机器可读错误码） |
| `.../backend/service/GridService.java` | 用例编排与错误翻译（accessId/commandId 校验、`mod_unreachable` / `mod_unavailable`、其余错误码透传）；网格访问控制：`listGrids` 计算 `unlocked`、`ensureAccessible` 锁定检查（`403 grid_locked`）、`unlock` 编排（模组校验通过后写解锁记录）、`deleteCommand` 转发删除 |
| `.../backend/service/AuthService.java` | 账号认证用例：登录（BCrypt 校验 + Sa-Token 会话）、注销、`/me`、`currentAccountId`（访问控制取账号 ID）；`ApplicationRunner` 首次启动引导建号 |
| `.../backend/controller/GridController.java` | `GET /api/grids`（含 `hasPassword`/`unlocked`）、`GET /api/grids/{accessId}/snapshot`、`POST /api/grids/{accessId}/unlock`（v0.3） |
| `.../backend/controller/IconController.java` | `GET /api/icons/{namespace}/{path}.png`（契约 v0.7）：图标纯字节转发——校验路径字符集后直连 `ModApiClient.fetchIcon`，透传 `image/png` 与一天缓存头；不经 service 业务层，错误兜底见 `GlobalExceptionHandler` |
| `.../backend/controller/CraftController.java` | `POST /api/grids/{accessId}/craft`（校验请求体）、`GET /api/grids/{accessId}/commands`、`DELETE /api/grids/{accessId}/commands/{commandId}`（v0.9 删除命令记录） |
| `.../backend/controller/AuthController.java` | `POST /api/auth/login`（唯一免认证端点）、`POST /api/auth/logout`、`GET /api/auth/me` |
| `.../backend/controller/GlobalExceptionHandler.java` | 全局异常兜底：统一输出 `{"error":"code"}`；`NotLoginException` → 401 `unauthorized`；`ResourceAccessException` → 503 `mod_unreachable`（服务不经 service 的直接转发路径，如图标端点） |
| `.../backend/controller/RealtimeController.java` | `GET /api/events`（SSE 实时事件，Sa-Token Cookie 认证；长超时 + 心跳；事件形状见 docs/contracts/web-api.md「实时事件」） |
| `.../backend/service/RealtimeEventHub.java` | SSE emitter 集合管理：注册 / 移除 / 广播 / 定时心跳 |
| `.../backend/service/GridStateSyncService.java` | 每 2s 拉模组网格列表与内存指纹比对，变化即经 `RealtimeEventHub` 广播（指纹只含模组侧状态，不含账号 `unlocked`） |
| `.../backend/dto/` | 契约 DTO（record，AEKey `key` 以 `JsonNode` 透传；认证 DTO 为 `LoginRequest` / `LoginResponse` / `MeResponse`）。模组侧与 web 侧网格摘要分离：`ModGridListResponse` / `ModGridSummaryResponse`（可空 `Boolean hasPassword`、可空 `Long lastSeenAt` 承接旧版模组）→ `GridService` 映射为 `GridListResponse` / `GridSummaryResponse`（primitive `hasPassword` + 计算的 `unlocked` + `@JsonInclude(NON_NULL) lastSeenAt`）；解锁请求 `UnlockRequest`、模组校验请求 `VerifyPasswordRequest` |
| `.../backend/domain/Account.java` | JPA 实体：后端账号（用户名唯一 + BCrypt 哈希，表 `accounts`） |
| `.../backend/domain/GridAccess.java` | JPA 实体：账号已解锁网格记录（表 `account_grid_access`，`unique(account_id, access_id)`） |
| `.../backend/repository/AccountRepository.java` | 账号表访问（按用户名查找、是否已有账号） |
| `.../backend/repository/GridAccessRepository.java` | 已解锁网格表访问（按账号+网格判定、清除密码时整网格撤销） |

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
| `sa-token.*` | token-name `satoken`、timeout 7 天等 | 账号会话：Cookie / 请求头 `satoken` |
| `app.mod.base-url` | `http://127.0.0.1:8090` | 模组本地 API 地址（占位） |
| `app.mod.shared-secret` | `change-me` | 后端↔模组机器级认证密钥，**部署前必须替换** |
| `app.cors.allowed-origins` | `http://localhost:5173` | 允许的前端来源（占位） |
| `app.auth.enabled` | `true` | 是否强制登录（`/api/**` 除登录端点）；仅测试切片关闭 |
| `app.auth.admin-username` / `admin-password` | `admin` / `change-me` | **账户表为空的首次启动**引导创建的初始管理员（密码只以 BCrypt 哈希入库）；部署前务必替换 |

## 构建与运行

```powershell
cd .\web\backend
.\mvnw.cmd test              # 构建 + 上下文启动测试
.\mvnw.cmd spring-boot:run   # 本地运行
```

## 当前状态与后续

- [x] 分层骨架、CORS、模组 `RestClient`（含 2s/5s 超时）、H2 文件库、配置绑定已就绪。
- [x] **P1 只读转发 + P2 合成命令转发已实现**：`/api/grids`、`/api/grids/{id}/snapshot`、`/api/grids/{id}/craft`、`/api/grids/{id}/commands` 均透传模组响应；错误翻译见 `GridService`，兜底见 `GlobalExceptionHandler`。
- [x] **P3 账号认证已实现**（Sa-Token `1.46.0` spring-boot4-starter，兼容 Spring Boot 4.1 已实测）：登录/注销/`/me` 端点 + 拦截器保护 `/api/**`；H2 账户表（`domain` / `repository`）；首次启动引导建号；`satoken` Cookie 会话。
- [x] **网格密码访问控制已实现**（契约 v0.3）：网格摘要计算 `unlocked`；snapshot/craft/commands 转发前锁定检查（`403 grid_locked`）；`POST /api/grids/{id}/unlock`（模组 `verify-password` 204 后按账号写 `account_grid_access`，密码不匹配透传 `401 invalid_password`）；密码清除时撤销该网格解锁记录；旧版模组（无 `hasPassword` 字段）按公开网格处理。
- [x] 测试：`ModApiClientTests`（MockRestServiceServer，含 verify-password 与旧模组兼容、fetchIcon 字节转发）、`ModClientConfigTests`（超时）、`GridServiceTests`（错误翻译 + 访问控制）、`GridControllerTests` / `CraftControllerTests` / `IconControllerTests`（MockMvc 字段对齐，切片关闭拦截器）/ `RealtimeControllerTests`、`AuthFlowTests`（全上下文认证闭环）、`GridAccessFlowTests`（全上下文解锁闭环：403 → 401 → 204 → unlocked → snapshot 放行）、`GridStateSyncServiceTests`（指纹比对与事件广播）。
- [x] **实时推送（SSE）已实现**（roadmap 已完成项）：`GET /api/events` 订阅实时事件；`GridStateSyncService` 每 2s 拉模组列表比对指纹，变化即广播 `grids-updated` / `grid-snapshot-updated`（只含模组侧状态，不含账号 `unlocked`）；前端收到后 refetch，轮询保留作兜底。
- [x] **术语统一为 grid（契约 v0.5，模组 0.0.8）**：`NetworkController`→`GridController`、`NetworkService`→`GridService`、`Network*Response`→`Grid*Response`、`ModNetwork*`→`ModGrid*`、`ModApiClient.listNetworks`→`listGrids`；URL `/api/networks`→`/api/grids`；错误码 `network_locked`→`grid_locked`、`network_not_found`→`grid_not_found`。仅术语改名，`accessId` 与字段值不变。
- [x] **物品中文名称透传（契约 v0.6，模组 0.0.9，roadmap.md §4）**：`StackEntry` DTO 新增可选 `nameKey`（模组快照 JSON 同名字段透传，`JsonNode key` 不变）；`@JsonInclude(NON_NULL)` 省略 null，旧版模组/旧快照无该字段时 Web 响应仍然省略（向后兼容）。
- [x] **物品图标转发（契约 v0.7，模组 0.0.10，roadmap.md §5）**：`GET /api/icons/{namespace}/{path}.png`——`IconController` 校验路径字符集（`[a-z0-9_.-]`，非法 400 `invalid_icon_path`）后经 `ModApiClient.fetchIcon` 纯字节转发模组 `GET /icons/...`（不做 JSON 解析、不自建缓存、不做 per-network 访问控制），响应透传 `image/png` + `Cache-Control: public, max-age=86400`；模组 404 `icon_not_found`（未渲染，惰性补齐中）原样透传；`GlobalExceptionHandler` 兜底翻译 `ResourceAccessException` → 503 `mod_unreachable`。
- [x] **命令记录删除转发（契约 v0.9，模组 0.0.13）**：`DELETE /api/grids/{id}/commands/{commandId}` 纯转发模组（`CraftController` → `GridService.deleteCommand` → `ModApiClient.deleteCommand`）；`commandId` 非法 400 `invalid_command_id`；`grid_locked` 访问控制同样适用；模组 404 `command_not_found` / `grid_not_found` 原样透传。模组侧自动保留每网格最近 50 条（可配置）终态记录。
- [x] **`lastSeenAt` 透传（契约 v0.10，模组 0.0.14）**：`ModGridSummaryResponse` 增加可空 `Long lastSeenAt`（旧版模组缺字段 → null），`GridSummaryResponse` 以 `@JsonInclude(NON_NULL)` 输出（null 省略，向后兼容）；`GridService` 原样映射。
- [ ] 暂无改密 / 注册 / 多账号管理端点（个人自用场景，roadmap 未列入待办）。

## 相关文档

- [架构总览](../architecture.md)
- [后续迭代路线](../roadmap.md)（认证语义已拍板：账号制先行）
- [数据契约](../contracts/index.md)
- [模组](../mod/index.md)
- [快速开始](../getting-started.md)