# 前端

> 文档首页：[`index.md`](../index.md) › 组件 › 前端

浏览器端 UI。负责登录、网络选择、库存、样板/可合成项展示、合成状态，以及下达合成命令。

## 状态

- 已初始化 **Vue 3 + Vite** 脚手架，目录为 `web/frontend/`。
- 业务页面尚未接入后端；当前仓库中主要是脚手架模板代码。
- 包与脚本见 [`../../web/frontend/package.json`](../../web/frontend/package.json)。

## 约定

- 前端只与后端通信，不直连 Minecraft 服务器或 AE2 网络。
- 与后端的跨域由后端 `app.cors.allowed-origins` 控制（当前占位 `http://localhost:5173`，为 Vite 默认端口）。
- 展示可合成项时，应使用模组快照中的 `patterns[].primaryOutput`，不要让用户手写 `AEKey` JSON。

## 常用命令

```powershell
cd .\web\frontend
npm install
npm run dev    # 本地开发，默认 5173
npm run build  # 生产构建
```

## 相关文档

- [架构总览](../architecture.md)
- [后端](../backend/index.md)
- [数据契约](../contracts/index.md)