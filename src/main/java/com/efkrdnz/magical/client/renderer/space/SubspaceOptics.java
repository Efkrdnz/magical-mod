package com.efkrdnz.magical.client.renderer.space;

/**
 * The transparency of the boundary wall, as arithmetic rather than as taste.
 *
 * <p>The problem this solves is that the old dome was, at the same moment, too much and too
 * little. From inside in daylight it laid a pale cyan wash over the entire sky, because a
 * constant alpha on an additive blend covers the most area exactly where the viewer is trying to
 * see; from thirty blocks outside it was invisible, because the same constant alpha spread over
 * the same sphere delivers nothing at the one place a silhouette lives. No single number fixes
 * both: lowering it deepens the second failure and raising it deepens the first.
 *
 * <p>So the alpha is not a number, it is a function of angle. Where the wall faces you it is
 * {@link #A_FACE}, which is almost nothing; where it turns away it climbs to {@link #A_RIM}.
 * That is the ordinary Fresnel arrangement of any real pane of glass, and it is why glass is
 * both see-through and visibly present: you look through the middle of a window and you can
 * still find its edges. {@link #K} sets how tightly the thickening hugs the limb.
 *
 * <p>Every method here is pure arithmetic on doubles and the shader recomputes the same curve in
 * floats. That duplication is deliberate: the shader cannot be unit-tested and this can, so the
 * numbers that decide whether the domain is readable in daylight live somewhere a test can pin
 * them.
 */
public final class SubspaceOptics {

    /** How tightly the wall thickens toward its limb. Higher is a thinner, sharper rim. */
    public static final double K = 3.0;
    /** A sealed boundary spreads its weight further down the face, so it reads as a held wall. */
    public static final double K_SEALED = 2.4;
    /**
     * Alpha looking straight through the wall.
     *
     * <p>This was four and a half percent, which is what the arithmetic says a clean pane is, and
     * in the game it was nothing at all: the caster stands at the exact centre of their own
     * domain, where every sight line meets the wall square on and the Fresnel term therefore
     * never fires. The one number they ever see is this one, so it has to carry the whole reading
     * on its own. Seven percent is a world visibly held under glass and still a world you can
     * fight in.
     */
    public static final double A_FACE = 0.070;
    /** Alpha at the silhouette. Short of opaque on purpose: a domain is never a solid ball. */
    public static final double A_RIM = 0.58;
    /** The one law allowed to make the wall heavier, because a seal is a wall you cannot pass. */
    public static final double A_RIM_SEALED = 0.80;
    /** A little extra body within arm's reach of the wall, so the caster can find it by touch. */
    public static final double NEAR_LIFT = 0.10;

    /**
     * Line widths in pixels: the horizon and meridian, the graduations, and a law mark's strokes.
     *
     * <p>All three were a third narrower and it was the wrong instinct. A hairline is the right
     * width for a diagram on a white page and the wrong one for a line hung in the middle of a
     * world: at a pixel and a half it is legible over sky and gone over gravel, which means the
     * one reading a player needs most - where the wall is, against terrain - is the one it fails.
     */
    public static final double PX_MAIN = 2.2;
    public static final double PX_HAIR = 1.6;
    public static final double PX_BURN = 1.4;

    /**
     * How much of the wall a line darkens, before any light is put on it.
     *
     * <p>The lines are cut into the pane rather than laid on top of it, which is what lets them
     * hold against a bright sky: ink survives daylight and glow does not.
     */
    public static final double INK_LINE = 0.80;
    public static final double INK_TICK = 0.58;
    public static final double INK_MARK = 0.72;

    /**
     * The shoulder under every line: this much wider and this much fainter.
     *
     * <p>A single crisp stroke reads beautifully over one background and vanishes into another. A
     * soft shoulder either side gives it something of its own to sit on, so the line stays the
     * same width where width matters and stops depending on what is behind it. It is what an
     * engraved line does, and it is cheaper than drawing the line twice.
     */
    public static final double SHOULDER_SPREAD = 3.2;
    public static final double SHOULDER_INK = 0.34;
    /**
     * The floor under a screen-space derivative. Exactly at the limb a shell's parameters stop
     * changing across a pixel, {@code fwidth} collapses toward zero and an antialiased stroke
     * divides by it; without this the rim grows a ring of fireflies.
     */
    public static final double MIN_GRAD = 1.0e-4;

    /** Where the crown cap begins and ends, as {@code |ny|} on the unit shell, and how dim it is. */
    public static final double CROWN_LO = 0.955;
    public static final double CROWN_HI = 0.985;
    public static final double CROWN_DIM = 0.18;

    /**
     * Light is clamped before it is added, so no stack of strokes can reach white.
     *
     * <p>The knee sets how fast a line reaches that ceiling. It was 1.0, which meant a noon line -
     * held at {@code SubspaceSun.DAY_LIFT} - arrived with seven percent of its own colour on it and
     * every law mark in daylight came out the same grey. At 3.0 the ceiling is still the ceiling
     * and the difference between noon and midnight is a difference in degree rather than in kind.
     */
    public static final double GLOW_CEILING = 0.30;
    public static final double GLOW_KNEE = 3.0;

