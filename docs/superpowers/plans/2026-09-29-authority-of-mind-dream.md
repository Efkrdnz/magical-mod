# Authority of Mind, Stage 3: The Dream — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A mind that believes you completely can be put to sleep. A mob sleeps where it stands. A player is pulled into a world you built in advance, and the only way out is to find the one thing you got wrong on purpose.

**Architecture:** The pure core (`DreamRules`, `Dreamscape`) holds every number, the plot geometry and the Flaw. `DreamService` owns the sessions:
- enter lays the body down as a `SleeperEntity` and moves the mind to the owner's plot;
- wake reverses it;
- a server tick runs the clock, the plot bounds, and contact with the Flaw.

`DreamGuard` makes the dream level a place nothing can be carried out of. `DreamBuilder` is the server end of Daydream inside your own dream, where edits are real blocks and real mobs. The plots live in a `SavedData` on the `magical:dream` level. The vanilla game-test server discards datapack dimensions, so `DreamService.testLevel` lays the plots out in the test level instead.

**Tech Stack:** NeoForge 21.4.157, Minecraft 1.21.4 (Mojmap), Java 21, JUnit 5, NeoForge GameTest.

## Global Constraints

From `docs/superpowers/specs/2026-09-28-authority-of-mind-design.md`, sections "Skills" and "The Dream":
- **Lull** is a press on a viewer. It costs 60 mana and has a 1200-tick clock. Its target must believe some live element of the wielder's at 0.8 or more.
- **A mob** sleeps where it stands for up to 600 ticks, or until it is hurt.
- **A player** is pulled into the wielder's **Dreamscape** in the `magical:dream` dimension, one plot per wielder.
  - Their body stays behind as a **Sleeper**: an entity wearing their skin, lying down.
  - Damage to the Sleeper wakes them.
- **Building:** the wielder builds the Dreamscape in advance by Lulling themselves. Inside their own dream, Daydream places real blocks and real figments, free.
- **The Flaw:** a Dreamscape must contain exactly one Flaw, a block or figment the wielder marks as wrong. Without one, Lull refuses.
- **Waking:** a dreamer wakes by touching or striking the Flaw, after 1200 ticks, or when their Sleeper is hurt.
- **A dream cannot kill.** Harm inside it is real, but a dreamer brought to one heart wakes at one heart.
- **Out of scope:** the wielder entering another player's dream alongside them.

Standing rules from stages 1-2 (CLAUDE.md, "Authority of Mind"):
- Every Mind skill is `selfManaged`. It bills itself through `MindService.payFor`, and only after every refusal has been checked.
- Game-bus handlers are `@EventBusSubscriber(modid = MagicalMod.MODID)` classes.
- A fake player never gets a player tick, so every clock here runs on `ServerTickEvent.Post`.
- Anything made real must have no route out of the lie. Stage 2's final review found four ways imagined matter leaked. Every such route here is closed by a rule and pinned by a test.

## Decisions

**D1. Lulling yourself is sneak + press.**
- It is free and has no clock, because it is the workshop, not the weapon.
- Your own dream never times out. Pressing Lull again inside it wakes you.
- A dreamer arrives where the owner stood when they last woke from their own dream.
- A fresh plot's arrival is the middle of its starter floor.

**D2. Plots.** Each owner claims the next index in `DreamPlots`, a `SavedData` named `magical_dream_plots` on the dream level.
- Plot *i* has origin `(1_000_000 + (i % 64) * 256, 100, 1_000_000 + (i / 64) * 256)`.
- Its bounds are 32 blocks each way horizontally, 16 below and 48 above.
- A new plot gets a 5x5 smooth-stone floor at `y - 1`.
- Anyone in a dream who leaves the bounds is put back at the arrival.
- The million-block offset keeps plots clear of every game-test template when `testLevel` stands in for the dimension.

**D3. Game tests use the test level as the dimension.**
- `DreamService.dreamLevel(server)` returns `testLevel` when a test has set it, else `server.getLevel(DREAM)`.
- `DreamService.isDream(level, x, z)` is true everywhere in the real dimension.
- In the test level, `isDream` is true only inside the plot grid (`DreamRules.plotAt(x, z) >= 0`). A stale `testLevel` therefore cannot change a test that runs near the origin.

**D4. Nothing leaves a dream.** In a dream:
- No `ItemEntity` or `ExperienceOrb` can join the level. An item a dreamer throws goes back into their inventory instead.
- Nothing is broken or placed by hand.
- No item can be used (`RightClickItem` is cancelled).
- A block answers only an empty hand: `RightClickBlock` never uses the item, and uses the block only with both hands empty.
- No entity can be interacted with, so there is no shearing, milking or trading.
- Explosions take no blocks, and mobs cannot grief.
- Mobs never target an owner who is in their own dream.
- Leaving the dream level by any route (a skill, a command) wakes the dreamer in their body.

What a dreamer carries in, they carry out, and nothing else.

**D5. The Sleeper.** It is a `LivingEntity` registered `noSave()`, with a 1.2 x 0.5 box.
- It stands on the server. Its renderer draws it lying down: pose `SLEEPING`, with the bed orientation taken from its yaw.
- Its skin comes from the tab list (`PlayerInfo`) while the dreamer is online. Otherwise it is the default skin for their UUID.
- A hit on the Sleeper takes nothing off the Sleeper. It wakes the dreamer, and then lands on them.
- `/kill` (`GENERIC_KILL`) wakes the dreamer unhurt.

**D6. Recovery.** The return point is a `DreamReturn` attachment on the dreamer. It is serialized and has no `copyOnDeath`.
- A logout drops the session and the body.
- A login with a return point and no session sends the player back to that point.
- A server stop clears the sessions, so recovery happens on the next login.

**D7. A lulled mob wears the existing `MagicStatus.ASLEEP`** for `DreamRules.MOB_SLEEP_TICKS`: no AI, no target, and any hit clears it. No new status or effect is made.

**D8. Dream figments are real vanilla mobs.**
- Each is persistent and tagged `magical_dream`, with at most 32 per plot.
- Never dreamed: the wither, the ender dragon, portal blocks and frames, and any block with a block entity.

**D9. Contact with the Flaw** is any of:
- the dreamer's bounding box (inflated by 0.05) meeting the Flaw block's cell or the Flaw figment's box;
- a left click, right click, attack or interaction on the Flaw.

A Flaw figment that dies clears the Flaw. The owner is exempt: their own Flaw does nothing to them.

**D10. Build edits** reach at most `DreamRules.EDIT_REACH` (8) blocks from the eye. They stay inside the plot, and place only into cells that `canBeReplaced`.

**D11.** The dream clock counts `server.getTickCount()`.

## File Structure

Pure core, in `src/main/java/com/efkrdnz/magical/magic/mind/`:
- `DreamRules.java`: every number, the plot geometry, the one-heart arithmetic, and what is never dreamed.
- `Dreamscape.java`: one owner's plot, arrival and Flaw.

Runtime, in `magic/mind/`:
- `DreamscapeNbt.java`: Dreamscape to and from NBT.
- `DreamPlots.java`: the `SavedData`.
- `DreamReturn.java`: the attachment value.
- `DreamSession.java`.
- `DreamService.java`: level, plots, sessions, Lull, tick and recovery.
- `DreamGuard.java`.
- `DreamBuilder.java`.
- `DreamPresets.java`: for captures only.

Entity: `entity/mind/SleeperEntity.java`.

Client: `client/mind/SleeperRenderer.java` and `client/mind/ClientDream.java`.

Network: `network/DreamEditPayload.java` (to the server) and `network/DreamStatePayload.java` (to the client).

Data: `src/main/resources/data/magical/dimension/dream.json` and `dimension_type/dream.json`.

Tests:
- Unit tests, in `src/test/java/com/efkrdnz/magical/`: `magic/mind/DreamRulesTest`, `DreamscapeTest`, `DreamPlotsTest`, and `network/DreamPayloadsTest`.
- Game tests, in `src/main/java/com/efkrdnz/magical/magic/mind/`: `DreamPlotGameTests`, `DreamGameTests`, `DreamGuardGameTests`, `LullGameTests`, `DreamBuilderGameTests`.

Existing helpers these tests use:
- `GameTestPlayers.survival(helper, BlockPos, name)`, `another(...)` and `onFloor(helper, BlockPos)` (absolute). These players are real `ServerPlayer`s placed through the player list, so chunks load around them.
- `MindGameTests.column()` and `MindGameTests.unveil(helper, UUID owner, Reverie, BlockPos absoluteAnchor)`.
- `MindService.endAll(UUID)` and `MindService.scenesOf(UUID)`.
- `MagicStatusService.has(Entity, MagicStatus)`.
- `PlayerMagicState.setAuthority(ResourceLocation)`, `hasAuthority`, `mind()`, `mana()`, `isSkillOnCooldown`, `setSkillCooldown`, `setBarrier`.
- `Lexicon.learn(String key, int count)` and `knows(String key)`.
- `Impression(Kind kind, String id)`, with `parse(String)` and `key()`.
- `Brush.MAX_CELLS` (128).
- `LiveScene.Element.index()`.

---

