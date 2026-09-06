package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SpaceSubspaceEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

public final class SpaceSubspaceRenderer extends EntityRenderer<SpaceSubspaceEntity, SpaceSubspaceRenderer.State> {
    public SpaceSubspaceRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpaceSubspaceEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.age = entity.tickCount + partialTick;
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float radius = state.radius;
        float pulse = 0.965F + Mth.sin(state.age * 0.06F) * 0.025F;
        drawShellBands(consumer, poseStack, radius * pulse, 30 + (int) (Mth.sin(state.age * 0.07F) * 8.0F));
        drawShellBands(consumer, poseStack, radius * (0.86F + Mth.sin(state.age * 0.043F) * 0.018F), 16);
        drawSphereRings(consumer, poseStack, radius * pulse, 122, state.age);
        drawOrbitArcs(consumer, poseStack, radius, state.age);
        drawGlyphTicks(consumer, poseStack, radius, state.age);
        drawGlints(consumer, poseStack, radius, state.age);
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void drawShellBands(VertexConsumer consumer, PoseStack poseStack, float radius, int alpha) {
        for (int i = -5; i <= 5; i++) {
            float y1 = radius * i / 6.0F;
            float y2 = radius * (i + 0.34F) / 6.0F;
            float r1 = (float) Math.sqrt(Math.max(0.0F, radius * radius - y1 * y1));
            float r2 = (float) Math.sqrt(Math.max(0.0F, radius * radius - y2 * y2));
            int localAlpha = alpha - Math.abs(i) * 2;
            ringSurface(consumer, poseStack, r1, r2, y1, y2, Math.max(4, localAlpha));
        }
    }

