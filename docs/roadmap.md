# 后续迭代路线（规划）

> 文档首页：[`index.md`](index.md) › 设计

本页收录**已拍板、待实施**的迭代项与决策理由。与交接会话记录冲突时，以本页 + 实际代码为准并回写修正。动手实现某一节前，先按仓库规范同步受影响的契约与组件文档；动模组代码的批次统一把 `mod_version` 递增（当前 `0.0.6`）。

## 1. 迭代顺序总览

| # | 主题 | 改动面 | 状态 |
| --- | --- | --- | --- |
| 1 | 前端接线：`/craft` 提交 + 命令状态列表 | 仅前端 | 已完成（2026-11） |
| 2 | 用户认证：**账号制先行** | 后端 + 前端，模组零改动 | 已完成（2026-11） |
| 2.5 | 网格密码：账号 + 每网格可选密码双层 | 模组 + 契约 + 后端 + 前端 | 已完成（2026-11，模组 0.0.6） |
| 3 | 模组 GUI 迭代 + `nameKey` 字段 | 模组 + 契约 + 后端 DTO + 前端 | 已拍板 |
| 4 | 物品图标：模组客户端渲染导出 | 模组 + 契约 + 后端 + 前端 | 已拍板 |
| 5 | 模组其他遗留（命令取消、`lastSeenAt`） | 模组 + 契约 | 已拍板 |

> #3、#4、#5 都动模组代码，可按批次合并提交，每批一个主题、`mod_version` 递增 1。

## 2. 用户认证与网络访问密码

### 背景

- 机器级认证（模组↔后端 `X-Shared-Secret`）已实现；用户级认证未实现。
- `NetworkIdentity.token` 注册时已生成（`NetworkIdentityStore.createIdentity`，随机 UUID hex）并存入 `ae2web_identities.dat`，但模组 GUI 无任何展示出口（`AccessScreen` 只有标题、状态文字、Register 按钮），后端也从未校验它。
- `displayName` 注册时写死为 `"Network @ x,y,z"`，GUI 无法改名。

### 决策：账号制先行（模组端零改动）

- 语义：密码是**后端账号密码**。后端引入 Sa-Token（`sa-token-spring-boot4-starter`，接入时实测 Spring Boot 4.1 兼容性）+ H2 账户表（`domain` / `repository` 占位已预留），登录后可见全部已注册网络。
- 模组端不动，`token` 字段继续闲置（语义仍是 design.md §4.1 的"后端持有"）。
- 前端 `PasswordField`（选择页预留栏位）语义回填为登录凭证，具体 UI 形态（登录页 vs 内嵌栏位）实施时定。

### 网格密码增强（已完成，2026-11，路线 B）

在账号登录之上叠加**每网格可选密码**（第二层访问控制），用户确认的最终形态：

- **设置**：游戏内接入方块 GUI（`EditBox` 掩码输入 + 设置/清除按钮），可选——不设密码即公开（已登录即可进）；密码明文不出模组，身份表只存 PBKDF2 哈希（`GridPasswords`，`pbkdf2:<迭代>:<盐B64>:<哈希B64>`）。
- **模组 0.0.6 改动**：`NetworkIdentity` 增加 `passwordHash` 组件；GUI 密码提交走 C2S 自定义 payload `GridPasswordPayload`（字符串无法走 containerData 的 int 通道，NeoForge 处理器默认主线程执行，满足 SavedData 写入要求）；`SnapshotManager` 维护密码哈希镜像缓存供 HTTP 线程读取；`LocalDataServer` 列表输出 `hasPassword` 并新增 `POST /networks/{id}/verify-password`。
- **后端**：网络摘要计算 `unlocked`（未设密码恒 true；设了密码按账号查 `account_grid_access` 表）；snapshot/craft/commands 转发前做 `ensureAccessible` 检查（锁定 → `403 network_locked`）；新增 `POST /api/networks/{id}/unlock`（转发模组 verify-password，204 后按账号记录解锁，跨会话生效）；后端观察到密码被清除（hasPassword=false）时撤销该网络全部解锁记录。旧版模组无 hasPassword 字段按公开处理（`ModNetworkSummaryResponse` 可空 `Boolean` 承接）。
- **前端**：`GridCard` 锁定徽标；详情页对 `network_locked` 渲染 `UnlockGate` 解锁门（替代错误横幅），解锁成功刷新快照与列表；mock 层主基地网络预置演示密码 `1234`。

### 每网络配对令牌（暂缓，实施前与用户再确认场景）