### Task 1: The pure core

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamRules.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/Dreamscape.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/DreamRulesTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/DreamscapeTest.java`

**Interfaces:**
- **Consumes:** `Offset(int dx, int dy, int dz)` (existing, pure) and `Belief.SURE` (0.8F, existing).
- **Produces:**
  - `DreamRules` constants: `LULL_BELIEF`, `MOB_SLEEP_TICKS`, `DREAM_TICKS`, `ONE_HEART`, `LULL_MANA`, `PLOT_BASE`, `PLOT_SPACING`, `PLOTS_PER_ROW`, `PLOT_Y`, `PLOT_HALF`, `PLOT_BELOW`, `PLOT_ABOVE`, `PLATFORM_HALF`, `MAX_FIGMENTS`, `EDIT_REACH`, `NEVER_DREAMED`.
  - `DreamRules` methods: `Offset origin(int plot)`, `boolean inside(int plot, double x, double y, double z)`, `int plotAt(double x, double z)`, `float dealt(float health, float incoming)`, `boolean wakes(float health, float incoming)`, `boolean refused(String id)`.
  - `Dreamscape`: `Dreamscape(int plot)`, `plot()`, `arrival()`, `arrivalYaw()`, `setArrival(Offset, float)`, `flaw()`, `markBlock(Offset)`, `markFigment(UUID)`, `clearIfFlaw(Offset)`, `clearIfFlaw(UUID)`, and `record Flaw(Offset block, UUID figment)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/efkrdnz/magical/magic/mind/DreamRulesTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DreamRulesTest {
    @Test
    void theNumbersAreTheSpecs() {
        assertEquals(Belief.SURE, DreamRules.LULL_BELIEF);
        assertEquals(600, DreamRules.MOB_SLEEP_TICKS);
        assertEquals(1200, DreamRules.DREAM_TICKS);
        assertEquals(60, DreamRules.LULL_MANA);
        assertEquals(2.0F, DreamRules.ONE_HEART);
    }

    @Test
    void plotsAreLaidOutInRowsFarFromTheOrigin() {
        assertEquals(new Offset(1_000_000, 100, 1_000_000), DreamRules.origin(0));
        assertEquals(new Offset(1_000_256, 100, 1_000_000), DreamRules.origin(1));
        assertEquals(new Offset(1_000_000, 100, 1_000_256), DreamRules.origin(64));
    }

    @Test
    void aPositionKnowsItsPlotAndNothingNearTheOriginIsOne() {
        assertEquals(0, DreamRules.plotAt(1_000_000.5, 1_000_000.5));
        assertEquals(1, DreamRules.plotAt(1_000_256 + 40, 1_000_000 - 40));
        assertEquals(64, DreamRules.plotAt(1_000_000.5, 1_000_256.5));
        assertEquals(-1, DreamRules.plotAt(0.5, 0.5));
        assertEquals(-1, DreamRules.plotAt(1_000_000 + 64 * 256, 1_000_000));
    }

    @Test
    void thePlotBoundsAreThirtyTwoEachWaySixteenDownAndFortyEightUp() {
        assertTrue(DreamRules.inside(0, 1_000_000.5, 100, 1_000_000.5));
        assertTrue(DreamRules.inside(0, 1_000_000 + 32.9, 100 - 16, 1_000_000 - 32));
        assertFalse(DreamRules.inside(0, 1_000_000 + 33.1, 100, 1_000_000.5));
        assertFalse(DreamRules.inside(0, 1_000_000.5, 100 - 16.5, 1_000_000.5));
        assertFalse(DreamRules.inside(0, 1_000_000.5, 100 + 48.5, 1_000_000.5));
        assertFalse(DreamRules.inside(1, 1_000_000.5, 100, 1_000_000.5));
    }

    @Test
    void aBlowThatWouldLeaveLessThanAHeartLeavesExactlyOneAndWakes() {
        assertFalse(DreamRules.wakes(20.0F, 4.0F));
        assertEquals(4.0F, DreamRules.dealt(20.0F, 4.0F));
        assertTrue(DreamRules.wakes(20.0F, 18.0F));
        assertEquals(18.0F, DreamRules.dealt(20.0F, 18.0F));
        assertTrue(DreamRules.wakes(20.0F, 100.0F));
        assertEquals(18.0F, DreamRules.dealt(20.0F, 100.0F));
        assertTrue(DreamRules.wakes(1.5F, 0.1F));
        assertEquals(0.0F, DreamRules.dealt(1.5F, 0.1F));
    }

    @Test
    void portalsAndBossesAreNeverDreamed() {
        for (String id : new String[] {"minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway",
                "minecraft:end_portal_frame", "minecraft:wither", "minecraft:ender_dragon"}) {
            assertTrue(DreamRules.refused(id), id);
        }
        assertFalse(DreamRules.refused("minecraft:stone"));
        assertFalse(DreamRules.refused("minecraft:zombie"));
    }
}
```

`src/test/java/com/efkrdnz/magical/magic/mind/DreamscapeTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DreamscapeTest {
    @Test
    void aNewDreamscapeArrivesOnItsFloorAndHasNoFlaw() {
        Dreamscape scape = new Dreamscape(3);
        assertEquals(3, scape.plot());
        assertEquals(new Offset(0, 0, 0), scape.arrival());
        assertNull(scape.flaw());
    }

    @Test
    void thereIsOnlyEverOneFlaw() {
        Dreamscape scape = new Dreamscape(0);
        scape.markBlock(new Offset(1, 0, 0));
        UUID cow = UUID.randomUUID();
        scape.markFigment(cow);
        assertNull(scape.flaw().block());
        assertEquals(cow, scape.flaw().figment());
        scape.markBlock(new Offset(2, 1, 0));
        assertEquals(new Offset(2, 1, 0), scape.flaw().block());
        assertNull(scape.flaw().figment());
    }

    @Test
    void onlyTheFlawItselfClearsIt() {
        Dreamscape scape = new Dreamscape(0);
        scape.markBlock(new Offset(1, 0, 0));
        assertFalse(scape.clearIfFlaw(new Offset(0, 0, 0)));
        assertFalse(scape.clearIfFlaw(UUID.randomUUID()));
        assertNotNull(scape.flaw());
        assertTrue(scape.clearIfFlaw(new Offset(1, 0, 0)));
        assertNull(scape.flaw());
    }

    @Test
    void aFlawIsExactlyOneThing() {
        assertThrows(IllegalArgumentException.class, () -> new Dreamscape.Flaw(null, null));
        assertThrows(IllegalArgumentException.class, () -> new Dreamscape.Flaw(new Offset(0, 0, 0), UUID.randomUUID()));
    }

    @Test
    void theArrivalIsWhereTheOwnerLastStood() {
        Dreamscape scape = new Dreamscape(0);
        scape.setArrival(new Offset(2, 0, -3), 90.0F);
        assertEquals(new Offset(2, 0, -3), scape.arrival());
        assertEquals(90.0F, scape.arrivalYaw());
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.DreamRulesTest" --tests "com.efkrdnz.magical.magic.mind.DreamscapeTest"`
Expected: compilation fails, because `DreamRules` and `Dreamscape` do not exist yet.

- [ ] **Step 3: Write `DreamRules`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.Set;

/**
 * Every number the Dream has, and the shape of the world it happens in. Pure: no Minecraft, so every
 * rule is pinned on an exact value in {@code DreamRulesTest}.
 */
public final class DreamRules {
    /** Lull takes only a viewer who believes some live element of yours this much. */
    public static final float LULL_BELIEF = Belief.SURE;
    public static final int MOB_SLEEP_TICKS = 600;
    public static final int DREAM_TICKS = 1200;
    /** What a dream leaves a dreamer it would have killed. */
    public static final float ONE_HEART = 2.0F;
    public static final int LULL_MANA = 60;

    /** Plots start a million blocks out, far from anything else in any level they are laid out in. */
    public static final int PLOT_BASE = 1_000_000;
    public static final int PLOT_SPACING = 256;
    public static final int PLOTS_PER_ROW = 64;
    public static final int PLOT_Y = 100;
    public static final int PLOT_HALF = 32;
    public static final int PLOT_BELOW = 16;
    public static final int PLOT_ABOVE = 48;
    /** The starter floor under a new plot's arrival: five by five. */
    public static final int PLATFORM_HALF = 2;
    public static final int MAX_FIGMENTS = 32;
    /** How far from the eye a build edit in your own dream may reach. */
    public static final double EDIT_REACH = 8.0;

    /** A way out of the dream, or a thing whose death is worth more than the dream. */
    public static final Set<String> NEVER_DREAMED = Set.of(
            "minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway", "minecraft:end_portal_frame",
            "minecraft:wither", "minecraft:ender_dragon");

    private DreamRules() {}

    /** The block a plot is measured from: its arrival floor is one below. */
    public static Offset origin(int plot) {
        return new Offset(PLOT_BASE + Math.floorMod(plot, PLOTS_PER_ROW) * PLOT_SPACING, PLOT_Y,
                PLOT_BASE + Math.floorDiv(plot, PLOTS_PER_ROW) * PLOT_SPACING);
    }

    public static boolean inside(int plot, double x, double y, double z) {
        Offset o = origin(plot);
        return x >= o.dx() - PLOT_HALF && x < o.dx() + PLOT_HALF + 1
                && z >= o.dz() - PLOT_HALF && z < o.dz() + PLOT_HALF + 1
                && y >= o.dy() - PLOT_BELOW && y <= o.dy() + PLOT_ABOVE;
    }

    /** The plot whose cell of the grid this column falls in, or -1 outside the grid. */
    public static int plotAt(double x, double z) {
        double half = PLOT_SPACING / 2.0;
        int col = (int) Math.floor((x - PLOT_BASE + half) / PLOT_SPACING);
        int row = (int) Math.floor((z - PLOT_BASE + half) / PLOT_SPACING);
        if (col < 0 || row < 0 || col >= PLOTS_PER_ROW) {
            return -1;
        }
        return row * PLOTS_PER_ROW + col;
    }

    /** Whether a blow wakes the dreamer: it would leave them one heart or less. */
    public static boolean wakes(float health, float incoming) {
        return incoming >= health - ONE_HEART;
    }

    /** What of a blow actually lands in a dream: never more than leaves one heart. */
    public static float dealt(float health, float incoming) {
        return Math.min(incoming, Math.max(0.0F, health - ONE_HEART));
    }

    public static boolean refused(String id) {
        return NEVER_DREAMED.contains(id);
    }
}
```

- [ ] **Step 4: Write `Dreamscape`**

```java
package com.efkrdnz.magical.magic.mind;

import java.util.UUID;

/**
 * One wielder's Dreamscape: the plot it is built on, where a dreamer arrives, and the one thing in it
 * the wielder marked as wrong. The blocks and creatures themselves are real and live in the level; this
 * is only what the level cannot say. Pure.
 */
public final class Dreamscape {
    /** Exactly one of a block (an offset from the plot's origin) or a figment (its entity UUID). */
    public record Flaw(Offset block, UUID figment) {
        public Flaw {
            if ((block == null) == (figment == null)) {
                throw new IllegalArgumentException("a Flaw is exactly one block or one figment");
            }
        }
    }

    private final int plot;
    private Offset arrival = new Offset(0, 0, 0);
    private float arrivalYaw;
    private Flaw flaw;

    public Dreamscape(int plot) {
        this.plot = plot;
    }

    public int plot() { return plot; }
    public Offset arrival() { return arrival; }
    public float arrivalYaw() { return arrivalYaw; }
    public Flaw flaw() { return flaw; }

    public void setArrival(Offset at, float yaw) {
        this.arrival = at;
        this.arrivalYaw = yaw;
    }

    public void markBlock(Offset at) {
        this.flaw = new Flaw(at, null);
    }

    public void markFigment(UUID figment) {
        this.flaw = new Flaw(null, figment);
    }

    /** Clears the Flaw if it is this block, and says whether it was. */
    public boolean clearIfFlaw(Offset at) {
        if (flaw != null && at.equals(flaw.block())) {
            flaw = null;
            return true;
        }
        return false;
    }

    /** Clears the Flaw if it is this figment, and says whether it was. */
    public boolean clearIfFlaw(UUID figment) {
        if (flaw != null && figment.equals(flaw.figment())) {
            flaw = null;
            return true;
        }
        return false;
    }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.DreamRulesTest" --tests "com.efkrdnz.magical.magic.mind.DreamscapeTest"`
Expected: PASS, 11 tests.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/DreamRules.java src/main/java/com/efkrdnz/magical/magic/mind/Dreamscape.java src/test/java/com/efkrdnz/magical/magic/mind/DreamRulesTest.java src/test/java/com/efkrdnz/magical/magic/mind/DreamscapeTest.java
git commit -m "feat(mind): the rules of the dream and the shape of a Dreamscape

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The dream level and its plots

**Files:**
- Create: `src/main/resources/data/magical/dimension/dream.json`
- Create: `src/main/resources/data/magical/dimension_type/dream.json`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamscapeNbt.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamPlots.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/DreamPlotsTest.java`
- Test: `src/main/java/com/efkrdnz/magical/magic/mind/DreamPlotGameTests.java`

**Interfaces:**
- **Consumes:** `DreamRules` and `Dreamscape` (Task 1).
- **Produces:**
  - `DreamService`:
    - `DREAM` (`ResourceKey<Level>`), `DREAM_TAG = "magical_dream"`, and the package-private `static ServerLevel testLevel`.
    - `public static ServerLevel dreamLevel(MinecraftServer)`.
    - `public static boolean isDream(Level, double x, double z)`, plus the overloads `isDream(Level, BlockPos)` and `isDream(Entity)`.
    - Package-private: `static Dreamscape dreamscape(ServerLevel dream, UUID owner)`, `static BlockPos at(int plot, Offset)`, and `static Offset offsetIn(int plot, BlockPos)`.
  - `DreamPlots`: `static DreamPlots of(ServerLevel)`, `get(UUID)`, `claim(UUID)`, `ownerOf(int plot)`, `changed()`.
  - `DreamscapeNbt`: `save(Dreamscape)` and `load(CompoundTag)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/efkrdnz/magical/magic/mind/DreamPlotsTest.java`:

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DreamPlotsTest {
    @Test
    void aDreamscapeSurvivesTheDiskWithItsFlaw() {
        Dreamscape block = new Dreamscape(4);
        block.setArrival(new Offset(2, 0, -1), 45.0F);
        block.markBlock(new Offset(3, 1, 0));
        Dreamscape back = DreamscapeNbt.load(DreamscapeNbt.save(block));
        assertEquals(4, back.plot());
        assertEquals(new Offset(2, 0, -1), back.arrival());
        assertEquals(45.0F, back.arrivalYaw());
        assertEquals(new Offset(3, 1, 0), back.flaw().block());

        Dreamscape figment = new Dreamscape(5);
        UUID cow = UUID.randomUUID();
        figment.markFigment(cow);
        assertEquals(cow, DreamscapeNbt.load(DreamscapeNbt.save(figment)).flaw().figment());
        assertNull(DreamscapeNbt.load(DreamscapeNbt.save(new Dreamscape(6))).flaw());
    }

    @Test
    void everyOwnerHasTheirOwnPlotAndKeepsItAcrossALoad() {
        DreamPlots plots = new DreamPlots();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(0, plots.claim(a).plot());
        assertEquals(1, plots.claim(b).plot());
        DreamPlots loaded = DreamPlots.load(plots.save(new CompoundTag(), null), null);
        assertEquals(0, loaded.get(a).plot());
        assertEquals(1, loaded.get(b).plot());
        assertEquals(b, loaded.ownerOf(1));
        assertEquals(2, loaded.claim(UUID.randomUUID()).plot());
    }
}
```

`src/main/java/com/efkrdnz/magical/magic/mind/DreamPlotGameTests.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamPlotGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_plot_1")
    public static void aNewDreamscapeHasAFloorAndIsItsOwnersAlone(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        Dreamscape scape = DreamService.dreamscape(level, owner);
        helper.assertTrue(DreamService.dreamscape(level, owner) == scape, "asking twice claimed a second plot");
        helper.assertTrue(DreamService.dreamscape(level, UUID.randomUUID()).plot() != scape.plot(), "two owners share a plot");
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        helper.assertTrue(level.getBlockState(arrival.below()).is(Blocks.SMOOTH_STONE), "nothing to stand on at the arrival");
        helper.assertTrue(level.getBlockState(arrival.below().offset(DreamRules.PLATFORM_HALF, 0, DreamRules.PLATFORM_HALF)).is(Blocks.SMOOTH_STONE),
                "the floor is not five by five");
        helper.assertTrue(DreamService.isDream(level, arrival), "the plot is not a dream");
        helper.assertFalse(DreamService.isDream(level, helper.absolutePos(new BlockPos(1, 1, 1))), "the test itself is a dream");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run the unit test and confirm it fails**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.DreamPlotsTest"`
Expected: compilation fails, because `DreamscapeNbt` and `DreamPlots` do not exist yet.

- [ ] **Step 3: Write the dimension**

`src/main/resources/data/magical/dimension/dream.json`:

```json
{
  "type": "magical:dream",
  "generator": {
    "type": "minecraft:flat",
    "settings": {
      "biome": "minecraft:the_void",
      "features": false,
      "lakes": false,
      "layers": [
        {
          "block": "minecraft:air",
          "height": 1
        }
      ],
      "structure_overrides": []
    }
  }
}
```

`src/main/resources/data/magical/dimension_type/dream.json`. It is always night, so a dreamed zombie never burns. The sky has stars, and no mob spawns on its own:

```json
{
  "ultrawarm": false,
  "natural": false,
  "coordinate_scale": 1.0,
  "piglin_safe": false,
  "bed_works": false,
  "respawn_anchor_works": false,
  "has_raids": false,
  "has_skylight": true,
  "has_ceiling": false,
  "ambient_light": 0.1,
  "fixed_time": 18000,
  "logical_height": 384,
  "min_y": -64,
  "height": 384,
  "infiniburn": "#minecraft:infiniburn_overworld",
  "effects": "minecraft:overworld",
  "monster_spawn_light_level": {
    "type": "minecraft:uniform",
    "max_inclusive": 0,
    "min_inclusive": 0
  },
  "monster_spawn_block_light_limit": 0
}
```

- [ ] **Step 4: Write `DreamscapeNbt`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;

/** A Dreamscape to and from NBT; offsets as three-int arrays, the Flaw as whichever half it is. */
public final class DreamscapeNbt {
    private DreamscapeNbt() {}

    public static CompoundTag save(Dreamscape scape) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("plot", scape.plot());
        putOffset(tag, "arrival", scape.arrival());
        tag.putFloat("yaw", scape.arrivalYaw());
        Dreamscape.Flaw flaw = scape.flaw();
        if (flaw != null && flaw.block() != null) {
            putOffset(tag, "flaw_block", flaw.block());
        } else if (flaw != null) {
            tag.putUUID("flaw_figment", flaw.figment());
        }
        return tag;
    }

    public static Dreamscape load(CompoundTag tag) {
        Dreamscape scape = new Dreamscape(tag.getInt("plot"));
        Offset arrival = offset(tag, "arrival");
        scape.setArrival(arrival == null ? new Offset(0, 0, 0) : arrival, tag.getFloat("yaw"));
        Offset flawBlock = offset(tag, "flaw_block");
        if (flawBlock != null) {
            scape.markBlock(flawBlock);
        } else if (tag.hasUUID("flaw_figment")) {
            scape.markFigment(tag.getUUID("flaw_figment"));
        }
        return scape;
    }

    private static void putOffset(CompoundTag tag, String key, Offset offset) {
        tag.putIntArray(key, new int[] {offset.dx(), offset.dy(), offset.dz()});
    }

    private static Offset offset(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        return values.length == 3 ? new Offset(values[0], values[1], values[2]) : null;
    }
}
```

- [ ] **Step 5: Write `DreamPlots`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Who owns which plot of the dream level, and what each owner's Dreamscape says. Saved with the level. */
final class DreamPlots extends SavedData {
    static final String NAME = "magical_dream_plots";

    private final Map<UUID, Dreamscape> scapes = new LinkedHashMap<>();
    private int next;

    DreamPlots() {}

    static DreamPlots of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DreamPlots::new, DreamPlots::load), NAME);
    }

    Dreamscape get(UUID owner) {
        return scapes.get(owner);
    }

    Dreamscape claim(UUID owner) {
        Dreamscape scape = new Dreamscape(next++);
        scapes.put(owner, scape);
        setDirty();
        return scape;
    }

    UUID ownerOf(int plot) {
        for (Map.Entry<UUID, Dreamscape> entry : scapes.entrySet()) {
            if (entry.getValue().plot() == plot) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** A Dreamscape is mutable: whoever changed one says so, or the change is not saved. */
    void changed() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("next", next);
        ListTag list = new ListTag();
        scapes.forEach((owner, scape) -> {
            CompoundTag entry = DreamscapeNbt.save(scape);
            entry.putUUID("owner", owner);
            list.add(entry);
        });
        tag.put("scapes", list);
        return tag;
    }

    static DreamPlots load(CompoundTag tag, HolderLookup.Provider provider) {
        DreamPlots plots = new DreamPlots();
        plots.next = tag.getInt("next");
        for (Tag raw : tag.getList("scapes", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("owner")) {
                continue;
            }
            Dreamscape scape = DreamscapeNbt.load(entry);
            plots.scapes.put(entry.getUUID("owner"), scape);
            plots.next = Math.max(plots.next, scape.plot() + 1);
        }
        return plots;
    }
}
```

- [ ] **Step 6: Write `DreamService` (the level, the plots, and what counts as a dream)**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * The Dream: a level of plots, one per wielder, where a mind that believed completely is kept until it
 * finds the Flaw. This class owns the level, the plots, the sessions and Lull.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class DreamService {
    public static final ResourceKey<Level> DREAM = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dream"));
    /** Every mob a wielder dreams into their plot wears this. */
    public static final String DREAM_TAG = "magical_dream";

    /**
     * Game tests only. The vanilla test server discards datapack dimensions, so a test lays the plots
     * out in its own level. They start a million blocks out, and {@link #isDream} only counts the plot
     * grid there, so nothing near a test template is ever a dream.
     */
    static ServerLevel testLevel;

    private DreamService() {}

    public static ServerLevel dreamLevel(MinecraftServer server) {
        if (testLevel != null && testLevel.getServer() == server) {
            return testLevel;
        }
        return server.getLevel(DREAM);
    }

    public static boolean isDream(Level level, double x, double z) {
        if (level.dimension().equals(DREAM)) {
            return true;
        }
        return level == testLevel && DreamRules.plotAt(x, z) >= 0;
    }

    public static boolean isDream(Level level, BlockPos pos) {
        return isDream(level, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean isDream(Entity entity) {
        return isDream(entity.level(), entity.getX(), entity.getZ());
    }

    /** The owner's Dreamscape, claimed and floored the first time anybody asks for it. */
    static Dreamscape dreamscape(ServerLevel dream, UUID owner) {
        DreamPlots plots = DreamPlots.of(dream);
        Dreamscape existing = plots.get(owner);
        if (existing != null) {
            return existing;
        }
        Dreamscape claimed = plots.claim(owner);
        floor(dream, claimed.plot());
        return claimed;
    }

    static BlockPos at(int plot, Offset offset) {
        Offset origin = DreamRules.origin(plot);
        return new BlockPos(origin.dx() + offset.dx(), origin.dy() + offset.dy(), origin.dz() + offset.dz());
    }

    static Offset offsetIn(int plot, BlockPos pos) {
        Offset origin = DreamRules.origin(plot);
        return new Offset(pos.getX() - origin.dx(), pos.getY() - origin.dy(), pos.getZ() - origin.dz());
    }

    private static void floor(ServerLevel dream, int plot) {
        for (int x = -DreamRules.PLATFORM_HALF; x <= DreamRules.PLATFORM_HALF; x++) {
            for (int z = -DreamRules.PLATFORM_HALF; z <= DreamRules.PLATFORM_HALF; z++) {
                dream.setBlock(at(plot, new Offset(x, -1, z)), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
```

- [ ] **Step 7: Run the tests and confirm they pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.DreamPlotsTest"`
Expected: PASS, 2 tests.

Run: `.\gradlew runGameTestServer --console=plain`
Expected: every game test passes, including `aNewDreamscapeHasAFloorAndIsItsOwnersAlone`.

- [ ] **Step 8: Commit**

```bash
git add -- src/main/resources/data/magical/dimension/dream.json src/main/resources/data/magical/dimension_type/dream.json src/main/java/com/efkrdnz/magical/magic/mind/DreamscapeNbt.java src/main/java/com/efkrdnz/magical/magic/mind/DreamPlots.java src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java src/main/java/com/efkrdnz/magical/magic/mind/DreamPlotGameTests.java src/test/java/com/efkrdnz/magical/magic/mind/DreamPlotsTest.java
git commit -m "feat(mind): the dream level, one plot to a wielder

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Falling asleep and waking — the Sleeper, the session, the clock

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/entity/mind/SleeperEntity.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamReturn.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamSession.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java`
- Modify: `src/main/java/com/efkrdnz/magical/registry/MagicalEntities.java` (after the `FIGMENT` register)
- Modify: `src/main/java/com/efkrdnz/magical/registry/MagicalEntityEvents.java` (after the `FIGMENT` attribute line)
- Modify: `src/main/java/com/efkrdnz/magical/registry/MagicalAttachments.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java` (after the `FIGMENT` renderer line)
- Modify: `src/main/resources/assets/magical/lang/en_us.json`
- Test: `src/main/java/com/efkrdnz/magical/magic/mind/DreamGameTests.java`

**Interfaces:**
- **Consumes:**
  - From Task 2: `DreamService.dreamLevel`, `dreamscape`, `at`, `offsetIn`, `testLevel`.
  - From Task 1: `DreamRules`.
- **Produces:**
  - `SleeperEntity.of(ServerPlayer)`, `dreamer()` (`Optional<UUID>`), `dreamerName()`.
  - `MagicalEntities.SLEEPER`.
  - `MagicalAttachments.DREAM_RETURN`.
  - `DreamReturn.of(ServerPlayer)`, plus `level(MinecraftServer)`, `x()`, `y()`, `z()`, `yaw()`, `pitch()`.
  - `DreamSession`, with fields `dreamer`, `owner`, `own`, `plot`, `wakesAt`, `sleeperId`, `wakeNow`, `hurt`, `hurtAmount`.
  - On `DreamService`, package-private: `enter(ServerPlayer, UUID owner, boolean own)` (returns boolean), `wake(ServerPlayer)`, `session(UUID)`, `recover(ServerPlayer)`, `touched(ServerPlayer, BlockPos)`, `touched(ServerPlayer, Entity)`.
  - On `DreamService`, public: `dreaming(UUID)`, `dreamingOwn(UUID)`, `sleeperStruck(SleeperEntity, ServerLevel, DamageSource, float)`.

- [ ] **Step 1: Write the failing game tests**

`src/main/java/com/efkrdnz/magical/magic/mind/DreamGameTests.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.SleeperEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    /** A fake player with the barrier drained, the test level standing in for the dream. */
    static ServerPlayer sleeper(GameTestHelper helper, String name) {
        DreamService.testLevel = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), name);
        player.getData(MagicalAttachments.MAGIC_STATE).setBarrier(0);
        return player;
    }

    /** A stone Flaw one block east of the arrival. */
    static Dreamscape withFlaw(ServerLevel level, UUID owner) {
        Dreamscape scape = DreamService.dreamscape(level, owner);
        level.setBlock(DreamService.at(scape.plot(), new Offset(1, 0, 0)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        scape.markBlock(new Offset(1, 0, 0));
        return scape;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_1")
    public static void aDreamerSleepsWhereTheyStoodAndWakesThere(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-enter-test");
        Vec3 stood = player.position();
        UUID owner = UUID.randomUUID();
        helper.assertTrue(DreamService.enter(player, owner, false), "the dream was refused");
        DreamSession session = DreamService.session(player.getUUID());
        helper.assertTrue(DreamRules.plotAt(player.getX(), player.getZ()) == session.plot, "the dreamer is not in the owner's plot");
        helper.assertTrue(helper.getLevel().getEntity(session.sleeperId) instanceof SleeperEntity body
                && body.position().distanceTo(stood) < 0.01 && body.dreamer().orElseThrow().equals(player.getUUID()),
                "no body of theirs lies where they stood");
        helper.assertTrue(player.hasData(MagicalAttachments.DREAM_RETURN), "nothing remembers where to wake");
        DreamService.wake(player);
        helper.assertTrue(player.position().distanceTo(stood) < 0.01, "they woke somewhere else");
        helper.assertFalse(player.hasData(MagicalAttachments.DREAM_RETURN), "the return point outlived the dream");
        helper.assertTrue(helper.getLevel().getEntity(session.sleeperId) == null, "the body outlived the dream");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_2")
    public static void touchingTheFlawWakesTheDreamer(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-flaw-test");
        Vec3 stood = player.position();
        UUID owner = UUID.randomUUID();
        Dreamscape scape = withFlaw(helper.getLevel(), owner);
        DreamService.enter(player, owner, false);
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 0.72, arrival.getY(), arrival.getZ() + 0.5);
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the Flaw was touched and nothing woke");
            helper.assertTrue(player.position().distanceTo(stood) < 0.01, "they woke somewhere else");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_3")
    public static void aBlowOnTheSleeperWakesTheDreamerAndLandsOnThem(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-struck-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel level = helper.getLevel();
        SleeperEntity body = (SleeperEntity) level.getEntity(DreamService.session(player.getUUID()).sleeperId);
        float before = player.getHealth();
        body.hurtServer(level, level.damageSources().generic(), 4.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the body was struck and nothing woke");
            helper.assertTrue(Math.abs(player.getHealth() - (before - 4.0F)) < 0.01F, "the blow did not land on the woken player: " + player.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_4")
    public static void aDreamLeavesOneHeartAndWakes(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-heart-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel level = helper.getLevel();
        player.hurtServer(level, level.damageSources().generic(), 100.0F);
        helper.assertTrue(player.isAlive() && Math.abs(player.getHealth() - DreamRules.ONE_HEART) < 0.01F,
                "the dream did not stop at one heart: " + player.getHealth());
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "brought to one heart and still dreaming");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_5")
    public static void aDreamEndsWhenItsTimeIsUp(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-clock-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        DreamSession session = DreamService.session(player.getUUID());
        helper.assertTrue(session.wakesAt == helper.getLevel().getServer().getTickCount() + DreamRules.DREAM_TICKS,
                "a dream does not last 1200 ticks");
        session.wakesAt = 0L;
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the clock ran out and nothing woke");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_6")
    public static void aDreamerWhoStraysIsPutBackAtTheArrival(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-stray-test");
        UUID owner = UUID.randomUUID();
        DreamService.enter(player, owner, false);
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), owner);
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + DreamRules.PLOT_HALF + 6, arrival.getY(), arrival.getZ());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(DreamService.dreaming(player.getUUID()), "straying woke them");
            helper.assertTrue(player.position().distanceTo(Vec3.atBottomCenterOf(arrival)) < 0.01, "they were not put back");
            DreamService.wake(player);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_7")
    public static void aDreamerWhoLoggedOutWakesWhereTheyFellAsleep(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-recover-test");
        Vec3 stood = player.position();
        player.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.of(player));
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), UUID.randomUUID());
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        DreamService.recover(player);
        helper.assertTrue(player.position().distanceTo(stood) < 0.01, "a stranded dreamer was not sent back");
        helper.assertFalse(player.hasData(MagicalAttachments.DREAM_RETURN), "the return point outlived the recovery");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_8")
    public static void yourOwnDreamHasNoClockAndRemembersWhereYouLeft(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-own-test");
        helper.assertTrue(DreamService.enter(player, player.getUUID(), true), "your own dream was refused");
        helper.assertTrue(DreamService.session(player.getUUID()).wakesAt == Long.MAX_VALUE, "your own dream has a clock");
        helper.assertTrue(DreamService.dreamingOwn(player.getUUID()), "your own dream is not yours");
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), player.getUUID());
        BlockPos arrival = DreamService.at(scape.plot(), scape.arrival());
        player.teleportTo(arrival.getX() + 2.5, arrival.getY(), arrival.getZ() + 1.5);
        DreamService.wake(player);
        helper.assertTrue(scape.arrival().equals(new Offset(2, 0, 1)), "the arrival is not where you left: " + scape.arrival());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_9")
    public static void aKilledBodyWakesItsDreamerUnhurt(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-killed-test");
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel level = helper.getLevel();
        SleeperEntity body = (SleeperEntity) level.getEntity(DreamService.session(player.getUUID()).sleeperId);
        float before = player.getHealth();
        body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "the body was killed and nothing woke");
            helper.assertTrue(player.isAlive() && player.getHealth() == before, "a /kill on the body killed the dreamer");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_10")
    public static void leavingTheDreamAnyOtherWayWakesYouInYourBody(GameTestHelper helper) {
        ServerPlayer player = sleeper(helper, "dream-escape-test");
        Vec3 stood = player.position();
        DreamService.enter(player, UUID.randomUUID(), false);
        ServerLevel nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) {
            // The test server has only the overworld; stepping into it by any other level is the same route.
            helper.succeed();
            return;
        }
        player.teleportTo(nether, 0.5, 100, 0.5, java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class), 0.0F, 0.0F, true);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(DreamService.dreaming(player.getUUID()), "a dreamer who left the dream is still dreaming");
            helper.assertTrue(player.level() == helper.getLevel() && player.position().distanceTo(stood) < 0.01,
                    "a dreamer who left the dream did not wake in their body");
            helper.succeed();
        });
    }
}
```

- [ ] **Step 2: Run the game tests and confirm they fail**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: compilation fails, because `SleeperEntity`, `DreamSession`, `DreamReturn`, `DreamService.enter` and the others do not exist yet.

- [ ] **Step 3: Write `SleeperEntity`**

`src/main/java/com/efkrdnz/magical/entity/mind/SleeperEntity.java`:

```java
package com.efkrdnz.magical.entity.mind;

import com.efkrdnz.magical.magic.mind.DreamService;
import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A dreamer's body, lying where they fell asleep. It takes no harm of its own: a blow on it wakes the
 * dreamer and lands on them instead. Never saved; a body whose dreamer has gone is just gone.
 */
public class SleeperEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> DREAMER = SynchedEntityData.defineId(SleeperEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(SleeperEntity.class, EntityDataSerializers.STRING);

    public SleeperEntity(EntityType<? extends SleeperEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    /** The dreamer's body, where they stand, facing the way they face. */
    public static SleeperEntity of(ServerPlayer dreamer) {
        SleeperEntity body = new SleeperEntity(MagicalEntities.SLEEPER.get(), dreamer.serverLevel());
        body.moveTo(dreamer.getX(), dreamer.getY(), dreamer.getZ(), dreamer.getYRot(), 0.0F);
        body.yBodyRot = dreamer.getYRot();
        body.yHeadRot = dreamer.getYRot();
        body.entityData.set(DREAMER, Optional.of(dreamer.getUUID()));
        body.entityData.set(NAME, dreamer.getGameProfile().getName());
        return body;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DREAMER, Optional.empty());
        builder.define(NAME, "");
    }

    public Optional<UUID> dreamer() {
        return entityData.get(DREAMER);
    }

    public String dreamerName() {
        return entityData.get(NAME);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        DreamService.sleeperStruck(this, level, source, amount);
        return false;
    }

    @Override
    public Component getName() {
        String name = dreamerName();
        return name.isEmpty() ? super.getName() : Component.literal(name);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
```

- [ ] **Step 4: Register the entity, its attributes and a placeholder renderer**

In `MagicalEntities`, after the `FIGMENT` register:

```java
    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.mind.SleeperEntity>> SLEEPER = ENTITY_TYPES.register(
            "sleeper",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.mind.SleeperEntity>of(com.efkrdnz.magical.entity.mind.SleeperEntity::new, MobCategory.MISC)
                    .sized(1.2F, 0.5F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(2)
                    .noSave()
                    .noSummon()
                    .build(key("sleeper")));
```

In `MagicalEntityEvents.registerAttributes`, after the `FIGMENT` line:

```java
        event.put(MagicalEntities.SLEEPER.get(), com.efkrdnz.magical.entity.mind.SleeperEntity.createAttributes().build());
```

In `MagicalClientEvents.registerRenderers`, after the `FIGMENT` line. Task 7 replaces this line with the real renderer:

```java
        event.registerEntityRenderer(MagicalEntities.SLEEPER.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
```

- [ ] **Step 5: Write `DreamReturn` and register it**

`src/main/java/com/efkrdnz/magical/magic/mind/DreamReturn.java`:

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where a dreamer fell asleep, saved with them, so a dreamer the server loses track of (a logout, a
 * crash) wakes where they lay rather than in somebody's Dreamscape for good.
 */
public final class DreamReturn {
    private String dimension = "";
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public DreamReturn() {}

    public static DreamReturn of(ServerPlayer player) {
        DreamReturn point = new DreamReturn();
        point.dimension = player.level().dimension().location().toString();
        point.x = player.getX();
        point.y = player.getY();
        point.z = player.getZ();
        point.yaw = player.getYRot();
        point.pitch = player.getXRot();
        return point;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    /** The level to wake in; the overworld if the one they fell asleep in is gone. */
    public ServerLevel level(MinecraftServer server) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return level != null ? level : server.overworld();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        return tag;
    }

    public static DreamReturn load(CompoundTag tag) {
        DreamReturn point = new DreamReturn();
        point.dimension = tag.getString("dimension");
        point.x = tag.getDouble("x");
        point.y = tag.getDouble("y");
        point.z = tag.getDouble("z");
        point.yaw = tag.getFloat("yaw");
        point.pitch = tag.getFloat("pitch");
        return point;
    }
}
```

In `MagicalAttachments`, after `SWORD_RACK`. It has no `copyOnDeath`, because a dream cannot kill:

```java
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.efkrdnz.magical.magic.mind.DreamReturn>> DREAM_RETURN = ATTACHMENTS.register(
            "dream_return",
            () -> AttachmentType.builder(com.efkrdnz.magical.magic.mind.DreamReturn::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, com.efkrdnz.magical.magic.mind.DreamReturn>() {
                        @Override
                        public com.efkrdnz.magical.magic.mind.DreamReturn read(IAttachmentHolder holder, CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
                            return com.efkrdnz.magical.magic.mind.DreamReturn.load(tag);
                        }

                        @Override
                        public CompoundTag write(com.efkrdnz.magical.magic.mind.DreamReturn attachment, net.minecraft.core.HolderLookup.Provider provider) {
                            return attachment.save();
                        }
                    })
                    .build());
```

- [ ] **Step 6: Write `DreamSession`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.world.damagesource.DamageSource;

import java.util.UUID;

/** One mind asleep: whose dream it is in, when it ends, where its body lies, and why it should wake now. */
final class DreamSession {
    final UUID dreamer;
    final UUID owner;
    /** The wielder in their own Dreamscape: no clock, no Flaw, free to build. */
    final boolean own;
    final int plot;
    long wakesAt;
    int sleeperId = -1;
    /** Set by anything that wakes the dreamer; the tick does the waking, never the event that noticed. */
    boolean wakeNow;
    /** A blow on the body, landed on the dreamer once they are back in it. */
    DamageSource hurt;
    float hurtAmount;

    DreamSession(UUID dreamer, UUID owner, boolean own, int plot) {
        this.dreamer = dreamer;
        this.owner = owner;
        this.own = own;
        this.plot = plot;
    }
}
```

- [ ] **Step 7: Add the sessions to `DreamService`**

Add these imports:

```java
import com.efkrdnz.magical.entity.mind.SleeperEntity;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
```

Add these members:

```java
    private static final Map<UUID, DreamSession> SESSIONS = new LinkedHashMap<>();

    static DreamSession session(UUID dreamer) {
        return SESSIONS.get(dreamer);
    }

    public static boolean dreaming(UUID player) {
        return SESSIONS.containsKey(player);
    }

    public static boolean dreamingOwn(UUID player) {
        DreamSession session = SESSIONS.get(player);
        return session != null && session.own;
    }

    /** Puts a player to sleep: the body stays as a Sleeper where they stand, the mind goes to the owner's plot. */
    static boolean enter(ServerPlayer dreamer, UUID owner, boolean own) {
        if (SESSIONS.containsKey(dreamer.getUUID())) {
            return false;
        }
        ServerLevel dream = dreamLevel(dreamer.server);
        if (dream == null) {
            return false;
        }
        Dreamscape scape = dreamscape(dream, owner);
        dreamer.setData(MagicalAttachments.DREAM_RETURN, DreamReturn.of(dreamer));
        SleeperEntity body = SleeperEntity.of(dreamer);
        dreamer.serverLevel().addFreshEntity(body);
        DreamSession session = new DreamSession(dreamer.getUUID(), owner, own, scape.plot());
        session.sleeperId = body.getId();
        session.wakesAt = own ? Long.MAX_VALUE : dreamer.server.getTickCount() + DreamRules.DREAM_TICKS;
        SESSIONS.put(dreamer.getUUID(), session);
        BlockPos arrival = at(scape.plot(), scape.arrival());
        move(dreamer, dream, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, scape.arrivalYaw(), 0.0F);
        dream.playSound(null, arrival, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.8F, 0.6F);
        dreamer.displayClientMessage(Component.translatable("message.magical.dream_enter"), true);
        return true;
    }

    /**
     * Back into the body: where it lay, the body gone, any blow it took landed on the dreamer. Works
     * from anywhere, so a dreamer who left the dream by some other route wakes the same way.
     */
    static void wake(ServerPlayer dreamer) {
        DreamSession session = SESSIONS.remove(dreamer.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel dream = dreamLevel(dreamer.server);
        if (session.own && dream != null && dreamer.level() == dream
                && DreamRules.inside(session.plot, dreamer.getX(), dreamer.getY(), dreamer.getZ())) {
            dreamscape(dream, session.owner).setArrival(offsetIn(session.plot, dreamer.blockPosition()), dreamer.getYRot());
            DreamPlots.of(dream).changed();
        }
        if (!dreamer.hasData(MagicalAttachments.DREAM_RETURN)) {
            return;
        }
        DreamReturn back = dreamer.getData(MagicalAttachments.DREAM_RETURN);
        dreamer.removeData(MagicalAttachments.DREAM_RETURN);
        ServerLevel home = back.level(dreamer.server);
        discardBody(home, session.sleeperId);
        move(dreamer, home, back.x(), back.y(), back.z(), back.yaw(), back.pitch());
        home.playSound(null, dreamer.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 0.7F);
        dreamer.displayClientMessage(Component.translatable("message.magical.dream_woke"), true);
        if (session.hurt != null) {
            dreamer.hurtServer(home, session.hurt, session.hurtAmount);
        }
    }

    /** A player the server has no session for but who is still owed a way home: sent back to where they lay. */
    static void recover(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID()) || !player.hasData(MagicalAttachments.DREAM_RETURN)) {
            return;
        }
        DreamReturn back = player.getData(MagicalAttachments.DREAM_RETURN);
        player.removeData(MagicalAttachments.DREAM_RETURN);
        move(player, back.level(player.server), back.x(), back.y(), back.z(), back.yaw(), back.pitch());
    }

    /** A blow on a body. With a dreamer to wake, it wakes them; a body with no dreamer is only a shape, and goes. */
    public static void sleeperStruck(SleeperEntity body, ServerLevel level, DamageSource source, float amount) {
        DreamSession session = body.dreamer().map(SESSIONS::get).orElse(null);
        if (session == null || session.sleeperId != body.getId()) {
            body.discard();
            return;
        }
        if (!source.is(DamageTypes.GENERIC_KILL)) {
            session.hurt = source;
            session.hurtAmount = amount;
        }
        session.wakeNow = true;
    }

    /** A click on a block in a dream: the Flaw wakes whoever found it. */
    static void touched(ServerPlayer player, BlockPos pos) {
        DreamSession session = SESSIONS.get(player.getUUID());
        ServerLevel dream = dreamLevel(player.server);
        if (session == null || session.own || dream == null) {
            return;
        }
        Dreamscape.Flaw flaw = dreamscape(dream, session.owner).flaw();
        if (flaw != null && flaw.block() != null && at(session.plot, flaw.block()).equals(pos)) {
            session.wakeNow = true;
        }
    }

    /** A blow on, or a hand laid on, a creature in a dream: the Flaw wakes whoever found it. */
    static void touched(ServerPlayer player, Entity target) {
        DreamSession session = SESSIONS.get(player.getUUID());
        ServerLevel dream = dreamLevel(player.server);
        if (session == null || session.own || dream == null) {
            return;
        }
        Dreamscape.Flaw flaw = dreamscape(dream, session.owner).flaw();
        if (flaw != null && target.getUUID().equals(flaw.figment())) {
            session.wakeNow = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dream = dreamLevel(server);
        for (DreamSession session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.dreamer);
            if (player == null) {
                SESSIONS.remove(session.dreamer);
                continue;
            }
            // Left the dream level by some other route (a skill, a command): the dream is simply over.
            if (dream == null || player.level() != dream || session.wakeNow || server.getTickCount() >= session.wakesAt) {
                wake(player);
                continue;
            }
            Dreamscape scape = dreamscape(dream, session.owner);
            if (!DreamRules.inside(session.plot, player.getX(), player.getY(), player.getZ())) {
                BlockPos arrival = at(scape.plot(), scape.arrival());
                move(player, dream, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, scape.arrivalYaw(), 0.0F);
                continue;
            }
            if (!session.own && touchesFlaw(dream, player, scape)) {
                wake(player);
            }
        }
    }

    /** A dream cannot kill: a blow that would leave less than a heart leaves exactly one, and wakes them. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DreamSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        float health = player.getHealth();
        float incoming = event.getNewDamage();
        if (DreamRules.wakes(health, incoming)) {
            event.setNewDamage(DreamRules.dealt(health, incoming));
            session.wakeNow = true;
        }
    }

    /** What gets past the damage event (a /kill, the void) still does not kill a dreamer. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
            event.setCanceled(true);
            player.setHealth(DreamRules.ONE_HEART);
            SESSIONS.get(player.getUUID()).wakeNow = true;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamSession session = SESSIONS.remove(player.getUUID());
            if (session != null && player.hasData(MagicalAttachments.DREAM_RETURN)) {
                discardBody(player.getData(MagicalAttachments.DREAM_RETURN).level(player.server), session.sleeperId);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recover(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
    }

    private static boolean touchesFlaw(ServerLevel dream, ServerPlayer player, Dreamscape scape) {
        Dreamscape.Flaw flaw = scape.flaw();
        if (flaw == null) {
            return false;
        }
        AABB reach = player.getBoundingBox().inflate(0.05);
        if (flaw.block() != null) {
            BlockPos pos = at(scape.plot(), flaw.block());
            return !dream.getBlockState(pos).isAir() && reach.intersects(new AABB(pos));
        }
        Entity figment = dream.getEntity(flaw.figment());
        return figment != null && reach.intersects(figment.getBoundingBox());
    }

    private static void discardBody(ServerLevel level, int id) {
        if (level.getEntity(id) instanceof SleeperEntity body) {
            body.discard();
        }
    }

    private static void move(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }
```

- [ ] **Step 8: Add the language keys**

In `src/main/resources/assets/magical/lang/en_us.json`, after `"message.magical.unmanifested": ...,`:

```json
  "message.magical.dream_enter": "You fall asleep.",
  "message.magical.dream_woke": "You wake.",
  "entity.magical.sleeper": "Sleeper",
```

- [ ] **Step 9: Run the tests and confirm they pass**

Run: `.\gradlew build`, then `.\gradlew runGameTestServer --console=plain`
Expected: BUILD SUCCESSFUL, and every game test passes, including `dream_1` to `dream_10`. If the test server loads no Nether, `dream_10` passes trivially. It still compiles and runs, and the tick path it names is the same `wake` call.

- [ ] **Step 10: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/entity/mind/SleeperEntity.java src/main/java/com/efkrdnz/magical/magic/mind/DreamReturn.java src/main/java/com/efkrdnz/magical/magic/mind/DreamSession.java src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java src/main/java/com/efkrdnz/magical/magic/mind/DreamGameTests.java src/main/java/com/efkrdnz/magical/registry/MagicalEntities.java src/main/java/com/efkrdnz/magical/registry/MagicalEntityEvents.java src/main/java/com/efkrdnz/magical/registry/MagicalAttachments.java src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java src/main/resources/assets/magical/lang/en_us.json
git commit -m "feat(mind): a dreamer's body lies where they fell asleep, and a dream cannot kill

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Nothing leaves a dream

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamGuard.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java` (add `figmentGone`)
- Test: `src/main/java/com/efkrdnz/magical/magic/mind/DreamGuardGameTests.java`

**Interfaces:**
- **Consumes (Tasks 2-3):**
  - `DreamService`: `isDream(...)`, `touched(...)`, `dreamingOwn(UUID)`, `dreamscape`, `DREAM_TAG`, `testLevel`.
  - `DreamPlots.ownerOf`.
  - `DreamRules.plotAt`.
- **Produces:**
  - `DreamGuard` (event handlers only).
  - `DreamService.figmentGone(ServerLevel, Entity)`.

- [ ] **Step 1: Write the failing game tests**

`src/main/java/com/efkrdnz/magical/magic/mind/DreamGuardGameTests.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamGuardGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private static BlockPos plotCell(GameTestHelper helper, Offset offset) {
        DreamService.testLevel = helper.getLevel();
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), UUID.randomUUID());
        return DreamService.at(scape.plot(), offset);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_1")
    public static void noItemAndNoExperienceEverAppearInADream(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(0, 0, 0));
        ServerLevel level = helper.getLevel();
        helper.assertFalse(level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.DIAMOND))),
                "a diamond appeared in a dream");
        helper.assertFalse(level.addFreshEntity(new ExperienceOrb(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 10)),
                "experience appeared in a dream");
        BlockPos outside = helper.absolutePos(new BlockPos(2, 2, 2));
        ItemEntity awake = new ItemEntity(level, outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, new ItemStack(Items.DIAMOND));
        helper.assertTrue(level.addFreshEntity(awake), "the waking world lost its items too");
        awake.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_2")
    public static void nothingInADreamCanBeBrokenByHand(GameTestHelper helper) {
        BlockPos wall = plotCell(helper, new Offset(1, 0, 0));
        ServerLevel level = helper.getLevel();
        level.setBlock(wall, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-miner-test");
        player.teleportTo(wall.getX() - 0.5, wall.getY(), wall.getZ() + 0.5);
        helper.assertFalse(player.gameMode.destroyBlock(wall), "a dream block was broken");
        helper.assertTrue(level.getBlockState(wall).is(Blocks.DIAMOND_BLOCK), "the dream block is gone");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_3")
    public static void anExplosionInADreamTakesNoBlocks(GameTestHelper helper) {
        BlockPos wall = plotCell(helper, new Offset(1, 0, 0));
        ServerLevel level = helper.getLevel();
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.explode(null, wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() - 0.5, 3.0F, Level.ExplosionInteraction.TNT);
        helper.assertTrue(level.getBlockState(wall).is(Blocks.STONE), "an explosion broke a dream");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_4")
    public static void aFlawThatDiesIsNoLongerTheFlaw(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        Dreamscape scape = DreamService.dreamscape(level, owner);
        BlockPos at = DreamService.at(scape.plot(), new Offset(0, 0, 2));
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        zombie.addTag(DreamService.DREAM_TAG);
        level.addFreshEntity(zombie);
        scape.markFigment(zombie.getUUID());
        zombie.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(scape.flaw() == null, "a dead Flaw is still the Flaw");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_5")
    public static void aDreamedMobNeverTurnsOnItsDreamer(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-owner-test");
        DreamService.enter(owner, owner.getUUID(), true);
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.moveTo(owner.getX() + 2, owner.getY(), owner.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(zombie);
        zombie.setTarget(owner);
        helper.assertTrue(zombie.getTarget() == null, "a mob in your own dream hunts you");
        DreamService.wake(owner);
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_guard_6")
    public static void whatADreamerThrowsComesBackToTheirHand(GameTestHelper helper) {
        BlockPos at = plotCell(helper, new Offset(0, 0, 0));
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-toss-test");
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.getInventory().clearContent();
        player.drop(new ItemStack(Items.DIAMOND, 3), false, true);
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3, "a thrown diamond was lost to the dream");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run the game tests and confirm they fail**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: `dream_guard_1` to `dream_guard_6` FAIL, with messages such as "a diamond appeared in a dream" and "a dream block was broken".

- [ ] **Step 3: Write `DreamGuard`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * A dream is real while you are in it and worth nothing when you leave: no item and no experience can
 * appear in it (what a dreamer throws comes back to their hand), nothing in it is broken or placed by
 * hand, nothing carried is used, a block answers only an empty hand, no creature can be milked,
 * sheared or traded with, and no explosion or grief takes a block. What a dreamer carries in they
 * carry out, and nothing else.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class DreamGuard {
    private DreamGuard() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemEntity || entity instanceof ExperienceOrb)
                || !DreamService.isDream(event.getLevel(), entity.getX(), entity.getZ())) {
            return;
        }
        event.setCanceled(true);
        if (entity instanceof ItemEntity item && item.getOwner() instanceof ServerPlayer thrower) {
            thrower.getInventory().add(item.getItem());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && DreamService.isDream(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && DreamService.isDream(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (DreamService.isDream(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!DreamService.isDream(event.getLevel(), event.getPos())) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamService.touched(player, event.getPos());
        }
        Player player = event.getEntity();
        event.setUseItem(TriState.FALSE);
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && DreamService.isDream(event.getLevel(), event.getPos())) {
            DreamService.touched(player, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (DreamService.isDream(event.getTarget())) {
            if (event.getEntity() instanceof ServerPlayer player) {
                DreamService.touched(player, event.getTarget());
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (DreamService.isDream(event.getTarget())) {
            if (event.getEntity() instanceof ServerPlayer player) {
                DreamService.touched(player, event.getTarget());
            }
            event.setCanceled(true);
        }
    }

    /** A blow in a dream is a real blow; one on the Flaw also ends the dream. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DreamService.isDream(event.getTarget())) {
            DreamService.touched(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> DreamService.isDream(level, pos));
    }

    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        if (DreamService.isDream(event.getEntity())) {
            event.setCanGrief(false);
        }
    }

    /** A wielder building their own dream is never hunted by what they dreamed. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && DreamService.dreamingOwn(player.getUUID())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getTags().contains(DreamService.DREAM_TAG) && entity.level() instanceof ServerLevel level
                && DreamService.isDream(entity)) {
            DreamService.figmentGone(level, entity);
        }
    }
}
```

- [ ] **Step 4: Add `figmentGone` to `DreamService`**

```java
    /** A dreamed creature died: if it was its plot's Flaw, the plot has none now. */
    static void figmentGone(ServerLevel dream, Entity figment) {
        int plot = DreamRules.plotAt(figment.getX(), figment.getZ());
        UUID owner = plot < 0 ? null : DreamPlots.of(dream).ownerOf(plot);
        if (owner != null && dreamscape(dream, owner).clearIfFlaw(figment.getUUID())) {
            DreamPlots.of(dream).changed();
        }
    }
```

- [ ] **Step 5: Run the game tests and confirm they pass**

Run: `.\gradlew runGameTestServer --console=plain`
Expected: every game test passes, including `dream_guard_1` to `dream_guard_6`.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/DreamGuard.java src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java src/main/java/com/efkrdnz/magical/magic/mind/DreamGuardGameTests.java
git commit -m "feat(mind): nothing is carried out of a dream

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Lull

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicContent.java` (after the `INSIST` register; `AUTHORITY_SKILLS`)
- Modify: `src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java` (the Mind skill list)
- Modify: `src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java` (after the `INSIST` handler)
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindService.java` (`payFor` becomes package-private)
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java` (add `lull`, `enterOwn`, `sure`, `flawStands`)
- Modify: `src/main/resources/assets/magical/lang/en_us.json`
- Modify: `src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java`
- Test: `src/main/java/com/efkrdnz/magical/magic/mind/LullGameTests.java`

**Interfaces:**
- **Consumes:**
  - `MindService.scenesOf(UUID)`, `MindService.UNVEIL_REACH` (24.0), and `MindService.payFor(ServerPlayer, PlayerMagicState, MagicSkillDefinition, int)`.
  - `AimResolver.resolve(ServerLevel, LivingEntity, double, double, boolean)`.
  - `MagicStatusService.apply(LivingEntity, MagicStatus, int, ResourceLocation, Entity)` and `MagicStatus.ASLEEP`.
  - From Task 3: `DreamService.enter/wake/dreamscape/at`.
- **Produces:**
  - `MagicContent.LULL`.
  - `public static boolean DreamService.lull(ServerPlayer, PlayerMagicState)`.

- [ ] **Step 1: Write the failing tests**

In `MindAuthorityTest`, rename `theAuthorityGrantsDaydreamUnveilAndInsistAndNothingElse` to `theAuthorityGrantsItsFourSkillsAndNothingElse`, with this body:

```java
    @Test
    void theAuthorityGrantsItsFourSkillsAndNothingElse() {
        assertEquals(List.of(MagicContent.DAYDREAM.id(), MagicContent.UNVEIL.id(), MagicContent.INSIST.id(), MagicContent.LULL.id()),
                AuthorityContent.get(AuthorityContent.MIND).skillIds());
        for (var skill : List.of(MagicContent.DAYDREAM, MagicContent.UNVEIL, MagicContent.INSIST, MagicContent.LULL)) {
            assertTrue(MagicContent.AUTHORITY_SKILLS.contains(skill.id()), skill.id() + " is not an authority skill");
            assertEquals(-6, skill.tier());
        }
        assertTrue(AuthorityContent.commandIds().contains("authority_of_mind"));
    }
```

In `everyStringTheStageDrawsIsInTheLanguageFile`, add these to the key list:

```java
                "skill.magical.lull", "skill.magical.lull.desc", "message.magical.lull_nobody",
                "message.magical.lull_unsure", "message.magical.lull_no_flaw", "message.magical.lull_no_dream",
                "message.magical.lull_dreaming", "message.magical.dream_enter", "message.magical.dream_woke",
                "entity.magical.sleeper",
```

`src/main/java/com/efkrdnz/magical/magic/mind/LullGameTests.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class LullGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private static ServerPlayer wielder(GameTestHelper helper, String name) {
        DreamService.testLevel = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 0), name);
        player.getData(MagicalAttachments.MAGIC_STATE).setAuthority(AuthorityContent.MIND);
        return player;
    }

    /** A live scene of the wielder's, off to one side, and the viewer believing its first element this much. */
    private static void believes(GameTestHelper helper, ServerPlayer wielder, LivingEntity viewer, float belief) {
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(0, 2, 4)));
        LiveScene scene = MindGameTests.unveil(helper, wielder.getUUID(), MindGameTests.column(), anchor);
        scene.belief().set(viewer.getId(), 0, belief);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_1")
    public static void aSureMobFallsAsleepAndItCosts(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-mob-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 3))));
        believes(helper, wielder, husk, 0.9F);
        wielder.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getEyePosition());
        int before = state.mana();
        helper.assertTrue(DreamService.lull(wielder, state), "a sure husk would not sleep");
        helper.assertTrue(MagicStatusService.has(husk, MagicStatus.ASLEEP), "the husk is awake");
        helper.assertTrue(state.mana() < before, "Lull was free");
        helper.assertTrue(state.isSkillOnCooldown(MagicContent.LULL.id()), "Lull has no clock");
        MindService.endAll(wielder.getUUID());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_2")
    public static void anUnsureMobStaysAwakeAndItCostsNothing(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-unsure-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 3))));
        believes(helper, wielder, husk, 0.6F);
        wielder.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getEyePosition());
        int before = state.mana();
        helper.assertFalse(DreamService.lull(wielder, state), "a half-believer was lulled");
        helper.assertFalse(MagicStatusService.has(husk, MagicStatus.ASLEEP), "the husk sleeps");
        helper.assertTrue(state.mana() == before && !state.isSkillOnCooldown(MagicContent.LULL.id()), "a refusal was billed");
        MindService.endAll(wielder.getUUID());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_3")
    public static void aSurePlayerFallsIntoYourDreamscape(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-wielder-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        ServerPlayer dreamer = GameTestPlayers.another(helper, new BlockPos(2, 2, 3), "lull-dreamer-test");
        dreamer.getData(MagicalAttachments.MAGIC_STATE).setBarrier(0);
        DreamGameTests.withFlaw(helper.getLevel(), wielder.getUUID());
        believes(helper, wielder, dreamer, 0.9F);
        wielder.lookAt(EntityAnchorArgument.Anchor.EYES, dreamer.getEyePosition());
        helper.assertTrue(DreamService.lull(wielder, state), "a sure player would not sleep");
        helper.assertTrue(DreamService.dreaming(dreamer.getUUID()), "the player is not dreaming");
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), wielder.getUUID());
        helper.assertTrue(DreamRules.plotAt(dreamer.getX(), dreamer.getZ()) == scape.plot(), "the player is not in your Dreamscape");
        DreamService.wake(dreamer);
        MindService.endAll(wielder.getUUID());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_4")
    public static void aDreamscapeWithNoFlawTakesNobody(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-flawless-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        ServerPlayer dreamer = GameTestPlayers.another(helper, new BlockPos(2, 2, 3), "lull-safe-test");
        believes(helper, wielder, dreamer, 0.9F);
        wielder.lookAt(EntityAnchorArgument.Anchor.EYES, dreamer.getEyePosition());
        int before = state.mana();
        helper.assertFalse(DreamService.lull(wielder, state), "a Dreamscape with no Flaw took a dreamer");
        helper.assertFalse(DreamService.dreaming(dreamer.getUUID()), "the player is dreaming");
        helper.assertTrue(state.mana() == before, "a refusal was billed");
        MindService.endAll(wielder.getUUID());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_5")
    public static void sneakingLullsYouIntoYourOwnDreamAndLullAgainWakesYou(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-self-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        int before = state.mana();
        wielder.setShiftKeyDown(true);
        helper.assertTrue(DreamService.lull(wielder, state), "sneaking did not lull you");
        helper.assertTrue(DreamService.dreamingOwn(wielder.getUUID()), "you are not in your own dream");
        helper.assertTrue(state.mana() == before && !state.isSkillOnCooldown(MagicContent.LULL.id()), "your own dream was billed");
        wielder.setShiftKeyDown(false);
        helper.assertTrue(DreamService.lull(wielder, state), "Lull in your own dream did not wake you");
        helper.assertFalse(DreamService.dreaming(wielder.getUUID()), "you are still dreaming");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.mind.MindAuthorityTest"`
Expected: compilation fails, because `MagicContent.LULL` does not exist yet.

- [ ] **Step 3: Register the skill**

In `MagicContent`, after the `INSIST` register:

```java
    public static final MagicSkillDefinition LULL = register("lull", MagicSchool.ARCANE, MagicSkillType.BURST, -6, 0, 0.0F, 0.0F, 1.0F, 60, 1200, 20, 0.0F, 0, 0xA990FF, MagicAttribute.ARCANE);
```

In `AUTHORITY_SKILLS`, change the last line to:

```java
        DAYDREAM.id(), UNVEIL.id(), INSIST.id(), LULL.id());
```

In `AuthorityContent.AUTHORITY_OF_MIND`:

```java
            List.of(MagicContent.DAYDREAM.id(), MagicContent.UNVEIL.id(), MagicContent.INSIST.id(), MagicContent.LULL.id()));
```

In `MagicCastContentKept`, after the `INSIST` handler:

```java
        // Lull bills itself: a sneak into your own dream is free, a refusal costs nothing.
        SkillCastRegistry.register(MagicContent.LULL, SkillCastRegistry.selfManaged(ctx ->
                com.efkrdnz.magical.magic.mind.DreamService.lull(ctx.player(), ctx.state())));
```

In `MindService`, change `private static boolean payFor(` to `static boolean payFor(`.

- [ ] **Step 4: Add Lull to `DreamService`**

Add these imports:

```java
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
```

Add these members:

```java
    /** How far off the crosshair a body may stand and still be the one Lull means. */
    static final double LULL_TOLERANCE = 0.5;

    /**
     * The Lull press. In your own dream, it wakes you. Sneaking, it takes you into your own dream, free.
     * Aimed at a mob that is sure of your scene, the mob sleeps; at a player who is, they fall into your
     * Dreamscape, if it has a Flaw. Billed only once there is something to do.
     */
    public static boolean lull(ServerPlayer wielder, PlayerMagicState state) {
        DreamSession mine = SESSIONS.get(wielder.getUUID());
        if (mine != null) {
            if (mine.own) {
                wake(wielder);
                return true;
            }
            return false;
        }
        if (isDream(wielder)) {
            return false;
        }
        if (wielder.isShiftKeyDown()) {
            return enterOwn(wielder);
        }
        ServerLevel level = wielder.serverLevel();
        AimResolver.Result aim = AimResolver.resolve(level, wielder, MindService.UNVEIL_REACH, LULL_TOLERANCE, false);
        LivingEntity target = aim.living();
        if (!(target instanceof Mob) && !(target instanceof ServerPlayer)) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_nobody"), true);
            return false;
        }
        if (!sure(wielder.getUUID(), target)) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_unsure"), true);
            return false;
        }
        if (target instanceof Mob mob) {
            if (!MindService.payFor(wielder, state, MagicContent.LULL, DreamRules.LULL_MANA)) {
                return false;
            }
            MagicStatusService.apply(mob, MagicStatus.ASLEEP, DreamRules.MOB_SLEEP_TICKS, MagicContent.LULL.id(), wielder);
            level.sendParticles(new DustParticleOptions(0xBDA4FF, 1.2F), mob.getX(), mob.getEyeY() + 0.4, mob.getZ(),
                    12, 0.3, 0.2, 0.3, 0.0);
            state.sync(wielder);
            return true;
        }
        ServerPlayer dreamer = (ServerPlayer) target;
        if (SESSIONS.containsKey(dreamer.getUUID())) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_dreaming"), true);
            return false;
        }
        ServerLevel dream = dreamLevel(wielder.server);
        if (dream == null) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_no_dream"), true);
            return false;
        }
        if (!flawStands(dream, dreamscape(dream, wielder.getUUID()))) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_no_flaw"), true);
            return false;
        }
        if (!MindService.payFor(wielder, state, MagicContent.LULL, DreamRules.LULL_MANA)) {
            return false;
        }
        enter(dreamer, wielder.getUUID(), false);
        state.sync(wielder);
        return true;
    }

    /** Into your own dream, to build it: free, no clock, and no Flaw needed yet. */
    static boolean enterOwn(ServerPlayer wielder) {
        if (dreamLevel(wielder.server) == null) {
            wielder.displayClientMessage(Component.translatable("message.magical.lull_no_dream"), true);
            return false;
        }
        return enter(wielder, wielder.getUUID(), true);
    }

    /** Whether the viewer believes some live element of the wielder's, in its own level, at 0.8 or more. */
    static boolean sure(UUID owner, LivingEntity viewer) {
        for (LiveScene scene : MindService.scenesOf(owner)) {
            if (!scene.dimension().equals(viewer.level().dimension())) {
                continue;
            }
            for (LiveScene.Element element : scene.elements()) {
                if (scene.belief().get(viewer.getId(), element.index()) >= DreamRules.LULL_BELIEF) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A Flaw that is still there: a block not yet air, or a figment not yet dead (a dead one clears itself). */
    static boolean flawStands(ServerLevel dream, Dreamscape scape) {
        Dreamscape.Flaw flaw = scape.flaw();
        if (flaw == null) {
            return false;
        }
        return flaw.figment() != null || !dream.getBlockState(at(scape.plot(), flaw.block())).isAir();
    }
```

- [ ] **Step 5: Add the language keys**

In `en_us.json`, after `"skill.magical.insist.desc": ...,`:

```json
  "skill.magical.lull": "Lull",
  "skill.magical.lull.desc": "Press on someone who believes your scene completely. A creature sleeps where it stands; a player falls into your Dreamscape and wakes only by finding its Flaw. Sneak and press to dream yourself, and build it.",
```

After `"message.magical.dream_woke": "You wake.",`:

```json
  "message.magical.lull_nobody": "Nobody there to lull.",
  "message.magical.lull_unsure": "They do not believe you enough to sleep.",
  "message.magical.lull_no_flaw": "Your Dreamscape has no Flaw. Dream it yourself and mark one.",
  "message.magical.lull_no_dream": "There is nowhere to dream.",
  "message.magical.lull_dreaming": "They are already dreaming.",
```

- [ ] **Step 6: Run the tests and confirm they pass**

Run: `.\gradlew test`, then `.\gradlew runGameTestServer --console=plain`
Expected: every unit test passes, including `MindAuthorityTest`, and every game test passes, including `lull_1` to `lull_5`.

- [ ] **Step 7: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/MagicContent.java src/main/java/com/efkrdnz/magical/magic/AuthorityContent.java src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentKept.java src/main/java/com/efkrdnz/magical/magic/mind/MindService.java src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java src/main/java/com/efkrdnz/magical/magic/mind/LullGameTests.java src/main/resources/assets/magical/lang/en_us.json src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java
git commit -m "feat(mind): Lull puts the sure to sleep, and a player into your Dreamscape

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Building your own dream — the server end

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/network/DreamEditPayload.java`
- Create: `src/main/java/com/efkrdnz/magical/network/DreamStatePayload.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamBuilder.java`
- Create: `src/main/java/com/efkrdnz/magical/client/mind/ClientDream.java`
- Modify: `src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java` (register both payloads; add `sendDreamEdit` and `sendDreamState`; bump the registrar `"9"` to `"10"`)
- Modify: `src/main/java/com/efkrdnz/magical/client/ClientPayloadHandlers.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java` (add `sendState`, and call it from `enter`, `wake` and `figmentGone`)
- Modify: `src/main/resources/assets/magical/lang/en_us.json`
- Modify: `src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java`
- Test: `src/test/java/com/efkrdnz/magical/network/DreamPayloadsTest.java`
- Test: `src/main/java/com/efkrdnz/magical/magic/mind/DreamBuilderGameTests.java`

**Interfaces:**
- **Consumes:**
  - From Tasks 2-3: `DreamService.session`, `dreamscape`, `at`, `offsetIn`, `dreamLevel`, `DREAM_TAG`, and `DreamPlots.changed`.
  - `Manifestation.stateOf(String)` (package-private, existing; returns null for an unknown id).
  - `Impression.parse`, `Impression.kind()`, `Impression.id()`, `Impression.key()`.
  - `Lexicon.knows`.
- **Produces:**
  - `DreamEditPayload(int action, List<BlockPos> cells, String impression, int entity)`, with `PLACE = 0`, `ERASE = 1`, `FLAW = 2`.
  - `DreamStatePayload(boolean ownDream, Optional<BlockPos> flawBlock, int flawEntity)`.
  - `DreamBuilder.apply(ServerPlayer, DreamEditPayload)`, which returns a boolean.
  - `DreamService.sendState(ServerPlayer)`.
  - `MagicalNetwork.sendDreamEdit(DreamEditPayload)` and `sendDreamState(ServerPlayer, DreamStatePayload)`.
  - `ClientDream.ownDream()`, `flawBlock()`, `flawEntity()`, and `accept(DreamStatePayload)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/efkrdnz/magical/network/DreamPayloadsTest.java`:

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DreamPayloadsTest {
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
    void anEditRoundTrips() {
        roundTrip(DreamEditPayload.STREAM_CODEC, new DreamEditPayload(DreamEditPayload.PLACE,
                List.of(new BlockPos(1_000_000, 100, 1_000_001), new BlockPos(1_000_001, 100, 1_000_001)), "block:minecraft:stone", -1));
        roundTrip(DreamEditPayload.STREAM_CODEC, new DreamEditPayload(DreamEditPayload.FLAW, List.of(), "", 42));
    }

    @Test
    void anEditCarriesNoMoreThanABrushFull() {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i <= DreamEditPayload.MAX_CELLS; i++) {
            cells.add(new BlockPos(i, 0, 0));
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(Exception.class, () -> DreamEditPayload.STREAM_CODEC.encode(buffer, new DreamEditPayload(0, cells, "", -1)));
        } finally {
            buffer.release();
        }
    }

    @Test
    void aStateRoundTrips() {
        roundTrip(DreamStatePayload.STREAM_CODEC, new DreamStatePayload(true, Optional.of(new BlockPos(1_000_001, 101, 1_000_000)), -1));
        roundTrip(DreamStatePayload.STREAM_CODEC, new DreamStatePayload(true, Optional.empty(), 17));
        roundTrip(DreamStatePayload.STREAM_CODEC, new DreamStatePayload(false, Optional.empty(), -1));
    }
}
```

`src/main/java/com/efkrdnz/magical/magic/mind/DreamBuilderGameTests.java`:

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.DreamEditPayload;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class DreamBuilderGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    /** A wielder in their own dream, who has studied stone, a chest and cows. */
    private static ServerPlayer builder(GameTestHelper helper, String name) {
        DreamService.testLevel = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), name);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        state.mind().lexicon().learn("block:minecraft:stone", 5);
        state.mind().lexicon().learn("block:minecraft:chest", 5);
        state.mind().lexicon().learn("creature:minecraft:cow", 5);
        DreamService.enter(player, player.getUUID(), true);
        return player;
    }

    private static BlockPos cell(ServerPlayer player, int x, int y, int z) {
        Dreamscape scape = DreamService.dreamscape((ServerLevel) player.level(), player.getUUID());
        return DreamService.at(scape.plot(), new Offset(x, y, z));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_1")
    public static void whatYouStudiedIsMadeRealAndNothingElse(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-build-test");
        ServerLevel level = helper.getLevel();
        BlockPos a = cell(player, 2, 0, 0);
        BlockPos b = cell(player, 2, 1, 0);
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(a, b), "block:minecraft:stone", -1)),
                "known stone was refused");
        helper.assertTrue(level.getBlockState(a).is(Blocks.STONE) && level.getBlockState(b).is(Blocks.STONE), "the stone is not real");
        BlockPos c = cell(player, 3, 0, 0);
        helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(c), "block:minecraft:diamond_block", -1)),
                "an unstudied block was made real");
        helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(c), "block:minecraft:chest", -1)),
                "a block with a block entity was dreamed");
        helper.assertTrue(level.getBlockState(c).isAir(), "something stands where nothing should");
        DreamService.wake(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_2")
    public static void nothingIsBuiltOutOfReachOrOutsideThePlot(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-reach-test");
        ServerLevel level = helper.getLevel();
        BlockPos far = cell(player, 12, 0, 0);
        BlockPos outside = cell(player, DreamRules.PLOT_HALF + 2, 0, 0);
        helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(far, outside), "block:minecraft:stone", -1)),
                "a block was built out of reach");
        helper.assertTrue(level.getBlockState(far).isAir() && level.getBlockState(outside).isAir(), "a block stands out of reach");
        DreamService.wake(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_3")
    public static void theFlawIsMarkedAndUnmakingItUnmarksIt(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-flaw-mark-test");
        ServerLevel level = helper.getLevel();
        BlockPos wall = cell(player, 2, 0, 0);
        DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(wall), "block:minecraft:stone", -1));
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.FLAW, List.of(wall), "", -1)), "the Flaw was not marked");
        Dreamscape scape = DreamService.dreamscape(level, player.getUUID());
        helper.assertTrue(new Offset(2, 0, 0).equals(scape.flaw().block()), "the wrong thing is the Flaw");
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.ERASE, List.of(wall), "", -1)), "the Flaw could not be unmade");
        helper.assertTrue(level.getBlockState(wall).isAir(), "the unmade block stands");
        helper.assertTrue(scape.flaw() == null, "an unmade block is still the Flaw");
        DreamService.wake(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_4")
    public static void aDreamedCowIsARealCowThatStaysAndCanBeTheFlaw(GameTestHelper helper) {
        ServerPlayer player = builder(helper, "dream-cow-test");
        ServerLevel level = helper.getLevel();
        BlockPos at = cell(player, 0, 0, 2);
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(at), "creature:minecraft:cow", -1)),
                "a studied cow was refused");
        List<Mob> cows = level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(1.0), mob -> mob.getTags().contains(DreamService.DREAM_TAG));
        helper.assertTrue(cows.size() == 1 && cows.get(0).isPersistenceRequired(), "the cow is not a lasting dreamed cow");
        Entity cow = cows.get(0);
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.FLAW, List.of(), "", cow.getId())), "the cow could not be the Flaw");
        helper.assertTrue(cow.getUUID().equals(DreamService.dreamscape(level, player.getUUID()).flaw().figment()), "the cow is not the Flaw");
        helper.assertTrue(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.ERASE, List.of(), "", cow.getId())), "the cow could not be unmade");
        helper.assertTrue(cow.isRemoved() && DreamService.dreamscape(level, player.getUUID()).flaw() == null, "the unmade cow lingers");
        DreamService.wake(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "dream_build_5")
    public static void nobodyBuildsADreamTheyAreNotDreaming(GameTestHelper helper) {
        DreamService.testLevel = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 2), "dream-awake-test");
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        state.mind().lexicon().learn("block:minecraft:stone", 5);
        BlockPos near = helper.absolutePos(new BlockPos(2, 2, 3));
        helper.assertFalse(DreamBuilder.apply(player, new DreamEditPayload(DreamEditPayload.PLACE, List.of(near), "block:minecraft:stone", -1)),
                "a waking player built with a dream");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.network.DreamPayloadsTest"`
