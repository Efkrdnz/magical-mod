package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.TowerAuraEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class TowerAuraRenderer extends EntityRenderer<TowerAuraEntity, TowerAuraRenderer.State> {
    private static final int SEGMENTS = 48;
    private static final int BANDS = 16;

    public TowerAuraRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(TowerAuraEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        return entity.shouldRender(cameraX, cameraY, cameraZ) && frustum.isVisible(entity.cullingBounds());
    }

    @Override
    public void extractRenderState(TowerAuraEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.height = entity.height();
        state.time = (entity.level() != null ? entity.level().getGameTime() % 240000L : 0L) + partialTick;
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(MagicalRenderTypes.towerAura());
        drawShell(consumer, matrix, state, 1.0F, 1.0F);
        // Faint outer echo shell, out of phase, for depth in the flux.
        drawShell(consumer, matrix, state, 1.045F, 0.38F);
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void drawShell(VertexConsumer consumer, Matrix4f matrix, State state, float radiusScale, float alphaScale) {
        for (int band = 0; band < BANDS; band++) {
            for (int segment = 0; segment < SEGMENTS; segment++) {
                vertex(consumer, matrix, state, segment, band, radiusScale, alphaScale);
                vertex(consumer, matrix, state, segment, band + 1, radiusScale, alphaScale);
                vertex(consumer, matrix, state, segment + 1, band + 1, radiusScale, alphaScale);
                vertex(consumer, matrix, state, segment + 1, band, radiusScale, alphaScale);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, State state, int segment, int band, float radiusScale, float alphaScale) {
        float angle = Mth.TWO_PI * segment / SEGMENTS;
        float heightFraction = band / (float) BANDS;
        float time = state.time;
        // Flux: integer angle harmonics keep the wrap seam continuous; the surface breathes,
        // buckles, and ripples instead of standing as a rigid cylinder.
        float flux = 1.0F
                + 0.035F * Mth.sin(time * 0.031F + angle * 3.0F + heightFraction * 9.0F)
                + 0.028F * Mth.sin(time * 0.019F - angle * 5.0F + heightFraction * 14.0F + 2.1F)
                + 0.018F * Mth.sin(time * 0.057F + angle * 8.0F - heightFraction * 22.0F);
        float radius = state.radius * radiusScale * flux;
        float x = Mth.cos(angle) * radius;
        float z = Mth.sin(angle) * radius;
        float y = heightFraction * state.height;
        // Mirrored angular UV keeps the noise seamless where the cylinder wraps.
        float u = 1.0F - Math.abs(1.0F - 2.0F * segment / (float) SEGMENTS);
        int alpha = Mth.clamp(Math.round(alphaProfile(heightFraction) * alphaScale * 255.0F), 0, 255);
        consumer.addVertex(matrix, x, y, z).setUv(u, heightFraction).setColor(255, 255, 255, alpha);
    }

    /** Soft fade near the ground and a long dissolve toward the crown. */
    private static float alphaProfile(float heightFraction) {
        float bottom = Mth.clamp(heightFraction / 0.05F, 0.0F, 1.0F);
        float top = 1.0F - Mth.clamp((heightFraction - 0.72F) / 0.28F, 0.0F, 1.0F);
        return bottom * (0.25F + 0.75F * top * top);
    }

    public static final class State extends EntityRenderState {
        private float radius = 26.0F;
        private float height = 320.0F;
        private float time;
    }
}
