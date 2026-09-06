package com.efkrdnz.magical.magic;

import net.minecraft.nbt.CompoundTag;

public record MagicSkillTuning(int damage, int speed, int size, int duration, int efficiency) {
    public static final int MIN = -3;
    public static final int MAX = 3;
    public static final int ABSOLUTE_MAX = 11;
    public static final MagicSkillTuning DEFAULT = new MagicSkillTuning(0, 0, 0, 0, 0);

    public MagicSkillTuning {
        damage = clamp(damage);
        speed = clamp(speed);
        size = clamp(size);
        duration = clamp(duration);
        efficiency = clamp(efficiency);
    }

    public MagicSkillTuning adjust(MagicTuningStat stat, int delta) {
        return adjust(stat, delta, MAX);
    }

    public MagicSkillTuning adjust(MagicTuningStat stat, int delta, int limit) {
        int boundedLimit = clampLimit(limit);
        return switch (stat) {
            case DAMAGE -> withLimit(damage + delta, speed, size, duration, efficiency, boundedLimit);
            case SPEED -> withLimit(damage, speed + delta, size, duration, efficiency, boundedLimit);
            case SIZE -> withLimit(damage, speed, size + delta, duration, efficiency, boundedLimit);
            case DURATION -> withLimit(damage, speed, size, duration + delta, efficiency, boundedLimit);
            case EFFICIENCY -> withLimit(damage, speed, size, duration, efficiency + delta, boundedLimit);
        };
    }

    public MagicSkillTuning clampToLimit(int limit) {
        return withLimit(damage, speed, size, duration, efficiency, clampLimit(limit));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("damage", damage);
        tag.putInt("speed", speed);
        tag.putInt("size", size);
        tag.putInt("duration", duration);
        tag.putInt("efficiency", efficiency);
        return tag;
    }

    public static MagicSkillTuning load(CompoundTag tag) {
        return new MagicSkillTuning(
                tag.getInt("damage"),
                tag.getInt("speed"),
                tag.getInt("size"),
                tag.getInt("duration"),
                tag.getInt("efficiency"));
    }

    private static int clamp(int value) {
        return Math.max(-ABSOLUTE_MAX, Math.min(ABSOLUTE_MAX, value));
    }

    private static MagicSkillTuning withLimit(int damage, int speed, int size, int duration, int efficiency, int limit) {
        return new MagicSkillTuning(
                Math.max(-limit, Math.min(limit, damage)),
                Math.max(-limit, Math.min(limit, speed)),
                Math.max(-limit, Math.min(limit, size)),
                Math.max(-limit, Math.min(limit, duration)),
                Math.max(-limit, Math.min(limit, efficiency)));
    }

    private static int clampLimit(int limit) {
        return Math.max(MAX, Math.min(ABSOLUTE_MAX, limit));
    }
}
