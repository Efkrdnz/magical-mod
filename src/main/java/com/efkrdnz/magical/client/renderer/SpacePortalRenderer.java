package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SpacePortalEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class SpacePortalRenderer extends EntityRenderer<SpacePortalEntity, SpacePortalRenderer.State> {
    public SpacePortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = state.age;
        float open = Mth.clamp(age / 12.0F, 0.0F, 1.0F);
        float fade = 1.0F - Mth.clamp((age - 520.0F) / 80.0F, 0.0F, 1.0F);
        float width = 1.7F * open * (0.96F + Mth.sin(age * 0.18F) * 0.04F);
        float height = 2.8F * open * (0.98F + Mth.cos(age * 0.13F) * 0.035F);
        int alpha = Mth.clamp(Math.round(230.0F * fade), 0, 230);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.portalYaw));
        poseStack.translate(0.0D, -height * 0.5D, 0.0D);
        VertexConsumer rift = buffer.getBuffer(MagicalRenderTypes.spatialRift());
        drawPlane(rift, poseStack, width, height, alpha);
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.08D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.4F));
        drawPlane(rift, poseStack, width * 0.64F, height * 1.08F, Math.round(alpha * 0.58F));
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, -0.08D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 1.1F));
        drawPlane(rift, poseStack, width * 1.18F, height * 0.76F, Math.round(alpha * 0.38F));
        poseStack.popPose();

        VertexConsumer lightning = buffer.getBuffer(RenderType.lightning());
        for (int i = 0; i < 3; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0D, height * 0.5D, 0.02D + i * 0.025D);
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * (1.8F + i * 0.55F) + i * 37.0F));
            drawOval(lightning, poseStack, width * (0.46F + i * 0.07F), height * (0.34F + i * 0.035F), 0x78DFFF, (0.34F - i * 0.07F) * fade);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }


    private static void drawPlane(VertexConsumer consumer, PoseStack poseStack, float width, float height, int alpha) {
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

    private static void drawOval(VertexConsumer consumer, PoseStack poseStack, float radiusX, float radiusY, int color, float alpha) {
        int steps = 96;
        for (int i = 0; i < steps; i++) {
            float a0 = Mth.TWO_PI * i / steps;
            float a1 = Mth.TWO_PI * (i + 1) / steps;
            RenderShape.line(
                    consumer,
                    poseStack,
                    Mth.cos(a0) * radiusX,
                    Mth.sin(a0) * radiusY,
                    0.0F,
                    Mth.cos(a1) * radiusX,
                    Mth.sin(a1) * radiusY,
                    0.0F,
                    0.014F,
                    color,
                    alpha);
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpacePortalEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.portalYaw = entity.portalYaw();
    }

    /** Snapshot the renderer draws from. 1.21.4 renders from state, not from the entity. */
    public static final class State extends EntityRenderState {
        public float age;
        public float portalYaw;
    }
}
