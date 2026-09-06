package com.efkrdnz.magical.magic.skill.arcane;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ARCANE T0 (starter) - LOCK_FACING / HITSCAN_TOUCH / SINGLE_TARGET. Instant 6-block touch: the
 * first living hit takes damage and has its facing pinned for the duration (attacks outside its
 * frontal cone do nothing). Sneak = turn the victim to face away first.
 */
public final class ArcaneSnapSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ARCANE_SNAP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity victim = ctx.aim().living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    SpellFx.decal(ctx.level(), ctx.definition(), ctx.aim().point(), ctx.aim().normal(), 0.6F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                if (ctx.sneak()) {
                    // reverse polarity: face directly away from the caster, then pin
                    Vec3 away = victim.position().subtract(ctx.caster().position());
                    float yaw = (float) Math.toDegrees(Math.atan2(-away.x, away.z));
                    victim.setYRot(yaw);
                    victim.yBodyRot = yaw;
                    victim.yHeadRot = yaw;
                }
                int ticks = Math.max(10, ctx.duration());
                MagicStatusService.apply(victim, MagicStatus.FACING_PINNED, ticks, ctx.definition().id(), ctx.caster());
                SkillTargets.hurt(ctx.level(), ctx.caster(), victim, ctx.damage(), ctx.definition(), true);
                SpellEffectEntity pin = SpellEffectEntity.spawn(ctx, victim.getEyePosition(), ticks, 1.0F, victim.getLookAngle());
                pin.setTarget(victim);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 6.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.6D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(0.0F, 6.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            // ride the victim's eyes along the frozen bearing
            Entity target = entity.target();
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                entity.finish();
                return;
            }
            entity.setPos(living.getEyePosition());
            double yawRad = Math.toRadians(living.getYRot());
            double pitchRad = Math.toRadians(living.getXRot());
            entity.setDirection(new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)));
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.NEEDLE).frame(4).frameRotated(45.0F).band(GlyphKind.TICK_BAND, 24).stamps(StampId.EYE, 4).core(CoreKind.CROSS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.body(Silhouette.Form.CROSSED_BLADES, FxKinds.Body.GLASS, 2, 0.22F, 2.0F))
                .silhouette(Silhouette.mark(FxKinds.Mark.CLOCK_SPOKES, 0.9F, 12).withOffset(-1.55F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.STATIC_GLITCH)
                .bounds(2.0F, 2.5F, 2.5F);
    }
}
