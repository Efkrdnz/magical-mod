# Authority of Mind, Stage 1 (Illusion) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the first stage of the Authority of Mind. The wielder learns impressions by gazing, authors a reverie in Daydream and the Playbill, and unveils it as an illusion that each viewer believes by its own measure. Belief rises with plausibility, falls on contradiction, and shatters below 0.1. Mobs path around, target and fear what they believe.

**Architecture:** The model and the arithmetic are pure and live in `magic/mind/` (`Lexicon`, `Reverie`, `Plausibility`, `Belief`, `Susceptibility`, `UnveilCost`, `GazeTracker`, `Brush`). They have no level in them and are pinned on exact values, the same split as `Pile`/`PileService` and `Weave`/`LevelCausalWorld`. `MindService` is the only server class that knows about a level: it holds the live scenes per wielder (never saved), ticks belief on `ServerTickEvent.Post`, and sends each client the rows that concern it.

Imagined blocks are never placed:
- the client draws them;
- one mixin on `WalkNodeEvaluator` makes a mob's pathing respect what that mob believes.

A figment is a real, tracked, non-saving `PathfinderMob` that the server refuses to hurt. Each client draws it or skips it by its own belief.

**Tech Stack:** NeoForge 21.4.157, Minecraft 1.21.4 (Mojmap), Java 21, JUnit 5 (`neoForge.unitTest`, so MC classes are usable in unit tests), NeoForge GameTest, Mixin 0.8 (shipped by NeoForge, no build change).

## Global Constraints

Registration:
- Authority id `magical:authority_of_mind`, colour `0xBDA4FF`.
- Stage 1 skills are `daydream` and `unveil`. Both are tier `-6`, `MagicSchool.ARCANE` and `MagicAttribute.ARCANE`, and are appended to `MagicContent.AUTHORITY_SKILLS`.

Lexicon and scenes:

| Constant | Value |
|---|---|
| Gaze a block | 40 ticks, standing still |
| Gaze a creature | 60 ticks, standing still |
| Fidelity 1 / 2 / 3 | at 1 / 5 / 20 gazes |
| Budget | `min(128, 16 + 2 * impressions)` elements |
| Reverie slots | 3 |
| Offset reach | ±24 on every axis |
| Live scenes per wielder | at most 2 |
| Scene life | 1200 ticks |

Players are never impressions.

Plausibility:
- Starts at 0.5 and is clamped to `0.05..1`.
- Terms:

  | Term | Value |
  |---|---|
  | unsupported | −0.40 × fraction |
  | context match within 8 | +0.30 × fraction |
  | alien | −0.20 × fraction |
  | fidelity | +0.10 × (mean fidelity − 1) |
  | figment out of habitat | −0.30 |
  | script unlike its kind | −0.20 |
  | script like its kind | +0.10 |
  | size | −0.10 per doubling above 32 elements |

Senses multiply the gain: Sound ×1.25, Shadow ×1.15, Scent ×1.10.

Susceptibility:

| Viewer | Value |
|---|---|
| zombie, husk, drowned | 1.3 |
| villager | 1.2 |
| creeper | 1.1 |
| skeleton, player | 1.0 |
| spider | 0.9 |
| witch | 0.5 |
| enderman | 0.4 |
| bosses | 0.2 |
| the wielder | 0 |

The Warden is blind and believes only an element that carries Sound.

Belief:
- Gain per tick: `b += 0.02 * p * senses * susc * novelty * (1 - b)`.
- Decay while unseen: −0.002 per tick.
- A viewer is convinced at `b >= 0.5`.
- An element shatters below 0.1, and only through a contradiction.
- Scepticism after a shatter: novelty `0.5^n` for 6000 ticks.
- Contradictions:

  | Event | Belief |
  |---|---|
  | touch, strike or pass-through | −0.60 |
  | a projectile passes through | −0.35 |
  | witnessing another's contradiction | −0.20 |
  | a hollow strike (below 0.5) | −0.25 |

Costs and mob behaviour:
- Unveil costs `10 + 1 per block + 5 per figment + 10 per sense layer` mana, scaled by `costScale`, with a 200-tick cooldown.
- Daydream is a free toggle.
- Pathing counts an imagined block for a mob that believes it at `>= 0.3`.

Out of scope for this plan:
- Phantom harm, Insist, manifestation and "becomes real" (stage 2).
- Lull and the Dream (stage 3).
- The Patrol and Mimic scripts.

Code rules:
- Game-bus handlers use `@EventBusSubscriber(modid = MagicalMod.MODID)`.
- No `GuiGraphics.fill` under `client/hud`.
- Every in-game surface is frameless (a scrim is allowed, not a panel).
- The Blood school is not touched, and Chaos is not referenced.

Commit rules:
- Stage with `git add -- <paths>` and run `git commit` with no pathspec.
- Never stage the peer session's hunks in `CLAUDE.md`, `SKILL_CREATION_NOTES.md`, `SpaceManipulationOverlay.java` or `HudDebug.java`, or the untracked `SpaceManipulationLayout(.java|Test.java)`.
- End each message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

Game tests:
- Run with `.\gradlew runGameTestServer --console=plain`, or the assertion text is erased.
- Keep every entity inside the 5x5x5 `unwaking_empty` interior.
- A fake player gets no player tick.

Paths below are relative to `src/main/java/com/efkrdnz/magical/` (production) and `src/test/java/com/efkrdnz/magical/` (tests) unless written in full.

---

## File structure

Pure core in `magic/mind/`:

| File | Role |
|---|---|
| `Impression` | a block or creature key |
| `Lexicon` | gazes per impression → fidelity, budget; NBT |
| `Offset` | a cell relative to the anchor; rotation |
| `Sense` | Sound, Shadow, Scent |
| `Stance` | a figment's standing behaviour |
| `Reaction` | a figment's response to a viewer |
| `Script` | a figment's stance and reaction |
| `ImaginedBlock` | one authored block |
| `Figment` | one authored creature |
| `Reverie` | a scene: blocks, figments, clusters, refusals |
| `ReverieNbt` | NBT for `Reverie` |
| `MindState` | the lexicon, three reveries and the active slot; NBT |
| `MindWorld` | what plausibility needs from a world |
| `CreatureTraits` | habitat and the scripts that fit a kind |
| `Plausibility` | the terms and the reading |
| `Belief` | the viewer × element ledger |
| `Contradiction` | the contradiction kinds and their penalties |
| `Susceptibility` | the viewer table |
| `Scepticism` | novelty after shatters |
| `UnveilCost` | the mana an unveil costs |
| `GazeTracker` | what the wielder is gazing at and for how long |
| `Brush` | Daydream's cell shapes |
| `DraftRay` | where Daydream's cursor lands |
| `MindPresets` | three worked reveries |

Server side in `magic/mind/`:
- `MindGazeService`: counts still looks into gazes.
- `LiveScene`: one unveiled reverie in the world.
- `LevelMindWorld`: `MindWorld` over any `Level`, shared with the client forecast.
- `MindService`: scenes, the belief tick, Unveil, billing, saving drafts.
- `MindSync`: who is sent which scene and which rows.
- `MindPathing`: the query the mixin asks.
- `MindMobEvents`: target and avoid goals added to mobs.
- `FigmentHunts`: which kinds hunt or fear which.
- `MindCommands`: `/magical mind`.

Other production files:
- `mixin/WalkNodeEvaluatorMixin`.
- `entity/mind/FigmentEntity`, `entity/mind/FigmentReactionGoal`.
- Network payloads: `network/IllusionScenePayload`, `IllusionEndPayload`, `BeliefSyncPayload`, `SaveReveriePayload`, `OpenPlaybillPayload`.

Client side in `client/mind/`:
- `ClientMind`: the scenes and beliefs this client has been sent.
- `GazeEyeOverlay`.
- `IllusionRenderer`, `FigmentRenderer`, `FigmentDummies`.
- `BeliefSight` (pure), `BeliefSightRenderer`.
- `DaydreamMode`, `DaydreamInput`, `DraftRenderer`.
- `ImpressionReelLayout`, `ImpressionReelOverlay`.

The Playbill is in `client/screen/mind/`: `PlaybillScreen`, `PlaybillLayout`.

Game tests in `magic/mind/`: `MindGameTests`, `MindPathGameTests`, `FigmentGameTests`, `UnveilGameTests`.

Resources:
- `src/main/resources/magical.mixins.json`.
- `src/main/templates/META-INF/neoforge.mods.toml`: uncomment `[[mixins]]`.
- `src/main/resources/assets/magical/lang/en_us.json`.

Modified:
- `PlayerMagicState`, `AuthorityContent`, `MagicContent`, `MagicCastContentKept`.
- `MagicalEntities`, `MagicalEntityEvents`, `MagicalNetwork`, `ClientPayloadHandlers`, `MagicalClientEvents`.
- `HudLayers`, `MagicalCommands`.
- `AnchorMarkRenderer` (`band` becomes public).
- `CLAUDE.md` (from the index only).

---

### Task 1: Impression and Lexicon

**Files:**
- Create: `magic/mind/Impression.java`, `magic/mind/Lexicon.java`
- Test: `magic/mind/LexiconTest.java`

**Interfaces:**
- Produces:
  - `Impression.block(String id)`, `Impression.creature(String id)`, `Impression.parse(String key)`: returns null for garbage, and for `creature:minecraft:player`.
  - `Impression.key()`: returns `"block:<id>"` or `"creature:<id>"`.
  - `Impression.kind()`: returns `Impression.Kind.BLOCK` or `CREATURE`.
  - `Impression.id()`.
  - `Lexicon`: `gaze(String key)`, `learn(String key, int gazes)`, `knows(String)`, `gazes(String)`, `fidelity(String)` (0..3), `size()`, `budget()`, `keys()` (`SortedSet<String>`), `copyFrom(Lexicon)`, `clear()`, `CompoundTag save()`, `void load(CompoundTag)`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LexiconTest {
    @Test
    void aKeyNamesItsKindAndItsId() {
        assertEquals("block:minecraft:grass_block", Impression.block("minecraft:grass_block").key());
        Impression back = Impression.parse("creature:minecraft:villager");
        assertEquals(Impression.Kind.CREATURE, back.kind());
        assertEquals("minecraft:villager", back.id());
        assertNull(Impression.parse("nonsense"));
        assertNull(Impression.parse("creature:minecraft:player"), "a player is never an impression");
    }

    @Test
    void fidelityClimbsAtOneFiveAndTwentyGazes() {
        Lexicon lexicon = new Lexicon();
        String key = "block:minecraft:stone";
        assertEquals(0, lexicon.fidelity(key));
        lexicon.gaze(key);
        assertEquals(1, lexicon.fidelity(key));
        lexicon.learn(key, 4);
        assertEquals(1, lexicon.fidelity(key), "learn never lowers and never adds");
        lexicon.learn(key, 5);
        assertEquals(2, lexicon.fidelity(key));
        lexicon.learn(key, 20);
        assertEquals(3, lexicon.fidelity(key));
        lexicon.learn(key, 2);
        assertEquals(20, lexicon.gazes(key));
    }

    @Test
    void theBudgetIsSixteenPlusTwoPerImpressionCappedAt128() {
        Lexicon lexicon = new Lexicon();
        assertEquals(16, lexicon.budget());
        for (int i = 0; i < 10; i++) {
            lexicon.gaze("block:minecraft:b" + i);
        }
        assertEquals(36, lexicon.budget());
        for (int i = 10; i < 100; i++) {
            lexicon.gaze("block:minecraft:b" + i);
        }
        assertEquals(128, lexicon.budget());
    }

    @Test
    void itSurvivesASaveAndACopy() {
        Lexicon lexicon = new Lexicon();
        lexicon.learn("creature:minecraft:cat", 7);
        CompoundTag tag = lexicon.save();
        Lexicon loaded = new Lexicon();
        loaded.load(tag);
        assertEquals(7, loaded.gazes("creature:minecraft:cat"));
        Lexicon copy = new Lexicon();
        copy.copyFrom(loaded);
        loaded.clear();
        assertEquals(1, copy.size());
        assertEquals(0, loaded.size());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.LexiconTest"`
Expected: compilation FAIL, `Impression` and `Lexicon` do not exist.

- [ ] **Step 3: Write `Impression`**

```java
package com.efkrdnz.magical.magic.mind;

/** One thing the wielder has looked at long enough to imagine: a block or a creature, by id. */
public record Impression(Kind kind, String id) {
    public enum Kind { BLOCK, CREATURE }

    private static final String BLOCK_PREFIX = "block:";
    private static final String CREATURE_PREFIX = "creature:";
    private static final String PLAYER = "minecraft:player";

    public static Impression block(String id) {
        return new Impression(Kind.BLOCK, id);
    }

    public static Impression creature(String id) {
        return new Impression(Kind.CREATURE, id);
    }

    /** The key back into an impression, or null for anything that is not one. */
    public static Impression parse(String key) {
        if (key == null) {
            return null;
        }
        if (key.startsWith(BLOCK_PREFIX) && key.length() > BLOCK_PREFIX.length()) {
            return block(key.substring(BLOCK_PREFIX.length()));
        }
        if (key.startsWith(CREATURE_PREFIX) && key.length() > CREATURE_PREFIX.length()) {
            String id = key.substring(CREATURE_PREFIX.length());
            return PLAYER.equals(id) ? null : creature(id);
        }
        return null;
    }

    public String key() {
        return (kind == Kind.BLOCK ? BLOCK_PREFIX : CREATURE_PREFIX) + id;
    }
}
```

- [ ] **Step 4: Write `Lexicon`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Everything the wielder can imagine, and how well: a count of gazes per impression. Fidelity is
 * read off the count and the budget off the number of impressions, so studying the world is the
 * only way a reverie gets bigger or better.
 */
public final class Lexicon {
    public static final int FIDELITY_2_AT = 5;
    public static final int FIDELITY_3_AT = 20;
    public static final int BASE_BUDGET = 16;
    public static final int BUDGET_PER_IMPRESSION = 2;
    public static final int MAX_BUDGET = 128;

    private final TreeMap<String, Integer> gazes = new TreeMap<>();

    public void gaze(String key) {
        gazes.merge(key, 1, Integer::sum);
    }

    /** Raises the count to at least {@code count}; never lowers it. */
    public void learn(String key, int count) {
        if (count > 0) {
            gazes.merge(key, count, Math::max);
        }
    }

    public boolean knows(String key) {
        return gazes(key) >= 1;
    }

    public int gazes(String key) {
        return gazes.getOrDefault(key, 0);
    }

    public int fidelity(String key) {
        int count = gazes(key);
        if (count >= FIDELITY_3_AT) {
            return 3;
        }
        if (count >= FIDELITY_2_AT) {
            return 2;
        }
        return count >= 1 ? 1 : 0;
    }

    public int size() {
        return gazes.size();
    }

    public int budget() {
        return Math.min(MAX_BUDGET, BASE_BUDGET + BUDGET_PER_IMPRESSION * size());
    }

    public SortedSet<String> keys() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(gazes.keySet()));
    }

    public void copyFrom(Lexicon other) {
        gazes.clear();
        gazes.putAll(other.gazes);
    }

    public void clear() {
        gazes.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        gazes.forEach(tag::putInt);
        return tag;
    }

    public void load(CompoundTag tag) {
        gazes.clear();
        for (String key : tag.getAllKeys()) {
            if (Impression.parse(key) != null) {
                learn(key, tag.getInt(key));
            }
        }
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.LexiconTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/Impression.java src/main/java/com/efkrdnz/magical/magic/mind/Lexicon.java src/test/java/com/efkrdnz/magical/magic/mind/LexiconTest.java
git commit -m "feat: mind lexicon of impressions"
```

---

### Task 2: The reverie model

**Files:**
- Create in `magic/mind/`: `Offset.java`, `Sense.java`, `Stance.java`, `Reaction.java`, `Script.java`, `ImaginedBlock.java`, `Figment.java`, `Reverie.java`, `ReverieNbt.java`
- Test: `magic/mind/ReverieTest.java`

**Interfaces:**
- Consumes: `Lexicon.knows`, `Lexicon.budget` (Task 1).
- Produces:
  - `Offset(int dx, int dy, int dz)`, with `rotate(int clockwiseTurns)`, `within(int reach)`, `neighbours()` (six).
  - `Sense`: `SOUND`, `SHADOW`, `SCENT`, with `multiplier(Set<Sense>)`, `mask(Set<Sense>)` and `fromMask(int)`.
  - `Stance`: `IDLE`, `WANDER`, `GUARD`, `FOLLOW`.
  - `Reaction`: `IGNORE`, `APPROACH`, `FLEE`, `STARE`, `CHASE`.
  - `Script(Stance, Reaction)`, with `Script.DEFAULT`.
  - `ImaginedBlock(Offset at, String blockId, Set<Sense> senses)`.
  - `Figment(Offset at, String creatureId, Script script, Set<Sense> senses)`.
  - `Reverie`, with `Reverie.REACH = 24` and `Reverie.Refusal { NONE, FULL, UNKNOWN, OCCUPIED, TOO_FAR }`. Methods:
    - `name()` and `setName(String)`; `facing()` and `setFacing(int)`;
    - `addBlock(Offset, String, Lexicon)` and `addFigment(Offset, String, Lexicon)`, both returning a `Refusal`;
    - `remove(Offset)`, returning a boolean;
    - `blocks()` and `figments()`;
    - `clusters()`, returning `List<List<ImaginedBlock>>` in a stable order;
    - `setClusterSenses(Offset member, Set<Sense>)`, `setFigmentSenses(int, Set<Sense>)`, `setScript(int, Script)`;
    - `size()`, `isEmpty()`, `senseLayers()`;
    - `copy()`, `copyFrom(Reverie)`, `clear()`;
    - package-private `put(ImaginedBlock)` and `put(Figment)`.
  - `ReverieNbt.save(Reverie)`, returning a `CompoundTag`, and `ReverieNbt.load(CompoundTag)`, returning a `Reverie`.

The facing is `Direction.get2DDataValue()`: 0 south, 1 west, 2 north, 3 east. Each step is one clockwise quarter turn seen from above, which is exactly what `Offset.rotate(1)` does: `(x, z) -> (-z, x)` turns east `(1,0)` into south `(0,1)`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReverieTest {
    private static final String GRASS = "minecraft:grass_block";
    private static final String VILLAGER = "minecraft:villager";

    private static Lexicon knowing(String... keys) {
        Lexicon lexicon = new Lexicon();
        for (String key : keys) {
            lexicon.gaze(key);
        }
        return lexicon;
    }

    @Test
    void aQuarterTurnIsClockwiseFromAbove() {
        assertEquals(new Offset(0, 2, 1), new Offset(1, 2, 0).rotate(1), "east turns to south");
        assertEquals(new Offset(-1, 0, 0), new Offset(1, 0, 0).rotate(2));
        assertEquals(new Offset(1, 0, 0), new Offset(1, 0, 0).rotate(-4));
        assertEquals(new Offset(0, 0, -1), new Offset(1, 0, 0).rotate(3), "east turns to north");
    }

    @Test
    void aReverieRefusesWhatTheWielderHasNeverSeenWhatIsTakenAndWhatIsTooFar() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS, "creature:" + VILLAGER);
        assertEquals(Reverie.Refusal.NONE, reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon));
        assertEquals(Reverie.Refusal.OCCUPIED, reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon));
        assertEquals(Reverie.Refusal.OCCUPIED, reverie.addFigment(new Offset(0, 0, 0), VILLAGER, lexicon));
        assertEquals(Reverie.Refusal.UNKNOWN, reverie.addBlock(new Offset(1, 0, 0), "minecraft:diamond_block", lexicon));
        assertEquals(Reverie.Refusal.TOO_FAR, reverie.addBlock(new Offset(25, 0, 0), GRASS, lexicon));
        assertEquals(Reverie.Refusal.NONE, reverie.addFigment(new Offset(3, 0, 0), VILLAGER, lexicon));
        assertEquals(2, reverie.size());
    }

    @Test
    void theBudgetCountsBlocksAndFigmentsAlike() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS);
        for (int i = 0; i < 18; i++) {
            assertEquals(Reverie.Refusal.NONE, reverie.addBlock(new Offset(i - 9, 0, 0), GRASS, lexicon));
        }
        assertEquals(Reverie.Refusal.FULL, reverie.addBlock(new Offset(10, 0, 0), GRASS, lexicon),
                "one impression buys 16 + 2 = 18 elements");
    }

    @Test
    void clustersAreFaceConnectedAndSensesCoverAWholeCluster() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS);
        reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon);
        reverie.addBlock(new Offset(1, 0, 0), GRASS, lexicon);
        reverie.addBlock(new Offset(2, 1, 0), GRASS, lexicon);
        List<List<ImaginedBlock>> clusters = reverie.clusters();
        assertEquals(2, clusters.size(), "a diagonal is not a face");
        assertEquals(2, clusters.get(0).size());

        reverie.setClusterSenses(new Offset(1, 0, 0), EnumSet.of(Sense.SHADOW));
        assertEquals(Set.of(Sense.SHADOW), reverie.clusters().get(0).get(0).senses());
        assertEquals(Set.of(), reverie.clusters().get(1).get(0).senses());

        reverie.addBlock(new Offset(0, 0, 1), GRASS, lexicon);
        assertEquals(Set.of(Sense.SHADOW), reverie.clusters().get(0).get(2).senses(),
                "a block laid against a cluster joins its senses");
        assertEquals(1, reverie.senseLayers());
    }

    @Test
    void aFigmentCarriesItsScriptAndItsSenses() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("creature:" + VILLAGER);
        reverie.addFigment(new Offset(0, 0, 0), VILLAGER, lexicon);
        assertEquals(Script.DEFAULT, reverie.figments().get(0).script());
        reverie.setScript(0, new Script(Stance.WANDER, Reaction.FLEE));
        reverie.setFigmentSenses(0, EnumSet.of(Sense.SOUND, Sense.SHADOW));
        assertEquals(Reaction.FLEE, reverie.figments().get(0).script().reaction());
        assertEquals(2, reverie.senseLayers());
        assertEquals(1.25F * 1.15F, Sense.multiplier(reverie.figments().get(0).senses()), 1.0E-6F);
    }

    @Test
    void itSurvivesNbtAndACopyIsIndependent() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS, "creature:" + VILLAGER);
        reverie.setName("Pit");
        reverie.setFacing(3);
        reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon);
        reverie.setClusterSenses(new Offset(0, 0, 0), EnumSet.of(Sense.SCENT));
        reverie.addFigment(new Offset(2, 0, 2), VILLAGER, lexicon);
        reverie.setScript(0, new Script(Stance.GUARD, Reaction.STARE));

        Reverie back = ReverieNbt.load(ReverieNbt.save(reverie));
        assertEquals("Pit", back.name());
        assertEquals(3, back.facing());
        assertEquals(Set.of(Sense.SCENT), back.blocks().iterator().next().senses());
        assertEquals(new Script(Stance.GUARD, Reaction.STARE), back.figments().get(0).script());

        Reverie copy = reverie.copy();
        reverie.clear();
        assertEquals(2, copy.size());
        assertTrue(reverie.isEmpty());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.ReverieTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write the value types**

`Offset.java`:

```java
package com.efkrdnz.magical.magic.mind;

import java.util.List;

/** A cell relative to a reverie's anchor, in the reverie's own facing. */
public record Offset(int dx, int dy, int dz) {
    /** Clockwise quarter turns seen from above; east (1,0) becomes south (0,1). */
    public Offset rotate(int clockwiseTurns) {
        int x = dx;
        int z = dz;
        for (int i = 0; i < Math.floorMod(clockwiseTurns, 4); i++) {
            int turned = -z;
            z = x;
            x = turned;
        }
        return new Offset(x, dy, z);
    }

    public boolean within(int reach) {
        return Math.abs(dx) <= reach && Math.abs(dy) <= reach && Math.abs(dz) <= reach;
    }

    public List<Offset> neighbours() {
        return List.of(new Offset(dx + 1, dy, dz), new Offset(dx - 1, dy, dz),
                new Offset(dx, dy + 1, dz), new Offset(dx, dy - 1, dz),
                new Offset(dx, dy, dz + 1), new Offset(dx, dy, dz - 1));
    }
}
```

`Sense.java`:

```java
package com.efkrdnz.magical.magic.mind;

import java.util.EnumSet;
import java.util.Set;

/** A layer laid over an element that makes it easier to believe; each is paid for at the unveil. */
public enum Sense {
    SOUND(1.25F),
    SHADOW(1.15F),
    SCENT(1.10F);

    private final float gain;

    Sense(float gain) {
        this.gain = gain;
    }

    public float gain() {
        return gain;
    }

    public static float multiplier(Set<Sense> senses) {
        float product = 1.0F;
        for (Sense sense : senses) {
            product *= sense.gain;
        }
        return product;
    }

    public static int mask(Set<Sense> senses) {
        int mask = 0;
        for (Sense sense : senses) {
            mask |= 1 << sense.ordinal();
        }
        return mask;
    }

    public static Set<Sense> fromMask(int mask) {
        EnumSet<Sense> senses = EnumSet.noneOf(Sense.class);
        for (Sense sense : values()) {
            if ((mask & (1 << sense.ordinal())) != 0) {
                senses.add(sense);
            }
        }
        return senses;
    }
}
```

`Stance.java`:

```java
package com.efkrdnz.magical.magic.mind;

/** What a figment does when nothing is happening to it. */
public enum Stance { IDLE, WANDER, GUARD, FOLLOW }
```

`Reaction.java`:

```java
package com.efkrdnz.magical.magic.mind;

/** What a figment does about the nearest viewer that believes it. */
public enum Reaction { IGNORE, APPROACH, FLEE, STARE, CHASE }
```

`Script.java`:

```java
package com.efkrdnz.magical.magic.mind;

/** A figment's behaviour: how it stands, and how it answers a viewer. */
public record Script(Stance stance, Reaction reaction) {
    public static final Script DEFAULT = new Script(Stance.IDLE, Reaction.IGNORE);
}
```

`ImaginedBlock.java`:

```java
package com.efkrdnz.magical.magic.mind;

import java.util.Set;

public record ImaginedBlock(Offset at, String blockId, Set<Sense> senses) {
    public ImaginedBlock {
        senses = Set.copyOf(senses);
    }

    public ImaginedBlock withSenses(Set<Sense> next) {
        return new ImaginedBlock(at, blockId, next);
    }
}
```

`Figment.java`:

```java
package com.efkrdnz.magical.magic.mind;

import java.util.Set;

public record Figment(Offset at, String creatureId, Script script, Set<Sense> senses) {
    public Figment {
        senses = Set.copyOf(senses);
    }

    public Figment withScript(Script next) {
        return new Figment(at, creatureId, next, senses);
    }

    public Figment withSenses(Set<Sense> next) {
        return new Figment(at, creatureId, script, next);
    }
}
```

- [ ] **Step 4: Write `Reverie`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * One authored scene: imagined blocks and figments at offsets from an anchor, in the facing the
 * wielder had when they wrote it. Blocks that touch face to face form a cluster, and a cluster is
 * one element to every viewer - believed, sensed and shattered as a whole.
 */
public final class Reverie {
    public static final int REACH = 24;

    public enum Refusal { NONE, FULL, UNKNOWN, OCCUPIED, TOO_FAR }

    private String name = "";
    private int facing;
    private final LinkedHashMap<Offset, ImaginedBlock> blocks = new LinkedHashMap<>();
    private final List<Figment> figments = new ArrayList<>();

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public int facing() {
        return facing;
    }

    public void setFacing(int facing) {
        this.facing = Math.floorMod(facing, 4);
    }

    public Refusal addBlock(Offset at, String blockId, Lexicon lexicon) {
        Refusal refusal = refuse(at, "block:" + blockId, lexicon);
        if (refusal != Refusal.NONE) {
            return refusal;
        }
        Set<Sense> inherited = Set.of();
        for (Offset neighbour : at.neighbours()) {
            ImaginedBlock next = blocks.get(neighbour);
            if (next != null) {
                inherited = next.senses();
                break;
            }
        }
        blocks.put(at, new ImaginedBlock(at, blockId, inherited));
        return Refusal.NONE;
    }

    public Refusal addFigment(Offset at, String creatureId, Lexicon lexicon) {
        Refusal refusal = refuse(at, "creature:" + creatureId, lexicon);
        if (refusal != Refusal.NONE) {
            return refusal;
        }
        figments.add(new Figment(at, creatureId, Script.DEFAULT, Set.of()));
        return Refusal.NONE;
    }

    private Refusal refuse(Offset at, String key, Lexicon lexicon) {
        if (!at.within(REACH)) {
            return Refusal.TOO_FAR;
        }
        if (!lexicon.knows(key)) {
            return Refusal.UNKNOWN;
        }
        if (occupied(at)) {
            return Refusal.OCCUPIED;
        }
        return size() >= lexicon.budget() ? Refusal.FULL : Refusal.NONE;
    }

    public boolean occupied(Offset at) {
        return blocks.containsKey(at) || figments.stream().anyMatch(f -> f.at().equals(at));
    }

    public boolean remove(Offset at) {
        return blocks.remove(at) != null || figments.removeIf(f -> f.at().equals(at));
    }

    public Collection<ImaginedBlock> blocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public List<Figment> figments() {
        return Collections.unmodifiableList(figments);
    }

    /** Face-connected clusters, each in the order its blocks were laid, clusters by their first block. */
    public List<List<ImaginedBlock>> clusters() {
        List<List<ImaginedBlock>> clusters = new ArrayList<>();
        Set<Offset> seen = new HashSet<>();
        for (Offset start : blocks.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            Set<Offset> members = new HashSet<>();
            ArrayDeque<Offset> open = new ArrayDeque<>();
            open.add(start);
            members.add(start);
            while (!open.isEmpty()) {
                for (Offset next : open.poll().neighbours()) {
                    if (blocks.containsKey(next) && members.add(next)) {
                        seen.add(next);
                        open.add(next);
                    }
                }
            }
            List<ImaginedBlock> cluster = new ArrayList<>();
            for (ImaginedBlock block : blocks.values()) {
                if (members.contains(block.at())) {
                    cluster.add(block);
                }
            }
            clusters.add(List.copyOf(cluster));
        }
        return clusters;
    }

    public void setClusterSenses(Offset member, Set<Sense> senses) {
        for (List<ImaginedBlock> cluster : clusters()) {
            if (cluster.stream().anyMatch(b -> b.at().equals(member))) {
                for (ImaginedBlock block : cluster) {
                    blocks.put(block.at(), block.withSenses(senses));
                }
                return;
            }
        }
    }

    public void setFigmentSenses(int index, Set<Sense> senses) {
        if (index >= 0 && index < figments.size()) {
            figments.set(index, figments.get(index).withSenses(senses));
        }
    }

    public void setScript(int index, Script script) {
        if (index >= 0 && index < figments.size()) {
            figments.set(index, figments.get(index).withScript(script));
        }
    }

    public int size() {
        return blocks.size() + figments.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** Sense layers as billed: one per sense per cluster, one per sense per figment. */
    public int senseLayers() {
        int layers = 0;
        for (List<ImaginedBlock> cluster : clusters()) {
            layers += cluster.get(0).senses().size();
        }
        for (Figment figment : figments) {
            layers += figment.senses().size();
        }
        return layers;
    }

    void put(ImaginedBlock block) {
        if (block.at().within(REACH) && !occupied(block.at())) {
            blocks.put(block.at(), block);
        }
    }

    void put(Figment figment) {
        if (figment.at().within(REACH) && !occupied(figment.at())) {
            figments.add(figment);
        }
    }

    public Reverie copy() {
        Reverie copy = new Reverie();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(Reverie other) {
        name = other.name;
        facing = other.facing;
        blocks.clear();
        blocks.putAll(other.blocks);
        figments.clear();
        figments.addAll(other.figments);
    }

    public void clear() {
        name = "";
        facing = 0;
        blocks.clear();
        figments.clear();
    }
}
```

- [ ] **Step 5: Write `ReverieNbt`**

Enums are stored by name, so reordering one can never turn a Flee into a Chase. A load caps the element count at `Lexicon.MAX_BUDGET` and drops anything outside `REACH`, so a forged tag cannot grow a scene.

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class ReverieNbt {
    private ReverieNbt() {}

    public static CompoundTag save(Reverie reverie) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", reverie.name());
        tag.putInt("facing", reverie.facing());
        ListTag blocks = new ListTag();
        for (ImaginedBlock block : reverie.blocks()) {
            CompoundTag entry = at(block.at());
            entry.putString("id", block.blockId());
            entry.putInt("senses", Sense.mask(block.senses()));
            blocks.add(entry);
        }
        tag.put("blocks", blocks);
        ListTag figments = new ListTag();
        for (Figment figment : reverie.figments()) {
            CompoundTag entry = at(figment.at());
            entry.putString("id", figment.creatureId());
            entry.putString("stance", figment.script().stance().name());
            entry.putString("reaction", figment.script().reaction().name());
            entry.putInt("senses", Sense.mask(figment.senses()));
            figments.add(entry);
        }
        tag.put("figments", figments);
        return tag;
    }

    public static Reverie load(CompoundTag tag) {
        Reverie reverie = new Reverie();
        reverie.setName(tag.getString("name"));
        reverie.setFacing(tag.getInt("facing"));
        for (Tag raw : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            if (reverie.size() >= Lexicon.MAX_BUDGET) {
                break;
            }
            CompoundTag entry = (CompoundTag) raw;
            reverie.put(new ImaginedBlock(offset(entry), entry.getString("id"),
                    Sense.fromMask(entry.getInt("senses"))));
        }
        for (Tag raw : tag.getList("figments", Tag.TAG_COMPOUND)) {
            if (reverie.size() >= Lexicon.MAX_BUDGET) {
                break;
            }
            CompoundTag entry = (CompoundTag) raw;
            Script script = new Script(named(Stance.class, entry.getString("stance"), Stance.IDLE),
                    named(Reaction.class, entry.getString("reaction"), Reaction.IGNORE));
            reverie.put(new Figment(offset(entry), entry.getString("id"), script,
                    Sense.fromMask(entry.getInt("senses"))));
        }
        return reverie;
    }

    private static CompoundTag at(Offset offset) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("x", offset.dx());
        entry.putInt("y", offset.dy());
        entry.putInt("z", offset.dz());
        return entry;
    }

    private static Offset offset(CompoundTag entry) {
        return new Offset(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
    }

    private static <E extends Enum<E>> E named(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException missing) {
            return fallback;
        }
    }
}
```

- [ ] **Step 6: Run the test and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.ReverieTest"`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/Offset.java src/main/java/com/efkrdnz/magical/magic/mind/Sense.java src/main/java/com/efkrdnz/magical/magic/mind/Stance.java src/main/java/com/efkrdnz/magical/magic/mind/Reaction.java src/main/java/com/efkrdnz/magical/magic/mind/Script.java src/main/java/com/efkrdnz/magical/magic/mind/ImaginedBlock.java src/main/java/com/efkrdnz/magical/magic/mind/Figment.java src/main/java/com/efkrdnz/magical/magic/mind/Reverie.java src/main/java/com/efkrdnz/magical/magic/mind/ReverieNbt.java src/test/java/com/efkrdnz/magical/magic/mind/ReverieTest.java
git commit -m "feat: the reverie model - blocks, figments, clusters, senses, scripts"
```

---

### Task 3: MindState on the player

**Files:**
- Create: `magic/mind/MindState.java`
- Modify: `magic/PlayerMagicState.java`:
  - a field beside `weave` (line 59);
  - an accessor beside `weave()` (line 516);
  - `clearAuthority()` (line 1178);
  - `copy()` (line 2079, beside `copy.weave.copyFrom(weave)`);
  - `save()` (line 2194, beside `tag.put("weave", ...)`);
  - `load()` (line 2365, beside `state.weave.load(...)`).
- Test: `magic/mind/MindStateTest.java`

**Interfaces:**
- Consumes: `Lexicon`, `Reverie`, `ReverieNbt` (Tasks 1 and 2).
- Produces:
  - `MindState.SLOTS = 3`.
  - `MindState`: `lexicon()`, `reverie(int slot)` (clamped to 0..2), `activeSlot()`, `setActiveSlot(int)`, `active()`, `save()`, `load(CompoundTag)`, `copyFrom(MindState)`, `clear()`.
  - `PlayerMagicState.mind()`.

`PlayerMagicState.save()` is also the wire format. `sync(player)` sends the whole tag, so the client reads the lexicon and the reveries off `ClientMagicState.get().mind()` with no new payload.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.PlayerMagicState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MindStateTest {
    private static PlayerMagicState written() {
        PlayerMagicState state = new PlayerMagicState();
        state.mind().lexicon().learn("block:minecraft:stone", 6);
        state.mind().reverie(1).setName("Wall");
        state.mind().reverie(1).addBlock(new Offset(0, 0, 0), "minecraft:stone", state.mind().lexicon());
        state.mind().setActiveSlot(1);
        return state;
    }

    @Test
    void theMindRidesThePlayerSave() {
        PlayerMagicState back = PlayerMagicState.load(written().save());
        assertEquals(6, back.mind().lexicon().gazes("block:minecraft:stone"));
        assertEquals("Wall", back.mind().active().name());
        assertEquals(1, back.mind().active().size());
    }

    @Test
    void aCopyForDeathKeepsItAndItIsIndependent() {
        PlayerMagicState state = written();
        PlayerMagicState copy = state.copy();
        state.mind().clear();
        assertEquals(1, copy.mind().reverie(1).size());
        assertEquals(0, state.mind().lexicon().size());
    }

    @Test
    void losingTheAuthorityForgetsTheMind() {
        PlayerMagicState state = written();
        state.clearAuthority();
        assertEquals(0, state.mind().lexicon().size());
        assertTrue(state.mind().reverie(1).isEmpty());
        assertEquals(0, state.mind().activeSlot());
    }

    @Test
    void aSlotOutOfRangeIsClamped() {
        MindState mind = new MindState();
        mind.setActiveSlot(9);
        assertEquals(2, mind.activeSlot());
        assertSame(mind.reverie(2), mind.reverie(-1 + 3));
        assertSame(mind.reverie(0), mind.reverie(-5));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.MindStateTest"`
