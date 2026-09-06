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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * ARCANE T2 - BANISH / AIM_TARGET_HITSCAN / PHASED_ENTITY. The struck entity is exiled from the
 * fight in both directions for the duration, then takes the return damage. Sneak = aim backward.
 */
public final class ArcaneExileSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ARCANE_EXILE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                com.efkrdnz.magical.magic.cast.AimResolver.Result aim = ctx.sneak()
                        ? com.efkrdnz.magical.magic.cast.AimResolver.resolve(ctx.level(), ctx.caster(), ctx.look().scale(-1.0D), aimRange(), aimTolerance(), false, 0, null)
                        : ctx.aim();
                LivingEntity victim = aim.living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    SpellFx.decal(ctx.level(), ctx.definition(), aim.point(), aim.normal(), 0.6F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                int ticks = Math.max(20, ctx.duration());
                MagicStatusService.apply(victim, MagicStatus.EXILED, ticks, ctx.definition().id(), ctx.caster());
                SpellEffectEntity anchor = SpellEffectEntity.spawn(ctx, victim.position(), ticks, victim.getBbHeight(), aim.look());
                anchor.setTarget(victim);
                SpellFx.impact(ctx.level(), ctx.definition(), victim.getBoundingBox().getCenter(), aim.normal(), victim, ctx.caster(), 0.8F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 16.0D;
            }

            @Override
            public double aimTolerance() {
                return 1.2D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(3.0F, 16.0F);
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
                    entity.discard();
                    return;
                }
                entity.setPos(living.position());
                entity.setRadius(living.getBbHeight());
                entity.setValue(1.0F - entity.tickCount / (float) Math.max(1, entity.life()));
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                Entity target = entity.target();
                if (target instanceof LivingEntity living && living.isAlive()) {
                    MagicStatusService.clear(living, MagicStatus.EXILED);
                    SkillTargets.hurt(entity.serverLevel(), entity.owner(), living, entity.damage(), entity.definition(), true);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.LOCK).frame(9).band(GlyphKind.DASHED_RING, 9).stamps(StampId.KEY, 3).arcSweep(0.3F, ColorRole.BRIGHT).core(CoreKind.DISC_GLOW).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.rift(Silhouette.Form.VERTICAL_PANE, FxKinds.Rift.GLITCH_CUT, 1.1F, 0.7F, FxKinds.RiftInterior.VOID_BLACK))
                .silhouette(Silhouette.glyph(0.8F).withRole(ColorRole.DIM).withOpacity(0.8F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.LATTICE_GRID, FxKinds.Smoke.HEX_FRAGMENT, FxKinds.Overlay.VIGNETTE)
                .bounds(2.0F, 3.0F, 1.0F);
    }
}
