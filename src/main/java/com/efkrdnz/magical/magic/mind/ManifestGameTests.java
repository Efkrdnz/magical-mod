package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class ManifestGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private ManifestGameTests() {}

    /** A column of two stones (weight 1) standing on the floor, and one husk certain of it (consensus 1). */
    private static LiveScene believedColumn(GameTestHelper helper, UUID owner, LivingEntity[] believer) {
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 3)));
        LiveScene scene = MindGameTests.unveil(helper, owner, MindGameTests.column(), anchor);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 1))));
        scene.belief().set(husk.getId(), 0, 1.0F);
        believer[0] = husk;
        return scene;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_1")
    public static void aClusterEnoughMindsAgreeOnIsRealForEveryone(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedColumn(helper, owner, new LivingEntity[1]);
        BlockPos base = scene.elements().get(0).cells().get(0);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(scene.manifested(0), "consensus " + scene.consensus(0) + " against weight " + scene.weight(0) + " made nothing real");
            helper.assertTrue(helper.getLevel().getBlockState(base).is(Blocks.STONE), "the column's foot is not stone");
            helper.assertTrue(helper.getLevel().getBlockState(base.above()).is(Blocks.STONE), "the column's head is not stone");
            MindService.endAll(owner);
            helper.assertTrue(helper.getLevel().getBlockState(base).isAir() && helper.getLevel().getBlockState(base.above()).isAir(),
                    "ending the scene did not give the ground back");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_2")
    public static void aRealClusterFallsBackWhenTheAgreementGoes(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LivingEntity[] believer = new LivingEntity[1];
        LiveScene scene = believedColumn(helper, owner, believer);
        BlockPos base = scene.elements().get(0).cells().get(0);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(scene.manifested(0), "the column never became real");
            // 0.6 is still half the weight: it holds.
            scene.belief().set(believer[0].getId(), 0, 0.6F);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(scene.manifested(0), "a real column fell at " + scene.consensus(0) + ", above half its weight");
            scene.belief().set(believer[0].getId(), 0, 0.3F);
        });
        helper.runAtTickTime(28, () -> {
            helper.assertFalse(scene.manifested(0), "nobody is convinced and the column is still real");
            helper.assertTrue(helper.getLevel().getBlockState(base).isAir(), "the stone was not given back");
            helper.assertTrue(MindService.scene(scene.id()) != null, "reverting ended the scene");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_3")
    public static void aRealBlockBrokenDropsNothing(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedColumn(helper, owner, new LivingEntity[1]);
        BlockPos head = scene.elements().get(0).cells().get(1);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(scene.manifested(0), "the column never became real");
            helper.getLevel().destroyBlock(head, true);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(head).inflate(3.0)).isEmpty(),
                    "breaking an agreed-on stone paid out a real one");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_4")
    public static void aSandBlockIsNeverMadeRealAndNothingFalls(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 3))).above(2);
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("block:minecraft:sand");
        reverie.addBlock(new Offset(0, 0, 0), "minecraft:sand", lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie, anchor, 0, lexicon);
        helper.assertTrue(scene != null, "the scene was refused");
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 1))));
        scene.belief().set(husk.getId(), 0, 1.0F);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(scene.consensus(0) >= scene.weight(0), "the sand was never agreed on, so refusing it proves nothing");
            helper.assertFalse(scene.manifested(0), "an imagined sand block was made real");
            helper.assertTrue(helper.getLevel().getBlockState(anchor).isAir(), "sand stands where the lie was");
            AABB around = new AABB(anchor).inflate(4.0);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, around).isEmpty(), "a sand block fell");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "sand was dropped");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    private static final String CHICKEN = "minecraft:chicken";

    /** A chicken figment on the floor at (2,2,3) and a husk at (2,2,1) certain of it. */
    private static LiveScene believedChicken(GameTestHelper helper, UUID owner, LivingEntity[] believer) {
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("creature:" + CHICKEN);
        reverie.addFigment(new Offset(0, 0, 0), CHICKEN, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie,
                BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 3))), 0, lexicon);
        helper.assertTrue(scene != null, "the chicken was refused");
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 1))));
        scene.belief().set(husk.getId(), 0, 1.0F);
        believer[0] = husk;
        return scene;
    }

    private static FigmentEntity creature(GameTestHelper helper, LiveScene scene) {
        return (FigmentEntity) helper.getLevel().getEntity(scene.figmentEntity(0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_5")
    public static void aRealFigmentTakesRealBlowsAndWeakensAsDoubtGrows(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LivingEntity[] believer = new LivingEntity[1];
        LiveScene scene = believedChicken(helper, owner, believer);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(1, 2, 3), "mind-real-test");
        helper.runAtTickTime(12, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "the chicken never became real");
            helper.assertFalse(FigmentEntity.isFigment(chicken), "a real chicken is still no body to the mod");
            helper.assertTrue(chicken.canBeSeenAsEnemy(), "a real chicken is still no enemy to vanilla");
            player.attack(chicken);
            helper.assertTrue(chicken.getHealth() < chicken.getMaxHealth(), "a blow on a real chicken landed nothing");
            helper.assertFalse(scene.belief().shattered(player.getId(), 0), "striking a real thing was taken as evidence it is not there");
            // Consensus 0.6 of a weight of 1 holds it, at six tenths of a chicken.
            scene.belief().set(believer[0].getId(), 0, 0.6F);
        });
        helper.runAtTickTime(20, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "a chicken held at 0.6 of its weight fell");
            // The husk still sees the chicken, so its belief climbs a little past 0.6 before the step
            // reads it: hold to the agreement the step actually read, and prove it is well short of whole.
            float share = Consensus.healthFraction(scene.consensus(0), scene.weight(0));
            helper.assertTrue(share >= 0.6F && share < 0.7F, "the agreement " + share + " is not about six tenths");
            helper.assertTrue(Math.abs(chicken.getMaxHealth() - 4.0F * share) < 0.01F,
                    "max health " + chicken.getMaxHealth() + " is not " + share + " of 4");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_6")
    public static void aSlainFigmentIsGoneForGood(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedChicken(helper, owner, new LivingEntity[1]);
        helper.runAtTickTime(12, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "the chicken never became real");
            chicken.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 100.0F);
            helper.assertTrue(scene.slain(0), "a killed chicken is not slain");
            helper.assertFalse(scene.manifested(0), "a slain chicken is still counted real");
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(scene.slain(0) && !scene.manifested(0), "the chicken came back");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    /** One imagined block of {@code blockId} on the floor under {@code cell}, and a husk at (2,2,1) certain of it. */
    private static LiveScene believedBlock(GameTestHelper helper, UUID owner, String blockId, BlockPos cell) {
        BlockPos anchor = BlockPos.containing(onFloor(helper, cell));
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("block:" + blockId);
        reverie.addBlock(new Offset(0, 0, 0), blockId, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie, anchor, 0, lexicon);
        helper.assertTrue(scene != null, "the " + blockId + " was refused");
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 1))));
        scene.belief().set(husk.getId(), 0, 1.0F);
        return scene;
    }

    /** Item entities within four blocks of {@code around}: every one, or only those of {@code item}. */
    private static int items(GameTestHelper helper, BlockPos around, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(4.0),
                entity -> item == null || entity.getItem().is(item)).size();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_7")
    public static void aRealFenceThatReconnectedStillDropsNothingAndStillGoesBack(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedBlock(helper, owner, "minecraft:oak_fence", new BlockPos(2, 2, 3));
        BlockPos fence = scene.elements().get(0).cells().get(0);
        helper.runAtTickTime(12, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(scene.manifested(0), "the fence never became real");
            level.setBlock(fence.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(fence).getValue(FenceBlock.EAST), "the fence did not reach for the real stone beside it");
            level.destroyBlock(fence, true);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(items(helper, fence, null) == 0, "breaking a real imagined fence that had changed its state paid out an item");
            MindService.endAll(owner);
            helper.assertTrue(helper.getLevel().getBlockState(fence).isAir(), "the fence cell is not given back to air");
            helper.assertTrue(helper.getLevel().getBlockState(fence.east()).is(Blocks.STONE), "the real stone beside the fence was taken");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_8")
    public static void aPistonCannotPushARealImaginedBlock(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedBlock(helper, owner, "minecraft:stone", new BlockPos(2, 2, 3));
        BlockPos stone = scene.elements().get(0).cells().get(0);
        BlockPos piston = stone.west();
        helper.runAtTickTime(12, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(scene.manifested(0), "the stone never became real");
            level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
            level.setBlock(piston.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        });
        helper.runAtTickTime(20, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(level.getBlockState(stone).is(Blocks.STONE), "the real imagined stone was pushed out of its cell: "
                    + level.getBlockState(stone));
            helper.assertTrue(level.getBlockState(stone.east()).isAir(), "the stone was pushed on into the world");
            helper.assertFalse(level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED), "the piston extended");
            MindService.endAll(owner);
            helper.assertTrue(level.getBlockState(stone).isAir(), "the stone was not given back");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_9")
    public static void anExplosionLeavesARealImaginedBlockStandingAndDropsNothing(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedBlock(helper, owner, "minecraft:bricks", new BlockPos(2, 2, 3));
        BlockPos bricks = scene.elements().get(0).cells().get(0);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(scene.manifested(0), "the bricks never became real");
            Vec3 at = Vec3.atCenterOf(bricks.above());
            helper.getLevel().explode(null, at.x, at.y, at.z, 4.0F, Level.ExplosionInteraction.TNT);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(bricks).is(Blocks.BRICKS), "the explosion broke the real imagined bricks");
            // The template floor is real and may drop what it is made of; the bricks may drop nothing.
            helper.assertTrue(items(helper, bricks, Items.BRICKS) == 0, "the explosion paid out the imagined bricks");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_10")
    public static void aBlockAPlayerPlacesIntoABrokenCellIsTheirs(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedBlock(helper, owner, "minecraft:stone", new BlockPos(2, 2, 3));
        BlockPos cell = scene.elements().get(0).cells().get(0);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(0, 2, 1), "mind-place-test");
        helper.runAtTickTime(12, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(scene.manifested(0), "the stone never became real");
            level.destroyBlock(cell, true);
            helper.assertTrue(level.getBlockState(cell).isAir(), "the real stone did not break");
            // The player's own stone - the very block the lie was made of - set on the floor under the cell.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            BlockPos floor = cell.below();
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false));
            helper.assertTrue(level.getBlockState(cell).is(Blocks.STONE), "the player stone was not placed");
        });
        helper.runAtTickTime(14, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(items(helper, cell, null) == 0, "the broken imagined stone paid out an item");
            MindService.endAll(owner);
            helper.assertTrue(level.getBlockState(cell).is(Blocks.STONE), "ending the scene took the player own stone");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_11")
    public static void aSlainRealFigmentLeavesNoLootAndNoExperience(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedChicken(helper, owner, new LivingEntity[1]);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(1, 2, 3), "mind-slay-test");
        BlockPos at = BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 3)));
        helper.runAtTickTime(12, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "the chicken never became real");
            chicken.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 100.0F);
            helper.assertTrue(scene.slain(0), "the chicken was not slain");
        });
        helper.runAtTickTime(18, () -> {
            AABB around = new AABB(at).inflate(6.0);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "a slain figment dropped loot");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around).isEmpty(), "a slain figment dropped experience");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_12")
    public static void aRealImaginedTrapdoorDoesNotOpen(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedBlock(helper, owner, "minecraft:oak_trapdoor", new BlockPos(2, 2, 3));
        BlockPos trapdoor = scene.elements().get(0).cells().get(0);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(1, 2, 3), "mind-door-test");
        helper.runAtTickTime(12, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(scene.manifested(0), "the trapdoor never became real");
            player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(trapdoor), Direction.UP, trapdoor, false));
            helper.assertTrue(level.getBlockState(trapdoor).is(Blocks.OAK_TRAPDOOR), "the trapdoor is gone");
            helper.assertFalse(level.getBlockState(trapdoor).getValue(TrapDoorBlock.OPEN), "a real imagined trapdoor opened");
            MindService.endAll(owner);
            helper.assertTrue(level.getBlockState(trapdoor).isAir(), "the trapdoor was not given back");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_13")
    public static void aSaplingAndTntAreNeverMadeReal(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(1, 2, 3)));
        // Dirt under the sapling, so it would survive in its cell and only the refusal keeps it imagined.
        helper.getLevel().setBlock(anchor.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("block:minecraft:oak_sapling", "block:minecraft:tnt");
        reverie.addBlock(new Offset(0, 0, 0), "minecraft:oak_sapling", lexicon);
        reverie.addBlock(new Offset(2, 0, 0), "minecraft:tnt", lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie, anchor, 0, lexicon);
        helper.assertTrue(scene != null && scene.elements().size() == 2, "the scene is not two clusters");
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 1))));
        scene.belief().set(husk.getId(), 0, 1.0F);
        scene.belief().set(husk.getId(), 1, 1.0F);
        helper.runAtTickTime(20, () -> {
            for (LiveScene.Element element : scene.elements()) {
                int i = element.index();
                String id = element.blockIds().get(0);
                helper.assertTrue(scene.consensus(i) >= scene.weight(i), "the " + id + " was never agreed on, so refusing it proves nothing");
                helper.assertFalse(scene.manifested(i), "an imagined " + id + " was made real");
                helper.assertTrue(helper.getLevel().getBlockState(element.cells().get(0)).isAir(), id + " stands where the lie was");
            }
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_14")
    public static void aFigmentMadeRealAgainIsNotHealed(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LivingEntity[] believer = new LivingEntity[1];
        LiveScene scene = believedChicken(helper, owner, believer);
        float[] wounded = new float[1];
        helper.runAtTickTime(12, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "the chicken never became real");
            chicken.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 2.0F);
            wounded[0] = chicken.getHealth();
            helper.assertTrue(wounded[0] < chicken.getMaxHealth(), "the blow landed nothing");
            scene.belief().set(believer[0].getId(), 0, 0.1F);
        });
        helper.runAtTickTime(18, () -> {
            helper.assertFalse(creature(helper, scene).isManifested(), "the chicken is still real with nobody convinced");
            scene.belief().set(believer[0].getId(), 0, 1.0F);
        });
        helper.runAtTickTime(24, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "the chicken was not made real again");
            helper.assertTrue(chicken.getHealth() <= wounded[0] + 0.001F,
                    "becoming real again healed the chicken from " + wounded[0] + " to " + chicken.getHealth());
            MindService.endAll(owner);
            helper.succeed();
        });
    }
}
