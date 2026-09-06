package com.efkrdnz.magical.magic;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public final class SpaceArsenalStorage extends SimpleContainer {
    public static final int SLOT_COUNT = 54;
    public static final int SLOT_CAPACITY = 999;
    private static final String DATA_KEY = "MagicalSpaceRift";
    private static final String ITEMS_KEY = "Items";
    private final ServerPlayer owner;

    private SpaceArsenalStorage(ServerPlayer owner) {
        super(SLOT_COUNT);
        this.owner = owner;
        load();
    }

    public static SpaceArsenalStorage open(ServerPlayer owner) {
        return new SpaceArsenalStorage(owner);
    }

    @Override
    public int getMaxStackSize() {
        return SLOT_CAPACITY;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return SLOT_CAPACITY;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (!stack.isEmpty() && stack.getCount() > SLOT_CAPACITY) {
            stack = stack.copyWithCount(SLOT_CAPACITY);
        }
        super.setItem(index, stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (owner != null) {
            save();
        }
    }

    private void load() {
        CompoundTag root = owner.getPersistentData().getCompound(DATA_KEY);
        if (!root.contains(ITEMS_KEY, Tag.TAG_LIST)) {
            return;
        }
        HolderLookup.Provider lookup = owner.registryAccess();
        ListTag items = root.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag slotTag = items.getCompound(i);
            int slot = slotTag.getInt("Slot");
            if (slot < 0 || slot >= getContainerSize() || !slotTag.contains("Stack", Tag.TAG_COMPOUND)) {
                continue;
            }
            ItemStack stack = ItemStack.parseOptional(lookup, slotTag.getCompound("Stack"));
            if (!stack.isEmpty()) {
                stack.setCount(Mth.clamp(slotTag.getInt("Count999"), 1, SLOT_CAPACITY));
                getItems().set(slot, stack);
            }
        }
    }

    public void save() {
        HolderLookup.Provider lookup = owner.registryAccess();
        ListTag items = new ListTag();
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("Slot", slot);
            slotTag.putInt("Count999", Mth.clamp(stack.getCount(), 1, SLOT_CAPACITY));
            slotTag.put("Stack", stack.copyWithCount(Math.min(stack.getCount(), 99)).saveOptional(lookup));
            items.add(slotTag);
        }
        CompoundTag root = new CompoundTag();
        root.put(ITEMS_KEY, items);
        owner.getPersistentData().put(DATA_KEY, root);
    }
}
