package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class UnveilGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private UnveilGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_unveil_1")
    public static void anUnveilIsPaidForAndAnEmptyOneIsNot(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 0), "mind-unveil-test");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        // Face the floor two blocks ahead, so the crosshair meets a block inside the template.
        player.setYRot(0.0F);
        player.setXRot(45.0F);

        state.mind().setActiveSlot(0);
        state.mind().active().clear();
        int before = state.mana();
        helper.assertFalse(MindService.unveil(player, state), "an empty reverie went up");
        helper.assertTrue(state.mana() == before, "an empty reverie was billed");
        helper.assertFalse(state.isSkillOnCooldown(MagicContent.UNVEIL.id()), "an empty reverie started the clock");

        Reverie cat = MindPresets.named("cat");
        MindPresets.impressions(cat).forEach(key -> state.mind().lexicon().learn(key, 5));
        state.mind().active().copyFrom(cat);
        helper.assertTrue(MindService.unveil(player, state), "the cat was refused");
        helper.assertTrue(state.mana() < before, "the cat went up for free");
        helper.assertTrue(state.isSkillOnCooldown(MagicContent.UNVEIL.id()), "Unveil started no cooldown");
        helper.assertTrue(MindService.scenesOf(player.getUUID()).size() == 1, "no scene is standing");
        MindService.endAll(player.getUUID());
        helper.succeed();
    }
}
