package com.efkrdnz.magical.magic.sword.rack;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The twelve sockets a wielder racks their own weapons in: socket <i>i</i> is flown as sword
 * <i>i</i> of the formation.
 *
 * <p><b>Its own attachment, and deliberately not a field on {@code PlayerMagicState}.</b> The
 * magic state is copy-based and wholesale - it is replaced on reset, cloned on sync, written
 * without a registry - and none of that is what real items want. A rack in the state would be
 * emptied by {@code /magical reset}, and an item is the one thing the mod must never destroy on
 * the player's behalf. Here it is saved with the registry the items need, copied on death so it is
 * kept on death, and left alone by everything that rebuilds the magic state.
 *
 * <p>A socket holds one weapon. What may be put in one is the menu's question
 * ({@link SwordRackRules}, and the rung's count of sockets); this is only the storage, so a weapon
 * that stopped being allowed - a rung lost, Weapon God gone - stays where it was and can always be
 * taken back out.
 */
public final class SwordRack implements Container {

    /** One socket per sword the apex rung fields. */
    public static final int SIZE = 12;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items, provider);
        return tag;
    }

    public static SwordRack load(CompoundTag tag, HolderLookup.Provider provider) {
        SwordRack rack = new SwordRack();
        ContainerHelper.loadAllItems(tag, rack.items, provider);
        return rack;
    }

    /** A deep copy: a clone that shared its stacks would move items between two players. */
    public SwordRack copy() {
        SwordRack copy = new SwordRack();
        for (int i = 0; i < SIZE; i++) {
            copy.items.set(i, items.get(i).copy());
        }
        return copy;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SIZE ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /**
     * Nothing to do. The rack is saved with its player, and the formation reads it every tick, so
     * a weapon racked is a weapon flying on the next one.
     */
    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public void clearContent() {
        items.clear();
    }
}
