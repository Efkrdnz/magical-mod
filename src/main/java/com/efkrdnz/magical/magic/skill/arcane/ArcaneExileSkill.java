package com.efkrdnz.magical.magic.skill.arcane;

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
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * ARCANE T2 - BANISH / AIM_TARGET_HITSCAN / PHASED_ENTITY. The struck entity is exiled from the
 * fight in both directions for the duration, then takes the return damage. Sneak = aim backward.
 */
public final class ArcaneExileSkill implements SkillModule {
    /** Ticks between runes peeling off the exiled body. */
    private static final int PEEL_INTERVAL = 3;

    /** How far a hoop of the cage turns a tick, in radians: a lap in fifteen ticks, about a mote's life. */
    private static final double HOOP_SPIN = 0.42D;

    /** How far each hoop of the cage leans off level, in radians (about 34 degrees). */
    private static final double HOOP_TILT = 0.6D;

    /** How far outside the body's own half-width the cage's hoops run. */
    private static final double HOOP_CLEARANCE = 0.4D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ARCANE_EXILE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                com.efkrdnz.magical.magic.cast.AimResolver.Result aim = ctx.sneak()
                        ? com.efkrdnz.magical.magic.cast.AimResolver.resolve(ctx.level(), ctx.caster(), ctx.look().scale(-1.0D), aimRange(), aimTolerance(), false, 0, null)
                        : ctx.aim();
                LivingEntity victim = aim.living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    SpellFx.decal(ctx.level(), ctx.definition(), aim.point(), aim.normal(), 0.6F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                int ticks = Math.max(20, ctx.duration());
                MagicStatusService.apply(victim, MagicStatus.EXILED, ticks, ctx.definition().id(), ctx.caster());
                SpellEffectEntity anchor = SpellEffectEntity.spawn(ctx, victim.position(), ticks, victim.getBbHeight(), aim.look());
                anchor.setTarget(victim);
                SpellFx.impact(ctx.level(), ctx.definition(), victim.getBoundingBox().getCenter(), aim.normal(), victim, ctx.caster(), 0.8F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 16.0D;
            }

            @Override
            public double aimTolerance() {
                return 1.2D;
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
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                Entity target = entity.target();
                if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                    entity.discard();
                    return;
                }
                entity.setPos(living.position());
                entity.setRadius(living.getBbHeight());
                entity.setValue(1.0F - entity.tickCount / (float) Math.max(1, entity.life()));
                cage(entity, living);
                if (entity.tickCount % PEEL_INTERVAL == 0) {
                    peel(entity, living);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                Entity target = entity.target();
                if (target instanceof LivingEntity living && living.isAlive()) {
                    MagicStatusService.clear(living, MagicStatus.EXILED);
                    SkillTargets.hurt(entity.serverLevel(), entity.owner(), living, entity.damage(), entity.definition(), true);
                }
            }
        };
    }

    /**
     * A rune lifting off the exiled body every few ticks: it is being written out of the fight. The
     * rift says so too, but it is one flat pane - faint over daylight and a line from the side - so
     * after the hit had faded an exiled body read as an ordinary one. One rune at a time, off the
     * side of the body rather than out of its middle, so the victim is never hidden behind them.
     */
    private static void peel(SpellEffectEntity entity, LivingEntity victim) {
        ServerLevel level = entity.serverLevel();
        RandomSource random = level.random;
        double a = random.nextDouble() * Math.PI * 2.0D;
        double reach = victim.getBbWidth() * 0.5D + 0.15D;
        double y = victim.getY() + victim.getBbHeight() * (0.15D + 0.8D * random.nextDouble());
        TintedParticleOptions rune = new TintedParticleOptions(MagicalParticles.RUNE.get(), VisualProfiles.of(entity.definition()).color(ColorRole.BRIGHT), 1.1F);
        // a rune keeps nine tenths of its speed a tick: it lifts about six tenths of a block and fades
        onlookers(level, victim, rune, victim.getX() + Math.cos(a) * reach, y, victim.getZ() + Math.sin(a) * reach, 0, Math.cos(a) * 0.015D, 0.06D, Math.sin(a) * 0.015D, 1.0D);
    }

    /**
     * The cage the exiled body is held in: two hoops of motes turning opposite ways round its
     * middle, one leaning about each horizontal axis, so from any side one reads as a ring and the
     * other as a slanted band across it - a gyroscope with the body inside. A mote a hoop a tick,
     * laid where the hoop has turned to and left there with no speed, so each hoop is a lap of dots
     * that swells behind its head and fades at its tail. The lone runes were too sparse to carry the
     * status on their own: at tick twenty-six an exiled golem looked like an ordinary one. The hoops
     * run outside the body, so it is never hidden.
     */
    private static void cage(SpellEffectEntity entity, LivingEntity victim) {
        ServerLevel level = entity.serverLevel();
        double radius = victim.getBbWidth() * 0.5D + HOOP_CLEARANCE;
        double cx = victim.getX();
        double cy = victim.getY() + victim.getBbHeight() * 0.5D;
        double cz = victim.getZ();
        double lean = Math.sin(HOOP_TILT);
        double flat = Math.cos(HOOP_TILT);
        TintedParticleOptions mote = new TintedParticleOptions(MagicalParticles.MOTE.get(), VisualProfiles.of(entity.definition()).color(ColorRole.BRIGHT), 1.0F);
        // a level ring (cos a, 0, sin a) leant about x: its z half rises and falls
        double a = entity.tickCount * HOOP_SPIN;
        double across = Math.sin(a) * radius;
        onlookers(level, victim, mote, cx + Math.cos(a) * radius, cy + across * lean, cz + across * flat, 0, 0.0D, 0.0D, 0.0D, 0.0D);
        // the other leant about z, turning the other way and a quarter lap behind
        double b = Math.PI * 0.5D - a;
        double along = Math.cos(b) * radius;
        onlookers(level, victim, mote, cx + along * flat, cy + along * lean, cz + Math.sin(b) * radius, 0, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * The cage and the runes, for everybody but the body inside them. Mobs cast this on players, and
     * from an exiled player's own eyes the front of the hoops runs across the bottom of the view
     * under a block away and a peeling rune climbs past the camera - for the whole exile. Anyone
     * else is sent them as usual.
     */
    private static <T extends ParticleOptions> void onlookers(ServerLevel level, LivingEntity victim, T options,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (!(victim instanceof ServerPlayer subject)) {
            level.sendParticles(options, x, y, z, count, dx, dy, dz, speed);
            return;
        }
        for (ServerPlayer viewer : level.players()) {
            if (viewer != subject) {
                level.sendParticles(viewer, options, false, false, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.LOCK).frame(9).band(GlyphKind.DASHED_RING, 9).stamps(StampId.KEY, 3).arcSweep(0.3F, ColorRole.BRIGHT).core(CoreKind.DISC_GLOW).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.rift(Silhouette.Form.VERTICAL_PANE, FxKinds.Rift.GLITCH_CUT, 1.1F, 0.7F, FxKinds.RiftInterior.VOID_BLACK))
                .silhouette(Silhouette.glyph(0.8F).withRole(ColorRole.DIM).withOpacity(0.8F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.LATTICE_GRID, FxKinds.Smoke.HEX_FRAGMENT, FxKinds.Overlay.VIGNETTE)
                .bounds(2.0F, 3.0F, 1.0F);
    }
}
