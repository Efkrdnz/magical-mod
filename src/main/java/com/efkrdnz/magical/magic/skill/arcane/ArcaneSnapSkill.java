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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ARCANE T0 (starter) - LOCK_FACING / HITSCAN_TOUCH / SINGLE_TARGET. Instant 6-block touch: the
 * first living hit takes damage and has its facing pinned for the duration (attacks outside its
 * frontal cone do nothing). Sneak = turn the victim to face away first.
 */
public final class ArcaneSnapSkill implements SkillModule {
    /** Magic-crit sparks cracked off the head as the snap lands. */
    private static final int SNAP_SPARKS = 12;

    /** Ticks between stamps of the lock ring round the pinned head. */
    private static final int LOCK_INTERVAL = 5;

    /** Motes in one stamp of the lock ring. */
    private static final int LOCK_MOTES = 8;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ARCANE_SNAP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity victim = ctx.aim().living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    SpellFx.decal(ctx.level(), ctx.definition(), ctx.aim().point(), ctx.aim().normal(), 0.6F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                if (ctx.sneak()) {
                    // reverse polarity: face directly away from the caster, then pin
                    Vec3 away = victim.position().subtract(ctx.caster().position());
                    float yaw = (float) Math.toDegrees(Math.atan2(-away.x, away.z));
                    victim.setYRot(yaw);
                    victim.yBodyRot = yaw;
                    victim.yHeadRot = yaw;
                }
                int ticks = Math.max(10, ctx.duration());
                MagicStatusService.apply(victim, MagicStatus.FACING_PINNED, ticks, ctx.definition().id(), ctx.caster());
                SkillTargets.hurt(ctx.level(), ctx.caster(), victim, ctx.damage(), ctx.definition(), true);
                snapCrack(ctx.level(), victim);
                SpellEffectEntity pin = SpellEffectEntity.spawn(ctx, victim.getEyePosition(), ticks, 1.0F, victim.getLookAngle());
                pin.setTarget(victim);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 6.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.6D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(0.0F, 6.0F);
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
            // ride the victim's eyes along the frozen bearing
            Entity target = entity.target();
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                entity.finish();
                return;
            }
            entity.setPos(living.getEyePosition());
            double yawRad = Math.toRadians(living.getYRot());
            double pitchRad = Math.toRadians(living.getXRot());
            Vec3 facing = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
            entity.setDirection(facing);
            if ((entity.tickCount - 1) % LOCK_INTERVAL == 0) {
                lockRing(entity, living, facing);
            }
        };
    }

    /**
     * The snap itself: a crack of vanilla's magic-crit sparks off the head, where the facing is
     * pinned. The impact cue carries the hit on the body. At tier zero that cue is a handful of
     * matter, so on its own the starter skill landed as a small blur.
     */
    private static void snapCrack(ServerLevel level, LivingEntity victim) {
        Vec3 eye = victim.getEyePosition();
        // a crit keeps four tenths of the speed it is handed and seven tenths of that a tick: a
        // crack about half a block wide that is gone in under ten ticks
        onlookers(level, victim, ParticleTypes.ENCHANTED_HIT, eye.x, eye.y, eye.z, SNAP_SPARKS, 0.15D, 0.12D, 0.15D, 0.5D);
    }

    /**
     * The lock, drawn round the pinned head square to the bearing it is pinned on: a ring of motes
     * that faces whoever the victim faces and is an edge from the side, so the frozen bearing reads
     * from any angle. The old look was the delivery circle stamped over the face, which an accented
     * hit no longer draws, so the pin had nothing to show for itself once the flash was gone.
     * Stamped again every few ticks, turned half a gap each time, so it shimmers like a dial rather
     * than sitting still. It stands a little in front of the face and wider than the head, so the
     * face is framed and never covered.
     */
    private static void lockRing(SpellEffectEntity entity, LivingEntity victim, Vec3 facing) {
        ServerLevel level = entity.serverLevel();
        Vec3 up = Math.abs(facing.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = facing.cross(up).normalize();
        Vec3 w = u.cross(facing).normalize();
        double radius = 0.3D + victim.getBbWidth() * 0.2D;
        Vec3 centre = victim.getEyePosition().add(facing.scale(0.2D));
        double offset = (entity.tickCount / LOCK_INTERVAL) * Math.PI / LOCK_MOTES;
        TintedParticleOptions mote = new TintedParticleOptions(MagicalParticles.MOTE.get(), VisualProfiles.of(entity.definition()).color(ColorRole.BRIGHT), 0.9F);
        for (int i = 0; i < LOCK_MOTES; i++) {
            double a = offset + i * Math.PI * 2.0D / LOCK_MOTES;
            Vec3 p = centre.add(u.scale(Math.cos(a) * radius)).add(w.scale(Math.sin(a) * radius));
            onlookers(level, victim, mote, p.x, p.y, p.z, 0, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * Particles on the victim's head, for everybody but the victim. Mobs cast this on players, and a
     * pinned player's camera is at that head: the crack would burst out of the eye and a ring a
     * fifth of a block ahead of it sits at the rim of a wide field of view, each a pale pane across
     * the frame. Anyone else is sent them as usual.
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
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.NEEDLE).frame(4).frameRotated(45.0F).band(GlyphKind.TICK_BAND, 24).stamps(StampId.EYE, 4).core(CoreKind.CROSS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.body(Silhouette.Form.CROSSED_BLADES, FxKinds.Body.GLASS, 2, 0.22F, 2.0F))
                .silhouette(Silhouette.mark(FxKinds.Mark.CLOCK_SPOKES, 0.9F, 12).withOffset(-1.55F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.STATIC_GLITCH)
                .bounds(2.0F, 2.5F, 2.5F);
    }
}
