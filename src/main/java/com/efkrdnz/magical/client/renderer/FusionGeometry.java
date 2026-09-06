package com.efkrdnz.magical.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Small shared quad/ring/tube builders for the fusion spell renderers. All emit into a
 * POSITION_TEX_COLOR quad buffer; the fusion_orb / fusion_beam shaders turn the UVs into glow.
 */
final class FusionGeometry {
    private FusionGeometry() {}

    static int red(int color) {
        return color >> 16 & 255;
    }

    static int green(int color) {
        return color >> 8 & 255;
    }

    static int blue(int color) {
        return color & 255;
    }

    /** A flat quad in local space with explicit UVs. */
    static void quad(VertexConsumer consumer, Matrix4f matrix,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float x4, float y4, float z4, float u4, float v4,
            int r, int g, int b, int a) {
        if (a <= 0) {
            return;
        }
        consumer.addVertex(matrix, x1, y1, z1).setUv(u1, v1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, z2).setUv(u2, v2).setColor(r, g, b, a);
        consumer.addVertex(matrix, x3, y3, z3).setUv(u3, v3).setColor(r, g, b, a);
        consumer.addVertex(matrix, x4, y4, z4).setUv(u4, v4).setColor(r, g, b, a);
    }

    /** A camera-facing square centred on the origin; the orb shader masks it to a glowing disc. */
    static void orb(VertexConsumer consumer, Matrix4f matrix, float radius, int r, int g, int b, int a) {
        quad(consumer, matrix,
                -radius, -radius, 0.0F, 0.0F, 0.0F,
                radius, -radius, 0.0F, 1.0F, 0.0F,
                radius, radius, 0.0F, 1.0F, 1.0F,
                -radius, radius, 0.0F, 0.0F, 1.0F,
                r, g, b, a);
    }

    /** A flat ring in the local XY plane (UV x runs around the loop, y across the band). */
    static void ring(VertexConsumer consumer, Matrix4f matrix, float radius, float thickness, int segments, int r, int g, int b, int a) {
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float a0 = Mth.TWO_PI * t0;
            float a1 = Mth.TWO_PI * t1;
            float inner0x = Mth.cos(a0) * (radius - thickness);
            float inner0y = Mth.sin(a0) * (radius - thickness);
            float outer0x = Mth.cos(a0) * (radius + thickness);
            float outer0y = Mth.sin(a0) * (radius + thickness);
            float inner1x = Mth.cos(a1) * (radius - thickness);
            float inner1y = Mth.sin(a1) * (radius - thickness);
            float outer1x = Mth.cos(a1) * (radius + thickness);
            float outer1y = Mth.sin(a1) * (radius + thickness);
            quad(consumer, matrix,
                    inner0x, inner0y, 0.0F, t0, 0.0F,
                    outer0x, outer0y, 0.0F, t0, 1.0F,
                    outer1x, outer1y, 0.0F, t1, 1.0F,
                    inner1x, inner1y, 0.0F, t1, 0.0F,
                    r, g, b, a);
        }
    }

    /** A crossed tube along local +Z from z0 to z1 (two perpendicular blades), UV x along its length. */
    static void tube(VertexConsumer consumer, Matrix4f matrix, float z0, float z1, float halfWidth, int r, int g, int b, int a) {
        quad(consumer, matrix,
                -halfWidth, 0.0F, z0, 0.0F, 0.0F,
                halfWidth, 0.0F, z0, 0.0F, 1.0F,
                halfWidth, 0.0F, z1, 1.0F, 1.0F,
                -halfWidth, 0.0F, z1, 1.0F, 0.0F,
                r, g, b, a);
        quad(consumer, matrix,
                0.0F, -halfWidth, z0, 0.0F, 0.0F,
                0.0F, halfWidth, z0, 0.0F, 1.0F,
                0.0F, halfWidth, z1, 1.0F, 1.0F,
                0.0F, -halfWidth, z1, 1.0F, 0.0F,
                r, g, b, a);
    }

    /** A crossed vertical column from y0 to y1 (two perpendicular blades), UV x running up its height. */
    static void column(VertexConsumer consumer, Matrix4f matrix, float y0, float y1, float halfWidth, int r, int g, int b, int a) {
        quad(consumer, matrix,
                -halfWidth, y0, 0.0F, 0.0F, 0.0F,
                halfWidth, y0, 0.0F, 0.0F, 1.0F,
                halfWidth, y1, 0.0F, 1.0F, 1.0F,
                -halfWidth, y1, 0.0F, 1.0F, 0.0F,
                r, g, b, a);
        quad(consumer, matrix,
                0.0F, y0, -halfWidth, 0.0F, 0.0F,
                0.0F, y0, halfWidth, 0.0F, 1.0F,
                0.0F, y1, halfWidth, 1.0F, 1.0F,
                0.0F, y1, -halfWidth, 1.0F, 0.0F,
                r, g, b, a);
    }

    static int fade(int base, float factor) {
        return Mth.clamp(Math.round(base * factor), 0, 255);
    }
}
