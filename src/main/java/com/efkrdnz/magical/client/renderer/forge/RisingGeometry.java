package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * RISING: the upward cut. A crescent standing across the aim that climbs the whole time it is
 * alive, its trail left below where the blade has already been.
 */
public final class RisingGeometry {

    private static final float BASE_Y = 0.15f;
    private static final float RISE_PER_TICK = 0.45f;
    private static final float FORWARD = 0.45f;
    private static final float RADIUS = 1.1f;
    private static final float THICKNESS = 0.6f;
    private static final float TRAIL_DROP = 0.5f;
    private static final float MAX_ARC = 220.0f;

    private RisingGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        float lift = BASE_Y + state.ageInTicks * RISE_PER_TICK;
        float half = Math.min(state.arc, MAX_ARC) * 0.5f;
        Sweep sweep = new Sweep(Plane.UPRIGHT, state.halfWidth * RADIUS, state.halfWidth * THICKNESS, -half, half);
        poseStack.pushPose();
        poseStack.translate(0.0f, 0.0f, state.reach * FORWARD);
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, lift - lag * TRAIL_DROP, 0.0f);
            ForgeRibbon.arc(edge, poseStack.last().pose(), sweep, palette, alpha);
            poseStack.popPose();
        });
        poseStack.pushPose();
        poseStack.translate(0.0f, lift, 0.0f);
        ForgeElementAccent.draw(edge, poseStack.last().pose(), sweep, palette, state.alpha, state.accent);
        poseStack.popPose();
        poseStack.popPose();
    }
}
