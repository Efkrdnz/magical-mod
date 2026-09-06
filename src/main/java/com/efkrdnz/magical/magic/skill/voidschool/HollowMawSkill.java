package com.efkrdnz.magical.magic.skill.voidschool;

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
import com.efkrdnz.magical.magic.service.SpellIntercept;
import com.efkrdnz.magical.magic.skill.SkillModule;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * VOID T0 - DEVOUR / SELF_FRONT_WINDOW / MOUTH_CONE. An iris-mouth opens ahead of the hand for a
 * short window: hostile projectiles entering its cone are eaten (mana back, bite grows), then it
 * snaps shut on everything living in the cone. A reactive parry-bite. Sneak = open it behind you.
 */
public final class HollowMawSkill implements SkillModule {
    private static final int EAT_FROM = 2;
    private static final int BITE = 10;
    private static final int MAX_EATEN = 4;
    private static final double CONE_COS = Math.cos(Math.toRadians(40.0D));

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HOLLOW_MAW;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 dir = ctx.lookOrBack();
                SpellEffectEntity maw = SpellEffectEntity.spawn(ctx, ctx.eye().add(dir.scale(0.9D)), Math.max(BITE + 4, ctx.duration() + 2), 2.4F * Math.max(0.5F, ctx.size()), dir);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.defence();
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static boolean inCone(SpellEffectEntity maw, Vec3 point) {
        Vec3 rel = point.subtract(maw.position());
        double dist = rel.length();
        if (dist > maw.radius() || dist < 1.0E-3D) {
            return dist <= maw.radius();
        }
        return rel.scale(1.0D / dist).dot(maw.direction()) >= CONE_COS;
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
            Vec3 dir = entity.sneakMode() ? owner.getLookAngle().scale(-1.0D) : owner.getLookAngle();
            entity.setPos(owner.getEyePosition().add(dir.scale(0.9D)));
            entity.setDirection(dir);
            int t = entity.tickCount;
            if (t >= EAT_FROM && t <= BITE && entity.extra() < MAX_EATEN) {
                for (Entity projectile : SpellIntercept.hostileProjectiles(level, entity.position(), entity.radius(), owner)) {
                    if (!inCone(entity, projectile.position())) {
                        continue;
                    }
                    SpellIntercept.erase(projectile);
                    entity.setExtra(entity.extra() + 1);
                    if (owner instanceof ServerPlayer player) {
                        var state = player.getData(MagicalAttachments.MAGIC_STATE);
                        state.addMana(6);
                        state.sync(player);
                    }
                    SpellFx.burst(level, entity.definition(), projectile.position(), dir.scale(-1.0D), 1.0F);
                    if (entity.extra() >= MAX_EATEN) {
                        break;
                    }
                }
            }
            entity.setValue(Math.min(1.0F, t / (float) BITE));
            if (t == BITE) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                float damage = entity.damage() + 1.5F * entity.extra();
                for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, entity.position(), entity.radius())) {
                    if (!inCone(entity, hostile.getBoundingBox().getCenter())) {
                        continue;
                    }
                    SkillTargets.hurt(level, owner, hostile, damage, entity.definition(), true);
                    hostile.push(dir.x * 0.25D * Math.max(0.5D, entity.knockback() * 2.0D), 0.05D, dir.z * 0.25D * Math.max(0.5D, entity.knockback() * 2.0D));
                    hostile.hurtMarked = true;
                }
                SpellFx.impact(level, entity.definition(), entity.position().add(dir.scale(0.6D)), dir, null, owner, 1.0F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.TOOTH).frame(7).band(GlyphKind.TOOTH_BAND, 16).band(GlyphKind.RUNE_BAND, 7, ColorRole.INK).stamps(StampId.BONE, 7).core(CoreKind.IRIS).inkOutwardIn(true).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.rift(Silhouette.Form.PANE, FxKinds.Rift.IRIS_MOUTH, 0.75F, 0.75F, FxKinds.RiftInterior.VOID_BLACK))
                .silhouette(Silhouette.body(Silhouette.Form.SPIKE_CLUSTER, FxKinds.Body.OBSIDIAN, 7, 0.45F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.MAW, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.IRIS_CLOSE)
                .bounds(3.0F, 2.0F, 2.0F);
    }
}