Expected: compilation fails, because the payloads do not exist yet.

- [ ] **Step 3: Write the payloads**

`src/main/java/com/efkrdnz/magical/network/DreamEditPayload.java`:

```java
package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.mind.Brush;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** One Daydream edit in the wielder's own dream: real blocks placed or unmade, or the Flaw marked. Re-checked on arrival. */
public record DreamEditPayload(int action, List<BlockPos> cells, String impression, int entity) implements CustomPacketPayload {
    public static final int PLACE = 0;
    public static final int ERASE = 1;
    public static final int FLAW = 2;
    public static final int MAX_CELLS = Brush.MAX_CELLS;

    public static final Type<DreamEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dream_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DreamEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DreamEditPayload::action,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)), DreamEditPayload::cells,
            ByteBufCodecs.stringUtf8(128), DreamEditPayload::impression,
            ByteBufCodecs.VAR_INT, DreamEditPayload::entity,
            DreamEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

`src/main/java/com/efkrdnz/magical/network/DreamStatePayload.java`:

```java
package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** To the wielder: whether they are in their own dream now, and where its Flaw is (a block, or an entity id, or neither). */
public record DreamStatePayload(boolean ownDream, Optional<BlockPos> flawBlock, int flawEntity) implements CustomPacketPayload {
    public static final Type<DreamStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dream_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DreamStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DreamStatePayload::ownDream,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), DreamStatePayload::flawBlock,
            ByteBufCodecs.VAR_INT, DreamStatePayload::flawEntity,
            DreamStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 4: Register the payloads**

