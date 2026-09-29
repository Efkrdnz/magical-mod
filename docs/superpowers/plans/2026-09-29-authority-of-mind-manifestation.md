# Authority of Mind, stage 2: Manifestation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enough minds agreeing on a lie makes it real. Clusters become real blocks and figments become real bodies. Both fall back to illusion when the agreement goes. Insist pushes an audience over the line, and a lie that hurts is believed more.

**Architecture:** The pure core gains three classes and two `Belief` methods:
- `Consensus`: weights, voters, and the manifest threshold with its half-weight hysteresis.
- `PhantomHarm`: the harm table.
- `SceneAim`: a ray against boxes.
- `Belief.consensus` and `Belief.nudge`.

`KindStats` reads a creature kind's vanilla health and attack. The runtime gains four classes:
- `Manifestation` runs every 5 ticks from `MindService.tickScene`. It places a cluster through `ConjuredTerrainService` and turns a `FigmentEntity` real through a synced flag.
- `MindHarm` applies phantom harm.
- `Insist` holds through the generic `HoldService` path.
- `ManifestGuard` stops a manifested block from dropping items.

Clients learn which elements are real from a list added to `BeliefSyncPayload`. They draw a lilac rim that hardens and fades.

**Tech Stack:** NeoForge 21.4.157, Minecraft 1.21.4 (Mojmap), Java 21, JUnit 5, NeoForge GameTest.

## Global Constraints

These values come from `docs/superpowers/specs/2026-09-28-authority-of-mind-design.md`, sections "Manifestation" and "Skills":
- A cluster weighs 0.5 a block. A figment weighs its kind's max health / 4: zombie 5, iron golem 25, Warden 125.
- Consensus is `sum(w * b)` over viewers convinced of the element (b >= 0.5). The weight `w` is 1 for a mob, 3 for a player and 5 for a boss.
- An element manifests when its consensus reaches its weight. It stays real while consensus holds at half the weight or more, and reverts below that.
- A manifested cluster is placed as real blocks through `ConjuredTerrainService`. It collides for everyone and is given back.
- A manifested figment is real for everyone, with health `kind * min(1, consensus / weight)` and its kind's attack.
- Striking a manifested thing is not a contradiction.
- The transition is drawn: the lilac rim hardens and cracks into the material.
- Insist is a hold on an element: 3 mana a tick and no cooldown. Every viewer perceiving the element at 0.3 or above gains 0.01 a tick, and every viewer below 0.3 loses 0.01.
- Phantom harm hits a viewer at 0.5 or above for magic damage times belief. The touch raises belief by 0.10 instead of breaking it. Below 0.5 the same touch is a contradiction.
- The harmful sources are lava, fire, magma, cactus, berry bush and figment attacks.
- An illusion lasts 1200 ticks unless it manifests.

These are project rules:
- Every Authority skill is `selfManaged` and bills itself. A press that refuses is never charged.
- The wielder is never a viewer. They add nothing to consensus and are never harmed by their own lie.
- Keep the pure core free of level access. `Consensus`, `PhantomHarm` and `SceneAim` touch no `Level`.
- Game tests use template `unwaking_empty`. Give each test a unique `batch` and call `MindService.endAll(owner)` before `helper.succeed()`. Keep every entity inside the 5x5x5 interior, and end fake player names in `-test`.
- Run game tests with `.\gradlew runGameTestServer --console=plain`. Without it, Gradle's progress bar erases the assertion message.
- Commits follow these rules:
  - Stage with `git add -- <paths>`, then run `git commit` with no pathspec.
  - Never stage the peer session's files: `CLAUDE.md` working-tree hunks, `SKILL_CREATION_NOTES.md`, `gradlew`, `.claude/settings.local.json`, `client/SpaceManipulationOverlay.java`, `client/hud/HudDebug.java`, `SpaceManipulationLayout(.java|Test.java)`, `docs/assets/`, `logs/`.
  - Never run a bare `git stash`.
  - End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Decisions (made here, binding on every task)

**D1. A manifested figment keeps its entity.**
- It is the same `FigmentEntity`, turned real by a synced `MANIFESTED` flag. It is not swapped for a vanilla mob.
- It keeps its script and goals, its 0.6x1.95 body and its type's `fireImmune`.
- It drops nothing: its type's loot table does not exist, and a `PathfinderMob` gives no XP.

**D2. Fire and fluids never manifest.**
- The excluded blocks are `minecraft:fire`, `minecraft:soul_fire`, `minecraft:lava` and `minecraft:water`. A real lava pool would flow and burn past its own revert.
- Imagined lava therefore only ever hurts the mind, which is what the spec wants a hurting lie to be.
- Any other cell manifests only into a position that meets all of these:
  - it is loaded;
  - it is `canBeReplaced()`;
  - it has no block entity;
  - no living body other than a ghost figment is in it.
- A manifest that places nothing is not marked manifested. It is tried again at the next consensus step.
- Cells of a manifested cluster that could not be placed stay drawn as illusion.

**D3. Consensus is computed in one pass.** Every element is summed every `MindSync.BELIEF_INTERVAL` (5) ticks, aligned with the belief sync. The same tick's payload then carries the new manifested list.

**D4. A scene with anything manifested does not expire.**
- `LiveScene.over(age, anythingReal) = age >= LIFE_TICKS && !anythingReal`.
- Ending a scene reverts everything it made real. A scene ends on owner logout, `endAll`, the level going away, or the server stopping.

**D5. Insist rides the generic hold path.** There is no `InsistPayload`.
- `GenericHoldInput` treats `insist` as holdable without a visual profile.
- The press handler is a no-op `selfManaged`.
- `MindService.onServerTick` runs `Insist.tick` for every online player who has the Authority of Mind and holds Insist (`HoldService.isHeldSkill`).
- Billing is 3 mana a tick as `max(1, round(3 * costScale))`, with no cooldown. `MagicSkillDefinition.resolve` floors mana at 4, so the stats' `manaCost()` is never used.
- State syncs every 5 ticks. The "nothing there" and "no mana" lines appear at most once a second.

**D6. Phantom harm.**
- Harm per block: lava 4, fire 1, soul fire 2, magma block 1, cactus 1, sweet berry bush 1. A cluster's harm is the most of any block in it.
- A believer (b >= 0.5) who crosses into a harmful cluster:
  - takes base * b magic damage;
  - is nudged +0.10;
  - makes no witnessed contradiction for anyone else.
- While it stays inside, it takes the same harm every 20 ticks of scene age, with no further raise.
- A figment striking a believer deals kind attack * b and nudges +0.10. A kind with no attack does nothing.
- The damage source is `indirectMagic(direct or owner, owner)` while the owner is online, else `magic()`. It goes through `MagicDamageService.hurt(..., MagicContent.UNVEIL.id())`.

**D7. `Belief.nudge(viewer, element, delta)`:**
- never shatters;
- clamps to 0..1;
- removes the row at 0 or below;
- creates a row only from a positive delta;
- leaves a shattered row alone.

**D8. The transition is drawn twice.**
- Clients draw a lilac rim at the element's cells, or round the figment's box. It fades from full over `Consensus.HARDEN_TICKS` (16).
- The server fires `LevelEvent.PARTICLES_DESTROY_BLOCK` (2001) at every placed cell: the crack, as block-break particles and sound in the material.
- A figment manifesting plays `SoundEvents.ILLUSIONER_PREPARE_MIRROR`. Anything reverting plays `SoundEvents.ILLUSIONER_MIRROR_MOVE`.

**D9. Voter weights.** `minecraft:player` counts 3. `minecraft:wither`, `minecraft:ender_dragon` and `minecraft:elder_guardian` count 5. Any other mind counts 1.

**D10. A real figment's health follows consensus.**
- At every step its max health is `KindStats.maxHealth(kind) * Consensus.healthFraction(c, w)`.
- Health is clamped down to that value, never raised.
- A slain manifested figment is gone for good (`scene.slain`). A slain element is skipped by perception, touches, projectiles, consensus, aiming and the pathfinder.

**D11. A manifested block pays out nothing.**
- `ManifestGuard` stops a manifested block from dropping anything, and explosions do not break one. Otherwise an imagined diamond wall is real diamonds.
- Pistons and endermen can still move one. This is a known gap, recorded in CLAUDE.md.

**D12. The forecast counts sure minds.** The forecast's "Becomes real" counts minds that are sure (b = `Belief.SURE`, 0.8): `needed = ceil(weight / (voter * 0.8))`.

---

### Task 1: Consensus and the two new Belief methods

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/Consensus.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/Belief.java`. Add two methods, and add the import `java.util.function.IntToDoubleFunction`.
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/ConsensusTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/BeliefTest.java` (add three tests)

**Interfaces:**
- Consumes:
  - `Belief.SURE` and `Belief.CONVINCED`;
  - package-private `Belief.set(int,int,float)`;
  - `Belief.expose(int,int,Contradiction)` and `Contradiction.TOUCH`.
- Produces on `Consensus`:
  - constants `PER_BLOCK`, `PER_HEALTH`, `HOLD` (0.5) and `HARDEN_TICKS` (16);
  - `static float clusterWeight(int blocks)`;
  - `static float figmentWeight(float maxHealth)`;
  - `static float voter(String entityTypeId)`;
  - `static boolean real(boolean manifested, float consensus, float weight)`;
  - `static int needed(float weight, float voter)`;
  - `static float healthFraction(float consensus, float weight)`.
- Produces on `Belief`:
  - `consensus(int elements, IntToDoubleFunction voter) -> float[]`;
  - `nudge(int viewer, int element, float delta)`.

- [ ] **Step 1: Write the failing tests**

`ConsensusTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsensusTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void aBlockWeighsAHalfAndAFigmentAQuarterOfItsHealth() {
        assertEquals(7.5F, Consensus.clusterWeight(15), EPSILON);
        assertEquals(5.0F, Consensus.figmentWeight(20.0F), EPSILON);
        assertEquals(25.0F, Consensus.figmentWeight(100.0F), EPSILON);
        assertEquals(125.0F, Consensus.figmentWeight(500.0F), EPSILON);
    }

    @Test
    void aPlayerCountsThreeABossFiveAndAnyOtherMindOne() {
        assertEquals(3.0F, Consensus.voter("minecraft:player"), EPSILON);
        assertEquals(5.0F, Consensus.voter("minecraft:wither"), EPSILON);
        assertEquals(5.0F, Consensus.voter("minecraft:ender_dragon"), EPSILON);
        assertEquals(5.0F, Consensus.voter("minecraft:elder_guardian"), EPSILON);
        assertEquals(1.0F, Consensus.voter("minecraft:zombie"), EPSILON);
        assertEquals(1.0F, Consensus.voter("minecraft:warden"), EPSILON);
    }

    @Test
    void itManifestsAtItsWeightAndHoldsDownToHalf() {
        assertFalse(Consensus.real(false, 4.99F, 5.0F));
        assertTrue(Consensus.real(false, 5.0F, 5.0F));
        assertTrue(Consensus.real(true, 2.5F, 5.0F));
        assertFalse(Consensus.real(true, 2.49F, 5.0F));
        assertFalse(Consensus.real(false, 10.0F, 0.0F), "nothing that weighs nothing is ever real");
    }

    @Test
    void theForecastCountsMindsThatAreSure() {
        // A fifteen-block wall weighs 7.5: ten zombies at 0.8, or four players.
        assertEquals(10, Consensus.needed(7.5F, 1.0F));
        assertEquals(4, Consensus.needed(7.5F, 3.0F));
        assertEquals(7, Consensus.needed(5.0F, 1.0F));
        assertEquals(1, Consensus.needed(0.8F, 1.0F), "an exact fit is not rounded up by float error");
        assertEquals(-1, Consensus.needed(5.0F, 0.0F));
    }

    @Test
    void aRealFigmentIsAsHaleAsItsConsensus() {
        assertEquals(0.5F, Consensus.healthFraction(2.5F, 5.0F), EPSILON);
        assertEquals(1.0F, Consensus.healthFraction(9.0F, 5.0F), EPSILON);
        assertEquals(1.0F, Consensus.healthFraction(1.0F, 0.0F), EPSILON);
    }
}
```

Add these to `BeliefTest.java`. It is in the same package and already has `EPSILON`.

