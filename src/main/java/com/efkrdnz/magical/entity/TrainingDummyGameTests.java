package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The meter reads a real hit off the real damage container: what was dealt, what armour took and
 * what came off. {@code DummyMeterTest} pins the arithmetic; only a live hurt can say the container
 * is still there to be read when {@code actuallyHurt} returns.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class TrainingDummyGameTests {
    private TrainingDummyGameTests() {}

    @GameTest(template = "unwaking_empty", timeoutTicks = 40, batch = "training_dummy")
    public static void anArmouredDummyShowsWhatWasDealtAndWhatItTook(GameTestHelper helper) {
        TrainingDummyEntity dummy = helper.spawn(MagicalEntities.TRAINING_DUMMY.get(), new BlockPos(2, 1, 2));
        Husk attacker = helper.spawn(EntityType.HUSK, new BlockPos(1, 1, 1));
        attacker.setNoAi(true);
        dummy.setArmor(20);
        dummy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 10F);
        String last = dummy.meterLines().get(0);
        helper.assertTrue(last.startsWith("Last  10.0 → "), "an armoured hit must read dealt then taken: " + last);
        helper.assertTrue(dummy.meterLines().stream().anyMatch(line -> line.startsWith("Cut  armor ")),
                "the armour's share must be named: " + dummy.meterLines());
        helper.assertTrue(dummy.getHealth() == dummy.getMaxHealth(), "a dummy never runs down");
        helper.succeed();
    }

    @GameTest(template = "unwaking_empty", timeoutTicks = 60, batch = "training_dummy")
    public static void nothingKnocksADummyAside(GameTestHelper helper) {
        TrainingDummyEntity dummy = helper.spawn(MagicalEntities.TRAINING_DUMMY.get(), new BlockPos(2, 1, 2));
        helper.runAtTickTime(5, () -> {
            net.minecraft.world.phys.Vec3 start = dummy.position();
            // The three routes a hit moves a body by: vanilla's knockback, a skill's push, and a
            // velocity written outright - knockback resistance only ever answered the first.
            dummy.knockback(4.0, 1.0, 0.0);
            dummy.push(1.5, 1.0, -1.5);
            dummy.setDeltaMovement(2.0, 2.0, 2.0);
            helper.runAfterDelay(10, () -> {
                net.minecraft.world.phys.Vec3 now = dummy.position();
                helper.assertTrue(Math.abs(now.x - start.x) < 1e-6 && Math.abs(now.z - start.z) < 1e-6
                        && now.y <= start.y + 1e-6, "a dummy stays where it stands: " + start + " -> " + now);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "unwaking_empty", timeoutTicks = 40, batch = "training_dummy")
    public static void killRemovesADummyInsteadOfMeteringIt(GameTestHelper helper) {
        TrainingDummyEntity dummy = helper.spawn(MagicalEntities.TRAINING_DUMMY.get(), new BlockPos(2, 1, 2));
        dummy.kill(helper.getLevel());
        helper.assertTrue(dummy.isRemoved(), "/kill heals a dummy back to full unless it is removed");
        helper.succeed();
    }

    @GameTest(template = "unwaking_empty", timeoutTicks = 40, batch = "training_dummy")
    public static void anUnarmouredDummyTakesEverything(GameTestHelper helper) {
        TrainingDummyEntity dummy = helper.spawn(MagicalEntities.TRAINING_DUMMY.get(), new BlockPos(2, 1, 2));
        Husk attacker = helper.spawn(EntityType.HUSK, new BlockPos(1, 1, 1));
        attacker.setNoAi(true);
        dummy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 7F);
        dummy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 7F);
        java.util.List<String> lines = dummy.meterLines();
        helper.assertTrue(lines.get(0).startsWith("Last  7.0  "), "no reduction, no arrow: " + lines.get(0));
        helper.assertTrue(lines.get(3).startsWith("Hits 2 "), "back-to-back hits both count: " + lines.get(3));
        helper.succeed();
    }
}
