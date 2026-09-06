package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.AbyssalDischargeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class AbyssalDischargeRenderer extends EntityRenderer<AbyssalDischargeEntity, AbyssalDischargeRenderer.State> {
    public AbyssalDischargeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AbyssalDischargeEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.life = entity.life();
        state.color = entity.color();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float progress = Mth.clamp(state.ageInTicks / Math.max(1.0F, state.life), 0.0F, 1.0F);
        float expansion = Mth.clamp((state.ageInTicks - 2.0F) / Math.max(1.0F, state.life * 0.48F), 0.0F, 1.0F);
        float fade = Mth.sin((1.0F - progress) * Mth.HALF_PI);
        float activeRadius = state.radius * expansion;
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        int red = red(state.color);
        int green = green(state.color);
        int blue = blue(state.color);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 4.2F));
        ring(consumer, poseStack, activeRadius, 0.1F + expansion * 0.12F, 0.05F, 92, 32, 145, alpha(210, fade));
        ring(consumer, poseStack, state.radius * 0.72F, 0.045F, 0.09F, red, green, blue, alpha(130, fade));
        ring(consumer, poseStack, state.radius * 0.42F, 0.035F, 0.12F, 155, 72, 215, alpha(120, fade));
        polygon(consumer, poseStack, 9, state.radius * 0.84F, 0.06F, 0.14F, state.ageInTicks * -2.4F, 70, 18, 118, alpha(155, fade));
        polygon(consumer, poseStack, 5, state.radius * 0.36F, 0.045F, 0.16F, 90.0F + state.ageInTicks * 5.0F, 178, 88, 240, alpha(140, fade));
        glyphTicks(consumer, poseStack, 28, state.radius * 0.58F, 0.08F, 0.18F, 123, 34, 188, alpha(145, fade));
        spokes(consumer, poseStack, 18, state.radius * 0.18F, activeRadius, 0.025F, 0.2F, 65, 11, 90, alpha(90, fade));
        poseStack.popPose();

        for (int i = 0; i < 16; i++) {
            float angle = (360.0F * i / 16.0F) + state.ageInTicks * (1.4F + (i % 3) * 0.35F);
            float radians = angle * Mth.DEG_TO_RAD;
            float distance = activeRadius * (0.45F + (i % 4) * 0.13F);
            float height = 1.7F + (i % 5) * 0.34F + Mth.sin(state.ageInTicks * 0.2F + i) * 0.25F;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(radians) * distance, 0.9F + height * 0.32F, Mth.sin(radians) * distance);
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle + state.ageInTicks * 9.0F));
            tear(consumer, poseStack, 0.16F + expansion * 0.14F, height, red, green, blue, alpha(95 + (i % 3) * 20, fade));
            poseStack.popPose();
        }

        super.render(state, poseStack, buffer, packedLight);
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float y, int red, int green, int blue, int alpha) {
        if (radius <= 0.01F || alpha <= 0) {
            return;
        }
        float inner = Math.max(0.01F, radius - thickness);
        float outer = radius + thickness;
        for (int i = 0; i < 128; i++) {
            float angleA = Mth.TWO_PI * i / 128.0F;
            float angleB = Mth.TWO_PI * (i + 1) / 128.0F;
            quad(consumer, poseStack,
                    Mth.cos(angleA) * inner, y, Mth.sin(angleA) * inner,
                    Mth.cos(angleA) * outer, y, Mth.sin(angleA) * outer,
                    Mth.cos(angleB) * outer, y, Mth.sin(angleB) * outer,
                    Mth.cos(angleB) * inner, y, Mth.sin(angleB) * inner,
                    red, green, blue, alpha);
        }
    }

    private static void polygon(VertexConsumer consumer, PoseStack poseStack, int sides, float radius, float thickness, float y, float offsetDegrees, int red, int green, int blue, int alpha) {
        float offset = offsetDegrees * Mth.DEG_TO_RAD;
        for (int i = 0; i < sides; i++) {
            float angleA = offset + Mth.TWO_PI * i / sides;
            float angleB = offset + Mth.TWO_PI * (i + 1) / sides;
            line(consumer, poseStack, Mth.cos(angleA) * radius, y, Mth.sin(angleA) * radius, Mth.cos(angleB) * radius, y, Mth.sin(angleB) * radius, thickness, red, green, blue, alpha);
        }
    }

    private static void glyphTicks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float length, float y, int red, int green, int blue, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            float inner = radius - length * (1.0F + (i % 3) * 0.65F);
            float outer = radius + length * (1.2F + (i % 4) * 0.4F);
            line(consumer, poseStack, Mth.cos(angle) * inner, y, Mth.sin(angle) * inner, Mth.cos(angle) * outer, y, Mth.sin(angle) * outer, 0.026F, red, green, blue, alpha);
        }
    }

    private static void spokes(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, float y, int red, int green, int blue, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            line(consumer, poseStack, Mth.cos(angle) * innerRadius, y, Mth.sin(angle) * innerRadius, Mth.cos(angle) * outerRadius, y, Mth.sin(angle) * outerRadius, thickness, red, green, blue, alpha);
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, int red, int green, int blue, int alpha) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length <= 0.001F || alpha <= 0) {
            return;
        }
        float px = -dz / length * thickness * 0.5F;
        float pz = dx / length * thickness * 0.5F;
        quad(consumer, poseStack, x1 - px, y1, z1 - pz, x1 + px, y1, z1 + pz, x2 + px, y2, z2 + pz, x2 - px, y2, z2 - pz, red, green, blue, alpha);
    }

    private static void tear(VertexConsumer consumer, PoseStack poseStack, float width, float height, int red, int green, int blue, int alpha) {
        quad(consumer, poseStack, -width, -height * 0.55F, 0.0F, width, -height * 0.55F, 0.0F, width * 0.25F, height * 0.45F, 0.0F, -width * 0.25F, height * 0.45F, 0.0F, red, green, blue, alpha);
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
        private float radius = 12.0F;
        private int life = 36;
        private int color = 0x341052;
    }
}
