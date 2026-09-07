package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.client.renderer.forge.ForgeImpactGeometry;
import com.efkrdnz.magical.client.renderer.forge.ForgePalette;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon;
import com.efkrdnz.magical.client.renderer.forge.ForgeTelegraphGeometry;
import com.efkrdnz.magical.entity.ForgeEffectEntity;
import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * Draws every forged-combat visual that is not the strike itself: the eight element impacts, the
 * charge telegraph, the brand and the slam cracks. Each style says which frame it wants to be drawn
 * in — facing the camera, flat on the ground, or left in world space — and the shapes live in
 * {@code client.renderer.forge}.
 */
public final class ForgeEffectRenderer extends EntityRenderer<ForgeEffectEntity, ForgeEffectRenderer.State> {

    /** How a style wants its pose set up before its geometry runs. */
    private enum Frame { BILLBOARD, GROUND, WORLD }

    private static final float FADE_START = 0.6f;

    public ForgeEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ForgeEffectEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.style = entity.style();
        state.color = entity.color();
        state.secondary = entity.secondaryColor();
        state.scale = entity.scale();
        state.heavy = entity.heavy();
        state.life = Math.max(1, entity.life());
        state.seed = entity.getId();
        state.progress = Mth.clamp(state.ageInTicks / state.life, 0.0f, 1.0f);
        state.alpha = 1.0f - ForgeRibbon.smoothstep(FADE_START, 1.0f, state.progress);
        Vec3 end = entity.end().subtract(entity.position());
        state.endX = (float) end.x;
        state.endY = (float) end.y;
        state.endZ = (float) end.z;
        follow(entity, state, partialTick);
    }

    /** A visual bound to a body is drawn on that body's own interpolated position, not a tick behind. */
    private static void follow(ForgeEffectEntity entity, State state, float partialTick) {
        state.anchorX = 0.0f;
        state.anchorY = 0.0f;
        state.anchorZ = 0.0f;
        Entity target = entity.level().getEntity(entity.targetId());
        if (target == null) {
            return;
        }
        Vec3 offset = target.getPosition(partialTick).subtract(entity.getPosition(partialTick));
        state.anchorX = (float) offset.x;
        state.anchorY = (float) offset.y;
        state.anchorZ = (float) offset.z;
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (state.alpha > 0.0f) {
            draw(state, poseStack, buffer);
        }
        super.render(state, poseStack, buffer, packedLight);
    }

    /**
     * Exactly one render type is live at any point below. Both forge types share the immediate
     * source's one buffer, so fetching the second consumer builds the first and any write through
     * it afterwards throws; the fetch therefore happens inside the branch that is about to write.
     */
    private void draw(State state, PoseStack poseStack, MultiBufferSource buffer) {
        ForgePalette palette = new ForgePalette(state.color, state.secondary, state.secondary);
        poseStack.pushPose();
        poseStack.translate(state.anchorX, state.anchorY, state.anchorZ);
        frame(state.style, poseStack);
        switch (state.style) {
            case CHARGE_TELEGRAPH -> ForgeTelegraphGeometry.chargeTelegraph(poseStack, buffer, state, palette);
            case STORM_FORK -> ForgeImpactGeometry.stormFork(buffer.getBuffer(MagicalRenderTypes.forgeEdge()),
                    poseStack.last().pose(), state, palette);
            default -> onDisc(state, poseStack.last().pose(),
                    buffer.getBuffer(MagicalRenderTypes.forgeImpact()), palette);
        }
        poseStack.popPose();
    }

    /** The nine styles that are pure impact disc, all written through the one consumer above. */
    private static void onDisc(State state, Matrix4f pose, VertexConsumer disc, ForgePalette palette) {
        switch (state.style) {
            case FIRE_BLOOM -> ForgeImpactGeometry.fireBloom(disc, pose, state, palette);
            case FROST_SHARDS -> ForgeImpactGeometry.frostShards(disc, pose, state, palette);
            case VOID_IMPLOSION -> ForgeImpactGeometry.voidImplosion(disc, pose, state, palette);
            case RADIANT_CROSS -> ForgeImpactGeometry.radiantCross(disc, pose, state, palette);
            case VENOM_DRIP -> ForgeImpactGeometry.venomDrip(disc, pose, state, palette);
            case TERRA_SHARDS -> ForgeImpactGeometry.terraShards(disc, pose, state, palette);
            case GALE_SWIRL -> ForgeImpactGeometry.galeSwirl(disc, pose, state, palette);
            case BRAND_MARK -> ForgeTelegraphGeometry.brandMark(disc, pose, state, palette);
            case SLAM_CRACK -> ForgeTelegraphGeometry.slamCrack(disc, pose, state, palette);
            case STORM_FORK, CHARGE_TELEGRAPH -> { } // drawn by the caller, on their own types
        }
    }

    private void frame(ForgeEffectStyle style, PoseStack poseStack) {
        switch (frameOf(style)) {
            case BILLBOARD -> poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            case GROUND -> {
                poseStack.translate(0.0f, 0.08f, 0.0f);
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
            }
            case WORLD -> { }
        }
    }

    private static Frame frameOf(ForgeEffectStyle style) {
        return switch (style) {
            case TERRA_SHARDS, SLAM_CRACK -> Frame.GROUND;
            case STORM_FORK, CHARGE_TELEGRAPH -> Frame.WORLD;
            default -> Frame.BILLBOARD;
        };
    }

    /** Everything the impact and telegraph geometry is allowed to know about an effect. */
    public static final class State extends EntityRenderState {
        public ForgeEffectStyle style = ForgeEffectStyle.FIRE_BLOOM;
        public int color = 0xFFFFFF, secondary = 0xFFFFFF;
        public boolean heavy;
        public long seed;
        public float scale = 1.0f, life = 8.0f, progress, alpha;
        public float endX, endY, endZ;
        /** Offset from this entity's render position to the body it rides, zero when it rides none. */
        public float anchorX, anchorY, anchorZ;
    }
}
