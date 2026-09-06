package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SovereignAegisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class SovereignAegisRenderer extends EntityRenderer<SovereignAegisEntity, SovereignAegisRenderer.State> {
    public SovereignAegisRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SovereignAegisEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.mode = entity.mode();
        state.radius = entity.radius();
        state.fade = entity.fade(partialTick);
        state.hitFlash = entity.hitFlash();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float age = state.ageInTicks;
        float fade = state.fade;
        poseStack.pushPose();
        if (state.mode == SovereignAegisEntity.MODE_ULTIMATE_PROTECTION) {
            ultimate(consumer, poseStack, state.radius, age, state.hitFlash);
        } else if (state.mode == SovereignAegisEntity.MODE_SANCTUARY) {
            sanctuary(consumer, poseStack, state.radius, age, fade);
        } else {
            seal(consumer, poseStack, state.radius, age, fade);
        }
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void ultimate(VertexConsumer consumer, PoseStack poseStack, float radius, float age, int hitFlash) {
        if (hitFlash <= 0) {
            return;
        }
        float flash = hitFlash / 12.0F;
        poseStack.translate(0.0D, -0.72D, 0.0D);
        ring(consumer, poseStack, radius * (1.3F + (1.0F - flash) * 0.45F), 0.055F, 1.16F, 255, 255, 220, Math.round(210 * flash));
        ring(consumer, poseStack, radius * (0.82F + (1.0F - flash) * 0.18F), 0.035F, 1.16F, 130, 230, 255, Math.round(150 * flash));
        for (int i = 0; i < 24; i++) {
            float angle = Mth.TWO_PI * i / 24.0F + age * 0.11F;
            float inner = radius * (0.42F + (i % 4) * 0.06F);
            float outer = radius * (1.05F + (i % 5) * 0.08F + (1.0F - flash) * 0.35F);
            float y = 0.78F + (i % 6) * 0.12F;
            int red = i % 3 == 0 ? 145 : 255;
            int green = i % 3 == 0 ? 230 : 250;
            int blue = i % 3 == 0 ? 255 : 210;
            line(consumer, poseStack, Mth.cos(angle) * inner, y, Mth.sin(angle) * inner,
                    Mth.cos(angle + 0.1F) * outer, y + 0.1F, Mth.sin(angle + 0.1F) * outer,
                    0.018F + flash * 0.016F, red, green, blue, Math.round(190 * flash));
        }
    }

    private static void sanctuary(VertexConsumer consumer, PoseStack poseStack, float radius, float age, float fade) {
        float alpha = Mth.clamp(fade * 0.9F + 0.1F, 0.0F, 1.0F);
        float centerY = radius * 0.62F;
        sphereVeil(consumer, poseStack, radius, centerY, age, alpha, 80, 205, 255);
        sphereShell(consumer, poseStack, radius, centerY, age, alpha, 115, 230, 255);
        complexMagicCircle(consumer, poseStack, radius * 1.08F, age, alpha, 105, 230, 255, 255, 236, 150);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 1.5F));
        polygon(consumer, poseStack, 9, radius * 0.76F, 0.035F, 0.095F, 255, 248, 190, Math.round(130 * alpha));
        star(consumer, poseStack, 6, radius * 0.58F, 0.032F, 0.12F, 160, 245, 255, Math.round(120 * alpha));
        poseStack.popPose();
        for (int i = 0; i < 18; i++) {
            float angle = Mth.TWO_PI * i / 18.0F + age * 0.018F;
            float wave = 0.55F + 0.25F * Mth.sin(age * 0.08F + i);
            line(consumer, poseStack, Mth.cos(angle) * radius * 0.92F, 0.16F, Mth.sin(angle) * radius * 0.92F,
                    Mth.cos(angle + 0.18F) * radius * wave, centerY + Mth.sin(i * 1.7F) * radius * 0.28F,
                    Mth.sin(angle + 0.18F) * radius * wave, 0.014F, 190, 250, 255, Math.round(58 * alpha));
        }
    }

    private static void seal(VertexConsumer consumer, PoseStack poseStack, float radius, float age, float fade) {
        float alpha = Mth.clamp(fade * 0.85F + 0.15F, 0.0F, 1.0F);
        poseStack.translate(0.0D, 0.02D, 0.0D);
        complexMagicCircle(consumer, poseStack, radius * 1.42F, age, alpha, 255, 214, 84, 255, 255, 225);
        ring(consumer, poseStack, radius * 1.25F, 0.08F, 0.05F, 255, 220, 95, Math.round(230 * alpha));
        ring(consumer, poseStack, radius * 0.82F, 0.05F, 0.08F, 255, 255, 210, Math.round(175 * alpha));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2.2F));
        polygon(consumer, poseStack, 7, radius * 1.04F, 0.045F, 0.11F, 255, 235, 130, Math.round(190 * alpha));
        star(consumer, poseStack, 5, radius * 0.78F, 0.04F, 0.14F, 255, 255, 235, Math.round(155 * alpha));
        polygon(consumer, poseStack, 3, radius * 0.48F, 0.04F, 0.17F, 255, 255, 235, Math.round(140 * alpha));
        poseStack.popPose();
        sphereVeil(consumer, poseStack, radius * 1.12F, radius * 0.82F, age * 0.35F, alpha * 0.68F, 255, 205, 75);
        sphereShell(consumer, poseStack, radius * 1.12F, radius * 0.82F, age * 0.5F, alpha * 0.78F, 255, 220, 95);
        for (int i = 0; i < 16; i++) {
            float angle = Mth.TWO_PI * i / 16.0F + age * 0.008F;
            float base = radius * (0.92F + (i % 2) * 0.16F);
            line(consumer, poseStack, Mth.cos(angle) * base, 0.08F, Mth.sin(angle) * base,
                    Mth.cos(angle + 0.06F) * radius * 0.58F, radius * 1.64F, Mth.sin(angle + 0.06F) * radius * 0.58F,
                    0.02F, 255, 244, 170, Math.round(130 * alpha));
        }
        for (int i = 0; i < 9; i++) {
            float y = 0.32F + i * radius * 0.18F;
            float localRadius = radius * (1.08F - i * 0.045F);
            ring(consumer, poseStack, localRadius, 0.018F, y, 255, 230, 130, Math.round((82 - i * 4) * alpha));
        }
    }

    private static void haloBands(VertexConsumer consumer, PoseStack poseStack, float radius, float age, int red, int green, int blue, int alpha) {
        for (int i = 0; i < 8; i++) {
            float angle = Mth.TWO_PI * i / 8.0F + age * 0.02F;
            line(consumer, poseStack, Mth.cos(angle) * radius, 0.28F, Mth.sin(angle) * radius, Mth.cos(angle + 0.42F) * radius, 1.9F, Mth.sin(angle + 0.42F) * radius, 0.024F, red, green, blue, alpha);
        }
    }

    private static void dome(VertexConsumer consumer, PoseStack poseStack, float radius, int rings, int segments, int red, int green, int blue, int alpha) {
        for (int ring = 1; ring <= rings; ring++) {
            float vertical = ring / (float) rings;
            float y = Mth.sin(vertical * Mth.HALF_PI) * radius;
            float r = Mth.cos(vertical * Mth.HALF_PI) * radius;
            ring(consumer, poseStack, r, 0.025F, y, red, green, blue, Math.max(8, alpha - ring * 2));
        }
        for (int i = 0; i < segments; i += 4) {
            float angle = Mth.TWO_PI * i / segments;
            line(consumer, poseStack, Mth.cos(angle) * radius, 0.02F, Mth.sin(angle) * radius, 0.0F, radius, 0.0F, 0.018F, red, green, blue, alpha);
        }
    }

    private static void sphereShell(VertexConsumer consumer, PoseStack poseStack, float radius, float centerY, float age, float alpha, int red, int green, int blue) {
        for (int band = -7; band <= 7; band++) {
            float latitude = band / 7.0F * 1.28F;
            float y = centerY + Mth.sin(latitude) * radius;
            float r = Math.max(0.08F, Mth.cos(latitude) * radius);
            ring(consumer, poseStack, r, 0.018F, y, red, green, blue, Math.round((band == 0 ? 74 : 42) * alpha));
        }
        for (int meridian = 0; meridian < 12; meridian++) {
            poseStack.pushPose();
            poseStack.translate(0.0D, centerY, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(meridian * 15.0F + age * 0.35F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            ring(consumer, poseStack, radius, 0.014F, 0.0F, red, green, blue, Math.round(35 * alpha));
            poseStack.popPose();
        }
        for (int i = 0; i < 24; i++) {
            float angle = Mth.TWO_PI * i / 24.0F + age * 0.01F;
            float y1 = centerY + Mth.sin(age * 0.045F + i) * radius * 0.55F;
            float y2 = centerY + Mth.sin(age * 0.045F + i + 0.65F) * radius * 0.55F;
            float r1 = radius * (0.74F + 0.2F * Mth.sin(i * 1.3F));
            float r2 = radius * (0.74F + 0.2F * Mth.cos(i * 1.1F));
            line(consumer, poseStack, Mth.cos(angle) * r1, y1, Mth.sin(angle) * r1,
                    Mth.cos(angle + 0.24F) * r2, y2, Mth.sin(angle + 0.24F) * r2,
                    0.012F, red, green, blue, Math.round(36 * alpha));
        }
    }

    private static void sphereVeil(VertexConsumer consumer, PoseStack poseStack, float radius, float centerY, float age, float alpha, int red, int green, int blue) {
        int latitudes = 8;
        int segments = 48;
        for (int lat = -latitudes; lat < latitudes; lat++) {
            float t1 = lat / (float) latitudes * Mth.HALF_PI;
            float t2 = (lat + 1) / (float) latitudes * Mth.HALF_PI;
            float y1 = centerY + Mth.sin(t1) * radius;
            float y2 = centerY + Mth.sin(t2) * radius;
            float r1 = Mth.cos(t1) * radius;
            float r2 = Mth.cos(t2) * radius;
            int bandAlpha = Math.round((lat % 2 == 0 ? 13 : 8) * alpha);
            for (int seg = 0; seg < segments; seg += 2) {
                float a = Mth.TWO_PI * seg / segments + age * 0.002F;
                float b = Mth.TWO_PI * (seg + 1) / segments + age * 0.002F;
                quad(consumer, poseStack,
                        Mth.cos(a) * r1, y1, Mth.sin(a) * r1,
                        Mth.cos(a) * r2, y2, Mth.sin(a) * r2,
                        Mth.cos(b) * r2, y2, Mth.sin(b) * r2,
                        Mth.cos(b) * r1, y1, Mth.sin(b) * r1,
                        red, green, blue, bandAlpha);
            }
        }
    }

    private static void complexMagicCircle(VertexConsumer consumer, PoseStack poseStack, float radius, float age, float alpha, int redA, int greenA, int blueA, int redB, int greenB, int blueB) {
        ring(consumer, poseStack, radius, 0.055F, 0.035F, redA, greenA, blueA, Math.round(190 * alpha));
        ring(consumer, poseStack, radius * 0.92F, 0.018F, 0.055F, redB, greenB, blueB, Math.round(125 * alpha));
        ring(consumer, poseStack, radius * 0.72F, 0.025F, 0.075F, redA, greenA, blueA, Math.round(115 * alpha));
        ring(consumer, poseStack, radius * 0.48F, 0.018F, 0.095F, redB, greenB, blueB, Math.round(105 * alpha));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 0.7F));
        polygon(consumer, poseStack, 7, radius * 0.96F, 0.025F, 0.08F, redB, greenB, blueB, Math.round(115 * alpha));
        star(consumer, poseStack, 5, radius * 0.56F, 0.026F, 0.12F, redB, greenB, blueB, Math.round(120 * alpha));
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 1.1F));
        polygon(consumer, poseStack, 5, radius * 0.36F, 0.023F, 0.13F, redA, greenA, blueA, Math.round(145 * alpha));
        poseStack.popPose();
        for (int i = 0; i < 28; i++) {
            float angle = Mth.TWO_PI * i / 28.0F;
            float inner = i % 2 == 0 ? radius * 0.78F : radius * 0.84F;
            float outer = radius * 0.9F;
            line(consumer, poseStack, Mth.cos(angle) * inner, 0.11F, Mth.sin(angle) * inner,
                    Mth.cos(angle) * outer, 0.11F, Mth.sin(angle) * outer,
                    0.013F, redB, greenB, blueB, Math.round(95 * alpha));
        }
        for (int i = 0; i < 12; i++) {
            float angle = Mth.TWO_PI * i / 12.0F + age * 0.004F;
            line(consumer, poseStack, Mth.cos(angle) * radius * 0.22F, 0.115F, Mth.sin(angle) * radius * 0.22F,
                    Mth.cos(angle) * radius * 0.66F, 0.115F, Mth.sin(angle) * radius * 0.66F,
                    0.012F, redA, greenA, blueA, Math.round(65 * alpha));
        }
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float y, int red, int green, int blue, int alpha) {
        int segments = 96;
        float inner = Math.max(0.01F, radius - thickness);
        float outer = radius + thickness;
        for (int i = 0; i < segments; i++) {
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack, Mth.cos(a) * inner, y, Mth.sin(a) * inner, Mth.cos(a) * outer, y, Mth.sin(a) * outer, Mth.cos(b) * outer, y, Mth.sin(b) * outer, Mth.cos(b) * inner, y, Mth.sin(b) * inner, red, green, blue, alpha);
        }
    }

    private static void polygon(VertexConsumer consumer, PoseStack poseStack, int sides, float radius, float thickness, float y, int red, int green, int blue, int alpha) {
        for (int i = 0; i < sides; i++) {
            float a = Mth.TWO_PI * i / sides;
            float b = Mth.TWO_PI * (i + 1) / sides;
            line(consumer, poseStack, Mth.cos(a) * radius, y, Mth.sin(a) * radius, Mth.cos(b) * radius, y, Mth.sin(b) * radius, thickness, red, green, blue, alpha);
        }
    }

    private static void star(VertexConsumer consumer, PoseStack poseStack, int points, float radius, float thickness, float y, int red, int green, int blue, int alpha) {
        int skip = points == 5 ? 2 : Math.max(2, points / 2 - 1);
        for (int i = 0; i < points; i++) {
            float a = Mth.TWO_PI * i / points;
            float b = Mth.TWO_PI * ((i + skip) % points) / points;
            line(consumer, poseStack, Mth.cos(a) * radius, y, Mth.sin(a) * radius, Mth.cos(b) * radius, y, Mth.sin(b) * radius, thickness, red, green, blue, alpha);
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, int red, int green, int blue, int alpha) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        float nx = length < 0.001F ? thickness : -dz / length * thickness;
        float nz = length < 0.001F ? 0.0F : dx / length * thickness;
        quad(consumer, poseStack, x1 - nx, y1, z1 - nz, x1 + nx, y1, z1 + nz, x2 + nx, y2, z2 + nz, x2 - nx, y2, z2 - nz, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha);
    }

    public static final class State extends EntityRenderState {
        private int mode;
        private float radius;
        private float fade;
        private int hitFlash;
    }
}
