package com.efkrdnz.magical.arcane;

import com.efkrdnz.magical.MagicalConfig;
import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = MagicalMod.MODID)
public final class ArcaneProgression {
    private ArcaneProgression() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ArcanePlayerData data = player.getData(MagicalAttachments.ARCANE_DATA);
        if (MagicalConfig.STARTER_UNLOCK_ON_LOGIN.get() && data.unlockedComponents().isEmpty()) {
            data.unlockAll(ArcaneContent.STARTER_UNLOCKS);
        }
        data.setMana(Math.max(1, data.mana()));
        data.sync(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int interval = MagicalConfig.MANA_REGEN_INTERVAL_TICKS.get();
        if (interval <= 0 || player.tickCount % interval != 0) {
            return;
        }
        ArcanePlayerData data = player.getData(MagicalAttachments.ARCANE_DATA);
        int before = data.mana();
        data.setMana(before + MagicalConfig.MANA_PER_REGEN.get());
        if (before != data.mana()) {
            data.sync(player);
        }
    }
}
