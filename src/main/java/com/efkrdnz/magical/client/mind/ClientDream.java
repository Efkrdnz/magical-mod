package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.network.DreamStatePayload;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** What the client knows of its own dream: whether it is in it, and where its Flaw is. */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class ClientDream {
    private static boolean ownDream;
    private static BlockPos flawBlock;
    private static int flawEntity = -1;

    private ClientDream() {}

    public static void accept(DreamStatePayload payload) {
        ownDream = payload.ownDream();
        flawBlock = payload.flawBlock().orElse(null);
        flawEntity = payload.flawEntity();
    }

    public static boolean ownDream() { return ownDream; }
    public static BlockPos flawBlock() { return flawBlock; }
    public static int flawEntity() { return flawEntity; }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ownDream = false;
        flawBlock = null;
        flawEntity = -1;
    }
}
