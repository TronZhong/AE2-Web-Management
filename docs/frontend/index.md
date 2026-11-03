# 前端

> 文档首页：[`index.md`](../index.md) › 组件 › 前端

浏览器端 UI。负责登录、网络选择、库存、样板/可合成项展示、合成状态，以及下达合成命令。当前已实现**网格选择页 + 网格详情页（只读）**，数据来自按契约构造的 mock（后端转发未实现）。

## 概览

| 项 | 值 |
| --- | --- |
| 框架 | Vue 3 + Vite |
| 路由 | vue-router 4（`/` 选择页，`/networks/:accessId` 详情页） |
| 样式 | Tailwind CSS v4（设计令牌在 `src/style.css` 的 `@theme`，暗色终端风） |
| 数据源 | 默认 mock；`VITE_USE_MOCK=false` 切换真实请求（后端上线后生效） |
| 契约 | [`contracts/web-api.md`](../../contracts/web-api.md)（v0，指向 `docs/contracts/web-api.md`） |

## 目录结构与源码映射

| 路径 | 说明 |
| --- | --- |
| [`../../web/frontend/vite.config.js`](../../web/frontend/vite.config.js) | Vite 配置：`@vitejs/plugin-vue` + `@tailwindcss/vite` |
| [`../../web/frontend/src/style.css`](../../web/frontend/src/style.css) | 全局样式与设计令牌（色板/字体/圆角），组件不写死颜色；`html` 按 `--app-zoom`（1.5）整体放缩 |
| [`../../web/frontend/src/main.js`](../../web/frontend/src/main.js) | 入口：挂载根组件并接通路由 |
| [`../../web/frontend/src/App.vue`](../../web/frontend/src/App.vue) | 应用外壳：顶栏（标识 + MOCK/实时徽标）、路由出口、页脚 |
| **`router/`** | |
| [`../../web/frontend/src/router/index.js`](../../web/frontend/src/router/index.js) | 路由表：选择页与详情页（懒加载、`props: true` 传 accessId） |
| **`api/`** — 后端调用层（组件唯一网络出口） | |
| [`../../web/frontend/src/api/index.js`](../../web/frontend/src/api/index.js) | `fetchNetworks` / `fetchSnapshot`；`USE_MOCK` 开关与契约错误码归一化 |
| [`../../web/frontend/src/api/mock/fixtures.js`](../../web/frontend/src/api/mock/fixtures.js) | 三个示例网络（两在线一离线）的列表与快照固定数据 |
| [`../../web/frontend/src/api/mock/index.js`](../../web/frontend/src/api/mock/index.js) | mock 读取：模拟延迟；在线快照推进 `capturedAt` 并少量漂移数量 |
| **`composables/`** — 状态与轮询 | |
| [`../../web/frontend/src/composables/useNetworks.js`](../../web/frontend/src/composables/useNetworks.js) | 网络列表状态（首次 loading、后续静默刷新） |
| [`../../web/frontend/src/composables/useSnapshot.js`](../../web/frontend/src/composables/useSnapshot.js) | 单网络快照状态 |
| [`../../web/frontend/src/composables/usePolling.js`](../../web/frontend/src/composables/usePolling.js) | 定时轮询：页面不可见暂停、恢复补拉、卸载自停 |
| [`../../web/frontend/src/composables/useItemTooltip.js`](../../web/frontend/src/composables/useItemTooltip.js) | 物品悬浮提示的全局单例状态（ItemSlot 写入，ItemTooltip 渲染） |
| **`lib/`** — 纯函数 | |
| [`../../web/frontend/src/lib/format.js`](../../web/frontend/src/lib/format.js) | 数量千分位/紧凑角标、存储字节（1024 进制）、时间与 AEKey 友好名 |
| **`components/base/`** — 可复用基础组件 | |
| [`../../web/frontend/src/components/base/BaseCard.vue`](../../web/frontend/src/components/base/BaseCard.vue) | 统一卡片容器（标题 + 动作区插槽 + 内容插槽） |
| [`../../web/frontend/src/components/base/StatusBadge.vue`](../../web/frontend/src/components/base/StatusBadge.vue) | 状态徽标（ok/err/accent/neutral 四变体） |
| [`../../web/frontend/src/components/base/BaseButton.vue`](../../web/frontend/src/components/base/BaseButton.vue) | 按钮（primary/ghost） |
| [`../../web/frontend/src/components/base/EmptyState.vue`](../../web/frontend/src/components/base/EmptyState.vue) | 空状态占位 |
| [`../../web/frontend/src/components/base/ItemSlot.vue`](../../web/frontend/src/components/base/ItemSlot.vue) | MC 风格物品格：hover 走全局提示层（数量跟随数据刷新）；贴图 URL 预留，当前按 id 稳定着色的方块占位 |
| [`../../web/frontend/src/components/base/ItemTooltip.vue`](../../web/frontend/src/components/base/ItemTooltip.vue) | 跟随鼠标的 MC 风格 tooltip（teleport 到 body，按缩放系数换算坐标） |
| **`components/grid/`** — 选择页组件 | |
| [`../../web/frontend/src/components/grid/GridCard.vue`](../../web/frontend/src/components/grid/GridCard.vue) | 网格卡片：名称、在线徽标、控制器位置、快照摘要、进入按钮 |
| [`../../web/frontend/src/components/grid/PasswordField.vue`](../../web/frontend/src/components/grid/PasswordField.vue) | 密码输入（**预留栏位**，不校验；语义待认证方案定稿后回填） |
| **`components/snapshot/`** — 详情页组件 | |
| [`../../web/frontend/src/components/snapshot/StatusCpuCard.vue`](../../web/frontend/src/components/snapshot/StatusCpuCard.vue) | 状态 + 合成 CPU 合并卡：在线态、数据截至、控制器、CPU 忙闲/存储/协处理器 |
| [`../../web/frontend/src/components/snapshot/InventoryCard.vue`](../../web/frontend/src/components/snapshot/InventoryCard.vue) | 库存卡：MC 格子布局（ItemSlot 网格），关键字过滤 + 按行窗口化虚拟滚动，列数自适应宽度 |
| [`../../web/frontend/src/components/snapshot/PatternListCard.vue`](../../web/frontend/src/components/snapshot/PatternListCard.vue) | 样板卡：主输出格子网格，点击格子弹出计划合成卡 |
| [`../../web/frontend/src/components/snapshot/CraftPlanModal.vue`](../../web/frontend/src/components/snapshot/CraftPlanModal.vue) | 计划合成弹窗：z 轴更高 + 背景模糊；输入需求以 MC 格子展示（角标 = 候选 × 倍率 × 批次）；提交按钮待 /craft 接通后启用 |
| **`pages/`** — 页面 | |
| [`../../web/frontend/src/pages/GridSelectPage.vue`](../../web/frontend/src/pages/GridSelectPage.vue) | 选择页：网格卡片栅格，10s 轮询 + 手动刷新 |
| [`../../web/frontend/src/pages/GridDetailPage.vue`](../../web/frontend/src/pages/GridDetailPage.vue) | 详情页：四类卡片布局，快照 5s 轮询，离线提示条 |

