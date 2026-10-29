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
import com.ae2webmanagement.ae2webmanagementmod.network.NetworkIdentity;

/**
 * 网络接入身份表（挂 Overworld 的 {@link SavedData}，对应 docs/design.md §4.1）。
 *
 * <p>以 ME 控制器锚点（维度 + 坐标）为唯一键，一个锚点对应一条身份记录。
 * 多个 Access Block 接入同一控制器网络时共享同一身份。
 *
 * <h3>存储格式（NBT）</h3>
 * <pre>{@code
 * identities: [ {accessId, level, pos, token, displayName, createdAt, lastSeenAt}, … ]
 * }</pre>
 */
public final class NetworkIdentityStore extends SavedData {

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

    private final Map<UUID, NetworkIdentity> identities = new HashMap<>();
    private final Map<String, UUID> anchorToId = new HashMap<>();

    public NetworkIdentityStore() {
    }

    /**
     * 获取存档级身份表。身份表始终挂载在 Overworld，不随接入方块所在维度分裂。
     */
    public static NetworkIdentityStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NetworkIdentityStore::new, NetworkIdentityStore::load),
                SAVED_DATA_KEY);
    }

    /** 从 NBT 加载已有数据。 */
    public static NetworkIdentityStore load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkIdentityStore store = new NetworkIdentityStore();
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

            NetworkIdentity identity = new NetworkIdentity(
                    accessId, levelKey, pos, token, displayName, createdAt, lastSeenAt);
            store.identities.put(accessId, identity);
            store.anchorToId.put(anchorKey(levelKey, pos), accessId);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (NetworkIdentity id : identities.values()) {
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
        String displayName = "Network @ " + anchor.pos().toShortString();

        NetworkIdentity identity = new NetworkIdentity(
                accessId, anchor.levelKey(), anchor.pos(), token, displayName, now, now);
        identities.put(accessId, identity);
        anchorToId.put(key, accessId);
        setDirty();
        return Result.created(identity);
    }

    /** 按 accessId 查找。 */
    public Optional<NetworkIdentity> get(UUID accessId) {
        return Optional.ofNullable(identities.get(accessId));
    }

    /** 全部身份（只读）。 */
    public Collection<NetworkIdentity> getAll() {
        return Collections.unmodifiableCollection(identities.values());
    }

    // ==================== 工具 ====================

    private static String anchorKey(ResourceKey<Level> levelKey, BlockPos pos) {
        return levelKey.location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ==================== 结果 ====================

    public sealed interface Result permits Created, AlreadyExists {
        NetworkIdentity identity();

        static Result created(NetworkIdentity id) { return new Created(id); }
        static Result alreadyExists(NetworkIdentity id) { return new AlreadyExists(id); }
    }

    public record Created(NetworkIdentity identity) implements Result {}
    public record AlreadyExists(NetworkIdentity identity) implements Result {}
}
