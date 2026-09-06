package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.DimensionalGuillotineEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class DimensionalGuillotineRenderer extends EntityRenderer<DimensionalGuillotineEntity, DimensionalGuillotineRenderer.State> {
    public DimensionalGuillotineRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DimensionalGuillotineEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.width = entity.width();
        state.height = entity.height();
        state.yaw = entity.yaw();
        state.life = entity.life();
        state.charge = entity.chargeTicks();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(MagicalRenderTypes.spatialRift());
        float age = state.ageInTicks;
        float open = Mth.clamp(age / Math.max(1.0F, state.charge), 0.0F, 1.0F);
        float fade = 1.0F - Mth.clamp((age - state.charge) / Math.max(1.0F, state.life - state.charge), 0.0F, 1.0F);
        float snap = age < state.charge ? open * open : fade;
        float width = state.width * (0.24F + snap * 0.76F);
        float height = state.height * (0.6F + open * 0.4F);
        int alpha = Mth.clamp(Math.round(245.0F * Math.max(0.0F, fade)), 0, 255);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.translate(0.0D, -height * 0.5D, 0.0D);
        drawRiftPlane(consumer, poseStack, width, height, alpha);
        poseStack.translate(0.0D, 0.0D, -0.08D);
        drawRiftPlane(consumer, poseStack, width * 0.62F, height * 1.08F, Math.round(alpha * 0.72F));
        poseStack.translate(0.0D, 0.0D, 0.18D);
        drawRiftPlane(consumer, poseStack, width * 1.32F, height * 0.88F, Math.round(alpha * 0.38F));
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void drawRiftPlane(VertexConsumer consumer, PoseStack poseStack, float width, float height, int alpha) {
        float half = width * 0.5F;
        consumer.addVertex(poseStack.last(), -half, 0.0F, 0.0F).setUv(0.0F, 1.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), half, 0.0F, 0.0F).setUv(1.0F, 1.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), half, height, 0.0F).setUv(1.0F, 0.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), -half, height, 0.0F).setUv(0.0F, 0.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), -half, height, 0.0F).setUv(0.0F, 0.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), half, height, 0.0F).setUv(1.0F, 0.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), half, 0.0F, 0.0F).setUv(1.0F, 1.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), -half, 0.0F, 0.0F).setUv(0.0F, 1.0F).setColor(255, 255, 255, alpha);
    }

    public static final class State extends EntityRenderState {
        private float width = 12.0F;
        private float height = 14.0F;
        private float yaw;
        private int life = 42;
        private int charge = 14;
    }
}
