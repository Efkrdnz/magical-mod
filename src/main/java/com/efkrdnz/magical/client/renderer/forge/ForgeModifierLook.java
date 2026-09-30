package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ModifierStack;

import org.joml.Matrix4f;

/**
 * The runes that show on the blade itself, rather than in what it sheds.
 *
 * <p>A weapon's runes are most of what makes one forged blade different from another, and until
 * now a player could read none of them off a swing. Most runes show as matter ({@code ForgeMatter}
 * gives each its own); these change the steel. <b>REACH</b> is already the size of every form, and
 * a thrown wave's width. <b>CHORUS</b>, which lands a strike again, draws the blade again: a second
 * blade beside the first, a little behind it, one per copy of the rune, alternating sides - so two
 * copies of CHORUS is a triple slash. <b>GUARD</b>, the one defensive rune, draws a pale crescent on
 * the wielder's side of the blade, heavier with each copy.
 *
 * <p>Both are measured against the blade, not the screen. Copies drawn on the blade's own circle and
 * only turned along it lay over the blade and read as nothing; a ward drawn as a soft haze round the
 * blade read as smoke. A copy stands off the plane of the swing and a ward stands inside the arc, so
 * each is a shape of its own.
 */
public final class ForgeModifierLook {

    /** How far each chorus copy stands off the swing's plane, in blade thicknesses, and how far behind it runs. */
    private static final float GHOST_GAP = 1.0f;
    private static final float GHOST_LAG = 5.0f;
    /** How strong the first chorus copy is against the blade; each further one is weaker. */
    private static final float GHOST_ALPHA = 0.6f;
    /** Where the ward's crescent stands, as a fraction of the blade's radius, and how thick it is against the blade. */
    private static final float WARD_RADIUS = 0.6f;
    private static final float WARD_THICKNESS = 0.55f;
    private static final float WARD_PER_STACK = 0.45f;
    /**
     * A ward is the same cold steel whatever element the blade burns with: drawn in the blade's own
     * pale colour it merged into the blade's lip and read as a brighter edge rather than a guard.
     */
    private static final ForgePalette WARD = new ForgePalette(0xB8D4FF, 0x5F86CC, 0xF2F8FF);
    /** How much broader the ward round a lance is than the lance's own glow. */
    private static final float LANCE_WARD_SPREAD = 2.2f;

    private ForgeModifierLook() {}

    /** How many copies of the blade CHORUS draws beside it. */
    public static int ghosts(ModifierStack mods) {
        return mods.stacks(ForgeModifierKind.CHORUS);
    }

    /** How strong GUARD's ward is, against the blade itself; zero without the rune. */
    public static float ward(ModifierStack mods) {
        return Math.min(1.0f, mods.stacks(ForgeModifierKind.GUARD) * WARD_PER_STACK);
    }

    /**
     * How far chorus copy {@code k} (1-based) stands off the swing's plane, in blade thicknesses:
     * the first two on either side, each further pair further out.
     */
    public static float ghostOffset(int k) {
        return (k % 2 == 1 ? 1.0f : -1.0f) * GHOST_GAP * ((k + 1) / 2);
    }

    /** How many degrees chorus copy {@code k} runs behind the blade: a strike landed again, later. */
    public static float ghostLag(int k) {
        return GHOST_LAG * k;
    }

    /** The ward's crescent for a blade: inside it, toward the wielder, and thinner. */
    public static Sweep wardSweep(Sweep blade) {
        return new Sweep(blade.plane(), blade.radius() * WARD_RADIUS, blade.thickness() * WARD_THICKNESS,
                blade.fromDegrees(), blade.toDegrees());
    }

    /** Draws the runes that show on an arc of blade, over the blade that has already been drawn. */
    public static void adorn(ForgeStroke stroke, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha,
            ModifierStack mods, float[] eye) {
        if (alpha <= 0.0f || mods.isEmpty()) {
            return;
        }
        int ghosts = ghosts(mods);
        float[] normal = ForgeRibbon.planar(sweep.plane(), 0.0f, 0.0f, 1.0f);
        float behind = -Math.signum(sweep.toDegrees() - sweep.fromDegrees());
        for (int k = 1; k <= ghosts; k++) {
            float off = ghostOffset(k) * sweep.thickness();
            Matrix4f beside = new Matrix4f(pose).translate(normal[0] * off, normal[1] * off, normal[2] * off);
            Sweep copy = sweep.shifted(ghostLag(k) * behind);
            float strength = alpha * GHOST_ALPHA / k;
            ForgeRibbon.arc(stroke, beside, copy, palette, strength);
            ForgeRibbon.sheath(stroke, beside, copy, palette, strength, eye[0], eye[1], eye[2]);
        }
        float ward = ward(mods);
        if (ward > 0.0f) {
            Sweep inner = wardSweep(sweep);
            ForgePalette pale = WARD;
            ForgeRibbon.arc(stroke, pose, inner, pale, alpha * ward);
            ForgeRibbon.sheath(stroke, pose, inner, pale, alpha * ward, eye[0], eye[1], eye[2]);
        }
    }

    /** The ward round a straight lance: a thrust has no arc to echo, but it can still be guarded. */
    public static void adornLance(ForgeStroke stroke, Matrix4f pose, float length, float halfWidth,
            ForgePalette palette, float alpha, ModifierStack mods, float[] eye) {
        float ward = ward(mods);
        if (alpha <= 0.0f || ward <= 0.0f) {
            return;
        }
        ForgeRibbon.lanceSheath(stroke, pose, length, halfWidth * LANCE_WARD_SPREAD, WARD,
                alpha * ward, eye[0], eye[1], eye[2]);
    }
}
