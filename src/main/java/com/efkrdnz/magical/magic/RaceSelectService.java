package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.menu.RaceSelectMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Opens the race chooser on one player's client, mirroring {@link ClassSelectService}. */
public final class RaceSelectService {
    private RaceSelectService() {}

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new RaceSelectMenu(containerId, inventory),
                Component.translatable("screen.magical.race_select")));
    }
}
