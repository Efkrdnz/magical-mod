# Incantation Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bind the pure incantation core (Plan 1, landed at `cb3e3a5`) to the game: a body entity that flies, stands, hits, explodes, pulses and releases its payload; the spawner and the renderer; then the four Incantation skills, the Reciter's service and its sessions, the Grimoire on the player state, the edit payload, the commands, the HUD stamps, the lang keys, and the Ledger's removal.

**Architecture:** Two phases in one plan, because the second cannot be exercised without the first and the first has no player-facing entry without the second. **Phase A, the body** (the design's Plan 2, Tasks 1–6): `VerseBodyEntity` reads a `ProjectilePlan` (prototype + stamped `ShotState` + payload) and is the one entity of the Authority; `VerseBodySpawner` fans a `ShotPlan` out of a hand or a release point; `VerseBodyRenderer` draws one look per prototype off the synced state through the existing FX painters. Gametests drive it with hand-built plans, no verses involved. **Phase B, the Authority** (the design's Plan 3, Tasks 7–12): `IncantationService.recite` runs `Reciter.recite` on a per-player `ReciteSession` against a `LevelReciteWorld`, bills the plan, spawns its root and cools the skill; the `Grimoire` rides on `PlayerMagicState` next to the Fracture and syncs with the state blob; four skills, one payload, five commands, four stamps, the lang keys; and last, once the new kit stands, the Ledger kit is removed in one commit so the build is never red.

**Tech Stack:** Java 21, NeoForge 21.4.157 (Minecraft 1.21.4). JUnit 5 through `.\gradlew test`; NeoForge gametests through `.\gradlew runGameTestServer`; the dev client for the one capture (`.\gradlew runClient -PquickPlay=...`).

