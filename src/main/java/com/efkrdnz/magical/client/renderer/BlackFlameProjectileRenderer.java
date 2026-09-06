package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.BlackFlameProjectileEntity;
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

public final class BlackFlameProjectileRenderer extends EntityRenderer<BlackFlameProjectileEntity, BlackFlameProjectileRenderer.State> {
    public BlackFlameProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BlackFlameProjectileEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.scale = entity.radius();
        state.motion = entity.getDeltaMovement();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float age = state.ageInTicks;
        float pulse = 0.96F + Mth.sin(age * 0.55F) * 0.08F + Mth.sin(age * 1.37F) * 0.045F;
        float scale = state.scale * pulse;
        Vec3 direction = direction(state.motion);

        for (int i = 13; i >= 1; i--) {
            float fade = (14.0F - i) / 13.0F;
            float drift = Mth.sin(age * 0.18F + i * 1.9F) * scale * 0.045F;
            poseStack.pushPose();
            poseStack.translate(-direction.x * i * 0.2D, -direction.y * i * 0.2D + drift, -direction.z * i * 0.2D);
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * (7.0F + i * 0.3F) + i * 31.0F));
            drawUnstableFire(consumer, poseStack, scale * (0.58F + fade * 0.76F), age - i * 0.55F, fade * 0.58F);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        drawUnstableCore(consumer, poseStack, scale, age);
        poseStack.popPose();

        super.render(state, poseStack, buffer, packedLight);
    }

    private static Vec3 direction(Vec3 motion) {
        return motion.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : motion.normalize();
    }

    private static void drawUnstableCore(VertexConsumer consumer, PoseStack poseStack, float scale, float age) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 16.0F));
        diamond(consumer, poseStack, scale * 0.82F, 5, 1, 10, 245);
        poseStack.mulPose(Axis.ZP.rotationDegrees(43.0F + Mth.sin(age * 0.21F) * 8.0F));
        diamond(consumer, poseStack, scale * 1.15F, 36, 3, 64, 190);
        poseStack.mulPose(Axis.ZP.rotationDegrees(37.0F));
        diamond(consumer, poseStack, scale * 1.42F, 92, 6, 54, 116);
        poseStack.popPose();

        for (int i = 0; i < 18; i++) {
            float angle = i * 20.0F + age * (9.0F + (i % 4) * 2.0F);
            float length = scale * (0.9F + (i % 5) * 0.17F + Mth.sin(age * 0.42F + i) * 0.15F);
            float width = scale * (0.11F + (i % 3) * 0.035F);
            int red = i % 3 == 0 ? 188 : 92;
            int green = i % 3 == 0 ? 25 : 6;
            int blue = i % 3 == 0 ? 12 : 74;
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            flameTongue(consumer, poseStack, width, length, Mth.sin(age * 0.6F + i) * width * 0.95F, red, green, blue, 166);
            poseStack.popPose();
        }

        brokenRing(consumer, poseStack, scale * 0.95F, scale * 0.025F, 42, 156, 18, 12, 150, age);
        brokenRing(consumer, poseStack, scale * 1.35F, scale * 0.018F, 34, 54, 4, 82, 105, -age * 0.7F);
        brokenRing(consumer, poseStack, scale * 1.72F, scale * 0.014F, 28, 20, 2, 28, 78, age * 0.43F);
    }

    private static void drawUnstableFire(VertexConsumer consumer, PoseStack poseStack, float scale, float age, float fade) {
        diamond(consumer, poseStack, scale * 0.55F, 5, 1, 10, alpha(148, fade));
        for (int i = 0; i < 7; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(i * 51.0F + age * (8.0F + i)));
            int red = i % 2 == 0 ? 150 : 48;
            int green = i % 2 == 0 ? 18 : 4;
            int blue = i % 2 == 0 ? 10 : 66;
            flameTongue(consumer, poseStack, scale * (0.06F + i * 0.005F), scale * (0.75F + i * 0.07F), Mth.sin(age * 0.5F + i) * scale * 0.04F, red, green, blue, alpha(125, fade));
            poseStack.popPose();
        }
    }

    private static void flameTongue(VertexConsumer consumer, PoseStack poseStack, float width, float height, float bend, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        consumer.addVertex(poseStack.last(), -width, -height * 0.34F, 0.0F).setColor(4, 1, 9, Math.max(0, alpha / 3));
        consumer.addVertex(poseStack.last(), width * 0.88F, -height * 0.18F, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), bend, height, 0.0F).setColor(Math.min(220, red + 42), Math.min(60, green + 12), Math.max(8, blue - 8), Math.max(0, alpha - 18));
        consumer.addVertex(poseStack.last(), -width * 0.7F, height * 0.12F, 0.0F).setColor(8, 1, 14, Math.max(0, alpha / 2));
    }

    private static void diamond(VertexConsumer consumer, PoseStack poseStack, float scale, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), 0.0F, -scale, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), scale * 0.72F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), 0.0F, scale, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), -scale * 0.72F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
    }

    private static void brokenRing(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, int segments, int red, int green, int blue, int alpha, float phase) {
        for (int i = 0; i < segments; i++) {
            if ((i + Mth.floor(Math.abs(phase) * 0.07F)) % 5 == 0) {
                continue;
            }
            float jitterA = Mth.sin(phase * 0.2F + i * 1.7F) * thickness * 3.0F;
            float jitterB = Mth.cos(phase * 0.23F + i * 1.4F) * thickness * 3.0F;
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 0.72F) / segments;
            quad(consumer, poseStack,
                    Mth.cos(a) * (radius - thickness + jitterA), Mth.sin(a) * (radius - thickness + jitterA),
                    Mth.cos(a) * (radius + thickness + jitterA), Mth.sin(a) * (radius + thickness + jitterA),
                    Mth.cos(b) * (radius + thickness + jitterB), Mth.sin(b) * (radius + thickness + jitterB),
                    Mth.cos(b) * (radius - thickness + jitterB), Mth.sin(b) * (radius - thickness + jitterB),
                    red, green, blue, alpha);
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        consumer.addVertex(poseStack.last(), x1, y1, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x1, y1, 0.0F).setColor(red, green, blue, alpha);
    }

    private static int alpha(int base, float fade) {
        return Mth.clamp(Math.round(base * fade), 0, 255);
    }

    public static final class State extends EntityRenderState {
        private float scale = 1.0F;
        private Vec3 motion = Vec3.ZERO;
    }
}
