package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.forge.chain.ForgeGrade;

import net.minecraft.util.Mth;

/**
 * What a weapon's own numbers do to the strike it throws.
 *
 * <p>A forged weapon carries an element, a form, a grade, a quality and a temper, and until now the
 * only two of those the picture knew about were the element and the form. Two swings looked
 * identical whether they came off a crude first attempt or a divine masterwork, which made half the
 * forge worth nothing to look at.
 *
 * <p>The element already owns colour and ornament and the form owns the silhouette, so those are
 * taken. Grade gets brightness and quality gets breadth: independent dimensions, so the pair says
 * more than either, and a player learns to read both off one cut. Both are deliberately gentle -
 * brightness past the shader's ceiling clips to white and loses the element, and breadth is the
 * drawing claiming a size the hit shape does not have.
 */
public final class ForgeWeaponLook {

    /** Brightness of the worst grade and the best, either side of an ordinary one at 1. */
    private static final float DIMMEST = 0.62f;
    private static final float BRIGHTEST = 1.28f;

    /** How far a flawless weapon widens its cut, and how far a shoddy one narrows it. */
    private static final float NARROWEST = 0.86f;
    private static final float WIDEST = 1.18f;

    private static final int MAX_QUALITY = 100;

    private ForgeWeaponLook() {}

    /**
     * How hard a strike off a weapon of this grade burns, as a multiplier on its alpha.
     *
     * <p>An ordinal rather than the enum: it arrives over the wire, and an ordinal a client does
     * not recognise has to draw something rather than nothing, so anything out of range is taken as
     * the middle of the ladder.
     */
    public static float emission(int gradeOrdinal) {
        int count = ForgeGrade.values().length;
        if (gradeOrdinal < 0 || gradeOrdinal >= count) {
            return Mth.lerp(0.5f, DIMMEST, BRIGHTEST);
        }
        return Mth.lerp(gradeOrdinal / (float) (count - 1), DIMMEST, BRIGHTEST);
    }

    /**
     * How wide a weapon of this quality cuts, as a multiplier on the strike's half-width.
     *
     * <p>Centred so that an ordinary fifty leaves the blade at exactly the size its form asked for;
     * the scale only ever reads as better or worse than ordinary, never as a different move.
     */
    public static float breadth(int quality) {
        float t = Mth.clamp(quality, 0, MAX_QUALITY) / (float) MAX_QUALITY;
        return Mth.lerp(t, NARROWEST, WIDEST);
    }
}
