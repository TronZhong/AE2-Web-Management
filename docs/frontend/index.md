# 前端

> 文档首页：[`index.md`](../index.md) › 组件 › 前端

浏览器端 UI。负责登录、网络选择、库存与合成状态的展示，以及下达合成命令的表单。

## 状态

- **预留**：尚未选择框架与包结构，无任何代码。
- 目录：`web/frontend/`（当前仅有 [`README.md`](../../web/frontend/README.md) 指针）。

## 约定

- 前端只与后端通信，不直连 Minecraft 服务器或 AE2 网络。
- 与后端的跨域由后端 `app.cors.allowed-origins` 控制（当前占位 `http://localhost:5173`，为 Vite 默认端口）。

## 相关文档

- [架构总览](../architecture.md)
- [后端](../backend/index.md)
- [数据契约](../contracts/index.md)