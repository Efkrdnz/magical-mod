package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
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
 * MAGIC ORIGINATOR (fallen_firmament + glint) - STEER_DARK_ZONE / SKY_LOOK_TRACKED /
 * MOVING_SHADOW_DISC_WITH_BRIGHT_RIM. A black disc hangs over the aim point and follows it: under
 * the shadow enemies are blinded, silenced and pressed; its rim is a blazing annulus that burns
 * anyone crossing it from inside and throws them back in. A moving prison with a burning wall.
 */
public final class TotalEclipseSkill implements SkillModule {
    private static final int WINDUP = 20;
    private static final double HEIGHT = 10.0D;
    private static final double RIM = 1.2D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.TOTAL_ECLIPSE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 ground = ctx.aim().point();
                SpellEffectEntity disc = SpellEffectEntity.spawn(ctx, ground.add(0.0D, HEIGHT, 0.0D), WINDUP + Math.max(60, ctx.duration()), Math.max(4.0F, ctx.size()), new Vec3(0.0D, -1.0D, 0.0D));
                disc.setValue((float) HEIGHT);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 24.0D;
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
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity owner = entity.livingOwner();
            int t = entity.tickCount;
            if (t < WINDUP) {
                return;
            }
            if (t == WINDUP) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                SpellFx.impact(level, entity.definition(), entity.position().subtract(0.0D, HEIGHT, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.0F);
            }
            // glide toward the caster's aim point
            if (owner != null && owner.isAlive()) {
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), 48.0D, 0.0D, true, 16, null);
                Vec3 wanted = aim.point();
                Vec3 to = new Vec3(wanted.x - entity.getX(), 0.0D, wanted.z - entity.getZ());
                double step = Math.max(0.1D, entity.speed());
                if (to.length() > step) {
                    to = to.normalize().scale(step);
                }
                entity.setPos(entity.getX() + to.x, entity.getY(), entity.getZ() + to.z);
            }
            Vec3 ground = AimResolver.groundBelow(level, entity.position(), 24);
            double groundY = ground != null ? ground.y : entity.getY() - HEIGHT;
            entity.setValue((float) (entity.getY() - groundY));
            Vec3 base = new Vec3(entity.getX(), groundY, entity.getZ());
            double radius = entity.radius();
            CompoundTag data = entity.serverData();
            for (LivingEntity hostile : SkillTargets.hostilesInCylinder(level, owner, base.subtract(0.0D, 1.0D, 0.0D), radius + RIM + 1.0D, HEIGHT + 2.0D)) {
                double dist = Math.sqrt(Math.pow(hostile.getX() - base.x, 2.0D) + Math.pow(hostile.getZ() - base.z, 2.0D));
                String key = "in_" + hostile.getId();
                boolean wasInside = data.getBoolean(key);
                boolean inside = dist <= radius;
                data.putBoolean(key, inside);
                if (inside) {
                    MagicStatusService.apply(hostile, MagicStatus.DAZZLED, 12, entity.definition().id(), owner);
                    MagicStatusService.apply(hostile, MagicStatus.SILENCED, 12, entity.definition().id(), owner);
                    Vec3 v = hostile.getDeltaMovement();
                    if (v.y > 0.0D) {
                        hostile.setDeltaMovement(v.x, 0.0D, v.z);
                        hostile.hurtMarked = true;
                    }
                    if (t % 10 == 0) {
                        SkillTargets.hurt(level, owner, hostile, 6.0F, entity.definition().id());
                    }
                } else if (wasInside && dist <= radius + RIM + 1.0D) {
                    // crossed the burning rim from inside: burned and thrown back in
                    SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition(), true);
                    SkillTargets.shove(hostile, base, -0.9D * Math.max(0.5D, entity.knockback()), 0.2D);
                    data.putBoolean(key, true);
                    SpellFx.barrierHit(level, entity.definition(), hostile.getBoundingBox().getCenter(), hostile.position().subtract(base).normalize());
                }
            }
            if (t % 20 == 0) {
                SpellFx.zoneTick(level, entity.definition(), base, (float) radius * 0.5F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.ECLIPSE).frame(16).band(GlyphKind.SOLID_RING, 1, ColorRole.INK).band(GlyphKind.TICK_BAND, 72, ColorRole.DIM).band(GlyphKind.PETAL_BAND, 12, ColorRole.HOT).stamps(StampId.CRESCENT, 12).orbit(7, 0.86F, 4).core(CoreKind.VOID_PIT).stack(3, 0.6F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.SKY)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("total_eclipse", 9.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.VIGNETTE)
                .budget(3)
                .bounds(12.0F, 3.0F, 14.0F);
    }
}
