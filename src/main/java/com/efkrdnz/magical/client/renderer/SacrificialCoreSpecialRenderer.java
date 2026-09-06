package com.efkrdnz.magical.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SacrificialCoreSpecialRenderer implements SpecialModelRenderer<Void> {
    @Override
    public void render(@Nullable Void argument, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, boolean hasFoil) {
        // Millisecond clock so the icon stays animated regardless of level/tick availability.
        float time = (Util.getMillis() % 1200000L) / 50.0F;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        SacrificialCoreRenderer.renderCore(poseStack, bufferSource, time);
        poseStack.popPose();
    }

    @Nullable
    @Override
    public Void extractArgument(ItemStack stack) {
        return null;
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet modelSet) {
            return new SacrificialCoreSpecialRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
