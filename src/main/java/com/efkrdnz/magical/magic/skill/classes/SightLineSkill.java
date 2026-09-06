package com.efkrdnz.magical.magic.skill.classes;

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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * MARKSMAN - PIERCE_LANE / LOOK_VECTOR / DISTANCE_SCALED. One line drawn from the eye to the end of
 * the sightline, and everything standing on it is hit - harder the further down the line it stands,
 * because the round has had further to settle. The Marksman is paid for the shot nobody else can
 * take. Sneak = a tighter lane that hits fewer things for more.
 */
public final class SightLineSkill implements SkillModule {
    private static final double RANGE = 40.0D;
    private static final double LANE = 1.1D;
    private static final double TIGHT_LANE = 0.55D;
    /** Extra damage per block of travel, applied on top of the base. */
    private static final float PER_BLOCK = 0.05F;
    private static final float TIGHT_BONUS = 1.4F;
    private static final int STEPS = 40;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SIGHT_LINE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerLevel level = ctx.level();
                Vec3 start = ctx.eye();
                AimResolver.Result aim = ctx.aim();
                Vec3 end = aim.point();
                double reach = Math.min(RANGE, end.subtract(start).length());
                Vec3 direction = ctx.look();
                double lane = ctx.sneak() ? TIGHT_LANE : LANE;
                float scale = ctx.sneak() ? TIGHT_BONUS : 1.0F;

                // Walk the lane in steps and collect each distinct body once, nearest first.
                List<LivingEntity> struck = new ArrayList<>();
                for (int step = 1; step <= STEPS; step++) {
                    double travelled = reach * step / STEPS;
                    Vec3 at = start.add(direction.scale(travelled));
                    for (LivingEntity victim : SkillTargets.hostilesWithin(level, ctx.caster(), at, lane)) {
                        if (struck.contains(victim)) {
                            continue;
                        }
                        struck.add(victim);
                        float damage = (ctx.damage() + (float) travelled * PER_BLOCK * ctx.damage()) * scale;
                        SkillTargets.hurt(level, ctx.caster(), victim, damage, ctx.definition(), true);
                        SkillTargets.shove(victim, start, 0.3D * Math.max(0.2D, ctx.stats().knockback()), 0.1D);
                    }
                }

                SpellEffectEntity beam = SpellEffectEntity.spawn(ctx, start.add(direction.scale(reach * 0.5D)),
                        Math.max(6, ctx.duration()), (float) lane, direction);
                beam.serverData().putDouble("LEN", reach);
                SpellFx.release(ctx.caster(), ctx.definition(), direction);
                SpellFx.impact(level, ctx.definition(), start.add(direction.scale(reach)), direction, null, ctx.caster(), 1.0F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return RANGE;
            }

            @Override
            public double aimTolerance() {
                return 1.5D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(5.0F, 36.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        // Purely a lingering mark: the damage was resolved the instant the trigger was pulled.
        return entity -> entity.setValue(1.0F - entity.tickCount / (float) Math.max(1, entity.life()));
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.FEATHER).frame(11).band(GlyphKind.DASHED_RING, 32).band(GlyphKind.CHAIN_BAND, 4).stamps(StampId.CROSS, 12).core(CoreKind.IRIS).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.LANE, FxKinds.Filament.DASH_TRAIN, 1, 0.06F, 40.0F, 2))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LENS_STREAKS, 0.18F, 2, 1))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.TUNNEL)
                .bounds(40.0F, 1.5F, 0.6F);
    }
}