```java
    @Test
    void consensusSumsOnlyTheConvincedEachByItsWeight() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.6F);
        belief.set(2, 0, 0.49F);
        belief.set(3, 0, 1.0F);
        belief.set(1, 1, 0.8F);
        belief.set(1, 5, 0.9F);
        float[] sums = belief.consensus(2, viewer -> viewer == 3 ? 3.0 : 1.0);
        assertEquals(2, sums.length);
        assertEquals(0.6F + 3.0F, sums[0], EPSILON);
        assertEquals(0.8F, sums[1], EPSILON);
    }

    @Test
    void aNudgeMovesBeliefWithoutEverShatteringIt() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.3F);
        belief.nudge(1, 0, 0.01F);
        assertEquals(0.31F, belief.get(1, 0), EPSILON);
        belief.set(1, 0, 0.05F);
        belief.nudge(1, 0, -0.01F);
        assertEquals(0.04F, belief.get(1, 0), EPSILON);
        assertFalse(belief.shattered(1, 0));
        belief.nudge(1, 0, -0.5F);
        assertEquals(0.0F, belief.get(1, 0), EPSILON);
        assertFalse(belief.shattered(1, 0), "doubt pushed to nothing is not a contradiction");
        belief.nudge(2, 0, -0.01F);
        assertEquals(0.0F, belief.get(2, 0), EPSILON);
        belief.nudge(2, 0, 0.10F);
        assertEquals(0.10F, belief.get(2, 0), EPSILON);
        belief.set(3, 0, 0.95F);
        belief.nudge(3, 0, 0.10F);
        assertEquals(1.0F, belief.get(3, 0), EPSILON);
    }

    @Test
    void aShatteredRowIsDeafToANudge() {
        Belief belief = new Belief();
        assertTrue(belief.expose(4, 0, Contradiction.TOUCH));
        belief.nudge(4, 0, 0.10F);
        assertTrue(belief.shattered(4, 0));
        assertEquals(0.0F, belief.get(4, 0), EPSILON);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.ConsensusTest" --tests "com.efkrdnz.magical.magic.mind.BeliefTest"`
Expected: FAIL. Compilation fails because `Consensus`, `Belief.consensus` and `Belief.nudge` do not exist.

- [ ] **Step 3: Write `Consensus.java`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.Set;

/**
 * When a lie becomes real. An element has a weight - half a point a block, a quarter of a creature's
 * health - and a consensus, the sum of each convinced viewer's belief times how much their mind
 * counts. It manifests when the consensus reaches the weight and holds while it stays above half.
 */
public final class Consensus {
    public static final float PER_BLOCK = 0.5F;
    public static final float PER_HEALTH = 0.25F;
    /** A real thing stays real down to this fraction of its weight. */
    public static final float HOLD = 0.5F;
    /** How long the lilac rim takes to fade off a thing that has just become real. */
    public static final int HARDEN_TICKS = 16;

    private static final float MIND = 1.0F;
    private static final float PLAYER = 3.0F;
    private static final float BOSS = 5.0F;
    private static final Set<String> BOSSES = Set.of("minecraft:wither", "minecraft:ender_dragon", "minecraft:elder_guardian");
    private static final double ROUNDING = 1.0E-4;

    private Consensus() {}

    public static float clusterWeight(int blocks) {
        return PER_BLOCK * blocks;
    }

    public static float figmentWeight(float maxHealth) {
        return PER_HEALTH * maxHealth;
    }

    /** How much one convinced mind of this kind counts toward making a thing real. */
    public static float voter(String entityTypeId) {
        if ("minecraft:player".equals(entityTypeId)) {
            return PLAYER;
        }
        return BOSSES.contains(entityTypeId) ? BOSS : MIND;
    }

    /** Whether an element is real after this step, given whether it was real before it. */
    public static boolean real(boolean manifested, float consensus, float weight) {
        if (weight <= 0.0F) {
            return false;
        }
        return consensus >= (manifested ? weight * HOLD : weight);
    }

    /** How many minds of one weight, each sure of it, make a thing of this weight real; -1 for never. */
    public static int needed(float weight, float voter) {
        if (voter <= 0.0F) {
            return -1;
        }
        return (int) Math.ceil(weight / (voter * Belief.SURE) - ROUNDING);
    }

