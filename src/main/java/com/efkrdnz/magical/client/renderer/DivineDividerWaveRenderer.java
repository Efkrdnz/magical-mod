package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.DivineDividerWaveEntity;
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

public final class DivineDividerWaveRenderer extends EntityRenderer<DivineDividerWaveEntity, DivineDividerWaveRenderer.State> {
    public DivineDividerWaveRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DivineDividerWaveEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.width = entity.width();
        state.height = entity.height();
        state.life = entity.life();
        state.yaw = entity.yaw();
        state.color = entity.color();
        state.direction = entity.direction();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float progress = Mth.clamp(state.ageInTicks / Math.max(1.0F, state.life), 0.0F, 1.0F);
        float remaining = 1.0F - progress;
        float fade = remaining * remaining;
        float snap = Mth.clamp(state.ageInTicks / 5.0F, 0.0F, 1.0F);
        int red = red(state.color);
        int green = green(state.color);
        int blue = blue(state.color);
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Vec3 direction = state.direction;

        for (int i = 8; i >= 1; i--) {
            float trailFade = fade * (1.0F - i / 10.0F);
            float trailScale = 1.0F - i * 0.045F;
            poseStack.pushPose();
            poseStack.translate(-direction.x * i * 0.62D, -direction.y * i * 0.62D, -direction.z * i * 0.62D);
            drawCrossDivider(consumer, poseStack, state.width * trailScale * 0.82F, state.height * (0.92F + i * 0.012F), state.yaw, red, green, blue, trailFade, state.ageInTicks + i * 2.0F);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.02D, 0.0D);
        drawCrossDivider(consumer, poseStack, state.width * (0.58F + snap * 0.22F), state.height * 1.08F, state.yaw, red, green, blue, fade, state.ageInTicks);
        poseStack.popPose();

        super.render(state, poseStack, buffer, packedLight);
    }

    private static void drawCrossDivider(VertexConsumer consumer, PoseStack poseStack, float width, float height, float yaw, int red, int green, int blue, float fade, float age) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.translate(0.0D, -height * 0.5D, 0.0D);
        drawDivider(consumer, poseStack, width, height, red, green, blue, fade, age);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - yaw));
        poseStack.translate(0.0D, -height * 0.5D, 0.0D);
        drawDivider(consumer, poseStack, width * 0.88F, height, red, green, blue, fade * 0.82F, age + 4.0F);
        poseStack.popPose();
    }

    private static void drawDivider(VertexConsumer consumer, PoseStack poseStack, float width, float height, int red, int green, int blue, float fade, float age) {
        float halfWidth = width * 0.5F;
        featheredPlane(consumer, poseStack, halfWidth * 1.18F, height, 0.0F, 232, 248, 255, fade);
        featheredPlane(consumer, poseStack, halfWidth * 0.72F, height * 0.92F, 0.065F, 255, 255, 255, fade * 1.25F);
        featheredPlane(consumer, poseStack, halfWidth * 1.46F, height * 0.72F, -0.38F, red, green, blue, fade * 0.54F);

        line(consumer, poseStack, 0.0F, -height * 0.04F, 0.12F, 0.0F, height * 1.05F, 0.12F, width * 0.15F, 255, 255, 255, alpha(255, fade));
        line(consumer, poseStack, -halfWidth * 0.86F, height * 0.08F, 0.06F, halfWidth * 0.58F, height * 0.96F, 0.06F, width * 0.058F, 245, 252, 255, alpha(224, fade));
        line(consumer, poseStack, halfWidth * 0.72F, height * 0.04F, 0.04F, -halfWidth * 0.44F, height * 0.8F, 0.04F, width * 0.042F, 204, 235, 255, alpha(162, fade));

        for (int i = 1; i <= 12; i++) {
            float trailFade = fade * (1.0F - i / 13.0F);
            float z = -i * 0.54F;
            float sway = Mth.sin(age * 0.26F + i * 0.9F) * width * 0.09F;
            float trailWidth = width * (1.08F + i * 0.16F);
            line(consumer, poseStack, -trailWidth * 0.4F + sway, height * 0.04F, z, trailWidth * 0.24F + sway, height * 0.98F, z, width * 0.04F, red, green, blue, alpha(132, trailFade));
            line(consumer, poseStack, trailWidth * 0.36F - sway, height * 0.14F, z - 0.16F, -trailWidth * 0.2F - sway, height * 0.82F, z - 0.16F, width * 0.026F, 255, 255, 255, alpha(92, trailFade));
        }

        for (int i = 0; i < 9; i++) {
            float y = height * (0.1F + i * 0.095F);
            float z = -0.2F - i * 0.18F;
            float edgeFade = fade * (1.0F - i * 0.055F);
            line(consumer, poseStack, -halfWidth * (1.08F - i * 0.045F), y, z, halfWidth * (0.96F - i * 0.035F), y + height * 0.045F, z, width * 0.018F, 214, 242, 255, alpha(96, edgeFade));
        }
    }

    private static void featheredPlane(VertexConsumer consumer, PoseStack poseStack, float halfWidth, float height, float z, int red, int green, int blue, float fade) {
        int bands = 8;
        for (int i = 0; i < bands; i++) {
            float inner = i / (float) bands;
            float outer = (i + 1) / (float) bands;
            float innerX = inner * halfWidth;
            float outerX = outer * halfWidth;
            float innerAlpha = featherAlpha(inner, fade);
            float outerAlpha = featherAlpha(outer, fade);
            gradientQuad(consumer, poseStack, innerX, 0.0F, z, outerX, 0.0F, z, outerX, height, z, innerX, height, z, red, green, blue, alpha(148, innerAlpha), alpha(148, outerAlpha), alpha(148, outerAlpha), alpha(148, innerAlpha));
            gradientQuad(consumer, poseStack, -outerX, 0.0F, z, -innerX, 0.0F, z, -innerX, height, z, -outerX, height, z, red, green, blue, alpha(148, outerAlpha), alpha(148, innerAlpha), alpha(148, innerAlpha), alpha(148, outerAlpha));
        }
    }

    private static float featherAlpha(float normalizedDistance, float fade) {
        float edge = 1.0F - normalizedDistance;
        return fade * edge * edge;
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
        float half = thickness * 0.5F;
        float px = -dy / length * half;
        float py = dx / length * half;
        quad(consumer, poseStack, x1 - px, y1 - py, z1, x1 + px, y1 + py, z1, x2 + px, y2 + py, z2, x2 - px, y2 - py, z2, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha);
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
        private float width = 2.0F;
        private float height = 3.4F;
        private int life = 32;
        private float yaw;
        private int color = 0xF8FCFF;
        private Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);
    }
}
