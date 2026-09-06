package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SpacePocketRoomEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class SpacePocketRoomEffectRenderer extends EntityRenderer<SpacePocketRoomEffectEntity, SpacePocketRoomEffectRenderer.State> {
    public SpacePocketRoomEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpacePocketRoomEffectEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.height = entity.height();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float age = state.ageInTicks;
        float radius = state.radius;
        float half = radius;
        float top = state.height;
        for (int i = 0; i <= 8; i++) {
            float t = -half + i * (half * 2.0F / 8.0F);
            int alpha = alpha(42 + (i % 3) * 12, 0.72F + Mth.sin(age * 0.035F + i) * 0.18F);
            line3(consumer, poseStack, -half, 0.03F, t, half, 0.03F, t, 0.028F, 60, 185, 255, alpha);
            line3(consumer, poseStack, t, 0.03F, -half, t, 0.03F, half, 0.028F, 60, 185, 255, alpha);
            line3(consumer, poseStack, -half, top - 0.1F, t, half, top - 0.1F, t, 0.018F, 75, 100, 230, alpha / 2);
            line3(consumer, poseStack, t, top - 0.1F, -half, t, top - 0.1F, half, 0.018F, 75, 100, 230, alpha / 2);
        }
        for (int i = 0; i < 32; i++) {
            float seed = i * 17.31F;
            float x = Mth.sin(seed) * half * 0.92F;
            float z = Mth.cos(seed * 1.37F) * half * 0.92F;
            float y = 1.2F + Math.floorMod(i * 7, 10) * (top - 2.0F) / 10.0F + Mth.sin(age * 0.04F + i) * 0.18F;
            float size = 0.06F + (i % 4) * 0.018F;
            diamond(consumer, poseStack, x, y, z, size, i % 2 == 0 ? 82 : 140, i % 2 == 0 ? 220 : 95, 255, alpha(118, 0.8F + Mth.sin(age * 0.08F + i) * 0.2F));
        }
        for (int i = 0; i < 14; i++) {
            float y = 1.0F + i * top / 15.0F;
            float twist = age * 0.018F + i * 0.7F;
            float x1 = Mth.sin(twist) * half * 0.82F;
            float z1 = Mth.cos(twist * 1.4F) * half * 0.82F;
            float x2 = Mth.sin(twist + 1.8F) * half * 0.82F;
            float z2 = Mth.cos((twist + 1.8F) * 1.4F) * half * 0.82F;
            line3(consumer, poseStack, x1, y, z1, x2, y + Mth.sin(age * 0.03F + i) * 0.35F, z2, 0.025F, 115, 70, 255, alpha(54, 1.0F));
        }
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void diamond(VertexConsumer consumer, PoseStack poseStack, float x, float y, float z, float size, int red, int green, int blue, int alpha) {
        quad(consumer, poseStack, x, y - size, z, x + size, y, z, x, y + size, z, x - size, y, z, red, green, blue, alpha);
    }

    private static void line3(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length <= 0.0001F) {
            length = 1.0F;
        }
        float px = -dz / length * thickness * 0.5F;
        float pz = dx / length * thickness * 0.5F;
        quad(consumer, poseStack, x1 - px, y1, z1 - pz, x1 + px, y1, z1 + pz, x2 + px, y2, z2 + pz, x2 - px, y2, z2 - pz, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, z4).setColor(red, green, blue, alpha);
    }

    private static int alpha(int base, float fade) {
        return Mth.clamp(Math.round(base * fade), 0, 255);
    }

    public static final class State extends EntityRenderState {
        private float radius = 18.0F;
        private float height = 13.0F;
    }
}
