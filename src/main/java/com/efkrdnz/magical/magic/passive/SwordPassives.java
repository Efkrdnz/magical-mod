package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.sword.SwordMath;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.efkrdnz.magical.magic.sword.stance.Watch;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What the formation does to its wielder while they are doing something else.
 *
 * <p>Four passives and not one of them carries a number of its own: every one reads the stance the
 * wielder is standing in and how much steel is with them. Sword Heart makes the mana pool a
 * function of the swords present, so spending one lowers the ceiling and its return raises it.
 * Ward of the Array turns a melee blow aside while the guard is up. Returning halves the clock a
 * spent sword walks home on, and is read by {@code SwordService.returnTicks} rather than here.
 * Mirror of the Array reflects the stance you were last standing in, and is read by
 * {@code StanceWatchService.tick} rather than here.
 *
 * <p><b>Two of the four therefore have no hook in this file, and that is deliberate rather than
 * unfinished.</b> A passive belongs where the thing it changes already lives: a return clock is
 * read in one place and a Watch is dispatched in one place, and re-implementing either here so
 * that all four passives had a method would put the same rule in two files.
 */
public final class SwordPassives implements ClassPassiveHandler {

    /** A blow arriving from nowhere has no line to be answered down. */
    private static final double NEAR_ZERO = 1.0e-6D;

    /**
     * How wide the guard is, in degrees either side of the wielder's look.
     *
     * <p>Wider than a shield and narrower than a circle. Guard's swords stand on an arc round the
     * shoulders rather than in a ring, so a blow from directly behind meets nothing - which is the
     * one piece of counterplay a stance that turns melee has to have.
     */
    public static final double WARD_ARC = 110.0D;

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.SWORD_HEART.id(),
                MagicPassiveContent.WARD_OF_THE_ARRAY.id(),
                // Returning is read by SwordService.returnTicks and Mirror of the Array by
                // StanceWatchService.tick; neither has a hook here. They are claimed anyway
                // because ClassPassiveEffectsTest matches the registry against the handlers in
                // BOTH directions, so an unclaimed id is a red build rather than a quiet one.
                // That is the right trade and this comment is why the list looks odd.
                MagicPassiveContent.RETURNING.id(),
                MagicPassiveContent.MIRROR_OF_THE_ARRAY.id());
    }

    /**
     * Sword Heart: the pool is the steel.
     *
     * <p>{@link ClassPassiveEffects} re-sums this on the slow tick, so the ceiling falls the
     * moment a sword is spent by a volley, a Watch or a shed. The fall is the point - every skill
     * that sends steel away also spends the mage - and it is why {@code PlayerMagicState} has to
     * clamp current mana down with the maximum rather than leave the HUD ring overrunning its own
     * track. Sheathing takes it all: off means gone, so the pool goes with the swords.
     */
    @Override
    public int bonusMaxMana(ServerPlayer player, PlayerMagicState state) {
        if (!state.isPassiveEnabled(MagicPassiveContent.SWORD_HEART.id())) {
            return 0;
        }
        return SwordMath.bonusMaxMana(SwordService.present(player, state));
    }

    /**
     * Ward of the Array: a blow that arrives into the guard is met by a sword, which goes away.
     *
     * <p>The melee half of {@link Watch#INTERCEPT}, and it is here rather than in
     * {@code StanceWatchService} for the same reason the projectile half is there rather than
     * here: an arrow has to be turned before it lands and can only be reached on a tick, while a
     * blow has already landed by the time anything knows of it and can only be reached in the
     * damage event. One behaviour, two hooks, because the game offers two.
     *
     * <p>Both halves share {@code SwordService.watchDue}, so the guard turns one thing per
     * interval whichever kind of thing it was. That is the balance: a Guard under fire from an
     * archer and a swordsman at once does not get to answer both.
     *
     * <p>This runs after vanilla mitigation and before {@code state.absorbDamage}, so a warded hit
     * also saves barrier. That ordering costs nothing and it is the whole defensive identity of
     * the class.
     */
    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        if (amount <= 0.0F || !state.isPassiveEnabled(MagicPassiveContent.WARD_OF_THE_ARRAY.id())) {
            return amount;
        }
        if (!guarding(player, state) || SwordService.present(player, state) <= 0) {
            return amount;
        }
        if (!withinTheGuard(player, source)) {
            return amount;
        }
        boolean relentless = SwordService.rulesFor(state).relentless();
        if (!SwordService.watchDue(player, Watch.INTERCEPT.intervalAt(relentless))) {
            return amount;
        }
        SwordService.spendSword(player, state);
        return Math.max(0.0F, amount - (float) SwordMath.WARD_ABSORB);
    }

    /**
     * Whether the wielder has steel out in a stance that guards - their own, or the reflection.
     *
     * <p>The mirror is asked as well, so Mirror of the Array covers a Sword God who has stepped
     * out of Guard into something else, exactly as it does for every other Watch.
     */
    private static boolean guarding(ServerPlayer player, PlayerMagicState state) {
        if (!state.swordArray().drawn()) {
            return false;
        }
        if (state.swordArray().stance().watch() == Watch.INTERCEPT) {
            return true;
        }
        SwordStance reflected = SwordService.mirrorStance(player);
        return reflected != null && reflected.watch() == Watch.INTERCEPT
                && state.isPassiveEnabled(MagicPassiveContent.MIRROR_OF_THE_ARRAY.id());
    }

    /**
     * Whether whatever dealt the blow is in front of the wielder, inside {@link #WARD_ARC}.
     *
     * <p>False when the source has no position - starvation, a fall, the void - so such a blow is
     * unwardable by construction rather than by a special case: there is nowhere for a sword to
     * have been standing.
     */
    private static boolean withinTheGuard(ServerPlayer player, DamageSource source) {
        Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        Vec3 at = from != null ? from.position() : source.getSourcePosition();
        if (at == null) {
            return false;
        }
        Vec3 offset = at.subtract(player.getX(), player.getY() + SwordService.BODY_CENTRE, player.getZ());
        Vec3 look = player.getLookAngle();
        // Horizontal only: a guard is an arc round the shoulders, and a body standing on a roof is
        // still in front of you.
        double flat = Math.sqrt(offset.x * offset.x + offset.z * offset.z)
                * Math.sqrt(look.x * look.x + look.z * look.z);
        if (flat < NEAR_ZERO) {
            return true;
        }
        return (offset.x * look.x + offset.z * look.z) / flat >= Math.cos(Math.toRadians(WARD_ARC));
    }

    /**
     * Declared although it clears nothing, because {@code handlersDoNotShareScratchAcrossPlayers}
     * reflects over {@code getDeclaredMethods()} and a handler that omits it fails the build. That
     * is the right default: this class keeps no per-player scratch today, and the check is what
     * will notice on the day somebody gives it some.
     *
     * <p>Every clock the four passives read - the return clock, the Watch clock, the mirror clock -
     * is the live half, which belongs to {@link SwordService} and is deliberately never saved; its
     * own {@code forget} is wired beside {@code PileService.forget}.
     */
    @Override
    public void forget(UUID playerId) {
        // No scratch to drop.
    }
}
