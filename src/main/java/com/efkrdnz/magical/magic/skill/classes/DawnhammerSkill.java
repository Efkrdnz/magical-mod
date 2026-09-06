package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
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
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * DIVINESMITH - SMASH / MELEE_SWING_FRONT / SWUNG_ARC. A hammer of forge-light swings down over ten
 * ticks: everything in the frontal arc on the hit frame is driven DOWN (flight cancelled, staggered)
 * and any barrier it carries is shattered. Sneak = swing behind you.
 */
public final class DawnhammerSkill implements SkillModule {
    private static final int SWING = 10;
    private static final int HIT_FRAME = 7;
    private static final double REACH = 2.6D;
    private static final double HALF_ARC_COS = Math.cos(Math.toRadians(60.0D));

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.DAWNHAMMER;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 flat = flat(ctx.lookOrBack());
                SpellEffectEntity hammer = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 1.4D, 0.0D), SWING, (float) REACH * Math.max(0.6F, ctx.size() / 1.3F), flat);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(0.0F, 3.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static Vec3 flat(Vec3 look) {
        Vec3 f = new Vec3(look.x, 0.0D, look.z);
        return f.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : f.normalize();
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
            Vec3 dir = flat(entity.sneakMode() ? owner.getLookAngle().scale(-1.0D) : owner.getLookAngle());
            entity.setPos(owner.getX(), owner.getY() + 1.4D, owner.getZ());
            entity.setDirection(dir);
            entity.setValue(entity.tickCount / (float) SWING);
            if (entity.tickCount != HIT_FRAME) {
                return;
            }
            Vec3 origin = owner.position();
            double reach = entity.radius();
            for (LivingEntity victim : SkillTargets.hostilesWithin(level, owner, origin.add(0.0D, 1.0D, 0.0D), reach + 1.0D)) {
                Vec3 rel = victim.getBoundingBox().getCenter().subtract(origin);
                Vec3 flatRel = new Vec3(rel.x, 0.0D, rel.z);
                if (flatRel.length() > reach || flatRel.lengthSqr() > 1.0E-4D && flatRel.normalize().dot(dir) < HALF_ARC_COS) {
                    continue;
                }
                if (rel.y < -1.5D || rel.y > 3.5D) {
                    continue;
                }
                SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition(), true);
                Vec3 v = victim.getDeltaMovement();
                victim.setDeltaMovement(v.x * 0.3D, -0.6D, v.z * 0.3D);
                victim.hurtMarked = true;
                victim.removeEffect(MobEffects.LEVITATION);
                MagicStatusService.apply(victim, MagicStatus.ROOTED, 6, entity.definition().id(), owner);
                if (victim instanceof ServerPlayer player) {
                    if (player.getAbilities().flying && !player.isCreative()) {
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                    }
                    var state = player.getData(MagicalAttachments.MAGIC_STATE);
                    if (state.barrier() > 0) {
                        state.setBarrier(0);
                        state.sync(player);
                        SpellFx.barrierHit(level, entity.definition(), victim.getBoundingBox().getCenter(), new Vec3(0.0D, 1.0D, 0.0D));
                    }
                }
            }
            Vec3 head = origin.add(dir.scale(reach * 0.8D)).add(0.0D, 0.05D, 0.0D);
            SpellFx.impact(level, entity.definition(), head, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 1.4F);
            SpellFx.decal(level, entity.definition(), head, new Vec3(0.0D, 1.0D, 0.0D), 1.6F);
            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.HAMMER).frame(4).band(GlyphKind.PETAL_BAND, 12, ColorRole.HOT).band(GlyphKind.TICK_BAND, 24).stamps(StampId.KITE, 6).core(CoreKind.SUNBURST).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("dawnhammer", 3.5F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.SHOCK_RING)
                .bounds(5.0F, 4.0F, 2.0F);
    }
}