    /** The share of its kind's health a real figment has, by how firmly it is agreed on. */
    public static float healthFraction(float consensus, float weight) {
        return weight <= 0.0F ? 1.0F : Math.min(1.0F, consensus / weight);
    }
}
```

- [ ] **Step 4: Add the two methods to `Belief.java`**

Insert them after `forget(int viewer)`, and add `import java.util.function.IntToDoubleFunction;`:

```java
    /**
     * Every element's consensus in one pass: the sum, over viewers convinced of it, of their belief
     * times what their mind counts ({@code voter}, by entity id). Rows for elements past the end are
     * ignored; shattered rows hold no belief and count for nothing.
     */
    public float[] consensus(int elements, IntToDoubleFunction voter) {
        float[] sums = new float[elements];
        for (Map.Entry<Long, Float> entry : values.entrySet()) {
            float b = entry.getValue();
            if (b < CONVINCED) {
                continue;
            }
            long key = entry.getKey();
            int element = (int) (key & 0xFFFFFFFFL);
            if (element < 0 || element >= elements) {
                continue;
            }
            sums[element] += (float) voter.applyAsDouble((int) (key >> 32)) * b;
        }
        return sums;
    }

    /**
     * Moves a belief by a step, from outside the viewer's own senses (Insist, a lie that hurts). It is
     * never evidence: it cannot shatter, a shattered row ignores it, and doubt alone never writes a row.
     */
    public void nudge(int viewer, int element, float delta) {
        long key = key(viewer, element);
        if (shattered.contains(key)) {
            return;
        }
        Float b = values.get(key);
        if (b == null) {
            if (delta > 0.0F) {
                values.put(key, Math.min(1.0F, delta));
            }
            return;
        }
        float next = Math.min(1.0F, b + delta);
        if (next <= 0.0F) {
            values.remove(key);
        } else {
            values.put(key, next);
        }
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.ConsensusTest" --tests "com.efkrdnz.magical.magic.mind.BeliefTest"`
Expected: PASS. Every test in both classes passes.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/Consensus.java src/main/java/com/efkrdnz/magical/magic/mind/Belief.java src/test/java/com/efkrdnz/magical/magic/mind/ConsensusTest.java src/test/java/com/efkrdnz/magical/magic/mind/BeliefTest.java
git commit -m "feat(mind): consensus weights, voters and the half-weight hold

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: KindStats and PhantomHarm

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/KindStats.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/PhantomHarm.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/KindStatsTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/PhantomHarmTest.java`

**Interfaces:**
- Produces on `KindStats`:
  - `maxHealth(String creatureId) -> float` (fallback 20);
  - `attack(String creatureId) -> float` (fallback 0).
- Produces on `PhantomHarm`:
  - constants `RAISE` (0.10F) and `INTERVAL` (20);
  - `of(String blockId)` and `of(List<String> blockIds)`;
  - `amount(float base, float belief)`;
  - `unmanifestable(String blockId)`.

- [ ] **Step 1: Write the failing tests**

`KindStatsTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KindStatsTest {
    private static final float EPSILON = 1.0E-5F;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aKindIsAsToughAndAsStrongAsItsVanillaSelf() {
        assertEquals(20.0F, KindStats.maxHealth("minecraft:zombie"), EPSILON);
        assertEquals(3.0F, KindStats.attack("minecraft:zombie"), EPSILON);
        assertEquals(100.0F, KindStats.maxHealth("minecraft:iron_golem"), EPSILON);
        assertEquals(15.0F, KindStats.attack("minecraft:iron_golem"), EPSILON);
        assertEquals(500.0F, KindStats.maxHealth("minecraft:warden"), EPSILON);
        assertEquals(30.0F, KindStats.attack("minecraft:warden"), EPSILON);
        assertEquals(20.0F, KindStats.maxHealth("minecraft:villager"), EPSILON);
        assertEquals(0.0F, KindStats.attack("minecraft:villager"), EPSILON);
    }

    @Test
    void anUnknownKindIsAnOrdinaryBodyWithNoBite() {
        assertEquals(20.0F, KindStats.maxHealth("minecraft:no_such_thing"), EPSILON);
        assertEquals(0.0F, KindStats.attack("minecraft:no_such_thing"), EPSILON);
        assertEquals(20.0F, KindStats.maxHealth("not an id"), EPSILON);
        assertEquals(0.0F, KindStats.attack("not an id"), EPSILON);
    }
}
```

`PhantomHarmTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhantomHarmTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void lavaBurnsHardestAndACactusPricks() {
        assertEquals(4.0F, PhantomHarm.of("minecraft:lava"), EPSILON);
        assertEquals(1.0F, PhantomHarm.of("minecraft:fire"), EPSILON);
        assertEquals(2.0F, PhantomHarm.of("minecraft:soul_fire"), EPSILON);
        assertEquals(1.0F, PhantomHarm.of("minecraft:magma_block"), EPSILON);
        assertEquals(1.0F, PhantomHarm.of("minecraft:cactus"), EPSILON);
        assertEquals(1.0F, PhantomHarm.of("minecraft:sweet_berry_bush"), EPSILON);
        assertEquals(0.0F, PhantomHarm.of("minecraft:stone"), EPSILON);
    }

    @Test
    void aClusterHurtsAsMuchAsItsWorstBlock() {
        assertEquals(4.0F, PhantomHarm.of(List.of("minecraft:stone", "minecraft:cactus", "minecraft:lava")), EPSILON);
        assertEquals(0.0F, PhantomHarm.of(List.of()), EPSILON);
    }

    @Test
    void harmIsScaledByHowFirmlyItIsBelieved() {
        assertEquals(2.4F, PhantomHarm.amount(4.0F, 0.6F), EPSILON);
    }

    @Test
    void fireAndFluidsNeverManifest() {
        assertTrue(PhantomHarm.unmanifestable("minecraft:fire"));
        assertTrue(PhantomHarm.unmanifestable("minecraft:soul_fire"));
        assertTrue(PhantomHarm.unmanifestable("minecraft:lava"));
        assertTrue(PhantomHarm.unmanifestable("minecraft:water"));
        assertFalse(PhantomHarm.unmanifestable("minecraft:stone"));
        assertFalse(PhantomHarm.unmanifestable("minecraft:cactus"));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.KindStatsTest" --tests "com.efkrdnz.magical.magic.mind.PhantomHarmTest"`
Expected: FAIL. Compilation fails because the classes do not exist.

- [ ] **Step 3: Write `PhantomHarm.java`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an imagined block does to a body that believes it. A lie that hurts is believed more: a
 * believer who touches imagined lava is burned by it, magic damage times their belief, and comes away
 * surer. Fire and fluids are never placed as real blocks, so imagined lava only ever burns the mind.
 */
public final class PhantomHarm {
    /** How much surer a believer is after a lie has hurt them. */
    public static final float RAISE = 0.10F;
    /** Ticks between burns while a believer stays inside. */
    public static final int INTERVAL = 20;

    private static final Map<String, Float> HARM = Map.of(
            "minecraft:lava", 4.0F,
            "minecraft:fire", 1.0F,
            "minecraft:soul_fire", 2.0F,
            "minecraft:magma_block", 1.0F,
            "minecraft:cactus", 1.0F,
            "minecraft:sweet_berry_bush", 1.0F);
    private static final Set<String> UNMANIFESTABLE = Set.of(
            "minecraft:fire", "minecraft:soul_fire", "minecraft:lava", "minecraft:water");

    private PhantomHarm() {}

    public static float of(String blockId) {
        return HARM.getOrDefault(blockId, 0.0F);
    }

    /** A cluster hurts as much as the worst block in it. */
    public static float of(List<String> blockIds) {
        float worst = 0.0F;
        for (String id : blockIds) {
            worst = Math.max(worst, of(id));
        }
        return worst;
    }

    public static float amount(float base, float belief) {
        return base * belief;
    }

    /** Whether a block is never placed for real, however many minds agree on it. */
    public static boolean unmanifestable(String blockId) {
        return UNMANIFESTABLE.contains(blockId);
    }
}
```

- [ ] **Step 4: Write `KindStats.java`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.Optional;

/**
 * A creature kind's own numbers, read off its vanilla attributes: what a figment of it weighs and how
 * hard it bites once it is real. No level is needed, so the Playbill can ask on the client.
 */
public final class KindStats {
    private static final float ORDINARY_HEALTH = 20.0F;

    private KindStats() {}

    public static float maxHealth(String creatureId) {
        return base(creatureId, Attributes.MAX_HEALTH, ORDINARY_HEALTH);
    }

    public static float attack(String creatureId) {
        return base(creatureId, Attributes.ATTACK_DAMAGE, 0.0F);
    }

    @SuppressWarnings("unchecked")
    private static float base(String creatureId, Holder<Attribute> attribute, float fallback) {
        ResourceLocation id = ResourceLocation.tryParse(creatureId);
        if (id == null) {
            return fallback;
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty() || !DefaultAttributes.hasSupplier(type.get())) {
            return fallback;
        }
        AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type.get());
        return supplier.hasAttribute(attribute) ? (float) supplier.getBaseValue(attribute) : fallback;
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.KindStatsTest" --tests "com.efkrdnz.magical.magic.mind.PhantomHarmTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/KindStats.java src/main/java/com/efkrdnz/magical/magic/mind/PhantomHarm.java src/test/java/com/efkrdnz/magical/magic/mind/KindStatsTest.java src/test/java/com/efkrdnz/magical/magic/mind/PhantomHarmTest.java
git commit -m "feat(mind): a kind's vanilla numbers and the harm an imagined block does

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: SceneAim, and the scene's real, slain and consensus state

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/SceneAim.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/LiveScene.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/SceneAimTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/LiveSceneClockTest.java`

**Interfaces:**
- Consumes: `Consensus.clusterWeight`, `Consensus.figmentWeight`, `KindStats.maxHealth`.
- Produces on `SceneAim`:
  - `Hit(int index, double distance)`;
  - `nearest(Vec3 from, Vec3 to, List<AABB> boxes) -> Hit or null`.
- Produces on `LiveScene`, as package fields: `Set<Integer> manifested`, `Set<Integer> slain`, `Map<Integer, UUID> edits`, `float[] consensus`.
- Produces on `LiveScene`, as public methods:
  - `manifested(int)`, `slain(int)`, `anythingReal()`, `manifestedList() -> List<Integer>`;
  - `consensus(int) -> float` and `weight(int) -> float`;
  - `aim(Vec3 from, Vec3 to, IntFunction<AABB> figmentBox) -> SceneAim.Hit`, where the index is an element index, or null;
  - `static boolean over(long age, boolean anythingReal)` and `boolean over(long now)`.

- [ ] **Step 1: Write the failing tests**

`SceneAimTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneAimTest {
    private static final Vec3 EYE = new Vec3(0.0, 0.5, 0.5);
    private static final Vec3 FAR = new Vec3(10.0, 0.5, 0.5);

    @Test
    void theNearestBoxAlongTheRayIsTheOneAimedAt() {
        List<AABB> boxes = List.of(new AABB(5, 0, 0, 6, 1, 1), new AABB(2, 0, 0, 3, 1, 1), new AABB(0, 5, 0, 1, 6, 1));
        SceneAim.Hit hit = SceneAim.nearest(EYE, FAR, boxes);
        assertNotNull(hit);
        assertEquals(1, hit.index());
        assertEquals(2.0, hit.distance(), 1.0E-6);
    }

    @Test
    void aRayThatMeetsNothingAimsAtNothing() {
        assertNull(SceneAim.nearest(EYE, FAR, List.of(new AABB(0, 5, 0, 1, 6, 1))));
        assertNull(SceneAim.nearest(EYE, FAR, List.of()));
        assertNull(SceneAim.nearest(EYE, new Vec3(1.5, 0.5, 0.5), List.of(new AABB(2, 0, 0, 3, 1, 1))), "the ray stops short");
    }

    @Test
    void aBoxTheEyeIsInsideIsAimedAtFromNoDistanceAtAll() {
        SceneAim.Hit hit = SceneAim.nearest(new Vec3(0.5, 0.5, 0.5), FAR,
                List.of(new AABB(5, 0, 0, 6, 1, 1), new AABB(0, 0, 0, 1, 1, 1)));
        assertNotNull(hit);
        assertEquals(1, hit.index());
        assertEquals(0.0, hit.distance(), 1.0E-9);
    }
}
```

`LiveSceneClockTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveSceneClockTest {
    @Test
    void aSceneOutlivesItsClockOnlyWhileSomethingInItIsReal() {
        assertFalse(LiveScene.over(LiveScene.LIFE_TICKS - 1, false));
        assertTrue(LiveScene.over(LiveScene.LIFE_TICKS, false));
        assertFalse(LiveScene.over(LiveScene.LIFE_TICKS * 5L, true));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.SceneAimTest" --tests "com.efkrdnz.magical.magic.mind.LiveSceneClockTest"`
Expected: FAIL (compilation error).

- [ ] **Step 3: Write `SceneAim.java`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** The nearest of some boxes along a ray. Imagined blocks are not in the world, so no level clip finds them. */
public final class SceneAim {
    public record Hit(int index, double distance) {}

    private SceneAim() {}

    public static Hit nearest(Vec3 from, Vec3 to, List<AABB> boxes) {
        Hit best = null;
        for (int i = 0; i < boxes.size(); i++) {
            AABB box = boxes.get(i);
            double distance;
            if (box.contains(from)) {
                distance = 0.0;
            } else {
                Optional<Vec3> at = box.clip(from, to);
                if (at.isEmpty()) {
                    continue;
                }
                distance = from.distanceTo(at.get());
            }
            if (best == null || distance < best.distance()) {
                best = new Hit(i, distance);
            }
        }
        return best;
    }
}
```

- [ ] **Step 4: Extend `LiveScene.java`**

Add the imports `net.minecraft.world.phys.Vec3`, `java.util.Collections` and `java.util.function.IntFunction`.

After the `audience` field, add:

```java
    /** Elements that are real right now; see {@code Manifestation}. */
    final Set<Integer> manifested = new HashSet<>();
    /** Real figments that were killed: gone from the scene for good. */
    final Set<Integer> slain = new HashSet<>();
    /** The terrain edit holding each real cluster's blocks, by element. */
    final Map<Integer, UUID> edits = new HashMap<>();
    /** Each element's consensus at the last step, see {@link Consensus}. */
    float[] consensus;
```

At the end of the constructor, after `this.plausibility = new float[elements.size()];`, add:

```java
        this.consensus = new float[elements.size()];
```

After `expired(long now)`, add:

```java
    /** Whether a scene is finished: its clock has run out and nothing in it is real. */
    public static boolean over(long age, boolean anythingReal) {
        return age >= LIFE_TICKS && !anythingReal;
    }

    public boolean over(long now) {
        return over(now - bornAt, anythingReal());
    }

    public boolean manifested(int element) {
        return manifested.contains(element);
    }

    public boolean slain(int element) {
        return slain.contains(element);
    }

    public boolean anythingReal() {
        return !manifested.isEmpty();
    }

    /** The real elements in index order, for the wire. */
    public List<Integer> manifestedList() {
        List<Integer> list = new ArrayList<>(manifested);
        Collections.sort(list);
        return list;
    }

    public float consensus(int element) {
        return consensus[element];
    }

    /** What an element weighs: half a point a block, a quarter of its kind's health. */
    public float weight(int element) {
        Element e = elements.get(element);
        return e.kind() == Kind.CLUSTER ? Consensus.clusterWeight(e.cells().size())
                : Consensus.figmentWeight(KindStats.maxHealth(e.figment().creatureId()));
    }

    /**
     * The element nearest along a ray: a cluster by any of its cells, a figment by the box it has now
     * ({@code figmentBox}, by element index). Slain elements are not there to aim at.
     */
    public SceneAim.Hit aim(Vec3 from, Vec3 to, IntFunction<AABB> figmentBox) {
        List<AABB> boxes = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        for (Element element : elements) {
            if (slain(element.index())) {
                continue;
            }
            if (element.kind() == Kind.CLUSTER) {
                for (BlockPos cell : element.cells()) {
                    boxes.add(new AABB(cell));
                    owners.add(element.index());
                }
            } else {
                boxes.add(figmentBox.apply(element.index()));
                owners.add(element.index());
            }
        }
        SceneAim.Hit hit = SceneAim.nearest(from, to, boxes);
        return hit == null ? null : new SceneAim.Hit(owners.get(hit.index()), hit.distance());
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.*"`
Expected: PASS for every mind unit test.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/SceneAim.java src/main/java/com/efkrdnz/magical/magic/mind/LiveScene.java src/test/java/com/efkrdnz/magical/magic/mind/SceneAimTest.java src/test/java/com/efkrdnz/magical/magic/mind/LiveSceneClockTest.java
git commit -m "feat(mind): a scene knows what in it is real, slain and agreed on

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Real clusters

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/Manifestation.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/ManifestGuard.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/ManifestGameTests.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindService.java` (`end`, `onServerTick`, `onServerStopping`, `tickScene`, `touches`, `projectiles`, `perceiveAll`)
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindPathing.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindPathGameTests.java` (one belief value in `mind_path_3`)
- Modify: `src/main/resources/assets/magical/lang/en_us.json`

**Interfaces:**
- Consumes:
  - the `LiveScene` state from Task 3;
  - `Belief.consensus`, `Consensus.real` and `Consensus.voter` from Task 1;
  - `PhantomHarm.unmanifestable` from Task 2;
  - `ConjuredTerrainService.begin`, `replace`, `restore`, `restoreUnlessBuiltOver` and `lookup` (package `com.efkrdnz.magical.magic.service`).
- Produces (all package-private in `magic.mind`):
  - `Manifestation.step(ServerLevel, LiveScene, long now)`;
  - `Manifestation.manifest(ServerLevel, LiveScene, Element) -> boolean`;
  - `Manifestation.unmanifest(ServerLevel, LiveScene, Element, boolean tell)`;
  - `Manifestation.revertAll(LiveScene)`;
  - `Manifestation.holds(ServerLevel, BlockPos) -> boolean`;
  - `Manifestation.stateOf(String blockId) -> BlockState or null`.
- Lang keys: `message.magical.manifested`, `message.magical.unmanifested`.

- [ ] **Step 1: Write the failing game tests**

`ManifestGameTests.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class ManifestGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private ManifestGameTests() {}

    /** A column of two stones (weight 1) standing on the floor, and one husk certain of it (consensus 1). */
    private static LiveScene believedColumn(GameTestHelper helper, UUID owner, LivingEntity[] believer) {
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 3)));
        LiveScene scene = MindGameTests.unveil(helper, owner, MindGameTests.column(), anchor);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 1))));
        scene.belief().set(husk.getId(), 0, 1.0F);
        believer[0] = husk;
        return scene;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_1")
    public static void aClusterEnoughMindsAgreeOnIsRealForEveryone(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedColumn(helper, owner, new LivingEntity[1]);
        BlockPos base = scene.elements().get(0).cells().get(0);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(scene.manifested(0), "consensus " + scene.consensus(0) + " against weight " + scene.weight(0) + " made nothing real");
            helper.assertTrue(helper.getLevel().getBlockState(base).is(Blocks.STONE), "the column's foot is not stone");
            helper.assertTrue(helper.getLevel().getBlockState(base.above()).is(Blocks.STONE), "the column's head is not stone");
            MindService.endAll(owner);
            helper.assertTrue(helper.getLevel().getBlockState(base).isAir() && helper.getLevel().getBlockState(base.above()).isAir(),
                    "ending the scene did not give the ground back");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_2")
    public static void aRealClusterFallsBackWhenTheAgreementGoes(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LivingEntity[] believer = new LivingEntity[1];
        LiveScene scene = believedColumn(helper, owner, believer);
        BlockPos base = scene.elements().get(0).cells().get(0);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(scene.manifested(0), "the column never became real");
            // 0.6 is still half the weight: it holds.
            scene.belief().set(believer[0].getId(), 0, 0.6F);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(scene.manifested(0), "a real column fell at " + scene.consensus(0) + ", above half its weight");
            scene.belief().set(believer[0].getId(), 0, 0.3F);
        });
        helper.runAtTickTime(28, () -> {
            helper.assertFalse(scene.manifested(0), "nobody is convinced and the column is still real");
            helper.assertTrue(helper.getLevel().getBlockState(base).isAir(), "the stone was not given back");
            helper.assertTrue(MindService.scene(scene.id()) != null, "reverting ended the scene");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_3")
    public static void aRealBlockBrokenDropsNothing(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedColumn(helper, owner, new LivingEntity[1]);
        BlockPos head = scene.elements().get(0).cells().get(1);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(scene.manifested(0), "the column never became real");
            helper.getLevel().destroyBlock(head, true);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(head).inflate(3.0)).isEmpty(),
                    "breaking an agreed-on stone paid out a real one");
            MindService.endAll(owner);
            helper.succeed();
        });
    }
}
```

- [ ] **Step 2: Run the game tests to verify they fail**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: `mind_manifest_1` to `mind_manifest_3` FAIL, for example with "consensus 0.0 against weight 1.0 made nothing real".

- [ ] **Step 3: Write `Manifestation.java`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where a lie becomes real and stops being real. Every {@link MindSync#BELIEF_INTERVAL} ticks each
 * element's consensus is summed and held against its weight ({@link Consensus#real}). A cluster is
 * placed through {@link ConjuredTerrainService}, so what it replaced is always given back; a figment is
 * turned real by its own flag.
 */
final class Manifestation {
    private Manifestation() {}

    static void step(ServerLevel level, LiveScene scene, long now) {
        if ((now - scene.bornAt()) % MindSync.BELIEF_INTERVAL != 0) {
            return;
        }
        scene.consensus = scene.belief().consensus(scene.elements().size(), viewer -> voter(level, viewer));
        for (LiveScene.Element element : scene.elements()) {
            int index = element.index();
            if (scene.slain(index)) {
                continue;
            }
            boolean was = scene.manifested(index);
            boolean real = Consensus.real(was, scene.consensus(index), scene.weight(index));
            if (real && !was) {
                if (manifest(level, scene, element)) {
                    scene.manifested.add(index);
                    tell(level, scene, "message.magical.manifested");
                }
            } else if (!real && was) {
                unmanifest(level, scene, element, true);
            }
        }
    }

    private static double voter(ServerLevel level, int viewer) {
        Entity entity = level.getEntity(viewer);
        return entity == null ? 0.0 : Consensus.voter(MindService.typeId(entity));
    }

    static boolean manifest(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        return switch (element.kind()) {
            case CLUSTER -> manifestCluster(level, scene, element);
            case FIGMENT -> false;
        };
    }

    static void unmanifest(ServerLevel level, LiveScene scene, LiveScene.Element element, boolean tell) {
        if (!scene.manifested.remove(element.index())) {
            return;
        }
        if (element.kind() == LiveScene.Kind.CLUSTER) {
            unmanifestCluster(level, scene, element);
        }
        Vec3 at = element.box().getCenter();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.0F);
        if (tell) {
            tell(level, scene, "message.magical.unmanifested");
        }
    }

    /** Gives back everything a scene made real; called as the scene ends. */
    static void revertAll(LiveScene scene) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(scene.dimension());
        if (level == null) {
            return;
        }
        for (int index : scene.manifestedList()) {
            unmanifest(level, scene, scene.elements().get(index), false);
        }
    }

    /** Whether a block position is held real by some scene in this level. */
    static boolean holds(ServerLevel level, BlockPos pos) {
        for (LiveScene scene : MindService.scenesIn(level.dimension())) {
            int element = scene.elementAt(pos);
            if (element >= 0 && scene.manifested(element)) {
                return true;
            }
        }
        return false;
    }

    static BlockState stateOf(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
    }

    private static boolean manifestCluster(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        for (int i = 0; i < element.cells().size(); i++) {
            String id = element.blockIds().get(i);
            BlockState state = PhantomHarm.unmanifestable(id) ? null : stateOf(id);
            BlockPos pos = element.cells().get(i);
            if (state == null || !level.isLoaded(pos)) {
                continue;
            }
            BlockState here = level.getBlockState(pos);
            if (!here.canBeReplaced() || here.hasBlockEntity() || !clearOfBodies(level, pos)) {
                continue;
            }
            if (ConjuredTerrainService.replace(level, edit, pos, state)) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            }
        }
        if (edit.size() == 0) {
            ConjuredTerrainService.restore(level, edit);
            return false;
        }
        scene.edits.put(element.index(), edit.id());
        return true;
    }

    private static void unmanifestCluster(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        UUID id = scene.edits.remove(element.index());
        ConjuredTerrainService.Edit edit = id == null ? null : ConjuredTerrainService.lookup(level, id);
        if (edit == null) {
            return;
        }
        Set<BlockState> placed = new HashSet<>();
        for (String blockId : element.blockIds()) {
            BlockState state = stateOf(blockId);
            if (state != null) {
                placed.add(state);
            }
        }
        ConjuredTerrainService.restoreUnlessBuiltOver(level, edit, placed::contains);
    }

    /** No real block is put inside a living body; a ghost figment is no body. */
    private static boolean clearOfBodies(ServerLevel level, BlockPos pos) {
        List<LivingEntity> bodies = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos),
                entity -> entity.isAlive() && !FigmentEntity.isFigment(entity));
        return bodies.isEmpty();
    }

    private static void tell(ServerLevel level, LiveScene scene, String key) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(scene.owner());
        if (owner != null) {
            owner.displayClientMessage(Component.translatable(key), true);
        }
    }
}
```

- [ ] **Step 4: Write `ManifestGuard.java`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * A real block is only as real as the minds agreeing on it: breaking one yields nothing, and an
 * explosion does not break it at all. Without this an imagined diamond wall is real diamonds.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class ManifestGuard {
    private ManifestGuard() {}

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (MindService.anyLive() && Manifestation.holds(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (MindService.anyLive() && event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> Manifestation.holds(level, pos));
        }
    }
}
```

