package com.efkrdnz.magical.client.renderer.space;

/**
 * How the boundary is seen: the pane it holds the world under, and the chamfer every stroke on it
 * is cut with.
 *
 * <p>The wall this replaces was an inverted Fresnel, and the arithmetic was right about a viewer
 * who does not exist. A domain is a sphere centred on its caster, so from the one viewpoint anyone
 * ever has, every sight line runs along the surface normal and the angle the whole transparency
 * was a function of is a constant to ten decimal places. The wall was therefore a flat seven
 * percent, which moves a daylight sky by fifteen levels and blackstone by two, and a wall you
 * cannot find against terrain is not a wall.
 *
 * <p>So visibility is not carried by the pane at all. The pane is {@link #A_PANE}, a tint, and the
 * reading is carried by structure - and every stroke of that structure is a <em>chamfer</em>: a
 * lip that darkens toward {@link #BODY} meeting a lip that lightens toward {@link #GLAZE} at a
 * one-pixel arris. The two move a background in opposite directions, so whichever lip the
 * background defeats, the other one carries, and the worst case has a closed form:
 *
 * <pre>{@code   floor = A_SHADOW * A_HIGHLIGHT * (GLAZE - BODY) / (A_SHADOW + A_HIGHLIGHT)}</pre>
 *
 * <p>which is {@link #chamferFloor(int)}: fifty-seven levels of two hundred and fifty-five in the
 * worst channel against the worst background in the game, proved rather than sampled, and pinned
 * by {@code SubspaceVaultTest}. That is the number the old wall got three on.
 *
 * <p>The Fresnel is kept, and only on the branch where it was always right: from outside, the
 * impact parameter genuinely sweeps the disc, the limb genuinely is where a silhouette lives, and
 * nothing here is a constant.
 */
public final class SubspaceOptics {

    /**
     * The pane, looking through it from inside.
     *
     * <p>Three percent: six levels of cold slate over daylight sand, at the floor of what an eye
     * detects, and less than half what the wall it replaces laid over the entire frame. It carries
     * no reading whatever and it is not supposed to - it is the difference between a world and a
     * world held under glass.
     */
    public static final double A_PANE = 0.030;
    /** The one law allowed to thicken the wall, because a seal is a wall you cannot pass. */
    public static final double SEAL_LIFT = 0.030;

    /** Alpha at the silhouette, from outside. Short of opaque: a domain is never a solid ball. */
    public static final double A_LIMB = 0.62;
    public static final double A_LIMB_SEALED = 0.84;
    /** How tightly the outside wall thickens toward its limb. Higher is a thinner, sharper rim. */
    public static final double K_LIMB = 3.0;
    public static final double K_SEALED = 2.4;

    /** The darkening lip of every stroke, and the lightening one. See the class note. */
    public static final double A_SHADOW = 0.62;
    public static final double A_HIGHLIGHT = 0.52;
    /**
     * The arris between the two lips, in pixels.
     *
     * <p>Measured in the same screen-space derivative the stroke is, so the two blow up together at
     * grazing incidence and the chamfer can never flatten into one mid-blue line - which is the one
     * real hazard in the whole idea, designed out rather than tuned around.
     */
    public static final double ARRIS_PIXELS = 1.2;
    /** How much solid lip a stroke must keep either side of the arris, or the two average out. */
    public static final double MIN_LIP_PIXELS = 1.5;

/**
     * Stroke widths in pixels, all of them wide enough to hold two lips and an arris.
     *
     * <p>Every one of these went up by about a sixth after the first capture: the arithmetic said
     * a five-pixel stroke clears fifty-seven levels against any background and that is true, but a
     * five-pixel line thirty blocks away on a bright sky is a thread, and a wall you have to look
     * for is the failure this was rebuilt to fix. The plinth is the heaviest because it is the only
     * member in the lower half of the frame and because a plinth carries a wall.
     */
    public static final double PX_RIB = 6.2;
    /** A rib carrying a law is half again as heavy, which is the cue that survives at any distance. */
    public static final double RIB_LIT_GAIN = 1.34;
    public static final double PX_SPRING = 6.2;
    public static final double PX_FOOT = 7.0;
    public static final double PX_OCULUS = 6.0;
    public static final double PX_NOTCH = 6.0;
    public static final double PX_GLYPH = 6.6;
    public static final double PX_RIPPLE = 6.0;

    /** How much an unwritten slot is held back by, so an empty socket is drawn and not just absent. */
    public static final double NOTCH_DIM = 0.72;
    /** The panel a law glyph is embossed on, so the glyph has ground of its own to sit against. */
    public static final double BOSS_LIFT = 0.060;

    /** Where the pane stops, as the height the shader reads back off the shell. */
    public static final double APERTURE_SIN = Math.sin(Math.toRadians(SubspaceVault.OCULUS_LATITUDE_DEGREES));
    /** How many dashes a dissolved-gravity ring breaks into, right round the wall. */
    public static final double DASH_CYCLES = 72.0;

