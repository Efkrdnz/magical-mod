package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.efkrdnz.magical.forge.FormFamily;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * The form table: which geometry class draws which family, and which render types it needs.
 *
 * <p>Seven of the eight families are pure edge, so they are handed one consumer that stays live for
 * the whole call. SLAM spans two render types and is handed the source instead, because
 * {@link ForgeBuffers} forbids holding a consumer across a fetch of another type.</p>
 */
public final class ForgeForms {

    private ForgeForms() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffer, ForgeStrikeRenderer.State state,
            ForgePalette palette) {
        float partial = state.partialTick;
        if (state.family == FormFamily.SLAM) {
            SlamGeometry.render(poseStack, buffer, state, palette, partial);
            return;
        }
        VertexConsumer edge = buffer.getBuffer(MagicalRenderTypes.forgeEdge());
        switch (state.family) {
            case SLASH -> SlashGeometry.render(poseStack, edge, state, palette, partial);
            case CLEAVE -> CleaveGeometry.render(poseStack, edge, state, palette, partial);
            case THRUST -> ThrustGeometry.render(poseStack, edge, state, palette, partial);
            case SPIN -> SpinGeometry.render(poseStack, edge, state, palette, partial);
            case WAVE -> WaveGeometry.render(poseStack, edge, state, palette, partial);
            case RISING -> RisingGeometry.render(poseStack, edge, state, palette, partial);
            case FLURRY -> FlurryGeometry.render(poseStack, edge, state, palette, partial);
            case SLAM -> { } // handled above: it is the one form drawn across two render types
        }
    }
}
