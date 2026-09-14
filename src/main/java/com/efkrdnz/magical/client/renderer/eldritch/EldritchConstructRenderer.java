package com.efkrdnz.magical.client.renderer.eldritch;

import com.efkrdnz.magical.client.model.eldritch.EldritchModels;
import com.efkrdnz.magical.client.model.geo.GeoModelBaker;
import com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell;
import com.efkrdnz.magical.entity.fx.EldritchConstructEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a construct: the profile's FX through the shell, then the creature.
 *
 * <p>The frame is the vanilla entity frame (face the yaw, flip into model space) with no vertical
 * offset, so the geometry's ground is the entity's feet. The pose is written into the bones by
 * name from {@link EldritchPose}; a bone the model does not have is skipped. The body is drawn
 * translucent with the lifecycle alpha, then the glow layer full bright if the model has one.
 */
public final class EldritchConstructRenderer extends ProfileRendererShell<EldritchConstructEntity> {
    private static final float SEGMENT_LENGTH = 4.0F;
    private static final float FORM_TICKS = 12.0F;
    private static final float DISSOLVE_TICKS = 12.0F;
    private static final float GAZE_LAG = 0.15F;
    private static final float TENTACLE_SWAY = 0.35F;
    /** How far the wards on a back sit from the spine, in blocks. */
    private static final double WARD_BACK = 0.3D;
    private static final float WARD_SPREAD_DEGREES = 28.0F;
    /** Where Tendril Lash grows from: the right shoulder. */
    private static final double SHOULDER_SIDE = 0.4D;
    private static final double SHOULDER_HEIGHT = 1.3D;
    private static final String[] SEGMENT_BONES = {"seg0", "seg1", "seg2", "seg3", "seg4", "seg5"};

    public EldritchConstructRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends ProfileRendererShell.State {
        public String model = "";
        public byte anchor;
        public float scale = 1.0F;
        public int extra;
        public float yaw;
        public float ownerYaw;
        public Vec3 ownerOffset = Vec3.ZERO;
        public Vec3 target;
        public boolean hiddenFromWearer;
        public float gazeYaw;
        public float gazePitch;
    }