    private static void drawSphereRings(VertexConsumer consumer, PoseStack poseStack, float radius, int alpha, float age) {
        ring(consumer, poseStack, radius, 0.055F, 0.0F, alpha, 170, 245, 255);
        for (float y : new float[] {-0.78F, -0.54F, -0.27F, 0.27F, 0.54F, 0.78F}) {
            float ringY = radius * y;
            float ringRadius = (float) Math.sqrt(Math.max(0.0F, radius * radius - ringY * ringY));
            ring(consumer, poseStack, ringRadius, 0.024F, ringY, alpha - 34, y < 0.0F ? 125 : 190, y < 0.0F ? 224 : 248, 255);
        }
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 45.0F + age * (0.22F + i * 0.04F)));
            poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(90.0D)));
            ring(consumer, poseStack, radius * (0.98F - i * 0.015F), 0.028F, 0.0F, alpha - i * 12, 138, 231, 255);
            poseStack.popPose();
        }
    }

    private static void drawOrbitArcs(VertexConsumer consumer, PoseStack poseStack, float radius, float age) {
        for (int i = 0; i < 5; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 72.0F + age * (0.62F + i * 0.11F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(26.0F + i * 11.0F));
            dashedRing(consumer, poseStack, radius * (0.72F + i * 0.055F), radius * 0.011F, 0.0F, 128, 18 + i, 104, 235, 255, 112 - i * 10);
            poseStack.popPose();
        }
    }

    private static void drawGlyphTicks(VertexConsumer consumer, PoseStack poseStack, float radius, float age) {
        for (int layer = 0; layer < 3; layer++) {
            float y = radius * (-0.42F + layer * 0.42F);
            float ringRadius = (float) Math.sqrt(Math.max(0.0F, radius * radius - y * y)) * 0.96F;
            int count = 24 + layer * 8;
            for (int i = 0; i < count; i++) {
                float angle = Mth.TWO_PI * i / count + age * 0.008F * (layer + 1);
                float tick = radius * (i % 3 == 0 ? 0.09F : 0.052F);
                float thickness = radius * 0.0048F;
                float inner = ringRadius - tick;
                float outer = ringRadius;
                line(consumer, poseStack,
                        Mth.cos(angle) * inner, y, Mth.sin(angle) * inner,
                        Mth.cos(angle) * outer, y, Mth.sin(angle) * outer,
                        thickness, 190, 248, 255, 92);
            }
        }
    }

    private static void drawGlints(VertexConsumer consumer, PoseStack poseStack, float radius, float age) {
        for (int i = 0; i < 9; i++) {
            float angle = Mth.TWO_PI * i / 9.0F + age * 0.018F;
            float y = Mth.sin(age * 0.027F + i * 1.7F) * radius * 0.64F;
            float horizontal = (float) Math.sqrt(Math.max(0.0F, radius * radius - y * y)) * 1.01F;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(angle) * horizontal, y, Mth.sin(angle) * horizontal);
            poseStack.mulPose(Axis.YP.rotationDegrees(-age * 2.5F + i * 37.0F));
            spark(consumer, poseStack, radius * (0.035F + (i % 3) * 0.012F), 225, 252, 255, 118);
            poseStack.popPose();
        }
    }

    private static void ringSurface(VertexConsumer consumer, PoseStack poseStack, float r1, float r2, float y1, float y2, int alpha) {
        int segments = 72;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.PI * 2.0D * i / segments;
            double a2 = Math.PI * 2.0D * (i + 1) / segments;
            quad(
                    consumer,
                    poseStack,
                    (float) Math.cos(a1) * r1,
                    y1,
                    (float) Math.sin(a1) * r1,
                    (float) Math.cos(a2) * r1,
                    y1,
                    (float) Math.sin(a2) * r1,
                    (float) Math.cos(a2) * r2,
                    y2,
                    (float) Math.sin(a2) * r2,
                    (float) Math.cos(a1) * r2,
                    y2,
                    (float) Math.sin(a1) * r2,
                    78,
                    205,
                    255,
                    alpha);
        }
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float y, int alpha, int red, int green, int blue) {
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.PI * 2.0D * i / segments;
            double a2 = Math.PI * 2.0D * (i + 1) / segments;
            float inner = radius - thickness;
            float outer = radius + thickness;
            quad(
                    consumer,
                    poseStack,
                    (float) Math.cos(a1) * inner,
                    y,
                    (float) Math.sin(a1) * inner,
                    (float) Math.cos(a2) * inner,
                    y,
                    (float) Math.sin(a2) * inner,
                    (float) Math.cos(a2) * outer,
                    y,
                    (float) Math.sin(a2) * outer,
                    (float) Math.cos(a1) * outer,
                    y,
                    (float) Math.sin(a1) * outer,
                    red,
                    green,
                    blue,
                    alpha);
        }
    }

    private static void dashedRing(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float y, int segments, int dashEvery, int red, int green, int blue, int alpha) {
        for (int i = 0; i < segments; i++) {
            if (i % dashEvery > dashEvery * 0.54F) {
                continue;
            }
            float angleA = Mth.TWO_PI * i / segments;
            float angleB = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    Mth.cos(angleA) * (radius - thickness), y, Mth.sin(angleA) * (radius - thickness),
                    Mth.cos(angleA) * (radius + thickness), y, Mth.sin(angleA) * (radius + thickness),
                    Mth.cos(angleB) * (radius + thickness), y, Mth.sin(angleB) * (radius + thickness),
                    Mth.cos(angleB) * (radius - thickness), y, Mth.sin(angleB) * (radius - thickness),
                    red, green, blue, alpha);
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, int red, int green, int blue, int alpha) {
        Vec3f normal = perpendicular(x2 - x1, y2 - y1, z2 - z1, thickness);
        quad(consumer, poseStack,
                x1 - normal.x, y1 - normal.y, z1 - normal.z,
                x1 + normal.x, y1 + normal.y, z1 + normal.z,
                x2 + normal.x, y2 + normal.y, z2 + normal.z,
                x2 - normal.x, y2 - normal.y, z2 - normal.z,
                red, green, blue, alpha);
    }

    private static Vec3f perpendicular(float dx, float dy, float dz, float thickness) {
        float px = -dz;
        float py = 0.0F;
        float pz = dx;
        float length = Mth.sqrt(px * px + py * py + pz * pz);
        if (length < 1.0E-5F) {
            px = 1.0F;
            py = 0.0F;
            pz = 0.0F;
            length = 1.0F;
        }
        return new Vec3f(px / length * thickness, py / length * thickness, pz / length * thickness);
    }

    private static void spark(VertexConsumer consumer, PoseStack poseStack, float size, int red, int green, int blue, int alpha) {
        quad(consumer, poseStack, -size, 0.0F, 0.0F, 0.0F, -size * 0.18F, 0.0F, size, 0.0F, 0.0F, 0.0F, size * 0.18F, 0.0F, red, green, blue, alpha);
        quad(consumer, poseStack, 0.0F, -size, 0.0F, size * 0.18F, 0.0F, 0.0F, 0.0F, size, 0.0F, -size * 0.18F, 0.0F, 0.0F, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha);
    }

    public static final class State extends EntityRenderState {
        private float radius = 5.0F;
        private float age;
    }

    private record Vec3f(float x, float y, float z) {}
}
