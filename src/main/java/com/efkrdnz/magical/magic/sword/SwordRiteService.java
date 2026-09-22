package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Answering: the only way a Sword Summoner is found in normal play.
 *
 * <p>There is no command, no worldgen, no structure, no advancement, no new block and no new item
 * behind this class. There is a <b>breadcrumb</b> that costs nothing and is live for every player
 * from the day the mod loads - a sword sneak-dropped at night under open sky hangs for
 * {@link #HANG_TICKS} ticks with a thin edge of light down it before it falls - and there is a
 * <b>rite</b> that is the breadcrumb done four times on purpose. Anyone who has ever fumbled
 * Ctrl+Q outdoors has seen the first. Only somebody who decided to has seen the second.
 *
 * <p><b>The payoff is the whole reason this rite was chosen over the three others proposed, and it
 * is the part to get right.</b> The four swords do not merely unlock the class: they <em>stand
 * up</em>. The ceremony ends with the class taken and the steel already out, so the first thing
 * the new wielder ever sees is their own formation standing where the four items were lying - the
 * four swords they laid on the ground, on their feet, following them. The unlock is the first
 * Call the Blade, performed with your feet, and four is exactly the base rung's complement.
 *
 * <p>It used to be more than that: the four bearings the swords were lying at were quantised onto
 * a lattice and written in as the wielder's first four stations. That went with the lattice. The
 * shape is chosen from six designed stances now rather than authored a press at a time, and a
 * rite that handed you a shape you could no longer edit would have been handing you a fossil.
 *
 * <p><b>Every refusal is silent.</b> The swords simply fall and nothing is consumed. The player is
 * never told which condition they missed, because a rite that explains itself is a recipe, and the
 * value of this one is that it was found.
 *
 * <p>Nothing here is saved. The toss map is per-wielder, in memory, dropped on logout and on a
 * change of dimension, exactly as {@code PileService} drops its Piles - a rite half-performed is a
 * thing that was happening, not a thing that is owned. What survives a login is the
 * {@link SwordArray} the rite wrote, which lives on {@code PlayerMagicState} like every other
 * authored half in the mod.
 */
public final class SwordRiteService {

    // ---- the breadcrumb ------------------------------------------------------------------------

    /** How long a sneak-dropped sword refuses to tumble. Long enough to notice, short enough to miss. */
    public static final int HANG_TICKS = 8;

    // ---- the rite ------------------------------------------------------------------------------

    /** Four, and four is also {@code SwordRules.SUMMONER.swords()}. That is not a coincidence. */
    public static final int RITE_SWORDS = 4;

    /** Ticks from the first of the four to the fourth coming to rest. Twenty seconds of walking. */
    public static final int RITE_WINDOW = 400;

    /** Blocks between every pair. Two swords in one heap are one sword as far as a bearing goes. */
    public static final double RITE_MIN_SEPARATION = 2.0D;

    /** Blocks from the centroid every sword must lie within. The honest lever if this ever fires too often. */
    public static final double RITE_RADIUS = 8.0D;

    /** Blocks from the centroid the thrower must be standing. You stand in your own Array or it is not yours. */
    public static final double RITE_CENTRE = 3.0D;

    /** How long the four hang before they are spent. Forty ticks is two seconds of it being obvious. */
    public static final int CEREMONY_TICKS = 40;

    /** How far they rise while they hang. */
    public static final double RITE_LIFT = 1.2D;

    // ---- the sweep -----------------------------------------------------------------------------

    /**
     * How often the candidates are examined.
     *
     * <p>The expensive half of this service runs at a twentieth of the rate of the cheap half:
     * only wielders holding {@link #RITE_SWORDS} or more live tosses are looked at, and everything
     * a look costs - the sky test, the pairwise separation, the centroid - happens here and
     * nowhere else. The per-tick pass touches the handful of swords that are actually hanging.
     */
    private static final int SWEEP_INTERVAL = 20;

    /**
     * The most tosses one wielder is tracked through at once.
     *
     * <p>The window alone does not bound this: a player standing in a sword farm could push
     * hundreds of entries into one list inside four hundred ticks. Twice the rite is enough to
     * hold "the four most recent" without letting the map become a leak.
     */
    private static final int MAX_OFFERINGS = 8;

    /** Horizontal speed squared under which a dropped sword counts as having come to rest. */
    private static final double REST_HORIZONTAL_SQR = 1.0e-4D;

    private static final double SEPARATION_SQR = RITE_MIN_SEPARATION * RITE_MIN_SEPARATION;

    private static final double RADIUS_SQR = RITE_RADIUS * RITE_RADIUS;

    private static final double CENTRE_SQR = RITE_CENTRE * RITE_CENTRE;

    // ---- the light -----------------------------------------------------------------------------

    private static final int EDGE_MOTES = 3;

    /** Half the length of the drawn edge, as the spread handed to the particle sender. */
    private static final double EDGE_HALF_LENGTH = 0.45D;

    private static final double EDGE_SPREAD = 0.02D;

    private static final double EDGE_RISE = 0.2D;

    private static final int SEAL_MOTES = 18;

    /**
     * One sword being watched, and when it left the hand.
     *
     * <p>The entity is held directly rather than by id because every read of it is on the server
     * thread with the level in hand, and every read checks {@link ItemEntity#isRemoved()} first -
     * which is also how "none has been picked back up" is answered, since a pickup discards the
     * entity.
     */
    private record Offering(ItemEntity item, int tossedAt) {}

    /**
     * A rite that has been accepted and is now happening.
     *
     * <p>{@code rest} is where each sword came to rest, frozen at the instant the fourth of them
     * did, and it is what the lift is drawn against: a wielder who walks away while the blades are
     * rising did not move the swords, so the ceremony stays where it was answered.
     */
    private record Rite(ResourceKey<Level> dimension, List<ItemEntity> blades, List<Vec3> rest,
                        int startedAt) {}

    /** A single sword refusing to tumble, and the tick it gives up at. */
    private record Hang(ItemEntity item, int until) {}

    private static final Map<UUID, List<Offering>> OFFERED = new HashMap<>();

    private static final Map<UUID, Rite> RITES = new HashMap<>();

    private static final List<Hang> HANGING = new ArrayList<>();

    /**
     * The last tick this service ran, and the reason it is here is not tidiness.
     *
     * <p>{@link #tick} is called from {@code SwordRiteEvents}, and the file manifest also invites
     * a second call beside {@code PileService.tick} in {@code MagicGameplayEvents}. Two calls in
     * one server tick would run the ceremony at double speed and halve every window in the file,
     * with a green build and nothing logged. One comparison makes the second call free.
     */
    private static int lastTick = -1;

    private SwordRiteService() {}

    // ---- what the event handler asks -----------------------------------------------------------

    /**
     * Whether this stack is a blade the rite will look at, and it is the handler's first field read.
     *
     * <p>{@code #minecraft:swords} or anything carrying the {@code magical:forged_weapon}
     * component, which is how a forged weapon is identified everywhere else in the mod. Almost
     * every toss in the game fails here and costs one tag lookup.
     */
    public static boolean isRiteBlade(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || ForgedWeapons.isForged(stack));
    }

    /**
     * A sword has left a player's hand.
     *
     * <p>Only a <em>sneak</em>-drop is watched. Q is a thing players do by accident all day;
     * Shift+Q is a thing they did. Death drops do not come through {@code ItemTossEvent} at all,
     * which is correct and is the single strongest guard in the rite: what you were carrying when
     * you died cannot answer for you.
     */
    public static void tossed(ServerPlayer player, ItemEntity item) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isShiftKeyDown()) {
            return;
        }
        int now = server.getTickCount();
        ServerLevel level = player.serverLevel();
        if (omenHolds(level, item.blockPosition())) {
            hang(item, now);
        }
        List<Offering> offerings = OFFERED.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
        offerings.add(new Offering(item, now));
        prune(offerings, now);
    }

    /**
     * The cheap pass every tick and the expensive one every twentieth.
     *
     * <p>Returns on its first line for every server in the world that has no sword hanging, no
     * rite running and nobody being watched, which is almost all of them almost always.
     */
    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now == lastTick) {
            return;
        }
        lastTick = now;
        if (HANGING.isEmpty() && RITES.isEmpty() && OFFERED.isEmpty()) {
            return;
        }
        holdTheHanging(now);
        advance(server, now);
        if (now % SWEEP_INTERVAL == 0) {
            sweep(server, now);
        }
    }

    /**
     * Nothing of a rite outlives the wielder's session or survives their leaving the dimension.
     *
     * <p>A rite in progress is released rather than dropped: its swords get their weight back and
     * fall where they were, which is the same thing every other refusal in this file does.
     */
    public static void forget(UUID wielder) {
        OFFERED.remove(wielder);
        Rite rite = RITES.remove(wielder);
        if (rite != null) {
            release(rite);
        }
    }

    public static void clear() {
        OFFERED.clear();
        for (Rite rite : RITES.values()) {
            release(rite);
        }
        RITES.clear();
        for (Hang hang : HANGING) {
            hang.item().setNoGravity(false);
        }
        HANGING.clear();
        lastTick = -1;
    }

    // ---- the breadcrumb ------------------------------------------------------------------------

    /**
     * Night, open sky, no rain - and the same three questions the rite asks.
     *
     * <p>They are deliberately one method rather than two similar ones. A breadcrumb that showed
     * itself in the rain would be teaching a rite that refuses in the rain, and the whole of this
     * feature's design is that the small thing is an honest demonstration of the large one.
     */
    private static boolean omenHolds(ServerLevel level, BlockPos pos) {
        return !level.isDay() && !level.isRaining() && level.canSeeSky(pos);
    }

    private static void hang(ItemEntity item, int now) {
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        HANGING.add(new Hang(item, now + HANG_TICKS));
    }

    private static void holdTheHanging(int now) {
        if (HANGING.isEmpty()) {
            return;
        }
        Iterator<Hang> held = HANGING.iterator();
        while (held.hasNext()) {
            Hang hang = held.next();
            ItemEntity item = hang.item();
            if (item.isRemoved() || now >= hang.until()) {
                item.setNoGravity(false);
                held.remove();
                continue;
            }
            item.setDeltaMovement(Vec3.ZERO);
            if (item.level() instanceof ServerLevel level) {
                paintEdge(level, item.position());
            }
        }
    }

    /** A thin edge of light down the blade: a tight column of motes, not a puff around it. */
    private static void paintEdge(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + EDGE_RISE, at.z,
                EDGE_MOTES, EDGE_SPREAD, EDGE_HALF_LENGTH, EDGE_SPREAD, 0.0D);
    }

    // ---- the sweep -----------------------------------------------------------------------------

    /**
     * Who is holding four or more live tosses, and does their fourth answer every question.
     *
     * <p>Wielders with fewer than four, and wielders already in a ceremony, cost one integer
     * comparison here and nothing else.
     */
    private static void sweep(MinecraftServer server, int now) {
        if (OFFERED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, List<Offering>>> watched = OFFERED.entrySet().iterator();
        while (watched.hasNext()) {
            Map.Entry<UUID, List<Offering>> entry = watched.next();
            List<Offering> offerings = entry.getValue();
            prune(offerings, now);
            if (offerings.isEmpty()) {
                watched.remove();
                continue;
            }
            if (offerings.size() < RITE_SWORDS || RITES.containsKey(entry.getKey())) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                begin(player, offerings, now);
            }
        }
    }

    /** Drops what has been picked up, what has burned, and what is older than the window. */
    private static void prune(List<Offering> offerings, int now) {
        offerings.removeIf(offering -> offering.item().isRemoved()
                || offering.item().getItem().isEmpty()
                || now - offering.tossedAt() > RITE_WINDOW);
        while (offerings.size() > MAX_OFFERINGS) {
            offerings.remove(0);
        }
    }

    /**
     * Every condition in the rite, asked in the cheapest order, and silent on every no.
     *
     * <p>The wielder's own gates come first because they are two map reads and they exclude almost
     * everybody: a player who has not chosen a starting class cannot take a root that nothing ever
     * offered them, and a player who already holds the class has nothing to find. That second gate
     * is also the only thing standing between a Sword Summoner and their own Array being rewritten
     * by four swords they happened to drop.
     */
    private static void begin(ServerPlayer player, List<Offering> offerings, int now) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAnyRootClass() || state.hasClass(MagicalClasses.SWORD_SUMMONER)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.isDay() || level.isRaining()) {
            return;
        }

        List<Offering> four = theFourMostRecentAtRest(offerings, level);
        if (four == null || now - four.get(0).tossedAt() > RITE_WINDOW) {
            return;
        }

        List<Vec3> rest = new ArrayList<>(RITE_SWORDS);
        for (Offering offering : four) {
            ItemEntity blade = offering.item();
            if (!level.canSeeSky(blade.blockPosition())) {
                return;
            }
            rest.add(blade.position());
        }
        if (!spreadOut(rest) || !gathered(rest, player.position())) {
            return;
        }

        List<ItemEntity> blades = new ArrayList<>(RITE_SWORDS);
        for (Offering offering : four) {
            ItemEntity blade = offering.item();
            blade.setNoGravity(true);
            blade.setDeltaMovement(Vec3.ZERO);
            blade.setNeverPickUp();
            blades.add(blade);
            HANGING.removeIf(hang -> hang.item() == blade);
        }
        offerings.removeAll(four);
        RITES.put(player.getUUID(), new Rite(level.dimension(), blades, rest, now));
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 0.6F);
    }

    /**
     * The {@link #RITE_SWORDS} most recently thrown swords that are lying still in this level, in
     * the order they were thrown, or null when there are not that many.
     *
     * <p>Most recent rather than first, so a wielder who threw a fifth sword is answered on the
     * four they finished with rather than refused for the one they started with. "The fourth comes
     * to rest" is then literally what the last element of this list is.
     */
    private static List<Offering> theFourMostRecentAtRest(List<Offering> offerings, ServerLevel level) {
        List<Offering> four = new ArrayList<>(RITE_SWORDS);
        for (int i = offerings.size() - 1; i >= 0 && four.size() < RITE_SWORDS; i--) {
            Offering offering = offerings.get(i);
            ItemEntity blade = offering.item();
            if (blade.level() == level && blade.onGround()
                    && blade.getDeltaMovement().horizontalDistanceSqr() < REST_HORIZONTAL_SQR) {
                four.add(0, offering);
            }
        }
        return four.size() == RITE_SWORDS ? four : null;
    }

    /** Every pair at least {@link #RITE_MIN_SEPARATION} apart: four bearings, not one heap. */
    private static boolean spreadOut(List<Vec3> rest) {
        for (int i = 0; i < rest.size(); i++) {
            for (int j = i + 1; j < rest.size(); j++) {
                if (rest.get(i).distanceToSqr(rest.get(j)) < SEPARATION_SQR) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Every sword inside {@link #RITE_RADIUS} of the centroid, and the thrower inside {@link #RITE_CENTRE}. */
    private static boolean gathered(List<Vec3> rest, Vec3 thrower) {
        Vec3 centroid = Vec3.ZERO;
        for (Vec3 lying : rest) {
            centroid = centroid.add(lying);
        }
        centroid = centroid.scale(1.0D / rest.size());
        for (Vec3 lying : rest) {
            if (lying.distanceToSqr(centroid) > RADIUS_SQR) {
                return false;
            }
        }
        return thrower.distanceToSqr(centroid) <= CENTRE_SQR;
    }

    // ---- the ceremony --------------------------------------------------------------------------

    private static void advance(MinecraftServer server, int now) {
        if (RITES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Rite>> running = RITES.entrySet().iterator();
        while (running.hasNext()) {
            Map.Entry<UUID, Rite> entry = running.next();
            Rite rite = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()
                    || !player.level().dimension().equals(rite.dimension())
                    || anyBladeGone(rite)) {
                release(rite);
                running.remove();
                continue;
            }
            int elapsed = now - rite.startedAt();
            hold(player.serverLevel(), rite, Math.min(1.0D, elapsed / (double) CEREMONY_TICKS));
            if (elapsed >= CEREMONY_TICKS) {
                seal(player, rite);
                running.remove();
            }
        }
    }

    /**
     * The four rise {@link #RITE_LIFT} blocks over the ceremony and hang exactly where they lay.
     *
     * <p>They rise in place rather than gathering on the thrower, because the four are still items
     * on the ground until the seal - what gathers is the formation, and it does that in one motion
     * at the end where it reads as the swords having stood up rather than as them having walked.
     */
    private static void hold(ServerLevel level, Rite rite, double progress) {
        double lift = RITE_LIFT * ease(progress);
        for (int i = 0; i < rite.blades().size(); i++) {
            ItemEntity blade = rite.blades().get(i);
            Vec3 lying = rite.rest().get(i);
            blade.setDeltaMovement(Vec3.ZERO);
            blade.setPos(lying.x, lying.y + lift, lying.z);
            paintEdge(level, blade.position());
        }
    }

    /** Smoothstep: the rise starts and ends still, so forty ticks reads as a lift and not a jump. */
    private static double ease(double progress) {
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static boolean anyBladeGone(Rite rite) {
        for (ItemEntity blade : rite.blades()) {
            if (blade.isRemoved() || blade.getItem().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A refusal after the ceremony has begun: the swords get their weight back and fall. */
    private static void release(Rite rite) {
        for (ItemEntity blade : rite.blades()) {
            if (blade.isRemoved()) {
                continue;
            }
            blade.setNoGravity(false);
            blade.setDefaultPickUpDelay();
        }
    }

    /**
     * The class is taken and the steel comes out, in that order.
     *
     * <p>Order matters. The unlock runs first so that anything hung off it - the reward skills,
     * the reward passives, the rung the live half reads - sees a wielder who holds the class
     * before it sees one with their swords out. {@link SwordService#draw} runs second, through the
     * real toggle with no special case and no back door, so the formation the rite raises is the
     * one Call the Blade would have raised and nothing in the unlock chain can clear it.
     *
     * <p>Drawing here rather than leaving it to the new wielder's first keypress is the whole
     * payoff: the rite is over the instant the four swords are standing, and they are standing
     * before the chat line about the class has been read.
     */
    private static void seal(ServerPlayer player, Rite rite) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.unlockClass(player, MagicalClasses.SWORD_SUMMONER)) {
            release(rite);
            return;
        }

        SwordService.draw(player, state);
        state.sync(player);

        ServerLevel level = player.serverLevel();
        for (ItemEntity blade : rite.blades()) {
            Vec3 at = blade.position();
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + EDGE_RISE, at.z,
                    SEAL_MOTES, 0.08D, EDGE_HALF_LENGTH, 0.08D, 0.02D);
            blade.discard();
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.4F);

        MagicalClassDefinition definition = MagicalClasses.get(MagicalClasses.SWORD_SUMMONER);
        if (definition != null) {
            player.displayClientMessage(Component.translatable("message.magical.class_unlocked",
                    Component.translatable(definition.nameKey())), false);
        }
    }
}
