package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamPlotGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_plot_1")
    public static void aNewDreamscapeHasAFloorAndIsItsOwnersAlone(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        Dreamscape scape = DreamService.dreamscape(level, owner);
        helper.assertTrue(DreamService.dreamscape(level, owner) == scape, "asking twice claimed a second plot");
        helper.assertTrue(DreamService.dreamscape(level, UUID.randomUUID()).plot() != scape.plot(), "two owners share a plot");
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        helper.assertTrue(level.getBlockState(arrival.below()).is(Blocks.SMOOTH_STONE), "nothing to stand on at the arrival");
        helper.assertTrue(level.getBlockState(arrival.below().offset(DreamRules.PLATFORM_HALF, 0, DreamRules.PLATFORM_HALF)).is(Blocks.SMOOTH_STONE),
                "the floor is not five by five");
        helper.assertTrue(DreamService.isDream(level, arrival), "the plot is not a dream");
        helper.assertFalse(DreamService.isDream(level, helper.absolutePos(new BlockPos(1, 1, 1))), "the test itself is a dream");
        helper.succeed();
    }
}
