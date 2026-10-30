# 后端（Spring Boot）

> 文档首页：[`index.md`](../index.md) › 组件 › 后端

对网页暴露 API，负责登录/认证、授权、持久化，并把对游戏数据的请求转发给模组本地 API。当前为**分层骨架**，未写具体业务。

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
| `.../backend/config/ModClientConfig.java` | 组装访问模组的 `RestClient` bean（带 `X-Shared-Secret` 头） |
| `.../backend/config/CorsConfig.java` | 全局 CORS |
| `.../backend/client/ModApiClient.java` | 访问模组的唯一入口（当前仅字段，无方法） |
| `.../backend/controller/` | Web 层（`package-info` 占位，无实现） |
| `.../backend/service/` | 应用服务层（占位） |
| `.../backend/repository/` | 持久化层（占位） |
| `.../backend/domain/` | JPA 实体（占位） |
| `.../backend/dto/` | 请求/响应与契约对象（占位） |

> 说明：`config/` 与 `client/` 下省略的完整路径为
> `web/backend/src/main/java/com/ae2webmanagement/backend/`，各层职责见各自 `package-info.java`。

分层职责边界：

- `controller` 只做协议适配（参数绑定/校验、转 DTO），不含业务。
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

- [x] 分层骨架、CORS、模组 `RestClient`、H2 文件库、配置绑定已就绪。
- [ ] **未加认证**：计划引入 `Sa-Token`（`sa-token-spring-boot4-starter`）。
- [ ] `controller/service/repository/domain/dto` 均为空，无实体、无接口。
- [ ] 初版本机 API 契约已记录，但 `controller/service/client/dto` 尚未实现转发。

## 相关文档

- [架构总览](../architecture.md)
- [数据契约](../contracts/index.md)
- [模组](../mod/index.md)
- [快速开始](../getting-started.md)