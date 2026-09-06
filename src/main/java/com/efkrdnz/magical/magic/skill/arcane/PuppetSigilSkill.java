package com.efkrdnz.magical.magic.skill.arcane;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * ARCANE T3 - PUPPET / HITSCAN_STRING_FAN / FIVE_STRING_FAN. Five rune-threads string the first
 * living entity along the look ray; for the duration its legs walk it toward wherever the caster
 * looks (sneak: away). Walking it into another entity strikes both; cutting the strings hurts it.
 */
public final class PuppetSigilSkill implements SkillModule {
    private static final int WINDUP = 10;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.PUPPET_SIGIL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity victim = ctx.aim().living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    return CastResult.CONSUMED_NO_COOLDOWN; // a retry, not a fail state
                }
                int ticks = WINDUP + Math.max(20, ctx.duration());
                SpellEffectEntity sigil = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.9D)), ticks, 1.0F, ctx.look());
                sigil.setTarget(victim);
                MagicStatusService.apply(victim, MagicStatus.PUPPETED, ticks, ctx.definition().id(), ctx.caster());
                if (victim instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 16.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.9D;
            }

            @Override
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(4.0F, 16.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                LivingEntity owner = entity.livingOwner();
                Entity target = entity.target();
                if (owner == null || !owner.isAlive() || !(target instanceof LivingEntity puppet) || !puppet.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.9D)).add(0.0D, -0.2D, 0.0D));
                entity.setDirection(owner.getLookAngle());
                if (entity.tickCount < WINDUP) {
                    return;
                }
                if (entity.tickCount == WINDUP) {
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    SkillTargets.hurt(level, owner, puppet, 0.5F, entity.definition(), true);
                }
                if (owner.distanceTo(puppet) > 24.0D) {
                    entity.finish();
                    return;
                }
                // walk toward (or away from) the caster's aim point
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), 32.0D, 0.0D, true, 12, null);
                Vec3 goal = aim.point();
                Vec3 dir = goal.subtract(puppet.position());
                dir = new Vec3(dir.x, 0.0D, dir.z);
                if (entity.sneakMode()) {
                    dir = dir.scale(-1.0D);
                }
                if (dir.lengthSqr() > 0.6D) {
                    Vec3 step = dir.normalize().scale(0.22D * Math.max(0.5D, entity.speed()));
                    Vec3 v = puppet.getDeltaMovement();
                    puppet.setDeltaMovement(step.x, v.y, step.z);
                    puppet.hurtMarked = true;
                    float yaw = (float) Math.toDegrees(Math.atan2(-step.x, step.z));
                    puppet.setYRot(yaw);
                    puppet.yBodyRot = yaw;
                    if (puppet.horizontalCollision && puppet.onGround()) {
                        puppet.setDeltaMovement(puppet.getDeltaMovement().add(0.0D, 0.42D, 0.0D));
                    }
                }
                // collision strikes: walking the puppet into another hostile hurts both
                if (entity.tickCount % 10 == 0) {
                    for (LivingEntity other : SkillTargets.hostilesIn(level, owner, puppet.getBoundingBox().inflate(0.2D))) {
                        if (other == puppet) {
                            continue;
                        }
                        SkillTargets.hurt(level, owner, other, entity.damage() * 0.5F, entity.definition(), true);
                        SkillTargets.hurt(level, owner, puppet, entity.damage() * 0.5F, entity.definition(), false);
                        break;
                    }
                }
                entity.setValue(entity.tickCount / (float) entity.life());
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                Entity target = entity.target();
                if (target instanceof LivingEntity puppet && puppet.isAlive()) {
                    MagicStatusService.clear(puppet, MagicStatus.PUPPETED);
                    if (puppet instanceof Mob mob) {
                        mob.setNoAi(false);
                    }
                    if (entity.tickCount >= WINDUP) {
                        SkillTargets.hurt(entity.serverLevel(), entity.owner(), puppet, entity.damage(), entity.definition(), true);
                    }
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.HAND).frame(11).frameRotated(45.0F).band(GlyphKind.BRAID_BAND, 5).band(GlyphKind.RUNE_BAND, 10).stamps(StampId.BONE, 5).orbit(5, 0.82F, 4).core(CoreKind.CROSS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.LINK, FxKinds.Filament.THREAD_KNOTS, 5, 0.05F))
                .silhouette(Silhouette.body(Silhouette.Form.CROSSED_BLADES, FxKinds.Body.BONE_IVORY, 2, 0.45F, 0.9F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.IRIS_CLOSE)
                .bounds(20.0F, 4.0F, 4.0F);
    }
}
