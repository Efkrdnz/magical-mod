package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.magic.MagicCastingService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.magic.skill.blood.CoagulateSkill;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pooled blood, run against a real level: spilled, waited for, lifted, landed, paid - and the
 * kit that moves it: a vein opened, a spear thrown, a shell set, a rite held, a walk taken.
 *
 * <p>Everything a unit test can hold is held elsewhere - the rules in {@code BloodHarvestRulesTest},
 * the motion in {@code BloodHarvestMotionTest}. What only a level can show is that the entities
 * actually tick through their phases against a player who is or is not there, and that the Vessel
 * moves exactly once, by exactly the yield, when the blood lands and not before.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class BloodHarvestGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final String BATCH = "blood_harvest";

    private static final BlockPos NEAR = new BlockPos(2, 2, 2);
    private static final Vec3 CORPSE = new Vec3(5.5D, 2.05D, 2.5D);

    /** Well outside pull range, still inside the chunks the player themselves keeps loaded. */
    private static final BlockPos FAR = new BlockPos(2, 2, 16);

    private BloodHarvestGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void nearbyBloodLiftsStreamsInAndFillsTheVesselWhenItLands(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, NEAR);
        int before = state(player).bloodVessel();
        int yield = BloodHarvestRules.VESSEL_PER_DROP;
        BloodHarvestEntity pool = BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE), yield);
        double distance = BloodHarvestEntity.chestOf(player).distanceTo(pool.position());
        int flight = BloodHarvestRules.flightTicks(distance);

        helper.runAtTickTime(3, () -> {
            helper.assertTrue(pool.phase() == BloodHarvestEntity.PHASE_STREAMING, "A pool within reach must lift");
            helper.assertTrue(pool.flight() == flight, "The flight must be the rules' flight for the distance");
            helper.assertTrue(state(player).bloodVessel() == before, "The Vessel must not fill before the blood lands");
        });
        helper.runAtTickTime(3 + flight + 2, () -> {
            helper.assertTrue(state(player).bloodVessel() == before + yield,
                    "The Vessel must fill by exactly the yield when the blood lands, got "
                            + (state(player).bloodVessel() - before));
            helper.assertTrue(pool.isRemoved(), "Landed blood must be gone");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void farBloodPoolsUntilThePlayerComesNear(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, FAR);
        BloodHarvestEntity pool = BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE),
                BloodHarvestRules.VESSEL_PER_DROP);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(pool.isPooled(), "Blood out of reach must wait");
            Vec3 near = helper.absoluteVec(Vec3.atBottomCenterOf(NEAR));
            player.teleportTo(near.x, near.y, near.z);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(pool.phase() == BloodHarvestEntity.PHASE_STREAMING, "Blood must lift once the player is near");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 330, batch = BATCH)
    public static void bloodNobodyComesForDries(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, FAR);
        int before = state(player).bloodVessel();
        BloodHarvestEntity pool = BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE),
                BloodHarvestRules.VESSEL_PER_DROP);
        helper.runAtTickTime(BloodHarvestRules.POOL_LIFETIME - 2, () ->
                helper.assertTrue(pool.isPooled() && !pool.isRemoved(), "A pool must wait out its whole lifetime"));
        helper.runAtTickTime(BloodHarvestRules.POOL_LIFETIME + 3, () -> {
            helper.assertTrue(pool.isRemoved(), "A pool nobody came for must dry");
            helper.assertTrue(state(player).bloodVessel() == before, "Dried blood pays nothing");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aBatteryWaitsWithinReachAndATraceIsWorthNothing(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, NEAR);
        BloodHarvestEntity battery = BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE),
                BloodHarvestRules.KIND_BATTERY, 20, BloodHarvestRules.POOL_LIFETIME);
        BloodHarvestEntity trace = BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE.add(1.0D, 0.0D, 0.0D)),
                BloodHarvestRules.KIND_TRACE, 0, 60);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(battery.isPooled(), "A battery within pull range must not lift on its own");
            helper.assertTrue(battery.worth() == 20, "and keeps its worth while it waits");
            helper.assertTrue(trace.isPooled() && trace.worth() == 0, "A trace waits too, worth nothing");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void veinWalkLandsOnABatteryLeavesATraceAndDrinksIt(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, NEAR, MagicContent.VEIN_WALK.id());
        Vec3 origin = player.position();
        // Inside the barrier shell the framework puts round the template, like everything a test
        // relies on: a pool in the next cell over is in another test's space.
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 2.05D, 0.5D));
        BloodHarvestEntity battery = BloodHarvestEntity.spawn(helper.getLevel(), player, at,
                BloodHarvestRules.KIND_BATTERY, 20, BloodHarvestRules.POOL_LIFETIME);
        int before = state(player).bloodVessel();
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.VEIN_WALK.id(), false));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(player.position().distanceTo(at) < 1.5D, "The walk must land on the furthest pool in reach");
            helper.assertTrue(battery.phase() == BloodHarvestEntity.PHASE_STREAMING, "A battery landed on must lift into the walker");
            List<BloodHarvestEntity> traces = BloodHarvestEntity.poolsWithin(player, 32.0D).stream()
                    .filter(pool -> pool.kind() == BloodHarvestRules.KIND_TRACE).toList();
            helper.assertTrue(traces.size() == 1 && traces.get(0).position().distanceTo(origin) < 0.5D,
                    "A trace must be left where the walk began");
        });
        helper.runAtTickTime(3 + BloodHarvestRules.MAX_FLIGHT_TICKS, () -> {
            helper.assertTrue(state(player).bloodVessel() == before + 20,
                    "The battery must pay out as it lands, got " + (state(player).bloodVessel() - before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void openVeinBleedsItsVictimIntoAPoolAtItsFeetAndTrailsWhenItRuns(GameTestHelper helper) {
        // The framework encases the template in barrier blocks, and the aim ray stops at the first
        // block, so the victim stands inside the shell with the player: two blocks east, then the
        // far corner when it runs.
        ServerPlayer player = bloodMage(helper, NEAR, MagicContent.OPEN_VEIN.id());
        Zombie victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        float health = victim.getHealth();
        helper.runAtTickTime(1, () -> {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, victim.getEyePosition());
            MagicCastingService.castById(player, MagicContent.OPEN_VEIN.id(), false);
        });
        helper.runAtTickTime(2, () -> helper.assertTrue(MagicStatusService.has(victim, MagicStatus.REVEALED),
                "The cast must take: the victim is lit for as long as it bleeds"));
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(victim.getHealth() < health, "The victim must bleed at the first bite");
            List<BloodHarvestEntity> pools = BloodHarvestEntity.poolsWithin(player, 32.0D);
            helper.assertTrue(pools.size() == 1, "One pool at its feet, got " + pools.size());
            helper.assertTrue(pools.get(0).position().distanceTo(victim.position()) < 1.0D, "at its feet");
            helper.assertTrue(pools.get(0).worth() >= 4 && pools.get(0).feeding(), "being fed");
            Vec3 away = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(0, 2, 0)));
            victim.teleportTo(away.x, away.y, away.z);
        });
        helper.runAtTickTime(24, () -> {
            List<BloodHarvestEntity> pools = BloodHarvestEntity.poolsWithin(player, 32.0D);
            helper.assertTrue(pools.size() == 2, "A victim that ran must leave a second pool, got " + pools.size());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void coagulateDrinksThePoolsInReachAndSetsThemIntoBarrier(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, NEAR, MagicContent.COAGULATE.id());
        BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE),
                BloodHarvestRules.KIND_BATTERY, 20, BloodHarvestRules.POOL_LIFETIME);
        BloodHarvestEntity.spawn(helper.getLevel(), player, helper.absoluteVec(CORPSE.add(0.0D, 0.0D, 3.0D)),
                BloodHarvestRules.KIND_BATTERY, 30, BloodHarvestRules.POOL_LIFETIME);
        state(player).setBarrier(0);
        int vessel = state(player).bloodVessel();
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.COAGULATE.id(), false));
        helper.runAtTickTime(4, () -> {
            int expected = Math.round(50 * CoagulateSkill.SHELL_PER_HUNDRED / 100.0F);
            helper.assertTrue(state(player).barrier() == expected,
                    "Fifty blood at the base rate is " + expected + " barrier, got " + state(player).barrier());
            helper.assertTrue(BloodHarvestEntity.poolsWithin(player, 32.0D).isEmpty(), "The pools must be drunk");
            helper.assertTrue(state(player).bloodVessel() == vessel, "A dry Vessel is not drawn from");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void coagulateOnAFullBarrierStandsAShellAboveItThatWearsWithTheHits(GameTestHelper helper) {
        // The shell is a layer above the barrier, not a refill of it: at a full barrier it raises the
        // cap by what it granted, hits wear it first, and when it is gone the cap is what it was.
        ServerPlayer player = bloodMage(helper, NEAR, MagicContent.COAGULATE.id());
        PlayerMagicState state = state(player);
        state.addBloodVessel(40);
        state.setBarrier(state.maxBarrier());
        int full = state.barrier();
        int granted = Math.round(40 * CoagulateSkill.SHELL_PER_HUNDRED / 100.0F);
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.COAGULATE.id(), false));
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(state.maxBarrier() == full + granted, "The shell raises the cap by what it granted, cap "
                    + state.maxBarrier() + " for " + full + " + " + granted);
            helper.assertTrue(state.barrier() == full + granted, "and stands above the full barrier, got " + state.barrier());
            state.setBarrier(state.barrier() - 10);
        });
        helper.runAtTickTime(8, () -> {
            List<com.efkrdnz.magical.entity.fx.SpellEffectEntity> rings = shells(helper, player);
            helper.assertTrue(rings.size() == 1, "One shell, got " + rings.size());
            float integrity = com.efkrdnz.magical.magic.blood.BloodFieldData.decode(rings.get(0).syncedData()).integrity();
            float expected = (granted - 10) / (float) granted;
            helper.assertTrue(Math.abs(integrity - expected) < 0.02F, "A hit wears the shell first: integrity " + integrity
                    + ", expected " + expected);
            helper.assertTrue(state.maxBarrier() == full + granted - 10, "and the cap follows what is left, got " + state.maxBarrier());
            state.setBarrier(0);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(shells(helper, player).isEmpty(), "A shell worn to nothing is gone");
            helper.assertTrue(state.maxBarrier() == full, "and the cap is what it was, got " + state.maxBarrier());
            helper.succeed();
        });
    }

    private static List<com.efkrdnz.magical.entity.fx.SpellEffectEntity> shells(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntities(com.efkrdnz.magical.registry.MagicalEntities.SPELL_EFFECT.get(),
                player.getBoundingBox().inflate(8.0D),
                effect -> effect.owner() == player && MagicContent.COAGULATE.id().equals(effect.definition().id()));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void aRiteHeldPoursHeartsIntoABatteryAheadThatWaits(GameTestHelper helper) {
        ServerPlayer player = bloodMage(helper, NEAR, MagicContent.BLOOD_RITE.id());
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        HoldService.setHeld(player, 0, true);
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.BLOOD_RITE.id(), false));
        helper.runAtTickTime(20, () -> HoldService.setHeld(player, 0, true));
        helper.runAtTickTime(30, () -> {
            List<BloodHarvestEntity> pools = BloodHarvestEntity.poolsWithin(player, 32.0D);
            helper.assertTrue(pools.size() == 1, "One battery ahead, got " + pools.size());
            BloodHarvestEntity pool = pools.get(0);
            helper.assertTrue(pool.kind() == BloodHarvestRules.KIND_BATTERY, "set down, not harvested");
            helper.assertTrue(pool.worth() >= 8 && pool.feeding(), "and fed a heart at a time, got " + pool.worth());
            double ahead = pool.position().distanceTo(player.position());
            helper.assertTrue(ahead > 2.5D && ahead < 5.5D, "about four blocks ahead, got " + ahead);
            HoldService.setHeld(player, 0, false);
        });
        helper.runAtTickTime(60, () -> {
            List<BloodHarvestEntity> pools = BloodHarvestEntity.poolsWithin(player, 32.0D);
            helper.assertTrue(pools.size() == 1 && !pools.get(0).feeding(), "Releasing the key ends the rite");
            helper.assertTrue(pools.get(0).isPooled(), "and the battery waits where it was poured");
            helper.succeed();
        });
    }

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    private static ServerPlayer bloodMage(GameTestHelper helper, BlockPos at) {
        return bloodMage(helper, at, MagicContent.BLOOD_MANIPULATION.id());
    }

    /** A player holding one blood skill, which is all it takes to be a blood mage, standing at {@code at}. */
    private static ServerPlayer bloodMage(GameTestHelper helper, BlockPos at, net.minecraft.resources.ResourceLocation skill) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "harvest-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        player.teleportTo(stand.x, stand.y, stand.z);
        state(player).unlockAll(Set.of(skill));
        return player;
    }
}
