package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.entity.verse.VerseBodySpawner;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The body against a real level, driven by hand-built plans and no verse at all: a needle flies its
 * line and hurts what it meets, a ring stands on the floor and pulses over its radius for its
 * duration, a detonation spares no one in its radius, a fuse releases its payload where the body
 * is, a bounce is not an end, a blink carries its caster, a twin path spawns two, a naught body is
 * gone at once. What a unit test can hold (the fan, the codec, the effect order, the steering) is
 * held in {@code entity/verse}; this is what only a level shows.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class VerseBodyGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    /** The template is five blocks of air in a barrier shell: the stand at its middle, the victim two blocks on, the far wall half a block past that. */
    private static final BlockPos STAND = new BlockPos(2, 2, 2);
    private static final BlockPos VICTIM = new BlockPos(4, 2, 2);
    /** Any registered skill will do for a hand-built plan; the recite skills arrive with the Authority. */
    private static final ResourceLocation SKILL = MagicContent.ARCANE_SNAP.id();

    private VerseBodyGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** A survival player standing on the floor at {@code at}, full of mana, client-loaded so a detonation can reach them. */
    private static ServerPlayer caster(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = GameTestPlayers.survival(helper, at, "verse-test");
        state(player).refillMana();
        return player;
    }

    private static Zombie victim(GameTestHelper helper, ServerPlayer player) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(GameTestPlayers.onFloor(helper, VICTIM)));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
        return zombie;
    }

    private static ProjectilePlan body(VersePrototype prototype, Consumer<ShotState> stamp, PayloadKind kind, int fuse, ShotPlan payload) {
        ShotState state = new ShotState();
        stamp.accept(state);
        return new ProjectilePlan(prototype, prototype.id(), state, kind, fuse, payload);
    }

    private static ShotPlan shot(ProjectilePlan... bodies) {
        return new ShotPlan(List.of(bodies), new ShotState());
    }

    private static List<VerseBodyEntity> bodies(GameTestHelper helper, ServerPlayer player) {
        return VerseBodyEntity.ownedBy(helper.getLevel(), player, helper.getBounds().inflate(2.0D));
    }

    private static void spawnFromHand(GameTestHelper helper, ServerPlayer player, ShotPlan plan) {
        VerseBodySpawner.spawn(helper.getLevel(), player, plan, player.getEyePosition(), player.getLookAngle(), SKILL);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_1")
    public static void aNeedleFliesItsLineAndHurtsWhatItMeets(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        Zombie zombie = victim(helper, player);
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> spawnFromHand(helper, player, shot(body(VersePrototypes.NEEDLE, s -> { }, PayloadKind.NONE, 0, null))));
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(zombie.getHealth() < health, "the needle hurt the husk: " + zombie.getHealth() + " of " + health);
            helper.assertTrue(bodies(helper, player).isEmpty(), "and ended on the hit");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 130, batch = "verse_2")
    public static void aRingStandsOnTheFloorAndPulsesOverItsRadiusForItsDuration(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        Zombie zombie = victim(helper, player);
        double floorY = GameTestPlayers.onFloor(helper, STAND).y;
        helper.runAtTickTime(1, () -> spawnFromHand(helper, player, shot(body(VersePrototypes.RING_RIME, s -> { }, PayloadKind.NONE, 0, null))));
        helper.runAtTickTime(3, () -> {
            List<VerseBodyEntity> rings = bodies(helper, player);
            helper.assertTrue(rings.size() == 1, "one ring stands");
            helper.assertTrue(Math.abs(rings.get(0).getY() - floorY) < 0.2D, "on the floor, not at eye height: " + rings.get(0).getY());
            double ahead = rings.get(0).position().subtract(player.getEyePosition()).horizontalDistance();
            helper.assertTrue(ahead > 0.6D && ahead < 1.4D, "one block ahead of the hand: " + ahead);
        });
        helper.runAtTickTime(15, () -> helper.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the rime ring froze the husk inside it"));
        helper.runAtTickTime(108, () -> {
            helper.assertTrue(bodies(helper, player).isEmpty(), "and stood down after its hundred ticks");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_3")
    public static void aDetonationBurstsAtOnceAndSparesNoOneInItsRadius(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        Zombie zombie = victim(helper, player);
        // A fresh state carries a full barrier, and the barrier would soak the blast before it
        // reached the health bar; the rule under test is the radius, so take the barrier away.
        state(player).setBarrier(0);
        float health = zombie.getHealth();
        float own = player.getHealth();
        helper.runAtTickTime(1, () -> spawnFromHand(helper, player, shot(body(VersePrototypes.BURST, s -> { }, PayloadKind.NONE, 0, null))));
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(zombie.getHealth() < health, "the burst hurt the husk");
            helper.assertTrue(player.getHealth() < own, "and the caster standing beside it: " + player.getHealth());
            helper.assertTrue(bodies(helper, player).isEmpty(), "a detonation lasts one tick");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_4")
    public static void aFuseReleasesItsPayloadWhereTheBodyIs(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        ShotPlan payload = shot(body(VersePrototypes.RING_RIME, s -> { }, PayloadKind.NONE, 0, null));
        ProjectilePlan orb = body(VersePrototypes.ORB, s -> s.multiplySpeed(0.2D), PayloadKind.FUSE, 3, payload);
        helper.runAtTickTime(1, () -> VerseBodySpawner.spawn(helper.getLevel(), player, shot(orb), player.getEyePosition(), new Vec3(0.0D, 1.0D, 0.0D), SKILL));
        helper.runAtTickTime(2, () -> helper.assertTrue(bodies(helper, player).size() == 1 && bodies(helper, player).get(0).prototype() == VersePrototypes.ORB, "the orb flies"));
        helper.runAtTickTime(9, () -> {
            List<VerseBodyEntity> left = bodies(helper, player);
            helper.assertTrue(left.size() == 1, "the orb is gone and its payload stands: " + left.size());
            helper.assertTrue(left.get(0).prototype() == VersePrototypes.RING_RIME, "the payload is the ring");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_5")
    public static void aBounceIsNotAnEndAndTheNextWallIs(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        // Half speed is 0.8 blocks a tick. Straight back from x 2.5 into the shell at x 0: the first
        // wall at flight tick 4, the bounce, then five blocks to the shell at x 5 by flight tick 11.
        ProjectilePlan needle = body(VersePrototypes.NEEDLE, s -> { s.addBounces(1); s.multiplySpeed(0.5D); }, PayloadKind.NONE, 0, null);
        helper.runAtTickTime(1, () -> VerseBodySpawner.spawn(helper.getLevel(), player, shot(needle), player.getEyePosition(), new Vec3(-1.0D, 0.0D, 0.0D), SKILL));
        helper.runAtTickTime(3, () -> helper.assertTrue(bodies(helper, player).size() == 1, "still flying after the bounce"));
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(bodies(helper, player).isEmpty(), "and ended on the wall it had no bounce left for");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_6")
    public static void aBlinkCarriesItsCasterToWhereItEnds(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        Zombie zombie = victim(helper, player);
        double before = Math.abs(player.getX() - zombie.getX());
        helper.runAtTickTime(1, () -> spawnFromHand(helper, player, shot(body(VersePrototypes.BLINK, s -> { }, PayloadKind.NONE, 0, null))));
        helper.runAtTickTime(8, () -> {
            double after = Math.abs(player.getX() - zombie.getX());
            helper.assertTrue(after < before - 0.5D, "the caster was carried toward the impact: " + before + " -> " + after);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_7")
    public static void aTwinPathSpawnsItsSiblingBesideIt(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        ProjectilePlan needle = body(VersePrototypes.NEEDLE, s -> { s.behaviour(Behaviour.TWIN_PATH); s.multiplySpeed(0.25D); }, PayloadKind.NONE, 0, null);
        helper.runAtTickTime(1, () -> VerseBodySpawner.spawn(helper.getLevel(), player, shot(needle), player.getEyePosition(), new Vec3(1.0D, 0.0D, 0.0D), SKILL));
        helper.runAtTickTime(3, () -> {
            List<VerseBodyEntity> twins = bodies(helper, player);
            helper.assertTrue(twins.size() == 2, "two bodies from one plan: " + twins.size());
            helper.assertTrue(twins.get(0).direction().distanceTo(twins.get(1).direction()) > 0.1D, "on parted headings");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_8")
    public static void aNaughtBodyIsGoneOnItsFirstTick(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        ProjectilePlan needle = body(VersePrototypes.NEEDLE, s -> s.behaviour(Behaviour.NAUGHT), PayloadKind.NONE, 0, null);
        helper.runAtTickTime(1, () -> VerseBodySpawner.spawn(helper.getLevel(), player, shot(needle), player.getEyePosition(), new Vec3(1.0D, 0.0D, 0.0D), SKILL));
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(bodies(helper, player).isEmpty(), "a naught body never flies");
            helper.succeed();
        });
    }
}
