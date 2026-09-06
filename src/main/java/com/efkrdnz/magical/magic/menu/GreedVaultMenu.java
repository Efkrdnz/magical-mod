package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class GreedVaultMenu extends AbstractContainerMenu {
    public static final int BUTTON_DEPOSIT_ALL = 0;
    public static final int BUTTON_BUY_MAX_MANA = 1;
    public static final int BUTTON_BUY_MAX_BARRIER = 2;
    public static final int BUTTON_BUY_PASSIVE_BASE = 100;

    private final Player player;
    private final PlayerMagicState state;

    public GreedVaultMenu(int containerId, Inventory inventory) {
        super(MagicalMenus.GREED_VAULT.get(), containerId);
        this.player = inventory.player;
        this.state = player.getData(MagicalAttachments.MAGIC_STATE);
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
        if (!state.isPassiveEnabled(MagicPassiveContent.SIN_GREED.id())) {
            return true;
        }
        if (id == BUTTON_DEPOSIT_ALL) {
            state.depositAllManaToVault();
            syncIfServer();
            return true;
        }
        if (id == BUTTON_BUY_MAX_MANA) {
            state.buyMaxManaUpgrade();
            syncIfServer();
            return true;
        }
        if (id == BUTTON_BUY_MAX_BARRIER) {
            state.buyMaxBarrierUpgrade();
            syncIfServer();
            return true;
        }
        if (id >= BUTTON_BUY_PASSIVE_BASE && id < BUTTON_BUY_PASSIVE_BASE + MagicPassiveContent.normalPassives().size()) {
            ResourceLocation passiveId = MagicPassiveContent.normalPassives().get(id - BUTTON_BUY_PASSIVE_BASE).id();
            MagicPassiveDefinition definition = MagicPassiveContent.get(passiveId);
            if (definition != null && definition.shopTier() > 0) {
                state.buyShopPassive(passiveId);
                syncIfServer();
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    private void syncIfServer() {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            state.sync(serverPlayer);
        }
    }
}
