package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SoulBondEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class SoulBondRenderer extends EntityRenderer<SoulBondEntity, SoulBondRenderer.State> {
    public SoulBondRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SoulBondEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.owner = entity.ownerPoint().subtract(entity.position());
        state.target = entity.targetPoint().subtract(entity.position());
        state.ownerRadius = entity.ownerRadius();
        state.targetRadius = entity.targetRadius();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float fade = 0.78F + Mth.sin(state.ageInTicks * 0.12F) * 0.1F;
        renderCircleAt(poseStack, buffer, state.owner, state.ownerRadius, fade, state.ageInTicks);
        renderCircleAt(poseStack, buffer, state.target, state.targetRadius, fade, state.ageInTicks + 13.0F);
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void renderCircleAt(PoseStack poseStack, MultiBufferSource buffer, Vec3 position, float radius, float fade, float age) {
        poseStack.pushPose();
        poseStack.translate(position.x, position.y, position.z);
        MagicCircleRenderer.renderSoulValleyCircle(poseStack, buffer, radius, fade, age);
        poseStack.popPose();
    }

    public static final class State extends EntityRenderState {
        private Vec3 owner = Vec3.ZERO;
        private Vec3 target = Vec3.ZERO;
        private float ownerRadius = 0.8F;
        private float targetRadius = 0.8F;
    }
}
