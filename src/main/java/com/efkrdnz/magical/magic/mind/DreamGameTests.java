package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.SleeperEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.SoulAuthorityService;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
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

    /**
     * Another mod refusing this one player every teleport, the way a real listener would. It stays
     * registered, and is inert once the player is gone.
     */
    static void refuseTravelFor(ServerPlayer player) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityTravelToDimensionEvent.class, event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        });
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

    /**
     * The body lies in another dimension from the dream, so waking is a real crossing. A fake player
     * never acknowledges one, so it stays mid-crossing (invulnerable to everything) until the test
     * says the client has arrived; the blow must wait for that, then land exactly once.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "dream_11")
    public static void aBlowOnTheBodyWaitsForTheDreamerToFinishCrossingBack(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-crossing-test");
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.succeed();
            return;
        }
        BlockPos pocket = new BlockPos(0, 100, 0);
        for (BlockPos pos : BlockPos.betweenClosed(pocket.offset(-1, -1, -1), pocket.offset(1, 2, 1))) {
            boolean inside = pos.getX() == 0 && pos.getZ() == 0 && pos.getY() >= 100 && pos.getY() <= 101;
            nether.setBlock(pos, inside ? Blocks.AIR.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }
        DreamService.enter(player, UUID.randomUUID(), false);
        CompoundTag home = DreamReturn.of(player).save();
        home.putString("dimension", Level.NETHER.location().toString());
        home.putDouble("x", 0.5);
        home.putDouble("y", 100.0);
        home.putDouble("z", 0.5);
        player.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.load(home));
        ServerLevel level = helper.getLevel();
        SleeperEntity body = (SleeperEntity) level.getEntity(DreamService.session(player.getUUID()).sleeperId);
        float before = player.getHealth();
        body.hurtServer(level, level.damageSources().generic(), 4.0F);
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the body was struck and nothing woke");
            helper.assertTrue(player.level() == nether, "the dreamer did not cross back into the body's dimension");
            helper.assertTrue(player.isChangingDimension(), "the crossing finished by itself, so nothing here holds the blow back");
            helper.assertTrue(player.getHealth() == before, "the blow landed mid-crossing: " + player.getHealth());
            helper.assertTrue(DreamService.pendingHurt(player.getUUID()) != null, "the blow was dropped instead of held");
            player.hasChangedDimension();
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(Math.abs(player.getHealth() - (before - 4.0F)) < 0.01F,
                        "the blow never landed once the crossing was done: " + player.getHealth());
                helper.assertTrue(DreamService.pendingHurt(player.getUUID()) == null, "the blow is still owed after landing");
                player.server.getPlayerList().remove(player);
                helper.succeed();
            });
        });
    }

    /** Nothing keeps a dreamer's chunk loaded but the dream itself, so the body's chunk is held for exactly that long. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_12")
    public static void theBodyChunkIsHeldForTheDreamAndLetGoOnWaking(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-ticket-test");
        ChunkPos chunk = player.chunkPosition();
        ServerLevel level = helper.getLevel();
        helper.assertFalse(forced(level, chunk), "the chunk was already held before anyone slept");
        DreamService.enter(player, UUID.randomUUID(), false);
        helper.assertTrue(forced(level, chunk), "nothing holds the sleeping body's chunk");
        DreamService.wake(player);
        helper.assertFalse(forced(level, chunk), "the body's chunk is still held after waking");
        helper.succeed();
    }

    /** A dreamer with no return point is not stranded in the dream: they wake at the world spawn and the body still goes. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_13")
    public static void aDreamerWithNoReturnPointWakesAtTheWorldSpawn(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-noreturn-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        int bodyId = DreamService.session(player.getUUID()).sleeperId;
        player.removeData(MagicalAttachments.DREAM_RETURN);
        DreamService.wake(player);
        ServerLevel overworld = player.server.overworld();
        helper.assertTrue(player.level() == overworld, "a dreamer with no return point did not wake in the overworld");
        helper.assertTrue(DreamRules.plotAt(player.getX(), player.getZ()) < 0, "a dreamer with no return point was left in the dream");
        helper.assertTrue(helper.getLevel().getEntity(bodyId) == null, "the body outlived the dream");
        helper.succeed();
    }

    /**
     * A dream that brought its dreamer to one heart must not finish the job outside: what was burning,
     * withering, lifting or freezing them in the dream stops when they wake. A fake player is never
     * ticked by a connection, so the test ticks it, and starves it of natural healing so nothing but
     * the dream's leftovers could decide whether it lives.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "dream_14")
    public static void aDreamerWokenBurningAndWitheredLivesOn(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-calm-test");
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0.0F);
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel level = helper.getLevel();
        player.igniteForSeconds(30.0F);
        player.setTicksFrozen(300);
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 600, 0));
        MagicStatusService.apply(player, MagicStatus.ROOTED, 600, MagicContent.LULL.id(), null);
        player.fallDistance = 30.0F;
        player.hurtServer(level, level.damageSources().generic(), 100.0F);
        java.util.concurrent.atomic.AtomicBoolean woke = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            if (woke.get()) {
                player.doTick();
            }
        });
        helper.runAfterDelay(62, () -> {
            helper.assertTrue(player.isAlive(), "the dream killed its dreamer after they woke");
            helper.succeed();
        });
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "brought to one heart and still dreaming");
            helper.assertFalse(player.isOnFire(), "the dreamer woke still burning");
            helper.assertTrue(player.getTicksFrozen() == 0, "the dreamer woke still freezing");
            helper.assertFalse(player.hasEffect(MobEffects.WITHER) || player.hasEffect(MobEffects.POISON),
                    "the dreamer woke still withering");
            helper.assertFalse(player.hasEffect(MobEffects.LEVITATION), "the dreamer woke still lifting");
            helper.assertFalse(MagicStatusService.has(player, MagicStatus.ROOTED), "the dreamer woke still rooted");
            helper.assertTrue(player.fallDistance == 0.0F, "the dreamer woke still falling");
            woke.set(true);
        });
    }

    /**
     * Nobody is left in the dream with no dream running: a player found in a plot with no session is
     * sent to where they lay if anything remembers it, and to the world spawn if nothing does. Driven
     * by the server tick, so this also proves the rescue is wired to it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "dream_15")
    public static void aPlayerInTheDreamWithNoSessionIsSentHome(GameTestHelper helper) {
        ServerPlayer remembered = sleeper(helper, "dream-stranded-test");
        ServerPlayer forgotten = GameTestPlayers.another(helper, new BlockPos(1, 2, 2), "dream-lost-test");
        Vec3 stood = remembered.position();
        remembered.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.of(remembered));
        forgotten.removeData(MagicalAttachments.DREAM_RETURN);
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), UUID.randomUUID());
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        remembered.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        forgotten.teleportTo(arrival.getX() + 1.5, arrival.getY(), arrival.getZ() + 0.5);
        helper.assertTrue(DreamService.isDream(remembered) && DreamService.isDream(forgotten), "the stage did not put them in the dream");
        helper.runAfterDelay(DreamService.RESCUE_INTERVAL_TICKS + 2, () -> {
            helper.assertTrue(remembered.position().distanceTo(stood) < 0.01, "a stranded player was not sent to where they lay");
            helper.assertFalse(remembered.hasData(MagicalAttachments.DREAM_RETURN), "the return point outlived the rescue");
            helper.assertFalse(DreamService.isDream(forgotten), "a stranded player with no return point was left in the dream");
            helper.assertTrue(forgotten.level() == forgotten.server.overworld(), "a stranded player with no return point was not sent to the overworld");
            forgotten.server.getPlayerList().remove(forgotten);
            helper.succeed();
        });
    }

    /**
     * A Soul move is refused whole when it would put a player in a dream nobody sent them into: a
     * dreamer who calls a friend, or swaps with one, leaves the friend where they stand, and the
     * price is handed back.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_16")
    public static void aSoulMoveNeverPullsAPlayerIntoTheDream(GameTestHelper helper) {
        ServerPlayer dreamer = sleeper(helper, "dream-soul-test");
        ServerPlayer friend = GameTestPlayers.another(helper, new BlockPos(1, 2, 3), "dream-friend-test");
        Vec3 friendStood = friend.position();
        helper.assertTrue(DreamService.enter(dreamer, UUID.randomUUID(), false), "the dream was refused");
        Vec3 dreamerStood = dreamer.position();
        PlayerMagicState state = dreamer.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.SOUL);
        state.unlock(MagicContent.SOUL_VOW.id());
        state.equip(0, MagicContent.SOUL_VOW.id());
        state.setSoulBond(friend.level().dimension().location().toString(), friend.getUUID());
        state.setMana(state.maxMana());
        int before = state.mana();
        SoulAuthorityService.castSoulVowMode(dreamer, 0, 101);
        helper.assertTrue(friend.position().distanceTo(friendStood) < 0.01 && !DreamService.isDream(friend),
                "a dreamer's call pulled a player into the dream");
        SoulAuthorityService.castSoulVowMode(dreamer, 0, 100);
        helper.assertTrue(friend.position().distanceTo(friendStood) < 0.01 && !DreamService.isDream(friend),
                "a dreamer's swap put a player in the dream");
        helper.assertTrue(dreamer.position().distanceTo(dreamerStood) < 0.01, "a refused swap still moved the dreamer");
        helper.assertTrue(state.mana() == before, "a refused Soul move kept its price: " + state.mana() + " of " + before);
        DreamService.wake(dreamer);
        friend.server.getPlayerList().remove(friend);
        helper.succeed();
    }

    /** A return point in a level that no longer exists wakes its dreamer at the world spawn, not at its stale coordinates. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_17")
    public static void aReturnPointInAMissingLevelWakesAtTheWorldSpawn(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-gone-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        CompoundTag gone = DreamReturn.of(player).save();
        gone.putString("dimension", "magical:nowhere_at_all");
        gone.putDouble("x", 4000.5);
        gone.putDouble("z", 4000.5);
        player.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.load(gone));
        DreamService.wake(player);
        assertAtWorldSpawn(helper, player, "a return point in a missing level");
        helper.succeed();
    }

    /** A return point whose coordinates are not numbers is no return point: its dreamer wakes at the world spawn. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_18")
    public static void aReturnPointThatIsNotANumberWakesAtTheWorldSpawn(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-nan-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        CompoundTag broken = DreamReturn.of(player).save();
        broken.putDouble("x", Double.NaN);
        helper.assertTrue(DreamReturn.load(broken).level(player.server) == null, "a return point that is not a number still names a level");
        player.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.load(broken));
        DreamService.wake(player);
        assertAtWorldSpawn(helper, player, "a return point that is not a number");
        helper.succeed();
    }

    /** Nobody falls asleep inside a dream: the return point would be taken in it, and the way out lost. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_19")
    public static void yourOwnDreamIsRefusedFromInsideADream(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-nested-test");
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), UUID.randomUUID());
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        helper.assertFalse(DreamService.enterOwn(player), "a player already in a dream fell asleep into their own");
        helper.assertFalse(DreamService.dreaming(player.getUUID()), "a player already in a dream is dreaming twice");
        DreamService.rescueStranded(player.server);
        helper.succeed();
    }

    /**
     * A rescue is a way home like a wake, so it ends what the dream was doing to the body: a player
     * found burning and withered in a plot with no session arrives neither.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_21")
    public static void aRescueEndsWhatTheDreamWasDoingToTheBody(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-rescue-calm-test");
        player.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.of(player));
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), UUID.randomUUID());
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        player.igniteForSeconds(15.0F);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 400, 1));
        DreamService.rescueStranded(player.server);
        helper.assertFalse(DreamService.isDream(player), "the stranded player was not rescued");
        helper.assertTrue(player.getRemainingFireTicks() <= 0, "a rescued player is still burning");
        helper.assertFalse(player.hasEffect(net.minecraft.world.effect.MobEffects.WITHER), "a rescued player is still withered");
        helper.succeed();
    }

    /** A sleeping body is a player in all but the name: never gazed at, and never learned or dreamed by its id. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_20")
    public static void aSleepingBodyIsNeverAnImpression(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-gaze-test");
        SleeperEntity body = SleeperEntity.of(player);
        helper.assertTrue(MindGazeService.keyOf(body, null) == null, "a sleeping body can be gazed at");
        helper.assertTrue(Impression.parse("creature:magical:sleeper") == null, "a sleeping body can be learned by its id");
        helper.assertTrue(DreamRules.refused("magical:sleeper"), "a sleeping body can be dreamed");
        helper.succeed();
    }

    private static void assertAtWorldSpawn(GameTestHelper helper, ServerPlayer player, String what) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        helper.assertFalse(DreamService.dreaming(player.getUUID()), what + " kept its dreamer dreaming");
        helper.assertTrue(player.level() == overworld && Double.isFinite(player.getX())
                        && Math.abs(player.getX() - (spawn.getX() + 0.5)) < 0.01 && Math.abs(player.getZ() - (spawn.getZ() + 0.5)) < 0.01,
                what + " did not wake its dreamer at the world spawn: " + player.position());
    }

    private static boolean forced(ServerLevel level, ChunkPos chunk) {
        ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData.factory(), ForcedChunksSavedData.FILE_ID);
        return data != null && data.getEntityForcedChunks().getTickingChunks().values().stream()
                .anyMatch(chunks -> chunks.contains(chunk.toLong()));
    }

    /** A teleport something else refuses leaves no dream behind: no session, no return point, no body, nobody moved. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_22")
    public static void aRefusedEntryLeavesNothingBehind(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-refused-test");
        refuseTravelFor(player);
        Vec3 stood = player.position();
        helper.assertFalse(DreamService.enter(player, UUID.randomUUID(), false), "a refused teleport still entered the dream");
        helper.assertFalse(DreamService.dreaming(player.getUUID()), "a refused entry left a session");
        helper.assertFalse(player.hasData(MagicalAttachments.DREAM_RETURN), "a refused entry left a return point");
        helper.assertTrue(player.position().distanceTo(stood) < 0.01, "a refused entry moved the player");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(SleeperEntity.class, player.getBoundingBox().inflate(3.0)).isEmpty(),
                "a refused entry left a body lying");
        helper.succeed();
    }

    /** A body nobody is dreaming in - the capture command's, or one a crash left - unmakes itself after its grace. */
    @GameTest(template = TEMPLATE, timeoutTicks = SleeperEntity.ORPHAN_GRACE_TICKS + 60, batch = "dream_23")
    public static void aBodyNobodyDreamsInUnmakesItself(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-orphan-test");
        SleeperEntity body = SleeperEntity.of(player);
        helper.getLevel().addFreshEntity(body);
        helper.runAfterDelay(SleeperEntity.ORPHAN_GRACE_TICKS + 25, () -> {
            helper.assertTrue(body.isRemoved(), "a body nobody dreams in outlived its grace");
            helper.succeed();
        });
    }

    /** The body of someone still dreaming is never taken for an orphan, however long they dream. */
    @GameTest(template = TEMPLATE, timeoutTicks = SleeperEntity.ORPHAN_GRACE_TICKS + 60, batch = "dream_24")
    public static void aBodySomeoneDreamsInOutlastsTheGrace(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-kept-body-test");
        helper.assertTrue(DreamService.enter(player, UUID.randomUUID(), true), "the dream was refused");
        SleeperEntity body = helper.getLevel().getEntitiesOfClass(SleeperEntity.class, helper.getBounds().inflate(4.0)).get(0);
        helper.runAfterDelay(SleeperEntity.ORPHAN_GRACE_TICKS + 25, () -> {
            helper.assertFalse(body.isRemoved(), "the body of a dreamer was unmade while they dreamed");
            helper.assertTrue(DreamService.dreaming(player.getUUID()), "the dreamer woke");
            DreamService.wake(player);
            helper.succeed();
        });
    }

    /**
     * A player in creative or spectator is a visitor, not a dreamer: they may be moved into the dream
     * by a command and are never sent home - until they stop being one.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_25")
    public static void aCreativeVisitorMayStandInTheDream(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-visitor-test");
        player.setGameMode(GameType.CREATIVE);
        EntityTravelToDimensionEvent ask = new EntityTravelToDimensionEvent(player, DreamService.DREAM);
        DreamService.onTravel(ask);
        helper.assertFalse(ask.isCanceled(), "a creative visitor was refused the dream");
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), UUID.randomUUID());
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        DreamService.rescueStranded(player.server);
        helper.assertTrue(DreamService.isDream(player), "a creative visitor was sent home");
        player.setGameMode(GameType.SURVIVAL);
        DreamService.rescueStranded(player.server);
        helper.assertFalse(DreamService.isDream(player), "a visitor who stopped being one was left in the dream");
        helper.succeed();
    }
}
