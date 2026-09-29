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
