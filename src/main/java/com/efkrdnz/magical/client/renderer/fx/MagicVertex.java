package com.efkrdnz.magical.client.renderer.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The vertex contract shared by every library shader: POSITION_COLOR_TEX_LIGHTMAP where the
 * lightmap slot (UV2) carries 32 bits of integer data decoded in the vertex shader.
 *
 * <pre>
 *   x = kind:5 | count:6 &lt;&lt; 5 | paramB:5 &lt;&lt; 11
 *   y = phase:8 | seed:6 &lt;&lt; 8 | mode:2 &lt;&lt; 14
 * </pre>
 */
public final class MagicVertex {
    private MagicVertex() {}

    public static int pack(int kind, int count, int paramB, float phase01, int seed, int mode) {
        int x = (kind & 31) | ((count & 63) << 5) | ((paramB & 31) << 11);
        int phase = Mth.clamp(Math.round(Mth.clamp(phase01, 0.0F, 1.0F) * 255.0F), 0, 255);
        int y = phase | ((seed & 63) << 8) | ((mode & 3) << 14);
        return (x & 0xFFFF) | (y << 16);
    }

    public static int withPhase(int packed, float phase01) {
        int phase = Mth.clamp(Math.round(Mth.clamp(phase01, 0.0F, 1.0F) * 255.0F), 0, 255);
        return (packed & ~(0xFF << 16)) | (phase << 16);
    }

    public static int seedOf(long value) {
        return (int) (Math.floorMod(value * 0x9E3779B97F4A7C15L >>> 33, 64L));
    }

    public static void emit(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v, int rgb, float opacity, int packed) {
        int alpha = Mth.clamp(Math.round(opacity * 255.0F), 0, 255);
        if (alpha <= 0) {
            alpha = 1;
        }
        consumer.addVertex(matrix, x, y, z)
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
                .setUv(u, v)
                .setUv2(packed & 0xFFFF, (packed >>> 16) & 0xFFFF);
    }

    /** Convenience quad: four corners in order, uv 0..1 mapped (x0,y0)=(u0,v0). */
    public static void quad(VertexConsumer consumer, Matrix4f matrix,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float v0, float u1, float v1, int rgb, float opacity, int packed) {
        emit(consumer, matrix, x0, y0, z0, u0, v0, rgb, opacity, packed);
        emit(consumer, matrix, x1, y1, z1, u0, v1, rgb, opacity, packed);
        emit(consumer, matrix, x2, y2, z2, u1, v1, rgb, opacity, packed);
        emit(consumer, matrix, x3, y3, z3, u1, v0, rgb, opacity, packed);
    }
}
