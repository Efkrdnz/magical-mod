package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeEffectRenderer;
import com.efkrdnz.magical.client.renderer.FusionGeometry;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import org.joml.Matrix4f;

/**
 * The eight element impacts. Everything but the storm fork is a wedge of the unit disc drawn on
 * {@code forgeImpact()}, so the shader's own core, rim and spokes do most of the work and these
 * methods only say where the pieces go; the fork is a bolt of blade on {@code forgeEdge()}.
 *
 * <p>The caller has already put the pose in the right frame — camera-facing, flat on the ground or
 * world-aligned — so every method here draws in the local XY plane unless it says otherwise.</p>
 */
public final class ForgeImpactGeometry {

    private static final int SPIRAL_STEPS = 14;
    private static final int BOLT_STEPS = 7;
    private static final int BRANCHES = 2;
    private static final float BOLT_WIDTH = 0.09f;
    private static final float BOLT_JITTER = 0.35f;

    private ForgeImpactGeometry() {}

    /** FIRE: ember petals thrown outward from a hot middle as the bloom opens. */
    public static void fireBloom(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(235.0f, state.alpha);
        float spread = state.scale * (0.25f + 0.85f * state.progress);
        float petal = state.scale * 0.42f * (1.0f - 0.5f * state.progress);
        for (int i = 0; i < 5; i++) {
            float angle = Mth.TWO_PI * i / 5.0f + state.progress * 0.6f;
            ForgeDisc.blob(disc, pose, Mth.cos(angle) * spread, Mth.sin(angle) * spread, petal,
                    palette.primary(), alpha);
        }
        ForgeDisc.blob(disc, pose, 0.0f, 0.0f, state.scale * 0.5f, palette.bloom(), alpha);
    }

    /** FROST: six crystal slivers standing out of the point of impact. */
    public static void frostShards(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(245.0f, state.alpha);
        float reach = state.scale * (0.5f + 0.6f * state.progress);
        for (int i = 0; i < 6; i++) {
            float angle = Mth.TWO_PI * i / 6.0f;
            ForgeDisc.wedge(disc, pose, reach, angle - 0.10f, angle + 0.10f, 0.15f, 1.0f, palette.bloom(), alpha);
        }
    }

    /** STORM: the chain bolt, jagged from this impact to whatever it jumped to. */
    public static void stormFork(VertexConsumer edge, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        RandomSource rng = RandomSource.create(state.seed);
        int alpha = ForgeRibbon.alpha(255.0f, state.alpha);
        float[] end = {state.endX, state.endY, state.endZ};
        bolt(edge, pose, new float[3], end, rng, palette.bloom(), alpha);
        for (int i = 0; i < BRANCHES; i++) {
            float t = 0.35f + 0.3f * i;
            float[] from = {end[0] * t, end[1] * t, end[2] * t};
            float[] to = {from[0] + spread(rng), from[1] + spread(rng), from[2] + spread(rng)};
            bolt(edge, pose, from, to, rng, palette.primary(), alpha / 2);
        }
    }

    /** VOID: a ring falling inward onto a core in the colour the element is not. */
    public static void voidImplosion(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(240.0f, state.alpha);
        float radius = state.scale * (1.3f - state.progress);
        ForgeDisc.wedge(disc, pose, radius, 0.0f, Mth.TWO_PI, 0.78f, 1.0f, palette.primary(), alpha);
        ForgeDisc.blob(disc, pose, 0.0f, 0.0f, state.scale * 0.45f * state.progress, palette.inverted(), alpha);
    }

    /** RADIANT: a four-armed flare standing in its own bloom. */
    public static void radiantCross(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(250.0f, state.alpha);
        float reach = state.scale * (0.7f + 0.8f * state.progress);
        ForgeDisc.blob(disc, pose, 0.0f, 0.0f, reach * 0.7f, palette.secondary(), alpha / 3);
        for (int i = 0; i < 4; i++) {
            float angle = Mth.HALF_PI * i;
            ForgeDisc.wedge(disc, pose, reach, angle - 0.07f, angle + 0.07f, 0.0f, 1.0f, palette.bloom(), alpha);
        }
    }

    /** VENOM: three droplets sliding down out of the wound. */
    public static void venomDrip(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(225.0f, state.alpha);
        float fall = state.scale * state.progress * 1.4f;
        for (int i = 0; i < 3; i++) {
            float offset = (i - 1) * state.scale * 0.45f;
            ForgeDisc.blob(disc, pose, offset, -fall - i * 0.12f, state.scale * 0.22f, palette.primary(), alpha);
        }
    }

    /** TERRA: angular slabs shouldered up out of the ground. The caller lays this one flat. */
    public static void terraShards(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(235.0f, state.alpha);
        float reach = state.scale * (0.6f + 0.7f * state.progress);
        for (int i = 0; i < 5; i++) {
            float angle = Mth.TWO_PI * i / 5.0f + 0.4f;
            ForgeDisc.wedge(disc, pose, reach, angle - 0.22f, angle + 0.22f, 0.45f, 1.0f, palette.primary(), alpha);
        }
    }

    /** GALE: two ribbons spiralling out of the same middle, half a turn apart. */
    public static void galeSwirl(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(215.0f, state.alpha);
        for (int arm = 0; arm < 2; arm++) {
            float base = Mth.PI * arm + state.progress * 2.0f;
            for (int i = 0; i < SPIRAL_STEPS; i++) {
                float t0 = i / (float) SPIRAL_STEPS;
                float t1 = (i + 1) / (float) SPIRAL_STEPS;
                ForgeDisc.wedge(disc, pose, state.scale * 1.2f, base + t0 * Mth.PI, base + t1 * Mth.PI,
                        t0 * 0.9f, t0 * 0.9f + 0.16f, palette.primary(), alpha);
            }
        }
    }

    /** A jagged crossed ribbon from one point to another, wandering off the straight line as it goes. */
    private static void bolt(VertexConsumer edge, Matrix4f pose, float[] from, float[] to, RandomSource rng,
            int color, int alpha) {
        float[] previous = from;
        for (int i = 1; i <= BOLT_STEPS; i++) {
            float t = i / (float) BOLT_STEPS;
            boolean last = i == BOLT_STEPS;
            float[] next = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                next[axis] = Mth.lerp(t, from[axis], to[axis]) + (last ? 0.0f : spread(rng));
            }
            spark(edge, pose, previous, next, color, alpha);
            previous = next;
        }
    }

    private static void spark(VertexConsumer edge, Matrix4f pose, float[] a, float[] b, int color, int alpha) {
        int r = FusionGeometry.red(color);
        int g = FusionGeometry.green(color);
        int blue = FusionGeometry.blue(color);
        for (int axis = 0; axis < 2; axis++) {
            float ox = axis == 0 ? 0.0f : BOLT_WIDTH;
            float oy = axis == 0 ? BOLT_WIDTH : 0.0f;
            FusionGeometry.quad(edge, pose,
                    a[0] - ox, a[1] - oy, a[2], 0.0f, 0.0f,
                    a[0] + ox, a[1] + oy, a[2], 0.0f, 1.0f,
                    b[0] + ox, b[1] + oy, b[2], 1.0f, 1.0f,
                    b[0] - ox, b[1] - oy, b[2], 1.0f, 0.0f,
                    r, g, blue, alpha);
        }
    }

    private static float spread(RandomSource rng) {
        return (rng.nextFloat() - 0.5f) * BOLT_JITTER;
    }
}
