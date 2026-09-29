package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.entity.mind.FigmentReactionGoal;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.service.SkillTargets;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_figment_10")
    public static void theWielderStrikingTheirOwnFigmentLearnsNothing(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(1, 2, 2), "mind-owner-test");
        LiveScene scene = villagerAt(helper, player.getUUID(), new BlockPos(2, 2, 2));
        helper.runAtTickTime(2, () -> {
            FigmentEntity target = figment(helper, scene);
            helper.assertTrue(target != null, "the figment was never spawned");
            player.attack(target);
            helper.assertFalse(scene.belief().shattered(player.getId(), 0),
                    "the wielder is never a viewer, so their own blow cannot see through their own lie");
            MindService.endAll(player.getUUID());
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

    private static boolean reacting(FigmentEntity figment) {
        return figment.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.isRunning() && wrapped.getGoal() instanceof FigmentReactionGoal);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "mind_figment_4")
    public static void aChasingFigmentStrikesHollowAtAViewerWhoDoubts(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("creature:" + VILLAGER);
        reverie.addFigment(new Offset(0, 0, 0), VILLAGER, lexicon);
        reverie.setScript(0, new Script(Stance.IDLE, Reaction.CHASE));
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie,
                BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 2))), 0, lexicon);
        helper.assertTrue(scene != null, "the figment scene was refused");
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(3, 2, 2))));
        scene.belief().set(husk.getId(), 0, 0.6F);
        boolean[] doubting = {false};
        // The doubt is held at 0.3 from the tick the chase begins: a viewer next to the figment would
        // otherwise climb back toward belief in the twenty ticks before the next strike.
        helper.onEachTick(() -> {
            FigmentEntity chaser = figment(helper, scene);
            if (!doubting[0] && chaser != null && reacting(chaser)) {
                doubting[0] = true;
            }
            if (!doubting[0]) {
                return;
            }
            if (scene.belief().shattered(husk.getId(), 0)) {
                MindService.endAll(owner);
                helper.succeed();
            } else {
                scene.belief().set(husk.getId(), 0, 0.3F);
            }
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(doubting[0], "the figment never began its chase");
            helper.assertTrue(false, "a chase that reached a doubter struck no hollow blow");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_figment_5")
    public static void aFigmentIsMeasuredWhereItStandsNotWhereItWasBorn(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = villagerAt(helper, owner, new BlockPos(2, 2, 2));
        FigmentEntity walker = figment(helper, scene);
        helper.assertTrue(walker != null, "the figment was never spawned");
        LiveScene.Element element = scene.elements().get(0);
        walker.setPos(walker.getX() + 2.0, walker.getY(), walker.getZ());
        AABB live = MindService.liveBox(helper.getLevel(), scene, element);
        helper.assertTrue(live.equals(walker.getBoundingBox()), "the live box is not the creature's own");
        helper.assertFalse(live.equals(element.box()), "the live box is still the birthplace");
        MindService.endAll(owner);
        helper.succeed();
    }

    /**
     * In stage 1 nothing out of a reverie is real, so to everything outside the Mind code a figment is
     * no body at all: a spell does not aim at it or count it, a block may be placed where it stands,
     * and fire and potions leave nothing on it for a doubter to see.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_figment_7")
    public static void aFigmentIsNoBodyToAnythingButAMind(GameTestHelper helper) {
        ServerPlayer caster = GameTestPlayers.survival(helper, new BlockPos(1, 2, 2), "mind-nobody-test");
        UUID owner = UUID.randomUUID();
        LiveScene scene = villagerAt(helper, owner, new BlockPos(3, 2, 2));
        FigmentEntity figment = figment(helper, scene);
        helper.assertTrue(figment != null, "the figment was never spawned");
        ServerLevel level = helper.getLevel();
        helper.assertFalse(SkillTargets.isHostile(caster, figment), "a spell counts a figment as a foe");
        helper.assertFalse(SkillTargets.isAlly(caster, figment), "a spell counts a figment as a friend");
        helper.assertTrue(SkillTargets.hostilesWithin(level, caster, figment.position(), 4.0).stream().noneMatch(e -> e == figment),
                "hostilesWithin gathered a figment");
        helper.assertTrue(SkillTargets.alliesWithin(level, caster, figment.position(), 4.0).stream().noneMatch(e -> e == figment),
                "alliesWithin gathered a figment");
        caster.lookAt(EntityAnchorArgument.Anchor.EYES, figment.getBoundingBox().getCenter());
        AimResolver.Result aim = AimResolver.resolve(level, caster, 8.0, 1.0, false);
        helper.assertTrue(aim.entity() != figment, "an aimed spell snapped onto a figment");
        helper.assertFalse(figment.blocksBuilding, "a figment stops a block being placed where it stands");
        helper.assertFalse(figment.canBeSeenAsEnemy(), "vanilla may take a figment for an enemy");
        figment.igniteForSeconds(5.0F);
        helper.assertFalse(figment.isOnFire() || figment.displayFireAnimation(), "a figment burns where every client can see it");
        helper.assertFalse(figment.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "a figment took a potion effect");
        MindService.endAll(owner);
        helper.succeed();
    }

    /**
     * A zoglin goes for every living thing near it, through vanilla's own targeting. A figment is not
     * one, whatever the zoglin makes of it: only a mind that hunts a creature by instinct and believes
     * it goes after a figment of it, and that path is the Mind's own.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "mind_figment_8")
    public static void vanillaAggressionPassesAFigmentBy(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = villagerAt(helper, owner, new BlockPos(3, 2, 3));
        Zoglin zoglin = helper.spawn(EntityType.ZOGLIN, helper.relativeVec(onFloor(helper, new BlockPos(1, 2, 1))));
        helper.onEachTick(() -> helper.assertFalse(zoglin.getTarget() instanceof FigmentEntity,
                "a zoglin went for a figment"));
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(figment(helper, scene) != null, "the figment was never spawned");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    /**
     * The other half of the same seam: a figment is no enemy to vanilla, and vanilla's avoid goal asks
     * for one, so a creeper that believes a cat must still find it through the Mind's own flee goal.
     * The search is asked directly: where a flight leads inside the walled template is up to chance.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_figment_9")
    public static void aCreeperStillFindsTheCatItBelievesToRunFrom(GameTestHelper helper) {
        String cat = "minecraft:cat";
        UUID owner = UUID.randomUUID();
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("creature:" + cat);
        reverie.addFigment(new Offset(0, 0, 0), cat, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie,
                BlockPos.containing(onFloor(helper, new BlockPos(3, 2, 3))), 0, lexicon);
        helper.assertTrue(scene != null, "the figment scene was refused");
        Mob creeper = helper.spawn(EntityType.CREEPER, helper.relativeVec(onFloor(helper, new BlockPos(1, 2, 1))));
        MindMobEvents.BelievedFleeGoal flee = creeper.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(goal -> goal instanceof MindMobEvents.BelievedFleeGoal)
                .map(goal -> (MindMobEvents.BelievedFleeGoal) goal)
                .findFirst().orElse(null);
        helper.assertTrue(flee != null, "a creeper was never taught to flee a figment");
        helper.assertTrue(flee.nearestFeared() == null, "a creeper runs from a cat it does not believe");
        scene.belief().set(creeper.getId(), 0, 0.6F);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(flee.nearestFeared() instanceof FigmentEntity, "a creeper cannot find the cat it believes");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_figment_6")
    public static void anArrowThroughAFigmentPassesAndCountsAsEvidence(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = villagerAt(helper, owner, new BlockPos(3, 2, 1));
        FigmentEntity target = figment(helper, scene);
        helper.assertTrue(target != null, "the figment was never spawned");
        helper.assertFalse(target.canBeHitByProjectile(), "a projectile could hit a figment");
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(3, 2, 3))));
        scene.belief().set(husk.getId(), 0, 0.6F);
        Arrow arrow = helper.spawn(EntityType.ARROW,
                helper.relativeVec(new Vec3(target.getX() - 1.5, target.getY() + 1.0, target.getZ())));
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0.5, 0.0, 0.0);
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(scene.belief().get(husk.getId(), 0) < Belief.CONVINCED,
                    "a viewer watched an arrow cross the figment and still believes it");
            helper.assertTrue(target.isAlive(), "the arrow killed a figment");
            MindService.endAll(owner);
            helper.succeed();
        });
    }
}
