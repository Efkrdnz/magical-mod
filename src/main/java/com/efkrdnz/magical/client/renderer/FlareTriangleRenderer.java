package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.FlareTriangleEntity;
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

public final class FlareTriangleRenderer extends EntityRenderer<FlareTriangleEntity, FlareTriangleRenderer.State> {
    private static final int RING_SEGMENTS = 56;

    public FlareTriangleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FlareTriangleEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.pointCount = entity.pointCount();
        state.wallThickness = entity.wallThickness();
        state.completedProgress = entity.completedProgress(partialTick);
        state.baseY = entity.baseY();
        state.origin = entity.position();
        for (int i = 0; i < 3; i++) {
            state.points[i] = entity.point(i);
            state.eruptions[i] = entity.eruptionTicks(i);
        }
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float age = state.ageInTicks;
        poseStack.pushPose();
        poseStack.translate(-state.origin.x, -state.origin.y, -state.origin.z);

        for (int i = 0; i < state.pointCount; i++) {
            Vec3 point = state.points[i];
            poseStack.pushPose();
            poseStack.translate(point.x, point.y + 0.055D, point.z);
            float pulse = 1.0F + Mth.sin(age * 0.22F + i * 1.9F) * 0.07F;
            groundSigil(consumer, poseStack, (1.05F + state.wallThickness * 0.35F) * pulse, age, i);
            if (state.eruptions[i] > 0) {
                geyser(consumer, poseStack, state.eruptions[i], age, i);
            }
            poseStack.popPose();
        }

