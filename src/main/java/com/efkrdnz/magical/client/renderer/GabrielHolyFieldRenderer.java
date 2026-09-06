package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.GabrielHolyFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GabrielHolyFieldRenderer extends EntityRenderer<GabrielHolyFieldEntity, GabrielHolyFieldRenderer.State> {
    public GabrielHolyFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GabrielHolyFieldEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.currentRadius = entity.currentRadius(entity.tickCount + partialTick);
        state.color = entity.color();
        state.lifeTicks = entity.lifeTicks();
        state.lastStrikeAge = entity.tickCount + partialTick - entity.lastStrikeTick();
        state.lastStrikeX = entity.lastStrikeX();
        state.lastStrikeZ = entity.lastStrikeZ();
    }

    @Override
    public boolean shouldRender(GabrielHolyFieldEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        double extent = Math.max(10.0D, entity.radius() * 3.0D + 5.0D);
        AABB bounds = new AABB(entity.getX() - extent, entity.getY() - 1.0D, entity.getZ() - extent, entity.getX() + extent, entity.getY() + 24.0D, entity.getZ() + extent);
        return entity.shouldRender(cameraX, cameraY, cameraZ) && frustum.isVisible(bounds);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Holy Field is rendered from a pre-entity level stage so it can ignore terrain depth
        // while still being covered by entities rendered later.
        super.render(state, poseStack, buffer, packedLight);
    }

    public static void renderThroughBlocksBeforeEntities(GabrielHolyFieldEntity entity, PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPosition, float partialTick) {
        VertexConsumer consumer = buffer.getBuffer(MagicalRenderTypes.magicCircle());
        float age = entity.tickCount + partialTick;
        float fade = 1.0F - Mth.clamp((age - (entity.lifeTicks() - 28.0F)) / 28.0F, 0.0F, 1.0F);
        poseStack.pushPose();
        poseStack.translate(entity.getX() - cameraPosition.x, entity.getY() - cameraPosition.y, entity.getZ() - cameraPosition.z);
        renderFormation(consumer, poseStack, entity.radius(), age, fade);
        if (age >= GabrielHolyFieldEntity.FINAL_FORM_START_TICKS) {
            float fieldFade = fade;
            if (age < GabrielHolyFieldEntity.FORMATION_TICKS) {
                float local = (age - GabrielHolyFieldEntity.FINAL_FORM_START_TICKS) / (float) GabrielHolyFieldEntity.FINAL_FORM_TICKS;
                fieldFade *= smooth(local) * 0.78F;
            }
            renderActiveField(consumer, poseStack, entity.currentRadius(age), entity.radius(), age, fieldFade);
        }
        if (age >= GabrielHolyFieldEntity.FORMATION_TICKS) {
            renderStrikeFlashes(consumer, poseStack, age - entity.lastStrikeTick(), entity.lastStrikeX(), entity.lastStrikeZ(), fade);
        }
        poseStack.popPose();
    }

    private static void renderFormation(VertexConsumer consumer, PoseStack poseStack, float radius, float age, float fade) {
        if (age < GabrielHolyFieldEntity.SEED_TICKS) {
            float progress = smooth(age / GabrielHolyFieldEntity.SEED_TICKS);
            drawCircleSet(consumer, poseStack, 0.0F, 0.0F, radius * 0.34F * progress, 0xFFF8D6, fade * progress, age, 0.0F);
            return;
        }
        if (age < GabrielHolyFieldEntity.SEED_TICKS + GabrielHolyFieldEntity.CROSS_TICKS) {
            float local = (age - GabrielHolyFieldEntity.SEED_TICKS) / GabrielHolyFieldEntity.CROSS_TICKS;
            float in = smooth(Mth.clamp(local / 0.36F, 0.0F, 1.0F));
            float out = 1.0F - smooth(Mth.clamp((local - 0.68F) / 0.32F, 0.0F, 1.0F));
            drawCircleSet(consumer, poseStack, 0.0F, 0.0F, radius * 0.34F, 0xFFF8D6, fade, age, 0.0F);
            for (int i = 0; i < 4; i++) {
                float angle = Mth.HALF_PI * i;
                drawCircleSet(consumer, poseStack, Mth.cos(angle) * radius * 0.46F, Mth.sin(angle) * radius * 0.46F, radius * 0.24F * in, 0xFFD700, fade * in * out, age, i * 31.0F);
            }
            return;
        }
        if (age < GabrielHolyFieldEntity.SEED_TICKS + GabrielHolyFieldEntity.CROSS_TICKS + GabrielHolyFieldEntity.X_TICKS) {
            float local = (age - GabrielHolyFieldEntity.SEED_TICKS - GabrielHolyFieldEntity.CROSS_TICKS) / GabrielHolyFieldEntity.X_TICKS;
            float in = smooth(Mth.clamp(local / 0.36F, 0.0F, 1.0F));
            float out = 1.0F - smooth(Mth.clamp((local - 0.68F) / 0.32F, 0.0F, 1.0F));
            drawCircleSet(consumer, poseStack, 0.0F, 0.0F, radius * 0.38F, 0xFFFFFF, fade * out, age, 13.0F);
            for (int i = 0; i < 4; i++) {
                float angle = Mth.PI / 4.0F + Mth.HALF_PI * i;
                drawCircleSet(consumer, poseStack, Mth.cos(angle) * radius * 0.5F, Mth.sin(angle) * radius * 0.5F, radius * 0.26F * in, 0xFFF1A8, fade * in * out, age, i * 27.0F);
            }
            return;
        }
        if (age < GabrielHolyFieldEntity.FORMATION_TICKS) {
            float local = (age - GabrielHolyFieldEntity.FINAL_FORM_START_TICKS) / GabrielHolyFieldEntity.FINAL_FORM_TICKS;
            float progress = smooth(local);
            drawCircleSet(consumer, poseStack, 0.0F, 0.0F, radius * progress, 0xFFF1A8, fade * progress, age, 0.0F);
            for (int i = 0; i < 8; i++) {
                float angle = Mth.TWO_PI * i / 8.0F;
                float x = Mth.cos(angle) * radius * 0.5F * (1.0F - progress);
                float z = Mth.sin(angle) * radius * 0.5F * (1.0F - progress);
                verticalBeam(consumer, poseStack, x, z, 0.12F, 0.0F, 5.5F + progress * 10.0F, 255, 248, 214, alpha(110, fade * (1.0F - progress * 0.35F)));
            }
        }
    }

    private static void renderActiveField(VertexConsumer consumer, PoseStack poseStack, float radius, float finalRadius, float age, float fade) {
        float pulse = 0.985F + Mth.sin(age * 0.08F) * 0.018F;
        drawMainHolyCircle(consumer, poseStack, radius * pulse, fade, age);
        drawCircleSet(consumer, poseStack, 0.0F, 0.0F, finalRadius * (0.98F + Mth.sin(age * 0.12F) * 0.012F), 0xFFFFFF, fade * 0.26F, age, 37.0F);
        for (int i = 0; i < 10; i++) {
            float angle = age * 0.018F + Mth.TWO_PI * i / 10.0F;
            float laneRadius = radius * (0.22F + (i % 5) * 0.145F);
            float x = Mth.cos(angle) * laneRadius;
            float z = Mth.sin(angle) * laneRadius;
            ring(consumer, poseStack, x, z, radius * 0.035F, radius * 0.003F, 22, 255, 255, 255, alpha(54, fade));
        }
        if (((int) age / 10) % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                float angle = age * 0.031F + Mth.HALF_PI * i;
                verticalBeam(consumer, poseStack, Mth.cos(angle) * radius * 0.42F, Mth.sin(angle) * radius * 0.42F, 0.05F, 0.0F, 8.0F, 255, 240, 168, alpha(40, fade));
            }
        }
    }

    private static void renderStrikeFlashes(VertexConsumer consumer, PoseStack poseStack, float lastStrikeAge, float lastStrikeX, float lastStrikeZ, float fade) {
        if (lastStrikeAge < 0.0F || lastStrikeAge > 10.0F) {
            return;
        }
        float strikeFade = fade * (1.0F - lastStrikeAge / 10.0F);
        verticalBeam(consumer, poseStack, lastStrikeX, lastStrikeZ, 0.34F, -0.1F, 16.0F, 255, 255, 255, alpha(220, strikeFade));
        verticalBeam(consumer, poseStack, lastStrikeX, lastStrikeZ, 0.82F, -0.1F, 13.0F, 255, 221, 88, alpha(105, strikeFade));
        ring(consumer, poseStack, lastStrikeX, lastStrikeZ, 1.2F + lastStrikeAge * 0.18F, 0.035F, 64, 255, 247, 196, alpha(150, strikeFade));
    }

    private static void drawCircleSet(VertexConsumer consumer, PoseStack poseStack, float x, float z, float radius, int color, float fade, float age, float offset) {
        if (radius <= 0.02F || fade <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, 0.02F, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 0.24F + offset));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius, radius * 0.014F, 128, color, alpha(180, fade));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius * 0.82F, radius * 0.006F, 96, 0xFFFFFF, alpha(96, fade));
        polygonColor(consumer, poseStack, 8, radius * 0.92F, radius * 0.007F, -90.0F + age * 0.6F, color, alpha(126, fade));
        polygonColor(consumer, poseStack, 5, radius * 0.48F, radius * 0.008F, -18.0F - age * 0.8F, 0xFFD700, alpha(148, fade));
        star(consumer, poseStack, 5, radius * 0.25F, radius * 0.58F, radius * 0.0055F, 0xFFFFFF, alpha(118, fade));
        spokesColor(consumer, poseStack, 18, radius * 0.18F, radius * 0.96F, radius * 0.0036F, color, alpha(74, fade));
        glyphTicks(consumer, poseStack, 26, radius * 0.72F, radius * 0.05F, 0xFFF8D6, alpha(105, fade));
        poseStack.popPose();
    }

    private static void drawMainHolyCircle(VertexConsumer consumer, PoseStack poseStack, float radius, float fade, float age) {
        if (radius <= 0.02F || fade <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.026F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 0.12F));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius * 1.04F, radius * 0.018F, 192, 0xFFF1A8, alpha(215, fade));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius * 0.985F, radius * 0.006F, 192, 0xFFFFFF, alpha(128, fade));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius * 0.91F, radius * 0.009F, 160, 0xFFD700, alpha(150, fade));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius * 0.79F, radius * 0.0045F, 144, 0xFFF8D6, alpha(92, fade));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius * 0.64F, radius * 0.006F, 128, 0xF6C857, alpha(116, fade));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius * 0.46F, radius * 0.0045F, 112, 0xFFFFFF, alpha(82, fade));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius * 0.24F, radius * 0.0075F, 96, 0xFFF1A8, alpha(142, fade));
        polygonColor(consumer, poseStack, 12, radius * 0.99F, radius * 0.0075F, -90.0F + age * 0.32F, 0xFFFFFF, alpha(118, fade));
        polygonColor(consumer, poseStack, 9, radius * 0.86F, radius * 0.0065F, -10.0F - age * 0.42F, 0xFFD700, alpha(132, fade));
        polygonColor(consumer, poseStack, 7, radius * 0.69F, radius * 0.006F, -90.0F + age * 0.58F, 0xFFF8D6, alpha(118, fade));
        polygonColor(consumer, poseStack, 5, radius * 0.52F, radius * 0.0075F, -18.0F - age * 0.74F, 0xFFE27A, alpha(150, fade));
        polygonColor(consumer, poseStack, 3, radius * 0.31F, radius * 0.0085F, -90.0F + age * 1.05F, 0xFFFFFF, alpha(156, fade));
        star(consumer, poseStack, 5, radius * 0.34F, radius * 0.74F, radius * 0.0055F, 0xFFFFFF, alpha(100, fade));
        star(consumer, poseStack, 6, radius * 0.22F, radius * 0.57F, radius * 0.0045F, 0xFFD700, alpha(86, fade));
        spokesColor(consumer, poseStack, 36, radius * 0.17F, radius * 1.01F, radius * 0.0033F, 0xFFF8D6, alpha(74, fade));
        spokesColor(consumer, poseStack, 18, radius * 0.43F, radius * 0.82F, radius * 0.0045F, 0xFFD700, alpha(86, fade));
        glyphTicks(consumer, poseStack, 72, radius * 0.965F, radius * 0.033F, 0xFFFFFF, alpha(112, fade));
        glyphTicks(consumer, poseStack, 48, radius * 0.74F, radius * 0.044F, 0xFFF1A8, alpha(92, fade));
        glyphTicks(consumer, poseStack, 30, radius * 0.39F, radius * 0.04F, 0xFFD700, alpha(118, fade));
        for (int i = 0; i < 12; i++) {
            float angle = Mth.TWO_PI * i / 12.0F + age * 0.004F;
            float x = Mth.cos(angle) * radius * 0.88F;
            float z = Mth.sin(angle) * radius * 0.88F;
            drawMiniSeal(consumer, poseStack, x, z, radius * 0.055F, angle, fade, i % 2 == 0 ? 0xFFFFFF : 0xFFD700);
        }
        poseStack.popPose();
    }

    private static void drawMiniSeal(VertexConsumer consumer, PoseStack poseStack, float x, float z, float radius, float angle, float fade, int color) {
        poseStack.pushPose();
        poseStack.translate(x, 0.045F, z);
        poseStack.mulPose(Axis.YP.rotation(-angle));
        ringColor(consumer, poseStack, 0.0F, 0.0F, radius, radius * 0.11F, 22, color, alpha(92, fade));
        polygonColor(consumer, poseStack, 4, radius * 0.72F, radius * 0.08F, 45.0F, 0xFFF8D6, alpha(78, fade));
        line(consumer, poseStack, -radius * 0.52F, 0.055F, 0.0F, radius * 0.52F, 0.055F, 0.0F, radius * 0.08F, red(color), green(color), blue(color), alpha(88, fade));
        line(consumer, poseStack, 0.0F, 0.055F, -radius * 0.52F, 0.0F, 0.055F, radius * 0.52F, radius * 0.08F, red(color), green(color), blue(color), alpha(88, fade));
        poseStack.popPose();
    }

    private static void ringColor(VertexConsumer consumer, PoseStack poseStack, float cx, float cz, float radius, float thickness, int segments, int color, int alpha) {
        ring(consumer, poseStack, cx, cz, radius, thickness, segments, red(color), green(color), blue(color), alpha);
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float cx, float cz, float radius, float thickness, int segments, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        float inner = Math.max(0.01F, radius - thickness);
        float outer = radius + thickness;
        for (int i = 0; i < segments; i++) {
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    cx + Mth.cos(a) * inner, 0.0F, cz + Mth.sin(a) * inner,
                    cx + Mth.cos(a) * outer, 0.0F, cz + Mth.sin(a) * outer,
                    cx + Mth.cos(b) * outer, 0.0F, cz + Mth.sin(b) * outer,
                    cx + Mth.cos(b) * inner, 0.0F, cz + Mth.sin(b) * inner,
                    red, green, blue, alpha);
        }
    }

    private static void polygonColor(VertexConsumer consumer, PoseStack poseStack, int sides, float radius, float thickness, float degreesOffset, int color, int alpha) {
        int safeSides = Math.max(3, sides);
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < safeSides; i++) {
            float a = offset + Mth.TWO_PI * i / safeSides;
            float b = offset + Mth.TWO_PI * (i + 1) / safeSides;
            line(consumer, poseStack, Mth.cos(a) * radius, 0.01F, Mth.sin(a) * radius, Mth.cos(b) * radius, 0.01F, Mth.sin(b) * radius, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void spokesColor(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int color, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            line(consumer, poseStack, Mth.cos(angle) * innerRadius, 0.02F, Mth.sin(angle) * innerRadius, Mth.cos(angle) * outerRadius, 0.02F, Mth.sin(angle) * outerRadius, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void star(VertexConsumer consumer, PoseStack poseStack, int points, float innerRadius, float outerRadius, float thickness, int color, int alpha) {
        for (int i = 0; i < points; i++) {
            int next = (i + 2) % points;
            float a = -Mth.HALF_PI + Mth.TWO_PI * i / points;
            float b = -Mth.HALF_PI + Mth.TWO_PI * next / points;
            line(consumer, poseStack, Mth.cos(a) * outerRadius, 0.03F, Mth.sin(a) * outerRadius, Mth.cos(b) * outerRadius, 0.03F, Mth.sin(b) * outerRadius, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void glyphTicks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float length, int color, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            float tangent = angle + Mth.HALF_PI;
            float x = Mth.cos(angle) * radius;
            float z = Mth.sin(angle) * radius;
            line(consumer, poseStack, x - Mth.cos(tangent) * length, 0.035F, z - Mth.sin(tangent) * length, x + Mth.cos(tangent) * length, 0.035F, z + Mth.sin(tangent) * length, length * 0.2F, red(color), green(color), blue(color), alpha);
        }
    }

    private static void verticalBeam(VertexConsumer consumer, PoseStack poseStack, float x, float z, float halfWidth, float yMin, float yMax, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            poseStack.pushPose();
            poseStack.translate(x, 0.0F, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 60.0F));
            quad(consumer, poseStack, -halfWidth, yMin, 0.0F, halfWidth, yMin, 0.0F, halfWidth, yMax, 0.0F, -halfWidth, yMax, 0.0F, red, green, blue, alpha);
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
        private float radius;
        private float currentRadius;
        private int color;
        private int lifeTicks;
        private float lastStrikeAge;
        private float lastStrikeX;
        private float lastStrikeZ;
    }
}
