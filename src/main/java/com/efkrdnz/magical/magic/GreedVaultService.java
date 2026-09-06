package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.menu.GreedVaultMenu;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;

public final class GreedVaultService {
    private GreedVaultService() {}

    public static void open(ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.isPassiveEnabled(MagicPassiveContent.SIN_GREED.id())) {
            player.displayClientMessage(Component.translatable("message.magical.greed_required"), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new GreedVaultMenu(containerId, inventory),
                Component.translatable("screen.magical.greed_vault")));
    }

    public static void depositAll(ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.isPassiveEnabled(MagicPassiveContent.SIN_GREED.id())) {
            player.displayClientMessage(Component.translatable("message.magical.greed_required"), true);
            return;
        }
        int deposited = state.depositAllManaToVault();
        state.sync(player);
        if (deposited <= 0) {
            player.displayClientMessage(Component.translatable("message.magical.vault_no_mana"), true);
            return;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 0.55F);
        player.displayClientMessage(Component.translatable("message.magical.vault_deposited", deposited, state.manaVault()), true);
    }
}
