package com.efkrdnz.magical.magic.skill.fire;

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * FIRE T0 - ROLL / SELF_LAUNCH_GROUND / ROLLING_SOLID_SPHERE. A molten slag boulder is kicked along
 * the look yaw; a real rolling body that climbs steps, bounces off walls and slows on flat ground,
 * bowling over and igniting what it rolls through, then shattering into an ember ring. Sneak =
 * conjure it behind your heels and roll it backward.
 */
public final class SlagRollerSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SLAG_ROLLER;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 look = ctx.look();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                if (flat.lengthSqr() < 1.0E-4D) {
                    flat = new Vec3(0.0D, 0.0D, 1.0D);
                }
                flat = flat.normalize();
                if (ctx.sneak()) {
                    flat = flat.scale(-1.0D);
                }
                Vec3 start = ctx.feet().add(flat.scale(0.9D)).add(0.0D, 0.1D, 0.0D);
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), start, Math.max(40, ctx.duration()), ctx.size() * 0.5F, flat, (int) (ctx.seed() & 63));
                template.setMode(ctx.sneak() ? (byte) 1 : (byte) 0);
                RollingBodyEntity boulder = RollingBodyEntity.create(ctx.level(), template, start, Math.max(0.6F, ctx.size()), flat.scale(Math.max(0.2F, ctx.stats().speed())), false);
                boulder.setPhysics(0.06F, 0.965F, 0.6F);
                ctx.level().addFreshEntity(boulder);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(2.0F, 12.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                if (!(entity instanceof RollingBodyEntity boulder)) {
                    return;
                }
                Vec3 v = boulder.getDeltaMovement();
                double speed = Math.sqrt(v.x * v.x + v.z * v.z);
                float kick = Math.max(0.2F, boulder.speed());
                float speedFraction = (float) Math.min(1.0D, speed / kick);
                CompoundTag data = boulder.serverData();
                for (LivingEntity hit : SkillTargets.hostilesIn(boulder.serverLevel(), boulder.owner(), boulder.getBoundingBox().inflate(0.3D))) {
                    String key = "icd_" + hit.getId();
                    if (data.getInt(key) > boulder.tickCount) {
                        continue;
                    }
                    data.putInt(key, boulder.tickCount + 10);
                    SkillTargets.hurt(boulder.serverLevel(), boulder.owner(), hit, boulder.damage() * Math.max(0.35F, speedFraction), boulder.definition(), true);
                    Vec3 dir = boulder.direction();
                    hit.push(dir.x * 0.6D * boulder.knockback() + (hit.getX() - boulder.getX()) * 0.3D, 0.35D, dir.z * 0.6D * boulder.knockback() + (hit.getZ() - boulder.getZ()) * 0.3D);
                    hit.hurtMarked = true;
                    hit.igniteForSeconds(3.0F);
                }
                if (boulder.tickCount > 10 && speed < 0.04D && boulder.onGround()) {
                    data.putInt("Still", data.getInt("Still") + 1);
                    if (data.getInt("Still") >= 5) {
                        boulder.finish();
                    }
                } else {
                    data.putInt("Still", 0);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                Vec3 centre = entity.position();
                for (LivingEntity hit : SkillTargets.hostilesWithin(entity.serverLevel(), entity.owner(), centre, 1.6D)) {
                    SkillTargets.hurt(entity.serverLevel(), entity.owner(), hit, 2.0F, entity.definition(), false);
                    hit.igniteForSeconds(2.0F);
                }
                SpellFx.impact(entity.serverLevel(), entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.2F);
                SpellFx.decal(entity.serverLevel(), entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), 1.2F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.CRACKED_STONE).frame(10).band(GlyphKind.FACET_BAND, 12).stamps(StampId.TRIANGLE, 5).core(CoreKind.EMBER_PIT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.body(Silhouette.Form.BOULDER, FxKinds.Body.MAGMA_ROCK, 8, 0.6F))
                .trail(new ProfileCues.TrailSpec(FxKinds.Smoke.SPARK_STREAK, 2, 0.12F, 12, 0.3F, 0, 1.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.EMBER_FIELD, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.EMBER_DRIFT)
                .bounds(2.0F, 2.0F, 1.0F);
    }
}