In `MagicalNetwork.registerPayloads`, change `event.registrar("9")` to `event.registrar("10")`. Then add these to the chain after the `SaveReveriePayload` entry:

```java
                .playToServer(DreamEditPayload.TYPE, DreamEditPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.efkrdnz.magical.magic.mind.DreamBuilder.apply(player, payload);
                            }
                        }))
                .playToClient(DreamStatePayload.TYPE, DreamStatePayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> handleClientPayload(payload)))
```

In `MagicalNetwork`, beside `sendSaveReverie`:

```java
    /** A Daydream edit in the wielder's own dream. Re-checked on the server before anything is built. */
    public static void sendDreamEdit(DreamEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendDreamState(ServerPlayer player, DreamStatePayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
```

In `ClientPayloadHandlers`, after the `BeliefSyncPayload` handler:

```java
    public static void handle(com.efkrdnz.magical.network.DreamStatePayload payload) {
        com.efkrdnz.magical.client.mind.ClientDream.accept(payload);
    }
```

`src/main/java/com/efkrdnz/magical/client/mind/ClientDream.java`:

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.network.DreamStatePayload;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** What the client knows of its own dream: whether it is in it, and where its Flaw is. */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class ClientDream {
    private static boolean ownDream;
    private static BlockPos flawBlock;
    private static int flawEntity = -1;

    private ClientDream() {}

    public static void accept(DreamStatePayload payload) {
        ownDream = payload.ownDream();
        flawBlock = payload.flawBlock().orElse(null);
        flawEntity = payload.flawEntity();
    }

    public static boolean ownDream() { return ownDream; }
    public static BlockPos flawBlock() { return flawBlock; }
    public static int flawEntity() { return flawEntity; }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ownDream = false;
        flawBlock = null;
        flawEntity = -1;
    }
}
```

- [ ] **Step 5: Write `DreamBuilder`**

```java
package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.DreamEditPayload;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The server end of Daydream in your own dream, where belief is total and what you draw is real: blocks
 * and creatures from your Lexicon, free, inside your plot and within reach; unmaking them; and marking
 * the one thing that is wrong. Every edit is re-checked here; the client only asks.
 */
