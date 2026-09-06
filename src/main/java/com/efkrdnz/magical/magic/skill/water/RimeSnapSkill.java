package com.efkrdnz.magical.magic.skill.water;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * WATER T0 - ROOT / AIM_SURFACE_INSTANT_STAMP / DISC. A frost bloom snaps onto the aimed surface:
 * everything inside is hurt, extinguished and rooted (can still turn, cast and be hit, cannot walk
 * or jump). Sneak = stamp it under your own feet.
 */
public final class RimeSnapSkill implements SkillModule {
    private static final int WINDUP = 3;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.RIME_SNAP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos;
                Vec3 normal;
                if (ctx.sneak()) {
                    pos = ctx.feet().add(0.0D, 0.04D, 0.0D);
                    normal = new Vec3(0.0D, 1.0D, 0.0D);
                } else if (ctx.aim().hitEntity()) {
                    Vec3 under = AimResolver.groundBelow(ctx.level(), ctx.aim().entity().position().add(0.0D, 0.5D, 0.0D), 6);
                    pos = under != null ? under.add(0.0D, 0.04D, 0.0D) : ctx.aim().entity().position();
                    normal = new Vec3(0.0D, 1.0D, 0.0D);
                } else if (ctx.aim().hitBlock()) {
                    pos = ctx.aim().point();
                    normal = ctx.aim().normal();
                } else {
                    pos = ctx.feet().add(0.0D, 0.04D, 0.0D);
                    normal = new Vec3(0.0D, 1.0D, 0.0D);
                }
                int root = Math.max(10, ctx.duration());
                SpellEffectEntity bloom = SpellEffectEntity.spawn(ctx, pos, WINDUP + root + 10, Math.max(1.0F, ctx.size()), normal);
                bloom.setExtra(root);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 16.0D;
            }

            @Override
            public double aimTolerance() {
                return 1.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(1.0F, 12.0F);
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
            if (entity.tickCount != WINDUP) {
                return;
            }
            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            for (LivingEntity hit : SkillTargets.hostilesWithin(entity.serverLevel(), entity.owner(), entity.position(), entity.radius())) {
                SkillTargets.hurt(entity.serverLevel(), entity.owner(), hit, entity.damage(), entity.definition(), true);
                hit.clearFire();
                MagicStatusService.apply(hit, MagicStatus.ROOTED, entity.extra(), entity.definition().id(), entity.owner());
            }
            SpellFx.impact(entity.serverLevel(), entity.definition(), entity.position(), entity.direction(), null, entity.owner(), 1.2F);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.SNOWFLAKE).frame(6).band(GlyphKind.FACET_BAND, 12).band(GlyphKind.DASHED_RING, 12, com.efkrdnz.magical.magic.visual.ColorRole.BASE).stamps(StampId.SNOWFLAKE, 6).core(CoreKind.HEX_LENS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.mark(FxKinds.Mark.FROST_BLOOM, 2.4F, 6))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.FROST_BLOOM, FxKinds.Smoke.FROST_CRYSTAL, FxKinds.Overlay.FROST_EDGES)
                .bounds(3.0F, 1.5F, 1.0F);
    }
}
