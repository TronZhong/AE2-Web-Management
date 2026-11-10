# 前端

> 文档首页：[`index.md`](../index.md) › 组件 › 前端

浏览器端 UI。负责登录、网格选择、库存、样板/可合成项展示、合成状态，以及下达合成命令。当前已实现**网格选择页 + 网格详情页（只读 + 合成命令下达与状态跟踪）**；默认走后端真实请求（实时数据），`VITE_USE_MOCK=true` 启用按契约构造的 mock。

## 概览

| 项 | 值 |
| --- | --- |
| 框架 | Vue 3 + Vite |
| 路由 | vue-router 4（`/login` 登录页，`/` 选择页，`/grids/:accessId` 详情页） |
| 样式 | Tailwind CSS v4（设计令牌在 `src/style.css` 的 `@theme`，暗色终端风） |
| 数据源 | 默认真实请求（实时数据）；`VITE_USE_MOCK=true`（或 `npm run dev:mock`）启用 mock（dev 走 Vite `/api` 代理） |
| 认证 | 账号制（后端 satoken Cookie 会话）；未登录一律重定向登录页 |
| 契约 | [`contracts/web-api.md`](../../contracts/web-api.md)（v0.10，指向 `docs/contracts/web-api.md`） |

## 目录结构与源码映射

