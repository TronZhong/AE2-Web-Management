# Web API 契约（后端 ↔ 前端）

后端对网页前端暴露的 `/api` 端点的版本化 schema；当前为 v0.9（网格列表 + 单网格快照只读端点、合成命令 `/craft`、`/commands`、`DELETE /commands/{commandId}` 均为对模组的透传转发；v0.2 新增账号制认证端点 `/auth/login|logout|me`；v0.3 新增网格密码访问控制；v0.4 新增实时事件端点 `GET /api/events`（SSE）；v0.5 全文术语统一为 grid——`/api/networks`→`/api/grids`、`networks`→`grids`、`network_locked/not_found`→`grid_locked/not_found`；v0.6 快照 stack 条目新增可选 `nameKey`（物品中文名称，roadmap.md §4，向后兼容）；v0.7 新增 `GET /api/icons/{namespace}/{path}.png` 图标字节转发端点，roadmap.md §5；v0.8 移除 `craftingCpus[].name`——CPU 无法命名，前端按列表序号 `#N` 显示，模组 0.0.12；v0.9 新增 `DELETE /api/grids/{accessId}/commands/{commandId}` 删除命令记录——删 PENDING 即撤销、删终态仅清理历史，模组 0.0.13）。

> 权威文档见 [`docs/contracts/web-api.md`](../docs/contracts/web-api.md)。本文只作指针。
