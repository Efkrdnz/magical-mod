package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * FLURRY: the string of jabs. One short ribbon per pulse, fanned across the cone and staggered in
 * height, each one appearing on the tick its own hit lands and dying on its own schedule — the
 * pulse ticks are read from {@link ForgeStrikeMath} so the picture cannot drift from the damage.
 */
public final class FlurryGeometry {

    private static final float HEIGHT = 1.2f;
    private static final float RIBBON_ARC = 34.0f;
    private static final float FAN = 0.32f;
    private static final float THICKNESS = 0.32f;
    private static final float PULSE_FADE = 4.0f;
    private static final float STAGGER = 0.18f;

    private FlurryGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        int[] pulses = ForgeStrikeMath.flurryPulseTicks(state.heavy);
        poseStack.pushPose();
        poseStack.translate(0.0f, HEIGHT, 0.0f);
        for (int i = 0; i < pulses.length; i++) {
            float age = state.ageInTicks - pulses[i];
            if (age < 0.0f || age > PULSE_FADE) {
                continue;
            }
            float offset = fan(i, pulses.length);
            float centre = offset * state.arc * FAN;
            Sweep sweep = new Sweep(Plane.GROUND, state.reach, state.halfWidth * THICKNESS,
                    centre - RIBBON_ARC * 0.5f, centre + RIBBON_ARC * 0.5f);
            float alpha = state.alpha * (1.0f - age / PULSE_FADE);
            poseStack.pushPose();
            poseStack.translate(0.0f, offset * STAGGER, 0.0f);
            ForgeRibbon.arc(edge, poseStack.last().pose(), sweep, palette, alpha);
            ForgeElementAccent.draw(edge, poseStack.last().pose(), sweep, palette, alpha, state.accent);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** -1 at the first ribbon, +1 at the last, so an odd count always keeps one straight ahead. */
    private static float fan(int index, int count) {
        return count <= 1 ? 0.0f : index * 2.0f / (count - 1) - 1.0f;
    }
}
