package com.efkrdnz.magical.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record SpaceWaypoint(String name, String dimension, int x, int y, int z) {
    public static final int MAX_NAME_LENGTH = 24;

    public static SpaceWaypoint at(String name, String dimension, BlockPos pos) {
        return new SpaceWaypoint(cleanName(name), dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("dimension", dimension);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    public static SpaceWaypoint load(CompoundTag tag) {
        String dimension = tag.getString("dimension");
        if (dimension == null || dimension.isBlank()) {
            dimension = "minecraft:overworld";
        }
        return new SpaceWaypoint(
                cleanName(tag.getString("name")),
                dimension,
                tag.getInt("x"),
                tag.getInt("y"),
                tag.getInt("z"));
    }

    public static String cleanName(String raw) {
        String cleaned = raw == null ? "" : raw.strip();
        if (cleaned.isEmpty()) {
            return "Waypoint";
        }
        return cleaned.length() <= MAX_NAME_LENGTH ? cleaned : cleaned.substring(0, MAX_NAME_LENGTH);
    }
}
