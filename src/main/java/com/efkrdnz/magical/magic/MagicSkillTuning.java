package com.efkrdnz.magical.magic;

import net.minecraft.nbt.CompoundTag;

/**
 * How one skill is customised: five stats, and a budget to divide between them.
 *
 * <p>The budget is the number that grows with proficiency - {@code MAX} at the start, climbing to
 * {@code ABSOLUTE_MAX}. It used to be a cap on each stat separately, which meant a proficient player
 * simply put the maximum into all five and every build was the same build. It is now the total, so
 * putting a point somewhere is choosing not to put it somewhere else.
 *
 * <p>A single stat may still take the whole budget: the rule is that you cannot have everything at
 * once, not that you cannot specialise.
 */
public record MagicSkillTuning(int damage, int speed, int size, int duration, int efficiency) {

    /** The budget at zero proficiency. */
    public static final int MAX = 3;

    /** And its ceiling, however much proficiency is earned past that. */
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

    /**
     * Points spent out of the budget.
     *
     * <p>Only what has been added counts. Taking a stat below zero weakens the skill and buys
     * nothing back - it is a way to say "this one does not matter to me", not a source of points.
     */
    public int spent() {
        return Math.max(0, damage) + Math.max(0, speed) + Math.max(0, size)
                + Math.max(0, duration) + Math.max(0, efficiency);
    }

    /** Whether this allocation is legal for someone holding {@code budget} points. */
    public boolean fitsIn(int budget) {
        return spent() <= budget;
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
