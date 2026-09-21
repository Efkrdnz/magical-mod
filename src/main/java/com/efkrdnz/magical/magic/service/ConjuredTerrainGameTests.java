package com.efkrdnz.magical.magic.service;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The conjured ledger against a real level: an edit the level loads with is given back once its
 * chunk is here, not lost because no chunk was there to give it back into.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class ConjuredTerrainGameTests {

    private static final String TEMPLATE = "unwaking_empty";

    private ConjuredTerrainGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 20, batch = "conjured_1")
    public static void anOrphanComesBackOnceItsChunkIsHere(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        helper.assertTrue(ConjuredTerrainService.replace(level, edit, at, Blocks.STONE.defaultBlockState()), "a block conjured");
        ConjuredTerrainService.restoreOrphans(level);
        helper.assertTrue(level.getBlockState(at).is(Blocks.STONE), "adopting the orphans touches no block: a level has no chunks when it loads");
        ConjuredTerrainService.restoreLoadedOrphans(level);
        helper.assertTrue(level.getBlockState(at).isAir(), "the sweep finds its chunk here and gives the block back");
        helper.assertTrue(ConjuredTerrainService.lookup(level, edit.id()) == null, "and closes the edit");
        helper.succeed();
    }
}
