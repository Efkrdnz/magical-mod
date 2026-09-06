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
import com.efkrdnz.magical.magic.service.SpellIntercept;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * ARCANE T0 - ATTRACT_PROJECTILES / AIM_POINT_PLANT / RADIAL_WELL. A hovering magnetite needle
 * bends every hostile projectile within 6 blocks toward itself and swallows what reaches it (six
 * charges). Sneak = reverse polarity: bends them away.
 */
public final class LodestoneSkill implements SkillModule {
    private static final int CHARGES = 6;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LODESTONE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.aim().point().add(ctx.aim().normal().scale(0.9D));
                SpellEffectEntity stone = SpellEffectEntity.spawn(ctx, pos, Math.max(40, ctx.duration()), 6.0F * Math.max(0.5F, ctx.size()), ctx.aim().normal());
                stone.setExtra(CHARGES);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 14.0D;
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
                return TuningView.UTILITY;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            boolean repel = entity.sneakMode();
            double radius = entity.radius();
            Vec3 centre = entity.position();
            for (Entity projectile : SpellIntercept.hostileProjectiles(entity.serverLevel(), centre, radius, entity.owner())) {
                double dist = projectile.position().distanceTo(centre);
                if (!repel && dist < 0.6D) {
                    SpellIntercept.erase(projectile);
                    entity.setExtra(entity.extra() - 1);
                    SpellFx.impact(entity.serverLevel(), entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 0.6F);
                    if (entity.extra() <= 0) {
                        entity.finish();
                        return;
                    }
                    continue;
                }
                float maxDeg = (float) (22.0D + 26.0D * (1.0D - Math.min(1.0D, dist / radius)));
                Vec3 target = repel ? projectile.position().add(projectile.position().subtract(centre).normalize().scale(4.0D)) : centre;
                SpellIntercept.bendToward(projectile, target, maxDeg);
            }
            entity.setValue(entity.extra() / (float) CHARGES);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.HORSESHOE).frame(8).band(GlyphKind.DASHED_RING, 16).stamps(StampId.BAR, 8).core(CoreKind.HEX_LENS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.body(Silhouette.Form.PRISM, FxKinds.Body.STONE, 6, 0.16F, 1.3F))
                .silhouette(Silhouette.filament(Silhouette.Form.RING, FxKinds.Filament.RUNE_THREAD, 6, 0.06F, 1.6F, 0))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.HEX_CELLS, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.HEX_PULSE)
                .bounds(4.0F, 3.0F, 2.0F);
    }
}
