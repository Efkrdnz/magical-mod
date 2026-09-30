package com.efkrdnz.magical.client.renderer.forge;

import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/**
 * One vertex of a textured forge quad, carrying its own colour and alpha.
 *
 * <p>{@code FusionGeometry.quad} gives a whole quad one alpha, which was all the shader-drawn
 * ribbon needed. A textured smear fades from its tail to its head across the quads of one arc, and
 * a lance runs from its body colour on the spine to its edge colour at the lip, so each corner has
 * to say its own.
 */
public final class ForgeQuads {

    private ForgeQuads() {}

    public static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, float u, float v,
            int rgb, int alpha) {
        consumer.addVertex(pose, x, y, z).setUv(u, v)
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.max(0, Math.min(255, alpha)));
    }

    /** A point as a three-float array, for the geometry that builds its corners that way. */
    public static void vertex(VertexConsumer consumer, Matrix4f pose, float[] p, float u, float v, int rgb,
            int alpha) {
        vertex(consumer, pose, p[0], p[1], p[2], u, v, rgb, alpha);
    }
}
