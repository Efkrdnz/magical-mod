package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.BlackFlameBrandEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;

public final class BlackFlameBrandRenderer extends EntityRenderer<BlackFlameBrandEntity, EntityRenderState> {
    public BlackFlameBrandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void render(EntityRenderState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(state, poseStack, buffer, packedLight);
    }
}
