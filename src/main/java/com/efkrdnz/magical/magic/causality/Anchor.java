package com.efkrdnz.magical.magic.causality;

import net.minecraft.nbt.CompoundTag;

/**
 * The mark, and the only door out of the wielder own causality.
 *
 * <p>The design doc is explicit that this Authority starts at home: what you take, what you deal,
 * what you cast. Reaching into somebody else is a separate act with a separate price, and the mark
 * is that act made into an object. Every anchored {@link Cause}, {@link Condition} and
 * {@link Scope} is dead weight on the board until one of these is out there, which the board says
 * plainly rather than leaving the wielder to wonder why nothing fires.
 *
 * <p>It expires, it is one at a time, and it does not cross a dimension. Those three together are
 * what stop a wielder marking a boss on Monday and collecting on Friday: the anchor is a commitment
 * made during the fight, not a bookmark.
 */
public final class Anchor {

    /** How long a mark lasts unless it is replaced or its bearer dies. */
    public static final int TICKS = 900;

    /** How far the wielder can reach to place one. */
    public static final double REACH = 28.0D;

    private int entityId = -1;
    private String dimension = "";
    private long until = Long.MIN_VALUE;

    public int entityId() {
        return entityId;
    }

    public String dimension() {
        return dimension;
    }

    public long until() {
        return until;
    }

    /** True while the mark is still set, whatever has since happened to whoever is wearing it. */
    public boolean set(long now) {
        return entityId >= 0 && now < until;
    }

    public int ticksLeft(long now) {
        return set(now) ? (int) Math.min(Integer.MAX_VALUE, until - now) : 0;
    }

    public void place(int id, String dimensionKey, long now) {
        entityId = id;
        dimension = dimensionKey == null ? "" : dimensionKey;
        until = now + TICKS;
    }

    public void clear() {
        entityId = -1;
        dimension = "";
        until = Long.MIN_VALUE;
    }

    public void copyFrom(Anchor other) {
        entityId = other.entityId;
        dimension = other.dimension;
        until = other.until;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", entityId);
        tag.putString("dim", dimension);
        tag.putLong("until", until);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null) {
            clear();
            return;
        }
        entityId = tag.contains("id") ? tag.getInt("id") : -1;
        dimension = tag.getString("dim");
        until = tag.contains("until") ? tag.getLong("until") : Long.MIN_VALUE;
    }
}
