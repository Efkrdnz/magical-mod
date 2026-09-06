package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.magic.SpaceArsenalStorage;
import com.efkrdnz.magical.registry.MagicalMenus;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SpaceArsenalStorageMenu extends AbstractContainerMenu {
    private static final int ROWS = 6;
    private static final int COLUMNS = 9;
    private final Container storage;
    private final int[] storageCounts = new int[SpaceArsenalStorage.SLOT_COUNT];

    /**
     * Client-side constructor. The real contents arrive by slot sync, so this only needs a
     * container of the right size to hang the slots on.
     */
    public SpaceArsenalStorageMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new net.minecraft.world.SimpleContainer(SpaceArsenalStorage.SLOT_COUNT));
    }

    public SpaceArsenalStorageMenu(int containerId, Inventory playerInventory, Container storage) {
        super(MagicalMenus.SPACE_ARSENAL_STORAGE.get(), containerId);
        checkContainerSize(storage, SpaceArsenalStorage.SLOT_COUNT);
        this.storage = storage;
        storage.startOpen(playerInventory.player);

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new RiftSlot(storage, column + row * COLUMNS, 8 + column * 18, 18 + row * 18));
            }
        }

        int playerInventoryY = 103 + (ROWS - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(playerInventory, column + row * COLUMNS + COLUMNS, 8 + column * 18, playerInventoryY + row * 18));
            }
        }

        int hotbarY = 161 + (ROWS - 4) * 18;
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, hotbarY));
        }

        for (int slot = 0; slot < SpaceArsenalStorage.SLOT_COUNT; slot++) {
            final int storageSlot = slot;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return SpaceArsenalStorageMenu.this.storage.getItem(storageSlot).getCount();
                }

                @Override
                public void set(int value) {
                    storageCounts[storageSlot] = value;
                }
            });
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return storage.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < SpaceArsenalStorage.SLOT_COUNT) {
                if (!moveItemStackTo(stack, SpaceArsenalStorage.SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, SpaceArsenalStorage.SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        storage.stopOpen(player);
        if (storage instanceof SpaceArsenalStorage arsenalStorage) {
            arsenalStorage.save();
        }
    }

    public int displayCount(int slot) {
        if (slot < 0 || slot >= storageCounts.length) {
            return 0;
        }
        int syncedCount = storageCounts[slot];
        if (syncedCount > 0) {
            return syncedCount;
        }
        return storage.getItem(slot).getCount();
    }

    private static final class RiftSlot extends Slot {
        private RiftSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return SpaceArsenalStorage.SLOT_CAPACITY;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return SpaceArsenalStorage.SLOT_CAPACITY;
        }
    }
}
