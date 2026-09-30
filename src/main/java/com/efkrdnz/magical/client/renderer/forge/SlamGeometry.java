package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Matrix4f;

/**
 * SLAM: the ground blow. The only form drawn on two render types — a shock disc lying flat on the
 * ground for the impact shader, ringed by a cracked rim of blade. A heavy slam throws its second
 * ring out at the same tick the second hit lands, so the visual and the damage agree.
 *
 * <p>This is the one geometry handed the buffer source rather than a consumer, because it has to
 * finish and flush the disc before it may ask for the rim — see {@link ForgeBuffers}.</p>
 */
public final class SlamGeometry {

    private static final float RIM_Y = 0.10f;
    private static final int SECOND_RING_TICK = 4;
    private static final float SECOND_RING_RADIUS = 1.6f;
    private static final float SECOND_RING_ALPHA = 0.7f;
    private static final float THICKNESS = 0.35f;
    private static final float GROW_FROM = 0.55f;

    private SlamGeometry() {}

    /**
     * The ring of the blow running out along the ground. The shader disc that used to sit under it
     * is gone: what a slam leaves on the ground is the ground's own business - cracks and dust.
     */
    public static void render(PoseStack poseStack, ForgeStroke stroke, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        float radius = state.reach * (GROW_FROM + (1.0f - GROW_FROM) * state.progress);
        poseStack.pushPose();
        poseStack.translate(0.0f, RIM_Y, 0.0f);
        Matrix4f pose = poseStack.last().pose();
        Sweep rim = new Sweep(Plane.GROUND, radius, state.halfWidth * THICKNESS, 0.0f, 360.0f);
        ForgeRibbon.arc(stroke, pose, rim, palette, state.alpha);
        ForgeElementAccent.draw(stroke, pose, rim, palette, state.alpha, state.accent);
        ForgeAura.arc(stroke, pose, rim, palette, state, state.alpha);
        if (state.heavy && state.ageInTicks - partialTick >= SECOND_RING_TICK) {
            ForgeRibbon.arc(stroke.dull(), pose, rim.scaled(SECOND_RING_RADIUS), palette,
                    state.alpha * SECOND_RING_ALPHA);
        }
        poseStack.popPose();
    }
}
