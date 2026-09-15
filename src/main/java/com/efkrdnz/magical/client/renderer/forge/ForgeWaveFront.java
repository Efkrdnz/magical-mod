package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.FusionGeometry;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * The face of a thrown wave: a sheet that bulges toward where it is going and bends toward nothing
 * else.
 *
 * <p>Every other shape in this school is an arc, and an arc has a belly, and a belly points
 * somewhere. For a swing that is right - a swing is a thing you do in a direction, with the wielder
 * standing at the middle of it. For something thrown it is wrong twice over: the direction is
 * arbitrary, and whichever one gets picked is not the direction the wave is actually travelling. A
 * wave was drawn leaning up and to the right; then it was drawn with its belly down. Both were the
 * same mistake wearing a different angle.
 *
 * <p>So this is a surface of revolution about the line of flight. Roll it any way you like about
 * that line and it is the same sheet, because there is no up on it to get wrong: the middle stands
 * furthest forward, each ring out from the middle falls a little further behind, and the rim trails
 * last. The only curve on it is the one that points where it is going.
 */
public final class ForgeWaveFront {

    /** Rings from the middle out to the rim, and steps round the axis. */
    private static final int RINGS = 7;
    private static final int SEGMENTS = 24;

    /**
     * How hard the rings bunch toward the rim.
     *
     * <p>Spaced evenly they are not, and the reason is the shader: it puts a hard cutting lip at
     * the far side of the ribbon, and a lip that is one seventh of the sheet wide is not a lip, it
     * is a gradient. Bunched, the outer band covers the last few percent of the radius, so the
     * front ends on an edge - which is the difference between a blade and a bubble.
     */
    private static final float RIM_BUNCH = 1.9f;

    /**
     * How the sheet falls away from its middle. Squared rather than straight, so it leaves the
     * apex nearly flat and turns hardest near the rim: a cone leads with a point, and a wave is a
     * face of force rather than a drill.
     */
    private static final float FALLOFF = 2.0f;

    /** What the sheet and its glow may each contribute. One layer of an additive stack. */
    private static final float FRONT_ALPHA = 205.0f;
    private static final float HALO_ALPHA = 95.0f;

    /**
     * How far the glow stands off the rim, as a fraction of the rim's own radius. Enough to frame
     * the edge; any more and it is the halo the eye reads as the size of the thing, which is a lie
     * about where the wave catches.
     */
    private static final float HALO = 0.20f;

    private ForgeWaveFront() {}

    /**
     * A point on the sheet: {@code across} runs 0 at the middle to 1 at the rim, {@code angle} runs
     * round the axis, and the depth it comes back with depends on {@code across} alone.
     *
     * <p>That last clause is the whole design, so it is worth saying out loud: the angle decides
     * only where round the flight line a point sits, never how far along it. Nothing here leans.
     */
    public static float[] point(float radius, float depth, float angle, float across) {
        float out = Mth.clamp(across, 0.0f, 1.0f);
        double radians = Math.toRadians(angle);
        float ring = radius * out;
        return new float[] {
                (float) Math.cos(radians) * ring,
                (float) Math.sin(radians) * ring,
                depth * (1.0f - (float) Math.pow(out, FALLOFF))};
    }