    /**
     * Light is clamped before it is added, so no stack of strokes can reach white.
     *
     * <p>The knee sets how fast a stroke reaches that ceiling; at noon a line arrives with only
     * {@code SubspaceSun.DAY_LIFT} of lift, and at a knee of one that came out as the same grey for
     * every law in the domain.
     */
    public static final double GLOW_CEILING = 0.30;
    public static final double GLOW_KNEE = 3.0;
    /** How much of a stroke own colour is mixed into the light in its groove. */
    public static final double GLOW_TINT = 0.45;
    /** The softness of the lighting term across a glyph stroke, in signed-distance gradient. */
    public static final double GLYPH_LIGHT_SPREAD = 0.35;
    /**
     * The floor under a screen-space derivative. Exactly at the limb a shell parameters stop
     * changing across a pixel, {@code fwidth} collapses toward zero and an antialiased stroke
     * divides by it; without this the rim grows a ring of fireflies.
     */
    public static final double MIN_GRAD = 1.0e-4;

    /** The wall own colour: a cold slate that darkens daylight instead of whitening it. */
    public static final int BODY = 0x121A26;
    /** The lit lip. Cold and near-white, and the reason the guaranteed floor is as high as it is. */
    public static final int GLAZE = 0xDCEBFF;
    /** The light in the groove of a stroke the sun has caught. */
    public static final int BURNISH = 0x57BCFF;

    private SubspaceOptics() {}

    /** What the pane takes out of the world, from inside, at every angle, because there is only one. */
    public static double paneAlpha(boolean sealed) {
        return sealed ? A_PANE + SEAL_LIFT : A_PANE;
    }

    /**
     * The worst change a chamfered stroke can make to a background, over every background there is.
     *
     * <p>The two lips move a background in opposite directions, so the hardest background is the
     * one where they are equal and opposite; solving for it removes the background from the answer
     * altogether. This is the test the wall that shipped could not have passed.
     */
    public static double chamferFloor(int channel) {
        double body = component(BODY, channel);
        double glaze = component(GLAZE, channel);
        return A_SHADOW * A_HIGHLIGHT * (glaze - body) / (A_SHADOW + A_HIGHLIGHT);
    }

    /** How much solid lip a stroke of this width keeps either side of its arris. */
    public static double lipCorePixels(double pixels) {
        return (pixels - 1.0) * 0.5 - ARRIS_PIXELS * 0.5;
    }

    /**
     * The alpha the wall delivers to a sight line from outside, given where that line crosses the
     * dome disc.
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
        double rim = sealed ? A_LIMB_SEALED : A_LIMB;
        double k = sealed ? K_SEALED : K_LIMB;
        return paneAlpha(sealed) + (rim - paneAlpha(sealed)) * Math.pow(1.0 - cosine, k);
    }

    /**
     * What each wall must paint so that {@code crossings} of them together deliver {@code
     * delivered}.
     *
     * <p>Standing outside you cross two walls, the near one and the far one. Painting both at the
     * delivered alpha would make the domain twice as heavy from outside as from in, which is
     * exactly backwards: the person deciding whether to walk into it is the one who needs to see
     * through it. Undoing the over-blend is an exact root, so the two agree at every angle.
     */
    public static double alphaPerCrossing(double delivered, int crossings) {
        if (crossings <= 1) {
            return delivered;
        }
        return 1.0 - Math.pow(1.0 - clamp01(delivered), 1.0 / crossings);
    }

    /** The area-weighted mean alpha over the dome whole disc, seen from outside. */
    public static double meanAlphaOverDisc(boolean sealed) {
        return meanAlphaOverInner(sealed, 1.0);
    }

    /**
     * The same mean taken only over the inner {@code fraction} of the disc - the part of the screen
     * a viewer outside is actually trying to see through, with the silhouette excluded.
     */
    public static double meanAlphaOverInner(boolean sealed, double fraction) {
        double r0 = clamp01(fraction);
        double pane = paneAlpha(sealed);
        if (r0 <= 0.0) {
            return pane;
        }
        double rim = sealed ? A_LIMB_SEALED : A_LIMB;
        double k = sealed ? K_SEALED : K_LIMB;
        // Substituting the cosine for the radius turns the area integral into a beta function, so
        // the mean is closed-form and a test can assert it without sampling.
        double t = 1.0 - Math.sqrt(Math.max(0.0, 1.0 - r0 * r0));
        double integral = 2.0 * (Math.pow(t, k + 1.0) / (k + 1.0) - Math.pow(t, k + 2.0) / (k + 2.0));
        return pane + (rim - pane) * integral / (r0 * r0);
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
     * The worst number of subspace surfaces one pixel can stack, pane and structure together.
     *
     * <p>Small enough that {@link #GLOW_CEILING} can be proved rather than guessed at, which is how
     * every additive effect in this mod has always failed over daylight.
     */
    public static int maxSurfacesCrossed(boolean inside) {
        return inside ? 3 : 5;
    }

    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }
}
