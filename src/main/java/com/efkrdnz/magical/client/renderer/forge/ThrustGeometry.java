package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

/**
 * THRUST: the lunge. A tapered lance driven straight down the aim for the strike's whole reach,
 * needling to a point at the far end and flaring where it leaves the wielder's hands. The element
 * crowns the point rather than the shaft, so a thrust reads as a spearhead.
 */
public final class ThrustGeometry {

    private static final float LIFT = 1.2f;
    private static final float WIDTH = 0.40f;
    private static final float FLARE = 0.30f;
    /** Where the lunge starts, as a fraction of its reach, and when it is fully driven out. */
    private static final float FROM = 0.35f;
    private static final float OUT_BY = 0.45f;
    private static final float TRAIL_GAP = 0.7f;
    private static final float CROWN = 0.5f;
    private static final float CROWN_THICKNESS = 0.5f;

    private ThrustGeometry() {}

    public static void render(PoseStack poseStack, VertexConsumer edge, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        // The lance is driven out rather than being there from the first frame, and it never
        // reaches past the reach the hit shape was built with.
        float length = state.reach * Mth.lerp(ForgeMotion.swept(state.progress / OUT_BY), FROM, 1.0f);
        ForgeRibbon.trail(state.heavy, state.accent.invertTrail(), state.alpha, (lag, alpha) -> {
            poseStack.pushPose();
            poseStack.translate(0.0f, LIFT, -lag * TRAIL_GAP);
            ForgeRibbon.lance(edge, poseStack.last().pose(), length, state.halfWidth * WIDTH,
                    state.halfWidth * FLARE, palette, alpha);
            poseStack.popPose();
        });
        // The lance's own glow, drawn once at the head's position rather than per trail copy.
        poseStack.pushPose();
        poseStack.translate(0.0f, LIFT, 0.0f);
        ForgeAura.lance(edge, poseStack.last().pose(), length, state.halfWidth * WIDTH, palette, state.alpha);
        poseStack.popPose();
        float radius = state.halfWidth * CROWN;
        Sweep crown = new Sweep(Plane.UPRIGHT, radius, radius * CROWN_THICKNESS, 0.0f, 360.0f);
        poseStack.pushPose();
        poseStack.translate(0.0f, LIFT, length);
        ForgeElementAccent.draw(edge, poseStack.last().pose(), crown, palette, state.alpha, state.accent);
        poseStack.popPose();
    }
}
