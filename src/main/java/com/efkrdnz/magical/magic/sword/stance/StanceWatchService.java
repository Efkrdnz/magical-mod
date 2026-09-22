package com.efkrdnz.magical.magic.sword.stance;

import com.efkrdnz.magical.entity.SkillClashEffectEntity;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.incantation.VersePassives;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.sword.SwordService;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the swords do while nobody is pressing anything.
 *
 * <p>The half of the class the wielder asked for by name - <em>swords doing things by
 * themselves</em> - and the reason a stance is a decision rather than a skin. One behaviour per
 * stance, chosen by {@link SwordStance#watch()}, and the whole of the difference between them is
 * the four numbers on the {@link Watch} constant plus one branch here.
 *
 * <p><b>It runs on {@code SwordArrayEntity.tick}, every tick, and not on the slow tick.</b> Two
 * reasons, and the first is not negotiable: an arrow has to be turned <em>before</em> it lands
 * rather than discounted afterwards, and ten ticks is eight blocks of arrow. The second is free
 * gating - that entity exists only while the steel is out, so a wielder with no class and a
 * wielder with their swords away both pay nothing for any of this.
 *
 * <p>Three of the six spend a sword and three do not, and where one is spent it is spent
 * <em>after</em> the effect has been established, so a Watch that found nothing to do is a Watch
 * that cost nothing.
 */
public final class StanceWatchService {

    /** Degrees either side of the look that {@link Watch#STAB} will pick a body out of. */
    public static final double STAB_CONE = 22.0D;

    /** How far above a body {@link Watch#DROP} starts its sword. */
    public static final double DROP_HEIGHT = Pattern.FALL_HEIGHT;

    /** Blocks a tick a dropped sword falls. Fast enough to read as a drop, slow enough to see. */
    public static final double DROP_SPEED = 1.1D;

    /** Ticks a dropped sword is given to reach what it was aimed at. */
    public static final int DROP_FLIGHT = 20;

    /** Blocks a tick a stabbing sword travels. */
    public static final double STAB_SPEED = 1.6D;

    public static final int STAB_FLIGHT = 24;

    /** How hard {@link Watch#SHRED} throws what it cuts. */
    public static final double SHRED_SHOVE = 0.55D;

    public static final double SHRED_LIFT = 0.18D;

    /** The flare a turned projectile leaves, the same size the Halo verse uses. */
    private static final float FLARE_SCALE = 0.22F;

    private static final int FLARE_LIFE = 6;

    /** Refreshed while falling, so the posture ends the instant the stance changes. */
    private static final int GLIDE_DURATION = 40;

    /** Mirror of the Array runs the reflected Watch at half rate. */
    public static final int MIRROR_SLOWDOWN = 2;

    private StanceWatchService() {
    }

    /**
     * One tick of whatever the current stance does by itself.
     *
     * <p>Called from the formation entity, which only exists while the steel is out. Nothing here
     * checks that again.
     */
    public static void tick(ServerLevel level, ServerPlayer wielder, PlayerMagicState state) {
        SwordStance stance = state.swordArray().stance();
        SwordService.noteStance(wielder, stance);
        boolean relentless = SwordService.rulesFor(state).relentless();
        run(level, wielder, state, stance.watch(), relentless, false);

        // Mirror of the Array: the stance you were standing in a moment ago is still standing
        // behind you, at half rate. The apex rung grants no active, so this is what it is for -
        // and it is the one thing in the kit that makes a stance change additive rather than a
        // swap, which is why the passive is worth a rung on its own.
        SwordStance reflected = SwordService.mirrorStance(wielder);
        if (reflected != null && reflected != stance
                && state.isPassiveEnabled(MagicPassiveContent.MIRROR_OF_THE_ARRAY.id())) {
            run(level, wielder, state, reflected.watch(), relentless, true);
        }
    }

    /**
     * One Watch, once, on whichever of the two clocks it belongs to.
     *
     * <p>{@code mirror} picks the clock and halves the rate. Two clocks and not one, because a
     * reflection sharing the wielder's own clock would take every other turn from the stance they
     * are actually standing in - a passive that makes your chosen stance worse is not a reward.
     */
    private static void run(ServerLevel level, ServerPlayer wielder, PlayerMagicState state,
            Watch watch, boolean relentless, boolean mirror) {
        int interval = watch.intervalAt(relentless) * (mirror ? MIRROR_SLOWDOWN : 1);
        switch (watch) {
            // Not on a clock: a posture is a state of the wielder and answers every tick or never.
            case GLIDE -> glide(wielder);
            // Its own gate, because the scan has to be every tick even though the spend is not.
            case INTERCEPT -> intercept(level, wielder, state, watch, interval, mirror);
            case STAB -> {
                if (due(wielder, interval, mirror)) {
                    stab(level, wielder, state, watch);
                }
            }
            case SHEAR -> {
                if (due(wielder, interval, mirror)) {
                    sweep(level, wielder, state, watch, false);
                }
            }
            case SHRED -> {
                if (due(wielder, interval, mirror)) {
                    sweep(level, wielder, state, watch, true);
                }
            }
            case DROP -> {
                if (due(wielder, interval, mirror)) {
                    drop(level, wielder, state, watch);
                }
            }
        }
    }

    private static boolean due(ServerPlayer wielder, int interval, boolean mirror) {
        return mirror ? SwordService.mirrorWatchDue(wielder, interval)
                : SwordService.watchDue(wielder, interval);
    }

    // ---- Guard -------------------------------------------------------------------------------

    /**
     * A closing projectile is met by a sword, which goes away for its trouble.
     *
     * <p>Half of the old Ward of the Array, moved off the lattice onto the formation. It used to
     * ask which <em>bearing</em> covered the incoming line, so the wielder who had spent four
     * presses covering their flanks was the one whose flanks were covered - a good rule that
     * depended entirely on a shape nobody could see. The stance is the choice now: standing in
     * Guard is choosing to be covered, and the price is a sword rather than a press.
     *
     * <p><b>No passive gates this.</b> Guard is a Summoner stance and the rung's whole clause is
     * that the swords act on their own, so gating the one thing Guard does behind a passive the
     * Rider grants would have left the base rung's headline stance doing nothing at all. Ward of
     * the Array buys the <em>other</em> half - a melee blow discounted - which lives in
     * {@code SwordPassives.incomingDamage} because a blow can only be answered in the damage
     * event, exactly as an arrow can only be answered before it lands.
     *
     * <p>The scan runs every tick and the spend is on the interval, which is why this one does not
     * go through the shared clock at the top: gating the scan would let an arrow through, and
     * arming the clock on a tick that found nothing would make the guard miss the one that
     * mattered. So the clock is consulted only once a target is in hand.
     */
    private static void intercept(ServerLevel level, ServerPlayer wielder, PlayerMagicState state,
            Watch watch, int interval, boolean mirror) {
        if (SwordService.present(wielder, state) <= 0) {
            return;
        }
        Vec3 centre = wielder.getBoundingBox().getCenter();
        AABB box = new AABB(centre, centre).inflate(watch.range());
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, box,
                one -> one.isAlive() && one.getOwner() != wielder)) {
            Vec3 offset = shot.position().subtract(centre);
            if (offset.lengthSqr() > watch.range() * watch.range()
                    || !VersePassives.closing(shot.getDeltaMovement(), offset)) {
                continue;
            }
            if (!due(wielder, interval, mirror)) {
                return;
            }
            if (!shot.deflect(ProjectileDeflection.REVERSE, wielder, wielder, true)) {
                return;
            }
            SwordService.spendSword(wielder, state);
            flare(level, shot.position());
            state.sync(wielder);
            return;
        }
    }

    // ---- Vanguard ----------------------------------------------------------------------------

    /** The body the wielder is looking at is darted at by one sword, which returns on the clock. */
    private static void stab(ServerLevel level, ServerPlayer wielder, PlayerMagicState state,
            Watch watch) {
        LivingEntity mark = underTheCrosshair(level, wielder, watch.range());
        if (mark == null || SwordService.present(wielder, state) <= 0) {
            return;
        }
        Vec3 from = launchPoint(wielder, state);
        Vec3 to = mark.getBoundingBox().getCenter();
        SwordBladeEntity blade = SwordBladeEntity.loose(level, wielder, MagicContent.CALL_THE_BLADE.id(),
                from, to.subtract(from), 0, 1, watch.bite(), 0.15D, STAB_SPEED, STAB_FLIGHT);
        if (blade == null) {
            return;
        }
        SwordService.spendSword(wielder, state);
        state.sync(wielder);
    }

    /**
     * The nearest living body inside {@link #STAB_CONE} of the look, or null.
     *
     * <p>A cone rather than a ray, because a ray through a moving target at twenty blocks is a
     * skill that works when you stand still and does nothing when either of you moves.
     */
    private static LivingEntity underTheCrosshair(ServerLevel level, ServerPlayer wielder, double range) {
        Vec3 eye = wielder.getEyePosition();
        Vec3 look = wielder.getLookAngle();
        double cone = Math.cos(Math.toRadians(STAB_CONE));
        LivingEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, eye).inflate(range), one -> one.isAlive() && one != wielder)) {
            Vec3 offset = body.getBoundingBox().getCenter().subtract(eye);
            double distance = offset.length();
            if (distance < 1.0E-4D || distance > range) {
                continue;
            }
            if (offset.scale(1.0D / distance).dot(look) < cone) {
                continue;
            }
            if (distance < nearest) {
                nearest = distance;
                best = body;
            }
        }
        return best;
    }

    // ---- Crown and Coil ------------------------------------------------------------------------

    /**
     * Everything hostile inside the formation's own radius is cut.
     *
     * <p>Costs no sword, because the ring <em>is</em> the attack: nothing leaves, so nothing has
     * to come back. That is why {@link Watch#SHEAR} and {@link Watch#SHRED} carry the two smallest
     * bites in the enum - they are the only two that can fire forever.
     */
    private static void sweep(ServerLevel level, ServerPlayer wielder, PlayerMagicState state,
            Watch watch, boolean shove) {
        if (SwordService.present(wielder, state) <= 0) {
            return;
        }
        Vec3 centre = wielder.getBoundingBox().getCenter();
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre, centre).inflate(watch.range()),
                one -> one.isAlive() && one != wielder);
        if (caught.isEmpty()) {
            return;
        }
        for (LivingEntity body : caught) {
            SkillTargets.hurt(level, wielder, body, watch.bite(), MagicContent.CALL_THE_BLADE.id());
            if (shove) {
                SkillTargets.shove(body, centre, SHRED_SHOVE, SHRED_LIFT);
            }
        }
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.35F, 1.4F);
    }

    // ---- Wings --------------------------------------------------------------------------------

    /**
     * The fans carry the wielder down.
     *
     * <p>Slow falling rather than a written velocity, and that is forced rather than chosen: the
     * server cannot deliver a velocity to a player's own client at all - vanilla sends the
     * {@code hasImpulse} packet over {@code broadcast} and
     * {@code ChunkMap.TrackedEntity.updatePlayer} never puts a player in their own audience -
     * which is the whole reason the Keel's carry lives in {@code SwordKeelClient}. A mob effect is
     * a thing the server <em>can</em> hand over, the client honours it, and it costs this file no
     * second half to keep in step.
     *
     * <p>The fall distance is reset as well. The effect alone would leave a wielder who took the
     * stance mid-drop still carrying the metres they had already fallen.
     */
    private static void glide(ServerPlayer wielder) {
        if (wielder.onGround() || wielder.isFallFlying()) {
            return;
        }
        wielder.resetFallDistance();
        if (wielder.getDeltaMovement().y < 0.0D) {
            wielder.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, GLIDE_DURATION, 0,
                    true, false, false));
        }
    }

    // ---- Rain ----------------------------------------------------------------------------------

    /**
     * A body the wielder has just wounded has a sword come down on it.
     *
     * <p>A reaction and not a turret, which is the difference between Rain and the two sweeps: it
     * fires only at something the wielder themselves hit inside
     * {@code SwordService.VICTIM_MEMORY_TICKS}, so the stance rewards committing to a target
     * rather than standing in a crowd.
     */
    private static void drop(ServerLevel level, ServerPlayer wielder, PlayerMagicState state,
            Watch watch) {
        LivingEntity mark = SwordService.recentVictim(wielder);
        if (mark == null || SwordService.present(wielder, state) <= 0) {
            return;
        }
        Vec3 to = mark.getBoundingBox().getCenter();
        if (to.distanceTo(wielder.getBoundingBox().getCenter()) > watch.range()) {
            return;
        }
        Vec3 from = to.add(0.0D, DROP_HEIGHT, 0.0D);
        SwordBladeEntity blade = SwordBladeEntity.loose(level, wielder, MagicContent.CALL_THE_BLADE.id(),
                from, new Vec3(0.0D, -1.0D, 0.0D), 0, 1, watch.bite(), 0.1D, DROP_SPEED, DROP_FLIGHT);
        if (blade == null) {
            return;
        }
        SwordService.spendSword(wielder, state);
        state.sync(wielder);
    }

    // ---- shared ---------------------------------------------------------------------------------

    /** Where a sword leaving the formation starts: its own slot if it has one, else the eye. */
    private static Vec3 launchPoint(ServerPlayer wielder, PlayerMagicState state) {
        List<Vec3> present = SwordService.presentPositions(wielder, state);
        return present.isEmpty() ? wielder.getEyePosition() : present.get(0);
    }

    private static void flare(ServerLevel level, Vec3 at) {
        int color = MagicContent.CALL_THE_BLADE.color();
        level.addFreshEntity(SkillClashEffectEntity.create(level, at, color, color, FLARE_LIFE, FLARE_SCALE));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6F, 1.5F);
    }
}
