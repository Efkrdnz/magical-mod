package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.joml.Matrix4f;

/**
 * SLASH: the plain horizontal cut. A crescent laid flat at chest height, swept through the strike's
 * own arc at its own reach, tilted a little off level so it reads as a swing and not a hoop.
 */
public final class SlashGeometry {

    private static final float HEIGHT = 1.15f;
    private static final float TILT = -22.0f;
    private static final float TRAIL_SWEEP = 30.0f;
    private static final float THICKNESS = 0.55f;
    private static final float MAX_ARC = 300.0f;

    private SlashGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        poseStack.pushPose();
        poseStack.translate(0.0f, HEIGHT, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(TILT));
        Matrix4f pose = poseStack.last().pose();
        float half = Math.min(state.arc, MAX_ARC) * 0.5f;
        Sweep head = new Sweep(Plane.GROUND, state.reach, state.halfWidth * THICKNESS, -half, half);
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha,
                (lag, alpha) -> ForgeRibbon.arc(edge, pose, head.shifted(-lag * TRAIL_SWEEP), palette, alpha));
        ForgeElementAccent.draw(edge, pose, head, palette, state.alpha, state.accent);
        poseStack.popPose();
    }
}
