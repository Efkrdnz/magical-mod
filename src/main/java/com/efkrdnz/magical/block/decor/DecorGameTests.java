package com.efkrdnz.magical.block.decor;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.registry.MagicalDecor;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The registered blocks do what their kind says: {@code DecorKindTest} holds the numbers and the
 * files, and only a live level can say the properties, tool tags, loot tables and fire table were
 * actually wired into the block the game uses.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DecorGameTests {
    private DecorGameTests() {}

    @GameTest(template = "unwaking_empty", timeoutTicks = 40, batch = "decor")
    public static void everyDecorBlockBreaksLightsBurnsAndDropsAsItsKindSays(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos absolute = helper.absolutePos(pos);
        for (DecorKind kind : DecorKind.values()) {
            for (DyeColor colour : DecorKind.COLOURS) {
                Block block = MagicalDecor.block(kind, colour).get();
                helper.setBlock(pos, block);
                BlockState state = helper.getBlockState(pos);
                String id = kind.path(colour);
                helper.assertTrue(state.getDestroySpeed(helper.getLevel(), absolute) == kind.hardness(), id + " hardness");
                helper.assertTrue(state.getExplosionResistance(helper.getLevel(), absolute, null) == kind.resistance(), id + " blast resistance");
                helper.assertTrue(state.getLightEmission() == kind.light(), id + " light");
                helper.assertTrue(state.requiresCorrectToolForDrops() == kind.tool().requiresTool(), id + " needs a tool");
                helper.assertTrue(state.isFlammable(helper.getLevel(), absolute, Direction.UP) == kind.flammable(), id + " burns");
                List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), absolute, null);
                helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), id + " drops itself: " + drops);
                helper.assertTrue(state.canOcclude() != kind.translucent(), id + " occludes unless it is crystal");
                checkTool(helper, kind, state, id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "unwaking_empty", timeoutTicks = 40, batch = "decor")
    public static void everyMasonryBlockBreaksLikeStoneAndDropsItself(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos absolute = helper.absolutePos(pos);
        ItemStack wooden = new ItemStack(Items.WOODEN_PICKAXE);
        for (Masonry masonry : Masonry.all()) {
            Block block = MagicalDecor.block(masonry).get();
            helper.setBlock(pos, block);
            BlockState state = helper.getBlockState(pos);
            String id = masonry.path();
            helper.assertTrue(state.getDestroySpeed(helper.getLevel(), absolute) == masonry.hardness(), id + " hardness");
            helper.assertTrue(state.getExplosionResistance(helper.getLevel(), absolute, null) == masonry.resistance(), id + " blast resistance");
            helper.assertTrue(state.getSoundType(helper.getLevel(), absolute, null) == masonry.sound(), id + " sound");
            helper.assertTrue(state.requiresCorrectToolForDrops() && wooden.isCorrectToolForDrops(state), id + " is a pickaxe block");
            List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), absolute, null);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), id + " drops itself: " + drops);
        }
        helper.succeed();
    }

    private static void checkTool(GameTestHelper helper, DecorKind kind, BlockState state, String id) {
        ItemStack wooden = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
        switch (kind.tool()) {
            case PICKAXE -> helper.assertTrue(wooden.isCorrectToolForDrops(state), id + " is harvested by any pickaxe");
            case STONE_PICKAXE -> helper.assertTrue(!wooden.isCorrectToolForDrops(state) && stone.isCorrectToolForDrops(state),
                    id + " needs a stone pickaxe, like iron");
            case AXE -> helper.assertTrue(new ItemStack(Items.IRON_AXE).getDestroySpeed(state) > 1F, id + " is quick to an axe");
            case NONE -> {
                if (kind == DecorKind.WOVEN_CLOTH) {
                    helper.assertTrue(new ItemStack(Items.SHEARS).getDestroySpeed(state) > 1F, id + " is quick to shears");
                }
            }
        }
    }
}
