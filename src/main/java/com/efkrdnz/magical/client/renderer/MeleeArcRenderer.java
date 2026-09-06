package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.MeleeArcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the melee slash. The flying wave is a crescent that bulges along its travel direction, so it
 * leads edge-first ("facing forward") rather than presenting a flat moon; the in-front cut is a
 * sharp, camera-facing crescent that flashes and vanishes. Both have a white-hot cutting edge.
 */
public final class MeleeArcRenderer extends EntityRenderer<MeleeArcEntity, MeleeArcRenderer.State> {
    public MeleeArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MeleeArcEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        int color = entity.color();
        state.r = FusionGeometry.red(color);
        state.g = FusionGeometry.green(color);
        state.b = FusionGeometry.blue(color);
        state.halfWidth = entity.halfWidth();
        state.arc = entity.arc();
        state.life = Math.max(1, entity.life());
        state.cut = entity.cut();
        Vec3 dir = entity.direction();
        state.dirX = (float) dir.x;
        state.dirY = (float) dir.y;
        state.dirZ = (float) dir.z;
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float progress = Mth.clamp(state.ageInTicks / state.life, 0.0F, 1.0F);
        float fade = Mth.clamp(state.ageInTicks / (state.cut ? 0.6F : 1.5F), 0.0F, 1.0F)
                * (1.0F - smoothstep(state.cut ? 0.4F : 0.62F, 1.0F, progress));
        if (fade <= 0.0F) {
            super.render(state, poseStack, buffer, packedLight);
            return;
        }
        VertexConsumer beam = buffer.getBuffer(MagicalRenderTypes.fusionBeam());
        if (state.cut) {
            renderCut(state, poseStack, beam, fade);
        } else {
            renderFlying(state, poseStack, beam, fade);
        }
        super.render(state, poseStack, buffer, packedLight);
    }

    /** The in-front cut: a sharp diagonal crescent that faces the camera and flashes instantly. */
    private void renderCut(State state, PoseStack poseStack, VertexConsumer beam, float fade) {
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(-35.0F));
        crescentFlat(beam, poseStack.last().pose(), state, fade, state.halfWidth * 1.2F, state.halfWidth * 0.5F, 118.0F);
        poseStack.popPose();
    }

    /** The flying wave: a crescent that bulges toward the travel direction so it leads edge-first. */
    private void renderFlying(State state, PoseStack poseStack, VertexConsumer beam, float fade) {
        poseStack.pushPose();
        Vec3 dir = new Vec3(state.dirX, state.dirY, state.dirZ);
        if (dir.lengthSqr() > 1.0E-6D) {
            dir = dir.normalize();
            poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(dir.x, dir.z)));
            poseStack.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D))));
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F)); // diagonal slash
        // Trailing echo, then the leading crescent.
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, -0.9D);
        crescentForward(beam, poseStack.last().pose(), state, fade * 0.35F, 0.82F);
        poseStack.popPose();
        crescentForward(beam, poseStack.last().pose(), state, fade, 1.0F);
        poseStack.popPose();
    }

    /** Crescent in the local Y-Z plane, bulging toward +Z (forward). Belly leads, tips sweep back. */
    private void crescentForward(VertexConsumer beam, Matrix4f m, State state, float fade, float scale) {
        int segments = 30;
        float radius = state.halfWidth * 1.3F * scale;
        float maxThick = state.halfWidth * 0.85F * scale;
        float halfArc = Math.min(140.0F, state.arc * 0.9F) * 0.5F * Mth.DEG_TO_RAD;
        int bodyAlpha = Math.round(205.0F * fade);
        int edgeR = Math.min(255, state.r + 130);
        int edgeG = Math.min(255, state.g + 130);
        int edgeB = Math.min(255, state.b + 130);
        int edgeAlpha = Math.round(255.0F * fade);
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float a0 = Mth.lerp(t0, -halfArc, halfArc);
            float a1 = Mth.lerp(t1, -halfArc, halfArc);
            float w0 = maxThick * bladeProfile(a0 / halfArc);
            float w1 = maxThick * bladeProfile(a1 / halfArc);
            float oy0 = Mth.sin(a0) * radius;
            float oz0 = Mth.cos(a0) * radius;
            float oy1 = Mth.sin(a1) * radius;
            float oz1 = Mth.cos(a1) * radius;
            float iy0 = Mth.sin(a0) * (radius - w0);
            float iz0 = Mth.cos(a0) * (radius - w0);
            float iy1 = Mth.sin(a1) * (radius - w1);
            float iz1 = Mth.cos(a1) * (radius - w1);
            FusionGeometry.quad(beam, m,
                    0.0F, iy0, iz0, t0, 0.0F,
                    0.0F, oy0, oz0, t0, 1.0F,
                    0.0F, oy1, oz1, t1, 1.0F,
                    0.0F, iy1, iz1, t1, 0.0F,
                    state.r, state.g, state.b, bodyAlpha);
            float ey0 = Mth.lerp(0.62F, iy0, oy0);
            float ez0 = Mth.lerp(0.62F, iz0, oz0);
            float ey1 = Mth.lerp(0.62F, iy1, oy1);
            float ez1 = Mth.lerp(0.62F, iz1, oz1);
            FusionGeometry.quad(beam, m,
                    0.0F, ey0, ez0, t0, 0.0F,
                    0.0F, oy0, oz0, t0, 1.0F,
                    0.0F, oy1, oz1, t1, 1.0F,
                    0.0F, ey1, ez1, t1, 0.0F,
                    edgeR, edgeG, edgeB, edgeAlpha);
        }
    }

    /** Crescent in the local X-Y plane (used camera-facing for the flat cut flash). */
    private void crescentFlat(VertexConsumer beam, Matrix4f m, State state, float fade, float radius, float maxThick, float arcDegrees) {
        int segments = 26;
        float halfArc = arcDegrees * 0.5F * Mth.DEG_TO_RAD;
        int bodyAlpha = Math.round(205.0F * fade);
        int edgeR = Math.min(255, state.r + 130);
        int edgeG = Math.min(255, state.g + 130);
        int edgeB = Math.min(255, state.b + 130);
        int edgeAlpha = Math.round(255.0F * fade);
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float a0 = Mth.lerp(t0, -halfArc, halfArc);
            float a1 = Mth.lerp(t1, -halfArc, halfArc);
            float w0 = maxThick * bladeProfile(a0 / halfArc);
            float w1 = maxThick * bladeProfile(a1 / halfArc);
            float ox0 = Mth.sin(a0) * radius;
            float oy0 = Mth.cos(a0) * radius;
            float ox1 = Mth.sin(a1) * radius;
            float oy1 = Mth.cos(a1) * radius;
            float ix0 = Mth.sin(a0) * (radius - w0);
            float iy0 = Mth.cos(a0) * (radius - w0);
            float ix1 = Mth.sin(a1) * (radius - w1);
            float iy1 = Mth.cos(a1) * (radius - w1);
            FusionGeometry.quad(beam, m,
                    ix0, iy0, 0.0F, t0, 0.0F,
                    ox0, oy0, 0.0F, t0, 1.0F,
                    ox1, oy1, 0.0F, t1, 1.0F,
                    ix1, iy1, 0.0F, t1, 0.0F,
                    state.r, state.g, state.b, bodyAlpha);
            float ex0 = Mth.lerp(0.62F, ix0, ox0);
            float ey0 = Mth.lerp(0.62F, iy0, oy0);
            float ex1 = Mth.lerp(0.62F, ix1, ox1);
            float ey1 = Mth.lerp(0.62F, iy1, oy1);
            FusionGeometry.quad(beam, m,
                    ex0, ey0, 0.0F, t0, 0.0F,
                    ox0, oy0, 0.0F, t0, 1.0F,
                    ox1, oy1, 0.0F, t1, 1.0F,
                    ex1, ey1, 0.0F, t1, 0.0F,
                    edgeR, edgeG, edgeB, edgeAlpha);
        }
    }

    /** Blade cross-section: fat in the middle, tapering to needle points at both tips. */
    private static float bladeProfile(float n) {
        float x = Mth.clamp(n, -1.0F, 1.0F);
        return (float) Math.pow(1.0F - x * x, 0.7D);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public static final class State extends EntityRenderState {
        private int r = 255;
        private int g = 255;
        private int b = 255;
        private float halfWidth = 1.7F;
        private float arc = 150.0F;
        private float life = 12.0F;
        private boolean cut;
        private float dirX;
        private float dirY;
        private float dirZ = 1.0F;
    }
}
