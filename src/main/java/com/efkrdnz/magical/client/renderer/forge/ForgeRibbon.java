package com.efkrdnz.magical.client.renderer.forge;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * The one piece of geometry every forged strike is built from: an arc of blade with a real
 * cross-section - sharp at both lips, thickest along the spine - swept round the arc into a closed
 * solid, with a thickness curve along its length that runs thin at both tips and fattest a third of
 * the way in. Straight lances share the same profile through {@link #lance}.
 *
 * <p>It was a single flat sheet until the blade got its cross-section, and a sheet has no
 * silhouette: seen along its own plane it has zero projected area and disappears, which is why a
 * swing could not be seen unless the camera happened to face it. The solid also costs less overdraw
 * than the sheet did, because the two bands it used to draw overlapped between the inset
 * and the lip and the four faces of the solid do not overlap at all.
 *
 * <p>Everything here is painted from the smear atlas through a {@link ForgeStroke}: the blade on
 * the translucent smear pass, and the hot lip of an element that gives off light a second time on
 * the additive glint pass. The texture is the blade's look; nothing here shades it.</p>
 */
public final class ForgeRibbon {

    /** Which local plane a sweep lies in. The renderer has already turned +Z into the strike's aim. */
    public enum Plane { FORWARD, UPRIGHT, GROUND }

    /**
     * One arc of blade: where it lies, how far out it reaches, and how far around it sweeps.
     *
     * <p>Flat, and deliberately so. An arc is what a swing is - a thing done in a direction, with
     * the wielder standing at the middle of it - and a swing is over in three ticks. A thrown wave
     * is not an arc at all and does not come through here: see {@link ForgeWaveFront}.
     */
    public record Sweep(Plane plane, float radius, float thickness, float fromDegrees, float toDegrees) {

        public Sweep shifted(float degrees) {
            return new Sweep(plane, radius, thickness, fromDegrees + degrees, toDegrees + degrees);
        }

        public Sweep scaled(float factor) {
            return new Sweep(plane, radius * factor, thickness * factor, fromDegrees, toDegrees);
        }

        /** In-plane coordinates of the point at {@code t} along the arc, {@code out} across it. */
        public float[] uv(float t, float out) {
            float angle = Mth.lerp(t, fromDegrees, toDegrees) * Mth.DEG_TO_RAD;
            float inner = radius - thickness * bladeProfile(t);
            float reach = Mth.lerp(out, inner, radius);
            return new float[] {Mth.sin(angle) * reach, Mth.cos(angle) * reach};
        }

        /** The same point in local space, flat against the plane. */
        public float[] at(float t, float out) {
            return at(t, out, 0.0f);
        }

        /** The same point, standing {@code n} off the plane: one face of the solid. */
        public float[] at(float t, float out, float n) {
            float[] flat = uv(t, out);
            return planar(plane, flat[0], flat[1], n);
        }
    }

    /** One lagged copy of a strike, handed its lag (0 at the head) and the alpha it should draw at. */
    @FunctionalInterface
    public interface Sample {
        void at(float lag, float alpha);
    }

    public static final int SEGMENTS = 26;
    /**
     * Copies of the blade behind its head. One for a light blow and two for a heavy one: the
     * texture is the smear now, and the opened arc is already the whole path the blade swept, so
     * the four and six copies the shader needed only stacked into a solid block of colour.
     */
    private static final int LIGHT_TRAIL = 1;
    private static final int HEAVY_TRAIL = 2;
    // Coverage, not light: the smear blends translucently, so these are how much of what is behind
    // a blade shows through it. Under full because the solid shows a near and a far face and both
    // land on the same pixel.
    private static final float BODY_ALPHA = 150.0f;
    private static final float EDGE_ALPHA = 215.0f;
    /** What the hot lip adds on the glint pass, before the weapon's grade scales it. */
    private static final float GLINT_ALPHA = 150.0f;
    private static final float PEAK = 0.35f;
    /**
     * Where the body gives way to the cutting edge, as a fraction across the blade.
     *
     * <p>Well past the middle. Split evenly, half the blade was drawn in the edge colour at the
     * higher alpha and the element's own primary only ever covered the inner half, so a void cut
     * came out pale lavender and a venom one came out white. The edge is a lip, not a half.
     */
    private static final float SPINE = 0.70f;
    /**
     * Half-thickness at the spine, as a fraction of the blade's width across. A blade is far
     * thinner than it is broad, so this is small; it only has to be non-zero for the strike to have
     * a silhouette from every angle, and too much turns a cut into a log.
     */
    private static final float THICKNESS = 0.34f;
    /**
     * How far the glow stands off the blade, as a fraction of the blade's width across. Wider than
     * the steel, because a glow is; nowhere near wide enough to read as a second, fatter blade,
     * because the hit shape has not moved.
     */
    private static final float SHEATH = 0.80f;
    /** What the glow may contribute at its centreline. Well under the steel: it is light, not edge. */
    private static final float SHEATH_ALPHA = 80.0f;
    /**
     * Segments round the sheath. Half the steel's, because a soft band shows its faceting far less
     * than a hard lip does, and it costs four quads a segment where the solid costs four.
     */
    private static final int SHEATH_SEGMENTS = 13;

    private ForgeRibbon() {}

    /**
     * Runs {@code sample} from the oldest lagged copy to the newest so the trail draws behind the
     * head. Alpha falls off linearly with lag; an inverted trail hands the head the faint end
     * instead, which is how VOID leads with its wake rather than its edge.
     */
    public static void trail(boolean heavy, boolean inverted, float alpha, Sample sample) {
        int samples = heavy ? HEAVY_TRAIL : LIGHT_TRAIL;
        for (int i = samples - 1; i >= 0; i--) {
            float lag = i / (float) samples;
            float falloff = inverted ? 0.25f + lag * 0.75f : 1.0f - lag;
            sample.at(lag, alpha * falloff);
        }
    }

    /**
     * One arc of blade as a closed solid: four faces per segment, the inner and outer half of the
     * cross-section on each side of the spine.
     *
     * <p>It used to be two flat bands laid over each other - a body band across the whole width and
     * a brighter one over the outer third - which had no thickness at all and double-wrote every
     * pixel where they overlapped. The four faces meet at the lips and at the spine and overlap
     * nowhere, so this is both solid and cheaper per pixel than the sheet was.
     */
    public static void arc(ForgeStroke stroke, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha) {
        if (alpha(EDGE_ALPHA, alpha) <= 0) {
            return;
        }
        float glint = GLINT_ALPHA * stroke.glintStrength();
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = i / (float) SEGMENTS;
            float t1 = (i + 1) / (float) SEGMENTS;
            for (float side = -1.0f; side <= 1.0f; side += 2.0f) {
                // Inner half in the body colour, outer half in the edge colour, both sampled from
                // the one row: the texture decides where the blade is broken, speckled or wet.
                face(stroke.smear(), stroke.row(), stroke, pose, sweep, t0, t1, 0.0f, SPINE, palette.primary(),
                        BODY_ALPHA * alpha, side);
                face(stroke.smear(), stroke.row(), stroke, pose, sweep, t0, t1, SPINE, 1.0f, palette.edge(),
                        EDGE_ALPHA * alpha, side);
                if (stroke.glint() != null) {
                    face(stroke.glint(), ForgeSmear.Row.LIP, stroke, pose, sweep, t0, t1, SPINE, 1.0f,
                            palette.bloom(), glint * alpha, side);
                }
            }
        }
    }

    /**
     * One face of the solid: the quad spanning {@code t0..t1} along the arc and {@code inner..outer}
     * across it, standing off the plane by the cross-section on {@code side}.
     *
     * <p>The offset is tapered by {@link #bladeProfile} as well as the cross-section, so the tips
     * come to a point in three dimensions rather than staying full-thickness and reading as a
     * paddle. The v it hands the shader is the true position across the blade, so the shader's
     * spine and cutting edge land where the geometry actually puts them.
     */
    private static void face(VertexConsumer consumer, ForgeSmear.Row row, ForgeStroke stroke, Matrix4f pose,
            Sweep sweep, float t0, float t1, float inner, float outer, int color, float alpha, float side) {
        int a0 = Math.round(alpha * stroke.ramp(sweep, t0));
        int a1 = Math.round(alpha * stroke.ramp(sweep, t1));
        if (a0 <= 0 && a1 <= 0) {
            return;
        }
        float u0 = stroke.u(sweep, t0);
        float u1 = stroke.u(sweep, t1);
        float vIn = ForgeSmear.v(row, inner);
        float vOut = ForgeSmear.v(row, outer);
        ForgeQuads.vertex(consumer, pose, sweep.at(t0, inner, side * halfThickness(sweep, t0, inner)), u0, vIn,
                color, a0);
        ForgeQuads.vertex(consumer, pose, sweep.at(t0, outer, side * halfThickness(sweep, t0, outer)), u0, vOut,
                color, a0);
        ForgeQuads.vertex(consumer, pose, sweep.at(t1, outer, side * halfThickness(sweep, t1, outer)), u1, vOut,
                color, a1);
        ForgeQuads.vertex(consumer, pose, sweep.at(t1, inner, side * halfThickness(sweep, t1, inner)), u1, vIn,
                color, a1);
    }

    /**
     * The glow around the blade: a band that follows the same arc but always turns its broad side
     * to the viewer, so a strike has something to show from every angle.
     *
     * <p>The solid gave the blade a thickness; it did not stop a blade from being a blade. Seen
     * down the line of its own swing an arc is still close to a line, because that is what a swing
     * going away from you looks like - and most swings go away from you, which is why an overhead
     * chop read as a scratch above the wielder's head. What a real one has at that angle is its
     * glow, and a glow faces everyone.
     *
     * <p>Both halves run their v from the spine outward, so the band is symmetric about its
     * centreline and the shader's cutting-lip term never fires on it: the steel keeps the hard
     * edge, this keeps the bloom. Drawn once for the whole strike rather than once per trail copy -
     * it is the light the swing is giving off, not another copy of the swing.
     */
    public static void sheath(ForgeStroke stroke, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha,
            float camX, float camY, float camZ) {
        int glow = alpha(SHEATH_ALPHA, alpha);
        if (glow <= 0) {
            return;
        }
        VertexConsumer edge = stroke.light();
        int color = palette.primary();
        for (int i = 0; i < SHEATH_SEGMENTS; i++) {
            float t0 = i / (float) SHEATH_SEGMENTS;
            float t1 = (i + 1) / (float) SHEATH_SEGMENTS;
            float[] spine0 = sweep.at(t0, 0.5f);
            float[] spine1 = sweep.at(t1, 0.5f);
            float[] across0 = facing(sweep, t0, camX, camY, camZ);
            float[] across1 = facing(sweep, t1, camX, camY, camZ);
            float half0 = sheathHalfWidth(sweep, t0);
            float half1 = sheathHalfWidth(sweep, t1);
            float u0 = stroke.u(sweep, t0);
            float u1 = stroke.u(sweep, t1);
            int g0 = Math.round(glow * stroke.ramp(sweep, t0));
            int g1 = Math.round(glow * stroke.ramp(sweep, t1));
            for (float side = -1.0f; side <= 1.0f; side += 2.0f) {
                wing(edge, pose, spine0, across0, half0 * side, u0, g0, spine1, across1, half1 * side, u1, g1,
                        color);
            }
        }
    }

    /** The same glow around a straight lance, whose axis is local +Z rather than an arc. */
    public static void lanceSheath(ForgeStroke stroke, Matrix4f pose, float length, float halfWidth,
            ForgePalette palette, float alpha, float camX, float camY, float camZ) {
        int glow = alpha(SHEATH_ALPHA, alpha);
        if (glow <= 0) {
            return;
        }
        VertexConsumer edge = stroke.light();
        int color = palette.primary();
        for (int i = 0; i < SHEATH_SEGMENTS; i++) {
            float t0 = i / (float) SHEATH_SEGMENTS;
            float t1 = (i + 1) / (float) SHEATH_SEGMENTS;
            float[] spine0 = {0.0f, 0.0f, t0 * length};
            float[] spine1 = {0.0f, 0.0f, t1 * length};
            float[] across0 = square(0.0f, 0.0f, 1.0f, camX - spine0[0], camY - spine0[1], camZ - spine0[2]);
            float[] across1 = square(0.0f, 0.0f, 1.0f, camX - spine1[0], camY - spine1[1], camZ - spine1[2]);
            float half0 = halfWidth * SHEATH * bladeProfile(t0);
            float half1 = halfWidth * SHEATH * bladeProfile(t1);
            float u0 = lanceU(t0, length, stroke);
            float u1 = lanceU(t1, length, stroke);
            int g0 = Math.round(glow * ForgeSmear.lanceRamp(t0));
            int g1 = Math.round(glow * ForgeSmear.lanceRamp(t1));
            for (float side = -1.0f; side <= 1.0f; side += 2.0f) {
                wing(edge, pose, spine0, across0, half0 * side, u0, g0, spine1, across1, half1 * side, u1, g1,
                        color);
            }
        }
    }

    /**
     * The direction the sheath spreads at {@code t}: square to the arc's own run and square to the
     * line of sight, which is what makes the band face the viewer without turning it into a
     * billboard that ignores the shape it is wrapping.
     */
    public static float[] facing(Sweep sweep, float t, float camX, float camY, float camZ) {
        float step = 1.0f / (SHEATH_SEGMENTS * 2.0f);
        float[] before = sweep.at(Math.max(0.0f, t - step), 0.5f);
        float[] after = sweep.at(Math.min(1.0f, t + step), 0.5f);
        float[] here = sweep.at(t, 0.5f);
        return square(after[0] - before[0], after[1] - before[1], after[2] - before[2],
                camX - here[0], camY - here[1], camZ - here[2]);
    }

    /**
     * How far the glow stands off the blade at {@code t}. It tapers on {@link #bladeProfile}, so it
     * closes to nothing at both tips rather than ending on a hard cap in mid-air.
     */
    public static float sheathHalfWidth(Sweep sweep, float t) {
        return sweep.thickness() * SHEATH * bladeProfile(t);
    }

    /**
     * One half of one segment of a sheath, on the atlas's soft SHEATH row: the spine samples the
     * middle of the row and the rim its clear top line, so the band has no lip and fades to nothing
     * at its edge the same way on both sides.
     */
    private static void wing(VertexConsumer edge, Matrix4f pose, float[] spine0, float[] across0, float half0,
            float u0, int alpha0, float[] spine1, float[] across1, float half1, float u1, int alpha1, int color) {
        float vSpine = ForgeSmear.v(ForgeSmear.Row.SHEATH, 0.5f);
        float vRim = ForgeSmear.v(ForgeSmear.Row.SHEATH, 0.0f);
        ForgeQuads.vertex(edge, pose, spine0, u0, vSpine, color, alpha0);
        ForgeQuads.vertex(edge, pose, spine0[0] + across0[0] * half0, spine0[1] + across0[1] * half0,
                spine0[2] + across0[2] * half0, u0, vRim, color, alpha0);
        ForgeQuads.vertex(edge, pose, spine1[0] + across1[0] * half1, spine1[1] + across1[1] * half1,
                spine1[2] + across1[2] * half1, u1, vRim, color, alpha1);
        ForgeQuads.vertex(edge, pose, spine1, u1, vSpine, color, alpha1);
    }

    /**
     * A unit vector square to both {@code run} and {@code view}: which way a band of light spreads
     * so it turns its broad side to the viewer without becoming a billboard that ignores the shape
     * it is wrapping. The wave's rim halo asks for the same thing round a closed ring.
     *
     * <p>A viewer sighting exactly along the arc leaves the two parallel and their cross product
     * zero. That happens, so it gets an answer rather than a NaN: any perpendicular of the run will
     * do, because at that angle the band is edge-on to them however it is turned.
     */
    public static float[] square(float runX, float runY, float runZ, float viewX, float viewY, float viewZ) {
        float x = runY * viewZ - runZ * viewY;
        float y = runZ * viewX - runX * viewZ;
        float z = runX * viewY - runY * viewX;
        float lengthSqr = x * x + y * y + z * z;
        if (lengthSqr <= 1.0E-10f) {
            return anyPerpendicular(runX, runY, runZ);
        }
        float length = (float) Math.sqrt(lengthSqr);
        return new float[] {x / length, y / length, z / length};
    }

    /** Some unit vector square to {@code run}; which one does not matter where this is reached. */
    private static float[] anyPerpendicular(float runX, float runY, float runZ) {
        float ax = Math.abs(runX) < 0.9f ? 1.0f : 0.0f;
        float ay = Math.abs(runX) < 0.9f ? 0.0f : 1.0f;
        float x = -runZ * ay;
        float y = runZ * ax;
        float z = runX * ay - runY * ax;
        float lengthSqr = x * x + y * y + z * z;
        if (lengthSqr <= 1.0E-10f) {
            return new float[] {0.0f, 1.0f, 0.0f};
        }
        float length = (float) Math.sqrt(lengthSqr);
        return new float[] {x / length, y / length, z / length};
    }

    /**
     * A straight tapered blade along local +Z from the origin to {@code length}, widening across
     * local X. The base flare keeps a thrust from starting at nothing, while the tip still needles
     * out to a point.
     */
    public static void lance(ForgeStroke stroke, Matrix4f pose, float length, float halfWidth, float baseFlare,
            ForgePalette palette, float alpha) {
        int body = alpha(EDGE_ALPHA, alpha);
        if (body <= 0) {
            return;
        }
        int glint = alpha(GLINT_ALPHA * stroke.glintStrength(), alpha);
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = i / (float) SEGMENTS;
            float t1 = (i + 1) / (float) SEGMENTS;
            float w0 = lanceWidth(t0, halfWidth, baseFlare);
            float w1 = lanceWidth(t1, halfWidth, baseFlare);
            float u0 = lanceU(t0, length, stroke);
            float u1 = lanceU(t1, length, stroke);
            // Clear at the hilt and full toward the point: a thrust starts at the wielder's eye, and
            // drawn whole its base filled the bottom of the view.
            float r0 = ForgeSmear.lanceRamp(t0);
            float r1 = ForgeSmear.lanceRamp(t1);
            for (int half = 0; half < 4; half++) {
                // Two slabs crossed, each split at its centreline, so both of a lance's edges carry
                // the lip: the row runs from its body on the spine out to the lip at either side.
                boolean vertical = half >= 2;
                float side = half % 2 == 0 ? -1.0f : 1.0f;
                slab(stroke.smear(), stroke.row(), pose, t0, t1, u0, u1, length, w0 * side, w1 * side, vertical,
                        palette.primary(), palette.edge(), Math.round(body * r0), Math.round(body * r1));
                if (stroke.glint() != null && glint > 0) {
                    slab(stroke.glint(), ForgeSmear.Row.LIP, pose, t0, t1, u0, u1, length, w0 * side, w1 * side,
                            vertical, palette.bloom(), palette.bloom(), Math.round(glint * r0),
                            Math.round(glint * r1));
                }
            }
        }
    }

    /** The u along a lance: anchored to its point, as an arc's is to its head. */
    private static float lanceU(float t, float length, ForgeStroke stroke) {
        return ForgeSmear.head(t, length, stroke.offset());
    }

    /**
     * The blade seen end-on: zero at both lips and one along the spine, as a fraction of the
     * sweep's own thickness.
     *
     * <p>A lens rather than a tent, so the faces meet the lips tangentially and the solid reads as
     * something ground to an edge instead of a folded strip. Clamped at both ends: a trail copy
     * scaled past its lip would otherwise come back negative and turn the solid inside out.
     */
    public static float crossSection(float out) {
        float x = Mth.clamp(out, 0.0f, 1.0f);
        return (float) Math.sqrt(Math.max(0.0, 1.0 - Math.pow(2.0 * x - 1.0, 2.0)));
    }

    /** How far off the plane one face of the blade stands at this point: the cross-section, scaled. */
    public static float halfThickness(Sweep sweep, float t, float out) {
        return crossSection(out) * bladeProfile(t) * sweep.thickness() * THICKNESS;
    }

    /** Thin at both tips, fattest just past a third of the way along. */
    public static float bladeProfile(float t) {
        float x = Mth.clamp(t, 0.0f, 1.0f);
        float n = x < PEAK ? x / PEAK : (1.0f - x) / (1.0f - PEAK);
        return (float) Math.pow(Mth.clamp(n, 0.0f, 1.0f), 0.65);
    }

    /** Lifts a point from a plane's own two axes into local space, flat against the plane. */
    public static float[] planar(Plane plane, float u, float v) {
        return planar(plane, u, v, 0.0f);
    }

    /**
     * The same, standing {@code n} off the plane along the one axis the plane does not span.
     *
     * <p>That axis is the whole point. Put the offset on an axis the plane already uses and the
     * blade shears along its own face instead of gaining a side, and it is still a sheet.
     */
    public static float[] planar(Plane plane, float u, float v, float n) {
        return switch (plane) {
            case FORWARD -> new float[] {n, u, v};
            case UPRIGHT -> new float[] {u, v, n};
            case GROUND -> new float[] {u, n, v};
        };
    }

    /** The GLSL ramp, in Java: renderers fade strikes with it and shaders shape them with it. */
    public static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static int alpha(float base, float factor) {
        return Mth.clamp(Math.round(base * factor), 0, 255);
    }

    private static float lanceWidth(float t, float halfWidth, float baseFlare) {
        float taper = 1.0f - t;
        return halfWidth * bladeProfile(t) + baseFlare * taper * taper;
    }

    /**
     * Half of one slice of a lance, from its centreline out to one edge ({@code w} signed). The
     * pair of orientations makes it read as a blade from any angle; the body colour on the spine
     * runs into the edge colour at the lip, corner by corner.
     */
    private static void slab(VertexConsumer consumer, ForgeSmear.Row row, Matrix4f pose, float t0, float t1,
            float u0, float u1, float length, float w0, float w1, boolean vertical, int spine, int lip, int alpha0,
            int alpha1) {
        float z0 = t0 * length;
        float z1 = t1 * length;
        float vSpine = ForgeSmear.v(row, 0.5f);
        float vLip = ForgeSmear.v(row, 1.0f);
        ForgeQuads.vertex(consumer, pose, 0.0f, 0.0f, z0, u0, vSpine, spine, alpha0);
        ForgeQuads.vertex(consumer, pose, vertical ? 0.0f : w0, vertical ? w0 : 0.0f, z0, u0, vLip, lip, alpha0);
        ForgeQuads.vertex(consumer, pose, vertical ? 0.0f : w1, vertical ? w1 : 0.0f, z1, u1, vLip, lip, alpha1);
        ForgeQuads.vertex(consumer, pose, 0.0f, 0.0f, z1, u1, vSpine, spine, alpha1);
    }
}
