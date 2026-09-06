package com.efkrdnz.magical.client.renderer.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Cached local-space meshes. Every mesh is a float array of quads, five floats per vertex
 * (x, y, z, u, v), four vertices per quad, built once per (emitter, parameters) key and emitted
 * with a pose matrix; all per-frame work is transform and copy, no trig.
 */
public final class FxMesh {
    private static final Map<String, float[]> CACHE = new HashMap<>();
    private static final int STRIDE = 5;

    private FxMesh() {}

    public static void emit(VertexConsumer consumer, Matrix4f matrix, float[] mesh, float scaleX, float scaleY, float scaleZ, int rgb, float opacity, int packed) {
        for (int i = 0; i + STRIDE * 4 <= mesh.length; i += STRIDE * 4) {
            for (int v = 0; v < 4; v++) {
                int o = i + v * STRIDE;
                MagicVertex.emit(consumer, matrix, mesh[o] * scaleX, mesh[o + 1] * scaleY, mesh[o + 2] * scaleZ, mesh[o + 3], mesh[o + 4], rgb, opacity, packed);
            }
        }
    }

    /** Emit with a per-vertex opacity function of the local position (for baked fresnel / fades). */
    public static void emitShaded(VertexConsumer consumer, Matrix4f matrix, float[] mesh, float scaleX, float scaleY, float scaleZ, int rgb, float opacity, int packed, VertexShade shade) {
        for (int i = 0; i + STRIDE * 4 <= mesh.length; i += STRIDE * 4) {
            for (int v = 0; v < 4; v++) {
                int o = i + v * STRIDE;
                float x = mesh[o] * scaleX;
                float y = mesh[o + 1] * scaleY;
                float z = mesh[o + 2] * scaleZ;
                MagicVertex.emit(consumer, matrix, x, y, z, mesh[o + 3], mesh[o + 4], rgb, opacity * shade.opacity(x, y, z, mesh[o + 3], mesh[o + 4]), packed);
            }
        }
    }

    @FunctionalInterface
    public interface VertexShade {
        float opacity(float x, float y, float z, float u, float v);
    }

    /** Unit annulus in the XY plane: u = angle01 around, v = 0 inner .. 1 outer. */
    public static float[] annulus(int segments, float innerRatio) {
        return CACHE.computeIfAbsent("annulus:" + segments + ":" + innerRatio, key -> {
            float[] m = new float[segments * 4 * STRIDE];
            int o = 0;
            for (int i = 0; i < segments; i++) {
                float a0 = (float) i / segments;
                float a1 = (float) (i + 1) / segments;
                float c0 = Mth.cos(a0 * Mth.TWO_PI), s0 = Mth.sin(a0 * Mth.TWO_PI);
                float c1 = Mth.cos(a1 * Mth.TWO_PI), s1 = Mth.sin(a1 * Mth.TWO_PI);
                o = put(m, o, c0 * innerRatio, s0 * innerRatio, 0.0F, a0, 0.0F);
                o = put(m, o, c0, s0, 0.0F, a0, 1.0F);
                o = put(m, o, c1, s1, 0.0F, a1, 1.0F);
                o = put(m, o, c1 * innerRatio, s1 * innerRatio, 0.0F, a1, 0.0F);
            }
            return m;
        });
    }

    /** Partial annulus from startDeg sweeping sweepDeg (u still runs 0..1 over the sweep). */
    public static float[] arc(int segments, float innerRatio, float startDeg, float sweepDeg) {
        String key = "arc:" + segments + ":" + innerRatio + ":" + Math.round(startDeg) + ":" + Math.round(sweepDeg);
        return CACHE.computeIfAbsent(key, k -> {
            float[] m = new float[segments * 4 * STRIDE];
            int o = 0;
            float start = startDeg * Mth.DEG_TO_RAD;
            float sweep = sweepDeg * Mth.DEG_TO_RAD;
            for (int i = 0; i < segments; i++) {
                float t0 = (float) i / segments;
                float t1 = (float) (i + 1) / segments;
                float a0 = start + t0 * sweep;
                float a1 = start + t1 * sweep;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                o = put(m, o, c0 * innerRatio, s0 * innerRatio, 0.0F, t0, 0.0F);
                o = put(m, o, c0, s0, 0.0F, t0, 1.0F);
                o = put(m, o, c1, s1, 0.0F, t1, 1.0F);
                o = put(m, o, c1 * innerRatio, s1 * innerRatio, 0.0F, t1, 0.0F);
            }
            return m;
        });
    }

