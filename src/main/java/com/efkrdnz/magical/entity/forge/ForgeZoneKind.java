package com.efkrdnz.magical.entity.forge;

/**
 * Which lingering Art a {@code ForgeZoneEntity} is. The ordinal is synced and saved, and the
 * per-tick behaviour is looked up from it in {@code forge.art.ForgeZones} - never captured in a
 * lambda the entity would have to carry across a save and reload.
 */
public enum ForgeZoneKind {
    /** Fire · Spin - a flame disc that bites every few ticks. */
    PYRE_WHEEL,
    /** Fire · Slam - a burning ring that keeps re-igniting whatever stands in it. */
    EMBER_ERUPTION,
    /** Frost · Spin - one hard freeze the moment it opens, then a fading ring. */
    GLACIAL_HALO,
    /** Void · Spin - a gravity well that drags bodies in and drinks their mana. */
    NULL_ORBIT,
    /** Void · Slam - a rift that collapses on a delay. */
    ABYSS_RIFT,
    /** Radiant · Spin - heals allies, burns the undead. */
    SANCTIFIED_RING,
    /** Venom · Wave - a poison cloud left in the crescent's wake. */
    MIASMA_CLOUD,
    /** Gale · Spin - pulls inward, then throws everything out. */
    CYCLONE,
    /** Storm · Rising - the delayed bolt that follows a launch. */
    SKYFALL_BOLT;

    private static final ForgeZoneKind[] VALUES = values();

    public static ForgeZoneKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PYRE_WHEEL;
    }
}
