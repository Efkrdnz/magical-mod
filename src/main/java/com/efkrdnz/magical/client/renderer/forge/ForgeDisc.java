package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.FusionGeometry;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * The two primitives every {@code forgeImpact()} draw is made of. Both keep the shader's contract:
 * whatever slice of the disc is being drawn, the UVs stay the unit disc's own, so the shader's
 * radius, rim and spokes line up with the geometry instead of being stretched over it.
 */
public final class ForgeDisc {

    private static final float STEPS_PER_RADIAN = 6.0f;

    private ForgeDisc() {}

    /** A wedge of the unit disc, from one angle to another and from one radius out to another. */
    public static void wedge(VertexConsumer disc, Matrix4f pose, float scale, float from, float to, float inner,
            float outer, int color, int alpha) {
        int steps = Math.max(1, Math.round(Math.abs(to - from) * STEPS_PER_RADIAN));
        for (int i = 0; i < steps; i++) {
            float a0 = Mth.lerp(i / (float) steps, from, to);
            float a1 = Mth.lerp((i + 1) / (float) steps, from, to);
            corner(disc, pose, scale, a0, inner, color, alpha);
            corner(disc, pose, scale, a0, outer, color, alpha);
            corner(disc, pose, scale, a1, outer, color, alpha);
            corner(disc, pose, scale, a1, inner, color, alpha);
        }
    }

    /** A round blob centred anywhere in the local plane, carrying its own full unit-disc UVs. */
    public static void blob(VertexConsumer disc, Matrix4f pose, float x, float y, float radius, int color,
            int alpha) {
        if (radius <= 0.0f || alpha <= 0) {
            return;
        }
        FusionGeometry.quad(disc, pose,
                x - radius, y - radius, 0.0f, 0.0f, 0.0f,
                x - radius, y + radius, 0.0f, 0.0f, 1.0f,
                x + radius, y + radius, 0.0f, 1.0f, 1.0f,
                x + radius, y - radius, 0.0f, 1.0f, 0.0f,
                FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), alpha);
    }

    private static void corner(VertexConsumer disc, Matrix4f pose, float scale, float angle, float radius,
            int color, int alpha) {
        float u = Mth.cos(angle) * radius;
        float v = Mth.sin(angle) * radius;
        disc.addVertex(pose, u * scale, v * scale, 0.0f)
                .setUv(0.5f + 0.5f * u, 0.5f + 0.5f * v)
                .setColor(FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), alpha);
    }
}