Expected: compilation FAIL, `mind()` does not exist.

- [ ] **Step 3: Write `MindState`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** The wielder's side of the Authority of Mind: what they can imagine and three written scenes. */
public final class MindState {
    public static final int SLOTS = 3;

    private final Lexicon lexicon = new Lexicon();
    private final Reverie[] reveries = {new Reverie(), new Reverie(), new Reverie()};
    private int activeSlot;

    public Lexicon lexicon() {
        return lexicon;
    }

    public Reverie reverie(int slot) {
        return reveries[clamp(slot)];
    }

    public int activeSlot() {
        return activeSlot;
    }

    public void setActiveSlot(int slot) {
        activeSlot = clamp(slot);
    }

    public Reverie active() {
        return reveries[activeSlot];
    }

    private static int clamp(int slot) {
        return Math.max(0, Math.min(SLOTS - 1, slot));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("lexicon", lexicon.save());
        ListTag slots = new ListTag();
        for (Reverie reverie : reveries) {
            slots.add(ReverieNbt.save(reverie));
        }
        tag.put("reveries", slots);
        tag.putInt("active", activeSlot);
        return tag;
    }

    public void load(CompoundTag tag) {
        lexicon.load(tag.getCompound("lexicon"));
        ListTag slots = tag.getList("reveries", Tag.TAG_COMPOUND);
        for (int i = 0; i < SLOTS; i++) {
            reveries[i].copyFrom(i < slots.size() ? ReverieNbt.load(slots.getCompound(i)) : new Reverie());
        }
        setActiveSlot(tag.getInt("active"));
    }

    public void copyFrom(MindState other) {
        lexicon.copyFrom(other.lexicon);
        for (int i = 0; i < SLOTS; i++) {
            reveries[i].copyFrom(other.reveries[i]);
        }
        activeSlot = other.activeSlot;
    }

    public void clear() {
        lexicon.clear();
        for (Reverie reverie : reveries) {
            reverie.clear();
        }
        activeSlot = 0;
    }
}
```

- [ ] **Step 4: Wire it into `PlayerMagicState`**

Beside the `weave` field (line 59):

```java
    private final com.efkrdnz.magical.magic.mind.MindState mind = new com.efkrdnz.magical.magic.mind.MindState();
```

Beside `weave()` (line 516):

```java
    public com.efkrdnz.magical.magic.mind.MindState mind() {
        return mind;
    }
```

In `clearAuthority()`, after `weave.clear();`:

```java
        mind.clear();
```

In `copy()`, after `copy.weave.copyFrom(weave);`:

```java
        copy.mind.copyFrom(mind);
```

In `save()`, after `tag.put("weave", weave.save());`:

```java
        tag.put("mind", mind.save());
```

In `load(...)`, after `state.weave.load(tag.getCompound("weave"));`:

```java
        state.mind.load(tag.getCompound("mind"));
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.MindStateTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/MindState.java src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java src/test/java/com/efkrdnz/magical/magic/mind/MindStateTest.java
git commit -m "feat: the mind rides the player state - lexicon, three reveries, the active slot"
```

---

### Task 4: Plausibility

**Files:**
- Create in `magic/mind/`: `MindWorld.java`, `CreatureTraits.java`, `Plausibility.java`
- Test: `magic/mind/PlausibilityTest.java`

**Interfaces:**
- Consumes: `Lexicon.fidelity`, `Script`, `Reaction` (Tasks 1 and 2).
- Produces:
  - `MindWorld`: `solid(x,y,z)`, `blockId(x,y,z)`, `openSkyDaylight(x,y,z)`, `water(x,y,z)`.
  - `CreatureTraits.of(String creatureId)`, with `aquatic()`, `undead()`, `natural()` and `unnatural()`.
  - `Plausibility.Placed(int x, int y, int z, String id)`, where `id` is a block or creature id with no prefix.
  - `Plausibility.Term(String key, float value)`, where `key` is a lang suffix under `mind.magical.term.`.
  - `Plausibility.Reading(float p, List<Term> terms)`.
  - `Plausibility.cluster(MindWorld, List<Placed> blocks, Lexicon, int sceneSize)`, returning a `Reading`.
  - `Plausibility.figment(MindWorld, Placed at, Script, Lexicon, int sceneSize)`, returning a `Reading`.
  - `Plausibility.sizeTerm(int sceneSize)`, returning a float.
  - Constants `START = 0.5F`, `FLOOR = 0.05F`, `CONTEXT_RADIUS = 8`.

What the terms mean in code:
- **Supported:** an imagined block with a real solid below it, or with a real solid beside or above it (it is attached to the real world), is supported. So is every block that meets a supported block face to face inside its own cluster. Everything else is unsupported.
- **Context:** the fraction of the cluster's blocks whose material occurs among the real blocks within `CONTEXT_RADIUS` of the cluster's first block. **Alien:** the rest. Air never counts as a material.
- **Size:** `-0.10 * log2(sceneSize / 32)` when `sceneSize > 32`, else nothing.
- **Figments:** a figment has no material terms. It takes fidelity, habitat, the script of its kind, and size:
  - An aquatic kind takes the habitat term when it stands out of water.
  - An undead kind takes it when it stands under open daylight, where it would be burning and is not.
  - The script term looks at the reaction. It is like the kind when the reaction is in `natural()` and unlike when it is in `unnatural()`.

A term of value zero is left out of `terms`, so the Playbill forecast prints only what moved the number.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlausibilityTest {
    private static final float EPSILON = 1.0E-5F;

    /** A flat world: a floor of {@code floor} at y 0 from -12 to 12, air above, optional daylight. */
    private static final class FlatWorld implements MindWorld {
        private final Map<String, String> blocks = new HashMap<>();
        private final boolean daylight;

        FlatWorld(String floor, boolean daylight) {
            this.daylight = daylight;
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    blocks.put(x + "," + 0 + "," + z, floor);
                }
            }
        }

        void set(int x, int y, int z, String id) {
            blocks.put(x + "," + y + "," + z, id);
        }

        @Override public String blockId(int x, int y, int z) {
            return blocks.getOrDefault(x + "," + y + "," + z, "minecraft:air");
        }

        @Override public boolean solid(int x, int y, int z) {
            String id = blockId(x, y, z);
            return !id.equals("minecraft:air") && !id.equals("minecraft:water");
        }

        @Override public boolean water(int x, int y, int z) {
            return blockId(x, y, z).equals("minecraft:water");
        }

        @Override public boolean openSkyDaylight(int x, int y, int z) {
            return daylight;
        }
    }

    private static Lexicon studied(String key, int gazes) {
        Lexicon lexicon = new Lexicon();
        lexicon.learn(key, gazes);
        return lexicon;
    }

    @Test
    void aGrassLidOverAPitIsHeldByItsRimAndMatchesTheField() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        world.set(0, 0, 0, "minecraft:air");
        world.set(1, 0, 0, "minecraft:air");
        List<Plausibility.Placed> lid = List.of(
                new Plausibility.Placed(0, 0, 0, "minecraft:grass_block"),
                new Plausibility.Placed(1, 0, 0, "minecraft:grass_block"));
        Plausibility.Reading reading = Plausibility.cluster(world, lid,
                studied("block:minecraft:grass_block", 5), 2);
        assertEquals(0.90F, reading.p(), EPSILON, "0.5 + context 0.30 + fidelity 0.10");
        assertTrue(reading.terms().stream().noneMatch(t -> t.key().equals("unsupported")));
    }

    @Test
    void aFloatingSlabPaysForHangingInTheAir() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        Plausibility.Reading reading = Plausibility.cluster(world,
                List.of(new Plausibility.Placed(0, 5, 0, "minecraft:grass_block")),
                studied("block:minecraft:grass_block", 5), 1);
        assertEquals(0.50F, reading.p(), EPSILON, "0.5 - 0.40 + 0.30 + 0.10");
    }

    @Test
    void theLazyLieIsADiamondWallInAField() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        Plausibility.Reading reading = Plausibility.cluster(world,
                List.of(new Plausibility.Placed(0, 1, 0, "minecraft:diamond_block"),
                        new Plausibility.Placed(0, 2, 0, "minecraft:diamond_block")),
                studied("block:minecraft:diamond_block", 1), 2);
        assertEquals(0.30F, reading.p(), EPSILON, "0.5 - alien 0.20");
    }

    @Test
    void aVillagerThatFleesIsLikeItsKindAndOneThatChasesIsNot() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        Lexicon lexicon = studied("creature:minecraft:villager", 5);
        Plausibility.Placed at = new Plausibility.Placed(0, 1, 0, "minecraft:villager");
        assertEquals(0.70F, Plausibility.figment(world, at, new Script(Stance.WANDER, Reaction.FLEE), lexicon, 1).p(), EPSILON);
        assertEquals(0.40F, Plausibility.figment(world, at, new Script(Stance.WANDER, Reaction.CHASE), lexicon, 1).p(), EPSILON);
    }

    @Test
    void anUndeadFigmentInDaylightThatDoesNotBurnIsDoubted() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", true);
        Plausibility.Reading reading = Plausibility.figment(world,
                new Plausibility.Placed(0, 1, 0, "minecraft:zombie"),
                new Script(Stance.IDLE, Reaction.CHASE), studied("creature:minecraft:zombie", 1), 1);
        assertEquals(0.30F, reading.p(), EPSILON, "0.5 - habitat 0.30 + like its kind 0.10");
    }

    @Test
    void aFishOnDryLandIsOutOfItsHabitat() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        world.set(3, 1, 3, "minecraft:water");
        Lexicon lexicon = studied("creature:minecraft:cod", 1);
        Script idle = Script.DEFAULT;
        assertEquals(0.30F, Plausibility.figment(world, new Plausibility.Placed(0, 1, 0, "minecraft:cod"), idle, lexicon, 1).p(), EPSILON,
                "0.5 - habitat 0.30 + like its kind 0.10: ignoring is a fish's nature");
        assertEquals(0.60F, Plausibility.figment(world, new Plausibility.Placed(3, 1, 3, "minecraft:cod"), idle, lexicon, 1).p(), EPSILON);
    }

    @Test
    void sizeCostsATenthPerDoublingAboveThirtyTwo() {
        assertEquals(0.0F, Plausibility.sizeTerm(32), EPSILON);
        assertEquals(-0.10F, Plausibility.sizeTerm(64), EPSILON);
        assertEquals(-0.20F, Plausibility.sizeTerm(128), EPSILON);
    }

    @Test
    void theReadingIsClampedAndReportsOnlyWhatMovedIt() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", true);
        Plausibility.Reading reading = Plausibility.figment(world,
                new Plausibility.Placed(0, 1, 0, "minecraft:zombie"),
                new Script(Stance.IDLE, Reaction.FLEE), studied("creature:minecraft:zombie", 1), 128);
        assertEquals(Plausibility.FLOOR, reading.p(), EPSILON, "0.5 - 0.30 - 0.20 - 0.20 is below the floor");
        assertEquals(List.of("habitat", "unlike_kind", "size"), reading.terms().stream().map(Plausibility.Term::key).toList());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.PlausibilityTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `MindWorld`**

```java
package com.efkrdnz.magical.magic.mind;

/** The little that plausibility needs to know about the real world around a lie. */
public interface MindWorld {
    /** A real block a body would stand on or bump into. */
    boolean solid(int x, int y, int z);

    /** The real block's id, {@code minecraft:air} where there is none. */
    String blockId(int x, int y, int z);

    /** Daytime and nothing between this cell and the sky. */
    boolean openSkyDaylight(int x, int y, int z);

    boolean water(int x, int y, int z);
}
```

- [ ] **Step 4: Write `CreatureTraits`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.Map;
import java.util.Set;

/** What a kind of creature is like, as far as a viewer's common sense goes. */
public record CreatureTraits(boolean aquatic, boolean undead, Set<Reaction> natural, Set<Reaction> unnatural) {
    public static final CreatureTraits NEUTRAL = new CreatureTraits(false, false, Set.of(), Set.of());

    private static final CreatureTraits HUNTER = new CreatureTraits(false, false,
            Set.of(Reaction.CHASE, Reaction.APPROACH), Set.of(Reaction.FLEE));
    private static final CreatureTraits UNDEAD_HUNTER = new CreatureTraits(false, true,
            Set.of(Reaction.CHASE, Reaction.APPROACH), Set.of(Reaction.FLEE));
    private static final CreatureTraits PREY = new CreatureTraits(false, false,
            Set.of(Reaction.FLEE, Reaction.IGNORE), Set.of(Reaction.CHASE));
    private static final CreatureTraits WARDEN = new CreatureTraits(false, false,
            Set.of(Reaction.CHASE, Reaction.STARE), Set.of(Reaction.FLEE));
    private static final CreatureTraits PET = new CreatureTraits(false, false,
            Set.of(Reaction.IGNORE, Reaction.STARE, Reaction.APPROACH), Set.of(Reaction.CHASE));
    private static final CreatureTraits FISH = new CreatureTraits(true, false,
            Set.of(Reaction.IGNORE, Reaction.FLEE), Set.of(Reaction.CHASE));

    private static final Map<String, CreatureTraits> TABLE = Map.ofEntries(
            Map.entry("minecraft:zombie", UNDEAD_HUNTER),
            Map.entry("minecraft:drowned", UNDEAD_HUNTER),
            Map.entry("minecraft:zombie_villager", UNDEAD_HUNTER),
            Map.entry("minecraft:skeleton", UNDEAD_HUNTER),
            Map.entry("minecraft:stray", UNDEAD_HUNTER),
            Map.entry("minecraft:phantom", UNDEAD_HUNTER),
            Map.entry("minecraft:husk", HUNTER),
            Map.entry("minecraft:spider", HUNTER),
            Map.entry("minecraft:cave_spider", HUNTER),
            Map.entry("minecraft:creeper", HUNTER),
            Map.entry("minecraft:pillager", HUNTER),
            Map.entry("minecraft:vindicator", HUNTER),
            Map.entry("minecraft:witch", HUNTER),
            Map.entry("minecraft:enderman", HUNTER),
            Map.entry("minecraft:iron_golem", WARDEN),
            Map.entry("minecraft:wolf", WARDEN),
            Map.entry("minecraft:villager", PREY),
            Map.entry("minecraft:cow", PREY),
            Map.entry("minecraft:sheep", PREY),
            Map.entry("minecraft:pig", PREY),
            Map.entry("minecraft:chicken", PREY),
            Map.entry("minecraft:rabbit", PREY),
            Map.entry("minecraft:horse", PREY),
            Map.entry("minecraft:cat", PET),
            Map.entry("minecraft:ocelot", PET),
            Map.entry("minecraft:cod", FISH),
            Map.entry("minecraft:salmon", FISH),
            Map.entry("minecraft:tropical_fish", FISH),
            Map.entry("minecraft:pufferfish", FISH),
            Map.entry("minecraft:squid", FISH),
            Map.entry("minecraft:glow_squid", FISH),
            Map.entry("minecraft:dolphin", FISH),
            Map.entry("minecraft:axolotl", FISH));

    public static CreatureTraits of(String creatureId) {
        return TABLE.getOrDefault(creatureId, NEUTRAL);
    }
}
```

- [ ] **Step 5: Write `Plausibility`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How easy a lie is to believe where it stands, as a number and the terms that made it. Pure: the
 * world comes in through {@link MindWorld}, so the Playbill forecasts with exactly the arithmetic
 * the server will use.
 */
public final class Plausibility {
    public static final float START = 0.5F;
    public static final float FLOOR = 0.05F;
    public static final float CEILING = 1.0F;
    public static final int CONTEXT_RADIUS = 8;
    public static final float UNSUPPORTED = -0.40F;
    public static final float CONTEXT = 0.30F;
    public static final float ALIEN = -0.20F;
    public static final float FIDELITY = 0.10F;
    public static final float HABITAT = -0.30F;
    public static final float UNLIKE_KIND = -0.20F;
    public static final float LIKE_KIND = 0.10F;
    public static final float SIZE_PER_DOUBLING = -0.10F;
    public static final int SIZE_FREE = 32;
    private static final String AIR = "minecraft:air";

    public record Placed(int x, int y, int z, String id) {}

    public record Term(String key, float value) {}

    public record Reading(float p, List<Term> terms) {
        public Reading {
            terms = List.copyOf(terms);
        }
    }

    private Plausibility() {}

    public static Reading cluster(MindWorld world, List<Placed> blocks, Lexicon lexicon, int sceneSize) {
        List<Term> terms = new ArrayList<>();
        int count = blocks.size();
        if (count == 0) {
            return new Reading(START, terms);
        }
        add(terms, "unsupported", UNSUPPORTED * unsupported(world, blocks) / count);

        Placed first = blocks.get(0);
        Set<String> nearby = materialsNear(world, first.x(), first.y(), first.z());
        int matching = 0;
        float fidelity = 0.0F;
        for (Placed block : blocks) {
            if (nearby.contains(block.id())) {
                matching++;
            }
            fidelity += lexicon.fidelity("block:" + block.id());
        }
        add(terms, "context", CONTEXT * matching / count);
        add(terms, "alien", ALIEN * (count - matching) / count);
        add(terms, "fidelity", FIDELITY * (fidelity / count - 1.0F));
        add(terms, "size", sizeTerm(sceneSize));
        return read(terms);
    }

    public static Reading figment(MindWorld world, Placed at, Script script, Lexicon lexicon, int sceneSize) {
        List<Term> terms = new ArrayList<>();
        CreatureTraits traits = CreatureTraits.of(at.id());
        add(terms, "fidelity", FIDELITY * (lexicon.fidelity("creature:" + at.id()) - 1));
        boolean outOfWater = traits.aquatic() && !world.water(at.x(), at.y(), at.z());
        boolean unburnt = traits.undead() && world.openSkyDaylight(at.x(), at.y(), at.z());
        add(terms, "habitat", outOfWater || unburnt ? HABITAT : 0.0F);
        if (traits.natural().contains(script.reaction())) {
            add(terms, "like_kind", LIKE_KIND);
        } else if (traits.unnatural().contains(script.reaction())) {
            add(terms, "unlike_kind", UNLIKE_KIND);
        }
        add(terms, "size", sizeTerm(sceneSize));
        return read(terms);
    }

    public static float sizeTerm(int sceneSize) {
        if (sceneSize <= SIZE_FREE) {
            return 0.0F;
        }
        return SIZE_PER_DOUBLING * (float) (Math.log((double) sceneSize / SIZE_FREE) / Math.log(2.0));
    }

    private static Reading read(List<Term> terms) {
        float p = START;
        for (Term term : terms) {
            p += term.value();
        }
        return new Reading(Math.max(FLOOR, Math.min(CEILING, p)), terms);
    }

    private static void add(List<Term> terms, String key, float value) {
        if (Math.abs(value) > 1.0E-6F) {
            terms.add(new Term(key, value));
        }
    }

    /** Blocks not held up by the real world, directly or through their own cluster. */
    private static int unsupported(MindWorld world, List<Placed> blocks) {
        Set<Long> cells = new HashSet<>();
        for (Placed block : blocks) {
            cells.add(pack(block.x(), block.y(), block.z()));
        }
        Set<Long> held = new HashSet<>();
        ArrayDeque<Placed> open = new ArrayDeque<>();
        for (Placed block : blocks) {
            if (anchored(world, block)) {
                held.add(pack(block.x(), block.y(), block.z()));
                open.add(block);
            }
        }
        while (!open.isEmpty()) {
            Placed from = open.poll();
            for (int[] d : FACES) {
                long next = pack(from.x() + d[0], from.y() + d[1], from.z() + d[2]);
                if (cells.contains(next) && held.add(next)) {
                    open.add(new Placed(from.x() + d[0], from.y() + d[1], from.z() + d[2], from.id()));
                }
            }
        }
        return blocks.size() - held.size();
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static boolean anchored(MindWorld world, Placed block) {
        for (int[] d : FACES) {
            if (world.solid(block.x() + d[0], block.y() + d[1], block.z() + d[2])) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> materialsNear(MindWorld world, int x, int y, int z) {
        Set<String> materials = new HashSet<>();
        for (int dx = -CONTEXT_RADIUS; dx <= CONTEXT_RADIUS; dx++) {
            for (int dy = -CONTEXT_RADIUS; dy <= CONTEXT_RADIUS; dy++) {
                for (int dz = -CONTEXT_RADIUS; dz <= CONTEXT_RADIUS; dz++) {
                    String id = world.blockId(x + dx, y + dy, z + dz);
                    if (!AIR.equals(id)) {
                        materials.add(id);
                    }
                }
            }
        }
        return materials;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
```

`anchored` checks all six faces. Real solid below is the obvious case. Real solid beside or above is "attached to the real world", which covers the rim of a pit lid.

- [ ] **Step 6: Run the test and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.PlausibilityTest"`
Expected: PASS (8 tests).

- [ ] **Step 7: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/MindWorld.java src/main/java/com/efkrdnz/magical/magic/mind/CreatureTraits.java src/main/java/com/efkrdnz/magical/magic/mind/Plausibility.java src/test/java/com/efkrdnz/magical/magic/mind/PlausibilityTest.java
git commit -m "feat: plausibility - support, context, fidelity, habitat, kind and size"
```

---

### Task 5: Belief, susceptibility, scepticism, cost

**Files:**
- Create in `magic/mind/`: `Contradiction.java`, `Belief.java`, `Susceptibility.java`, `Scepticism.java`, `UnveilCost.java`
- Test: `magic/mind/BeliefTest.java`, `magic/mind/SusceptibilityTest.java`

**Interfaces:**
- Consumes: `Reverie`, `Sense` (Task 2).
- Produces:
  - `Contradiction`: `TOUCH(0.60)`, `PROJECTILE(0.35)`, `WITNESS(0.20)`, `HOLLOW_STRIKE(0.25)`, with `penalty()`.
  - `Belief`, with constants `RATE`, `DECAY`, `CONVINCED = 0.5F`, `SHATTER = 0.1F`, `PATHING = 0.3F` and `SURE = 0.8F`. Methods:
    - `get(int viewer, int element)`, returning a float;
    - `convinced(int, int)` and `shattered(int, int)`;
    - `gain(int viewer, int element, float p, float senses, float susceptibility, float novelty)`, returning the new value;
    - `decay(int viewer, int element)`;
    - `contradict(int viewer, int element, Contradiction)`, returning a boolean that is true when this contradiction shattered it;
    - `forget(int viewer)`;
    - `rows()`, returning `List<Row>` with `Row(int viewer, int element, float belief, boolean shattered)`;
    - package-private `set(int, int, float)`;
    - `static ticksToReach(float target, float p, float senses, float susceptibility, float novelty)`, returning an int (−1 when it never gets there).
  - `Susceptibility.of(String entityTypeId)` returns a float. `Susceptibility.blind(String entityTypeId)` returns a boolean.
  - `Scepticism`, with `MEMORY_TICKS = 6000`:
    - `seenThrough(String viewer, Collection<String> impressions, long now)`;
    - `novelty(String viewer, Collection<String> impressions, long now)`, returning a float.
  - `UnveilCost.of(Reverie)` returns an int.

Viewers are keyed by entity id inside one scene, because a scene lives in one level for at most 1200 ticks. Scepticism is the viewer's own memory and outlives any scene, so it is keyed by UUID string.

The Playbill forecast reports ticks to `SURE` (0.8), not to `CONVINCED`. That is the figure the spec's worked numbers use: a zombie at p 0.82 with Sound and Scent reaches 0.8 in about 2.7 s. The forecast line is labelled "Certain in".

- [ ] **Step 1: Write the failing tests**

`BeliefTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BeliefTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void gainClosesAFractionOfTheDistanceLeft() {
        Belief belief = new Belief();
        assertEquals(0.02F * 0.5F, belief.gain(1, 0, 0.5F, 1.0F, 1.0F, 1.0F), EPSILON);
        belief.set(1, 0, 0.5F);
        assertEquals(0.5F + 0.02F * 0.8F * 1.25F * 1.3F * 0.5F, belief.gain(1, 0, 0.8F, 1.25F, 1.3F, 1.0F), EPSILON);
    }

    @Test
    void decayIsSteadyAndNeverShatters() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.004F);
        belief.decay(1, 0);
        assertEquals(0.002F, belief.get(1, 0), EPSILON);
        belief.decay(1, 0);
        belief.decay(1, 0);
        assertEquals(0.0F, belief.get(1, 0), EPSILON);
        assertFalse(belief.shattered(1, 0));
    }

    @Test
    void aTouchBreaksAWeakBeliefAndDentsAStrongOne() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.65F);
        assertTrue(belief.contradict(1, 0, Contradiction.TOUCH), "0.65 - 0.60 is under 0.1");
        assertTrue(belief.shattered(1, 0));
        assertEquals(0.0F, belief.gain(1, 0, 1.0F, 1.0F, 1.0F, 1.0F), EPSILON, "a shattered element is not seen again");

        belief.set(2, 0, 0.9F);
        assertFalse(belief.contradict(2, 0, Contradiction.WITNESS));
        assertEquals(0.70F, belief.get(2, 0), EPSILON);
        assertFalse(belief.contradict(2, 0, Contradiction.PROJECTILE));
        assertEquals(0.35F, belief.get(2, 0), EPSILON);
        assertFalse(belief.contradict(2, 0, Contradiction.WITNESS));
        assertTrue(belief.contradict(2, 0, Contradiction.HOLLOW_STRIKE), "0.15 - 0.25");
    }

    @Test
    void aViewerWhoNeverBelievedHasNothingToShatter() {
        Belief belief = new Belief();
        assertFalse(belief.contradict(5, 0, Contradiction.TOUCH));
        assertFalse(belief.shattered(5, 0));
    }