| 路径 | 说明 |
| --- | --- |
| [`../../web/frontend/vite.config.js`](../../web/frontend/vite.config.js) | Vite 配置：`@vitejs/plugin-vue` + `@tailwindcss/vite`；dev 下把 `/api` 代理到后端 `http://localhost:8080`（真实请求模式下必需） |
| [`../../web/frontend/src/style.css`](../../web/frontend/src/style.css) | 全局样式与设计令牌（色板/字体/圆角），组件不写死颜色；`html` 按 `--app-zoom`（1.5）整体放缩 |
| [`../../web/frontend/src/main.js`](../../web/frontend/src/main.js) | 入口：挂载根组件并接通路由；注册 401 统一处理（清会话 → 跳登录页带回跳） |
| [`../../web/frontend/src/App.vue`](../../web/frontend/src/App.vue) | 应用外壳：顶栏（站点标识 + 当前账号；仅 mock 模式显示「MOCK 数据」徽标）、路由出口、页脚 |
| **`router/`** | |
| [`../../web/frontend/src/router/index.js`](../../web/frontend/src/router/index.js) | 路由表：选择页与详情页（懒加载、`props: true` 传 accessId） |
| **`api/`** — 后端调用层（组件唯一网络出口） | |
| [`../../web/frontend/src/api/index.js`](../../web/frontend/src/api/index.js) | `login` / `logout` / `fetchMe`（认证）+ `fetchGrids` / `fetchSnapshot` / `submitCraft` / `fetchCommands` / `deleteCommand`（v0.9 删除命令记录）/ `unlockGrid`（v0.3 解锁）+ `iconUrlOf`（v0.7 图标 URL 构造）；`USE_MOCK` 开关（默认实时，`VITE_USE_MOCK=true` 启用 mock）、契约错误码归一化、`onUnauthorized` 401 统一回调 |
| [`../../web/frontend/src/api/events.js`](../../web/frontend/src/api/events.js) | SSE 实时事件订阅（`EventSource` 连 `/api/events`；`USE_MOCK` 时 no-op）：`grids-updated` → 刷新列表，`grid-snapshot-updated` → 刷新对应网格；断线由浏览器自动重连，轮询兜底 |
| [`../../web/frontend/src/api/mock/fixtures.js`](../../web/frontend/src/api/mock/fixtures.js) | 三个示例网格（两在线一离线）的列表与快照固定数据；`GRID_PASSWORDS` 演示密码表（主基地网格 `1234`） |
| [`../../web/frontend/src/api/mock/index.js`](../../web/frontend/src/api/mock/index.js) | mock 读取：模拟延迟；在线快照推进 `capturedAt` 并少量漂移数量；命令队列模拟（在线网格 PENDING 延时转 SUBMITTED，离线保持排队；终态超 50 条自动修剪）+ `deleteCommand` 删除记录；网格密码模拟（锁定网格 snapshot/craft/commands 抛 `grid_locked`，`unlockGrid` 按会话记住解锁）；mock 登录接受任意非空凭证 |
| [`../../web/frontend/src/api/events.js`](../../web/frontend/src/api/events.js) | SSE 事件订阅（EventSource → `/api/events`；`USE_MOCK` 时 no-op） |
| **`composables/`** — 状态与轮询 | |
| [`../../web/frontend/src/composables/useAuth.js`](../../web/frontend/src/composables/useAuth.js) | 账号登录态单例：`ensureReady`（启动恢复会话）、`login` / `logout` / `clearSession` |
| [`../../web/frontend/src/composables/useGrids.js`](../../web/frontend/src/composables/useGrids.js) | 网格列表状态（首次 loading、后续静默刷新） |
| [`../../web/frontend/src/composables/useSnapshot.js`](../../web/frontend/src/composables/useSnapshot.js) | 单网格快照状态 |
| [`../../web/frontend/src/composables/useCommands.js`](../../web/frontend/src/composables/useCommands.js) | 单网格合成命令列表状态（全量历史；提交后可手动刷新） |
| [`../../web/frontend/src/composables/usePolling.js`](../../web/frontend/src/composables/usePolling.js) | 定时轮询：页面不可见暂停、恢复补拉、卸载自停 |
| [`../../web/frontend/src/composables/useItemTooltip.js`](../../web/frontend/src/composables/useItemTooltip.js) | 物品悬浮提示的全局单例状态（ItemSlot 写入，ItemTooltip 渲染） |
| **`lib/`** — 纯函数 | |
| [`../../web/frontend/src/lib/format.js`](../../web/frontend/src/lib/format.js) | 数量千分位/紧凑角标、存储字节（1024 进制）、时间与 AEKey 友好名（英文回退） |
| [`../../web/frontend/src/lib/itemName.js`](../../web/frontend/src/lib/itemName.js) | 物品中文名解析（roadmap §4）：`displayName()` 按 `nameKey` 查中文语言表 → id 启发式 → 英文友好名三级回退 |
| **`data/lang/`** — 中文语言表（官方文件「物品名相关键」子集） | |
| [`../../web/frontend/src/data/lang/minecraft.zh_cn.json`](../../web/frontend/src/data/lang/minecraft.zh_cn.json) | Minecraft 1.21.1 client.jar 的 `zh_cn.json` 子集（仅 `block.*` / `item.*` / `fluid_type.*` 键，正文未转写） |
| [`../../web/frontend/src/data/lang/ae2.zh_cn.json`](../../web/frontend/src/data/lang/ae2.zh_cn.json) | AE2 19.2.18（Modrinth jar）的 `zh_cn.json` 子集（同上过滤） |
| **`components/base/`** — 可复用基础组件 | |
| [`../../web/frontend/src/components/base/BaseCard.vue`](../../web/frontend/src/components/base/BaseCard.vue) | 统一卡片容器（标题 + 动作区插槽 + 内容插槽）；`fill` 模式（lg 起）内容区纵向 flex 撑满剩余高度，供同行等高卡片内部拉伸/滚动 |
| [`../../web/frontend/src/components/base/StatusBadge.vue`](../../web/frontend/src/components/base/StatusBadge.vue) | 状态徽标（ok/err/accent/neutral 四变体） |
| [`../../web/frontend/src/components/base/BaseButton.vue`](../../web/frontend/src/components/base/BaseButton.vue) | 按钮（primary/ghost） |
| [`../../web/frontend/src/components/base/EmptyState.vue`](../../web/frontend/src/components/base/EmptyState.vue) | 空状态占位 |
| [`../../web/frontend/src/components/base/ItemSlot.vue`](../../web/frontend/src/components/base/ItemSlot.vue) | MC 风格物品格：hover 走全局提示层（数量跟随数据刷新）；`iconUrl` 渲染模组导出的 64×64 PNG（`loading="lazy"`，`@error` 回退按 id 着色的方块占位并有限重试） |
| [`../../web/frontend/src/components/base/ItemTooltip.vue`](../../web/frontend/src/components/base/ItemTooltip.vue) | 跟随鼠标的 MC 风格 tooltip（teleport 到 body，按缩放系数换算坐标） |
| [`../../web/frontend/src/components/base/HoverDetail.vue`](../../web/frontend/src/components/base/HoverDetail.vue) | 截断文本悬浮详情：单行截断 + 末尾向下小箭头（仅溢出时出现），**悬停箭头**弹出完整内容的信息窗口（teleport 到 body、窗口左上角对齐鼠标使鼠标恰在窗口内；悬浮截断文本不触发）；移入窗口保持、移出窗口即关 |
| **`components/grid/`** — 选择页组件 | |
| [`../../web/frontend/src/components/grid/GridCard.vue`](../../web/frontend/src/components/grid/GridCard.vue) | 网格卡片：名称、在线徽标、锁定徽标（v0.3 `hasPassword && !unlocked`）、控制器位置、快照摘要、时间行（在线「数据截至」/ 离线「最后在线」，契约 v0.10 `lastSeenAt`，缺字段回退 `capturedAt`）、进入/解锁按钮 |
| [`../../web/frontend/src/components/grid/UnlockGate.vue`](../../web/frontend/src/components/grid/UnlockGate.vue) | 网格解锁门（v0.3）：锁定网格的详情页以此面板替代内容，密码提交 `POST /unlock`，`invalid_password` 等错误码直出，成功后冒泡 `unlocked` 事件 |
| **`components/snapshot/`** — 详情页组件 | |
| [`../../web/frontend/src/components/snapshot/StatusCpuCard.vue`](../../web/frontend/src/components/snapshot/StatusCpuCard.vue) | 状态 + 合成 CPU + 合成命令合并卡（详情页首行左卡，与样板卡横向对半分）：上半为在线态、数据截至、控制器、accessId；下半上下堆叠——上为 CPU 忙闲/存储/协处理器、下为合成命令列表（均最多完整显示 2 条，超出滚动）；过长控制器位置 / accessId 文本经 HoverDetail 悬浮展开 |
| [`../../web/frontend/src/components/snapshot/InventoryCard.vue`](../../web/frontend/src/components/snapshot/InventoryCard.vue) | 库存卡：MC 格子布局（ItemSlot 网格），关键字过滤 + 按行窗口化虚拟滚动，列数自适应宽度 |
| [`../../web/frontend/src/components/snapshot/PatternListCard.vue`](../../web/frontend/src/components/snapshot/PatternListCard.vue) | 样板卡：主输出格子网格（窄屏视口固定 440px，桌面端随卡片拉伸填满、与合并卡等高；数量超出向下滚动），点击格子弹出计划合成卡；提交成功后向上冒泡 `submitted` 事件 |
| [`../../web/frontend/src/components/snapshot/CraftPlanModal.vue`](../../web/frontend/src/components/snapshot/CraftPlanModal.vue) | 计划合成弹窗：z 轴更高 + 背景模糊；输入需求以 MC 格子展示（角标 = 候选 × 倍率 × 批次）；「开始合成」提交 `POST /craft`，成功展示受理回执（commandId + 时间），契约错误码直接透出 |
| [`../../web/frontend/src/components/snapshot/CommandList.vue`](../../web/frontend/src/components/snapshot/CommandList.vue) | 合成命令列表（嵌入视图，无卡片外壳）：由状态卡下半内嵌使用，栏目标题与计数由宿主提供；命令历史倒序展示（最新在上，最多完整显示 2 条，超出滚动），状态徽标（排队中/已提交/失败）、模组回执与失败原因；过长回执 / 物品 id 经 HoverDetail 悬浮展开；每行删除按钮（契约 v0.9，两段式确认）emit `delete` 由页面调 API 后刷新——删 PENDING 即撤销、删终态仅清理历史 |
| **`pages/`** — 页面 | |
| [`../../web/frontend/src/pages/LoginPage.vue`](../../web/frontend/src/pages/LoginPage.vue) | 登录页：用户名/密码表单，unauthorized → 「用户名或密码错误」，成功后回跳 `?redirect=` |
| [`../../web/frontend/src/pages/GridSelectPage.vue`](../../web/frontend/src/pages/GridSelectPage.vue) | 选择页：网格卡片栅格，10s 轮询 + SSE 事件即时刷新 |
| [`../../web/frontend/src/pages/GridDetailPage.vue`](../../web/frontend/src/pages/GridDetailPage.vue) | 详情页：首行「状态·CPU·命令」合并卡与样板卡横向对半分（合并卡内 CPU 上、命令下），库存卡整行（固定视口滚动）；快照与命令列表各 5s 轮询 + SSE 事件即时刷新，离线提示条；快照返回 `grid_locked` 时以解锁门替代内容，解锁成功刷新快照与列表 |

