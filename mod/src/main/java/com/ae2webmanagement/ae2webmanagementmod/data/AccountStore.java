package com.ae2webmanagement.ae2webmanagementmod.data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 网页账号表（挂 Overworld 的 {@link SavedData}，docs/design.md §4.4，roadmap.md §11）。
 *
 * <p>账号只能<strong>在游戏内接入方块 GUI 注册</strong>（网页端无注册口）；以
 * {@code playerUuid} 为唯一键，玩家名仅作展示/网页登录别名，随玩家改名跟随更新。
 * 密码只存 PBKDF2 哈希（复用 {@link GridPasswords} 的工具与存储格式
 * {@code pbkdf2:<迭代>:<盐B64>:<哈希B64>}），明文不出模组。</p>
 *
 * <h3>存储格式（NBT）</h3>
 * <pre>{@code
 * accounts: [ {playerUuid, playerName, passwordHash, createdAt}, … ]
 * }</pre>
 */
public final class AccountStore extends SavedData {

    private static final String SAVED_DATA_KEY = "ae2web_accounts";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_PLAYER_UUID = "playerUuid";
    private static final String KEY_PLAYER_NAME = "playerName";
    private static final String KEY_PASSWORD_HASH = "passwordHash";
    private static final String KEY_CREATED_AT = "createdAt";

    private final Map<UUID, AccountEntry> accounts = new HashMap<>();

    /**
     * 一条账号记录。
     *
     * @param playerUuid   绑定玩家的 UUID（唯一键；离线模式由名字派生，同样稳定）
     * @param playerName   玩家最新名字（网页登录别名，随改名跟随更新）
     * @param passwordHash 密码的 PBKDF2 哈希（{@link GridPasswords#hash} 产物）
     * @param createdAt    注册时间（epoch 毫秒）
     */
    public record AccountEntry(UUID playerUuid, String playerName, String passwordHash, long createdAt) {
    }

    /** 创建空表；通常由 Minecraft 的 SavedData 工厂调用。 */
    public AccountStore() {
    }

    /**
     * 获取存档级账号表。账号表始终挂载在 Overworld，不随维度分裂。
     */
    public static AccountStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AccountStore::new, AccountStore::load),
                SAVED_DATA_KEY);
    }

    /** 从 NBT 加载已有数据。 */
    public static AccountStore load(CompoundTag tag, HolderLookup.Provider registries) {
        AccountStore store = new AccountStore();
        ListTag list = tag.getList(KEY_ACCOUNTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID playerUuid = entry.getUUID(KEY_PLAYER_UUID);
            String playerName = entry.getString(KEY_PLAYER_NAME);
            String passwordHash = entry.getString(KEY_PASSWORD_HASH);
            long createdAt = entry.getLong(KEY_CREATED_AT);
            store.accounts.put(playerUuid, new AccountEntry(playerUuid, playerName, passwordHash, createdAt));
        }
        return store;
    }

    /** 把全部账号序列化为 SavedData NBT。 */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (AccountEntry entry : accounts.values()) {
            CompoundTag compound = new CompoundTag();
            compound.putUUID(KEY_PLAYER_UUID, entry.playerUuid());
            compound.putString(KEY_PLAYER_NAME, entry.playerName());
            compound.putString(KEY_PASSWORD_HASH, entry.passwordHash());
            compound.putLong(KEY_CREATED_AT, entry.createdAt());
            list.add(compound);
        }
        tag.put(KEY_ACCOUNTS, list);
        return tag;
    }

    // ==================== 业务方法（主线程调用） ====================

    /**
     * 注册账号（游戏内 GUI 提交，主线程调用）。
     *
     * <p>同一 {@code playerUuid} 重复注册返回空；密码须先满足
     * {@link GridPasswords#isValidPassword}（非法输入一律拒绝，静默忽略）。</p>
     *
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家当前名字
     * @param password   密码原文（只在此处短暂存在，落盘前已哈希）
     * @return 注册成功的账号记录；已存在或密码非法时为空
     */
    public Optional<AccountEntry> register(UUID playerUuid, String playerName, String password) {
        if (playerUuid == null || this.accounts.containsKey(playerUuid)) {
            return Optional.empty();
        }
        if (!GridPasswords.isValidPassword(password)) {
            return Optional.empty();
        }
        AccountEntry entry = new AccountEntry(
                playerUuid, playerName, GridPasswords.hash(password), System.currentTimeMillis());
        this.accounts.put(playerUuid, entry);
        setDirty();
        return Optional.of(entry);
    }

    /** 按 playerUuid 查找账号。 */
    public Optional<AccountEntry> get(UUID playerUuid) {
        return Optional.ofNullable(this.accounts.get(playerUuid));
    }

    /**
     * 更新玩家名（玩家改名跟随，{@code PlayerLoggedInEvent} 主线程调用）。
     *
     * <p>名字仅在变化时写入；账号不存在时返回空。</p>
     *
     * @param playerUuid 玩家 UUID
     * @param playerName 最新玩家名
     * @return 更新后的账号记录；账号不存在或名字未变化时为空
     */
    public Optional<AccountEntry> updateName(UUID playerUuid, String playerName) {
        AccountEntry existing = this.accounts.get(playerUuid);
        if (existing == null || existing.playerName().equals(playerName)) {
            return Optional.empty();
        }
        AccountEntry updated = new AccountEntry(
                playerUuid, playerName, existing.passwordHash(), existing.createdAt());
        this.accounts.put(playerUuid, updated);
        setDirty();
        return Optional.of(updated);
    }

    /** 全部账号（只读）。 */
    public Collection<AccountEntry> getAll() {
        return Collections.unmodifiableCollection(this.accounts.values());
    }
}