public final class DreamBuilder {
    private DreamBuilder() {}

    public static boolean apply(ServerPlayer player, DreamEditPayload edit) {
        DreamSession session = DreamService.session(player.getUUID());
        ServerLevel dream = DreamService.dreamLevel(player.server);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (session == null || !session.own || dream == null || player.level() != dream
                || !state.hasAuthority(AuthorityContent.MIND)) {
            return false;
        }
        Dreamscape scape = DreamService.dreamscape(dream, player.getUUID());
        boolean changed = switch (edit.action()) {
            case DreamEditPayload.PLACE -> place(dream, player, state, scape, edit);
            case DreamEditPayload.ERASE -> erase(dream, player, scape, edit);
            case DreamEditPayload.FLAW -> flaw(dream, player, scape, edit);
            default -> false;
        };
        if (changed) {
            DreamPlots.of(dream).changed();
            DreamService.sendState(player);
        }
        return changed;
    }

    private static boolean place(ServerLevel dream, ServerPlayer player, PlayerMagicState state, Dreamscape scape, DreamEditPayload edit) {
        Impression chosen = Impression.parse(edit.impression());
        if (chosen == null || !state.mind().lexicon().knows(chosen.key()) || DreamRules.refused(chosen.id())) {
            return false;
        }
        if (chosen.kind() == Impression.Kind.CREATURE) {
            return !edit.cells().isEmpty() && spawn(dream, player, scape, chosen.id(), edit.cells().get(0));
        }
        BlockState block = Manifestation.stateOf(chosen.id());
        if (block == null || block.isAir() || block.hasBlockEntity()) {
            return false;
        }
        boolean placed = false;
        for (BlockPos pos : edit.cells()) {
            if (usable(player, scape, pos) && dream.getBlockState(pos).canBeReplaced()) {
                dream.setBlock(pos, block, Block.UPDATE_ALL);
                placed = true;
            }
        }
        return placed;
    }

