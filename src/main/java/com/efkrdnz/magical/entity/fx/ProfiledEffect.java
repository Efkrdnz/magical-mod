package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import net.minecraft.world.phys.Vec3;

/** What the generic renderer shell needs from any profile-driven entity. */
public interface ProfiledEffect {
    int skillIndex();

    /** Age in ticks (partial tick added by the renderer). */
    int effectAge();

    /** Total life in ticks, or 0 for unbounded. */
    int effectLife();

    /** 0..1 lifecycle for the shaders; defaults to age/life. */
    default float effectPhase() {
        int life = effectLife();
        return life <= 0 ? 0.5F : Math.max(0.0F, Math.min(1.0F, effectAge() / (float) life));
    }

    int effectSeed();

    /** Travel / facing direction (zero = none). */
    Vec3 effectDirection();

    /** Extra scalar the painters may use (charge, integrity, sweep bearing). */
    default float effectValue() {
        return 0.0F;
    }

    /** Which silhouette subset to draw (see {@link com.efkrdnz.magical.magic.visual.Silhouette#forModes}). */
    default int effectDrawMode() {
        return 0;
    }

    /** Radius override for silhouettes that scale with the stat (0 = use profile sizes). */
    default float effectRadius() {
        return 0.0F;
    }

    /** Second point for LINK silhouettes, world space (null = none). */
    default Vec3 effectEndPoint() {
        return null;
    }

    /** Synced irregular payload (cell lists, paths) for custom painters; null if none. */
    default net.minecraft.nbt.CompoundTag effectData() {
        return null;
    }

    default VisualProfile profile() {
        return VisualProfiles.byIndex(skillIndex());
    }
}