**Design:** `docs/superpowers/specs/2026-09-19-authority-of-mana-incantation-design.md` §10 (runtime binding), §11 (what is removed), §13 (fiction, the codex label), §14 item 5 (the core review's hand-offs). The core's API is what Plan 1 built: `Reciter.recite(session, breath, mana, costScale, world)` → `RecitePlan(root, beatTicks, restTicks, rests, manaSpent, manaLeft, frayed, events)`; `ShotPlan(bodies, state)`; `ProjectilePlan(prototype, verse, stamped, payloadKind, fuseTicks, payload)`; `VersePrototype(id, school, damage, healing, speed, lifetimeTicks, radius, isStatic, durationTicks, explosionRadius, explosionDamage, pulse, hit, pulseIntervalTicks, look, carriesCaster)`; `ShotState` (getters listed in Task 2); `Grimoire`, `Incantation`, `ReciteSession`, `ReciteWorld`, `IncantationValidator`, `VerseContent.CATALOGUE`, `VersePrototypes.byId/all`, `ReciteCaps`.

## Global Constraints

- **Packages.** The body and everything that serves it: `com.efkrdnz.magical.entity.verse` (`VerseBodyEntity`, `VerseBehaviours`, `VerseHitEffects`, `VerseBodySpawner`, `VerseFan`, `ShotPlanCodec`). The renderer: `com.efkrdnz.magical.client.renderer.verse` (`VerseBodyRenderer`, `VerseLooks`). The service and the world: `com.efkrdnz.magical.magic.incantation` (`IncantationService`, `LevelReciteWorld`), which are the **only** two files in that package allowed to import Minecraft beyond `ResourceLocation` and `net.minecraft.nbt`; the core files Plan 1 listed stay pure. Gametests live in `src/main/java` (as `EldritchGameTests` does): `magic/incantation/VerseBodyGameTests`, `magic/incantation/IncantationGameTests`. The payload: `com.efkrdnz.magical.network.SetIncantationPayload`.
- **Units** are the core's: ticks, half-hearts, blocks per tick, degrees, blocks per tick squared.
- **Every hit funnels through `MagicDamageService.hurt(target, source, amount, skillId)`** with `damageSources().indirectMagic(body, caster)` and the recite skill's id, so wards, passives and Tier Five attribution see it. Healing is `LivingEntity.heal`. Blood Toll's price is `BloodDamageTypes.price(player)`, the Blood school's true damage (§14 item 3 asks that the two systems name it once).
- **The server never writes a player's velocity** (the Space law lesson in `CLAUDE.md`). DISPLACE and a body that carries its caster move a player only through `SafeSpotSearch.place`; the Void Pit pulls mobs and bodies, never players.
- **Caps.** At most `ReciteCaps.MAX_BODIES` (64) bodies per spawn or release, twins included. An UNDYING body still ends at `VerseBodyEntity.UNDYING_CAP_TICKS` (1200). Payload nesting is bounded by the core's `MAX_DEPTH`; the codec refuses deeper.
- **The fan turns about the vertical axis** (yaw), in the plane a first-person caster reads; Column keeps its name from the spec but is one body ahead, one left, one right. Below 180 the fan is inclusive `-p..+p` in N-1 steps; at 180 and above it is the full circle exclusive starting at 0. Spread is a uniform deviation within ±spread/2 in yaw and in pitch, per body.
- **A body ends one way and releases the payload of that way:** a hit ends it with its Latch, its fuse ends it with its Fuse, its expiry ends it with its Epitaph. Every end fires the body's explosion (if it has one) and carries the caster (if the prototype does). Naught is an expiry on the first tick. A bounce is not an end.
- **Damage** is `prototype.damage + damageAdd` (0 under Blunt, never negative), a crit multiplies it by `CRIT_MULTIPLIER` (5, Noita's). Hit effects are the prototype's own (`hit()`, or `pulse()` for a static) followed by the stamped `hitEffects()`, each applied once, in that order (§14 item 5's first hand-off).
- **No new art.** Every look is an existing `FxKinds` kind drawn by an existing painter (`OrbPainter.billboard`, `FilamentPainter.beam`, `MarkPainter.mark`); colour is the school's `SchoolMaterial` ramp. No vanilla projectile or particle stands for a body.
- **Sync is the state blob.** The Grimoire rides in `PlayerMagicState.save()` under `"grimoire"`, so `PlayerMagicStatePayload` carries it and no new play-to-client payload exists. Sessions are never saved.
- **Tests** use `org.junit.jupiter.api.Assertions` static imports, four-space indent, a Javadoc thesis on the class, names that state the rule. Gametests: `@GameTestHolder(MagicalMod.MODID)`, `@PrefixGameTestTemplate(false)`, template `"unwaking_empty"`, one batch per test, a fake player named `*-test` made by `gametest/GameTestPlayers.survival` (Task 5 lifts it out of `EldritchGameTests.eldritchMage`, which stays as it is; a fake player never receives a `PlayerTickEvent`, so cooldowns it sets never tick down), everything a test relies on inside the template's five blocks of air (relative 0..4 on every axis, the stand at (2,2,2) and the victim at (4,2,2) as `EldritchGameTests` has them; the runner wraps the template in a barrier shell), victims are husks (zombies burn in the test world's daylight).
- **Line numbers in this plan are as of `cb3e3a5`;** anchor every edit on the quoted text, not the number.
- **Commit messages** are `<type>: <description>` in the repo's voice and end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Never `git add` `gradlew` or `.claude/settings.local.json`. Run `.\gradlew test` (or the single class named in the step) before every commit; `.\gradlew runGameTestServer` where a task adds gametests.

## File map

| File | Responsibility |
|---|---|
| `entity/verse/VerseFan.java` | The fan: yaws from a pattern and a count; a deviation from a spread and a roll. Pure. |
| `entity/verse/ShotPlanCodec.java` | `ShotPlan`/`ProjectilePlan`/`ShotState` to NBT and back |
| `entity/verse/VerseHitEffects.java` | The effect list of a body, and what each `HitEffect` does to a living thing |
| `entity/verse/VerseBehaviours.java` | The steering arithmetic (gravity, Seeker, Errant, Serpentine, Gyre, bounce, twin) and the behaviour bitmask. Pure. |
| `entity/verse/VerseBodyEntity.java` | The one entity: synced picture, server plan, flight, standing, hits, explosions, pulses, wakes, ends, payload release, counters, NBT |
| `entity/verse/VerseBodySpawner.java` | `spawn` from a hand, `release` from a body, `relay` from an impact |
| `registry/MagicalEntities.java` | `+ VERSE_BODY` |
| `client/renderer/verse/VerseLooks.java` | Look → painter kind and size; behaviour → glyph; wake → filament and colour. Pure table. |
| `client/renderer/verse/VerseBodyRenderer.java` | One renderer, a function of the synced state |
| `client/MagicalClientEvents.java` | `registerRenderers` `+1` |
| `magic/incantation/LevelReciteWorld.java` | `ReciteWorld` answered over the level and the Grimoire |
| `magic/incantation/IncantationService.java` | Sessions per wielder, `recite`, `setIncantation`, `preview`, `forget`, `parseIds`, `skillFor` |
| `magic/PlayerMagicState.java` | The `grimoire` slot: field, accessor, save, load, copy, clear |
| `magic/MagicGameplayEvents.java` | Sessions dropped on logout, respawn and dimension change |
| `magic/MagicContent.java`, `magic/AuthorityContent.java`, `magic/cast/MagicCastContentKept.java` | The four skills, granted by the Authority of Mana, each a self-managed press |
| `client/hud/HudGlyphs.java` | Four stamps |
| `network/SetIncantationPayload.java`, `network/MagicalNetwork.java` | The edit payload, its handler and its sender |
| `registry/MagicalCommands.java` | `/magical incantation set|know|show|preview`, `magical-debug recite` |
| `client/screen/MagicPyramidScreen.java` | The authority row reads the held Authority's name |
| `assets/magical/lang/en_us.json` | Skills, verses, messages, the authority's new description |
| `CLAUDE.md` | The capture line and the commands |
| tests | `VerseFanTest`, `ShotPlanCodecTest`, `VerseHitEffectsTest`, `VerseBehavioursTest`, `VerseLooksTest`, `PlayerMagicStateGrimoireTest`, `IncantationServiceTest`, `VerseLangKeysTest`; gametests `VerseBodyGameTests`, `IncantationGameTests` |
| removed (Task 12) | `magic/mana/*`, `ManaLedgerTest`, `client/ManaAuthorityInput`, `client/WritOverlay`, `network/ApplyWritPayload`, and every line §11 lists |

---

## Phase A: the body

### Task 1: The fan

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/entity/verse/VerseFan.java`
- Test: `src/test/java/com/efkrdnz/magical/entity/verse/VerseFanTest.java`

**Interfaces:**
- Produces: `VerseFan.yaws(int count, double patternDegrees) -> double[]` (degrees of yaw off the aim, one per body); `VerseFan.deviation(double spreadDegrees, double roll01) -> double` (degrees, in ±spread/2); `VerseFan.FULL_CIRCLE = 180.0`. Task 5's spawner reads both.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.entity.verse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Where each body of one shot points, as §9 of the design lays it down: below 180 the fan is
 * inclusive, -p..+p in N-1 steps; at 180 it is the full circle exclusive; one body points straight.
 * Spread is a deviation within half the spread either way, decided by a roll the spawner supplies.
 */
class VerseFanTest {

    private static final double EPS = 1.0E-9D;

    @Test
    void columnIsMinusNinetyZeroAndPlusNinety() {
        assertArrayEquals(new double[] {-90.0D, 0.0D, 90.0D}, VerseFan.yaws(3, 90.0D), EPS);
    }

    @Test
    void cleftIsMinusAndPlusFortyFive() {
        assertArrayEquals(new double[] {-45.0D, 45.0D}, VerseFan.yaws(2, 45.0D), EPS);
    }

    @Test
    void tridentIsMinusTwentyZeroAndPlusTwenty() {
        assertArrayEquals(new double[] {-20.0D, 0.0D, 20.0D}, VerseFan.yaws(3, 20.0D), EPS);
    }

    @Test
    void mirrorIsAheadAndBehind() {
        assertArrayEquals(new double[] {0.0D, 180.0D}, VerseFan.yaws(2, 180.0D), EPS);
    }

    @Test
    void hexadIsEverySixtyDegreesStartingAhead() {
        assertArrayEquals(new double[] {0.0D, 60.0D, 120.0D, 180.0D, 240.0D, 300.0D}, VerseFan.yaws(6, 180.0D), EPS);
    }

    @Test
    void pentacleIsEverySeventyTwo() {
        assertArrayEquals(new double[] {0.0D, 72.0D, 144.0D, 216.0D, 288.0D}, VerseFan.yaws(5, 180.0D), EPS);
    }

    @Test
    void oneBodyOrNoPatternPointsStraight() {
        assertArrayEquals(new double[] {0.0D}, VerseFan.yaws(1, 45.0D), EPS);
        assertArrayEquals(new double[] {0.0D, 0.0D, 0.0D, 0.0D}, VerseFan.yaws(4, 0.0D), EPS);
        assertArrayEquals(new double[0], VerseFan.yaws(0, 45.0D), EPS);
    }

    @Test
    void deviationSpansHalfTheSpreadEitherWayAndNothingWithoutASpread() {
        assertEquals(-10.0D, VerseFan.deviation(20.0D, 0.0D), EPS);
        assertEquals(10.0D, VerseFan.deviation(20.0D, 1.0D), EPS);
        assertEquals(0.0D, VerseFan.deviation(20.0D, 0.5D), EPS);
        assertEquals(0.0D, VerseFan.deviation(0.0D, 0.9D), EPS);
        assertEquals(0.0D, VerseFan.deviation(-8.0D, 0.9D), EPS);
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.entity.verse.VerseFanTest"`
Expected: compilation failure, `VerseFan` does not exist.

- [ ] **Step 3: Write the fan**

```java
package com.efkrdnz.magical.entity.verse;

/**
 * Where each body of one shot points, in degrees of yaw off the aim, as §9 of the design lays it
 * down: below 180 the fan is inclusive, {@code -p..+p} in N-1 steps, so Column (90, three bodies)
 * is -90, 0, +90; at 180 and above it is the full circle exclusive starting ahead, so Mirror (180,
 * two) is 0 and 180 and Hexad (180, six) is every 60. A lone body, or no pattern, points straight.
 * The fan turns about the vertical axis, the plane a first-person caster reads. Pure, so a test
 * can hold every row of the table without a level.
 */
public final class VerseFan {

    /** At this pattern and above the fan is the whole circle rather than an arc. */
    public static final double FULL_CIRCLE = 180.0D;

    private VerseFan() {
    }

    public static double[] yaws(int count, double patternDegrees) {
        if (count <= 0) {
            return new double[0];
        }
        double[] yaws = new double[count];
        if (count == 1 || patternDegrees <= 0.0D) {
            return yaws;
        }
        if (patternDegrees >= FULL_CIRCLE) {
            double step = 360.0D / count;
            for (int i = 0; i < count; i++) {
                yaws[i] = i * step;
            }
            return yaws;
        }
        double step = 2.0D * patternDegrees / (count - 1);
        for (int i = 0; i < count; i++) {
            yaws[i] = -patternDegrees + i * step;
        }
        return yaws;
    }

    /** A deviation within half the spread either way, from a 0..1 roll the caller supplies, so the random stays outside. */
    public static double deviation(double spreadDegrees, double roll01) {
        if (spreadDegrees <= 0.0D) {
            return 0.0D;
        }
        return (roll01 - 0.5D) * spreadDegrees;
    }
}
```

- [ ] **Step 4: Run the test**

Run: `.\gradlew test --tests "com.efkrdnz.magical.entity.verse.VerseFanTest"`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/entity/verse/VerseFan.java src/test/java/com/efkrdnz/magical/entity/verse/VerseFanTest.java
git commit -m "feat: the fan, where each body of a shot points"
```

---

### Task 2: The shot plan codec

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/entity/verse/ShotPlanCodec.java`
- Test: `src/test/java/com/efkrdnz/magical/entity/verse/ShotPlanCodecTest.java`

**Interfaces:**
- Consumes: `ShotState` getters `beatTicks, speedMultiplier, gravity, bounces, lifetimeAddTicks, spreadDegrees, patternDegrees, damageAdd, healingAdd, explosionRadius, explosionDamageAdd, critChance, knockback, nullsDamage, school, behaviours, hitEffects, wakes, wakeAmount, friendlyFire, recoil, screenshake, lightLevel, drawManyCount` and mutators `setBeat, multiplySpeed, addGravity, addBounces, addLifetime, addSpread, setPattern, addDamage, addHealing, addExplosionRadius, addExplosionDamage, addCrit, addKnockback, nullDamage, setSchool, behaviour, hitEffect, wake(Wake, int), allowFriendlyFire, addRecoil, addScreenshake, light, setDrawManyCount`; `VersePrototypes.byId(ResourceLocation)`; `ReciteCaps.MAX_DEPTH`, `MAX_BODIES`.
- Produces: `ShotPlanCodec.save(ShotPlan) -> CompoundTag`, `load(CompoundTag) -> ShotPlan` (never null; empty tag → empty plan), `saveBody(ProjectilePlan) -> CompoundTag`, `loadBody(CompoundTag) -> ProjectilePlan` (null when the prototype is unknown), package-private `saveState(ShotState)` / `loadState(CompoundTag)`. Task 5's entity saves its plan through `saveBody`/`loadBody`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.entity.verse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.PayloadKind;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ShotPlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VerseIds;
import com.efkrdnz.magical.magic.incantation.VersePrototypes;
import com.efkrdnz.magical.magic.incantation.Wake;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * A body in an unloaded chunk keeps its payload: every number of a shot state, every body of a plan
 * and every payload beneath it come back from NBT as they went in, and a prototype the table no
 * longer holds is dropped rather than poisoning the plan.
 */
class ShotPlanCodecTest {

    private static final double EPS = 1.0E-12D;

    private static ShotState everything() {
        ShotState s = new ShotState();
        s.setBeat(7);
        s.multiplySpeed(2.5D);
        s.addGravity(0.04D);
        s.addBounces(3);
        s.addLifetime(25);
        s.addSpread(12.0D);
        s.setPattern(45.0D);
        s.addDamage(2.5D);
        s.addHealing(1.0D);
        s.addExplosionRadius(1.5D);
        s.addExplosionDamage(1.5D);
        s.addCrit(15.0D);
        s.addKnockback(1.0D);
        s.setSchool(MagicSchool.FIRE);
        s.behaviour(Behaviour.SEEKER);
        s.behaviour(Behaviour.PUNCTURE);
        s.hitEffect(HitEffect.BURN);
        s.hitEffect(HitEffect.FREEZE);
        s.wake(Wake.FIRE, 5);
        s.wake(Wake.FROST, 5);
        s.allowFriendlyFire();
        s.addRecoil(10.0D);
        s.addScreenshake(1.0D);
        s.light(12);
        s.setDrawManyCount(2);
        return s;
    }

    @Test
    void everyNumberOfTheStateSurvivesTheRoundTrip() {
        ShotState back = ShotPlanCodec.loadState(ShotPlanCodec.saveState(everything()));
        assertEquals(7, back.beatTicks());
        assertEquals(2.5D, back.speedMultiplier(), EPS);
        assertEquals(0.04D, back.gravity(), EPS);
        assertEquals(3, back.bounces());
        assertEquals(25, back.lifetimeAddTicks());
        assertEquals(12.0D, back.spreadDegrees(), EPS);
        assertEquals(45.0D, back.patternDegrees(), EPS);
        assertEquals(2.5D, back.damageAdd(), EPS);
        assertEquals(1.0D, back.healingAdd(), EPS);
        assertEquals(1.5D, back.explosionRadius(), EPS);
        assertEquals(1.5D, back.explosionDamageAdd(), EPS);
        assertEquals(15.0D, back.critChance(), EPS);
        assertEquals(1.0D, back.knockback(), EPS);
        assertFalse(back.nullsDamage());
        assertEquals(MagicSchool.FIRE, back.school());
        assertEquals(List.of(Behaviour.SEEKER, Behaviour.PUNCTURE), back.behaviours());
        assertEquals(List.of(HitEffect.BURN, HitEffect.FREEZE), back.hitEffects());
        assertEquals(List.of(Wake.FIRE, Wake.FROST), back.wakes());
        assertEquals(10, back.wakeAmount());
        assertTrue(back.friendlyFire());
        assertEquals(10.0D, back.recoil(), EPS);
        assertEquals(1.0D, back.screenshake(), EPS);
        assertEquals(12, back.lightLevel());
        assertEquals(2, back.drawManyCount());
    }

    @Test
    void aBluntStateStaysBluntAndAnUnschooledOneStaysUnschooled() {
        ShotState s = new ShotState();
        s.nullDamage();
        ShotState back = ShotPlanCodec.loadState(ShotPlanCodec.saveState(s));
        assertTrue(back.nullsDamage());
        assertEquals(null, back.school());
        assertEquals(1.0D, back.speedMultiplier(), EPS);
    }

    @Test
    void aBodyKeepsItsPrototypeVerseKindFuseAndPayload() {
        ShotState inner = new ShotState();
        inner.addDamage(1.0D);
        ProjectilePlan child = new ProjectilePlan(VersePrototypes.NEEDLE, VerseIds.of("needle"), inner, PayloadKind.NONE, 0, null);
        ShotPlan payload = new ShotPlan(List.of(child), new ShotState());
        ProjectilePlan body = new ProjectilePlan(VersePrototypes.ORB, VerseIds.of("orb_fuse"), everything(), PayloadKind.FUSE, 8, payload);
        ShotState group = new ShotState();
        group.setPattern(45.0D);
        group.addSpread(-8.0D);
        ShotPlan plan = new ShotPlan(List.of(body), group);

        ShotPlan back = ShotPlanCodec.load(ShotPlanCodec.save(plan));

        assertEquals(1, back.bodies().size());
        ProjectilePlan b = back.bodies().get(0);
        assertSame(VersePrototypes.ORB, b.prototype());
        assertEquals(VerseIds.of("orb_fuse"), b.verse());
        assertEquals(PayloadKind.FUSE, b.payloadKind());
        assertEquals(8, b.fuseTicks());
        assertEquals(2.5D, b.stamped().damageAdd(), EPS);
        assertTrue(b.hasPayload());
        assertSame(VersePrototypes.NEEDLE, b.payload().bodies().get(0).prototype());
        assertEquals(1.0D, b.payload().bodies().get(0).stamped().damageAdd(), EPS);
        assertFalse(b.payload().bodies().get(0).hasPayload());
        assertEquals(2, back.countAll());
        assertEquals(45.0D, back.state().patternDegrees(), EPS);
        assertEquals(-8.0D, back.state().spreadDegrees(), EPS);
    }

    @Test
    void anUnknownPrototypeIsDroppedRatherThanPoisoningThePlan() {
        ProjectilePlan body = new ProjectilePlan(VersePrototypes.NEEDLE, VerseIds.of("needle"), new ShotState(), PayloadKind.NONE, 0, null);
        CompoundTag tag = ShotPlanCodec.save(new ShotPlan(List.of(body), new ShotState()));
        tag.getList("bodies", Tag.TAG_COMPOUND).getCompound(0).putString("prototype", "magical:body/nothing");
        assertTrue(ShotPlanCodec.load(tag).bodies().isEmpty());
        assertEquals(null, ShotPlanCodec.loadBody(tag.getList("bodies", Tag.TAG_COMPOUND).getCompound(0)));
    }

    @Test
    void anEmptyTagIsAnEmptyPlan() {
        ShotPlan back = ShotPlanCodec.load(new CompoundTag());
        assertTrue(back.bodies().isEmpty());
        assertEquals(1.0D, back.state().speedMultiplier(), EPS);
        assertEquals(0, back.state().beatTicks());
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.entity.verse.ShotPlanCodecTest"`
Expected: compilation failure, `ShotPlanCodec` does not exist.

- [ ] **Step 3: Write the codec**

```java
package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.PayloadKind;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.ShotPlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.VersePrototypes;
import com.efkrdnz.magical.magic.incantation.Wake;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A shot plan to NBT and back, so a body in an unloaded chunk keeps its payload. The state is
 * rebuilt through the same mutators the verses use, so a number the state clamps on the way in is
 * clamped the same way on the way back; a body whose prototype the table no longer holds is dropped,
 * as {@code ReciteSession.of} drops a verse the catalogue no longer holds; a payload deeper than
 * {@link ReciteCaps#MAX_DEPTH} is refused, because nothing the Reciter made can be that deep.
 */
public final class ShotPlanCodec {

    private ShotPlanCodec() {
    }

    public static CompoundTag save(ShotPlan plan) {
        CompoundTag tag = new CompoundTag();
        tag.put("state", saveState(plan.state()));
        ListTag bodies = new ListTag();
        for (ProjectilePlan body : plan.bodies()) {
            bodies.add(saveBody(body));
        }
        tag.put("bodies", bodies);
        return tag;
    }

    /** Never null: an empty or unreadable tag is an empty plan. */
    public static ShotPlan load(CompoundTag tag) {
        return load(tag, 0);
    }

    private static ShotPlan load(CompoundTag tag, int depth) {
        if (tag == null || depth > ReciteCaps.MAX_DEPTH) {
            return new ShotPlan(List.of(), new ShotState());
        }
        List<ProjectilePlan> bodies = new ArrayList<>();
        ListTag list = tag.getList("bodies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && bodies.size() < ReciteCaps.MAX_BODIES; i++) {
            ProjectilePlan body = loadBody(list.getCompound(i), depth);
            if (body != null) {
                bodies.add(body);
            }
        }
        return new ShotPlan(bodies, loadState(tag.getCompound("state")));
    }

    public static CompoundTag saveBody(ProjectilePlan body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("prototype", body.prototype().id().toString());
        tag.putString("verse", body.verse().toString());
        tag.put("stamped", saveState(body.stamped()));
        tag.putInt("payloadKind", body.payloadKind().ordinal());
        tag.putInt("fuse", body.fuseTicks());
        if (body.hasPayload()) {
            tag.put("payload", save(body.payload()));
        }
        return tag;
    }

    /** Null when the prototype is unknown. */
    public static ProjectilePlan loadBody(CompoundTag tag) {
        return loadBody(tag, 0);
    }

    private static ProjectilePlan loadBody(CompoundTag tag, int depth) {
        VersePrototype prototype = VersePrototypes.byId(ResourceLocation.tryParse(tag.getString("prototype")));
        if (prototype == null) {
            return null;
        }
        ResourceLocation verse = tag.contains("verse", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("verse")) : null;
        PayloadKind kind = enumAt(PayloadKind.values(), tag.getInt("payloadKind"), PayloadKind.NONE);
        ShotPlan payload = tag.contains("payload", Tag.TAG_COMPOUND) ? load(tag.getCompound("payload"), depth + 1) : null;
        return new ProjectilePlan(prototype, verse == null ? prototype.id() : verse, loadState(tag.getCompound("stamped")),
                kind, tag.getInt("fuse"), payload);
    }

    static CompoundTag saveState(ShotState s) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("beat", s.beatTicks());
        tag.putDouble("speed", s.speedMultiplier());
        tag.putDouble("gravity", s.gravity());
        tag.putInt("bounces", s.bounces());
        tag.putInt("lifetime", s.lifetimeAddTicks());
        tag.putDouble("spread", s.spreadDegrees());
        tag.putDouble("pattern", s.patternDegrees());
        tag.putDouble("damage", s.damageAdd());
        tag.putDouble("healing", s.healingAdd());
        tag.putDouble("explosionRadius", s.explosionRadius());
        tag.putDouble("explosionDamage", s.explosionDamageAdd());
        tag.putDouble("crit", s.critChance());
        tag.putDouble("knockback", s.knockback());
        tag.putBoolean("blunt", s.nullsDamage());
        tag.putInt("school", s.school() == null ? -1 : s.school().ordinal());
        tag.putIntArray("behaviours", ordinals(s.behaviours()));
        tag.putIntArray("hitEffects", ordinals(s.hitEffects()));
        tag.putIntArray("wakes", ordinals(s.wakes()));
        tag.putInt("wakeAmount", s.wakeAmount());
        tag.putBoolean("friendlyFire", s.friendlyFire());
        tag.putDouble("recoil", s.recoil());
        tag.putDouble("screenshake", s.screenshake());
        tag.putInt("light", s.lightLevel());
        tag.putInt("drawMany", s.drawManyCount());
        return tag;
    }

    /** Through the mutators, so the state's own clamps hold on the way back. The wake amount rides on the first wake. */
    static ShotState loadState(CompoundTag tag) {
        ShotState s = new ShotState();
        if (tag == null) {
            return s;
        }
        s.setBeat(tag.getInt("beat"));
        s.multiplySpeed(tag.contains("speed", Tag.TAG_DOUBLE) ? tag.getDouble("speed") : 1.0D);
        s.addGravity(tag.getDouble("gravity"));
        s.addBounces(tag.getInt("bounces"));
        s.addLifetime(tag.getInt("lifetime"));
        s.addSpread(tag.getDouble("spread"));
        s.setPattern(tag.getDouble("pattern"));
        s.addDamage(tag.getDouble("damage"));
        s.addHealing(tag.getDouble("healing"));
        s.addExplosionRadius(tag.getDouble("explosionRadius"));
        s.addExplosionDamage(tag.getDouble("explosionDamage"));
        s.addCrit(tag.getDouble("crit"));
        s.addKnockback(tag.getDouble("knockback"));
        if (tag.getBoolean("blunt")) {
            s.nullDamage();
        }
        MagicSchool school = enumAt(MagicSchool.values(), tag.contains("school", Tag.TAG_INT) ? tag.getInt("school") : -1, null);
        if (school != null) {
            s.setSchool(school);
        }
        for (int ordinal : tag.getIntArray("behaviours")) {
            Behaviour behaviour = enumAt(Behaviour.values(), ordinal, null);
            if (behaviour != null) {
                s.behaviour(behaviour);
            }
        }
        for (int ordinal : tag.getIntArray("hitEffects")) {
            HitEffect effect = enumAt(HitEffect.values(), ordinal, null);
            if (effect != null) {
                s.hitEffect(effect);
            }
        }
        int[] wakes = tag.getIntArray("wakes");
        for (int i = 0; i < wakes.length; i++) {
            Wake wake = enumAt(Wake.values(), wakes[i], null);
            if (wake != null) {
                s.wake(wake, i == 0 ? tag.getInt("wakeAmount") : 0);
            }
        }
        if (tag.getBoolean("friendlyFire")) {
            s.allowFriendlyFire();
        }
        s.addRecoil(tag.getDouble("recoil"));
        s.addScreenshake(tag.getDouble("screenshake"));
        s.light(tag.getInt("light"));
        s.setDrawManyCount(tag.getInt("drawMany"));
        return s;
    }

    private static int[] ordinals(List<? extends Enum<?>> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i).ordinal();
        }
        return out;
    }

    private static <E extends Enum<E>> E enumAt(E[] values, int ordinal, E fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
```

- [ ] **Step 4: Run the test**

Run: `.\gradlew test --tests "com.efkrdnz.magical.entity.verse.ShotPlanCodecTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/entity/verse/ShotPlanCodec.java src/test/java/com/efkrdnz/magical/entity/verse/ShotPlanCodecTest.java
git commit -m "feat: a shot plan to NBT and back, so a body keeps its payload"
```

---

### Task 3: The hit effects

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/entity/verse/VerseHitEffects.java`
- Test: `src/test/java/com/efkrdnz/magical/entity/verse/VerseHitEffectsTest.java`

**Interfaces:**
- Consumes: `ProjectilePlan.prototype().hit()` / `.pulse()` (nullable `HitEffect`), `ProjectilePlan.stamped().hitEffects()`; `SkillTargets.shove(LivingEntity target, Vec3 from, double strength, double up)`; `SafeSpotSearch.standableNear(Level, Vec3 wanted, int up, int down, float width, float height)` and `SafeSpotSearch.place(LivingEntity, Vec3 feet, float yaw, float pitch, boolean keepVelocity)`.
- Produces: `VerseHitEffects.effectsOf(ProjectilePlan) -> List<HitEffect>` (the prototype's `hit()` first, then the stamped ones, each once, in order); `pulseEffectsOf(ProjectilePlan)` (the same with `pulse()` first); `apply(HitEffect, LivingEntity target, Entity source, Vec3 from, RandomSource random)`. Task 5's entity calls all three.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.entity.verse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.PayloadKind;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.VersePrototypes;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The core review's first hand-off: a body applies its prototype's own effect (Ember's BURN, Arc
 * Bolt's SHOCK, a ring's pulse) as well as what a Wreath, Uplift or Displace stamped on it, the
 * body's own first, each once, in the order they were written.
 */
class VerseHitEffectsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ProjectilePlan body(VersePrototype prototype, HitEffect... stamped) {
        ShotState state = new ShotState();
        for (HitEffect effect : stamped) {
            state.hitEffect(effect);
        }
        return new ProjectilePlan(prototype, prototype.id(), state, PayloadKind.NONE, 0, null);
    }

    @Test
    void theBodysOwnEffectComesFirstAndTheStampedOnesFollow() {
        assertEquals(List.of(HitEffect.BURN, HitEffect.FREEZE, HitEffect.UPLIFT),
                VerseHitEffects.effectsOf(body(VersePrototypes.EMBER, HitEffect.FREEZE, HitEffect.UPLIFT)));
        assertEquals(List.of(HitEffect.SHOCK), VerseHitEffects.effectsOf(body(VersePrototypes.ARC)));
    }

    @Test
    void anEffectStampedTwiceOrAlreadyTheBodysOwnIsAppliedOnce() {
        assertEquals(List.of(HitEffect.BURN, HitEffect.WITHER),
                VerseHitEffects.effectsOf(body(VersePrototypes.EMBER, HitEffect.BURN, HitEffect.WITHER, HitEffect.WITHER)));
    }

    @Test
    void aBodyWithNothingWrittenOnItHasNoEffect() {
        assertTrue(VerseHitEffects.effectsOf(body(VersePrototypes.NEEDLE)).isEmpty());
        assertTrue(VerseHitEffects.pulseEffectsOf(body(VersePrototypes.NEEDLE)).isEmpty());
    }

    @Test
    void aStaticPulsesItsOwnEffectThenTheStampedOnes() {
        assertEquals(List.of(HitEffect.FREEZE, HitEffect.SHOCK),
                VerseHitEffects.pulseEffectsOf(body(VersePrototypes.RING_RIME, HitEffect.SHOCK)));
        assertEquals(List.of(HitEffect.UPLIFT), VerseHitEffects.pulseEffectsOf(body(VersePrototypes.RING_UPLIFT)));
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.entity.verse.VerseHitEffectsTest"`
Expected: compilation failure, `VerseHitEffects` does not exist.

- [ ] **Step 3: Write the effects**

```java
package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
import com.efkrdnz.magical.magic.service.SkillTargets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What a body does to what it hits beyond damage: the port of {@code game_effect_entities}. The
 * list is the body's own effect first (Ember's BURN, Arc Bolt's SHOCK, a ring's pulse) and then
 * what a Wreath, Uplift or Displace stamped on it, each once, in the order written; the spawner
 * applies both, or those bodies silently lose their effect.
 */
public final class VerseHitEffects {

    public static final float BURN_SECONDS = 4.0F;
    public static final int FREEZE_TICKS = 60;
    public static final int SHOCK_TICKS = 10;
    public static final int WITHER_TICKS = 80;
    public static final int UPLIFT_TICKS = 30;
    public static final double SHOCK_SHOVE = 0.45D;
    public static final double DISPLACE_BLOCKS = 4.0D;

    private VerseHitEffects() {
    }

    /** On a hit: the prototype's {@code hit()} first, then the stamped effects, each once. */
    public static List<HitEffect> effectsOf(ProjectilePlan body) {
        return ordered(body.prototype().hit(), body);
    }

    /** On a pulse: the prototype's {@code pulse()} first, then the stamped effects, each once. */
    public static List<HitEffect> pulseEffectsOf(ProjectilePlan body) {
        return ordered(body.prototype().pulse(), body);
    }

    private static List<HitEffect> ordered(HitEffect own, ProjectilePlan body) {
        Set<HitEffect> effects = new LinkedHashSet<>();
        if (own != null) {
            effects.add(own);
        }
        effects.addAll(body.stamped().hitEffects());
        return List.copyOf(effects);
    }

    /**
     * BURN sets fire, FREEZE applies slowness and puts a fire out, SHOCK is a short stun through
     * knockback and a heavy slow, WITHER the wither effect, UPLIFT levitation, DISPLACE moves the
     * target a short random distance to standable ground, through the one placement path the mod
     * has (so a player is moved by the teleport packet and nothing else).
     */
    public static void apply(HitEffect effect, LivingEntity target, Entity source, Vec3 from, RandomSource random) {
        switch (effect) {
            case BURN -> target.igniteForSeconds(BURN_SECONDS);
            case FREEZE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_TICKS, 1, false, true), source);
                target.clearFire();
            }
            case SHOCK -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SHOCK_TICKS, 4, false, true), source);
                SkillTargets.shove(target, from, SHOCK_SHOVE, 0.12D);
            }
            case WITHER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0, false, true), source);
            case UPLIFT -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, UPLIFT_TICKS, 0, false, true), source);
            case DISPLACE -> displace(target, random);
        }
    }

    private static void displace(LivingEntity target, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        Vec3 wanted = target.position().add(Math.cos(angle) * DISPLACE_BLOCKS, 0.0D, Math.sin(angle) * DISPLACE_BLOCKS);
        Vec3 feet = SafeSpotSearch.standableNear(target.level(), wanted, 2, 3, target.getBbWidth(), target.getBbHeight());
        if (feet != null) {
            SafeSpotSearch.place(target, feet, target.getYRot(), target.getXRot(), false);
        }
    }
}
```

- [ ] **Step 4: Run the test**

Run: `.\gradlew test --tests "com.efkrdnz.magical.entity.verse.VerseHitEffectsTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/entity/verse/VerseHitEffects.java src/test/java/com/efkrdnz/magical/entity/verse/VerseHitEffectsTest.java
git commit -m "feat: what a body does to what it hits, its own effect first"
```

---

### Task 4: The steering arithmetic

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/entity/verse/VerseBehaviours.java`
- Test: `src/test/java/com/efkrdnz/magical/entity/verse/VerseBehavioursTest.java`

**Interfaces:**
- Produces (all pure, `net.minecraft.world.phys.Vec3` in and out): `steer(Vec3 velocity, double gravity, Vec3 toTarget, boolean seeker, boolean errant, int tick, int seed) -> Vec3`; `roll(int seed, int tick, int salt) -> double` in -1..1; `serpentineOffset(Vec3 velocity, int tick) -> Vec3`; `sideOf(Vec3 velocity) -> Vec3`; `gyreOffset(Vec3 launch, int tick) -> Vec3`; `bounce(Vec3 velocity, Vec3 normal) -> Vec3`; `twin(Vec3 direction, boolean left) -> Vec3`; `mask(Collection<Behaviour>) -> int`; `has(int mask, Behaviour) -> boolean`; the constants named in the code. Task 5's entity and spawner call them.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.entity.verse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.Behaviour;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The flight arithmetic behind the behaviours, held without a level: gravity bends the flight,
 * a Seeker turns toward its target without changing speed, Errant turns only on its interval and
 * always the same way for the same seed, Serpentine rides beside the line and comes back to it,
 * a Gyre keeps its distance and grows it, a bounce reflects, twins part symmetrically, and every
 * behaviour fits the bitmask the entity syncs.
 */
class VerseBehavioursTest {

    private static final double EPS = 1.0E-9D;
    private static final Vec3 AHEAD = new Vec3(0.0D, 0.0D, 1.0D);

    @Test
    void gravityTakesFromTheHeightAndNothingElse() {
        Vec3 v = VerseBehaviours.steer(AHEAD, 0.04D, null, false, false, 1, 0);
        assertEquals(0.0D, v.x, EPS);
        assertEquals(-0.04D, v.y, EPS);
        assertEquals(1.0D, v.z, EPS);
    }

    @Test
    void aSeekerTurnsTowardItsTargetAndKeepsItsSpeed() {
        Vec3 v = VerseBehaviours.steer(AHEAD.scale(1.6D), 0.0D, new Vec3(5.0D, 0.0D, 0.0D), true, false, 1, 0);
        assertTrue(v.x > 0.0D, "it turned toward +x: " + v);
        assertTrue(v.z > 0.0D, "and still mostly flies ahead: " + v);
        assertEquals(1.6D, v.length(), 1.0E-6D);
    }

    @Test
    void errantTurnsOnItsIntervalOnlyAndIsDecidedByTheSeed() {
        Vec3 still = VerseBehaviours.steer(AHEAD, 0.0D, null, false, true, VerseBehaviours.ERRANT_INTERVAL - 1, 7);
        assertEquals(0.0D, AHEAD.distanceTo(still), EPS);
        Vec3 turned = VerseBehaviours.steer(AHEAD, 0.0D, null, false, true, VerseBehaviours.ERRANT_INTERVAL, 7);
        assertNotEquals(0.0D, AHEAD.distanceTo(turned), "it turns on the interval");
        assertEquals(1.0D, turned.length(), 1.0E-6D);
        Vec3 again = VerseBehaviours.steer(AHEAD, 0.0D, null, false, true, VerseBehaviours.ERRANT_INTERVAL, 7);
        assertEquals(0.0D, turned.distanceTo(again), EPS);
        Vec3 other = VerseBehaviours.steer(AHEAD, 0.0D, null, false, true, VerseBehaviours.ERRANT_INTERVAL, 8);
        assertNotEquals(0.0D, turned.distanceTo(other), "another seed turns another way");
    }

    @Test
    void aRollIsWithinOneEitherWayAndRepeatable() {
        for (int tick = 0; tick < 200; tick++) {
            double roll = VerseBehaviours.roll(3, tick, 1);
            assertTrue(roll >= -1.0D && roll <= 1.0D, "roll " + roll);
            assertEquals(roll, VerseBehaviours.roll(3, tick, 1), EPS);
        }
    }

    @Test
    void serpentineRidesBesideTheLineAndReturnsToItEveryPeriod() {
        Vec3 sum = Vec3.ZERO;
        for (int tick = 1; tick <= (int) VerseBehaviours.SERPENTINE_PERIOD_TICKS; tick++) {
            Vec3 offset = VerseBehaviours.serpentineOffset(AHEAD, tick);
            assertEquals(0.0D, offset.dot(AHEAD), EPS, "the offset is beside the line");
            sum = sum.add(offset);
        }
        assertEquals(0.0D, sum.length(), 1.0E-6D, "a whole period nets nothing");
        assertNotEquals(0.0D, VerseBehaviours.serpentineOffset(AHEAD, 2).length(), "and it does move");
    }

    @Test
    void aGyreKeepsItsDistanceAndWidensIt() {
        Vec3 first = VerseBehaviours.gyreOffset(AHEAD, 0);
        Vec3 later = VerseBehaviours.gyreOffset(AHEAD, 10);
        assertEquals(VerseBehaviours.GYRE_RADIUS, first.horizontalDistance(), EPS);
        assertEquals(VerseBehaviours.GYRE_RADIUS + 10 * VerseBehaviours.GYRE_CLIMB, later.horizontalDistance(), EPS);
        assertNotEquals(0.0D, first.subtract(later).horizontalDistance(), "it has moved round");
    }

    @Test
    void aBounceReflectsOffTheFaceAndKeepsTheSpeed() {
        Vec3 v = VerseBehaviours.bounce(new Vec3(1.0D, -1.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D));
        assertEquals(1.0D, v.x, EPS);
        assertEquals(1.0D, v.y, EPS);
        assertEquals(0.0D, v.z, EPS);
    }

    @Test
    void twinsPartSymmetricallyAndStayUnit() {
        Vec3 left = VerseBehaviours.twin(AHEAD, true);
        Vec3 right = VerseBehaviours.twin(AHEAD, false);
        assertEquals(1.0D, left.length(), 1.0E-6D);
        assertEquals(-left.x, right.x, EPS);
        assertEquals(left.z, right.z, EPS);
        assertNotEquals(0.0D, left.x, "they do part");
    }

    @Test
    void everyBehaviourFitsTheMaskAndComesBackOut() {
        int mask = VerseBehaviours.mask(List.of(Behaviour.values()));
        for (Behaviour behaviour : Behaviour.values()) {
            assertTrue(VerseBehaviours.has(mask, behaviour), behaviour + " is in the mask");
        }
        int two = VerseBehaviours.mask(EnumSet.of(Behaviour.SEEKER, Behaviour.NAUGHT));
        assertTrue(VerseBehaviours.has(two, Behaviour.SEEKER));
        assertTrue(VerseBehaviours.has(two, Behaviour.NAUGHT));
        assertFalse(VerseBehaviours.has(two, Behaviour.PUNCTURE));
        assertTrue(Behaviour.values().length <= 31, "the mask is an int");
    }

    @Test
    void theSideOfAVerticalFlightIsStillASide() {
        Vec3 side = VerseBehaviours.sideOf(new Vec3(0.0D, 1.0D, 0.0D));
        assertEquals(1.0D, side.length(), 1.0E-6D);
        assertEquals(0.0D, side.y, EPS);
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.entity.verse.VerseBehavioursTest"`
Expected: compilation failure, `VerseBehaviours` does not exist.

- [ ] **Step 3: Write the arithmetic**

```java
package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.incantation.Behaviour;
import java.util.Collection;
import net.minecraft.world.phys.Vec3;

/**
 * The port of {@code extra_entities}, as arithmetic: what each behaviour does to a flight vector,
 * with no entity in it, so the rules can be held by a test and the entity only has to call them in
 * a fixed order. Anything random is a {@link #roll} of the body's seed and the tick, so every
 * client and the server agree on where an Errant body went.
 */
public final class VerseBehaviours {

    /** How far toward its target a Seeker turns each tick, as a fraction of the way. */
    public static final double SEEK_TURN = 0.22D;
    public static final double SERPENTINE_AMPLITUDE = 0.35D;
    public static final double SERPENTINE_PERIOD_TICKS = 10.0D;
    public static final int ERRANT_INTERVAL = 5;
    public static final double ERRANT_TURN_DEGREES = 35.0D;
    public static final double ERRANT_CLIMB = 0.3D;
    public static final double GYRE_RADIUS = 1.6D;
    /** Blocks of orbit radius a Gyre gains per tick. */
    public static final double GYRE_CLIMB = 0.10D;
    public static final double GYRE_TURN_RADIANS = 0.35D;
    public static final double GYRE_HEIGHT = 1.0D;
    public static final double TWIN_YAW_DEGREES = 12.0D;

    private VerseBehaviours() {
    }

    /**
     * Gravity, then the steering that keeps the speed: what the flight vector becomes this tick.
     * {@code toTarget} is the vector from the body to what a Seeker is after, or null.
     */
    public static Vec3 steer(Vec3 velocity, double gravity, Vec3 toTarget, boolean seeker, boolean errant, int tick, int seed) {
        Vec3 v = velocity.add(0.0D, -gravity, 0.0D);
        double speed = v.length();
        if (speed < 1.0E-6D) {
            return v;
        }
        if (seeker && toTarget != null && toTarget.lengthSqr() > 1.0E-6D) {
            Vec3 wanted = toTarget.normalize().scale(speed);
            v = v.add(wanted.subtract(v).scale(SEEK_TURN)).normalize().scale(speed);
        }
        if (errant && tick % ERRANT_INTERVAL == 0) {
            double yaw = Math.toRadians(roll(seed, tick, 1) * ERRANT_TURN_DEGREES);
            double climb = roll(seed, tick, 2) * ERRANT_CLIMB;
            v = v.yRot((float) yaw);
            v = new Vec3(v.x, v.y + climb * speed, v.z).normalize().scale(speed);
        }
        return v;
    }

    /** -1..1, decided by the seed, the tick and a salt, so it repeats exactly and never needs a level. */
    public static double roll(int seed, int tick, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + tick * 0xBF58476D1CE4E5B9L + salt * 0x94D049BB133111EBL;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (2.0D / (1L << 53)) - 1.0D;
    }

    /**
     * Serpentine: the change this tick of a lateral sine beside the flight line, so it is added to
     * the step rather than the velocity and the body comes back to its line every period.
     */
    public static Vec3 serpentineOffset(Vec3 velocity, int tick) {
        Vec3 side = sideOf(velocity);
        double now = Math.sin(tick * 2.0D * Math.PI / SERPENTINE_PERIOD_TICKS);
        double before = Math.sin((tick - 1) * 2.0D * Math.PI / SERPENTINE_PERIOD_TICKS);
        return side.scale((now - before) * SERPENTINE_AMPLITUDE);
    }

    /** A unit vector beside the flight; a vertical flight takes +x as its side. */
    public static Vec3 sideOf(Vec3 velocity) {
        if (velocity.lengthSqr() < 1.0E-9D) {
            return Vec3.ZERO;
        }
        Vec3 dir = velocity.normalize();
        Vec3 up = Math.abs(dir.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = dir.cross(up);
        return side.lengthSqr() < 1.0E-9D ? Vec3.ZERO : side.normalize();
    }

    /** Gyre: where a body orbiting its caster stands this tick, relative to the caster's feet, starting on the launch bearing. */
    public static Vec3 gyreOffset(Vec3 launch, int tick) {
        double angle = Math.atan2(launch.x, launch.z) + tick * GYRE_TURN_RADIANS;
        double radius = GYRE_RADIUS + tick * GYRE_CLIMB;
        return new Vec3(Math.sin(angle) * radius, GYRE_HEIGHT, Math.cos(angle) * radius);
    }

    /** A reflection off a face, keeping the speed. */
    public static Vec3 bounce(Vec3 velocity, Vec3 normal) {
        return velocity.subtract(normal.scale(2.0D * velocity.dot(normal)));
    }

    /**
     * A twin's heading: the body's, turned {@link #TWIN_YAW_DEGREES} either way about the vertical
     * axis, with the same convention as {@code Vec3.yRot} but in double precision: {@code yRot}
     * takes its sine and cosine from float tables, and the two twins would otherwise differ by a
     * few parts in a hundred thousand instead of mirroring each other.
     */
    public static Vec3 twin(Vec3 direction, boolean left) {
        double radians = Math.toRadians(left ? TWIN_YAW_DEGREES : -TWIN_YAW_DEGREES);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        Vec3 d = direction.normalize();
        return new Vec3(d.x * cos + d.z * sin, d.y, d.z * cos - d.x * sin);
    }

    public static int mask(Collection<Behaviour> behaviours) {
        int mask = 0;
        for (Behaviour behaviour : behaviours) {
            mask |= 1 << behaviour.ordinal();
        }
        return mask;
    }

    public static boolean has(int mask, Behaviour behaviour) {
        return (mask & (1 << behaviour.ordinal())) != 0;
    }
}
```

- [ ] **Step 4: Run the test**

Run: `.\gradlew test --tests "com.efkrdnz.magical.entity.verse.VerseBehavioursTest"`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/entity/verse/VerseBehaviours.java src/test/java/com/efkrdnz/magical/entity/verse/VerseBehavioursTest.java
git commit -m "feat: the steering arithmetic behind a body's behaviours"
```

---

### Task 5: The body and its spawner

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/entity/verse/VerseBodyEntity.java`
- Create: `src/main/java/com/efkrdnz/magical/entity/verse/VerseBodySpawner.java`
- Modify: `src/main/java/com/efkrdnz/magical/registry/MagicalEntities.java` (after `ELDRITCH_CONSTRUCT`, line 279)
- Create: `src/main/java/com/efkrdnz/magical/gametest/GameTestPlayers.java` (the fake-player factory, shared with Task 8's gametests)
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VerseBodyGameTests.java`

**Interfaces:**
- Consumes: Tasks 1–4; `MagicDamageService.hurt(LivingEntity, DamageSource, float, ResourceLocation)`; `MagicCounterService.offerCounter(ServerPlayer, CounterableSkillThreat, Vec3, int)`, `hasActivePrompt(ServerPlayer, CounterableSkillThreat)`, `expirePrompt(ServerPlayer, CounterableSkillThreat)`, `spawnClash(ServerLevel, Vec3, int, int)`; `SkillTargets.hostilesWithin(ServerLevel, Entity, Vec3, double)`, `shove(LivingEntity, Vec3, double, double)`; `SafeSpotSearch.standableNear/liftClear/place`; `SpellFx.impact(ServerLevel, MagicSkillDefinition, Vec3, Vec3, Entity, Entity, float)`; `AimResolver.resolve(ServerLevel, LivingEntity, Vec3, double, double, boolean, int, Predicate<Entity>)` → `.point()`, `AimResolver.groundBelow(ServerLevel, Vec3, int)`; `UnwakingCapabilities.refuseMovement(ServerPlayer)`; `SpellEntityVisibility`.
- Produces: `VerseBodyEntity.spawn(ServerLevel, LivingEntity caster, ProjectilePlan, Vec3 position, Vec3 direction, ResourceLocation skillId) -> VerseBodyEntity`; `ownedBy(ServerLevel, LivingEntity, AABB) -> List<VerseBodyEntity>`; the synced accessors a renderer may read on either side, `prototype(), school(), radius(), life(), behaviourMask(), wakeMask(), direction(), seed(), has(Behaviour)`; the server-only `plan(), skillId(), livingOwner()` (the plan is NBT and the owner a UUID lookup, so both are null on a client); `markRelayed()`. `VerseBodySpawner.spawn(ServerLevel, LivingEntity, ShotPlan, Vec3 origin, Vec3 aim, ResourceLocation)`, `release(ServerLevel, LivingEntity, ShotPlan, Vec3 at, Vec3 travel, ResourceLocation)`, `relay(ServerLevel, VerseBodyEntity, Vec3)`, all returning `List<VerseBodyEntity>` (relay one). `MagicalEntities.VERSE_BODY`. Task 6's renderer reads the accessors; Task 7's service calls `VerseBodySpawner.spawn`.

- [ ] **Step 1: Write the shared fake-player factory and the failing gametests**

The factory is `EldritchGameTests.eldritchMage` with the skill unlock left to the caller, in a package of its own so every gametest class can use it instead of carrying a copy. `EldritchGameTests` keeps its own copy for now; switching it over is not this task.

```java
package com.efkrdnz.magical.gametest;

import com.efkrdnz.magical.magic.cast.AimResolver;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Fake players for gametests, made the way {@code EldritchGameTests.eldritchMage} makes them: a
 * survival player on the floor of a template cell, client-loaded so damage reaches them, with
 * every earlier test's leftover player removed first (fake players outlive their tests and stand
 * in the neighbouring structures, as hostile as any other player). A fake player never receives
 * a {@code PlayerTickEvent}, so cooldowns it sets never tick down; and no client moves one, so
 * gravity never sets it down either, which is why it is put on the floor by hand.
 */
public final class GameTestPlayers {

    private GameTestPlayers() {
    }

    /** A survival player called {@code name} (end it in {@code -test}, so the next test can find and remove it) standing on the floor under {@code at}. */
    public static ServerPlayer survival(GameTestHelper helper, BlockPos at, String name) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // A player is invulnerable until their client reports the world loaded; a fake one never does.
        player.setClientLoaded(true);
        // The second gate, and the one a caster's own spell falls foul of: ServerPlayer.hurt refuses
        // any blow whose damage source names a player when PVP is off, and a caster is the named
        // entity of their own blast. An integrated server turns PVP on; the gametest server leaves
        // it off, so a fake player would be immune to everything they cast at their own feet.
        server.setPvpAllowed(true);
        Vec3 stand = onFloor(helper, at);
        player.teleportTo(stand.x, stand.y, stand.z);
        return player;
    }

    /** The floor under a template cell, absolute: a thing put there starts where it would land. */
    public static Vec3 onFloor(GameTestHelper helper, BlockPos at) {
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        Vec3 floor = AimResolver.groundBelow(helper.getLevel(), above, 8);
        return floor != null ? floor : above;
    }
}
```

The gametests:

```java
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
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `.\gradlew runGameTestServer`
Expected: compilation failure, `VerseBodyEntity` and `VerseBodySpawner` do not exist.

- [ ] **Step 3: Write the entity**

```java
package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.boss.unwaking.UnwakingCapabilities;
import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.PayloadKind;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.VersePrototypes;
import com.efkrdnz.magical.magic.incantation.Wake;
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One body of a recited shot, and the Authority of Mana's only entity. It reads everything it is
 * from a {@link ProjectilePlan}: the prototype (what it is), the stamped {@link ShotState} (what the
 * verses before it wrote), and the payload it releases. What the renderer needs is synced - the
 * prototype, the school, the radius, the life, the behaviours, the wakes, the direction, a seed;
 * the plan itself is server-only NBT so a body in an unloaded chunk keeps its payload.
 *
 * <p>A body ends one way and releases the payload of that way: a hit ends it with its Latch, its
 * fuse with its Fuse, its expiry with its Epitaph. Every end fires the explosion it carries and
 * carries the caster if the prototype does. A bounce is not an end. Damage funnels through
 * {@link MagicDamageService} under the recite skill's id, which is also what a counter reads:
 * Sovereign Aegis refuses authority skills by id, so it refuses these.
 */
public final class VerseBodyEntity extends Entity implements CounterableSkillThreat {
    private static final EntityDataAccessor<Integer> PROTOTYPE = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> SCHOOL = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEHAVIOURS = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> WAKES = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.INT);

    /** Noita's critical hit. */
    public static final double CRIT_MULTIPLIER = 5.0D;
    /** An Undying body still ends here, because a server tick is not free. */
    public static final int UNDYING_CAP_TICKS = 1200;
    public static final double SEEK_RANGE = 12.0D;
    /** A bursting ricochet with no explosion of its own bursts this wide with the body's damage. */
    public static final double BURST_RADIUS = 1.5D;
    static final int OWNER_GRACE_TICKS = 5;
    static final int WAKE_INTERVAL = 4;
    static final double WAKE_REACH = 0.4D;
    static final float WAKE_BURN_SECONDS = 2.0F;
    static final int WAKE_FROST_TICKS = 20;
    static final double PIT_PULL = 0.08D;
    static final int COUNTER_WINDOW_TICKS = 13;
    /** How far ahead of a body a player is warned, when no wall comes first. */
    static final double COUNTER_WARNING_BLOCKS = 9.0D;

    private UUID ownerUuid;
    private ResourceLocation skillId = MagicContent.STARTER_SKILL;
    private ProjectilePlan plan;
    /** The flight vector in blocks per tick; the direction synced for the renderer follows it. */
    private Vec3 velocity = Vec3.ZERO;
    /** The heading at spawn, which a Gyre turns about. */
    private Vec3 launch = new Vec3(0.0D, 0.0D, 1.0D);
    private double speed;
    private int bouncesLeft;
    private boolean released;
    private boolean relayed;
    private final Set<Integer> struck = new HashSet<>();
    private VersePrototype prototypeCache;
    private int prototypeCacheIndex = -1;

    public VerseBodyEntity(EntityType<? extends VerseBodyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static VerseBodyEntity spawn(ServerLevel level, LivingEntity caster, ProjectilePlan body, Vec3 position, Vec3 direction, ResourceLocation skillId) {
        VerseBodyEntity entity = new VerseBodyEntity(MagicalEntities.VERSE_BODY.get(), level);
        entity.ownerUuid = caster == null ? null : caster.getUUID();
        entity.skillId = skillId;
        entity.entityData.set(SEED, level.random.nextInt(64));
        entity.bind(body);
        entity.setPos(position.x, position.y, position.z);
        entity.aim(direction);
        level.addFreshEntity(entity);
        return entity;
    }

    /** Every body this owner has in the box, for tests and readouts. */
    public static List<VerseBodyEntity> ownedBy(ServerLevel level, LivingEntity owner, AABB box) {
        return level.getEntitiesOfClass(VerseBodyEntity.class, box, body -> owner.getUUID().equals(body.ownerUuid));
    }

    /** The synced picture and the server numbers, off the plan. */
    private void bind(ProjectilePlan body) {
        plan = body;
        VersePrototype prototype = body.prototype();
        ShotState s = body.stamped();
        entityData.set(PROTOTYPE, VersePrototypes.all().indexOf(prototype));
        entityData.set(SCHOOL, (byte) (s.school() != null ? s.school() : prototype.school()).ordinal());
        entityData.set(RADIUS, prototype.radius());
        entityData.set(LIFE, prototype.isStatic()
                ? Math.max(1, prototype.durationTicks())
                : Math.max(1, prototype.lifetimeTicks() + s.lifetimeAddTicks()));
        entityData.set(BEHAVIOURS, VerseBehaviours.mask(s.behaviours()));
        entityData.set(WAKES, (byte) wakeMask(s.wakes()));
        speed = prototype.speed() * s.speedMultiplier();
        bouncesLeft = s.bounces();
    }

    private void aim(Vec3 direction) {
        Vec3 heading = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        launch = heading;
        velocity = heading.scale(speed);
        setDirection(heading);
    }

    private static int wakeMask(List<Wake> wakes) {
        int mask = 0;
        for (Wake wake : wakes) {
            mask |= 1 << wake.ordinal();
        }
        return mask;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PROTOTYPE, 0);
        builder.define(SCHOOL, (byte) MagicSchool.ARCANE.ordinal());
        builder.define(RADIUS, 0.15F);
        builder.define(LIFE, 40);
        builder.define(BEHAVIOURS, 0);
        builder.define(WAKES, (byte) 0);
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, 0.0F);
        builder.define(DIR_Z, 1.0F);
        builder.define(SEED, 0);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (plan == null) {
            discard();
            return;
        }
        if (has(Behaviour.NAUGHT)) {
            end(level, position(), PayloadKind.EPITAPH);
            return;
        }
        if (plan.prototype().isStatic()) {
            tickStanding(level);
        } else {
            tickFlying(level);
        }
    }

    private void tickStanding(ServerLevel level) {
        int interval = Math.max(1, plan.prototype().pulseIntervalTicks());
        if (tickCount % interval == 0) {
            pulse(level);
        }
        if (isRemoved()) {
            return;
        }
        if (fuseDue()) {
            end(level, position(), PayloadKind.FUSE);
        } else if (lifeOver()) {
            end(level, position(), PayloadKind.EPITAPH);
        }
    }

    private void tickFlying(ServerLevel level) {
        if (fuseDue()) {
            end(level, position(), PayloadKind.FUSE);
            return;
        }
        Vec3 from = position();
        Vec3 step = flightStep(level);
        Vec3 to = from.add(step);
        // The wall bounds the step, and anything living between here and it is met first: a body
        // fired at an enemy standing against a wall hits the enemy, not the wall behind them. A
        // Puncture strikes and flies on, and then the wall has its turn in the same tick.
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 reach = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
        offerApproachCounters(level, from);
        LivingEntity hit = firstEntityHit(from, reach);
        while (hit != null) {
            if (hit instanceof ServerPlayer player && MagicCounterService.hasActivePrompt(player, this)) {
                MagicCounterService.expirePrompt(player, this);
            }
            strike(level, hit);
            if (!has(Behaviour.PUNCTURE)) {
                end(level, new Vec3(hit.getX(), hit.getY(0.55D), hit.getZ()), PayloadKind.LATCH);
                return;
            }
            // A Puncture takes everything on the segment: what it struck is in the set now, so the
            // next call finds the next body along the line, or nothing.
            hit = firstEntityHit(from, reach);
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            if (bouncesLeft > 0) {
                bounce(level, blockHit);
                return;
            }
            end(level, blockHit.getLocation(), PayloadKind.LATCH);
            return;
        }
        setPos(to.x, to.y, to.z);
        setDirection(velocity);
        if (tickCount % WAKE_INTERVAL == 0) {
            wake(level);
        }
        if (lifeOver()) {
            end(level, position(), PayloadKind.EPITAPH);
        }
    }

    /**
     * Where the body goes this tick, in a fixed order: a Gyre is placed on its orbit and nothing
     * else applies; otherwise gravity, then Seeker and Errant on the flight vector, then Serpentine
     * as an offset riding on top of it.
     */
    private Vec3 flightStep(ServerLevel level) {
        Entity owner = ownerEntity();
        if (has(Behaviour.GYRE) && owner != null) {
            Vec3 wanted = owner.position().add(VerseBehaviours.gyreOffset(launch, tickCount));
            Vec3 step = wanted.subtract(position());
            if (step.lengthSqr() > 1.0E-6D) {
                velocity = step.normalize().scale(Math.max(speed, 1.0E-3D));
            }
            return step;
        }
        Vec3 toTarget = null;
        if (has(Behaviour.SEEKER)) {
            LivingEntity target = nearestHostile(level, owner);
            if (target != null) {
                toTarget = target.getBoundingBox().getCenter().subtract(position());
            }
        }
        velocity = VerseBehaviours.steer(velocity, plan.stamped().gravity(), toTarget, has(Behaviour.SEEKER), has(Behaviour.ERRANT), tickCount, seed());
        Vec3 step = velocity;
        if (has(Behaviour.SERPENTINE)) {
            step = step.add(VerseBehaviours.serpentineOffset(velocity, tickCount));
        }
        return step;
    }

    private LivingEntity nearestHostile(ServerLevel level, Entity owner) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : SkillTargets.hostilesWithin(level, owner != null ? owner : this, position(), SEEK_RANGE)) {
            double distance = candidate.distanceToSqr(position());
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private void bounce(ServerLevel level, BlockHitResult hit) {
        Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
        bouncesLeft--;
        velocity = VerseBehaviours.bounce(velocity, normal);
        Vec3 at = hit.getLocation().add(normal.scale(0.05D));
        setPos(at.x, at.y, at.z);
        setDirection(velocity);
        if (has(Behaviour.BOUNCE_BURST)) {
            explode(level, at, Math.max(explosionRadius(), BURST_RADIUS), Math.max(explosionDamage(), damage()));
        }
    }

    private LivingEntity firstEntityHit(Vec3 from, Vec3 to) {
        Entity owner = ownerEntity();
        boolean ownerFair = plan.stamped().friendlyFire() && tickCount > OWNER_GRACE_TICKS;
        AABB path = getBoundingBox().expandTowards(to.subtract(from)).inflate(0.3D + radius());
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : level().getEntities(this, path, target -> target instanceof LivingEntity living && living.isAlive()
                && !struck.contains(target.getId()) && (target != owner || ownerFair))) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(radius()).clip(from, to);
            if (hit.isPresent()) {
                double distance = from.distanceToSqr(hit.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = (LivingEntity) entity;
                }
            }
        }
        return closest;
    }

    /** The hit: healing, damage with its crit roll, knockback, then the effects, the body's own first. */
    private void strike(ServerLevel level, LivingEntity target) {
        struck.add(target.getId());
        Entity owner = ownerEntity();
        Entity source = owner == null ? this : owner;
        ShotState s = plan.stamped();
        double healing = plan.prototype().healing() + s.healingAdd();
        if (healing > 0.0D) {
            target.heal((float) healing);
        }
        double damage = damage();
        if (damage > 0.0D) {
            if (s.critChance() > 0.0D && random.nextDouble() * 100.0D < s.critChance()) {
                damage *= CRIT_MULTIPLIER;
            }
            MagicDamageService.hurt(target, damageSources().indirectMagic(this, source), (float) damage, skillId);
        }
        if (s.knockback() > 0.0D) {
            SkillTargets.shove(target, position(), 0.25D * s.knockback(), 0.05D);
        }
        for (HitEffect effect : VerseHitEffects.effectsOf(plan)) {
            VerseHitEffects.apply(effect, target, source, position(), random);
        }
    }

    /** A static's pulse: its effect over its radius. The caster stands in their own ring unharmed unless friendly fire is on, but is healed by it. */
    private void pulse(ServerLevel level) {
        Entity owner = ownerEntity();
        Entity source = owner == null ? this : owner;
        ShotState s = plan.stamped();
        double radius = radius();
        double healing = plan.prototype().healing() + s.healingAdd();
        double damage = damage();
        List<HitEffect> effects = VerseHitEffects.pulseEffectsOf(plan);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), LivingEntity::isAlive)) {
            if (target.distanceToSqr(position()) > radius * radius) {
                continue;
            }
            if (healing > 0.0D) {
                target.heal((float) healing);
            }
            if (target == owner && !s.friendlyFire()) {
                continue;
            }
            if (damage > 0.0D) {
                MagicDamageService.hurt(target, damageSources().indirectMagic(this, source), (float) damage, skillId);
            }
            for (HitEffect effect : effects) {
                VerseHitEffects.apply(effect, target, source, position(), random);
            }
            if (plan.prototype().look() == VersePrototype.Look.PIT && !(target instanceof ServerPlayer)) {
                Vec3 pull = position().subtract(target.position());
                if (pull.lengthSqr() > 1.0E-4D) {
                    pull = pull.normalize().scale(PIT_PULL);
                    target.push(pull.x, pull.y * 0.5D, pull.z);
                    target.hurtMarked = true;
                }
            }
        }
        if (plan.prototype().look() == VersePrototype.Look.PIT) {
            // The synced prototype, not the plan: a body loaded with a prototype the game no longer
            // knows carries no plan and discards itself on its next tick, and must not crash a pit first.
            for (VerseBodyEntity other : level.getEntitiesOfClass(VerseBodyEntity.class, getBoundingBox().inflate(radius), body -> body != this && !body.prototype().isStatic())) {
                Vec3 pull = position().subtract(other.position());
                if (pull.lengthSqr() > 1.0E-4D) {
                    other.velocity = other.velocity.add(pull.normalize().scale(PIT_PULL)).normalize().scale(Math.max(other.speed, 1.0E-3D));
                }
            }
        }
    }

    /** What the body leaves beside its line: fire sets alight, water puts out, frost slows. Never a block. */
    private void wake(ServerLevel level) {
        ShotState s = plan.stamped();
        if (s.wakes().isEmpty()) {
            return;
        }
        Entity owner = ownerEntity();
        double reach = radius() + WAKE_REACH + s.wakeAmount() * 0.04D;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach), living -> living.isAlive() && living != owner)) {
            for (Wake wakeKind : s.wakes()) {
                switch (wakeKind) {
                    case FIRE -> target.igniteForSeconds(WAKE_BURN_SECONDS);
                    case WATER -> target.clearFire();
                    case FROST -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WAKE_FROST_TICKS, 0, false, true), owner);
                }
            }
        }
    }

    // ------------------------------------------------------------------ ends

    private void end(ServerLevel level, Vec3 at, PayloadKind reason) {
        if (isRemoved()) {
            return;
        }
        explode(level, at);
        if (plan.payloadKind() == reason) {
            release(level, at);
        }
        if (reason == PayloadKind.LATCH && has(Behaviour.RELAY) && !relayed) {
            VerseBodySpawner.relay(level, this, at);
        }
        carryCaster(level, at);
        discard();
    }

    private void release(ServerLevel level, Vec3 at) {
        if (released || !plan.hasPayload()) {
            return;
        }
        released = true;
        VerseBodySpawner.release(level, livingOwner(), plan.payload(), at, direction(), skillId);
    }

    private void explode(ServerLevel level, Vec3 at) {
        double radius = explosionRadius();
        if (radius > 0.0D) {
            explode(level, at, radius, explosionDamage());
        }
    }

    /** Everything living in the radius, the caster included, with falloff. */
    private void explode(ServerLevel level, Vec3 at, double radius, double damage) {
        Entity owner = ownerEntity();
        Entity source = owner == null ? this : owner;
        MagicSkillDefinition skill = MagicContent.get(skillId);
        if (skill != null) {
            SpellFx.impact(level, skill, at, new Vec3(0.0D, 1.0D, 0.0D), null, owner, (float) Math.max(0.5D, radius * 0.5D));
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), LivingEntity::isAlive)) {
            double distance = Math.sqrt(target.distanceToSqr(at));
            if (distance > radius) {
                continue;
            }
            double falloff = Math.max(0.25D, 1.0D - distance / radius);
            if (damage > 0.0D) {
                MagicDamageService.hurt(target, damageSources().indirectMagic(this, source), (float) (damage * falloff), skillId);
            }
            SkillTargets.shove(target, at, 0.3D * falloff, 0.12D * falloff);
        }
    }

    /** A Blink or a Step Word: the caster to where the body ended, through the one placement path the mod has. */
    private void carryCaster(ServerLevel level, Vec3 at) {
        if (!plan.prototype().carriesCaster() || !(ownerEntity() instanceof LivingEntity living)) {
            return;
        }
        if (living instanceof ServerPlayer player && UnwakingCapabilities.refuseMovement(player)) {
            return;
        }
        Vec3 feet = placeNear(level, at, living);
        if (feet == null) {
            // A block hit ends on the face it struck, and that point floors into the struck block
            // itself, so both searches are looking up and down a column of solid wall. The last
            // point the body stood in is the same impact one step back, and it is in open air.
            feet = placeNear(level, position(), living);
        }
        if (feet == null) {
            return;
        }
        SafeSpotSearch.place(living, feet, living.getYRot(), living.getXRot(), false);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** Somewhere a body of this size can stand at {@code wanted}: on the ground under it, else lifted clear of it. */
    private static Vec3 placeNear(ServerLevel level, Vec3 wanted, LivingEntity living) {
        Vec3 feet = SafeSpotSearch.standableNear(level, wanted, 2, 3, living.getBbWidth(), living.getBbHeight());
        return feet != null ? feet : SafeSpotSearch.liftClear(level, wanted, living.getBbWidth(), living.getBbHeight(), 2.0D);
    }

    // ------------------------------------------------------------------ counters

    /** A counter is offered to any player on the line ahead, as far as the next wall and no further. */
    private void offerApproachCounters(ServerLevel level, Vec3 from) {
        if (damage() <= 0.0D || velocity.lengthSqr() < 1.0E-6D) {
            return;
        }
        Entity owner = ownerEntity();
        Vec3 heading = velocity.normalize();
        Vec3 far = from.add(heading.scale(COUNTER_WARNING_BLOCKS));
        BlockHitResult wall = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            far = wall.getLocation();
        }
        AABB warningPath = new AABB(from, far).inflate(2.0D + radius());
        for (Entity entity : level.getEntities(this, warningPath, target -> target instanceof ServerPlayer player && player.isAlive() && target != owner)) {
            ServerPlayer player = (ServerPlayer) entity;
            if (player.getEyePosition().subtract(from).dot(heading) < 0.0D) {
                continue;
            }
            MagicCounterService.offerCounter(player, this, player.getEyePosition().add(0.0D, -0.25D, 0.0D), COUNTER_WINDOW_TICKS);
        }
    }

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public ResourceLocation counterSkillId() {
        return skillId;
    }

    @Override
    public MagicAttribute counterAttribute() {
        MagicSkillDefinition skill = MagicContent.get(skillId);
        return skill == null ? MagicAttribute.ARCANE : skill.attribute();
    }

    @Override
    public Entity counterOwner() {
        return ownerEntity();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition) {
        MagicSkillDefinition skill = MagicContent.get(skillId);
        MagicCounterService.spawnClash(level, clashPosition, skill == null ? 0xF0F4FF : skill.color(), counterSkill.color());
        discard();
    }

    // ------------------------------------------------------------------ numbers

    private boolean fuseDue() {
        return plan.payloadKind() == PayloadKind.FUSE && tickCount >= Math.max(1, plan.fuseTicks());
    }

    private boolean lifeOver() {
        if (tickCount >= UNDYING_CAP_TICKS) {
            return true;
        }
        return !has(Behaviour.UNDYING) && tickCount >= life();
    }

    /** {@code prototype.damage + damageAdd}, nothing under Blunt, never negative. Server only: it reads the plan. */
    double damage() {
        ShotState s = plan.stamped();
        return s.nullsDamage() ? 0.0D : Math.max(0.0D, plan.prototype().damage() + s.damageAdd());
    }

    private double explosionRadius() {
        return Math.max(0.0D, plan.prototype().explosionRadius() + plan.stamped().explosionRadius());
    }

    private double explosionDamage() {
        return plan.stamped().nullsDamage() ? 0.0D : Math.max(0.0D, plan.prototype().explosionDamage() + plan.stamped().explosionDamageAdd());
    }

    // ------------------------------------------------------------------ accessors

    /** The plan this body flies. Server only: it is NBT, never synced, so on a client it is null. */
    public ProjectilePlan plan() {
        return plan;
    }

    public ResourceLocation skillId() {
        return skillId;
    }

    /** From the synced index, cached, because {@code VersePrototypes.all()} copies the table. */
    public VersePrototype prototype() {
        int index = entityData.get(PROTOTYPE);
        if (index != prototypeCacheIndex || prototypeCache == null) {
            List<VersePrototype> all = VersePrototypes.all();
            prototypeCache = index >= 0 && index < all.size() ? all.get(index) : VersePrototypes.NEEDLE;
            prototypeCacheIndex = index;
        }
        return prototypeCache;
    }

    public MagicSchool school() {
        int ordinal = entityData.get(SCHOOL);
        MagicSchool[] schools = MagicSchool.values();
        return ordinal >= 0 && ordinal < schools.length ? schools[ordinal] : MagicSchool.ARCANE;
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int behaviourMask() {
        return entityData.get(BEHAVIOURS);
    }

    public int wakeMask() {
        return entityData.get(WAKES);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    public boolean has(Behaviour behaviour) {
        return VerseBehaviours.has(behaviourMask(), behaviour);
    }

    public Vec3 direction() {
        return new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
    }

    private void setDirection(Vec3 v) {
        if (v.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 n = v.normalize();
        entityData.set(DIR_X, (float) n.x);
        entityData.set(DIR_Y, (float) n.y);
        entityData.set(DIR_Z, (float) n.z);
    }

    public void markRelayed() {
        relayed = true;
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    /** The owner as a living thing, or null. Server only: a client cannot look an entity up by UUID. */
    public LivingEntity livingOwner() {
        return ownerEntity() instanceof LivingEntity living ? living : null;
    }

    // ------------------------------------------------------------------ nbt and entity contract

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putString("Skill", skillId.toString());
        if (plan != null) {
            tag.put("Body", ShotPlanCodec.saveBody(plan));
        }
        tag.putDouble("VelX", velocity.x);
        tag.putDouble("VelY", velocity.y);
        tag.putDouble("VelZ", velocity.z);
        tag.putDouble("LaunchX", launch.x);
        tag.putDouble("LaunchY", launch.y);
        tag.putDouble("LaunchZ", launch.z);
        tag.putInt("Age", tickCount);
        tag.putInt("Bounces", bouncesLeft);
        tag.putBoolean("Released", released);
        tag.putBoolean("Relayed", relayed);
        tag.putInt("Seed", seed());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Skill"));
        if (id != null) {
            skillId = id;
        }
        entityData.set(SEED, tag.getInt("Seed"));
        ProjectilePlan body = tag.contains("Body") ? ShotPlanCodec.loadBody(tag.getCompound("Body")) : null;
        if (body == null) {
            plan = null;
            return;
        }
        bind(body);
        velocity = new Vec3(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
        launch = new Vec3(tag.getDouble("LaunchX"), tag.getDouble("LaunchY"), tag.getDouble("LaunchZ"));
        tickCount = tag.getInt("Age");
        bouncesLeft = tag.getInt("Bounces");
        released = tag.getBoolean("Released");
        relayed = tag.getBoolean("Relayed");
        setDirection(velocity);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
```

- [ ] **Step 4: Write the spawner**

```java
package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.ShotPlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A shot plan into the world: the fan from the group's pattern, a deviation within the group's
 * spread per body, speed from the prototype times the stamped multiplier, statics a block ahead of
 * the hand (or at the release point) and dropped to the floor, Near Word bodies on the caster,
 * Sightline bodies at the crosshair, twins beside each other. {@link #release} is the same call from
 * a body's Latch, Fuse or Epitaph, and {@link #relay} the same from an impact. Never more than
 * {@link ReciteCaps#MAX_BODIES} per call, twins included.
 */
public final class VerseBodySpawner {

    public static final double STANDING_AHEAD = 1.0D;
    public static final int STANDING_DROP = 6;
    public static final double SIGHTLINE_RANGE = 64.0D;
    public static final double RELAY_STEP_BACK = 0.3D;

    private VerseBodySpawner() {
    }

    public static List<VerseBodyEntity> spawn(ServerLevel level, LivingEntity caster, ShotPlan shot, Vec3 origin, Vec3 aim, ResourceLocation skillId) {
        return spawnAll(level, caster, shot, origin, aim, skillId, true);
    }

    public static List<VerseBodyEntity> release(ServerLevel level, LivingEntity caster, ShotPlan payload, Vec3 at, Vec3 travel, ResourceLocation skillId) {
        return spawnAll(level, caster, payload, at, travel, skillId, false);
    }

    /** Relay: the same body again from the impact point, on a seeded heading, flattened so a relay off a wall runs along the ground; it will not relay again. */
    public static List<VerseBodyEntity> relay(ServerLevel level, VerseBodyEntity body, Vec3 at) {
        Vec3 heading = body.direction().yRot((float) (VerseBehaviours.roll(body.seed(), body.tickCount, 3) * Math.PI));
        heading = new Vec3(heading.x, Math.abs(heading.y) * 0.25D, heading.z);
        VerseBodyEntity next = VerseBodyEntity.spawn(level, body.livingOwner(), body.plan(),
                at.subtract(body.direction().scale(RELAY_STEP_BACK)), heading, body.skillId());
        next.markRelayed();
        return List.of(next);
    }

    private static List<VerseBodyEntity> spawnAll(ServerLevel level, LivingEntity caster, ShotPlan shot, Vec3 origin, Vec3 aim, ResourceLocation skillId, boolean fromHand) {
        List<VerseBodyEntity> spawned = new ArrayList<>();
        List<ProjectilePlan> bodies = shot.bodies();
        int count = Math.min(bodies.size(), ReciteCaps.MAX_BODIES);
        ShotState group = shot.state();
        Vec3 heading = aim.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : aim.normalize();
        double[] yaws = VerseFan.yaws(count, group.patternDegrees());
        for (int i = 0; i < count && spawned.size() < ReciteCaps.MAX_BODIES; i++) {
            ProjectilePlan body = bodies.get(i);
            Vec3 direction = headingOf(level, caster, body, heading, yaws[i], group.spreadDegrees());
            Vec3 at = placeOf(level, caster, body, origin, heading, fromHand);
            if (body.stamped().has(Behaviour.TWIN_PATH)) {
                spawned.add(VerseBodyEntity.spawn(level, caster, body, at, VerseBehaviours.twin(direction, false), skillId));
                if (spawned.size() < ReciteCaps.MAX_BODIES) {
                    spawned.add(VerseBodyEntity.spawn(level, caster, body, at, VerseBehaviours.twin(direction, true), skillId));
                }
            } else {
                spawned.add(VerseBodyEntity.spawn(level, caster, body, at, direction, skillId));
            }
        }
        return spawned;
    }

    /** Sightline aims once, at the crosshair, and ignores the fan; everything else takes its fan yaw and a deviation within the spread. */
    static Vec3 headingOf(ServerLevel level, LivingEntity caster, ProjectilePlan body, Vec3 heading, double fanYaw, double spread) {
        if (body.stamped().has(Behaviour.SIGHTLINE) && caster != null) {
            Vec3 point = AimResolver.resolve(level, caster, heading, SIGHTLINE_RANGE, 0.0D, false, 0, null).point();
            Vec3 line = point.subtract(caster.getEyePosition());
            return line.lengthSqr() < 1.0E-6D ? heading : line.normalize();
        }
        double yawDeviation = VerseFan.deviation(spread, level.random.nextDouble());
        double pitchDeviation = VerseFan.deviation(spread, level.random.nextDouble());
        return deviate(heading.yRot((float) Math.toRadians(fanYaw)), yawDeviation, pitchDeviation);
    }

    /** A yaw about the vertical axis, then a pitch about the side axis, both in degrees. */
    static Vec3 deviate(Vec3 direction, double yawDegrees, double pitchDegrees) {
        Vec3 d = direction.yRot((float) Math.toRadians(yawDegrees));
        Vec3 side = VerseBehaviours.sideOf(d);
        if (side.lengthSqr() < 1.0E-9D) {
            return d;
        }
        Vec3 up = side.cross(d).normalize();
        double pitch = Math.toRadians(pitchDegrees);
        return d.scale(Math.cos(pitch)).add(up.scale(Math.sin(pitch))).normalize();
    }

    /** Near Word on the caster; a flying body at the origin; a static a block ahead (or at the release point), dropped to the floor unless it is a held word. */
    static Vec3 placeOf(ServerLevel level, LivingEntity caster, ProjectilePlan body, Vec3 origin, Vec3 heading, boolean fromHand) {
        if (body.stamped().has(Behaviour.NEAR_WORD) && caster != null) {
            return caster.position().add(0.0D, caster.getBbHeight() * 0.5D, 0.0D);
        }
        VersePrototype prototype = body.prototype();
        if (!prototype.isStatic()) {
            return origin;
        }
        Vec3 wanted = fromHand ? origin.add(heading.scale(STANDING_AHEAD)) : origin;
        if (prototype.look() == VersePrototype.Look.WORD) {
            return wanted;
        }
        Vec3 floor = AimResolver.groundBelow(level, wanted, STANDING_DROP);
        return floor != null ? floor.add(0.0D, 0.05D, 0.0D) : wanted;
    }
}
```

- [ ] **Step 5: Register the entity**

In `src/main/java/com/efkrdnz/magical/registry/MagicalEntities.java`, directly after the `ELDRITCH_CONSTRUCT` registration (the block ending `.build(key("eldritch_construct")));`), add:

```java
    /** One body of a recited incantation: the Authority of Mana's only entity. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.verse.VerseBodyEntity>> VERSE_BODY = ENTITY_TYPES.register(
            "verse_body",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.verse.VerseBodyEntity>of(com.efkrdnz.magical.entity.verse.VerseBodyEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("verse_body")));
```

- [ ] **Step 6: Build, then run the gametests**

Run: `.\gradlew build` — Expected: green (the unit tests of Tasks 1–4 still pass; nothing else changed).
Run: `.\gradlew runGameTestServer` — Expected: the eight `VerseBodyGameTests` pass along with the existing ones. The geometry every timing above rests on: `unwaking_empty` is five blocks of air (relative 0..4 on every axis) in a one-block barrier shell the runner adds, the fake player stands at x 2.5 with eyes at about y 3.6, the husk at x 4.5, and the prototype numbers are `VersePrototypes`' (a needle 1.6 blocks a tick for 40 ticks, an orb 1.0, a blink 1.5, a rime ring radius 3 pulsing every 10 ticks for 100, a burst radius 3 for one tick). If a timing assertion fails, check those numbers against the file before touching the rule under test.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/entity/verse/VerseBodyEntity.java src/main/java/com/efkrdnz/magical/entity/verse/VerseBodySpawner.java src/main/java/com/efkrdnz/magical/registry/MagicalEntities.java src/main/java/com/efkrdnz/magical/gametest/GameTestPlayers.java src/main/java/com/efkrdnz/magical/magic/incantation/VerseBodyGameTests.java
git commit -m "feat: the verse body, the one entity of the Authority of Mana, and its spawner"
```

---

### Task 6: The renderer, one look per prototype

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/renderer/verse/VerseLooks.java`
- Create: `src/test/java/com/efkrdnz/magical/client/renderer/verse/VerseLooksTest.java`
- Create: `src/main/java/com/efkrdnz/magical/client/renderer/verse/VerseBodyRenderer.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java` (`registerRenderers`, line 149: one line added)

**Interfaces:**
- Consumes: `VerseBodyEntity` accessors from Task 5 (`prototype()`, `school()`, `radius()`, `life()`, `behaviourMask()`, `wakeMask()`, `direction()`, `seed()`, `plan()` is server-only and never read here); `VerseBehaviours.has(int, Behaviour)`; `VersePrototype.Look`; `FxContext(PoseStack, MultiBufferSource, float, Quaternionf, Vec3)`, `.timing(float age, float life, int seed)`, fields `detail`, `lod`, `opacity`, `phase`, `fade()`; `OrbPainter.billboard(FxContext, FxKinds.Orb, float radius, int rgb, float opacity, float phase, int count, int paramB)`; `FilamentPainter.beam(FxContext, FxKinds.Filament, float halfWidth, float length, int rgb, float opacity, float reveal, int count, int taper)` (drawn from the origin along local +Z), `FilamentPainter.orientAlong(PoseStack, Vec3)`; `MarkPainter.mark(FxContext, FxKinds.Mark, float radius, int rgb, float opacity, float phase, int count, int paramB)` (the local XY plane: pitch 90 for a floor); `FxBudget.detailForDistance(int, double)`, `FxBudget.lodForDistance(double)`; `SchoolMaterial.of(MagicSchool).variantColor(int)`; `MagicalEntities.VERSE_BODY`.
- Produces: `VerseLooks.of(Look) -> Row`, `VerseLooks.drawnSize(Row, float radius) -> float`, `VerseLooks.glyph(Behaviour) -> FxKinds.Orb`, `VerseLooks.wake(Wake) -> FxKinds.Filament`, `VerseLooks.wakeColor(Wake) -> int`; `VerseBodyRenderer`. Nothing later depends on the renderer.

The table is a separate pure class so a test can hold that every Look has a row, every Behaviour a glyph and every Wake a filament, without a client; the renderer itself is the only file here the JVM tests never load.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.client.renderer.verse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.Wake;
import org.junit.jupiter.api.Test;

/** The look table is total over its three enums, and a body is never drawn smaller than it can be seen. */
class VerseLooksTest {

    @Test
    void everyLookHasARow() {
        for (VersePrototype.Look look : VersePrototype.Look.values()) {
            VerseLooks.Row row = VerseLooks.of(look);
            assertNotNull(row, look + " has a row");
            assertNotNull(row.shape(), look + " has a shape");
            switch (row.shape()) {
                case BOLT -> assertNotNull(row.filament(), look + " is a bolt and names its filament");
                case ORB -> assertNotNull(row.orb(), look + " is an orb and names its orb kind");
                case MARK -> assertNotNull(row.mark(), look + " is a mark and names its mark kind");
            }
            assertTrue(row.opacity() > 0.0F && row.opacity() <= 1.0F, look + " opacity in (0, 1]");
            assertTrue(row.count() > 0, look + " count positive");
        }
    }

    @Test
    void theStandingLooksAreMarksOnTheFloor() {
        assertEquals(VerseLooks.Shape.MARK, VerseLooks.of(VersePrototype.Look.RING).shape());
        assertEquals(VerseLooks.Shape.MARK, VerseLooks.of(VersePrototype.Look.BURST).shape());
        assertEquals(VerseLooks.Shape.MARK, VerseLooks.of(VersePrototype.Look.PIT).shape());
        assertEquals(VerseLooks.Shape.ORB, VerseLooks.of(VersePrototype.Look.WORD).shape(), "a word hangs in the air, so it is not a floor mark");
    }

    @Test
    void everyBehaviourHasAGlyphAndEveryWakeAFilamentAndAColour() {
        for (Behaviour behaviour : Behaviour.values()) {
            assertNotNull(VerseLooks.glyph(behaviour), behaviour + " has a glyph");
        }
        for (Wake wake : Wake.values()) {
            assertNotNull(VerseLooks.wake(wake), wake + " has a filament");
            assertTrue(VerseLooks.wakeColor(wake) != 0, wake + " has a colour");
        }
    }

    @Test
    void aBodyIsNeverDrawnSmallerThanItCanBeSeenAndGrowsWithItsRadius() {
        VerseLooks.Row needle = VerseLooks.of(VersePrototype.Look.NEEDLE);
        assertTrue(VerseLooks.drawnSize(needle, 0.0F) >= VerseLooks.MIN_DRAWN, "a zero radius still draws");
        assertTrue(VerseLooks.drawnSize(needle, 0.5F) > VerseLooks.drawnSize(needle, 0.2F), "a wider body draws wider");
        assertEquals(0.5F * needle.scale(), VerseLooks.drawnSize(needle, 0.5F), 1.0E-6F, "the row's scale times the radius");
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.renderer.verse.VerseLooksTest"`
Expected: compilation failure, `VerseLooks` does not exist.

- [ ] **Step 3: Write the table**

```java
package com.efkrdnz.magical.client.renderer.verse;

import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.Wake;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a body looks like, per {@link VersePrototype.Look}: one existing painter kind, a scale on
 * the prototype's radius, and the count and parameter the shader takes. No new art - every kind
 * here is one the profile painters already draw - and the colour is the school's own ramp, so a
 * Fire needle and an Arcane needle are the same shape in different light. Pure, so a test can
 * hold the table total.
 */
public final class VerseLooks {

    /** The palette variant a body is painted in; the brighter variant 1 lights its glyphs. */
    public static final int PALETTE_VARIANT = 0;
    public static final int GLYPH_VARIANT = 1;
    /** Nothing draws below this, whatever the prototype says its radius is. */
    public static final float MIN_DRAWN = 0.08F;

    public enum Shape { BOLT, ORB, MARK }

    /** One look. {@code length} is the bolt's beam length in blocks; the other shapes ignore it. */
    public record Row(Shape shape, FxKinds.Orb orb, FxKinds.Filament filament, FxKinds.Mark mark,
                      float scale, float length, float opacity, int count, int paramB) {
        static Row bolt(FxKinds.Filament filament, float scale, float length, float opacity, int count, int paramB) {
            return new Row(Shape.BOLT, null, filament, null, scale, length, opacity, count, paramB);
        }

        static Row orb(FxKinds.Orb orb, float scale, float opacity, int count, int paramB) {
            return new Row(Shape.ORB, orb, null, null, scale, 0.0F, opacity, count, paramB);
        }

        static Row mark(FxKinds.Mark mark, float scale, float opacity, int count, int paramB) {
            return new Row(Shape.MARK, null, null, mark, scale, 0.0F, opacity, count, paramB);
        }
    }

    private static final Map<VersePrototype.Look, Row> ROWS = new EnumMap<>(VersePrototype.Look.class);
    private static final Map<Behaviour, FxKinds.Orb> GLYPHS = new EnumMap<>(Behaviour.class);

    static {
        ROWS.put(VersePrototype.Look.NEEDLE, Row.bolt(FxKinds.Filament.PLASMA_TUBE, 1.4F, 1.2F, 1.0F, 4, 10));
        ROWS.put(VersePrototype.Look.ORB, Row.orb(FxKinds.Orb.PLASMA, 1.6F, 1.0F, 6, 10));
        ROWS.put(VersePrototype.Look.SHARD, Row.orb(FxKinds.Orb.SHARD_DIAMOND, 1.8F, 1.0F, 5, 12));
        ROWS.put(VersePrototype.Look.EMBER, Row.orb(FxKinds.Orb.EMBER_CLUSTER, 2.0F, 1.0F, 7, 9));
        ROWS.put(VersePrototype.Look.ARC, Row.bolt(FxKinds.Filament.LIGHTNING, 1.6F, 2.2F, 1.0F, 6, 6));
        ROWS.put(VersePrototype.Look.DART, Row.bolt(FxKinds.Filament.RIBBON, 1.5F, 1.0F, 1.0F, 3, 8));
        ROWS.put(VersePrototype.Look.WHISPER, Row.orb(FxKinds.Orb.SPARK_BURST, 1.2F, 0.8F, 8, 6));
        ROWS.put(VersePrototype.Look.BLINK, Row.orb(FxKinds.Orb.LENS_STREAKS, 1.8F, 1.0F, 6, 12));
        ROWS.put(VersePrototype.Look.RING, Row.mark(FxKinds.Mark.RIPPLES, 1.0F, 0.9F, 8, 6));
        ROWS.put(VersePrototype.Look.BURST, Row.mark(FxKinds.Mark.RAY_BURST, 3.0F, 1.0F, 12, 8));
        ROWS.put(VersePrototype.Look.PIT, Row.mark(FxKinds.Mark.VORTEX_SPIRAL, 1.0F, 0.9F, 8, 10));
        ROWS.put(VersePrototype.Look.WORD, Row.orb(FxKinds.Orb.HEX_LENS, 1.5F, 0.9F, 6, 8));

        GLYPHS.put(Behaviour.SEEKER, FxKinds.Orb.CRESCENT);
        GLYPHS.put(Behaviour.SIGHTLINE, FxKinds.Orb.LENS_STREAKS);
        GLYPHS.put(Behaviour.PUNCTURE, FxKinds.Orb.SHARD_DIAMOND);
        GLYPHS.put(Behaviour.SERPENTINE, FxKinds.Orb.THIN_HALO);
        GLYPHS.put(Behaviour.GYRE, FxKinds.Orb.HOLLOW_SHELL);
        GLYPHS.put(Behaviour.ERRANT, FxKinds.Orb.SPARK_BURST);
        GLYPHS.put(Behaviour.RELAY, FxKinds.Orb.CHARGE_SPHERE);
        GLYPHS.put(Behaviour.TWIN_PATH, FxKinds.Orb.BLOOM_FLASH);
        GLYPHS.put(Behaviour.NAUGHT, FxKinds.Orb.VOID_CORE);
        GLYPHS.put(Behaviour.UNDYING, FxKinds.Orb.HEX_LENS);
        GLYPHS.put(Behaviour.LANTERN, FxKinds.Orb.BLOOM_FLASH);
        GLYPHS.put(Behaviour.NEAR_WORD, FxKinds.Orb.PLASMA);
        GLYPHS.put(Behaviour.BOUNCE_BURST, FxKinds.Orb.EMBER_CLUSTER);
    }

    private VerseLooks() {
    }

    public static Row of(VersePrototype.Look look) {
        Row row = ROWS.get(look);
        return row != null ? row : ROWS.get(VersePrototype.Look.ORB);
    }

    /** The row's scale on the body's radius, floored so a thin needle is still a line. */
    public static float drawnSize(Row row, float radius) {
        return Math.max(MIN_DRAWN, radius * row.scale());
    }

    /** The small orb that orbits a body carrying this behaviour. */
    public static FxKinds.Orb glyph(Behaviour behaviour) {
        FxKinds.Orb orb = GLYPHS.get(behaviour);
        return orb != null ? orb : FxKinds.Orb.PLASMA;
    }

    public static FxKinds.Filament wake(Wake wake) {
        return switch (wake) {
            case FIRE -> FxKinds.Filament.FLAME_TONGUE;
            case WATER -> FxKinds.Filament.WATER;
            case FROST -> FxKinds.Filament.CRYSTAL_SHARD;
        };
    }

    /** A wake is its material's colour, not the body's school: a Fire Wake behind an Arcane needle is still fire. */
    public static int wakeColor(Wake wake) {
        return switch (wake) {
            case FIRE -> SchoolMaterial.FIRE.variantColor(0);
            case WATER -> SchoolMaterial.WATER.variantColor(0);
            case FROST -> SchoolMaterial.WATER.variantColor(1);
        };
    }
}
```

- [ ] **Step 4: Run the test**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.renderer.verse.VerseLooksTest"`
Expected: 4 tests pass.

- [ ] **Step 5: Write the renderer**

```java
package com.efkrdnz.magical.client.renderer.verse;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.MarkPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.entity.verse.VerseBehaviours;
import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.Wake;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a verse body: the {@link VerseLooks} row for its prototype in its school's colour, a small
 * glyph orbiting it per behaviour it carries, a filament trailing behind it per wake, and a wider
 * bloom when it is a Lantern. A static fades in and out over its life; a flying body is full
 * until it ends. Everything is drawn through the existing painters, so the budget and the LOD are
 * theirs.
 */
public final class VerseBodyRenderer extends EntityRenderer<VerseBodyEntity, VerseBodyRenderer.State> {

    private static final float GLYPH_SIZE = 0.12F;
    private static final float GLYPH_ORBIT = 0.2F;
    private static final float GLYPH_TURN_PER_TICK = 0.2F;
    private static final float GLYPH_OPACITY = 0.9F;
    private static final float WAKE_LENGTH = 1.6F;
    private static final float WAKE_WIDTH = 0.6F;
    private static final float WAKE_OPACITY = 0.7F;
    private static final float LANTERN_SCALE = 3.0F;
    private static final float LANTERN_OPACITY = 0.35F;

    public VerseBodyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends EntityRenderState {
        public float partialTick;
        public VersePrototype.Look look = VersePrototype.Look.ORB;
        public boolean isStatic;
        public MagicSchool school = MagicSchool.ARCANE;
        public float radius;
        public float age;
        public int life;
        public int behaviours;
        public int wakes;
        public Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);
        public int seed;
        public double distanceSqr;
        public Vec3 cameraOffset = Vec3.ZERO;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VerseBodyEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        VersePrototype prototype = entity.prototype();
        state.partialTick = partialTick;
        state.look = prototype.look();
        state.isStatic = prototype.isStatic();
        state.school = entity.school();
        state.radius = entity.radius();
        state.age = entity.tickCount + partialTick;
        state.life = entity.life();
        state.behaviours = entity.behaviourMask();
        state.wakes = entity.wakeMask();
        state.direction = entity.direction();
        state.seed = entity.seed();
        Vec3 pos = entity.getPosition(partialTick);
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        state.distanceSqr = camera.distanceToSqr(pos);
        state.cameraOffset = camera.subtract(pos);
    }

    @Override
    public boolean shouldRender(VerseBodyEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        AABB box = entity.getBoundingBox().inflate(Math.max(1.0D, entity.radius() + 1.0D));
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(cameraX, cameraY, cameraZ)) && frustum.isVisible(box);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        VerseLooks.Row row = VerseLooks.of(state.look);
        SchoolMaterial material = SchoolMaterial.of(state.school);
        int rgb = material.variantColor(VerseLooks.PALETTE_VARIANT);
        int bright = material.variantColor(VerseLooks.GLYPH_VARIANT);
        FxContext ctx = new FxContext(poseStack, buffers, state.partialTick, entityRenderDispatcher.cameraOrientation(), state.cameraOffset)
                .timing(state.age, state.isStatic ? state.life : 0.0F, state.seed);
        ctx.detail = FxBudget.detailForDistance(3, state.distanceSqr);
        ctx.lod = FxBudget.lodForDistance(state.distanceSqr);
        float fade = state.isStatic ? ctx.fade() : 1.0F;
        float size = VerseLooks.drawnSize(row, state.radius);

        poseStack.pushPose();
        switch (row.shape()) {
            case BOLT -> {
                poseStack.pushPose();
                FilamentPainter.orientAlong(poseStack, state.direction);
                poseStack.translate(0.0D, 0.0D, -row.length() * 0.5D);
                FilamentPainter.beam(ctx, row.filament(), size, row.length(), rgb, fade * row.opacity(), 1.0F, row.count(), row.paramB());
                poseStack.popPose();
            }
            case ORB -> OrbPainter.billboard(ctx, row.orb(), size, rgb, fade * row.opacity(), ctx.phase, row.count(), row.paramB());
            case MARK -> {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                poseStack.translate(0.0D, 0.0D, -0.04D);
                MarkPainter.mark(ctx, row.mark(), size, rgb, fade * row.opacity(), ctx.phase, row.count(), row.paramB());
                poseStack.popPose();
            }
        }
        if (VerseBehaviours.has(state.behaviours, Behaviour.LANTERN)) {
            OrbPainter.billboard(ctx, FxKinds.Orb.BLOOM_FLASH, size * LANTERN_SCALE, bright, fade * LANTERN_OPACITY, ctx.phase, 1, 4);
        }
        drawGlyphs(ctx, poseStack, state, size, bright, fade);
        drawWakes(ctx, poseStack, state, size, fade);
        poseStack.popPose();
    }

    /** One small orb per behaviour the body carries, spaced round it and turning with its age. */
    private static void drawGlyphs(FxContext ctx, PoseStack poseStack, State state, float size, int bright, float fade) {
        Behaviour[] all = Behaviour.values();
        int carried = Integer.bitCount(state.behaviours);
        if (carried == 0) {
            return;
        }
        float orbit = size + GLYPH_ORBIT;
        int index = 0;
        for (Behaviour behaviour : all) {
            if (!VerseBehaviours.has(state.behaviours, behaviour)) {
                continue;
            }
            double angle = index * (Math.PI * 2.0D / carried) + state.age * GLYPH_TURN_PER_TICK;
            poseStack.pushPose();
            poseStack.translate(Math.cos(angle) * orbit, Math.sin(angle) * orbit * 0.5D, Math.sin(angle) * orbit);
            OrbPainter.billboard(ctx, VerseLooks.glyph(behaviour), GLYPH_SIZE, bright, fade * GLYPH_OPACITY, ctx.phase, 1, 8);
            poseStack.popPose();
            index++;
        }
    }

    /** One filament per wake, trailing behind the body along the line it came down. */
    private static void drawWakes(FxContext ctx, PoseStack poseStack, State state, float size, float fade) {
        if (state.wakes == 0 || state.isStatic) {
            return;
        }
        Vec3 back = state.direction.scale(-1.0D);
        for (Wake wake : Wake.values()) {
            if ((state.wakes & (1 << wake.ordinal())) == 0) {
                continue;
            }
            poseStack.pushPose();
            FilamentPainter.orientAlong(poseStack, back);
            FilamentPainter.beam(ctx, VerseLooks.wake(wake), size * WAKE_WIDTH, WAKE_LENGTH, VerseLooks.wakeColor(wake), fade * WAKE_OPACITY, 1.0F, 4, 6);
            poseStack.popPose();
        }
    }
}
```

- [ ] **Step 6: Bind the renderer**

In `src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java`, in `registerRenderers`, after the `ELDRITCH_CONSTRUCT` line (search for `MagicalEntities.ELDRITCH_CONSTRUCT.get()`), add:

```java
        event.registerEntityRenderer(MagicalEntities.VERSE_BODY.get(), com.efkrdnz.magical.client.renderer.verse.VerseBodyRenderer::new);
```

- [ ] **Step 7: Build, then look at it**

Run: `.\gradlew build` — Expected: green (`VerseLooksTest` and everything before it).

There is no way to spawn a body from the game yet (the service arrives in Task 7 and the commands in Task 11), so the visual check for this task is the build alone. The capture in Task 11 is where the look is judged.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/client/renderer/verse/VerseLooks.java src/test/java/com/efkrdnz/magical/client/renderer/verse/VerseLooksTest.java src/main/java/com/efkrdnz/magical/client/renderer/verse/VerseBodyRenderer.java src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java
git commit -m "feat: the verse body drawn, one look per prototype in its school's colour"
```

---

## Phase B: the Authority

Phase A ends with a body that can be flown by hand and by test, and nothing in the game that spawns one. Phase B wires the core and the body to a player: a Grimoire on the state, four skills that recite it, the names, the edit payload, the commands, and the removal of the Ledger kit those skills replace.

### Task 7: The Grimoire on the state, and the service that recites it

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java` (six anchors, listed in Step 3)
- Create: `src/test/java/com/efkrdnz/magical/magic/PlayerMagicStateGrimoireTest.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/LevelReciteWorld.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/IncantationService.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicGameplayEvents.java` (`onPlayerLogout` line 188, `onPlayerRespawn` line 201, `onPlayerDimensionChange` line 212)

**Interfaces:**
- Consumes: `Grimoire` (`SLOTS`, `incantation(int)`, `known()`, `learnAll`, `everyOtherSkipAndFlip()`, `clear()`, `copyFrom`, `save()`, `load(CompoundTag)`); `Incantation` (`entries()`, `breath()`, `size()`, `isEmpty()`, `write(List<ResourceLocation>, int, VerseCatalogue) -> boolean`, `copyFrom`); `ReciteSession.of(Incantation, VerseCatalogue)`, `unreadCount()`, `nextUnread()`; `Reciter.recite(ReciteSession, int breath, int mana, double costScale, ReciteWorld) -> RecitePlan`; `RecitePlan` (`root()`, `manaSpent()`, `frayed()`, `cooldownTicks()`, `bodies()`); `IncantationValidator.problems(List<ResourceLocation>, int, Set<ResourceLocation>, VerseCatalogue) -> List<Finding>`; `VerseContent.CATALOGUE`, `VerseContent.get(ResourceLocation)`; `VerseIds.of(String)`; `CastContext` (`player()`, `state()`, `definition()`, `stats()`, `aimDirection()`); `MagicSinService.adjustStatsBeforeCast(ServerPlayer, PlayerMagicState, MagicSkillResolvedStats)`, `spendManaForSkill(ServerPlayer, PlayerMagicState, int) -> boolean`, `afterSuccessfulCast(ServerPlayer, PlayerMagicState, MagicSkillDefinition)`; `MagicSkillResolvedStats.costScale()`; `PlayerMagicState.mana()`, `isSkillOnCooldown`, `setSkillCooldown`, `sync(ServerPlayer)`; `VerseBodySpawner.spawn` (Task 5); `SkillTargets.hostilesWithin`; `BloodDamageTypes.price(LivingEntity)`.
- Produces: `PlayerMagicState.grimoire() -> Grimoire`; `IncantationService.recite(CastContext, int slot)`, `setIncantation(ServerPlayer, int slot, int breath, List<ResourceLocation> ids) -> boolean`, `preview(ServerPlayer, PlayerMagicState, int slot) -> RecitePlan`, `parseIds(List<String>) -> List<ResourceLocation>` (null on a bad id), `forget(UUID)`, `resetSession(UUID, int slot)`, `SLOTS`; `LevelReciteWorld(ServerPlayer, PlayerMagicState, int slot)`. Task 8 adds `IncantationService.skillFor(int)` and the four registrations; Tasks 10 and 11 call `setIncantation`, `parseIds` and `preview`.

- [ ] **Step 1: Write the failing state test**

```java
package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.VerseContent;
import com.efkrdnz.magical.magic.incantation.VerseIds;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Grimoire rides on the state like the Fracture does: copied, saved, loaded, and emptied with the Authority. */
class PlayerMagicStateGrimoireTest {

    private static final List<ResourceLocation> VERSES = List.of(VerseIds.of("needle"), VerseIds.of("weight"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlayerMagicState written() {
        PlayerMagicState state = new PlayerMagicState();
        state.grimoire().learnAll(VERSES);
        assertTrue(state.grimoire().incantation(1).write(VERSES, 3, VerseContent.CATALOGUE), "the slot takes a known incantation");
        return state;
    }

    @Test
    void aCopyKeepsTheGrimoire() {
        PlayerMagicState copy = written().copy();
        assertEquals(2, copy.grimoire().incantation(1).size());
        assertEquals(3, copy.grimoire().incantation(1).breath());
        assertTrue(copy.grimoire().knows(VerseIds.of("weight")));
    }

    @Test
    void aSaveAndLoadKeepsTheGrimoire() {
        PlayerMagicState loaded = PlayerMagicState.load(written().save());
        assertEquals(2, loaded.grimoire().incantation(1).size());
        assertEquals(3, loaded.grimoire().incantation(1).breath());
        assertEquals(VerseIds.of("needle"), loaded.grimoire().incantation(1).entries().get(0).id());
        assertTrue(loaded.grimoire().knows(VerseIds.of("needle")));
    }

    @Test
    void clearingTheAuthorityEmptiesTheGrimoire() {
        PlayerMagicState state = written();
        state.clearAuthority();
        assertTrue(state.grimoire().incantation(1).isEmpty(), "the incantation went with the Authority");
        assertTrue(state.grimoire().known().isEmpty(), "and so did what it knew");
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.PlayerMagicStateGrimoireTest"`
Expected: compilation failure, `grimoire()` does not exist on `PlayerMagicState`.

- [ ] **Step 3: Put the Grimoire on the state**

Six edits to `src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java`, each anchored on text that exists at commit `cb3e3a5` (line numbers as of that commit; anchor by the text):

1. The field. After line 50, `private final com.efkrdnz.magical.magic.chaos.Fracture fracture = new com.efkrdnz.magical.magic.chaos.Fracture();`, add:

```java
    // The wielder half of the Authority of Mana: what they know and the four incantations they
    // wrote. Saved with the state; the recite sessions that read it are held by IncantationService
    // and are never saved, so a body in flight across a save is just a body.
    private final com.efkrdnz.magical.magic.incantation.Grimoire grimoire = new com.efkrdnz.magical.magic.incantation.Grimoire();
```

2. The accessor. After the `fracture()` accessor (lines 501-503), add:

```java
    public com.efkrdnz.magical.magic.incantation.Grimoire grimoire() {
        return grimoire;
    }
```

3. In `clearAuthority()` (line 1125), after `fracture.reset();`, add:

```java
        grimoire.clear();
```

4. In `copy()`, after `copy.fracture.copyFrom(fracture);` (line 2057), add:

```java
        copy.grimoire.copyFrom(grimoire);
```

5. In `save()`, after `tag.put("fracture", fracture.save());` (line 2168), add:

```java
        tag.put("grimoire", grimoire.save());
```

6. In `load(CompoundTag)`, after `state.fracture.load(tag.getList("fracture", com.efkrdnz.magical.magic.chaos.Fracture.tagType()));` (line 2331), add:

```java
        state.grimoire.load(tag.getCompound("grimoire"));
```

`Grimoire.load` of an empty compound (an old save with no key) leaves the Grimoire empty, so no migration is needed.

- [ ] **Step 4: Run the state test**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.PlayerMagicStateGrimoireTest"`
Expected: 3 tests pass. Also run `.\gradlew test --tests "com.efkrdnz.magical.magic.PlayerStateSyncCostTest"` - it prints the payload size and passes; the Grimoire adds a few bytes to an empty state.

- [ ] **Step 5: Write the level-backed world**

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.BloodDamageTypes;
import com.efkrdnz.magical.magic.service.SkillTargets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * The world as a clause verse sees it: the questions {@link ReciteWorld} asks, answered off a
 * real player and level. This and {@link IncantationService} are the only files in the package
 * that know Minecraft; the Reciter never does.
 */
public final class LevelReciteWorld implements ReciteWorld {
    private final ServerPlayer player;
    private final PlayerMagicState state;
    private final int slot;

    public LevelReciteWorld(ServerPlayer player, PlayerMagicState state, int slot) {
        this.player = player;
        this.state = state;
        this.slot = slot;
    }

    @Override
    public int enemiesWithin(double blocks) {
        return SkillTargets.hostilesWithin(player.serverLevel(), player, player.position(), blocks).size();
    }

    /** Anything in flight counts, another wielder's bodies included: a crowded sky is a crowded sky. */
    @Override
    public int projectilesWithin(double blocks) {
        return player.serverLevel().getEntities(player, player.getBoundingBox().inflate(blocks),
                entity -> entity instanceof Projectile || entity instanceof VerseBodyEntity).size();
    }

    @Override
    public double healthFraction() {
        float max = player.getMaxHealth();
        return max <= 0.0F ? 1.0D : Math.max(0.0D, Math.min(1.0D, player.getHealth() / max));
    }

    @Override
    public boolean everyOtherSkipAndFlip() {
        return state.grimoire().everyOtherSkipAndFlip();
    }

    @Override
    public int random(int bound) {
        return bound <= 0 ? 0 : player.getRandom().nextInt(bound);
    }

    @Override
    public List<Verse> allVerses() {
        return List.copyOf(VerseContent.CATALOGUE.all());
    }

    @Override
    public boolean isKnown(ResourceLocation id) {
        return state.grimoire().knows(id);
    }

    /** The verses written in the other three slots, in slot order, each as many times as it is written. */
    @Override
    public List<Verse> otherIncantationVerses() {
        List<Verse> verses = new ArrayList<>();
        for (int other = 0; other < Grimoire.SLOTS; other++) {
            if (other == slot) {
                continue;
            }
            for (Incantation.Entry entry : state.grimoire().incantation(other).entries()) {
                Verse verse = VerseContent.get(entry.id());
                if (verse != null) {
                    verses.add(verse);
                }
            }
        }
        return verses;
    }

    /** Blood Toll pays in flesh through the Blood school's true damage: no armour, no resistance, no barrier. */
    @Override
    public void payHealth(double halfHearts) {
        if (halfHearts > 0.0D) {
            player.hurt(BloodDamageTypes.price(player), (float) halfHearts);
        }
    }
}
```

- [ ] **Step 6: Write the service**

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.entity.verse.VerseBodySpawner;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Authority of Mana's runtime: a press on Incantation k recites slot k of the wielder's
 * Grimoire. The core plans the shot ({@link Reciter}); this bills it, spawns it, sets the cooldown
 * and tells the wielder what comes next.
 *
 * <p>Sessions (the deck, the hand, the discard) live here, keyed by UUID, and are never saved:
 * rebuilt from the Grimoire on first use, dropped on logout, respawn, a change of dimension
 * and when the Authority is cleared, as {@code PileService} drops its Piles. A session that
 * outlived its dimension is rebuilt too.
 */
public final class IncantationService {

    public static final int SLOTS = Grimoire.SLOTS;

    private record Held(ResourceKey<Level> dimension, ReciteSession[] sessions) {
    }

    private static final Map<UUID, Held> SESSIONS = new HashMap<>();

    private IncantationService() {
    }

    // ------------------------------------------------------------------ the press

    /** The self-managed cast of Incantation {@code slot + 1}: everything after the registry's unlock and authority checks. */
    public static void recite(CastContext ctx, int slot) {
        ServerPlayer player = ctx.player();
        if (player == null || slot < 0 || slot >= SLOTS) {
            return;
        }
        PlayerMagicState state = ctx.state();
        ResourceLocation skillId = ctx.definition().id();
        Incantation incantation = state.grimoire().incantation(slot);
        if (incantation.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_empty", slot + 1), true);
            return;
        }
        if (state.isSkillOnCooldown(skillId)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }
        MagicSkillResolvedStats stats = MagicSinService.adjustStatsBeforeCast(player, state, ctx.stats());
        ReciteSession session = session(player, state, slot);
        // The Reciter walks the deck in a loop bounded by ReciteCaps.MAX_STEPS and nests only as
        // deep as ReciteCaps.MAX_DEPTH, so the stack it needs is fixed whatever the incantation;
        // the step cap is what stops a Refrain of Ten reading a Recall All forever, not the stack.
        RecitePlan plan = Reciter.recite(session, incantation.breath(), state.mana(), stats.costScale(),
                new LevelReciteWorld(player, state, slot));
        // One call bills and refunds alike: a negative manaSpent (Wellspring) hands mana back and
        // is clamped to the pool by spendManaForSkill's own negative branch. The Reciter never
        // plans past state.mana(), so the bill cannot bounce; if a sin rule still refuses, the
        // shot is not spawned but the deck has moved - that is the price of trying to cast broke.
        boolean paid = MagicSinService.spendManaForSkill(player, state, plan.manaSpent());
        if (!paid) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            state.setSkillCooldown(skillId, plan.cooldownTicks());
            state.sync(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 look = ctx.aimDirection();
        Vec3 origin = player.getEyePosition().add(look.scale(0.4D)).add(0.0D, -0.1D, 0.0D);
        int spawned = VerseBodySpawner.spawn(level, player, plan.root(), origin, look, skillId).size();
        state.setSkillCooldown(skillId, plan.cooldownTicks());
        if (spawned > 0) {
            MagicSinService.afterSuccessfulCast(player, state, ctx.definition());
        }
        state.sync(player);
        readout(player, session, incantation, plan);
    }

    private static void readout(ServerPlayer player, ReciteSession session, Incantation incantation, RecitePlan plan) {
        ResourceLocation next = session.nextUnread();
        Component nextName = next == null
                ? Component.translatable("message.magical.incantation_rest")
                : Component.translatable("verse.magical." + next.getPath());
        player.displayClientMessage(Component.translatable("message.magical.incantation_next",
                nextName, session.unreadCount(), incantation.size()), true);
        if (plan.frayed()) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_frayed"), false);
        }
    }

    // ------------------------------------------------------------------ writing

    /** Writes an incantation into a slot after the validator has passed it, and starts that slot's session over. */
    public static boolean setIncantation(ServerPlayer player, int slot, int breath, List<ResourceLocation> ids) {
        if (slot < 0 || slot >= SLOTS || ids == null) {
            return false;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        List<IncantationValidator.Finding> findings = IncantationValidator.problems(ids, breath, state.grimoire().known(), VerseContent.CATALOGUE);
        if (!findings.isEmpty()) {
            IncantationValidator.Finding first = findings.get(0);
            String where = first.id() == null ? String.valueOf(first.index()) : first.id().toString();
            player.displayClientMessage(Component.translatable("message.magical.incantation_rejected",
                    first.problem().name().toLowerCase(java.util.Locale.ROOT) + " at " + where), false);
            return false;
        }
        if (!state.grimoire().incantation(slot).write(ids, breath, VerseContent.CATALOGUE)) {
            return false;
        }
        resetSession(player.getUUID(), slot);
        state.sync(player);
        player.displayClientMessage(Component.translatable("message.magical.incantation_written", slot + 1, ids.size(), breath), false);
        return true;
    }

    /** The plan a press would produce, without pressing: the uses are spent on a copy and the session is left alone. */
    public static RecitePlan preview(ServerPlayer player, PlayerMagicState state, int slot) {
        Incantation copy = new Incantation();
        copy.copyFrom(state.grimoire().incantation(slot));
        ReciteSession session = ReciteSession.of(copy, VerseContent.CATALOGUE);
        return Reciter.recite(session, copy.breath(), state.mana(), 1.0D, new LevelReciteWorld(player, state, slot));
    }

    /** {@code needle} and {@code magical:needle} both name the needle; anything unparsable makes the whole list null. */
    public static List<ResourceLocation> parseIds(List<String> raw) {
        if (raw == null) {
            return null;
        }
        List<ResourceLocation> ids = new ArrayList<>(raw.size());
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                return null;
            }
            ResourceLocation id = s.contains(":") ? ResourceLocation.tryParse(s) : (ResourceLocation.isValidPath(s) ? VerseIds.of(s) : null);
            if (id == null) {
                return null;
            }
            ids.add(id);
        }
        return ids;
    }

    // ------------------------------------------------------------------ sessions

    private static ReciteSession session(ServerPlayer player, PlayerMagicState state, int slot) {
        ResourceKey<Level> dimension = player.level().dimension();
        Held held = SESSIONS.get(player.getUUID());
        if (held == null || !held.dimension().equals(dimension)) {
            held = new Held(dimension, new ReciteSession[SLOTS]);
            SESSIONS.put(player.getUUID(), held);
        }
        ReciteSession session = held.sessions()[slot];
        if (session == null) {
            session = ReciteSession.of(state.grimoire().incantation(slot), VerseContent.CATALOGUE);
            held.sessions()[slot] = session;
        }
        return session;
    }

    public static void resetSession(UUID wielder, int slot) {
        Held held = SESSIONS.get(wielder);
        if (held != null && slot >= 0 && slot < SLOTS) {
            held.sessions()[slot] = null;
        }
    }

    /** Logout, respawn, a change of dimension, the Authority cleared: the deck goes, the Grimoire stays. */
    public static void forget(UUID wielder) {
        SESSIONS.remove(wielder);
    }
}
```

`message.magical.not_enough_mana` is an existing key (the casting service uses it); the new keys are added in Task 9, and the build stays green in the meantime because a missing lang key is only a raw string in the actionbar.

- [ ] **Step 7: Drop the sessions where the Pile is dropped**

In `src/main/java/com/efkrdnz/magical/magic/MagicGameplayEvents.java`:

In `onPlayerLogout`, directly after `com.efkrdnz.magical.magic.chaos.PileService.forget(player.getUUID());` (line 196), add:

```java
            com.efkrdnz.magical.magic.incantation.IncantationService.forget(player.getUUID());
```

In `onPlayerRespawn`, after `PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);` (line 203), add:

```java
            com.efkrdnz.magical.magic.incantation.IncantationService.forget(player.getUUID());
```

In `onPlayerDimensionChange`, after `PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);` (line 214), add:

```java
            com.efkrdnz.magical.magic.incantation.IncantationService.forget(player.getUUID());
```

The `clearAuthority` path needs no hook: the session is rebuilt from the Grimoire on the next press, and the Grimoire is empty by then, so the press refuses with the empty message.

- [ ] **Step 8: Build**

Run: `.\gradlew build`
Expected: green. Nothing calls `recite` yet; the four skills that do arrive in Task 8, with the gametests that press them.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java src/test/java/com/efkrdnz/magical/magic/PlayerMagicStateGrimoireTest.java src/main/java/com/efkrdnz/magical/magic/incantation/LevelReciteWorld.java src/main/java/com/efkrdnz/magical/magic/incantation/IncantationService.java src/main/java/com/efkrdnz/magical/magic/MagicGameplayEvents.java
git commit -m "feat: the Grimoire on the state, and the service that recites it"
```

---

### Task 8: The four incantations, the Authority of Mana's new kit

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicContent.java` (after `MANA_FORM`, line 100; `AUTHORITY_SKILLS`, lines 214-216)
- Modify: `src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java` (`AUTHORITY_OF_MANA`, lines 27-30)
- Modify: `src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java` (`register()`, after the `MANA_FORM` registration, line 62-63)
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/IncantationService.java` (add `skillFor`)
- Modify: `src/main/java/com/efkrdnz/magical/client/hud/HudGlyphs.java` (the static block, after line 36)
- Modify: `src/main/resources/assets/magical/lang/en_us.json` (lines 662 and after 668)
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/IncantationGameTests.java`

**Interfaces:**
- Consumes: `IncantationService.recite(CastContext, int)`, `forget(UUID)`, `setIncantation`, `preview` (Task 7); `SkillCastRegistry.register(MagicSkillDefinition, SkillCastHandler)`, `SkillCastRegistry.selfManaged(Consumer<CastContext>)`; `PlayerMagicState.setAuthority(ResourceLocation) -> boolean` (unlocks the authority's skills), `grimoire()`, `refillMana()`, `mana()`, `maxMana()`, `skillCooldown(ResourceLocation)`, `setSkillCooldown`; `MagicCastingService.castById(ServerPlayer, ResourceLocation, boolean)`; `ProjectileVerses.NEEDLE`, `ProjectileVerses.EMBER`, `MulticastVerses.COUPLET` (the id constants); `VerseBodyEntity.ownedBy` (Task 5).
- Produces: `MagicContent.INCANTATION_1..INCANTATION_4` (`MagicSkillDefinition`, ids `magical:incantation_1..4`); `IncantationService.skillFor(int slot) -> MagicSkillDefinition`; four lang keys; the authority's new description. Task 11's `magical-debug recite` calls `skillFor`; Task 12 removes the three old skills from the two sets touched here.

The three Ledger skills stay registered until Task 12, in both `AUTHORITY_SKILLS` and the authority's grant list, because `AuthorityGrantTest` requires the union of every authority's grants to equal `AUTHORITY_SKILLS` exactly, and the removal is one atomic commit at the end.

- [ ] **Step 1: Write the failing gametests**

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicCastingService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A press on Incantation I through the real cast path: the registry's gates, the service's bill,
 * the spawner's bodies. What a unit test holds about the Reciter is held in the core; this is
 * the binding - that a press spends mana and uses, that an empty slot refuses, that a session
 * survives a press and not a forget.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class IncantationGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);
    private static final int SLOT = 0;
    private static final ResourceLocation SKILL = MagicContent.INCANTATION_1.id();

    private IncantationGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** A survival wielder of the Authority of Mana, on the floor, full of mana, knowing every verse. */
    private static ServerPlayer wielder(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, STAND, "mana-test");
        // Look along +x, into the two and a half blocks of air before the far wall.
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        PlayerMagicState state = state(player);
        state.setAuthority(AuthorityContent.MANA);
        state.refillMana();
        state.grimoire().learnAll(VerseContent.CATALOGUE.all().stream().map(Verse::id).toList());
        IncantationService.forget(player.getUUID());
        return player;
    }

    private static void write(ServerPlayer player, int breath, ResourceLocation... ids) {
        boolean written = state(player).grimoire().incantation(SLOT).write(List.of(ids), breath, VerseContent.CATALOGUE);
        if (!written) {
            throw new IllegalStateException("the test incantation did not validate");
        }
        IncantationService.resetSession(player.getUUID(), SLOT);
    }

    private static int usesLeft(ServerPlayer player, int index) {
        return state(player).grimoire().incantation(SLOT).entries().get(index).usesRemaining();
    }

    private static List<VerseBodyEntity> bodies(GameTestHelper helper, ServerPlayer player) {
        return VerseBodyEntity.ownedBy(helper.getLevel(), player, helper.getBounds().inflate(2.0D));
    }

    private static void press(ServerPlayer player) {
        MagicCastingService.castById(player, SKILL, false);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "incantation_1")
    public static void aPressRecitesTheWrittenIncantation(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        write(player, 1, MulticastVerses.COUPLET, ProjectileVerses.NEEDLE, ProjectileVerses.NEEDLE);
        int max = state(player).maxMana();
        helper.runAtTickTime(1, () -> press(player));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(bodies(helper, player).size() == 2, "a couplet of needles is two bodies: " + bodies(helper, player).size());
            // A needle is 4 mana and a couplet nothing, at the cost scale a fresh state resolves to.
            helper.assertTrue(state(player).mana() == max - 8, "two needles billed: " + state(player).mana() + " of " + max);
            helper.assertTrue(state(player).skillCooldown(SKILL) > 0, "the beat became the cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "incantation_2")
    public static void anEmptyIncantationRefusesAndCostsNothing(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        int max = state(player).maxMana();
        helper.runAtTickTime(1, () -> press(player));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(bodies(helper, player).isEmpty(), "nothing written, nothing flies");
            helper.assertTrue(state(player).mana() == max, "and nothing billed");
            helper.assertTrue(state(player).skillCooldown(SKILL) == 0, "and no cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "incantation_3")
    public static void aWrittenIncantationMustBeKnown(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        state(player).grimoire().clear();
        helper.runAtTickTime(1, () -> {
            boolean unknown = IncantationService.setIncantation(player, SLOT, 1, List.of(ProjectileVerses.NEEDLE));
            helper.assertFalse(unknown, "a verse never learned cannot be written");
            state(player).grimoire().learnAll(List.of(ProjectileVerses.NEEDLE));
            boolean known = IncantationService.setIncantation(player, SLOT, 1, List.of(ProjectileVerses.NEEDLE));
            helper.assertTrue(known, "and can be once it is");
            helper.assertTrue(state(player).grimoire().incantation(SLOT).size() == 1, "the slot holds it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "incantation_4")
    public static void aPreviewSpendsNoUsesAndAPressDoes(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        write(player, 1, ProjectileVerses.EMBER);
        helper.runAtTickTime(1, () -> {
            RecitePlan plan = IncantationService.preview(player, state(player), SLOT);
            helper.assertTrue(plan.bodies().size() == 1, "the preview plans the ember");
            helper.assertTrue(usesLeft(player, 0) == 15, "and spends none of its fifteen uses: " + usesLeft(player, 0));
            press(player);
        });
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(usesLeft(player, 0) == 14, "a press spends one: " + usesLeft(player, 0));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "incantation_5")
    public static void aSecondPressPlaysTheNextVerseAndAForgetStartsOver(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        // Breath one: the ember, then the needle, then the deck is spent and shuffles back.
        write(player, 1, ProjectileVerses.EMBER, ProjectileVerses.NEEDLE);
        helper.runAtTickTime(1, () -> press(player));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(usesLeft(player, 0) == 14, "the first press read the ember");
            state(player).setSkillCooldown(SKILL, 0);
            IncantationService.forget(player.getUUID());
            press(player);
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(usesLeft(player, 0) == 13, "forgotten, the session starts at the ember again: " + usesLeft(player, 0));
            state(player).setSkillCooldown(SKILL, 0);
            press(player);
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(usesLeft(player, 0) == 13, "kept, the session moves on to the needle: " + usesLeft(player, 0));
            helper.succeed();
        });
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `.\gradlew runGameTestServer`
Expected: compilation failure, `MagicContent.INCANTATION_1` does not exist.

- [ ] **Step 3: Register the four skills**

In `src/main/java/com/efkrdnz/magical/magic/MagicContent.java`, directly after the `MANA_FORM` line (line 100) and before the `// The Authority of Chaos.` comment, add:

```java
    // The four incantations replace the Ledger kit below (removed once these are live). Base mana
    // and cooldown are zero on purpose: the Reciter bills the verses it reads and turns the beat
    // into the cooldown, so the definition carries no price of its own. Same ARCANE reasoning.
    public static final MagicSkillDefinition INCANTATION_1 = register("incantation_1", MagicSchool.ARCANE, MagicSkillType.BURST, -6, 0, 0.0F, 0.0F, 1.0F, 0, 0, 20, 0.0F, 0, 0xF0F4FF, MagicAttribute.ARCANE);
    public static final MagicSkillDefinition INCANTATION_2 = register("incantation_2", MagicSchool.ARCANE, MagicSkillType.BURST, -6, 0, 0.0F, 0.0F, 1.0F, 0, 0, 20, 0.0F, 0, 0xF0F4FF, MagicAttribute.ARCANE);
    public static final MagicSkillDefinition INCANTATION_3 = register("incantation_3", MagicSchool.ARCANE, MagicSkillType.BURST, -6, 0, 0.0F, 0.0F, 1.0F, 0, 0, 20, 0.0F, 0, 0xF0F4FF, MagicAttribute.ARCANE);
    public static final MagicSkillDefinition INCANTATION_4 = register("incantation_4", MagicSchool.ARCANE, MagicSkillType.BURST, -6, 0, 0.0F, 0.0F, 1.0F, 0, 0, 20, 0.0F, 0, 0xF0F4FF, MagicAttribute.ARCANE);
```

In the same file, change the `AUTHORITY_SKILLS` line `OPEN_LEDGER.id(), WRIT.id(), MANA_FORM.id(),` (line 215) to:

```java
            OPEN_LEDGER.id(), WRIT.id(), MANA_FORM.id(),
            INCANTATION_1.id(), INCANTATION_2.id(), INCANTATION_3.id(), INCANTATION_4.id(),
```

In `src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java`, change the grant list of `AUTHORITY_OF_MANA` (line 30) from

```java
            List.of(MagicContent.OPEN_LEDGER.id(), MagicContent.WRIT.id(), MagicContent.MANA_FORM.id()));
```

to

```java
            List.of(MagicContent.OPEN_LEDGER.id(), MagicContent.WRIT.id(), MagicContent.MANA_FORM.id(),
                    MagicContent.INCANTATION_1.id(), MagicContent.INCANTATION_2.id(),
                    MagicContent.INCANTATION_3.id(), MagicContent.INCANTATION_4.id()));
```

- [ ] **Step 4: Bind the presses**

In `src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java`, inside `register()`, directly after the `MANA_FORM` registration (the statement ending `ManaFormService.toggle(ctx.player(), ctx.state())));`), add:

```java
        // A press on Incantation k recites slot k. Self-managed: the service bills the verses it
        // read and sets the cooldown from the beat, so the registry's mana and cooldown never apply.
        SkillCastRegistry.register(MagicContent.INCANTATION_1, SkillCastRegistry.selfManaged(ctx ->
                com.efkrdnz.magical.magic.incantation.IncantationService.recite(ctx, 0)));
        SkillCastRegistry.register(MagicContent.INCANTATION_2, SkillCastRegistry.selfManaged(ctx ->
                com.efkrdnz.magical.magic.incantation.IncantationService.recite(ctx, 1)));
        SkillCastRegistry.register(MagicContent.INCANTATION_3, SkillCastRegistry.selfManaged(ctx ->
                com.efkrdnz.magical.magic.incantation.IncantationService.recite(ctx, 2)));
        SkillCastRegistry.register(MagicContent.INCANTATION_4, SkillCastRegistry.selfManaged(ctx ->
                com.efkrdnz.magical.magic.incantation.IncantationService.recite(ctx, 3)));
```

In `src/main/java/com/efkrdnz/magical/magic/incantation/IncantationService.java`, add after `SLOTS`:

```java
    /** The skill that recites a slot, for the debug command; slot 0 is Incantation I. */
    public static com.efkrdnz.magical.magic.MagicSkillDefinition skillFor(int slot) {
        return switch (slot) {
            case 1 -> com.efkrdnz.magical.magic.MagicContent.INCANTATION_2;
            case 2 -> com.efkrdnz.magical.magic.MagicContent.INCANTATION_3;
            case 3 -> com.efkrdnz.magical.magic.MagicContent.INCANTATION_4;
            default -> com.efkrdnz.magical.magic.MagicContent.INCANTATION_1;
        };
    }
```

- [ ] **Step 5: A card icon for each**

A skill with no visual profile emblem needs a stamp (see `HudGlyphs`). In `src/main/java/com/efkrdnz/magical/client/hud/HudGlyphs.java`, in the static block, after `fallback("circle_arsenal", StampId.RING.atlasCell());` (line 36), add:

```java
        // The four incantations: one plain counting mark each, I to IV.
        fallback("incantation_1", StampId.DOT.atlasCell());
        fallback("incantation_2", StampId.BAR.atlasCell());
        fallback("incantation_3", StampId.TRIANGLE.atlasCell());
        fallback("incantation_4", StampId.SQUARE.atlasCell());
```

- [ ] **Step 6: Name them**

In `src/main/resources/assets/magical/lang/en_us.json`, replace the value of `authority.magical.authority_of_mana.desc` (line 662) with the spec's:

```json
  "authority.magical.authority_of_mana.desc": "The right to compose magic from nothing. Not to cast a spell someone else finished, but to write one, verse by verse, and have the world read it back.",
```

and directly after the `skill.magical.mana_form.desc` line (line 668) add:

```json
  "skill.magical.incantation_1": "Incantation I",
  "skill.magical.incantation_1.desc": "Authority of Mana: recite the first incantation in your Grimoire. Each press reads the next verses in it, in breaths, and bills the mana they name; the beat between them is the cooldown. Write it with /magical incantation set 1.",
  "skill.magical.incantation_2": "Incantation II",
  "skill.magical.incantation_2.desc": "Authority of Mana: recite the second incantation in your Grimoire. Write it with /magical incantation set 2.",
  "skill.magical.incantation_3": "Incantation III",
  "skill.magical.incantation_3.desc": "Authority of Mana: recite the third incantation in your Grimoire. Write it with /magical incantation set 3.",
  "skill.magical.incantation_4": "Incantation IV",
  "skill.magical.incantation_4.desc": "Authority of Mana: recite the fourth incantation in your Grimoire. Write it with /magical incantation set 4.",
```

- [ ] **Step 7: Build, then run the gametests**

Run: `.\gradlew build` — Expected: green. `AuthorityGrantTest` passes because both sets grew by the same four; `ContentLangKeysTest` because every new skill has its two keys; `VisualProfilesTest` because the four share a colour and a profile with nothing that collides (they are cards with stamps, not cast circles of their own).
Run: `.\gradlew runGameTestServer` — Expected: the five `IncantationGameTests` pass with everything else. If `aPressRecitesTheWrittenIncantation` fails on the mana line alone, print `MagicSinService.adjustStatsBeforeCast(...).costScale()` for a fresh state: a passive that scales the price at level one would show there, and the assertion should then compare against `max - Math.round(8 * costScale)`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/MagicContent.java src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java src/main/java/com/efkrdnz/magical/magic/incantation/IncantationService.java src/main/java/com/efkrdnz/magical/client/hud/HudGlyphs.java src/main/resources/assets/magical/lang/en_us.json src/main/java/com/efkrdnz/magical/magic/incantation/IncantationGameTests.java
git commit -m "feat: four incantations, the Authority of Mana's new kit"
```

---

### Task 9: Every verse named and described

**Files:**
- Modify: `src/main/resources/assets/magical/lang/en_us.json` (after the `skill.magical.incantation_4.desc` line Task 8 added)
- Create: `src/test/java/com/efkrdnz/magical/magic/incantation/VerseLangKeysTest.java`

**Interfaces:**
- Consumes: `VerseContent.CATALOGUE.all()`, `Verse.path()`.
- Produces: `verse.magical.<path>` and `verse.magical.<path>.desc` for all hundred verses (the service's readout and Plan 4's editor read them), `entity.magical.verse_body`, and the nine `message.magical.incantation_*` keys the service and the commands use.

Names are the spec's (§9), descriptions are its effects in a sentence. The test holds the catalogue and the file to each other, so a verse added without a name fails the build.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every verse in the catalogue has a name and a description in the language file; every message the service sends has a line. */
class VerseLangKeysTest {

    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";
    private static final List<String> MESSAGES = List.of(
            "message.magical.incantation_empty", "message.magical.incantation_next", "message.magical.incantation_rest",
            "message.magical.incantation_frayed", "message.magical.incantation_rejected", "message.magical.incantation_written",
            "message.magical.incantation_known", "message.magical.incantation_unknown_verse", "message.magical.incantation_preview",
            "entity.magical.verse_body");

    @Test
    void everyVerseHasANameAndADescription() throws IOException {
        String lang = read();
        List<String> missing = new ArrayList<>();
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            String key = "verse.magical." + verse.path();
            if (!lang.contains('"' + key + '"')) {
                missing.add(key);
            }
            if (!lang.contains('"' + key + ".desc\"")) {
                missing.add(key + ".desc");
            }
        }
        assertTrue(missing.isEmpty(), "verses without a line: " + missing);
    }

    @Test
    void everyMessageTheServiceSendsHasALine() throws IOException {
        String lang = read();
        List<String> missing = new ArrayList<>();
        for (String key : MESSAGES) {
            if (!lang.contains('"' + key + '"')) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "messages without a line: " + missing);
    }

    private static String read() throws IOException {
        try (InputStream in = VerseLangKeysTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(in, "missing language file");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.VerseLangKeysTest"`
Expected: FAIL, "verses without a line: [verse.magical.needle, ...]" (two hundred keys) and the ten messages.

- [ ] **Step 3: Write the lines**

In `src/main/resources/assets/magical/lang/en_us.json`, directly after the `skill.magical.incantation_4.desc` line, add every line below (each ends in a comma; the file's last entry is elsewhere):

```json
  "entity.magical.verse_body": "Verse",
  "message.magical.incantation_empty": "Incantation %s has nothing written in it.",
  "message.magical.incantation_next": "Next: %s  ·  %s of %s unread",
  "message.magical.incantation_rest": "a rest",
  "message.magical.incantation_frayed": "The incantation frayed: a verse asked for what was not there.",
  "message.magical.incantation_rejected": "The incantation was refused: %s",
  "message.magical.incantation_written": "Incantation %s: %s verses, breath %s.",
  "message.magical.incantation_known": "Known: %s",
  "message.magical.incantation_unknown_verse": "No verse is called %s.",
  "message.magical.incantation_preview": "Incantation %s would cast %s bodies for %s mana, cooling %s ticks.",
  "verse.magical.needle": "Needle",
  "verse.magical.needle.desc": "A thin fast bolt: 3 damage, forty ticks of flight, a little tighter and a little likelier to crit than most.",
  "verse.magical.needle_latch": "Needle with Latch",
  "verse.magical.needle_latch.desc": "A needle that reads the next verse where it hits.",
  "verse.magical.needle_fuse": "Needle with Fuse",
  "verse.magical.needle_fuse.desc": "A needle that reads the next verse four ticks into its flight, wherever it is.",
  "verse.magical.needle_twin_latch": "Twin-Latch Needle",
  "verse.magical.needle_twin_latch.desc": "A needle that reads the next two verses where it hits.",
  "verse.magical.orb": "Orb",
  "verse.magical.orb.desc": "A slow round shot: 5 damage, sixty ticks of flight.",
  "verse.magical.orb_latch": "Orb with Latch",
  "verse.magical.orb_latch.desc": "An orb that reads the next verse where it hits.",
  "verse.magical.orb_fuse": "Orb with Fuse",
  "verse.magical.orb_fuse.desc": "An orb that reads the next verse eight ticks into its flight.",
  "verse.magical.orb_epitaph": "Orb with Epitaph",
  "verse.magical.orb_epitaph.desc": "An orb that reads the next verse when its flight runs out.",
  "verse.magical.shard": "Shard",
  "verse.magical.shard.desc": "A heavy shot: 9 damage, slow, and it shoves what it hits.",
  "verse.magical.ember": "Ember",
  "verse.magical.ember.desc": "A ball of fire: 6 damage, an explosion two blocks wide, and it sets what it hits alight. Fifteen uses.",
  "verse.magical.arc_bolt": "Arc Bolt",
  "verse.magical.arc_bolt.desc": "A fast bolt of lightning: 7 damage, twenty ticks of flight, and a shock to whatever it strikes.",
  "verse.magical.balm_dart": "Balm Dart",
  "verse.magical.balm_dart.desc": "A dart that heals 4 to whatever it reaches. Twenty uses.",
  "verse.magical.whisper": "Whisper",
  "verse.magical.whisper.desc": "A tiny shot that lasts four ticks and costs one mana; it sets the beat to nothing and shortens the rest.",
  "verse.magical.blink_dart": "Blink Dart",
  "verse.magical.blink_dart.desc": "A dart that carries you to wherever it ends.",
  "verse.magical.wild_bolt": "Wild Bolt",
  "verse.magical.wild_bolt.desc": "Reads a random projectile verse you know in its place.",
  "verse.magical.detonation": "Detonation",
  "verse.magical.detonation.desc": "An explosion three blocks wide, a block ahead of the hand or wherever the verse before it landed. It spares no one.",
  "verse.magical.rime_ring": "Rime Ring",
  "verse.magical.rime_ring.desc": "A ring three blocks wide that stands for a hundred ticks and freezes everything in it every ten. Fifteen uses.",
  "verse.magical.storm_ring": "Storm Ring",
  "verse.magical.storm_ring.desc": "A ring three blocks wide that stands for a hundred ticks and shocks everything in it every ten. Fifteen uses.",
  "verse.magical.balm_ring": "Balm Ring",
  "verse.magical.balm_ring.desc": "A ring three blocks wide that stands for a hundred ticks and heals everyone in it every twenty. Six uses.",
  "verse.magical.uplift_ring": "Uplift Ring",
  "verse.magical.uplift_ring.desc": "A ring three blocks wide that stands for a hundred ticks and lifts everything in it off the ground.",
  "verse.magical.void_pit": "Void Pit",
  "verse.magical.void_pit.desc": "A pit that stands for eighty ticks and pulls bodies and beings toward its centre, 1 damage every ten ticks. Three uses, and never more.",
  "verse.magical.held_word": "Held Word",
  "verse.magical.held_word.desc": "A word held in the air for twenty ticks, then it reads the next three verses where it hung.",
  "verse.magical.weight": "Weight",
  "verse.magical.weight.desc": "The next body hits for 2.5 more.",
  "verse.magical.ballast": "Ballast",
  "verse.magical.ballast.desc": "The next body hits for 6 more and flies at a third of its speed.",
  "verse.magical.haste": "Haste",
  "verse.magical.haste.desc": "The next body flies two and a half times as fast.",
  "verse.magical.endurance": "Endurance",
  "verse.magical.endurance.desc": "The next body flies twenty-five ticks longer.",
  "verse.magical.true_aim": "True Aim",
  "verse.magical.true_aim.desc": "The next body flies straighter: sixty degrees off the spread.",
  "verse.magical.keen_edge": "Keen Edge",
  "verse.magical.keen_edge.desc": "The next body is fifteen points likelier to crit, and a crit is five times the damage.",
  "verse.magical.ricochet": "Ricochet",
  "verse.magical.ricochet.desc": "The next body bounces off walls ten times before it ends.",
  "verse.magical.sink": "Sink",
  "verse.magical.sink.desc": "The next body falls as it flies.",
  "verse.magical.loft": "Loft",
  "verse.magical.loft.desc": "The next body rises as it flies.",
  "verse.magical.volatile": "Volatile",
  "verse.magical.volatile.desc": "The next body explodes when it ends, a block and a half wider and harder than it would have, and flies a little slower for it.",
  "verse.magical.second_wind": "Second Wind",
  "verse.magical.second_wind.desc": "Seven ticks off the rest at the end of the incantation.",
  "verse.magical.blunt": "Blunt",
  "verse.magical.blunt.desc": "The next body does no damage at all, and flies ninety ticks longer.",
  "verse.magical.seeker": "Seeker",
  "verse.magical.seeker.desc": "The next body turns toward the nearest hostile within twelve blocks.",
  "verse.magical.sightline": "Sightline",
  "verse.magical.sightline.desc": "The next body flies at whatever the crosshair is on, whatever the fan says.",
  "verse.magical.puncture": "Puncture",
  "verse.magical.puncture.desc": "The next body passes through what it hits and goes on; it hits for 1.5 less, and it can hit you.",
  "verse.magical.serpentine": "Serpentine",
  "verse.magical.serpentine.desc": "The next body weaves side to side, twice as fast.",
  "verse.magical.gyre": "Gyre",
  "verse.magical.gyre.desc": "The next body circles you instead of flying, widening as it goes.",
  "verse.magical.errant": "Errant",
  "verse.magical.errant.desc": "The next body turns every five ticks on a whim.",
  "verse.magical.relay": "Relay",
  "verse.magical.relay.desc": "When the next body hits, one like it flies on from there, once. It is a little shorter, weaker and wider for it.",
  "verse.magical.twin_path": "Twin Path",
  "verse.magical.twin_path.desc": "The next body is two, twelve degrees apart.",
  "verse.magical.naught": "Naught",
  "verse.magical.naught.desc": "The next body ends the moment it is cast. Its Epitaph still reads.",
  "verse.magical.undying": "Undying",
  "verse.magical.undying.desc": "The next body flies until it hits something, a minute at most. Three uses.",
  "verse.magical.wellspring": "Wellspring",
  "verse.magical.wellspring.desc": "Twelve mana back into the pool.",
  "verse.magical.flame_wreath": "Flame Wreath",
  "verse.magical.flame_wreath.desc": "The next body is fire, and sets what it hits alight.",
  "verse.magical.rime_wreath": "Rime Wreath",
  "verse.magical.rime_wreath.desc": "The next body is water, and freezes what it hits.",
  "verse.magical.storm_wreath": "Storm Wreath",
  "verse.magical.storm_wreath.desc": "The next body shocks what it hits, and hits for a little more.",
  "verse.magical.umbral_wreath": "Umbral Wreath",
  "verse.magical.umbral_wreath.desc": "The next body is dark, and withers what it hits.",
  "verse.magical.fire_wake": "Fire Wake",
  "verse.magical.fire_wake.desc": "The next body leaves fire in its wake: anything it passes is set alight.",
  "verse.magical.water_wake": "Water Wake",
  "verse.magical.water_wake.desc": "The next body leaves water in its wake: anything it passes is put out.",
  "verse.magical.frost_wake": "Frost Wake",
  "verse.magical.frost_wake.desc": "The next body leaves frost in its wake: anything it passes is slowed.",
  "verse.magical.lantern": "Lantern",
  "verse.magical.lantern.desc": "The next body glows.",
  "verse.magical.uplift": "Uplift",
  "verse.magical.uplift.desc": "The next body lifts what it hits off the ground.",
  "verse.magical.displace": "Displace",
  "verse.magical.displace.desc": "The next body throws what it hits a few blocks in a random direction.",
  "verse.magical.bursting_ricochet": "Bursting Ricochet",
  "verse.magical.bursting_ricochet.desc": "The next body bounces once more, and bursts at every bounce.",
  "verse.magical.wild_mark": "Wild Mark",
  "verse.magical.wild_mark.desc": "Reads a random modifier verse you know in its place.",
  "verse.magical.couplet": "Couplet",
  "verse.magical.couplet.desc": "The next two verses are cast together.",
  "verse.magical.tercet": "Tercet",
  "verse.magical.tercet.desc": "The next three verses are cast together.",
  "verse.magical.quatrain": "Quatrain",
  "verse.magical.quatrain.desc": "The next four verses are cast together.",
  "verse.magical.octave": "Octave",
  "verse.magical.octave.desc": "The next eight verses are cast together.",
  "verse.magical.loose_couplet": "Loose Couplet",
  "verse.magical.loose_couplet.desc": "The next two verses are cast together, ten degrees looser.",
  "verse.magical.loose_tercet": "Loose Tercet",
  "verse.magical.loose_tercet.desc": "The next three verses are cast together, twenty degrees looser.",
  "verse.magical.cleft": "Cleft",
  "verse.magical.cleft.desc": "The next two verses are cast together, one to each side, forty-five degrees apart.",
  "verse.magical.trident": "Trident",
  "verse.magical.trident.desc": "The next three verses are cast together, fanned twenty degrees to either side.",
  "verse.magical.mirror": "Mirror",
  "verse.magical.mirror.desc": "The next two verses are cast together, one ahead and one behind you.",
  "verse.magical.column": "Column",
  "verse.magical.column.desc": "The next three verses are cast together, one ahead and one to each side.",
  "verse.magical.pentacle": "Pentacle",
  "verse.magical.pentacle.desc": "The next five verses are cast together, all round you.",
  "verse.magical.hexad": "Hexad",
  "verse.magical.hexad.desc": "The next six verses are cast together, every sixty degrees.",
  "verse.magical.epic": "Epic",
  "verse.magical.epic.desc": "Everything left unread is cast together. Ten uses.",
  "verse.magical.far_word": "Far Word",
  "verse.magical.far_word.desc": "A silent word flies ten ticks ahead and reads the next verse where it ends.",
  "verse.magical.step_word": "Step Word",
  "verse.magical.step_word.desc": "A silent word flies out, carries you to where it ends, and reads the next verse there.",
  "verse.magical.near_word": "Near Word",
  "verse.magical.near_word.desc": "The next verse is cast on you rather than from your hand.",
  "verse.magical.fresh_page": "Fresh Page",
  "verse.magical.fresh_page.desc": "Everything already read goes back into the pile, and the rest is eight ticks shorter.",
  "verse.magical.blood_toll": "Blood Toll",
  "verse.magical.blood_toll.desc": "Thirty mana back, paid in two hearts of blood that nothing can soften. Beat and rest both shorter.",
  "verse.magical.refrain_2": "Refrain of Two",
  "verse.magical.refrain_2.desc": "The next verse is read twice, a little weaker each time.",
  "verse.magical.refrain_3": "Refrain of Three",
  "verse.magical.refrain_3.desc": "The next verse is read three times, weaker each time.",
  "verse.magical.refrain_4": "Refrain of Four",
  "verse.magical.refrain_4.desc": "The next verse is read four times, weaker each time.",
  "verse.magical.refrain_10": "Refrain of Ten",
  "verse.magical.refrain_10.desc": "The next verse is read ten times, much weaker each time. Five uses.",
  "verse.magical.impose_latch": "Impose Latch",
  "verse.magical.impose_latch.desc": "The next body gains a Latch: it reads the verse after it where it hits.",
  "verse.magical.impose_fuse": "Impose Fuse",
  "verse.magical.impose_fuse.desc": "The next body gains a Fuse: it reads the verse after it seven ticks into its flight.",
  "verse.magical.impose_epitaph": "Impose Epitaph",
  "verse.magical.impose_epitaph.desc": "The next body gains an Epitaph: it reads the verse after it when its flight runs out.",
  "verse.magical.recall_first": "Recall First",
  "verse.magical.recall_first.desc": "Reads the first verse of the incantation again.",
  "verse.magical.recall_last": "Recall Last",
  "verse.magical.recall_last.desc": "Reads the last verse of the incantation again.",
  "verse.magical.recall_pair": "Recall Pair",
  "verse.magical.recall_pair.desc": "Reads the second and third verses of the incantation again.",
  "verse.magical.recall_all": "Recall All",
  "verse.magical.recall_all.desc": "Reads the whole incantation again, at once.",
  "verse.magical.recall_modifiers": "Recall Modifiers",
  "verse.magical.recall_modifiers.desc": "Reads every modifier in the incantation again, then the next verse.",
  "verse.magical.recall_projectiles": "Recall Projectiles",
  "verse.magical.recall_projectiles.desc": "Reads every projectile in the incantation again.",
  "verse.magical.recall_statics": "Recall Statics",
  "verse.magical.recall_statics.desc": "Reads every static in the incantation again, then the next verse.",
  "verse.magical.wild_recall": "Wild Recall",
  "verse.magical.wild_recall.desc": "Reads a random verse from one of your other incantations, then the next verse.",
  "verse.magical.clause_outnumbered": "Clause: Outnumbered",
  "verse.magical.clause_outnumbered.desc": "If six or more hostiles are within sixteen blocks, read on; otherwise skip to Otherwise.",
  "verse.magical.clause_crowded": "Clause: Crowded Sky",
  "verse.magical.clause_crowded.desc": "If twelve or more projectiles are within sixteen blocks, read on; otherwise skip to Otherwise.",
  "verse.magical.clause_wounded": "Clause: Wounded",
  "verse.magical.clause_wounded.desc": "If you are at a quarter of your health or less, read on; otherwise skip to Otherwise.",
  "verse.magical.clause_every_other": "Clause: Every Other",
  "verse.magical.clause_every_other.desc": "Read on every other time; otherwise skip to Otherwise.",
  "verse.magical.otherwise": "Otherwise",
  "verse.magical.otherwise.desc": "Where a failed clause lands. Read straight through, it reads the next verse.",
  "verse.magical.end_clause": "End Clause",
  "verse.magical.end_clause.desc": "Where a passed clause stops skipping. Read straight through, it reads the next verse.",
  "verse.magical.wild_verse": "Wild Verse",
  "verse.magical.wild_verse.desc": "Reads a random verse you know in its place.",
  "verse.magical.blind_draw": "Blind Draw",
  "verse.magical.blind_draw.desc": "Reads a random verse from the incantation, read or unread, and spends its use.",
  "verse.magical.blind_trio": "Blind Trio",
  "verse.magical.blind_trio.desc": "Reads three random verses from the incantation, read or unread, and spends their uses.",
  "verse.magical.reprise": "Reprise",
  "verse.magical.reprise.desc": "Reads the rest of the incantation, then reads it again.",
```

- [ ] **Step 4: Run the test**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.VerseLangKeysTest"`
Expected: 2 tests pass. Then `.\gradlew build` - green; `HudLangKeysTest` and `ContentLangKeysTest` unaffected.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/assets/magical/lang/en_us.json src/test/java/com/efkrdnz/magical/magic/incantation/VerseLangKeysTest.java
git commit -m "feat: every verse named and described"
```

---

### Task 10: The incantation edit payload, capped on the wire

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/SetIncantationPayloadCaps.java`
- Create: `src/main/java/com/efkrdnz/magical/network/SetIncantationPayload.java`
- Modify: `src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java` (the `playToServer` chain after the `ApplyFracturePayload` registration, line 218-223; the senders after `sendFracture`, line 402-408)
- Create: `src/test/java/com/efkrdnz/magical/magic/incantation/IncantationServiceTest.java`

**Interfaces:**
- Consumes: `IncantationService.setIncantation(ServerPlayer, int, int, List<ResourceLocation>)`, `parseIds(List<String>)`; `ReciteCaps.MAX_VERSES` (20), `MIN_BREATH` (1), `MAX_BREATH` (8).
- Produces: `SetIncantationPayload(int slot, int breath, List<String> ids)` with `TYPE` `magical:set_incantation` and `STREAM_CODEC`; `MagicalNetwork.sendIncantation(int slot, int breath, List<String> ids)` for Plan 4's editor screen. Nothing in this plan sends it; the commands in Task 11 call the service directly on the server.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The one pure edge of the service: what a typed verse name becomes. */
class IncantationServiceTest {

    @Test
    void aBarePathIsAMagicalVerse() {
        assertEquals(List.of(VerseIds.of("needle"), VerseIds.of("weight")), IncantationService.parseIds(List.of("needle", "weight")));
    }

    @Test
    void aQualifiedIdIsKept() {
        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("other", "thing")), IncantationService.parseIds(List.of("other:thing")));
    }

    @Test
    void oneBadNameRefusesTheWholeList() {
        assertNull(IncantationService.parseIds(List.of("needle", "Not A Path")));
        assertNull(IncantationService.parseIds(List.of("")));
        assertNull(IncantationService.parseIds(List.of("bad::id")));
        assertNull(IncantationService.parseIds(null));
    }

    @Test
    void thePayloadCarriesNoMoreThanTheCapsAllow() {
        assertEquals(ReciteCaps.MAX_VERSES, SetIncantationPayloadCaps.MAX_IDS);
        assertEquals(64, SetIncantationPayloadCaps.MAX_ID_LENGTH);
    }
}
```

`SetIncantationPayloadCaps` is a pure holder in the incantation package so the test needs no network classes; the payload reads its caps from it.

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.IncantationServiceTest"`
Expected: compilation failure, `SetIncantationPayloadCaps` does not exist (`parseIds` exists since Task 7).

- [ ] **Step 3: Write the caps and the payload**

Create `src/main/java/com/efkrdnz/magical/magic/incantation/SetIncantationPayloadCaps.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/** What the edit payload will carry, pinned next to the core's caps so the two cannot drift. */
public final class SetIncantationPayloadCaps {
    public static final int MAX_IDS = ReciteCaps.MAX_VERSES;
    /** {@code magical:needle_twin_latch} is 25; a namespace and a path together never need more. */
    public static final int MAX_ID_LENGTH = 64;

    private SetIncantationPayloadCaps() {
    }
}
```

Create `src/main/java/com/efkrdnz/magical/network/SetIncantationPayload.java`:

```java
package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.incantation.SetIncantationPayloadCaps;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A whole incantation as the editor left it: the slot, the breath and the verse ids in order.
 * The list is capped at the core's verse cap and each id at a fixed length on the wire, and the
 * server re-validates everything ({@code IncantationValidator}) before writing a byte of it.
 */
public record SetIncantationPayload(int slot, int breath, List<String> ids) implements CustomPacketPayload {

    public static final Type<SetIncantationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "set_incantation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetIncantationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SetIncantationPayload::slot,
                    ByteBufCodecs.INT,
                    SetIncantationPayload::breath,
                    ByteBufCodecs.stringUtf8(SetIncantationPayloadCaps.MAX_ID_LENGTH).apply(ByteBufCodecs.list(SetIncantationPayloadCaps.MAX_IDS)),
                    SetIncantationPayload::ids,
                    SetIncantationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 4: Register and send it**

In `src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java`, in the `playToServer` chain, directly after the `ApplyFracturePayload` registration (the block ending `ChaosAuthorityService.setFracture(player, payload.ordinals());` `}` `}))`), add:

```java
                .playToServer(SetIncantationPayload.TYPE, SetIncantationPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                                com.efkrdnz.magical.magic.incantation.IncantationService.setIncantation(
                                        player, payload.slot(), payload.breath(),
                                        com.efkrdnz.magical.magic.incantation.IncantationService.parseIds(payload.ids()));
                            }
                        }))
```

`setIncantation` treats a null id list (an unparsable name) as a refusal and returns false without a message; the editor will have validated client-side first.

After `sendFracture` (line 402-408), add:

```java
    /** The editor's whole incantation to the server; refused here if it could not fit on the wire. */
    public static void sendIncantation(int slot, int breath, java.util.List<String> ids) {
        if (ids == null || ids.size() > com.efkrdnz.magical.magic.incantation.SetIncantationPayloadCaps.MAX_IDS) {
            return;
        }
        for (String id : ids) {
            if (id == null || id.length() > com.efkrdnz.magical.magic.incantation.SetIncantationPayloadCaps.MAX_ID_LENGTH) {
                return;
            }
        }
        PacketDistributor.sendToServer(new SetIncantationPayload(slot, breath, java.util.List.copyOf(ids)));
    }
```

- [ ] **Step 5: Run the test, then build**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.IncantationServiceTest"`
Expected: 4 tests pass.
Run: `.\gradlew build` - green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/SetIncantationPayloadCaps.java src/main/java/com/efkrdnz/magical/network/SetIncantationPayload.java src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java src/test/java/com/efkrdnz/magical/magic/incantation/IncantationServiceTest.java
git commit -m "feat: the incantation edit payload, capped on the wire"
```

---

### Task 11: The commands, the codex names the Authority it holds, the capture

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java` (the `magical` chain before the `pile` block, line 556; the `magical-debug` chain after the `skill` block, line 796; handlers next to `seedPile`, line 904)
- Modify: `src/main/java/com/efkrdnz/magical/magic/menu/MagicPyramidMenu.java` (add `authorityRowLabelKey`)
- Modify: `src/main/java/com/efkrdnz/magical/client/screen/MagicPyramidScreen.java` (line 426)
- Modify: `src/main/resources/assets/magical/lang/en_us.json` (one key, next to `screen.magical.tier`)
- Create: `src/test/java/com/efkrdnz/magical/magic/menu/AuthorityRowLabelTest.java`
- Modify: `CLAUDE.md` (a new `### Authority of Mana: incantations` section before `### Sin system`)

**Interfaces:**
- Consumes: `IncantationService.SLOTS`, `setIncantation`, `parseIds`, `preview`, `skillFor` (Tasks 7-8); `ReciteCaps.MIN_BREATH`/`MAX_BREATH`; `VerseContent.CATALOGUE.all()`, `contains`; `Grimoire.learn`, `learnAll`, `known()`; `Incantation.Entry.usesRemaining()`; `AuthorityContent.get(ResourceLocation)`, `AuthorityDefinition.nameKey()`; `ClientMagicState.get().authorityId()`.
- Produces: `/magical incantation set|know|show|preview`, `/magical-debug recite <slot>`; `MagicPyramidMenu.authorityRowLabelKey(ResourceLocation) -> String`.

- [ ] **Step 1: Write the failing label test**

```java
package com.efkrdnz.magical.magic.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.efkrdnz.magical.magic.AuthorityContent;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The heading over the codex's Authority row is the Authority the player holds, not Space for everyone. */
class AuthorityRowLabelTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theHeldAuthorityNamesTheRow() {
        assertEquals("authority.magical.authority_of_mana", MagicPyramidMenu.authorityRowLabelKey(AuthorityContent.MANA));
        assertEquals("authority.magical.authority_of_space", MagicPyramidMenu.authorityRowLabelKey(AuthorityContent.SPACE));
        assertEquals("authority.magical.authority_of_chaos", MagicPyramidMenu.authorityRowLabelKey(AuthorityContent.CHAOS));
    }

    @Test
    void noAuthorityIsAPlainHeading() {
        assertEquals("screen.magical.authority_row", MagicPyramidMenu.authorityRowLabelKey(null));
        assertEquals("screen.magical.authority_row", MagicPyramidMenu.authorityRowLabelKey(ResourceLocation.fromNamespaceAndPath("magical", "authority_of_nothing")));
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.menu.AuthorityRowLabelTest"`
Expected: compilation failure, `authorityRowLabelKey` does not exist.

- [ ] **Step 3: The label**

In `src/main/java/com/efkrdnz/magical/magic/menu/MagicPyramidMenu.java`, after the `AUTHORITY_TIER` constant (line 24), add:

```java
    /** The heading over the Authority row: the held Authority's name, or a plain word when none is held. */
    public static String authorityRowLabelKey(ResourceLocation authorityId) {
        com.efkrdnz.magical.magic.AuthorityDefinition held = authorityId == null ? null : com.efkrdnz.magical.magic.AuthorityContent.get(authorityId);
        return held == null ? "screen.magical.authority_row" : held.nameKey();
    }
```

In `src/main/java/com/efkrdnz/magical/client/screen/MagicPyramidScreen.java`, replace line 426, `name = Component.translatable("authority.magical.authority_of_space");`, with:

```java
                PlayerMagicState held = ClientMagicState.get();
                name = Component.translatable(MagicPyramidMenu.authorityRowLabelKey(held == null ? null : held.authorityId()));
```

(`PlayerMagicState`, `ClientMagicState` and `MagicPyramidMenu` are already imported or referenced in the file; if `PlayerMagicState` is not, add `import com.efkrdnz.magical.magic.PlayerMagicState;`.)

In `src/main/resources/assets/magical/lang/en_us.json`, directly after the `"screen.magical.tier"` line, add:

```json
  "screen.magical.authority_row": "Authority",
```

- [ ] **Step 4: Run the label test**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.menu.AuthorityRowLabelTest"`
Expected: 2 tests pass.

- [ ] **Step 5: The commands**

In `src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java`, add to the imports:

```java
import com.efkrdnz.magical.magic.incantation.IncantationService;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.RecitePlan;
import com.efkrdnz.magical.magic.incantation.Verse;
import com.efkrdnz.magical.magic.incantation.VerseContent;
```

In the `magical` chain, directly before the comment `// Capture tooling, and nothing else: a screenshot of an avalanche needs a` that precedes `.then(Commands.literal("pile")` (line 556), add:

```java
                    // The Grimoire by hand, until the editor screen: write a slot, learn verses,
                    // read a slot back, and see what a press would cast without pressing. Slots
                    // are one-based here, as the skills are named (Incantation I is slot 1).
                    .then(Commands.literal("incantation")
                            .then(Commands.literal("set")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, IncantationService.SLOTS))
                                            .then(Commands.argument("breath", IntegerArgumentType.integer(ReciteCaps.MIN_BREATH, ReciteCaps.MAX_BREATH))
                                                    .then(Commands.argument("verses", StringArgumentType.greedyString())
                                                            .executes(context -> withPlayer(context.getSource(), player -> incantationSet(player,
                                                                    IntegerArgumentType.getInteger(context, "slot"),
                                                                    IntegerArgumentType.getInteger(context, "breath"),
                                                                    StringArgumentType.getString(context, "verses"))))))))
                            .then(Commands.literal("know")
                                    .then(Commands.argument("verse", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(versePaths(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> incantationKnow(player,
                                                    StringArgumentType.getString(context, "verse"))))))
                            .then(Commands.literal("show")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, IncantationService.SLOTS))
                                            .executes(context -> withPlayer(context.getSource(), player -> incantationShow(player,
                                                    IntegerArgumentType.getInteger(context, "slot"))))))
                            .then(Commands.literal("preview")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, IncantationService.SLOTS))
                                            .executes(context -> withPlayer(context.getSource(), player -> incantationPreview(player,
                                                    IntegerArgumentType.getInteger(context, "slot")))))))
```

In the `magical-debug` chain, directly after the `skill` block (the line `.executes(context -> debugCastSkill(context.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "id"), true)))))`, line 796), add:

```java
                    // A press on Incantation <slot> with the unlock, the cooldown and the pool taken care of.
                    .then(Commands.literal("recite")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(1, IncantationService.SLOTS))
                                    .executes(context -> debugCastSkill(context.getSource(),
                                            IncantationService.skillFor(IntegerArgumentType.getInteger(context, "slot") - 1).id(), false))))
```

Directly before `seedPile` (its javadoc begins `/**` a few lines above line 904; insert before that javadoc), add the handlers:

```java
    /** "all" first, then every verse path, for the know command's suggestions. */
    private static java.util.List<String> versePaths() {
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("all");
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            names.add(verse.path());
        }
        return names;
    }

    private static int incantationSet(ServerPlayer player, int slot, int breath, String verses) {
        java.util.List<String> raw = java.util.Arrays.asList(verses.trim().split("\\s+"));
        java.util.List<ResourceLocation> ids = IncantationService.parseIds(raw);
        if (ids == null) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_unknown_verse", verses), false);
            return 0;
        }
        return IncantationService.setIncantation(player, slot - 1, breath, ids) ? 1 : 0;
    }

    private static int incantationKnow(ServerPlayer player, String verse) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if ("all".equalsIgnoreCase(verse)) {
            state.grimoire().learnAll(VerseContent.CATALOGUE.all().stream().map(Verse::id).toList());
        } else {
            java.util.List<ResourceLocation> ids = IncantationService.parseIds(java.util.List.of(verse));
            if (ids == null || !VerseContent.CATALOGUE.contains(ids.get(0))) {
                player.displayClientMessage(Component.translatable("message.magical.incantation_unknown_verse", verse), false);
                return 0;
            }
            state.grimoire().learn(ids.get(0));
        }
        state.sync(player);
        int known = state.grimoire().known().size();
        player.displayClientMessage(Component.translatable("message.magical.incantation_known", known), false);
        return known;
    }

    private static int incantationShow(ServerPlayer player, int slot) {
        var incantation = player.getData(MagicalAttachments.MAGIC_STATE).grimoire().incantation(slot - 1);
        if (incantation.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_empty", slot), false);
            return 0;
        }
        net.minecraft.network.chat.MutableComponent line = Component.literal("Incantation " + slot + " (breath " + incantation.breath() + "): ");
        boolean first = true;
        for (var entry : incantation.entries()) {
            if (!first) {
                line.append(Component.literal(", "));
            }
            first = false;
            line.append(Component.translatable("verse.magical." + entry.id().getPath()));
            if (entry.usesRemaining() >= 0) {
                line.append(Component.literal(" (" + entry.usesRemaining() + ")"));
            }
        }
        player.displayClientMessage(line, false);
        return incantation.size();
    }

    private static int incantationPreview(ServerPlayer player, int slot) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (state.grimoire().incantation(slot - 1).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_empty", slot), false);
            return 0;
        }
        RecitePlan plan = IncantationService.preview(player, state, slot - 1);
        player.displayClientMessage(Component.translatable("message.magical.incantation_preview",
                slot, plan.bodies().size(), plan.manaSpent(), plan.cooldownTicks()), false);
        return plan.bodies().size();
    }
```

- [ ] **Step 6: Build, then the capture**

Run: `.\gradlew build` - green.

Then the first launch that recites for real. It clears the beach, takes the Authority, learns every verse, writes a trident of needles into slot 1 and an orb with a fuse that releases a rime ring into slot 2, and presses each. Midnight, because the bodies are additive light:

```powershell
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical unlockall;magical authority set authority_of_mana;magical incantation know all;magical incantation set 1 1 trident needle needle needle;magical incantation set 2 1 orb_fuse rime_ring;time set midnight;tp @s ~ ~ ~ 0 0;summon iron_golem ~ ~ ~6 {NoAI:1b};121:magical-debug recite 1;181:magical-debug recite 2" -PautoScreenshot=123,125,183,192,215 -PautoExit
```

Look at the five captures in `run/screenshots/` (the newest five). Expected: at 123 and 125 three needles fanned twenty degrees apart flying at the golem; at 183 an orb a few blocks out; at 192 the orb gone and a ring standing on the sand where its fuse ran out; at 215 the ring still there, fading. If the bodies are invisible, check `MagicalClientEvents.registerRenderers` has the `VERSE_BODY` line (Task 6) before anything else. If the screen shows the class chooser, `magical class unlock mystic` did not land before the tick-40 batch: the onboarding note in CLAUDE.md applies.

- [ ] **Step 7: CLAUDE.md**

In `CLAUDE.md`, before `### Sin system`, add:

```markdown
### Authority of Mana: incantations

Layer -6 with the other authorities. The kit is four skills, `incantation_1..4` (Incantation I to IV), and each is a press that recites one slot of the wielder's **Grimoire** (`magic/incantation/Grimoire`, on `PlayerMagicState`, saved with it): a *verse* is a card, an *incantation* is up to twenty of them in an order with a *breath* (1-8) that says how many are read per press, and the core in `magic/incantation/` (`Reciter`, pure, no Minecraft in it) turns the read verses into a `RecitePlan` - the bodies of one shot with the modifiers stamped on them, the mana they cost, the beat that becomes the cooldown. `IncantationService.recite` bills it through `MagicSinService.spendManaForSkill` (a negative bill is a refund), spawns it through `entity/verse/VerseBodySpawner` (the fan from the pattern, a deviation within the spread, statics a block ahead of the hand and dropped to the floor, Near Word on the caster, twins twelve degrees apart), and keeps the deck per wielder and per dimension, never saved. One entity, `entity/verse/VerseBodyEntity` (`verse_body`): it reads its prototype, its stamped `ShotState` and its payload off a `ProjectilePlan` (server NBT through `ShotPlanCodec`), and **ends one way and releases the payload of that way** - a hit its Latch, its fuse its Fuse, its expiry its Epitaph; every end fires the explosion it carries and carries the caster if it is a Blink; a bounce is not an end. The renderer (`client/renderer/verse/VerseBodyRenderer`, one `VerseLooks` row per prototype look) draws it with the existing FX painters in its school's colour, a glyph per behaviour orbiting it and a filament per wake behind it. Commands: `/magical incantation set <slot 1-4> <breath 1-8> <verses...>` writes a slot (bare paths are `magical:`), `know all|<verse>` learns, `show <slot>` reads it back, `preview <slot>` says what a press would cast; `magical-debug recite <slot>` presses it with the unlock, the cooldown and the pool taken care of. Capture, a trident of needles then an orb whose fuse releases a rime ring: `.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical unlockall;magical authority set authority_of_mana;magical incantation know all;magical incantation set 1 1 trident needle needle needle;magical incantation set 2 1 orb_fuse rime_ring;time set midnight;tp @s ~ ~ ~ 0 0;summon iron_golem ~ ~ ~6 {NoAI:1b};121:magical-debug recite 1;181:magical-debug recite 2" -PautoScreenshot=123,125,183,192,215 -PautoExit`. Designs: `docs/superpowers/specs/2026-09-19-authority-of-mana-incantation-design.md`; plans: `docs/superpowers/plans/2026-09-19-incantation-core.md` (the core), `docs/superpowers/plans/2026-09-19-incantation-runtime.md` (the body and the Authority).
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java src/main/java/com/efkrdnz/magical/magic/menu/MagicPyramidMenu.java src/main/java/com/efkrdnz/magical/client/screen/MagicPyramidScreen.java src/main/resources/assets/magical/lang/en_us.json src/test/java/com/efkrdnz/magical/magic/menu/AuthorityRowLabelTest.java CLAUDE.md
git commit -m "feat: the incantation commands, and the codex names the Authority it holds"
```

---

### Task 12: The Ledger kit goes, the incantations having replaced it

One atomic commit. Nothing in this task is new; every line is a removal or the shortening of a call site, and the build at the end is the proof. The three Ledger skills, their overlay, their payload, their service, their laws and their captures all go together, because a reference to any one of them from a file that stays would break the build in between.

**Files:**
- Delete: `src/main/java/com/efkrdnz/magical/magic/mana/` (the whole directory: `ManaFormService`, `ManaLedger`, `ManaLedgerService`, `Writ`, `WritAspect`, `WritLaw`, `WritOperation`, `WritSubject`, and the stray `bash.exe.stackdump`), `src/test/java/com/efkrdnz/magical/magic/mana/ManaLedgerTest.java`, `src/main/java/com/efkrdnz/magical/client/ManaAuthorityInput.java`, `src/main/java/com/efkrdnz/magical/client/WritOverlay.java`, `src/main/java/com/efkrdnz/magical/network/ApplyWritPayload.java`
- Modify: `MagicContent.java`, `AuthorityContent.java`, `MagicCastContentKept.java`, `MagicCastingService.java`, `MagicSinService.java`, `MagicGameplayEvents.java`, `PlayerMagicState.java`, `MagicalClientEvents.java`, `HudLayers.java`, `MagicalNetwork.java`, `MagicalCommands.java`, `RuleWheelPainter.java`, `en_us.json`, `HudLangKeysTest.java`

**Interfaces:**
- Consumes: nothing new. After this task `MagicContent.OPEN_LEDGER`, `WRIT`, `MANA_FORM`, `PlayerMagicState.manaLedger()`, `manaFormTicks()`, `inManaForm()`, `setManaFormTicks()`, `MagicalNetwork.sendWrit` and the `magical ledger` command no longer exist; nothing outside the deleted files referenced them except the call sites listed below (verified with a grep for `magic.mana.`, `OPEN_LEDGER`, `MagicContent.WRIT`, `MANA_FORM`, `WritOverlay`, `ManaAuthorityInput`, `ApplyWritPayload` at `cb3e3a5`). Kept on purpose: `RuleWheelPainter` (the Writ overlay was one of its callers; the dials still draw for nothing else today but it is the painter, not the kit), `SubspaceLedger`, the `Ledger`/`Gilded Ledger` passives, `ConjuredTerrainService.Ledger`, `MagicWheelOverlay`.

- [ ] **Step 1: Delete the files**

```bash
git rm -r src/main/java/com/efkrdnz/magical/magic/mana src/test/java/com/efkrdnz/magical/magic/mana/ManaLedgerTest.java src/main/java/com/efkrdnz/magical/client/ManaAuthorityInput.java src/main/java/com/efkrdnz/magical/client/WritOverlay.java src/main/java/com/efkrdnz/magical/network/ApplyWritPayload.java
```

If `bash.exe.stackdump` is untracked, `git rm` leaves it; delete it by hand so the directory is gone.

- [ ] **Step 2: The content**

`src/main/java/com/efkrdnz/magical/magic/MagicContent.java`:
- Delete the three lines `OPEN_LEDGER = register("open_ledger", ...)`, `WRIT = register("writ", ...)`, `MANA_FORM = register("mana_form", ...)` (lines 98-100 at `cb3e3a5`). Keep the four-line `// The Authority of Mana. ARCANE rather than a school of its own:` comment above them; it now introduces the `INCANTATION_1..4` block Task 8 placed directly after, and Task 8's own comment line `// The four incantations replace the Ledger kit below (removed once these are live).` becomes `// The four incantations.`
- In `AUTHORITY_SKILLS`, delete the line `OPEN_LEDGER.id(), WRIT.id(), MANA_FORM.id(),`, leaving the `INCANTATION_1..4` line.

`src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java`: the grant list of `AUTHORITY_OF_MANA` becomes

```java
            List.of(MagicContent.INCANTATION_1.id(), MagicContent.INCANTATION_2.id(),
                    MagicContent.INCANTATION_3.id(), MagicContent.INCANTATION_4.id()));
```

`src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java`: delete the comment above `OPEN_LEDGER`'s registration (the lines from `// The Authority of Mana` down to `// witnessed, then an aspect, an operation and a subject - not pressing a button.`, lines 56-58) and the three registrations for `OPEN_LEDGER`, `WRIT` and `MANA_FORM` (lines 59-63). Task 8's four registrations stay.

- [ ] **Step 3: The call sites**

`src/main/java/com/efkrdnz/magical/magic/MagicCastingService.java` (lines 314-321): delete the four-line comment beginning `// A writ with MANIFESTATION set to ZERO over this spell is anti-magic` and the block

```java
        if (com.efkrdnz.magical.magic.mana.WritLaw.silenced(player, definition)) {
            player.displayClientMessage(Component.translatable("message.magical.writ_silenced"), true);
            return;
        }
```

`src/main/java/com/efkrdnz/magical/magic/MagicSinService.java`:
- Lines 66-71: delete the four-line comment beginning `// Last word goes to the Ledgers of the world.` and replace

```java
        return com.efkrdnz.magical.magic.mana.WritLaw.apply(player,
                ClassPassiveEffects.adjustCast(player, state, adjusted));
```

with

```java
        return ClassPassiveEffects.adjustCast(player, state, adjusted);
```

- Lines 109-111: delete the two-line comment beginning `// Anyone nearby holding an open Ledger just watched this happen` and the line `com.efkrdnz.magical.magic.mana.ManaLedgerService.witnessCast(player, definition);`.

`src/main/java/com/efkrdnz/magical/magic/MagicGameplayEvents.java`:
- Line 62: delete `com.efkrdnz.magical.magic.mana.ManaFormService.tick(player, state);`.
- Lines 241-251 (in `onIncomingDamage`): delete the five-line comment beginning `// A wielder who has put the body down cannot be struck` and the block

```java
        if (state.inManaForm()
                && com.efkrdnz.magical.magic.mana.ManaFormService.absorb(player, state,
                        event.getContainer().getNewDamage())) {
            event.getContainer().setNewDamage(0.0F);
            event.setCanceled(true);
            return;
        }
```

`src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java` - every `manaLedger` and `manaForm` line, and nothing else:
- line 47: the `manaLedger` field; line 51: `private int manaFormTicks;`
- lines 491-499: the javadoc above `manaLedger()` and the accessor; lines 506-512: `manaFormTicks()` and `inManaForm()`
- line 1129: `manaLedger.clear();`; line 1131: `manaFormTicks = 0;` (in `clearAuthority`)
- lines 1139-1141: `setManaFormTicks`
- lines 2056 and 2058: the two `copy.` lines
- lines 2167 and 2169: the `tag.put("manaLedger", ...)` and `tag.putInt("manaFormTicks", ...)` lines
- lines 2330 and 2332: the two `state.` load lines

Anchor each by its text (`grep -n "manaLedger\|manaForm" PlayerMagicState.java` lists exactly these fourteen lines plus the javadoc); the Grimoire lines Task 7 put beside them stay.

`src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java`:
- Lines 216-218: delete `if (WritOverlay.active()) { WritOverlay.finish(); }`.
- Lines 251-256: delete the `if (ManaAuthorityInput.tickSlot(minecraft, i)) { ... continue; }` block with its comment.
- Line 423: delete `|| WritOverlay.handleScroll(scrollDeltaY)`.
- Line 430: `dispatchMouseButton` becomes

```java
            return FractureOverlay.handleMouseButton(button, action)
                    || SpaceManipulationOverlay.handleMouseButton(button, action);
```

`src/main/java/com/efkrdnz/magical/client/hud/HudLayers.java`, line 139: delete `com.efkrdnz.magical.client.WritOverlay.render(graphics, minecraft);`.

`src/main/java/com/efkrdnz/magical/client/RuleWheelPainter.java`, line 19: the javadoc `{@link WritOverlay} draws three of these for aspect, operation and subject` becomes plain text, `the Writ overlay drew three of these for aspect, operation and subject` - a `{@link}` to a deleted class is a javadoc error.

`src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java`: delete the `ApplyWritPayload` registration (the `.playToServer(ApplyWritPayload.TYPE, ...)` block, lines 211-217) and `sendWrit` (lines 397-399).

`src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java`:
- Delete the `ledger` literal with its two-line comment (`// The Ledger, without an authority or a cooldown in the way.` through the line ending `StringArgumentType.getString(context, "subject"))))))))))`, lines 226-253).
- Delete the five helpers `debugLedger`, `debugWitness`, `debugWrit`, `skillId(String)` and `enumByName`, each with the javadoc directly above it (from the javadoc `/** Opens or shuts the book with none of the skill gates, so a capture needs no authority. */` through the closing brace of `enumByName`, lines 1088-1169). The stray javadoc `/** {@code subspace rule}: the same three words as {@code hud rule}, onto the standing domain. */` on line 1087 stays: it belongs to `subspaceRule`, which follows. `lowerNames` and `byName` are defined elsewhere and stay.

- [ ] **Step 4: The language file and its test**

`src/main/resources/assets/magical/lang/en_us.json`: delete these 45 keys with their lines - `skill.magical.open_ledger`, `skill.magical.open_ledger.desc`, `skill.magical.writ`, `skill.magical.writ.desc`, `skill.magical.mana_form`, `skill.magical.mana_form.desc`, every `writ.magical.*` key (23 of them: `aspect.cost` … `aspect.manifestation`, `operation.raise` … `operation.restore`, `subject.all/mine/theirs`, `wheel.aspect/operation/subject`, `page`, `page_empty`, `whole_school`, `release_hint`, `reading`), `message.magical.writ_hold`, `message.magical.ledger_opened`, `message.magical.ledger_closed`, `message.magical.ledger_entered`, `message.magical.ledger_unwitnessed`, `message.magical.ledger_full`, `message.magical.writ_declared`, `message.magical.writ_struck`, `message.magical.writ_nothing_to_strike`, `message.magical.writ_locked`, `message.magical.writ_silenced`, `message.magical.mana_form_entered`, `message.magical.mana_form_ended`, `message.magical.mana_form_spent`, `message.magical.mana_form_shattered`, `message.magical.mana_form_too_shallow`. At `cb3e3a5` they are contiguous (lines 663-707) apart from the four incantation skill keys and the verse block Tasks 8 and 9 inserted after `skill.magical.mana_form.desc`; those stay. `authority.magical.authority_of_mana` and its `.desc` stay.

`src/test/java/com/efkrdnz/magical/client/hud/HudLangKeysTest.java`: in `RETIRED`, after `"message.magical.space_rule_applied",` add:

```java
            // The Ledger kit of the Authority of Mana, replaced by the incantations.
            "skill.magical.open_ledger", "skill.magical.writ", "skill.magical.mana_form",
            "message.magical.writ_silenced", "message.magical.ledger_opened", "message.magical.mana_form_entered",
```

- [ ] **Step 5: Build and run everything**

Run: `.\gradlew build`
Expected: green. `AuthorityGrantTest` (the union of the grants is `AUTHORITY_SKILLS`, now without the three), `ContentLangKeysTest` (no skill without keys, and the retired keys are not skills any more), `PyramidLayersTest`, `HudLangKeysTest` (the retired keys are gone), `VerseLangKeysTest`, `PlayerMagicStateGrimoireTest`; `PlayerStateSyncCostTest` prints a smaller payload than before this task. A compile error naming `magic.mana` anywhere means a call site above was missed: `grep -rn "magic\.mana\.\|OPEN_LEDGER\|MagicContent\.WRIT\b\|MANA_FORM\|WritOverlay\|ManaAuthorityInput\|ApplyWritPayload\|manaLedger\|inManaForm\|ManaForm" src/` must print nothing.

Run: `.\gradlew runGameTestServer`
Expected: all green, `IncantationGameTests` and `VerseBodyGameTests` included.

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/com/efkrdnz/magical/magic/mana src/test/java/com/efkrdnz/magical/magic/mana src/main/java/com/efkrdnz/magical/client/ManaAuthorityInput.java src/main/java/com/efkrdnz/magical/client/WritOverlay.java src/main/java/com/efkrdnz/magical/network/ApplyWritPayload.java src/main/java/com/efkrdnz/magical/magic/MagicContent.java src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java src/main/java/com/efkrdnz/magical/magic/MagicCastingService.java src/main/java/com/efkrdnz/magical/magic/MagicSinService.java src/main/java/com/efkrdnz/magical/magic/MagicGameplayEvents.java src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java src/main/java/com/efkrdnz/magical/client/hud/HudLayers.java src/main/java/com/efkrdnz/magical/client/RuleWheelPainter.java src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java src/main/resources/assets/magical/lang/en_us.json src/test/java/com/efkrdnz/magical/client/hud/HudLangKeysTest.java
git commit -m "refactor: the Ledger kit goes, the incantations having replaced it"
```

---

## Done when

- `.\gradlew build` and `.\gradlew runGameTestServer` are green at the last commit.
- The capture in Task 11 shows three needles fanned, then an orb, then a ring where its fuse ran out.
- `grep -rn "magic\.mana\." src/` prints nothing.
- Spec §12 rows 2 and 3 point at this plan; Plan 4 (the editor screen, the HUD readout, recoil and screenshake, the Grimoire tab) is the next plan to write.
