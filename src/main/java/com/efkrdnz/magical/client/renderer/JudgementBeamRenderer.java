package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.JudgementBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class JudgementBeamRenderer extends EntityRenderer<JudgementBeamEntity, JudgementBeamRenderer.State> {
    public JudgementBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(JudgementBeamEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.color = entity.color();
        state.targetHeight = entity.targetHeight();
        state.chargeTicks = entity.chargeTicks();
        state.lifeTicks = entity.lifeTicks();
        state.impacted = entity.impacted();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float chargeProgress = Mth.clamp(state.ageInTicks / Math.max(1.0F, state.chargeTicks), 0.0F, 1.0F);
        float impactProgress = Mth.clamp((state.ageInTicks - state.chargeTicks) / Math.max(1.0F, state.lifeTicks - state.chargeTicks), 0.0F, 1.0F);
        float chargeFade = state.impacted ? 1.0F - impactProgress : 0.22F + chargeProgress * 0.78F;
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());

        drawChargeCircles(consumer, poseStack, state.targetHeight, chargeProgress, chargeFade, state.ageInTicks);
        if (state.impacted || state.ageInTicks >= state.chargeTicks) {
            drawBeam(consumer, poseStack, state.targetHeight, 1.0F - impactProgress, state.ageInTicks);
        }

        super.render(state, poseStack, buffer, packedLight);
    }

    private static void drawChargeCircles(VertexConsumer consumer, PoseStack poseStack, float targetHeight, float chargeProgress, float fade, float age) {
        float[] radiusPresets = {12.8F, 7.2F, 15.6F, 9.0F, 13.9F, 6.5F, 10.6F};
        int[] primaryPalette = {0xFFF6B0, 0xFFE16A, 0xFFFDF2, 0xFFD75A, 0xFFF0A0, 0xF9FAFF, 0xFFE89C};
        int[] accentPalette = {0xFFFFFF, 0xDDFBFF, 0xFFF8D8, 0xFFE7A8, 0xF3ECFF, 0xFFFDF5, 0xD7F3FF};
        int[] shadowPalette = {0xD08B22, 0xA96E18, 0xF4C14C, 0xBA7D26, 0xE0A840, 0xB9842C, 0xD6B35B};
        int[] emberPalette = {0xFFB14A, 0xFFCB63, 0xF5A642, 0xFFE08C, 0xF7BE57, 0xFFDA78, 0xFFBE58};
        float topY = targetHeight + 30.0F;
        float bottomY = targetHeight + 1.0F;
        for (int i = 0; i < radiusPresets.length; i++) {
            float layer = i / (float) (radiusPresets.length - 1);
            float y = Mth.lerp(layer, topY, bottomY) + Mth.sin(age * 0.08F + i) * 0.16F;
            float radius = radiusPresets[i] * (0.68F + chargeProgress * 0.38F);
            int primary = primaryPalette[i % primaryPalette.length];
            int accent = accentPalette[i % accentPalette.length];
            int shadow = shadowPalette[i % shadowPalette.length];
            int ember = emberPalette[i % emberPalette.length];
            float localFade = fade * (1.0F - layer * 0.08F);
            float spin = age * (1.15F + i * 0.18F);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            ringColor(consumer, poseStack, radius * 1.025F, radius * 0.03F, y, 176, 0xFFFDF6, alpha(118, localFade));
            ringColor(consumer, poseStack, radius, radius * 0.018F, y + 0.012F, 176, primary, alpha(190, localFade));
            ringColor(consumer, poseStack, radius * 0.91F, radius * 0.008F, y + 0.026F, 160, accent, alpha(150, localFade));
            ringColor(consumer, poseStack, radius * 0.79F, radius * 0.006F, y + 0.04F, 128, ember, alpha(112, localFade));
            ringColor(consumer, poseStack, radius * 0.66F, radius * 0.011F, y + 0.054F, 136, shadow, alpha(128, localFade));
            ringColor(consumer, poseStack, radius * 0.47F, radius * 0.006F, y + 0.07F, 112, 0xFFFFFF, alpha(108, localFade));
            ringColor(consumer, poseStack, radius * 0.29F, radius * 0.005F, y + 0.085F, 96, accent, alpha(84, localFade));
            polygonColor(consumer, poseStack, 7 + i % 3, radius * 0.86F, radius * 0.01F, y + 0.1F, -90.0F + age * (i + 0.65F), primary, alpha(150, localFade));
            polygonColor(consumer, poseStack, 4 + i % 5, radius * 0.58F, radius * 0.008F, y + 0.115F, 45.0F - age * (i + 0.52F), accent, alpha(132, localFade));
            star(consumer, poseStack, 5 + i % 3, radius * 0.2F, radius * 0.64F, radius * 0.0068F, y + 0.13F, 0xFFFDF4, alpha(145, localFade));
            star(consumer, poseStack, 8 + i % 2, radius * 0.34F, radius * 0.76F, radius * 0.0045F, y + 0.145F, shadow, alpha(92, localFade));
            spokesColor(consumer, poseStack, 14 + i * 3, radius * 0.18F, radius * 0.96F, radius * 0.0038F, y + 0.16F, primary, alpha(76, localFade));
            haloTicks(consumer, poseStack, 28 + i * 4, radius * 0.98F, radius * 0.07F, y + 0.175F, accent, alpha(118, localFade));
            glyphTicks(consumer, poseStack, 24 + i * 4, radius * 0.82F, radius * 0.052F, y + 0.19F, 0xFFFFFF, alpha(115, localFade));
            diamondMarks(consumer, poseStack, 8 + i % 5, radius * 0.72F, radius * 0.048F, y + 0.205F, i * 11.0F - age * 0.55F, primary, alpha(130, localFade));
            braidedArcs(consumer, poseStack, 12 + i * 2, radius * 0.55F, radius * 0.055F, y + 0.22F, i * 7.5F + age * 0.42F, ember, alpha(98, localFade));
            orbitSigils(consumer, poseStack, 5 + i % 4, 3 + i % 4, radius * 0.38F, radius * 0.075F, y + 0.235F, -age * 0.8F, accent, shadow, alpha(124, localFade));
            poseStack.popPose();
        }
    }

    private static void drawBeam(VertexConsumer consumer, PoseStack poseStack, float targetHeight, float fade, float age) {
        if (fade <= 0.0F) {
            return;
        }
        float yMin = -1.0F;
        float yMax = targetHeight + 30.0F;
        float pulse = 0.94F + Mth.sin(age * 0.55F) * 0.06F;
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 45.0F + age * 1.8F));
            verticalPlane(consumer, poseStack, 0.72F * pulse, yMin, yMax, 255, 244, 178, alpha(165, fade));
            poseStack.popPose();
        }
        for (int i = 0; i < 6; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 30.0F - age * 2.7F));
            verticalPlane(consumer, poseStack, 1.55F + i * 0.08F, yMin + i * 0.08F, yMax - i * 0.18F, 255, 44, 24, alpha(82, fade * (1.0F - i * 0.08F)));
            poseStack.popPose();
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 5.0F));
        verticalPlane(consumer, poseStack, 0.22F, yMin - 0.5F, yMax + 2.0F, 255, 255, 255, alpha(245, fade));
        poseStack.popPose();
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float y, int segments, int red, int green, int blue, int alpha) {
        float inner = Math.max(0.01F, radius - thickness);
        float outer = radius + thickness;
        for (int i = 0; i < segments; i++) {
            float angleA = Mth.TWO_PI * i / segments;
            float angleB = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    Mth.cos(angleA) * inner, y, Mth.sin(angleA) * inner,
                    Mth.cos(angleA) * outer, y, Mth.sin(angleA) * outer,
                    Mth.cos(angleB) * outer, y, Mth.sin(angleB) * outer,
                    Mth.cos(angleB) * inner, y, Mth.sin(angleB) * inner,
                    red, green, blue, alpha);
        }
    }

    private static void ringColor(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float y, int segments, int color, int alpha) {
        ring(consumer, poseStack, radius, thickness, y, segments, red(color), green(color), blue(color), alpha);
    }

    private static void polygon(VertexConsumer consumer, PoseStack poseStack, int sides, float radius, float thickness, float y, float degreesOffset, int red, int green, int blue, int alpha) {
        int safeSides = Math.max(3, sides);
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < safeSides; i++) {
            float angleA = offset + Mth.TWO_PI * i / safeSides;
            float angleB = offset + Mth.TWO_PI * (i + 1) / safeSides;
            line(consumer, poseStack, Mth.cos(angleA) * radius, y, Mth.sin(angleA) * radius, Mth.cos(angleB) * radius, y, Mth.sin(angleB) * radius, thickness, red, green, blue, alpha);
        }
    }

    private static void polygonColor(VertexConsumer consumer, PoseStack poseStack, int sides, float radius, float thickness, float y, float degreesOffset, int color, int alpha) {
        polygon(consumer, poseStack, sides, radius, thickness, y, degreesOffset, red(color), green(color), blue(color), alpha);
    }

    private static void spokes(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, float y, int red, int green, int blue, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            line(consumer, poseStack, Mth.cos(angle) * innerRadius, y, Mth.sin(angle) * innerRadius, Mth.cos(angle) * outerRadius, y, Mth.sin(angle) * outerRadius, thickness, red, green, blue, alpha);
        }
    }

    private static void spokesColor(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, float y, int color, int alpha) {
        spokes(consumer, poseStack, count, innerRadius, outerRadius, thickness, y, red(color), green(color), blue(color), alpha);
    }

    private static void star(VertexConsumer consumer, PoseStack poseStack, int points, float innerRadius, float outerRadius, float thickness, float y, int color, int alpha) {
        int safePoints = Math.max(3, points);
        for (int i = 0; i < safePoints; i++) {
            float outerA = -Mth.HALF_PI + Mth.TWO_PI * i / safePoints;
            float innerA = outerA + Mth.PI / safePoints;
            float outerB = -Mth.HALF_PI + Mth.TWO_PI * (i + 1) / safePoints;
            line(consumer, poseStack, Mth.cos(outerA) * outerRadius, y, Mth.sin(outerA) * outerRadius, Mth.cos(innerA) * innerRadius, y, Mth.sin(innerA) * innerRadius, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, Mth.cos(innerA) * innerRadius, y, Mth.sin(innerA) * innerRadius, Mth.cos(outerB) * outerRadius, y, Mth.sin(outerB) * outerRadius, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void glyphTicks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float length, float y, int color, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            float tangent = angle + Mth.HALF_PI;
            float centerX = Mth.cos(angle) * radius;
            float centerZ = Mth.sin(angle) * radius;
            float half = length * (0.55F + (i % 4) * 0.12F);
            line(consumer, poseStack,
                    centerX - Mth.cos(tangent) * half,
                    y,
                    centerZ - Mth.sin(tangent) * half,
                    centerX + Mth.cos(tangent) * half,
                    y,
                    centerZ + Mth.sin(tangent) * half,
                    length * 0.18F,
                    red(color), green(color), blue(color), alpha);
        }
    }

    private static void haloTicks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float length, float y, int color, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            float inner = radius - length * (0.35F + (i % 3) * 0.14F);
            float outer = radius + length * (0.5F + (i % 4) * 0.12F);
            line(consumer, poseStack,
                    Mth.cos(angle) * inner,
                    y,
                    Mth.sin(angle) * inner,
                    Mth.cos(angle) * outer,
                    y,
                    Mth.sin(angle) * outer,
                    length * 0.14F,
                    red(color), green(color), blue(color), alpha);
        }
    }

    private static void diamondMarks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float size, float y, float degreesOffset, int color, int alpha) {
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < count; i++) {
            float angle = offset + Mth.TWO_PI * i / count;
            float cx = Mth.cos(angle) * radius;
            float cz = Mth.sin(angle) * radius;
            float radialX = Mth.cos(angle);
            float radialZ = Mth.sin(angle);
            float tangentX = -radialZ;
            float tangentZ = radialX;
            float longHalf = size * (1.15F + (i % 2) * 0.25F);
            float shortHalf = size * 0.54F;
            quad(consumer, poseStack,
                    cx + radialX * longHalf, y, cz + radialZ * longHalf,
                    cx + tangentX * shortHalf, y, cz + tangentZ * shortHalf,
                    cx - radialX * longHalf, y, cz - radialZ * longHalf,
                    cx - tangentX * shortHalf, y, cz - tangentZ * shortHalf,
                    red(color), green(color), blue(color), alpha);
        }
    }

    private static void braidedArcs(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float arcLength, float y, float degreesOffset, int color, int alpha) {
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        float step = Mth.TWO_PI / count;
        for (int i = 0; i < count; i++) {
            float center = offset + step * i;
            float half = step * (0.22F + (i % 2) * 0.08F);
            float radiusA = radius + (i % 2 == 0 ? arcLength : -arcLength);
            float radiusB = radius + (i % 2 == 0 ? -arcLength * 0.35F : arcLength * 0.35F);
            line(consumer, poseStack,
                    Mth.cos(center - half) * radiusA,
                    y,
                    Mth.sin(center - half) * radiusA,
                    Mth.cos(center + half) * radiusB,
                    y,
                    Mth.sin(center + half) * radiusB,
                    arcLength * 0.16F,
                    red(color), green(color), blue(color), alpha);
        }
    }

    private static void orbitSigils(VertexConsumer consumer, PoseStack poseStack, int count, int sides, float orbitRadius, float sigilRadius, float y, float degreesOffset, int primary, int secondary, int alpha) {
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < count; i++) {
            float angle = offset + Mth.TWO_PI * i / count;
            float cx = Mth.cos(angle) * orbitRadius;
            float cz = Mth.sin(angle) * orbitRadius;
            poseStack.pushPose();
            poseStack.translate(cx, 0.0F, cz);
            poseStack.mulPose(Axis.YP.rotationDegrees(degreesOffset + i * 360.0F / count));
            ringColor(consumer, poseStack, sigilRadius, sigilRadius * 0.08F, y, 32, primary, alpha);
            polygonColor(consumer, poseStack, sides, sigilRadius * 0.74F, sigilRadius * 0.05F, y + 0.012F, -90.0F, secondary, alpha(alpha, 0.72F));
            star(consumer, poseStack, Math.max(3, sides), sigilRadius * 0.24F, sigilRadius * 0.62F, sigilRadius * 0.035F, y + 0.024F, 0xFFFFFF, alpha(alpha, 0.58F));
            poseStack.popPose();
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, int red, int green, int blue, int alpha) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (alpha <= 0 || length <= 0.0001F) {
            return;
        }
        float half = thickness * 0.5F;
        float px = -dz / length * half;
        float pz = dx / length * half;
        quad(consumer, poseStack, x1 - px, y1, z1 - pz, x1 + px, y1, z1 + pz, x2 + px, y2, z2 + pz, x2 - px, y2, z2 - pz, red, green, blue, alpha);
    }

    private static void verticalPlane(VertexConsumer consumer, PoseStack poseStack, float halfWidth, float yMin, float yMax, int red, int green, int blue, int alpha) {
        quad(consumer, poseStack, -halfWidth, yMin, 0.0F, halfWidth, yMin, 0.0F, halfWidth, yMax, 0.0F, -halfWidth, yMax, 0.0F, red, green, blue, alpha);
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
        private int color = 0xFFDC38;
        private float targetHeight = 1.8F;
        private int chargeTicks = 80;
        private int lifeTicks = 114;
        private boolean impacted;
    }
}