    @Test
    void convincedIsAtOneHalf() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.49F);
        assertFalse(belief.convinced(1, 0));
        belief.set(1, 0, 0.5F);
        assertTrue(belief.convinced(1, 0));
    }

    @Test
    void theWorkedZombieIsCertainInAboutTwoPointSevenSeconds() {
        float senses = Sense.multiplier(EnumSet.of(Sense.SOUND, Sense.SCENT));
        int ticks = Belief.ticksToReach(Belief.SURE, 0.82F, senses, 1.3F, 1.0F);
        assertEquals(55, ticks);
        assertEquals(-1, Belief.ticksToReach(Belief.SURE, 0.82F, senses, 0.0F, 1.0F), "the wielder never believes");
    }

    @Test
    void forgettingAViewerDropsOnlyItsRows() {
        Belief belief = new Belief();
        belief.set(1, 0, 0.3F);
        belief.set(1, 1, 0.3F);
        belief.set(2, 0, 0.3F);
        belief.forget(1);
        List<Belief.Row> rows = belief.rows();
        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).viewer());
    }

    @Test
    void theSameTrickTwiceIsHalfATrick() {
        Scepticism scepticism = new Scepticism();
        Set<String> wall = Set.of("block:minecraft:stone");
        assertEquals(1.0F, scepticism.novelty("u", wall, 0L), EPSILON);
        scepticism.seenThrough("u", wall, 0L);
        assertEquals(0.5F, scepticism.novelty("u", wall, 100L), EPSILON);
        scepticism.seenThrough("u", wall, 100L);
        assertEquals(0.25F, scepticism.novelty("u", Set.of("block:minecraft:stone", "block:minecraft:dirt"), 200L), EPSILON);
        assertEquals(1.0F, scepticism.novelty("u", wall, 100L + Scepticism.MEMORY_TICKS + 1), EPSILON, "the memory fades");
        assertEquals(1.0F, scepticism.novelty("someone else", wall, 200L), EPSILON);
    }

    @Test
    void anUnveilCostsTenAMarkPerBlockFivePerFigmentAndTenPerSenseLayer() {
        Lexicon lexicon = new Lexicon();
        lexicon.gaze("block:minecraft:grass_block");
        lexicon.gaze("creature:minecraft:villager");
        Reverie reverie = new Reverie();
        for (int i = 0; i < 5; i++) {
            reverie.addBlock(new Offset(i, 0, 0), "minecraft:grass_block", lexicon);
        }
        reverie.setClusterSenses(new Offset(0, 0, 0), EnumSet.of(Sense.SHADOW));
        reverie.addFigment(new Offset(0, 1, 2), "minecraft:villager", lexicon);
        reverie.setFigmentSenses(0, EnumSet.of(Sense.SOUND, Sense.SHADOW));
        assertEquals(10 + 5 + 5 + 30, UnveilCost.of(reverie));
    }
}
```

`SusceptibilityTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SusceptibilityTest {
    @Test
    void theTableIsTheSpecs() {
        assertEquals(1.3F, Susceptibility.of("minecraft:zombie"));
        assertEquals(1.3F, Susceptibility.of("minecraft:husk"));
        assertEquals(1.3F, Susceptibility.of("minecraft:drowned"));
        assertEquals(1.2F, Susceptibility.of("minecraft:villager"));
        assertEquals(1.1F, Susceptibility.of("minecraft:creeper"));
        assertEquals(1.0F, Susceptibility.of("minecraft:skeleton"));
        assertEquals(1.0F, Susceptibility.of("minecraft:player"));
        assertEquals(0.9F, Susceptibility.of("minecraft:spider"));
        assertEquals(0.5F, Susceptibility.of("minecraft:witch"));
        assertEquals(0.4F, Susceptibility.of("minecraft:enderman"));
        assertEquals(0.2F, Susceptibility.of("minecraft:wither"));
        assertEquals(0.2F, Susceptibility.of("minecraft:ender_dragon"));
        assertEquals(1.0F, Susceptibility.of("minecraft:cow"), "anything unlisted is ordinary");
    }

    @Test
    void theWardenIsBlindAndNothingElseIs() {
        assertTrue(Susceptibility.blind("minecraft:warden"));
        assertFalse(Susceptibility.blind("minecraft:zombie"));
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.BeliefTest" --tests "com.efkrdnz.magical.magic.mind.SusceptibilityTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `Contradiction` and `Belief`**

```java
package com.efkrdnz.magical.magic.mind;

/** The fast ways down: evidence that the thing is not there. */
public enum Contradiction {
    TOUCH(0.60F),
    PROJECTILE(0.35F),
    WITNESS(0.20F),
    HOLLOW_STRIKE(0.25F);

    private final float penalty;

    Contradiction(float penalty) {
        this.penalty = penalty;
    }

    public float penalty() {
        return penalty;
    }
}
```

```java
package com.efkrdnz.magical.magic.mind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Belief, viewer by element, for one live scene. It climbs slowly while a viewer perceives the
 * element, leaks away while it does not, and falls hard on contradiction; below {@link #SHATTER}
 * the viewer has seen through it and never sees that element again.
 */
public final class Belief {
    public static final float RATE = 0.02F;
    public static final float DECAY = 0.002F;
    public static final float CONVINCED = 0.5F;
    public static final float SHATTER = 0.1F;
    public static final float PATHING = 0.3F;
    public static final float SURE = 0.8F;
    private static final int FORECAST_CAP = 20 * 600;

    public record Row(int viewer, int element, float belief, boolean shattered) {}

    private final Map<Long, Float> values = new HashMap<>();
    private final Set<Long> shattered = new HashSet<>();

    private static long key(int viewer, int element) {
        return ((long) viewer << 32) | (element & 0xFFFFFFFFL);
    }

    public float get(int viewer, int element) {
        return values.getOrDefault(key(viewer, element), 0.0F);
    }

    public boolean convinced(int viewer, int element) {
        return get(viewer, element) >= CONVINCED;
    }

    public boolean shattered(int viewer, int element) {
        return shattered.contains(key(viewer, element));
    }

    public float gain(int viewer, int element, float p, float senses, float susceptibility, float novelty) {
        long key = key(viewer, element);
        if (shattered.contains(key)) {
            return 0.0F;
        }
        float b = values.getOrDefault(key, 0.0F);
        float next = Math.min(1.0F, b + RATE * p * senses * susceptibility * novelty * (1.0F - b));
        if (next > 0.0F) {
            values.put(key, next);
        }
        return next;
    }

    public void decay(int viewer, int element) {
        long key = key(viewer, element);
        Float b = values.get(key);
        if (b == null) {
            return;
        }
        float next = b - DECAY;
        if (next <= 0.0F) {
            values.remove(key);
        } else {
            values.put(key, next);
        }
    }

    /** True when this contradiction is the one that shattered the element for this viewer. */
    public boolean contradict(int viewer, int element, Contradiction contradiction) {
        long key = key(viewer, element);
        Float b = values.get(key);
        if (b == null || shattered.contains(key)) {
            return false;
        }
        float next = b - contradiction.penalty();
        if (next < SHATTER) {
            values.remove(key);
            shattered.add(key);
            return true;
        }
        values.put(key, next);
        return false;
    }

    public void forget(int viewer) {
        values.keySet().removeIf(key -> (int) (key >> 32) == viewer);
        shattered.removeIf(key -> (int) (key >> 32) == viewer);
    }

    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        values.forEach((key, b) -> rows.add(new Row((int) (key >> 32), (int) (long) key, b, false)));
        for (long key : shattered) {
            rows.add(new Row((int) (key >> 32), (int) key, 0.0F, true));
        }
        return rows;
    }

    void set(int viewer, int element, float belief) {
        values.put(key(viewer, element), belief);
    }

    public static int ticksToReach(float target, float p, float senses, float susceptibility, float novelty) {
        float rate = RATE * p * senses * susceptibility * novelty;
        if (rate <= 0.0F) {
            return -1;
        }
        float b = 0.0F;
        for (int tick = 1; tick <= FORECAST_CAP; tick++) {
            b += rate * (1.0F - b);
            if (b >= target) {
                return tick;
            }
        }
        return -1;
    }
}
```

- [ ] **Step 4: Write `Susceptibility`, `Scepticism` and `UnveilCost`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.Map;

/** How readily a kind of mind believes; anything unlisted is ordinary. The wielder is 0 by the caller. */
public final class Susceptibility {
    public static final float ORDINARY = 1.0F;

    private static final Map<String, Float> TABLE = Map.ofEntries(
            Map.entry("minecraft:zombie", 1.3F),
            Map.entry("minecraft:husk", 1.3F),
            Map.entry("minecraft:drowned", 1.3F),
            Map.entry("minecraft:villager", 1.2F),
            Map.entry("minecraft:creeper", 1.1F),
            Map.entry("minecraft:skeleton", 1.0F),
            Map.entry("minecraft:player", 1.0F),
            Map.entry("minecraft:spider", 0.9F),
            Map.entry("minecraft:witch", 0.5F),
            Map.entry("minecraft:enderman", 0.4F),
            Map.entry("minecraft:wither", 0.2F),
            Map.entry("minecraft:ender_dragon", 0.2F),
            Map.entry("minecraft:elder_guardian", 0.2F));

    private Susceptibility() {}

    public static float of(String entityTypeId) {
        return TABLE.getOrDefault(entityTypeId, ORDINARY);
    }

    /** A blind mind believes only what it can hear. */
    public static boolean blind(String entityTypeId) {
        return "minecraft:warden".equals(entityTypeId);
    }
}
```

```java
package com.efkrdnz.magical.magic.mind;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** A viewer who has seen through a trick is slower to fall for its materials again, for a while. */
public final class Scepticism {
    public static final int MEMORY_TICKS = 6000;

    private record Memory(int times, long until) {}

    private final Map<String, Memory> memories = new HashMap<>();

    public void seenThrough(String viewer, Collection<String> impressions, long now) {
        for (String impression : impressions) {
            String key = viewer + "|" + impression;
            Memory memory = memories.get(key);
            int times = memory == null || memory.until() < now ? 1 : memory.times() + 1;
            memories.put(key, new Memory(times, now + MEMORY_TICKS));
        }
    }

    public float novelty(String viewer, Collection<String> impressions, long now) {
        float novelty = 1.0F;
        for (String impression : impressions) {
            Memory memory = memories.get(viewer + "|" + impression);
            if (memory != null && memory.until() >= now) {
                novelty = Math.min(novelty, (float) Math.pow(0.5, memory.times()));
            }
        }
        return novelty;
    }

    public void prune(long now) {
        memories.values().removeIf(memory -> memory.until() < now);
    }
}
```

```java
package com.efkrdnz.magical.magic.mind;

/** The mana an unveil asks, before the wielder's cost scale. */
public final class UnveilCost {
    public static final int BASE = 10;
    public static final int PER_BLOCK = 1;
    public static final int PER_FIGMENT = 5;
    public static final int PER_SENSE_LAYER = 10;

    private UnveilCost() {}

    public static int of(Reverie reverie) {
        return BASE + PER_BLOCK * reverie.blocks().size() + PER_FIGMENT * reverie.figments().size()
                + PER_SENSE_LAYER * reverie.senseLayers();
    }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.BeliefTest" --tests "com.efkrdnz.magical.magic.mind.SusceptibilityTest"`
Expected: PASS (9 + 2 tests).

If `theWorkedZombieIsCertainInAboutTwoPointSevenSeconds` reports 54 or 56, that is float order. Here is the closed form: `ln(0.2) / ln(1 - 0.02*0.82*1.375*1.3) = 54.09`, so the first tick at or past 0.8 is 55. Check the product order in `ticksToReach` against `gain`. Do not change the expectation.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/Contradiction.java src/main/java/com/efkrdnz/magical/magic/mind/Belief.java src/main/java/com/efkrdnz/magical/magic/mind/Susceptibility.java src/main/java/com/efkrdnz/magical/magic/mind/Scepticism.java src/main/java/com/efkrdnz/magical/magic/mind/UnveilCost.java src/test/java/com/efkrdnz/magical/magic/mind/BeliefTest.java src/test/java/com/efkrdnz/magical/magic/mind/SusceptibilityTest.java
git commit -m "feat: belief - gain, decay, contradiction, susceptibility, scepticism, unveil cost"
```

---

### Task 6: The Authority, its two skills, and every string of the stage

**Files:**
- Modify: `magic/AuthorityContent.java`:
  - an id beside `CAUSALITY` (line 16);
  - a definition after `AUTHORITY_OF_CAUSALITY` (line 41).
- Modify: `magic/MagicContent.java`:
  - two `register` lines after `CAUSAL_ANCHOR` (line 148);
  - `AUTHORITY_SKILLS` (line 257).
- Modify: `magic/cast/MagicCastContentKept.java`: register Daydream after the Suspend line (line 98).
- Modify: `src/main/resources/assets/magical/lang/en_us.json`
- Test: `magic/mind/MindAuthorityTest.java`

**Interfaces:**
- Produces:
  - `AuthorityContent.MIND` (`ResourceLocation`) and `AuthorityContent.AUTHORITY_OF_MIND`.
  - `MagicContent.DAYDREAM` and `MagicContent.UNVEIL`.
  - Every lang key used in later tasks: `mind.magical.term.<key>`, `mind.magical.stance.<name>`, `mind.magical.reaction.<name>`, `mind.magical.sense.<name>`, `mind.magical.brush.<name>`, `mind.magical.refusal.<name>` (all lower case), plus `screen.magical.playbill.*` and `message.magical.*`.

Daydream is a toggle that the client owns: `DaydreamInput` (Task 14) consumes the key before a cast packet is ever sent. The server registration is only a `holdHint`, so a press that somehow reaches the server says what the skill is rather than casting a generic burst. Unveil's handler is registered in Task 11 with the service it calls. Until then it falls through the generic path, which is harmless between tasks.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MindAuthorityTest {
    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theAuthorityGrantsDaydreamAndUnveilAndNothingElse() {
        assertEquals(List.of(MagicContent.DAYDREAM.id(), MagicContent.UNVEIL.id()),
                AuthorityContent.get(AuthorityContent.MIND).skillIds());
        assertTrue(MagicContent.AUTHORITY_SKILLS.contains(MagicContent.DAYDREAM.id()));
        assertTrue(MagicContent.AUTHORITY_SKILLS.contains(MagicContent.UNVEIL.id()));
        assertEquals(-6, MagicContent.DAYDREAM.tier());
        assertEquals(-6, MagicContent.UNVEIL.tier());
        assertTrue(AuthorityContent.commandIds().contains("authority_of_mind"));
    }

    @Test
    void everyStringTheStageDrawsIsInTheLanguageFile() throws IOException {
        String lang = readLang();
        List<String> keys = new ArrayList<>(List.of(
                "authority.magical.authority_of_mind", "authority.magical.authority_of_mind.desc",
                "skill.magical.daydream", "skill.magical.daydream.desc",
                "skill.magical.unveil", "skill.magical.unveil.desc",
                "message.magical.daydream_hold", "message.magical.unveil_empty",
                "message.magical.unveil_too_many", "message.magical.unveil_nowhere",
                "message.magical.gaze_learned", "message.magical.gaze_studied",
                "message.magical.reverie_saved", "message.magical.reverie_refused",
                "screen.magical.playbill", "screen.magical.playbill.slot",
                "screen.magical.playbill.plausibility", "screen.magical.playbill.senses",
                "screen.magical.playbill.certain_in", "screen.magical.playbill.never",
                "screen.magical.playbill.cost", "screen.magical.playbill.save",
                "screen.magical.playbill.done", "screen.magical.playbill.empty",
                "screen.magical.playbill.stance", "screen.magical.playbill.reaction",
                "screen.magical.playbill.cluster", "mind.magical.daydream.hint"));
        for (String term : List.of("unsupported", "context", "alien", "fidelity", "habitat", "like_kind", "unlike_kind", "size")) {
            keys.add("mind.magical.term." + term);
        }
        for (Stance stance : Stance.values()) {
            keys.add("mind.magical.stance." + stance.name().toLowerCase(Locale.ROOT));
        }
        for (Reaction reaction : Reaction.values()) {
            keys.add("mind.magical.reaction." + reaction.name().toLowerCase(Locale.ROOT));
        }
        for (Sense sense : Sense.values()) {
            keys.add("mind.magical.sense." + sense.name().toLowerCase(Locale.ROOT));
        }
        for (Reverie.Refusal refusal : Reverie.Refusal.values()) {
            if (refusal != Reverie.Refusal.NONE) {
                keys.add("mind.magical.refusal." + refusal.name().toLowerCase(Locale.ROOT));
            }
        }
        for (String brush : List.of("point", "line", "wall", "box")) {
            keys.add("mind.magical.brush." + brush);
        }
        List<String> missing = keys.stream().filter(key -> !lang.contains('"' + key + '"')).toList();
        assertTrue(missing.isEmpty(), "missing lang keys: " + missing);
    }

    private static String readLang() throws IOException {
        try (InputStream stream = MindAuthorityTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(stream, "could not find " + LANG_PATH + " on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.MindAuthorityTest"`
Expected: compilation FAIL, `AuthorityContent.MIND` and `MagicContent.DAYDREAM` do not exist.

- [ ] **Step 3: Register the skills in `MagicContent`**

After the `CAUSAL_ANCHOR` line (148):

```java
    public static final MagicSkillDefinition DAYDREAM = register("daydream", MagicSchool.ARCANE, MagicSkillType.BURST, -6, 0, 0.0F, 0.0F, 1.0F, 0, 0, 20, 0.0F, 0, 0xBDA4FF, MagicAttribute.ARCANE);
    public static final MagicSkillDefinition UNVEIL = register("unveil", MagicSchool.ARCANE, MagicSkillType.BURST, -6, 0, 0.0F, 0.0F, 1.0F, 10, 200, 20, 0.0F, 0, 0xD9C8FF, MagicAttribute.ARCANE);
```

Append to the `AUTHORITY_SKILLS` set. Its last line becomes:

```java
            CAUSAL_BOARD.id(), CAUSAL_ANCHOR.id(), DECREE.id(), RECOMPENSE.id(), SUSPEND.id(),
            DAYDREAM.id(), UNVEIL.id());
```

- [ ] **Step 4: Register the Authority in `AuthorityContent`**

Beside the other ids:

```java
    public static final ResourceLocation MIND = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "authority_of_mind");
```

After `AUTHORITY_OF_CAUSALITY`:

```java
    public static final AuthorityDefinition AUTHORITY_OF_MIND = register(
            MIND,
            0xBDA4FF,
            List.of(MagicContent.DAYDREAM.id(), MagicContent.UNVEIL.id()));
```

- [ ] **Step 5: Register Daydream's hint in `MagicCastContentKept`**

After the `SUSPEND` registration:

```java
        // The Authority of Mind. Daydream is a client toggle (DaydreamInput owns the key), so the
        // server only ever sees it if that input is bypassed; the hint says what the skill is.
        SkillCastRegistry.register(MagicContent.DAYDREAM,
                SkillCastRegistry.holdHint("message.magical.daydream_hold"));
```

- [ ] **Step 6: Add the strings**

In `en_us.json`, after the last `authority_of_causality` skill entry near line 2142, add:

```json
  "authority.magical.authority_of_mind": "Authority of Mind",
  "authority.magical.authority_of_mind.desc": "Reality is what enough minds agree on. Study the world, write a scene, and let everyone who sees it decide whether it is there.",
  "skill.magical.daydream": "Daydream",
  "skill.magical.daydream.desc": "Toggle. Draw a reverie into the air out of what you have studied. Your inventory key opens the Playbill.",
  "skill.magical.unveil": "Unveil",
  "skill.magical.unveil.desc": "Set your active reverie down where you are looking. It is exactly as real as the minds around it believe.",
```

Before the closing `}` of the file (append after the last `message.magical.one_blade_hold` line, adding its comma), add:

```json
  "message.magical.daydream_hold": "Daydream is a toggle - press it to start drawing.",
  "message.magical.unveil_empty": "Your active reverie is empty. Daydream to write one.",
  "message.magical.unveil_too_many": "Two scenes already stand. Let one fade first.",
  "message.magical.unveil_nowhere": "There is nothing there to set a scene on.",
  "message.magical.gaze_learned": "You can imagine %s now.",
  "message.magical.gaze_studied": "%s studied more closely: fidelity %s.",
  "message.magical.reverie_saved": "Reverie saved.",
  "message.magical.reverie_refused": "The reverie was refused: %s",
  "screen.magical.playbill": "Playbill",
  "screen.magical.playbill.slot": "Reverie %s",
  "screen.magical.playbill.plausibility": "Plausibility",
  "screen.magical.playbill.senses": "Senses",
  "screen.magical.playbill.certain_in": "Certain in",
  "screen.magical.playbill.never": "never",
  "screen.magical.playbill.cost": "Cost",
  "screen.magical.playbill.save": "Save",
  "screen.magical.playbill.done": "Done",
  "screen.magical.playbill.empty": "Nothing written. Daydream to draw a scene.",
  "screen.magical.playbill.stance": "Stance",
  "screen.magical.playbill.reaction": "Reaction",
  "screen.magical.playbill.cluster": "%s x%s",
  "mind.magical.daydream.hint": "Scroll: impression   Right: place   Left: erase   Alt+scroll: brush   %s: Playbill",
  "mind.magical.term.unsupported": "hanging in the air",
  "mind.magical.term.context": "matches what is nearby",
  "mind.magical.term.alien": "nothing like it nearby",
  "mind.magical.term.fidelity": "how well studied",
  "mind.magical.term.habitat": "out of its place",
  "mind.magical.term.like_kind": "acts like its kind",
  "mind.magical.term.unlike_kind": "acts unlike its kind",
  "mind.magical.term.size": "too much at once",
  "mind.magical.stance.idle": "Idle",
  "mind.magical.stance.wander": "Wander",
  "mind.magical.stance.guard": "Guard",
  "mind.magical.stance.follow": "Follow",
  "mind.magical.reaction.ignore": "Ignore",
  "mind.magical.reaction.approach": "Approach",
  "mind.magical.reaction.flee": "Flee",
  "mind.magical.reaction.stare": "Stare",
  "mind.magical.reaction.chase": "Chase",
  "mind.magical.sense.sound": "Sound",
  "mind.magical.sense.shadow": "Shadow",
  "mind.magical.sense.scent": "Scent",
  "mind.magical.brush.point": "Point",
  "mind.magical.brush.line": "Line",
  "mind.magical.brush.wall": "Wall",
  "mind.magical.brush.box": "Box",
  "mind.magical.refusal.full": "the budget is spent",
  "mind.magical.refusal.unknown": "you have never studied that",
  "mind.magical.refusal.occupied": "something is already there",
  "mind.magical.refusal.too_far": "too far from the anchor"
```

- [ ] **Step 7: Run the test and the existing authority tests**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.MindAuthorityTest" --tests "com.efkrdnz.magical.magic.AuthorityGrantTest"`
Expected: PASS. `AuthorityGrantTest` walks `AuthorityContent.all()`, so it now covers Mind too.

- [ ] **Step 8: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java src/main/java/com/efkrdnz/magical/magic/MagicContent.java src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java src/main/resources/assets/magical/lang/en_us.json src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java
git commit -m "feat: the Authority of Mind - Daydream and Unveil registered, every string of stage 1"
```

---

### Task 7: Gaze

**Files:**
- Create: `magic/mind/GazeTracker.java` (pure), `magic/mind/MindGazeService.java` (server), `client/mind/GazeEyeOverlay.java` (client)
- Modify: `client/hud/HudLayers.java:147` (`renderSelector` draws the eye)
- Test: `magic/mind/GazeTrackerTest.java`

**Interfaces:**
- Consumes: `Lexicon.gaze`, `Lexicon.fidelity`, `Impression` (Task 1); `PlayerMagicState.mind()` (Task 3); `AuthorityContent.MIND` (Task 6).
- Produces:
  - `GazeTracker`, with `BLOCK_TICKS = 40` and `CREATURE_TICKS = 60`:
    - `tick(String lookingAt, boolean still)`, returning the key completed this tick or null;
    - `progress()` (0..1) and `key()`.
  - `MindGazeService.keyOf(Entity, BlockState)` returns a String. It is shared by the server and the client.
  - `MindGazeService.displayName(String key)` returns a `Component`.
  - `GazeEyeOverlay.render(GuiGraphics, Minecraft)`.

The server decides what is learned. The client runs its own `GazeTracker` over `Minecraft.hitResult` only to draw the eye filling up, so no payload is needed. The two can disagree by a tick, which a progress mark can afford.

A gaze is completed once per `BLOCK_TICKS`/`CREATURE_TICKS` of unbroken, still looking. A wielder who keeps staring keeps studying, which is how fidelity 3 is reached. Players and non-living entities are never impressions. Task 10 adds figments to that exclusion.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GazeTrackerTest {
    private static final String STONE = "block:minecraft:stone";
    private static final String COW = "creature:minecraft:cow";

    @Test
    void aBlockTakesFortyStillTicksAndACreatureSixty() {
        GazeTracker tracker = new GazeTracker();
        for (int i = 1; i < GazeTracker.BLOCK_TICKS; i++) {
            assertNull(tracker.tick(STONE, true));
        }
        assertEquals(STONE, tracker.tick(STONE, true));
        assertEquals(0.0F, tracker.progress(), 1.0E-6F, "a finished gaze starts over");

        for (int i = 1; i < GazeTracker.CREATURE_TICKS; i++) {
            assertNull(tracker.tick(COW, true));
        }
        assertEquals(COW, tracker.tick(COW, true));
    }

    @Test
    void movingOrLookingAwayStartsItOver() {
        GazeTracker tracker = new GazeTracker();
        for (int i = 0; i < 30; i++) {
            tracker.tick(STONE, true);
        }
        assertEquals(0.75F, tracker.progress(), 1.0E-6F);
        tracker.tick(STONE, false);
        assertEquals(0.0F, tracker.progress(), 1.0E-6F);
        for (int i = 0; i < 30; i++) {
            tracker.tick(STONE, true);
        }
        tracker.tick(COW, true);
        assertEquals(COW, tracker.key());
        assertEquals(1.0F / GazeTracker.CREATURE_TICKS, tracker.progress(), 1.0E-6F);
        tracker.tick(null, true);
        assertNull(tracker.key());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.GazeTrackerTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `GazeTracker`**

```java
package com.efkrdnz.magical.magic.mind;

/** Counts unbroken, still looking at one thing; a full count is one gaze. */
public final class GazeTracker {
    public static final int BLOCK_TICKS = 40;
    public static final int CREATURE_TICKS = 60;

    private String key;
    private int ticks;

    /** The key whose gaze completed on this tick, or null. */
    public String tick(String lookingAt, boolean still) {
        if (lookingAt == null) {
            key = null;
            ticks = 0;
            return null;
        }
        if (!lookingAt.equals(key)) {
            key = lookingAt;
            ticks = 0;
        }
        if (!still) {
            ticks = 0;
            return null;
        }
        ticks++;
        if (ticks >= need(key)) {
            ticks = 0;
            return key;
        }
        return null;
    }

    public String key() {
        return key;
    }

    public float progress() {
        return key == null ? 0.0F : (float) ticks / need(key);
    }

    public static int need(String key) {
        return key.startsWith("creature:") ? CREATURE_TICKS : BLOCK_TICKS;
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.GazeTrackerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Write `MindGazeService`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Learning the world by looking at it: a wielder of Mind who stands still and stares studies. */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MindGazeService {
    public static final double REACH = 16.0;
    public static final double AIM_TOLERANCE = 0.3;
    public static final double STILL_SQR = 0.0025;

    private static final Map<UUID, GazeTracker> TRACKERS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST = new HashMap<>();

    private MindGazeService() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(AuthorityContent.MIND)) {
            TRACKERS.remove(id);
            LAST.remove(id);
            return;
        }
        Vec3 now = player.position();
        Vec3 last = LAST.put(id, now);
        boolean still = last != null && last.distanceToSqr(now) < STILL_SQR;
        AimResolver.Result aim = AimResolver.resolve((ServerLevel) player.level(), player, REACH, AIM_TOLERANCE, false);
        BlockState block = aim.hitBlock() ? player.level().getBlockState(aim.blockPos()) : null;
        String done = TRACKERS.computeIfAbsent(id, key -> new GazeTracker()).tick(keyOf(aim.entity(), block), still);
        if (done != null) {
            learn(player, state, done);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKERS.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
    }

    /** The impression key for what is under the crosshair, or null when it is nothing one can imagine. */
    public static String keyOf(Entity entity, BlockState block) {
        if (entity != null) {
            if (entity instanceof Player || !(entity instanceof LivingEntity)) {
                return null;
            }
            return Impression.creature(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()).key();
        }
        if (block != null && !block.isAir()) {
            return Impression.block(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString()).key();
        }
        return null;
    }

    static void learn(ServerPlayer player, PlayerMagicState state, String key) {
        Lexicon lexicon = state.mind().lexicon();
        int before = lexicon.fidelity(key);
        lexicon.gaze(key);
        int after = lexicon.fidelity(key);
        if (before == 0) {
            player.displayClientMessage(Component.translatable("message.magical.gaze_learned", displayName(key)), true);
        } else if (after > before) {
            player.displayClientMessage(Component.translatable("message.magical.gaze_studied", displayName(key), after), true);
        }
        state.sync(player);
    }

    public static Component displayName(String key) {
        Impression impression = Impression.parse(key);
        ResourceLocation id = impression == null ? null : ResourceLocation.tryParse(impression.id());
        if (id == null) {
            return Component.literal(key);
        }
        return impression.kind() == Impression.Kind.BLOCK
                ? BuiltInRegistries.BLOCK.getValue(id).getName()
                : BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription();
    }
}
```

`Registry.getValue(ResourceLocation)` is the 1.21.4 name; `get` returns a `Holder.Reference` since 1.21.2. If the compiler disagrees, check `build/moddev/artifacts/neoforge-21.4.157-sources.jar` for `net/minecraft/core/Registry.java` and use the method that returns `T`.

- [ ] **Step 6: Write `GazeEyeOverlay`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.mind.GazeTracker;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The wielder's eye filling as they study: the name of what they are looking at under the
 * crosshair, the fidelity they already have as pips, and a hairline that fills to the next gaze.
 * Frameless - shadowed text and a one-pixel line, nothing behind them.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class GazeEyeOverlay {
    private static final int LILAC = 0xFFBDA4FF;
    private static final int TRACK = 0x66FFFFFF;
    private static final int BAR_WIDTH = 32;
    private static final int BELOW_CROSSHAIR = 10;
    private static final GazeTracker TRACKER = new GazeTracker();
    private static Vec3 last;

    private GazeEyeOverlay() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !ClientMagicState.get().hasAuthority(AuthorityContent.MIND)) {
            TRACKER.tick(null, false);
            last = null;
            return;
        }
        Vec3 now = minecraft.player.position();
        boolean still = last != null && last.distanceToSqr(now) < MindGazeService.STILL_SQR;
        last = now;
        HitResult hit = minecraft.hitResult;
        Entity entity = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        BlockState block = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? minecraft.level.getBlockState(blockHit.getBlockPos()) : null;
        TRACKER.tick(MindGazeService.keyOf(entity, block), still);
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        String key = TRACKER.key();
        if (key == null || TRACKER.progress() <= 0.0F || minecraft.screen != null) {
            return;
        }
        int fidelity = ClientMagicState.get().mind().lexicon().fidelity(key);
        Component name = MindGazeService.displayName(key).copy()
                .append(Component.literal(" " + "◆".repeat(fidelity) + "◇".repeat(3 - fidelity)));
        int centreX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + BELOW_CROSSHAIR;
        graphics.drawString(minecraft.font, name, centreX - minecraft.font.width(name) / 2, y, LILAC, true);
        int barY = y + minecraft.font.lineHeight + 1;
        int left = centreX - BAR_WIDTH / 2;
        graphics.fill(left, barY, left + BAR_WIDTH, barY + 1, TRACK);
        graphics.fill(left, barY, left + Math.round(BAR_WIDTH * TRACKER.progress()), barY + 1, LILAC);
    }
}
```

Check the actual names before relying on them:
- `ClientMagicState.get()` returns the client's `PlayerMagicState`. Check `client/ClientMagicState.java`. If it wraps the state, reach `mind()` and `hasAuthority` through the wrapper's accessor.
- If `ClientMagicState` lives in another package, fix the import.

The overlay is under `client/mind`, not `client/hud`, so the `GuiGraphics.fill` rule for `client/hud` does not apply. The hairline is a mark, not a panel.

- [ ] **Step 7: Draw it**

In `HudLayers.renderSelector`, add a first line:

```java
        com.efkrdnz.magical.client.mind.GazeEyeOverlay.render(graphics, minecraft);
```

- [ ] **Step 8: Build**

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL. Every existing test is still green.

- [ ] **Step 9: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/GazeTracker.java src/main/java/com/efkrdnz/magical/magic/mind/MindGazeService.java src/main/java/com/efkrdnz/magical/client/mind/GazeEyeOverlay.java src/main/java/com/efkrdnz/magical/client/hud/HudLayers.java src/test/java/com/efkrdnz/magical/magic/mind/GazeTrackerTest.java
git commit -m "feat: gaze - a still, steady look teaches the lexicon"
```

---

### Task 8: Live scenes and the belief tick

**Files:**
- Create in `magic/mind/`: `LevelMindWorld.java`, `LiveScene.java`, `MindService.java`, `MindGameTests.java` (game test, in `src/main`)

**Interfaces:**
- Consumes: everything in Tasks 1–5.
- Produces:
  - `LevelMindWorld(Level level)` implements `MindWorld`. It takes a plain `Level`, so the client's forecast (Task 15) uses the same class.
  - `LiveScene`, with `LIFE_TICKS = 1200`, `REREAD_TICKS = 100`, `enum Kind { CLUSTER, FIGMENT }`, and `record Element(int index, Kind kind, List<BlockPos> cells, List<String> blockIds, Set<String> impressions, Set<Sense> senses, BlockPos figmentAt, Figment figment, AABB box)`. Methods:
    - `id()`, `owner()`, `dimension()`, `anchor()`, `turns()`, `bornAt()`;
    - `elements()`, `belief()`, `plausibility(int)`, `lexicon()`;
    - `elementAt(BlockPos)`, returning −1 for none (cluster cells only);
    - `bounds()`, `expired(long now)`, `reread(MindWorld)`;
    - `static key(int a, int b)`, returning a long.
  - `MindService`, with `MAX_LIVE = 2`, `VIEW_RANGE = 32`, `HEARING_RANGE = 16`, `PLAYER_VIEW_CONE = 0.5`. Methods:
    - `unveilAt(ServerLevel, UUID owner, Reverie, BlockPos anchor, int turns, Lexicon)`, returning the `LiveScene` or null when refused;
    - `scenesOf(UUID)` and `allScenes()`, each returning a `List<LiveScene>`;
    - `end(LiveScene)` and `endAll(UUID)`;
    - `believes(Entity viewer, LiveScene, int element)`, returning a float;
    - package-private `tickScene(ServerLevel, LiveScene)`, `viewers(...)` and `perceives(...)`.

What the tick does, in order:
1. Every `REREAD_TICKS` it re-reads plausibility, because the world around a lie changes.
2. It gathers the viewers: every living `Mob` or `Player` within `VIEW_RANGE` of the scene's bounds, except the owner.
3. It checks touches. A viewer whose box **enters** a cluster's cells contradicts it with `TOUCH`. Every other viewer who perceives that element contradicts it with `WITNESS`. Entry is edge-triggered through `LiveScene.inside`, so standing in a lie is one contradiction and not twenty a second, and a viewer inside a lie gains no belief in it.
4. It checks projectiles. A projectile whose path this tick crosses a cluster's cells is a `PROJECTILE` contradiction for every viewer who perceives the element, once per projectile and element.
5. It updates belief. Every viewer who perceives an element gains on it, and every viewer who does not decays.
   - Perception is within `VIEW_RANGE`, with a clear `level.clip` from the eye to the element's centre.
   - A player also needs the element inside the forward cone (`dot >= 0.5`).
   - A blind viewer (the Warden) perceives only an element carrying Sound, within `HEARING_RANGE`, with no line of sight.
6. It clears out old viewers. Viewers no longer in range decay, and viewers who no longer exist are forgotten.

A shatter records scepticism for that viewer against every impression in the element. Scenes are held in memory only. They end on expiry, on the owner's logout, and on server stop.

- [ ] **Step 1: Write the failing game tests**

`MindGameTests.java` in `src/main/java/com/efkrdnz/magical/magic/mind/`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
```

`Belief.set` is package-private and these tests sit in `magic.mind`, which is why they live here and not in `gametest/`.

- [ ] **Step 2: Run the game tests and confirm they fail**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: compilation FAIL, `MindService` and `LiveScene` do not exist.

- [ ] **Step 3: Write `LevelMindWorld`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

/** {@link MindWorld} over a real level. An unloaded cell is air: a lie never loads a chunk. */
public record LevelMindWorld(Level level) implements MindWorld {
    private static final String AIR = "minecraft:air";

    @Override
    public boolean solid(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public String blockId(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) {
            return AIR;
        }
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }

    @Override
    public boolean openSkyDaylight(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) && level.isDay() && level.canSeeSky(pos);
    }

    @Override
    public boolean water(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER);
    }
}
```

- [ ] **Step 4: Write `LiveScene`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One reverie set down in the world. Its elements are its clusters (in {@link Reverie#clusters()}
 * order) followed by its figments, and an element's index is its address in the belief ledger and
 * on the wire. Nothing here is saved.
 */
public final class LiveScene {
    public static final int LIFE_TICKS = 1200;
    public static final int REREAD_TICKS = 100;
    private static final double FIGMENT_HALF_WIDTH = 0.3;
    private static final double FIGMENT_HEIGHT = 1.95;

    public enum Kind { CLUSTER, FIGMENT }

    public record Element(int index, Kind kind, List<BlockPos> cells, List<String> blockIds, Set<String> impressions,
                          Set<Sense> senses, BlockPos figmentAt, Figment figment, AABB box) {}

    private final int id;
    private final UUID owner;
    private final ResourceKey<Level> dimension;
    private final BlockPos anchor;
    private final int turns;
    private final long bornAt;
    private final Lexicon lexicon;
    private final List<Element> elements = new ArrayList<>();
    private final Map<Long, Integer> cellIndex = new HashMap<>();
    private final Belief belief = new Belief();
    private final float[] plausibility;
    private final AABB bounds;
    final Set<Long> inside = new HashSet<>();
    final Set<Long> seenProjectiles = new HashSet<>();
    final Set<Integer> knownViewers = new LinkedHashSet<>();

    LiveScene(int id, UUID owner, ResourceKey<Level> dimension, Reverie reverie, BlockPos anchor, int turns,
              Lexicon lexicon, long bornAt) {
        this.id = id;
        this.owner = owner;
        this.dimension = dimension;
        this.anchor = anchor;
        this.turns = turns;
        this.bornAt = bornAt;
        this.lexicon = new Lexicon();
        this.lexicon.copyFrom(lexicon);
        for (List<ImaginedBlock> cluster : reverie.clusters()) {
            List<BlockPos> cells = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            Set<String> impressions = new LinkedHashSet<>();
            AABB box = null;
            for (ImaginedBlock block : cluster) {
                BlockPos cell = world(block.at());
                cells.add(cell);
                ids.add(block.blockId());
                impressions.add(Impression.block(block.blockId()).key());
                box = box == null ? new AABB(cell) : box.minmax(new AABB(cell));
                cellIndex.put(cell.asLong(), elements.size());
            }
            elements.add(new Element(elements.size(), Kind.CLUSTER, List.copyOf(cells), List.copyOf(ids),
                    Set.copyOf(impressions), cluster.get(0).senses(), null, null, box));
        }
        for (Figment figment : reverie.figments()) {
            BlockPos at = world(figment.at());
            AABB box = new AABB(at.getX() + 0.5 - FIGMENT_HALF_WIDTH, at.getY(), at.getZ() + 0.5 - FIGMENT_HALF_WIDTH,
                    at.getX() + 0.5 + FIGMENT_HALF_WIDTH, at.getY() + FIGMENT_HEIGHT, at.getZ() + 0.5 + FIGMENT_HALF_WIDTH);
            elements.add(new Element(elements.size(), Kind.FIGMENT, List.of(), List.of(),
                    Set.of(Impression.creature(figment.creatureId()).key()), figment.senses(), at, figment, box));
        }
        AABB all = elements.get(0).box();
        for (Element element : elements) {
            all = all.minmax(element.box());
        }
        this.bounds = all;
        this.plausibility = new float[elements.size()];
    }

    private BlockPos world(Offset offset) {
        Offset turned = offset.rotate(turns);
        return anchor.offset(turned.dx(), turned.dy(), turned.dz());
    }

    public void reread(MindWorld world) {
        int size = elements.stream().mapToInt(e -> e.kind() == Kind.CLUSTER ? e.cells().size() : 1).sum();
        for (Element element : elements) {
            Plausibility.Reading reading;
            if (element.kind() == Kind.CLUSTER) {
                List<Plausibility.Placed> placed = new ArrayList<>();
                for (int i = 0; i < element.cells().size(); i++) {
                    BlockPos cell = element.cells().get(i);
                    placed.add(new Plausibility.Placed(cell.getX(), cell.getY(), cell.getZ(), element.blockIds().get(i)));
                }
                reading = Plausibility.cluster(world, placed, lexicon, size);
            } else {
                BlockPos at = element.figmentAt();
                reading = Plausibility.figment(world, new Plausibility.Placed(at.getX(), at.getY(), at.getZ(),
                        element.figment().creatureId()), element.figment().script(), lexicon, size);
            }
            plausibility[element.index()] = reading.p();
        }
    }

    public static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    public int elementAt(BlockPos pos) {
        return cellIndex.getOrDefault(pos.asLong(), -1);
    }

    public boolean expired(long now) {
        return now - bornAt >= LIFE_TICKS;
    }

    public int id() { return id; }
    public UUID owner() { return owner; }
    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos anchor() { return anchor; }
    public int turns() { return turns; }
    public long bornAt() { return bornAt; }
    public Lexicon lexicon() { return lexicon; }
    public List<Element> elements() { return java.util.Collections.unmodifiableList(elements); }
    public Belief belief() { return belief; }
    public float plausibility(int element) { return plausibility[element]; }
    public AABB bounds() { return bounds; }
}
```

`unveilAt` refuses an empty reverie, so `elements.get(0)` always exists.

- [ ] **Step 5: Write `MindService`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every live scene, per wielder, and the tick that decides who believes what. The only class in
 * the Authority that knows what a level is, besides {@link LevelMindWorld}.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MindService {
    public static final int MAX_LIVE = 2;
    public static final double VIEW_RANGE = 32.0;
    public static final double HEARING_RANGE = 16.0;
    public static final double PLAYER_VIEW_CONE = 0.5;
    private static final int SCEPTICISM_PRUNE_TICKS = 1200;

    private static final Map<UUID, List<LiveScene>> SCENES = new LinkedHashMap<>();
    private static final Scepticism SCEPTICISM = new Scepticism();
    private static int nextId = 1;

    private MindService() {}

    public static LiveScene unveilAt(ServerLevel level, UUID owner, Reverie reverie, BlockPos anchor, int turns, Lexicon lexicon) {
        List<LiveScene> live = SCENES.computeIfAbsent(owner, key -> new ArrayList<>());
        if (live.size() >= MAX_LIVE || reverie.isEmpty()) {
            if (live.isEmpty()) {
                SCENES.remove(owner);
            }
            return null;
        }
        LiveScene scene = new LiveScene(nextId++, owner, level.dimension(), reverie.copy(), anchor, turns, lexicon,
                level.getGameTime());
        scene.reread(new LevelMindWorld(level));
        live.add(scene);
        return scene;
    }

    public static List<LiveScene> scenesOf(UUID owner) {
        return List.copyOf(SCENES.getOrDefault(owner, List.of()));
    }

    public static List<LiveScene> allScenes() {
        List<LiveScene> all = new ArrayList<>();
        SCENES.values().forEach(all::addAll);
        return all;
    }

    public static void end(LiveScene scene) {
        List<LiveScene> live = SCENES.get(scene.owner());
        if (live == null || !live.remove(scene)) {
            return;
        }
        if (live.isEmpty()) {
            SCENES.remove(scene.owner());
        }
    }

    public static void endAll(UUID owner) {
        scenesOf(owner).forEach(MindService::end);
    }

    /** How strongly this entity believes this element, zero when it has never seen it. */
    public static float believes(Entity viewer, LiveScene scene, int element) {
        return scene.belief().get(viewer.getId(), element);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (LiveScene scene : allScenes()) {
            ServerLevel level = server.getLevel(scene.dimension());
            if (level == null || scene.expired(level.getGameTime())) {
                end(scene);
                continue;
            }
            tickScene(level, scene);
        }
        if (server.getTickCount() % SCEPTICISM_PRUNE_TICKS == 0) {
            SCEPTICISM.prune(server.overworld().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        endAll(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SCENES.clear();
    }

    static void tickScene(ServerLevel level, LiveScene scene) {
        long now = level.getGameTime();
        if ((now - scene.bornAt()) % LiveScene.REREAD_TICKS == 0) {
            scene.reread(new LevelMindWorld(level));
        }
        List<LivingEntity> viewers = viewers(level, scene);
        touches(level, scene, viewers, now);
        projectiles(level, scene, viewers, now);
        Set<Integer> present = new HashSet<>();
        for (LivingEntity viewer : viewers) {
            present.add(viewer.getId());
            perceiveAll(level, scene, viewer, now);
        }
        for (Integer gone : List.copyOf(scene.knownViewers)) {
            if (present.contains(gone)) {
                continue;
            }
            if (level.getEntity(gone) == null) {
                scene.belief().forget(gone);
                scene.knownViewers.remove(gone);
            } else {
                for (LiveScene.Element element : scene.elements()) {
                    scene.belief().decay(gone, element.index());
                }
            }
        }
        scene.knownViewers.addAll(present);
    }

    static List<LivingEntity> viewers(ServerLevel level, LiveScene scene) {
        return level.getEntitiesOfClass(LivingEntity.class, scene.bounds().inflate(VIEW_RANGE),
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.getUUID().equals(scene.owner())
                        && (entity instanceof Mob || entity instanceof Player));
    }

    private static void perceiveAll(ServerLevel level, LiveScene scene, LivingEntity viewer, long now) {
        String type = typeId(viewer);
        float susceptibility = Susceptibility.of(type);
        boolean blind = Susceptibility.blind(type);
        int id = viewer.getId();
        for (LiveScene.Element element : scene.elements()) {
            int index = element.index();
            if (scene.belief().shattered(id, index) || scene.inside.contains(LiveScene.key(id, index))) {
                continue;
            }
            if (perceives(level, viewer, element, blind)) {
                float novelty = SCEPTICISM.novelty(viewer.getStringUUID(), element.impressions(), now);
                scene.belief().gain(id, index, scene.plausibility(index), Sense.multiplier(element.senses()),
                        susceptibility, novelty);
            } else {
                scene.belief().decay(id, index);
            }
        }
    }

    static boolean perceives(ServerLevel level, LivingEntity viewer, LiveScene.Element element, boolean blind) {
        Vec3 eye = viewer.getEyePosition();
        Vec3 centre = element.box().getCenter();
        double distance = eye.distanceTo(centre);
        if (blind) {
            return element.senses().contains(Sense.SOUND) && distance <= HEARING_RANGE;
        }
        if (distance > VIEW_RANGE) {
            return false;
        }
        if (viewer instanceof Player player
                && player.getViewVector(1.0F).dot(centre.subtract(eye).normalize()) < PLAYER_VIEW_CONE) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS || element.box().inflate(0.5).contains(hit.getLocation());
    }

    private static void touches(ServerLevel level, LiveScene scene, List<LivingEntity> viewers, long now) {
        for (LivingEntity viewer : viewers) {
            AABB body = viewer.getBoundingBox();
            for (LiveScene.Element element : scene.elements()) {
                if (element.kind() != LiveScene.Kind.CLUSTER) {
                    continue;
                }
                long key = LiveScene.key(viewer.getId(), element.index());
                if (!crosses(body, element)) {
                    scene.inside.remove(key);
                    continue;
                }
                if (!scene.inside.add(key)) {
                    continue;
                }
                contradict(scene, viewer, element, Contradiction.TOUCH, now);
                for (LivingEntity witness : viewers) {
                    if (witness != viewer && perceives(level, witness, element, Susceptibility.blind(typeId(witness)))) {
                        contradict(scene, witness, element, Contradiction.WITNESS, now);
                    }
                }
            }
        }
    }

    private static void projectiles(ServerLevel level, LiveScene scene, List<LivingEntity> viewers, long now) {
        for (LiveScene.Element element : scene.elements()) {
            if (element.kind() != LiveScene.Kind.CLUSTER) {
                continue;
            }
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, element.box().inflate(4.0))) {
                AABB path = new AABB(projectile.xo, projectile.yo, projectile.zo,
                        projectile.getX(), projectile.getY(), projectile.getZ()).inflate(0.1);
                if (!crosses(path, element)
                        || !scene.seenProjectiles.add(LiveScene.key(projectile.getId(), element.index()))) {
                    continue;
                }
                for (LivingEntity viewer : viewers) {
                    if (perceives(level, viewer, element, Susceptibility.blind(typeId(viewer)))) {
                        contradict(scene, viewer, element, Contradiction.PROJECTILE, now);
                    }
                }
            }
        }
    }

    private static boolean crosses(AABB box, LiveScene.Element element) {
        if (!box.intersects(element.box())) {
            return false;
        }
        for (BlockPos cell : element.cells()) {
            if (box.intersects(new AABB(cell))) {
                return true;
            }
        }
        return false;
    }

    static void contradict(LiveScene scene, LivingEntity viewer, LiveScene.Element element, Contradiction contradiction, long now) {
        if (scene.belief().contradict(viewer.getId(), element.index(), contradiction)) {
            SCEPTICISM.seenThrough(viewer.getStringUUID(), element.impressions(), now);
        }
    }

    static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
```

- [ ] **Step 6: Run the game tests and confirm they pass**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: the four `mind_*` tests pass, with no other test newly failing.

If `doubtIsContagious` finds the watcher at 0.5 or near it, the watcher never witnessed the touch: its clip from its eye to the column's centre is hitting the template. Move the watcher to `(3, 2, 1)` rather than loosening the bound.

- [ ] **Step 7: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/LevelMindWorld.java src/main/java/com/efkrdnz/magical/magic/mind/LiveScene.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/magic/mind/MindGameTests.java
git commit -m "feat: live scenes - who sees a lie, who walks through it, who watched"
```

---

### Task 9: The mixin - mobs path by what they believe

**Files:**
- Create: `magic/mind/MindPathing.java`, `mixin/WalkNodeEvaluatorMixin.java`, `src/main/resources/magical.mixins.json`, `magic/mind/MindPathGameTests.java` (game test)
- Modify: `src/main/templates/META-INF/neoforge.mods.toml:55-56` (uncomment `[[mixins]]`)
- Modify: `magic/mind/LiveScene.java` (an `elementAt(long)` overload), `magic/mind/MindService.java` (`scenesIn`, `anyLive`)

**Interfaces:**
- Consumes: `LiveScene.elementAt`, `LiveScene.belief`, `MindService` (Task 8); `Belief.PATHING` (Task 5).
- Produces:
  - `MindPathing.override(PathfindingContext, Mob, int x, int y, int z)`, returning a `PathType` or null when the lie has nothing to say.
  - `MindService.scenesIn(ResourceKey<Level>)`, returning a `List<LiveScene>`.
  - `MindService.anyLive()`, returning a boolean.
  - `LiveScene.elementAt(long packedPos)`.

Why `getPathType` is the right seam, verified in `neoforge-21.4.157-sources.jar`:
- `WalkNodeEvaluator.getPathTypeWithinMobBB` calls `this.getPathType(context, x, y, z)` for every cell the mob's box would occupy.
- Every node type comes through `getCachedPathType`, which calls `getPathTypeOfMob`, which calls `getPathTypeWithinMobBB`.
- The per-evaluator cache is rebuilt for each path search, so an answer that depends on belief is never stale for more than one search.
- `NodeEvaluator.mob` is `protected`, so a mixin that extends `NodeEvaluator` reads it directly.

The answers:
- **BLOCKED** for a cell holding an imagined block the mob believes at `>= Belief.PATHING`.
- **WALKABLE** for a cell that is really open (`context.getPathTypeFromState == OPEN`) and stands on an imagined block the mob believes.
- **null** (vanilla decides) otherwise. The first check, `MindService.anyLive()`, keeps the cost at one boolean when no lie stands anywhere.

- [ ] **Step 1: Write the failing game tests**

`MindPathGameTests.java` in `src/main/java/com/efkrdnz/magical/magic/mind/`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class MindPathGameTests {
    private static final String TEMPLATE = "unwaking_empty";
    private static final String STONE = "minecraft:stone";

    private MindPathGameTests() {}

    /** An imagined stone wall two high across x 0..3 of the template, leaving x 4 open. */
    private static Reverie wall() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = MindGameTests.knowing("block:" + STONE);
        for (int x = 0; x < 4; x++) {
            reverie.addBlock(new Offset(x, 0, 0), STONE, lexicon);
            reverie.addBlock(new Offset(x, 1, 0), STONE, lexicon);
        }
        return reverie;
    }

    private static Path pathAcross(GameTestHelper helper, Mob mob, BlockPos floor) {
        mob.setOnGround(true);
        return mob.getNavigation().createPath(floor.offset(0, 0, 4), 0);
    }

    private static boolean throughWall(Path path, BlockPos wallRow) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos node = path.getNode(i).asBlockPos();
            if (node.getZ() == wallRow.getZ() && node.getX() < wallRow.getX() + 4
                    && node.getY() >= wallRow.getY() && node.getY() <= wallRow.getY() + 1) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_path_1")
    public static void aMobThatBelievesAWallWalksRoundIt(GameTestHelper helper) {
        BlockPos floor = BlockPos.containing(onFloor(helper, new BlockPos(0, 2, 0)));
        BlockPos wallRow = floor.offset(0, 0, 2);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(1, 2, 0))));
        UUID owner = UUID.randomUUID();
        LiveScene scene = MindGameTests.unveil(helper, owner, wall(), wallRow);
        scene.belief().set(husk.getId(), 0, Belief.CONVINCED);
        Path path = pathAcross(helper, husk, floor.offset(1, 0, 0));
        MindService.endAll(owner);
        helper.assertTrue(path != null && path.canReach(), "no way round an imagined wall with a gap at x 4");
        helper.assertFalse(throughWall(path, wallRow), "the believer walked through the wall it believes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_path_2")
    public static void aMobThatDoesNotBelieveItWalksStraightThrough(GameTestHelper helper) {
        BlockPos floor = BlockPos.containing(onFloor(helper, new BlockPos(0, 2, 0)));
        BlockPos wallRow = floor.offset(0, 0, 2);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(1, 2, 0))));
        UUID owner = UUID.randomUUID();
        LiveScene scene = MindGameTests.unveil(helper, owner, wall(), wallRow);
        scene.belief().set(husk.getId(), 0, 0.2F);
        Path path = pathAcross(helper, husk, floor.offset(1, 0, 0));
        MindService.endAll(owner);
        helper.assertTrue(path != null && throughWall(path, wallRow), "a doubter should take the straight line");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_path_3")
    public static void aBelievedLidOverAPitDropsWhoeverTrustsIt(GameTestHelper helper) {
        // A stone floor one block up across the template, with a hole at (2, 0, 2) and a lid imagined over it.
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (x != 2 || z != 2) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
        }
        BlockPos hole = helper.absolutePos(new BlockPos(2, 0, 2));
        Reverie lid = new Reverie();
        lid.addBlock(new Offset(0, 0, 0), STONE, MindGameTests.knowing("block:" + STONE));
        UUID owner = UUID.randomUUID();
        LiveScene scene = MindGameTests.unveil(helper, owner, lid, hole);
        Mob husk = helper.spawn(EntityType.HUSK, new BlockPos(2, 1, 2));
        scene.belief().set(husk.getId(), 0, 0.6F);
        helper.assertTrue(MindPathing.override(new net.minecraft.world.level.pathfinder.PathfindingContext(helper.getLevel(), husk),
                        husk, hole.getX(), hole.getY() + 1, hole.getZ()) == net.minecraft.world.level.pathfinder.PathType.WALKABLE,
                "the cell over a believed lid is not walkable");
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(scene.belief().shattered(husk.getId(), 0), "the husk fell through its lid and still believes it");
            MindService.endAll(owner);
            helper.succeed();
        });
    }
}
```

- [ ] **Step 2: Run the game tests and confirm they fail**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: compilation FAIL, `MindPathing` does not exist.

- [ ] **Step 3: Add the lookups**

In `LiveScene`, beside `elementAt(BlockPos)`:

```java
    public int elementAt(long packedPos) {
        return cellIndex.getOrDefault(packedPos, -1);
    }
