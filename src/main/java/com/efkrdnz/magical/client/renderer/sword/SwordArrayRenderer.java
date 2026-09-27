package com.efkrdnz.magical.client.renderer.sword;

import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.magic.sword.ArrayPose;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.FrameEase;
import com.efkrdnz.magical.magic.sword.stance.Formation;
import com.efkrdnz.magical.magic.sword.stance.Slot;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Array at rest: every sword in formation, and nothing else.
 *
 * <p><b>Both sides run one arithmetic.</b> The swords are placed by {@code Formation.place} and
 * turned into the world by {@link ArrayPose#worldOffset(Slot, Frame)} - the same pure methods the
 * server calls, with the same arguments - so twelve sword positions cost <em>zero bytes</em> on
 * the wire and a sword cannot desync, because there is nothing to desync. All the entity carries
 * is the frame, the stance and which swords are present, and all three change rarely.
 *
 * <p><b>The gaps are the counterplay.</b> A sword that has been spent leaves a <em>hole</em> where
 * it was standing rather than letting its neighbours close ranks - which is what the present mask
 * is for and why it is a mask and not a count - so an opponent counts the swords from thirty
 * blocks and reads how much Loose, Below and Watch is left without the wielder being asked.
 *
 * <p><b>Only what is racked is drawn.</b> Sword <i>j</i> is the <i>j</i>-th weapon in the wielder's
 * rack, so a rack of one is a formation of one, and a sword whose arm arrives empty is not drawn at
 * all - there is no plain steel to stand in for it.
 *
 * <p><b>A held formation is drawn off its wielder, not off this entity.</b> The entity's position
 * reaches the client in steps and a round trip behind the client's own movement, so a formation
 * drawn from it stuttered and trailed round a walking wielder, while one round a wielder turning on
 * the spot - whose facing is walked between ticks - was smooth. The origin is taken off the wielder
 * as they are drawn this frame, through {@code ArrayPose.heldOrigin}, which is the formula the
 * server places the frame with.
 *
 * <p><b>There is nothing else here.</b> The formation used to carry a thread from the wielder's
 * chest to every sword and a red wash that deepened as the count ran down. Both were readings of
 * something the steel already says - where the swords are, and how many of them are left - so both
 * went, and the swords and their gaps are the whole of the picture.
 */
public final class SwordArrayRenderer extends ProfileRendererShell<SwordArrayEntity> {

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
        /** The wielder's pitch, for a BODY-anchored stance. Positive is up, as Formation wants. */
        public double lookElevation;
        /** The argument Formation.place takes for its wave terms. See the note in extract. */
        public double phase;
        /** What each sword flies as: its rack socket's weapon, empty for Duskfall. */
        public final ItemStack[] arms = emptyArms();
        /** From where the entity is drawn to the formation's origin, off the wielder; zero off the body. */
        public double offsetX;
        public double offsetY;
        public double offsetZ;
    }

    private static ItemStack[] emptyArms() {
        ItemStack[] arms = new ItemStack[com.efkrdnz.magical.magic.sword.rack.SwordRack.SIZE];
        java.util.Arrays.fill(arms, ItemStack.EMPTY);
        return arms;
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
        // Positive up, which is the opposite sign to Minecraft's pitch and the convention
        // Formation.place takes. A LOOK stance is pinned to the eye and already carries the
        // wielder's own pitch in the frame, so its elevation must be zero or the aim is applied
        // twice; a BODY stance sits at pitch zero and this is the whole of its aim.
        state.lookElevation = wielder == null || state.stance.anchor() == SwordStance.Anchor.LOOK
                ? 0.0D : -wielder.getViewXRot(partialTick);
        // The level's game time, which is the clock SwordService.phase reads on the other side.
        // An entity's own age starts when its spawn packet lands, so the ring would be drawn at
        // one angle and fired from another, by an offset that was different for every observer.
        state.phase = entity.level().getGameTime() + partialTick;
        for (int sword = 0; sword < state.arms.length; sword++) {
            state.arms[sword] = entity.arm(sword);
        }
        // Where the server will put the origin next, rather than where it last said it was. See
        // the class note: this is the whole of the fix for a formation stuttering at a walk.
        state.offsetX = 0.0D;
        state.offsetY = 0.0D;
        state.offsetZ = 0.0D;
        if (wielder != null && entity.bind().followsTheWielder()) {
            Vec3 feet = wielder.getPosition(partialTick);
            double[] origin = ArrayPose.heldOrigin(state.stance.anchor(), feet.x, feet.y, feet.z,
                    wielder.getEyePosition(partialTick).y);
            state.offsetX = origin[0] - state.origin.x;
            state.offsetY = origin[1] - state.origin.y;
            state.offsetZ = origin[2] - state.origin.z;
        }
    }

    /**
     * The frame reaches {@code MAX_EXTENT * scale} out from its origin, and the origin is the only
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
        pose.pushPose();
        pose.translate(state.offsetX, state.offsetY, state.offsetZ);
        FxContext ctx = new FxContext(pose, buffers, state.partialTick,
                entityRenderDispatcher.cameraOrientation(),
                state.cameraOffset.subtract(state.offsetX, state.offsetY, state.offsetZ))
                .timing(state.age, state.life, state.seed);
        Frame frame = new Frame(0.0D, 0.0D, 0.0D, state.frameYaw, state.framePitch, state.scale);
        for (int index = 0; index < state.slots; index++) {
            ItemStack arm = index < state.arms.length ? state.arms[index] : ItemStack.EMPTY;
            if ((state.mask & (1 << index)) == 0 || arm.isEmpty()) {
                // The gap is the point, and a sword with no weapon is not there. See the class note.
                continue;
            }
            // Both machines read the phase off the level's game time, so a formation that drifts,
            // spins or bobs does it identically everywhere and nobody is told where a sword is.
            Slot slot = Formation.place(state.stance, index, state.slots, state.phase, state.lookElevation);
            double[] offset = ArrayPose.worldOffset(slot, frame);
            double[] facing = ArrayPose.worldDirection(slot, frame);
            pose.pushPose();
            pose.translate(offset[0], offset[1], offset[2]);
            SwordBladeRenderer.blade(ctx, new Vec3(facing[0], facing[1], facing[2]), 0.0F, arm);
            pose.popPose();
        }
        pose.popPose();
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
