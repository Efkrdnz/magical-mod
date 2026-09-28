package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * MAGIC ORIGINATOR (fallen_firmament + glint) - STEER_DARK_ZONE / SKY_LOOK_TRACKED /
 * MOVING_SHADOW_DISC_WITH_BRIGHT_RIM. A black disc hangs over the aim point and follows it: under
 * the shadow enemies are blinded, silenced and pressed; its rim is a blazing annulus that burns
 * anyone crossing it from inside and throws them back in. A moving prison with a burning wall.
 */
public final class TotalEclipseSkill implements SkillModule {
    private static final int WINDUP = 20;
    private static final double HEIGHT = 10.0D;
    private static final double RIM = 1.2D;
    /** Flames licking up somewhere on the burning rim, each tick the shadow is down. */
    private static final int RIM_FLAMES = 2;
    /** The rim catching all at once as the shadow falls: a flame every fifteen degrees. */
    private static final int IGNITION_FLAMES = 24;
    /** The gout of fire off a body burned crossing the rim. */
    private static final int SCORCH_FLAMES = 8;
    /**
     * The shadow's edge sweeping out as it falls: dark wisps in a ring, one every eighteen degrees.
     * A wisp keeps 0.93 of its speed a tick, so 0.6 carries it about eight blocks, out to the rim.
     */
    private static final int SHADOW_WISPS = 20;
    private static final double SHADOW_SPEED = 0.6D;
    private static final float SHADOW_WISP_SCALE = 2.6F;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.TOTAL_ECLIPSE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 ground = ctx.aim().point();
                SpellEffectEntity disc = SpellEffectEntity.spawn(ctx, ground.add(0.0D, HEIGHT, 0.0D), WINDUP + Math.max(60, ctx.duration()), Math.max(4.0F, ctx.size()), new Vec3(0.0D, -1.0D, 0.0D));
                disc.setValue((float) HEIGHT);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 24.0D;
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
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(4.0F, 24.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity owner = entity.livingOwner();
            int t = entity.tickCount;
            if (t < WINDUP) {
                return;
            }
            if (t == WINDUP) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }
            // glide toward the caster's aim point
            if (owner != null && owner.isAlive()) {
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), 48.0D, 0.0D, true, 16, null);
                Vec3 wanted = aim.point();
                Vec3 to = new Vec3(wanted.x - entity.getX(), 0.0D, wanted.z - entity.getZ());
                double step = Math.max(0.1D, entity.speed());
                if (to.length() > step) {
                    to = to.normalize().scale(step);
                }
                entity.setPos(entity.getX() + to.x, entity.getY(), entity.getZ() + to.z);
            }
            Vec3 ground = AimResolver.groundBelow(level, entity.position(), 24);
            double groundY = ground != null ? ground.y : entity.getY() - HEIGHT;
            entity.setValue((float) (entity.getY() - groundY));
            Vec3 base = new Vec3(entity.getX(), groundY, entity.getZ());
            double radius = entity.radius();
            burnRim(level, base, radius, t == WINDUP ? IGNITION_FLAMES : 0);
            if (t == WINDUP) {
                fall(level, entity, base);
            }
            CompoundTag data = entity.serverData();
            for (LivingEntity hostile : SkillTargets.hostilesInCylinder(level, owner, base.subtract(0.0D, 1.0D, 0.0D), radius + RIM + 1.0D, HEIGHT + 2.0D)) {
                double dist = Math.sqrt(Math.pow(hostile.getX() - base.x, 2.0D) + Math.pow(hostile.getZ() - base.z, 2.0D));
                String key = "in_" + hostile.getId();
                boolean wasInside = data.getBoolean(key);
                boolean inside = dist <= radius;
                data.putBoolean(key, inside);
                if (inside) {
                    MagicStatusService.apply(hostile, MagicStatus.DAZZLED, 12, entity.definition().id(), owner);
                    MagicStatusService.apply(hostile, MagicStatus.SILENCED, 12, entity.definition().id(), owner);
                    Vec3 v = hostile.getDeltaMovement();
                    if (v.y > 0.0D) {
                        hostile.setDeltaMovement(v.x, 0.0D, v.z);
                        hostile.hurtMarked = true;
                    }
                    if (t % 10 == 0) {
                        SkillTargets.hurt(level, owner, hostile, 6.0F, entity.definition().id());
                    }
                } else if (wasInside && dist <= radius + RIM + 1.0D) {
                    // crossed the burning rim from inside: burned and thrown back in. Drawn below as a
                    // burn off the wall (its shards and a gout of flame), not a heavy generic impact:
                    // a blast, puffs and a cloud of shadow smoke on a body the fire just threw back
                    SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition(), false);
                    FusionHits.land(level, entity.definition(), hostile, owner);
                    SkillTargets.shove(hostile, base, -0.9D * Math.max(0.5D, entity.knockback()), 0.2D);
                    data.putBoolean(key, true);
                    SpellFx.barrierHit(level, entity.definition(), hostile.getBoundingBox().getCenter(), hostile.position().subtract(base).normalize());
                    Vec3 at = hostile.getBoundingBox().getCenter();
                    double half = hostile.getBbWidth() * 0.5D;
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, SCORCH_FLAMES, half, hostile.getBbHeight() * 0.3D, half, 0.03D);
                }
            }
            if (t % 20 == 0) {
                SpellFx.zoneTickWithin(level, entity.definition(), base, radius);
            }
        };
    }

    /**
     * The shadow falling: a ring of dark wisps swept out along the ground from under the disc to its
     * burning rim as the painter opens the ink shadow, and the impact's sound. A heavy generic impact
     * went off here - a blast, a ring of white puffs and a clump of portal motes that stood on the
     * middle of the shadow for three seconds - where the whole point is that the light goes out.
     */
    private static void fall(ServerLevel level, SpellEffectEntity entity, Vec3 base) {
        VisualProfile profile = VisualProfiles.of(entity.definition());
        ProfileCues.SoundCue cue = profile.sounds().impact();
        if (cue != null) {
            level.playSound(null, base.x, base.y, base.z, cue.sound(), SoundSource.PLAYERS, cue.volume(), cue.pitch());
        }
        TintedParticleOptions shade = new TintedParticleOptions(MagicalParticles.WISP.get(), profile.color(ColorRole.DIM), SHADOW_WISP_SCALE);
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < SHADOW_WISPS; i++) {
            double a = offset + Math.PI * 2.0D * i / SHADOW_WISPS;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            level.sendParticles(shade, base.x + cos * 0.8D, base.y + 0.25D, base.z + sin * 0.8D, 0, cos, 0.0D, sin, SHADOW_SPEED);
        }
    }

    /**
     * The blazing annulus as real fire: a couple of flames licking up somewhere on the rim each
     * tick, so the wall burns all the way round without a flame standing still, and on the tick the
     * shadow falls the whole ring catching at once. The painter's rim is the light of it; the disc,
     * the ink shadow and the lensed stars stay shader.
     */
    private static void burnRim(ServerLevel level, Vec3 base, double radius, int ignition) {
        // the painter draws its burning ring just outside the shadow's edge
        double rim = radius * 1.05D;
        for (int i = 0; i < RIM_FLAMES; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double r = rim + (level.random.nextDouble() - 0.5D) * 0.6D;
            level.sendParticles(ParticleTypes.FLAME, base.x + Math.cos(a) * r, base.y + 0.1D, base.z + Math.sin(a) * r,
                    0, 0.0D, 1.0D, 0.0D, 0.03D + 0.03D * level.random.nextDouble());
        }
        for (int i = 0; i < ignition; i++) {
            double a = Math.PI * 2.0D * i / ignition;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            level.sendParticles(ParticleTypes.FLAME, base.x + cos * rim, base.y + 0.1D, base.z + sin * rim, 0, cos * 0.3D, 1.0D, sin * 0.3D, 0.08D);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .palette(2)
                // a shadow is smoke and ink, not portal motes: a reverse-portal mote hardly moves for
                // three seconds, and the void accent left a still magenta clump in the middle of it
                .accent(Accent.GLOOM)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.ECLIPSE).frame(16).band(GlyphKind.SOLID_RING, 1, ColorRole.INK).band(GlyphKind.TICK_BAND, 72, ColorRole.DIM).band(GlyphKind.PETAL_BAND, 12, ColorRole.HOT).stamps(StampId.CRESCENT, 12).orbit(7, 0.86F, 4).core(CoreKind.VOID_PIT).stack(3, 0.6F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.SKY)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("total_eclipse", 9.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.VIGNETTE)
                .budget(3)
                .bounds(12.0F, 3.0F, 14.0F);
    }
}
