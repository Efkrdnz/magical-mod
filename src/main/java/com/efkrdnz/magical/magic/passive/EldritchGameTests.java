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
    private static final String BATCH = "eldritch";
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
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "deep-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        player.teleportTo(stand.x, stand.y, stand.z);
        state(player).unlockAll(Set.of(skill));
        state(player).refillMana();
        return player;
    }

    private static Zombie victim(GameTestHelper helper, ServerPlayer player) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, VICTIM);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
        return zombie;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
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
}
