package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.menu.ClassTreeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Opens the radial class evolution tree, mirroring {@link MagicCodexService}. */
public final class ClassTreeService {
    private ClassTreeService() {}

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new ClassTreeMenu(containerId, inventory),
                Component.translatable("screen.magical.class_tree_title")));
    }
}