```

In `MindService`:

```java
    public static boolean anyLive() {
        return !SCENES.isEmpty();
    }

    public static List<LiveScene> scenesIn(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        List<LiveScene> here = new ArrayList<>();
        for (List<LiveScene> scenes : SCENES.values()) {
            for (LiveScene scene : scenes) {
                if (scene.dimension().equals(dimension)) {
                    here.add(scene);
                }
            }
        }
        return here;
    }
```

- [ ] **Step 4: Write `MindPathing`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;

/** What the pathfinder asks a mob's imagination, cell by cell. Server only. */
public final class MindPathing {
    private MindPathing() {}

    public static PathType override(PathfindingContext context, Mob mob, int x, int y, int z) {
        if (mob == null || mob.level().isClientSide() || !MindService.anyLive()) {
            return null;
        }
        long here = BlockPos.asLong(x, y, z);
        long below = BlockPos.asLong(x, y - 1, z);
        for (LiveScene scene : MindService.scenesIn(mob.level().dimension())) {
            int solid = scene.elementAt(here);
            if (solid >= 0 && scene.belief().get(mob.getId(), solid) >= Belief.PATHING) {
                return PathType.BLOCKED;
            }
            int floor = scene.elementAt(below);
            if (floor >= 0 && scene.belief().get(mob.getId(), floor) >= Belief.PATHING
                    && context.getPathTypeFromState(x, y, z) == PathType.OPEN) {
                return PathType.WALKABLE;
            }
        }
        return null;
    }
}
```

- [ ] **Step 5: Write the mixin and its config**

`src/main/java/com/efkrdnz/magical/mixin/WalkNodeEvaluatorMixin.java`:

```java
package com.efkrdnz.magical.mixin;

import com.efkrdnz.magical.magic.mind.MindPathing;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mod's one mixin. A walking mob asks this for the type of every cell its box would take; an
 * imagined block it believes answers first, so it routes round a wall that is not there and onto
 * a floor that is not there.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin extends NodeEvaluator {
    @Inject(method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("HEAD"), cancellable = true)
    private void magical$believedPathType(PathfindingContext context, int x, int y, int z,
                                          CallbackInfoReturnable<PathType> cir) {
        PathType believed = MindPathing.override(context, this.mob, x, y, z);
        if (believed != null) {
            cir.setReturnValue(believed);
        }
    }
}
```

`src/main/resources/magical.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.efkrdnz.magical.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": ["WalkNodeEvaluatorMixin"],
  "injectors": {
    "defaultRequire": 1
  }
}
```

In `src/main/templates/META-INF/neoforge.mods.toml`, uncomment the two lines under the mixins comment so they read:

```toml
[[mixins]]
config="${mod_id}.mixins.json"
```

The runtime is Mojmap, so there is no refmap and no `build.gradle` change. `defaultRequire: 1` makes a missed injection a load failure rather than a silent no-op.

- [ ] **Step 6: Run the game tests and confirm they pass**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: the three `mind_path_*` tests pass, and the `mind_*` tests still pass. The log shows the mixin config applied: search it for `magical.mixins.json`. If it reports `InvalidInjectionException`, compare the method descriptor against the sources jar before anything else.

- [ ] **Step 7: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/MindPathing.java src/main/java/com/efkrdnz/magical/mixin/WalkNodeEvaluatorMixin.java src/main/resources/magical.mixins.json src/main/templates/META-INF/neoforge.mods.toml src/main/java/com/efkrdnz/magical/magic/mind/LiveScene.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/magic/mind/MindPathGameTests.java
git commit -m "feat: the first mixin - a mob paths round the walls it believes and onto the floors"
```

---

### Task 10: Figments

**Files:**
- Create: `entity/mind/FigmentEntity.java`, `entity/mind/FigmentReactionGoal.java`, `magic/mind/FigmentHunts.java`, `magic/mind/MindMobEvents.java`, `magic/mind/FigmentGameTests.java` (game test)
- Modify:
  - `registry/MagicalEntities.java`: a `FIGMENT` holder beside `BLOOD_HARVEST`.
  - `registry/MagicalEntityEvents.java`: attributes.
  - `client/MagicalClientEvents.java:168`: bind a `NoopRenderer` for now; Task 12 replaces it.
  - `magic/mind/LiveScene.java`: the figment entity ids.
  - `magic/mind/MindService.java`: spawn on unveil, strikes, release, exclusions.
  - `magic/mind/MindGazeService.java`: a figment is never an impression.
- Test: `magic/mind/FigmentHuntsTest.java`

**Interfaces:**
- Consumes: `LiveScene`, `MindService` (Tasks 8 and 9); `Script`, `Stance`, `Reaction` (Task 2); `Belief.CONVINCED`.
- Produces:
  - `MagicalEntities.FIGMENT`.
  - `FigmentEntity`:
    - `spawn(ServerLevel, LiveScene, LiveScene.Element)` returns the `FigmentEntity`;
    - accessors `creatureId()`, `sceneId()`, `element()`, `owner()`;
    - `static BiPredicate<Integer, Integer> clientSees`, the client hook Task 12 sets.
  - `FigmentHunts`: `hunts(String hunter, String prey)`, `fears(String fearer, String feared)`, `huntsAny(String)`, `fearsAny(String)`.
  - `MindService` gains:
    - `scene(int id)`, returning the `LiveScene` or null;
    - `believes(Entity viewer, FigmentEntity)`, returning a float;
    - `figmentStruck(FigmentEntity, LivingEntity attacker)`;
    - `figmentStrikes(FigmentEntity, LivingEntity target)`.
  - `LiveScene.figmentEntity(int element)` returns an entity id or −1.

The figment is a real, tracked, non-saving mob, so vanilla targeting, fleeing and pathing all see it. Stage 1 draws a line under what it can do:
- `hurtServer` always returns false and turns the blow into a `TOUCH` contradiction for the attacker, with witnesses.
- It pushes nothing and is pushed by nothing.
- A `CHASE` figment that reaches a viewer below 0.5 lands a *hollow strike* (`HOLLOW_STRIKE`). A strike at a believer does nothing yet: phantom harm is stage 2.
- A figment discards itself the tick its scene is gone, so `MindService.end` never needs a level.

- [ ] **Step 1: Write the failing unit test**

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FigmentHuntsTest {
    @Test
    void theUndeadHuntVillagersAndGolemsHuntTheUndead() {
        assertTrue(FigmentHunts.hunts("minecraft:zombie", "minecraft:villager"));
        assertTrue(FigmentHunts.hunts("minecraft:husk", "minecraft:iron_golem"));
        assertTrue(FigmentHunts.hunts("minecraft:iron_golem", "minecraft:zombie"));
        assertFalse(FigmentHunts.hunts("minecraft:iron_golem", "minecraft:villager"));
        assertFalse(FigmentHunts.hunts("minecraft:cow", "minecraft:villager"));
        assertTrue(FigmentHunts.huntsAny("minecraft:skeleton"));
        assertFalse(FigmentHunts.huntsAny("minecraft:cow"));
    }

    @Test
    void creepersFearCatsAndSkeletonsFearWolves() {
        assertTrue(FigmentHunts.fears("minecraft:creeper", "minecraft:cat"));
        assertTrue(FigmentHunts.fears("minecraft:creeper", "minecraft:ocelot"));
        assertTrue(FigmentHunts.fears("minecraft:skeleton", "minecraft:wolf"));
        assertFalse(FigmentHunts.fears("minecraft:zombie", "minecraft:cat"));
        assertTrue(FigmentHunts.fearsAny("minecraft:creeper"));
        assertFalse(FigmentHunts.fearsAny("minecraft:zombie"));
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.FigmentHuntsTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `FigmentHunts`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.Map;
import java.util.Set;

/** Who goes after, and who runs from, a creature they believe is there. Vanilla's instincts, by id. */
public final class FigmentHunts {
    private static final Set<String> TOWNSFOLK = Set.of("minecraft:villager", "minecraft:iron_golem", "minecraft:wandering_trader");
    private static final Set<String> MONSTERS = Set.of("minecraft:zombie", "minecraft:husk", "minecraft:drowned",
            "minecraft:zombie_villager", "minecraft:skeleton", "minecraft:stray", "minecraft:spider",
            "minecraft:cave_spider", "minecraft:pillager", "minecraft:vindicator", "minecraft:witch");

    private static final Map<String, Set<String>> HUNTS = Map.ofEntries(
            Map.entry("minecraft:zombie", TOWNSFOLK),
            Map.entry("minecraft:husk", TOWNSFOLK),
            Map.entry("minecraft:drowned", TOWNSFOLK),
            Map.entry("minecraft:zombie_villager", TOWNSFOLK),
            Map.entry("minecraft:skeleton", Set.of("minecraft:iron_golem", "minecraft:wolf")),
            Map.entry("minecraft:stray", Set.of("minecraft:iron_golem", "minecraft:wolf")),
            Map.entry("minecraft:spider", Set.of("minecraft:iron_golem")),
            Map.entry("minecraft:pillager", TOWNSFOLK),
            Map.entry("minecraft:vindicator", TOWNSFOLK),
            Map.entry("minecraft:iron_golem", MONSTERS),
            Map.entry("minecraft:snow_golem", MONSTERS),
            Map.entry("minecraft:wolf", Set.of("minecraft:skeleton", "minecraft:stray", "minecraft:sheep", "minecraft:rabbit", "minecraft:fox")));

    private static final Map<String, Set<String>> FEARS = Map.of(
            "minecraft:creeper", Set.of("minecraft:cat", "minecraft:ocelot"),
            "minecraft:skeleton", Set.of("minecraft:wolf"),
            "minecraft:stray", Set.of("minecraft:wolf"));

    private FigmentHunts() {}

    public static boolean hunts(String hunter, String prey) {
        return HUNTS.getOrDefault(hunter, Set.of()).contains(prey);
    }

    public static boolean fears(String fearer, String feared) {
        return FEARS.getOrDefault(fearer, Set.of()).contains(feared);
    }

    public static boolean huntsAny(String hunter) {
        return HUNTS.containsKey(hunter);
    }

    public static boolean fearsAny(String fearer) {
        return FEARS.containsKey(fearer);
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.FigmentHuntsTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Write the failing game tests**

`FigmentGameTests.java` in `src/main/java/com/efkrdnz/magical/magic/mind/`:

```java
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
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(husk.getTarget() instanceof FigmentEntity, "the husk never went for the villager");
            scene.belief().set(husk.getId(), 0, 0.2F);
        });
        helper.runAtTickTime(44, () -> {
            helper.assertTrue(husk.getTarget() == null, "the husk still hunts a villager it no longer believes");
            MindService.endAll(owner);
        });
        helper.runAtTickTime(46, () -> {
            helper.assertTrue(figment(helper, scene) == null || !figment(helper, scene).isAlive(),
                    "the figment outlived its scene");
            helper.succeed();
        });
    }
}
```

- [ ] **Step 6: Run the game tests and confirm they fail**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: compilation FAIL, `FigmentEntity` does not exist.

- [ ] **Step 7: Register the entity type**

In `MagicalEntities`, after `BLOOD_HARVEST`:

```java
    /** A creature someone imagined: real to the server's AI, drawn only for the minds that believe it. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.mind.FigmentEntity>> FIGMENT = ENTITY_TYPES.register(
            "figment",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.mind.FigmentEntity>of(com.efkrdnz.magical.entity.mind.FigmentEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(2)
                    .noSave()
                    .noSummon()
                    .build(key("figment")));
```

In `MagicalEntityEvents.registerAttributes`:

```java
        event.put(MagicalEntities.FIGMENT.get(), com.efkrdnz.magical.entity.mind.FigmentEntity.createAttributes().build());
```

In `MagicalClientEvents.registerRenderers`, beside the `UNWAKING_COUNTER` NoopRenderer line (Task 12 replaces this with `FigmentRenderer`):

```java
        event.registerEntityRenderer(MagicalEntities.FIGMENT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
```

- [ ] **Step 8: Write `FigmentEntity`**

```java
package com.efkrdnz.magical.entity.mind;

import com.efkrdnz.magical.magic.mind.LiveScene;
import com.efkrdnz.magical.magic.mind.MindService;
import com.efkrdnz.magical.magic.mind.Script;
import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * A creature out of a reverie. The server's AI treats it as real - that is the point, a husk has to
 * be able to hunt it - but it cannot be hurt, pushes nothing, and each client draws it only for a
 * mind that believes it.
 */