    private static boolean spawn(ServerLevel dream, ServerPlayer player, Dreamscape scape, String creatureId, BlockPos pos) {
        if (!usable(player, scape, pos)
                || dream.getEntitiesOfClass(Mob.class, plotBox(scape), mob -> mob.getTags().contains(DreamService.DREAM_TAG)).size() >= DreamRules.MAX_FIGMENTS) {
            return false;
        }
        Entity entity = EntityType.byString(creatureId).map(type -> type.create(dream, EntitySpawnReason.COMMAND)).orElse(null);
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
        mob.setPersistenceRequired();
        mob.addTag(DreamService.DREAM_TAG);
        return dream.addFreshEntity(mob);
    }

    private static boolean erase(ServerLevel dream, ServerPlayer player, Dreamscape scape, DreamEditPayload edit) {
        if (edit.entity() >= 0) {
            Entity target = dream.getEntity(edit.entity());
            if (target == null || !target.getTags().contains(DreamService.DREAM_TAG) || !usable(player, scape, target.blockPosition())) {
                return false;
            }
            scape.clearIfFlaw(target.getUUID());
            target.discard();
            return true;
        }
        boolean erased = false;
        for (BlockPos pos : edit.cells()) {
            if (usable(player, scape, pos) && !dream.getBlockState(pos).isAir()) {
                dream.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                scape.clearIfFlaw(DreamService.offsetIn(scape.plot(), pos));
                erased = true;
            }
        }
        return erased;
    }

