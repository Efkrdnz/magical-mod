package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.fx.EldritchConstructEntity;
import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicCastingService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The calls to the deep, run against a real level: a grasp that roots and crushes, an eye that
 * reveals, jaws that snap, a lash that harries, wards that take a hit, a call that pulses and is
 * noticed. The rules a unit test can hold are held in {@code EldritchServiceTest}; what only a
 * level shows is that the constructs tick against a player and a victim who are actually there.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class EldritchGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);
    private static final BlockPos VICTIM = new BlockPos(4, 2, 2);

    private EldritchGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    private static List<EldritchConstructEntity> constructs(GameTestHelper helper, ServerPlayer player, ResourceLocation skill) {
        return EldritchConstructEntity.ownedBy(helper.getLevel(), player, skill, 32.0D);
    }

    /**
     * A survival player holding one call, standing at {@code at} with full mana: survival so the
     * bill is real and Notice moves, which is half of what these tests are about.
     */
    private static ServerPlayer eldritchMage(GameTestHelper helper, BlockPos at, ResourceLocation skill) {
        var server = helper.getLevel().getServer();
        // Fake players outlive their tests and stand in the neighbouring structures, well within
        // a call's reach and as hostile as any other player. Each eldritch test runs in a batch of
        // its own and starts by clearing the ones before it, so the only hostile is the victim.
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "deep-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // A player is invulnerable until their client reports the world loaded; a fake one never does.
        player.setClientLoaded(true);
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        player.teleportTo(stand.x, stand.y, stand.z);
        state(player).unlockAll(Set.of(skill));
        state(player).refillMana();
        return player;
    }

    private static Zombie victim(GameTestHelper helper, ServerPlayer player) {
        // A husk: a zombie that does not burn in the daylight of the test world, so every drop of
        // health in these tests is the work of a call.
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, VICTIM);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
        return zombie;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "eldritch_1")
    public static void aGraspRootsWhatItReachesAndCrushesIt(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.GRASP_OF_THE_DEEP.id());
        Zombie zombie = victim(helper, player);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position());
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.GRASP_OF_THE_DEEP.id(), false));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(constructs(helper, player, MagicContent.GRASP_OF_THE_DEEP.id()).size() == 1, "one tentacle must erupt");
            helper.assertTrue(state(player).notice() == 12, "a grasp draws twelve notice, got " + state(player).notice());
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(MagicStatusService.has(zombie, MagicStatus.ROOTED), "the grasped thing must be rooted");
            helper.assertTrue(zombie.getHealth() < health, "and crushed");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "eldritch_2")
    public static void anEyeRevealsWhatItSeesAndTheStareStings(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.UNBLINKING_EYE.id());
        Zombie zombie = victim(helper, player);
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.UNBLINKING_EYE.id(), false));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(constructs(helper, player, MagicContent.UNBLINKING_EYE.id()).size() == 1, "one eye must open");
            helper.assertTrue(MagicStatusService.has(zombie, MagicStatus.REVEALED), "what the eye sees is revealed");
            helper.assertTrue(player.getUUID().equals(MagicStatusService.sourceOf(zombie, MagicStatus.REVEALED)), "to its owner");
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(zombie.getHealth() < health, "the stare stings");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "eldritch_3")
    public static void jawsSnapOnWhatStandsInThem(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.HUNGERING_MAW.id());
        Zombie zombie = victim(helper, player);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position());
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.HUNGERING_MAW.id(), false));
        helper.runAtTickTime(10, () -> {
            List<EldritchConstructEntity> maws = constructs(helper, player, MagicContent.HUNGERING_MAW.id());
            helper.assertTrue(maws.size() == 1, "jaws must open");
            helper.assertTrue(maws.get(0).syncedData().getInt("snap") == 0, "and not snap before they are open");
            helper.assertTrue(zombie.getHealth() == health, "nothing bitten while opening");
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(zombie.getHealth() < health, "the jaws must snap on what stands in them");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "eldritch_4")
    public static void aLashStingsShovesAndHarriesWhatIsAhead(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.TENDRIL_LASH.id());
        Zombie zombie = victim(helper, player);
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.TENDRIL_LASH.id(), false));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(zombie.getHealth() < health, "the thing ahead is stung");
            helper.assertTrue(MagicStatusService.has(zombie, MagicStatus.HARRIED), "and harried");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "eldritch_5")
    public static void aWardTakesTheHitAndBitesBack(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.SKIN_OF_THE_DEEP.id());
        Zombie zombie = victim(helper, player);
        float zombieHealth = zombie.getHealth();
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.SKIN_OF_THE_DEEP.id(), false));
        helper.runAtTickTime(5, () -> {
            List<EldritchConstructEntity> skins = constructs(helper, player, MagicContent.SKIN_OF_THE_DEEP.id());
            helper.assertTrue(skins.size() == 1 && skins.get(0).extra() == 4, "four wards at one point of size");
            float health = player.getHealth();
            player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), 6.0F);
            helper.assertTrue(player.getHealth() == health, "a ward takes the hit, got " + player.getHealth() + " of " + health);
            helper.assertTrue(skins.get(0).extra() == 3, "and is spent, wards left " + skins.get(0).extra());
            helper.assertTrue(zombie.getHealth() < zombieHealth, "and bites what struck");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140, batch = "eldritch_6")
    public static void aHeldCallPulsesGraspsAndIsNoticed(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.CALL_OF_THE_DEEP.id());
        Zombie zombie = victim(helper, player);
        state(player).setNotice(EldritchService.WATCHED_AT - 8);
        HoldService.setHeld(player, 0, true);
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.CALL_OF_THE_DEEP.id(), false));
        helper.runAtTickTime(3, () -> helper.assertTrue(
                constructs(helper, player, MagicContent.CALL_OF_THE_DEEP.id()).size() >= 1, "the eye must open"));
        // A hold times out unless the key is seen again; the client re-sends it, so the test does.
        for (int tick = 10; tick <= 60; tick += 10) {
            int at = tick;
            helper.runAtTickTime(at, () -> HoldService.setHeld(player, 0, true));
        }
        helper.runAtTickTime(70, () -> {
            HoldService.setHeld(player, 0, true);
            helper.assertTrue(MagicStatusService.has(zombie, MagicStatus.ROOTED), "a pulse must grasp the thing in reach");
            helper.assertTrue(state(player).notice() >= EldritchService.WATCHED_AT, "two pulses past the rung: notice " + state(player).notice());
        });
        helper.runAtTickTime(75, () -> HoldService.setHeld(player, 0, false));
        helper.runAtTickTime(95, () -> {
            boolean eyeGone = constructs(helper, player, MagicContent.CALL_OF_THE_DEEP.id()).stream()
                    .noneMatch(c -> EldritchConstructEntity.MODEL_EYE.equals(c.model()));
            helper.assertTrue(eyeGone, "letting go ends the call");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "eldritch_7")
    public static void noticeCoolsAPointASlowTickForAMageWhoStaysQuiet(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.TENDRIL_LASH.id());
        state(player).setNotice(40);
        // A fake player has no connection to tick it, so the test runs the slow tick the player
        // tick would, at the cadence MagicGameplayEvents uses.
        for (int tick = ClassPassiveEffects.SLOW_TICK_INTERVAL; tick < 45; tick += ClassPassiveEffects.SLOW_TICK_INTERVAL) {
            int at = tick;
            helper.runAtTickTime(at, () -> ClassPassiveEffects.slowTick(player, state(player)));
        }
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(state(player).notice() < 40, "notice must cool, still " + state(player).notice());
            helper.assertTrue(state(player).notice() >= 34, "but not faster than a point a slow tick: " + state(player).notice());
            helper.succeed();
        });
    }
}
