package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The harvest's loop, run against a real level: spilled, waited for, lifted, landed, paid.
 *
 * <p>Everything a unit test can hold is held elsewhere - the rules in {@code BloodHarvestRulesTest},
 * the motion in {@code BloodHarvestMotionTest}. What only a level can show is that the entity
 * actually ticks through its phases against a player who is or is not there, and that the Vessel
 * moves exactly once, by exactly the yield, when the blood lands and not before.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class BloodHarvestGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final String BATCH = "blood_harvest";

    private static final BlockPos NEAR = new BlockPos(2, 2, 2);
    private static final Vec3 CORPSE = new Vec3(5.5D, 2.05D, 2.5D);

    /** Well outside pull range, still inside the chunks the player themselves keeps loaded. */
    private static final BlockPos FAR = new BlockPos(2, 2, 16);

    private BloodHarvestGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void nearbyBloodLiftsStreamsInAndFillsTheVesselWhenItLands(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, NEAR);
        int before = state(player).bloodVessel();
        int yield = BloodHarvestRules.VESSEL_PER_DROP;
        BloodHarvestEntity pool = BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE), yield);
        double distance = BloodHarvestEntity.chestOf(player).distanceTo(pool.position());
        int flight = BloodHarvestRules.flightTicks(distance);

        helper.runAtTickTime(3, () -> {
            helper.assertTrue(pool.phase() == BloodHarvestEntity.PHASE_STREAMING, "A pool within reach must lift");
            helper.assertTrue(pool.flight() == flight, "The flight must be the rules' flight for the distance");
            helper.assertTrue(state(player).bloodVessel() == before, "The Vessel must not fill before the blood lands");
        });
        helper.runAtTickTime(3 + flight + 2, () -> {
            helper.assertTrue(state(player).bloodVessel() == before + yield,
                    "The Vessel must fill by exactly the yield when the blood lands, got "
                            + (state(player).bloodVessel() - before));
            helper.assertTrue(pool.isRemoved(), "Landed blood must be gone");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void farBloodPoolsUntilThePlayerComesNear(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, FAR);
        BloodHarvestEntity pool = BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE),
                BloodHarvestRules.VESSEL_PER_DROP);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(pool.isPooled(), "Blood out of reach must wait");
            Vec3 near = helper.absoluteVec(Vec3.atBottomCenterOf(NEAR));
            player.teleportTo(near.x, near.y, near.z);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(pool.phase() == BloodHarvestEntity.PHASE_STREAMING, "Blood must lift once the player is near");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 330, batch = BATCH)
    public static void bloodNobodyComesForDries(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, FAR);
        int before = state(player).bloodVessel();
        BloodHarvestEntity pool = BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE),
                BloodHarvestRules.VESSEL_PER_DROP);
        helper.runAtTickTime(BloodHarvestRules.POOL_LIFETIME - 2, () ->
                helper.assertTrue(pool.isPooled() && !pool.isRemoved(), "A pool must wait out its whole lifetime"));
        helper.runAtTickTime(BloodHarvestRules.POOL_LIFETIME + 3, () -> {
            helper.assertTrue(pool.isRemoved(), "A pool nobody came for must dry");
            helper.assertTrue(state(player).bloodVessel() == before, "Dried blood pays nothing");
            helper.succeed();
        });
    }

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** A player holding one blood skill, which is all it takes to be a blood mage, standing at {@code at}. */
    private static ServerPlayer bloodMage(GameTestHelper helper, BlockPos at) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "harvest-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        player.teleportTo(stand.x, stand.y, stand.z);
        state(player).unlockAll(Set.of(MagicContent.CRIMSON_TITHE.id()));
        return player;
    }
}
