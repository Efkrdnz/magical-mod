package com.efkrdnz.magical.client.fx;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Client-side struct-of-arrays pool of identity-carrying spell particles rendered with the
 * smoke_veil shaders in the AFTER_PARTICLES pass. No vanilla ParticleTypes are involved, so the
 * roster's trails and impact matter never depend on the particle engine API.
 */
public final class SpellParticles {
    private static final int CAPACITY = 4096;
    private static final double[] X = new double[CAPACITY];
    private static final double[] Y = new double[CAPACITY];
    private static final double[] Z = new double[CAPACITY];
    private static final double[] PX = new double[CAPACITY];
    private static final double[] PY = new double[CAPACITY];
    private static final double[] PZ = new double[CAPACITY];
    private static final float[] VX = new float[CAPACITY];
    private static final float[] VY = new float[CAPACITY];
    private static final float[] VZ = new float[CAPACITY];
    private static final float[] SIZE = new float[CAPACITY];
    private static final float[] INTENSITY = new float[CAPACITY];
    private static final float[] DRAG = new float[CAPACITY];
    private static final float[] GRAVITY = new float[CAPACITY];
    private static final int[] RGB = new int[CAPACITY];
    private static final int[] KIND = new int[CAPACITY];
    private static final int[] PARAM = new int[CAPACITY];
    private static final int[] SEED = new int[CAPACITY];
    private static final int[] AGE = new int[CAPACITY];
    private static final int[] LIFE = new int[CAPACITY];
    private static int count;
    private static int cursor;

    private SpellParticles() {}

    public static int count() {
        return count;
    }

    public static void clear() {
        count = 0;
        cursor = 0;
    }

    /**
     * @param shade 0..31 (0 = dark ink, 31 = lit); for RUNE_MOTE the atlas cell (count | paramB<<6) is packed by the caller into kind/param
     */
    public static void spawn(FxKinds.Smoke kind, double x, double y, double z, double vx, double vy, double vz, float size, int life, int rgb, float intensity, int shade, float drag, float gravity) {
        int i;
        if (count < CAPACITY) {
            i = count++;
        } else {
            i = cursor;
            cursor = (cursor + 1) % CAPACITY;
        }
        X[i] = x; Y[i] = y; Z[i] = z;
        PX[i] = x; PY[i] = y; PZ[i] = z;
        VX[i] = (float) vx; VY[i] = (float) vy; VZ[i] = (float) vz;
        SIZE[i] = size;
        INTENSITY[i] = intensity;
        DRAG[i] = drag;
        GRAVITY[i] = gravity;
        RGB[i] = rgb;
        KIND[i] = kind.id();
        // RUNE_MOTE carries an atlas cell (up to 11 bits) instead of a shade
        PARAM[i] = kind == FxKinds.Smoke.RUNE_MOTE ? Math.max(0, shade) : Mth.clamp(shade, 0, 31);
        SEED[i] = (int) ((x * 31 + y * 17 + z * 7 + i) % 64 + 64) % 64;
        AGE[i] = 0;
        LIFE[i] = Math.max(1, life);
    }

    /** Emit a burst around a point: half along dir (reflected), half tangential. */
    public static void burst(FxKinds.Smoke kind, Vec3 pos, Vec3 dir, int total, float speed, float size, int life, int rgb, float intensity, int shade) {
        int n = Math.max(1, Math.round(total * FxBudget.lodMultiplier()));
        for (int i = 0; i < n; i++) {
            float h1 = hash(i * 3 + 1 + (int) (pos.x * 7));
            float h2 = hash(i * 5 + 2 + (int) (pos.z * 11));
            float h3 = hash(i * 7 + 3 + (int) (pos.y * 13));
            Vec3 v;
            if (i % 2 == 0) {
                v = dir.scale(0.5F + h1).add((h2 - 0.5F) * 0.8F, (h3 - 0.5F) * 0.8F, (h1 - 0.5F) * 0.8F);
            } else {
                float a = h1 * Mth.TWO_PI;
                v = new Vec3(Mth.cos(a), (h3 - 0.3F) * 0.8F, Mth.sin(a));
            }
            v = v.normalize().scale(speed * (0.4F + h2));
            spawn(kind, pos.x, pos.y, pos.z, v.x, v.y, v.z, size * (0.6F + h3 * 0.8F), Math.round(life * (0.6F + h1 * 0.8F)), rgb, intensity, shade, 0.9F, kind == FxKinds.Smoke.SPARK_STREAK || kind == FxKinds.Smoke.GLASS_SPLINTER || kind == FxKinds.Smoke.DROPLET ? 0.04F : 0.002F);
        }
    }