- [ ] **Step 5: Wire it into `MindService.java`**

In `end(LiveScene scene)`, call `Manifestation.revertAll(scene);` on the line immediately before `MindSync.ended(scene);`.

Replace the body of the loop in `onServerTick` with:

```java
            ServerLevel level = server.getLevel(scene.dimension());
            if (level == null || scene.over(level.getGameTime())) {
                end(scene);
                continue;
            }
            tickScene(level, scene);
```

Replace the body of `onServerStopping` with:

```java
        // The ledger would give the blocks back at the next load anyway; this gives them back now.
        allScenes().forEach(Manifestation::revertAll);
        SCENES.clear();
        // A memory's expiry is an absolute game time; carried into a world with a lower clock it would
        // never be pruned, and the host's own player would bring the old world's doubt with them.
        SCEPTICISM.clear();
```

In `tickScene`, call `Manifestation.step(level, scene, now);` on the line immediately before `MindSync.tick(level, scene);`.

In `perceiveAll`, extend the skip condition so it also skips slain elements:

```java
            if (scene.slain(index) || scene.belief().shattered(id, index) || scene.inside.contains(LiveScene.key(id, index))) {
```

In `touches`, add the following right after `long key = LiveScene.key(viewer.getId(), element.index());`:

```java
                if (scene.manifested(element.index())) {
                    // A real block cannot be walked into; nothing about touching it is evidence.
                    scene.inside.remove(key);
                    continue;
                }
```

In `projectiles`, add this at the top of the element loop:

```java
            if (scene.manifested(element.index()) || scene.slain(element.index())) {
                continue;
            }
```

- [ ] **Step 6: Make the pathfinder ignore real clusters**

A real cluster is real terrain and the pathfinder already sees it. In `MindPathing.override`, guard both checks:

```java
            int solid = scene.elementAt(here);
            if (solid >= 0 && !scene.manifested(solid) && scene.belief().get(mob.getId(), solid) >= Belief.PATHING) {
                return PathType.BLOCKED;
            }
            int floor = scene.elementAt(below);
            if (floor >= 0 && !scene.manifested(floor) && scene.belief().get(mob.getId(), floor) >= Belief.PATHING
                    && context.getPathTypeFromState(x, y, z) == PathType.OPEN) {
                return PathType.WALKABLE;
            }
```

- [ ] **Step 7: Keep `mind_path_3` about trusting a floor, not making one**

`mind_path_3` is the one existing test whose lie now reaches consensus. Its lid is one stone (weight 0.5), and its husk believes it at 0.6. The test is about a mob trusting an imagined floor, so move that belief below `CONVINCED`. It stays above `Belief.PATHING`, which is 0.3, so the husk still trusts the floor. In `MindPathGameTests.java` at the `mind_path_3` test, change:

```java
        scene.belief().set(husk.getId(), 0, 0.6F);
```

to:

```java
        // Below CONVINCED, so one husk cannot make the lid real; above PATHING, so it still trusts it.
        scene.belief().set(husk.getId(), 0, 0.45F);
```

The husk still falls and its belief still shatters: 0.45 - 0.60 < 0.1.

- [ ] **Step 8: Add the lang keys**

In `en_us.json`, next to `message.magical.unveil_nowhere`:

```json
  "message.magical.manifested": "Enough minds agree: it is real.",
  "message.magical.unmanifested": "Doubt takes it back.",
```

- [ ] **Step 9: Run the game tests to verify they pass**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: `mind_manifest_1` to `mind_manifest_3` PASS, and every other game test stays green. That includes all of `MindGameTests`, `MindPathGameTests` and `FigmentGameTests`.

