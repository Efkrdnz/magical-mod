package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.skill.sword.TheKeelSkill;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Keel's split, run against a real level.
 *
 * <p>A ride is one law with two halves on two machines, exactly as a space law is, and for exactly
 * the same reason: <b>the server cannot deliver a velocity to a player's own client</b>. Vanilla
 * sends the {@code hasImpulse} motion packet over {@code broadcast}, and
 * {@code ChunkMap.TrackedEntity.updatePlayer} never puts a player into their own audience, so a
 * {@code setDeltaMovement} written on the server against the rider is a write nobody ever reads.
 * The one channel that would reach them, {@code hurtMarked}, <em>replaces</em> the client's
 * velocity with the server's copy, which is a stutter and not a ride.
 *
 * <p>So the carry lives in {@code client/SwordKeelClient} on {@code PlayerTickEvent.Pre}, against
 * that client's own player and nobody else's, and the server keeps only the consequences: the
 * mana, the Edge the ridden station sheds, the clock and the break. This pins the half that can be
 * pinned - that the server keeps the consequences and never writes the movement. The half that
 * cannot be tested here is what a client does with its own player, because there is no client;
 * that is verified in a dev-client capture.
 *
 * <p>The test exists because the failure is <em>silent and green</em>. Put the push back on the
 * server and every unit test still passes, every log line is clean, and the ride simply does
 * nothing at all when a human presses the key - which is precisely how this was found the first
 * time, in the Authority of Space, where every law but one did nothing to a player and four green
 * tests certified them.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class SwordKeelGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);

    /** Enough metal on one bearing to be ridden: {@link TheKeelSkill#RIDE_MIN_EDGE} is the floor. */
    private static final int RIDE_EDGE = TheKeelSkill.RIDE_MIN_EDGE;

    /** A velocity this small is the floor's own settling, not a push. */
    private static final double STILL = 1.0E-6D;

    private SwordKeelGameTests() {}

    /**
     * The ride starts, the server bills it, and the server never writes the rider's velocity.
     *
     * <p>Zeroing the movement at the start of each observed tick is what makes the reading
     * unambiguous: whatever is in the vector afterwards was put there by this tick's server work,
     * and gravity acts on {@code y}, so only the horizontal plane is asserted on.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "sword_keel_1")
    public static void theServerNeverPushesTheRider(GameTestHelper helper) {
        ServerPlayer player = rider(helper);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            standUpTheArray(player, state);
            // The SNEAK press. A plain press is setDown - it freezes the frame where it stands -
            // and only the sneak-press mounts, which is the whole of the difference between
            // leaving your Array behind and standing on it.
            TheKeelSkill.press(player, state, true);
            helper.assertTrue(TheKeelSkill.riding(player),
                    "the sneak-press must start a ride, or the rest of this test proves nothing");
            player.setDeltaMovement(Vec3.ZERO);
        });
        // Several ticks apart, so a push written on one tick of the ride's clock rather than every
        // tick - the Edge shed, the mana interval - is still caught.
        for (int tick : new int[] {3, 7, 12, 20}) {
            int at = tick;
            helper.runAtTickTime(at, () -> {
                Vec3 motion = player.getDeltaMovement();
                helper.assertTrue(Math.abs(motion.x) < STILL && Math.abs(motion.z) < STILL,
                        "the server wrote the rider's velocity at tick " + at + " (" + motion.x + ", "
                                + motion.z + "); it cannot deliver one, so the carry belongs in "
                                + "SwordKeelClient and the ride will look dead to a human");
                player.setDeltaMovement(Vec3.ZERO);
            });
        }
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(TheKeelSkill.riding(player),
                    "and the ride must still be running - losing the push must not lose the ride");
            helper.succeed();
        });
    }

    /**
     * The other half: the consequences the server <em>does</em> own still land. A ride that cost
     * nothing because its push moved out to the client would be a free flight.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "sword_keel_2")
    public static void aRiderStillPaysForTheRideTheServerCannotPush(GameTestHelper helper) {
        ServerPlayer player = rider(helper);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        int[] afterMount = new int[1];
        helper.runAtTickTime(1, () -> {
            standUpTheArray(player, state);
            state.setMana(state.maxMana());
            TheKeelSkill.press(player, state, true);
            helper.assertTrue(TheKeelSkill.riding(player), "the sneak-press must start a ride");
            // Read the pool AFTER the mount, so what is measured below is the ride's own toll and
            // not the one payment the press made. Starting from the full pool would pass on the
            // mount alone and would still pass with the per-tick billing deleted entirely.
            afterMount[0] = state.mana();
        });
        // Past two whole intervals, so at least one toll has certainly fallen inside the window.
        helper.runAtTickTime(TheKeelSkill.RIDE_MANA_INTERVAL * 2 + 5, () -> {
            helper.assertTrue(state.mana() < afterMount[0],
                    "a ride bills mana on the server every " + TheKeelSkill.RIDE_MANA_INTERVAL
                            + " ticks; the pool has not moved off " + afterMount[0] + " since the mount");
            helper.succeed();
        });
    }

    /** One bearing straight ahead, manned heavily enough that the Keel will accept it. */
    private static void standUpTheArray(ServerPlayer player, PlayerMagicState state) {
        SwordService.refreshRung(player, state);
        SwordArray array = state.swordArray();
        array.clear();
        array.plant(new Station(0, 0, Station.REACH_MIN, RIDE_EDGE), 0);
        SwordService.tendArrayEntity(player, state);
    }

    /**
     * A survival player on the floor of the cell, with the whole hidden chain taken.
     *
     * <p>Fake players outlive their tests and stand in the neighbouring structures, so each test
     * runs in a batch of its own and clears the ones before it first. Two details are not
     * optional: a player is invulnerable until their client reports the world loaded and a fake
     * one never does, and nothing moves a fake player onto the floor, so it is put there by hand
     * or it spends the test falling and the velocity reading is gravity rather than a law.
     */
    private static ServerPlayer rider(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
                TheKeelSkill.forget(leftover.getUUID());
                SwordService.forget(leftover.getUUID());
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "keel-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setClientLoaded(true);
        Vec3 stand = onFloor(helper, STAND);
        player.teleportTo(stand.x, stand.y, stand.z);
        // Straight onto the class progress, which is where SwordService.rulesFor looks. Every rung
        // and not just the top one: SwordService.holds asks for SWORD_SUMMONER by name, so a
        // wielder holding only the Sword God node is refused at the Keel's first gate with
        // "skill_locked" - which is correct, because the chain is a chain, but it means a test
        // that grants the summit alone proves nothing.
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        for (var rung : List.of(com.efkrdnz.magical.classes.MagicalClasses.SWORD_SUMMONER,
                com.efkrdnz.magical.classes.MagicalClasses.SWORD_RIDER,
                com.efkrdnz.magical.classes.MagicalClasses.SWORD_SAINT,
                com.efkrdnz.magical.classes.MagicalClasses.SWORD_GOD)) {
            state.classProgressFor(rung).unlock();
        }
        SwordService.refreshRung(player, state);
        // payFor bills the press, and a fresh state has an empty pool, so without this the mount
        // refuses for mana and the ride never starts.
        state.setMana(state.maxMana());
        return player;
    }

    /** The floor under a template cell, absolute: a thing put there starts where it would land. */
    private static Vec3 onFloor(GameTestHelper helper, BlockPos at) {
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        Vec3 floor = com.efkrdnz.magical.magic.cast.AimResolver.groundBelow(helper.getLevel(), above, 8);
        return floor != null ? floor : above;
    }
}
