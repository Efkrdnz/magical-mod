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

    /** Gives a two-block edit back with the second block's chunk "unloaded", by whichever route is asked. */
    private static void unloadedPositionStaysPending(GameTestHelper helper, boolean builtOverAware) {
        ServerLevel level = helper.getLevel();
        BlockPos loaded = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos away = helper.absolutePos(new BlockPos(3, 2, 2));
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        helper.assertTrue(ConjuredTerrainService.replace(level, edit, loaded, Blocks.STONE.defaultBlockState()), "a block conjured");
        helper.assertTrue(ConjuredTerrainService.replace(level, edit, away, Blocks.STONE.defaultBlockState()), "a second block conjured");
        java.util.function.Predicate<BlockPos> chunkHere = pos -> !pos.equals(away);
        if (builtOverAware) {
            ConjuredTerrainService.restoreUnlessBuiltOver(level, edit, chunkHere, state -> state.is(Blocks.STONE));
        } else {
            ConjuredTerrainService.restore(level, edit, chunkHere);
        }
        helper.assertTrue(level.getBlockState(loaded).isAir(), "the loaded block came back");
        helper.assertTrue(level.getBlockState(away).is(Blocks.STONE), "the block in the missing chunk cannot have been touched");
        helper.assertTrue(edit.size() == 1 && edit.positions().contains(away), "the unloaded position was forgotten instead of kept pending");
        helper.assertTrue(ConjuredTerrainService.lookup(level, edit.id()) != null, "the edit was closed with a block still conjured");
        helper.assertTrue(ConjuredTerrainService.isOrphan(level, edit.id()), "the edit was not left for the orphan sweep");
        ConjuredTerrainService.restoreLoadedOrphans(level);
        helper.assertTrue(level.getBlockState(away).isAir(), "the sweep did not give the block back once its chunk was here");
        helper.assertTrue(ConjuredTerrainService.lookup(level, edit.id()) == null, "the edit stayed open after everything came back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20, batch = "conjured_2")
    public static void aRestoreIntoAnUnloadedChunkLeavesThePositionPending(GameTestHelper helper) {
        unloadedPositionStaysPending(helper, false);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20, batch = "conjured_3")
    public static void aGiveBackIntoAnUnloadedChunkLeavesThePositionPending(GameTestHelper helper) {
        unloadedPositionStaysPending(helper, true);
    }
}
