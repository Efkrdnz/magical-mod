package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.MagicCodexService;
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
 * The class evolution tree. Slotless, with its own id space so it never collides with the codex's
 * hand-packed button ranges. Taking a node routes through {@link PlayerMagicState#evolveClass},
 * so the convergence and XP-spending rules live in exactly one place.
 */
public final class ClassTreeMenu extends AbstractContainerMenu {
    public static final int BUTTON_EVOLVE_BASE = 0;
    public static final int BUTTON_OPEN_FORGE = 5000;
    public static final int BUTTON_OPEN_SPELL_CREATOR = 5001;
    public static final int BUTTON_OPEN_CODEX = 5002;

    private final Player player;
    private final PlayerMagicState state;

    public ClassTreeMenu(int containerId, Inventory inventory) {
        super(MagicalMenus.CLASS_TREE.get(), containerId);
        this.player = inventory.player;
        this.state = player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** Node order shared by screen and menu, so a button index means the same thing on both sides. */
    public static List<MagicalClassDefinition> nodes() {
        return List.copyOf(MagicalClasses.all());
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
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (id == BUTTON_OPEN_FORGE) {
            if (state.hasClass(MagicalClasses.BLACKSMITH)) {
                com.efkrdnz.magical.forge.BlacksmithForgeService.open(serverPlayer);
            }
            return true;
        }
        if (id == BUTTON_OPEN_SPELL_CREATOR) {
            if (state.hasClass(MagicalClasses.SPELL_CREATOR)) {
                MagicCodexService.openAt(serverPlayer, MagicCodexService.View.SPELL_CREATOR);
            }
            return true;
        }
        if (id == BUTTON_OPEN_CODEX) {
            MagicCodexService.open(serverPlayer);
            return true;
        }
        List<MagicalClassDefinition> nodes = nodes();
        if (id >= BUTTON_EVOLVE_BASE && id < BUTTON_EVOLVE_BASE + nodes.size()) {
            MagicalClassDefinition definition = nodes.get(id - BUTTON_EVOLVE_BASE);
            if (state.evolveClass(serverPlayer, definition.id())) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.magical.class_evolved", Component.translatable(definition.nameKey())), false);
                state.sync(serverPlayer);
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }
}
