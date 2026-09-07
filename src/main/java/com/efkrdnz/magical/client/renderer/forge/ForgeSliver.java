package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.FusionGeometry;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * The small flat pieces the element ornaments are made of, and the UV contract they owe the edge
 * shader.
 *
 * <p>{@code rendertype_forge_edge.fsh} reads {@code texCoord0.x} as the length axis — its
 * {@code 0.04 / 0.96} end cutoff and its fbm flow run along it — and {@code texCoord0.y} as the
 * width axis, with the white-hot spine at {@code 0.5} and the hard cutting lip at {@code 1}. So u
 * runs ALONG a piece and v runs ACROSS it. Swapping the two lights a sliver down one long lip and
 * blanks both of its ends, which is what a shard reduced to a floating dot at its tip.</p>
 */
public final class ForgeSliver {

    /** a = start-left, b = start-right, c = end-right, d = end-left: u along, v across. */
    private static final float[] ALONG_UV = {0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f};
    /** a = base-left, b = tip, c = tip, d = base-right: u base-to-tip, spine down the middle. */
    private static final float[] SPIKE_UV = {0.0f, 0.0f, 1.0f, 0.5f, 1.0f, 0.5f, 0.0f, 1.0f};

    private ForgeSliver() {}

    /** A flat sliver between two in-plane points, widened perpendicular to its own direction. */
    public static void link(VertexConsumer edge, Matrix4f pose, Plane plane, float[] from, float[] to,
            float halfWidth, int color, int alpha) {
        float du = to[0] - from[0];
        float dv = to[1] - from[1];
        float length = Mth.sqrt(du * du + dv * dv);
        if (length < 1.0E-4f) {
            return;
        }
        float nu = -dv / length * halfWidth;
        float nv = du / length * halfWidth;
        quad(edge, pose,
                ForgeRibbon.planar(plane, from[0] + nu, from[1] + nv),
                ForgeRibbon.planar(plane, from[0] - nu, from[1] - nv),
                ForgeRibbon.planar(plane, to[0] - nu, to[1] - nv),
                ForgeRibbon.planar(plane, to[0] + nu, to[1] + nv),
                ALONG_UV, color, alpha);
    }

    /**
     * A tail falling straight down from a point already in local space, widened across local X. The
     * renderer has only yawed and pitched the pose, so local −Y is the direction gravity would take
     * the drop — unlike a plane's own second axis, which is −Z for both GROUND and FORWARD sweeps.
     */
    public static void tail(VertexConsumer edge, Matrix4f pose, float[] head, float length, float halfWidth,
            int color, int alpha) {
        float bottom = head[1] - length;
        quad(edge, pose,
                new float[] {head[0] - halfWidth, head[1], head[2]},
                new float[] {head[0] + halfWidth, head[1], head[2]},
                new float[] {head[0] + halfWidth, bottom, head[2]},
                new float[] {head[0] - halfWidth, bottom, head[2]},
                ALONG_UV, color, alpha);
    }

    /** A spike, emitted as a quad with its two middle corners collapsed onto the tip. */
    public static void spike(VertexConsumer edge, Matrix4f pose, Plane plane, float[] left, float[] tip,
            float[] right, int color, int alpha) {
        float[] point = ForgeRibbon.planar(plane, tip[0], tip[1]);
        quad(edge, pose, ForgeRibbon.planar(plane, left[0], left[1]), point, point,
                ForgeRibbon.planar(plane, right[0], right[1]), SPIKE_UV, color, alpha);
    }

    private static void quad(VertexConsumer edge, Matrix4f pose, float[] a, float[] b, float[] c, float[] d,
            float[] uv, int color, int alpha) {
        FusionGeometry.quad(edge, pose,
                a[0], a[1], a[2], uv[0], uv[1],
                b[0], b[1], b[2], uv[2], uv[3],
                c[0], c[1], c[2], uv[4], uv[5],
                d[0], d[1], d[2], uv[6], uv[7],
                FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), alpha);
    }
}
