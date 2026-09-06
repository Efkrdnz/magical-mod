package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.MagicBarrageShotEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class MagicBarrageShotRenderer extends EntityRenderer<MagicBarrageShotEntity, MagicBarrageShotRenderer.State> {
    public MagicBarrageShotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MagicBarrageShotEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.color = entity.color();
        state.size = entity.size();
        state.yaw = (float) (Mth.atan2(entity.direction().x, entity.direction().z) * Mth.RAD_TO_DEG);
        state.pitch = (float) (-Math.asin(entity.direction().y) * Mth.RAD_TO_DEG);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float fade = 1.0F - Mth.clamp((state.ageInTicks - 56.0F) / 14.0F, 0.0F, 1.0F);
        float length = 0.85F + state.size * 0.8F;
        float width = 0.08F + state.size * 0.055F;
        int red = red(state.color);
        int green = green(state.color);
        int blue = blue(state.color);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        quad(consumer, poseStack, -width, 0.0F, -length * 0.45F, width, 0.0F, -length * 0.45F, width * 0.36F, 0.0F, length * 0.62F, -width * 0.36F, 0.0F, length * 0.62F, red, green, blue, alpha(190, fade));
        quad(consumer, poseStack, 0.0F, -width, -length * 0.38F, 0.0F, width, -length * 0.38F, 0.0F, width * 0.28F, length * 0.72F, 0.0F, -width * 0.28F, length * 0.72F, 245, 250, 255, alpha(155, fade));
        quad(consumer, poseStack, -width * 0.35F, 0.0F, length * 0.55F, width * 0.35F, 0.0F, length * 0.55F, 0.0F, 0.0F, length * 0.95F, 0.0F, 0.0F, length * 0.95F, 255, 255, 255, alpha(220, fade));
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
    }

    private static int alpha(int base, float fade) {
        return Mth.clamp(Math.round(base * fade), 0, 255);
    }

    private static int red(int color) {
        return color >> 16 & 255;
    }

    private static int green(int color) {
        return color >> 8 & 255;
    }

    private static int blue(int color) {
        return color & 255;
    }

    public static final class State extends EntityRenderState {
        private int color = 0x8E7BFF;
        private float size = 1.0F;
        private float yaw;
        private float pitch;
    }
}
