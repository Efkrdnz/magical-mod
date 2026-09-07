package com.efkrdnz.magical.forge.menu;

import com.efkrdnz.magical.forge.ForgeMaterials;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.registry.MagicalMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Runeforge menu: one weapon slot the player draws runes onto, plus their own inventory. The
 * chain is validated and applied server-side by {@code BlacksmithForgeService}; this menu only
 * hosts the weapon and mirrors the codex screen's inventory layout.
 */
public final class BlacksmithForgeMenu extends AbstractContainerMenu {
    public static final int WEAPON_SLOT_INDEX = 0;
    public static final int WEAPON_SLOT_X = 214;
    public static final int WEAPON_SLOT_Y = 30;
    public static final int PLAYER_INV_X = 40;
    public static final int PLAYER_INV_Y = 242;
    public static final int HOTBAR_Y = 300;

    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    private final Player player;
    private final SimpleContainer container = new SimpleContainer(1);

    public BlacksmithForgeMenu(int containerId, Inventory inventory) {
        super(MagicalMenus.BLACKSMITH_FORGE.get(), containerId);
        this.player = inventory.player;
        addSlot(new ForgeWeaponSlot(container, WEAPON_SLOT_INDEX, WEAPON_SLOT_X, WEAPON_SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_INV_X + column * 18, HOTBAR_Y));
        }
    }

    public ItemStack weaponStack() {
        return weaponSlot().getItem();
    }

    public Slot weaponSlot() {
        return getSlot(WEAPON_SLOT_INDEX);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index == WEAPON_SLOT_INDEX) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (ForgeMaterials.isForgeable(stack)) {
            if (!moveItemStackTo(stack, WEAPON_SLOT_INDEX, WEAPON_SLOT_INDEX + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, container);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return false;
    }

    /** Only accepts forgeable weapons, caps at one, and migrates legacy runes on placement. */
    private final class ForgeWeaponSlot extends Slot {
        private ForgeWeaponSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ForgeMaterials.isForgeable(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!player.level().isClientSide()) {
                ForgedWeapons.getOrMigrate(getItem());
            }
        }
    }
}
