package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class FigmentGameTests {
    private static final String TEMPLATE = "unwaking_empty";
    private static final String VILLAGER = "minecraft:villager";

    private FigmentGameTests() {}

    private static LiveScene villagerAt(GameTestHelper helper, UUID owner, BlockPos relative) {
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("creature:" + VILLAGER);
        reverie.addFigment(new Offset(0, 0, 0), VILLAGER, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie,
                BlockPos.containing(onFloor(helper, relative)), 0, lexicon);
        helper.assertTrue(scene != null, "the figment scene was refused");
        return scene;
    }

    private static FigmentEntity figment(GameTestHelper helper, LiveScene scene) {
        return (FigmentEntity) helper.getLevel().getEntity(scene.figmentEntity(0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "mind_figment_1")
    public static void aHuskHuntsAFigmentVillagerItBelieves(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = villagerAt(helper, owner, new BlockPos(3, 2, 3));
        Mob husk = helper.spawn(EntityType.HUSK, new BlockPos(1, 0, 1));
        scene.belief().set(husk.getId(), 0, 0.6F);
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getTarget() instanceof FigmentEntity, "the husk has not gone for the villager it believes");
            MindService.endAll(owner);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_figment_2")
    public static void strikingAFigmentShattersItForTheStriker(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(1, 2, 2), "mind-strike-test");
        UUID owner = UUID.randomUUID();
        LiveScene scene = villagerAt(helper, owner, new BlockPos(2, 2, 2));
        scene.belief().set(player.getId(), 0, 0.6F);
        helper.runAtTickTime(2, () -> {
            FigmentEntity target = figment(helper, scene);
            helper.assertTrue(target != null, "the figment was never spawned");
            player.attack(target);
            helper.assertTrue(scene.belief().shattered(player.getId(), 0), "a blow through a figment left the belief standing");
            helper.assertTrue(target.isAlive(), "a figment cannot be killed, only disbelieved");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "mind_figment_3")
    public static void aHuskThatStopsBelievingLetsGo(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = villagerAt(helper, owner, new BlockPos(3, 2, 3));
        Mob husk = helper.spawn(EntityType.HUSK, new BlockPos(1, 0, 1));
        scene.belief().set(husk.getId(), 0, 0.6F);
        // The husk walks up and strikes what it believes about 30 ticks in, and that blow is a TOUCH that
        // shatters its own belief and releases it anyway - so the doubt is planted the tick the hunt
        // begins, not at a fixed time the husk may already have spent.
        long[] doubted = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (doubted[0] < 0 && husk.getTarget() instanceof FigmentEntity) {
                doubted[0] = now;
                scene.belief().set(husk.getId(), 0, 0.2F);
            } else if (doubted[0] >= 0 && now == doubted[0] + 4) {
                helper.assertTrue(husk.getTarget() == null, "the husk still hunts a villager it no longer believes");
                MindService.endAll(owner);
            } else if (doubted[0] >= 0 && now == doubted[0] + 6) {
                helper.assertTrue(figment(helper, scene) == null || !figment(helper, scene).isAlive(),
                        "the figment outlived its scene");
                helper.succeed();
            }
        });
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(doubted[0] >= 0, "the husk never went for the villager");
            MindService.endAll(owner);
        });
    }
}