## 当前状态与后续

- [x] 网格选择页（列表 + 预留密码栏位，本期不校验）。
- [x] 网格详情页：状态+CPU 合并卡内嵌合成命令列表（与样板卡横向对半分；卡内 CPU 在上、命令在下，两列表各最多完整显示 2 条、超出滚动）、样板格子系统（格子区窄屏固定 440px、桌面端拉伸填满与合并卡等高，点击弹计划合成卡）、库存格子系统（整行，固定视口滚动）。
- [x] MC 风格物品格（`ItemSlot`）：悬浮名称提示（全局 tooltip 层）、紧凑数量角标、按 id 着色的方块占位，贴图 URL 预留。
- [x] 全站默认 150% 放缩（`style.css` 的 `--app-zoom`）。
- [x] mock 数据源与 `VITE_USE_MOCK` 切换（默认实时数据；`VITE_USE_MOCK=true` 或 `npm run dev:mock` 启用 mock）；契约错误码直接透出为错误文案。
- [x] 后端转发已实现，默认即走真实请求（dev 代理 `vite.config.js` 的 `/api` → `http://localhost:8080`），响应形状与 mock 一致。
- [x] 合成命令接线：计划合成弹窗提交 `POST /craft`（含受理回执与错误码透出）+ 详情页合成命令卡（`GET /commands` 5s 轮询、状态徽标、倒序展示），mock 层模拟模组队列语义。
- [x] 账号认证接入：登录页 + 全局路由守卫（未登录重定向并带回跳）+ 顶栏用户名/登出 + 401 统一处理（会话过期自动跳登录页）；原选择页 `PasswordField` 预留栏位移除（每网络口令语义由全局账号取代）。
- [x] 登出即跳登录页（roadmap 已完成项）：顶栏登出清会话后立即 `router.push({name:'login'})`（不带 `redirect`，主动登出不回跳）；`useAuth` 不依赖 router。
- [x] 网格密码接入（契约 v0.3）：`GridCard` 锁定徽标与按钮文案切换；详情页 `UnlockGate` 解锁门（错误码直出、MOCK 模式显示演示密码）；解锁成功刷新快照与网格列表；mock 层主基地网格预置密码 `1234`、锁定网格 snapshot/craft/commands 抛 `grid_locked`。
- [x] 实时推送接入（roadmap 已完成项）：`src/api/events.js` 用 EventSource 订阅 `/api/events`，选页/详情页收到事件立即刷新；**5s/10s 轮询保留作兜底**（SSE 断线/未登录自动降级，EventSource 浏览器自带重连）。
- [x] 术语统一为 grid（契约 v0.5，模组 0.0.8）：`fetchNetworks`→`fetchGrids`、`unlockNetwork`→`unlockGrid`、`useNetworks`→`useGrids`（读 `data.grids`）、路由 `/networks/:accessId`→`/grids/:accessId`、mock 常量 `NETWORKS`/`NETWORK_PASSWORDS`→`GRIDS`/`GRID_PASSWORDS`、错误码 `network_locked`/`network_not_found`→`grid_locked`/`grid_not_found`；组件 props `network`→`grid`。
- [x] 物品中文名（契约 v0.6，roadmap.md §4 已完成）：`src/lib/itemName.js` 引入，快照条目按模组 `nameKey` 查官方中文语言表（minecraft/ae2，`src/data/lang/`），查不到回退 id 启发式与英文友好名；库存/样板/命令/计划合成各组件与 mock fixtures 已接线。
- [x] 物品贴图接入（契约 v0.7，roadmap.md §5 已完成）：`api/index.js` 的 `iconUrlOf()` 按 key id 构造 `/api/icons/{ns}/{path}.png`（id 段不满足模组字符集或 mock 模式返回空串），库存/样板/命令/计划合成各组件传入 `ItemSlot` 的 `iconUrl`；`loading="lazy"` + `@error` 回退占位方块，5s 后有界重试 2 次（配合模组 404 惰性补齐渐进出现），iconUrl 变化时重试状态归零。专用服务器（图标恒 404）与 mock 模式下保持占位方块。
- [x] 详情页布局改版：状态·CPU·命令合并卡与样板卡横向对半分（卡内 CPU 在上、命令在下，各最多 2 条滚动）；新增 `HoverDetail` 截断文本悬浮详情（向下小箭头 + 信息窗口，**悬停箭头触发**，窗口对齐鼠标、移入保持、移出即关），应用于控制器 / accessId / 命令回执与物品 id；样板卡格子区窄屏 440px 固定、桌面端拉伸填满（库存维持 480px 不变）；原独立 `CommandsCard` 更名为 `CommandList` 嵌入视图。顺带修复 mock `submitCraft` 对响应式 key 对象 `structuredClone` 失败导致 mock 模式无法提交合成的问题。
- [x] 命令删除接线（契约 v0.9）：`CommandList` 每行删除按钮两段式确认（点一次变「确认删除」再点一次生效，blur 复位），PENDING 行提示「删除即撤销」；`StatusCpuCard` 冒泡 `delete`、详情页调 `deleteCommand` 后刷新命令列表（`command_not_found` 视为已删除静默通过）；mock 层模拟删除与 50 条终态自动修剪。
- [x] 最后在线展示（契约 v0.10）：网格摘要新增 `lastSeenAt` 透传，`GridCard` 离线网格显示「最后在线」行（在线网格与旧后端缺字段时省略）；mock 层在线网格推导为当前时间、离线网格用 fixture 值。
- [x] 数据源默认实时 + 徽标精简（2026-11）：`USE_MOCK` 默认翻转为 false——前端启动即走真实请求，启用 mock 需参数 `VITE_USE_MOCK=true`（`npm run dev:mock` + `.env.mock`）；顶栏与详情页仅 mock 模式显示「MOCK 数据」徽标，实时数据不再显示徽标；`GridCard` 离线网格以「最后在线」单行替代原「数据截至」+「最后在线」两行；选择页移除手动刷新按钮（SSE 实时推送 + 轮询兜底已覆盖）。

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
npm run dev       # 本地开发，默认 5173（实时数据，需后端 8080）
npm run dev:mock  # mock 数据源演示模式（--mode mock，注入 VITE_USE_MOCK=true）
npm run build     # 生产构建
```

## 相关文档

- [架构总览](../architecture.md)
- [Web API 契约](../contracts/web-api.md)
- [后续迭代路线](../roadmap.md)（craft 接线、认证、中文名、物品贴图已实施）
- [后端](../backend/index.md)
- [数据契约](../contracts/index.md)
