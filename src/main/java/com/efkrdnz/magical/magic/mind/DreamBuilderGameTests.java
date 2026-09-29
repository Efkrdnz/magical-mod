package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.DreamEditPayload;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamBuilderGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    /** A wielder in their own dream, who has studied stone, a chest and cows. */
    private static ServerPlayer builder(GameTestHelper helper, String name) {
        DreamService.testLevel = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), name);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        state.mind().lexicon().learn("block:minecraft:stone", 5);
        state.mind().lexicon().learn("block:minecraft:chest", 5);
        state.mind().lexicon().learn("creature:minecraft:cow", 5);
        DreamService.enter(player, player.getUUID(), true);
        return player;
    }

    private static BlockPos cell(ServerPlayer player, int x, int y, int z) {
        Dreamscape scape = DreamService.dreamscape((ServerLevel) player.level(), player.getUUID());
        return DreamService.at(scape.plot(), new Offset(x, y, z));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_1")
    public static void whatYouStudiedIsMadeRealAndNothingElse(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-build-test");
        ServerLevel level = helper.getLevel();
        BlockPos a = cell(player, 2, 0, 0);
        BlockPos b = cell(player, 2, 1, 0);
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(a, b), "block:minecraft:stone", -1)),
                "known stone was refused");
        helper.assertTrue(level.getBlockState(a).is(Blocks.STONE) && level.getBlockState(b).is(Blocks.STONE), "the stone is not real");
        BlockPos c = cell(player, 3, 0, 0);
        helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(c), "block:minecraft:diamond_block", -1)),
                "an unstudied block was made real");
        helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(c), "block:minecraft:chest", -1)),
                "a block with a block entity was dreamed");
        helper.assertTrue(level.getBlockState(c).isAir(), "something stands where nothing should");
        DreamService.wake(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_2")
    public static void nothingIsBuiltOutOfReachOrOutsideThePlot(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-reach-test");
        ServerLevel level = helper.getLevel();
        BlockPos far = cell(player, 12, 0, 0);
        BlockPos outside = cell(player, DreamRules.PLOT_HALF + 2, 0, 0);
        helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(far, outside), "block:minecraft:stone", -1)),
                "a block was built out of reach");
        helper.assertTrue(level.getBlockState(far).isAir() && level.getBlockState(outside).isAir(), "a block stands out of reach");
        DreamService.wake(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_3")
    public static void theFlawIsMarkedAndUnmakingItUnmarksIt(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-flaw-mark-test");
        ServerLevel level = helper.getLevel();
        BlockPos wall = cell(player, 2, 0, 0);
        DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(wall), "block:minecraft:stone", -1));
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.FLAW, List.of(wall), "", -1)), "the Flaw was not marked");
        Dreamscape scape = DreamService.dreamscape(level, player.getUUID());
        helper.assertTrue(new Offset(2, 0, 0).equals(scape.flaw().block()), "the wrong thing is the Flaw");
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.ERASE, List.of(wall), "", -1)), "the Flaw could not be unmade");
        helper.assertTrue(level.getBlockState(wall).isAir(), "the unmade block stands");
        helper.assertTrue(scape.flaw() == null, "an unmade block is still the Flaw");
        DreamService.wake(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "dream_build_4")
    public static void aDreamedCowIsARealCowThatStaysAndCanBeTheFlaw(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-cow-test");
        ServerLevel level = helper.getLevel();
        BlockPos at = cell(player, 0, 0, 2);
        // A plot a million blocks out has no player tick to keep its chunks tracked, and a creature added
        // to an untracked chunk is invisible to an area query. Hold the chunk and give it a few ticks.
        ChunkPos chunk = new ChunkPos(at);
        level.setChunkForced(chunk.x, chunk.z, true);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(at), "creature:minecraft:cow", -1)),
                    "a studied cow was refused");
            List<Mob> cows = level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(1.0), mob -> mob.getTags().contains(DreamService.DREAM_TAG));
            helper.assertTrue(cows.size() == 1 && cows.get(0).isPersistenceRequired(), "the cow is not a lasting dreamed cow");
            Entity cow = cows.get(0);
            helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.FLAW, List.of(), "", cow.getId())), "the cow could not be the Flaw");
            helper.assertTrue(cow.getUUID().equals(DreamService.dreamscape(level, player.getUUID()).flaw().figment()), "the cow is not the Flaw");
            helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.ERASE, List.of(), "", cow.getId())), "the cow could not be unmade");
            helper.assertTrue(cow.isRemoved() && DreamService.dreamscape(level, player.getUUID()).flaw() == null, "the unmade cow lingers");
            DreamService.wake(player);
            level.setChunkForced(chunk.x, chunk.z, false);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_5")
    public static void nobodyBuildsADreamTheyAreNotDreaming(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-awake-test");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        state.mind().lexicon().learn("block:minecraft:stone", 5);
        BlockPos near = helper.absolutePos(new BlockPos(2, 2, 3));
        helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(near), "block:minecraft:stone", -1)),
                "a waking player built with a dream");
        helper.succeed();
    }
}
