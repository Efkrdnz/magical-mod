package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.voxel.BloodHarvestMotion;
import com.efkrdnz.magical.client.renderer.fx.voxel.VoxelEmitter;
import com.efkrdnz.magical.client.renderer.fx.voxel.VoxelMotion;
import com.efkrdnz.magical.client.renderer.fx.voxel.VoxelStyle;
import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.passive.BloodHarvestRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws harvested blood: a pool of cubes at the corpse, the stream of them into the player, and
 * the burst back out when the Vessel overflows.
 *
 * <p>The same cubes, colour and render type as Blood Manipulation's field, through the same
 * {@link VoxelEmitter}, out of the same shared voxel budget. What differs is the motion, which is
 * {@link BloodHarvestMotion}'s, and the target: the field flies to points it was told, this flies to
 * a chest that is read off the owner's interpolated position every frame. The entity itself never
 * moves - it marks where the blood was spilled - so the owner is the only thing that has to be
 * re-read to draw a stream that tracks a sprinting player without stutter.
 */
public final class BloodHarvestRenderer extends EntityRenderer<BloodHarvestEntity, BloodHarvestRenderer.State> {

    private static final VoxelStyle STYLE = VoxelStyle.HARVEST;
    private static final int ALPHA = 255;

    public BloodHarvestRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BloodHarvestEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.phase = entity.phase();
        state.phaseAge = state.age - entity.phaseTick();
        state.flight = entity.flight();
        state.life = entity.life();
        state.cubes = Math.min(STYLE.cap(), BloodHarvestRules.cubes(entity.worth()));
        state.seed = entity.seed();

        Vec3 origin = entity.getPosition(partialTick);
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        state.cameraOffset = camera.subtract(origin);
        state.distanceSqr = camera.distanceToSqr(origin);

        Entity owner = entity.owner();
        state.chest = owner == null ? null
                : owner.getPosition(partialTick)
                        .add(0.0D, owner.getBbHeight() * BloodHarvestEntity.CHEST_HEIGHT, 0.0D)
                        .subtract(origin);
    }

    /**
     * The box is the pool while it pools, and the pool plus the owner once it flies: the corpse can
     * be behind the camera while the blood is coming straight at it.
     */
    @Override
    public boolean shouldRender(BloodHarvestEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        AABB box = entity.getBoundingBox().inflate(STYLE.burstRadius() + 0.5D);
        Entity owner = entity.owner();
        if (owner != null && entity.phase() != BloodHarvestEntity.PHASE_POOLED) {
            box = box.minmax(owner.getBoundingBox().inflate(STYLE.archHeight() + 1.0D));
        }
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(cameraX, cameraY, cameraZ))
                && frustum.isVisible(box);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float keep = VoxelMotion.lodKeep(Math.sqrt(state.distanceSqr));
        if (keep <= 0.0F) {
            return;
        }
        int wanted = Math.max(1, Math.round(state.cubes * keep));
        int granted = FxBudget.claimVoxels(wanted);
        if (granted <= 0) {
            return;
        }
        if (granted < wanted) {
            keep *= granted / (float) wanted;
        }

        // The pose is the entity's position less the camera's, so the camera in local space is the
        // offset the state carries: no extra translate goes in, so nothing has to come back out.
        VertexConsumer consumer = buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        VoxelEmitter emitter = new VoxelEmitter(consumer, poseStack.last(),
                (float) state.cameraOffset.x, (float) state.cameraOffset.y, (float) state.cameraOffset.z);
        int basePacked = MagicVertex.pack(STYLE.kind().id(), STYLE.shaderCount(), STYLE.shaderParamB(),
                0.0F, state.seed, 0);
        VoxelStyle.Timeline timing = STYLE.timing();

        // A stream with no tracked owner is drawn as a pool: better a puddle that lingers a frame
        // than cubes flying at a point nobody can see.
        boolean flying = state.phase == BloodHarvestEntity.PHASE_STREAMING && state.chest != null;
        boolean bursting = state.phase == BloodHarvestEntity.PHASE_OVERFLOW && state.chest != null;
        float chestX = state.chest == null ? 0.0F : (float) state.chest.x;
        float chestY = state.chest == null ? 0.0F : (float) state.chest.y;
        float chestZ = state.chest == null ? 0.0F : (float) state.chest.z;
        float cubeFlight = BloodHarvestMotion.cubeFlight(timing, state.flight);

        float[] position = new float[3];
        for (int i = 0; i < state.cubes; i++) {
            float edge = VoxelMotion.lodEdge(BloodShapeGeometry.hash01(i * 7919 + 13), keep);
            if (edge <= 0.0F) {
                continue;
            }
            float rank = BloodHarvestMotion.rank(i, state.seed);
            float grow = BloodHarvestMotion.grow(timing, rank, state.age);
            float erosion;
            if (bursting) {
                float progress = Mth.clamp(state.phaseAge / BloodHarvestRules.OVERFLOW_TICKS, 0.0F, 1.0F);
                BloodHarvestMotion.burst(STYLE, i, state.seed, progress, chestX, chestY, chestZ, position);
                erosion = BloodHarvestMotion.bursting(progress);
                grow = 1.0F;
            } else if (flying) {
                BloodHarvestMotion.poolSpot(STYLE, i, state.seed, position);
                float delay = BloodHarvestMotion.delayFor(timing, i, state.seed);
                float progress = BloodHarvestMotion.progress(delay, cubeFlight, state.phaseAge);
                BloodHarvestMotion.fly(STYLE, i, state.seed, progress,
                        position[0], position[1], position[2], chestX, chestY, chestZ, position);
                erosion = BloodHarvestMotion.entering(timing, progress, cubeFlight);
                BloodHarvestMotion.wobble(STYLE, i, state.seed, state.age, grow * (1.0F - progress), position);
            } else {
                BloodHarvestMotion.poolSpot(STYLE, i, state.seed, position);
                erosion = BloodHarvestMotion.dryOut(timing, rank, state.age, state.life);
                BloodHarvestMotion.wobble(STYLE, i, state.seed, state.age, grow, position);
            }
            float half = BloodHarvestMotion.halfExtent(STYLE, i, state.seed, grow, erosion, edge);
            if (half <= 0.0F) {
                continue;
            }
            // 0.7 is where the body shader starts its dissolve, the same mapping the field uses.
            int packed = MagicVertex.withPhase(basePacked, 0.7F + 0.3F * erosion);
            emitter.cube(position[0], position[1], position[2], half, STYLE.rgb(), ALPHA, packed);
        }
        FxBudget.countQuads(emitter.faces());
        super.render(state, poseStack, buffers, packedLight);
    }

    public static final class State extends EntityRenderState {
        float age;
        byte phase;
        float phaseAge;
        int flight;
        int life;
        int cubes;
        int seed;
        Vec3 cameraOffset = Vec3.ZERO;
        double distanceSqr;
        /** The owner's chest relative to the pool, or null while the owner is not tracked. */
        Vec3 chest;
    }
}
