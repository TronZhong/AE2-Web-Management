package com.ae2webmanagement.ae2webmanagementmod.account;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;

import com.ae2webmanagement.ae2webmanagementmod.data.AccountStore;

/**
 * 账号镜像缓存（主线程写 / HTTP 线程读，对应 {@code SnapshotManager} 的镜像模式）。
 *
 * <p>本地数据 API 的 {@code GET /accounts} 与网格列表 owner 名字解析都在 HTTP
 * 线程执行，不能触碰 {@code SavedData}；本类用 {@link ConcurrentHashMap} 镜像
 * {@link AccountStore} 的记录：服务端启动时全量恢复，GUI 注册、玩家改名后由
 * 主线程立即刷新，HTTP 读取无需等待主线程调度。</p>
 */
public final class AccountManager {

    private final Map<UUID, AccountStore.AccountEntry> cache = new ConcurrentHashMap<>();

    /** 服务端启动时从 Overworld 的账号表恢复镜像。 */
    public void start(MinecraftServer server) {
        this.cache.clear();
        AccountStore.get(server.overworld()).getAll()
                .forEach(entry -> this.cache.put(entry.playerUuid(), entry));
    }

    /** 服务端停止时清空镜像；持久化数据保留在 SavedData 中。 */
    public void stop() {
        this.cache.clear();
    }

    /** 主线程写入/覆盖一条账号记录（注册或改名后调用）。 */
    public void put(AccountStore.AccountEntry entry) {
        this.cache.put(entry.playerUuid(), entry);
    }

    /** 按 playerUuid 读取账号记录（HTTP 线程可调用）。 */
    public Optional<AccountStore.AccountEntry> get(UUID playerUuid) {
        return Optional.ofNullable(this.cache.get(playerUuid));
    }

    /**
     * 按 playerUuid 解析玩家名（HTTP 线程可调用）。
     *
     * @return 玩家名；账号未知时为 {@code null}（调用方据此省略字段）
     */
    public String nameOf(UUID playerUuid) {
        AccountStore.AccountEntry entry = this.cache.get(playerUuid);
        return entry == null ? null : entry.playerName();
    }

    /** 全部账号记录的只读副本（HTTP 线程可调用）。 */
    public Collection<AccountStore.AccountEntry> getAll() {
        return List.copyOf(this.cache.values());
    }
}