public class FigmentEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> CREATURE = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENE = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(FigmentEntity.class, EntityDataSerializers.INT);
    private static final int GUARD_RADIUS = 3;
    private static final double FOLLOW_START_SQR = 16.0;
    private static final double FOLLOW_STOP_SQR = 4.0;

    /** Whether this client can see a figment (scene id, element); set by the client in Task 12. */
    public static volatile BiPredicate<Integer, Integer> clientSees = (scene, element) -> true;

    private UUID owner;

    public FigmentEntity(EntityType<? extends FigmentEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CREATURE, "minecraft:villager");
        builder.define(SCENE, -1);
        builder.define(ELEMENT, -1);
    }

    public static FigmentEntity spawn(ServerLevel level, LiveScene scene, LiveScene.Element element) {
        FigmentEntity figment = new FigmentEntity(MagicalEntities.FIGMENT.get(), level);
        BlockPos at = element.figmentAt();
        figment.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, scene.turns() * 90.0F, 0.0F);
        figment.entityData.set(CREATURE, element.figment().creatureId());
        figment.entityData.set(SCENE, scene.id());
        figment.entityData.set(ELEMENT, element.index());
        figment.owner = scene.owner();
        figment.install(element.figment().script(), at);
        level.addFreshEntity(figment);
        return figment;
    }

    private void install(Script script, BlockPos home) {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FigmentReactionGoal(this, script.reaction()));
        switch (script.stance()) {
            case WANDER -> goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
            case GUARD -> {
                restrictTo(home, GUARD_RADIUS);
                goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
            }
            case FOLLOW -> goalSelector.addGoal(3, new FollowOwnerGoal());
            case IDLE -> { }
        }
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    public String creatureId() {
        return entityData.get(CREATURE);
    }

    public int sceneId() {
        return entityData.get(SCENE);
    }

    public int element() {
        return entityData.get(ELEMENT);
    }

    public UUID owner() {
        return owner;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && MindService.scene(sceneId()) == null) {
            discard();
        }
    }

    /** Nothing lands on a thing that is not there; the blow is evidence instead. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            MindService.figmentStruck(this, attacker);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPickable() {
        return !level().isClientSide() || clientSees.test(sceneId(), element());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    private final class FollowOwnerGoal extends Goal {
        private Player leader;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            leader = owner == null ? null : level().getPlayerByUUID(owner);
            return leader != null && distanceToSqr(leader) > FOLLOW_START_SQR;
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null && leader.isAlive() && distanceToSqr(leader) > FOLLOW_STOP_SQR;
        }

        @Override
        public void tick() {
            getNavigation().moveTo(leader, 1.0);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }
}
```

- [ ] **Step 9: Write `FigmentReactionGoal`**

```java
package com.efkrdnz.magical.entity.mind;

import com.efkrdnz.magical.magic.mind.Belief;
import com.efkrdnz.magical.magic.mind.MindService;
import com.efkrdnz.magical.magic.mind.Reaction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;

/** A figment answering the nearest mind that believes it, the way its script says. */
public final class FigmentReactionGoal extends Goal {
    private static final double NOTICE = 12.0;
    private static final double APPROACH_STOP_SQR = 6.25;
    private static final double STRIKE_REACH_SQR = 2.25;
    private static final int STRIKE_COOLDOWN = 20;

    private final FigmentEntity figment;
    private final Reaction reaction;
    private LivingEntity viewer;
    private int cooldown;

    public FigmentReactionGoal(FigmentEntity figment, Reaction reaction) {
        this.figment = figment;
        this.reaction = reaction;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (reaction == Reaction.IGNORE) {
            return false;
        }
        viewer = nearestBeliever();
        return viewer != null;
    }

    @Override
    public boolean canContinueToUse() {
        return viewer != null && viewer.isAlive() && figment.distanceToSqr(viewer) < NOTICE * NOTICE * 2.25
                && MindService.believes(viewer, figment) >= Belief.CONVINCED;
    }

    @Override
    public void stop() {
        viewer = null;
        figment.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
        figment.getLookControl().setLookAt(viewer, 30.0F, 30.0F);
        double distance = figment.distanceToSqr(viewer);
        switch (reaction) {
            case APPROACH -> {
                if (distance > APPROACH_STOP_SQR) {
                    figment.getNavigation().moveTo(viewer, 0.9);
                } else {
                    figment.getNavigation().stop();
                }
            }
            case FLEE -> {
                if (figment.getNavigation().isDone()) {
                    Vec3 away = DefaultRandomPos.getPosAway(figment, 12, 7, viewer.position());
                    if (away != null) {
                        figment.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                    }
                }
            }
            case STARE -> figment.getNavigation().stop();
            case CHASE -> {
                figment.getNavigation().moveTo(viewer, 1.1);
                if (distance < STRIKE_REACH_SQR && cooldown == 0) {
                    figment.swing(InteractionHand.MAIN_HAND);
                    MindService.figmentStrikes(figment, viewer);
                    cooldown = STRIKE_COOLDOWN;
                }
            }
            case IGNORE -> { }
        }
    }

    private LivingEntity nearestBeliever() {
        return figment.level().getEntitiesOfClass(LivingEntity.class, figment.getBoundingBox().inflate(NOTICE),
                        entity -> entity != figment && !(entity instanceof FigmentEntity) && entity.isAlive()
                                && !entity.getUUID().equals(figment.owner())
                                && MindService.believes(entity, figment) >= Belief.CONVINCED)
                .stream().min(Comparator.comparingDouble(figment::distanceToSqr)).orElse(null);
    }
}
```

- [ ] **Step 10: Wire figments into the scene and the service**

In `LiveScene`, add a field and accessors:

```java
    final java.util.Map<Integer, Integer> figmentEntities = new java.util.HashMap<>();

    public int figmentEntity(int element) {
        return figmentEntities.getOrDefault(element, -1);
    }
```

In `MindService.unveilAt`, after `scene.reread(new LevelMindWorld(level));`:

```java
        for (LiveScene.Element element : scene.elements()) {
            if (element.kind() == LiveScene.Kind.FIGMENT) {
                com.efkrdnz.magical.entity.mind.FigmentEntity figment =
                        com.efkrdnz.magical.entity.mind.FigmentEntity.spawn(level, scene, element);
                scene.figmentEntities.put(element.index(), figment.getId());
            }
        }
```

Add these to `MindService`:

```java
    public static LiveScene scene(int id) {
        for (LiveScene scene : allScenes()) {
            if (scene.id() == id) {
                return scene;
            }
        }
        return null;
    }

    public static float believes(Entity viewer, com.efkrdnz.magical.entity.mind.FigmentEntity figment) {
        LiveScene scene = scene(figment.sceneId());
        return scene == null ? 0.0F : scene.belief().get(viewer.getId(), figment.element());
    }

    /** A blow through a figment: the striker learns, and so does everyone watching. */
    public static void figmentStruck(com.efkrdnz.magical.entity.mind.FigmentEntity figment, LivingEntity attacker) {
        LiveScene scene = scene(figment.sceneId());
        if (scene == null || !(figment.level() instanceof ServerLevel level)) {
            return;
        }
        LiveScene.Element element = scene.elements().get(figment.element());
        long now = level.getGameTime();
        contradict(scene, attacker, element, Contradiction.TOUCH, now);
        for (LivingEntity witness : viewers(level, scene)) {
            if (witness != attacker && perceives(level, witness, element, Susceptibility.blind(typeId(witness)))) {
                contradict(scene, witness, element, Contradiction.WITNESS, now);
            }
        }
    }

    /** A figment lands a blow. On a doubter nothing happens, and that is the evidence. */
    public static void figmentStrikes(com.efkrdnz.magical.entity.mind.FigmentEntity figment, LivingEntity target) {
        LiveScene scene = scene(figment.sceneId());
        if (scene == null || !(figment.level() instanceof ServerLevel level)) {
            return;
        }
        if (scene.belief().get(target.getId(), figment.element()) < Belief.CONVINCED) {
            contradict(scene, target, scene.elements().get(figment.element()), Contradiction.HOLLOW_STRIKE, level.getGameTime());
        }
    }
```

In `MindService.viewers`, exclude figments. The predicate becomes:

```java
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.getUUID().equals(scene.owner())
                        && !(entity instanceof com.efkrdnz.magical.entity.mind.FigmentEntity)
                        && (entity instanceof Mob || entity instanceof Player));
```

In `MindService.tickScene`, inside the `for (LivingEntity viewer : viewers)` loop after `perceiveAll(...)`, release a target the viewer no longer believes in:

```java
            if (viewer instanceof Mob mob && mob.getTarget() instanceof com.efkrdnz.magical.entity.mind.FigmentEntity figment
                    && figment.sceneId() == scene.id()
                    && scene.belief().get(mob.getId(), figment.element()) < Belief.CONVINCED) {
                mob.setTarget(null);
            }
```

In `MindGazeService.keyOf`, change the entity guard to:

```java
            if (entity instanceof Player || entity instanceof com.efkrdnz.magical.entity.mind.FigmentEntity
                    || !(entity instanceof LivingEntity)) {
                return null;
            }
```

- [ ] **Step 11: Write `MindMobEvents`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Teaches every mob that would hunt or flee a creature to hunt or flee a figment of one - but only
 * a figment it believes. Added once, as the mob joins a level.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MindMobEvents {
    private static final int TARGET_PRIORITY = 2;
    private static final int AVOID_PRIORITY = 1;
    private static final float AVOID_DISTANCE = 8.0F;

    private MindMobEvents() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || mob instanceof FigmentEntity) {
            return;
        }
        String type = MindService.typeId(mob);
        if (FigmentHunts.huntsAny(type)) {
            mob.targetSelector.addGoal(TARGET_PRIORITY, new NearestAttackableTargetGoal<>(mob, FigmentEntity.class, true,
                    (target, level) -> target instanceof FigmentEntity figment
                            && FigmentHunts.hunts(type, figment.creatureId())
                            && MindService.believes(mob, figment) >= Belief.CONVINCED));
        }
        if (FigmentHunts.fearsAny(type) && mob instanceof PathfinderMob runner) {
            runner.goalSelector.addGoal(AVOID_PRIORITY, new AvoidEntityGoal<>(runner, FigmentEntity.class, AVOID_DISTANCE, 1.0, 1.2,
                    entity -> entity instanceof FigmentEntity figment
                            && FigmentHunts.fears(type, figment.creatureId())
                            && MindService.believes(runner, figment) >= Belief.CONVINCED));
        }
    }
}
```

`MindService.typeId` is package-private, and `MindMobEvents` is in the same package. The `Selector` lambda's `(target, level)` shape is the 1.21.4 `TargetingConditions.Selector`, verified in the sources jar.

- [ ] **Step 12: Run everything and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.*"` then `.\gradlew runGameTestServer --console=plain`
Expected: unit tests PASS. The three `mind_figment_*` tests pass alongside the earlier `mind_*` and `mind_path_*` tests.

- [ ] **Step 13: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/entity/mind/FigmentEntity.java src/main/java/com/efkrdnz/magical/entity/mind/FigmentReactionGoal.java src/main/java/com/efkrdnz/magical/magic/mind/FigmentHunts.java src/main/java/com/efkrdnz/magical/magic/mind/MindMobEvents.java src/main/java/com/efkrdnz/magical/magic/mind/FigmentGameTests.java src/main/java/com/efkrdnz/magical/magic/mind/LiveScene.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/magic/mind/MindGazeService.java src/main/java/com/efkrdnz/magical/registry/MagicalEntities.java src/main/java/com/efkrdnz/magical/registry/MagicalEntityEvents.java src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java src/test/java/com/efkrdnz/magical/magic/mind/FigmentHuntsTest.java
git commit -m "feat: figments - creatures only believers see, hunted and fled by what believes them"
```

---

### Task 11: Unveil, the presets and `/magical mind`

**Files:**
- Create: `magic/mind/MindPresets.java`, `magic/mind/MindCommands.java`, `magic/mind/UnveilGameTests.java` (game test)
- Modify:
  - `magic/mind/MindService.java`: `unveil`, `payFor`.
  - `magic/cast/MagicCastContentKept.java`: register Unveil after the Daydream line from Task 6.
  - `registry/MagicalCommands.java:183`: `.then(MindCommands.build())` beside `causality`.
- Test: `magic/mind/MindPresetsTest.java`

**Interfaces:**
- Consumes: `MindService.unveilAt` (Task 8); `UnveilCost` (Task 5); `MagicContent.UNVEIL` (Task 6); `MindState` (Task 3).
- Produces:
  - `MindService.unveil(ServerPlayer, PlayerMagicState)`, returning true when a scene went up.
  - `MindService.UNVEIL_REACH = 24`.
  - `MindPresets.named(String)`, returning the `Reverie` or null, and `MindPresets.NAMES`, a `List<String>`.
  - `MindPresets.impressions(Reverie)`, returning a `Set<String>`.
  - `MindCommands.build()`, returning a `LiteralArgumentBuilder<CommandSourceStack>`.

**How Unveil places the scene:**
- **Anchor:** the cell in front of the block face the wielder is looking at, within `UNVEIL_REACH`. When the crosshair meets no block within reach, the press is refused and nothing is billed.
- **Rotation:** `player.getDirection().get2DDataValue() - reverie.facing()` clockwise quarter turns. A scene written facing south and unveiled facing west turns once, which is exactly `Offset.rotate(1)`.

**Billing** is `UnveilCost.of(reverie)` scaled by `stats.costScale()`, with the definition's 200-tick cooldown. Unveil is `selfManaged`, so `castViaRegistry` never billed it (the Causality lesson). `payFor` runs only after every refusal has been checked.

Commands, all under `/magical mind`:

| Command | Effect |
|---|---|
| `lexicon all` | learns every block and every non-MISC entity type at 20 gazes |
| `lexicon block <id> [gazes]` | learns one block, 5 gazes by default |
| `lexicon creature <id> [gazes]` | learns one creature, 5 gazes by default |
| `preset <slot 1-3> <pit\|wall\|cat>` | writes a worked reverie, learns what it needs, and faces it the way the player faces |
| `slot <1-3>` | picks the active reverie |
| `unveil` | clears Unveil's cooldown, then presses it: real mana, real placement |
| `show [slot]` | reads a reverie back |
| `belief` | reads every live scene's matrix back |
| `end` | ends the player's scenes |

- [ ] **Step 1: Write the failing unit test**

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MindPresetsTest {
    @Test
    void everyPresetIsAWholeReverieWithinTheBudgetOfWhatItNeeds() {
        for (String name : MindPresets.NAMES) {
            Reverie reverie = MindPresets.named(name);
            assertNotNull(reverie, name);
            assertFalse(reverie.isEmpty(), name);
            Lexicon lexicon = new Lexicon();
            MindPresets.impressions(reverie).forEach(key -> lexicon.learn(key, 5));
            assertTrue(reverie.size() <= lexicon.budget(), name + " is over the budget its own impressions buy");
        }
        assertNull(MindPresets.named("nonsense"));
    }

    @Test
    void theCatIsAGuardWithAVoice() {
        Reverie cat = MindPresets.named("cat");
        assertEquals(Set.of("creature:minecraft:cat"), MindPresets.impressions(cat));
        assertEquals(Stance.GUARD, cat.figments().get(0).script().stance());
        assertTrue(cat.figments().get(0).senses().contains(Sense.SOUND));
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.MindPresetsTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `MindPresets`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Three worked reveries from the design, for captures and for a new wielder to read: offsets are
 * from the air cell in front of the face the wielder aims at, written facing south.
 */
public final class MindPresets {
    public static final List<String> NAMES = List.of("pit", "wall", "cat");

    private MindPresets() {}

    public static Reverie named(String name) {
        return switch (name) {
            case "pit" -> pit();
            case "wall" -> wall();
            case "cat" -> cat();
            default -> null;
        };
    }

    /** A lid of grass level with the ground, for a two-deep pit whose floor is aimed at. */
    private static Reverie pit() {
        Reverie reverie = start("Pit");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                reverie.put(new ImaginedBlock(new Offset(x, 1, z), "minecraft:grass_block", Set.of()));
            }
        }
        return reverie;
    }

    /** Five wide, three tall, two blocks ahead, in cobblestone, with a shadow. */
    private static Reverie wall() {
        Reverie reverie = start("Wall");
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y < 3; y++) {
                reverie.put(new ImaginedBlock(new Offset(x, y, 2), "minecraft:cobblestone", EnumSet.of(Sense.SHADOW)));
            }
        }
        return reverie;
    }

    /** A cat on Guard, staring, with its voice on: the creeper stopper. */
    private static Reverie cat() {
        Reverie reverie = start("Cat");
        reverie.put(new Figment(new Offset(0, 0, 1), "minecraft:cat", new Script(Stance.GUARD, Reaction.STARE),
                EnumSet.of(Sense.SOUND)));
        return reverie;
    }

    private static Reverie start(String name) {
        Reverie reverie = new Reverie();
        reverie.setName(name);
        reverie.setFacing(0);
        return reverie;
    }

    public static Set<String> impressions(Reverie reverie) {
        Set<String> keys = new LinkedHashSet<>();
        reverie.blocks().forEach(block -> keys.add(Impression.block(block.blockId()).key()));
        reverie.figments().forEach(figment -> keys.add(Impression.creature(figment.creatureId()).key()));
        return keys;
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.MindPresetsTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Write the failing game test**

`UnveilGameTests.java` in `src/main/java/com/efkrdnz/magical/magic/mind/`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class UnveilGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private UnveilGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_unveil_1")
    public static void anUnveilIsPaidForAndAnEmptyOneIsNot(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 0), "mind-unveil-test");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        // Face the floor two blocks ahead, so the crosshair meets a block inside the template.
        player.setYRot(0.0F);
        player.setXRot(45.0F);

        state.mind().setActiveSlot(0);
        state.mind().active().clear();
        int before = state.mana();
        helper.assertFalse(MindService.unveil(player, state), "an empty reverie went up");
        helper.assertTrue(state.mana() == before, "an empty reverie was billed");
        helper.assertFalse(state.isSkillOnCooldown(MagicContent.UNVEIL.id()), "an empty reverie started the clock");

        Reverie cat = MindPresets.named("cat");
        MindPresets.impressions(cat).forEach(key -> state.mind().lexicon().learn(key, 5));
        state.mind().active().copyFrom(cat);
        helper.assertTrue(MindService.unveil(player, state), "the cat was refused");
        helper.assertTrue(state.mana() < before, "the cat went up for free");
        helper.assertTrue(state.isSkillOnCooldown(MagicContent.UNVEIL.id()), "Unveil started no cooldown");
        helper.assertTrue(MindService.scenesOf(player.getUUID()).size() == 1, "no scene is standing");
        MindService.endAll(player.getUUID());
        helper.succeed();
    }
}
```

`PlayerMagicState.mana()` (an int) and `setAuthority(ResourceLocation)` are the real names, checked at plan time.

- [ ] **Step 6: Run it and confirm it fails**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: compilation FAIL, `MindService.unveil` does not exist.

- [ ] **Step 7: Write `unveil` and `payFor` in `MindService`**

```java
    public static final double UNVEIL_REACH = 24.0;

    public static boolean unveil(ServerPlayer player, PlayerMagicState state) {
        if (player == null) {
            return false;
        }
        Reverie reverie = state.mind().active();
        if (reverie.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.unveil_empty"), true);
            return false;
        }
        if (scenesOf(player.getUUID()).size() >= MAX_LIVE) {
            player.displayClientMessage(Component.translatable("message.magical.unveil_too_many"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        AimResolver.Result aim = AimResolver.resolve(level, player, UNVEIL_REACH, 0.0, false);
        if (!aim.hitBlock()) {
            player.displayClientMessage(Component.translatable("message.magical.unveil_nowhere"), true);
            return false;
        }
        if (!payFor(player, state, MagicContent.UNVEIL, UnveilCost.of(reverie))) {
            return false;
        }
        BlockPos anchor = aim.blockPos().relative(aim.face());
        int turns = player.getDirection().get2DDataValue() - reverie.facing();
        unveilAt(level, player.getUUID(), reverie, anchor, turns, state.mind().lexicon());
        level.playSound(null, anchor, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.3F);
        state.sync(player);
        return true;
    }

    /** Bills a self-managed press by hand: the cast pipeline returns before it charges anything. */
    private static boolean payFor(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill, int baseMana) {
        if (state.isSkillOnCooldown(skill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
        int mana = Math.max(1, Math.round(baseMana * stats.costScale()));
        if (!MagicSinService.spendManaForSkill(player, state, mana)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        state.setSkillCooldown(skill.id(), stats.cooldownTicks());
        return true;
    }
```

Add the imports:
- `com.efkrdnz.magical.magic.MagicContent`, `MagicSinService`, `MagicSkillDefinition`, `MagicSkillResolvedStats`, `PlayerMagicState`;
- `com.efkrdnz.magical.magic.cast.AimResolver`;
- `net.minecraft.network.chat.Component`, `net.minecraft.server.level.ServerPlayer`, `net.minecraft.sounds.SoundEvents`, `net.minecraft.sounds.SoundSource`.

Match the packages `CausalityService` imports these from.

- [ ] **Step 8: Register Unveil**

In `MagicCastContentKept`, after the Daydream line:

```java
        SkillCastRegistry.register(MagicContent.UNVEIL, SkillCastRegistry.selfManaged(ctx ->
                com.efkrdnz.magical.magic.mind.MindService.unveil(ctx.player(), ctx.state())));
```

- [ ] **Step 9: Write `MindCommands`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;

import java.util.List;
import java.util.function.ToIntFunction;

/** {@code /magical mind ...}: forces every state of the Authority for captures and tests. */
public final class MindCommands {
    private static final int FULL_STUDY = 20;
    private static final int DEFAULT_STUDY = 5;

    private MindCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mind")
                .then(Commands.literal("lexicon")
                        .then(Commands.literal("all").executes(c -> run(c, MindCommands::learnAll)))
                        .then(learn("block", Impression.Kind.BLOCK))
                        .then(learn("creature", Impression.Kind.CREATURE)))
                .then(Commands.literal("preset")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, MindState.SLOTS))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            MindPresets.NAMES.forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(c -> run(c, player -> preset(player,
                                                IntegerArgumentType.getInteger(c, "slot") - 1,
                                                StringArgumentType.getString(c, "name")))))))
                .then(Commands.literal("slot")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, MindState.SLOTS))
                                .executes(c -> run(c, player -> {
                                    PlayerMagicState state = state(player);
                                    state.mind().setActiveSlot(IntegerArgumentType.getInteger(c, "slot") - 1);
                                    state.sync(player);
                                    return 1;
                                }))))
                .then(Commands.literal("unveil").executes(c -> run(c, player -> {
                    PlayerMagicState state = state(player);
                    state.setSkillCooldown(MagicContent.UNVEIL.id(), 0);
                    return MindService.unveil(player, state) ? 1 : 0;
                })))
                .then(Commands.literal("show")
                        .executes(c -> run(c, player -> show(player, state(player).mind().activeSlot())))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, MindState.SLOTS))
                                .executes(c -> run(c, player -> show(player, IntegerArgumentType.getInteger(c, "slot") - 1)))))
                .then(Commands.literal("belief").executes(c -> run(c, MindCommands::belief)))
                .then(Commands.literal("end").executes(c -> run(c, player -> {
                    MindService.endAll(player.getUUID());
                    return 1;
                })));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> learn(String literal, Impression.Kind kind) {
        return Commands.literal(literal)
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(c -> run(c, player -> learnOne(player, kind, ResourceLocationArgument.getId(c, "id").toString(), DEFAULT_STUDY)))
                        .then(Commands.argument("gazes", IntegerArgumentType.integer(1, 100))
                                .executes(c -> run(c, player -> learnOne(player, kind,
                                        ResourceLocationArgument.getId(c, "id").toString(), IntegerArgumentType.getInteger(c, "gazes"))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, ToIntFunction<ServerPlayer> action) throws CommandSyntaxException {
        return action.applyAsInt(context.getSource().getPlayerOrException());
    }

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    private static int learnAll(ServerPlayer player) {
        PlayerMagicState state = state(player);
        BuiltInRegistries.BLOCK.keySet().forEach(id -> state.mind().lexicon().learn(Impression.block(id.toString()).key(), FULL_STUDY));
        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(entry -> {
            if (entry.getValue().getCategory() != MobCategory.MISC) {
                state.mind().lexicon().learn(Impression.creature(entry.getKey().location().toString()).key(), FULL_STUDY);
            }
        });
        state.sync(player);
        return state.mind().lexicon().size();
    }

    private static int learnOne(ServerPlayer player, Impression.Kind kind, String id, int gazes) {
        PlayerMagicState state = state(player);
        Impression impression = new Impression(kind, id);
        if (Impression.parse(impression.key()) == null) {
            return 0;
        }
        state.mind().lexicon().learn(impression.key(), gazes);
        state.sync(player);
        return 1;
    }

    private static int preset(ServerPlayer player, int slot, String name) {
        Reverie preset = MindPresets.named(name);
        if (preset == null) {
            return 0;
        }
        PlayerMagicState state = state(player);
        MindPresets.impressions(preset).forEach(key -> state.mind().lexicon().learn(key, DEFAULT_STUDY));
        state.mind().reverie(slot).copyFrom(preset);
        state.mind().setActiveSlot(slot);
        state.sync(player);
        return 1;
    }

    private static int show(ServerPlayer player, int slot) {
        Reverie reverie = state(player).mind().reverie(slot);
        player.sendSystemMessage(Component.literal("Reverie " + (slot + 1) + " '" + reverie.name() + "' facing "
                + reverie.facing() + ": " + reverie.blocks().size() + " blocks in " + reverie.clusters().size()
                + " clusters, " + reverie.figments().size() + " figments, " + UnveilCost.of(reverie) + " mana"));
        for (Figment figment : reverie.figments()) {
            player.sendSystemMessage(Component.literal("  " + figment.creatureId() + " " + figment.script() + " " + figment.senses()));
        }
        return 1;
    }

    private static int belief(ServerPlayer player) {
        List<LiveScene> scenes = MindService.scenesOf(player.getUUID());
        for (LiveScene scene : scenes) {
            player.sendSystemMessage(Component.literal("Scene " + scene.id() + " at " + scene.anchor().toShortString()));
            for (LiveScene.Element element : scene.elements()) {
                player.sendSystemMessage(Component.literal("  " + element.index() + " " + element.kind()
                        + " p=" + String.format("%.2f", scene.plausibility(element.index()))));
            }
            for (Belief.Row row : scene.belief().rows()) {
                var entity = player.serverLevel().getEntity(row.viewer());
                String who = entity == null ? "#" + row.viewer() : entity.getName().getString();
                player.sendSystemMessage(Component.literal("    " + who + " -> " + row.element() + ": "
                        + (row.shattered() ? "shattered" : String.format("%.2f", row.belief()))));
            }
        }
        return scenes.size();
    }
}
```

In `MagicalCommands`, directly before `.then(Commands.literal("causality")` (line 183):

```java
                    .then(com.efkrdnz.magical.magic.mind.MindCommands.build())
```

- [ ] **Step 10: Run the game tests and confirm they pass**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: `mind_unveil_1` passes, along with every earlier `mind_*` test.

If the crosshair of a pitch-45 fake player at `(2, 2, 0)` finds no block, the floor is further than expected. Raise the pitch to 70 before touching the reach.

- [ ] **Step 11: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/MindPresets.java src/main/java/com/efkrdnz/magical/magic/mind/MindCommands.java src/main/java/com/efkrdnz/magical/magic/mind/UnveilGameTests.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java src/test/java/com/efkrdnz/magical/magic/mind/MindPresetsTest.java
git commit -m "feat: Unveil - a reverie set down where you look, billed by what it holds; /magical mind"
```

---

### Task 12: Sync, and drawing a lie for the minds that hold it

**Files:**
- Create: `network/IllusionScenePayload.java`, `network/IllusionEndPayload.java`, `network/BeliefSyncPayload.java`, `magic/mind/MindSync.java`, `client/mind/ClientMind.java`, `client/mind/IllusionRenderer.java`, `client/mind/FigmentRenderer.java`, `client/mind/FigmentDummies.java`
- Modify:
  - `network/MagicalNetwork.java`: register the three payloads beside `OpenCausalBoardPayload` (line 52).
  - `client/ClientPayloadHandlers.java`: three `handle` overloads beside `handle(CooldownSyncPayload)`.
  - `magic/mind/LiveScene.java`: `audience`.
  - `magic/mind/MindService.java`: call `MindSync` from `tickScene` and `end`.
  - `client/MagicalClientEvents.java`:
    - the renderer bind from Task 10 becomes `FigmentRenderer`;
    - `onClientSetup` (line 134) sets `FigmentEntity.clientSees`;
    - `renderLevelOverlays` (line 358) draws illusions.
- Test: `network/MindPayloadsTest.java`

**Interfaces:**
- Consumes: `LiveScene` (with `audience`), `MindService` (Tasks 8–11); `FigmentEntity.clientSees` (Task 10).
- Produces:
  - Payload types:
    - `IllusionScenePayload(int scene, boolean mine, List<Cell> cells)`, where `Cell(BlockPos pos, int state, int element)`, `MAX_CELLS = 256`.
    - `IllusionEndPayload(int scene)`.
    - `BeliefSyncPayload(int scene, List<Entry> entries)`, where `Entry(int viewer, int element, byte belief, boolean shattered)`, `MAX_ENTRIES = 512`, and `belief` is `round(b * 100)`.
  - `ClientMind`:
    - `scenes()`, returning a `Collection<ClientMind.View>`;
    - `belief(int scene, int viewer, int element)`, a float with −1 for shattered;
    - `visibility(int scene, int element)`, a float in 0..1;
    - `sees(int scene, int element)` and `mine(int scene)`, both booleans;
    - `rows(int scene)`, returning a `List<BeliefSyncPayload.Entry>`;
    - `clear()`.

**Who is sent what:**
- A scene goes to every player in its level within `AUDIENCE_RANGE = 64` of its bounds, the owner included. The audience is re-checked every 20 ticks, so a player who walks up later, or comes back from another dimension, is sent it then.
- Every `BELIEF_INTERVAL = 5` ticks, each audience member is sent their own rows: those where `viewer == player.getId()`. The owner is sent every row, because Belief Sight (Task 13) draws everyone's.
- When a scene ends, the audience is told.

**What a client draws:**
- **The owner** sees every element at 45% alpha, each cluster edged in a lilac line box. They always know it is a lie.
- **Anyone else** sees an element faded in by their own belief: `min(1, b / CONVINCED)`. A shattered element is never drawn. A figment is drawn once visibility reaches 0.5, which means belief 0.25, and is only pickable then.

Imagined blocks are drawn with `renderSingleBlock` through a buffer source that forces `RenderType.translucent()` and scales the alpha. A figment is drawn by a dummy of its creature type, posed like the figment each frame.

- [ ] **Step 1: Write the failing codec test**

```java
package com.efkrdnz.magical.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MindPayloadsTest {
    private static <T> void roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, packet);
            assertEquals(packet, codec.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void aSceneRoundTrips() {
        roundTrip(IllusionScenePayload.STREAM_CODEC, new IllusionScenePayload(7, true,
                List.of(new IllusionScenePayload.Cell(new BlockPos(1, -60, 3), 12, 0),
                        new IllusionScenePayload.Cell(new BlockPos(1, -59, 3), 12, 0))));
    }

    @Test
    void anEndAndABeliefRoundTrip() {
        roundTrip(IllusionEndPayload.STREAM_CODEC, new IllusionEndPayload(7));
        roundTrip(BeliefSyncPayload.STREAM_CODEC, new BeliefSyncPayload(7,
                List.of(new BeliefSyncPayload.Entry(42, 0, (byte) 64, false), new BeliefSyncPayload.Entry(42, 1, (byte) 0, true))));
    }

    @Test
    void aHostileLengthIsRefused() {
        List<IllusionScenePayload.Cell> cells = new ArrayList<>();
        for (int i = 0; i <= IllusionScenePayload.MAX_CELLS; i++) {
            cells.add(new IllusionScenePayload.Cell(BlockPos.ZERO, 1, 0));
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(Exception.class, () -> IllusionScenePayload.STREAM_CODEC.encode(buffer, new IllusionScenePayload(1, false, cells)));
        } finally {
            buffer.release();
        }
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.network.MindPayloadsTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write the payloads**

`IllusionScenePayload.java`:

```java
package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** A scene's imagined blocks, sent to each player near it; figments travel as entities. */
public record IllusionScenePayload(int scene, boolean mine, List<Cell> cells) implements CustomPacketPayload {
    /** Twice the largest budget: a reverie is at most 128 elements. */
    public static final int MAX_CELLS = 256;

    public record Cell(BlockPos pos, int state, int element) {
        public static final StreamCodec<ByteBuf, Cell> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Cell::pos,
                ByteBufCodecs.VAR_INT, Cell::state,
                ByteBufCodecs.VAR_INT, Cell::element,
                Cell::new);
    }

    public static final Type<IllusionScenePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "illusion_scene"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IllusionScenePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IllusionScenePayload::scene,
            ByteBufCodecs.BOOL, IllusionScenePayload::mine,
            Cell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)), IllusionScenePayload::cells,
            IllusionScenePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

`IllusionEndPayload.java`:

```java
package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IllusionEndPayload(int scene) implements CustomPacketPayload {
    public static final Type<IllusionEndPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "illusion_end"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IllusionEndPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IllusionEndPayload::scene,
            IllusionEndPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

`BeliefSyncPayload.java`:

```java
package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** A scene's belief rows: a viewer is sent its own, the scene's owner everybody's. Belief in hundredths. */
public record BeliefSyncPayload(int scene, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 512;

    public record Entry(int viewer, int element, byte belief, boolean shattered) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::viewer,
                ByteBufCodecs.VAR_INT, Entry::element,
                ByteBufCodecs.BYTE, Entry::belief,
                ByteBufCodecs.BOOL, Entry::shattered,
                Entry::new);

        public float value() {
            return belief / 100.0F;
        }
    }

    public static final Type<BeliefSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "belief_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeliefSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BeliefSyncPayload::scene,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), BeliefSyncPayload::entries,
            BeliefSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.network.MindPayloadsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Register and route the payloads**

