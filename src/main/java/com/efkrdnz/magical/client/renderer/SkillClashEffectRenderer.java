package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SkillClashEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class SkillClashEffectRenderer extends EntityRenderer<SkillClashEffectEntity, SkillClashEffectRenderer.State> {
    public SkillClashEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SkillClashEffectEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.incomingColor = entity.incomingColor();
        state.counterColor = entity.counterColor();
        state.life = entity.life();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float progress = Mth.clamp(state.ageInTicks / Math.max(1.0F, state.life), 0.0F, 1.0F);
        float fade = 1.0F - progress;
        float pulse = 1.0F + Mth.sin(state.ageInTicks * 0.85F) * 0.08F;
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 8.0F));
        ring(consumer, poseStack, 1.2F + progress * 5.2F, 0.08F + progress * 0.08F, red(state.counterColor), green(state.counterColor), blue(state.counterColor), alpha(190, fade));
        ring(consumer, poseStack, 0.7F + progress * 3.7F, 0.05F, red(state.incomingColor), green(state.incomingColor), blue(state.incomingColor), alpha(150, fade));
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(35.0F + state.ageInTicks * 2.5F));
        ribbon(consumer, poseStack, 5.4F * pulse, 0.46F, red(state.incomingColor), green(state.incomingColor), blue(state.incomingColor), alpha(210, fade));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        ribbon(consumer, poseStack, 5.4F * pulse, 0.46F, red(state.counterColor), green(state.counterColor), blue(state.counterColor), alpha(210, fade));
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.ageInTicks * 4.0F));
        verticalSpark(consumer, poseStack, 1.1F + progress * 1.4F, 3.4F + progress * 3.0F, 255, 255, 255, alpha(235, fade));
        poseStack.popPose();

        super.render(state, poseStack, buffer, packedLight);
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, int red, int green, int blue, int alpha) {
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            float angleA = Mth.TWO_PI * i / segments;
            float angleB = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    Mth.cos(angleA) * (radius - thickness), 0.0F, Mth.sin(angleA) * (radius - thickness),
                    Mth.cos(angleA) * (radius + thickness), 0.0F, Mth.sin(angleA) * (radius + thickness),
                    Mth.cos(angleB) * (radius + thickness), 0.0F, Mth.sin(angleB) * (radius + thickness),
                    Mth.cos(angleB) * (radius - thickness), 0.0F, Mth.sin(angleB) * (radius - thickness),
                    red, green, blue, alpha);
        }
    }

    private static void ribbon(VertexConsumer consumer, PoseStack poseStack, float length, float halfHeight, int red, int green, int blue, int alpha) {
        quad(consumer, poseStack, -length, -halfHeight, 0.0F, 0.0F, -halfHeight * 0.35F, 0.0F, 0.0F, halfHeight * 0.35F, 0.0F, -length, halfHeight, 0.0F, red, green, blue, alpha);
        quad(consumer, poseStack, length, -halfHeight, 0.0F, 0.0F, -halfHeight * 0.35F, 0.0F, 0.0F, halfHeight * 0.35F, 0.0F, length, halfHeight, 0.0F, red, green, blue, alpha);
    }

    private static void verticalSpark(VertexConsumer consumer, PoseStack poseStack, float width, float height, int red, int green, int blue, int alpha) {
        quad(consumer, poseStack, -width, -height, 0.0F, width, -height, 0.0F, width * 0.2F, height, 0.0F, -width * 0.2F, height, 0.0F, red, green, blue, alpha);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        quad(consumer, poseStack, -width, -height, 0.0F, width, -height, 0.0F, width * 0.2F, height, 0.0F, -width * 0.2F, height, 0.0F, red, green, blue, alpha);
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
        private int incomingColor = 0xF8FCFF;
        private int counterColor = 0xA57DFF;
        private int life = 24;
    }
}
