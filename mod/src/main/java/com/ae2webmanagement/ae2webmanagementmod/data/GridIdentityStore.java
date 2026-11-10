package com.ae2webmanagement.ae2webmanagementmod.data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import com.ae2webmanagement.ae2webmanagementmod.grid.ControllerAnchor;
import com.ae2webmanagement.ae2webmanagementmod.grid.GridIdentity;

/**
 * 网格接入身份表（挂 Overworld 的 {@link SavedData}，对应 docs/design.md §4.1）。
 *
 * <p>以 ME 控制器锚点（维度 + 坐标）为唯一键，一个锚点对应一条身份记录。
 * 多个 Access Block 接入同一控制器网格时共享同一身份。
 *
 * <h3>存储格式（NBT）</h3>
 * <pre>{@code
 * identities: [ {accessId, level, pos, token, displayName, createdAt, lastSeenAt, passwordHash}, … ]
 * }</pre>
 */
public final class GridIdentityStore extends SavedData {

    private static final String SAVED_DATA_KEY = "ae2web_identities";
    private static final String KEY_IDENTITIES = "identities";
    private static final String KEY_ACCESS_ID = "accessId";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_POS_X = "posX";
    private static final String KEY_POS_Y = "posY";
    private static final String KEY_POS_Z = "posZ";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_LAST_SEEN_AT = "lastSeenAt";
    private static final String KEY_PASSWORD_HASH = "passwordHash";

    private final Map<UUID, GridIdentity> identities = new HashMap<>();
    private final Map<String, UUID> anchorToId = new HashMap<>();

    public GridIdentityStore() {
    }