In `MagicalNetwork.registerPayloads`, after the `OpenCausalBoardPayload` line:

```java
                .playToClient(IllusionScenePayload.TYPE, IllusionScenePayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(IllusionEndPayload.TYPE, IllusionEndPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
                .playToClient(BeliefSyncPayload.TYPE, BeliefSyncPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
```

In `ClientPayloadHandlers`, beside `handle(CooldownSyncPayload)`:

```java
    public static void handle(IllusionScenePayload payload) {
        com.efkrdnz.magical.client.mind.ClientMind.accept(payload);
    }

    public static void handle(IllusionEndPayload payload) {
        com.efkrdnz.magical.client.mind.ClientMind.accept(payload);
    }

    public static void handle(BeliefSyncPayload payload) {
        com.efkrdnz.magical.client.mind.ClientMind.accept(payload);
    }
```

Import the three payload types in both files the way their neighbours are imported.

- [ ] **Step 6: Write `MindSync` and hook it in**

In `LiveScene`:

```java
    final java.util.Set<java.util.UUID> audience = new java.util.HashSet<>();
```

`MindSync.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.network.BeliefSyncPayload;
import com.efkrdnz.magical.network.IllusionEndPayload;
import com.efkrdnz.magical.network.IllusionScenePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Who hears about a scene, and what each of them is told. */
final class MindSync {
    static final double AUDIENCE_RANGE = 64.0;
    static final int AUDIENCE_INTERVAL = 20;
    static final int BELIEF_INTERVAL = 5;

    private MindSync() {}

    static void tick(ServerLevel level, LiveScene scene) {
        long age = level.getGameTime() - scene.bornAt();
        // An empty audience is re-checked every tick, so the wielder sees their scene on the tick it goes up.
        if (age % AUDIENCE_INTERVAL == 0 || scene.audience.isEmpty()) {
            scene.audience.removeIf(id -> !(level.getPlayerByUUID(id) instanceof ServerPlayer));
            for (ServerPlayer player : level.getPlayers(p -> p.getBoundingBox().intersects(scene.bounds().inflate(AUDIENCE_RANGE)))) {
                if (scene.audience.add(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player, scenePayload(scene, player.getUUID().equals(scene.owner())));
                }
            }
        }
        if (age % BELIEF_INTERVAL != 0) {
            return;
        }
        List<Belief.Row> rows = scene.belief().rows();
        for (UUID id : scene.audience) {
            if (!(level.getPlayerByUUID(id) instanceof ServerPlayer player)) {
                continue;
            }
            boolean owner = id.equals(scene.owner());
            List<BeliefSyncPayload.Entry> entries = new ArrayList<>();
            for (Belief.Row row : rows) {
                if ((owner || row.viewer() == player.getId()) && entries.size() < BeliefSyncPayload.MAX_ENTRIES) {
                    entries.add(new BeliefSyncPayload.Entry(row.viewer(), row.element(),
                            (byte) Math.round(row.belief() * 100.0F), row.shattered()));
                }
            }
            PacketDistributor.sendToPlayer(player, new BeliefSyncPayload(scene.id(), entries));
        }
    }

    static void ended(LiveScene scene) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (UUID id : scene.audience) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new IllusionEndPayload(scene.id()));
            }
        }
    }

    static IllusionScenePayload scenePayload(LiveScene scene, boolean mine) {
        List<IllusionScenePayload.Cell> cells = new ArrayList<>();
        for (LiveScene.Element element : scene.elements()) {
            for (int i = 0; i < element.cells().size() && cells.size() < IllusionScenePayload.MAX_CELLS; i++) {
                BlockPos cell = element.cells().get(i);
                ResourceLocation id = ResourceLocation.tryParse(element.blockIds().get(i));
                Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
                if (block != null) {
                    cells.add(new IllusionScenePayload.Cell(cell, Block.getId(block.defaultBlockState()), element.index()));
                }
            }
        }
        return new IllusionScenePayload(scene.id(), mine, cells);
    }
}
```

In `MindService.tickScene`, as its last line:

```java
        MindSync.tick(level, scene);
```

In `MindService.end`, after the scene is removed from the map and before the method returns:

```java
        MindSync.ended(scene);
```

- [ ] **Step 7: Write `ClientMind`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.mind.Belief;
import com.efkrdnz.magical.network.BeliefSyncPayload;
import com.efkrdnz.magical.network.IllusionEndPayload;
import com.efkrdnz.magical.network.IllusionScenePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every scene this client has been told about, and the belief rows it has been sent. */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class ClientMind {
    public static final float FIGMENT_VISIBLE = 0.5F;
    public static final float OWNER_ALPHA = 0.45F;

    public record Cell(BlockPos pos, BlockState state, int element) {}

    public record View(int id, boolean mine, List<Cell> cells) {}

    private static final Map<Integer, View> SCENES = new LinkedHashMap<>();
    private static final Map<Integer, List<BeliefSyncPayload.Entry>> ROWS = new HashMap<>();
    private static ClientLevel lastLevel;

    private ClientMind() {}

    public static void accept(IllusionScenePayload payload) {
        List<Cell> cells = new ArrayList<>();
        for (IllusionScenePayload.Cell cell : payload.cells()) {
            cells.add(new Cell(cell.pos(), Block.stateById(cell.state()), cell.element()));
        }
        SCENES.put(payload.scene(), new View(payload.scene(), payload.mine(), List.copyOf(cells)));
    }

    public static void accept(IllusionEndPayload payload) {
        SCENES.remove(payload.scene());
        ROWS.remove(payload.scene());
    }

    public static void accept(BeliefSyncPayload payload) {
        ROWS.put(payload.scene(), List.copyOf(payload.entries()));
    }

    public static Collection<View> scenes() {
        return SCENES.values();
    }

    public static boolean mine(int scene) {
        View view = SCENES.get(scene);
        return view != null && view.mine();
    }

    public static List<BeliefSyncPayload.Entry> rows(int scene) {
        return ROWS.getOrDefault(scene, List.of());
    }

    /** A viewer's belief in an element as this client was told it; -1 when they have seen through it. */
    public static float belief(int scene, int viewer, int element) {
        for (BeliefSyncPayload.Entry entry : rows(scene)) {
            if (entry.viewer() == viewer && entry.element() == element) {
                return entry.shattered() ? -1.0F : entry.value();
            }
        }
        return 0.0F;
    }

    /** How solid this element looks to this client, 0 (not there) to 1. */
    public static float visibility(int scene, int element) {
        View view = SCENES.get(scene);
        Minecraft minecraft = Minecraft.getInstance();
        if (view == null || minecraft.player == null) {
            return 0.0F;
        }
        if (view.mine()) {
            return 1.0F;
        }
        float belief = belief(scene, minecraft.player.getId(), element);
        return belief <= 0.0F ? 0.0F : Math.min(1.0F, belief / Belief.CONVINCED);
    }

    public static boolean sees(int scene, int element) {
        return visibility(scene, element) >= FIGMENT_VISIBLE;
    }

    public static void clear() {
        SCENES.clear();
        ROWS.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            clear();
            FigmentDummies.clear();
            lastLevel = level;
        }
    }
}
```

A new level (a dimension change, a logout) empties everything, and the server re-sends a scene on the next audience check. That is why `MindSync` drops audience members who are no longer in the scene's level.

- [ ] **Step 8: Write `IllusionRenderer`**

```java
package com.efkrdnz.magical.client.mind;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Imagined blocks, drawn by this client at the strength its own mind holds them. */
public final class IllusionRenderer {
    public static final int LILAC = 0xBDA4FF;
    private static final float EDGE_ALPHA = 0.9F;
    private static final float MIN_ALPHA = 0.02F;

