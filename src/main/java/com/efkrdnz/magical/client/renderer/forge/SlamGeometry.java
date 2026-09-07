package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.FusionGeometry;
import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Plane;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;

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

    private static final float DISC_Y = 0.06f;
    private static final float RIM_Y = 0.10f;
    private static final int SECOND_RING_TICK = 4;
    private static final float SECOND_RING_RADIUS = 1.6f;
    private static final float SECOND_RING_ALPHA = 0.7f;
    private static final float THICKNESS = 0.35f;
    private static final float GROW_FROM = 0.55f;
    private static final float DISC_ALPHA = 230.0f;

    private SlamGeometry() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffer, ForgeStrikeRenderer.State state,
            ForgePalette palette, float partialTick) {
        float radius = state.reach * (GROW_FROM + (1.0f - GROW_FROM) * state.progress);
        poseStack.pushPose();
        poseStack.translate(0.0f, DISC_Y, 0.0f);
        // The disc is written and flushed before the rim's consumer is asked for. Both types live on
        // the immediate source's one shared buffer, so a disc consumer held across that fetch would
        // already have been built and would throw on the next vertex.
        shock(buffer.getBuffer(MagicalRenderTypes.forgeImpact()), poseStack.last().pose(), radius,
                palette.primary(), ForgeRibbon.alpha(DISC_ALPHA, state.alpha));
        ForgeBuffers.flush(buffer, MagicalRenderTypes.forgeImpact());
        poseStack.translate(0.0f, RIM_Y - DISC_Y, 0.0f);
        VertexConsumer edge = buffer.getBuffer(MagicalRenderTypes.forgeEdge());
        Matrix4f pose = poseStack.last().pose();
        Sweep rim = new Sweep(Plane.GROUND, radius, state.halfWidth * THICKNESS, 0.0f, 360.0f);
        ForgeRibbon.arc(edge, pose, rim, palette, state.alpha);
        ForgeElementAccent.draw(edge, pose, rim, palette, state.alpha, state.accent);
        if (state.heavy && state.ageInTicks - partialTick >= SECOND_RING_TICK) {
            ForgeRibbon.arc(edge, pose, rim.scaled(SECOND_RING_RADIUS), palette, state.alpha * SECOND_RING_ALPHA);
        }
        poseStack.popPose();
    }

    /** The flat quad the impact shader carves its disc, rim cracks and spokes out of. */
    private static void shock(VertexConsumer disc, Matrix4f pose, float radius, int color, int alpha) {
        FusionGeometry.quad(disc, pose,
                -radius, 0.0f, -radius, 0.0f, 0.0f,
                -radius, 0.0f, radius, 0.0f, 1.0f,
                radius, 0.0f, radius, 1.0f, 1.0f,
                radius, 0.0f, -radius, 1.0f, 0.0f,
                FusionGeometry.red(color), FusionGeometry.green(color), FusionGeometry.blue(color), alpha);
    }
}
