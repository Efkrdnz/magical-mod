package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
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

    /**
     * A body that never believed the wall - it never saw it, the wall rose round it - has still been
     * inside it, and that is first-hand evidence: the wall is shattered for it, so it can never fade in
     * afterwards the way it would for a mind that simply had not looked yet.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 20, batch = "mind_5")
    public static void aBodyThatNeverBelievedAWallItStandsInHasSeenThroughIt(GameTestHelper helper) {
        LivingEntity stander = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 2))));
        UUID owner = UUID.randomUUID();
        LiveScene scene = unveil(helper, owner, column(), stander.blockPosition());
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(scene.belief().shattered(stander.getId(), 0),
                    "a body that stood inside a wall it never believed can still come to believe it");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    /**
     * One projectile query serves the whole scene, so each element has to take only the shots that
     * cross it: an arrow through one wall is evidence against that wall and says nothing about the
     * stone two blocks beside it, though both are in plain view of the same husk.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_6")
    public static void anArrowThroughOneLieSaysNothingOfTheNext(GameTestHelper helper) {
        LivingEntity viewer = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 4))));
        UUID owner = UUID.randomUUID();
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(1, 2, 1)));
        Reverie reverie = column();
        reverie.addBlock(new Offset(2, 0, 0), STONE, knowing("block:" + STONE));
        LiveScene scene = unveil(helper, owner, reverie, anchor);
        int struck = scene.elementAt(anchor);
        int spared = scene.elementAt(anchor.east(2));
        helper.assertTrue(struck >= 0 && spared >= 0 && struck != spared, "the two stones are not two elements");
        scene.belief().set(viewer.getId(), struck, 0.6F);
        scene.belief().set(viewer.getId(), spared, 0.6F);
        Arrow arrow = helper.spawn(EntityType.ARROW,
                helper.relativeVec(new Vec3(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() - 0.8)));
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0.0, 0.0, 0.5);
        // The arrow crosses the wall on its second or third tick. By the sixth the struck wall has lost
        // 0.35 and won back at most four ticks of gain; the stone beside it has only gained or decayed.
        helper.runAtTickTime(6, () -> {
            float hit = scene.belief().get(viewer.getId(), struck);
            float beside = scene.belief().get(viewer.getId(), spared);
            helper.assertTrue(hit < 0.4F, "the husk watched an arrow pass through a wall and believes it " + hit);
            helper.assertTrue(beside > 0.58F, "an arrow through one wall shook belief in the stone beside it: " + beside);
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
