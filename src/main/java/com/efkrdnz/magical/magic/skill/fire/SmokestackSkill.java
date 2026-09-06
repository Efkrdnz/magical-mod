package com.efkrdnz.magical.magic.skill.fire;

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
import com.efkrdnz.magical.magic.service.TargetDenialService;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * FIRE T0 - OBSCURE / AIM_POINT / SMOKE_COLUMN. A dense column of black smoke: mobs cannot acquire
 * targets inside it, from inside it, or through it; anything inside is slowed and lightly burned.
 * Sneak = plant it at your own feet.
 */
public final class SmokestackSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SMOKESTACK;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                SpellEffectEntity column = SpellEffectEntity.spawn(ctx, pos, Math.max(40, ctx.duration()), Math.max(1.5F, ctx.size()), new Vec3(0.0D, 1.0D, 0.0D));
                column.setValue(5.0F);
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
            public boolean aimDropsToGround() {
                return true;
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
            double height = entity.value() > 0.0F ? entity.value() : 5.0D;
            TargetDenialService.register(entity.level(), entity.position(), entity.radius(), height);
            if (entity.tickCount % 4 != 0) {
                return;
            }
            for (LivingEntity inside : SkillTargets.hostilesInCylinder(entity.serverLevel(), entity.owner(), entity.position(), entity.radius(), height)) {
                inside.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 0, false, false));
                if (entity.tickCount % 20 == 0) {
                    SkillTargets.hurt(entity.serverLevel(), entity.owner(), inside, entity.damage(), entity.definition().id());
                }
                if (inside instanceof ServerPlayer player && entity.tickCount % 8 == 0) {
                    SpellFx.overlay(player, entity.definition(), FxKinds.Overlay.INK_BLEED, 12, 0.55F, ColorRole.INK);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.CHIMNEY).frame(3).band(GlyphKind.TOOTH_BAND, 12).stamps(StampId.DOT, 6).core(CoreKind.EMBER_PIT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.swarm(Silhouette.Form.COLUMN, FxKinds.Smoke.SMOKE_PUFF, 48, 3.0F, 5.0F))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.EMBER_CLUSTER, 0.9F, 8, 4).withOffset(0.2F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.SMOKE_PUFF, FxKinds.Overlay.INK_BLEED)
                .budget(1)
                .bounds(4.0F, 6.0F, 1.0F);
    }
}
