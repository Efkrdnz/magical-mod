package com.efkrdnz.magical.magic.skill.light;

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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * LIGHT T1 - REVEAL / SELF_PULSE / ROTATING_SECTOR. A planted lantern sweeps a lighthouse blade a
 * full turn: every hostile the bearing passes (walls or not) is revealed (glowing, invisibility
 * stripped) for the duration and hurt once. Sneak = sweep the other way.
 */
public final class RevelationSkill implements SkillModule {
    private static final int SHOOT = 3;
    private static final int SWEEP_TICKS = 24;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.REVELATION;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity lantern = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 0.05D, 0.0D), SHOOT + SWEEP_TICKS + 20, Math.max(8.0F, ctx.size()), ctx.look());
                lantern.setExtra(Math.max(40, ctx.duration()));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(0.0F, 20.0F);
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
            Vec3 look = entity.direction();
            float startYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            int sweepStep = entity.tickCount - SHOOT;
            float sign = entity.sneakMode() ? -1.0F : 1.0F;
            float bearing = sweepStep < 0 ? startYaw : startYaw + sign * (360.0F / SWEEP_TICKS) * Math.min(sweepStep, SWEEP_TICKS);
            entity.setValue(bearing);
            if (sweepStep < 0 || sweepStep > SWEEP_TICKS) {
                return;
            }
            float prev = startYaw + sign * (360.0F / SWEEP_TICKS) * Math.max(0, sweepStep - 1);
            Vec3 origin = entity.position();
            for (LivingEntity hostile : SkillTargets.hostilesWithin(entity.serverLevel(), entity.owner(), origin, entity.radius())) {
                if (MagicStatusService.has(hostile, MagicStatus.REVEALED) && MagicStatusService.sourceOf(hostile, MagicStatus.REVEALED) != null
                        && MagicStatusService.sourceOf(hostile, MagicStatus.REVEALED).equals(entity.getUUID())) {
                    continue;
                }
                Vec3 rel = hostile.position().subtract(origin);
                float yaw = (float) Math.toDegrees(Math.atan2(-rel.x, rel.z));
                float a = Mth.wrapDegrees(yaw - prev) * sign;
                float span = 360.0F / SWEEP_TICKS + 0.5F;
                if (a < -0.5F || a > span) {
                    continue;
                }
                MagicStatusService.apply(hostile, MagicStatus.REVEALED, entity.extra(), entity.definition().id(), entity);
                hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, entity.extra(), 0, false, false));
                hostile.removeEffect(MobEffects.INVISIBILITY);
                SkillTargets.hurt(entity.serverLevel(), entity.owner(), hostile, entity.damage(), entity.definition(), true);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.BEACON).frame(9).band(GlyphKind.TICK_BAND, 72).stamps(StampId.ARROW, 4).arcSweep(0.3F, ColorRole.BRIGHT).core(CoreKind.DISC_GLOW).spin(SpinSignature.SWEEP))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.filament(Silhouette.Form.FAN, FxKinds.Filament.RIBBON, 1, 2.5F, 24.0F, 0).withOffset(0.3F))
                .silhouette(Silhouette.glyph(1.6F).withRole(ColorRole.DIM))
                .silhouette(Silhouette.body(Silhouette.Form.CAGE, FxKinds.Body.PEARL, 1, 0.25F, 0.5F).withOffset(0.1F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.TUNNEL)
                .bounds(26.0F, 8.0F, 1.0F);
    }
}
