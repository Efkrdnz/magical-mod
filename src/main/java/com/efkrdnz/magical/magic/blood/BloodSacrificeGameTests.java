package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The pact against a real level.
 *
 * <p>Two things here cannot be shown by a unit test. The first is that a price in flesh really is
 * true damage: the bypass tags live in a datapack and the barrier and the passive reductions live
 * in an event handler, so only a live hurt proves that a full suit of diamond and a standing
 * barrier take nothing off the bill. The second is that sealing a pact charges a real Vessel,
 * starts two real clocks and puts the ritual on cooldown, all in one call. The arithmetic behind
 * both is pinned in {@code BloodPriceTest} and {@code BloodSacrificeServiceTest}.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class BloodSacrificeGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);

    /** Two hearts, at the rate that makes a heart worth fifty. */
    private static final int TWO_HEARTS = 4 * BloodService.COST_PER_HEALTH;

    private BloodSacrificeGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** A survival blood mage standing on the floor, so the bill is real. */
    private static ServerPlayer bloodMage(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "pact-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // A player is invulnerable until their client reports the world loaded; a fake one never does.
        player.setClientLoaded(true);
        Vec3 stand = onFloor(helper, STAND);
        player.teleportTo(stand.x, stand.y, stand.z);
        state(player).unlockAll(Set.of(MagicContent.BLOOD_SACRIFICE.id()));
        return player;
    }

    private static Vec3 onFloor(GameTestHelper helper, BlockPos at) {
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        Vec3 floor = com.efkrdnz.magical.magic.cast.AimResolver.groundBelow(helper.getLevel(), above, 8);
        return floor != null ? floor : above;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "sacrifice_1")
    public static void aPriceInFleshIgnoresArmourAndBarrierAlike(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper);
        PlayerMagicState state = state(player);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        state.addBarrier(state.maxBarrier());

        helper.runAtTickTime(2, () -> {
            float health = player.getHealth();
            int barrier = state.barrier();
            helper.assertTrue(barrier > 0, "the test needs a barrier standing to prove it is not touched");

            helper.assertTrue(BloodService.payInHealthOnly(player, state, TWO_HEARTS),
                    "a full-health mage can afford two hearts");

            float paid = health - player.getHealth();
            helper.assertTrue(Math.abs(paid - 4.0F) < 0.01F,
                    "a hundred blood is two hearts of flesh whatever is worn over it, paid " + paid);
            helper.assertTrue(state.barrier() == barrier,
                    "the barrier soaked part of a price it is meant to be blind to");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "sacrifice_2")
    public static void aSealedPactEmptiesTheVesselAndStartsBothClocks(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper);
        PlayerMagicState state = state(player);
        state.addBloodVessel(BloodSacrificeService.RITUAL_COST);

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(BloodSacrificeService.seal(player, state,
                            List.of(MagicPassiveContent.CRIMSON_EDGE.id()),
                            List.of(MagicPassiveContent.THIN_SKIN.id())),
                    "a fair pact on a full Vessel must be sealed");

            helper.assertTrue(state.bloodVessel() == 0, "the whole Vessel goes, and it went " + state.bloodVessel());
            int boon = state.ritualRemaining(MagicPassiveContent.CRIMSON_EDGE.id());
            int price = state.ritualRemaining(MagicPassiveContent.THIN_SKIN.id());
            helper.assertTrue(boon > 0, "the boon must be running");
            helper.assertTrue(price == BloodSacrificeService.priceTicks(boon),
                    "the price must outlive the boon by half again: boon " + boon + ", price " + price);
            helper.assertTrue(state.isPassiveEnabled(MagicPassiveContent.CRIMSON_EDGE.id()),
                    "a granted boon must be switched on");
            helper.assertTrue(state.skillCooldown(MagicContent.BLOOD_SACRIFICE.id()) > 0,
                    "the cooldown starts at the seal, not at the cast");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "sacrifice_3")
    public static void aVesselOneShortOfFullBuysNothingAndLosesNothing(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper);
        PlayerMagicState state = state(player);
        state.addBloodVessel(BloodSacrificeService.RITUAL_COST - 1);

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!BloodSacrificeService.seal(player, state,
                            List.of(MagicPassiveContent.CRIMSON_EDGE.id()),
                            List.of(MagicPassiveContent.THIN_SKIN.id())),
                    "a Vessel one short is not a full Vessel");

            helper.assertTrue(state.bloodVessel() == BloodSacrificeService.RITUAL_COST - 1,
                    "a refused pact took blood anyway");
            helper.assertTrue(!state.hasPassive(MagicPassiveContent.CRIMSON_EDGE.id()),
                    "a refused pact granted its boon anyway");
            helper.assertTrue(state.skillCooldown(MagicContent.BLOOD_SACRIFICE.id()) == 0,
                    "a refused pact started the cooldown anyway");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "sacrifice_4")
    public static void aBloodDebtIsPaidInCorruptionAndHasNoClockToWaitOut(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper);
        PlayerMagicState state = state(player);
        state.addBloodVessel(BloodSacrificeService.RITUAL_COST);
        int corruption = state.corruption();

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(BloodSacrificeService.seal(player, state,
                            List.of(MagicPassiveContent.SECOND_HEART.id()),
                            List.of(MagicPassiveContent.BLOOD_DEBT.id())),
                    "three points of boon against four points of debt is a pact");

            helper.assertTrue(state.corruption() == corruption + BloodSacrificeService.BLOOD_DEBT_CORRUPTION,
                    "the debt is paid in corruption, and it went to " + state.corruption());
            helper.assertTrue(state.ritualRemaining(MagicPassiveContent.BLOOD_DEBT.id()) == 0,
                    "the debt must have no clock: waiting is exactly what it does not allow");
            helper.assertTrue(!state.hasPassive(MagicPassiveContent.BLOOD_DEBT.id()),
                    "the debt is not a passive, it is a number that stays");
            helper.succeed();
        });
    }

    /**
     * The clock against a live player, driven the way the player tick drives it.
     *
     * <p>{@code tickServer} is called here rather than waited for: a fake player in the game-test
     * server never receives {@code PlayerTickEvent}, so nothing in
     * {@code MagicGameplayEvents.onPlayerTick} runs for one - not even the mana regeneration at the
     * top of it. What this still shows, and a unit test cannot, is that the call works on a real
     * {@code ServerPlayer} on a real level: the passive is really removed and the sync that follows
     * it really goes out over a connection.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "sacrifice_5")
    public static void aPactRunsDownOnTheServerTickAndLetsGoByItself(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper);
        PlayerMagicState state = state(player);

        helper.runAtTickTime(2, () -> state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 20));
        helper.runAtTickTime(10, () -> {
            for (int tick = 0; tick < 19; tick++) {
                state.tickServer(player);
            }
            helper.assertTrue(state.ritualRemaining(MagicPassiveContent.CRIMSON_EDGE.id()) == 1,
                    "nineteen ticks off twenty must leave one, and left "
                            + state.ritualRemaining(MagicPassiveContent.CRIMSON_EDGE.id()));
            helper.assertTrue(state.hasPassive(MagicPassiveContent.CRIMSON_EDGE.id()),
                    "the boon went a tick early");
        });
        helper.runAtTickTime(20, () -> {
            state.tickServer(player);
            helper.assertTrue(state.ritualRemaining(MagicPassiveContent.CRIMSON_EDGE.id()) == 0,
                    "the last tick did not run the clock out");
            helper.assertTrue(!state.hasPassive(MagicPassiveContent.CRIMSON_EDGE.id()),
                    "the boon outlived its clock");
            helper.assertTrue(!state.isPassiveEnabled(MagicPassiveContent.CRIMSON_EDGE.id()),
                    "an expired boon is still switched on");
            helper.succeed();
        });
    }
}
