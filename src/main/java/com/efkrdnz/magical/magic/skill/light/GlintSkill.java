package com.efkrdnz.magical.magic.skill.light;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.entity.fx.ThrownSpellEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * LIGHT T0 - BLIND / LOB_AIRBURST / LOS_SPHERE. A lens-bead lobbed under gravity that airbursts
 * after its fuse (or second contact): everything with line of sight to the burst is hurt and
 * dazzled; cover blocks it. A throw-over-the-wall flashbang. Sneak = lob backward.
 */
public final class GlintSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.GLINT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 dir = ctx.lookOrBack();
                Vec3 start = ctx.eye().add(dir.scale(0.5D)).add(0.0D, -0.15D, 0.0D);
                int fuse = Math.max(6, ctx.duration());
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), start, fuse + 40, 5.5F * Math.max(0.5F, ctx.size()), dir, (int) (ctx.seed() & 63));
                template.setMode(ctx.sneak() ? (byte) 1 : (byte) 0);
                ThrownSpellEntity bead = ThrownSpellEntity.create(ctx.level(), template, start, dir.scale(Math.max(0.4D, ctx.stats().speed())).add(0.0D, 0.1D, 0.0D), 0.05F, 0.99F, 1, fuse);
                ctx.level().addFreshEntity(bead);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 14.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            if (!(entity instanceof ThrownSpellEntity bead) || !bead.landed()) {
                return;
            }
            ServerLevel level = bead.serverLevel();
            Vec3 burst = bead.position();
            for (LivingEntity hit : SkillTargets.hostilesWithin(level, bead.owner(), burst, bead.radius())) {
                HitResult los = level.clip(new ClipContext(burst, hit.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bead));
                if (los.getType() == HitResult.Type.BLOCK) {
                    continue;
                }
                SkillTargets.hurt(level, bead.owner(), hit, bead.damage(), bead.definition(), true);
                MagicStatusService.apply(hit, MagicStatus.DAZZLED, 40, bead.definition().id(), bead.owner());
                hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            SpellFx.impact(level, bead.definition(), burst, new Vec3(0.0D, 1.0D, 0.0D), null, bead.owner(), 2.2F);
            bead.discard();
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.STAR6).frame(6).band(GlyphKind.DASHED_RING, 24).stamps(StampId.NEEDLE, 6).core(CoreKind.DISC_GLOW).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LENS_STREAKS, 0.5F, 4, 10))
                .trail(new ProfileCues.TrailSpec(FxKinds.Smoke.SPARK_STREAK, 2, 0.08F, 10, 0.2F, 0, 1.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.FLASH)
                .bounds(2.0F, 2.0F, 1.0F);
    }
}
