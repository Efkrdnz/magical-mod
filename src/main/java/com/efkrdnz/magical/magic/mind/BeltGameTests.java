package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The belt of nine lies: names only, learned only, and offered to as things are learned. */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class BeltGameTests {
    private static final String TEMPLATE = "unwaking_empty";
    private static final String STONE = "block:minecraft:stone";
    private static final String COW = "creature:minecraft:cow";

    private static ServerPlayer wielder(GameTestHelper helper, String name, boolean withAuthority) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 0), name);
        if (withAuthority) {
            player.getData(MagicalAttachments.MAGIC_STATE).setAuthority(AuthorityContent.MIND);
        }
        return player;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "belt_1")
    public static void aLearnedLieGoesOntoTheBeltByName(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "belt-set-test", true);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.mind().lexicon().learn(STONE, 1);
        helper.assertTrue(MindService.setBeltSlot(player, 2, STONE), "a learned lie was refused");
        helper.assertTrue(STONE.equals(state.mind().belt().get(2)), "the belt does not hold it");
        helper.assertTrue(MindService.setBeltSlot(player, 2, ""), "clearing a slot was refused");
        helper.assertTrue(state.mind().belt().get(2) == null, "the slot was not cleared");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "belt_2")
    public static void anUnlearnedOrForgedLieIsRefused(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "belt-refuse-test", true);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.mind().lexicon().learn(STONE, 1);
        helper.assertFalse(MindService.setBeltSlot(player, 0, COW), "an unlearned lie was taken");
        helper.assertFalse(MindService.setBeltSlot(player, 9, STONE), "slot 9 was taken");
        helper.assertFalse(MindService.setBeltSlot(player, -1, STONE), "slot -1 was taken");
        state.mind().lexicon().learn("creature:minecraft:player", 1);
        helper.assertFalse(MindService.setBeltSlot(player, 0, "creature:minecraft:player"), "a player was put on the belt");
        for (int i = 0; i < Belt.SIZE; i++) {
            helper.assertTrue(state.mind().belt().get(i) == null, "a refusal changed slot " + i);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "belt_3")
    public static void aPlayerWithoutTheMindMayNotUseTheBelt(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "belt-plain-test", false);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.mind().lexicon().learn(STONE, 1);
        helper.assertFalse(MindService.setBeltSlot(player, 0, STONE), "a player without the Mind set a slot");
        helper.assertTrue(state.mind().belt().get(0) == null, "the belt changed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "belt_4")
    public static void aNewLieFillsTheFirstEmptySlotOnce(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "belt-learn-test", true);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.mind().belt().set(0, COW);
        MindGazeService.learn(player, state, STONE);
        helper.assertTrue(STONE.equals(state.mind().belt().get(1)), "a new lie did not take the first empty slot");
        MindGazeService.learn(player, state, STONE);
        helper.assertTrue(state.mind().belt().indexOf(STONE) == 1 && state.mind().belt().get(2) == null,
                "learning it again put it on the belt twice");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "belt_5")
    public static void aCommandOffersOnlyWhatIsNewlyLearned(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "belt-command-test", true);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.mind().lexicon().learn(STONE, 1);
        run(player, "magical mind lexicon block minecraft:stone 5");
        helper.assertTrue(state.mind().belt().indexOf(STONE) < 0, "a lie already known was offered again");
        run(player, "magical mind preset 1 wall");
        helper.assertTrue(state.mind().belt().get(0) != null, "a preset's new lies were not offered to the belt");
        helper.succeed();
    }

    private static void run(ServerPlayer player, String command) {
        player.server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
    }
}
