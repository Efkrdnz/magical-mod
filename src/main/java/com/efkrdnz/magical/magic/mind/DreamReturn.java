package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where a dreamer fell asleep, saved with them, so a dreamer the server loses track of (a logout, a
 * crash) wakes where they lay rather than in somebody's Dreamscape for good.
 */
public final class DreamReturn {
    private String dimension = "";
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public DreamReturn() {}

    public static DreamReturn of(ServerPlayer player) {
        DreamReturn point = new DreamReturn();
        point.dimension = player.level().dimension().location().toString();
        point.x = player.getX();
        point.y = player.getY();
        point.z = player.getZ();
        point.yaw = player.getYRot();
        point.pitch = player.getXRot();
        return point;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    /**
     * The level to wake in, or null when there is none to trust: the level they fell asleep in is
     * gone, or the point was refused on load. The caller then wakes them at the overworld's shared
     * spawn - never at these coordinates in some other level, where they mean nothing.
     */
    public ServerLevel level(MinecraftServer server) {
        ResourceLocation id = dimension.isEmpty() ? null : ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        return tag;
    }

    public static DreamReturn load(CompoundTag tag) {
        DreamReturn point = new DreamReturn();
        point.dimension = tag.getString("dimension");
        point.x = tag.getDouble("x");
        point.y = tag.getDouble("y");
        point.z = tag.getDouble("z");
        point.yaw = tag.getFloat("yaw");
        point.pitch = tag.getFloat("pitch");
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || !Float.isFinite(point.yaw) || !Float.isFinite(point.pitch)) {
            // A save that says NaN or infinity names no place; forget the level so waking falls back to spawn.
            point.dimension = "";
        }
        return point;
    }
}
