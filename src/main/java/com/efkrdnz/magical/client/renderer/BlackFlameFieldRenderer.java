package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.BlackFlameFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class BlackFlameFieldRenderer extends EntityRenderer<BlackFlameFieldEntity, BlackFlameFieldRenderer.State> {
    public BlackFlameFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BlackFlameFieldEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.life = entity.life();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float progress = Mth.clamp(state.ageInTicks / Math.max(1.0F, state.life), 0.0F, 1.0F);
        float in = Mth.clamp(progress / 0.15F, 0.0F, 1.0F);
        float out = 1.0F - Mth.clamp((progress - 0.78F) / 0.22F, 0.0F, 1.0F);
        float fade = in * out;
        float pulse = 0.98F + Mth.sin(state.ageInTicks * 0.18F) * 0.035F;
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.05D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 1.2F));
        ritualCircle(consumer, poseStack, state.radius * pulse, fade, state.ageInTicks);
        poseStack.popPose();

        for (int i = 0; i < 26; i++) {
            float angle = i * (360.0F / 26.0F) + Mth.sin(state.ageInTicks * 0.06F + i) * 10.0F;
            float radians = angle * Mth.DEG_TO_RAD;
            float lane = 0.28F + (i % 5) * 0.13F;
            float distance = state.radius * lane;
            float height = state.radius * (0.42F + (i % 4) * 0.11F) * (0.72F + Mth.sin(state.ageInTicks * 0.19F + i * 1.7F) * 0.18F);
            poseStack.pushPose();
            poseStack.translate(Mth.cos(radians) * distance, 0.55F + height * 0.32F, Mth.sin(radians) * distance);
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle + state.ageInTicks * (4.0F + (i % 3))));
            flame(consumer, poseStack, state.radius * (0.035F + (i % 3) * 0.012F), height, Mth.sin(state.ageInTicks * 0.24F + i) * 0.18F, colorR(i), colorG(i), colorB(i), alpha(125 + (i % 4) * 18, fade));
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.85D + Mth.sin(state.ageInTicks * 0.16F) * 0.08F, 0.0D);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        for (int i = 0; i < 9; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(i * 40.0F - state.ageInTicks * 5.5F));
            flame(consumer, poseStack, state.radius * 0.052F, state.radius * (0.8F + i * 0.025F), 0.0F, 16, 2, 22, alpha(92, fade));
            poseStack.popPose();
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * 14.0F));
        diamond(consumer, poseStack, state.radius * 0.72F, 62, 4, 82, alpha(74, fade));
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
        diamond(consumer, poseStack, state.radius * 0.48F, 184, 30, 12, alpha(58, fade));
        poseStack.popPose();

        super.render(state, poseStack, buffer, packedLight);
    }

    private static void ritualCircle(VertexConsumer consumer, PoseStack poseStack, float radius, float fade, float age) {
        ring(consumer, poseStack, radius * 1.04F, radius * 0.026F, 112, 24, 3, 28, alpha(215, fade));
        ring(consumer, poseStack, radius * 0.93F, radius * 0.012F, 96, 96, 8, 38, alpha(152, fade));
        ring(consumer, poseStack, radius * 0.72F, radius * 0.01F, 96, 188, 28, 12, alpha(118, fade));
        ring(consumer, poseStack, radius * 0.48F, radius * 0.008F, 72, 48, 4, 80, alpha(128, fade));
        polygon(consumer, poseStack, 9, radius * 0.98F, radius * 0.012F, -90.0F + age * 0.18F, 62, 5, 76, alpha(190, fade));
        polygon(consumer, poseStack, 6, radius * 0.64F, radius * 0.01F, -30.0F - age * 0.28F, 150, 18, 18, alpha(142, fade));
        pentagram(consumer, poseStack, radius * 0.78F, radius * 0.01F, -90.0F, 30, 2, 36, alpha(178, fade));
        spokes(consumer, poseStack, 18, radius * 0.25F, radius * 1.0F, radius * 0.0055F, 80, 6, 82, alpha(92, fade));
        zigzagRing(consumer, poseStack, 72, radius * 0.84F, radius * 0.035F, radius * 0.0045F, 178, 24, 12, alpha(105, fade));
        glyphMarks(consumer, poseStack, 36, radius * 0.56F, radius * 0.045F, radius * 0.005F, 32, 2, 54, alpha(132, fade));
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, int segments, int red, int green, int blue, int alpha) {
        for (int i = 0; i < segments; i++) {
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    Mth.cos(a) * (radius - thickness), 0.0F, Mth.sin(a) * (radius - thickness),
                    Mth.cos(a) * (radius + thickness), 0.0F, Mth.sin(a) * (radius + thickness),
                    Mth.cos(b) * (radius + thickness), 0.0F, Mth.sin(b) * (radius + thickness),
                    Mth.cos(b) * (radius - thickness), 0.0F, Mth.sin(b) * (radius - thickness),
                    red, green, blue, alpha);
        }
    }

    private static void polygon(VertexConsumer consumer, PoseStack poseStack, int sides, float radius, float thickness, float offsetDegrees, int red, int green, int blue, int alpha) {
        float offset = offsetDegrees * Mth.DEG_TO_RAD;
        for (int i = 0; i < sides; i++) {
            float a = offset + Mth.TWO_PI * i / sides;
            float b = offset + Mth.TWO_PI * (i + 1) / sides;
            line(consumer, poseStack, Mth.cos(a) * radius, Mth.sin(a) * radius, Mth.cos(b) * radius, Mth.sin(b) * radius, thickness, red, green, blue, alpha);
        }
    }

    private static void pentagram(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float offsetDegrees, int red, int green, int blue, int alpha) {
        float offset = offsetDegrees * Mth.DEG_TO_RAD;
        for (int i = 0; i < 5; i++) {
            int next = (i + 2) % 5;
            float a = offset + Mth.TWO_PI * i / 5.0F;
            float b = offset + Mth.TWO_PI * next / 5.0F;
            line(consumer, poseStack, Mth.cos(a) * radius, Mth.sin(a) * radius, Mth.cos(b) * radius, Mth.sin(b) * radius, thickness, red, green, blue, alpha);
        }
    }

    private static void spokes(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int red, int green, int blue, int alpha) {
        for (int i = 0; i < count; i++) {
            float a = Mth.TWO_PI * i / count;
            line(consumer, poseStack, Mth.cos(a) * innerRadius, Mth.sin(a) * innerRadius, Mth.cos(a) * outerRadius, Mth.sin(a) * outerRadius, thickness, red, green, blue, alpha);
        }
    }

    private static void zigzagRing(VertexConsumer consumer, PoseStack poseStack, int segments, float radius, float amplitude, float thickness, int red, int green, int blue, int alpha) {
        float previousX = radius + amplitude;
        float previousZ = 0.0F;
        for (int i = 1; i <= segments; i++) {
            float angle = Mth.TWO_PI * i / segments;
            float currentRadius = radius + ((i & 1) == 0 ? amplitude : -amplitude);
            float x = Mth.cos(angle) * currentRadius;
            float z = Mth.sin(angle) * currentRadius;
            line(consumer, poseStack, previousX, previousZ, x, z, thickness, red, green, blue, alpha);
            previousX = x;
            previousZ = z;
        }
    }

    private static void glyphMarks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float length, float thickness, int red, int green, int blue, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            float tangent = angle + Mth.HALF_PI;
            float cx = Mth.cos(angle) * radius;
            float cz = Mth.sin(angle) * radius;
            float half = length * (0.5F + (i % 3) * 0.2F);
            line(consumer, poseStack, cx - Mth.cos(tangent) * half, cz - Mth.sin(tangent) * half, cx + Mth.cos(tangent) * half, cz + Mth.sin(tangent) * half, thickness, red, green, blue, alpha);
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float z1, float x2, float z2, float thickness, int red, int green, int blue, int alpha) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length <= 0.0001F || alpha <= 0) {
            return;
        }
        float px = -dz / length * thickness * 0.5F;
        float pz = dx / length * thickness * 0.5F;
        quad(consumer, poseStack, x1 - px, 0.0F, z1 - pz, x1 + px, 0.0F, z1 + pz, x2 + px, 0.0F, z2 + pz, x2 - px, 0.0F, z2 - pz, red, green, blue, alpha);
    }

    private static void flame(VertexConsumer consumer, PoseStack poseStack, float width, float height, float bend, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        consumer.addVertex(poseStack.last(), -width, -height * 0.38F, 0.0F).setColor(6, 1, 10, Math.max(0, alpha / 3));
        consumer.addVertex(poseStack.last(), width, -height * 0.16F, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), bend, height, 0.0F).setColor(Math.min(255, red + 45), Math.min(120, green + 26), Math.min(90, blue + 18), Math.max(0, alpha - 12));
        consumer.addVertex(poseStack.last(), -width * 0.62F, height * 0.12F, 0.0F).setColor(10, 2, 16, Math.max(0, alpha / 2));
    }

    private static void diamond(VertexConsumer consumer, PoseStack poseStack, float scale, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), 0.0F, -scale, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), scale, 0.0F, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), 0.0F, scale, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), -scale, 0.0F, 0.0F).setColor(red, green, blue, alpha);
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

    private static int colorR(int index) {
        return index % 4 == 0 ? 188 : index % 3 == 0 ? 74 : 12;
    }

    private static int colorG(int index) {
        return index % 4 == 0 ? 28 : index % 3 == 0 ? 7 : 2;
    }

    private static int colorB(int index) {
        return index % 4 == 0 ? 12 : index % 3 == 0 ? 82 : 28;
    }

    private static int alpha(int base, float fade) {
        return Mth.clamp(Math.round(base * fade), 0, 255);
    }

    public static final class State extends EntityRenderState {
        private float radius = 4.0F;
        private int life = 120;
    }
}
