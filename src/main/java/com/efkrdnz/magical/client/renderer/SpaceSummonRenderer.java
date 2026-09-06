package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.SpaceSummonEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class SpaceSummonRenderer extends EntityRenderer<SpaceSummonEntity, SpaceSummonRenderer.State> {
    public SpaceSummonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (state.mode == SpaceSummonEntity.DYING_NEUTRON_STAR) {
            renderDyingNeutronStar(state, poseStack, buffer);
        } else if (state.mode == SpaceSummonEntity.WHITE_HOLE_PULSE) {
            renderWhiteHolePulse(state, poseStack, buffer);
        } else {
            renderEventHorizon(state, poseStack, buffer);
        }
        super.render(state, poseStack, buffer, packedLight);
    }

    private void renderWhiteHolePulse(State state, PoseStack poseStack, MultiBufferSource buffer) {
        float age = state.age;
        float charge = smooth(state.charge);
        float fade = state.fade;
        float radius = state.radius * (0.18F + charge * 0.34F);
        VertexConsumer wave = buffer.getBuffer(MagicalRenderTypes.whiteHoleWave());
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 4.8F));
        ShaderQuad.billboard(wave, poseStack, radius * 4.9F, radius * 4.9F, 0xF4FDFF, Mth.clamp(Math.round(210.0F * fade), 0, 235));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 9.5F));
        ShaderQuad.billboard(wave, poseStack, radius * 7.0F, radius * 7.0F, 0x9EEBFF, Mth.clamp(Math.round(105.0F * fade), 0, 150));
        poseStack.popPose();

        VertexConsumer lightning = buffer.getBuffer(RenderType.lightning());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 2.9F));
        for (int i = 0; i < 5; i++) {
            float local = radius * (1.2F + i * 0.58F + Mth.sin(age * 0.08F + i) * 0.08F);
            RenderShape.ring(lightning, poseStack, local, 0.0F, i % 2 == 0 ? 0xFFFFFF : 0xBDEBFF, (0.6F - i * 0.08F) * fade);
            poseStack.mulPose(Axis.XP.rotationDegrees(28.0F + i * 17.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * (0.35F + i * 0.08F)));
        }
        for (int i = 0; i < 18; i++) {
            float angle = Mth.TWO_PI * i / 18.0F + age * 0.035F;
            float inner = radius * 0.75F;
            float outer = radius * (3.0F + (i % 4) * 0.42F);
            RenderShape.line(lightning, poseStack, Mth.cos(angle) * inner, 0.0F, Mth.sin(angle) * inner, Mth.cos(angle) * outer, Mth.sin(age * 0.08F + i) * radius * 0.18F, Mth.sin(angle) * outer, radius * 0.012F, 0xF4FDFF, 0.28F * fade);
        }
        poseStack.popPose();
    }

    private void renderEventHorizon(State state, PoseStack poseStack, MultiBufferSource buffer) {
        float age = state.age;
        float charge = smooth(state.charge);
        float fade = state.fade;
        float radius = state.radius * (0.2F + charge * 0.28F);
        VertexConsumer horizon = buffer.getBuffer(MagicalRenderTypes.eventHorizonField());
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 2.4F));
        ShaderQuad.billboard(horizon, poseStack, radius * 4.4F, radius * 4.4F, 0x5E7DFF, Mth.clamp(Math.round(210.0F * fade), 0, 230));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 5.2F));
        ShaderQuad.billboard(horizon, poseStack, radius * 2.55F, radius * 2.55F, 0x16245F, Mth.clamp(Math.round(230.0F * fade), 0, 240));
        poseStack.popPose();

        VertexConsumer lens = buffer.getBuffer(MagicalRenderTypes.singularityLens());
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 1.2F));
        drawLens(lens, poseStack, radius * 3.4F, 0x5E7DFF, Mth.clamp(Math.round(160.0F * fade), 0, 190));
        poseStack.popPose();

        VertexConsumer lightning = buffer.getBuffer(RenderType.lightning());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.7F));
        poseStack.mulPose(Axis.XP.rotationDegrees(76.0F));
        RenderShape.ring(lightning, poseStack, radius * 1.7F, 0.0F, 0xA5BCFF, 0.46F * fade);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        RenderShape.ring(lightning, poseStack, radius * 2.25F, 0.0F, 0x5E7DFF, 0.28F * fade);
        poseStack.mulPose(Axis.XP.rotationDegrees(58.0F));
        RenderShape.ring(lightning, poseStack, radius * 2.85F, 0.0F, 0xFFFFFF, 0.16F * fade);
        poseStack.popPose();
    }


    private void renderDyingNeutronStar(State state, PoseStack poseStack, MultiBufferSource buffer) {
        float age = state.age;
        float charge = smooth(state.charge);
        float collapse = state.collapseTicks > 0 ? Mth.clamp(state.collapseTicks / 480.0F, 0.0F, 1.0F) : 0.0F;
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.47F);
        float shudder = Mth.sin(age * 2.9F) * 0.08F + Mth.sin(age * 5.7F) * 0.035F;
        float core = 0.82F + charge * 1.1F + pulse * 0.16F + shudder;
        float aura = core * (4.2F + charge * 1.8F + collapse * 8.0F);
        VertexConsumer stellar = buffer.getBuffer(MagicalRenderTypes.stellarGlow());

        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * -5.2F));
        ShaderQuad.billboard(stellar, poseStack, aura * 1.45F, aura * 1.45F, 0x8EEBFF, Mth.clamp(Math.round(195.0F + charge * 45.0F), 0, 245));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 12.0F));
        ShaderQuad.billboard(stellar, poseStack, core * 4.0F, core * 4.0F, 0xFFE9A6, 238);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 18.0F));
        ShaderQuad.billboard(stellar, poseStack, core * 2.15F, core * 2.15F, 0xFFFFFF, 255);
        poseStack.popPose();

        VertexConsumer lens = buffer.getBuffer(MagicalRenderTypes.singularityLens());

        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.6F));
        drawLens(lens, poseStack, aura * 1.35F, 0x73D8FF, Mth.clamp(Math.round((120.0F + charge * 80.0F) * (1.0F - collapse * 0.25F)), 0, 220));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * -3.8F));
        drawLens(lens, poseStack, aura * 0.82F, 0xFFE9A6, Mth.clamp(Math.round(95.0F + pulse * 90.0F + charge * 45.0F), 0, 235));
        drawLens(lens, poseStack, core * 2.9F, 0xFFFFFF, 245);
        drawLens(lens, poseStack, core * 1.32F, 0xFFFFFF, 255);
        poseStack.popPose();

        VertexConsumer lightning = buffer.getBuffer(RenderType.lightning());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2.2F));
        poseStack.mulPose(Axis.XP.rotationDegrees(72.0F + shudder * 120.0F));
        RenderShape.ring(lightning, poseStack, core * (2.15F + pulse * 0.22F), 0.0F, 0xBDEBFF, 0.88F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 6.5F));
        RenderShape.ring(lightning, poseStack, core * (2.75F + charge * 0.9F), 0.0F, 0xFFFFFF, 0.52F + charge * 0.18F);
        poseStack.mulPose(Axis.XP.rotationDegrees(63.0F));
        RenderShape.ring(lightning, poseStack, core * (3.45F + pulse * 0.5F + charge), 0.0F, 0xFFE9A6, 0.34F + charge * 0.22F);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        for (int i = 0; i < 10; i++) {
            float phase = age * (0.35F + i * 0.047F) + i * 19.73F;
            float angle = Mth.TWO_PI * i / 10.0F + Mth.sin(phase) * 0.22F;
            float length = core * (2.4F + charge * 3.2F + Mth.sin(phase * 1.7F) * 0.7F);
            float inner = core * (0.8F + Mth.cos(phase) * 0.1F);
            float thickness = 0.025F + charge * 0.035F;
            float x1 = Mth.cos(angle) * inner;
            float z1 = Mth.sin(angle) * inner;
            float x2 = Mth.cos(angle + Mth.sin(phase * 2.1F) * 0.1F) * length;
            float z2 = Mth.sin(angle + Mth.cos(phase * 1.9F) * 0.1F) * length;
            RenderShape.line(lightning, poseStack, x1, 0.0F, z1, x2, 0.0F, z2, thickness, i % 3 == 0 ? 0xFFFFFF : 0xBDEBFF, 0.28F + charge * 0.34F);
        }
        poseStack.popPose();
    }

    private static void drawLens(VertexConsumer consumer, PoseStack poseStack, float size, int alpha) {
        drawLens(consumer, poseStack, size, 0xFFFFFF, alpha);
    }

    private static void drawLens(VertexConsumer consumer, PoseStack poseStack, float size, int color, int alpha) {
        float half = size * 0.5F;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        consumer.addVertex(poseStack.last(), -half, -half, 0.0F).setUv(0.0F, 1.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), half, -half, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), half, half, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), -half, half, 0.0F).setUv(0.0F, 0.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), -half, half, 0.0F).setUv(0.0F, 0.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), half, half, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), half, -half, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, b, alpha);
        consumer.addVertex(poseStack.last(), -half, -half, 0.0F).setUv(0.0F, 1.0F).setColor(r, g, b, alpha);
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpaceSummonEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.mode = entity.mode();
        state.charge = entity.chargeFactor();
        state.fade = entity.fadeFactor();
        state.radius = entity.radius();
        state.collapseTicks = entity.collapseTicks();
    }

    /** Snapshot the renderer draws from. 1.21.4 renders from state, not from the entity. */
    public static final class State extends EntityRenderState {
        public float age;
        public int mode;
        public float charge;
        public float fade;
        public float radius;
        public int collapseTicks;
    }
}
