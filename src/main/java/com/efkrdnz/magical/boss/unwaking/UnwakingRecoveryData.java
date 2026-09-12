package com.efkrdnz.magical.boss.unwaking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

public final class UnwakingRecoveryData extends SavedData {
    public static final String NAME = "magical_unwaking";
    public record ReturnPoint(UUID run, Vec3 position, float yaw, float pitch, float flySpeed, boolean flying) {}
    private final Map<UUID, ReturnPoint> returns = new LinkedHashMap<>();
    private final java.util.Set<UUID> victories = new java.util.HashSet<>();

    public static UnwakingRecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(UnwakingRecoveryData::new, UnwakingRecoveryData::load), NAME);
    }

    public ReturnPoint point(UUID player) { return returns.get(player); }
    public void remember(UUID player, ReturnPoint point) { returns.put(player, point); setDirty(); }
    public void forget(UUID player) { returns.remove(player); setDirty(); }
    public boolean hasPending() { return !returns.isEmpty(); }
    public boolean victoryPending(UUID player) { return victories.contains(player); }
    public void qualifyVictory(UUID player) { victories.add(player); setDirty(); }
    public void clearVictory(UUID player) { victories.remove(player); setDirty(); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        returns.forEach((player, point) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putUUID("Run", point.run());
            entry.putDouble("X", point.position().x); entry.putDouble("Y", point.position().y); entry.putDouble("Z", point.position().z);
            entry.putFloat("Yaw", point.yaw()); entry.putFloat("Pitch", point.pitch()); entry.putFloat("FlySpeed", point.flySpeed());
            entry.putBoolean("Flying", point.flying());
            list.add(entry);
        });
        tag.put("Returns", list);
        ListTag awards = new ListTag();
        victories.stream().sorted().forEach(id -> { CompoundTag entry = new CompoundTag(); entry.putUUID("Player", id); awards.add(entry); });
        tag.put("Victories", awards);
        return tag;
    }

    static UnwakingRecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        UnwakingRecoveryData data = new UnwakingRecoveryData();
        for (Tag raw : tag.getList("Victories", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("Player")) data.victories.add(entry.getUUID("Player"));
        }
        for (Tag raw : tag.getList("Returns", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Player") || !entry.hasUUID("Run")) continue;
            Vec3 pos = new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"));
            if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) continue;
            float speed = entry.getFloat("FlySpeed");
            data.returns.put(entry.getUUID("Player"), new ReturnPoint(entry.getUUID("Run"), pos,
                    entry.getFloat("Yaw"), entry.getFloat("Pitch"), Float.isFinite(speed) && speed > 0 ? speed : 0.05F, entry.getBoolean("Flying")));
        }
        return data;
    }

    /** Vanilla logs some IO failures instead of propagating them: read back before any transfer. */
    public void flushVerified(MinecraftServer server) throws IOException {
        CompoundTag expected = save(new CompoundTag(), server.registryAccess());
        setDirty();
        server.overworld().getDataStorage().saveAndJoin();
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME + ".dat");
        CompoundTag actual = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompound("data");
        if (!expected.equals(actual)) throw new IOException("Unwaking return markers did not persist");
    }
}
