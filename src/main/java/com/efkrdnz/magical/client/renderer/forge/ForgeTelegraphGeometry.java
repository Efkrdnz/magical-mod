package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeEffectRenderer;
import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * The three forge visuals that are not impacts: the charge the wielder is holding, the brand a
 * branding weapon leaves on a body, and the cracks a slam opens in the ground.
 */
public final class ForgeTelegraphGeometry {

    private static final float RING_Y = 0.06f;
    private static final float RING_WIDE = 1.7f;
    private static final float RING_TIGHT = 0.75f;
    private static final float GLYPH_Y = 2.35f;
    private static final float GLYPH_SPIN = 7.0f;
    private static final float GLYPH_LENGTH = 0.85f;
    private static final float GLYPH_WIDTH = 0.11f;
    private static final float GLYPH_FLARE = 0.06f;
    private static final float BRAND_RADIUS = 0.8f;
    private static final float BRAND_SPIN = 0.035f;
    private static final int BRAND_TICKS = 6;
    private static final int CRACKS_LIGHT = 6;
    private static final int CRACKS_HEAVY = 9;

    private ForgeTelegraphGeometry() {}

    /**
     * CHARGE: a ring at the wielder's feet that closes in as the charge builds, under a blade glyph
     * turning over their head. Drawn in the world frame, so it orients itself.
     *
     * <p>The ring is on the impact type and the glyph on the edge type. The ring is finished and
     * flushed before the glyph's consumer is fetched — both share the immediate source's one buffer,
     * so a ring consumer held across that fetch would already be built and would throw.</p>
     */
    public static void chargeTelegraph(PoseStack poseStack, MultiBufferSource buffer,
            ForgeEffectRenderer.State state, ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(230.0f, state.alpha);
        float radius = state.scale * Mth.lerp(state.progress, RING_WIDE, RING_TIGHT);
        poseStack.pushPose();
        poseStack.translate(0.0f, RING_Y, 0.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        ForgeDisc.wedge(buffer.getBuffer(MagicalRenderTypes.forgeImpact()), poseStack.last().pose(), radius,
                0.0f, Mth.TWO_PI, 0.80f, 1.0f, palette.primary(), alpha);
        poseStack.popPose();
        ForgeBuffers.flush(buffer, MagicalRenderTypes.forgeImpact());
        poseStack.pushPose();
        poseStack.translate(0.0f, GLYPH_Y, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * GLYPH_SPIN));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
        ForgeRibbon.lance(buffer.getBuffer(MagicalRenderTypes.forgeEdge()), poseStack.last().pose(),
                GLYPH_LENGTH, GLYPH_WIDTH, GLYPH_FLARE, palette, state.alpha);
        poseStack.popPose();
    }

    /** BRAND: a slowly turning rune ring sitting on the marked body. Drawn camera-facing. */
    public static void brandMark(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(225.0f, state.alpha);
        float radius = state.scale * BRAND_RADIUS;
        float turn = state.ageInTicks * BRAND_SPIN;
        ForgeDisc.wedge(disc, pose, radius, 0.0f, Mth.TWO_PI, 0.86f, 1.0f, palette.primary(), alpha);
        for (int i = 0; i < BRAND_TICKS; i++) {
            float angle = turn + Mth.TWO_PI * i / BRAND_TICKS;
            ForgeDisc.wedge(disc, pose, radius, angle - 0.05f, angle + 0.05f, 0.55f, 0.86f,
                    palette.secondary(), alpha);
        }
    }

    /** SLAM: cracks running out from the blow, more of them when the blow was heavy. */
    public static void slamCrack(VertexConsumer disc, Matrix4f pose, ForgeEffectRenderer.State state,
            ForgePalette palette) {
        int alpha = ForgeRibbon.alpha(240.0f, state.alpha);
        float reach = state.scale * (0.6f + 0.5f * state.progress);
        int cracks = state.heavy ? CRACKS_HEAVY : CRACKS_LIGHT;
        for (int i = 0; i < cracks; i++) {
            float angle = Mth.TWO_PI * i / cracks + (i % 2) * 0.15f;
            ForgeDisc.wedge(disc, pose, reach, angle - 0.035f, angle + 0.035f, 0.12f, 1.0f,
                    palette.secondary(), alpha);
        }
        ForgeDisc.blob(disc, pose, 0.0f, 0.0f, reach * 0.35f, palette.primary(), alpha);
    }
}
