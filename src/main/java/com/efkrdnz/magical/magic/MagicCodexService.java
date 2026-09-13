package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.menu.MagicPyramidMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public final class MagicCodexService {
    private MagicCodexService() {}

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new MagicPyramidMenu(containerId, inventory),
                Component.translatable("screen.magical.magic_codex")));
    }
}
