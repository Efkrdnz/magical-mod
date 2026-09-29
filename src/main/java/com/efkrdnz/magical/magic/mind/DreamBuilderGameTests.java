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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    /**
     * A plot a million blocks out has no player tick to keep its chunks tracked, and a creature added to
     * an untracked chunk is invisible to every query. A test that needs creatures holds the chunks and
     * gives them a few ticks; {@link #release} lets go of them and wakes the dreamer.
     */
    private static void hold(ServerLevel level, BlockPos... at) {
        for (BlockPos pos : at) {
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        }
    }

    private static void release(ServerLevel level, ServerPlayer player, BlockPos... at) {
        DreamService.wake(player);
        for (BlockPos pos : at) {
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
        }
    }

    private static List<Mob> dreamed(ServerLevel level) {
        List<Mob> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.getTags().contains(DreamService.DREAM_TAG)) {
                found.add(mob);
            }
        }
        return found;
    }

    private static DreamEditPayload place(String impression, BlockPos... cells) {
        return new DreamEditPayload(DreamEditPayload.PLACE, List.of(cells), impression, -1);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_1")
    public static void whatYouStudiedIsMadeRealAndNothingElse(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-build-test");
        ServerLevel level = helper.getLevel();
        try {
            BlockPos a = cell(player, 2, 0, 0);
            BlockPos b = cell(player, 2, 1, 0);
            helper.assertTrue(DreamBuilder.apply(player, place("block:minecraft:stone", a, b)), "known stone was refused");
            helper.assertTrue(level.getBlockState(a).is(Blocks.STONE) && level.getBlockState(b).is(Blocks.STONE), "the stone is not real");
            BlockPos c = cell(player, 3, 0, 0);
            helper.assertFalse(DreamBuilder.apply(player, place("block:minecraft:diamond_block", c)), "an unstudied block was made real");
            helper.assertFalse(DreamBuilder.apply(player, place("block:minecraft:chest", c)), "a block with a block entity was dreamed");
            helper.assertTrue(level.getBlockState(c).isAir(), "something stands where nothing should");
        } finally {
            DreamService.wake(player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_2")
    public static void nothingIsBuiltOutOfReachOrOutsideThePlot(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-reach-test");
        ServerLevel level = helper.getLevel();
        try {
            BlockPos far = cell(player, 12, 0, 0);
            BlockPos outside = cell(player, DreamRules.PLOT_HALF + 2, 0, 0);
            helper.assertFalse(DreamBuilder.apply(player, place("block:minecraft:stone", far, outside)), "a block was built out of reach");
            helper.assertTrue(level.getBlockState(far).isAir() && level.getBlockState(outside).isAir(), "a block stands out of reach");
        } finally {
            DreamService.wake(player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_3")
    public static void theFlawIsMarkedAndUnmakingItUnmarksIt(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-flaw-mark-test");
        ServerLevel level = helper.getLevel();
        try {
            BlockPos wall = cell(player, 2, 0, 0);
            DreamBuilder.apply(player, place("block:minecraft:stone", wall));
            helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.FLAW, List.of(wall), "", -1)), "the Flaw was not marked");
            Dreamscape scape = DreamService.dreamscape(level, player.getUUID());
            helper.assertTrue(new Offset(2, 0, 0).equals(scape.flaw().block()), "the wrong thing is the Flaw");
            helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.ERASE, List.of(wall), "", -1)), "the Flaw could not be unmade");
            helper.assertTrue(level.getBlockState(wall).isAir(), "the unmade block stands");
            helper.assertTrue(scape.flaw() == null, "an unmade block is still the Flaw");
        } finally {
            DreamService.wake(player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "dream_build_4")
    public static void aDreamedCowIsARealCowThatStaysAndCanBeTheFlaw(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-cow-test");
        ServerLevel level = helper.getLevel();
        BlockPos at = cell(player, 0, 0, 2);
        hold(level, at);
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(DreamBuilder.apply(player, place("creature:minecraft:cow", at)), "a studied cow was refused");
                List<Mob> cows = level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(1.0), mob -> mob.getTags().contains(DreamService.DREAM_TAG));
                helper.assertTrue(cows.size() == 1 && cows.get(0).isPersistenceRequired(), "the cow is not a lasting dreamed cow");
                Entity cow = cows.get(0);
                helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.FLAW, List.of(), "", cow.getId())), "the cow could not be the Flaw");
                helper.assertTrue(cow.getUUID().equals(DreamService.dreamscape(level, player.getUUID()).flaw().figment()), "the cow is not the Flaw");
                helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.ERASE, List.of(), "", cow.getId())), "the cow could not be unmade");
                helper.assertTrue(cow.isRemoved() && DreamService.dreamscape(level, player.getUUID()).flaw() == null, "the unmade cow lingers");
            } finally {
                release(level, player, at);
            }
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
        helper.assertFalse(DreamBuilder.apply(player, place("block:minecraft:stone", near)), "a waking player built with a dream");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "dream_build_6")
    public static void aCreatureThatWandersOutIsBroughtBackOrUnmade(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-sweep-test");
        ServerLevel level = helper.getLevel();
        BlockPos at = cell(player, 0, 0, 2);
        BlockPos out = cell(player, DreamRules.PLOT_HALF + 12, 0, 2);
        BlockPos gone = new BlockPos(DreamRules.PLOT_BASE - 200, at.getY(), at.getZ());
        hold(level, at, out, gone);
        helper.runAfterDelay(5, () -> {
            try {
                DreamBuilder.apply(player, place("creature:minecraft:cow", at));
                DreamBuilder.apply(player, place("creature:minecraft:cow", at));
                List<Mob> cows = dreamed(level);
                helper.assertTrue(cows.size() == 2, "expected two dreamed cows, found " + cows.size());
                Mob strayed = cows.get(0);
                Mob lost = cows.get(1);
                strayed.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 0.0F, 0.0F);
                lost.moveTo(gone.getX() + 0.5, gone.getY(), gone.getZ() + 0.5, 0.0F, 0.0F);
                DreamService.sweepFigments(level);
                helper.assertTrue(!strayed.isRemoved()
                                && DreamRules.inside(DreamService.dreamscape(level, player.getUUID()).plot(), strayed.getX(), strayed.getY(), strayed.getZ()),
                        "a creature outside its plot was left there");
                helper.assertTrue(lost.isRemoved(), "a creature off the grid was left there");
            } finally {
                release(level, player, at, out, gone);
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "dream_build_7")
    public static void theCapHoldsForACreatureOutsideTheBoxButInsideThePlotCell(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-cap-test");
        ServerLevel level = helper.getLevel();
        BlockPos at = cell(player, 0, 0, 2);
        BlockPos out = cell(player, DreamRules.PLOT_HALF + 12, 0, 2);
        hold(level, at, out);
        helper.runAfterDelay(5, () -> {
            try {
                for (int i = 0; i < DreamRules.MAX_FIGMENTS; i++) {
                    helper.assertTrue(DreamBuilder.apply(player, place("creature:minecraft:cow", at)), "cow " + i + " was refused under the cap");
                }
                dreamed(level).get(0).moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 0.0F, 0.0F);
                helper.assertFalse(DreamBuilder.apply(player, place("creature:minecraft:cow", at)), "a creature outside the box escaped the cap");
                helper.assertTrue(dreamed(level).size() == DreamRules.MAX_FIGMENTS, "the cap was passed");
            } finally {
                for (Mob mob : dreamed(level)) {
                    mob.discard();
                }
                release(level, player, at, out);
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_8")
    public static void nothingIsBuiltOverTheFlaw(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-over-flaw-test");
        ServerLevel level = helper.getLevel();
        try {
            BlockPos wall = cell(player, 2, 0, 0);
            DreamBuilder.apply(player, place("block:minecraft:stone", wall));
            DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.FLAW, List.of(wall), "", -1));
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            helper.assertFalse(DreamBuilder.apply(player, place("block:minecraft:stone", wall)), "the cell of the Flaw was built over");
            helper.assertTrue(level.getBlockState(wall).isAir(), "something stands on the cell of the Flaw");
            helper.assertTrue(new Offset(2, 0, 0).equals(DreamService.dreamscape(level, player.getUUID()).flaw().block()), "the Flaw moved");
        } finally {
            DreamService.wake(player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_9")
    public static void noCreatureIsDreamedIntoSolidBlocks(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-solid-test");
        try {
            BlockPos at = cell(player, 0, 0, 2);
            helper.assertTrue(DreamBuilder.apply(player, place("block:minecraft:stone", at)), "the stone was refused");
            helper.assertFalse(DreamBuilder.apply(player, place("creature:minecraft:cow", at)), "a cow was dreamed into stone");
            helper.assertTrue(dreamed(helper.getLevel()).isEmpty(), "a cow stands in stone");
        } finally {
            DreamService.wake(player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_10")
    public static void aGuestInSomeoneElseDreamBuildsNothingAndMarksNothing(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerLevel level = helper.getLevel();
        ServerPlayer guest = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-guest-test");
        PlayerMagicState state = guest.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        state.mind().lexicon().learn("block:minecraft:stone", 5);
        UUID owner = UUID.randomUUID();
        DreamService.enter(guest, owner, false);
        try {
            Dreamscape scape = DreamService.dreamscape(level, owner);
            BlockPos wall = DreamService.at(scape.plot(), new Offset(2, 0, 0));
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            BlockPos free = DreamService.at(scape.plot(), new Offset(3, 0, 0));
            helper.assertFalse(DreamBuilder.apply(guest, place("block:minecraft:stone", free)), "a guest built in a dream that is not theirs");
            helper.assertTrue(level.getBlockState(free).isAir(), "a block of a guest stands");
            helper.assertFalse(DreamBuilder.apply(guest, new DreamEditPayload(DreamEditPayload.FLAW, List.of(wall), "", -1)), "a guest marked the Flaw");
            helper.assertTrue(scape.flaw() == null, "the mark of a guest is the Flaw");
        } finally {
            DreamService.wake(guest);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "dream_build_11")
    public static void onlyADreamedCreatureCanBeUnmade(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-untagged-test");
        ServerLevel level = helper.getLevel();
        BlockPos at = cell(player, 0, 0, 2);
        hold(level, at);
        helper.runAfterDelay(5, () -> {
            Entity stray = null;
            try {
                stray = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
                stray.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
                level.addFreshEntity(stray);
                // Anything born in a plot is tagged as it arrives; take the tag off to stand for a creature that is not the dream's.
                stray.removeTag(DreamService.DREAM_TAG);
                helper.assertTrue(level.getEntity(stray.getId()) != null, "the stray cow was never tracked, so the refusal proves nothing");
                helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.ERASE, List.of(), "", stray.getId())),
                        "an untagged creature was unmade");
                helper.assertFalse(stray.isRemoved(), "an untagged creature is gone");
            } finally {
                if (stray != null) {
                    stray.discard();
                }
                release(level, player, at);
            }
            helper.succeed();
        });
    }
}
