package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.AscendantVerdictEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * The verdict draws nothing itself; its telegraph is the magic circle it spawns on the target.
 * Registered so the entity type has a renderer at all, exactly as the black-flame brand is.
 */
public final class AscendantVerdictRenderer extends EntityRenderer<AscendantVerdictEntity, EntityRenderState> {
    public AscendantVerdictRenderer(EntityRendererProvider.Context context) {
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
