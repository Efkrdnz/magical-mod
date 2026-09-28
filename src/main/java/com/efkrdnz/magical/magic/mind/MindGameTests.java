package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class MindGameTests {
    private static final String TEMPLATE = "unwaking_empty";
    private static final String STONE = "minecraft:stone";

    private MindGameTests() {}

    static Lexicon knowing(String... keys) {
        Lexicon lexicon = new Lexicon();
        for (String key : keys) {
            lexicon.learn(key, 5);
        }
        return lexicon;
    }

    /** One imagined stone, a column of two, at the anchor. */
    static Reverie column() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + STONE);
        reverie.addBlock(new Offset(0, 0, 0), STONE, lexicon);
        reverie.addBlock(new Offset(0, 1, 0), STONE, lexicon);
        return reverie;
    }

    static LiveScene unveil(GameTestHelper helper, UUID owner, Reverie reverie, BlockPos anchor) {
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie, anchor, 0, knowing("block:" + STONE));
        helper.assertTrue(scene != null, "the scene was refused");
        return scene;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_1")
    public static void aPlayerWhoWalksIntoAWallHasWalkedThroughIt(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "mind-wall-test");
        UUID owner = UUID.randomUUID();
        LiveScene scene = unveil(helper, owner, column(), player.blockPosition());
        scene.belief().set(player.getId(), 0, 0.6F);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(scene.belief().shattered(player.getId(), 0),
                    "belief " + scene.belief().get(player.getId(), 0) + " survived a body inside the wall");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_2")
    public static void doubtIsContagious(GameTestHelper helper) {
        LivingEntity fooled = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(1, 2, 2))));
        LivingEntity watcher = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(3, 2, 2))));
        UUID owner = UUID.randomUUID();
        LiveScene scene = unveil(helper, owner, column(), fooled.blockPosition());
        scene.belief().set(fooled.getId(), 0, 0.6F);
        scene.belief().set(watcher.getId(), 0, 0.5F);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(scene.belief().shattered(fooled.getId(), 0), "the husk inside the wall still believes it");
            // 0.5 - 0.20 witnessed, then at most two ticks of gain (under 0.02 each at p <= 1).
            float left = scene.belief().get(watcher.getId(), 0);
            helper.assertTrue(left > 0.25F && left < 0.4F, "the watcher should have lost 0.20, has " + left);
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "mind_3")
    public static void aMobThatCanSeeALieComesToBelieveIt(GameTestHelper helper) {
        LivingEntity viewer = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(1, 2, 1))));
        UUID owner = UUID.randomUUID();
        BlockPos floor = BlockPos.containing(onFloor(helper, new BlockPos(3, 2, 3)));
        LiveScene scene = unveil(helper, owner, column(), floor);
        helper.runAtTickTime(40, () -> {
            float belief = scene.belief().get(viewer.getId(), 0);
            helper.assertTrue(belief > 0.05F, "a husk looking at a wall for two seconds believes it " + belief);
            helper.assertTrue(MindService.believes(viewer, scene, 0) == belief, "believes() reads the ledger");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20, batch = "mind_4")
    public static void aWielderHoldsTwoScenesAtOnce(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        unveil(helper, owner, column(), anchor);
        unveil(helper, owner, column(), anchor.east());
        helper.assertTrue(MindService.unveilAt(helper.getLevel(), owner, column(), anchor.west(), 0, knowing("block:" + STONE)) == null,
                "a third scene was allowed");
        helper.assertTrue(MindService.unveilAt(helper.getLevel(), UUID.randomUUID(), new Reverie(), anchor, 0, new Lexicon()) == null,
                "an empty reverie was unveiled");
        MindService.endAll(owner);
        helper.assertTrue(MindService.scenesOf(owner).isEmpty(), "endAll left a scene");
        helper.succeed();
    }
}
