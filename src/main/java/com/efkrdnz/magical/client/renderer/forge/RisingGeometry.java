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
    // Far enough out to be a cut in front of the wielder rather than something happening to
    // their own face: in first person this is the nearest geometry any form draws.
    private static final float FORWARD = 0.78f;
    // Pulled back from two. A rising cut is drawn a step in front of the wielder, so in first
    // person it is the closest thing to the camera of any form; at two it filled a third of the
    // frame and read as a wall rather than as a cut.
    private static final float RADIUS = 1.15f;
    private static final float THICKNESS = 0.95f;
    private static final float OPEN_BY = 0.5f;
    // Small enough that the lagged copies overlap into one blur. At half a block apart they
    // read as separate stacked crescents - a rising cut came out corrugated.
    private static final float TRAIL_DROP = 0.26f;
    private static final float MAX_ARC = 220.0f;

    private RisingGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        float lift = BASE_Y + state.ageInTicks * RISE_PER_TICK;
        float half = Math.min(state.arc, MAX_ARC) * 0.5f;
        Sweep full = new Sweep(Plane.UPRIGHT, state.halfWidth * RADIUS, state.halfWidth * THICKNESS, -half, half);
        Sweep sweep = ForgeMotion.opening(full, state.progress / OPEN_BY);
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
        ForgeAura.arc(edge, poseStack.last().pose(), sweep, palette, state, state.alpha);
        poseStack.popPose();
        poseStack.popPose();
    }
}
