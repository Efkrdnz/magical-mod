package com.efkrdnz.magical.gametest;

import com.efkrdnz.magical.magic.cast.AimResolver;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Fake players for gametests, made the way {@code EldritchGameTests.eldritchMage} makes them: a
 * survival player on the floor of a template cell, client-loaded so damage reaches them, with
 * every earlier test's leftover player removed first (fake players outlive their tests and stand
 * in the neighbouring structures, as hostile as any other player). A fake player never receives
 * a {@code PlayerTickEvent}, so cooldowns it sets never tick down; and no client moves one, so
 * gravity never sets it down either, which is why it is put on the floor by hand.
 */
public final class GameTestPlayers {

    private GameTestPlayers() {
    }

    /** A survival player called {@code name} (end it in {@code -test}, so the next test can find and remove it) standing on the floor under {@code at}. */
    public static ServerPlayer survival(GameTestHelper helper, BlockPos at, String name) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
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
        // A player is invulnerable until their client reports the world loaded; a fake one never does.
        player.setClientLoaded(true);
        // The second gate, and the one a caster's own spell falls foul of: ServerPlayer.hurt refuses
        // any blow whose damage source names a player when PVP is off, and a caster is the named
        // entity of their own blast. An integrated server turns PVP on; the gametest server leaves
        // it off, so a fake player would be immune to everything they cast at their own feet.
        server.setPvpAllowed(true);
        Vec3 stand = onFloor(helper, at);
        player.teleportTo(stand.x, stand.y, stand.z);
        return player;
    }

    /** The floor under a template cell, absolute: a thing put there starts where it would land. */
    public static Vec3 onFloor(GameTestHelper helper, BlockPos at) {
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        Vec3 floor = AimResolver.groundBelow(helper.getLevel(), above, 8);
        return floor != null ? floor : above;
    }
}
