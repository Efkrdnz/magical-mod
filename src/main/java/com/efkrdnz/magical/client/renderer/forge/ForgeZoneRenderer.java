package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.efkrdnz.magical.entity.ForgeZoneEntity;
import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * Draws a lingering Art: two counter-turning rings lying flat on the ground at the zone's radius,
 * with a soft disc under them. Everything goes on {@code MagicalRenderTypes.forgeImpact()} — one
 * render type, one consumer, so there is never a second live buffer to invalidate the first.
 *
 * <p>A zone loaded from disk mid-life starts its client age at zero, so the fade restarts. That is
 * cosmetic only: the server keeps the authoritative age and expires the zone on schedule.</p>
 */
public final class ForgeZoneRenderer extends EntityRenderer<ForgeZoneEntity, ForgeZoneRenderer.State> {

    private static final float GROUND_LIFT = 0.06f;
    private static final float FADE_START = 0.7f;
    private static final float OUTER_SPIN_PER_TICK = 3.5f;
    private static final float INNER_SPIN_PER_TICK = -5.0f;
    private static final int RING_SPOKES = 12;
    private static final float SPOKE_HALF_ANGLE = 0.10f;
    private static final float OUTER_INNER_EDGE = 0.82f;
    private static final float INNER_RADIUS = 0.55f;
    private static final float INNER_INNER_EDGE = 0.70f;
    private static final float FLOOR_ALPHA = 70.0f;
    private static final float RING_ALPHA = 225.0f;
    private static final float PULSE_DEPTH = 0.12f;

    public ForgeZoneRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ForgeZoneEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.kind = entity.kind();
        state.color = entity.color();
        state.secondary = entity.secondaryColor();
        state.radius = entity.radius();
        state.life = Math.max(1, entity.life());
        state.progress = Mth.clamp(state.ageInTicks / state.life, 0.0f, 1.0f);
        state.alpha = 1.0f - ForgeRibbon.smoothstep(FADE_START, 1.0f, state.progress);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (state.alpha > 0.0f) {
            draw(state, poseStack, buffer);
        }
        super.render(state, poseStack, buffer, packedLight);
    }

    private static void draw(State state, PoseStack poseStack, MultiBufferSource buffer) {
        ForgePalette palette = new ForgePalette(state.color, state.secondary, state.secondary);
        poseStack.pushPose();
        poseStack.translate(0.0f, GROUND_LIFT, 0.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer disc = buffer.getBuffer(MagicalRenderTypes.forgeImpact());
        float pulse = 1.0f + PULSE_DEPTH * Mth.sin(state.ageInTicks * 0.3f);
        floor(disc, pose, state, palette);
        ring(disc, pose, state.radius * pulse, state.ageInTicks * OUTER_SPIN_PER_TICK, OUTER_INNER_EDGE,
                palette.primary(), ForgeRibbon.alpha(RING_ALPHA, state.alpha));
        ring(disc, pose, state.radius * INNER_RADIUS, state.ageInTicks * INNER_SPIN_PER_TICK, INNER_INNER_EDGE,
                palette.bloom(), ForgeRibbon.alpha(RING_ALPHA, state.alpha * 0.8f));
        poseStack.popPose();
    }

    /** The ground the zone is standing on, so an empty ring never reads as a floating hoop. */
    private static void floor(VertexConsumer disc, Matrix4f pose, State state, ForgePalette palette) {
        ForgeDisc.wedge(disc, pose, state.radius, 0.0f, Mth.TWO_PI, 0.0f, 1.0f, palette.secondary(),
                ForgeRibbon.alpha(FLOOR_ALPHA, state.alpha));
    }

    /** One turning ring: spokes cut out of a band, so the rotation actually reads. */
    private static void ring(VertexConsumer disc, Matrix4f pose, float radius, float spinDegrees, float innerEdge,
            int color, int alpha) {
        float spin = spinDegrees * Mth.DEG_TO_RAD;
        float step = Mth.TWO_PI / RING_SPOKES;
        for (int spoke = 0; spoke < RING_SPOKES; spoke++) {
            float centre = spin + step * spoke;
            ForgeDisc.wedge(disc, pose, radius, centre - step * 0.5f + SPOKE_HALF_ANGLE,
                    centre + step * 0.5f - SPOKE_HALF_ANGLE, innerEdge, 1.0f, color, alpha);
        }
    }

    /** Everything the ring geometry above is allowed to know about a zone. */
    public static final class State extends EntityRenderState {
        public ForgeZoneKind kind = ForgeZoneKind.PYRE_WHEEL;
        public int color = 0xFFFFFF;
        public int secondary = 0xFFFFFF;
        public float radius = 2.5f;
        public float life = 20.0f;
        public float progress;
        public float alpha;
    }
}
