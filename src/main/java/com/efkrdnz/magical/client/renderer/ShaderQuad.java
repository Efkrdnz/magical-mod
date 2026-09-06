package com.efkrdnz.magical.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class ShaderQuad {
    private ShaderQuad() {
    }

    public static void billboard(VertexConsumer consumer, PoseStack poseStack, float width, float height, int color, int alpha) {
        plane(consumer, poseStack, width, height, color, alpha);
        reversePlane(consumer, poseStack, width, height, color, alpha);
    }

    public static void plane(VertexConsumer consumer, PoseStack poseStack, float width, float height, int color, int alpha) {
        float x = width * 0.5F;
        float y = height * 0.5F;
        int r = color >> 16 & 255;
        int g = color >> 8 & 255;
        int b = color & 255;
        consumer.addVertex(poseStack.last(), -x, -y, 0.0F).setUv(0.0F, 1.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), x, -y, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), x, y, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), -x, y, 0.0F).setUv(0.0F, 0.0F).setColor(r, g, b, alpha);
    }

    public static void reversePlane(VertexConsumer consumer, PoseStack poseStack, float width, float height, int color, int alpha) {
        float x = width * 0.5F;
        float y = height * 0.5F;
        int r = color >> 16 & 255;
        int g = color >> 8 & 255;
        int b = color & 255;
        consumer.addVertex(poseStack.last(), -x, y, 0.0F).setUv(0.0F, 0.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), x, y, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), x, -y, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), -x, -y, 0.0F).setUv(0.0F, 1.0F).setColor(r, g, b, alpha);
    }
}