    /**
     * How much of the domain's own light is mixed into the ink of a line.
     *
     * <p>Pure ink on a cold pane reads as a scratch in it. A trace of the burnish in the ink reads
     * as engraving, which is what these lines are.
     */
    public static final double LINE_TINT = 0.22;

    /**
     * What the two painted elements cover.
     *
     * <p>Everything else in the domain darkens, because a line that runs right round the wall
     * covers enough of the frame that lighting it would wash the sky out. The crown and a crossing
     * ring are short, and an over-blend cannot clip however bright it is, so these two are allowed
     * to be brighter than what they cross - which is the whole of what makes them read as cuts
     * rather than as more drawing.
     */
    public static final double INK_CROWN = 0.55;
    public static final double INK_RIPPLE = 0.45;

    /** The wall's own colour: a cold slate that darkens daylight instead of whitening it. */
    public static final int BODY = 0x121A26;
    /** The ink the lines are cut in, darker than the wall so a line reads against its own pane. */
    public static final int INK = 0x080C12;
    /** The one lit colour: the burnish on a line the sun has caught. */
    public static final int BURNISH = 0x57BCFF;
    /** The crown, a shade brighter than the burnish because it is the only hard edge. */
    public static final int CROWN = 0x88DFFF;

    private SubspaceOptics() {}

    /**
     * The alpha the wall delivers to a sight line, given where that line crosses the dome's disc.
     *
     * @param bOverR the impact parameter over the radius: 0 dead centre, 1 exactly at the limb
     */
    public static double alphaDelivered(double bOverR) {
        return alphaDelivered(bOverR, false);
    }

    /** As {@link #alphaDelivered(double)}, for a domain whose boundary has been sealed. */
    public static double alphaDelivered(double bOverR, boolean sealed) {
        double u = clamp01(bOverR);
        // A ray at impact parameter b meets the sphere where the normal leans b/R off the line of
        // sight, so the cosine between them falls out of Pythagoras without a trigonometric call.
        double cosine = Math.sqrt(Math.max(0.0, 1.0 - u * u));
        double rim = sealed ? A_RIM_SEALED : A_RIM;
        double k = sealed ? K_SEALED : K;
        return A_FACE + (rim - A_FACE) * Math.pow(1.0 - cosine, k);
    }

    /**
     * What each wall must paint so that {@code crossings} of them together deliver {@code
     * delivered}.
     *
     * <p>This is the whole reason the caster and their opponent see the same domain. Standing
     * inside you cross one wall; standing outside you cross two, the near one and the far one.
     * Painting both at the delivered alpha would make the domain twice as heavy from outside as
     * from in, which is exactly backwards - the person who has to decide whether to walk into it
     * is the one who needs to see through it. Undoing the over-blend is an exact root, not an
     * approximation, so the two pictures agree at every angle rather than merely at the centre
     * and the rim.
     */
    public static double alphaPerCrossing(double delivered, int crossings) {
        if (crossings <= 1) {
            return delivered;
        }
        return 1.0 - Math.pow(1.0 - clamp01(delivered), 1.0 / crossings);
    }

    /** The area-weighted mean alpha over the dome's whole disc: the number that reads as milky. */
    public static double meanAlphaOverDisc(boolean sealed) {
        return meanAlphaOverInner(sealed, 1.0);
    }

    /**
     * The same mean taken only over the inner {@code fraction} of the disc - the part of the
     * screen a viewer is actually trying to see through, with the silhouette excluded.
     */
    public static double meanAlphaOverInner(boolean sealed, double fraction) {
        double r0 = clamp01(fraction);
        if (r0 <= 0.0) {
            return A_FACE;
        }
        double rim = sealed ? A_RIM_SEALED : A_RIM;
        double k = sealed ? K_SEALED : K;
        // Substituting the cosine for the radius turns the area integral into a beta function, so
        // the mean is closed-form and a test can assert it without sampling.
        double t = 1.0 - Math.sqrt(Math.max(0.0, 1.0 - r0 * r0));
        double integral = 2.0 * (Math.pow(t, k + 1.0) / (k + 1.0) - Math.pow(t, k + 2.0) / (k + 2.0));
        return A_FACE + (rim - A_FACE) * integral / (r0 * r0);
    }

    /** One channel of a packed RGB as 0..1. Channel 0 is red, 1 green, 2 blue. */
    public static double component(int rgb, int channel) {
        return ((rgb >> (16 - 8 * channel)) & 0xFF) / 255.0;
    }

    /** How many walls a sight line crosses: one from inside the domain, two from outside it. */
    public static int membraneCrossings(boolean inside) {
        return inside ? 1 : 2;
    }

    /**
     * The worst number of subspace surfaces one pixel can stack, membrane and marks together.
     *
     * <p>The old dome could stack a dozen - two shell bands, a ring, an arc, a tick and a glint
     * all lay on the same sight line - and on an additive blend a dozen layers is white. The
     * budget is small enough that the ceiling in {@link #GLOW_CEILING} can be proved rather than
     * guessed at.
     */
    public static int maxSurfacesCrossed(boolean inside) {
        return inside ? 2 : 4;
    }

    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }
}
