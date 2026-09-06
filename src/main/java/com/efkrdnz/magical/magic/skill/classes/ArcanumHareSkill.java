package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.PolymorphShellEntity;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ARCHMAGE - TRANSFORM / AIM_TARGET_HITSCAN / TARGET_BODY. After a counterable windup a hitscan takes
 * the first living thing and transmutes it into a harmless porcelain hare for a duration that
 * shrinks with the victim's max health. Six cracks or the timer end it. Sneak = transform yourself
 * (an escape form).
 */
public final class ArcanumHareSkill implements SkillModule {
    private static final int WINDUP = 10;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ARCANUM_HARE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity hex = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.9D)), WINDUP, 0.6F, ctx.look());
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
            public int counterWindowTicks() {
                return WINDUP;
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
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity owner = entity.livingOwner();
            if (owner == null || !owner.isAlive()) {
                entity.finish();
                return;
            }
            entity.setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.9D)).add(0.0D, -0.2D, 0.0D));
            entity.setDirection(owner.getLookAngle());
            if (entity.tickCount != WINDUP - 1) {
                return;
            }
            LivingEntity victim;
            if (entity.sneakMode()) {
                victim = owner;
            } else {
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), 16.0D, 1.0D, false, 0, e -> SkillTargets.isHostile(owner, e));
                victim = aim.living();
                if (victim == null || !SkillTargets.isHostile(owner, victim)) {
                    SpellFx.decal(level, entity.definition(), aim.point(), aim.normal(), 1.0F);
                    return;
                }
                SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition(), true);
            }
            int base = Math.max(20, entity.duration());
            int life = Math.round(base * Mth.clamp(120.0F / Math.max(1.0F, victim.getMaxHealth()), 0.3F, 1.0F));
            PolymorphShellEntity shell = PolymorphShellEntity.create(level, entity.definition(), owner, victim, life, entity.seed());
            level.addFreshEntity(shell);
            SpellFx.impact(level, entity.definition(), victim.getBoundingBox().getCenter(), new Vec3(0.0D, 1.0D, 0.0D), victim == owner ? null : victim, owner, 1.2F);
            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.HARE).frame(13, CircleScript.FrameStyle.NESTED).band(GlyphKind.BRAID_BAND, 14, ColorRole.HOT).band(GlyphKind.TICK_BAND, 28).band(GlyphKind.RUNE_BAND, 7).stamps(StampId.CRESCENT, 7).orbit(5, 0.84F, 4).core(CoreKind.HEX_LENS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.custom("arcanum_hare", 0.6F).forModes(1))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.HEX_LENS, 0.35F, 6, 4).forModes(0))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.HEX_CELLS, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.HEX_PULSE)
                .bounds(2.0F, 2.0F, 1.0F);
    }
}
