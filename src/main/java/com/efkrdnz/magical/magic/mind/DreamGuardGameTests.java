package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamGuardGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    /** A mob born in a plot by any route but Daydream is the dream's all the same: it wears the tag. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_13")
    public static void aMobBornInADreamIsDreamed(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(0, 0, 0));
        ServerLevel level = helper.getLevel();
        Zombie born = new Zombie(EntityType.ZOMBIE, level);
        born.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.assertTrue(level.addFreshEntity(born), "the mob was refused");
        helper.assertTrue(born.getTags().contains(DreamService.DREAM_TAG), "a mob born in a dream is not the dream's");
        born.discard();
        BlockPos outside = helper.absolutePos(new BlockPos(2, 2, 2));
        Zombie awake = new Zombie(EntityType.ZOMBIE, level);
        awake.moveTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5);
        level.addFreshEntity(awake);
        helper.assertFalse(awake.getTags().contains(DreamService.DREAM_TAG), "a mob born awake was taken for the dream's");
        awake.discard();
        helper.succeed();
    }

    /**
     * A Flaw that grows into something else is still the Flaw: a tadpole marked as one becomes a frog
     * marked as one. The plot's chunk is held first, or the frog is added to a chunk no lookup can see.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "dream_guard_14")
    public static void aFlawThatTurnsIntoSomethingElseIsStillTheFlaw(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        DreamService.testLevel = helper.getLevel();
        ServerLevel level = helper.getLevel();
        Dreamscape scape = DreamService.dreamscape(level, owner);
        BlockPos at = DreamService.at(scape.plot(), new Offset(0, 0, 0));
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(at);
        level.setChunkForced(chunk.x, chunk.z, true);
        helper.runAfterDelay(5, () -> {
            try {
                Tadpole tadpole = new Tadpole(EntityType.TADPOLE, level);
                tadpole.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                helper.assertTrue(level.addFreshEntity(tadpole), "the tadpole was refused");
                helper.assertTrue(tadpole.getTags().contains(DreamService.DREAM_TAG), "the tadpole is not dreamed");
                scape.markFigment(tadpole.getUUID());
                CompoundTag grown = tadpole.saveWithoutId(new CompoundTag());
                grown.putInt("Age", 10 * 24000);
                tadpole.load(grown);
                helper.assertTrue(tadpole.isRemoved(), "the tadpole did not grow up");
                UUID flaw = scape.flaw() == null ? null : scape.flaw().figment();
                helper.assertTrue(flaw != null && !flaw.equals(tadpole.getUUID()), "the Flaw did not follow what it became");
                helper.assertTrue(level.getEntity(flaw) instanceof Frog, "the Flaw is not the frog: " + level.getEntity(flaw));
                Frog frog = (Frog) level.getEntity(flaw);
                helper.assertTrue(frog.getTags().contains(DreamService.DREAM_TAG), "the frog is not dreamed");
                frog.discard();
            } finally {
                level.setChunkForced(chunk.x, chunk.z, false);
            }
            helper.succeed();
        });
    }

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

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_7")
    public static void aKillInADreamEarnsNoClassExperience(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(0, 0, 2));
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-slayer-test");
        player.getData(MagicalAttachments.MAGIC_STATE).unlockClass(player, MagicalClasses.WARRIOR);
        int before = player.getData(MagicalAttachments.MAGIC_STATE).classXpPool(MagicalClasses.WARRIOR);
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        zombie.addTag(DreamService.DREAM_TAG);
        level.addFreshEntity(zombie);
        zombie.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
        helper.assertTrue(zombie.isDeadOrDying(), "the zombie survived");
        helper.assertTrue(player.getData(MagicalAttachments.MAGIC_STATE).classXpPool(MagicalClasses.WARRIOR) == before,
                "a kill in a dream earned class experience");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_8")
    public static void aKillOutsideADreamStillEarnsClassExperience(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "awake-slayer-test");
        player.getData(MagicalAttachments.MAGIC_STATE).unlockClass(player, MagicalClasses.WARRIOR);
        int before = player.getData(MagicalAttachments.MAGIC_STATE).classXpPool(MagicalClasses.WARRIOR);
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        level.addFreshEntity(zombie);
        zombie.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
        helper.assertTrue(player.getData(MagicalAttachments.MAGIC_STATE).classXpPool(MagicalClasses.WARRIOR) > before,
                "the gate also closed the waking world");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_9")
    public static void experienceGivenInADreamIsRefused(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(0, 0, 0));
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-xp-test");
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        int before = player.totalExperience;
        player.giveExperiencePoints(10);
        helper.assertTrue(player.totalExperience == before, "a dream gave experience points");
        player.giveExperienceLevels(2);
        helper.assertTrue(player.experienceLevel == 0, "a dream gave experience levels");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_10")
    public static void aPottedPlantCannotBePickedByHandInADream(GameTestHelper helper) {
        BlockPos pot = plotCell(helper, new Offset(1, 0, 0));
        ServerLevel level = helper.getLevel();
        level.setBlock(pot, Blocks.POTTED_POPPY.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-potter-test");
        player.teleportTo(pot.getX() + 0.5, pot.getY() + 1.0, pot.getZ() - 1.5);
        player.getInventory().clearContent();
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pot), Direction.UP, pot, false));
        helper.assertTrue(player.getInventory().isEmpty(), "a dream pot gave up its flower");
        helper.assertTrue(level.getBlockState(pot).is(Blocks.POTTED_POPPY), "the dream pot lost its flower");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_11")
    public static void aDoorStillAnswersAnEmptyHandInADream(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(1, 0, 0));
        ServerLevel level = helper.getLevel();
        level.setBlock(at, Blocks.LEVER.defaultBlockState(), Block.UPDATE_ALL);
        boolean before = level.getBlockState(at).getValue(net.minecraft.world.level.block.LeverBlock.POWERED);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-lever-test");
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() - 1.5);
        player.getInventory().clearContent();
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
        helper.assertTrue(level.getBlockState(at).getValue(net.minecraft.world.level.block.LeverBlock.POWERED) != before,
                "the whitelist shut a lever too");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_12")
    public static void whatAFullHandCannotTakeBackStaysInTheDream(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(0, 0, 0));
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-full-test");
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.getInventory().clearContent();
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().items.set(slot, new ItemStack(Items.DIRT, 64));
        }
        ItemEntity dropped = player.drop(new ItemStack(Items.DIAMOND, 3), false, true);
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "the full inventory took a diamond");
        helper.assertTrue(dropped != null && dropped.isAddedToLevel(), "the overflow was deleted instead of dropped");
        helper.assertTrue(dropped.getItem().is(Items.DIAMOND) && dropped.getItem().getCount() == 3,
                "the overflow lost part of its stack, kept " + dropped.getItem());
        helper.assertTrue(DreamService.isDream(dropped), "the overflow is not in the dream");
        dropped.discard();
        helper.succeed();
    }
}