    /**
     * 获取存档级身份表。身份表始终挂载在 Overworld，不随接入方块所在维度分裂。
     */
    public static GridIdentityStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GridIdentityStore::new, GridIdentityStore::load),
                SAVED_DATA_KEY);
    }

    /** 从 NBT 加载已有数据。 */
    public static GridIdentityStore load(CompoundTag tag, HolderLookup.Provider registries) {
        GridIdentityStore store = new GridIdentityStore();
        ListTag list = tag.getList(KEY_IDENTITIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID accessId = entry.getUUID(KEY_ACCESS_ID);
            ResourceLocation levelId = ResourceLocation.parse(entry.getString(KEY_LEVEL));
            ResourceKey<Level> levelKey = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, levelId);
            BlockPos pos = new BlockPos(
                    entry.getInt(KEY_POS_X),
                    entry.getInt(KEY_POS_Y),
                    entry.getInt(KEY_POS_Z));
            String token = entry.getString(KEY_TOKEN);
            String displayName = entry.getString(KEY_DISPLAY_NAME);
            long createdAt = entry.getLong(KEY_CREATED_AT);
            long lastSeenAt = entry.getLong(KEY_LAST_SEEN_AT);
            // 旧版本存档无 passwordHash 字段：getString 对缺失键返回空串 = 未设密码
            String passwordHash = entry.getString(KEY_PASSWORD_HASH);

            GridIdentity identity = new GridIdentity(
                    accessId, levelKey, pos, token, displayName, createdAt, lastSeenAt, passwordHash);
            store.identities.put(accessId, identity);
            store.anchorToId.put(anchorKey(levelKey, pos), accessId);
        }
        return store;
    }

    /** 把全部身份记录序列化为 SavedData NBT。 */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (GridIdentity id : identities.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_ACCESS_ID, id.accessId());
            entry.putString(KEY_LEVEL, id.controllerLevel().location().toString());
            entry.putInt(KEY_POS_X, id.controllerPos().getX());
            entry.putInt(KEY_POS_Y, id.controllerPos().getY());
            entry.putInt(KEY_POS_Z, id.controllerPos().getZ());
            entry.putString(KEY_TOKEN, id.token());
            entry.putString(KEY_DISPLAY_NAME, id.displayName());
            entry.putLong(KEY_CREATED_AT, id.createdAt());
            entry.putLong(KEY_LAST_SEEN_AT, id.lastSeenAt());
            entry.putString(KEY_PASSWORD_HASH, id.passwordHash() == null ? "" : id.passwordHash());
            list.add(entry);
        }
        tag.put(KEY_IDENTITIES, list);
        return tag;
    }

    // ==================== 业务方法 ====================

    /**
     * 为给定的控制器锚点登记（或返回已有）身份。
     *
     * @param anchor 控制器锚点
     * @return 已有的身份（{@link Result#ALREADY_EXISTS}）或新创建的身份（{@link Result#CREATED}）
     */
    public Result addOrGet(ControllerAnchor anchor) {
        String key = anchorKey(anchor.levelKey(), anchor.pos());
        UUID existing = anchorToId.get(key);
        if (existing != null) {
            return Result.alreadyExists(identities.get(existing));
        }

        long now = System.currentTimeMillis();
        UUID accessId = UUID.randomUUID();
        String token = UUID.randomUUID().toString().replace("-", "");
        String displayName = "Grid @ " + anchor.pos().toShortString();

        GridIdentity identity = new GridIdentity(
                accessId, anchor.levelKey(), anchor.pos(), token, displayName, now, now, "");
        identities.put(accessId, identity);
        anchorToId.put(key, accessId);
        setDirty();
        return Result.created(identity);
    }

    /** 按 accessId 查找。 */
    public Optional<GridIdentity> get(UUID accessId) {
        return Optional.ofNullable(identities.get(accessId));
    }

    /** 按控制器锚点查找已注册网格的 accessId（不创建新记录）。 */
    public Optional<UUID> findAccessId(ControllerAnchor anchor) {
        return Optional.ofNullable(anchorToId.get(anchorKey(anchor.levelKey(), anchor.pos())));
    }

    /**
     * 设置或清除网格密码（主线程调用）。
     *
     * @param accessId 网格接入 ID（须已注册）
     * @param password 密码原文；{@code null} 表示清除密码（恢复公开）
     * @return 更新后的身份记录；网格不存在时为空
     */
    public Optional<GridIdentity> setPassword(UUID accessId, String password) {
        GridIdentity identity = identities.get(accessId);
        if (identity == null) {
            return Optional.empty();
        }
        String hash = password == null ? "" : GridPasswords.hash(password);
        GridIdentity updated = new GridIdentity(
                identity.accessId(), identity.controllerLevel(), identity.controllerPos(),
                identity.token(), identity.displayName(), identity.createdAt(), identity.lastSeenAt(), hash);
        identities.put(accessId, updated);
        setDirty();
        return Optional.of(updated);
    }

    /**
     * 修改网格显示名（接入方块 GUI 改名，主线程调用）。
     *
     * <p>只改 {@code displayName}，其余字段（accessId / 锚点 / token / 密码哈希等）
     * 保持不变；写后标记 SavedData 脏以便存档。网页展示名来自本字段，
     * 改名后 {@link com.ae2webmanagement.ae2webmanagementmod.snapshot.SnapshotManager#updateIdentity}
     * 同步镜像缓存，让 HTTP 列表无需等待下一个采样周期即可看到新名。</p>
     *
     * @param accessId    网格接入 ID（须已注册）
     * @param newName     新显示名（非空；长度校验由调用方 {@code AccessBlockEntity} 负责）
     * @return 更新后的身份记录；网格不存在时为空
     */
    public Optional<GridIdentity> rename(UUID accessId, String newName) {
        GridIdentity identity = identities.get(accessId);
        if (identity == null) {
            return Optional.empty();
        }
        GridIdentity updated = new GridIdentity(
                identity.accessId(), identity.controllerLevel(), identity.controllerPos(),
                identity.token(), newName, identity.createdAt(), identity.lastSeenAt(), identity.passwordHash());
        identities.put(accessId, updated);
        setDirty();
        return Optional.of(updated);
    }

    /**
     * 更新网格最近一次在线时间（快照循环回写，主线程调用；docs/design.md §4.1）。
     *
     * <p>快照循环每轮对「在线」网格调用：写入当前采样时间，让该字段保持
     * 「最近一次在线时间」的语义（离线轮不调用，字段保持最后一次在线值）；
     * 随 {@code setDirty} 持久化到 SavedData，跨重启仍正确。照 {@link #rename}
     * 的不可变 record 模式重建记录；返回的新记录由调用方覆盖回身份镜像缓存。</p>
     *
     * @param accessId  网格接入 ID（须已注册）
     * @param timestamp 本次快照采样时间（epoch 毫秒）
     * @return 更新后的身份记录；网格不存在时为空
     */
    public Optional<GridIdentity> touchLastSeen(UUID accessId, long timestamp) {
        GridIdentity identity = identities.get(accessId);
        if (identity == null) {
            return Optional.empty();
        }
        GridIdentity updated = new GridIdentity(
                identity.accessId(), identity.controllerLevel(), identity.controllerPos(),
                identity.token(), identity.displayName(), identity.createdAt(), timestamp, identity.passwordHash());
        identities.put(accessId, updated);
        setDirty();
        return Optional.of(updated);
    }

    /** 全部身份（只读）。 */
    public Collection<GridIdentity> getAll() {
        return Collections.unmodifiableCollection(identities.values());
    }

    // ==================== 工具 ====================

    /** 组合“维度|坐标”字符串，作为控制器锚点的内存索引键。 */
    private static String anchorKey(ResourceKey<Level> levelKey, BlockPos pos) {
        return levelKey.location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ==================== 结果 ====================

    public sealed interface Result permits Created, AlreadyExists {
        GridIdentity identity();

        static Result created(GridIdentity id) { return new Created(id); }
        static Result alreadyExists(GridIdentity id) { return new AlreadyExists(id); }
    }

    public record Created(GridIdentity identity) implements Result {}
    public record AlreadyExists(GridIdentity identity) implements Result {}
}
