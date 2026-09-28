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
import com.efkrdnz.magical.magic.visual.Accent;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
    /**
     * Wisps of vapour laid over the pool each time it puts things to sleep (every four ticks). The
     * pool is a cloud and nothing else, so it breathes more than a lingering emitter usually would.
     */
    private static final int VAPOUR_WISPS = 6;
    /** A wisp's size: about a block across as it rises, spreading to two. */
    private static final float VAPOUR_SCALE = 4.0F;
    /** How far across the pool the vapour lies, as a share of its radius. */
    private static final double VAPOUR_SPREAD = 0.45D;
    /**
     * The breath let out the moment the flask breaks. The pool's own breathing starts four ticks
     * later and a wisp is born small, so without it the cloud was a few specks for its first half
     * second and read as a splash that had fizzled rather than a draught that had spilled.
     */
    private static final int EXHALE_WISPS = 12;
    /** The exhale is held back when the pool is this close to its thrower (2.5 blocks, squared). */
    private static final double EXHALE_CLEARANCE_SQR = 6.25D;
    /**
     * No wisp is born nearer its thrower than this (in blocks, across the ground) or within a further
     * {@link #THROWER_RING} of it. The pool is three blocks round and a flask reaches six, so the
     * thrower often stands at its edge, and a sneak-shatter puts them in its middle: a wisp born at
     * their feet rises through their eyes a block and a half across. Pushed out, the ones that would
     * have been there stand round them as a ring instead.
     */
    private static final double THROWER_CLEARANCE = 1.8D;
    private static final double THROWER_RING = 0.6D;

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
                // not round the drinker's own head: a flask broken at their feet breathes like any other
                // pool, but a burst of a dozen wisps there would climb straight through their view
                Entity thrower = flask.owner();
                if (thrower == null || thrower.position().distanceToSqr(pos) > EXHALE_CLEARANCE_SQR) {
                    exhale(level, pool);
                }
                SpellFx.impact(level, flask.definition(), at, flask.landingNormal(), null, flask.owner(), 0.9F);
                flask.discard();
                return;
            }
            if (entity.tickCount % 4 != 0) {
                return;
            }
            breathe(level, entity);
            for (LivingEntity sleeper : SkillTargets.hostilesInCylinder(level, entity.owner(), entity.position(), entity.radius(), POOL_HEIGHT)) {
                MagicStatusService.apply(sleeper, MagicStatus.ASLEEP, 12, entity.definition().id(), entity.owner());
                if (sleeper instanceof ServerPlayer player) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 3, false, false));
                    SpellFx.overlay(player, entity.definition(), FxKinds.Overlay.IRIS_CLOSE, 12, 0.7F, ColorRole.DIM);
                }
            }
        };
    }

    /**
     * The vapour itself, as real smoke in the draught's own lavender: slow wisps laid low over the
     * pool, lit by the world like any mist, so it sits on the ground rather than glowing over it.
     */
    private static void breathe(ServerLevel level, SpellEffectEntity pool) {
        vent(level, pool, VAPOUR_WISPS, 0.2D, 0.06D, 0.004D);
    }

    /** The spill: the cloud's first breath, all at once and a little livelier than the ones after. */
    private static void exhale(ServerLevel level, SpellEffectEntity pool) {
        vent(level, pool, EXHALE_WISPS, 0.3D, 0.1D, 0.012D);
    }

    /**
     * Wisps laid over the pool the way a gaussian scatter would lay them, placed here rather than by
     * the client so that none is born under its thrower's eyes: one that falls inside the clearance
     * is carried out along its own bearing to the ring round them.
     */
    private static void vent(ServerLevel level, SpellEffectEntity pool, int wisps, double lift, double lean, double drift) {
        TintedParticleOptions vapour = vapour(pool);
        Vec3 at = pool.position();
        double spread = pool.radius() * VAPOUR_SPREAD;
        Entity thrower = pool.owner();
        for (int i = 0; i < wisps; i++) {
            double x = at.x + level.random.nextGaussian() * spread;
            double z = at.z + level.random.nextGaussian() * spread;
            if (thrower != null) {
                double dx = x - thrower.getX();
                double dz = z - thrower.getZ();
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < THROWER_CLEARANCE) {
                    double bearing = d > 1.0E-3D ? Math.atan2(dz, dx) : level.random.nextDouble() * Math.PI * 2.0D;
                    double r = THROWER_CLEARANCE + level.random.nextDouble() * THROWER_RING;
                    x = thrower.getX() + Math.cos(bearing) * r;
                    z = thrower.getZ() + Math.sin(bearing) * r;
                }
            }
            level.sendParticles(vapour, x, at.y + lift, z, 1, 0.0D, lean, 0.0D, drift);
        }
    }

    private static TintedParticleOptions vapour(SpellEffectEntity pool) {
        int lavender = VisualProfiles.of(pool.definition()).color(ColorRole.BASE);
        return new TintedParticleOptions(MagicalParticles.WISP.get(), lavender, VAPOUR_SCALE);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(3)
                // a thrown flask: its shatter is glass and a potion's swirl, not a splash of water
                .accent(Accent.BREW)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.CLOSED_EYE).frame(7).band(GlyphKind.WAVE_BAND, 9, ColorRole.DIM).band(GlyphKind.DASHED_RING, 18).stamps(StampId.CRESCENT, 9).core(CoreKind.RIPPLE).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                // the pool's glow only: thirty-six additive puffs of a lavender this pale lay a white slab
                // on the ground at noon, and ten at 0.3 still left white froth at its edges; the vapour
                // is breathe()'s real wisps now and this is only a sheen under them
                .silhouette(Silhouette.swarm(Silhouette.Form.POOL, FxKinds.Smoke.MIST_WISP, 6, 3.0F, 1.2F).withOpacity(0.14F).forModes(1))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LIQUID_DROP, 0.25F, 3, 6).forModes(0))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.IRIS_CLOSE)
                .budget(1)
                .bounds(4.0F, 2.0F, 1.0F);
    }
}