    private IllusionRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (minecraft.level == null || ClientMind.scenes().isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        for (ClientMind.View view : ClientMind.scenes()) {
            for (ClientMind.Cell cell : view.cells()) {
                float alpha = view.mine() ? ClientMind.OWNER_ALPHA : ClientMind.visibility(view.id(), cell.element());
                drawBlock(minecraft, pose, buffers, cam, cell.state(), cell.pos(), alpha);
            }
        }
        buffers.endBatch(RenderType.translucent());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (ClientMind.View view : ClientMind.scenes()) {
            if (view.mine()) {
                for (ClientMind.Cell cell : view.cells()) {
                    drawEdge(pose, lines, cam, new AABB(cell.pos()), LILAC, EDGE_ALPHA);
                }
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    /** One block at {@code alpha}; the caller ends the translucent batch. Shared with the Daydream draft. */
    public static void drawBlock(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam,
                                 BlockState state, BlockPos pos, float alpha) {
        if (alpha < MIN_ALPHA || minecraft.level == null) {
            return;
        }
        pose.pushPose();
        pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        int light = LevelRenderer.getLightColor(minecraft.level, pos);
        MultiBufferSource faded = type -> new Faded(buffers.getBuffer(RenderType.translucent()), alpha);
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, faded, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    /** A line box in world space, {@code rgb} at {@code alpha}; the caller ends the lines batch. */
    public static void drawEdge(PoseStack pose, VertexConsumer lines, Vec3 cam, AABB box, int rgb, float alpha) {
        ShapeRenderer.renderLineBox(pose, lines, box.move(-cam.x, -cam.y, -cam.z),
                ((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, alpha);
    }

    /** Passes every vertex through with its alpha scaled; the bulk paths default through setColor. */
    private record Faded(VertexConsumer inner, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }
}
```

The outline marks every cell of every cluster the owner has. A dozen line boxes are cheaper than working out the cluster hull, and they read as a drawing, which is what the owner is looking at.

If the compiler reports another abstract method on `VertexConsumer` in 1.21.4, delegate it the same way. `Faded` must not override the bulk `addVertex(...11 args...)` or `putBulkData`: their defaults route through `setColor`, which is the whole trick.

- [ ] **Step 9: Write `FigmentDummies` and `FigmentRenderer`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** A stand-in of the imagined kind for each figment, posed like it; only ever rendered, never added. */
public final class FigmentDummies {
    private static final Map<Integer, LivingEntity> DUMMIES = new HashMap<>();

    private FigmentDummies() {}

    public static LivingEntity posed(FigmentEntity figment) {
        LivingEntity dummy = DUMMIES.computeIfAbsent(figment.getId(), id -> create(figment));
        if (dummy == null) {
            DUMMIES.remove(figment.getId());
            return null;
        }
        dummy.setPos(figment.getX(), figment.getY(), figment.getZ());
        dummy.xo = figment.xo;
        dummy.yo = figment.yo;
        dummy.zo = figment.zo;
        dummy.xOld = figment.xOld;
        dummy.yOld = figment.yOld;
        dummy.zOld = figment.zOld;
        dummy.setYRot(figment.getYRot());
        dummy.yRotO = figment.yRotO;
        dummy.setXRot(figment.getXRot());
        dummy.xRotO = figment.xRotO;
        dummy.yBodyRot = figment.yBodyRot;
        dummy.yBodyRotO = figment.yBodyRotO;
        dummy.yHeadRot = figment.yHeadRot;
        dummy.yHeadRotO = figment.yHeadRotO;
        dummy.attackAnim = figment.attackAnim;
        dummy.oAttackAnim = figment.oAttackAnim;
        if (dummy.tickCount != figment.tickCount) {
            dummy.tickCount = figment.tickCount;
            dummy.walkAnimation.update(figment.walkAnimation.speed(), 1.0F);
        }
        return dummy;
    }

    private static LivingEntity create(FigmentEntity figment) {
        return EntityType.byString(figment.creatureId())
                .map(type -> type.create(figment.level(), EntitySpawnReason.LOAD))
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .orElse(null);
    }

    public static void clear() {
        DUMMIES.clear();
    }
}
```

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/** Draws a figment as its imagined kind - for the owner always, for anyone else once they half-believe it. */
public final class FigmentRenderer extends EntityRenderer<FigmentEntity, FigmentRenderer.State> {
    public static final class State extends EntityRenderState {
        FigmentEntity figment;
        float partial;
    }

    public FigmentRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FigmentEntity figment, State state, float partial) {
        super.extractRenderState(figment, state, partial);
        state.figment = figment;
        state.partial = partial;
    }

    @Override
    public void render(State state, PoseStack pose, MultiBufferSource buffers, int light) {
        FigmentEntity figment = state.figment;
        if (figment == null || !ClientMind.sees(figment.sceneId(), figment.element())) {
            return;
        }
        LivingEntity dummy = FigmentDummies.posed(figment);
        if (dummy != null) {
            Minecraft.getInstance().getEntityRenderDispatcher().render(dummy, 0.0, 0.0, 0.0, state.partial, pose, buffers, light);
        }
    }
}
```

`EntitySpawnReason` and `EntityType.create(Level, EntitySpawnReason)` are the 1.21.4 names. If `EntityRenderer` in 21.4.157 wants `shouldRender` overridden to keep a figment drawn while its box is off the frustum edge, leave the default: the dummy is the same size.

- [ ] **Step 10: Wire the client**

In `MagicalClientEvents.registerRenderers`, replace the Task 10 `NoopRenderer` line for `FIGMENT` with:

```java
        event.registerEntityRenderer(MagicalEntities.FIGMENT.get(), com.efkrdnz.magical.client.mind.FigmentRenderer::new);
```

In `onClientSetup` (line 134), add:

```java
        event.enqueueWork(() -> com.efkrdnz.magical.entity.mind.FigmentEntity.clientSees =
                com.efkrdnz.magical.client.mind.ClientMind::sees);
```

If `onClientSetup` already calls `enqueueWork`, put the assignment inside that call instead.

In `renderLevelOverlays`, inside the `AFTER_CUTOUT_BLOCKS` branch before its `return`:

```java
                com.efkrdnz.magical.client.mind.IllusionRenderer.render(event, minecraft);
```

- [ ] **Step 11: Build and run all tests**

Run: `.\gradlew build` then `.\gradlew runGameTestServer --console=plain`
Expected: BUILD SUCCESSFUL, and every `mind_*` game test still passes. `MindSync` runs inside them with fake players and a mock connection, which is the check that sending to an audience does not throw.

- [ ] **Step 12: Capture it**

`run/options.txt` must have `pauseOnLostFocus:false`. The owner's view of a wall and a cat: lilac edges, faint blocks.

```powershell
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical authority set authority_of_mind;time set day;tp @s ~ ~ ~ 0 20;magical mind preset 1 wall;magical mind preset 2 cat;120:magical mind slot 1;122:magical mind unveil;140:magical mind slot 2;142:magical mind unveil;150:summon husk ~3 ~ ~8" -PautoScreenshot=160,260 -PautoExit
```

Read the frames: the wall and the cat should both be visible at tick 160. At 260 the husk has had five seconds and `/magical mind belief` would show its rows.

- [ ] **Step 13: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/network/IllusionScenePayload.java src/main/java/com/efkrdnz/magical/network/IllusionEndPayload.java src/main/java/com/efkrdnz/magical/network/BeliefSyncPayload.java src/main/java/com/efkrdnz/magical/magic/mind/MindSync.java src/main/java/com/efkrdnz/magical/magic/mind/LiveScene.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/client/mind/ClientMind.java src/main/java/com/efkrdnz/magical/client/mind/IllusionRenderer.java src/main/java/com/efkrdnz/magical/client/mind/FigmentRenderer.java src/main/java/com/efkrdnz/magical/client/mind/FigmentDummies.java src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java src/main/java/com/efkrdnz/magical/client/ClientPayloadHandlers.java src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java src/test/java/com/efkrdnz/magical/network/MindPayloadsTest.java
git commit -m "feat: illusions drawn per mind - each client sees a lie as strongly as it believes it"
```

---

### Task 13: Belief Sight

**Files:**
- Create: `client/mind/BeliefSight.java` (pure), `client/mind/BeliefSightRenderer.java`
- Modify:
  - `client/renderer/causality/AnchorMarkRenderer.java:111`: `band` goes from `private` to `public`.
  - `client/MagicalClientEvents.java`: draw it in the `AFTER_CUTOUT_BLOCKS` branch.
- Test: `client/mind/BeliefSightTest.java`

**Interfaces:**
- Consumes: `ClientMind.scenes()`, `ClientMind.rows(int)`, `ClientMind.mine(int)` (Task 12); `AnchorMarkRenderer.band(VertexConsumer, Matrix4f, GlyphKind, int count, float radius, int weight, int rgb, float opacity)`.
- Produces:
  - `BeliefSight.Mark(GlyphKind kind, int rgb, float opacity)`.
  - `BeliefSight.mark(float belief)`, returning a `Mark` or null: null for no belief and for a shattered one (belief ≤ 0).
  - `BeliefSight.strongest(List<BeliefSyncPayload.Entry> rows, int viewer)`, returning a float. It is the viewer's strongest belief across a scene: −1 if every row is shattered, 0 with no rows.

The wielder's view only. Above every viewer's head there is one ring, on three channels that cannot be confused:
- **Shape:** dashed while the viewer doubts, solid once convinced.
- **Hue:** lilac `0xBDA4FF` for doubt, gold `0xEFC86A` for convinced.
- **Opacity:** `0.35 + 0.6 * b` while doubting, so a ring you can barely see is a mind you have barely reached.

A viewer who has seen through every element gets no ring: they are gone from the lie. The rings are drawn through terrain like the Causal Anchor's, because the viewer worth watching is the one behind the wall. `phase` stays at the anchor ring's 0.5 (see the CLAUDE.md note on `rendertype_glyph_ink`).

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.network.BeliefSyncPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeliefSightTest {
    @Test
    void doubtIsADashedLilacRingThatBrightensAndConvictionIsSolidGold() {
        assertNull(BeliefSight.mark(0.0F));
        assertNull(BeliefSight.mark(-1.0F));
        BeliefSight.Mark doubt = BeliefSight.mark(0.2F);
        assertEquals(GlyphKind.DASHED_RING, doubt.kind());
        assertEquals(BeliefSight.DOUBT, doubt.rgb());
        assertEquals(0.35F + 0.6F * 0.2F, doubt.opacity(), 1.0E-6F);
        BeliefSight.Mark sure = BeliefSight.mark(0.5F);
        assertEquals(GlyphKind.SOLID_RING, sure.kind());
        assertEquals(BeliefSight.CONVINCED, sure.rgb());
    }

    @Test
    void aViewerReadsAsTheirStrongestBelief() {
        List<BeliefSyncPayload.Entry> rows = List.of(
                new BeliefSyncPayload.Entry(1, 0, (byte) 20, false),
                new BeliefSyncPayload.Entry(1, 1, (byte) 70, false),
                new BeliefSyncPayload.Entry(2, 0, (byte) 0, true));
        assertEquals(0.70F, BeliefSight.strongest(rows, 1), 1.0E-6F);
        assertEquals(-1.0F, BeliefSight.strongest(rows, 2), 1.0E-6F, "seen through everything");
        assertEquals(0.0F, BeliefSight.strongest(rows, 3), 1.0E-6F);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.mind.BeliefSightTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `BeliefSight`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.magic.mind.Belief;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.network.BeliefSyncPayload;

import java.util.List;

/** What the ring over a viewer's head says, from a belief: shape, hue and strength. */
public final class BeliefSight {
    public static final int DOUBT = 0xBDA4FF;
    public static final int CONVINCED = 0xEFC86A;
    public static final float BASE_OPACITY = 0.35F;
    public static final float OPACITY_PER_BELIEF = 0.6F;
    public static final float CONVINCED_OPACITY = 0.95F;

    public record Mark(GlyphKind kind, int rgb, float opacity) {}

    private BeliefSight() {}

    public static Mark mark(float belief) {
        if (belief <= 0.0F) {
            return null;
        }
        if (belief >= Belief.CONVINCED) {
            return new Mark(GlyphKind.SOLID_RING, CONVINCED, CONVINCED_OPACITY);
        }
        return new Mark(GlyphKind.DASHED_RING, DOUBT, BASE_OPACITY + OPACITY_PER_BELIEF * belief);
    }

    public static float strongest(List<BeliefSyncPayload.Entry> rows, int viewer) {
        float best = 0.0F;
        boolean any = false;
        boolean allShattered = true;
        for (BeliefSyncPayload.Entry entry : rows) {
            if (entry.viewer() != viewer) {
                continue;
            }
            any = true;
            if (!entry.shattered()) {
                allShattered = false;
                best = Math.max(best, entry.value());
            }
        }
        return any && allShattered ? -1.0F : best;
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.mind.BeliefSightTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Write `BeliefSightRenderer` and open `band`**

In `AnchorMarkRenderer`, change `private static void band(` to `public static void band(`. Add one line of Javadoc above it: `/** A glyph ring in a camera-facing pose; shared with Belief Sight. */`.

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.renderer.causality.AnchorMarkRenderer;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/** The wielder's Belief Sight: a ring over every viewer of their scenes, through walls. */
public final class BeliefSightRenderer {
    private static final float RADIUS = 0.28F;
    private static final double ABOVE_HEAD = 0.55;
    private static final int PIPS = 8;
    private static final int WEIGHT = 3;

    private BeliefSightRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        Map<Integer, Float> strongest = new HashMap<>();
        for (ClientMind.View view : ClientMind.scenes()) {
            if (!view.mine()) {
                continue;
            }
            var rows = ClientMind.rows(view.id());
            for (var row : rows) {
                float belief = BeliefSight.strongest(rows, row.viewer());
                strongest.merge(row.viewer(), belief, Math::max);
            }
        }
        if (strongest.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(MagicalFxRenderTypes.glyphInkThrough());
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        strongest.forEach((id, belief) -> {
            BeliefSight.Mark mark = BeliefSight.mark(belief);
            if (mark == null || !(minecraft.level.getEntity(id) instanceof LivingEntity body) || !body.isAlive()) {
                return;
            }
            Vec3 at = body.getPosition(partial).add(0.0, body.getBbHeight() + ABOVE_HEAD, 0.0);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(event.getCamera().rotation());
            AnchorMarkRenderer.band(consumer, pose.last().pose(), mark.kind(), PIPS, RADIUS, WEIGHT, mark.rgb(), mark.opacity());
            pose.popPose();
        });
        buffers.endBatch(MagicalFxRenderTypes.glyphInkThrough());
    }
}
```

In `MagicalClientEvents.renderLevelOverlays`, in the `AFTER_CUTOUT_BLOCKS` branch after the `IllusionRenderer` line:

```java
                com.efkrdnz.magical.client.mind.BeliefSightRenderer.render(event, minecraft);
```

- [ ] **Step 6: Build and capture**

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL.

Capture at midnight, where additive rings read true. A wall and two husks: one watches from open ground, the other stands behind a real pillar and sees nothing.

```powershell
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical authority set authority_of_mind;time set midnight;tp @s ~ ~ ~ 0 15;magical mind preset 1 wall;120:magical mind unveil;124:summon husk ~2 ~ ~9 {NoAI:1b};125:summon husk ~-3 ~ ~9 {NoAI:1b};126:fill ~-3 ~ ~7 ~-3 ~2 ~7 stone" -PautoScreenshot=140,200,300 -PautoExit
```

Expected at 300: a solid gold ring over the open husk, and no ring, or a faint dashed one, over the hidden husk.

- [ ] **Step 7: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/client/mind/BeliefSight.java src/main/java/com/efkrdnz/magical/client/mind/BeliefSightRenderer.java src/main/java/com/efkrdnz/magical/client/renderer/causality/AnchorMarkRenderer.java src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java src/test/java/com/efkrdnz/magical/client/mind/BeliefSightTest.java
git commit -m "feat: Belief Sight - a ring over every mind the lie has reached, dashed to solid"
```

---

### Task 14: Brushes, the draft ray, and saving a reverie

**Files:**
- Create: `magic/mind/Brush.java`, `magic/mind/DraftRay.java`, `network/SaveReveriePayload.java`
- Modify:
  - `magic/mind/Reverie.java`: `validate`.
  - `magic/mind/MindService.java`: `saveReverie`.
  - `network/MagicalNetwork.java`: register and `sendSaveReverie`.
- Test: `magic/mind/DaydreamCoreTest.java`, `network/SaveReveriePayloadTest.java`

**Interfaces:**
- Consumes: `Offset`, `Reverie`, `ReverieNbt`, `Lexicon` (Tasks 1 and 2); `MindState` (Task 3).
- Produces:
  - `Brush`, with values `POINT`, `LINE`, `WALL`, `BOX`. Methods: `cells(Offset from, Offset to)`, returning a `List<Offset>`; `next()`; and `MAX_CELLS = 128`.
  - `DraftRay.Hit(BlockPos solid, BlockPos place)`. `solid` is null when nothing was met within reach.
  - `DraftRay.march(Vec3 from, Vec3 direction, double reach, Predicate<BlockPos> solid)`, returning a `Hit`.
  - `Reverie.validate(Lexicon)`, returning a `Reverie.Refusal`.
  - `SaveReveriePayload(int slot, CompoundTag data)`.
  - `MagicalNetwork.sendSaveReverie(int slot, CompoundTag data)`.
  - `MindService.saveReverie(ServerPlayer, int slot, CompoundTag)`.

**How the pieces behave:**
- **Brushes:**
  - `POINT` is the cursor cell.
  - `LINE` steps along the longest axis from one corner to the other.
  - `WALL` is the vertical plane through both corners, running along whichever horizontal axis they differ more on.
  - `BOX` fills the whole cuboid.
  - No brush returns more than `MAX_CELLS`, so a careless box cannot build a packet the server would refuse anyway.
- **The draft ray** walks voxels (Amanatides-Woo) from the eye. It stops at the first cell the predicate calls solid, which is real terrain or an already-drafted block, and hands back the air cell before it. A ray that meets nothing ends in the air at `reach`, which is how a wielder draws a floating thing.
- **Saving:** the server does not trust the client's draft.
  - It loads the tag through `ReverieNbt.load`, which drops anything out of reach and caps the count.
  - It then refuses the lot if any element is an impression the wielder does not know, or if the reverie is over the wielder's budget.
  - Only then does it copy the draft into the slot and make that slot active.

- [ ] **Step 1: Write the failing tests**

`DaydreamCoreTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DaydreamCoreTest {
    private static final Offset O = new Offset(0, 0, 0);

    @Test
    void aPointIsTheCursorAndALineRunsCornerToCorner() {
        assertEquals(List.of(new Offset(2, 1, 0)), Brush.POINT.cells(O, new Offset(2, 1, 0)));
        List<Offset> line = Brush.LINE.cells(O, new Offset(3, 0, 1));
        assertEquals(4, line.size());
        assertEquals(O, line.get(0));
        assertEquals(new Offset(3, 0, 1), line.get(3));
    }

    @Test
    void aWallStandsAlongItsLongerSideAndABoxFillsItsCuboid() {
        List<Offset> wall = Brush.WALL.cells(O, new Offset(2, 1, 1));
        assertEquals(6, wall.size(), "x 0..2 by y 0..1, at the first corner's z");
        assertTrue(wall.stream().allMatch(o -> o.dz() == 0));
        assertEquals(8, Brush.BOX.cells(O, new Offset(1, 1, 1)).size());
        assertEquals(Brush.MAX_CELLS, Brush.BOX.cells(O, new Offset(9, 9, 9)).size());
        assertEquals(Brush.POINT, Brush.BOX.next());
    }

    @Test
    void theRayStopsInFrontOfTheFirstSolidCell() {
        DraftRay.Hit down = DraftRay.march(new Vec3(0.5, 3.5, 0.5), new Vec3(0, -1, 0), 6.0, pos -> pos.getY() <= 0);
        assertEquals(new BlockPos(0, 0, 0), down.solid());
        assertEquals(new BlockPos(0, 1, 0), down.place());

        DraftRay.Hit wall = DraftRay.march(new Vec3(0.5, 1.5, 0.5), new Vec3(1, 0, 0), 6.0, pos -> pos.getX() >= 3);
        assertEquals(new BlockPos(3, 1, 0), wall.solid());
        assertEquals(new BlockPos(2, 1, 0), wall.place());
    }

    @Test
    void aRayThatMeetsNothingEndsInTheAirAtItsReach() {
        DraftRay.Hit air = DraftRay.march(new Vec3(0.5, 3.5, 0.5), new Vec3(0, 0, 1), 6.0, pos -> false);
        assertNull(air.solid());
        assertEquals(new BlockPos(0, 3, 6), air.place());
    }

    @Test
    void aReverieIsRefusedForWhatItsWielderNeverStudiedOrCannotAfford() {
        Lexicon lexicon = new Lexicon();
        lexicon.gaze("block:minecraft:stone");
        Reverie reverie = new Reverie();
        reverie.addBlock(O, "minecraft:stone", lexicon);
        assertEquals(Reverie.Refusal.NONE, reverie.validate(lexicon));

        reverie.put(new ImaginedBlock(new Offset(1, 0, 0), "minecraft:gold_block", java.util.Set.of()));
        assertEquals(Reverie.Refusal.UNKNOWN, reverie.validate(lexicon));

        Reverie big = new Reverie();
        for (int i = 0; i < 20; i++) {
            big.put(new ImaginedBlock(new Offset(i - 10, 0, 0), "minecraft:stone", java.util.Set.of()));
        }
        assertEquals(Reverie.Refusal.FULL, big.validate(lexicon), "one impression buys 18, not 20");
    }
}
```

`SaveReveriePayloadTest.java`:

```java
package com.efkrdnz.magical.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaveReveriePayloadTest {
    @Test
    void aDraftRoundTrips() {
        CompoundTag data = new CompoundTag();
        data.putString("name", "Pit");
        SaveReveriePayload packet = new SaveReveriePayload(2, data);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SaveReveriePayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, SaveReveriePayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.DaydreamCoreTest" --tests "com.efkrdnz.magical.network.SaveReveriePayloadTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `Brush`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.ArrayList;
import java.util.List;

/** The shapes Daydream lays an impression in, from one corner to the cursor. */
public enum Brush {
    POINT, LINE, WALL, BOX;

    public static final int MAX_CELLS = 128;

    public Brush next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public List<Offset> cells(Offset from, Offset to) {
        List<Offset> cells = new ArrayList<>();
        switch (this) {
            case POINT -> cells.add(to);
            case LINE -> {
                int dx = to.dx() - from.dx();
                int dy = to.dy() - from.dy();
                int dz = to.dz() - from.dz();
                int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                for (int i = 0; i <= steps && cells.size() < MAX_CELLS; i++) {
                    double t = steps == 0 ? 0.0 : (double) i / steps;
                    cells.add(new Offset(from.dx() + (int) Math.round(dx * t), from.dy() + (int) Math.round(dy * t),
                            from.dz() + (int) Math.round(dz * t)));
                }
            }
            case WALL -> {
                boolean alongX = Math.abs(to.dx() - from.dx()) >= Math.abs(to.dz() - from.dz());
                int loY = Math.min(from.dy(), to.dy());
                int hiY = Math.max(from.dy(), to.dy());
                int lo = alongX ? Math.min(from.dx(), to.dx()) : Math.min(from.dz(), to.dz());
                int hi = alongX ? Math.max(from.dx(), to.dx()) : Math.max(from.dz(), to.dz());
                for (int y = loY; y <= hiY; y++) {
                    for (int a = lo; a <= hi && cells.size() < MAX_CELLS; a++) {
                        cells.add(alongX ? new Offset(a, y, from.dz()) : new Offset(from.dx(), y, a));
                    }
                }
            }
            case BOX -> {
                for (int x = Math.min(from.dx(), to.dx()); x <= Math.max(from.dx(), to.dx()); x++) {
                    for (int y = Math.min(from.dy(), to.dy()); y <= Math.max(from.dy(), to.dy()); y++) {
                        for (int z = Math.min(from.dz(), to.dz()); z <= Math.max(from.dz(), to.dz()) && cells.size() < MAX_CELLS; z++) {
                            cells.add(new Offset(x, y, z));
                        }
                    }
                }
            }
        }
        return cells.size() > MAX_CELLS ? cells.subList(0, MAX_CELLS) : cells;
    }
}
```

- [ ] **Step 4: Write `DraftRay`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Where Daydream's cursor lands: in front of the first solid cell on the ray, or in the air at reach. */
public final class DraftRay {
    public record Hit(BlockPos solid, BlockPos place) {}

    private DraftRay() {}

    public static Hit march(Vec3 from, Vec3 direction, double reach, Predicate<BlockPos> solid) {
        Vec3 d = direction.normalize();
        int x = (int) Math.floor(from.x);
        int y = (int) Math.floor(from.y);
        int z = (int) Math.floor(from.z);
        int stepX = d.x > 0 ? 1 : -1;
        int stepY = d.y > 0 ? 1 : -1;
        int stepZ = d.z > 0 ? 1 : -1;
        double deltaX = d.x == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.x);
        double deltaY = d.y == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.y);
        double deltaZ = d.z == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.z);
        double maxX = d.x == 0 ? Double.POSITIVE_INFINITY : (d.x > 0 ? x + 1 - from.x : from.x - x) * deltaX;
        double maxY = d.y == 0 ? Double.POSITIVE_INFINITY : (d.y > 0 ? y + 1 - from.y : from.y - y) * deltaY;
        double maxZ = d.z == 0 ? Double.POSITIVE_INFINITY : (d.z > 0 ? z + 1 - from.z : from.z - z) * deltaZ;
        BlockPos previous = new BlockPos(x, y, z);
        double travelled = 0.0;
        while (travelled <= reach) {
            BlockPos cell = new BlockPos(x, y, z);
            if (solid.test(cell)) {
                return new Hit(cell, previous);
            }
            previous = cell;
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                travelled = maxX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                y += stepY;
                travelled = maxY;
                maxY += deltaY;
            } else {
                z += stepZ;
                travelled = maxZ;
                maxZ += deltaZ;
            }
        }
        return new Hit(null, BlockPos.containing(from.add(d.scale(reach))));
    }
}
```

- [ ] **Step 5: Write `Reverie.validate`**

In `Reverie`:

```java
    /** The server's check of a draft it was sent: every element studied, and within the budget. */
    public Refusal validate(Lexicon lexicon) {
        for (ImaginedBlock block : blocks.values()) {
            if (!lexicon.knows("block:" + block.blockId())) {
                return Refusal.UNKNOWN;
            }
        }
        for (Figment figment : figments) {
            if (!lexicon.knows("creature:" + figment.creatureId())) {
                return Refusal.UNKNOWN;
            }
        }
        return size() > lexicon.budget() ? Refusal.FULL : Refusal.NONE;
    }
```

`DaydreamCoreTest` calls `Reverie.put` directly, which is package-private. The test is in the same package, so that works.

- [ ] **Step 6: Write the payload, its route and the server side**

`SaveReveriePayload.java`:

```java
package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A Daydream draft sent home. The server reads it through {@code ReverieNbt.load} and
 * {@code Reverie.validate}, so a forged packet can at worst describe a smaller scene than it meant to.
 */
public record SaveReveriePayload(int slot, CompoundTag data) implements CustomPacketPayload {
    public static final Type<SaveReveriePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "save_reverie"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveReveriePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slot());
                buf.writeNbt(payload.data());
            },
            buf -> new SaveReveriePayload(buf.readVarInt(), buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

In `MagicalNetwork.registerPayloads`, beside `SetWeavePayload`:

```java
                .playToServer(SaveReveriePayload.TYPE, SaveReveriePayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.efkrdnz.magical.magic.mind.MindService.saveReverie(player, payload.slot(), payload.data());
                            }
                        }))
```

And beside the other `send*` helpers:

```java
    public static void sendSaveReverie(int slot, net.minecraft.nbt.CompoundTag data) {
        PacketDistributor.sendToServer(new SaveReveriePayload(slot, data));
    }
```

In `MindService`:

```java
    public static void saveReverie(ServerPlayer player, int slot, net.minecraft.nbt.CompoundTag data) {
        PlayerMagicState state = player.getData(com.efkrdnz.magical.registry.MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(com.efkrdnz.magical.magic.AuthorityContent.MIND) || slot < 0 || slot >= MindState.SLOTS || data == null) {
            return;
        }
        Reverie draft = ReverieNbt.load(data);
        Reverie.Refusal refusal = draft.validate(state.mind().lexicon());
        if (refusal != Reverie.Refusal.NONE) {
            player.displayClientMessage(Component.translatable("message.magical.reverie_refused",
                    Component.translatable("mind.magical.refusal." + refusal.name().toLowerCase(java.util.Locale.ROOT))), true);
            state.sync(player);
            return;
        }
        state.mind().reverie(slot).copyFrom(draft);
        state.mind().setActiveSlot(slot);
        state.sync(player);
        player.displayClientMessage(Component.translatable("message.magical.reverie_saved"), true);
    }
```

A refused save still syncs, so a client that drafted on a stale lexicon snaps back to the truth.

- [ ] **Step 7: Run the tests and confirm they pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.*" --tests "com.efkrdnz.magical.network.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/Brush.java src/main/java/com/efkrdnz/magical/magic/mind/DraftRay.java src/main/java/com/efkrdnz/magical/magic/mind/Reverie.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/network/SaveReveriePayload.java src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java src/test/java/com/efkrdnz/magical/magic/mind/DaydreamCoreTest.java src/test/java/com/efkrdnz/magical/network/SaveReveriePayloadTest.java
git commit -m "feat: brushes, the draft ray, and a reverie saved only when every line of it is earned"
```

---

### Task 15: Daydream on the client - drawing in the air

**Files:**
- Create: `client/mind/DaydreamMode.java`, `client/mind/DaydreamInput.java`, `client/mind/DraftRenderer.java`, `client/mind/ImpressionReelLayout.java`, `client/mind/ImpressionReelOverlay.java`
- Modify `client/MagicalClientEvents.java`:
  - the cast-slot chain, beside `CausalityAuthorityInput.tickSlot` (line 291);
  - `dispatchScroll` and `dispatchMouseButton` (line 458 on);
  - the `AFTER_CUTOUT_BLOCKS` branch.
- Modify `client/hud/HudLayers.java`: `renderSelector`.
- Test: `client/mind/ImpressionReelLayoutTest.java`

**Interfaces:**
- Consumes:
  - `Brush`, `DraftRay`, `Reverie`, `ReverieNbt`, `Offset`, `Impression` (Tasks 1, 2 and 14);
  - `MagicalNetwork.sendSaveReverie` (Task 14);
  - `IllusionRenderer.drawBlock` and `drawEdge`, `IllusionRenderer.LILAC` (Task 12);
  - `ClientMagicState.get().mind()` (Task 3).
- Produces:
  - `DaydreamMode`:
    - `active()`, `toggle(Minecraft)`, `finish(boolean save)`;
    - `draft()`, `slot()`, `brush()`, `impression()`, `corner()`;
    - `world(Offset)` returning a `BlockPos`, `offset(BlockPos)` returning an `Offset`;
    - `cursor(Minecraft)`, returning a `DraftRay.Hit` or null;
    - `handleScroll(double)` and `handleMouseButton(int, int)`, both returning a boolean;
    - `setDraft(Reverie)` for the Playbill.
  - `DaydreamInput.tickSlot(Minecraft, int slot)` returns a boolean.
  - `ImpressionReelLayout(int guiWidth, int guiHeight)`, with `cell(int i)` (i in −2..2), `statusY()`, `reelY()`, `hintY()` and `SIDE = 2`.

**What Daydream feels like:**
- **Toggle.** Press the Daydream key once to start drawing and once more to stop, and the draft is saved if it changed.
- **Anchor.** Drawing starts from the block the wielder stands on, and the draft is shown turned to the way they face now. A scene written facing south and reopened facing east is drawn turned, exactly as Unveil would turn it.
- **Controls:**

  | Input | Effect |
  |---|---|
  | wheel | turns the impression reel |
  | Alt+wheel | cycles the brush |
  | right click | places |
  | left click | erases the drafted thing under the cursor |

- **Two-corner brushes.** A two-corner brush takes its first right click as a corner, drawn as a white box, and its second as the far corner.
- **Creatures.** A creature impression is always a single figment at the cursor, whatever the brush.
- **Real clicks.** Both clicks are cancelled, so drawing never mines or attacks.

The reel is frameless: shadowed names along the bottom of the screen, with the chosen one white and its neighbours muted. Above it is the brush and the budget, `23 / 48`. Below it is the hint line. Everything sits above `HUD_FLOOR = 40` px from the bottom, which is where vanilla's hotbar, hearts and XP bar live.

- [ ] **Step 1: Write the failing layout test**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImpressionReelLayoutTest {
    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1280, 720}};

    @Test
    void theReelNeverOverlapsItselfOrLeavesTheScreen() {
        for (int[] screen : SCREENS) {
            ImpressionReelLayout layout = new ImpressionReelLayout(screen[0], screen[1]);
            List<Rect> cells = new ArrayList<>();
            for (int i = -ImpressionReelLayout.SIDE; i <= ImpressionReelLayout.SIDE; i++) {
                cells.add(layout.cell(i));
            }
            for (int a = 0; a < cells.size(); a++) {
                Rect cell = cells.get(a);
                assertTrue(cell.x() >= ImpressionReelLayout.MARGIN && cell.right() <= screen[0] - ImpressionReelLayout.MARGIN,
                        cell + " leaves a " + screen[0] + "-wide screen");
                assertTrue(cell.w() >= ImpressionReelLayout.MIN_CELL, cell + " is too narrow to name anything");
                for (int b = a + 1; b < cells.size(); b++) {
                    assertFalse(cell.overlaps(cells.get(b)), cell + " overlaps " + cells.get(b));
                }
            }
        }
    }

    @Test
    void theThreeLinesStackAboveVanillasHud() {
        for (int[] screen : SCREENS) {
            ImpressionReelLayout layout = new ImpressionReelLayout(screen[0], screen[1]);
            assertTrue(layout.statusY() + ImpressionReelLayout.LINE <= layout.reelY());
            assertTrue(layout.reelY() + ImpressionReelLayout.LINE <= layout.hintY());
            assertTrue(layout.hintY() + ImpressionReelLayout.LINE <= screen[1] - ImpressionReelLayout.HUD_FLOOR,
                    "the hint runs into the hotbar at " + screen[0] + "x" + screen[1]);
        }
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.mind.ImpressionReelLayoutTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `ImpressionReelLayout`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;

/** Where Daydream's reel sits: three lines along the bottom, over vanilla's HUD, sized by the gui. */
public record ImpressionReelLayout(int guiWidth, int guiHeight) {
    public static final int SIDE = 2;
    public static final int MARGIN = 16;
    public static final int GAP = 6;
    public static final int LINE = 11;
    public static final int HUD_FLOOR = 40;
    public static final int MAX_CELL = 96;
    public static final int MIN_CELL = 40;

    public int cellWidth() {
        int slots = 2 * SIDE + 1;
        int room = guiWidth - 2 * MARGIN - GAP * (slots - 1);
        return Math.max(MIN_CELL, Math.min(MAX_CELL, room / slots));
    }

    public Rect cell(int index) {
        int width = cellWidth();
        int x = guiWidth / 2 + index * (width + GAP) - width / 2;
        return new Rect("reel" + index, x, reelY(), width, LINE);
    }

    public int hintY() {
        return guiHeight - HUD_FLOOR - LINE - 1;
    }

    public int reelY() {
        return hintY() - LINE - 1;
    }

    public int statusY() {
        return reelY() - LINE - 1;
    }
}
```

At 320 wide: `room = 320 - 32 - 24 = 264`, and `264 / 5 = 52`. That is at least `MIN_CELL`, and the reel spans `5*52 + 24 = 284`, which fits inside `320 - 32 = 288`.

- [ ] **Step 4: Run it and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.mind.ImpressionReelLayoutTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Write `DaydreamMode`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.mind.Brush;
import com.efkrdnz.magical.magic.mind.DraftRay;
import com.efkrdnz.magical.magic.mind.Impression;
import com.efkrdnz.magical.magic.mind.Lexicon;
import com.efkrdnz.magical.magic.mind.Offset;
import com.efkrdnz.magical.magic.mind.Reverie;
import com.efkrdnz.magical.magic.mind.ReverieNbt;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Daydream: the wielder draws their active reverie into the air around them with what they have
 * studied. A client-only draft; the server hears of it once, when drawing stops.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class DaydreamMode {
    public static final double REACH = 6.0;

    private static boolean active;
    private static Reverie draft;
    private static BlockPos anchor;
    private static int turns;
    private static int slot;
    private static int impression;
    private static Brush brush = Brush.POINT;
    private static BlockPos corner;
    private static boolean dirty;

    private DaydreamMode() {}

    public static boolean active() {
        return active;
    }

    public static void toggle(Minecraft minecraft) {
        if (active) {
            finish(true);
        } else {
            begin(minecraft);
        }
    }

    private static void begin(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        var mind = ClientMagicState.get().mind();
        slot = mind.activeSlot();
        draft = mind.active().copy();
        int facing = minecraft.player.getDirection().get2DDataValue();
        if (draft.isEmpty()) {
            draft.setFacing(facing);
        }
        turns = facing - draft.facing();
        anchor = minecraft.player.blockPosition();
        corner = null;
        dirty = false;
        impression = Math.min(impression, Math.max(0, keys().size() - 1));
        active = true;
    }

    public static void finish(boolean save) {
        if (active && save && dirty) {
            MagicalNetwork.sendSaveReverie(slot, ReverieNbt.save(draft));
        }
        active = false;
        draft = null;
        corner = null;
    }

    public static Reverie draft() { return draft; }
    public static int slot() { return slot; }
    public static Brush brush() { return brush; }
    public static BlockPos corner() { return corner; }

    /** The Playbill hands its edits back through here, so leaving Daydream saves them too. */
    public static void setDraft(Reverie edited) {
        if (active && edited != null) {
            draft = edited.copy();
            dirty = true;
        }
    }

    public static List<String> keys() {
        return new ArrayList<>(ClientMagicState.get().mind().lexicon().keys());
    }

    public static String impression() {
        List<String> keys = keys();
        return keys.isEmpty() ? null : keys.get(Math.floorMod(impression, keys.size()));
    }

    public static BlockPos world(Offset offset) {
        Offset turned = offset.rotate(turns);
        return anchor.offset(turned.dx(), turned.dy(), turned.dz());
    }

    public static Offset offset(BlockPos pos) {
        return new Offset(pos.getX() - anchor.getX(), pos.getY() - anchor.getY(), pos.getZ() - anchor.getZ()).rotate(-turns);
    }

    public static DraftRay.Hit cursor(Minecraft minecraft) {
        if (!active || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        return DraftRay.march(minecraft.player.getEyePosition(), minecraft.player.getViewVector(1.0F), REACH, pos ->
                !minecraft.level.getBlockState(pos).getCollisionShape(minecraft.level, pos).isEmpty()
                        || draft.blocks().stream().anyMatch(b -> b.at().equals(offset(pos))));
    }

    public static boolean handleScroll(double delta) {
        if (!active || delta == 0.0) {
            return false;
        }
        if (Screen.hasAltDown()) {
            brush = brush.next();
            corner = null;
        } else {
            impression += delta > 0 ? -1 : 1;
        }
        return true;
    }

    public static boolean handleMouseButton(int button, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.screen != null || action != GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            place(minecraft);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            erase(minecraft);
            return true;
        }
        return false;
    }

    private static void place(Minecraft minecraft) {
        Impression chosen = Impression.parse(impression());
        DraftRay.Hit hit = cursor(minecraft);
        if (chosen == null || hit == null) {
            return;
        }
        Lexicon lexicon = ClientMagicState.get().mind().lexicon();
        Reverie.Refusal worst = Reverie.Refusal.NONE;
        if (chosen.kind() == Impression.Kind.CREATURE) {
            worst = note(worst, draft.addFigment(offset(hit.place()), chosen.id(), lexicon));
        } else if (brush == Brush.POINT || corner != null) {
            BlockPos from = brush == Brush.POINT ? hit.place() : corner;
            for (Offset cell : brush.cells(offset(from), offset(hit.place()))) {
                Reverie.Refusal refusal = draft.addBlock(cell, chosen.id(), lexicon);
                worst = note(worst, refusal);
                if (refusal == Reverie.Refusal.FULL) {
                    break;
                }
            }
            corner = null;
        } else {
            corner = hit.place();
            return;
        }
        dirty = true;
        if (worst != Reverie.Refusal.NONE && worst != Reverie.Refusal.OCCUPIED && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.magical.reverie_refused",
                    Component.translatable("mind.magical.refusal." + worst.name().toLowerCase(Locale.ROOT))), true);
        }
    }

    private static Reverie.Refusal note(Reverie.Refusal worst, Reverie.Refusal next) {
        return worst == Reverie.Refusal.NONE ? next : worst;
    }

    private static void erase(Minecraft minecraft) {
        DraftRay.Hit hit = cursor(minecraft);
        if (hit == null) {
            return;
        }
        boolean removed = hit.solid() != null && draft.remove(offset(hit.solid()));
        if (!removed) {
            removed = draft.remove(offset(hit.place()));
        }
        dirty |= removed;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active && (minecraft.player == null || !ClientMagicState.get().hasAuthority(AuthorityContent.MIND))) {
            finish(false);
        }
    }
}
```

`note` keeps the first refusal that is not `NONE`, so the actionbar names the first reason a stroke fell short.

- [ ] **Step 6: Write `DaydreamInput`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.MagicalKeyMappings;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.client.Minecraft;

/** The Daydream key is a toggle the client owns: no cast packet is ever sent for it. */
public final class DaydreamInput {
    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private DaydreamInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        boolean daydreamSlot = ClientMagicState.get().hasAuthority(AuthorityContent.MIND)
                && MagicContent.DAYDREAM.id().equals(ClientMagicState.get().equippedSkill(slot));
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!daydreamSlot) {
            WAS_DOWN[slot] = false;
            return false;
        }
        if (down && !WAS_DOWN[slot]) {
            DaydreamMode.toggle(minecraft);
        }
        WAS_DOWN[slot] = down;
        return true;
    }
}
```

Check that `MagicalKeyMappings` is in `com.efkrdnz.magical.client`: `CausalityAuthorityInput` uses it unqualified from that package.

- [ ] **Step 7: Write `DraftRenderer` and `ImpressionReelOverlay`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.magic.mind.DraftRay;
import com.efkrdnz.magical.magic.mind.Figment;
import com.efkrdnz.magical.magic.mind.ImaginedBlock;
import com.efkrdnz.magical.magic.mind.Impression;
import com.efkrdnz.magical.magic.mind.Offset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** The draft in lilac, and where the next stroke will land in white. */
public final class DraftRenderer {
    private static final float DRAFT_ALPHA = 0.6F;
    private static final float EDGE_ALPHA = 0.5F;
    private static final int CURSOR = 0xFFFFFF;
    private static final float CURSOR_ALPHA = 0.9F;

    private DraftRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (!DaydreamMode.active() || minecraft.level == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        for (ImaginedBlock block : DaydreamMode.draft().blocks()) {
            IllusionRenderer.drawBlock(minecraft, pose, buffers, cam, state(block.blockId()), DaydreamMode.world(block.at()), DRAFT_ALPHA);
        }
        buffers.endBatch(RenderType.translucent());

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (ImaginedBlock block : DaydreamMode.draft().blocks()) {
            IllusionRenderer.drawEdge(pose, lines, cam, new AABB(DaydreamMode.world(block.at())), IllusionRenderer.LILAC, EDGE_ALPHA);
        }
        for (Figment figment : DaydreamMode.draft().figments()) {
            IllusionRenderer.drawEdge(pose, lines, cam, figmentBox(figment), IllusionRenderer.LILAC, CURSOR_ALPHA);
        }
        DraftRay.Hit hit = DaydreamMode.cursor(minecraft);
        if (hit != null) {
            BlockPos from = DaydreamMode.corner() != null ? DaydreamMode.corner() : hit.place();
            Impression chosen = Impression.parse(DaydreamMode.impression());
            boolean creature = chosen != null && chosen.kind() == Impression.Kind.CREATURE;
            var cells = creature ? java.util.List.of(DaydreamMode.offset(hit.place()))
                    : DaydreamMode.brush().cells(DaydreamMode.offset(from), DaydreamMode.offset(hit.place()));
            for (Offset cell : cells) {
                IllusionRenderer.drawEdge(pose, lines, cam, new AABB(DaydreamMode.world(cell)), CURSOR, CURSOR_ALPHA);
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    private static BlockState state(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? Blocks.AIR.defaultBlockState() : BuiltInRegistries.BLOCK.getValue(key).defaultBlockState();
    }

    private static AABB figmentBox(Figment figment) {
        BlockPos at = DaydreamMode.world(figment.at());
        var size = EntityType.byString(figment.creatureId()).map(EntityType::getDimensions).orElse(null);
        double half = size == null ? 0.3 : size.width() / 2.0;
        double height = size == null ? 1.95 : size.height();
        return new AABB(at.getX() + 0.5 - half, at.getY(), at.getZ() + 0.5 - half, at.getX() + 0.5 + half, at.getY() + height, at.getZ() + 0.5 + half);
    }
}
```

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** The impression reel along the bottom while Daydreaming: frameless, shadowed, three lines. */
public final class ImpressionReelOverlay {
    private static final int CHOSEN = 0xFFFFFFFF;
    private static final int NEIGHBOUR = 0xFF9A93B0;
    private static final int STATUS = 0xFFBDA4FF;
    private static final int HINT = 0xFF8C86A0;

    private ImpressionReelOverlay() {}

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!DaydreamMode.active() || minecraft.screen != null) {
            return;
        }
        ImpressionReelLayout layout = new ImpressionReelLayout(graphics.guiWidth(), graphics.guiHeight());
        List<String> keys = DaydreamMode.keys();
        String chosen = DaydreamMode.impression();
        int at = chosen == null ? 0 : keys.indexOf(chosen);
        for (int i = -ImpressionReelLayout.SIDE; i <= ImpressionReelLayout.SIDE && !keys.isEmpty(); i++) {
            String key = keys.get(Math.floorMod(at + i, keys.size()));
            Rect cell = layout.cell(i);
            String name = minecraft.font.plainSubstrByWidth(MindGazeService.displayName(key).getString(), cell.w());
            int x = cell.x() + (cell.w() - minecraft.font.width(name)) / 2;
            graphics.drawString(minecraft.font, name, x, cell.y(), i == 0 ? CHOSEN : NEIGHBOUR, true);
        }
        var lexicon = ClientMagicState.get().mind().lexicon();
        Component status = Component.translatable("mind.magical.brush." + DaydreamMode.brush().name().toLowerCase(Locale.ROOT))
                .append("   " + DaydreamMode.draft().size() + " / " + lexicon.budget());
        graphics.drawString(minecraft.font, status, (graphics.guiWidth() - minecraft.font.width(status)) / 2, layout.statusY(), STATUS, true);
        Component hint = Component.translatable("mind.magical.daydream.hint", minecraft.options.keyInventory.getTranslatedKeyMessage());
        String line = minecraft.font.plainSubstrByWidth(hint.getString(), graphics.guiWidth() - 2 * ImpressionReelLayout.MARGIN);
        graphics.drawString(minecraft.font, line, (graphics.guiWidth() - minecraft.font.width(line)) / 2, layout.hintY(), HINT, true);
    }
}
```

- [ ] **Step 8: Wire the client**

In `MagicalClientEvents.Hud.onClientTick`, directly before the `CausalityAuthorityInput.tickSlot` block (line 291):

```java
                if (com.efkrdnz.magical.client.mind.DaydreamInput.tickSlot(minecraft, i)) {
                    while (MagicalKeyMappings.CAST_SLOTS[i].consumeClick()) {
                        // Daydream is a toggle the client owns; nothing is cast.
                    }
                    continue;
                }
```

In `dispatchScroll`, make the first term:

```java
            return com.efkrdnz.magical.client.mind.DaydreamMode.handleScroll(scrollDeltaY)
                    || SovereignAegisInput.handleScroll(scrollDeltaY)
```

In `dispatchMouseButton`, make the first term:

```java
            return com.efkrdnz.magical.client.mind.DaydreamMode.handleMouseButton(button, action)
                    || CausalAnchorOverlay.handleMouseButton(button, action)
```

In `renderLevelOverlays`, in the `AFTER_CUTOUT_BLOCKS` branch after the Belief Sight line:

```java
                com.efkrdnz.magical.client.mind.DraftRenderer.render(event, minecraft);
```

In `HudLayers.renderSelector`, after the `GazeEyeOverlay` line:

```java
        com.efkrdnz.magical.client.mind.ImpressionReelOverlay.render(graphics, minecraft);
```

- [ ] **Step 9: Build and capture**

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL.

Capture. The Daydream key is a toggle, so `-PautoHold` would press it once. `-PautoClick` right-clicks place blocks while the window has focus. Daydream starts at 120 from a hold of slot 2, and the scroll turns to stone:

```powershell
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;magical reset;magical hud race human;magical class unlock mystic;magical authority set authority_of_mind;magical mind lexicon block minecraft:stone 20;magical mind lexicon block minecraft:cobblestone 5;magical mind lexicon creature minecraft:cat 5;magical hud equip 1 daydream;time set day;tp @s ~ ~ ~ 0 30" -PautoHold=cast_slot_2 -PautoClick="130:scroll-down;140:click-right;150:scroll-down;160:click-right" -PautoScreenshot=135,145,165 -PautoExit
```

Expected: the reel along the bottom with stone chosen. A white cursor box sits on the ground ahead, then a lilac stone block, then a second one.

If `-PautoHold` releases the key at the first screenshot and toggles Daydream off, that is fine: the draft was saved, and the frames before it show the mode. If it toggles off before 135, use `-PautoClick="120:hold;..."` with no release instead.

- [ ] **Step 10: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/client/mind/DaydreamMode.java src/main/java/com/efkrdnz/magical/client/mind/DaydreamInput.java src/main/java/com/efkrdnz/magical/client/mind/DraftRenderer.java src/main/java/com/efkrdnz/magical/client/mind/ImpressionReelLayout.java src/main/java/com/efkrdnz/magical/client/mind/ImpressionReelOverlay.java src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java src/main/java/com/efkrdnz/magical/client/hud/HudLayers.java src/test/java/com/efkrdnz/magical/client/mind/ImpressionReelLayoutTest.java
git commit -m "feat: Daydream - a reverie drawn into the air, one studied impression at a time"
```

---

### Task 16: The Playbill, with the Forecast

**Files:**
- Create: `client/screen/mind/PlaybillLayout.java`, `client/screen/mind/PlaybillScreen.java`, `network/OpenPlaybillPayload.java`
- Modify:
  - `client/mind/DaydreamMode.java`: the inventory key opens the Playbill.
  - `network/MagicalNetwork.java`: `OpenPlaybillPayload` and `sendOpenPlaybill`.
  - `client/ClientPayloadHandlers.java`: its handler.
  - `magic/mind/MindCommands.java`: `playbill`.
- Test: `client/screen/mind/PlaybillLayoutTest.java`

**Interfaces:**
- Consumes:
  - `DaydreamMode.active()`, `draft()`, `slot()`, `setDraft(Reverie)` (Task 15);
  - `Plausibility`, `Belief.ticksToReach`, `Belief.SURE`, `Susceptibility`, `UnveilCost`, `LevelMindWorld` (Tasks 4, 5 and 8);
  - `MagicalNetwork.sendSaveReverie` (Task 14).
- Produces:
  - `PlaybillLayout(int guiWidth, int guiHeight)`:
    - `tab(int)`, `row(int)`, `sense(int)`, `stance(int)`, `reaction(int)`, `save()` and `done()`, each returning a `Rect`;
    - `rows()`, `forecastX()`, `forecastWidth()`, `contentTop()`;
    - the hit tests `tabAt`, `rowAt`, `senseAt`, `stanceAt` and `reactionAt`, each returning an index or −1.
  - `PlaybillScreen.open()`.

The Playbill is the fourth frameless surface. It uses the Grimoire's scrim (`0xA6060B14`), shadowed text and no panel. It is `HudQuiet`, so the mod's HUD and vanilla's hotbar stand down, and `HudDebug.Captured`, so a capture can photograph it.

It has three columns under a row of three tabs:
- **Elements** (left): a cluster as `Stone x12`, a figment by its name.
- **The selected element** (middle): its three senses, and for a figment a Stance column and a Reaction column. A lit word is gold; an unlit one is muted.
- **The Forecast** (right). It runs `Plausibility` on the selected element as it would land where the wielder is looking right now, through `LevelMindWorld` over the client level. It prints:
  - every term with its sign;
  - the senses multiplier;
  - **Certain in** for a zombie, a skeleton, a player and an enderman (ticks to `Belief.SURE` over 20, to one decimal, or "never");
  - the unveil cost.

A tab switches the slot being edited, saving the one being left if it changed. **Save** sends the working copy. **Done** saves if dirty and closes. Opened from Daydream, the Playbill edits the Daydream draft itself and hands the edits back through `DaydreamMode.setDraft`.

Geometry is a function of the gui, never a constant the screen has to be big enough for:
- the columns are thirds of the width;
- Stance and Reaction share the middle column side by side;
- nothing goes below `guiHeight - MARGIN`.

`PlaybillLayoutTest` pins it from 320x240 up.

- [ ] **Step 1: Write the failing layout test**

```java
package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaybillLayoutTest {
    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1280, 720}};

    private static List<Rect> everything(PlaybillLayout layout) {
        List<Rect> rects = new ArrayList<>();
        for (int i = 0; i < 3; i++) rects.add(layout.tab(i));
        for (int i = 0; i < layout.rows(); i++) rects.add(layout.row(i));
        for (int i = 0; i < 3; i++) rects.add(layout.sense(i));
        for (int i = 0; i < 4; i++) rects.add(layout.stance(i));
        for (int i = 0; i < 5; i++) rects.add(layout.reaction(i));
        rects.add(layout.save());
        rects.add(layout.done());
        return rects;
    }

    @Test
    void nothingOverlapsAndEverythingIsOnScreen() {
        for (int[] screen : SCREENS) {
            PlaybillLayout layout = new PlaybillLayout(screen[0], screen[1]);
            List<Rect> rects = everything(layout);
            for (int a = 0; a < rects.size(); a++) {
                Rect rect = rects.get(a);
                assertTrue(rect.x() >= 0 && rect.right() <= screen[0] && rect.y() >= 0 && rect.bottom() <= screen[1],
                        rect + " leaves " + screen[0] + "x" + screen[1]);
                for (int b = a + 1; b < rects.size(); b++) {
                    assertFalse(rect.overlaps(rects.get(b)), rect + " overlaps " + rects.get(b) + " at " + screen[0] + "x" + screen[1]);
                }
            }
            assertTrue(layout.rows() >= 6, "fewer than six element rows at " + screen[0] + "x" + screen[1]);
            assertTrue(layout.forecastX() >= layout.stance(0).right(), "the forecast runs under the script");
            assertTrue(layout.forecastX() + layout.forecastWidth() <= screen[0]);
        }
    }

    @Test
    void everyHitTestAnswersAtItsCentreAndNothingInTheGaps() {
        PlaybillLayout layout = new PlaybillLayout(427, 240);
        for (int i = 0; i < 3; i++) {
            assertEquals(i, layout.tabAt(cx(layout.tab(i)), cy(layout.tab(i))));
            assertEquals(i, layout.senseAt(cx(layout.sense(i)), cy(layout.sense(i))));
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(i, layout.stanceAt(cx(layout.stance(i)), cy(layout.stance(i))));
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(i, layout.reactionAt(cx(layout.reaction(i)), cy(layout.reaction(i))));
        }
        assertEquals(2, layout.rowAt(cx(layout.row(2)), cy(layout.row(2))));
        assertEquals(-1, layout.tabAt(layout.tab(0).right() + PlaybillLayout.GAP / 2.0, cy(layout.tab(0))));
        assertEquals(-1, layout.rowAt(layout.row(0).right() + PlaybillLayout.GAP / 2.0, cy(layout.row(0))));
    }

    private static double cx(Rect rect) {
        return rect.x() + rect.w() / 2.0;
    }

    private static double cy(Rect rect) {
        return rect.y() + rect.h() / 2.0;
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.screen.mind.PlaybillLayoutTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write `PlaybillLayout`**

```java
package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;

/** The Playbill's geometry: three tabs over three columns, all of it a function of the gui. */
public record PlaybillLayout(int guiWidth, int guiHeight) {
    public static final int MARGIN = 16;
    public static final int GAP = 10;
    public static final int LINE = 12;
    public static final int TOP = 14;
    public static final int MAX_TAB = 72;
    public static final int BUTTON = 44;

    public int columnWidth() {
        return (guiWidth - 2 * MARGIN - 2 * GAP) / 3;
    }

    private int column(int i) {
        return MARGIN + i * (columnWidth() + GAP);
    }

    public Rect tab(int i) {
        int width = Math.min(MAX_TAB, columnWidth());
        return new Rect("tab" + i, MARGIN + i * (width + GAP), TOP, width, LINE);
    }

    public int contentTop() {
        return TOP + 2 * LINE;
    }

    private int floor() {
        return guiHeight - MARGIN - LINE - 4;
    }

    public int rows() {
        return (floor() - contentTop()) / LINE;
    }

    public Rect row(int i) {
        return new Rect("row" + i, column(0), contentTop() + i * LINE, columnWidth(), LINE);
    }

    /** Senses start one line down: the line above is their heading. */
    public Rect sense(int i) {
        return new Rect("sense" + i, column(1), contentTop() + (i + 1) * LINE, columnWidth(), LINE);
    }

    private int halfWidth() {
        return (columnWidth() - GAP) / 2;
    }

    public Rect stance(int i) {
        return new Rect("stance" + i, column(1), contentTop() + (i + 6) * LINE, halfWidth(), LINE);
    }

    public Rect reaction(int i) {
        return new Rect("reaction" + i, column(1) + halfWidth() + GAP, contentTop() + (i + 6) * LINE, halfWidth(), LINE);
    }

    public int forecastX() {
        return column(2);
    }

    public int forecastWidth() {
        return columnWidth();
    }

    public Rect done() {
        return new Rect("done", guiWidth - MARGIN - BUTTON, guiHeight - MARGIN - LINE, BUTTON, LINE);
    }

    public Rect save() {
        return new Rect("save", guiWidth - MARGIN - 2 * BUTTON - GAP, guiHeight - MARGIN - LINE, BUTTON, LINE);
    }

    public int tabAt(double x, double y) {
        return hit(x, y, 3, this::tab);
    }

    public int rowAt(double x, double y) {
        return hit(x, y, rows(), this::row);
    }

    public int senseAt(double x, double y) {
        return hit(x, y, 3, this::sense);
    }

    public int stanceAt(double x, double y) {
        return hit(x, y, 4, this::stance);
    }

    public int reactionAt(double x, double y) {
        return hit(x, y, 5, this::reaction);
    }

    public static boolean inside(Rect rect, double x, double y) {
        return x >= rect.x() && x < rect.right() && y >= rect.y() && y < rect.bottom();
    }

    private static int hit(double x, double y, int count, java.util.function.IntFunction<Rect> rect) {
        for (int i = 0; i < count; i++) {
            if (inside(rect.apply(i), x, y)) {
                return i;
            }
        }
        return -1;
    }
}
```

Check it at 320x240:
- `columnWidth = (320-32-20)/3 = 89`.
- `contentTop = 14 + 24 = 38`, `floor = 240 - 16 - 12 - 4 = 208`, `rows = 170 / 12 = 14`.
- The last reaction row ends at `38 + 11*12 = 170`, which is less than 208.
- The buttons sit at y 212..224 on the right. The last element row ends at `38 + 14*12 = 206`, and the list is in the left column, so neither the buttons nor the last row overlap anything.

- [ ] **Step 4: Run it and confirm it passes**

Run: `.\gradlew test --tests "com.efkrdnz.magical.client.screen.mind.PlaybillLayoutTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Write `PlaybillScreen`**

```java
package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.hud.HudQuiet;
import com.efkrdnz.magical.client.mind.DaydreamMode;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.mind.Belief;
import com.efkrdnz.magical.magic.mind.Figment;
import com.efkrdnz.magical.magic.mind.ImaginedBlock;
import com.efkrdnz.magical.magic.mind.LevelMindWorld;
import com.efkrdnz.magical.magic.mind.Lexicon;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import com.efkrdnz.magical.magic.mind.MindState;
import com.efkrdnz.magical.magic.mind.Offset;
import com.efkrdnz.magical.magic.mind.Plausibility;
import com.efkrdnz.magical.magic.mind.Reaction;
import com.efkrdnz.magical.magic.mind.Reverie;
import com.efkrdnz.magical.magic.mind.ReverieNbt;
import com.efkrdnz.magical.magic.mind.Script;
import com.efkrdnz.magical.magic.mind.Sense;
import com.efkrdnz.magical.magic.mind.Stance;
import com.efkrdnz.magical.magic.mind.Susceptibility;
import com.efkrdnz.magical.magic.mind.UnveilCost;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The reverie editor: elements, the selected element's script and senses, and the Forecast. */
public final class PlaybillScreen extends Screen implements HudDebug.Captured, HudQuiet {
    private static final int SCRIM = 0xA6060B14;
    private static final int BRIGHT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8C86A0;
    private static final int LIT = 0xFFEFC86A;
    private static final int LILAC = 0xFFBDA4FF;
    private static final int GAIN = 0xFF9FE0A8;
    private static final int LOSS = 0xFFF08C8C;
    private static final String[] FORECAST_VIEWERS = {"minecraft:zombie", "minecraft:skeleton", "minecraft:player", "minecraft:enderman"};

    private int slot;
    private Reverie working;
    private int selected;
    private boolean dirty;

    private PlaybillScreen() {
        super(Component.translatable("screen.magical.playbill"));
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new PlaybillScreen());
        }
    }

    @Override
    protected void init() {
        if (working == null) {
            load(DaydreamMode.active() ? DaydreamMode.slot() : ClientMagicState.get().mind().activeSlot());
        }
    }

    private void load(int next) {
        slot = next;
        working = DaydreamMode.active() && DaydreamMode.slot() == next
                ? DaydreamMode.draft().copy() : ClientMagicState.get().mind().reverie(next).copy();
        selected = 0;
        dirty = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private PlaybillLayout layout() {
        return new PlaybillLayout(width, height);
    }

    private int clusterCount() {
        return working.clusters().size();
    }

    private boolean figmentSelected() {
        return selected >= clusterCount() && selected < clusterCount() + working.figments().size();
    }

    private Set<Sense> selectedSenses() {
        if (selected < clusterCount()) {
            return working.clusters().get(selected).get(0).senses();
        }
        return figmentSelected() ? working.figments().get(selected - clusterCount()).senses() : Set.of();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        PlaybillLayout layout = layout();
        int tab = layout.tabAt(x, y);
        if (tab >= 0 && tab < MindState.SLOTS) {
            if (dirty) {
                save();
            }
            load(tab);
            return true;
        }
        int row = layout.rowAt(x, y);
        if (row >= 0 && row < clusterCount() + working.figments().size()) {
            selected = row;
            return true;
        }
        int sense = layout.senseAt(x, y);
        if (sense >= 0 && (selected < clusterCount() || figmentSelected())) {
            EnumSet<Sense> next = selectedSenses().isEmpty() ? EnumSet.noneOf(Sense.class) : EnumSet.copyOf(selectedSenses());
            Sense flipped = Sense.values()[sense];
            if (!next.remove(flipped)) {
                next.add(flipped);
            }
            if (selected < clusterCount()) {
                working.setClusterSenses(working.clusters().get(selected).get(0).at(), next);
            } else {
                working.setFigmentSenses(selected - clusterCount(), next);
            }
            dirty = true;
            return true;
        }
        if (figmentSelected()) {
            int index = selected - clusterCount();
            Script script = working.figments().get(index).script();
            int stance = layout.stanceAt(x, y);
            int reaction = layout.reactionAt(x, y);
            if (stance >= 0) {
                working.setScript(index, new Script(Stance.values()[stance], script.reaction()));
                dirty = true;
                return true;
            }
            if (reaction >= 0) {
                working.setScript(index, new Script(script.stance(), Reaction.values()[reaction]));
                dirty = true;
                return true;
            }
        }
        if (PlaybillLayout.inside(layout.save(), x, y)) {
            save();
            return true;
        }
        if (PlaybillLayout.inside(layout.done(), x, y)) {
            onClose();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private void save() {
        MagicalNetwork.sendSaveReverie(slot, ReverieNbt.save(working));
        if (DaydreamMode.active() && DaydreamMode.slot() == slot) {
            DaydreamMode.setDraft(working);
        }
        dirty = false;
    }

    @Override
    public void onClose() {
        if (dirty) {
            save();
        }
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, SCRIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        PlaybillLayout layout = layout();
        for (int i = 0; i < MindState.SLOTS; i++) {
            Rect tab = layout.tab(i);
            text(g, Component.translatable("screen.magical.playbill.slot", i + 1), tab, i == slot ? BRIGHT : MUTED);
            if (i == slot) {
                g.fill(tab.x(), tab.bottom() - 1, tab.right(), tab.bottom(), LILAC);
            }
        }
        List<Component> names = elementNames();
        if (names.isEmpty()) {
            text(g, Component.translatable("screen.magical.playbill.empty"), layout.row(0), MUTED);
        }
        for (int i = 0; i < names.size() && i < layout.rows(); i++) {
            text(g, names.get(i), layout.row(i), i == selected ? BRIGHT : MUTED);
        }
        if (!names.isEmpty()) {
            renderScript(g, layout);
            renderForecast(g, layout);
        }
        text(g, Component.translatable("screen.magical.playbill.save"), layout.save(), dirty ? LIT : MUTED);
        text(g, Component.translatable("screen.magical.playbill.done"), layout.done(), BRIGHT);
    }

    private List<Component> elementNames() {
        List<Component> names = new ArrayList<>();
        for (List<ImaginedBlock> cluster : working.clusters()) {
            names.add(Component.translatable("screen.magical.playbill.cluster",
                    MindGazeService.displayName("block:" + cluster.get(0).blockId()), cluster.size()));
        }
        for (Figment figment : working.figments()) {
            names.add(MindGazeService.displayName("creature:" + figment.creatureId()));
        }
        return names;
    }

    private void renderScript(GuiGraphics g, PlaybillLayout layout) {
        Rect heading = layout.sense(0);
        g.drawString(font, Component.translatable("screen.magical.playbill.senses"), heading.x(), heading.y() - PlaybillLayout.LINE, MUTED, true);
        Set<Sense> senses = selectedSenses();
        for (Sense sense : Sense.values()) {
            text(g, Component.translatable("mind.magical.sense." + lower(sense)), layout.sense(sense.ordinal()),
                    senses.contains(sense) ? LIT : MUTED);
        }
        if (!figmentSelected()) {
            return;
        }
        Script script = working.figments().get(selected - clusterCount()).script();
        g.drawString(font, Component.translatable("screen.magical.playbill.stance"), layout.stance(0).x(),
                layout.stance(0).y() - PlaybillLayout.LINE, MUTED, true);
        g.drawString(font, Component.translatable("screen.magical.playbill.reaction"), layout.reaction(0).x(),
                layout.reaction(0).y() - PlaybillLayout.LINE, MUTED, true);
        for (Stance stance : Stance.values()) {
            text(g, Component.translatable("mind.magical.stance." + lower(stance)), layout.stance(stance.ordinal()),
                    stance == script.stance() ? LIT : MUTED);
        }
        for (Reaction reaction : Reaction.values()) {
            text(g, Component.translatable("mind.magical.reaction." + lower(reaction)), layout.reaction(reaction.ordinal()),
                    reaction == script.reaction() ? LIT : MUTED);
        }
    }

    private void renderForecast(GuiGraphics g, PlaybillLayout layout) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        BlockPos anchor = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos().relative(hit.getDirection()) : minecraft.player.blockPosition();
        int turns = minecraft.player.getDirection().get2DDataValue() - working.facing();
        Lexicon lexicon = ClientMagicState.get().mind().lexicon();
        LevelMindWorld world = new LevelMindWorld(minecraft.level);
        Plausibility.Reading reading;
        if (selected < clusterCount()) {
            List<Plausibility.Placed> placed = new ArrayList<>();
            for (ImaginedBlock block : working.clusters().get(selected)) {
                BlockPos at = at(anchor, block.at(), turns);
                placed.add(new Plausibility.Placed(at.getX(), at.getY(), at.getZ(), block.blockId()));
            }
            reading = Plausibility.cluster(world, placed, lexicon, working.size());
        } else {
            Figment figment = working.figments().get(selected - clusterCount());
            BlockPos at = at(anchor, figment.at(), turns);
            reading = Plausibility.figment(world, new Plausibility.Placed(at.getX(), at.getY(), at.getZ(), figment.creatureId()),
                    figment.script(), lexicon, working.size());
        }
        int x = layout.forecastX();
        int y = layout.contentTop();
        int width = layout.forecastWidth();
        y = line(g, Component.translatable("screen.magical.playbill.plausibility"), String.format(Locale.ROOT, "%.2f", reading.p()), x, y, width, BRIGHT);
        for (Plausibility.Term term : reading.terms()) {
            y = line(g, Component.translatable("mind.magical.term." + term.key()),
                    String.format(Locale.ROOT, "%+.2f", term.value()), x, y, width, term.value() >= 0 ? GAIN : LOSS);
        }
        float senses = Sense.multiplier(selectedSenses());
        y = line(g, Component.translatable("screen.magical.playbill.senses"), String.format(Locale.ROOT, "x%.2f", senses), x, y, width, BRIGHT);
        y += 4;
        g.drawString(font, Component.translatable("screen.magical.playbill.certain_in"), x, y, MUTED, true);
        y += PlaybillLayout.LINE;
        for (String viewer : FORECAST_VIEWERS) {
            int ticks = Belief.ticksToReach(Belief.SURE, reading.p(), senses, Susceptibility.of(viewer), 1.0F);
            Component who = net.minecraft.world.entity.EntityType.byString(viewer)
                    .map(net.minecraft.world.entity.EntityType::getDescription).orElse(Component.literal(viewer));
            String value = ticks < 0 ? Component.translatable("screen.magical.playbill.never").getString()
                    : String.format(Locale.ROOT, "%.1fs", ticks / 20.0F);
            y = line(g, who, value, x, y, width, BRIGHT);
        }
        y += 4;
        line(g, Component.translatable("screen.magical.playbill.cost"), UnveilCost.of(working) + "", x, y, width, LILAC);
    }

    private static BlockPos at(BlockPos anchor, Offset offset, int turns) {
        Offset turned = offset.rotate(turns);
        return anchor.offset(turned.dx(), turned.dy(), turned.dz());
    }

    /** A label left and its value right-aligned, on one line of the forecast; returns the next line's y. */
    private int line(GuiGraphics g, Component label, String value, int x, int y, int width, int valueColour) {
        int valueWidth = font.width(value);
        String cut = font.plainSubstrByWidth(label.getString(), Math.max(0, width - valueWidth - 6));
        g.drawString(font, cut, x, y, MUTED, true);
        g.drawString(font, value, x + width - valueWidth, y, valueColour, true);
        return y + PlaybillLayout.LINE;
    }

    private void text(GuiGraphics g, Component text, Rect rect, int colour) {
        String cut = font.plainSubstrByWidth(text.getString(), rect.w());
        g.drawString(font, cut, rect.x(), rect.y() + 2, colour, true);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
```

The forecast names its viewers through `EntityType.getDescription` rather than `MindGazeService.displayName`, because a player is never an impression and `displayName` would print the raw key for one.

The hairline under the lit tab is a one-pixel mark, not a frame. `client/screen/mind` is outside `client/hud`, so `fill` is allowed here.

- [ ] **Step 6: Open it from Daydream and by command**

In `DaydreamMode`, add a `Pre` tick handler. It must run before vanilla's `handleKeybinds`, which would otherwise open the inventory:

```java
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active && minecraft.screen == null && minecraft.options.keyInventory.consumeClick()) {
            while (minecraft.options.keyInventory.consumeClick()) {
                // one press, one Playbill
            }
            com.efkrdnz.magical.client.screen.mind.PlaybillScreen.open();
        }
    }
```

`OpenPlaybillPayload.java`, following `OpenCausalBoardPayload` exactly: an empty record with `TYPE` `magical:open_playbill` and `StreamCodec.unit(new OpenPlaybillPayload())`. Copy that class and rename it.

Register it in `MagicalNetwork` beside `OpenCausalBoardPayload`:

```java
                .playToClient(OpenPlaybillPayload.TYPE, OpenPlaybillPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
```

```java
    public static void sendOpenPlaybill(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenPlaybillPayload());
    }
```

In `ClientPayloadHandlers`:

```java
    public static void handle(OpenPlaybillPayload payload) {
        com.efkrdnz.magical.client.screen.mind.PlaybillScreen.open();
    }
```

In `MindCommands.build()`, add:

```java
                .then(Commands.literal("playbill").executes(c -> run(c, player -> {
                    com.efkrdnz.magical.network.MagicalNetwork.sendOpenPlaybill(player);
                    return 1;
                })))
```

- [ ] **Step 7: Build and capture**

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL.

Capture. The wall preset is in slot 1 and the cat in slot 2. Open the Playbill after the chat fades, click the cat's tab and its row, then light Scent:

```powershell
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;magical reset;magical hud race human;magical class unlock mystic;magical authority set authority_of_mind;magical mind lexicon creature minecraft:cat 20;magical mind preset 1 wall;magical mind preset 2 cat;magical mind slot 1;time set day;tp @s ~ ~ ~ 0 20;251:magical mind playbill" -PautoClick="268:112,21;280:40,45;292:173,81" -PautoScreenshot=262,275,286,300 -PautoExit
```

The click points are gui coordinates at scale 3 (427x240): tab 2 is at x 98..170 and y 14..26, the first row is at y 38..50, and the Scent row is in the middle column at `38 + 3*12`. Recompute from `PlaybillLayout` if the auto scale differs.

Expected:
- 262 shows the wall's reading.
- 275 shows slot 2.
- 286 shows the cat selected, with its Guard and Stare lit and Sound lit.
- 300 shows Scent lit, and Certain in shortened by the 1.10 factor.

- [ ] **Step 8: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/client/screen/mind/PlaybillLayout.java src/main/java/com/efkrdnz/magical/client/screen/mind/PlaybillScreen.java src/main/java/com/efkrdnz/magical/network/OpenPlaybillPayload.java src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java src/main/java/com/efkrdnz/magical/client/ClientPayloadHandlers.java src/main/java/com/efkrdnz/magical/client/mind/DaydreamMode.java src/main/java/com/efkrdnz/magical/magic/mind/MindCommands.java src/test/java/com/efkrdnz/magical/client/screen/mind/PlaybillLayoutTest.java
git commit -m "feat: the Playbill - senses, scripts, and a forecast that teaches what makes a lie believable"
```

---

### Task 17: The guide, the whole suite, the capture, delivery

**Files:**
- Modify: `CLAUDE.md`, appending a section after `### Authority of Causality: the board and the ledger`. Commit it **from the index only**: a peer session has uncommitted hunks in this file.

- [ ] **Step 1: Write the CLAUDE.md section**

Append this section to the working-tree `CLAUDE.md`, directly before `### Sword school: the stances`:

```markdown
### Authority of Mind: illusions believed per viewer

Layer -6 with the other authorities. **Reality is consensus**, and stage 1 is the half of it where
nothing becomes real: the wielder writes a scene, sets it down, and every mind that sees it decides
for itself how much it is there. The shape is a **belief matrix**, viewer by element - which is why
this Authority cannot be pressed at one target and why the same wall is solid to one husk and
air to the next. Stage 2 (manifestation, Insist, phantom harm) and stage 3 (the Dream) are in the
design and not in the code.

**The Lexicon** (`magic/mind/Lexicon`, on `PlayerMagicState.mind()`, saved) is what can be imagined:
gazes per impression (`block:<id>`, `creature:<id>`, never a player). `MindGazeService` counts a
still look - 40 ticks a block, 60 a creature - as one gaze; fidelity is 1/2/3 at 1/5/20 gazes and
is worth plausibility; the budget is `16 + 2 * impressions` elements, capped at 128. A wielder who
has only looked at stone can only lie in stone. `GazeEyeOverlay` runs its own `GazeTracker` over the
client crosshair purely to draw the eye filling; the server decides what is learned.

**A reverie** (`Reverie`, three slots on `MindState`) is imagined blocks and figments at offsets
from an anchor, in the facing it was written in. Face-connected blocks are a **cluster** and a
cluster is one element: believed, sensed and shattered whole. Senses (Sound x1.25, Shadow x1.15,
Scent x1.10) multiply the gain and cost 10 mana a layer. Figments carry a `Script` (stance: Idle,
Wander, Guard, Follow; reaction: Ignore, Approach, Flee, Stare, Chase).

**Plausibility** (`Plausibility`, pure, pinned by `PlausibilityTest`) starts at 0.5 and is moved
by support, context within 8, alien material, fidelity, habitat, whether the script is like the
kind, and size; `LiveScene` re-reads it every 100 ticks because the world around a lie changes.
**Belief** (`Belief`): `b += 0.02 * p * senses * susceptibility * novelty * (1 - b)` while seen,
-0.002 a tick while not, and contradiction is the only fast way down - touch -0.60, a projectile
through it -0.35, watching someone else touch it -0.20, a figment's hollow strike -0.25. Below 0.1
it **shatters** for that viewer, never to be seen again, and `Scepticism` halves their novelty for
every impression in it for 6000 ticks. The wielder's susceptibility is zero by construction: they
are never a viewer.

**The runtime** (`MindService`, never saved) holds at most two `LiveScene`s per wielder for 1200
ticks. A touch is **edge-triggered** (`LiveScene.inside`): standing in a lie is one contradiction,
not twenty a second, and a viewer inside a lie gains nothing on it. Imagined blocks are never
placed: the server finds a body that walks into one by its box, and **mobs path by what they
believe** through the mod's first mixin, `mixin/WalkNodeEvaluatorMixin` on
`WalkNodeEvaluator.getPathType(PathfindingContext,int,int,int)` - BLOCKED for a believed imagined
solid, WALKABLE for open air over a believed imagined floor, both at `Belief.PATHING` (0.3), vanilla
otherwise, and one boolean (`MindService.anyLive`) when no lie stands anywhere. `defaultRequire: 1`
makes a missed injection a load failure. A **figment** (`entity/mind/FigmentEntity`) is a real,
tracked, non-saving `PathfinderMob` so vanilla targeting sees it: `MindMobEvents` gives every mob in
`FigmentHunts` a target goal and every fearer an avoid goal, both gated on believing it; a blow on
a figment is refused and becomes a contradiction; it discards itself the tick its scene is gone.

**Each client draws a lie as strongly as it believes it.** `MindSync` sends a scene to every player
within 64 blocks and belief rows every 5 ticks - a viewer its own, the owner everybody's.
`IllusionRenderer` draws imagined blocks through `renderSingleBlock` into a buffer that forces
`RenderType.translucent()` and scales alpha (`Faded`, which must not override the bulk vertex
paths, because their defaults route through `setColor`), at `min(1, b / 0.5)`; the owner sees 45%
blocks with lilac edges. A figment is drawn by a posed dummy of its kind (`FigmentDummies`) once
visibility reaches 0.5, and is pickable only then. **Belief Sight** (`BeliefSightRenderer`, through
terrain, sharing `AnchorMarkRenderer.band`) is one ring over each viewer: dashed lilac while doubting
with opacity `0.35 + 0.6b`, solid gold once convinced.

**Authoring**: Daydream (`client/mind/DaydreamMode`) is a client toggle - wheel turns the reel,
Alt+wheel the brush (`Brush`: point, line, wall, box), right places, left erases, the inventory key
opens the Playbill. The draft is saved once, on leaving, through `SaveReveriePayload`, and the
server re-reads it through `ReverieNbt.load` and `Reverie.validate`. The Playbill
(`client/screen/mind/PlaybillScreen`, `PlaybillLayout` pinned by its test) is frameless and prints
the **Forecast** for the selected element as it would land where the wielder looks: every term,
the senses, and "Certain in" for a zombie, a skeleton, a player and an enderman (ticks to 0.8).
Unveil is `selfManaged` and billed by hand in `MindService.payFor` - `10 + blocks + 5 per figment
+ 10 per sense layer`, scaled by `costScale` - only after every refusal has been checked.

Commands under `/magical mind`: `lexicon all | block <id> [gazes] | creature <id> [gazes]`,
`preset <slot> pit|wall|cat`, `slot <n>`, `unveil` (clears the cooldown, bills for real), `show`,
`belief` (the matrix back as chat), `end`, `playbill`. Capture, the owner's view of a wall at
midnight with two husks, one behind a real pillar: `.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical authority set authority_of_mind;time set midnight;tp @s ~ ~ ~ 0 15;magical mind preset 1 wall;120:magical mind unveil;124:summon husk ~2 ~ ~9 {NoAI:1b};125:summon husk ~-3 ~ ~9 {NoAI:1b};126:fill ~-3 ~ ~7 ~-3 ~2 ~7 stone" -PautoScreenshot=140,200,300 -PautoExit`.
Game tests: `MindGameTests`, `MindPathGameTests`, `FigmentGameTests`, `UnveilGameTests` (all in
`magic/mind/`, because `Belief.set` is package-private). Design:
`docs/superpowers/specs/2026-09-28-authority-of-mind-design.md`; plan:
`docs/superpowers/plans/2026-09-29-authority-of-mind-illusion.md`.
```

- [ ] **Step 2: Stage only this section**

Build the staged `CLAUDE.md` from `HEAD`'s copy plus the new section, never from the working tree:

```bash
git show HEAD:CLAUDE.md > "$TEMP/claude-head.md"
```

Insert the section before `### Sword school: the stances` in `$TEMP/claude-head.md` with a scratch Python script. Write the script to the scratchpad; do not use a heredoc, because the section contains apostrophes. Then stage it:

```bash
git update-index --cacheinfo 100644,$(git hash-object -w "$TEMP/claude-head.md"),CLAUDE.md
git diff --cached --stat
```

Expected: `CLAUDE.md` is the only staged file, with the section's lines added and nothing else. The peer's hunks stay unstaged in the working tree.

- [ ] **Step 3: Run the whole suite**

```bash
.\gradlew test
.\gradlew build
.\gradlew runGameTestServer --console=plain
```

Expected: every unit test passes, the build succeeds, and every game test passes (the `mind_*` batches and everything that existed before). If an older game test fails, run it alone on `HEAD~16` in a clean worktree before blaming this work. A green working-tree build says nothing about a committed subset.

- [ ] **Step 4: Capture the finished stage**

Run the capture in the new CLAUDE.md section. Then capture Task 15's Daydream capture and Task 16's Playbill capture again. Send the frames to the user with `SendUserFile`, with a one-line caption each.

- [ ] **Step 5: Commit the guide**

```bash
git commit -m "docs: the Authority of Mind, stage 1, in CLAUDE.md"
```

(No pathspec: the index holds exactly the section.)

- [ ] **Step 6: Deliver**

```bash
git remote -v
git push origin main
git -C "E:/minecraft mods/magical" switch --detach main
ls "E:/minecraft mods/magical/src/main/java/com/efkrdnz/magical/magic/mind/MindService.java"
```

Expected:
- `origin` is `Efkrdnz/magical-mod` and the push lands.
- The play checkout moves to the new `main`, and the file listing proves it.
- If the switch refuses because of local changes in that tree, stop and tell the user at the top of the reply.

---

## Self-review notes

Coverage of the spec's stage 1 ("the pure core, Gaze and the Lexicon, Daydream and the Reel, the Playbill with the Forecast, Unveil, players and mobs believing and doubting, figments, the mixin, Belief Sight"):

| Stage 1 item | Where |
|---|---|
| Pure core | Tasks 1, 2, 4, 5 and 14 |
| Gaze and the Lexicon | Tasks 1 and 7 |
| Daydream and the Reel | Tasks 14 and 15 |
| Playbill and Forecast | Task 16 |
| Unveil | Task 11 |
| Belief and doubt | Tasks 8 and 12 |
| Figments | Task 10 |
| The mixin | Task 9 |
| Belief Sight | Task 13 |

Deliberate departures from the spec's Code section:
- **No `DaydreamTogglePayload`.** The toggle is client-only and the draft travels once as `SaveReveriePayload`.
- **No `UnveilPayload`.** Unveil is an ordinary cast through the loadout.
- **No `broadcastToPlayer` audience for figments.** `ChunkMap.TrackedEntity.updatePlayers` only re-asks it when an entity changes section, so a figment would stay hidden from a player who came to believe it without moving. Every client is sent the figment and draws it by its own belief.
- **The Forecast reports ticks to 0.8** ("Certain in"). The spec's worked numbers, a zombie at 2.7 s, are to 0.8, not to the 0.5 at which a mind counts as convinced.
- **The Patrol and Mimic scripts** are deferred, as the spec's out-of-scope list allows.
