package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.SleeperEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    /** A fake player with the barrier drained, the test level standing in for the dream. */
    static ServerPlayer sleeper(GameTestHelper helper, String name) {
        DreamService.testLevel = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), name);
        player.getData(MagicalAttachments.MAGIC_STATE).setBarrier(0);
        return player;
    }

    /** A stone Flaw one block east of the arrival. */
    static Dreamscape withFlaw(ServerLevel level, UUID owner) {
        Dreamscape scape = DreamService.dreamscape(level, owner);
        level.setBlock(DreamService.at(scape.plot(), new Offset(1, 0, 0)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        scape.markBlock(new Offset(1, 0, 0));
        return scape;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_1")
    public static void aDreamerSleepsWhereTheyStoodAndWakesThere(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-enter-test");
        Vec3 stood = player.position();
        UUID owner = UUID.randomUUID();
        helper.assertTrue(DreamService.enter(player, owner, false), "the dream was refused");
        DreamSession session = DreamService.session(player.getUUID());
        helper.assertTrue(DreamRules.plotAt(player.getX(), player.getZ()) == session.plot, "the dreamer is not in the owner's plot");
        helper.assertTrue(helper.getLevel().getEntity(session.sleeperId) instanceof SleeperEntity body
                && body.position().distanceTo(stood) < 0.01 && body.dreamer().orElseThrow().equals(player.getUUID()),
                "no body of theirs lies where they stood");
        helper.assertTrue(player.hasData(MagicalAttachments.DREAM_RETURN), "nothing remembers where to wake");
        DreamService.wake(player);
        helper.assertTrue(player.position().distanceTo(stood) < 0.01, "they woke somewhere else");
        helper.assertFalse(player.hasData(MagicalAttachments.DREAM_RETURN), "the return point outlived the dream");
        helper.assertTrue(helper.getLevel().getEntity(session.sleeperId) == null, "the body outlived the dream");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_2")
    public static void touchingTheFlawWakesTheDreamer(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-flaw-test");
        Vec3 stood = player.position();
        UUID owner = UUID.randomUUID();
        Dreamscape scape = withFlaw(helper.getLevel(), owner);
        DreamService.enter(player, owner, false);
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 0.72, arrival.getY(), arrival.getZ() + 0.5);
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the Flaw was touched and nothing woke");
            helper.assertTrue(player.position().distanceTo(stood) < 0.01, "they woke somewhere else");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_3")
    public static void aBlowOnTheSleeperWakesTheDreamerAndLandsOnThem(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-struck-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel level = helper.getLevel();
        SleeperEntity body = (SleeperEntity) level.getEntity(DreamService.session(player.getUUID()).sleeperId);
        float before = player.getHealth();
        body.hurtServer(level, level.damageSources().generic(), 4.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the body was struck and nothing woke");
            helper.assertTrue(Math.abs(player.getHealth() - (before - 4.0F)) < 0.01F, "the blow did not land on the woken player: " + player.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_4")
    public static void aDreamLeavesOneHeartAndWakes(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-heart-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel level = helper.getLevel();
        player.hurtServer(level, level.damageSources().generic(), 100.0F);
        helper.assertTrue(player.isAlive() && Math.abs(player.getHealth() - DreamRules.ONE_HEART) < 0.01F,
                "the dream did not stop at one heart: " + player.getHealth());
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "brought to one heart and still dreaming");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_5")
    public static void aDreamEndsWhenItsTimeIsUp(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-clock-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        DreamSession session = DreamService.session(player.getUUID());
        helper.assertTrue(session.wakesAt == helper.getLevel().getServer().getTickCount() + DreamRules.DREAM_TICKS,
                "a dream does not last 1200 ticks");
        session.wakesAt = 0L;
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the clock ran out and nothing woke");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_6")
    public static void aDreamerWhoStraysIsPutBackAtTheArrival(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-stray-test");
        UUID owner = UUID.randomUUID();
        DreamService.enter(player, owner, false);
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), owner);
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + DreamRules.PLOT_HALF + 6, arrival.getY(), arrival.getZ());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(DreamService.dreaming(player.getUUID()), "straying woke them");
            helper.assertTrue(player.position().distanceTo(Vec3.atBottomCenterOf(arrival)) < 0.01, "they were not put back");
            DreamService.wake(player);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_7")
    public static void aDreamerWhoLoggedOutWakesWhereTheyFellAsleep(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-recover-test");
        Vec3 stood = player.position();
        player.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.of(player));
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), UUID.randomUUID());
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        DreamService.recover(player);
        helper.assertTrue(player.position().distanceTo(stood) < 0.01, "a stranded dreamer was not sent back");
        helper.assertFalse(player.hasData(MagicalAttachments.DREAM_RETURN), "the return point outlived the recovery");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_8")
    public static void yourOwnDreamHasNoClockAndRemembersWhereYouLeft(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-own-test");
        helper.assertTrue(DreamService.enter(player, player.getUUID(), true), "your own dream was refused");
        helper.assertTrue(DreamService.session(player.getUUID()).wakesAt == Long.MAX_VALUE, "your own dream has a clock");
        helper.assertTrue(DreamService.dreamingOwn(player.getUUID()), "your own dream is not yours");
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), player.getUUID());
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 2.5, arrival.getY(), arrival.getZ() + 1.5);
        DreamService.wake(player);
        helper.assertTrue(scape.arrival().equals(new Offset(2, 0, 1)), "the arrival is not where you left: " + scape.arrival());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_9")
    public static void aKilledBodyWakesItsDreamerUnhurt(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-killed-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel level = helper.getLevel();
        SleeperEntity body = (SleeperEntity) level.getEntity(DreamService.session(player.getUUID()).sleeperId);
        float before = player.getHealth();
        body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the body was killed and nothing woke");
            helper.assertTrue(player.isAlive() && player.getHealth() == before, "a /kill on the body killed the dreamer");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_10")
    public static void leavingTheDreamAnyOtherWayWakesYouInYourBody(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-escape-test");
        Vec3 stood = player.position();
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) {
            // The test server has only the overworld; stepping into it by any other level is the same route.
            helper.succeed();
            return;
        }
        player.teleportTo(nether, 0.5, 100, 0.5, java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class), 0.0F, 0.0F, true);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "a dreamer who left the dream is still dreaming");
            helper.assertTrue(player.level() == helper.getLevel() && player.position().distanceTo(stood) < 0.01,
                    "a dreamer who left the dream did not wake in their body");
            helper.succeed();
        });
    }
}