    private static boolean flaw(ServerLevel dream, ServerPlayer player, Dreamscape scape, DreamEditPayload edit) {
        if (edit.entity() >= 0) {
            Entity target = dream.getEntity(edit.entity());
            if (target == null || !target.getTags().contains(DreamService.DREAM_TAG) || !usable(player, scape, target.blockPosition())) {
                return false;
            }
            scape.markFigment(target.getUUID());
        } else if (edit.cells().size() == 1 && usable(player, scape, edit.cells().get(0))
                && !dream.getBlockState(edit.cells().get(0)).isAir()) {
            scape.markBlock(DreamService.offsetIn(scape.plot(), edit.cells().get(0)));
        } else {
            return false;
        }
        player.displayClientMessage(Component.translatable("message.magical.dream_flaw_marked"), true);
        return true;
    }

    private static boolean usable(ServerPlayer player, Dreamscape scape, BlockPos pos) {
        return DreamRules.inside(scape.plot(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                && player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= DreamRules.EDIT_REACH;
    }

    private static AABB plotBox(Dreamscape scape) {
        Offset o = DreamRules.origin(scape.plot());
        return new AABB(o.dx() - DreamRules.PLOT_HALF, o.dy() - DreamRules.PLOT_BELOW, o.dz() - DreamRules.PLOT_HALF,
                o.dx() + DreamRules.PLOT_HALF + 1, o.dy() + DreamRules.PLOT_ABOVE, o.dz() + DreamRules.PLOT_HALF + 1);
    }
}
```

- [ ] **Step 6: Tell the wielder what their dream is**

In `DreamService`, add these imports:

```java
import com.efkrdnz.magical.network.DreamStatePayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.Optional;
```

Then add this member:

```java
    /** To the wielder: whether they are in their own dream, and its Flaw, so Daydream can show it. */
    static void sendState(ServerPlayer player) {
        DreamSession session = SESSIONS.get(player.getUUID());
        ServerLevel dream = dreamLevel(player.server);
        if (session == null || !session.own || dream == null) {
            MagicalNetwork.sendDreamState(player, new DreamStatePayload(false, Optional.empty(), -1));
            return;
        }
        Dreamscape.Flaw flaw = dreamscape(dream, session.owner).flaw();
        Optional<BlockPos> block = flaw != null && flaw.block() != null ? Optional.of(at(session.plot, flaw.block())) : Optional.empty();
        int entity = -1;
        if (flaw != null && flaw.figment() != null) {
            Entity figment = dream.getEntity(flaw.figment());
            entity = figment == null ? -1 : figment.getId();
        }
        MagicalNetwork.sendDreamState(player, new DreamStatePayload(true, block, entity));
    }
```

Call it from three places:
- In `enter`: `if (own) { sendState(dreamer); }` just before `return true;`.
- In `wake`: `if (session.own) { sendState(dreamer); }` straight after the `if (session == null) { return; }` check, before anything that can return early. The session is already removed at that point, so it sends `false`.
- In `figmentGone`, inside the `if` that clears the Flaw, send the owner's state when they are online:

```java
            ServerPlayer online = dream.getServer().getPlayerList().getPlayer(owner);
            if (online != null) {
                sendState(online);
            }
```

- [ ] **Step 7: Add the language keys**

In `en_us.json`, after `"message.magical.lull_dreaming": ...,`:

```json
  "message.magical.dream_flaw_marked": "That is the Flaw.",
  "mind.magical.dream.hint": "Right: make it real   Left: unmake   Sneak + right: mark the Flaw",
  "mind.magical.dream.status": "Dreamscape",
```

In `MindAuthorityTest`, add these to the key list:

```java
                "message.magical.dream_flaw_marked", "mind.magical.dream.hint", "mind.magical.dream.status",
```

- [ ] **Step 8: Run the tests and confirm they pass**

Run: `.\gradlew test`, then `.\gradlew runGameTestServer --console=plain`
Expected: every unit test passes, including `DreamPayloadsTest` and `MindAuthorityTest`, and every game test passes, including `dream_build_1` to `dream_build_5`.

- [ ] **Step 9: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/network/DreamEditPayload.java src/main/java/com/efkrdnz/magical/network/DreamStatePayload.java src/main/java/com/efkrdnz/magical/magic/mind/DreamBuilder.java src/main/java/com/efkrdnz/magical/client/mind/ClientDream.java src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java src/main/java/com/efkrdnz/magical/client/ClientPayloadHandlers.java src/main/java/com/efkrdnz/magical/magic/mind/DreamService.java src/main/java/com/efkrdnz/magical/magic/mind/DreamBuilderGameTests.java src/main/resources/assets/magical/lang/en_us.json src/test/java/com/efkrdnz/magical/network/DreamPayloadsTest.java src/test/java/com/efkrdnz/magical/magic/mind/MindAuthorityTest.java
git commit -m "feat(mind): in your own dream what you draw is real, and one thing is wrong

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The client — the Sleeper drawn lying down, and Daydream building for real

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/mind/SleeperRenderer.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java` (swap the `NoopRenderer` for `SleeperRenderer`)
- Modify: `src/main/java/com/efkrdnz/magical/client/mind/DaydreamMode.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/mind/DraftRenderer.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/mind/ImpressionReelOverlay.java`

**Interfaces:**
- **Consumes:**
  - From Task 3: `SleeperEntity.dreamer()`.
  - From Task 6: `ClientDream.ownDream/flawBlock/flawEntity`, `MagicalNetwork.sendDreamEdit`, `DreamEditPayload`.
  - Existing: `IllusionRenderer.drawEdge(PoseStack, VertexConsumer, Vec3, AABB, int rgb, float alpha)`.
- **Produces:** `SleeperRenderer`. Nothing else new.

- [ ] **Step 1: Write `SleeperRenderer`**

```java
package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.entity.mind.SleeperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;

import java.util.UUID;

/**
 * A dreamer's body: their skin, lying down the way they faced. The entity stands on the server, so
 * its box is big enough to hit; only the drawing lies down, with the bed turned to its yaw, because
 * with no bed the game would lay every sleeper due north.
 */
public final class SleeperRenderer extends LivingEntityRenderer<SleeperEntity, PlayerRenderState, PlayerModel> {
    private final PlayerModel wide;
    private final PlayerModel slim;

    public SleeperRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public PlayerRenderState createRenderState() {
        return new PlayerRenderState();
    }

    @Override
    public void extractRenderState(SleeperEntity sleeper, PlayerRenderState state, float partialTick) {
        super.extractRenderState(sleeper, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(sleeper, state, partialTick, this.itemModelResolver);
        state.skin = skin(sleeper.dreamer().orElse(sleeper.getUUID()));
        state.pose = Pose.SLEEPING;
        state.bedOrientation = Direction.fromYRot(sleeper.getYRot());
        state.eyeHeight = 1.62F;
    }

    @Override
    public void render(PlayerRenderState state, PoseStack pose, MultiBufferSource buffers, int light) {
        this.model = state.skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(state, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerRenderState state) {
        return state.skin.texture();
    }

    @Override
    protected boolean shouldShowName(SleeperEntity sleeper, double distance) {
        return false;
    }

    /** The dreamer's own skin while they are online (they always are, or there is no body); the default for their UUID otherwise. */
    private static PlayerSkin skin(UUID dreamer) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(dreamer);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(dreamer);
    }
}
```

If the compiler rejects a line, adapt it to the 1.21.4 signature and keep the behaviour. The two to check are `shouldShowName`'s parameters in `LivingEntityRenderer`, and whether `LivingEntityRenderState.eyeHeight` is assignable (drop that line if it is final).

In `MagicalClientEvents.registerRenderers`, replace the `NoopRenderer` line for `SLEEPER` with:

```java
        event.registerEntityRenderer(MagicalEntities.SLEEPER.get(), com.efkrdnz.magical.client.mind.SleeperRenderer::new);
```

- [ ] **Step 2: Make Daydream build for real in your own dream**

In `DaydreamMode`, add these imports:

```java
import com.efkrdnz.magical.network.DreamEditPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
```

Then add this helper:

```java
    /** In your own dream Daydream writes real blocks through the server instead of a draft. */
    public static boolean dreaming() {
        return ClientDream.ownDream();
    }
```

Make these edits:

1. In `begin`, replace `draft = mind.active().copy();` with:

```java
        draft = dreaming() ? new Reverie() : mind.active().copy();
```

2. At the top of `place(Minecraft minecraft)`, before `Impression chosen = ...`:

```java
        if (dreaming()) {
            placeInDream(minecraft);
            return;
        }
```

3. At the top of `erase(Minecraft minecraft)`, before `DraftRay.Hit hit = cursor(minecraft);`:

```java
        if (dreaming()) {
            eraseInDream(minecraft);
            return;
        }
```

4. Add these methods:

```java
    private static void placeInDream(Minecraft minecraft) {
        DraftRay.Hit hit = cursor(minecraft);
        if (hit == null) {
            return;
        }
        if (Screen.hasShiftDown()) {
            markFlaw(minecraft, hit);
            return;
        }
        String key = impression();
        Impression chosen = Impression.parse(key);
        if (chosen == null) {
            return;
        }
        if (chosen.kind() == Impression.Kind.CREATURE) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.PLACE, List.of(hit.place()), key, -1));
            corner = null;
            return;
        }
        if (brush != Brush.POINT && corner == null) {
            corner = hit.place();
            return;
        }
        BlockPos from = brush == Brush.POINT ? hit.place() : corner;
        List<BlockPos> cells = brush.cells(offset(from), offset(hit.place())).stream().map(DaydreamMode::world).toList();
        MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.PLACE, cells, key, -1));
        corner = null;
    }

    private static void markFlaw(Minecraft minecraft, DraftRay.Hit hit) {
        Entity target = minecraft.crosshairPickEntity;
        if (target != null && !(target instanceof Player)) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.FLAW, List.of(), "", target.getId()));
        } else if (hit.solid() != null) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.FLAW, List.of(hit.solid()), "", -1));
        }
    }

    private static void eraseInDream(Minecraft minecraft) {
        Entity target = minecraft.crosshairPickEntity;
        if (target != null && !(target instanceof Player)) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.ERASE, List.of(), "", target.getId()));
            return;
        }
        DraftRay.Hit hit = cursor(minecraft);
        if (hit != null && hit.solid() != null) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.ERASE, List.of(hit.solid()), "", -1));
        }
    }
```

5. In `onClientTick`, stop ending Daydream for straying while dreaming, because a plot is 65 blocks across. Change the stray line to:

```java
            || (!dreaming() && minecraft.player.distanceToSqr(Vec3.atCenterOf(anchor)) > STRAY_BLOCKS * STRAY_BLOCKS);
```

6. Leaving Daydream saves the draft through `SaveReveriePayload`. In your own dream the draft is always empty and the scene lives in the level, so skip the save while `dreaming()`. Find the save call on leaving and guard it with `if (!dreaming())`.

- [ ] **Step 3: Show the Flaw while building**

In `DraftRenderer`, add `private static final int FLAW = 0xFFD36B;` and import `net.minecraft.world.entity.Entity`. Just before `buffers.endBatch(RenderType.lines());`, add:

```java
        if (ClientDream.ownDream()) {
            if (ClientDream.flawBlock() != null) {
                IllusionRenderer.drawEdge(pose, lines, cam, new AABB(ClientDream.flawBlock()).inflate(0.02), FLAW, 1.0F);
            }
            Entity figment = ClientDream.flawEntity() >= 0 ? minecraft.level.getEntity(ClientDream.flawEntity()) : null;
            if (figment != null) {
                IllusionRenderer.drawEdge(pose, lines, cam, figment.getBoundingBox().inflate(0.05), FLAW, 1.0F);
            }
        }
```

- [ ] **Step 4: Make the reel say where you are**

In `ImpressionReelOverlay.render`, make `status` and `hint` depend on `DaydreamMode.dreaming()`, keeping everything else in the method as it is:

```java
    Component status = Component.translatable("mind.magical.brush." + DaydreamMode.brush().name().toLowerCase(Locale.ROOT))
            .append("   ").append(DaydreamMode.dreaming()
                    ? Component.translatable("mind.magical.dream.status")
                    : Component.literal(DaydreamMode.draft().size() + " / " + lexicon.budget()));
    ...
    Component hint = DaydreamMode.dreaming()
            ? Component.translatable("mind.magical.dream.hint")
            : Component.translatable("mind.magical.daydream.hint", minecraft.options.keyInventory.getTranslatedKeyMessage());
```

- [ ] **Step 5: Build and run every test**

Run: `.\gradlew build`, then `.\gradlew runGameTestServer --console=plain`
Expected: BUILD SUCCESSFUL, every unit test passes (including `ImpressionReelLayoutTest`, which is unchanged), and every game test passes.

- [ ] **Step 6: Commit**

```bash
git add -- src/main/java/com/efkrdnz/magical/client/mind/SleeperRenderer.java src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java src/main/java/com/efkrdnz/magical/client/mind/DaydreamMode.java src/main/java/com/efkrdnz/magical/client/mind/DraftRenderer.java src/main/java/com/efkrdnz/magical/client/mind/ImpressionReelOverlay.java
git commit -m "feat(mind): the Sleeper lies in its dreamer's skin, and Daydream builds the dream for real

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Commands, the full suite, captures, the guide, delivery

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/mind/DreamPresets.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/mind/MindCommands.java`
- Modify: `CLAUDE.md`, **through the index only**. A peer session's hunks may be in its working tree, and they are never committed.

**Interfaces:**
- **Consumes:** `DreamService.enterOwn`, `wake`, `session`, `dreamscape`, `at`, `lull`, `sendState`; `SleeperEntity.of`; `DreamPlots.changed`.
- **Produces:** `/magical mind dream enter|wake|lull|body|preset <name>|show`.

- [ ] **Step 1: Write `DreamPresets`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Worked Dreamscapes for captures; they write blocks directly, around the owner's arrival, in their own dream only. */
public final class DreamPresets {
    public static final List<String> NAMES = List.of("library");

    private DreamPresets() {}

    public static boolean build(ServerPlayer player, String name) {
        DreamSession session = DreamService.session(player.getUUID());
        ServerLevel dream = DreamService.dreamLevel(player.server);
        if (session == null || !session.own || dream == null || !"library".equals(name)) {
            return false;
        }
        Dreamscape scape = DreamService.dreamscape(dream, player.getUUID());
        Offset a = scape.arrival();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                set(dream, scape, a, x, -1, z, Blocks.OAK_PLANKS.defaultBlockState());
                boolean wall = Math.abs(x) == 4 || Math.abs(z) == 4;
                for (int y = 0; y <= 2; y++) {
                    set(dream, scape, a, x, y, z, wall ? Blocks.BOOKSHELF.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                set(dream, scape, a, x, 3, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
        set(dream, scape, a, -3, 0, -3, Blocks.LANTERN.defaultBlockState());
        set(dream, scape, a, 3, 0, 3, Blocks.LANTERN.defaultBlockState());
        set(dream, scape, a, 4, 1, 0, Blocks.JACK_O_LANTERN.defaultBlockState());
        scape.markBlock(new Offset(a.dx() + 4, a.dy() + 1, a.dz()));
        DreamPlots.of(dream).changed();
        DreamService.sendState(player);
        return true;
    }

    private static void set(ServerLevel dream, Dreamscape scape, Offset a, int x, int y, int z, BlockState state) {
        BlockPos pos = DreamService.at(scape.plot(), new Offset(a.dx() + x, a.dy() + y, a.dz() + z));
        dream.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
```

- [ ] **Step 2: Add the commands**

In `MindCommands.build()`, append after the `"insist"` `.then(...)`:

```java
            .then(Commands.literal("dream")
                    .then(Commands.literal("enter").executes(c -> run(c, player -> DreamService.enterOwn(player) ? 1 : 0)))
                    .then(Commands.literal("wake").executes(c -> run(c, player -> {
                        DreamService.wake(player);
                        return 1;
                    })))
                    .then(Commands.literal("lull").executes(c -> run(c, player -> {
                        PlayerMagicState state = state(player);
                        state.setSkillCooldown(MagicContent.LULL.id(), 0);
                        return DreamService.lull(player, state) ? 1 : 0;
                    })))
                    .then(Commands.literal("body").executes(c -> run(c, MindCommands::body)))
                    .then(Commands.literal("preset")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .suggests((c, b) -> {
                                        DreamPresets.NAMES.forEach(b::suggest);
                                        return b.buildFuture();
                                    })
                                    .executes(c -> run(c, player -> DreamPresets.build(player, StringArgumentType.getString(c, "name")) ? 1 : 0))))
                    .then(Commands.literal("show").executes(c -> run(c, MindCommands::dreamShow))))
```

Add these methods, importing `com.efkrdnz.magical.entity.mind.SleeperEntity` and `net.minecraft.server.level.ServerLevel`. `run`, `state` and the `MagicContent` / `PlayerMagicState` imports already exist in `MindCommands`; add whichever of them are missing.

```java
    /** Your own body, lying three blocks ahead with nobody in it; for captures. A blow on it simply ends it. */
    private static int body(ServerPlayer player) {
        SleeperEntity body = SleeperEntity.of(player);
        var ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(3.0));
        body.moveTo(ahead.x, player.getY(), ahead.z, player.getYRot() + 90.0F, 0.0F);
        player.serverLevel().addFreshEntity(body);
        return 1;
    }

    private static int dreamShow(ServerPlayer player) {
        ServerLevel dream = DreamService.dreamLevel(player.server);
        if (dream == null) {
            player.sendSystemMessage(Component.literal("no dream level"));
            return 0;
        }
        Dreamscape scape = DreamService.dreamscape(dream, player.getUUID());
        Dreamscape.Flaw flaw = scape.flaw();
        String flawText = flaw == null ? "none" : flaw.block() != null ? "block " + flaw.block() : "figment " + flaw.figment();
        player.sendSystemMessage(Component.literal("plot " + scape.plot() + " at " + DreamService.at(scape.plot(), new Offset(0, 0, 0))
                + ", arrival " + scape.arrival() + ", flaw " + flawText
                + ", dreaming " + DreamService.dreaming(player.getUUID())));
        return 1;
    }
```

Run: `.\gradlew build`
Expected: BUILD SUCCESSFUL.

```bash
git add -- src/main/java/com/efkrdnz/magical/magic/mind/DreamPresets.java src/main/java/com/efkrdnz/magical/magic/mind/MindCommands.java
git commit -m "feat(mind): dream commands and a worked library Dreamscape

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 3: Run the full suite in a clean worktree**

```bash
git worktree add ../magical-verify HEAD
cd ../magical-verify
./gradlew test build
./gradlew runGameTestServer --console=plain
cd ../magical-port
git worktree remove ../magical-verify --force
```

Expected:
- `test build` is BUILD SUCCESSFUL.
- Every game test passes: the stage 2 count (119) plus 27 new ones. Those are `dream_plot_1`, `dream_1` to `dream_10`, `dream_guard_1` to `dream_guard_6`, `lull_1` to `lull_5`, and `dream_build_1` to `dream_build_5`.

- [ ] **Step 4: Captures**

`run/options.txt` must have `pauseOnLostFocus:false`. The capture runs these steps in order:
1. Lays your own body three blocks ahead.
2. Dreams you into your own plot.
3. Builds the library around the arrival.
4. Toggles Daydream on, so the reel and the gold Flaw edge show.
5. Wakes you.

```powershell
.\gradlew runClient '-PquickPlay=New World' '-PwindowSize=1280x720' '-PautoExit' '-PautoScreenshot=151,262,300,352' '-PautoCommands=gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical authority set authority_of_mind;magical mind lexicon all;magical hud equip 1 daydream;time set day;tp @s ~ ~ ~ 0 15;140:magical mind dream body;160:magical mind dream enter;170:magical mind dream preset library;340:magical mind dream wake' '-PautoHold=cast_slot_2' '-PautoClick=240:hold;243:release;296:hold;299:release'
```

Expected frames:
- **151:** your own skin, lying on the ground three blocks ahead.
- **262:** inside the library at night: bookshelves, lanterns, the reel reading "Dreamscape", and a gold edge round the jack o'lantern.
- **300:** the same room after the second toggle, with Daydream off and no reel.
- **352:** back in the waking world where you stood.

Look at every frame. If the body is out of frame at 151, change the `tp` pitch and run again. Send the frames at 151 and 262 to the user with `SendUserFile`.

- [ ] **Step 5: Update the guide through the index**

Write this script to the scratchpad as `mind3_claude_md.py` and run it with `python` from `E:\magical-port`. It patches the HEAD copy of CLAUDE.md into the index, and makes the same change in the working tree:

```python
import subprocess, pathlib
OLD = "Stage 2 makes a lie real by consensus (below); stage 3 (the Dream) is in the design and not in the code."
NEW = "Stage 2 makes a lie real by consensus and stage 3 puts the sure to sleep (both below)."
ANCHOR = "Commands under `/magical mind`:"
PARA = (
    "**The Dream** (`DreamService`, rules in the pure `DreamRules`, one `Dreamscape` per wielder in the `DreamPlots` SavedData "
    "on the `magical:dream` level). **Lull** (60 mana, 1200 ticks, `selfManaged`) takes a viewer sure of a live element of "
    "yours (belief >= 0.8): a mob wears `MagicStatus.ASLEEP` for 600 ticks or until hit; a player falls into your Dreamscape if "
    "it has a Flaw, their body left lying as a `SleeperEntity` (`noSave`, their skin off the tab list, standing on the server "
    "with a hittable box and drawn lying by `SleeperRenderer`, which turns the bed to its yaw or every sleeper would lie due "
    "north). Sneak + Lull dreams yourself, free and with no clock, and Lull again wakes you; a dreamer arrives where you last "
    "stood. Plots are a grid from a million blocks out (`DreamRules.origin`, 256 apart, 64 a row, 32 each way, 16 down, 48 up, "
    "a 5x5 floor); straying puts you back at the arrival, and leaving the dream level by any other route wakes you in your "
    "body. A dreamer wakes on touching or striking the Flaw (a block or a dreamed mob, exactly one, `Dreamscape.Flaw`), after "
    "1200 ticks (`server.getTickCount()`), or when the body is hit - the hit then lands on them, and a `/kill` on the body "
    "wakes them unhurt. **A dream cannot kill**: `LivingDamageEvent.Pre` trims any blow that would leave less than a heart to "
    "exactly one (`DreamRules.dealt`) and wakes them, and a death that gets past it is cancelled. The return point is the "
    "`DreamReturn` attachment, so a logout or a crash wakes them at the next login (`DreamService.recover`). **Nothing leaves "
    "a dream** (`DreamGuard`): no item or experience can join the level (a thrown item goes back to the thrower), nothing is "
    "broken or placed by hand, no item is used, a block answers only an empty hand, no creature can be interacted with, "
    "explosions and griefing take no blocks, and a mob never targets you in your own dream. **Building**: Daydream in your own "
    "dream sends `DreamEditPayload` to `DreamBuilder` - Lexicon blocks and creatures made real within 8 blocks, inside the "
    "plot, into replaceable cells; no block entities, portals or bosses (`DreamRules.NEVER_DREAMED`); dreamed mobs are "
    "persistent, tagged `magical_dream`, at most 32; sneak + right-click marks the Flaw, which the owner sees edged gold "
    "(`DreamStatePayload` -> `ClientDream`). The vanilla test server drops datapack dimensions, so game tests set "
    "`DreamService.testLevel` and the plots are laid out in the test level, where `isDream` counts only the plot grid. "
    "Commands: `mind dream enter|wake|lull|body|preset library|show`. Capture: the Mind launch with `magical hud equip 1 "
    "daydream`, `140:magical mind dream body;160:magical mind dream enter;170:magical mind dream preset library;340:magical "
    "mind dream wake`, `-PautoHold=cast_slot_2 -PautoClick=240:hold;243:release` and `-PautoScreenshot=151,262`. "
    "Plan: `docs/superpowers/plans/2026-09-29-authority-of-mind-dream.md`.\n\n"
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
git commit -m "docs(mind): the guide covers the Dream

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected: `--stat` shows only `CLAUDE.md`. Read `git diff --cached` before committing, and confirm no peer hunk is in it.

- [ ] **Step 6: Deliver**

```bash
git push origin main
git -C "E:/minecraft mods/magical" switch --detach main
```

Expected: the push succeeds, and the play checkout is at the new HEAD.
