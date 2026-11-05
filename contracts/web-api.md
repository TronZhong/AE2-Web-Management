# Web API 契约（后端 ↔ 前端）

后端对网页前端暴露的 `/api` 端点的版本化 schema；当前为 v0.4（网络列表 + 单网络快照只读端点、合成命令 `/craft`、`/commands` 均为对模组的透传转发；v0.2 新增账号制认证端点 `/auth/login|logout|me`；v0.3 新增网格密码访问控制；v0.4 新增实时事件端点 `GET /api/events`（SSE））。

> 权威文档见 [`docs/contracts/web-api.md`](../docs/contracts/web-api.md)。本文只作指针。
