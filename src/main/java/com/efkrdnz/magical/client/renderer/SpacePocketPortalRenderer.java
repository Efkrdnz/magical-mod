package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SpacePocketPortalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class SpacePocketPortalRenderer extends EntityRenderer<SpacePocketPortalEntity, SpacePocketPortalRenderer.State> {
    public SpacePocketPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpacePocketPortalEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.mode = entity.mode();
        state.radius = entity.radius();
        state.life = entity.life();
        state.yaw = entity.yaw();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float age = state.ageInTicks;
        float fade = state.life < 0 ? 1.0F : 1.0F - Mth.clamp((age - state.life + 24.0F) / 24.0F, 0.0F, 1.0F);
        float appear = Mth.clamp(age / 12.0F, 0.0F, 1.0F);
        float alpha = fade * appear;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
        if (state.mode == SpacePocketPortalEntity.MODE_EXIT_DOOR) {
            renderDoor(consumer, poseStack, state.radius, alpha, age);
        } else {
            renderTear(consumer, poseStack, state.radius, alpha, age);
        }
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void renderDoor(VertexConsumer consumer, PoseStack poseStack, float radius, float fade, float age) {
        float height = radius * 3.3F;
        float width = radius * 1.42F;
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.08F);
        veil(consumer, poseStack, width * 1.18F, height * 1.08F, 2, 1, 12, alpha(230, fade));
        veil(consumer, poseStack, width * 0.9F, height * 0.88F, 11, 4, 34, alpha(190, fade));
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, -0.02F + i * 0.018F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * (0.7F + i * 0.24F) + i * 41.0F));
            jaggedRing(consumer, poseStack, width * (0.7F + i * 0.13F), height * (0.56F + i * 0.11F), 44, i % 2 == 0 ? 38 : 120, i % 2 == 0 ? 120 : 245, 255, alpha(70 - i * 8, fade), age + i * 9.0F);
            poseStack.popPose();
        }
        for (int i = 0; i < 6; i++) {
            float inset = i * radius * 0.105F;
            int red = i % 2 == 0 ? 72 : 13;
            int green = i % 2 == 0 ? 225 : 92;
            int blue = i % 2 == 0 ? 255 : 210;
            frame(consumer, poseStack, width - inset, height - inset * 1.28F, radius * (0.018F + i * 0.0036F), red, green, blue, alpha(215 - i * 24, fade));
        }
        for (int i = 0; i < 13; i++) {
            float y = -height * 0.47F + i * height / 12.0F;
            float phase = age * 0.1F + i * 0.73F;
            float sway = Mth.sin(phase) * radius * (0.09F + (i % 3) * 0.035F);
            float endSway = Mth.cos(phase * 0.82F) * radius * 0.12F;
            int a = alpha(55 + (i % 4) * 12, fade);
            line(consumer, poseStack, -width * 0.42F + sway, y, 0.045F, width * 0.42F + endSway, y + Mth.sin(age * 0.075F + i) * radius * 0.045F, 0.045F, radius * 0.009F, 80, 215, 255, a);
        }
        for (int i = 0; i < 24; i++) {
            float seed = i * 12.9898F;
            float drift = age * (0.006F + (i % 5) * 0.0018F);
            float x = (Mth.sin(seed) * 0.5F + 0.5F) * width * 0.78F - width * 0.39F;
            float y = ((Mth.cos(seed * 1.37F + drift) * 0.5F + 0.5F) * height * 0.86F) - height * 0.43F;
            float twinkle = 0.45F + 0.55F * Mth.sin(age * 0.16F + i * 1.91F);
            diamond(consumer, poseStack, x, y, 0.07F, radius * (0.008F + twinkle * 0.011F), 170, 245, 255, alpha(65 + Math.round(twinkle * 90.0F), fade));
        }
        for (int i = 0; i < 10; i++) {
            float x = -width * 0.4F + i * width * 0.8F / 9.0F + Mth.sin(age * 0.05F + i) * radius * 0.025F;
            float top = height * 0.43F;
            float bottom = -height * 0.43F;
            int a = alpha(38 + (int) (pulse * 30.0F), fade);
            line(consumer, poseStack, x, top, 0.055F, x + Mth.sin(age * 0.09F + i) * radius * 0.08F, bottom, 0.055F, radius * 0.006F, 32, 155, 255, a);
        }
        for (int i = 0; i < 5; i++) {
            float y = -height * 0.34F + i * height * 0.17F + Mth.sin(age * 0.035F + i) * radius * 0.035F;
            float half = width * (0.17F + i * 0.045F);
            line(consumer, poseStack, -half, y, 0.075F, 0.0F, y + radius * 0.075F, 0.075F, radius * 0.012F, 145, 250, 255, alpha(90, fade));
            line(consumer, poseStack, 0.0F, y + radius * 0.075F, 0.075F, half, y, 0.075F, radius * 0.012F, 145, 250, 255, alpha(90, fade));
        }
        frame(consumer, poseStack, width * (1.05F + pulse * 0.025F), height * (1.025F + pulse * 0.018F), radius * 0.012F, 225, 255, 255, alpha(125, fade));
    }

    private static void renderTear(VertexConsumer consumer, PoseStack poseStack, float radius, float fade, float age) {
        veil(consumer, poseStack, radius * 1.7F, radius * 2.4F, 5, 1, 14, alpha(150, fade));
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * (2.0F + i * 0.7F) + i * 37.0F));
            jaggedRing(consumer, poseStack, radius * (0.74F + i * 0.14F), radius * (1.05F + i * 0.18F), 28, i % 2 == 0 ? 82 : 20, i % 2 == 0 ? 210 : 80, i % 2 == 0 ? 255 : 190, alpha(145 - i * 24, fade), age + i);
            poseStack.popPose();
        }
        for (int i = 0; i < 12; i++) {
            float a = Mth.TWO_PI * i / 12.0F + age * 0.035F;
            float x = Mth.cos(a) * radius * (0.38F + (i % 3) * 0.17F);
            float y = Mth.sin(a) * radius * (0.7F + (i % 2) * 0.22F);
            line(consumer, poseStack, x * 0.18F, y * 0.18F, 0.06F, x, y, 0.06F, radius * 0.012F, 90, 225, 255, alpha(105, fade));
        }
    }

    private static void veil(VertexConsumer consumer, PoseStack poseStack, float width, float height, int red, int green, int blue, int alpha) {
        gradientQuad(consumer, poseStack,
                -width * 0.5F, -height * 0.5F, 0.0F,
                width * 0.5F, -height * 0.5F, 0.0F,
                width * 0.5F, height * 0.5F, 0.0F,
                -width * 0.5F, height * 0.5F, 0.0F,
                red, green, blue, alpha / 5, alpha / 2, alpha / 3, alpha / 4);
    }

    private static void frame(VertexConsumer consumer, PoseStack poseStack, float width, float height, float thickness, int red, int green, int blue, int alpha) {
        line(consumer, poseStack, -width * 0.5F, -height * 0.5F, 0.05F, width * 0.5F, -height * 0.5F, 0.05F, thickness, red, green, blue, alpha);
        line(consumer, poseStack, width * 0.5F, -height * 0.5F, 0.05F, width * 0.5F, height * 0.5F, 0.05F, thickness, red, green, blue, alpha);
        line(consumer, poseStack, width * 0.5F, height * 0.5F, 0.05F, -width * 0.5F, height * 0.5F, 0.05F, thickness, red, green, blue, alpha);
        line(consumer, poseStack, -width * 0.5F, height * 0.5F, 0.05F, -width * 0.5F, -height * 0.5F, 0.05F, thickness, red, green, blue, alpha);
    }

    private static void diamond(VertexConsumer consumer, PoseStack poseStack, float x, float y, float z, float size, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        gradientQuad(consumer, poseStack,
                x, y + size, z,
                x + size * 0.62F, y, z,
                x, y - size, z,
                x - size * 0.62F, y, z,
                red, green, blue, alpha / 3, alpha, alpha / 2, alpha);
    }

    private static void jaggedRing(VertexConsumer consumer, PoseStack poseStack, float width, float height, int segments, int red, int green, int blue, int alpha, float age) {
        float previousX = 0.0F;
        float previousY = height * 0.5F;
        for (int i = 1; i <= segments; i++) {
            float a = Mth.TWO_PI * i / segments;
            float jitter = 1.0F + Mth.sin(age * 0.31F + i * 1.7F) * 0.12F;
            float x = Mth.sin(a) * width * 0.5F * jitter;
            float y = Mth.cos(a) * height * 0.5F * jitter;
            line(consumer, poseStack, previousX, previousY, 0.05F, x, y, 0.05F, width * 0.012F, red, green, blue, alpha);
            previousX = x;
            previousY = y;
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
        gradientQuad(consumer, poseStack, x1 - px, y1 - py, z1, x1 + px, y1 + py, z1, x2 + px, y2 + py, z2, x2 - px, y2 - py, z2, red, green, blue, alpha / 2, alpha, alpha, alpha / 2);
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
        private int mode;
        private float radius = 1.5F;
        private int life = 200;
        private float yaw;
    }
}
