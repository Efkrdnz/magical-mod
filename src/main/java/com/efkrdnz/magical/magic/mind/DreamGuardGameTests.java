package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamGuardGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private static BlockPos plotCell(GameTestHelper helper, Offset offset) {
        DreamService.testLevel = helper.getLevel();
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), UUID.randomUUID());
        return DreamService.at(scape.plot(), offset);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_1")
    public static void noItemAndNoExperienceEverAppearInADream(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(0, 0, 0));
        ServerLevel level = helper.getLevel();
        helper.assertFalse(level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.DIAMOND))),
                "a diamond appeared in a dream");
        helper.assertFalse(level.addFreshEntity(new ExperienceOrb(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 10)),
                "experience appeared in a dream");
        BlockPos outside = helper.absolutePos(new BlockPos(2, 2, 2));
        ItemEntity awake = new ItemEntity(level, outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, new ItemStack(Items.DIAMOND));
        helper.assertTrue(level.addFreshEntity(awake), "the waking world lost its items too");
        awake.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_2")
    public static void nothingInADreamCanBeBrokenByHand(GameTestHelper helper) {
        BlockPos wall = plotCell(helper, new Offset(1, 0, 0));
        ServerLevel level = helper.getLevel();
        level.setBlock(wall, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-miner-test");
        player.teleportTo(wall.getX() - 0.5, wall.getY(), wall.getZ() + 0.5);
        helper.assertFalse(player.gameMode.destroyBlock(wall), "a dream block was broken");
        helper.assertTrue(level.getBlockState(wall).is(Blocks.DIAMOND_BLOCK), "the dream block is gone");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_3")
    public static void anExplosionInADreamTakesNoBlocks(GameTestHelper helper) {
        BlockPos wall = plotCell(helper, new Offset(1, 0, 0));
        ServerLevel level = helper.getLevel();
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.explode(null, wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() - 0.5, 3.0F, Level.ExplosionInteraction.TNT);
        helper.assertTrue(level.getBlockState(wall).is(Blocks.STONE), "an explosion broke a dream");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_4")
    public static void aFlawThatDiesIsNoLongerTheFlaw(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        Dreamscape scape = DreamService.dreamscape(level, owner);
        BlockPos at = DreamService.at(scape.plot(), new Offset(0, 0, 2));
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        zombie.addTag(DreamService.DREAM_TAG);
        level.addFreshEntity(zombie);
        scape.markFigment(zombie.getUUID());
        zombie.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(scape.flaw() == null, "a dead Flaw is still the Flaw");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_5")
    public static void aDreamedMobNeverTurnsOnItsDreamer(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-owner-test");
        DreamService.enter(owner, owner.getUUID(), true);
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.moveTo(owner.getX() + 2, owner.getY(), owner.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(zombie);
        zombie.setTarget(owner);
        helper.assertTrue(zombie.getTarget() == null, "a mob in your own dream hunts you");
        DreamService.wake(owner);
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_6")
    public static void whatADreamerThrowsComesBackToTheirHand(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(0, 0, 0));
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-toss-test");
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.getInventory().clearContent();
        player.drop(new ItemStack(Items.DIAMOND, 3), false, true);
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3, "a thrown diamond was lost to the dream");
        helper.succeed();
    }
}