        if (state.pointCount >= 2) {
            wall(consumer, poseStack, state.points[0], state.points[1], state.wallThickness, age, 0);
        }
        if (state.pointCount >= 3) {
            wall(consumer, poseStack, state.points[1], state.points[2], state.wallThickness, age, 1);
            wall(consumer, poseStack, state.points[2], state.points[0], state.wallThickness, age, 2);
            triangleVeins(consumer, poseStack, state.points[0], state.points[1], state.points[2], state.baseY, age, state.completedProgress);
        }

        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void groundSigil(VertexConsumer consumer, PoseStack poseStack, float radius, float age, int index) {
        int orange = 0xFF7A1E;
        int gold = 0xFFE08A;
        ring(consumer, poseStack, radius, 0.055F, 0.0F, orange, 210);
        ring(consumer, poseStack, radius * 0.66F, 0.04F, 0.012F, gold, 165);
        ring(consumer, poseStack, radius * 1.28F, 0.035F, 0.018F, 0xFF3212, 120);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * (7.0F + index * 1.6F)));
        polygon(consumer, poseStack, 6 + index, radius * 0.9F, 0.02F, 0xFFB13B, 145);
        poseStack.mulPose(Axis.YP.rotationDegrees(37.0F + age * 4.0F));
        star(consumer, poseStack, radius * 0.75F, radius * 0.34F, 5, 0.03F, 0xFFF0B0, 120);
        poseStack.popPose();

        for (int i = 0; i < 12; i++) {
            float angle = (Mth.TWO_PI * i) / 12.0F + age * 0.025F;
            float inner = radius * 0.18F;
            float outer = radius * (0.92F + (i % 3) * 0.05F);
            lineGround(consumer, poseStack, Mth.cos(angle) * inner, Mth.sin(angle) * inner, Mth.cos(angle) * outer, Mth.sin(angle) * outer, 0.024F, 0xFF5A18, 95);
        }
    }

    private void geyser(VertexConsumer consumer, PoseStack poseStack, int ticks, float age, int index) {
        float life = ticks / 28.0F;
        float bloom = Mth.clamp(life, 0.0F, 1.0F);
        for (int i = 0; i < 9; i++) {
            poseStack.pushPose();
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * (14.0F + i) + i * 41.0F + index * 23.0F));
            float height = (1.8F + i * 0.22F) * bloom;
            float width = (0.22F + i * 0.018F) * (0.65F + bloom);
            flameTongue(consumer, poseStack, width, height, Mth.sin(age * 0.44F + i) * 0.22F, 255, 84 + i * 8, 14, Math.round(170 * bloom));
            poseStack.popPose();
        }
    }

    private void wall(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, float thickness, float age, int index) {
        Vec3 delta = b.subtract(a);
        double length = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (length < 0.1D) {
            return;
        }
        Vec3 normal = new Vec3(-delta.z / length, 0.0D, delta.x / length);
        int pieces = Math.max(8, Mth.ceil(length * 2.3D));
        float half = thickness * 0.46F;

        // Low, wide glow bed. This keeps the wall readable while the billboards make it feel like fire.
        for (int i = 0; i < pieces; i++) {
            float t0 = i / (float) pieces;
            float t1 = (i + 1) / (float) pieces;
            Vec3 p0 = a.lerp(b, t0);
            Vec3 p1 = a.lerp(b, t1);
            float wobble0 = Mth.sin(age * 0.18F + i * 0.8F + index) * 0.12F;
            float wobble1 = Mth.sin(age * 0.18F + (i + 1) * 0.8F + index) * 0.12F;
            Vec3 l0 = p0.add(normal.scale(-half + wobble0));
            Vec3 r0 = p0.add(normal.scale(half + wobble0));
            Vec3 l1 = p1.add(normal.scale(-half + wobble1));
            Vec3 r1 = p1.add(normal.scale(half + wobble1));
            groundPatch(consumer, poseStack, l0, r0, r1, l1, 0xFF2A0A, 72);
            lineGround(consumer, poseStack, (float) p0.x, (float) p0.z, (float) p1.x, (float) p1.z, 0.12F + thickness * 0.025F, 0xFFB23A, 115);
        }

        for (int i = 0; i < pieces; i++) {
            float t = (i + 0.5F) / pieces;
            Vec3 base = a.lerp(b, t);
            float seed = index * 9.17F + i * 1.618F;
            float side = Mth.sin(seed * 2.13F) * half * 0.58F;
            float rise = 0.58F + thickness * 0.35F + positiveNoise(age * 0.23F + seed) * 1.18F;
            float width = 0.20F + thickness * 0.08F + positiveNoise(age * 0.31F + seed * 0.7F) * 0.16F;
            Vec3 tongueBase = base.add(normal.scale(side));

            poseStack.pushPose();
            poseStack.translate(tongueBase.x, tongueBase.y + 0.12D, tongueBase.z);
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.37F + seed) * 8.0F));
            flameTongue(consumer, poseStack, width * 1.15F, rise * 0.92F, Mth.sin(age * 0.42F + seed) * 0.22F, 255, 74, 12, 132);
            poseStack.mulPose(Axis.ZP.rotationDegrees(19.0F + Mth.sin(seed) * 11.0F));
            flameTongue(consumer, poseStack, width * 0.62F, rise * 1.22F, Mth.cos(age * 0.53F + seed) * 0.16F, 255, 164, 42, 170);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-42.0F));
            flameTongue(consumer, poseStack, width * 0.34F, rise * 0.74F, Mth.sin(age * 0.68F + seed) * 0.10F, 255, 232, 126, 135);
            poseStack.popPose();

            if (i % 2 == 0) {
                heatCurtain(consumer, poseStack, base, normal, half, 0.7F + rise * 0.45F, age, seed);
            }
        }
    }

    private static float positiveNoise(float value) {
        return 0.5F + Mth.sin(value) * 0.32F + Mth.sin(value * 2.37F + 1.7F) * 0.18F;
    }

    private static void groundPatch(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, int alpha) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        consumer.addVertex(poseStack.last(), (float) a.x, (float) a.y + 0.045F, (float) a.z).setColor(red, green / 2, blue, Math.max(0, alpha / 2));
        consumer.addVertex(poseStack.last(), (float) b.x, (float) b.y + 0.045F, (float) b.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) c.x, (float) c.y + 0.045F, (float) c.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) d.x, (float) d.y + 0.045F, (float) d.z).setColor(red, green / 2, blue, Math.max(0, alpha / 2));
    }

    private static void heatCurtain(VertexConsumer consumer, PoseStack poseStack, Vec3 base, Vec3 normal, float halfWidth, float height, float age, float seed) {
        float sway = Mth.sin(age * 0.28F + seed) * 0.16F;
        Vec3 left = base.add(normal.scale(-halfWidth * 0.72F + sway));
        Vec3 right = base.add(normal.scale(halfWidth * 0.72F + sway));
        int bottomAlpha = 78;
        int topAlpha = 12;
        consumer.addVertex(poseStack.last(), (float) left.x, (float) left.y + 0.08F, (float) left.z).setColor(255, 54, 8, bottomAlpha);
        consumer.addVertex(poseStack.last(), (float) right.x, (float) right.y + 0.08F, (float) right.z).setColor(255, 132, 28, bottomAlpha);
        consumer.addVertex(poseStack.last(), (float) right.x, (float) right.y + height, (float) right.z).setColor(255, 214, 92, topAlpha);
        consumer.addVertex(poseStack.last(), (float) left.x, (float) left.y + height * 0.86F, (float) left.z).setColor(255, 78, 10, topAlpha);
    }

    private static void triangleVeins(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, Vec3 c, float baseY, float age, float progress) {
        float alpha = Mth.clamp(0.35F + progress * 0.9F, 0.0F, 1.0F);
        Vec3 center = new Vec3((a.x + b.x + c.x) / 3.0D, baseY, (a.z + b.z + c.z) / 3.0D);
        for (int i = 0; i < 18; i++) {
            float edge = (i % 6) / 6.0F;
            Vec3 start = switch (i % 3) {
                case 0 -> a.lerp(b, edge);
                case 1 -> b.lerp(c, edge);
                default -> c.lerp(a, edge);
            };
            start = new Vec3(start.x, baseY, start.z);
            Vec3 end = center.lerp(start, 0.18D + 0.18D * Mth.sin(age * 0.07F + i));
            float thickness = 0.018F + (i % 3) * 0.006F;
            lineGroundAtY(consumer, poseStack, (float) start.x, baseY + 0.08F, (float) start.z, (float) end.x, (float) end.z, thickness, i % 2 == 0 ? 0xFF641F : 0xFFD15A, Math.round(90 * alpha));
        }

        poseStack.pushPose();
        poseStack.translate(center.x, center.y + 0.09D, center.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 3.7F));
        ring(consumer, poseStack, (float) Math.max(1.3D, center.distanceTo(a) * 0.22D), 0.045F, 0.0F, 0xFFE08A, Math.round(115 * alpha));
        polygon(consumer, poseStack, 3, (float) Math.max(1.8D, center.distanceTo(a) * 0.42D), 0.015F, 0xFF4314, Math.round(90 * alpha));
        poseStack.popPose();
    }

    private static void verticalQuad(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, float heightA, float heightB, int color, int alpha) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        consumer.addVertex(poseStack.last(), (float) a.x, (float) a.y + 0.05F, (float) a.z).setColor(red, Math.max(16, green / 2), blue, Math.max(0, alpha / 3));
        consumer.addVertex(poseStack.last(), (float) b.x, (float) b.y + 0.05F, (float) b.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) b.x, (float) b.y + heightB, (float) b.z).setColor(255, Math.min(240, green + 78), Math.min(120, blue + 46), Math.max(0, alpha - 20));
        consumer.addVertex(poseStack.last(), (float) a.x, (float) a.y + heightA, (float) a.z).setColor(red, Math.max(18, green / 2), blue, Math.max(0, alpha / 2));
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float y, int color, int alpha) {
        float inner = Math.max(0.01F, radius - thickness);
        float outer = radius + thickness;
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a = Mth.TWO_PI * i / RING_SEGMENTS;
            float b = Mth.TWO_PI * (i + 1) / RING_SEGMENTS;
            consumer.addVertex(poseStack.last(), Mth.cos(a) * inner, y, Mth.sin(a) * inner).setColor(red, green, blue, alpha);
            consumer.addVertex(poseStack.last(), Mth.cos(a) * outer, y, Mth.sin(a) * outer).setColor(red, green, blue, alpha);
            consumer.addVertex(poseStack.last(), Mth.cos(b) * outer, y, Mth.sin(b) * outer).setColor(red, green, blue, alpha);
            consumer.addVertex(poseStack.last(), Mth.cos(b) * inner, y, Mth.sin(b) * inner).setColor(red, green, blue, alpha);
        }
    }

    private static void polygon(VertexConsumer consumer, PoseStack poseStack, int sides, float radius, float y, int color, int alpha) {
        for (int i = 0; i < sides; i++) {
            float a = Mth.TWO_PI * i / sides;
            float b = Mth.TWO_PI * (i + 1) / sides;
            lineGround(consumer, poseStack, Mth.cos(a) * radius, Mth.sin(a) * radius, Mth.cos(b) * radius, Mth.sin(b) * radius, 0.035F + y, color, alpha);
        }
    }

    private static void star(VertexConsumer consumer, PoseStack poseStack, float outer, float inner, int points, float y, int color, int alpha) {
        int vertices = points * 2;
        for (int i = 0; i < vertices; i++) {
            float radiusA = i % 2 == 0 ? outer : inner;
            float radiusB = (i + 1) % 2 == 0 ? outer : inner;
            float a = Mth.TWO_PI * i / vertices;
            float b = Mth.TWO_PI * (i + 1) / vertices;
            lineGround(consumer, poseStack, Mth.cos(a) * radiusA, Mth.sin(a) * radiusA, Mth.cos(b) * radiusB, Mth.sin(b) * radiusB, 0.03F + y, color, alpha);
        }
    }

    private static void lineGround(VertexConsumer consumer, PoseStack poseStack, float ax, float az, float bx, float bz, float thickness, int color, int alpha) {
        lineGroundAtY(consumer, poseStack, ax, 0.07F, az, bx, bz, thickness, color, alpha);
    }

    private static void lineGroundAtY(VertexConsumer consumer, PoseStack poseStack, float ax, float y, float az, float bx, float bz, float thickness, int color, int alpha) {
        float dx = bx - ax;
        float dz = bz - az;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001F) {
            return;
        }
        float nx = -dz / length * thickness;
        float nz = dx / length * thickness;
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        consumer.addVertex(poseStack.last(), ax - nx, y, az - nz).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), ax + nx, y, az + nz).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), bx + nx, y, bz + nz).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), bx - nx, y, bz - nz).setColor(red, green, blue, alpha);
    }

    private static void flameTongue(VertexConsumer consumer, PoseStack poseStack, float width, float height, float bend, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        consumer.addVertex(poseStack.last(), -width, -height * 0.10F, 0.0F).setColor(red, Math.max(18, green / 2), blue, Math.max(0, alpha / 3));
        consumer.addVertex(poseStack.last(), width, 0.0F, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), bend, height, 0.0F).setColor(255, Math.min(245, green + 85), Math.min(126, blue + 60), Math.max(0, alpha - 18));
        consumer.addVertex(poseStack.last(), -width * 0.72F, height * 0.16F, 0.0F).setColor(red, green / 2, blue, Math.max(0, alpha / 2));
    }

    public static final class State extends EntityRenderState {
        private int pointCount;
        private float wallThickness = 1.0F;
        private float completedProgress;
        private float baseY;
        private Vec3 origin = Vec3.ZERO;
        private final Vec3[] points = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
        private final int[] eruptions = new int[3];
    }
}
