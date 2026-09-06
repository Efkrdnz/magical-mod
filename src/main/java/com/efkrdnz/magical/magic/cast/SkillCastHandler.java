package com.efkrdnz.magical.magic.cast;

/**
 * One skill's server behaviour. Registered next to the skill in a {@code MagicCastContent*} file;
 * the dispatcher (MagicCastingService.castResolved) does the validation, mana, aim, cooldown and
 * bookkeeping and calls {@link #cast} in between.
 */
public interface SkillCastHandler {
    CastResult cast(CastContext ctx);

    /** Pressing the slot only prints a hint; the real cast arrives through a hold/charge payload. */
    default boolean holdGated() {
        return false;
    }

    default String holdHintKey() {
        return "message.magical.skill_locked";
    }

    /** The handler runs BEFORE stats/mana are resolved and manages its own costs (kept services). */
    default boolean selfManaged() {
        return false;
    }

    /** Range of the aim ray resolved into the CastContext. */
    default double aimRange() {
        return 16.0D;
    }

    /** Entity pick tolerance around the aim ray (0 = blocks only). */
    default double aimTolerance() {
        return 1.6D;
    }

    /** Drop a block-less aim point to the ground below (placed skills). */
    default boolean aimDropsToGround() {
        return false;
    }

    /** Ticks of counterable windup (0 = not counterable). */
    default int counterWindowTicks() {
        return 0;
    }

    default MobCastProfile mob() {
        return MobCastProfile.NONE;
    }

    default TuningView tuning() {
        return TuningView.DEFAULT;
    }

    /** The skill may be sustained by holding the slot key (deluge jet, arcane grasp sneak-hold). */
    default boolean holdable() {
        return false;
    }
}
