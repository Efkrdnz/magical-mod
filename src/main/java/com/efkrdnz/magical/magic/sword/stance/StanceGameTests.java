package com.efkrdnz.magical.magic.sword.stance;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The two halves of the toggle the wielder asked for, run against a real level.
 *
 * <p>Both of these are things no unit test can reach and both fail silently. A stance's
 * {@link Watch} runs on {@code SwordArrayEntity.tick}, so whether Guard actually turns an arrow
 * is a question about an entity in a world, a projectile with a velocity and a clock that has to
 * be due - four things each individually easy to get right, which have to line up on one tick.
 * And the answer to the scoping question was <b>swords vanish entirely</b>: off means no entity,
 * nothing drawn, nothing intercepting, no drain. An entity left behind after a sheathe would go
 * on running the Watch of a stance nobody is standing in, and it would look identical to a
 * working sheathe from every side except the one that matters.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class StanceGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);

    /**
     * Where the arrow starts, relative to the wielder.
     *
     * <p>Inside the cell with room to spare - the template is five blocks on a side and the
     * neighbouring cells are adjacent, so an entity placed past its own walls is an entity in
     * somebody else's test. Well inside {@link Watch#INTERCEPT}'s eight-block reach either way.
     */
    private static final Vec3 ARROW_FROM = new Vec3(0.0D, 1.0D, 1.8D);

    /**
     * Blocks a tick. Slow and weightless on purpose: the arrow must still be in the air when the
     * Watch's clock comes due, and a real arrow's speed would put it in the wielder first.
     */
    private static final double ARROW_SPEED = 0.05D;

    private StanceGameTests() {}

    /**
     * Guard turns a closing arrow and pays exactly one sword for it.
     *
     * <p>Exactly one, not at least one: the interval is what stops a volley emptying a formation
     * in a tick, and an intercept that spent a sword per arrow per tick would read as a working
     * guard right up until somebody fired two.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "sword_stance_1")
    public static void interceptTurnsAnArrowAndSpendsExactlyOneSword(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "stance-guard");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        int[] before = new int[1];
        Arrow[] shot = new Arrow[1];
        helper.runAtTickTime(1, () -> {
            standUp(player, state, SwordStance.GUARD);
            before[0] = SwordService.present(player, state);
            helper.assertTrue(before[0] > 1,
                    "the base rung fields four swords; with " + before[0] + " out there is no "
                            + "difference between spending one and spending the lot");
            Vec3 from = player.position().add(ARROW_FROM);
            Arrow arrow = new Arrow(helper.getLevel(), from.x, from.y, from.z,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            // Weightless, so it is still closing several ticks from now, and unowned, so the
            // Watch's "not the wielder's own" filter lets it through.
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(player.getEyePosition().subtract(from).normalize().scale(ARROW_SPEED));
            helper.getLevel().addFreshEntity(arrow);
            shot[0] = arrow;
        });
        helper.runAtTickTime(2 + Watch.INTERCEPT.interval() * 2, () -> {
            Vec3 offset = shot[0].position().subtract(player.getBoundingBox().getCenter());
            helper.assertTrue(shot[0].isAlive(), "the arrow was consumed rather than turned");
            helper.assertFalse(com.efkrdnz.magical.magic.incantation.VersePassives.closing(
                            shot[0].getDeltaMovement(), offset),
                    "the arrow is still closing on the wielder: Guard did not turn it");
            int after = SwordService.present(player, state);
            helper.assertValueEqual(after, before[0] - 1, "swords still with the wielder");
            helper.succeed();
        });
    }

    /**
     * Off means gone. The entity goes with the steel, and it goes on the press rather than on
     * some later tick noticing.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "sword_stance_2")
    public static void sheathingLeavesNoEntityBehind(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "stance-sheathe");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            standUp(player, state, SwordStance.GUARD);
            helper.assertTrue(SwordService.arrayEntity(player) != null,
                    "drawing must put a formation entity in the world, or the rest proves nothing");
            helper.assertTrue(SwordService.present(player, state) > 0, "and swords with it");
        });
        helper.runAtTickTime(3, () -> {
            SwordService.sheathe(player, state);
            helper.assertFalse(state.swordArray().drawn(), "the steel is still out after a sheathe");
            helper.assertTrue(SwordService.arrayEntity(player) == null,
                    "the formation entity outlived the sheathe that put the swords away");
        });
        // Several ticks later, because the entity is discarded rather than removed outright and a
        // discarded entity is still in the level's list for the rest of its own tick.
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(SwordArrayEntity.class,
                            player.getBoundingBox().inflate(32.0D)).isEmpty(),
                    "a formation entity is still standing near a wielder who sheathed nine ticks ago");
            helper.assertValueEqual(SwordService.present(player, state), 0,
                    "swords with a sheathed wielder");
            helper.succeed();
        });
    }

    /**
     * A wielder cannot pay a volley off by changing shape.
     *
     * <p>Only reachable in a level, because the thing being tested is a side effect of the
     * formation entity's own tick: {@code StanceWatchService.tick} is what tells the service the
     * stance moved. And it fails silently in the most expensive way there is - six free swords
     * for two keypresses, with every number on the screen agreeing.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "sword_stance_3")
    public static void aVolleyIsNotPaidOffByChangingStance(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "stance-debt");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        int[] whole = new int[1];
        helper.runAtTickTime(1, () -> {
            standUp(player, state, SwordStance.RAIN);
            whole[0] = SwordService.present(player, state);
            helper.assertValueEqual(whole[0], SwordStance.RAIN.swordCap(), "swords Rain fields");
            helper.assertValueEqual(SwordService.spendSwords(player, state, whole[0]), whole[0],
                    "swords the volley actually sent");
            helper.assertValueEqual(SwordService.present(player, state), 0, "swords left after all of them went");
        });
        helper.runAtTickTime(3, () -> stand(player, state, SwordStance.GUARD));
        helper.runAtTickTime(8, () -> {
            helper.assertValueEqual(SwordService.swords(state), SwordStance.GUARD.swordCap(),
                    "swords Guard fields");
            helper.assertValueEqual(SwordService.present(player, state), 0,
                    "swords present in Guard, owing a whole Rain volley");
            helper.assertValueEqual(SwordService.away(player, state), SwordStance.GUARD.swordCap(),
                    "holes visible in Guard - every slot it has, and no more");
        });
        helper.runAtTickTime(10, () -> stand(player, state, SwordStance.RAIN));
        helper.runAtTickTime(15, () -> {
            helper.assertValueEqual(SwordService.present(player, state), 0,
                    "swords back in Rain after a round trip through Guard - the debt was truncated"
                            + " to Guard's complement on the way through and the rest forgiven");
            helper.succeed();
        });
    }

    /**
     * And every sword that went out can come home, however narrow the stance got.
     *
     * <p>The other half of the same rule and the other way it fails: if the return machinery
     * reads the stance's window rather than the debt, the swords past that window are stranded
     * for good - no clock, no walk-over and no recall ever sees them again.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "sword_stance_4")
    public static void everySwordComesHomeEvenFromANarrowerStance(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "stance-strand");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        int[] spent = new int[1];
        helper.runAtTickTime(1, () -> {
            standUp(player, state, SwordStance.RAIN);
            spent[0] = SwordService.spendSwords(player, state, SwordStance.RAIN.swordCap());
            helper.assertTrue(spent[0] > SwordStance.VANGUARD.swordCap(),
                    "the volley has to be wider than the stance it is carried into, or the window"
                            + " and the debt are the same number and this proves nothing");
        });
        helper.runAtTickTime(3, () -> stand(player, state, SwordStance.VANGUARD));
        helper.runAtTickTime(8, () -> {
            helper.assertValueEqual(SwordService.present(player, state), 0, "swords present in Vanguard");
            SwordService.returnSwords(player, state, spent[0]);
            helper.assertValueEqual(SwordService.present(player, state), SwordStance.VANGUARD.swordCap(),
                    "swords back in Vanguard once the whole volley returned");
        });
        helper.runAtTickTime(10, () -> stand(player, state, SwordStance.RAIN));
        helper.runAtTickTime(15, () -> {
            helper.assertValueEqual(SwordService.present(player, state), SwordStance.RAIN.swordCap(),
                    "swords back in Rain - anything short of the full complement is steel the"
                            + " return machinery could not reach through Vanguard's window");
            helper.succeed();
        });
    }

    /** A change of posture mid-test, the way the key does it: set it, then let the entity notice. */
    private static void stand(ServerPlayer player, PlayerMagicState state, SwordStance stance) {
        state.swordArray().setStance(stance);
        SwordService.tendArrayEntity(player, state);
    }

    /** The steel out, in one named stance, with the entity that carries the Watch already there. */
    private static void standUp(ServerPlayer player, PlayerMagicState state, SwordStance stance) {
        SwordService.refreshRung(player, state);
        state.swordArray().clear();
        state.swordArray().setStance(stance);
        SwordService.draw(player, state);
        SwordService.tendArrayEntity(player, state);
    }

    /**
     * A survival player on the floor of the cell, with the whole hidden chain taken.
     *
     * <p>The same three traps {@code SwordKeelGameTests} documents: fake players outlive their
     * tests and stand in the neighbouring cells, so each test runs in its own batch and clears
     * the leftovers first; a player is invulnerable until their client reports the world loaded
     * and a fake one never does; and nothing moves a fake player onto the floor, so it is put
     * there by hand or it spends the test falling.
     */
    private static ServerPlayer wielder(GameTestHelper helper, String name) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().startsWith("stance-")) {
                SwordService.forget(leftover.getUUID());
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setClientLoaded(true);
        Vec3 stand = onFloor(helper, STAND);
        player.teleportTo(stand.x, stand.y, stand.z);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        // Every rung, not just the summit: SwordService.holds asks for SWORD_SUMMONER by name.
        for (var rung : List.of(MagicalClasses.SWORD_SUMMONER, MagicalClasses.SWORD_RIDER,
                MagicalClasses.SWORD_SAINT, MagicalClasses.SWORD_GOD)) {
            state.classProgressFor(rung).unlock();
        }
        SwordService.refreshRung(player, state);
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
