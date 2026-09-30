package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * The form table: which geometry class draws which family.
 *
 * <p>Every family is painted the same way, through one {@link ForgeStroke}: the smear pass for the
 * blade and, for an element that gives off light, the glint pass for its hot lip. Both are fixed
 * buffers, so both consumers stay live for the whole call and no form has to flush one before it
 * asks for the other.</p>
 */
public final class ForgeForms {

    private ForgeForms() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffer, ForgeStrikeRenderer.State state,
            ForgePalette palette) {
        float partial = state.partialTick;
        VertexConsumer smear = buffer.getBuffer(MagicalRenderTypes.forgeSmear());
        VertexConsumer glint = state.accent.glint() ? buffer.getBuffer(MagicalRenderTypes.forgeGlint()) : null;
        ForgeStroke stroke = new ForgeStroke(smear, glint, state.accent.row(), ForgeSmear.offset(state.seed),
                state.glint);
        switch (state.family) {
            case SLASH -> SlashGeometry.render(poseStack, stroke, state, palette, partial);
            case CLEAVE -> CleaveGeometry.render(poseStack, stroke, state, palette, partial);
            case THRUST -> ThrustGeometry.render(poseStack, stroke, state, palette, partial);
            case SPIN -> SpinGeometry.render(poseStack, stroke, state, palette, partial);
            case SLAM -> SlamGeometry.render(poseStack, stroke, state, palette, partial);
            case WAVE -> WaveGeometry.render(poseStack, stroke, state, palette, partial);
            case RISING -> RisingGeometry.render(poseStack, stroke, state, palette, partial);
            case FLURRY -> FlurryGeometry.render(poseStack, stroke, state, palette, partial);
        }
    }
}
