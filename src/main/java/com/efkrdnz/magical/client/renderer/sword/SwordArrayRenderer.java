package com.efkrdnz.magical.client.renderer.sword;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.magic.sword.ArrayPose;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.FrameEase;
import com.efkrdnz.magical.magic.sword.stance.Formation;
import com.efkrdnz.magical.magic.sword.stance.Slot;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Array at rest: every manned station's blade, and the threads that make the resource public.
 *
 * <p><b>Both sides run one arithmetic.</b> The swords are placed by {@code Formation.place} and
 * turned into the world by {@link ArrayPose#worldOffset(Slot, Frame)} - the same pure methods the
 * server calls, with the same arguments - so twelve sword positions cost <em>zero bytes</em> on
 * the wire and a sword cannot desync, because there is nothing to desync. All the entity carries
 * is the frame, the stance and which swords are present, and all three change rarely. It is also
 * why the fusion needs no case here at all: One Blade drives {@code frame.scale} to zero and
 * {@code worldOffset} puts every sword on the origin, which is the convergence, drawn by the same
 * line that draws the formation.
 *
 * <p><b>The gaps are the counterplay.</b> A sword that has been spent leaves a <em>hole</em> where
 * it was standing rather than letting its neighbours close ranks - which is what the present mask
 * is for and why it is a mask and not a count - so an opponent counts the swords from thirty
 * blocks and reads how much Loose, Below and Watch is left without the wielder being asked.
 *
 * <p><b>The threads say where the origin is.</b> One filament from the wielder's chest to every
 * present sword, which for every bind but {@code HELD} is a line across open ground to wherever
 * they left their formation. They are deliberately not billboarded: the only thing a thread has
 * to say is its direction, and a ribbon turned to face the camera has none.
 *
 * <p><b>In first person the chest is not a point in the scene - it is the camera.</b> The eye
 * stands at 1.62 and the chest at half of a 1.8-block body, so the near end of every thread is
 * 0.72 blocks dead below the viewer with <em>exactly zero</em> horizontal offset. Two things
 * follow, and the first capture of the Array showed both. Every thread lies in a vertical plane
 * that contains the eye, and a plane through the eye projects to a straight line - a vertical
 * one, at zero roll - so all twelve come out as identical vertical bars whatever their bearing
 * and none of them reads as a direction. And the near end, sitting on the camera plane itself, is
 * sent off the bottom of the frame by the perspective divide instead of stopping at the wielder:
 * a two-block tell drawn as a full-height streak. The third-person camera meets the same thing
 * from the other side, standing about four blocks back, which is <em>inside</em> a reach-four
 * Array, so the threads to the stations behind the wielder run through it.
 *
 * <p>{@link ThreadGeometry} is the answer to both, and it moves one end only. The far end is the
 * station's own {@code worldOffset} and stays there, because a thread that stopped short of its
 * blade or ran past it would be a line pointing at nothing; the start is walked out along the run
 * until the whole of what is drawn is clear of the camera. Nobody but the person the camera is
 * standing on loses a pixel of it.
 */
public final class SwordArrayRenderer extends ProfileRendererShell<SwordArrayEntity> {

    /**
     * Cinnabar, the mod's one "you are running out" colour.
     *
     * <p>It used to be strain, which no longer exists. It is the <em>spend</em> now, and that is
     * the better reading of the same channel: a formation with gaps in it is a wielder who has
     * fired, and the redder it is the less they have left. Kept public because
     * {@code SwordSilhouetteTest} names it.
     */
    public static final int STRAIN_RED = 0xD4402F;

    /** What a thread shows of itself at full strength, so a whole formation still has lines. */
    private static final float THREAD_FLOOR = 0.10F;

    /** What the rest of the reading is worth. Floor plus this is a thread at full brightness. */
    private static final float THREAD_LOAD = 0.75F;

    /** How thin a thread is drawn. It is a thread and not a beam; the blade is the object. */
    private static final float THREAD_HALF_WIDTH = 0.012F;

    /** Every sword is one sword, so every blade is drawn at the weight one blade is drawn at. */
    private static final int BLADE_EDGE = 1;

    public SwordArrayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends ProfileRendererShell.State {
        /** Bit <i>i</i> set means sword <i>i</i> is in formation. A gap is a sword that is away. */
        public int mask;
        /** How many slots the formation is laid out for, which is not how many are drawn. */
        public int slots;
        public SwordStance stance = SwordStance.first();
        public float scale = 1.0F;
        public float frameYaw;
        public float framePitch;
        /** The wielder's chest relative to the frame origin, or null while they are not loaded. */
        public Vec3 chest;
        /** The wielder's pitch, for a BODY-anchored stance. Positive is up, as Formation wants. */
        public double lookElevation;
        /** The argument Formation.place takes for its wave terms. See the note in render. */
        public double phase;
    }

    @Override
    public ProfileRendererShell.State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SwordArrayEntity entity, ProfileRendererShell.State base, float partialTick) {
        super.extractRenderState(entity, base, partialTick);
        State state = (State) base;
        // The radius a formation carries is the frame's gameplay reach, not the size of its
        // drawing, and ProfileRendererShell inflates MARK/SWARM/FIELD-dome silhouettes to whatever
        // radius it is handed - which would be a sphere the size of the Array round the wielder.
        // EldritchConstructRenderer zeroes it in the same place for the same reason.
        state.radius = 0.0F;
        state.mask = entity.presentMask();
        state.slots = entity.slotCount();
        state.stance = entity.stance();
        state.scale = Math.max(0.0F, entity.value());
        // Walked between the last two facings the server sent rather than taken raw. The server
        // eases the frame's rotation over a half-life, but it still only says so twenty times a
        // second, and a formation that steps twenty times a second is the thing that read as a
        // prop bolted to the camera. Angles rather than the vector: two facings a long way apart
        // lerp through the middle of the sphere, and the normalise of something near zero is a
        // NaN that takes the whole formation off the screen for a frame.
        state.frameYaw = FrameEase.lerpAngle(frameYaw(entity.lastFacing()),
                frameYaw(entity.facingAt(1.0F)), partialTick);
        state.framePitch = FrameEase.lerpAngle(framePitch(entity.lastFacing()),
                framePitch(entity.facingAt(1.0F)), partialTick);
        LivingEntity wielder = entity.livingTarget();
        Vec3 origin = entity.getPosition(partialTick);
        state.chest = wielder == null ? null
                : wielder.getPosition(partialTick).add(0.0D, wielder.getBbHeight() * 0.5D, 0.0D).subtract(origin);
        // Positive up, which is the opposite sign to Minecraft's pitch and the convention
        // Formation.place takes. A LOOK stance is pinned to the eye and already carries the
        // wielder's own pitch in the frame, so its elevation must be zero or the aim is applied
        // twice; a BODY stance sits at pitch zero and this is the whole of its aim.
        state.lookElevation = wielder == null || state.stance.anchor() == SwordStance.Anchor.LOOK
                ? 0.0D : -wielder.getViewXRot(partialTick);
        // The level's game time, which is the clock SwordService.phase reads on the other side.
        // It used to be the entity's own age here, and that is a different clock: an age starts
        // when the spawn packet lands, so the ring was drawn at one angle and fired from another,
        // by a constant offset that was different for every observer. SwordService.phase's own
        // note says an age would not do; the renderer was the one place using one.
        state.phase = entity.level().getGameTime() + partialTick;
    }

    /**
     * The frame reaches {@code REACH_MAX * scale} out from its origin, and the origin is the only
     * thing the entity's own box knows about. Without this a formation whose centre has just left
     * the frustum takes every blade with it while half of them are still on screen.
     */
    @Override
    public boolean shouldRender(SwordArrayEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        double reach = Formation.MAX_EXTENT * Math.max(1.0D, entity.value())
                + SwordBladeRenderer.Geometry.LENGTH;
        AABB box = entity.getBoundingBox().inflate(reach);
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(cameraX, cameraY, cameraZ))
                && frustum.isVisible(box);
    }

    @Override
    public void render(ProfileRendererShell.State base, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        super.render(base, pose, buffers, packedLight);
        State state = (State) base;
        if (state.mask == 0 || state.slots <= 0) {
            return;
        }
        FxContext ctx = new FxContext(pose, buffers, state.partialTick,
                entityRenderDispatcher.cameraOrientation(), state.cameraOffset)
                .timing(state.age, state.life, state.seed);
        ctx.detail = FxBudget.detailForDistance(3, state.distanceSqr);
        ctx.lod = FxBudget.lodForDistance(state.distanceSqr);

        Frame frame = new Frame(0.0D, 0.0D, 0.0D, state.frameYaw, state.framePitch, state.scale);
        // Both machines read this off the level's game time, so a formation that drifts, spins
        // or bobs does it identically everywhere and nobody is ever told where a sword is.
        double phase = state.phase;
        float heat = heat(state);
        // Beyond the far LOD band the threads go before the steel does: the steel is what the
        // picture is of, and a thread at fifty blocks is a pixel either way.
        boolean near = ctx.lod > 0.0F;

        for (int index = 0; index < state.slots; index++) {
            if ((state.mask & (1 << index)) == 0) {
                // The gap is the point. See the class note.
                continue;
            }
            Slot slot = Formation.place(state.stance, index, state.slots, phase, state.lookElevation);
            sword(ctx, pose, frame, slot, heat);
            if (near && state.chest != null) {
                thread(ctx, pose, frame, slot, state.chest, ctx.cameraPos, heat);
            }
        }
    }

    /** One sword, standing where the stance puts it and facing the way the stance points it. */
    private static void sword(FxContext ctx, PoseStack pose, Frame frame, Slot slot, float heat) {
        double[] offset = ArrayPose.worldOffset(slot, frame);
        double[] facing = ArrayPose.worldDirection(slot, frame);
        pose.pushPose();
        pose.translate(offset[0], offset[1], offset[2]);
        SwordBladeRenderer.blade(ctx, new Vec3(facing[0], facing[1], facing[2]), BLADE_EDGE,
                1.0F, 0.0F, tint(BLADE_EDGE, heat));
        pose.popPose();
    }

    /**
     * One thread from the wielder's chest out to a blade, less whatever of it is in the camera's
     * lap.
     *
     * <p>Drawn from the chest rather than from the frame origin, because for every bind but
     * {@code HELD} those are not the same point - a set Array's threads run out of the wielder and
     * across to wherever they left it, which is precisely the reading a set Array owes an
     * opponent.
     *
     * <p>{@code camera} is the viewer in the same frame as everything else here, the effect
     * origin: {@code FxContext.cameraPos} already is that, so no world position is rebuilt and
     * the trim costs one quadratic. The painter's half is as it was and the two things it rests
     * on are worth saying out loud, because both are the kind of assumption that draws a line to
     * the horizon when it is wrong - {@code orientAlong} normalises the direction it is handed,
     * and {@code beam} runs its tube from the local origin out to a <em>whole</em> length along
     * local +Z. So translating to the start and handing it the remaining run puts the tip exactly
     * on the blade. The shader fades the first tenth of whatever tube it gets, which is why a
     * trimmed thread swims into view rather than beginning on a cut end.
     */
    private static void thread(FxContext ctx, PoseStack pose, Frame frame, Slot slot, Vec3 chest,
            Vec3 camera, float heat) {
        double[] offset = ArrayPose.worldOffset(slot, frame);
        double from = ThreadGeometry.start(chest.x, chest.y, chest.z,
                offset[0], offset[1], offset[2], camera.x, camera.y, camera.z);
        if (from >= 1.0D) {
            return;
        }
        Vec3 run = new Vec3(offset[0], offset[1], offset[2]).subtract(chest);
        double length = run.length() * (1.0D - from);
        if (length < 1.0E-3D) {
            return;
        }
        Vec3 start = chest.add(run.scale(from));
        pose.pushPose();
        pose.translate(start.x, start.y, start.z);
        FilamentPainter.orientAlong(pose, run);
        FilamentPainter.beam(ctx, FxKinds.Filament.THREAD_KNOTS, THREAD_HALF_WIDTH, (float) length,
                SwordBladeRenderer.lerpRgb(0xB9C4CE, STRAIN_RED, heat),
                THREAD_FLOOR + THREAD_LOAD * (1.0F - heat), SwordBladeRenderer.BEAM_WHOLE, 3, 4);
        pose.popPose();
    }

    /**
     * How much of a thread the camera is standing on, and nothing else about it.
     *
     * <p>A nested class of plain doubles for the reason {@code SwordBladeRenderer.Geometry} is
     * one: measuring what a thread draws must never drag {@code EntityRenderer} into a unit test.
     * {@code SwordThreadTest} replays it through the real {@code FilamentPainter.orientAlong} and
     * the real tube mesh, so the far end is measured rather than argued about.
     */
    public static final class ThreadGeometry {

        /**
         * How near the camera any part of a thread may be drawn, in blocks.
         *
         * <p>Twice the 0.72 the first-person eye stands above the chest: the smallest clearance
         * that puts the start of a thread outside the wielder's own head at every pitch a frame
         * can take. It still leaves 56% of a level reach-3 run drawn at scale 1 and more of
         * everything longer - a tick of light under the blade rather than a bar through the frame
         * - and an opponent reading the Array from across the arena is nowhere near it and loses
         * nothing at all.
         */
        public static final double NEAR_CLEAR = 1.5D;

        private ThreadGeometry() {
        }

        /**
         * The fraction of the run at which the thread may start, so that no drawn point of it
         * comes within {@link #NEAR_CLEAR} of the camera.
         *
         * <p>Line against sphere, and the answer is the <em>far</em> root: the part of the run
         * worth drawing is the part beyond the camera, never the stub in front of it. A returned
         * {@code 1.0} means the blade itself is inside the clearance, so there is no such part and
         * the thread is not drawn at all - which is what stops a station behind the wielder being
         * threaded straight through a third-person camera sitting on top of it.
         */
        public static double start(double chestX, double chestY, double chestZ,
                double bladeX, double bladeY, double bladeZ,
                double cameraX, double cameraY, double cameraZ) {
            double dx = bladeX - chestX;
            double dy = bladeY - chestY;
            double dz = bladeZ - chestZ;
            double a = dx * dx + dy * dy + dz * dz;
            if (a < 1.0E-12D) {
                // The fusion: One Blade drives the frame scale to zero and puts every station on
                // the origin, and a run with no length has no start to find.
                return 1.0D;
            }
            double mx = chestX - cameraX;
            double my = chestY - cameraY;
            double mz = chestZ - cameraZ;
            double b = 2.0D * (mx * dx + my * dy + mz * dz);
            double c = mx * mx + my * my + mz * mz - NEAR_CLEAR * NEAR_CLEAR;
            double discriminant = b * b - 4.0D * a * c;
            if (discriminant <= 0.0D) {
                // The line of the run misses the clearance altogether, which is every thread
                // anybody but the wielder will ever see. Nothing comes off it.
                return 0.0D;
            }
            double root = Math.sqrt(discriminant);
            double enter = (-b - root) / (2.0D * a);
            double leave = (-b + root) / (2.0D * a);
            if (leave <= 0.0D || enter >= 1.0D) {
                // The clearance sits off one end of the run - behind the chest, or past the blade.
                // The segment actually drawn never enters it, so the whole run stands.
                return 0.0D;
            }
            if (leave >= 1.0D) {
                return 1.0D;
            }
            return leave;
        }
    }

    // ---- the readings ---------------------------------------------------------------------------

    /**
     * How far toward cinnabar the formation has gone, which is how much of it has been spent.
     *
     * <p>Read off the mask rather than synced, and that is the whole of it: the present count and
     * the slot count are both properties of one bitfield the entity was already sending, so a
     * reading that used to need its own integer on the wire now needs nothing at all. A whole
     * formation is steel; every gap pulls it toward red; nothing left is red.
     */
    public static float heat(State state) {
        if (state.slots <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F,
                1.0F - Integer.bitCount(state.mask) / (float) state.slots));
    }

    /** A blade's own weight on the palette ramp, pulled toward cinnabar by what has been spent. */
    public static int tint(int edge, float heat) {
        return heat <= 0.0F ? SwordBladeRenderer.edgeColor(edge)
                : SwordBladeRenderer.lerpRgb(SwordBladeRenderer.edgeColor(edge), STRAIN_RED, heat);
    }

    // ---- the frame's facing, read back off the wire ----------------------------------------------

    /**
     * The yaw the server wrote, recovered from the unit vector it wrote it as.
     *
     * <p>{@code Vec3.directionFromRotation} lays a look vector out as
     * {@code (-sin yaw cos pitch, -sin pitch, cos yaw cos pitch)}, so this is its inverse and
     * nothing more. It has to be exact: {@link ArrayPose} runs on the frame's degrees on both
     * sides, and a sign here is a silent mirror with a green build and no log line.
     */
    public static float frameYaw(Vec3 facing) {
        if (facing.lengthSqr() < 1.0E-6D) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
    }

    /** The same, for the pitch, which Minecraft counts positive downward. */
    public static float framePitch(Vec3 facing) {
        if (facing.lengthSqr() < 1.0E-6D) {
            return 0.0F;
        }
        Vec3 unit = facing.normalize();
        return (float) -Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, unit.y))));
    }
}