    /**
     * Draws the sheet: concentric rings of quads from the leading apex out to the trailing rim.
     *
     * <p>The v handed to the shader runs from its white-hot core at the apex out to its cutting lip
     * at the rim, which is where a thrown blade's edge is and where the eye should be told the
     * thing ends. Between them the element's own colour carries the body, so the sheet reads as a
     * lit dome with a hard edge rather than as a bright disc.
     */
    public static void draw(VertexConsumer edge, Matrix4f pose, float radius, float depth, ForgePalette palette,
            float alpha) {
        int value = ForgeRibbon.alpha(FRONT_ALPHA, alpha);
        if (radius <= 0.0f || value <= 0) {
            return;
        }
        for (int ring = 0; ring < RINGS; ring++) {
            float in = outward(ring);
            float out = outward(ring + 1);
            int color = palette.mix(palette.primary(), palette.edge(), (in + out) * 0.5f);
            for (int step = 0; step < SEGMENTS; step++) {
                float from = step * 360.0f / SEGMENTS;
                float to = (step + 1) * 360.0f / SEGMENTS;
                float[] a = point(radius, depth, from, in);
                float[] b = point(radius, depth, from, out);
                float[] c = point(radius, depth, to, out);
                float[] d = point(radius, depth, to, in);
                FusionGeometry.quad(edge, pose,
                        a[0], a[1], a[2], along(from), coreward(in),
                        b[0], b[1], b[2], along(from), coreward(out),
                        c[0], c[1], c[2], along(to), coreward(out),
                        d[0], d[1], d[2], along(to), coreward(in),
                        FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), value);
            }
        }
    }

    /**
     * The glow around the rim: a closed band that always turns its broad side to the viewer.
     *
     * <p>The sheet is not flat, but it is thin, and seen from directly beside its flight it is
     * still close to a line - the same reason a swing needs {@link ForgeRibbon#sheath}. This is the
     * light the wave is giving off, and light faces everyone.
     *
     * <p>Written here rather than borrowed from the sheath because a sheath tapers to nothing at
     * both ends of its arc, and a ring has no ends: asking one for a closed circle would pinch the
     * glow shut at whichever angle the seam landed on, which is an in-plane direction again.
     */
    public static void halo(VertexConsumer edge, Matrix4f pose, float radius, float depth, ForgePalette palette,
            float alpha, float camX, float camY, float camZ) {
        int glow = ForgeRibbon.alpha(HALO_ALPHA, alpha);
        if (radius <= 0.0f || glow <= 0) {
            return;
        }
        int color = palette.primary();
        float half = radius * HALO;
        for (int step = 0; step < SEGMENTS; step++) {
            float from = step * 360.0f / SEGMENTS;
            float to = (step + 1) * 360.0f / SEGMENTS;
            float[] spine0 = point(radius, depth, from, 1.0f);
            float[] spine1 = point(radius, depth, to, 1.0f);
            float[] across0 = ForgeRibbon.square(tangentX(from), tangentY(from), 0.0f,
                    camX - spine0[0], camY - spine0[1], camZ - spine0[2]);
            float[] across1 = ForgeRibbon.square(tangentX(to), tangentY(to), 0.0f,
                    camX - spine1[0], camY - spine1[1], camZ - spine1[2]);
            for (float side = -1.0f; side <= 1.0f; side += 2.0f) {
                wing(edge, pose, spine0, across0, from, spine1, across1, to, half * side, color, glow);
            }
        }
    }

    /** One half of one segment of the halo: spine at v 0.5, rim at v 0, so the band has no lip. */
    private static void wing(VertexConsumer edge, Matrix4f pose, float[] spine0, float[] across0, float from,
            float[] spine1, float[] across1, float to, float half, int color, int alpha) {
        FusionGeometry.quad(edge, pose,
                spine0[0], spine0[1], spine0[2], along(from), 0.5f,
                spine0[0] + across0[0] * half, spine0[1] + across0[1] * half, spine0[2] + across0[2] * half,
                along(from), 0.0f,
                spine1[0] + across1[0] * half, spine1[1] + across1[1] * half, spine1[2] + across1[2] * half,
                along(to), 0.0f,
                spine1[0], spine1[1], spine1[2], along(to), 0.5f,
                FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), alpha);
    }

    /** How far out ring {@code i} of {@link #RINGS} sits: 0 at the apex, 1 at the rim, bunched there. */
    private static float outward(int ring) {
        return 1.0f - (float) Math.pow(1.0f - ring / (float) RINGS, RIM_BUNCH);
    }

    /**
     * Where across the shader's ribbon a point this far out sits: the hot core on the axis, the
     * cutting lip on the rim, the element's own colour over everything between.
     */
    private static float coreward(float across) {
        return 0.5f + 0.5f * Mth.clamp(across, 0.0f, 1.0f);
    }

    /**
     * Where along the shader's ribbon a point at this angle sits.
     *
     * <p>A ribbon is faded out at both ends of u so it never stops on a hard cap in mid-air. This
     * sheet closes on itself and has no ends, so its u has to stay clear of that fade, and it has
     * to come back round to where it started or the seam would show as a line. A sine does both.
     */
    private static float along(float angle) {
        return 0.5f + 0.42f * (float) Math.sin(Math.toRadians(angle));
    }

    /** The way round the rim at this angle: what the halo spreads square to. */
    private static float tangentX(float angle) {
        return -(float) Math.sin(Math.toRadians(angle));
    }

    private static float tangentY(float angle) {
        return (float) Math.cos(Math.toRadians(angle));
    }
}
