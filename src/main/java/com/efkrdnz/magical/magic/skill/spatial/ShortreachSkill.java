package com.efkrdnz.magical.magic.skill.spatial;

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
 * SPATIAL T0 - SHRINK_REACH / HITSCAN_TOUCH / BOUND_ARMS. A rune tape touches the first living
 * thing along the look line and binds its arms: its reach is clamped so melee only lands at point
 * blank. Sneak = reach straight behind you.
 */
public final class ShortreachSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SHORTREACH;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                AimResolver.Result aim = ctx.sneak()
                        ? AimResolver.resolve(ctx.level(), ctx.caster(), ctx.look().scale(-1.0D), aimRange(), aimTolerance(), false, 0, null)
                        : ctx.aim();
                LivingEntity victim = aim.living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    SpellFx.decal(ctx.level(), ctx.definition(), aim.point(), aim.normal(), 0.5F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                int ticks = Math.max(20, ctx.duration());
                MagicStatusService.apply(victim, MagicStatus.REACH_CLAMPED, ticks, ctx.definition().id(), ctx.caster());
                SkillTargets.hurt(ctx.level(), ctx.caster(), victim, ctx.damage(), ctx.definition(), true);
                SpellEffectEntity tape = SpellEffectEntity.spawn(ctx, victim.position().add(0.0D, 0.1D, 0.0D), ticks, victim.getBbHeight() * 0.85F, new Vec3(0.0D, 1.0D, 0.0D));
                tape.setTarget(victim);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 12.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.6D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(0.0F, 12.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                Entity target = entity.target();
                if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(living.getX(), living.getY() + 0.1D, living.getZ());
                entity.setRadius(living.getBbHeight() * 0.85F);
                entity.setValue(entity.tickCount / (float) entity.life());
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if (entity.target() instanceof LivingEntity living) {
                    MagicStatusService.clear(living, MagicStatus.REACH_CLAMPED);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.PINCER).frame(12).band(GlyphKind.TICK_BAND, 24).band(GlyphKind.DASHED_RING, 12).stamps(StampId.BAR, 12).core(CoreKind.CROSS).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.HELIX, FxKinds.Filament.DASH_TRAIN, 4, 0.42F, 1.6F, 0))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.CLOCK_SPOKES, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.TUNNEL)
                .bounds(2.0F, 2.5F, 0.5F);
    }
}
