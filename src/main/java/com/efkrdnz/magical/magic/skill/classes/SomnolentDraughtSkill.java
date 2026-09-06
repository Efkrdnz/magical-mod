package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.entity.fx.ThrownSpellEntity;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ALCHEMIST - SLEEP / HITSCAN_SHATTER / VAPOUR_POOL. A flask flies to the resolved shatter point and
 * bursts into a low sleep-vapour pool: every living thing inside except the caster and their tamed
 * animals falls asleep until hurt. Sneak = shatter it at your own feet.
 */
public final class SomnolentDraughtSkill implements SkillModule {
    private static final byte MODE_POOL = 2;
    private static final int FLIGHT = 3;
    private static final double POOL_HEIGHT = 1.6D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SOMNOLENT_DRAUGHT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 start = ctx.eye().add(ctx.look().scale(0.5D)).add(0.0D, -0.2D, 0.0D);
                Vec3 target = ctx.sneak() ? ctx.feet().add(0.0D, 0.3D, 0.0D) : ctx.aim().point();
                Vec3 velocity = target.subtract(start).scale(1.0D / FLIGHT);
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), start, 20 + Math.max(20, ctx.duration()), 0.2F, velocity.normalize(), (int) (ctx.seed() & 63));
                template.setMode(ctx.sneak() ? (byte) 1 : (byte) 0);
                ThrownSpellEntity flask = ThrownSpellEntity.create(ctx.level(), template, start, velocity, 0.0F, 1.0F, 0, FLIGHT);
                ctx.level().addFreshEntity(flask);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 6.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.8D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(1.0F, 6.0F);
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
            if (entity instanceof ThrownSpellEntity flask) {
                if (!flask.landed()) {
                    return;
                }
                Vec3 at = flask.position();
                for (LivingEntity struck : SkillTargets.hostilesWithin(level, flask.owner(), at, 0.8D)) {
                    SkillTargets.hurt(level, flask.owner(), struck, flask.damage(), flask.definition(), true);
                    break;
                }
                Vec3 floor = AimResolver.groundBelow(level, at, 4);
                Vec3 pos = floor != null ? floor : at;
                SpellEffectEntity pool = SpellEffectEntity.create(level, flask.definition(), null, flask.owner(), pos, Math.max(20, flask.duration()), Math.max(1.5F, flask.radius() * 15.0F), new Vec3(0.0D, 1.0D, 0.0D), flask.seed());
                pool.setMode(MODE_POOL);
                pool.copyStatsFrom(flask);
                level.addFreshEntity(pool);
                SpellFx.impact(level, flask.definition(), at, flask.landingNormal(), null, flask.owner(), 0.9F);
                flask.discard();
                return;
            }
            if (entity.tickCount % 4 != 0) {
                return;
            }
            for (LivingEntity sleeper : SkillTargets.hostilesInCylinder(level, entity.owner(), entity.position(), entity.radius(), POOL_HEIGHT)) {
                MagicStatusService.apply(sleeper, MagicStatus.ASLEEP, 12, entity.definition().id(), entity.owner());
                if (sleeper instanceof ServerPlayer player) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 3, false, false));
                    SpellFx.overlay(player, entity.definition(), FxKinds.Overlay.IRIS_CLOSE, 12, 0.7F, ColorRole.DIM);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.CLOSED_EYE).frame(7).band(GlyphKind.WAVE_BAND, 9, ColorRole.DIM).band(GlyphKind.DASHED_RING, 18).stamps(StampId.CRESCENT, 9).core(CoreKind.RIPPLE).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.swarm(Silhouette.Form.POOL, FxKinds.Smoke.MIST_WISP, 36, 3.0F, 1.2F).forModes(1))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LIQUID_DROP, 0.25F, 3, 6).forModes(0))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.IRIS_CLOSE)
                .budget(1)
                .bounds(4.0F, 2.0F, 1.0F);
    }
}
