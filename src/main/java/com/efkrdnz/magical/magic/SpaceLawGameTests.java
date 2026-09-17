package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Where a law lands, run against a real level.
 *
 * <p>The split these pin is the whole point of {@link DomainPass}: a law's push is written by
 * whichever side owns the pushed thing's movement, and for a player that is never the server. The
 * first test would have failed the other way round before the split - the server wrote a player's
 * velocity happily, and the write simply never reached them, so the law looked dead from inside
 * the subspace. The second is the other half: losing the push must not lose the price.
 *
 * <p>What a client does with its own player cannot be tested here, because there is no client.
 * That half is {@code SpaceLawClient}, and it is verified in a dev client capture.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class SpaceLawGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);
    /** North of the caster, with room to be pushed further north before it meets a wall. */
    private static final BlockPos VICTIM = new BlockPos(2, 2, 4);
    private static final float RADIUS = 5.0F;

    private SpaceLawGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "space_law_1")
    public static void aLawMovesAMobAndNeverWritesAPlayersVelocity(GameTestHelper helper) {
        ServerPlayer player = mage(helper);
        Zombie husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, VICTIM)));
        helper.runAtTickTime(1, () -> {
            SpaceAuthorityService.raiseDebugSubspace(player, RADIUS, true);
            // A law that acts along one axis only, so the reading is unambiguous: whatever ends up
            // in z, nothing else in a test world would have put it there.
            SpaceAuthorityService.applyDebugRule(player, SpaceRuleCategory.VECTOR_FIELD,
                    SpaceRuleOperation.PULL_NORTH, SpaceTargetGroup.EVERYTHING);
            player.setDeltaMovement(Vec3.ZERO);
            husk.setDeltaMovement(Vec3.ZERO);
        });
        // Early enough that the husk is still well short of the wall, which would zero z on contact.
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(husk.getDeltaMovement().z < -0.1D,
                    "the pull must reach a mob: z=" + husk.getDeltaMovement().z);
            helper.assertTrue(Math.abs(player.getDeltaMovement().z) < 1.0E-6D,
                    "and must never be written onto a player by the server, which cannot deliver it: z="
                            + player.getDeltaMovement().z);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "space_law_2")
    public static void aPlayerStillPaysALawThatCannotPushThem(GameTestHelper helper) {
        ServerPlayer player = mage(helper);
        helper.runAtTickTime(1, () -> {
            SpaceAuthorityService.raiseDebugSubspace(player, RADIUS, true);
            SpaceAuthorityService.applyDebugRule(player, SpaceRuleCategory.MASS,
                    SpaceRuleOperation.ANCHOR_MASS, SpaceTargetGroup.EVERYTHING);
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "an anchored player still carries the weight, though the push cannot reach them");
            helper.succeed();
        });
    }

    /**
     * A survival player on the floor of the cell. Fake players outlive their tests and stand in the
     * neighbouring structures, and a subspace is five blocks wide at its smallest - wider than one
     * cell - so each test runs in a batch of its own and clears the ones before it first.
     */
    private static ServerPlayer mage(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
                SpaceAuthorityService.closeAllDomains(leftover, leftover.getData(MagicalAttachments.MAGIC_STATE), false);
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "space-test"), false);
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
        return player;
    }

    /** The floor under a template cell, absolute: a thing put there starts where it would land. */
    private static Vec3 onFloor(GameTestHelper helper, BlockPos at) {
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        Vec3 floor = com.efkrdnz.magical.magic.cast.AimResolver.groundBelow(helper.getLevel(), above, 8);
        return floor != null ? floor : above;
    }
}
