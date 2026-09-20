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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The body against a real level, driven by hand-built plans and no verse at all: a needle flies its
 * line and hurts what it meets, a ring stands on the floor and pulses over its radius for its
 * duration, a detonation spares no one in its radius, a fuse releases its payload where the body
 * is, a bounce is not an end, a blink carries its caster, a twin path spawns two, a naught body is
 * gone at once, a Puncture takes every body on its line, a pit turns a body flying past it toward
 * itself, a fan that lands on one body lands whole. What a unit test can hold (the fan, the codec, the effect order, the steering) is held
 * in {@code entity/verse}; this is what only a level shows.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class VerseBodyGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    /** The template is five blocks of air in a barrier shell: the stand at its middle, the victim two blocks on, the far wall half a block past that. */
    private static final BlockPos STAND = new BlockPos(2, 2, 2);
    private static final BlockPos VICTIM = new BlockPos(4, 2, 2);
    /** Halfway between the two: a second husk in front of the first, so one line can hold both. */
    private static final BlockPos NEAR_VICTIM = new BlockPos(3, 2, 2);
    /** A block back from the stand: a golem at the near mark is then a block and a third from the eyes, wide enough for a whole fan. */
    private static final BlockPos NEAR_STAND = new BlockPos(1, 2, 2);
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

    /** A husk on the floor of {@code at}, still as a post; never a zombie, which burns in the test world's day. */
    private static Zombie husk(GameTestHelper helper, BlockPos at) {
        return helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(GameTestPlayers.onFloor(helper, at)));
    }

    /** A golem on the floor of {@code at}: a body wide enough that every needle of a fan lands on it, and deep enough to take them. */
    private static IronGolem golem(GameTestHelper helper, BlockPos at) {
        return helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, helper.relativeVec(GameTestPlayers.onFloor(helper, at)));
    }

    private static Zombie victim(GameTestHelper helper, ServerPlayer player) {
        Zombie zombie = husk(helper, VICTIM);
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

    /** The bodies fanned over {@code pattern} degrees, as a trident fans its three. */
    private static ShotPlan fan(double pattern, ProjectilePlan... bodies) {
        ShotState group = new ShotState();
        group.setPattern(pattern);
        return new ShotPlan(List.of(bodies), group);
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

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_9")
    public static void aPunctureNeedleTakesEveryBodyOnItsLine(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        // Two husks a block apart on the line, and a needle at its own speed: 1.6 blocks a tick
        // carries the first step from the hand at x 2.5 to x 4.1, which is past both of them, so
        // the loop has to find the second one on the tick it struck the first, and then the wall
        // past them ends the flight. The loop terminates because a struck body is remembered and
        // never offered again; break that instead and the tick never ends, which no timeout can
        // interrupt, so the flag is the only safe thing to mutate to prove this test has teeth.
        Zombie near = husk(helper, NEAR_VICTIM);
        Zombie far = husk(helper, VICTIM);
        float nearHealth = near.getHealth();
        float farHealth = far.getHealth();
        ProjectilePlan needle = body(VersePrototypes.NEEDLE, s -> s.behaviour(Behaviour.PUNCTURE), PayloadKind.NONE, 0, null);
        helper.runAtTickTime(1, () -> VerseBodySpawner.spawn(helper.getLevel(), player, shot(needle), player.getEyePosition(), new Vec3(1.0D, 0.0D, 0.0D), SKILL));
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(near.getHealth() < nearHealth, "the needle hurt the near husk: " + near.getHealth() + " of " + nearHealth);
            helper.assertTrue(far.getHealth() < farHealth, "and went on through it into the far one: " + far.getHealth() + " of " + farHealth);
            helper.assertTrue(bodies(helper, player).isEmpty(), "and ended on the wall behind them both");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "verse_10")
    public static void aPitTurnsAPassingNeedleTowardItself(GameTestHelper helper) {
        ServerPlayer player = caster(helper, STAND);
        // The pit stands a block east of the hand and drops to the floor; its radius of 2.5 covers
        // the whole interior, so a body anywhere in the room is inside it. The needle goes off
        // along z at a sixteenth of its speed, which keeps it in the air and clear of every wall
        // well past the pit's tenth tick, when a static first pulses. Along z is x exactly zero,
        // so any x in the heading afterwards is the pit's doing and nothing else's.
        Vec3 launch = new Vec3(0.0D, 0.0D, 1.0D);
        ProjectilePlan pit = body(VersePrototypes.PIT, s -> { }, PayloadKind.NONE, 0, null);
        ProjectilePlan needle = body(VersePrototypes.NEEDLE, s -> s.multiplySpeed(0.0625D), PayloadKind.NONE, 0, null);
        helper.runAtTickTime(1, () -> {
            VerseBodySpawner.spawn(helper.getLevel(), player, shot(pit), player.getEyePosition(), new Vec3(1.0D, 0.0D, 0.0D), SKILL);
            VerseBodySpawner.spawn(helper.getLevel(), player, shot(needle), player.getEyePosition(), launch, SKILL);
        });
        helper.runAtTickTime(20, () -> {
            List<VerseBodyEntity> live = bodies(helper, player);
            helper.assertTrue(live.size() == 2, "the pit stands and the needle still flies: " + live.size());
            VerseBodyEntity standing = live.stream().filter(b -> b.prototype().isStatic()).findFirst().orElseThrow();
            VerseBodyEntity flying = live.stream().filter(b -> !b.prototype().isStatic()).findFirst().orElseThrow();
            Vec3 heading = flying.direction();
            helper.assertTrue(heading.x > 0.05D, "the needle leaned toward the pit it was flying past: x " + heading.x);
            Vec3 toPit = standing.position().subtract(flying.position()).normalize();
            helper.assertTrue(heading.dot(toPit) > launch.dot(toPit),
                    "and points nearer it than the heading it set out on: " + heading.dot(toPit) + " over " + launch.dot(toPit));
            helper.succeed();
        });
    }

    /**
     * One needle, then past its hurt cooldown a trident's fan of three on the same tick: the fan
     * takes three needles' worth, not one. Vanilla keeps a hit for ten ticks and refuses anything
     * in them that is no stronger, which would make every multicast one body wide.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "verse_11")
    public static void aFanThatLandsOnOneBodyLandsWhole(GameTestHelper helper) {
        ServerPlayer player = caster(helper, NEAR_STAND);
        IronGolem golem = golem(helper, NEAR_VICTIM);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, golem.getEyePosition());
        float[] health = {golem.getHealth(), 0.0F};
        helper.runAtTickTime(1, () -> spawnFromHand(helper, player, shot(body(VersePrototypes.NEEDLE, s -> { }, PayloadKind.NONE, 0, null))));
        helper.runAtTickTime(8, () -> {
            health[1] = golem.getHealth();
            helper.assertTrue(health[1] < health[0], "one needle hurt the golem: " + health[1] + " of " + health[0]);
        });
        helper.runAtTickTime(30, () -> spawnFromHand(helper, player, fan(20.0D,
                body(VersePrototypes.NEEDLE, s -> { }, PayloadKind.NONE, 0, null),
                body(VersePrototypes.NEEDLE, s -> { }, PayloadKind.NONE, 0, null),
                body(VersePrototypes.NEEDLE, s -> { }, PayloadKind.NONE, 0, null))));
        helper.runAtTickTime(38, () -> {
            float one = health[0] - health[1];
            float three = health[1] - golem.getHealth();
            helper.assertTrue(bodies(helper, player).isEmpty(), "every needle of the fan ended on the golem");
            helper.assertTrue(three > 2.5F * one, "three needles inside one hurt cooldown all landed: " + three + " against " + one + " for one");
            helper.succeed();
        });
    }

    /**
     * An orb carrying an explosion, on a golem: the golem takes the hit and the whole explosion,
     * which is what the reading says it lands, because falloff is measured from the nearest point
     * of the body reached and not from its feet, a block and a half below the burst.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "verse_12")
    public static void aBodyLandsWhatTheReadingSays(GameTestHelper helper) {
        ServerPlayer player = caster(helper, NEAR_STAND);
        IronGolem golem = golem(helper, NEAR_VICTIM);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, golem.getEyePosition());
        float health = golem.getHealth();
        ShotPlan orb = shot(body(VersePrototypes.ORB, s -> {
            s.addExplosionRadius(2.0D);
            s.addExplosionDamage(3.0D);
        }, PayloadKind.NONE, 0, null));
        double reading = Landing.of(orb).damage();
        helper.runAtTickTime(1, () -> spawnFromHand(helper, player, orb));
        helper.runAtTickTime(10, () -> {
            double landed = health - golem.getHealth();
            helper.assertTrue(Math.abs(landed - reading) < 0.01D, "the golem took what the reading says, " + reading + ", not " + landed);
            helper.succeed();
        });
    }
}
