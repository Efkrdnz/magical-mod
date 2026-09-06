package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SingularityEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class SingularityRenderer extends EntityRenderer<SingularityEntity, SingularityRenderer.State> {
    public SingularityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SingularityEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.visualRadius();
        state.charge = entity.chargeFactor();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = state.ageInTicks;
        float radius = state.radius * (0.98F + Mth.sin(age * 0.19F) * 0.035F);
        float formed = smooth(state.charge);
        float formingFade = 1.0F - formed;

        if (formingFade > 0.0F) {
            VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
            poseStack.pushPose();
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            drawFormingOrbs(glow, poseStack, radius, age, formed, formingFade);
            poseStack.popPose();
        }

        VertexConsumer lens = buffer.getBuffer(MagicalRenderTypes.singularityLens());
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 2.0F));
        drawShaderLens(lens, poseStack, radius * 3.15F, radius * 3.15F, Mth.clamp(Math.round(70 + formed * 175.0F), 0, 245));
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2.4F));
        poseStack.mulPose(Axis.XP.rotationDegrees(72.0F));
        drawShaderLens(lens, poseStack, radius * 5.7F, radius * 1.18F, Mth.clamp(Math.round(35 + formed * 137.0F), 0, 172));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 3.1F));
        drawShaderLens(lens, poseStack, radius * 4.3F, radius * 0.86F, Mth.clamp(Math.round(24 + formed * 94.0F), 0, 118));
        poseStack.popPose();

        super.render(state, poseStack, buffer, packedLight);
    }

    private static void drawShaderLens(VertexConsumer consumer, PoseStack poseStack, float width, float height, int alpha) {
        float x = width * 0.5F;
        float y = height * 0.5F;
        consumer.addVertex(poseStack.last(), -x, -y, 0.0F).setUv(0.0F, 1.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), x, -y, 0.0F).setUv(1.0F, 1.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), x, y, 0.0F).setUv(1.0F, 0.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), -x, y, 0.0F).setUv(0.0F, 0.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), -x, y, 0.0F).setUv(0.0F, 0.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), x, y, 0.0F).setUv(1.0F, 0.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), x, -y, 0.0F).setUv(1.0F, 1.0F).setColor(255, 255, 255, alpha);
        consumer.addVertex(poseStack.last(), -x, -y, 0.0F).setUv(0.0F, 1.0F).setColor(255, 255, 255, alpha);
    }

    private static void drawFormingOrbs(VertexConsumer consumer, PoseStack poseStack, float radius, float age, float progress, float fade) {
        int[] colors = {0xFF68D8, 0x32104F, 0x8EEBFF, 0xFFFFFF, 0xC43DFF, 0x18051F, 0xBFF7FF, 0xFFEAFE};
        for (int i = 0; i < 24; i++) {
            float seed = i * 37.0F;
            float angle = seed * Mth.DEG_TO_RAD + age * (0.018F + (i % 5) * 0.003F);
            float wobble = Mth.sin(age * 0.09F + i * 1.71F) * 0.16F;
            float start = radius * (3.1F + (i % 6) * 0.34F);
            float end = radius * (0.12F + (i % 4) * 0.03F);
            float distance = Mth.lerp(progress, start, end);
            float x = Mth.cos(angle) * distance;
            float y = Mth.sin(angle * 1.37F + i) * radius * (1.18F + (i % 3) * 0.16F) * (1.0F - progress * 0.72F) + wobble;
            float trailX = Mth.cos(angle - 0.18F) * distance * (1.0F + 0.06F * (i % 2));
            float trailY = y + Mth.sin(angle + i) * radius * 0.18F;
            int color = colors[i % colors.length];
            float localPulse = 0.82F + Mth.sin(age * 0.31F + i) * 0.18F;
            int alpha = alpha(150 + (i % 4) * 18, fade * localPulse);
            line(consumer, poseStack, trailX, trailY, x * (0.24F + progress * 0.18F), y * (0.24F + progress * 0.18F), radius * 0.026F, red(color), green(color), blue(color), alpha(78, fade));
            orb(consumer, poseStack, x, y, radius * (0.12F + (i % 4) * 0.025F) * (1.08F - progress * 0.34F), color, alpha);
        }
        for (int i = 0; i < 8; i++) {
            float angle = Mth.TWO_PI * i / 8.0F - age * 0.026F;
            float distance = radius * Mth.lerp(progress, 2.2F, 0.34F);
            int color = colors[(i * 3 + 2) % colors.length];
            line(consumer, poseStack, Mth.cos(angle) * distance, Mth.sin(angle) * distance, 0.0F, 0.0F, radius * 0.018F, red(color), green(color), blue(color), alpha(95, fade));
        }
    }

    private static void orb(VertexConsumer consumer, PoseStack poseStack, float x, float y, float radius, int color, int alpha) {
        if (alpha <= 0 || radius <= 0.001F) {
            return;
        }
        quad(consumer, poseStack,
                x - radius, y - radius,
                x + radius, y - radius,
                x + radius, y + radius,
                x - radius, y + radius,
                red(color), green(color), blue(color), alpha);
        quad(consumer, poseStack,
                x, y - radius * 1.36F,
                x + radius * 1.36F, y,
                x, y + radius * 1.36F,
                x - radius * 1.36F, y,
                255, 255, 255, Math.min(190, alpha));
    }

    private static void drawBlackCore(VertexConsumer consumer, PoseStack poseStack, float radius, float age, float fade) {
        float easedFade = smooth(fade);
        for (int i = 0; i < 9; i++) {
            float scale = radius * (1.04F - i * 0.075F);
            int alpha = alpha(Mth.clamp(238 - i * 12, 0, 255), easedFade);
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * (3.0F + i * 0.8F) + i * 23.0F));
            quad(consumer, poseStack,
                    0.0F, -scale,
                    scale, 0.0F,
                    0.0F, scale,
                    -scale, 0.0F,
                    0, 0, 2 + i, alpha);
            poseStack.popPose();
        }
        for (int i = 0; i < 16; i++) {
            float angle = Mth.TWO_PI * i / 16.0F + age * 0.035F;
            float x = Mth.cos(angle) * radius * 0.62F;
            float y = Mth.sin(angle) * radius * 0.62F;
            line(consumer, poseStack, x, y, -x * 0.18F, -y * 0.18F, radius * 0.018F, 2, 4, 18, alpha(120, easedFade));
        }
    }

    private static void drawLensingHalo(VertexConsumer consumer, PoseStack poseStack, float radius, float age) {
        for (int layer = 0; layer < 5; layer++) {
            float ringRadius = radius * (1.1F + layer * 0.22F + Mth.sin(age * 0.05F + layer) * 0.025F);
            int alpha = 128 - layer * 18;
            int red = layer % 2 == 0 ? 90 : 30;
            int green = layer % 2 == 0 ? 160 : 80;
            int blue = 255;
            brokenRing(consumer, poseStack, ringRadius, radius * (0.018F + layer * 0.004F), 72, red, green, blue, alpha, age * (0.8F + layer * 0.15F));
        }
    }

    private static void drawAccretionDisk(VertexConsumer consumer, PoseStack poseStack, float radius, float age, float z) {
        for (int i = 0; i < 42; i++) {
            float angleA = Mth.TWO_PI * i / 42.0F + age * 0.055F;
            float angleB = angleA + 0.12F + Mth.sin(age * 0.02F + i) * 0.035F;
            float inner = radius * (1.18F + (i % 3) * 0.03F);
            float outer = radius * (2.15F + (i % 5) * 0.08F);
            int alpha = 74 + (i % 4) * 16;
            int red = i % 5 == 0 ? 205 : 70;
            int green = i % 5 == 0 ? 235 : 145;
            int blue = 255;
            quad3d(consumer, poseStack,
                    Mth.cos(angleA) * inner, z, Mth.sin(angleA) * inner,
                    Mth.cos(angleA) * outer, z, Mth.sin(angleA) * outer,
                    Mth.cos(angleB) * outer, z, Mth.sin(angleB) * outer,
                    Mth.cos(angleB) * inner, z, Mth.sin(angleB) * inner,
                    red, green, blue, alpha);
        }
    }

    private static void brokenRing(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, int segments, int red, int green, int blue, int alpha, float phase) {
        for (int i = 0; i < segments; i++) {
            if ((i + Mth.floor(phase * 0.05F)) % 7 == 0) {
                continue;
            }
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 0.74F) / segments;
            float warpA = Mth.sin(phase * 0.13F + i * 0.91F) * thickness * 2.8F;
            float warpB = Mth.cos(phase * 0.11F + i * 1.17F) * thickness * 2.8F;
            quad(consumer, poseStack,
                    Mth.cos(a) * (radius - thickness + warpA), Mth.sin(a) * (radius - thickness + warpA),
                    Mth.cos(a) * (radius + thickness + warpA), Mth.sin(a) * (radius + thickness + warpA),
                    Mth.cos(b) * (radius + thickness + warpB), Mth.sin(b) * (radius + thickness + warpB),
                    Mth.cos(b) * (radius - thickness + warpB), Mth.sin(b) * (radius - thickness + warpB),
                    red, green, blue, alpha);
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float x2, float y2, float thickness, int red, int green, int blue, int alpha) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001F || alpha <= 0) {
            return;
        }
        float px = -dy / length * thickness;
        float py = dx / length * thickness;
        quad(consumer, poseStack, x1 - px, y1 - py, x1 + px, y1 + py, x2 + px, y2 + py, x2 - px, y2 - py, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), x1, y1, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x1, y1, 0.0F).setColor(red, green, blue, alpha);
    }

    private static void quad3d(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha);
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
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
        private float radius = 1.0F;
        private float charge = 1.0F;
    }
}
