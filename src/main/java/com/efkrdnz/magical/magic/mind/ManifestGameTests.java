package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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
}