- 适用场景：多玩家 / 对外部署，需要按网络隔离授权；网格密码已覆盖个人自用与小型多人场景，随机 token 语义暂无需求。
- 若实施：`AccessScreen` 展示已注册网络的 `accessId` + token（一键复制），token 字符串同步需要新的通道（`AccessMenu` 已扩为 `SimpleContainerData(2)`，仍只能传 int，token 需自定义 payload，可复用 `GridPasswordPayload` 的模式）；「重置 token」按钮 + 后端侧同步失效。
- token 流向约定：模组 `/networks` 可把 token 输出给后端（模组口仅 127.0.0.1 + 共享密钥可达，泄漏面可控），由**后端持有并校验，不下发给前端**。
- 改名（`displayName`）与 token 展示属同一批 GUI 改动，实施时一起做。

## 3. 物品中文名称

### 背景

- 模组快照只传 AEKey 通用 NBT（如 `{"#t":"ae2:i","id":"minecraft:iron_block"}`）；前端 `friendlyName()`（`src/lib/format.js`）只做英文 id 首字母大写，无中文数据源。
- **关键技术事实**：Minecraft 服务端没有多语言——`Language.getInstance()` 只加载 en_us，模组端调 `getHoverName().getString()` 等永远解析出英文，"模组直接传出中文名"不可行。

### 决策：模组传 `nameKey`（翻译键），前端查中文语言表

| 端 | 改动 |
| --- | --- |
| 模组 | `SnapshotManager` 采集处对物品 key 取对应 `Item#getDescriptionId()`（纯字符串拼接，如 `block.minecraft.stone` / `item.ae2.controller`），`Snapshot.StackEntry` 增加可选 `nameKey` 字段并在 HTTP JSON 输出；流体等其他 AEKey 类型的翻译键来源实施时按 AE2 源码确认 |
| 后端 | `StackEntry` DTO 透传加字段（`JsonNode key` 不变） |
| 前端 | 引入官方 `zh_cn.json` 语言表（minecraft 的取自 client.jar，ae2 的取自其 Modrinth jar），按 `nameKey` 查表得中文名；查不到回退现有英文友好名 |
| 契约 | **实现前先更新** `docs/contracts/index.md`：`inventory[]` / `patterns[]` 各 stack 条目新增可选 `nameKey`（向后兼容） |

### 过渡方案（可选，零模组改动）

前端按 id 启发式拼翻译键（`block.<ns>.<path>` / `item.<ns>.<path>` 双尝试）+ 语言表查询。vanilla 几乎全对、模组物品大多遵循约定，但存在反例；仅作过渡，最终以模组 `nameKey` 为准。

## 4. 物品图标

### 背景

前端 `ItemSlot.vue` 已预留 `iconUrl` prop 与 `pixelated` 渲染分支，当前用按 id 着色的方块占位；只差贴图来源。

### 决策：模组客户端渲染导出（契合"模组是数据出口"架构）

1. 模组客户端（`AE2WebManagementClient`）遍历快照中出现过的物品 key，用 `Minecraft.getInstance().getItemRenderer()` 离屏渲染 16×16 PNG，缓存到 `config/ae2webmanagement/icons/<namespace>/<path>.png`（方块物品得到真实物品栏 3D 图标）；
2. `LocalDataServer` 增加 `GET /icons/{namespace}/{path}.png`（仅回环，与现有 API 同级）；
3. 后端增加图标转发端点（如 `GET /api/icons/{namespace}/{path}.png`，可不做 per-network）；
4. 前端把 `iconUrl` 接到该地址，实现前同步更新契约文档。

### 备选（不推荐，仅记录理由）

- 前端静态贴图包（构建期从 jar 抽 PNG）：vanilla 方块只有平面贴图、观感打折，modded 需手工维护；
- 第三方贴图 CDN：只覆盖 vanilla、依赖外网，不可控。

## 5. 模组其他遗留

- **命令删除/取消 API**：`DELETE /networks/{accessId}/commands/{commandId}`。当前 PENDING 命令无法撤销、会一直等网络上线后自动提交；有了它前端命令列表才敢提供取消按钮。实现前更新契约文档。
- **`lastSeenAt` 随快照回写**：design.md §4.1 已标注，快照循环 `tick` 时更新身份表。

## 相关文档

- [技术设计](design.md)（已收敛的权威设计）
- [架构总览](architecture.md)
- [数据契约](contracts/index.md)
- [模组](mod/index.md) · [后端](backend/index.md) · [前端](frontend/index.md)
