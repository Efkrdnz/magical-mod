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
    private static final float RADIUS = 2.0f;
    private static final float THICKNESS = 1.0f;
    /** How big the crescent is when it leaves the hand, as a fraction of its full size. */
    private static final float FROM = 0.45f;
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
        // UPRIGHT, not FORWARD: a crescent lying in the plane it travels along is edge-on to
        // whoever threw it, and watching your own wave fly away showed a bright line and nothing
        // else. Broadside to the flight is also what WaveShape inflates - laterally and
        // vertically, not along the travel - so this is the honest face of it.
        Sweep full = new Sweep(Plane.UPRIGHT, state.halfWidth * RADIUS, state.halfWidth * THICKNESS, -half, half);
        // A thrown crescent opens out as it travels instead of arriving at full size.
        Sweep head = ForgeMotion.reaching(full, FROM, state.progress);
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, 0.0f, -lag * TRAIL_GAP);
            ForgeRibbon.arc(edge, poseStack.last().pose(), head.scaled(1.0f - lag * TRAIL_SHRINK), palette, alpha);
            poseStack.popPose();
        });
        ForgeElementAccent.draw(edge, poseStack.last().pose(), head, palette, state.alpha, state.accent);
        ForgeAura.arc(edge, poseStack.last().pose(), head, palette, state, state.alpha);
        poseStack.popPose();
    }
}
