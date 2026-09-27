package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.sword.rack.SwordArms;
import com.efkrdnz.magical.magic.sword.rack.SwordRack;
import com.efkrdnz.magical.magic.sword.rack.SwordRackLayout;
import com.efkrdnz.magical.magic.sword.rack.SwordRackRules;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The rack: twelve sockets round a ring, and the player's inventory under it.
 *
 * <p>Opened by sneaking as Call the Blade is pressed, or by {@code /magical sword rack}. The four
 * numbers the screen needs - how many sockets the rung opens, whether Weapon God is held, how many
 * the stance fields, which stance it is - ride {@link ContainerData}, read off the live state every
 * time the server asks, so a rung climbed with the rack open opens its sockets without a reopen.
 *
 * <p><b>A socket refuses, it never takes.</b> A locked socket or a weapon the wielder may not fly
 * is refused on the way in; nothing already in a socket is ever refused on the way out, so a
 * weapon racked before a rung was lost is always there to take back.
 */
public final class SwordRackMenu extends AbstractContainerMenu {

    public static final int DATA_UNLOCKED = 0;
    public static final int DATA_WEAPON_GOD = 1;
    public static final int DATA_FIELDED = 2;
    public static final int DATA_STANCE = 3;
    public static final int DATA_COUNT = 4;

    private static final int RACK_END = SwordRack.SIZE;
    private static final int COLUMNS = 9;

    private final Container rack;
    private final ContainerData data;

    /** The client's half: the contents arrive by slot sync and the numbers by data sync. */
    public SwordRackMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SwordRack.SIZE), new SimpleContainerData(DATA_COUNT));
    }

    public SwordRackMenu(int containerId, Inventory inventory, Container rack, ContainerData data) {
        super(MagicalMenus.SWORD_RACK.get(), containerId);
        checkContainerSize(rack, SwordRack.SIZE);
        checkContainerDataCount(data, DATA_COUNT);
        this.rack = rack;
        this.data = data;
        for (int socket = 0; socket < SwordRack.SIZE; socket++) {
            addSlot(new Socket(rack, socket, SwordRackLayout.socketX(socket), SwordRackLayout.socketY(socket)));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(inventory, column + row * COLUMNS + COLUMNS,
                        SwordRackLayout.INVENTORY_X + column * 18, SwordRackLayout.INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new Slot(inventory, column, SwordRackLayout.INVENTORY_X + column * 18, SwordRackLayout.HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, opener) -> new SwordRackMenu(containerId, inventory,
                        SwordArms.rack(player), data(player)),
                Component.translatable("screen.magical.sword_rack")));
    }

    /** The four numbers, read off the live state whenever the menu is asked for them. */
    public static ContainerData data(ServerPlayer player) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                return switch (index) {
                    case DATA_UNLOCKED -> SwordArms.unlocked(state);
                    case DATA_WEAPON_GOD -> SwordArms.weaponGod(state) ? 1 : 0;
                    case DATA_FIELDED -> Math.min(SwordArms.unlocked(state), state.swordArray().swords());
                    case DATA_STANCE -> state.swordArray().stance().ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    public int unlocked() {
        return data.get(DATA_UNLOCKED);
    }

    public boolean weaponGod() {
        return data.get(DATA_WEAPON_GOD) != 0;
    }

    public int fielded() {
        return data.get(DATA_FIELDED);
    }

    public int stanceOrdinal() {
        return data.get(DATA_STANCE);
    }

    /** Whether a socket may be filled: opened by the rung, and a weapon the wielder may fly. */
    public boolean accepts(int socket, ItemStack stack) {
        return socket < unlocked() && SwordRackRules.accepts(stack, weaponGod());
    }

    @Override
    public boolean stillValid(Player player) {
        return rack.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index < RACK_END) {
            if (!moveItemStackTo(stack, RACK_END, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, RACK_END, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    /** One socket: one weapon, and only the kind the wielder may fly. */
    private final class Socket extends Slot {
        private Socket(Container container, int socket, int x, int y) {
            super(container, socket, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return accepts(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }
}
