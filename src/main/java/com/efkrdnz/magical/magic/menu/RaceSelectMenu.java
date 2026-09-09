package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.race.MagicalRace;
import com.efkrdnz.magical.race.MagicalRaces;
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
 * The first-spawn race chooser, shown before the class chooser and before any magic is granted.
 *
 * <p>Slotless, exactly like {@link ClassSelectMenu}: one button id per race offset by
 * {@link #BUTTON_CHOOSE_RACE}. The choice runs through {@link PlayerMagicState#chooseRace}, so the
 * once-only rule lives in one place and a crafted packet cannot re-roll a race.
 */
public final class RaceSelectMenu extends AbstractContainerMenu {
    public static final int BUTTON_CHOOSE_RACE = 0;

    private final PlayerMagicState state;

    public RaceSelectMenu(int containerId, Inventory inventory) {
        super(MagicalMenus.RACE_SELECT.get(), containerId);
        this.state = inventory.player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** The races offered, in registration order; the screen and the menu must agree on this. */
    public static List<MagicalRace> choices() {
        return MagicalRaces.all();
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
        List<MagicalRace> choices = choices();
        if (id >= BUTTON_CHOOSE_RACE && id < BUTTON_CHOOSE_RACE + choices.size()) {
            MagicalRace race = choices.get(id - BUTTON_CHOOSE_RACE);
            if (player instanceof ServerPlayer serverPlayer && state.chooseRace(race.id())) {
                state.sync(serverPlayer);
                serverPlayer.displayClientMessage(
                        Component.translatable("message.magical.race_chosen", Component.translatable(race.nameKey())), false);
                serverPlayer.closeContainer();
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }
}
