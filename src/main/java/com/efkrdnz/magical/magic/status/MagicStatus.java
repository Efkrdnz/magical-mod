package com.efkrdnz.magical.magic.status;

/** Skill-applied statuses that gameplay hooks consult. Kept on a per-entity attachment. */
public enum MagicStatus {
    /** Yaw/pitch frozen; attacks outside a frontal cone deal nothing. */
    FACING_PINNED(true),
    /** Removed from the fight in both directions. */
    EXILED(true),
    /** Legs belong to the caster. */
    PUPPETED(true),
    /** Cannot cast. */
    SILENCED(true),
    /** Melee only lands at point blank. */
    REACH_CLAMPED(true),
    /** Feet pinned. */
    ROOTED(true),
    /** Marked through walls for the caster's team. */
    REVEALED(false),
    /** Cannot receive mob effects. */
    UNHALLOWED(false),
    /** Forced to target the source. */
    TAUNTED(false),
    /** External impulses cancelled. */
    IMMOVABLE(false),
    /** No AI / no actions until damaged. */
    ASLEEP(true),
    /** Cannot sprint; leaves a scent trail. */
    HARRIED(false),
    /** Exposure counter under a gaze. */
    GAZE(false),
    /** Scale changed. */
    COMPRESSED(false),
    /** Armour-scaling burn. */
    BRANDED(false),
    /** Plague carrier. */
    INFECTED(false),
    /** Dazzled: mobs drop targets, players see white. */
    DAZZLED(true),
    /** Transmuted into a harmless shape: cannot attack, cast or use items. */
    POLYMORPHED(true);

    private final boolean syncToVictim;

    MagicStatus(boolean syncToVictim) {
        this.syncToVictim = syncToVictim;
    }

    /** Whether the affected player's client needs to know (input locks, HUD). */
    public boolean syncToVictim() {
        return syncToVictim;
    }
}
