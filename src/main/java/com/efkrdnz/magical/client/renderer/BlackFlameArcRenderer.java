package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.BlackFlameArcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class BlackFlameArcRenderer extends EntityRenderer<BlackFlameArcEntity, BlackFlameArcRenderer.State> {
    public BlackFlameArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BlackFlameArcEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.width = entity.width();
        state.height = entity.height();
        state.life = entity.life();
        state.yaw = entity.yaw();
        state.pitch = entity.pitch();
        state.direction = entity.direction();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float progress = Mth.clamp(state.ageInTicks / Math.max(1.0F, state.life), 0.0F, 1.0F);
        float fade = (1.0F - progress) * (1.0F - progress);
        float appear = Mth.clamp(state.ageInTicks / 3.5F, 0.0F, 1.0F);
        float pulse = 0.96F + Mth.sin(state.ageInTicks * 0.8F) * 0.05F;

        for (int i = 9; i >= 1; i--) {
            float trailFade = fade * (1.0F - i / 10.5F);
            poseStack.pushPose();
            poseStack.translate(-state.direction.x * i * 0.34D, -state.direction.y * i * 0.34D, -state.direction.z * i * 0.34D);
            orientSlash(poseStack, state.yaw, state.pitch);
            drawArcStack(consumer, poseStack, state.width * (0.88F - i * 0.025F) * appear, state.height * (0.9F + i * 0.018F) * appear, trailFade, state.ageInTicks - i * 0.8F);
            poseStack.popPose();
        }

        poseStack.pushPose();
        orientSlash(poseStack, state.yaw, state.pitch);
        drawArcStack(consumer, poseStack, state.width * pulse * appear, state.height * pulse * appear, fade, state.ageInTicks);
        poseStack.popPose();

        super.render(state, poseStack, buffer, packedLight);
    }

    private static void orientSlash(PoseStack poseStack, float yaw, float pitch) {
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
    }

    private static void drawArcStack(VertexConsumer consumer, PoseStack poseStack, float width, float height, float fade, float age) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.22F) * 4.0F));
        drawCrescent(consumer, poseStack, width, height, 0.0F, 186, 22, 12, alpha(210, fade));
        drawCrescent(consumer, poseStack, width * 0.78F, height * 0.82F, 0.045F, 54, 4, 82, alpha(180, fade));
        drawCrescent(consumer, poseStack, width * 0.48F, height * 0.58F, 0.075F, 5, 1, 12, alpha(235, fade));
        drawEdgeSparks(consumer, poseStack, width, height, fade, age);
        poseStack.popPose();
    }

    private static void drawCrescent(VertexConsumer consumer, PoseStack poseStack, float width, float height, float z, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        int segments = 28;
        float start = -128.0F * Mth.DEG_TO_RAD;
        float end = 128.0F * Mth.DEG_TO_RAD;
        for (int i = 0; i < segments; i++) {
            float a = Mth.lerp(i / (float) segments, start, end);
            float b = Mth.lerp((i + 1) / (float) segments, start, end);
            float centerFadeA = 0.35F + 0.65F * Mth.sin(i / (float) segments * Mth.PI);
            float centerFadeB = 0.35F + 0.65F * Mth.sin((i + 1) / (float) segments * Mth.PI);
            float outerAx = Mth.sin(a) * width * 0.5F;
            float outerAy = Mth.cos(a) * height * 0.5F;
            float outerBx = Mth.sin(b) * width * 0.5F;
            float outerBy = Mth.cos(b) * height * 0.5F;
            float innerAx = Mth.sin(a) * width * 0.22F + width * 0.08F;
            float innerAy = Mth.cos(a) * height * 0.23F - height * 0.03F;
            float innerBx = Mth.sin(b) * width * 0.22F + width * 0.08F;
            float innerBy = Mth.cos(b) * height * 0.23F - height * 0.03F;
            int alphaA = alpha(alpha, centerFadeA);
            int alphaB = alpha(alpha, centerFadeB);
            gradientQuad(consumer, poseStack, innerAx, innerAy, z, outerAx, outerAy, z, outerBx, outerBy, z, innerBx, innerBy, z, red, green, blue, alphaA / 3, alphaA, alphaB, alphaB / 3);
            gradientQuad(consumer, poseStack, innerBx, innerBy, z, outerBx, outerBy, z, outerAx, outerAy, z, innerAx, innerAy, z, red, green, blue, alphaB / 3, alphaB, alphaA, alphaA / 3);
        }
    }

    private static void drawEdgeSparks(VertexConsumer consumer, PoseStack poseStack, float width, float height, float fade, float age) {
        for (int i = 0; i < 11; i++) {
            float normalized = i / 10.0F;
            float angle = Mth.lerp(normalized, -118.0F * Mth.DEG_TO_RAD, 118.0F * Mth.DEG_TO_RAD);
            float x = Mth.sin(angle) * width * 0.54F;
            float y = Mth.cos(angle) * height * 0.54F;
            float tangentX = Mth.cos(angle);
            float tangentY = -Mth.sin(angle);
            float length = width * (0.18F + 0.08F * Mth.sin(age * 0.6F + i));
            int red = i % 2 == 0 ? 216 : 62;
            int green = i % 2 == 0 ? 34 : 5;
            int blue = i % 2 == 0 ? 14 : 84;
            line(consumer, poseStack, x - tangentX * length, y - tangentY * length, -0.02F, x + tangentX * length, y + tangentY * length, -0.02F, width * 0.018F, red, green, blue, alpha(140, fade * (0.45F + normalized * 0.45F)));
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001F) {
            return;
        }
        float px = -dy / length * thickness * 0.5F;
        float py = dx / length * thickness * 0.5F;
        gradientQuad(consumer, poseStack, x1 - px, y1 - py, z1, x1 + px, y1 + py, z1, x2 + px, y2 + py, z2, x2 - px, y2 - py, z2, red, green, blue, alpha / 3, alpha, alpha, alpha / 3);
    }

    private static void gradientQuad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int red, int green, int blue, int alpha1, int alpha2, int alpha3, int alpha4) {
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha1);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha2);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha3);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha4);
    }

    private static int alpha(int base, float fade) {
        return Mth.clamp(Math.round(base * fade), 0, 255);
    }

    public static final class State extends EntityRenderState {
        private float width = 3.2F;
        private float height = 2.45F;
        private int life = 22;
        private float yaw;
        private float pitch;
        private Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);
    }
}