- [ ] **Step 10: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/Manifestation.java src/main/java/com/efkrdnz/magical/magic/mind/ManifestGuard.java src/main/java/com/efkrdnz/magical/magic/mind/ManifestGameTests.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/magic/mind/MindPathing.java src/main/java/com/efkrdnz/magical/magic/mind/MindPathGameTests.java src/main/resources/assets/magical/lang/en_us.json
git commit -m "feat(mind): a cluster enough minds agree on is placed for real and given back

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Real figments

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/entity/mind/FigmentEntity.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/Manifestation.java` (the FIGMENT branch, plus a hold step for health)
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindService.java` (`figmentStruck`, `figmentStrikes`, the new `figmentSlain`, and the hunter release in `tickScene`)
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/ManifestGameTests.java` (two tests)

**Interfaces:**
- Consumes:
  - `KindStats.maxHealth` and `KindStats.attack`;
  - `Consensus.healthFraction` and `Consensus.HARDEN_TICKS`;
  - `Manifestation` from Task 4.
- Produces on `FigmentEntity`:
  - `isManifested()`;
  - `manifest(float maxHealth, float attack)`, `unmanifest()` and `hold(float maxHealth)`;
  - `hardening(float partial) -> float`. This is client-side: 1 on the manifest tick, falling to 0 over `HARDEN_TICKS`.
- Produces on `MindService`: `figmentSlain(FigmentEntity)`.
- Changes: `FigmentEntity.isFigment(e)` is false for a manifested figment.

- [ ] **Step 1: Write the failing game tests**

Add these to `ManifestGameTests.java`. They need the imports `com.efkrdnz.magical.entity.mind.FigmentEntity`, `com.efkrdnz.magical.gametest.GameTestPlayers` and `net.minecraft.server.level.ServerPlayer`. A chicken weighs 1 (4 health / 4), so one husk certain of it makes it real.

```java
    private static final String CHICKEN = "minecraft:chicken";

    /** A chicken figment on the floor at (2,2,3) and a husk at (2,2,1) certain of it. */
    private static LiveScene believedChicken(GameTestHelper helper, UUID owner, LivingEntity[] believer) {
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("creature:" + CHICKEN);
        reverie.addFigment(new Offset(0, 0, 0), CHICKEN, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie,
                BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 3))), 0, lexicon);
        helper.assertTrue(scene != null, "the chicken was refused");
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 1))));
        scene.belief().set(husk.getId(), 0, 1.0F);
        believer[0] = husk;
        return scene;
    }

    private static FigmentEntity creature(GameTestHelper helper, LiveScene scene) {
        return (FigmentEntity) helper.getLevel().getEntity(scene.figmentEntity(0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_4")
    public static void aRealFigmentTakesRealBlowsAndWeakensAsDoubtGrows(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LivingEntity[] believer = new LivingEntity[1];
        LiveScene scene = believedChicken(helper, owner, believer);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(1, 2, 3), "mind-real-test");
        helper.runAtTickTime(12, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "the chicken never became real");
            helper.assertFalse(FigmentEntity.isFigment(chicken), "a real chicken is still no body to the mod");
            helper.assertTrue(chicken.canBeSeenAsEnemy(), "a real chicken is still no enemy to vanilla");
            player.attack(chicken);
            helper.assertTrue(chicken.getHealth() < chicken.getMaxHealth(), "a blow on a real chicken landed nothing");
            helper.assertFalse(scene.belief().shattered(player.getId(), 0), "striking a real thing was taken as evidence it is not there");
            // Consensus 0.6 of a weight of 1 holds it, at six tenths of a chicken.
            scene.belief().set(believer[0].getId(), 0, 0.6F);
        });
        helper.runAtTickTime(20, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "a chicken held at 0.6 of its weight fell");
            helper.assertTrue(Math.abs(chicken.getMaxHealth() - 2.4F) < 0.01F, "max health " + chicken.getMaxHealth() + " is not 0.6 of 4");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_manifest_5")
    public static void aSlainFigmentIsGoneForGood(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LiveScene scene = believedChicken(helper, owner, new LivingEntity[1]);
        helper.runAtTickTime(12, () -> {
            FigmentEntity chicken = creature(helper, scene);
            helper.assertTrue(chicken.isManifested(), "the chicken never became real");
            chicken.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 100.0F);
            helper.assertTrue(scene.slain(0), "a killed chicken is not slain");
            helper.assertFalse(scene.manifested(0), "a slain chicken is still counted real");
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(scene.slain(0) && !scene.manifested(0), "the chicken came back");
            MindService.endAll(owner);
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run the game tests to verify they fail**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: compilation fails on `isManifested`. Once it compiles, `mind_manifest_4` and `mind_manifest_5` fail with "the chicken never became real".

- [ ] **Step 3: Turn `FigmentEntity` real on a flag**

Add the imports `net.minecraft.world.entity.ai.attributes.AttributeInstance` and `com.efkrdnz.magical.magic.mind.Consensus`.

After `ELEMENT`, add the accessor:

```java
    private static final EntityDataAccessor<Boolean> MANIFESTED = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.BOOLEAN);
```

After `owner`, add a client-only field:

```java
    /** Client: the game time this figment became real, for the hardening rim; see {@link #hardening}. */
    private long hardenedAt = Long.MIN_VALUE;
```

In `defineSynchedData`, add `builder.define(MANIFESTED, false);`.

In `createAttributes`, add `.add(Attributes.ATTACK_DAMAGE, 0.0)`.

Replace `isFigment` with:

```java
    /**
     * Whether an entity is out of a reverie and not real: no body to anything outside the Mind code. A
     * figment enough minds agree on is real, and answers false - every spell, sweep and target can find it.
     */
    public static boolean isFigment(Entity entity) {
        return entity instanceof FigmentEntity figment && !figment.isManifested();
    }
```

Add these methods after `owner()`:

```java
    public boolean isManifested() {
        return entityData.get(MANIFESTED);
    }

    /** Becomes real: its kind's health, as much of it as the agreement allows, and its kind's bite. */
    public void manifest(float maxHealth, float attack) {
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(attack);
        }
        hold(maxHealth);
        setHealth(getMaxHealth());
        entityData.set(MANIFESTED, true);
    }

    /** Keeps a real figment's health within what the agreement still allows; never heals it. */
    public void hold(float maxHealth) {
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Math.max(1.0F, maxHealth));
        }
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    public void unmanifest() {
        entityData.set(MANIFESTED, false);
    }

    /** Client: how much of the lilac rim is left on a figment that has just become real, 1 to 0. */
    public float hardening(float partial) {
        if (!isManifested() || hardenedAt == Long.MIN_VALUE) {
            return 0.0F;
        }
        float age = (level().getGameTime() - hardenedAt) + partial;
        return Math.max(0.0F, Math.min(1.0F, 1.0F - age / Consensus.HARDEN_TICKS));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MANIFESTED.equals(key)) {
            // Both sides: a client that still thought it no body would let a block be placed into it.
            this.blocksBuilding = isManifested();
            if (level().isClientSide() && isManifested()) {
                hardenedAt = level().getGameTime();
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            MindService.figmentSlain(this);
        }
    }
```

Make every ghost override conditional. Keep each existing javadoc and add one line to it: "A real figment is a body like any other."

```java
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isManifested() || source.is(DamageTypes.GENERIC_KILL)) {
            return super.hurtServer(level, source, amount);
        }
        if (source.getDirectEntity() == source.getEntity() && source.getEntity() instanceof LivingEntity attacker) {
            MindService.figmentStruck(this, attacker);
        }
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return isManifested() && super.canBeHitByProjectile();
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return isManifested() ? super.getMovementEmission() : MovementEmission.NONE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return !isManifested() || super.isIgnoringBlockTriggers();
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return isManifested() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean displayFireAnimation() {
        return isManifested() && super.displayFireAnimation();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canBeAffected(MobEffectInstance effect) {
        return isManifested() && super.canBeAffected(effect);
    }

    @Override
    protected void doWaterSplashEffect() {
        if (isManifested()) {
            super.doWaterSplashEffect();
        }
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return isManifested() && super.canDrownInFluidType(type);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (isManifested()) {
            super.checkFallDamage(y, onGround, state, pos);
            return;
        }
        if (onGround) {
            resetFallDistance();
        } else if (y < 0.0) {
            fallDistance -= (float) y;
        }
    }

    @Override
    public boolean isPushable() {
        return isManifested() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (isManifested()) {
            super.pushEntities();
        }
    }

    @Override
    public boolean isPickable() {
        return !level().isClientSide() || isManifested() || clientSees.test(sceneId(), element());
    }
```

Change the end of the class javadoc to: "... and each client draws it only for a mind that believes it - until enough minds agree on it, when it is real for everyone ({@link #isManifested})."

- [ ] **Step 4: Fill the FIGMENT branch in `Manifestation`, and hold real figments to their consensus**

Replace `case FIGMENT -> false;` with `case FIGMENT -> manifestFigment(level, scene, element);`.

In `unmanifest`, turn the CLUSTER `if` into an if/else. It must read:

```java
        if (element.kind() == LiveScene.Kind.CLUSTER) {
            unmanifestCluster(level, scene, element);
        } else if (level.getEntity(scene.figmentEntity(element.index())) instanceof FigmentEntity figment) {
            figment.unmanifest();
        }
```

In `step`, extend the if/else chain so it reads `if (real && !was) {...} else if (!real && was) {...} else if (...) {...}`. The new last branch is:

```java
            } else if (real && element.kind() == LiveScene.Kind.FIGMENT
                    && level.getEntity(scene.figmentEntity(index)) instanceof FigmentEntity figment) {
                figment.hold(KindStats.maxHealth(element.figment().creatureId())
                        * Consensus.healthFraction(scene.consensus(index), scene.weight(index)));
            }
```

Add this method:

```java
    private static boolean manifestFigment(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        if (!(level.getEntity(scene.figmentEntity(element.index())) instanceof FigmentEntity figment) || !figment.isAlive()) {
            return false;
        }
        String kind = element.figment().creatureId();
        figment.manifest(KindStats.maxHealth(kind) * Consensus.healthFraction(scene.consensus(element.index()), scene.weight(element.index())),
                KindStats.attack(kind));
        level.playSound(null, figment.getX(), figment.getY(), figment.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }
```

- [ ] **Step 5: MindService: real blows, real strikes, the slain, and hunters kept**

In `figmentStruck`, add `|| figment.isManifested()` to the early-return condition, with the comment "A real figment is really there: a blow on it is a blow, not evidence."

Replace `figmentStrikes` with:

```java
    /**
     * A figment lands a blow. A real one bites with its kind's attack; on a believer it is phantom harm
     * (see MindHarm); on a doubter nothing happens, and that is the evidence.
     */
    public static void figmentStrikes(com.efkrdnz.magical.entity.mind.FigmentEntity figment, LivingEntity target) {
        LiveScene scene = scene(figment.sceneId());
        if (scene == null || !(figment.level() instanceof ServerLevel level)) {
            return;
        }
        if (figment.isManifested()) {
            float attack = (float) figment.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (attack > 0.0F) {
                target.hurtServer(level, figment.damageSources().mobAttack(figment), attack);
            }
            return;
        }
        if (scene.belief().get(target.getId(), figment.element()) < Belief.CONVINCED) {
            expose(scene, target, scene.elements().get(figment.element()), Contradiction.HOLLOW_STRIKE, level.getGameTime());
        }
    }

    /** A real figment was killed: it is gone from its scene for good. */
    public static void figmentSlain(com.efkrdnz.magical.entity.mind.FigmentEntity figment) {
        LiveScene scene = scene(figment.sceneId());
        if (scene == null) {
            return;
        }
        scene.slain.add(figment.element());
        scene.manifested.remove(figment.element());
    }
```

In `tickScene`, add `&& !figment.isManifested()` to the hunter-release condition. A hunter that doubts a real villager must not let it go.

- [ ] **Step 6: Run the game tests to verify they pass**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: `mind_manifest_1` to `mind_manifest_5` PASS. Every `FigmentGameTests` test stays green: those figments never manifest, because one believer at 0.6 against a villager weighing 5 is not enough.

- [ ] **Step 7: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/entity/mind/FigmentEntity.java src/main/java/com/efkrdnz/magical/magic/mind/Manifestation.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/magic/mind/ManifestGameTests.java
git commit -m "feat(mind): a figment enough minds agree on is a body, as hale as the agreement

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Phantom harm

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/MindHarm.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/PhantomHarmGameTests.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindService.java` (`touches`, `tickScene`, `figmentStrikes`)

**Interfaces:**
- Consumes:
  - `PhantomHarm`, `KindStats.attack` and `Belief.nudge`;
  - `MagicDamageService.hurt(LivingEntity, DamageSource, float, ResourceLocation)` (package `com.efkrdnz.magical.magic`).
- Produces (package-private): `MindHarm.touched(...)`, `MindHarm.smoulder(...)`, `MindHarm.struck(...)`.

- [ ] **Step 1: Write the failing game tests**

`PhantomHarmGameTests.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class PhantomHarmGameTests {
    private static final String TEMPLATE = "unwaking_empty";
    private static final String LAVA = "minecraft:lava";
    private static final String ZOMBIE = "minecraft:zombie";

    private PhantomHarmGameTests() {}

    /** One imagined lava block where the player stands. */
    private static LiveScene lavaUnder(GameTestHelper helper, UUID owner, ServerPlayer player) {
        Lexicon lexicon = MindGameTests.knowing("block:" + LAVA);
        Reverie reverie = new Reverie();
        reverie.addBlock(new Offset(0, 0, 0), LAVA, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie, player.blockPosition(), 0, lexicon);
        helper.assertTrue(scene != null, "the lava was refused");
        return scene;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_harm_1")
    public static void aBelieverWhoWalksIntoImaginedLavaIsBurnedAndBelievesMore(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "mind-burned-test");
        UUID owner = UUID.randomUUID();
        LiveScene scene = lavaUnder(helper, owner, player);
        scene.belief().set(player.getId(), 0, 0.6F);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "imagined lava burned nothing");
            helper.assertFalse(scene.belief().shattered(player.getId(), 0), "the burn was taken as evidence against the lava");
            float now = scene.belief().get(player.getId(), 0);
            helper.assertTrue(now > 0.65F && now < 0.75F, "belief " + now + " did not rise by a tenth");
            helper.assertFalse(scene.manifested(0), "lava was placed for real");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_harm_2")
    public static void aDoubterWhoWalksIntoImaginedLavaSeesThroughIt(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "mind-doubter-test");
        UUID owner = UUID.randomUUID();
        LiveScene scene = lavaUnder(helper, owner, player);
        scene.belief().set(player.getId(), 0, 0.3F);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "lava burned a mind that doubted it");
            helper.assertTrue(scene.belief().shattered(player.getId(), 0), "a doubter walked through lava and still half-believes it");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_harm_3")
    public static void aFigmentThatStrikesABelieverHurtsAndConvinces(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(1, 2, 2), "mind-bitten-test");
        UUID owner = UUID.randomUUID();
        Lexicon lexicon = MindGameTests.knowing("creature:" + ZOMBIE);
        Reverie reverie = new Reverie();
        reverie.addFigment(new Offset(0, 0, 0), ZOMBIE, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie,
                BlockPos.containing(onFloor(helper, new BlockPos(3, 2, 2))), 0, lexicon);
        helper.assertTrue(scene != null, "the zombie was refused");
        scene.belief().set(player.getId(), 0, 0.6F);
        helper.runAtTickTime(2, () -> {
            FigmentEntity zombie = (FigmentEntity) helper.getLevel().getEntity(scene.figmentEntity(0));
            MindService.figmentStrikes(zombie, player);
            // A zombie bites for 3; believed at 0.6 that is 1.8.
            float lost = player.getMaxHealth() - player.getHealth();
            helper.assertTrue(lost > 1.7F && lost < 1.9F, "an imagined zombie bit for " + lost);
            float now = scene.belief().get(player.getId(), 0);
            helper.assertTrue(now > 0.65F, "belief " + now + " did not rise after the bite");
            MindService.endAll(owner);
            helper.succeed();
        });
    }
}
```

- [ ] **Step 2: Run the game tests to verify they fail**

Run: `.\gradlew runGameTestServer --console=plain`
Expected:
- `mind_harm_1` FAIL: "imagined lava burned nothing".
- `mind_harm_2` passes already, because the doubter branch already exists.
- `mind_harm_3` FAIL: "an imagined zombie bit for 0.0".

- [ ] **Step 3: Write `MindHarm.java`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Phantom harm: a lie that hurts is believed more. A believer who touches imagined lava or is bitten by
 * an imagined wolf takes magic damage times their belief and comes away surer
 * ({@link PhantomHarm#RAISE}) - where a doubter who does the same has walked through it.
 */
final class MindHarm {
    private MindHarm() {}

    /** A believer has just crossed into a harmful cluster. */
    static void touched(ServerLevel level, LiveScene scene, LivingEntity viewer, LiveScene.Element element, float belief) {
        hurt(level, scene, null, viewer, PhantomHarm.amount(PhantomHarm.of(element.blockIds()), belief));
        scene.belief().nudge(viewer.getId(), element.index(), PhantomHarm.RAISE);
    }

    /** Believers still inside a harmful cluster burn again every {@link PhantomHarm#INTERVAL} ticks. */
    static void smoulder(ServerLevel level, LiveScene scene, List<LivingEntity> viewers, long now) {
        if ((now - scene.bornAt()) % PhantomHarm.INTERVAL != 0 || scene.inside.isEmpty()) {
            return;
        }
        for (LiveScene.Element element : scene.elements()) {
            float base = element.kind() == LiveScene.Kind.CLUSTER && !scene.manifested(element.index())
                    ? PhantomHarm.of(element.blockIds()) : 0.0F;
            if (base <= 0.0F) {
                continue;
            }
            for (LivingEntity viewer : viewers) {
                float belief = scene.belief().get(viewer.getId(), element.index());
                if (belief >= Belief.CONVINCED && scene.inside.contains(LiveScene.key(viewer.getId(), element.index()))) {
                    hurt(level, scene, null, viewer, PhantomHarm.amount(base, belief));
                }
            }
        }
    }

    /** An imagined creature bites a believer: its kind's attack times their belief. */
    static void struck(ServerLevel level, LiveScene scene, FigmentEntity figment, LivingEntity target,
                       LiveScene.Element element, float belief) {
        float amount = PhantomHarm.amount(KindStats.attack(element.figment().creatureId()), belief);
        if (amount <= 0.0F) {
            return;
        }
        hurt(level, scene, figment, target, amount);
        scene.belief().nudge(target.getId(), element.index(), PhantomHarm.RAISE);
    }

    private static void hurt(ServerLevel level, LiveScene scene, Entity direct, LivingEntity victim, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(scene.owner());
        DamageSource source = owner == null ? level.damageSources().magic()
                : level.damageSources().indirectMagic(direct != null ? direct : owner, owner);
        MagicDamageService.hurt(victim, source, amount, MagicContent.UNVEIL.id());
    }
}
```

- [ ] **Step 4: Wire it into `MindService`**

In `tickScene`, call `MindHarm.smoulder(level, scene, viewers, now);` on the line before `touches(level, scene, viewers, now);`. Running it first means a believer who only just stepped in is not burned twice on the same tick.

In `touches`, replace the two lines `expose(scene, viewer, element, Contradiction.TOUCH, now);` and `witnessed(...)` with:

```java
                float belief = scene.belief().get(viewer.getId(), element.index());
                if (belief >= Belief.CONVINCED && PhantomHarm.of(element.blockIds()) > 0.0F) {
                    // A lie that hurts is believed more: the burn is not evidence against the lava.
                    MindHarm.touched(level, scene, viewer, element, belief);
                    continue;
                }
                expose(scene, viewer, element, Contradiction.TOUCH, now);
                witnessed(level, scene, viewers, viewer, element, now);
```

In `figmentStrikes`, replace the final `if (... < Belief.CONVINCED) { expose(...); }` with:

```java
        LiveScene.Element element = scene.elements().get(figment.element());
        float belief = scene.belief().get(target.getId(), figment.element());
        if (belief >= Belief.CONVINCED) {
            MindHarm.struck(level, scene, figment, target, element, belief);
            return;
        }
        expose(scene, target, element, Contradiction.HOLLOW_STRIKE, level.getGameTime());
```

- [ ] **Step 5: Run the game tests to verify they pass**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: `mind_harm_1` to `mind_harm_3` PASS. Everything else stays green, including `mind_figment_4`, the doubter's hollow strike.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/MindHarm.java src/main/java/com/efkrdnz/magical/magic/mind/PhantomHarmGameTests.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java
git commit -m "feat(mind): a lie that hurts is believed more

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Insist

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/Insist.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/InsistGameTests.java`
- Create: `src/test/java/com/efkrdnz/magical/magic/mind/InsistTest.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicContent.java` (register `INSIST` and add it to `AUTHORITY_SKILLS`)
- Modify: `src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java` (the Mind list)
- Modify: `src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java` (the handler)
- Modify: `src/main/java/com/efkrdnz/magical/client/GenericHoldInput.java` (holdable)
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindService.java` (split `payFor`, add `payTick`, extend the server tick)
- Modify: `src/main/resources/assets/magical/lang/en_us.json`
- Modify: `src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java`

**Interfaces:**
- Consumes:
  - `LiveScene.aim` and `Belief.nudge`;
  - package-private `MindService.viewers`, `perceives`, `liveBox` and `typeId`;
  - `HoldService.isHeldSkill(ServerPlayer, ResourceLocation)` (package `com.efkrdnz.magical.magic.cast`);
  - `MagicalAttachments.MAGIC_STATE` (package `com.efkrdnz.magical.registry`);
  - `PlayerMagicState.hasAuthority(ResourceLocation)` and `AuthorityContent.MIND`.
- Produces:
  - `MagicContent.INSIST`;
  - on `Insist`: constants `PIVOT` (0.3F), `STEP` (0.01F) and `BASE_MANA` (3), plus `public static float push(float belief)` and `public static boolean tick(ServerPlayer, PlayerMagicState)`;
  - package-private `MindService.payTick(ServerPlayer, PlayerMagicState, MagicSkillDefinition, int)`.
- Lang keys: `skill.magical.insist`, `skill.magical.insist.desc`, `message.magical.insist_nothing`.

- [ ] **Step 1: Write the failing tests**

In `MindAuthorityTest.java`, rename `theAuthorityGrantsDaydreamAndUnveilAndNothingElse` to `theAuthorityGrantsDaydreamUnveilAndInsistAndNothingElse` and make it:

```java
    @Test
    void theAuthorityGrantsDaydreamUnveilAndInsistAndNothingElse() {
        assertEquals(List.of(MagicContent.DAYDREAM.id(), MagicContent.UNVEIL.id(), MagicContent.INSIST.id()),
                AuthorityContent.get(AuthorityContent.MIND).skillIds());
        assertTrue(MagicContent.AUTHORITY_SKILLS.contains(MagicContent.DAYDREAM.id()));
        assertTrue(MagicContent.AUTHORITY_SKILLS.contains(MagicContent.UNVEIL.id()));
        assertTrue(MagicContent.AUTHORITY_SKILLS.contains(MagicContent.INSIST.id()));
        assertEquals(-6, MagicContent.DAYDREAM.tier());
        assertEquals(-6, MagicContent.UNVEIL.tier());
        assertEquals(-6, MagicContent.INSIST.tier());
        assertTrue(AuthorityContent.commandIds().contains("authority_of_mind"));
    }
```

In `everyStringTheStageDrawsIsInTheLanguageFile`, add these keys to its list: `"skill.magical.insist"`, `"skill.magical.insist.desc"`, `"message.magical.insist_nothing"`, `"message.magical.manifested"`, `"message.magical.unmanifested"`.

Add a pure test, `InsistTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InsistTest {
    @Test
    void insistingHelpsAMindHalfwayThereAndHardensOneThatDoubts() {
        assertEquals(0.01F, Insist.push(0.3F), 1.0E-6F);
        assertEquals(0.01F, Insist.push(0.9F), 1.0E-6F);
        assertEquals(-0.01F, Insist.push(0.29F), 1.0E-6F);
        assertEquals(-0.01F, Insist.push(0.0F), 1.0E-6F);
    }
}
```

`InsistGameTests.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class InsistGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private InsistGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_insist_1")
    public static void insistingPushesTheHalfConvincedUpAndTheDoubtersDown(GameTestHelper helper) {
        ServerPlayer wielder = GameTestPlayers.survival(helper, new BlockPos(2, 2, 0), "mind-insist-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 3)));
        LiveScene scene = MindGameTests.unveil(helper, wielder.getUUID(), MindGameTests.column(), anchor);
        LivingEntity leaning = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(0, 2, 2))));
        LivingEntity doubting = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(4, 2, 2))));
        scene.belief().set(leaning.getId(), 0, 0.4F);
        scene.belief().set(doubting.getId(), 0, 0.2F);
        helper.runAtTickTime(3, () -> {
            wielder.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(anchor));
            float up = scene.belief().get(leaning.getId(), 0);
            float down = scene.belief().get(doubting.getId(), 0);
            int mana = state.mana();
            helper.assertTrue(Insist.tick(wielder, state), "insisting on a column in plain view did nothing");
            helper.assertTrue(Math.abs(scene.belief().get(leaning.getId(), 0) - (up + 0.01F)) < 1.0E-4F, "the leaning husk was not pushed up");
            helper.assertTrue(Math.abs(scene.belief().get(doubting.getId(), 0) - (down - 0.01F)) < 1.0E-4F, "the doubting husk was not pushed down");
            helper.assertTrue(state.mana() < mana, "insisting was free");

            wielder.setXRot(-90.0F);
            int before = state.mana();
            helper.assertFalse(Insist.tick(wielder, state), "insisting at the sky did something");
            helper.assertTrue(state.mana() == before, "insisting at nothing was billed");
            MindService.endAll(wielder.getUUID());
            helper.succeed();
        });
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.*"`
Expected: FAIL. Compilation fails because `MagicContent.INSIST` and `Insist` do not exist.

- [ ] **Step 3: Register the skill**

In `MagicContent.java`, directly after `UNVEIL`:

```java
    public static final MagicSkillDefinition INSIST = register("insist", MagicSchool.ARCANE, MagicSkillType.BURST, -6, 0, 0.0F, 0.0F, 1.0F, 3, 0, 20, 0.0F, 0, 0xC9B4FF, MagicAttribute.ARCANE);
```

In `AUTHORITY_SKILLS`, change the last line to `DAYDREAM.id(), UNVEIL.id(), INSIST.id());`.

In `AuthorityContent.java`, set the list of `AUTHORITY_OF_MIND` to `List.of(MagicContent.DAYDREAM.id(), MagicContent.UNVEIL.id(), MagicContent.INSIST.id())`.

In `MagicCastContentKept.java`, after the UNVEIL registration:

```java
        // Insist is a hold: GenericHoldInput reports it and MindService's tick does the work, so the
        // press itself has nothing to do - and nothing to bill.
        SkillCastRegistry.register(MagicContent.INSIST, SkillCastRegistry.selfManaged(ctx -> {}));
```

In `GenericHoldInput.java`, add the import `com.efkrdnz.magical.magic.MagicContent` and change the `holdable` line:

```java
            // Insist has no visual profile to carry the flag (no Authority skill has one), so it is named.
            boolean holdable = skill != null && (VisualProfiles.of(skill).holdable() || MagicContent.INSIST.id().equals(skill));
```

In `en_us.json`, add these after the `skill.magical.unveil.desc` line:

```json
  "skill.magical.insist": "Insist",
  "skill.magical.insist.desc": "Hold on a piece of your scene. Everyone watching it who half-believes it believes it more; everyone who doubts it doubts it harder. 3 mana a tick.",
```

Add this next to the manifested messages:

```json
  "message.magical.insist_nothing": "Nothing of yours is there to insist on.",
```

- [ ] **Step 4: Split `payFor` and add `payTick` in `MindService`**

Replace `payFor` with:

```java
    private static boolean payFor(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill, int baseMana) {
        if (state.isSkillOnCooldown(skill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        if (!spend(player, state, skill, baseMana)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        state.setSkillCooldown(skill.id(), skill.resolve(state.tuningFor(skill.id())).cooldownTicks());
        return true;
    }

    /** A hold's bill for one tick: mana only, never a clock, and the refusal said at most once a second. */
    static boolean payTick(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill, int baseMana) {
        if (spend(player, state, skill, baseMana)) {
            return true;
        }
        if (player.level().getGameTime() % 20 == 0) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
        }
        return false;
    }

    /** Resolve floors mana at 4, so the base is scaled here rather than read off the stats. */
    private static boolean spend(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill, int baseMana) {
        MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
        return MagicSinService.spendManaForSkill(player, state, Math.max(1, Math.round(baseMana * stats.costScale())));
    }
```

In `onServerTick`, add this after the scene loop and before the scepticism prune. It needs the imports `com.efkrdnz.magical.magic.AuthorityContent`, `com.efkrdnz.magical.magic.cast.HoldService` and `com.efkrdnz.magical.registry.MagicalAttachments`.

```java
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (HoldService.isHeldSkill(player, MagicContent.INSIST.id())) {
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                if (state.hasAuthority(AuthorityContent.MIND)) {
                    Insist.tick(player, state);
                }
            }
        }
```

- [ ] **Step 5: Write `Insist.java`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Held on an element of the wielder's own scene: every viewer perceiving it at {@link #PIVOT} or above
 * is pushed up a step a tick, and every one below it down a step - protesting too much. It is how a
 * scene is pushed over into real, and it is useless on an audience that already doubts.
 */
public final class Insist {
    public static final float PIVOT = 0.3F;
    public static final float STEP = 0.01F;
    public static final int BASE_MANA = 3;
    private static final int SYNC_TICKS = 5;
    private static final int NOTICE_TICKS = 20;
    /** How far past the first real block the ray is carried, so a real cluster's own face is still met. */
    private static final double PAST_THE_WALL = 0.5;

    private Insist() {}

    public static float push(float belief) {
        return belief >= PIVOT ? STEP : -STEP;
    }

    /** One tick of a held Insist; true when it found an element and paid for it. */
    public static boolean tick(ServerPlayer player, PlayerMagicState state) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 far = eye.add(look.scale(MindService.UNVEIL_REACH));
        BlockHitResult wall = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 to = wall.getType() == HitResult.Type.MISS ? far : wall.getLocation().add(look.scale(PAST_THE_WALL));
        LiveScene aimed = null;
        SceneAim.Hit best = null;
        for (LiveScene scene : MindService.scenesOf(player.getUUID())) {
            if (!scene.dimension().equals(level.dimension())) {
                continue;
            }
            SceneAim.Hit hit = scene.aim(eye, to, index -> MindService.liveBox(level, scene, scene.elements().get(index)));
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = hit;
                aimed = scene;
            }
        }
        if (aimed == null) {
            if (now % NOTICE_TICKS == 0) {
                player.displayClientMessage(Component.translatable("message.magical.insist_nothing"), true);
            }
            return false;
        }
        if (!MindService.payTick(player, state, MagicContent.INSIST, BASE_MANA)) {
            return false;
        }
        LiveScene.Element element = aimed.elements().get(best.index());
        for (LivingEntity viewer : MindService.viewers(level, aimed)) {
            int id = viewer.getId();
            if (aimed.belief().shattered(id, element.index())
                    || !MindService.perceives(level, aimed, viewer, element, Susceptibility.blind(MindService.typeId(viewer)))) {
                continue;
            }
            aimed.belief().nudge(id, element.index(), push(aimed.belief().get(id, element.index())));
        }
        if (now % SYNC_TICKS == 0) {
            state.sync(player);
        }
        return true;
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `.\gradlew test`
Expected: PASS for the whole unit suite, including `AuthorityGrantTest`, `ContentLangKeysTest`, `MobCastProfileTest` and `AccentResolutionTest`.

Run: `.\gradlew runGameTestServer --console=plain`
Expected: `mind_insist_1` PASS, and every other game test green.

- [ ] **Step 7: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/Insist.java src/main/java/com/efkrdnz/magical/magic/mind/InsistGameTests.java src/main/java/com/efkrdnz/magical/magic/MagicContent.java src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java src/main/java/com/efkrdnz/magical/client/GenericHoldInput.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/resources/assets/magical/lang/en_us.json src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java src/test/java/com/efkrdnz/magical/magic/mind/InsistTest.java
git commit -m "feat(mind): Insist, a hold that pushes the half-convinced over and the doubters away

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Clients learn what is real and draw it hardening

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/network/BeliefSyncPayload.java`
- Modify: `src/test/java/com/efkrdnz/magical/network/MindPayloadsTest.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindSync.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/mind/ClientMind.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/mind/IllusionRenderer.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/mind/FigmentRenderer.java`

**Interfaces:**
- Consumes: `LiveScene.manifestedList()`, `FigmentEntity.isManifested()`, `FigmentEntity.hardening(float)` and `Consensus.HARDEN_TICKS`.
- Produces:
  - `BeliefSyncPayload(int scene, List<Entry> entries, List<Integer> manifested)`, with `MAX_MANIFESTED = 128`;
  - `ClientMind.manifested(int scene, int element)` and `ClientMind.hardening(int scene, int element, float partial)`;
  - `IllusionRenderer.drawLocalEdge(PoseStack, MultiBufferSource, AABB, float alpha)`.

- [ ] **Step 1: Write the failing test**

In `MindPayloadsTest.anEndAndABeliefRoundTrip`, replace the `BeliefSyncPayload` round trip with:

```java
        roundTrip(BeliefSyncPayload.STREAM_CODEC, new BeliefSyncPayload(7,
                List.of(new BeliefSyncPayload.Entry(42, 0, (byte) 64, false), new BeliefSyncPayload.Entry(42, 1, (byte) 0, true)),
                List.of(0, 3)));
```

Add this test:

```java
    @Test
    void aHostileManifestedListIsRefused() {
        List<Integer> manifested = new ArrayList<>();
        for (int i = 0; i <= BeliefSyncPayload.MAX_MANIFESTED; i++) {
            manifested.add(i);
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(Exception.class, () -> BeliefSyncPayload.STREAM_CODEC.encode(buffer, new BeliefSyncPayload(1, List.of(), manifested)));
        } finally {
            buffer.release();
        }
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.network.MindPayloadsTest"`
Expected: FAIL. Compilation fails because the three-argument constructor and `MAX_MANIFESTED` do not exist.

- [ ] **Step 3: Extend the payload and send it**

In `BeliefSyncPayload`:
- Change the record header to `public record BeliefSyncPayload(int scene, List<Entry> entries, List<Integer> manifested)`.
- Add `public static final int MAX_MANIFESTED = 128;`.
- Change the javadoc to end "... and which of its elements are real".
- Replace the codec with:

```java
    public static final StreamCodec<RegistryFriendlyByteBuf, BeliefSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BeliefSyncPayload::scene,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), BeliefSyncPayload::entries,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_MANIFESTED)), BeliefSyncPayload::manifested,
            BeliefSyncPayload::new);
```

In `MindSync.tick`, compute the list once before the audience loop:

```java
        List<Integer> real = scene.manifestedList();
        if (real.size() > BeliefSyncPayload.MAX_MANIFESTED) {
            real = real.subList(0, BeliefSyncPayload.MAX_MANIFESTED);
        }
```

The send becomes `new BeliefSyncPayload(scene.id(), entries, real)`.

- [ ] **Step 4: `ClientMind` tracks what is real and when it became so**

Add the import `com.efkrdnz.magical.magic.mind.Consensus`, and add `java.util.Set` and `java.util.HashMap` if they are missing. Add these fields:

```java
    private static final Map<Integer, Set<Integer>> MANIFESTED = new HashMap<>();
    /** When each (scene, element) became real, in client game time; drives the hardening rim. */
    private static final Map<Long, Long> HARDENED = new HashMap<>();
```

Replace `accept(BeliefSyncPayload)` with:

```java
    public static void accept(BeliefSyncPayload payload) {
        ROWS.put(payload.scene(), List.copyOf(payload.entries()));
        Set<Integer> was = MANIFESTED.getOrDefault(payload.scene(), Set.of());
        Set<Integer> now = Set.copyOf(payload.manifested());
        ClientLevel level = Minecraft.getInstance().level;
        for (int element : now) {
            if (!was.contains(element) && level != null) {
                HARDENED.put(key(payload.scene(), element), level.getGameTime());
            }
        }
        MANIFESTED.put(payload.scene(), now);
    }

    private static long key(int scene, int element) {
        return ((long) scene << 32) | (element & 0xFFFFFFFFL);
    }

    public static boolean manifested(int scene, int element) {
        return MANIFESTED.getOrDefault(scene, Set.of()).contains(element);
    }

    /** How much of the lilac rim is left on an element that has just become real, 1 to 0. */
    public static float hardening(int scene, int element, float partial) {
        Long at = HARDENED.get(key(scene, element));
        ClientLevel level = Minecraft.getInstance().level;
        if (at == null || level == null || !manifested(scene, element)) {
            return 0.0F;
        }
        float age = (level.getGameTime() - at) + partial;
        return Math.max(0.0F, Math.min(1.0F, 1.0F - age / Consensus.HARDEN_TICKS));
    }
```

Three more changes in `ClientMind`:
- In `accept(IllusionEndPayload)`, also call `MANIFESTED.remove(payload.scene());` and `HARDENED.keySet().removeIf(k -> (int) (k >> 32) == payload.scene());`.
- In `clear()`, also call `MANIFESTED.clear();` and `HARDENED.clear();`.
- In `visibility`, add this as the first check after the null guard: `if (manifested(scene, element)) { return 1.0F; }`.

- [ ] **Step 5: `IllusionRenderer` leaves real blocks alone and draws the rim**

Make `EDGE_ALPHA` package-visible: `static final float EDGE_ALPHA = 0.9F;`. Add the overload. The old three-argument body becomes a delegation:

```java
    public static void drawLocalEdge(PoseStack pose, MultiBufferSource buffers, AABB box) {
        drawLocalEdge(pose, buffers, box, EDGE_ALPHA);
    }

    public static void drawLocalEdge(PoseStack pose, MultiBufferSource buffers, AABB box, float alpha) {
        ShapeRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box,
                ((LILAC >> 16) & 0xFF) / 255.0F, ((LILAC >> 8) & 0xFF) / 255.0F, (LILAC & 0xFF) / 255.0F, alpha);
    }
```

In `render`, make the first loop skip a cell that is real in the world:

```java
            for (ClientMind.Cell cell : view.cells()) {
                if (ClientMind.manifested(view.id(), cell.element()) && minecraft.level.getBlockState(cell.pos()) == cell.state()) {
                    continue;
                }
                float alpha = view.mine() ? ClientMind.OWNER_ALPHA : ClientMind.visibility(view.id(), cell.element());
                drawBlock(minecraft, pose, buffers, cam, cell.state(), cell.pos(), alpha);
            }
```

Replace the edge loop with:

```java
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (ClientMind.View view : ClientMind.scenes()) {
            for (ClientMind.Cell cell : view.cells()) {
                if (ClientMind.manifested(view.id(), cell.element())) {
                    // Everyone sees a lie harden: the rim closes on the material and fades.
                    float rim = ClientMind.hardening(view.id(), cell.element(), partial);
                    if (rim > 0.0F) {
                        drawEdge(pose, lines, cam, new AABB(cell.pos()), LILAC, EDGE_ALPHA * rim);
                    }
                } else if (view.mine()) {
                    drawEdge(pose, lines, cam, new AABB(cell.pos()), LILAC, EDGE_ALPHA);
                }
            }
        }
```

- [ ] **Step 6: `FigmentRenderer` shows a real figment to everyone**

Add the import `net.minecraft.world.phys.AABB`, and replace the body of `render` with:

```java
        FigmentEntity figment = state.figment;
        if (figment == null) {
            return;
        }
        boolean real = figment.isManifested();
        if (!real && !ClientMind.sees(figment.sceneId(), figment.element())) {
            return;
        }
        AABB local = figment.getBoundingBox().move(figment.position().reverse());
        if (!real && ClientMind.mine(figment.sceneId())) {
            // The wielder is never fooled: their figments carry the same lilac edge as their blocks.
            IllusionRenderer.drawLocalEdge(pose, buffers, local);
        }
        float rim = real ? figment.hardening(state.partial) : 0.0F;
        if (rim > 0.0F) {
            IllusionRenderer.drawLocalEdge(pose, buffers, local, IllusionRenderer.EDGE_ALPHA * rim);
        }
        LivingEntity dummy = FigmentDummies.posed(figment);
        if (dummy != null) {
            Minecraft.getInstance().getEntityRenderDispatcher().render(dummy, 0.0, 0.0, 0.0, state.partial, pose, buffers, light);
        }
```

- [ ] **Step 7: Run the tests and build**

Run: `.\gradlew test --tests "com.efkrdnz.magical.network.MindPayloadsTest"`
Expected: PASS.

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL. A `BeliefSyncPayload` construction site that was missed shows up as a compile error.

- [ ] **Step 8: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/network/BeliefSyncPayload.java src/test/java/com/efkrdnz/magical/network/MindPayloadsTest.java src/main/java/com/efkrdnz/magical/magic/mind/MindSync.java src/main/java/com/efkrdnz/magical/client/mind/ClientMind.java src/main/java/com/efkrdnz/magical/client/mind/IllusionRenderer.java src/main/java/com/efkrdnz/magical/client/mind/FigmentRenderer.java
git commit -m "feat(mind): every client sees a real thing, and sees its rim harden

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: The Forecast says what makes it real

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/client/screen/mind/PlaybillLayout.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/screen/mind/PlaybillScreen.java` (`renderForecast`)
- Modify: `src/test/java/com/efkrdnz/magical/client/screen/mind/PlaybillLayoutTest.java`
- Modify: `src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java` (one lang key)
- Modify: `src/main/resources/assets/magical/lang/en_us.json`

**Interfaces:**
- Consumes: `Consensus.clusterWeight`, `figmentWeight`, `voter` and `needed`; `KindStats.maxHealth`; `PhantomHarm.unmanifestable`.
- Produces: `PlaybillLayout.FORECAST_FIXED` (144) and `PlaybillLayout.forecastTerms() -> int`.
- Lang key: `screen.magical.playbill.becomes_real`.

The forecast now draws, from the top:
1. Plausibility.
2. The terms: at most `forecastTerms()` of them, the largest movers first.
3. Senses.
4. A 4 px gap, then "Certain in" and 4 viewers.
5. A 4 px gap, then "Becomes real" and 2 rows.
6. A 4 px gap, then the cost.

Without the terms that is 11 lines of 12 px plus three gaps of 4 px, which is 144.

- [ ] **Step 1: Write the failing test**

Add to `PlaybillLayoutTest`:

```java
    @Test
    void theForecastKeepsTwoTermsAndEndsAboveTheButtons() {
        for (int[] screen : SCREENS) {
            PlaybillLayout layout = new PlaybillLayout(screen[0], screen[1]);
            assertTrue(layout.forecastTerms() >= 2, "under two plausibility terms at " + screen[0] + "x" + screen[1]);
            int bottom = layout.contentTop() + PlaybillLayout.FORECAST_FIXED + layout.forecastTerms() * PlaybillLayout.LINE;
            assertTrue(bottom <= layout.save().y(), "the forecast runs to " + bottom + ", onto Save at " + layout.save().y()
                    + " at " + screen[0] + "x" + screen[1]);
        }
    }
```

In `MindAuthorityTest.everyStringTheStageDrawsIsInTheLanguageFile`, add `"screen.magical.playbill.becomes_real"`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.screen.mind.PlaybillLayoutTest" --tests "com.efkrdnz.magical.magic.mind.MindAuthorityTest"`
Expected: FAIL. `forecastTerms` does not exist (a compile error), and the lang key is missing.

- [ ] **Step 3: Add the budget to `PlaybillLayout`**

```java
    /**
     * Everything in the forecast but its plausibility terms: eleven lines and three 4 px gaps. The
     * terms get what is left above the buttons, largest movers first.
     */
    public static final int FORECAST_FIXED = 11 * LINE + 3 * 4;

    public int forecastTerms() {
        return Math.max(0, (floor() - contentTop() - FORECAST_FIXED) / LINE);
    }
```

In `en_us.json`, add the lang key next to `screen.magical.playbill.cost`:

```json
  "screen.magical.playbill.becomes_real": "Becomes real",
```

- [ ] **Step 4: Draw it in `PlaybillScreen.renderForecast`**

Add the imports `com.efkrdnz.magical.magic.mind.Consensus`, `com.efkrdnz.magical.magic.mind.KindStats`, `com.efkrdnz.magical.magic.mind.PhantomHarm` and `java.util.Comparator`.

Replace the terms loop with:

```java
        List<Plausibility.Term> terms = new ArrayList<>(reading.terms());
        terms.sort(Comparator.comparingDouble((Plausibility.Term term) -> -Math.abs(term.value())));
        for (Plausibility.Term term : terms.subList(0, Math.min(terms.size(), layout.forecastTerms()))) {
            y = line(g, Component.translatable("mind.magical.term." + term.key()),
                    String.format(Locale.ROOT, "%+.2f", term.value()), x, y, width, term.value() >= 0 ? GAIN : LOSS);
        }
```

Insert this between the `y += 4;` that follows the Certain-in loop and the final cost `line(...)`:

```java
        g.drawString(font, Component.translatable("screen.magical.playbill.becomes_real"), x, y, MUTED, true);
        y += PlaybillLayout.LINE;
        float weight = selectedWeight();
        for (String voter : new String[] {"minecraft:zombie", "minecraft:player"}) {
            Component who = EntityType.byString(voter).map(EntityType::getDescription).orElse(Component.literal(voter));
            int needed = weight < 0.0F ? -1 : Consensus.needed(weight, Consensus.voter(voter));
            String value = needed < 0 ? Component.translatable("screen.magical.playbill.never").getString() : String.valueOf(needed);
            y = line(g, who, value, x, y, width, GAIN);
        }
        y += 4;
```

Add this helper next to `selectedSenses()`:

```java
    /** What the selected element weighs, or -1 when nothing in it can ever be placed for real. */
    private float selectedWeight() {
        if (selected < clusterCount()) {
            List<ImaginedBlock> cluster = working.clusters().get(selected);
            boolean placeable = cluster.stream().anyMatch(block -> !PhantomHarm.unmanifestable(block.blockId()));
            return placeable ? Consensus.clusterWeight(cluster.size()) : -1.0F;
        }
        return Consensus.figmentWeight(KindStats.maxHealth(working.figments().get(selected - clusterCount()).creatureId()));
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.screen.mind.PlaybillLayoutTest" --tests "com.efkrdnz.magical.magic.mind.MindAuthorityTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/client/screen/mind/PlaybillLayout.java src/main/java/com/efkrdnz/magical/client/screen/mind/PlaybillScreen.java src/test/java/com/efkrdnz/magical/client/screen/mind/PlaybillLayoutTest.java src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java src/main/resources/assets/magical/lang/en_us.json
git commit -m "feat(mind): the Forecast says how many sure minds make it real

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Commands, the guide, the full suite, captures, delivery

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindCommands.java`
- Modify: `CLAUDE.md`, **through the index only**. Its working tree holds a peer session's hunks, and those must never be committed.

**Interfaces:**
- Consumes: package-private `LiveScene.consensus(int)`, `weight(int)` and `manifested(int)`; `MindService.viewers`, `Belief.set` and `Insist.tick`.
- Produces: `/magical mind consensus`, `/magical mind convince <belief>` and `/magical mind insist`.

- [ ] **Step 1: Add the three subcommands**

In `MindCommands.build()`, add these after the `"end"` `.then(...)` and before the final `;`. They need the imports `com.mojang.brigadier.arguments.FloatArgumentType` and `net.minecraft.world.entity.LivingEntity`.

```java
            .then(Commands.literal("consensus").executes(c -> run(c, MindCommands::consensus)))
            .then(Commands.literal("convince")
                    .then(Commands.argument("belief", FloatArgumentType.floatArg(0.0F, 1.0F))
                            .executes(c -> run(c, player -> convince(player, FloatArgumentType.getFloat(c, "belief"))))))
            .then(Commands.literal("insist").executes(c -> run(c, player -> Insist.tick(player, state(player)) ? 1 : 0)))
```

Add these methods:

```java
    /** Every element of the caller's scenes: consensus against weight, and whether it is real. */
    private static int consensus(ServerPlayer player) {
        List<LiveScene> scenes = MindService.scenesOf(player.getUUID());
        for (LiveScene scene : scenes) {
            for (LiveScene.Element element : scene.elements()) {
                int index = element.index();
                String state = scene.slain(index) ? "slain" : scene.manifested(index) ? "REAL" : "illusion";
                player.sendSystemMessage(Component.literal(String.format(java.util.Locale.ROOT,
                        "scene %d element %d (%s): %.2f / %.2f %s", scene.id(), index, element.kind(),
                        scene.consensus(index), scene.weight(index), state)));
            }
        }
        return scenes.size();
    }

    /** Sets every current viewer's belief in every element of the caller's scenes; for captures. */
    private static int convince(ServerPlayer player, float belief) {
        int set = 0;
        for (LiveScene scene : MindService.scenesOf(player.getUUID())) {
            for (LivingEntity viewer : MindService.viewers(player.serverLevel(), scene)) {
                for (LiveScene.Element element : scene.elements()) {
                    scene.belief().set(viewer.getId(), element.index(), belief);
                    set++;
                }
            }
        }
        return set;
    }
```

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL.

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/MindCommands.java
git commit -m "feat(mind): consensus, convince and insist commands

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 2: The full suite in a clean worktree**

A green working-tree build says nothing about the subset that was committed, so verify HEAD in its own worktree:

```bash
git worktree add ../magical-verify HEAD
cd ../magical-verify
./gradlew test build
./gradlew runGameTestServer --console=plain
cd ../magical-port
git worktree remove ../magical-verify --force
```

Expected:
- `test` and `build`: BUILD SUCCESSFUL.
- Game tests: all pass. That is the stage 1 count (95) plus ten new ones: `mind_manifest_1` to `mind_manifest_5`, `mind_harm_1` to `mind_harm_3`, and `mind_insist_1`.

- [ ] **Step 3: Captures**

Set `pauseOnLostFocus:false` in `run/options.txt` first. The capture unveils the wall preset in front of eight husks at midnight. The wall weighs 7.5; eight husks certain of it count 8, so it is placed for real. Converting belief to 0.2 afterwards gives it back.

```powershell
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoExit -PautoScreenshot=246,251,262,300,332 '-PautoCommands=gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doDaylightCycle false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical authority set authority_of_mind;magical mind lexicon all;magical mind preset 1 wall;time set midnight;tp @s ~ ~ ~ 0 0;200:magical mind unveil;205:summon husk ~-2 ~ ~6 {NoAI:1b};205:summon husk ~-1 ~ ~6 {NoAI:1b};205:summon husk ~ ~ ~6 {NoAI:1b};205:summon husk ~1 ~ ~6 {NoAI:1b};205:summon husk ~2 ~ ~6 {NoAI:1b};205:summon husk ~-2 ~ ~7 {NoAI:1b};205:summon husk ~ ~ ~7 {NoAI:1b};205:summon husk ~2 ~ ~7 {NoAI:1b};241:magical mind convince 1.0;290:magical mind convince 0.2'
```

What each frame should show:

| Tick | Expected |
|---|---|
| 246 | The wall hardening, rim at full. |
| 251 | The rim mid-fade. |
| 262 | The wall real. |
| 300 | The wall given back. |
| 332 | A second look at the given-back wall, in case the tick alignment lands late. |

Look at every frame. If the wall is off-frame, adjust the `tp` pitch and run again. Send the best two frames to the user with `SendUserFile`.

- [ ] **Step 4: The guide, through the index**

Write this script to the scratchpad as `mind2_claude_md.py` and run it with `python` from `E:\magical-port`. It patches HEAD's CLAUDE.md into the index and makes the same text change in the working tree, which keeps the peer session's hunks where they are:

```python
import subprocess, pathlib
OLD = "Stage 2 (manifestation, Insist, phantom harm) and stage 3 (the Dream) are in the design and not in the code."
NEW = "Stage 2 makes a lie real by consensus (below); stage 3 (the Dream) is in the design and not in the code."
ANCHOR = "Commands under `/magical mind`:"
PARA = (
    "**Manifestation** (`Manifestation`, every 5 ticks from `tickScene`) is where a lie becomes real. An element weighs "
    "`Consensus.clusterWeight` (0.5 a block) or `figmentWeight` (a quarter of its kind's health, read off vanilla by "
    "`KindStats`); its consensus is `Belief.consensus` - the sum over convinced viewers of belief times `Consensus.voter` "
    "(player 3, wither/dragon/elder guardian 5, anything else 1). It manifests at its weight and holds down to half "
    "(`Consensus.real`, pinned by `ConsensusTest`). A cluster is placed through `ConjuredTerrainService` into cells that "
    "`canBeReplaced`, hold no block entity and no body, fired as block-break particles so the rim cracks into the "
    "material, and given back with `restoreUnlessBuiltOver`; fire and fluids never manifest (`PhantomHarm.unmanifestable`: "
    "a real lava pool outlives its revert), so imagined lava only ever burns the mind. A figment is the same "
    "`FigmentEntity` with its synced `MANIFESTED` flag set: every ghost override yields to vanilla, `isFigment` answers "
    "false so every spell and sweep can find it, its max health follows the agreement (`hold`), a blow on it is a blow "
    "and not evidence, and a slain one is gone for good (`LiveScene.slain`). A scene with anything real does not expire "
    "(`LiveScene.over`) and ending one gives everything back (`Manifestation.revertAll`, also on server stop). "
    "`ManifestGuard` cancels a real block's drops and keeps it out of explosions, or an imagined diamond wall is real "
    "diamonds; pistons and endermen can still move one, a known gap. Clients learn what is real from "
    "`BeliefSyncPayload.manifested` and draw a lilac rim that fades over `Consensus.HARDEN_TICKS`; a real cell is not "
    "drawn as illusion. **Phantom harm** (`MindHarm`, table in `PhantomHarm`): a believer crossing into a harmful cluster "
    "or struck by a figment takes magic damage times belief and is nudged up 0.10 (`Belief.nudge`, which never shatters); "
    "a doubter doing the same has seen through it. **Insist** is a hold on the generic path (`GenericHoldInput` names it, "
    "`MindService.onServerTick` runs `Insist.tick` while `HoldService.isHeldSkill`): 3 mana a tick, the element under the "
    "crosshair (`LiveScene.aim` over `SceneAim`, since imagined cells are not in the world to clip), +0.01 to every "
    "perceiving viewer at 0.3 or above and -0.01 below. The Playbill's forecast adds **Becomes real** - how many sure "
    "zombies or players make the selected element real - and trims its terms to `PlaybillLayout.forecastTerms()`. "
    "Commands: `mind consensus` (weights and agreement), `mind convince <belief>` (sets every current viewer, for "
    "captures), `mind insist` (one tick). Plan: `docs/superpowers/plans/2026-09-29-authority-of-mind-manifestation.md`.\n\n"
)

def patch(text):
    assert OLD in text and ANCHOR in text, "anchor text moved"
    text = text.replace(OLD, NEW, 1)
    return text.replace(ANCHOR, PARA + ANCHOR, 1)

head = subprocess.run(["git", "show", "HEAD:CLAUDE.md"], capture_output=True, check=True).stdout.decode("utf-8")
blob = subprocess.run(["git", "hash-object", "-w", "--stdin"], input=patch(head).encode("utf-8"),
                      capture_output=True, check=True).stdout.decode().strip()
subprocess.run(["git", "update-index", "--cacheinfo", f"100644,{blob},CLAUDE.md"], check=True)
work = pathlib.Path("CLAUDE.md")
work.write_bytes(patch(work.read_bytes().decode("utf-8")).encode("utf-8"))
print("index and working tree patched")
```

Then:

```bash
git diff --cached --stat
git commit -m "docs(mind): the guide covers manifestation, phantom harm and Insist

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected: `--stat` shows `CLAUDE.md` alone, carrying only this change. Before committing, read `git diff --cached` and confirm none of the peer's hunks are in it.

- [ ] **Step 5: Deliver**

```bash
git push origin main
git -C "E:/minecraft mods/magical" switch --detach main
```

Expected: the push succeeds and the play checkout is at the new HEAD.
