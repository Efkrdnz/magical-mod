package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code /magical max} moves the base of the two pools by hand. It is its own bonus, beside the one
 * the Greed vault's upgrades buy: the vault, its bought upgrades and its prices never move.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class MaxPoolCommandGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private static void run(ServerPlayer player, String command) {
        player.server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20, batch = "max_pool_1")
    public static void theCommandRaisesTheBaseAndLeavesTheVaultAlone(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "max-pool-test");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.setManaVault(500);
        int mana = state.maxMana();
        int barrier = state.maxBarrier();

        run(player, "magical max mana add 50");
        run(player, "magical max barrier add 20");
        helper.assertTrue(state.maxMana() == mana + 50, "max mana did not rise by 50: " + state.maxMana());
        helper.assertTrue(state.maxBarrier() == barrier + 20, "max barrier did not rise by 20: " + state.maxBarrier());
        helper.assertTrue(state.manaVault() == 500, "the command touched the vault");
        helper.assertTrue(state.maxManaBonus() == 0 && state.maxBarrierBonus() == 0, "the command wrote the vault's bought upgrades");
        helper.assertTrue(state.manaBoostPurchases() == 0 && state.barrierBoostPurchases() == 0, "the command counted a vault purchase");

        run(player, "magical max mana add -80");
        helper.assertTrue(state.maxMana() == mana, "lowering went below the default: " + state.maxMana());
        run(player, "magical max barrier reset");
        helper.assertTrue(state.maxBarrier() == barrier, "reset did not restore the default barrier: " + state.maxBarrier());

        PlayerMagicState loaded = PlayerMagicState.load(state.save());
        run(player, "magical max mana add 30");
        helper.assertTrue(PlayerMagicState.load(state.save()).maxMana() == mana + 30, "the base bonus is not saved");
        helper.assertTrue(loaded.maxMana() == mana, "a save taken before the raise already had it");
        helper.succeed();
    }
}
