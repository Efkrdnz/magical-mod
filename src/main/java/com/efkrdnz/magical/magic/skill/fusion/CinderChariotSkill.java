package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.RollingBodyEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
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
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SPELL CREATOR (slag_roller + puppet_sigil) - RIDE / SELF_MOUNT / STEERED_ROLLING_WHEEL. A molten
 * spoked wheel the caster mounts and steers by looking; everything it rolls over is burned and
 * bowled aside. Pressing the slot again or sneaking dismounts, leaving an ember ring.
 */
public final class CinderChariotSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CINDER_CHARIOT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                List<RollingBodyEntity> riding = ctx.level().getEntitiesOfClass(RollingBodyEntity.class, ctx.caster().getBoundingBox().inflate(4.0D), w -> w.isVehicleMode() && ctx.caster().getUUID().equals(w.ownerUuid()));
                if (!riding.isEmpty()) {
                    riding.get(0).finish();
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                Vec3 look = ctx.look();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
                Vec3 start = ctx.feet().add(0.0D, 0.2D, 0.0D);
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), start, Math.max(60, ctx.duration()), 1.1F, flat, (int) (ctx.seed() & 63));
                RollingBodyEntity wheel = RollingBodyEntity.create(ctx.level(), template, start, Math.max(1.4F, ctx.size()), flat.scale(0.2D), true);
                wheel.setPhysics(0.08F, 0.9F, 0.3F);
                ctx.level().addFreshEntity(wheel);
                ctx.caster().startRiding(wheel, true);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.NONE;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                if (!(entity instanceof RollingBodyEntity wheel)) {
                    return;
                }
                ServerLevel level = wheel.serverLevel();
                LivingEntity rider = wheel.getControllingPassenger();
                if (wheel.tickCount > 5 && (rider == null || rider.isShiftKeyDown())) {
                    wheel.finish();
                    return;
                }
                Vec3 v = wheel.getDeltaMovement();
                double speed = Math.sqrt(v.x * v.x + v.z * v.z);
                if (speed < 0.05D) {
                    return;
                }
                CompoundTag data = wheel.serverData();
                AABB tread = wheel.getBoundingBox().inflate(0.3D, 0.0D, 0.3D);
                for (LivingEntity hit : SkillTargets.hostilesIn(level, wheel.owner(), tread)) {
                    if (hit == rider) {
                        continue;
                    }
                    String key = "icd_" + hit.getId();
                    if (data.getInt(key) > wheel.tickCount) {
                        continue;
                    }
                    data.putInt(key, wheel.tickCount + 10);
                    SkillTargets.hurt(level, wheel.owner(), hit, wheel.damage(), wheel.definition(), true);
                    hit.igniteForSeconds(3.0F);
                    Vec3 dir = wheel.direction();
                    Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
                    double sign = hit.position().subtract(wheel.position()).dot(side) >= 0.0D ? 1.0D : -1.0D;
                    hit.setDeltaMovement(side.scale(sign * 0.7D * Math.max(0.5D, wheel.knockback())).add(0.0D, 0.3D, 0.0D));
                    hit.hurtMarked = true;
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                entity.ejectPassengers();
                Vec3 centre = entity.position();
                for (LivingEntity hit : SkillTargets.hostilesWithin(level, entity.owner(), centre, 3.0D)) {
                    SkillTargets.hurt(level, entity.owner(), hit, 4.0F, entity.definition(), false);
                    hit.igniteForSeconds(2.0F);
                }
                SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.4F);
                SpellFx.decal(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), 3.0F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.WHEEL).frame(8).band(GlyphKind.FACET_BAND, 12).band(GlyphKind.BRAID_BAND, 6).band(GlyphKind.TOOTH_BAND, 24).stamps(StampId.SPIRAL, 6).spokes(12, 0.2F, false).orbit(5, 0.84F, 3).core(CoreKind.EMBER_PIT).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("cinder_chariot", 1.1F))
                .trail(new ProfileCues.TrailSpec(FxKinds.Smoke.EMBER_CLUSTER, 3, 0.15F, 12, 0.2F, 0, 0.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.EMBER_FIELD, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.EMBER_DRIFT)
                .budget(2)
                .bounds(4.0F, 3.0F, 1.0F);
    }
}
