package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalMenus;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The first-spawn starting class chooser. Slotless: the screen is the whole interaction, and the
 * only button ids are one per starting root, offset by {@link #BUTTON_CHOOSE_BASE}. The choice
 * itself runs through {@link PlayerMagicState#chooseStartingClass}, so the rules about which
 * classes are selectable and whether one was already picked live in exactly one place.
 */
public final class ClassSelectMenu extends AbstractContainerMenu {
    public static final int BUTTON_CHOOSE_BASE = 0;

    private final Player player;
    private final PlayerMagicState state;

    public ClassSelectMenu(int containerId, Inventory inventory) {
        super(MagicalMenus.CLASS_SELECT.get(), containerId);
        this.player = inventory.player;
        this.state = player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** The classes offered, in registration order; the screen and the menu must agree on this. */
    public static List<MagicalClassDefinition> choices() {
        return MagicalClasses.startingRoots();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        List<MagicalClassDefinition> choices = choices();
        if (id >= BUTTON_CHOOSE_BASE && id < BUTTON_CHOOSE_BASE + choices.size()) {
            MagicalClassDefinition definition = choices.get(id - BUTTON_CHOOSE_BASE);
            if (player instanceof ServerPlayer serverPlayer && state.chooseStartingClass(serverPlayer, definition.id())) {
                state.sync(serverPlayer);
                serverPlayer.displayClientMessage(
                        Component.translatable("message.magical.starting_class_chosen", Component.translatable(definition.nameKey())), false);
                serverPlayer.closeContainer();
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }
}
