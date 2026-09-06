package com.efkrdnz.magical.client.renderer.fx;

import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.FxPainters;
import com.efkrdnz.magical.entity.fx.ProfiledEffect;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The one renderer every profile-driven entity uses: resolve the profile from the synced skill
 * index, orient along the travel direction when the primary silhouette is a travel form, and let
 * the family painters draw. shouldRender uses the profile bounds.
 */
public class ProfileRendererShell<E extends Entity & ProfiledEffect> extends EntityRenderer<E, ProfileRendererShell.State> {
    public ProfileRendererShell(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(E entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.skillIndex = entity.skillIndex();
        state.age = entity.effectAge() + partialTick;
        state.life = entity.effectLife();
        state.phase = entity.effectPhase();
        state.seed = entity.effectSeed();
        state.direction = entity.effectDirection();
        state.value = entity.effectValue();
        state.radius = entity.effectRadius();
        state.drawMode = entity.effectDrawMode();
        state.data = entity.effectData();
        Vec3 pos = entity.getPosition(partialTick);
        state.origin = pos;
        Vec3 end = entity.effectEndPoint();
        state.endPoint = end != null ? end.subtract(pos) : null;
        state.distanceSqr = entityRenderDispatcher.camera.getPosition().distanceToSqr(pos);
        state.cameraOffset = entityRenderDispatcher.camera.getPosition().subtract(pos);
    }

    @Override
    public boolean shouldRender(E entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        ProfileCues.BoundsSpec bounds = entity.profile().bounds();
        float r = Math.max(bounds.horizontal(), entity.effectRadius() + 1.0F);
        AABB box = new AABB(entity.getX() - r, entity.getY() - bounds.down(), entity.getZ() - r, entity.getX() + r, entity.getY() + Math.max(bounds.up(), entity.effectRadius()), entity.getZ() + r);
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(cameraX, cameraY, cameraZ)) && frustum.isVisible(box);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VisualProfile profile = com.efkrdnz.magical.magic.visual.VisualProfiles.byIndex(state.skillIndex);
        FxContext ctx = new FxContext(poseStack, buffer, 0.0F, entityRenderDispatcher.cameraOrientation(), state.cameraOffset)
                .timing(state.age, state.life, state.seed)
                .phase(state.phase);
        ctx.extra = state.value;
        ctx.data = state.data;
        ctx.origin = state.origin;
        ctx.direction = state.direction;
        ctx.detail = FxBudget.detailForDistance(3, state.distanceSqr);
        ctx.lod = FxBudget.lodForDistance(state.distanceSqr);
        if (state.endPoint != null) {
            ctx.endPoint = state.endPoint;
        }
        poseStack.pushPose();
        for (Silhouette silhouette : profile.silhouettes()) {
            if (!silhouette.drawnIn(state.drawMode)) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(0.0F, silhouette.offsetY(), 0.0F);
            if (isTravelForm(silhouette) && state.direction.lengthSqr() > 1.0E-6D) {
                FilamentPainter.orientAlong(poseStack, state.direction);
            } else if (isGroundForm(silhouette)) {
                if (state.direction.lengthSqr() > 1.0E-6D && Math.abs(state.direction.y) < 0.999D && silhouette.family() != Silhouette.Family.GLYPH) {
                    FilamentPainter.orientToNormal(poseStack, state.direction);
                } else {
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
                }
                poseStack.translate(0.0F, 0.0F, -0.04F);
            }
            Silhouette scaled = silhouette;
            if (state.radius > 0.0F) {
                if (scalesWithRadius(silhouette)) {
                    scaled = withRadius(silhouette, state.radius);
                } else if (lengthFromRadius(silhouette)) {
                    scaled = silhouette.withSize(silhouette.sizeA(), state.radius);
                }
            }
            FxPainters.paint(ctx, profile, scaled);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    /** Forms whose local +Z must point along the synced direction (travel, or facing for panes/walls). */
    private static boolean isTravelForm(Silhouette s) {
        return switch (s.form()) {
            case TUBE, HELIX, CROSSED_BLADES, PANE, VERTICAL_PANE, WALL, SLAB, CHAIN, FAN, LANE -> true;
            default -> false;
        };
    }

    /** Filament lengths follow the synced radius (beams that extend to a clipped distance). */
    private static boolean lengthFromRadius(Silhouette s) {
        return s.family() == Silhouette.Family.FILAMENT && (s.form() == Silhouette.Form.TUBE || s.form() == Silhouette.Form.HELIX || s.form() == Silhouette.Form.COLUMN)
                || s.family() == Silhouette.Family.FIELD && s.form() == Silhouette.Form.LANE;
    }

    private static boolean isGroundForm(Silhouette s) {
        return s.family() == Silhouette.Family.MARK
                || (s.family() == Silhouette.Family.GLYPH)
                || (s.family() == Silhouette.Family.RIFT && (s.form() == Silhouette.Form.GROUND_SEAM || s.form() == Silhouette.Form.GROUND_STAR || s.form() == Silhouette.Form.DISC));
    }

    /** Silhouettes whose sizeA is the synced effect radius (marks, clouds, domes, basins). */
    private static boolean scalesWithRadius(Silhouette s) {
        return s.family() == Silhouette.Family.MARK || s.family() == Silhouette.Family.SWARM
                || (s.family() == Silhouette.Family.FIELD && (s.form() == Silhouette.Form.DOME || s.form() == Silhouette.Form.CYLINDER || s.form() == Silhouette.Form.SPHERE || s.form() == Silhouette.Form.DISC || s.form() == Silhouette.Form.BASIN));
    }

    private static Silhouette withRadius(Silhouette s, float radius) {
        return s.withSize(radius, s.sizeB());
    }

    public static class State extends EntityRenderState {
        public int skillIndex = -1;
        public float age;
        public int life;
        public float phase;
        public int seed;
        public Vec3 direction = Vec3.ZERO;
        public float value;
        public float radius;
        public int drawMode;
        public net.minecraft.nbt.CompoundTag data;
        public Vec3 origin = Vec3.ZERO;
        public Vec3 endPoint;
        public double distanceSqr;
        public Vec3 cameraOffset = Vec3.ZERO;
    }
}
