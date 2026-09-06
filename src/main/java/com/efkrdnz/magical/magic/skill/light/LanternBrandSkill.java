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
 * LIGHT T0 - HAZARD_ON_TARGET / AIM_TARGET_TOUCH / MOBILE_RADIUS_ON_VICTIM. A lantern hangs over
 * the struck target; every pulse sears everyone around it except the host, who becomes a walking
 * hazard scattering its own pack. A miss plants the lantern at the aim point. Sneak = reverse
 * polarity: the pulses draw others inward toward the host.
 */
public final class LanternBrandSkill implements SkillModule {
    private static final int PULSE = 10;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LANTERN_BRAND;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity host = ctx.aim().living();
                boolean valid = host != null && SkillTargets.isHostile(ctx.caster(), host);
                Vec3 pos = valid ? host.position() : ctx.aim().point();
                SpellEffectEntity lantern = SpellEffectEntity.spawn(ctx, pos, Math.max(40, ctx.duration()), 3.0F * Math.max(0.5F, ctx.size()), new Vec3(0.0D, 1.0D, 0.0D));
                if (valid) {
                    lantern.setTarget(host);
                    SkillTargets.hurt(ctx.level(), ctx.caster(), host, ctx.damage() * 1.5F, ctx.definition(), true);
                }
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 12.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.8D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(2.0F, 12.0F);
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
            Entity host = entity.target();
            if (host != null) {
                if (!host.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(host.position());
            }
            if (entity.tickCount % PULSE != 0) {
                return;
            }
            Vec3 centre = entity.position();
            boolean inward = entity.sneakMode();
            for (LivingEntity near : SkillTargets.hostilesWithin(entity.serverLevel(), entity.owner(), centre.add(0.0D, 0.8D, 0.0D), entity.radius())) {
                if (near == host) {
                    continue;
                }
                SkillTargets.hurt(entity.serverLevel(), entity.owner(), near, entity.damage(), entity.definition().id());
                SkillTargets.shove(near, centre, (inward ? -0.35D : 0.35D) * Math.max(0.5D, entity.knockback() * 2.0D), 0.08D);
            }
            entity.setValue((entity.tickCount / (float) PULSE) % 2.0F);
            SpellFx.zoneTick(entity.serverLevel(), entity.definition(), centre, entity.radius() * 0.5F);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.LANTERN).frame(16).band(GlyphKind.DASHED_RING, 12).spokes(24, 0.55F, true).stamps(StampId.TEARDROP, 6).core(CoreKind.SUNBURST).spin(SpinSignature.SWEEP))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.mark(FxKinds.Mark.RAY_BURST, 3.0F, 16))
                .silhouette(Silhouette.body(Silhouette.Form.CAGE, FxKinds.Body.AMBER, 1, 0.22F, 0.36F).withOffset(2.3F))
                .silhouette(Silhouette.filament(Silhouette.Form.COLUMN, FxKinds.Filament.CHAIN, 3, 0.05F, 0.5F, 0).withOffset(2.66F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.HEAT_SHIMMER)
                .bounds(4.0F, 4.0F, 1.0F);
    }
}
