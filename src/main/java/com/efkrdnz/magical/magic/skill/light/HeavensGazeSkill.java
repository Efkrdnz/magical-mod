package com.efkrdnz.magical.magic.skill.light;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusData;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * LIGHT T4 - STEER / SKY_EYE_LOOK_TRACKED / MOVING_DISC_WITH_CONE. A colossal slit eye opens above
 * the caster and pours a searchlight whose spot glides toward wherever the caster looks; anything
 * in the light is hurt and accumulates gaze until it is smitten. The caster moves freely.
 */
public final class HeavensGazeSkill implements SkillModule {
    private static final int WINDUP = 20;
    private static final int CLOSE = 16;
    private static final double EYE_HEIGHT = 16.0D;
    private static final int GAZE_SMITE = 30;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HEAVENS_GAZE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                int active = Math.max(60, ctx.duration());
                Vec3 eye = ctx.feet().add(0.0D, EYE_HEIGHT, 0.0D);
                SpellEffectEntity gaze = SpellEffectEntity.spawn(ctx, eye, WINDUP + active + CLOSE, Math.max(1.5F, ctx.size()), new Vec3(0.0D, -1.0D, 0.0D));
                gaze.setExtra(active);
                Vec3 spot = ctx.aim().point();
                CompoundTag data = new CompoundTag();
                data.putDouble("SX", spot.x);
                data.putDouble("SY", spot.y);
                data.putDouble("SZ", spot.z);
                gaze.setSyncedData(data);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 32.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }

            @Override
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(4.0F, 24.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity owner = entity.livingOwner();
            if (owner == null || !owner.isAlive()) {
                entity.finish();
                return;
            }
            entity.setPos(owner.getX(), Math.min(level.getMaxY() - 2, owner.getY() + EYE_HEIGHT), owner.getZ());
            if (entity.tickCount < WINDUP) {
                return;
            }
            if (entity.tickCount == WINDUP) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }
            int active = Math.max(60, entity.extra());
            if (entity.tickCount >= WINDUP + active) {
                entity.setPhase(SpellEffectEntity.PHASE_CLOSING);
                return;
            }
            // the spot glides toward the caster's aim point
            CompoundTag data = entity.syncedData();
            Vec3 spot = new Vec3(data.getDouble("SX"), data.getDouble("SY"), data.getDouble("SZ"));
            AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), 32.0D, 0.0D, true, 12, null);
            Vec3 goal = aim.point();
            Vec3 to = goal.subtract(spot);
            double glide = Math.max(0.1D, entity.speed());
            if (to.length() > glide) {
                spot = spot.add(to.normalize().scale(glide));
            } else {
                spot = goal;
            }
            Vec3 ground = AimResolver.groundBelow(level, spot.add(0.0D, 1.0D, 0.0D), 6);
            if (ground != null) {
                spot = new Vec3(spot.x, ground.y, spot.z);
            }
            CompoundTag next = new CompoundTag();
            next.putDouble("SX", spot.x);
            next.putDouble("SY", spot.y);
            next.putDouble("SZ", spot.z);
            entity.setSyncedData(next);
            entity.setDirection(spot.subtract(entity.position()).normalize());
            if (entity.tickCount % 10 != 0) {
                return;
            }
            for (LivingEntity hostile : SkillTargets.hostilesInCylinder(level, owner, spot, entity.radius(), 3.0D)) {
                SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition().id());
                MagicStatusData.Entry gaze = MagicStatusService.entry(hostile, MagicStatus.GAZE);
                int total = (gaze != null ? gaze.amplifier() : 0) + 10;
                if (total >= GAZE_SMITE) {
                    MagicStatusService.clear(hostile, MagicStatus.GAZE);
                    SkillTargets.hurt(level, owner, hostile, entity.damage() * 3.0F, entity.definition(), true);
                    hostile.setDeltaMovement(hostile.getDeltaMovement().add(0.0D, Math.max(0.3D, entity.knockback()), 0.0D));
                    hostile.hurtMarked = true;
                } else {
                    MagicStatusService.apply(hostile, MagicStatus.GAZE, 40, total, 0.0F, entity.definition().id(), owner);
                }
            }
            SpellFx.zoneTick(level, entity.definition(), spot, entity.radius());
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.EYE).frame(12).band(GlyphKind.TOOTH_BAND, 24).band(GlyphKind.CHAIN_BAND, 12, com.efkrdnz.magical.magic.visual.ColorRole.BASE).band(GlyphKind.RUNE_BAND, 24, com.efkrdnz.magical.magic.visual.ColorRole.DIM).stamps(StampId.EYE, 6).spokes(12, 0.3F, false).orbit(7, 0.9F, 6).core(CoreKind.IRIS).stack(3, 0.6F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.GROUND)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("heavens_gaze", 26.0F))
                .silhouette(Silhouette.rift(Silhouette.Form.HORIZONTAL_SLIT_EYE, FxKinds.Rift.HORIZONTAL_SLIT_EYE, 2.2F, 4.5F, FxKinds.RiftInterior.WHITE_LIGHT))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.IRIS_CLOSE)
                .budget(3)
                .bounds(40.0F, 6.0F, 22.0F);
    }
}
