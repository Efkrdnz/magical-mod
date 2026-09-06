package com.efkrdnz.magical.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

public final class RenderShape {
    private RenderShape() {
    }

    public static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float y, int color, float alpha) {
        int steps = 128;
        float thickness = Math.max(0.015F, radius * 0.006F);
        for (int i = 0; i < steps; i++) {
            float a0 = Mth.TWO_PI * i / steps;
            float a1 = Mth.TWO_PI * (i + 1) / steps;
            quad(consumer, poseStack,
                    Mth.cos(a0) * (radius - thickness), y, Mth.sin(a0) * (radius - thickness),
                    Mth.cos(a0) * (radius + thickness), y, Mth.sin(a0) * (radius + thickness),
                    Mth.cos(a1) * (radius + thickness), y, Mth.sin(a1) * (radius + thickness),
                    Mth.cos(a1) * (radius - thickness), y, Mth.sin(a1) * (radius - thickness),
                    color, alpha);
        }
    }

    public static void disc(VertexConsumer consumer, PoseStack poseStack, float radius, int color, float alpha) {
        int steps = 96;
        for (int i = 0; i < steps; i++) {
            float a0 = Mth.TWO_PI * i / steps;
            float a1 = Mth.TWO_PI * (i + 1) / steps;
            quad(consumer, poseStack,
                    0.0F, -radius, 0.0F,
                    Mth.cos(a0) * radius, 0.0F, Mth.sin(a0) * radius,
                    0.0F, radius, 0.0F,
                    Mth.cos(a1) * radius, 0.0F, Mth.sin(a1) * radius,
                    color, alpha);
        }
    }

    public static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, int color, float alpha) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        float nx = length < 1.0E-5F ? thickness : -dz / length * thickness;
        float nz = length < 1.0E-5F ? 0.0F : dx / length * thickness;
        quad(consumer, poseStack, x1 - nx, y1, z1 - nz, x1 + nx, y1, z1 + nz, x2 + nx, y2, z2 + nz, x2 - nx, y2, z2 - nz, color, alpha);
    }

    public static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int color, float alpha) {
        int a = Mth.clamp((int) (255.0F * alpha), 0, 255);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(r, g, b, a);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(r, g, b, a);
    }
}
