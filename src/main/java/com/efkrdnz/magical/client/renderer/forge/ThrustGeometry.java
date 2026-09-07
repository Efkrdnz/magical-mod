package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * THRUST: the lunge. A tapered lance driven straight down the aim for the strike's whole reach,
 * needling to a point at the far end and flaring where it leaves the wielder's hands. The element
 * crowns the point rather than the shaft, so a thrust reads as a spearhead.
 */
public final class ThrustGeometry {

    private static final float LIFT = 1.2f;
    private static final float WIDTH = 0.24f;
    private static final float FLARE = 0.20f;
    private static final float TRAIL_GAP = 0.7f;
    private static final float CROWN = 0.5f;
    private static final float CROWN_THICKNESS = 0.5f;

    private ThrustGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, LIFT, -lag * TRAIL_GAP);
            ForgeRibbon.lance(edge, poseStack.last().pose(), state.reach, state.halfWidth * WIDTH,
                    state.halfWidth * FLARE, palette, alpha);
            poseStack.popPose();
        });
        float radius = state.halfWidth * CROWN;
        Sweep crown = new Sweep(Plane.UPRIGHT, radius, radius * CROWN_THICKNESS, 0.0f, 360.0f);
        poseStack.pushPose();
        poseStack.translate(0.0f, LIFT, state.reach);
        ForgeElementAccent.draw(edge, poseStack.last().pose(), crown, palette, state.alpha, state.accent);
        poseStack.popPose();
    }
}
