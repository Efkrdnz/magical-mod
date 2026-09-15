package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.SpaceSubspaceEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Pushes this client's own player through whatever subspace laws are standing over them.
 *
 * <p>The server cannot do it. A law's push is a velocity, and a velocity written onto a
 * server-side player goes nowhere: vanilla sends the motion packet for {@code hasImpulse} over
 * {@code broadcast}, and a player is never in its own audience, so the one law that ever reached a
 * player was Control Gravity - which works only because it hands out flight through the abilities
 * packet instead. The one channel that would reach them, {@code hurtMarked}, replaces the client's
 * velocity with the server's copy, and the server has no real copy of a player's velocity to send.
 *
 * <p>So the push is made here, where the player's velocity actually lives, and only ever against
 * the player at this client - every other player in the subspace is doing the same at theirs.
 * {@code PlayerTickEvent.Pre} fires at the top of {@code Player.tick()}, before the player's own
 * physics and input run, so a law sets the velocity the rest of the tick works against rather than
 * fighting over the result. The server keeps the other half of every law: the damage, the effects,
 * the air, the flight, and the body moved across a boundary.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class SpaceLawClient {

    private SpaceLawClient() {
    }

    @SubscribeEvent
    public static void beforePlayerPhysics(PlayerTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || event.getEntity() != player || player.isSpectator()) {
            return;
        }
        double reach = SpaceSubspaceEntity.maxSearchRadius();
        for (SpaceSubspaceEntity subspace : level.getEntitiesOfClass(SpaceSubspaceEntity.class, player.getBoundingBox().inflate(reach))) {
            subspace.applyLocalPlayerMotion(player);
        }
    }
}