    @Override
    public ProfileRendererShell.State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EldritchConstructEntity entity, ProfileRendererShell.State base, float partialTick) {
        super.extractRenderState(entity, base, partialTick);
        State state = (State) base;
        state.model = entity.model();
        state.anchor = entity.anchor();
        state.scale = entity.scale();
        state.extra = entity.extra();
        Vec3 pos = entity.getPosition(partialTick);
        Entity owner = entity.owner();
        Minecraft minecraft = Minecraft.getInstance();
        // Only the wards on the back hide from their wearer: a lash from the shoulder is meant to be seen.
        state.hiddenFromWearer = state.anchor == EldritchConstructEntity.ANCHOR_OWNER && owner == minecraft.player
                && EldritchConstructEntity.MODEL_TENTACLE.equals(state.model) && state.extra > 0
                && minecraft.options.getCameraType().isFirstPerson();
        // Follow the owner between ticks, keeping whatever height the construct holds above them.
        state.ownerOffset = owner != null ? owner.getPosition(partialTick).add(0.0D, pos.y - owner.getY(), 0.0D).subtract(pos) : Vec3.ZERO;
        state.ownerYaw = owner instanceof LivingEntity living ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : 0.0F;
        Entity target = entity.target();
        Vec3 anchorPos = state.anchor == EldritchConstructEntity.ANCHOR_OWNER ? pos.add(state.ownerOffset) : pos;
        state.target = target != null ? target.getBoundingBox().getCenter().subtract(anchorPos) : null;
        Vec3 dir = entity.direction();
        float dirYaw = dir.lengthSqr() > 1.0E-6D ? (float) Math.toDegrees(Mth.atan2(-dir.x, dir.z)) : 0.0F;
        float wantedYaw = state.target != null ? (float) Math.toDegrees(Mth.atan2(-state.target.x, state.target.z)) : dirYaw;
        if (state.anchor == EldritchConstructEntity.ANCHOR_OWNER && !EldritchConstructEntity.MODEL_EYE.equals(state.model)) {
            wantedYaw = state.ownerYaw;
        }
        float wantedPitch = state.target != null ? (float) Math.atan2(state.target.y, Math.hypot(state.target.x, state.target.z)) : 0.0F;
        entity.gazeYaw = (float) Math.toDegrees(EldritchPose.ease((float) Math.toRadians(entity.gazeYaw), (float) Math.toRadians(wantedYaw), GAZE_LAG));
        entity.gazePitch = EldritchPose.ease(entity.gazePitch, wantedPitch, GAZE_LAG);
        state.gazeYaw = entity.gazeYaw;
        state.gazePitch = entity.gazePitch;
        state.yaw = EldritchConstructEntity.MODEL_EYE.equals(state.model) ? state.gazeYaw : wantedYaw;
    }

    @Override
    public void render(ProfileRendererShell.State base, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        State state = (State) base;
        if (state.hiddenFromWearer) {
            return;
        }
        super.render(base, pose, buffers, packedLight);
        EldritchModels.Entry entry = EldritchModels.get(state.model);
        if (entry == null) {
            return;
        }
        float alpha = EldritchPose.dissolve(state.age, state.life, DISSOLVE_TICKS);
        if (alpha <= 0.0F) {
            return;
        }
        pose.pushPose();
        if (state.anchor == EldritchConstructEntity.ANCHOR_OWNER) {
            pose.translate(state.ownerOffset.x, state.ownerOffset.y, state.ownerOffset.z);
        }
        if (EldritchConstructEntity.MODEL_TENTACLE.equals(state.model) && state.anchor == EldritchConstructEntity.ANCHOR_OWNER && state.extra > 0) {
            wards(state, entry, pose, buffers, packedLight, alpha);
        } else if (EldritchConstructEntity.MODEL_TENTACLE.equals(state.model) && state.anchor == EldritchConstructEntity.ANCHOR_OWNER) {
            // Tendril Lash: from the right shoulder, whipping ahead.
            double facing = Math.toRadians(state.ownerYaw);
            pose.translate(-Math.cos(facing) * SHOULDER_SIDE, SHOULDER_HEIGHT, -Math.sin(facing) * SHOULDER_SIDE);
            creature(state, entry, pose, buffers, packedLight, alpha, state.yaw, state.scale, 0);
        } else {
            creature(state, entry, pose, buffers, packedLight, alpha, state.yaw, state.scale, 0);
        }
        pose.popPose();
    }

    /** Skin of the Deep: {@code extra} small tentacles fanned across the back. */
    private void wards(State state, EldritchModels.Entry entry, PoseStack pose, MultiBufferSource buffers, int packedLight, float alpha) {
        int count = Math.min(state.extra, 8);
        for (int i = 0; i < count; i++) {
            float spread = (i - (count - 1) / 2.0F) * WARD_SPREAD_DEGREES;
            pose.pushPose();
            double back = Math.toRadians(state.ownerYaw);
            // Behind the owner: their facing is (-sin yaw, cos yaw), so the back is the opposite.
            pose.translate(Math.sin(back) * WARD_BACK + Math.cos(back) * Math.sin(Math.toRadians(spread)) * WARD_BACK,
                    0.9D, -Math.cos(back) * WARD_BACK + Math.sin(back) * Math.sin(Math.toRadians(spread)) * WARD_BACK);
            creature(state, entry, pose, buffers, packedLight, alpha, state.ownerYaw + 180.0F + spread, state.scale, i * 11);
            pose.popPose();
        }
    }

    private void creature(State state, EldritchModels.Entry entry, PoseStack pose, MultiBufferSource buffers, int packedLight,
            float alpha, float yaw, float scale, int seedOffset) {
        GeoModelBaker.Baked baked = entry.baked();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-scale, -scale, scale);
        posture(state, baked, seedOffset);
        int color = ((int) (alpha * 255.0F) << 24) | 0xFFFFFF;
        baked.root().render(pose, buffers.getBuffer(RenderType.entityTranslucent(entry.texture())), packedLight, OverlayTexture.NO_OVERLAY, color);
        if (entry.glow() != null) {
            baked.root().render(pose, buffers.getBuffer(RenderType.eyes(entry.glow())), packedLight, OverlayTexture.NO_OVERLAY, color);
        }
        pose.popPose();
    }

    private static void posture(State state, GeoModelBaker.Baked baked, int seedOffset) {
        float age = state.age + seedOffset;
        switch (state.model) {
            case EldritchConstructEntity.MODEL_TENTACLE -> tentacle(state, baked, age);
            case EldritchConstructEntity.MODEL_EYE -> eye(state, baked, age);
            case EldritchConstructEntity.MODEL_MAW -> maw(state, baked);
            default -> { }
        }
    }

    private static void tentacle(State state, GeoModelBaker.Baked baked, float age) {
        float[] pitch = new float[EldritchPose.SEGMENTS];
        boolean hasTarget = state.target != null && state.anchor != EldritchConstructEntity.ANCHOR_OWNER;
        float forward = 0.0F;
        float up = 0.0F;
        if (hasTarget) {
            // Into chain space: blocks to sixteenths, the horizontal distance along the front, the height up.
            forward = (float) Math.hypot(state.target.x, state.target.z) * 16.0F / state.scale;
            up = (float) state.target.y * 16.0F / state.scale;
        }
        EldritchPose.chain(EldritchPose.SEGMENTS, SEGMENT_LENGTH, forward, up, hasTarget, age, TENTACLE_SWAY, pitch);
        float grown = EldritchPose.grown(state.age, FORM_TICKS, EldritchPose.SEGMENTS);
        float lash = lashSweep(state);
        for (int i = 0; i < SEGMENT_BONES.length; i++) {
            ModelPart segment = baked.part(SEGMENT_BONES[i]);
            if (segment == null) {
                continue;
            }
            PartPose rest = segment.getInitialPose();
            segment.xRot = rest.xRot() + pitch[i] + (i == 0 ? lash : 0.0F);
            segment.yRot = rest.yRot();
            segment.zRot = rest.zRot();
            float s = EldritchPose.segmentScale(grown, i);
            segment.xScale = s;
            segment.yScale = s;
            segment.zScale = s;
        }
    }

    /** Tendril Lash: the base bends through a forward arc over the whip ticks the skill synced. */
    private static float lashSweep(State state) {
        if (state.data == null || !state.data.contains("whip")) {
            return 0.0F;
        }
        float whip = Math.max(1.0F, state.data.getInt("whip"));
        float t = Math.max(0.0F, Math.min(1.0F, state.age / whip));
        return 1.3F * (float) Math.sin(t * Math.PI);
    }

    private static void eye(State state, GeoModelBaker.Baked baked, float age) {
        ModelPart body = baked.part("body");
        if (body != null) {
            PartPose rest = body.getInitialPose();
            body.xRot = rest.xRot() - state.gazePitch;
            body.yRot = rest.yRot();
        }
        ModelPart pupil = baked.part("pupil");
        if (pupil != null) {
            float s = EldritchPose.pupil(state.target != null, age);
            pupil.xScale = s;
            pupil.yScale = s;
        }
        // Lids, if the model has them: open over the form ticks, shut again through the dissolve.
        float open = Math.min(EldritchPose.grown(state.age, FORM_TICKS, 1), EldritchPose.dissolve(state.age, state.life, DISSOLVE_TICKS));
        for (String name : new String[] {"lid_upper", "lid_lower"}) {
            ModelPart lid = baked.part(name);
            if (lid != null) {
                lid.xRot = lid.getInitialPose().xRot() * open;
            }
        }
    }

    private static void maw(State state, GeoModelBaker.Baked baked) {
        float windup = state.data != null && state.data.contains("windup") ? state.data.getInt("windup") : 20.0F;
        float snapAt = state.data != null && state.data.contains("snap") ? state.data.getInt("snap") : 0.0F;
        float gape = EldritchPose.jaws(state.age, windup, snapAt) * EldritchPose.MAX_GAPE;
        ModelPart upper = baked.part("jaw_upper");
        if (upper != null) {
            upper.xRot = upper.getInitialPose().xRot() - gape;
        }
        ModelPart lower = baked.part("jaw_lower");
        if (lower != null) {
            lower.xRot = lower.getInitialPose().xRot() + gape;
        }
    }
}
