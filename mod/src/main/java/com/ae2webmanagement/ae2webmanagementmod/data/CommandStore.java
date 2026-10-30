package com.ae2webmanagement.ae2webmanagementmod.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import com.ae2webmanagement.ae2webmanagementmod.command.CommandState;
import com.ae2webmanagement.ae2webmanagementmod.command.CraftCommand;

/**
 * 待办与已提交合成命令的持久化表，始终挂载在 Overworld。
 *
 * <p>HTTP 线程不能直接访问本表；它只负责把受理请求交给 CommandManager，
 * 由主线程写入这里。
 */
public final class CommandStore extends SavedData {

    private static final String SAVED_DATA_KEY = "ae2web_commands";
    private static final String KEY_COMMANDS = "commands";
    private static final String KEY_ID = "id";
    private static final String KEY_ACCESS_ID = "accessId";
    private static final String KEY_TARGET = "target";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_STATE = "state";
    private static final String KEY_SUBMITTED_AT = "submittedAt";
    private static final String KEY_RESULT = "result";

    private final Map<UUID, CraftCommand> commands = new HashMap<>();

    public CommandStore() {
    }

    /** 获取存档级命令表；始终从 Overworld 读取。 */
    public static CommandStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CommandStore::new, CommandStore::load),
                SAVED_DATA_KEY);
    }

    public static CommandStore load(CompoundTag tag, HolderLookup.Provider registries) {
        CommandStore store = new CommandStore();
        ListTag list = tag.getList(KEY_COMMANDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            CommandState state;
            try {
                state = CommandState.valueOf(entry.getString(KEY_STATE));
            } catch (IllegalArgumentException exception) {
                state = CommandState.FAILED;
            }

            CraftCommand command = new CraftCommand(
                    entry.getUUID(KEY_ID),
                    entry.getUUID(KEY_ACCESS_ID),
                    entry.getCompound(KEY_TARGET),
                    entry.getLong(KEY_AMOUNT),
                    state,
                    entry.getLong(KEY_SUBMITTED_AT),
                    entry.getString(KEY_RESULT));
            store.commands.put(command.id(), command);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CraftCommand command : this.commands.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_ID, command.id());
            entry.putUUID(KEY_ACCESS_ID, command.accessId());
            entry.put(KEY_TARGET, command.target().copy());
            entry.putLong(KEY_AMOUNT, command.amount());
            entry.putString(KEY_STATE, command.state().name());
            entry.putLong(KEY_SUBMITTED_AT, command.submittedAt());
            entry.putString(KEY_RESULT, command.result());
            list.add(entry);
        }
        tag.put(KEY_COMMANDS, list);
        return tag;
    }

    public void put(CraftCommand command) {
        this.commands.put(command.id(), command);
        setDirty();
    }

    public Optional<CraftCommand> get(UUID id) {
        return Optional.ofNullable(this.commands.get(id));
    }

    public Collection<CraftCommand> getAll() {
        return Collections.unmodifiableCollection(this.commands.values());
    }

    public List<CraftCommand> getForNetwork(UUID accessId) {
        List<CraftCommand> result = new ArrayList<>();
        for (CraftCommand command : this.commands.values()) {
            if (command.accessId().equals(accessId)) {
                result.add(command);
            }
        }
        result.sort((left, right) -> Long.compare(left.submittedAt(), right.submittedAt()));
        return result;
    }
}