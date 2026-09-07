package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.FusionGeometry;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * The one piece of geometry every forged strike is built from: an arc of blade, laid down as a body
 * band plus a brighter band along its leading lip, with a thickness curve that runs thin at both
 * tips and fattest a third of the way in. Straight lances share the same profile through
 * {@link #lance}.
 *
 * <p>Everything emitted here goes to {@code MagicalRenderTypes.forgeEdge()}; the caller supplies
 * that consumer so a form is free to draw its other pieces on another render type.</p>
 */
public final class ForgeRibbon {

    /** Which local plane a sweep lies in. The renderer has already turned +Z into the strike's aim. */
    public enum Plane { FORWARD, UPRIGHT, GROUND }

    /** One arc of blade: where it lies, how far out it reaches and how far around it sweeps. */
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

        /** The same point in local space. */
        public float[] at(float t, float out) {
            float[] flat = uv(t, out);
            return planar(plane, flat[0], flat[1]);
        }
    }

    /** One lagged copy of a strike, handed its lag (0 at the head) and the alpha it should draw at. */
    @FunctionalInterface
    public interface Sample {
        void at(float lag, float alpha);
    }

    public static final int SEGMENTS = 26;
    private static final int LIGHT_TRAIL = 4;
    private static final int HEAVY_TRAIL = 6;
    private static final float EDGE_INSET = 0.62f;
    private static final float BODY_ALPHA = 205.0f;
    private static final float EDGE_ALPHA = 255.0f;
    private static final float PEAK = 0.35f;

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

    /** Body band plus leading-edge band for one arc. */
    public static void arc(VertexConsumer edge, Matrix4f pose, Sweep sweep, ForgePalette palette, float alpha) {
        int bodyAlpha = alpha(BODY_ALPHA, alpha);
        int edgeAlpha = alpha(EDGE_ALPHA, alpha);
        if (bodyAlpha <= 0 && edgeAlpha <= 0) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = i / (float) SEGMENTS;
            float t1 = (i + 1) / (float) SEGMENTS;
            band(edge, pose, sweep, t0, t1, 0.0f, 1.0f, palette.primary(), bodyAlpha);
            band(edge, pose, sweep, t0, t1, EDGE_INSET, 1.0f, palette.edge(), edgeAlpha);
        }
    }

    /**
     * One quad spanning {@code t0..t1} along the arc and {@code inner..outer} across it. The UVs
     * hand the shader its own contract: u runs along the arc, v runs across the ribbon.
     */
    public static void band(VertexConsumer edge, Matrix4f pose, Sweep sweep, float t0, float t1, float inner,
            float outer, int color, int alpha) {
        float[] a = sweep.at(t0, inner);
        float[] b = sweep.at(t0, outer);
        float[] c = sweep.at(t1, outer);
        float[] d = sweep.at(t1, inner);
        FusionGeometry.quad(edge, pose,
                a[0], a[1], a[2], t0, 0.0f,
                b[0], b[1], b[2], t0, 1.0f,
                c[0], c[1], c[2], t1, 1.0f,
                d[0], d[1], d[2], t1, 0.0f,
                FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), alpha);
    }

    /**
     * A straight tapered blade along local +Z from the origin to {@code length}, widening across
     * local X. The base flare keeps a thrust from starting at nothing, while the tip still needles
     * out to a point.
     */
    public static void lance(VertexConsumer edge, Matrix4f pose, float length, float halfWidth, float baseFlare,
            ForgePalette palette, float alpha) {
        int bodyAlpha = alpha(BODY_ALPHA, alpha);
        int edgeAlpha = alpha(EDGE_ALPHA, alpha);
        if (bodyAlpha <= 0 && edgeAlpha <= 0) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = i / (float) SEGMENTS;
            float t1 = (i + 1) / (float) SEGMENTS;
            float w0 = lanceWidth(t0, halfWidth, baseFlare);
            float w1 = lanceWidth(t1, halfWidth, baseFlare);
            slab(edge, pose, t0, t1, length, w0, w1, palette.primary(), bodyAlpha, false);
            slab(edge, pose, t0, t1, length, w0, w1, palette.primary(), bodyAlpha, true);
            slab(edge, pose, t0, t1, length, w0 * EDGE_INSET, w1 * EDGE_INSET, palette.edge(), edgeAlpha, false);
            slab(edge, pose, t0, t1, length, w0 * EDGE_INSET, w1 * EDGE_INSET, palette.edge(), edgeAlpha, true);
        }
    }

    /** Thin at both tips, fattest just past a third of the way along. */
    public static float bladeProfile(float t) {
        float x = Mth.clamp(t, 0.0f, 1.0f);
        float n = x < PEAK ? x / PEAK : (1.0f - x) / (1.0f - PEAK);
        return (float) Math.pow(Mth.clamp(n, 0.0f, 1.0f), 0.65);
    }

    /** Lifts a point from a plane's own two axes into local space. */
    public static float[] planar(Plane plane, float u, float v) {
        return switch (plane) {
            case FORWARD -> new float[] {0.0f, u, v};
            case UPRIGHT -> new float[] {u, v, 0.0f};
            case GROUND -> new float[] {u, 0.0f, v};
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

    /** One slice of a lance. The pair of orientations makes it read as a blade from any angle. */
    private static void slab(VertexConsumer edge, Matrix4f pose, float t0, float t1, float length, float w0,
            float w1, int color, int alpha, boolean vertical) {
        float z0 = t0 * length;
        float z1 = t1 * length;
        float ax0 = vertical ? 0.0f : -w0;
        float ay0 = vertical ? -w0 : 0.0f;
        float ax1 = vertical ? 0.0f : -w1;
        float ay1 = vertical ? -w1 : 0.0f;
        FusionGeometry.quad(edge, pose,
                ax0, ay0, z0, t0, 0.0f,
                -ax0, -ay0, z0, t0, 1.0f,
                -ax1, -ay1, z1, t1, 1.0f,
                ax1, ay1, z1, t1, 0.0f,
                FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), alpha);
    }
}
