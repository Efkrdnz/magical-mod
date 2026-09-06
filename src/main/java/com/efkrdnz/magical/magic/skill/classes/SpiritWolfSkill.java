package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpiritWolfEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
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
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * BEASTMASTER - SUMMON_COMPANION / SELF_SIDE / MOBILE_BEAST. A spectral wolf condenses at the
 * caster's side and hunts for the duration; one wolf per caster, recast refreshes it. Sneak = send
 * it to guard the aimed spot instead.
 */
public final class SpiritWolfSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SPIRIT_WOLF;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                int life = Math.max(60, ctx.duration());
                Vec3 guard = ctx.sneak() ? SafeSpotSearch.standableNear(ctx.level(), ctx.aim().point(), 2, 3, 0.8F, 0.9F) : null;
                if (ctx.sneak() && guard == null) {
                    guard = ctx.aim().point();
                }
                List<SpiritWolfEntity> existing = ctx.level().getEntitiesOfClass(SpiritWolfEntity.class, new AABB(ctx.feet(), ctx.feet()).inflate(64.0D), w -> ctx.caster().getUUID().equals(w.ownerUuid()));
                if (!existing.isEmpty()) {
                    SpiritWolfEntity wolf = existing.get(0);
                    wolf.refresh(life, guard);
                    wolf.setHealth(wolf.getMaxHealth());
                    SpellFx.impact(ctx.level(), ctx.definition(), wolf.position().add(0.0D, 0.5D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, ctx.caster(), 0.8F);
                    return CastResult.SUCCESS;
                }
                Vec3 look = ctx.look();
                Vec3 side = new Vec3(-look.z, 0.0D, look.x);
                side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                Vec3 spawn = ctx.feet().add(side.scale(1.2D));
                Vec3 safe = SafeSpotSearch.standableNear(ctx.level(), spawn, 1, 2, 0.8F, 0.9F);
                SpiritWolfEntity wolf = SpiritWolfEntity.create(ctx.level(), ctx.definition(), ctx.caster(), safe != null ? safe : ctx.feet(), life, ctx.damage(), (int) (ctx.seed() & 63), guard);
                ctx.level().addFreshEntity(wolf);
                SpellFx.impact(ctx.level(), ctx.definition(), wolf.position().add(0.0D, 0.5D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, ctx.caster(), 1.0F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 16.0D;
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
                return MobCastProfile.summon();
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT;
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.WOLF).frame(4).band(GlyphKind.WAVE_BAND, 10).band(GlyphKind.TICK_BAND, 20).stamps(StampId.FOOTPRINT, 8).core(CoreKind.RIPPLE).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("spirit_wolf", 1.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.MIST_WISP, FxKinds.Overlay.WATER_DROPLETS)
                .bounds(2.0F, 1.5F, 0.5F);
    }
}
