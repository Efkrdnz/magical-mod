package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.service.Bodies;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SPELL CREATOR (magma_vent + deluge_jet) - LAUNCH_PERIODIC / AIM_SURFACE / VERTICAL_JET_PULSE. A
 * geyser bore cracks open and erupts on a timer: anything above it is launched (allies land softly,
 * enemies are scalded); between eruptions it hisses a slowing steam ring. Sneak = bore under your
 * own feet.
 */
public final class ScaldingGeyserSkill implements SkillModule {
    private static final int PERIOD = 30;
    private static final int ERUPTION = 10;
    private static final double JET_HEIGHT = 8.0D;
    private static final double BORE = 1.2D;
    /**
     * Steam puffs thrown up the jet each tick of an eruption. The jet is the skill: at two a tick it
     * was a dotted line of specks up the sky, where the shader column it replaced was a white pillar.
     * Four a tick, born anywhere up the bottom of the column, fill it from the first tick; the jet
     * only runs a third of the time, so the average stays under two a tick.
     */
    private static final int JET_PUFFS = 4;
    /** How far up the column a puff may be born, so the jet stands at once instead of climbing. */
    private static final double JET_BIRTH = 1.8D;
    /** The bore bursting open at the start of each eruption: puffs rolled out round its lip. */
    private static final int BURST_PUFFS = 8;
    /** Spray kicked round the bore every other tick of an eruption. */
    private static final int BORE_SPRAY = 3;
    /** The water the jet threw up, falling back out of its top once an eruption, and when. */
    private static final int FALLBACK_DROPS = 10;
    private static final int FALLBACK_TICK = 4;
    /** Between eruptions the steam ring breathes one wisp every so many ticks. */
    private static final int HISS_INTERVAL = 4;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SCALDING_GEYSER;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                SpellEffectEntity bore = SpellEffectEntity.spawn(ctx, pos, Math.max(60, ctx.duration()), 2.0F * Math.max(0.6F, ctx.size() / 1.2F), new Vec3(0.0D, 1.0D, 0.0D));
                return CastResult.SUCCESS;
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
                return MobCastProfile.attack(2.0F, 16.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            int t = entity.tickCount % PERIOD;
            boolean erupting = t < ERUPTION;
            entity.setValue(erupting ? 1.0F - t / (float) ERUPTION : 0.0F);
            if (t == 0) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                SpellFx.impact(level, entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.4F);
            }
            if (erupting) {
                double launch = Math.max(0.8D, entity.speed());
                for (LivingEntity e : Bodies.of(level, LivingEntity.class, entity.getBoundingBox().inflate(BORE, JET_HEIGHT, BORE), l -> l.isAlive())) {
                    double dx = e.getX() - entity.getX();
                    double dz = e.getZ() - entity.getZ();
                    if (dx * dx + dz * dz > BORE * BORE || e.getY() < entity.getY() - 0.5D || e.getY() > entity.getY() + JET_HEIGHT) {
                        continue;
                    }
                    Vec3 v = e.getDeltaMovement();
                    e.setDeltaMovement(v.x * 0.5D, Math.max(v.y, launch), v.z * 0.5D);
                    e.hurtMarked = true;
                    if (SkillTargets.isAlly(entity.owner(), e)) {
                        e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
                    } else if (SkillTargets.isHostile(entity.owner(), e) && t == 1) {
                        SkillTargets.hurt(level, entity.owner(), e, entity.damage(), entity.definition(), true);
                        e.clearFire();
                        e.igniteForSeconds(3.0F);
                    }
                }
                spout(level, entity, t);
                return;
            }
            if (t % 5 == 0) {
                for (LivingEntity hostile : SkillTargets.hostilesInCylinder(level, entity.owner(), entity.position(), entity.radius(), 2.0D)) {
                    hostile.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 0, false, false));
                }
            }
            if (t % HISS_INTERVAL == 0) {
                hiss(level, entity.position());
            }
        };
    }

    /**
     * The eruption's matter: steam thrown up the jet as real cloud, spray kicked round the bore, and
     * the water it lifted raining back out of the top. The painter keeps the column's light and only
     * a faint glow at its crown, where an additive orb used to stand in for the mist.
     */
    private static void spout(ServerLevel level, SpellEffectEntity entity, int t) {
        Vec3 bore = entity.position();
        float strength = 1.0F - t / (float) ERUPTION;
        LivingEntity owner = entity.livingOwner();
        // a bore under the caster's own feet would bury their view in its steam: they keep the spray and the rain
        boolean underOwner = owner != null && Math.pow(owner.getX() - bore.x, 2.0D) + Math.pow(owner.getZ() - bore.z, 2.0D) <= BORE * BORE;
        if (!underOwner) {
            for (int i = 0; i < JET_PUFFS; i++) {
                // a cloud keeps 0.96 of its speed a tick: 0.25 to 0.45 carries it five to ten blocks, the jet's height
                double vy = (0.25D + 0.2D * level.random.nextDouble()) * (0.6D + 0.4D * strength);
                level.sendParticles(ParticleTypes.CLOUD, bore.x + jitter(level, 0.3D), bore.y + 0.3D + JET_BIRTH * level.random.nextDouble(), bore.z + jitter(level, 0.3D),
                        0, jitter(level, 0.03D), vy, jitter(level, 0.03D), 1.0D);
            }
            if (t == 0) {
                double offset = level.random.nextDouble() * Math.PI * 2.0D;
                for (int i = 0; i < BURST_PUFFS; i++) {
                    double a = offset + Math.PI * 2.0D * i / BURST_PUFFS;
                    // rolled out low round the lip and lifting: 0.12 across carries a puff about two blocks
                    level.sendParticles(ParticleTypes.CLOUD, bore.x + Math.cos(a) * 0.5D, bore.y + 0.2D, bore.z + Math.sin(a) * 0.5D,
                            0, Math.cos(a), 0.6D, Math.sin(a), 0.12D);
                }
            }
        }
        if ((t & 1) == 0) {
            level.sendParticles(ParticleTypes.SPLASH, bore.x, bore.y + 0.1D, bore.z, BORE_SPRAY, BORE * 0.4D, 0.0D, BORE * 0.4D, 0.0D);
        }
        if (t == FALLBACK_TICK) {
            level.sendParticles(ParticleTypes.FALLING_WATER, bore.x, bore.y + JET_HEIGHT * 0.75D, bore.z, FALLBACK_DROPS, 0.9D, 0.6D, 0.9D, 0.0D);
        }
    }

    /** One wisp of steam off the hissing ring between eruptions, drifting up. */
    private static void hiss(ServerLevel level, Vec3 bore) {
        double a = level.random.nextDouble() * Math.PI * 2.0D;
        double r = 0.6D + 1.2D * level.random.nextDouble();
        level.sendParticles(ParticleTypes.CLOUD, bore.x + Math.cos(a) * r, bore.y + 0.15D, bore.z + Math.sin(a) * r, 0, 0.0D, 1.0D, 0.0D, 0.05D);
    }

    private static double jitter(ServerLevel level, double amount) {
        return (level.random.nextDouble() - 0.5D) * 2.0D * amount;
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.GEYSER).frame(12).band(GlyphKind.TICK_BAND, 30, ColorRole.HOT).band(GlyphKind.WAVE_BAND, 12).band(GlyphKind.TOOTH_BAND, 18).stamps(StampId.FLAME, 6).orbit(3, 0.84F, 3).core(CoreKind.EMBER_PIT).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.custom("scalding_geyser", 8.0F))
                .silhouette(Silhouette.body(Silhouette.Form.PLATE_FAN, FxKinds.Body.MAGMA_ROCK, 6, 1.3F, 0.35F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.MIST_WISP, FxKinds.Overlay.HEAT_SHIMMER)
                .budget(2)
                .bounds(4.0F, 10.0F, 1.0F);
    }
}
