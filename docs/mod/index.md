# 模组（NeoForge）

> 文档首页：[`index.md`](../index.md) › 组件 › 模组

游戏内唯一能读写 AE2 状态的部分。本工程是 NeoForge 1.21.1，已接入 AE2 19.2.18，具备接入方块、控制器定位、Overworld 身份表、4 秒快照采集、本机 HTTP API 和合成命令队列。

## 概览

| 项 | 值 |
| --- | --- |
| 加载器 | NeoForge |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| AE2 | 19.2.18（Modrinth Maven，`maven.modrinth:ae2`） |
| Gradle 插件 | NeoGradle ModDev 2.0.148 |
| 映射 | Parchment `2024.11.17`（MC 1.21.1） |
| Java | 21 |
| 版本 | `0.1.0` |
| mod_id / 包名 | `ae2webmanagement` / `com.ae2webmanagement.ae2webmanagementmod` |

## 目录结构与源码映射

| 路径 | 说明 |
| --- | --- |
| [`../../mod/build.gradle`](../../mod/build.gradle) | 构建脚本：Modrinth 仓库、AE2 依赖声明、运行配置、jar 元数据 |
| [`../../mod/gradle.properties`](../../mod/gradle.properties) | 所有版本号与 mod 元数据（含 `ae2_version`） |
| [`../../mod/settings.gradle`](../../mod/settings.gradle) | Gradle 插件仓库与工具链解析 |
| [`../../mod/src/main/java/.../AE2WebManagement.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagement.java) | 模组主类：注册配置、接入方块，启动/停止快照、命令和本地 API；`AccountManager` 账号镜像随服务端启停恢复/清空、`PlayerLoggedInEvent` 跟随更新账号表玩家名（改名同步，0.1.0） |
| [`../../mod/src/main/java/.../Config.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/Config.java) | 运行时配置（HTTP 监听地址/端口、共享密钥、快照间隔） |
| [`../../mod/src/main/java/.../AE2WebManagementClient.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/AE2WebManagementClient.java) | 仅客户端：配置界面注册（专用服不加载） |
| **`grid/`** — AE2 网格工具（可复用） | |
| [`../../mod/src/main/java/.../grid/ControllerAnchor.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/grid/ControllerAnchor.java) | 控制器位置锚点（值对象：`levelKey` + `pos`） |
| [`../../mod/src/main/java/.../grid/ControllerLocator.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/grid/ControllerLocator.java) | 从 `IGrid` 定位 ME 控制器（`getOwner() instanceof ControllerBlockEntity`） |
| **`access/`** — 接入方块（网格节点 + 控制器定位） | |
| [`../../mod/src/main/java/.../access/AccessBlock.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessBlock.java) | 接入方块的方块类，右击打开 GUI |
| [`../../mod/src/main/java/.../access/AccessBlockEntity.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessBlockEntity.java) | 接入方块的方块实体：托管网格节点、定位控制器、缓存锚点；GUI 权限状态机 `resolveView`（无控制器 → 无账号 → 网格未注册 → 他人只读 / 本人管理，roadmap §11）与全部写操作（注册账号 / 注册网格 / 改名 / 改密 / 删除）的服务端校验与执行；改密结果写入反馈状态（成功 / 旧密码不匹配 / 新密码非法）并随每次尝试递增序号，供 GUI 边沿检测 |
| [`../../mod/src/main/java/.../access/AccessMenu.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessMenu.java) | 注册菜单：`containerData[0]` GUI 视图状态机（`VIEW_*`）、`containerData[1]` 注册反馈（`STATUS_*`）、`containerData[2]` 网格密码标记、`containerData[3]` 改密反馈打包值 `(seq<<2)|code`（`PASSWORD_CHANGE_*`），随 `broadcastChanges` 每 tick 从方块实体实时刷新；各写操作 payload 入口（`handleAccountRegister` / `handleRegister` / `handleRename` / `handleChangePassword` / `handleDelete`）；身份信息变化经 `GridIdentityPayload` 推给客户端 |
| [`../../mod/src/main/java/.../access/AccountRegisterPayload.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccountRegisterPayload.java) | C2S 自定义 payload：GUI 提交网页账号注册（密码 + 确认在客户端比对，服务端只收一个密码），PBKDF2 哈希入账号表；服务端主线程处理并校验 containerId |
| [`../../mod/src/main/java/.../access/GridRegisterPayload.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/GridRegisterPayload.java) | C2S 自定义 payload：GUI 提交注册网格（名称 + 密码，都可空 = 默认名 + 公开网格），服务端校验后创建身份记录并记 `ownerUuid` |
| [`../../mod/src/main/java/.../access/GridRenamePayload.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/GridRenamePayload.java) | C2S 自定义 payload：GUI 提交新显示名（displayName 改名），服务端校验 owner 后主线程处理并校验 containerId |
| [`../../mod/src/main/java/.../access/GridChangePasswordPayload.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/GridChangePasswordPayload.java) | C2S 自定义 payload：GUI 提交改密（旧 + 新密码；未设密码时旧密码须为空串，新密码空串 = 清除），取代已删除的 `GridPasswordPayload` |
| [`../../mod/src/main/java/.../access/GridDeletePayload.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/GridDeletePayload.java) | C2S 自定义 payload：GUI 提交删除网格（二次确认在客户端），服务端主线程级联清理（身份 + 快照 + 命令记录 + 镜像） |
| [`../../mod/src/main/java/.../access/GridIdentityPayload.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/GridIdentityPayload.java) | S2C 自定义 payload：服务端推送 accessId/displayName/控制器位置 + `hasAccount`/`isOwner`/`ownerName` 给客户端 GUI 展示（字段超 6 个，`StreamCodec.ofMember` 手写 write/read） |
| [`../../mod/src/main/java/.../access/client/AccessScreen.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/client/AccessScreen.java) | 客户端界面：AE2 风格（面板复用 `ae2:textures/guis/background.png` 九宫格绘制、输入框用 AE2 `AETextField`、按钮用 AE2 `AE2Button`、配色取 AE2 官方调色板）；按 `containerData` 视图状态机切换表单——账号注册（密码 + 确认）、网格注册（名称 + 密码）、他人网格只读（显示创建者名，不显示 accessId）、本人网格管理（主页：改名 / 删除两段式确认 + 改密入口；子页：旧 + 新 + 确认新密码，成功自动回主页）；输入框聚焦时吞掉除 ESC 外的按键（防 E 误关 GUI），点击输入框外取消焦点恢复普通按键；身份信息展示沿用 `GridIdentityPayload`（接入 ID 仅 owner 可见） |
| [`../../mod/src/main/java/.../access/AccessRegistration.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/access/AccessRegistration.java) | 方块/物品/方块实体注册、AE2 capability 注册、payload 注册（5 个 C2S：账号注册 / 网格注册 / 改名 / 改密 / 删除；1 个 S2C：`GridIdentityPayload`）、创造标签页 |
| **`grid/`** — 网格身份 | |
| [`../../mod/src/main/java/.../grid/GridIdentity.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/grid/GridIdentity.java) | 网格接入身份（数据记录：`accessId` + 控制器锚点 + token + `passwordHash` + `lastSeenAt` + `ownerUuid`（0.1.0，可空 = 无主）） |
| [`../../mod/src/main/java/.../data/GridIdentityStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/GridIdentityStore.java) | Overworld 网格身份表（`ae2web_identities.dat`），`register` 注册网格（记 `ownerUuid`）、`remove` 删除网格、`setPassword` 写密码哈希、`rename` 改显示名、`touchLastSeen` 快照循环回写最近在线时间；NBT 缺 `ownerUuid` 字段按无主处理（不做迁移） |
| **`account/` + 账号表** — 网页账号（0.1.0，roadmap §11） | |
| [`../../mod/src/main/java/.../data/AccountStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/AccountStore.java) | Overworld 账号表（`ae2web_accounts.dat`）：`playerUuid` 唯一键 + PBKDF2 哈希（复用 `GridPasswords` 格式），`register` 注册（已存在或密码非法返回空）、`updateName` 玩家改名跟随（账号只能在游戏内注册，网页端无注册口） |
| [`../../mod/src/main/java/.../account/AccountManager.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/account/AccountManager.java) | 账号镜像缓存：主线程写 / HTTP 线程读（对应 `SnapshotManager` 的镜像模式），服务端启动恢复、注册/改名后由主线程刷新；HTTP 线程经它读账号列表（`GET /accounts`）与解析网格 owner 名字 |
| **`snapshot/`** — 快照 | |
| [`../../mod/src/main/java/.../snapshot/Snapshot.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/Snapshot.java) | 库存、合成 CPU、样板摘要、采样时间和在线态 |
| [`../../mod/src/main/java/.../snapshot/SnapshotManager.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/snapshot/SnapshotManager.java) | 主线程 4 秒采样、内存缓存、离线保留；快照循环对在线网格回写身份表 `lastSeenAt`（0.0.14）；密码哈希镜像（`getPasswordHash`/`updatePasswordHash`，HTTP 线程可读）；改名后 `updateIdentity` 即时刷新身份镜像；`removeGrid` 删除网格时级联清理内存快照与镜像（0.1.0） |
| [`../../mod/src/main/java/.../data/SnapshotStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/SnapshotStore.java) | Overworld 快照表（`ae2web_snapshots.dat`），含库存、CPU 和样板摘要；`remove` 删除网格快照（0.1.0 级联清理） |
| **`command/`** — 合成命令 | |
| [`../../mod/src/main/java/.../command/CraftCommand.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CraftCommand.java) | 合成意图记录：目标 `AEKey`、数量、状态、结果 |
| [`../../mod/src/main/java/.../command/CommandState.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CommandState.java) | 命令状态枚举（PENDING/SUBMITTED/FAILED） |
| [`../../mod/src/main/java/.../command/CommandManager.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/command/CommandManager.java) | HTTP 入队与主线程调度：持久化、AE2 异步重算、提交；`delete` 同步派发主线程原子删除（撤销 PENDING / 清理历史）、终态写入后按网格自动修剪历史；`removeGridCommands` 删除网格时清理其全部命令记录（含 PENDING，0.1.0 级联清理） |
| [`../../mod/src/main/java/.../data/CommandStore.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/CommandStore.java) | Overworld 命令表（`ae2web_commands.dat`），支持 `remove` 删除记录 |
| [`../../mod/src/main/java/.../data/GridPasswords.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/data/GridPasswords.java) | 网格密码 PBKDF2 哈希工具（纯 JDK；`pbkdf2:<迭代>:<盐B64>:<哈希B64>`，上限 64 字符）；0.1.0 起账号密码同样复用本工具 |
| **`http/`** — 本地数据 API | |
| [`../../mod/src/main/java/.../http/LocalDataServer.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/http/LocalDataServer.java) | 仅回环绑定；`/health`、`/grids` 网格/快照查询（含 `hasPassword`、`lastSeenAt`、0.1.0 起 `ownerUuid`/`ownerName`）、合成命令投递与查询、`DELETE /grids/{id}/commands/{commandId}` 删除命令记录、`verify-password` 密码校验、`/accounts` 账号列表（0.1.0，含哈希，供后端同步后本地校验登录）、`/icons` 图标静态文件服务（只读磁盘，含路径穿越防护与 404 惰性补齐） |
| **`icon/`** — 图标导出（公共侧） | |
| [`../../mod/src/main/java/.../icon/IconPaths.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/icon/IconPaths.java) | 图标缓存根目录（`config/ae2webmanagement/icons/<分辨率>`，当前 `64x64`；分辨率编入路径，调整 `ICON_RESOLUTION` 后旧缓存自动失效），服务端与客户端共用 |
| [`../../mod/src/main/java/.../icon/IconExporter.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/icon/IconExporter.java) | 图标导出网关：客户端注册实现，专用服务器保持 no-op；HTTP 线程经它做 404 惰性补齐 |
| **`client/icon/`** — 客户端图标渲染 | |
| [`../../mod/src/main/java/.../client/icon/IconService.java`](../../mod/src/main/java/com/ae2webmanagement/ae2webmanagementmod/client/icon/IconService.java) | 仅客户端：作业队列 + 每帧预算的离屏渲染（64×64 PNG，16 GUI 单位逻辑画布 4× 超采样，复用单个 FBO），物品借 `ItemRenderer`、流体走 `IClientFluidTypeExtensions` 贴图 blit，IO 写盘走单线程 daemon executor；渲染挂点为 `RenderFrameEvent.Post` |
| **配置/元数据** | |
| [`../../mod/src/main/templates/META-INF/neoforge.mods.toml`](../../mod/src/main/templates/META-INF/neoforge.mods.toml) | 模组元数据模板，含 AE2 `required` 依赖 |
| [`../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json`](../../mod/src/main/resources/assets/ae2webmanagement/lang/en_us.json) | 语言文件（含方块名、GUI 文案与配置项翻译） |
| [`../../mod/src/main/resources/assets/ae2webmanagement/lang/zh_cn.json`](../../mod/src/main/resources/assets/ae2webmanagement/lang/zh_cn.json) | 简体中文语言文件 |

## 配置（gradle.properties 关键项）

| 键 | 当前值 | 说明 |
| --- | --- | --- |
| `minecraft_version` | `1.21.1` | 目标 MC 版本 |
| `neo_version` | `21.1.265` | 必须与 MC 版本一致 |
| `ae2_version` | `19.2.18` | AE2 依赖版本（`build.gradle` 引用） |
| `mod_version` | `0.1.0` | 版本号 `0.<次版本>.<补丁>` 三段式（roadmap.md 引言）：补丁位 0–9 每批次递增、满 9 进位次版本（`0.1.9→0.2.0`），次版本不向首位进位 |
| `mod_id` | `ae2webmanagement` | 必须与主类 `MODID` 一致 |
| `mod_name` | `AE2 Web Management` | 展示名 |
| `mod_group_id` | `com.ae2webmanagement.ae2webmanagementmod` | 应与源码基础包一致 |

> 运行时配置（`Config.java`）：`httpBindAddress`（默认 127.0.0.1）、`httpPort`（默认 8090）、`sharedSecret`（默认空 = 关闭校验）、`snapshotIntervalSeconds`（默认 4）、`iconExportEnabled`（默认 true，客户端离屏渲染开关，专用服务器/弱机器可关）、`iconRenderPerFrame`（默认 2，每帧最多渲染的图标数）、`commandHistoryLimit`（默认 50，每网格保留的终态命令历史上限，超出自动删最旧，PENDING 不清理）。

## 构建

```powershell
# 首次需确保 JAVA_HOME 指向 JDK 21
$env:JAVA_HOME = "D:\Jvav\Java\jdk-21"
# 在仓库根目录
.\mod\gradlew.bat -p .\mod build
# 只验证编译可跑
.\mod\gradlew.bat -p .\mod compileJava
```

## 当前状态与后续

- [x] **AE2 接入与身份表**：接入方块作为网格节点定位控制器，身份表统一存 Overworld。
- [x] **快照循环**：每 4 秒读取 `getCachedInventory()` 与 `getCpus()`，离线时保留最后数据。
- [x] **样板摘要采集**：`SnapshotManager.capturePatterns` 遍历 `ICraftingProvider` 采集、按 `IPatternDetails` 去重，随快照缓存/持久化/HTTP 透传。
- [x] **方块与 GUI**：方块使用默认缺失纹理渲染；GUI 含标题、实时状态、身份信息（accessId/displayName/控制器位置）、改名区、注册按钮与网格密码区（设置/清除，掩码输入）。
- [x] **本机 API**：仅绑定回环地址，支持健康检查、网格列表（`/grids`，含 `hasPassword`）、快照查询、命令投递、命令查询与 `verify-password` 密码校验。
- [x] **命令队列**：HTTP 只入队，主线程持久化并用 AE2 异步重算后提交。
- [x] **后端代理**：Spring Boot 已把本机 API 的网格列表 / 快照 / 合成命令转发给网页（`web/backend/`，见 [后端](../backend/index.md)）。
- [x] **网格密码（0.0.6）**：游戏内 GUI 设密（PBKDF2 哈希入身份表）、`verify-password` 校验端点、C2S payload 通道；旧版后端/前端可无缝忽略。
- [x] **GUI 迭代（0.0.7）**：接入方块 GUI 状态实时刷新（`containerData` 随 `broadcastChanges` 每 tick 同步，网格变化无需重开菜单）、展示 accessId/displayName/控制器位置（S2C `GridIdentityPayload`）、displayName 可改名（C2S `GridRenamePayload` + `GridIdentityStore.rename` + 身份镜像缓存即时刷新）；`token` 展示/复制未做（配对令牌方案暂缓，见 [`roadmap.md`](../roadmap.md) §8）。
- [x] **术语统一为 grid（0.0.8）**：`network/` 包改名 `grid/`（`GridIdentity` / `GridIdentityStore`）、本地数据口端点 `/networks`→`/grids`、JSON key `networks`→`grids`、错误码 `network_not_found`→`grid_not_found`；默认显示名由 `"Network @ ..."` 改为 `"Grid @ ..."`。`SavedData` 文件名（`ae2web_identities.dat`）与 NBT、`accessId` 均不变。

- [x] **`nameKey` 字段（`roadmap.md` §4，0.0.9）**：快照 stack 条目附带物品翻译键（物品取 `Item#getDescriptionId()`、流体取 `FluidType#getDescriptionId()`，其余 AEKey 类型从显示名组件提取）；`SnapshotStore` NBT 与 HTTP JSON 透传，旧存档/旧快照无该字段时按「可选」处理。服务端无多语言，不能直接出中文名，由前端按翻译键查中文语言表。
- [x] **物品图标导出 · 模组部分（`roadmap.md` §5，0.0.10）**：客户端 `IconService` 离屏渲染已见物品/流体为 16×16 PNG 写入 `config/ae2webmanagement/icons/`；本地 API 新增 `GET /icons/{namespace}/{path}.png` 静态服务（路径穿越防护、`public, max-age=86400` 缓存头、404 惰性补齐）；渲染挂点为已验证的 `RenderFrameEvent.Post`，专用服务器无客户端、图标端点 404。后端 `/api/icons` 转发与前端 `iconUrl` 接线已于后端/前端批次完成（契约 v0.7，见 [`../backend/index.md`](../backend/index.md) 与 [`../frontend/index.md`](../frontend/index.md)）。
- [x] **图标分辨率 64×64（0.0.11）**：`IconService` 将正交投影固定在 16×16 GUI 单位逻辑画布（原版物品格尺寸），FBO/NativeImage 按 64×64 像素铺设，1 GUI 单位采样 4 像素——修复「仅调大 `ICON_SIZE` 后物品只占画布左上角 1/4」的投影错位；平面贴图最近邻放大 4×，3D 物品获得 4× SSAA。分辨率编入缓存路径（`icons/64x64/`），调整 `IconPaths.ICON_RESOLUTION` 后旧分辨率缓存自动失效。
- [x] **CPU 序号显示（0.0.12）**：AE2 合成 CPU 无法命名，快照 `craftingCpus[]` 移除 `name` 字段（`CpuEntry` / `SavedData` NBT / HTTP JSON 同步移除；旧存档残留 `name` 键读取时忽略）；采集时按游戏内 `CraftingCPUCycler` 同序排序（协处理器↓、存储↓），网页按列表序号显示 `#N`、与游戏内编号一致。
- [x] **命令记录删除与历史自动清理（契约 v0.9，0.0.13）**：`DELETE /grids/{id}/commands/{commandId}` 物理删除命令记录——删 PENDING 即撤销（主线程原子执行：与模拟/提交串行化并取消在途 AE2 计算，保证不会提交），删终态仅清理历史；HTTP 线程经 `MinecraftServer#execute` 同步等待（上限 2s < 后端 5s 读超时），响应即最终结果；每个网格自动保留最近 `commandHistoryLimit`（默认 50）条终态记录（`update` 终态写入后修剪 + 启动时全量修剪一次），PENDING 不自动清理。
- [x] **`lastSeenAt` 随快照回写（契约 v0.10，0.0.14）**：`SnapshotManager.tick` 对在线网格调 `GridIdentityStore.touchLastSeen` 回写最近在线时间（离线轮不更新），返回的新身份覆盖回镜像缓存；`GET /grids` 摘要新增 `lastSeenAt` 字段，旧版后端/前端可忽略。
- [x] **账号制与 GUI 权限分层（`roadmap.md` §11，契约 v0.11，0.1.0）**：账号只能在游戏内接入方块 GUI 注册（网页端无注册口）——`AccountStore`（`ae2web_accounts.dat`，`playerUuid` 唯一键 + PBKDF2 哈希，复用 `GridPasswords` 格式）+ `AccountManager` 镜像（主线程写 / HTTP 线程读）；GUI 按「无控制器 → 无账号（注册表单）→ 网格未注册（名称 + 密码，记 owner）→ 他人网格只读（显示创建者）/ 本人网格管理（改名 / 改密旧+新 / 删除二次确认）」分层（`containerData` 视图状态机每 tick 刷新，原地切换视图）；全部写操作服务端校验「账号存在 + 玩家 UUID == ownerUuid + 网格存在」，不依赖客户端隐藏按钮；删除网格级联清理身份 + 快照（内存与 `SnapshotStore` 落盘）+ 全部命令记录（含 PENDING）+ 身份镜像；本地 API 新增 `GET /accounts`（共享密钥保护，供后端同步哈希后本地校验登录）、`GET /grids` 摘要新增 `ownerUuid`/`ownerName`（无主网格省略）；玩家改名经 `PlayerLoggedInEvent` 跟随更新账号表（网页登录名随后端同步自动跟随）。
- [x] **GUI 体验与风格重构（0.1.1）**：GUI 改为 AE2 风格——面板九宫格复用 `ae2:textures/guis/background.png`、输入框/按钮直接使用 AE2 的 `AETextField`/`AE2Button`（经 AE2 `ScreenStyle.GSON` 以官方调色板构造样式）、文字配色取 AE2 调色板；输入框聚焦时吞掉除 ESC 外的按键（修复输入时按 E 误关 GUI），点击输入框外取消焦点恢复普通按键；改名按钮增加两段式确认（与删除一致）；修改密码移入独立子页（旧 + 新 + 确认新密码，成功自动回主页），改密反馈经 `containerData[3]` 打包值 `(seq<<2)|code` 同步（旧密码不匹配 / 新密码非法不再静默）；顶部信息块重排（维度与坐标分两行、标签本地化、超长省略号），接入 ID 仅 owner 管理视图可见（只读视图隐藏；accessId 本身非凭证，网页端本就向已登录用户下发，见 `GridService`）。

> 实现顺序与完整方案见 [技术设计](../design.md)：锚点身份（控制器 + `SavedData`）→ 注册器方块 → 4 秒快照循环 → 命令队列 → 本地 127.0.0.1 数据口。

## 相关文档

- [技术设计](../design.md)（定稿方案）
- [后续迭代路线](../roadmap.md)（GUI 迭代、`nameKey`、图标导出已实施项）
- [架构总览](../architecture.md)（线程安全、缓存、存档 ID 等设计约束）
- [数据契约](../contracts/index.md)
- [后端](../backend/index.md)
- [快速开始](../getting-started.md)