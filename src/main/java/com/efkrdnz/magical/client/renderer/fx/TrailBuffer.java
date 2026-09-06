package com.efkrdnz.magical.client.renderer.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Per-entity ring buffer of the last N positions, rendered as camera-facing filament quads with
 * the "along" coordinate remapped to segment age so trails fade continuously.
 */
public final class TrailBuffer {
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final int[] ticks;
    private int head;
    private int size;

    public TrailBuffer(int capacity) {
        xs = new double[capacity];
        ys = new double[capacity];
        zs = new double[capacity];
        ticks = new int[capacity];
    }

    public void push(double x, double y, double z, int tick) {
        if (size > 0) {
            int last = (head - 1 + xs.length) % xs.length;
            if (ticks[last] == tick) {
                xs[last] = x;
                ys[last] = y;
                zs[last] = z;
                return;
            }
        }
        xs[head] = x;
        ys[head] = y;
        zs[head] = z;
        ticks[head] = tick;
        head = (head + 1) % xs.length;
        size = Math.min(size + 1, xs.length);
    }

    public int size() {
        return size;
    }

    /**
     * Emit the trail as one filament ribbon. Positions are world-space; origin is subtracted so the
     * caller's pose can be at the effect origin. Width tapers from headWidth at the newest point to 0.
     */
    public void emit(VertexConsumer consumer, Matrix4f matrix, Vec3 origin, Vec3 cameraPos, float headWidth, int rgb, float opacity, int packedBase, int maxSegments) {
        int n = Math.min(size, maxSegments + 1);
        if (n < 2) {
            return;
        }
        Vector3f prevA = null;
        Vector3f prevB = null;
        for (int i = 0; i < n; i++) {
            int idx = (head - 1 - i + xs.length * 2) % xs.length;
            float t = i / (float) (n - 1); // 0 newest .. 1 oldest
            float px = (float) (xs[idx] - origin.x);
            float py = (float) (ys[idx] - origin.y);
            float pz = (float) (zs[idx] - origin.z);
            // direction to the next-older point for the side vector
            int nidx = (head - 1 - Math.min(i + 1, n - 1) + xs.length * 2) % xs.length;
            float dx = (float) (xs[idx] - xs[nidx]);
            float dy = (float) (ys[idx] - ys[nidx]);
            float dz = (float) (zs[idx] - zs[nidx]);
            float cx = (float) (cameraPos.x - xs[idx]);
            float cy = (float) (cameraPos.y - ys[idx]);
            float cz = (float) (cameraPos.z - zs[idx]);
            // side = normalize(dir x toCamera)
            float sx = dy * cz - dz * cy;
            float sy = dz * cx - dx * cz;
            float sz = dx * cy - dy * cx;
            float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0E-4F) {
                sx = 1.0F; sy = 0.0F; sz = 0.0F;
            } else {
                sx /= sl; sy /= sl; sz /= sl;
            }
            float w = headWidth * (1.0F - t);
            Vector3f a = new Vector3f(px - sx * w, py - sy * w, pz - sz * w);
            Vector3f b = new Vector3f(px + sx * w, py + sy * w, pz + sz * w);
            if (prevA != null) {
                float t0 = (i - 1) / (float) (n - 1);
                // along = 1 - age so the head is the bright tip
                int packed = MagicVertex.withPhase(packedBase, 0.5F);
                MagicVertex.emit(consumer, matrix, prevA.x, prevA.y, prevA.z, 1.0F - t0, 0.0F, rgb, opacity * (1.0F - t0), packed);
                MagicVertex.emit(consumer, matrix, prevB.x, prevB.y, prevB.z, 1.0F - t0, 1.0F, rgb, opacity * (1.0F - t0), packed);
                MagicVertex.emit(consumer, matrix, b.x, b.y, b.z, 1.0F - t, 1.0F, rgb, opacity * (1.0F - t), packed);
                MagicVertex.emit(consumer, matrix, a.x, a.y, a.z, 1.0F - t, 0.0F, rgb, opacity * (1.0F - t), packed);
            }
            prevA = a;
            prevB = b;
        }
    }
}
