package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class MindPathGameTests {
    private static final String TEMPLATE = "unwaking_empty";
    private static final String STONE = "minecraft:stone";

    private MindPathGameTests() {}

    /** An imagined stone wall two high across x 0..3 of the template, leaving x 4 open. */
    private static Reverie wall() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("block:" + STONE);
        for (int x = 0; x < 4; x++) {
            reverie.addBlock(new Offset(x, 0, 0), STONE, lexicon);
            reverie.addBlock(new Offset(x, 1, 0), STONE, lexicon);
        }
        return reverie;
    }

    private static Path pathAcross(GameTestHelper helper, Mob mob, BlockPos floor) {
        mob.setOnGround(true);
        return mob.getNavigation().createPath(floor.offset(0, 0, 4), 0);
    }

    private static boolean throughWall(Path path, BlockPos wallRow) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos node = path.getNode(i).asBlockPos();
            if (node.getZ() == wallRow.getZ() && node.getX() < wallRow.getX() + 4
                    && node.getY() >= wallRow.getY() && node.getY() <= wallRow.getY() + 1) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_path_1")
    public static void aMobThatBelievesAWallWalksRoundIt(GameTestHelper helper) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(1, 2, 0))));
        // Row the husk stands in, one column left of it: the template floor is not the same height
        // in every column, so the wall is measured from the husk's own cell, not from column 0.
        BlockPos floor = husk.blockPosition().offset(-1, 0, 0);
        BlockPos wallRow = floor.offset(0, 0, 2);
        UUID owner = UUID.randomUUID();
        LiveScene scene = MindGameTests.unveil(helper, owner, wall(), wallRow);
        scene.belief().set(husk.getId(), 0, Belief.CONVINCED);
        Path path = pathAcross(helper, husk, floor.offset(1, 0, 0));
        MindService.endAll(owner);
        helper.assertTrue(path != null && path.canReach(), "no way round an imagined wall with a gap at x 4");
        helper.assertFalse(throughWall(path, wallRow), "the believer walked through the wall it believes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_path_2")
    public static void aMobThatDoesNotBelieveItWalksStraightThrough(GameTestHelper helper) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(1, 2, 0))));
        // Row the husk stands in, one column left of it: the template floor is not the same height
        // in every column, so the wall is measured from the husk's own cell, not from column 0.
        BlockPos floor = husk.blockPosition().offset(-1, 0, 0);
        BlockPos wallRow = floor.offset(0, 0, 2);
        UUID owner = UUID.randomUUID();
        LiveScene scene = MindGameTests.unveil(helper, owner, wall(), wallRow);
        scene.belief().set(husk.getId(), 0, 0.2F);
        Path path = pathAcross(helper, husk, floor.offset(1, 0, 0));
        MindService.endAll(owner);
        helper.assertTrue(path != null && throughWall(path, wallRow), "a doubter should take the straight line");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_path_3")
    public static void aBelievedLidOverAPitDropsWhoeverTrustsIt(GameTestHelper helper) {
        // A stone floor one block up across the template, with a hole at (2, 0, 2) and a lid imagined over it.
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (x != 2 || z != 2) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
        }
        BlockPos hole = helper.absolutePos(new BlockPos(2, 0, 2));
        Reverie lid = new Reverie();
        lid.addBlock(new Offset(0, 0, 0), STONE, MindGameTests.knowing("block:" + STONE));
        UUID owner = UUID.randomUUID();
        LiveScene scene = MindGameTests.unveil(helper, owner, lid, hole);
        Mob husk = helper.spawn(EntityType.HUSK, new BlockPos(2, 1, 2));
        scene.belief().set(husk.getId(), 0, 0.6F);
        helper.assertTrue(MindPathing.override(new net.minecraft.world.level.pathfinder.PathfindingContext(helper.getLevel(), husk),
                        husk, hole.getX(), hole.getY() + 1, hole.getZ()) == net.minecraft.world.level.pathfinder.PathType.WALKABLE,
                "the cell over a believed lid is not walkable");
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(scene.belief().shattered(husk.getId(), 0), "the husk fell through its lid and still believes it");
            MindService.endAll(owner);
            helper.succeed();
        });
    }
}
