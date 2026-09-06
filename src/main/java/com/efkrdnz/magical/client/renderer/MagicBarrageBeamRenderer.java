package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.MagicBarrageBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class MagicBarrageBeamRenderer extends EntityRenderer<MagicBarrageBeamEntity, MagicBarrageBeamRenderer.State> {
    public MagicBarrageBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MagicBarrageBeamEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.color = entity.color();
        state.size = entity.size();
        state.startOffset = entity.startOffset();
        state.chargeTicks = entity.chargeTicks();
        state.fadeTicks = entity.fadeTicks();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        int red = red(state.color);
        int green = green(state.color);
        int blue = blue(state.color);
        float charge = Mth.clamp(state.ageInTicks / Math.max(1.0F, state.chargeTicks), 0.0F, 1.0F);
        float impact = Mth.clamp((state.ageInTicks - state.chargeTicks) / Math.max(1.0F, state.fadeTicks), 0.0F, 1.0F);
        float beamWidth = state.ageInTicks < state.chargeTicks
                ? 0.018F + charge * 0.035F
                : (0.22F + state.size * 0.18F) * (1.0F - impact * 0.55F);
        int beamAlpha = state.ageInTicks < state.chargeTicks
                ? alpha(70 + Math.round(charge * 120.0F), 1.0F)
                : alpha(235, 1.0F - impact);
        Vec3 start = state.startOffset;
        Vec3 end = Vec3.ZERO;
        line(consumer, poseStack, start, end, beamWidth, red, green, blue, beamAlpha);
        line(consumer, poseStack, start.scale(0.82D), end, beamWidth * 0.42F, 245, 250, 255, Math.max(0, beamAlpha - 30));

        float warningRadius = state.size * (0.75F + charge * 0.55F + impact * 0.9F);
        int warningAlpha = state.ageInTicks < state.chargeTicks ? alpha(120, 1.0F - charge * 0.25F) : alpha(185, 1.0F - impact);
        ring(consumer, poseStack, warningRadius, 0.035F + impact * 0.075F, red, green, blue, warningAlpha);
        ring(consumer, poseStack, warningRadius * 0.48F, 0.022F, 245, 250, 255, Math.max(0, warningAlpha - 42));
        if (state.ageInTicks >= state.chargeTicks) {
            burst(consumer, poseStack, warningRadius * (1.15F + impact * 0.85F), red, green, blue, alpha(145, 1.0F - impact));
        }
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, int red, int green, int blue, int alpha) {
        int segments = 72;
        for (int i = 0; i < segments; i++) {
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    new Vec3(Mth.cos(a) * (radius - thickness), 0.0D, Mth.sin(a) * (radius - thickness)),
                    new Vec3(Mth.cos(a) * (radius + thickness), 0.0D, Mth.sin(a) * (radius + thickness)),
                    new Vec3(Mth.cos(b) * (radius + thickness), 0.0D, Mth.sin(b) * (radius + thickness)),
                    new Vec3(Mth.cos(b) * (radius - thickness), 0.0D, Mth.sin(b) * (radius - thickness)),
                    red, green, blue, alpha);
        }
    }

    private static void burst(VertexConsumer consumer, PoseStack poseStack, float radius, int red, int green, int blue, int alpha) {
        for (int i = 0; i < 14; i++) {
            float angle = Mth.TWO_PI * i / 14.0F;
            line(consumer, poseStack, Vec3.ZERO, new Vec3(Mth.cos(angle) * radius, 0.0D, Mth.sin(angle) * radius), 0.045F, red, green, blue, alpha);
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, float thickness, int red, int green, int blue, int alpha) {
        Vec3 direction = b.subtract(a);
        if (direction.lengthSqr() < 1.0E-6D || alpha <= 0) {
            return;
        }
        Vec3 normal = new Vec3(-direction.z, 0.0D, direction.x);
        if (normal.lengthSqr() < 1.0E-6D) {
            normal = new Vec3(1.0D, 0.0D, 0.0D);
        }
        normal = normal.normalize().scale(thickness * 0.5F);
        quad(consumer, poseStack, a.subtract(normal), a.add(normal), b.add(normal), b.subtract(normal), red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) d.x, (float) d.y, (float) d.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) d.x, (float) d.y, (float) d.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, alpha);
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
        private int color = 0x8E7BFF;
        private float size = 1.0F;
        private Vec3 startOffset = new Vec3(0.0D, 8.0D, 0.0D);
        private int chargeTicks = 20;
        private int fadeTicks = 12;
    }
}
