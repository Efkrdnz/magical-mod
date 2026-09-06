package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.menu.ClassSelectMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Opens the starting class chooser on one player's client, mirroring {@link MagicCodexService}. */
public final class ClassSelectService {
    private ClassSelectService() {}

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new ClassSelectMenu(containerId, inventory),
                Component.translatable("screen.magical.class_select")));
    }
}
