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
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * BEASTMASTER - SUMMON_COMPANION / SELF_SIDE / MOBILE_BEAST. A spectral wolf condenses at the
 * caster's side and hunts for the duration; one wolf per caster, recast refreshes it. Sneak = send
 * it to guard the aimed spot instead.
 */
public final class SpiritWolfSkill implements SkillModule {
    /**
     * Mist drawn in to where the wolf condenses: wisps of its colour run in off a ring round the
     * spot, a wisp keeping 0.93 of its speed a tick so it covers about fourteen times what it is
     * sent at, over a ripple on the ground it condensed out of.
     */
    private static final int CONDENSE_WISPS = 8;
    private static final double CONDENSE_RING = 1.1D;
    private static final float CONDENSE_WISP_SCALE = 2.2F;
    private static final float CONDENSE_RIPPLE = 0.6F;

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
                    // a recast gathers it again out of the same mist it first condensed from
                    condense(ctx.level(), ctx.definition(), wolf.position());
                    return CastResult.SUCCESS;
                }
                Vec3 look = ctx.look();
                Vec3 side = new Vec3(-look.z, 0.0D, look.x);
                side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                Vec3 spawn = ctx.feet().add(side.scale(1.2D));
                Vec3 safe = SafeSpotSearch.standableNear(ctx.level(), spawn, 1, 2, 0.8F, 0.9F);
                SpiritWolfEntity wolf = SpiritWolfEntity.create(ctx.level(), ctx.definition(), ctx.caster(), safe != null ? safe : ctx.feet(), life, ctx.damage(), (int) (ctx.seed() & 63), guard);
                ctx.level().addFreshEntity(wolf);
                condense(ctx.level(), ctx.definition(), wolf.position());
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

    /**
     * The wolf condensing at the caster's side. It was a full hit cue, a block and a bit from the
     * caster's own camera: its splash, bubbles and shader mist puffs went off at the edge of their
     * view as though something had struck them, and it laid no mark, because the hit sat half a
     * block over the floor with nothing under it. Only its sound is kept.
     */
    private static void condense(ServerLevel level, MagicSkillDefinition definition, Vec3 feet) {
        VisualProfile profile = VisualProfiles.of(definition);
        TintedParticleOptions mist = new TintedParticleOptions(MagicalParticles.WISP.get(), profile.color(ColorRole.BRIGHT), CONDENSE_WISP_SCALE);
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < CONDENSE_WISPS; i++) {
            double a = offset + Math.PI * 2.0D * i / CONDENSE_WISPS;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double y = feet.y + 0.3D + 0.3D * (i % 2);
            level.sendParticles(mist, feet.x + cos * CONDENSE_RING, y, feet.z + sin * CONDENSE_RING, 0, -cos, 0.15D, -sin, CONDENSE_RING / 14.0D);
        }
        SpellFx.zoneTick(level, definition, feet.add(0.0D, 0.04D, 0.0D), CONDENSE_RIPPLE);
        ProfileCues.SoundCue cue = profile.sounds().impact();
        if (cue != null) {
            level.playSound(null, feet.x, feet.y, feet.z, cue.sound(), SoundSource.PLAYERS, cue.volume(), cue.pitch());
        }
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
