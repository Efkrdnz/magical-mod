package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.registry.MagicalMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class SpaceWalkerMenu extends AbstractContainerMenu {
    public SpaceWalkerMenu(int containerId, Inventory inventory) {
        super(MagicalMenus.SPACE_WALKER.get(), containerId);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
