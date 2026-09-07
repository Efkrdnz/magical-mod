package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * CLEAVE: the overhead chop. A tall, narrow ribbon standing in front of the wielder, running from
 * above their head down past their feet, with the wake hanging above it because the blade fell.
 */
public final class CleaveGeometry {

    private static final float MIN_RADIUS = 1.5f;
    private static final float RADIUS_SCALE = 0.42f;
    private static final float SPAN = 72.0f;
    private static final float THICKNESS = 0.28f;
    private static final float LIFT = 1.0f;
    private static final float TRAIL_RISE = 0.55f;

    private CleaveGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        float radius = Math.max(MIN_RADIUS, state.reach * RADIUS_SCALE);
        Sweep sweep = new Sweep(Plane.FORWARD, radius, state.halfWidth * THICKNESS, -SPAN, SPAN);
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, LIFT + lag * TRAIL_RISE, 0.0f);
            ForgeRibbon.arc(edge, poseStack.last().pose(), sweep, palette, alpha);
            poseStack.popPose();
        });
        poseStack.pushPose();
        poseStack.translate(0.0f, LIFT, 0.0f);
        ForgeElementAccent.draw(edge, poseStack.last().pose(), sweep, palette, state.alpha, state.accent);
        poseStack.popPose();
    }
}
