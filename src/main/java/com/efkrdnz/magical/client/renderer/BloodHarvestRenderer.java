package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.voxel.BloodBleedMotion;
import com.efkrdnz.magical.client.renderer.fx.voxel.BloodHarvestMotion;
import com.efkrdnz.magical.client.renderer.fx.voxel.VoxelEmitter;
import com.efkrdnz.magical.client.renderer.fx.voxel.VoxelMotion;
import com.efkrdnz.magical.client.renderer.fx.voxel.VoxelStyle;
import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.passive.BloodBleedBirths;
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
 * Draws pooled blood: a pool of cubes on the ground, the drops falling into it out of the body
 * that bleeds, the stream of it into the player, the burst back out when the Vessel overflows,
 * and the vein a walk leaves in the air.
 *
 * <p>The same cubes, colour and render type as Blood Manipulation's field, through the same
 * {@link VoxelEmitter}, out of the same shared voxel budget. What differs is the motion, which is
 * {@link BloodHarvestMotion}'s and {@link BloodBleedMotion}'s, and the targets: the field flies to
 * points it was told, this flies to and from bodies that are read off their interpolated positions
 * every frame. The entity itself never moves - it marks where the blood is - so the owner and the
 * source are the only things re-read to draw a stream that tracks a sprinting player without
 * stutter.
 */
public final class BloodHarvestRenderer extends EntityRenderer<BloodHarvestEntity, BloodHarvestRenderer.State> {

    private static final VoxelStyle STYLE = VoxelStyle.HARVEST;
    private static final int ALPHA = 255;

    /** A battery is set blood: a shade darker than a harvest that still runs. */
    private static final int BATTERY_RGB = 0xC81E2E;

    /** A trace is old blood: dim, so it reads as a mark to step back to rather than a thing to collect. */
    private static final int TRACE_RGB = 0x7A1020;

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
        state.kind = entity.kind();
        state.cubes = Math.min(STYLE.cap(), state.phase == BloodHarvestEntity.PHASE_VEIN
                ? BloodHarvestRules.VEIN_CUBES : BloodHarvestRules.cubes(state.kind, entity.worth()));
        state.seed = entity.seed();
        state.feedTicks = entity.feedTicks();
        state.births = entity.births();

        Vec3 origin = entity.getPosition(partialTick);
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        state.cameraOffset = camera.subtract(origin);
        state.distanceSqr = camera.distanceToSqr(origin);

        Entity owner = entity.owner();
        state.chest = owner == null ? null : chestOf(owner, partialTick).subtract(origin);
        Entity source = entity.fed() ? entity.source() : null;
        state.source = source == null ? null : chestOf(source, partialTick).subtract(origin);
    }

    private static Vec3 chestOf(Entity body, float partialTick) {
        return body.getPosition(partialTick).add(0.0D, body.getBbHeight() * BloodHarvestEntity.CHEST_HEIGHT, 0.0D);
    }

    /**
     * The box is the pool while it pools, plus the owner once it flies and the source while it is
     * fed: the corpse can be behind the camera while the blood is coming straight at it.
     */
    @Override
    public boolean shouldRender(BloodHarvestEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        AABB box = entity.getBoundingBox().inflate(STYLE.burstRadius() + 0.5D);
        Entity owner = entity.owner();
        if (owner != null && entity.phase() != BloodHarvestEntity.PHASE_POOLED) {
            box = box.minmax(owner.getBoundingBox().inflate(STYLE.archHeight() + 1.0D));
        }
        Entity source = entity.fed() ? entity.source() : null;
        if (source != null) {
            box = box.minmax(source.getBoundingBox().inflate(STYLE.archHeight() + 1.0D));
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
        int rgb = switch (state.kind) {
            case BloodHarvestRules.KIND_BATTERY -> BATTERY_RGB;
            case BloodHarvestRules.KIND_TRACE -> TRACE_RGB;
            default -> STYLE.rgb();
        };

        // A stream with no tracked owner is drawn as a pool: better a puddle that lingers a frame
        // than cubes flying at a point nobody can see.
        boolean flying = state.phase == BloodHarvestEntity.PHASE_STREAMING && state.chest != null;
        boolean bursting = state.phase == BloodHarvestEntity.PHASE_OVERFLOW && state.chest != null;
        boolean vein = state.phase == BloodHarvestEntity.PHASE_VEIN && state.chest != null;
        boolean fed = state.phase == BloodHarvestEntity.PHASE_POOLED && state.feedTicks > 0;
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
            } else if (vein) {
                // The body comes apart where it stood - the entity sits at the chest it left - and
                // runs down the vein into wherever the walker now is.
                BloodHarvestMotion.burst(STYLE, i, state.seed, 1.0F, 0.0F, 0.0F, 0.0F, position);
                float delay = BloodHarvestMotion.delayFor(timing, i, state.seed);
                float progress = BloodHarvestMotion.progress(delay, cubeFlight, state.phaseAge);
                BloodHarvestMotion.fly(STYLE, i, state.seed, progress,
                        position[0], position[1], position[2], chestX, chestY, chestZ, position);
                erosion = BloodHarvestMotion.entering(timing, progress, cubeFlight);
                grow = 1.0F;
            } else if (fed) {
                // Drop by drop out of the body that bleeds, each at the bite that shed it. The
                // birth is the client's own memory of the feed, so it never moves once set.
                float bornAt = state.births.bornAt(i);
                if (!BloodBleedMotion.born(bornAt, state.age)) {
                    continue;
                }
                float fall = BloodBleedMotion.fallProgress(bornAt, state.age);
                BloodHarvestMotion.poolSpot(STYLE, i, state.seed, position);
                erosion = 0.0F;
                if (state.source != null && fall < 1.0F) {
                    BloodBleedMotion.fall(STYLE, i, state.seed, fall, position[0], position[1], position[2],
                            (float) state.source.x, (float) state.source.y, (float) state.source.z, position);
                    erosion = BloodBleedMotion.forming(timing, fall, BloodBleedMotion.FALL_TICKS);
                }
                erosion = Math.max(erosion, BloodHarvestMotion.dryOut(timing, rank, state.age, state.life));
                grow = 1.0F;
                BloodHarvestMotion.wobble(STYLE, i, state.seed, state.age, fall, position);
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
            emitter.cube(position[0], position[1], position[2], half, rgb, ALPHA, packed);
        }
        FxBudget.countQuads(emitter.faces());
        super.render(state, poseStack, buffers, packedLight);
    }

    public static final class State extends EntityRenderState {
        float age;
        byte phase;
        byte kind;
        float phaseAge;
        int flight;
        int life;
        int cubes;
        int seed;
        int feedTicks;
        /** When each drop of a fed pool left the body, as this client saw it. */
        BloodBleedBirths births;
        Vec3 cameraOffset = Vec3.ZERO;
        double distanceSqr;
        /** The owner's chest relative to the pool, or null while the owner is not tracked. */
        Vec3 chest;
        /** The chest of the body a fed pool bleeds out of, relative to the pool, or null. */
        Vec3 source;
    }
}
