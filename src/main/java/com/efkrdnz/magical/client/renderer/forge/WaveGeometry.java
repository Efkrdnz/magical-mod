package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/**
 * WAVE: the thrown crescent. Shaped as the old melee arc was — the belly bulges along the travel
 * direction so the wave leads edge-first instead of presenting a flat moon at the camera — with the
 * motion trail strung out behind it and shrinking as it falls back.
 */
public final class WaveGeometry {

    private static final float DIAGONAL = 40.0f;
    private static final float RADIUS = 1.3f;
    private static final float THICKNESS = 0.85f;
    private static final float MAX_ARC = 140.0f;
    private static final float ARC_SCALE = 0.9f;
    private static final float TRAIL_GAP = 0.9f;
    private static final float TRAIL_SHRINK = 0.18f;

    private WaveGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(DIAGONAL));
        float half = Math.min(MAX_ARC, state.arc * ARC_SCALE) * 0.5f;
        Sweep head = new Sweep(Plane.FORWARD, state.halfWidth * RADIUS, state.halfWidth * THICKNESS, -half, half);
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -lag * TRAIL_GAP);
            ForgeRibbon.arc(edge, poseStack.last().pose(), head.scaled(1.0f - lag * TRAIL_SHRINK), palette, alpha);
            poseStack.popPose();
        });
        ForgeElementAccent.draw(edge, poseStack.last().pose(), head, palette, state.alpha, state.accent);
        poseStack.popPose();
    }
}