## 当前状态与后续

- [x] 网格选择页（列表 + 预留密码栏位，本期不校验）。
- [x] 网格详情页：状态+CPU 合并卡（左上 1/2）、样板格子系统（右上 1/2，点击弹计划合成卡）、库存格子系统（下方占满）。
- [x] MC 风格物品格（`ItemSlot`）：悬浮名称提示（全局 tooltip 层）、紧凑数量角标、按 id 着色的方块占位，贴图 URL 预留。
- [x] 全站默认 150% 放缩（`style.css` 的 `--app-zoom`）。
- [x] mock 数据源与 `VITE_USE_MOCK` 切换；契约错误码直接透出为错误文案。
- [ ] 后端按 `docs/contracts/web-api.md` 实现转发后切真实请求。
- [ ] 计划合成弹窗的「开始合成」接 `POST /craft`（后端转发后于下一迭代启用）。
- [ ] 认证接入（密码栏位语义：全局账号 or 每网络令牌，见 `docs/design.md` 4.1）。
- [ ] 物品贴图接入 `ItemSlot` 的 `iconUrl`（当前为方块占位）。

## 约定

- 前端只与后端通信，不直连 Minecraft 服务器或 AE2 网络；网络调用必须走 `src/api/`。
- 展示可合成项时，使用快照 `patterns[].primaryOutput`，不让用户手写 `AEKey` JSON。
- 「可合成」口径仅为样板主输出，不含可发射物品（`docs/design.md` §7），UI 文案已注明。
- 颜色/字体/圆角一律用 `style.css` 令牌生成的工具类，保持暗色终端风一致。
- 全站放缩由 `--app-zoom` 控制；涉及物理像素坐标的组件（如 `ItemTooltip`）必须按该系数换算。

## 常用命令

```powershell
cd .\web\frontend
npm install
npm run dev    # 本地开发，默认 5173
npm run build  # 生产构建
```

## 相关文档

- [架构总览](../architecture.md)
- [Web API 契约](../contracts/web-api.md)
- [后端](../backend/index.md)
- [数据契约](../contracts/index.md)