    public static void tick() {
        int write = 0;
        for (int i = 0; i < count; i++) {
            if (++AGE[i] >= LIFE[i]) {
                continue;
            }
            PX[i] = X[i]; PY[i] = Y[i]; PZ[i] = Z[i];
            VY[i] -= GRAVITY[i];
            VX[i] *= DRAG[i]; VY[i] *= DRAG[i]; VZ[i] *= DRAG[i];
            X[i] += VX[i]; Y[i] += VY[i]; Z[i] += VZ[i];
            if (write != i) {
                copy(i, write);
            }
            write++;
        }
        count = write;
        cursor = 0;
    }

    private static void copy(int from, int to) {
        X[to] = X[from]; Y[to] = Y[from]; Z[to] = Z[from];
        PX[to] = PX[from]; PY[to] = PY[from]; PZ[to] = PZ[from];
        VX[to] = VX[from]; VY[to] = VY[from]; VZ[to] = VZ[from];
        SIZE[to] = SIZE[from]; INTENSITY[to] = INTENSITY[from]; DRAG[to] = DRAG[from]; GRAVITY[to] = GRAVITY[from];
        RGB[to] = RGB[from]; KIND[to] = KIND[from]; PARAM[to] = PARAM[from]; SEED[to] = SEED[from];
        AGE[to] = AGE[from]; LIFE[to] = LIFE[from];
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float partialTick) {
        if (count == 0) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Quaternionf orientation = camera.rotation();
        VertexConsumer dark = null;
        VertexConsumer bright = null;
        FxKinds.Smoke[] kinds = FxKinds.Smoke.values();
        for (int i = 0; i < count; i++) {
            FxKinds.Smoke kind = kinds[Math.floorMod(KIND[i], kinds.length)];
            boolean isDark = kind.dark();
            VertexConsumer consumer;
            if (isDark) {
                if (dark == null) {
                    dark = buffers.getBuffer(MagicalFxRenderTypes.smokeVeil());
                }
                consumer = dark;
            } else {
                if (bright == null) {
                    bright = buffers.getBuffer(MagicalFxRenderTypes.smokeAdd());
                }
                consumer = bright;
            }
            double x = Mth.lerp(partialTick, PX[i], X[i]) - cam.x;
            double y = Mth.lerp(partialTick, PY[i], Y[i]) - cam.y;
            double z = Mth.lerp(partialTick, PZ[i], Z[i]) - cam.z;
            float life = Mth.clamp((AGE[i] + partialTick) / LIFE[i], 0.0F, 1.0F);
            int packed = kind == FxKinds.Smoke.RUNE_MOTE
                    ? MagicVertex.pack(KIND[i], PARAM[i] & 63, (PARAM[i] >> 6) & 31, life, SEED[i], 0)
                    : MagicVertex.pack(KIND[i], 40, PARAM[i], life, SEED[i], isDark ? 1 : 0);
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(orientation);
            if (kind.stretched()) {
                // orient the quad along the screen-space velocity: approximate with the velocity's yaw
                float speed = Mth.sqrt(VX[i] * VX[i] + VY[i] * VY[i] + VZ[i] * VZ[i]);
                float ang = (float) Math.toDegrees(Math.atan2(VY[i], Math.max(1.0E-4F, Mth.sqrt(VX[i] * VX[i] + VZ[i] * VZ[i]))));
                pose.mulPose(Axis.ZP.rotationDegrees(ang - 90.0F));
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.square(), SIZE[i], SIZE[i] * (1.0F + speed * 12.0F), 1.0F, RGB[i], INTENSITY[i], packed);
            } else {
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.square(), SIZE[i], SIZE[i], 1.0F, RGB[i], INTENSITY[i], packed);
            }
            pose.popPose();
        }
        FxBudget.countQuads(count);
        // each type is ended by the caller's endBatch order (fixed buffers) or explicitly here
        buffers.endBatch(MagicalFxRenderTypes.smokeVeil());
        buffers.endBatch(MagicalFxRenderTypes.smokeAdd());
    }

    private static float hash(int n) {
        n = (n ^ 61) ^ (n >>> 16);
        n *= 9;
        n = n ^ (n >>> 4);
        n *= 0x27d4eb2d;
        n = n ^ (n >>> 15);
        return (n & 0xFFFF) / (float) 0xFFFF;
    }
}