    /** Unit square in XY (-1..1) with uv 0..1: planar glyph layers, billboards, marks. */
    public static float[] square() {
        return CACHE.computeIfAbsent("square", key -> {
            float[] m = new float[4 * STRIDE];
            int o = 0;
            o = put(m, o, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F);
            o = put(m, o, -1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
            o = put(m, o, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
            put(m, o, 1.0F, -1.0F, 0.0F, 1.0F, 0.0F);
            return m;
        });
    }

    /** Eight trapezoids covering the ring innerRatio..1 with planar uv (outer N-gon frames). */
    public static float[] trapezoidAnnulus(float innerRatio) {
        return CACHE.computeIfAbsent("trap:" + innerRatio, key -> {
            int n = 8;
            float[] m = new float[n * 4 * STRIDE];
            int o = 0;
            float outer = 1.45F; // square corner reach so the N-gon at radius 1 is fully inside
            for (int i = 0; i < n; i++) {
                float a0 = (float) i / n * Mth.TWO_PI;
                float a1 = (float) (i + 1) / n * Mth.TWO_PI;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                float r1 = outer / Math.max(Math.abs(c0), Math.abs(s0)) * 0.7071F;
                float r2 = outer / Math.max(Math.abs(c1), Math.abs(s1)) * 0.7071F;
                float x0 = c0 * innerRatio, y0 = s0 * innerRatio;
                float x1 = c0 * r1, y1 = s0 * r1;
                float x2 = c1 * r2, y2 = s1 * r2;
                float x3 = c1 * innerRatio, y3 = s1 * innerRatio;
                o = put(m, o, x0, y0, 0.0F, x0 * 0.5F + 0.5F, y0 * 0.5F + 0.5F);
                o = put(m, o, x1, y1, 0.0F, x1 * 0.5F + 0.5F, y1 * 0.5F + 0.5F);
                o = put(m, o, x2, y2, 0.0F, x2 * 0.5F + 0.5F, y2 * 0.5F + 0.5F);
                o = put(m, o, x3, y3, 0.0F, x3 * 0.5F + 0.5F, y3 * 0.5F + 0.5F);
            }
            return m;
        });
    }

    /** Two crossed blades along +Z from 0..1 with half-width 1 (u along, v across). */
    public static float[] tube() {
        return CACHE.computeIfAbsent("tube", key -> {
            float[] m = new float[2 * 4 * STRIDE];
            int o = 0;
            o = put(m, o, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F);
            o = put(m, o, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
            o = put(m, o, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F);
            o = put(m, o, -1.0F, 0.0F, 1.0F, 1.0F, 0.0F);
            o = put(m, o, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F);
            o = put(m, o, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
            o = put(m, o, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            put(m, o, 0.0F, -1.0F, 1.0F, 1.0F, 0.0F);
            return m;
        });
    }

    /** Vertical crossed blades from y 0..1, half-width 1 (u up, v across). */
    public static float[] column() {
        return CACHE.computeIfAbsent("column", key -> {
            float[] m = new float[2 * 4 * STRIDE];
            int o = 0;
            o = put(m, o, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F);
            o = put(m, o, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
            o = put(m, o, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
            o = put(m, o, -1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
            o = put(m, o, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F);
            o = put(m, o, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F);
            o = put(m, o, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            put(m, o, 0.0F, 1.0F, -1.0F, 1.0F, 0.0F);
            return m;
        });
    }

    /** Flat ring in XY of radius 1 with band half-width w (u around, v across): rings as beam bands. */
    public static float[] ring(int segments, float halfWidth) {
        return CACHE.computeIfAbsent("ring:" + segments + ":" + halfWidth, key -> {
            float[] m = new float[segments * 4 * STRIDE];
            int o = 0;
            for (int i = 0; i < segments; i++) {
                float t0 = (float) i / segments;
                float t1 = (float) (i + 1) / segments;
                float a0 = t0 * Mth.TWO_PI, a1 = t1 * Mth.TWO_PI;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                o = put(m, o, c0 * (1.0F - halfWidth), s0 * (1.0F - halfWidth), 0.0F, t0, 0.0F);
                o = put(m, o, c0 * (1.0F + halfWidth), s0 * (1.0F + halfWidth), 0.0F, t0, 1.0F);
                o = put(m, o, c1 * (1.0F + halfWidth), s1 * (1.0F + halfWidth), 0.0F, t1, 1.0F);
                o = put(m, o, c1 * (1.0F - halfWidth), s1 * (1.0F - halfWidth), 0.0F, t1, 0.0F);
            }
            return m;
        });
    }

    /** Vertical cylinder wall of radius 1, height 0..1; u = mirrored angle01 (seamless), v = height. */
    public static float[] cylinderWall(int segments) {
        return CACHE.computeIfAbsent("cyl:" + segments, key -> {
            float[] m = new float[segments * 4 * STRIDE];
            int o = 0;
            for (int i = 0; i < segments; i++) {
                float t0 = (float) i / segments;
                float t1 = (float) (i + 1) / segments;
                float a0 = t0 * Mth.TWO_PI, a1 = t1 * Mth.TWO_PI;
                float u0 = 1.0F - Math.abs(1.0F - 2.0F * t0);
                float u1 = 1.0F - Math.abs(1.0F - 2.0F * t1);
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                o = put(m, o, c0, 0.0F, s0, u0, 0.0F);
                o = put(m, o, c0, 1.0F, s0, u0, 1.0F);
                o = put(m, o, c1, 1.0F, s1, u1, 1.0F);
                o = put(m, o, c1, 0.0F, s1, u1, 0.0F);
            }
            return m;
        });
    }

    /** Dome (upper hemisphere, radius 1); u mirrored angle01, v latitude01 (0 equator .. 1 apex). */
    public static float[] dome(int segments, int rings) {
        return CACHE.computeIfAbsent("dome:" + segments + ":" + rings, key -> {
            float[] m = new float[segments * rings * 4 * STRIDE];
            int o = 0;
            for (int r = 0; r < rings; r++) {
                float v0 = (float) r / rings, v1 = (float) (r + 1) / rings;
                float lat0 = v0 * Mth.HALF_PI, lat1 = v1 * Mth.HALF_PI;
                float y0 = Mth.sin(lat0), y1 = Mth.sin(lat1);
                float rr0 = Mth.cos(lat0), rr1 = Mth.cos(lat1);
                for (int i = 0; i < segments; i++) {
                    float t0 = (float) i / segments, t1 = (float) (i + 1) / segments;
                    float a0 = t0 * Mth.TWO_PI, a1 = t1 * Mth.TWO_PI;
                    float u0 = 1.0F - Math.abs(1.0F - 2.0F * t0), u1 = 1.0F - Math.abs(1.0F - 2.0F * t1);
                    o = put(m, o, Mth.cos(a0) * rr0, y0, Mth.sin(a0) * rr0, u0, v0);
                    o = put(m, o, Mth.cos(a0) * rr1, y1, Mth.sin(a0) * rr1, u0, v1);
                    o = put(m, o, Mth.cos(a1) * rr1, y1, Mth.sin(a1) * rr1, u1, v1);
                    o = put(m, o, Mth.cos(a1) * rr0, y0, Mth.sin(a1) * rr0, u1, v0);
                }
            }
            return m;
        });
    }

    /** Full sphere of radius 1; u mirrored angle01, v latitude01 (0 bottom .. 1 top). */
    public static float[] sphere(int segments, int rings) {
        return CACHE.computeIfAbsent("sphere:" + segments + ":" + rings, key -> {
            float[] m = new float[segments * rings * 4 * STRIDE];
            int o = 0;
            for (int r = 0; r < rings; r++) {
                float v0 = (float) r / rings, v1 = (float) (r + 1) / rings;
                float lat0 = (v0 - 0.5F) * Mth.PI, lat1 = (v1 - 0.5F) * Mth.PI;
                float y0 = Mth.sin(lat0), y1 = Mth.sin(lat1);
                float rr0 = Mth.cos(lat0), rr1 = Mth.cos(lat1);
                for (int i = 0; i < segments; i++) {
                    float t0 = (float) i / segments, t1 = (float) (i + 1) / segments;
                    float a0 = t0 * Mth.TWO_PI, a1 = t1 * Mth.TWO_PI;
                    float u0 = 1.0F - Math.abs(1.0F - 2.0F * t0), u1 = 1.0F - Math.abs(1.0F - 2.0F * t1);
                    o = put(m, o, Mth.cos(a0) * rr0, y0, Mth.sin(a0) * rr0, u0, v0);
                    o = put(m, o, Mth.cos(a0) * rr1, y1, Mth.sin(a0) * rr1, u0, v1);
                    o = put(m, o, Mth.cos(a1) * rr1, y1, Mth.sin(a1) * rr1, u1, v1);
                    o = put(m, o, Mth.cos(a1) * rr0, y0, Mth.sin(a1) * rr0, u1, v0);
                }
            }
            return m;
        });
    }

    /** Axis-aligned box -1..1 x 0..1 x -1..1 (six faces, planar uv per face). */
    public static float[] slab() {
        return CACHE.computeIfAbsent("slab", key -> {
            float[] m = new float[6 * 4 * STRIDE];
            int o = 0;
            // +Z
            o = put(m, o, -1, 0, 1, 0, 0); o = put(m, o, -1, 1, 1, 0, 1); o = put(m, o, 1, 1, 1, 1, 1); o = put(m, o, 1, 0, 1, 1, 0);
            // -Z
            o = put(m, o, 1, 0, -1, 0, 0); o = put(m, o, 1, 1, -1, 0, 1); o = put(m, o, -1, 1, -1, 1, 1); o = put(m, o, -1, 0, -1, 1, 0);
            // +X
            o = put(m, o, 1, 0, 1, 0, 0); o = put(m, o, 1, 1, 1, 0, 1); o = put(m, o, 1, 1, -1, 1, 1); o = put(m, o, 1, 0, -1, 1, 0);
            // -X
            o = put(m, o, -1, 0, -1, 0, 0); o = put(m, o, -1, 1, -1, 0, 1); o = put(m, o, -1, 1, 1, 1, 1); o = put(m, o, -1, 0, 1, 1, 0);
            // top
            o = put(m, o, -1, 1, 1, 0, 0); o = put(m, o, -1, 1, -1, 0, 1); o = put(m, o, 1, 1, -1, 1, 1); o = put(m, o, 1, 1, 1, 1, 0);
            // bottom
            o = put(m, o, -1, 0, -1, 0, 0); o = put(m, o, -1, 0, 1, 0, 1); o = put(m, o, 1, 0, 1, 1, 1); put(m, o, 1, 0, -1, 1, 0);
            return m;
        });
    }

    /** N-sided prism of radius 1, height 0..1 (side faces + top + bottom fans as quads). */
    public static float[] prism(int sides) {
        return CACHE.computeIfAbsent("prism:" + sides, key -> {
            float[] m = new float[(sides * 3) * 4 * STRIDE];
            int o = 0;
            for (int i = 0; i < sides; i++) {
                float a0 = (float) i / sides * Mth.TWO_PI, a1 = (float) (i + 1) / sides * Mth.TWO_PI;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                o = put(m, o, c0, 0, s0, 0, 0); o = put(m, o, c0, 1, s0, 0, 1); o = put(m, o, c1, 1, s1, 1, 1); o = put(m, o, c1, 0, s1, 1, 0);
                // top wedge (as a degenerate quad)
                o = put(m, o, 0, 1, 0, 0.5F, 0.5F); o = put(m, o, c0, 1, s0, 0, 0); o = put(m, o, c1, 1, s1, 1, 0); o = put(m, o, 0, 1, 0, 0.5F, 0.5F);
                // bottom wedge
                o = put(m, o, 0, 0, 0, 0.5F, 0.5F); o = put(m, o, c1, 0, s1, 1, 0); o = put(m, o, c0, 0, s0, 0, 0); o = put(m, o, 0, 0, 0, 0.5F, 0.5F);
            }
            return m;
        });
    }

    /** Spike cluster: n tapered quads (crossed pairs) radiating from the origin, length 1, half-width 1. */
    public static float[] spikeCluster(int count) {
        return CACHE.computeIfAbsent("spikes:" + count, key -> {
            float[] m = new float[count * 2 * 4 * STRIDE];
            int o = 0;
            float golden = 2.39996F;
            for (int i = 0; i < count; i++) {
                float t = (i + 0.5F) / count;
                float y = 1.0F - 2.0F * t;
                float rr = Mth.sqrt(Math.max(0.0F, 1.0F - y * y));
                float a = i * golden;
                float dx = Mth.cos(a) * rr, dy = y, dz = Mth.sin(a) * rr;
                // orthonormal side vectors
                float sx, sy, sz;
                if (Math.abs(dy) < 0.9F) { sx = -dz; sy = 0; sz = dx; } else { sx = 1; sy = 0; sz = 0; }
                float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
                sx /= sl; sy /= sl; sz /= sl;
                float tx = dy * sz - dz * sy, ty = dz * sx - dx * sz, tz = dx * sy - dy * sx;
                float w = 0.18F;
                o = put(m, o, -sx * w, -sy * w, -sz * w, 0, 0);
                o = put(m, o, sx * w, sy * w, sz * w, 0, 1);
                o = put(m, o, dx, dy, dz, 1, 1);
                o = put(m, o, dx, dy, dz, 1, 0);
                o = put(m, o, -tx * w, -ty * w, -tz * w, 0, 0);
                o = put(m, o, tx * w, ty * w, tz * w, 0, 1);
                o = put(m, o, dx, dy, dz, 1, 1);
                o = put(m, o, dx, dy, dz, 1, 0);
            }
            return m;
        });
    }

    /** Plate fan: n overlapping flat plates stacked upward and fanned around Y (radius 1, height 1). */
    public static float[] plateFan(int plates) {
        return CACHE.computeIfAbsent("plates:" + plates, key -> {
            float[] m = new float[plates * 2 * 4 * STRIDE];
            int o = 0;
            for (int i = 0; i < plates; i++) {
                float y0 = (float) i / plates, y1 = (float) (i + 1) / plates + 0.08F;
                float a = (i * 37.0F) * Mth.DEG_TO_RAD;
                float c = Mth.cos(a), s = Mth.sin(a);
                float w = 1.0F - i * 0.06F;
                o = put(m, o, -c * w, y0, -s * w, 0, 0);
                o = put(m, o, -c * w, y1, -s * w, 0, 1);
                o = put(m, o, c * w, y1, s * w, 1, 1);
                o = put(m, o, c * w, y0, s * w, 1, 0);
                o = put(m, o, s * w * 0.35F, y0, -c * w * 0.35F, 0, 0);
                o = put(m, o, s * w * 0.35F, y1, -c * w * 0.35F, 0, 1);
                o = put(m, o, -s * w * 0.35F, y1, c * w * 0.35F, 1, 1);
                o = put(m, o, -s * w * 0.35F, y0, c * w * 0.35F, 1, 0);
            }
            return m;
        });
    }

    /** Hairline cage: 12 box edges as thin crossed quads (unit cube -1..1). */
    public static float[] cage() {
        return CACHE.computeIfAbsent("cage", key -> {
            float w = 0.03F;
            float[][] edges = {
                {-1, -1, -1, 1, -1, -1}, {-1, 1, -1, 1, 1, -1}, {-1, -1, 1, 1, -1, 1}, {-1, 1, 1, 1, 1, 1},
                {-1, -1, -1, -1, 1, -1}, {1, -1, -1, 1, 1, -1}, {-1, -1, 1, -1, 1, 1}, {1, -1, 1, 1, 1, 1},
                {-1, -1, -1, -1, -1, 1}, {1, -1, -1, 1, -1, 1}, {-1, 1, -1, -1, 1, 1}, {1, 1, -1, 1, 1, 1}};
            float[] m = new float[edges.length * 2 * 4 * STRIDE];
            int o = 0;
            for (float[] e : edges) {
                float dx = e[3] - e[0], dy = e[4] - e[1], dz = e[5] - e[2];
                float sx = dx == 0 ? w : 0, sy = dy == 0 && dx != 0 ? w : 0, sz = dz == 0 && (dx != 0 || dy != 0) && dy == 0 ? 0 : 0;
                if (dx != 0) { sx = 0; sy = w; sz = 0; } else if (dy != 0) { sx = w; sy = 0; sz = 0; } else { sx = w; sy = 0; sz = 0; }
                float tx = dx != 0 ? 0 : (dy != 0 ? 0 : 0), ty = dx != 0 ? 0 : (dy != 0 ? 0 : w), tz = dx != 0 ? w : (dy != 0 ? w : 0);
                o = put(m, o, e[0] - sx, e[1] - sy, e[2] - sz, 0, 0);
                o = put(m, o, e[0] + sx, e[1] + sy, e[2] + sz, 0, 1);
                o = put(m, o, e[3] + sx, e[4] + sy, e[5] + sz, 1, 1);
                o = put(m, o, e[3] - sx, e[4] - sy, e[5] - sz, 1, 0);
                o = put(m, o, e[0] - tx, e[1] - ty, e[2] - tz, 0, 0);
                o = put(m, o, e[0] + tx, e[1] + ty, e[2] + tz, 0, 1);
                o = put(m, o, e[3] + tx, e[4] + ty, e[5] + tz, 1, 1);
                o = put(m, o, e[3] - tx, e[4] - ty, e[5] - tz, 1, 0);
            }
            return m;
        });
    }

    /** Low-poly boulder: a jittered sphere (radius ~1) with facet uvs. */
    public static float[] boulder(int seed) {
        return CACHE.computeIfAbsent("boulder:" + seed, key -> {
            int segments = 8, rings = 5;
            float[] base = sphere(segments, rings);
            float[] m = base.clone();
            for (int i = 0; i < m.length; i += STRIDE) {
                float x = m[i], y = m[i + 1], z = m[i + 2];
                float h = (float) Math.sin(x * 12.9898 + y * 78.233 + z * 37.719 + seed) * 43758.5453F;
                h = h - (float) Math.floor(h);
                float scale = 0.82F + h * 0.3F;
                m[i] = x * scale;
                m[i + 1] = (y * 0.5F + 0.5F) * scale;
                m[i + 2] = z * scale;
            }
            return m;
        });
    }

    private static int put(float[] m, int o, float x, float y, float z, float u, float v) {
        m[o] = x;
        m[o + 1] = y;
        m[o + 2] = z;
        m[o + 3] = u;
        m[o + 4] = v;
        return o + STRIDE;
    }
}
