package com.efkrdnz.magical.magic.skill.sword;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.sword.Bind;
import com.efkrdnz.magical.magic.sword.SwordService;
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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.TierProfile;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * SWORD T-3, Sword Rider and above - <b>it takes the frame's origin off your body</b>, and the
 * sneak bit says which of the two endpoints then moves.
 *
 * <p>That is the whole skill, and it is one record field. A press freezes the frame where it
 * stands and the wielder walks out of their own formation ({@link Bind#SET}); a sneak-press slides
 * one station's blade under their feet and the frame carries them ({@link Bind#RIDDEN}). The
 * difference between "my formation stays here" and "I stand on my formation" is which end of
 * {@code Frame.withOrigin} is moving, which is why there is no second mechanism here and no mode
 * on any blade.
 *
 * <p><b>The movement half is not in this file and must never be.</b> The server cannot deliver a
 * velocity to a player's own client: vanilla sends the {@code hasImpulse} motion packet over
 * {@code broadcast} and {@code ChunkMap.TrackedEntity.updatePlayer} never puts a player in their
 * own audience, while {@code hurtMarked} replaces the client's velocity with the server's copy.
 * So {@code client/SwordKeelClient} pushes the rider on {@code PlayerTickEvent.Pre} against the
 * local player only, exactly as {@code SpaceLawClient} does, and everything here is the half a
 * server can actually own: the Edge clock, the mana clock, the leash, the fall-damage exemption
 * and the authoritative bind. There is deliberately no {@code setDeltaMovement} anywhere in this
 * class and {@code SwordKeelGameTests.theServerNeverPushesTheRider} exists to keep it that way.
 *
 * <p>The ride's clock lives here rather than in {@code SwordService.slowTick} because the slow
 * tick is ten ticks wide and this one bills every ten and sheds metal every eighty; a clock that
 * can only fire on multiples of ten cannot express "the ride ends the instant the station empties".
 * {@link Events} is the {@code SwordRiteEvents} shape - the subscribers in one place, the logic
 * out of them - and every arm returns on an empty map before it reads anything else.
 */
public final class TheKeelSkill implements SkillModule {

    /** Mana every {@link #RIDE_MANA_INTERVAL} ticks, on top of the ten the press costs. */
    public static final int RIDE_MANA = 3;

    public static final int RIDE_MANA_INTERVAL = 10;

    /**
     * The ride spends one more sword this often, and it ends the instant there are none left.
     *
     * <p>The one sword taken at the press is the board you are standing on; each of these is the
     * next one, because a wielder cannot keep riding on a formation they have already thrown. It
     * is the whole of the ride's ongoing price beyond the mana, and it is paid in the same
     * currency every other skill in the kit is: the swords are the resource, and the count has
     * been on screen the whole time.
     */
    public static final int RIDE_SWORD_INTERVAL = 80;

    /**
     * The ride's cooldown as a multiple of the press's.
     *
     * <p>The spec prices the press at 30 ticks and the ride at 60. Both ride on one definition, so
     * the ride takes the definition's cooldown doubled rather than a flat 60: a wielder who spent
     * tuning points on this skill should see it on both halves, and {@code payFor} has no cooldown
     * override to hand the number to.
     */
    public static final int RIDE_COOLDOWN_FACTOR = 2;

    /** Ticks after a ride ends during which the ground is not allowed to collect. */
    public static final int FALL_GRACE_TICKS = 40;

    /** One hit of this much ends a ride. A rider is fast, not safe, and a bow answers it. */
    public static final float RIDE_BREAK_DAMAGE = 4.0F;

    /**
     * The cast circle's radius in blocks, and it is the blade under the feet.
     *
     * <p>Every tier below zero resolves to {@code TierProfile.forTier(4)}, whose radius is 3.0
     * and whose {@code throughTerrain} is true - so this skill's {@code GROUND} circle was a
     * six-block disc under the caster with no depth test, drawn over the wielder's own legs and
     * over everything between the floor and the camera. The thing the circle is about is the one
     * station that comes under the feet, and a station is a bearing at
     * {@link Station#REACH_MIN}..{@link Station#REACH_MAX} blocks: one block of radius is the
     * shortest arm in the structure, which is the widest a mark can be and still be a mark of
     * <em>one</em> place to stand rather than of the whole formation.
     *
     * <p>Like the Bearing's, this circle does not draw today: the handler is self-managed, so
     * {@code castViaRegistry} returns before {@code SpellFx.windup}. The number is right anyway.
     */
    public static final float FOOTING_RADIUS = 1.0F;

    /** Live rides, keyed by wielder. Never saved: a ride does not survive a logout or a portal. */
    private static final Map<UUID, Ride> RIDES = new HashMap<>();

    /**
     * One wielder in the air.
     *
     * <p>Mutable and package-private to nothing: it never leaves this file, is never compared and
     * is never written to disk. {@code grace} outlives the ride itself, because the forty ticks of
     * fall-damage exemption are the only part of a ride that matters after it has ended.
     */
    private static final class Ride {
        private final int maxTicks;
        private int ticks;
        private int grace;

        private Ride(int maxTicks) {
            this.maxTicks = maxTicks;
        }
    }

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.THE_KEEL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerPlayer player = ctx.player();
                if (player == null) {
                    return CastResult.HANDLED;
                }
                press(player, ctx.state(), ctx.sneak());
                return CastResult.HANDLED;
            }

            /**
             * Self-managed, so {@code castViaRegistry} returns before it resolves a stat, spends a
             * point of mana, casts the aim ray or starts a clock - and {@code ctx.aim()} is null in
             * here. {@link SwordService#payFor} is the only thing that makes the definition's
             * numbers real; nothing else in the build would ever say they were not.
             */
            @Override
            public boolean selfManaged() {
                return true;
            }

            /** Never null. A null here is an NPE on the server thread inside entity ticking. */
            @Override
            public MobCastProfile mob() {
                return MobCastProfile.NONE;
            }

            @Override
            public TuningView tuning() {
                // Damage is hidden rather than relabelled: the Keel does not hit anything, and a
                // stat that does nothing is worse in the panel than a stat that is absent.
                return new TuningView(false, true, true, true, true,
                        null, "screen.magical.tuning.haste", "screen.magical.tuning.reach",
                        "screen.magical.tuning.hold", "screen.magical.tuning.thrift");
            }
        };
    }

    // ---- the press ------------------------------------------------------------------------------

    /**
     * One press of the Keel: set the frame down, take it back up, or stand on it.
     *
     * <p>Every refusal happens before {@link SwordService#payFor}, so a press that refuses itself
     * is never charged. A press that <em>ends</em> a ride is free and starts no clock on purpose:
     * landing is not a cast, and charging a wielder mana to stop flying is the kind of rule that
     * only ever gets discovered by falling out of the sky with an empty pool.
     */
    public static void press(ServerPlayer player, PlayerMagicState state, boolean sneak) {
        if (!SwordService.holds(state) || !SwordService.rulesFor(state).worldOrigin()) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        if (riding(player)) {
            endRide(player, state);
            state.sync(player);
            return;
        }
        if (sneak) {
            mount(player, state);
            return;
        }
        setDown(player, state);
    }

    /** The press: origin and facing freeze where they were, or come back to the body. */
    private static void setDown(ServerPlayer player, PlayerMagicState state) {
        boolean lower = SwordService.bind(player) != Bind.SET;
        if (!SwordService.payFor(player, state, MagicContent.THE_KEEL, -1)) {
            return;
        }
        if (lower) {
            SwordService.setDown(player);
        } else {
            // Pressing again picks the frame back up. Past KEEL_LEASH the slow tick has already
            // done it for you, and cut every blade's line home on the way.
            SwordService.hold(player);
        }
        SwordService.tendArrayEntity(player, state);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 0.5F, lower ? 1.4F : 1.8F);
        state.sync(player);
    }

    /** The sneak-press: one sword comes under the feet and the frame carries the wielder. */
    private static void mount(ServerPlayer player, PlayerMagicState state) {
        if (SwordService.present(player, state) <= 0) {
            player.displayClientMessage(Component.translatable("message.magical.sword_none_present"), true);
            return;
        }
        MagicSkillResolvedStats stats = MagicContent.THE_KEEL.resolve(state.tuningFor(MagicContent.THE_KEEL.id()));
        if (!SwordService.payFor(player, state, MagicContent.THE_KEEL, -1)) {
            return;
        }
        // payFor wrote the press's clock; the ride's is twice it. Written after, deliberately, so
        // the one place a cooldown is decided for this skill is still payFor plus this one line.
        state.setSkillCooldown(MagicContent.THE_KEEL.id(), stats.cooldownTicks() * RIDE_COOLDOWN_FACTOR);
        SwordService.ride(player);
        // The board: one sword goes under the feet the instant the ride starts, so a wielder can
        // see what it cost before they have travelled a block.
        SwordService.spendSword(player, state);
        SwordService.tendArrayEntity(player, state);
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        RIDES.put(player.getUUID(), new Ride(Math.max(RIDE_SWORD_INTERVAL, stats.durationTicks())));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 1.4F);
        state.sync(player);
    }

    // ---- the ride -------------------------------------------------------------------------------

    /**
     * True while this wielder is standing on one of their own blades.
     *
     * <p>Not simply "has an entry": a landed ride keeps its entry for {@link #FALL_GRACE_TICKS}
     * so the ground cannot collect on the way down, and during those forty ticks the wielder is
     * walking about like anybody else and a fresh sneak-press must be able to lift them again.
     */
    public static boolean riding(ServerPlayer player) {
        Ride ride = RIDES.get(player.getUUID());
        return ride != null && ride.grace <= 0 && ride.ticks < ride.maxTicks;
    }

    /**
     * One tick of one ride, and the four ways it can end.
     *
     * <p>Order matters only in one place: the Edge is shed <em>before</em> the emptiness is
     * checked, so the eightieth tick both spends the last point and ends the ride, rather than
     * ending it eighty ticks later having flown on nothing.
     */
    private static void tickRide(ServerPlayer player, PlayerMagicState state, Ride ride) {
        // No gravity and no accumulated fall: both are re-asserted every tick because anything
        // else in the game may clear them, and a rider who lands on a stale fallDistance dies to
        // a skill that has no damage in it at all.
        player.setNoGravity(true);
        player.fallDistance = 0.0F;

        ride.ticks++;
        if (SwordService.bind(player) != Bind.RIDDEN || ride.ticks >= ride.maxTicks) {
            endRide(player, state);
            state.sync(player);
            return;
        }
        boolean dirty = false;
        if (ride.ticks % RIDE_MANA_INTERVAL == 0) {
            if (!MagicSinService.spendManaForSkill(player, state, RIDE_MANA)) {
                player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
                endRide(player, state);
                state.sync(player);
                return;
            }
            dirty = true;
        }
        if (ride.ticks % RIDE_SWORD_INTERVAL == 0) {
            SwordService.spendSword(player, state);
            dirty = true;
            if (SwordService.present(player, state) <= 0) {
                // Nothing left to stand on. The board went with the last one.
                endRide(player, state);
                state.sync(player);
                return;
            }
        }
        if (dirty) {
            state.sync(player);
        }
    }

    /**
     * The ride is over: gravity back, the frame back on the body, and forty ticks of grace.
     *
     * <p>The grace outlives the {@code Ride} it came from, so the entry stays in the map with its
     * clock running and {@link #riding} answers false for it - the ride is what {@code Bind.RIDDEN}
     * says it is, and this is only the landing.
     */
    private static void endRide(ServerPlayer player, PlayerMagicState state) {
        Ride ride = RIDES.get(player.getUUID());
        if (ride == null) {
            return;
        }
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        ride.grace = FALL_GRACE_TICKS;
        ride.ticks = ride.maxTicks;
        if (SwordService.bind(player) == Bind.RIDDEN) {
            SwordService.hold(player);
        }
        SwordService.tendArrayEntity(player, state);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 0.5F, 1.5F);
    }

    /** Wired to logout, to a change of dimension and to the server stopping. Idempotent. */
    public static void forget(UUID wielder) {
        RIDES.remove(wielder);
    }

    public static void clear() {
        RIDES.clear();
    }

    // ---- what the game calls ----------------------------------------------------------------------

    /**
     * The Keel's subscribers, in one place and with no logic in them.
     *
     * <p>{@code CausalityEvents} and {@code SwordRiteEvents} own their own tick, logout and
     * dimension arms rather than editing {@code MagicGameplayEvents}, for the reason this class
     * shares: a ride is a thing that belongs to one session in one level, and the file that knows
     * what a ride is should be the file that lets go of it. Every arm here returns on an empty map
     * before it reads a level, a player or a state.
     */
    @EventBusSubscriber(modid = MagicalMod.MODID)
    public static final class Events {

        private Events() {}

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            if (RIDES.isEmpty()) {
                return;
            }
            MinecraftServer server = event.getServer();
            RIDES.entrySet().removeIf(entry -> {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null || !player.isAlive()) {
                    return true;
                }
                Ride ride = entry.getValue();
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                if (ride.grace <= 0 && ride.ticks < ride.maxTicks) {
                    tickRide(player, state, ride);
                    return false;
                }
                // The landing: the ground is held off for FALL_GRACE_TICKS and then the entry goes.
                player.fallDistance = 0.0F;
                return --ride.grace <= 0;
            });
        }

        /**
         * One solid hit and the rider is out of the sky.
         *
         * <p>Read-only on the event: the blow lands in full and the ride is what it costs. A
         * cancel or a reduction here would make the Keel a defensive skill, which is the one thing
         * a straight line at a fixed speed with no armour bonus must never be.
         */
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (RIDES.isEmpty() || event.getAmount() < RIDE_BREAK_DAMAGE) {
                return;
            }
            if (event.getEntity() instanceof ServerPlayer player && riding(player)) {
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                endRide(player, state);
                state.sync(player);
            }
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            forget(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
            // The frame does not come through the portal, so neither does the blade under you.
            if (event.getEntity() instanceof ServerPlayer player) {
                player.setNoGravity(false);
            }
            forget(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            clear();
        }
    }

    // ---- what it looks like ------------------------------------------------------------------------

    /**
     * {@code EmblemId.KEEL} is a blade lying flat with a mast and a head standing on it: the
     * frame taken off the body, which is the only thing this skill does. An emblem may not be
     * shared - {@code VisualProfiles.validate} calls a repeated one a <b>hard</b> collision and
     * throws at common setup - so the six SWORD marks are six new constants rather than
     * the commit that adds them, and swapping this word is the whole of that change here.
     *
     * <p>No {@code stamps(...)} layer, in this profile or in any other SWORD one: the school's
     * stamp is {@code StampId.EDGE} at atlas cell 32 and {@code STAMP_BAND}'s {@code paramB} field
     * is five bits wide, so a band cannot carry it. The school bands with {@code TICK_BAND}
     * instead, which is exactly why {@code SchoolMaterial.SWORD} names that kind.
     */
    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SWORD)
                .circle(CircleScript.of(SchoolMaterial.SWORD).emblem(EmblemId.KEEL).frame(5)
                        .band(GlyphKind.TICK_BAND, 30, ColorRole.BRIGHT)
                        .band(GlyphKind.DASHED_RING, 10, ColorRole.DIM)
                        .core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .tier(TierProfile.forTier(definition().tier()).withRadius(FOOTING_RADIUS))
                // A circle lying on the floor under the caster, drawn with no depth test, is
                // painted over the caster's own legs and over anything they are standing behind.
                // Below took the same decision for the same reason.
                .throughTerrain(false)
                .silhouette(Silhouette.body(Silhouette.Form.CAGE, FxKinds.Body.METAL_BANDS, 4, 0.5F, 1.1F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.LATTICE_GRID, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.HEAT_SHIMMER)
                .budget(2)
                .bounds(3.0F, 2.0F, 2.0F);
    }
}
