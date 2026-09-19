# Incantation Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The pure evaluator of the Authority of Mana — verses, piles, the draw, payloads, copies, Refrains, Imposes, Clauses — and the whole verse catalogue as data, under JUnit, with no Minecraft in it beyond `ResourceLocation` and NBT.

**Architecture:** A function-for-function port of Noita's `gun.lua` into one object, `Recital`, that verse actions call back into, exactly as the Lua actions call the Lua helpers. Cards are `VerseCard`s in three piles on a `ReciteSession` that lives between presses; a press is `Reciter.recite`, which returns an immutable `RecitePlan` (bodies stamped with the shot state at the instant they were added, nested payload plans, beat, rest, events) that a later plan spawns. The authored state is a `Grimoire` (four `Incantation`s and the known set) with NBT save, sized for the `PlayerMagicState` slot the Ledger holds now.

**Tech Stack:** Java 21, NeoForge 21.4.157 (only `net.minecraft.resources.ResourceLocation` and `net.minecraft.nbt.*` are touched), JUnit 5 via `.\gradlew test`.

**Design:** `docs/superpowers/specs/2026-09-19-authority-of-mana-incantation-design.md` (the rules in §8 are the tests below, by name). Lua reference: the scratchpad copies of `gun.lua` / `gun_actions.lua` described in `docs/research/2026-09-19-noita-spell-composition.md`.

## Global Constraints

- Package `com.efkrdnz.magical.magic.incantation`, under `src/main/java`; tests under `src/test/java` in the same package (they reach package-private members).
- Nothing in the package imports `net.minecraft` except `ResourceLocation` and the `nbt` package. No `MagicalMod`, no registries, no level.
- Ids are `magical:<path>`; prototypes are `magical:body/<path>`. Lang keys (`verse.magical.<path>`, `.desc`) are Plan 3's, not this plan's.
- Internal names mirror the Lua so the port can be checked line by line: `deck`, `hand`, `discard`, `drawActions`, `drawAction`, `dontDraw`, `forceStopDraws`, `startReload`, `reloading`, `gotProjectiles`, `checkRecursion`.
- Where the Lua **reassigns** a pile (`hand = {}`, `deck = {}`, `discarded = {}`), the session **replaces the list object**; where it inserts or removes, the live list is mutated. A verse that iterates a pile captures the list once and loops by index, which is exactly `ipairs`.
- One deviation from the Lua in code, not in behaviour: `_handle_reload` reloads only when `not reloading`; the reload for the "root draw ran dry" case is done by Noita's C++ and we have none, so `Recital.recite` rests whenever the deck is empty or `startReload` is set.
- Caps: `ReciteCaps.MAX_VERSES` 20, `MAX_BREATH` 8, `MAX_STEPS` 1024, `MAX_BODIES` 64, `MAX_DEPTH` 4, `RECURSION_LIMIT` 2. Past a cap the recite frays: drawing stops, what was planned still comes back.
- Timing is in ticks. Damage is half-hearts, speed blocks per tick, spread and pattern degrees, gravity blocks per tick squared.
- Tests use `org.junit.jupiter.api.Assertions` static imports, four-space indent, a Javadoc thesis on the class (as `PileTest`). Test names are the spec's rule names.
- Every commit message ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Never `git add` `gradlew` or `.claude/settings.local.json`.

## File map

| File | Responsibility |
|---|---|
| `VerseType.java` | The eight types and the three predicates the type decides |
| `PayloadKind.java`, `Behaviour.java`, `HitEffect.java`, `Wake.java` | The enums the shot state carries |
| `ShotState.java` | The mutable bag every verse writes, with `copy()` |
| `VerseIds.java` | `magical:` ids for verses and prototypes |
| `VerseAction.java` | `(Recital, recursion, iteration) -> iterMax` |
| `Verse.java` | The card definition, with `Declared` numbers |
| `VersePrototype.java`, `VersePrototypes.java` | The body a verse names, and the table of them |
| `VerseCatalogue.java` | Instance registry of verses |
| `Incantation.java` | Ordered entries with uses, breath; NBT |
| `Grimoire.java` | Four incantations, the known set, the Every Other toggle; NBT |
| `IncantationValidator.java` | Pure checks shared by server and the future screen |
| `VerseCard.java` | A verse instance in the piles: deck index, uses |
| `ReciteSession.java` | The three piles between presses, first-shot flag, rest carry |
| `ReciteWorld.java` | What a verse may ask the world |
| `ReciteEvent.java` | What happened during a recite |
| `ShotPlan.java`, `ProjectilePlan.java`, `RecitePlan.java` | The output |
| `ReciteCaps.java` | The bounds |
| `Recital.java` | The machine: gun.lua's globals and helpers |
| `Reciter.java` | `recite(session, breath, mana, costScale, world)` |
| `VerseContent.java` | The real catalogue, built from the six files below |
| `ProjectileVerses.java`, `StaticVerses.java`, `ModifierVerses.java`, `MulticastVerses.java`, `UtilityVerses.java`, `ControlVerses.java` | The verses, by type |
| tests: `ShotStateTest`, `VerseCatalogueTest`, `IncantationTest`, `GrimoireTest`, `IncantationValidatorTest`, `ReciteSessionTest`, `ReciterTest`, `OverrunTest`, `PayloadTest`, `MulticastTest`, `RecallTest`, `WildTest`, `RefrainTest`, `ImposeTest`, `ClauseTest`, `CapsTest`, `VerseContentTest`; fixtures `FixedWorld`, `ReciteFixtures` | |

---

### Task 1: The shot state and its enums

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VerseType.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/PayloadKind.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/Behaviour.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/HitEffect.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/Wake.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ShotState.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/ShotStateTest.java`

**Interfaces:**
- Produces: `VerseType {PROJECTILE, STATIC, MODIFIER, MULTICAST, MATERIAL, CONTROL, UTILITY, PASSIVE}` with `spawnsBodies()`, `spendsUseAlone()`, `imposeScans()`, `payloadWorthy()`; `ShotState` with the getters and mutators listed in the code, `copy()`, `has(Behaviour)`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicSchool;
import org.junit.jupiter.api.Test;

/**
 * The bag every verse writes into. A copy is a snapshot: what a body is stamped with must not move
 * when the shot goes on being written, and the speed multiplier is clamped on every write.
 */
class ShotStateTest {

    @Test
    void aCopyDoesNotFollowTheOriginal() {
        ShotState state = new ShotState();
        state.addDamage(2.5D);
        state.behaviour(Behaviour.SEEKER);
        ShotState copy = state.copy();
        state.addDamage(1.0D);
        state.behaviour(Behaviour.PUNCTURE);
        assertEquals(2.5D, copy.damageAdd(), 1e-9);
        assertTrue(copy.has(Behaviour.SEEKER));
        assertFalse(copy.has(Behaviour.PUNCTURE));
        assertEquals(3.5D, state.damageAdd(), 1e-9);
    }

    @Test
    void speedIsClampedOnEveryWrite() {
        ShotState state = new ShotState();
        for (int i = 0; i < 10; i++) {
            state.multiplySpeed(2.5D);
        }
        assertEquals(ShotState.MAX_SPEED_MULTIPLIER, state.speedMultiplier(), 1e-9);
        state.multiplySpeed(0.0D);
        assertEquals(0.0D, state.speedMultiplier(), 1e-9);
    }

    @Test
    void anExplosionRadiusCannotGoNegative() {
        ShotState state = new ShotState();
        state.addExplosionRadius(1.5D);
        state.addExplosionRadius(-4.0D);
        assertEquals(0.0D, state.explosionRadius(), 1e-9);
    }

    @Test
    void theDefaultsAreNoitasZeroState() {
        ShotState state = new ShotState();
        assertEquals(0, state.beatTicks());
        assertEquals(1.0D, state.speedMultiplier(), 1e-9);
        assertEquals(0.0D, state.spreadDegrees(), 1e-9);
        assertEquals(0.0D, state.patternDegrees(), 1e-9);
        assertFalse(state.nullsDamage());
        assertFalse(state.friendlyFire());
        assertEquals(null, state.school());
        state.setSchool(MagicSchool.FIRE);
        assertEquals(MagicSchool.FIRE, state.school());
    }

    @Test
    void theTypePredicatesAreNoitas() {
        assertTrue(VerseType.PROJECTILE.spawnsBodies());
        assertTrue(VerseType.STATIC.spawnsBodies());
        assertTrue(VerseType.MATERIAL.spawnsBodies());
        assertFalse(VerseType.MODIFIER.spawnsBodies());
        assertTrue(VerseType.CONTROL.spendsUseAlone());
        assertTrue(VerseType.UTILITY.spendsUseAlone());
        assertFalse(VerseType.PROJECTILE.spendsUseAlone());
        assertTrue(VerseType.MULTICAST.imposeScans());
        assertTrue(VerseType.PASSIVE.imposeScans());
        assertFalse(VerseType.UTILITY.imposeScans());
        assertTrue(VerseType.UTILITY.payloadWorthy());
        assertFalse(VerseType.MODIFIER.payloadWorthy());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ShotStateTest"`
Expected: compilation failure, `ShotState` and `VerseType` do not exist.

- [ ] **Step 3: Write the enums and the state**

`VerseType.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/**
 * Noita's eight action types under our names, in Noita's order (OTHER is CONTROL). Three things the
 * type decides, all ported: which cards make a press "have produced a body" (what spends every
 * card's use), which cards an Impose walks over, and which cards make a payload worth a Latch.
 */
public enum VerseType {
    PROJECTILE,
    STATIC,
    MODIFIER,
    MULTICAST,
    MATERIAL,
    CONTROL,
    UTILITY,
    PASSIVE;

    /** {@code got_projectiles}: a press that played one of these spends the uses of every card in hand. */
    public boolean spawnsBodies() {
        return this == PROJECTILE || this == STATIC || this == MATERIAL;
    }

    /** A card of this type spends its use even on a press that produced no body. */
    public boolean spendsUseAlone() {
        return this == CONTROL || this == UTILITY;
    }

    /** What an Impose scans over on its way to a target. */
    public boolean imposeScans() {
        return this == MODIFIER || this == PASSIVE || this == CONTROL || this == MULTICAST;
    }

    /** What makes a payload worth spawning a Latch for, and what an Impose may target. */
    public boolean payloadWorthy() {
        return this == PROJECTILE || this == STATIC || this == MATERIAL || this == UTILITY;
    }
}
```

`PayloadKind.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/** How a body releases its payload: on a hit (Latch), after a fuse (Fuse), or when it expires (Epitaph). */
public enum PayloadKind {
    NONE,
    LATCH,
    FUSE,
    EPITAPH
}
```

`Behaviour.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/** The port of {@code extra_entities}: a name the body's entity looks up. A new one is a constant and a tick function. */
public enum Behaviour {
    SEEKER,
    SIGHTLINE,
    PUNCTURE,
    SERPENTINE,
    GYRE,
    ERRANT,
    RELAY,
    TWIN_PATH,
    NAUGHT,
    UNDYING,
    LANTERN,
    NEAR_WORD,
    BOUNCE_BURST
}
```

`HitEffect.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/** The port of {@code game_effect_entities}: what a body does to what it hits, beyond damage. */
public enum HitEffect {
    BURN,
    FREEZE,
    SHOCK,
    WITHER,
    UPLIFT,
    DISPLACE
}
```

`Wake.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/** The port of {@code trail_material}: what a body leaves behind it. */
public enum Wake {
    FIRE,
    WATER,
    FROST
}
```

`ShotState.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bag every verse of a shot writes into: {@code GunActionState} under our names.
 *
 * <p>A body added while the shot runs is stamped with a {@link #copy()} of this at that instant;
 * the shot's final state carries the shot-wide numbers (spread, pattern, beat) the spawner reads for
 * the group. That split is the whole binding rule: a modifier reaches every body added after it and
 * none added before.
 */
public final class ShotState {

    public static final double MAX_SPEED_MULTIPLIER = 20.0D;

    private int beatTicks;
    private double speedMultiplier = 1.0D;
    private double childSpeedMultiplier = 1.0D;
    private double dampening = 1.0D;
    private double gravity;
    private int bounces;
    private int lifetimeAddTicks;
    private double spreadDegrees;
    private double patternDegrees;
    private double damageAdd;
    private double healingAdd;
    private double explosionRadius;
    private double explosionDamageAdd;
    private double critChance;
    private double knockback;
    private boolean nullAllDamage;
    private MagicSchool school;
    private final List<Behaviour> behaviours = new ArrayList<>();
    private final List<HitEffect> hitEffects = new ArrayList<>();
    private final List<Wake> wakes = new ArrayList<>();
    private int wakeAmount;
    private boolean friendlyFire;
    private double recoil;
    private double screenshake;
    private int lightLevel;
    private int drawManyCount;

    public ShotState copy() {
        ShotState c = new ShotState();
        c.beatTicks = beatTicks;
        c.speedMultiplier = speedMultiplier;
        c.childSpeedMultiplier = childSpeedMultiplier;
        c.dampening = dampening;
        c.gravity = gravity;
        c.bounces = bounces;
        c.lifetimeAddTicks = lifetimeAddTicks;
        c.spreadDegrees = spreadDegrees;
        c.patternDegrees = patternDegrees;
        c.damageAdd = damageAdd;
        c.healingAdd = healingAdd;
        c.explosionRadius = explosionRadius;
        c.explosionDamageAdd = explosionDamageAdd;
        c.critChance = critChance;
        c.knockback = knockback;
        c.nullAllDamage = nullAllDamage;
        c.school = school;
        c.behaviours.addAll(behaviours);
        c.hitEffects.addAll(hitEffects);
        c.wakes.addAll(wakes);
        c.wakeAmount = wakeAmount;
        c.friendlyFire = friendlyFire;
        c.recoil = recoil;
        c.screenshake = screenshake;
        c.lightLevel = lightLevel;
        c.drawManyCount = drawManyCount;
        return c;
    }

    // ---- timing ----
    public int beatTicks() { return beatTicks; }
    public void addBeat(int ticks) { beatTicks += ticks; }
    public void setBeat(int ticks) { beatTicks = ticks; }

    // ---- motion ----
    public double speedMultiplier() { return speedMultiplier; }
    public void multiplySpeed(double factor) {
        speedMultiplier = Math.max(0.0D, Math.min(MAX_SPEED_MULTIPLIER, speedMultiplier * factor));
    }
    public double childSpeedMultiplier() { return childSpeedMultiplier; }
    public double dampening() { return dampening; }
    public double gravity() { return gravity; }
    public void addGravity(double amount) { gravity += amount; }
    public int bounces() { return bounces; }
    public void addBounces(int count) { bounces += count; }
    public int lifetimeAddTicks() { return lifetimeAddTicks; }
    public void addLifetime(int ticks) { lifetimeAddTicks += ticks; }
    public double spreadDegrees() { return spreadDegrees; }
    public void addSpread(double degrees) { spreadDegrees += degrees; }
    public double patternDegrees() { return patternDegrees; }
    public void setPattern(double degrees) { patternDegrees = degrees; }

    // ---- damage ----
    public double damageAdd() { return damageAdd; }
    public void addDamage(double halfHearts) { damageAdd += halfHearts; }
    public double healingAdd() { return healingAdd; }
    public void addHealing(double halfHearts) { healingAdd += halfHearts; }
    public double explosionRadius() { return explosionRadius; }
    public void addExplosionRadius(double blocks) { explosionRadius = Math.max(0.0D, explosionRadius + blocks); }
    public double explosionDamageAdd() { return explosionDamageAdd; }
    public void addExplosionDamage(double halfHearts) { explosionDamageAdd += halfHearts; }
    public double critChance() { return critChance; }
    public void addCrit(double percent) { critChance += percent; }
    public double knockback() { return knockback; }
    public void addKnockback(double amount) { knockback += amount; }
    public boolean nullsDamage() { return nullAllDamage; }
    public void nullDamage() { nullAllDamage = true; }

    // ---- element and bundles ----
    public MagicSchool school() { return school; }
    public void setSchool(MagicSchool school) { this.school = school; }
    public List<Behaviour> behaviours() { return Collections.unmodifiableList(behaviours); }
    public void behaviour(Behaviour behaviour) { behaviours.add(behaviour); }
    public boolean has(Behaviour behaviour) { return behaviours.contains(behaviour); }
    public List<HitEffect> hitEffects() { return Collections.unmodifiableList(hitEffects); }
    public void hitEffect(HitEffect effect) { hitEffects.add(effect); }
    public List<Wake> wakes() { return Collections.unmodifiableList(wakes); }
    public int wakeAmount() { return wakeAmount; }
    public void wake(Wake wake, int amount) { wakes.add(wake); wakeAmount += amount; }
    public boolean friendlyFire() { return friendlyFire; }
    public void allowFriendlyFire() { friendlyFire = true; }

    // ---- feel ----
    public double recoil() { return recoil; }
    public void addRecoil(double amount) { recoil += amount; }
    public double screenshake() { return screenshake; }
    public void addScreenshake(double amount) { screenshake += amount; }
    public int lightLevel() { return lightLevel; }
    public void light(int level) { lightLevel = Math.max(lightLevel, level); }

    // ---- bookkeeping ----
    public int drawManyCount() { return drawManyCount; }
    public void setDrawManyCount(int count) { drawManyCount = count; }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ShotStateTest"`
Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/VerseType.java src/main/java/com/efkrdnz/magical/magic/incantation/PayloadKind.java src/main/java/com/efkrdnz/magical/magic/incantation/Behaviour.java src/main/java/com/efkrdnz/magical/magic/incantation/HitEffect.java src/main/java/com/efkrdnz/magical/magic/incantation/Wake.java src/main/java/com/efkrdnz/magical/magic/incantation/ShotState.java src/test/java/com/efkrdnz/magical/magic/incantation/ShotStateTest.java
git commit -m "feat: the shot state a verse writes into, and the eight verse types" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 2: Verses, prototypes and the catalogue

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VerseIds.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VerseAction.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/Verse.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VersePrototype.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VersePrototypes.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VerseCatalogue.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/Recital.java` (an empty forward declaration; Task 5 fills it)
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/VerseCatalogueTest.java`

**Interfaces:**
- Consumes: `VerseType`, `HitEffect` (Task 1).
- Produces: `VerseIds.of(path)`, `VerseIds.body(path)`; `VerseAction { int NONE = 0; int run(Recital, int recursion, int iteration); }`; `Verse` record, `Verse.of(path, type, mana, maxUses, prototype, bodies, declared, action)`, `verse.asRecursive()`, `Verse.UNLIMITED`, `Verse.Declared.of(draw, beat, rest)`, `Declared.ALL`; `VersePrototype` record with `Look`; `VersePrototypes.NEEDLE … WORD_STEP`, `byId`, `all()`; `VerseCatalogue.register/get/contains/all/ofType/size`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * A catalogue is an instance so a test can hold a small one. Two verses cannot share an id, an
 * unknown id answers null rather than throwing (the validator turns that into words), and every
 * prototype the table holds has the {@code magical:body/} namespace so it can never be mistaken for
 * a verse.
 */
class VerseCatalogueTest {

    private static Verse needle(String path) {
        return Verse.of(path, VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (recital, recursion, iteration) -> VerseAction.NONE);
    }

    @Test
    void anIdIsRegisteredOnce() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(needle("needle"));
        assertThrows(IllegalStateException.class, () -> catalogue.register(needle("needle")));
        assertEquals(1, catalogue.size());
    }

    @Test
    void anUnknownIdIsNull() {
        VerseCatalogue catalogue = new VerseCatalogue();
        assertNull(catalogue.get(VerseIds.of("nothing")));
        assertTrue(catalogue.ofType(VerseType.CONTROL).isEmpty());
    }

    @Test
    void recursiveIsACopyNotAMutation() {
        Verse plain = needle("needle");
        Verse recursive = plain.asRecursive();
        assertTrue(recursive.recursive());
        assertFalse(plain.recursive());
        assertEquals(plain.id(), recursive.id());
    }

    @Test
    void prototypesLiveUnderBody() {
        for (VersePrototype prototype : VersePrototypes.all()) {
            ResourceLocation id = prototype.id();
            assertEquals("magical", id.getNamespace());
            assertTrue(id.getPath().startsWith("body/"), id + " is not a body");
            assertEquals(prototype, VersePrototypes.byId(id));
        }
        assertEquals(17, VersePrototypes.all().size());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.VerseCatalogueTest"`
Expected: compilation failure.

- [ ] **Step 3: Write the types**

`VerseIds.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/** Ids without touching {@code MagicalMod}, so the package stays free of NeoForge. */
public final class VerseIds {

    public static final String NAMESPACE = "magical";

    private VerseIds() {
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static ResourceLocation body(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "body/" + path);
    }
}
```

`Recital.java` (forward declaration only; Task 5 replaces the whole file):

```java
package com.efkrdnz.magical.magic.incantation;

/** The machine a verse runs against. Filled in by the Recital task. */
public final class Recital {
}
```

`VerseAction.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/**
 * A card's function: {@code data.action(recursion_level, iteration)}. The return is the Refrain
 * family's {@code iter_max}; every other verse returns {@link #NONE}, which is the Lua's nil.
 */
@FunctionalInterface
public interface VerseAction {

    int NONE = 0;

    int run(Recital recital, int recursion, int iteration);
}
```

`Verse.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One card. Type, mana, uses and the recursive flag are what the machine reads; the prototype and
 * body count are what an Impose reads; {@link Declared} is what a tooltip shows, and
 * {@code VerseContentTest} holds it to what the action actually does.
 */
public record Verse(ResourceLocation id, VerseType type, int mana, int maxUses, boolean recursive,
                    VersePrototype prototype, int bodies, Declared declared, VerseAction action) {

    public static final int UNLIMITED = -1;

    /** The numbers a verse claims: cards drawn after it, beat added, rest added. */
    public record Declared(int draw, int beat, int rest) {
        public static final int ALL = -1;
        public static final Declared NONE = new Declared(0, 0, 0);

        public static Declared of(int draw, int beat, int rest) {
            return new Declared(draw, beat, rest);
        }
    }

    public Verse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(action, "action");
        if (bodies < 1) {
            throw new IllegalArgumentException(id + " fires " + bodies + " bodies");
        }
    }

    public static Verse of(String path, VerseType type, int mana, int maxUses, VersePrototype prototype,
                           int bodies, Declared declared, VerseAction action) {
        return new Verse(VerseIds.of(path), type, mana, maxUses, false, prototype, bodies, declared, action);
    }

    /** The same verse flagged {@code recursive}, which the recursion limit counts. (The record's own {@code recursive()} is the flag.) */
    public Verse asRecursive() {
        return new Verse(id, type, mana, maxUses, true, prototype, bodies, declared, action);
    }

    public boolean hasPrototype() {
        return prototype != null;
    }

    public boolean unlimited() {
        return maxUses < 0;
    }

    public String path() {
        return id.getPath();
    }
}
```

`VersePrototype.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;
import net.minecraft.resources.ResourceLocation;

/**
 * The body a verse names: the port of the projectile XML. A verse adds its deltas to the shot state
 * and names one of these; the spawner adds the two together. A static one does not move and pulses
 * its effect every {@code pulseIntervalTicks} for {@code durationTicks}.
 */
public record VersePrototype(ResourceLocation id, MagicSchool school, double damage, double healing, double speed,
                             int lifetimeTicks, float radius, boolean isStatic, int durationTicks,
                             double explosionRadius, double explosionDamage, HitEffect pulse, int pulseIntervalTicks,
                             Look look, boolean carriesCaster) {

    public enum Look {
        NEEDLE, ORB, SHARD, EMBER, ARC, DART, WHISPER, BLINK, RING, BURST, PIT, WORD
    }

    public boolean explodes() {
        return explosionRadius > 0.0D;
    }
}
```

`VersePrototypes.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.VersePrototype.Look;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** The table of bodies. Every number here is the body's own; the verse's deltas sit on top. */
public final class VersePrototypes {

    private static final Map<ResourceLocation, VersePrototype> BY_ID = new LinkedHashMap<>();

    public static final VersePrototype NEEDLE = flying("needle", MagicSchool.ARCANE, 3.0D, 0.0D, 1.6D, 40, 0.15F, 0.0D, 0.0D, Look.NEEDLE, false);
    public static final VersePrototype ORB = flying("orb", MagicSchool.ARCANE, 5.0D, 0.0D, 1.0D, 60, 0.3F, 0.0D, 0.0D, Look.ORB, false);
    public static final VersePrototype SHARD = flying("shard", MagicSchool.ARCANE, 9.0D, 0.0D, 0.7D, 60, 0.35F, 0.0D, 0.0D, Look.SHARD, false);
    public static final VersePrototype EMBER = flying("ember", MagicSchool.FIRE, 6.0D, 0.0D, 0.9D, 50, 0.25F, 2.0D, 2.5D, Look.EMBER, false);
    public static final VersePrototype ARC = flying("arc", MagicSchool.LIGHT, 7.0D, 0.0D, 2.0D, 20, 0.2F, 0.0D, 0.0D, Look.ARC, false);
    public static final VersePrototype DART = flying("dart", MagicSchool.LIGHT, 0.0D, 4.0D, 1.0D, 50, 0.2F, 0.0D, 0.0D, Look.DART, false);
    public static final VersePrototype WHISPER = flying("whisper", MagicSchool.ARCANE, 2.5D, 0.0D, 0.6D, 4, 0.25F, 0.0D, 0.0D, Look.WHISPER, false);
    public static final VersePrototype BLINK = flying("blink", MagicSchool.SPATIAL, 1.0D, 0.0D, 1.5D, 30, 0.2F, 0.0D, 0.0D, Look.BLINK, true);
    public static final VersePrototype BURST = standing("burst", MagicSchool.FIRE, 0.0D, 0.0D, 0.5F, 1, 3.0D, 8.0D, null, 1, Look.BURST);
    public static final VersePrototype RING_RIME = standing("ring_rime", MagicSchool.WATER, 0.0D, 0.0D, 3.0F, 100, 0.0D, 0.0D, HitEffect.FREEZE, 10, Look.RING);
    public static final VersePrototype RING_STORM = standing("ring_storm", MagicSchool.LIGHT, 1.0D, 0.0D, 3.0F, 100, 0.0D, 0.0D, HitEffect.SHOCK, 10, Look.RING);
    public static final VersePrototype RING_BALM = standing("ring_balm", MagicSchool.LIGHT, 0.0D, 1.0D, 3.0F, 100, 0.0D, 0.0D, null, 20, Look.RING);
    public static final VersePrototype RING_UPLIFT = standing("ring_uplift", MagicSchool.ARCANE, 0.0D, 0.0D, 3.0F, 100, 0.0D, 0.0D, HitEffect.UPLIFT, 10, Look.RING);
    public static final VersePrototype PIT = standing("pit", MagicSchool.VOID, 1.0D, 0.0D, 2.5F, 80, 0.0D, 0.0D, null, 10, Look.PIT);
    public static final VersePrototype WORD_HELD = standing("word_held", MagicSchool.ARCANE, 0.0D, 0.0D, 0.2F, 20, 0.0D, 0.0D, null, 20, Look.WORD);
    public static final VersePrototype WORD_FAR = flying("word_far", MagicSchool.ARCANE, 0.0D, 0.0D, 2.0D, 10, 0.1F, 0.0D, 0.0D, Look.WORD, false);
    public static final VersePrototype WORD_STEP = flying("word_step", MagicSchool.SPATIAL, 0.0D, 0.0D, 1.5D, 30, 0.1F, 0.0D, 0.0D, Look.WORD, true);

    private VersePrototypes() {
    }

    private static VersePrototype flying(String path, MagicSchool school, double damage, double healing, double speed,
                                         int lifetimeTicks, float radius, double explosionRadius, double explosionDamage,
                                         Look look, boolean carriesCaster) {
        return put(new VersePrototype(VerseIds.body(path), school, damage, healing, speed, lifetimeTicks, radius, false, 0,
                explosionRadius, explosionDamage, null, 0, look, carriesCaster));
    }

    private static VersePrototype standing(String path, MagicSchool school, double damage, double healing, float radius,
                                           int durationTicks, double explosionRadius, double explosionDamage,
                                           HitEffect pulse, int pulseIntervalTicks, Look look) {
        return put(new VersePrototype(VerseIds.body(path), school, damage, healing, 0.0D, 0, radius, true, durationTicks,
                explosionRadius, explosionDamage, pulse, pulseIntervalTicks, look, false));
    }

    private static VersePrototype put(VersePrototype prototype) {
        BY_ID.put(prototype.id(), prototype);
        return prototype;
    }

    public static VersePrototype byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    public static List<VersePrototype> all() {
        return List.copyOf(BY_ID.values());
    }
}
```

`VerseCatalogue.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** An instance registry of verses, in registration order. {@code VerseContent.CATALOGUE} is the real one. */
public final class VerseCatalogue {

    private final Map<ResourceLocation, Verse> verses = new LinkedHashMap<>();

    public Verse register(Verse verse) {
        if (verses.putIfAbsent(verse.id(), verse) != null) {
            throw new IllegalStateException("two verses share " + verse.id());
        }
        return verse;
    }

    /** Null when unknown; the validator turns that into a finding. */
    public Verse get(ResourceLocation id) {
        return verses.get(id);
    }

    public boolean contains(ResourceLocation id) {
        return verses.containsKey(id);
    }

    public Collection<Verse> all() {
        return Collections.unmodifiableCollection(verses.values());
    }

    public List<Verse> ofType(VerseType type) {
        return verses.values().stream().filter(verse -> verse.type() == type).toList();
    }

    public int size() {
        return verses.size();
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.VerseCatalogueTest"`
Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/VerseIds.java src/main/java/com/efkrdnz/magical/magic/incantation/Recital.java src/main/java/com/efkrdnz/magical/magic/incantation/VerseAction.java src/main/java/com/efkrdnz/magical/magic/incantation/Verse.java src/main/java/com/efkrdnz/magical/magic/incantation/VersePrototype.java src/main/java/com/efkrdnz/magical/magic/incantation/VersePrototypes.java src/main/java/com/efkrdnz/magical/magic/incantation/VerseCatalogue.java src/test/java/com/efkrdnz/magical/magic/incantation/VerseCatalogueTest.java
git commit -m "feat: a verse is a card with a prototype and a declared number; the catalogue holds them" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The Grimoire: incantations, the known set, the validator

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ReciteCaps.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/Incantation.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/Grimoire.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/IncantationValidator.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/IncantationTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/GrimoireTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/IncantationValidatorTest.java`

**Interfaces:**
- Consumes: `Verse`, `VerseCatalogue`, `VerseIds` (Task 2).
- Produces: `ReciteCaps` constants; `Incantation` (`entries()`, `breath()`, `size()`, `isEmpty()`, `write(ids, breath, catalogue)`, `setUses(index, uses)`, `clear()`, `copyFrom`, `save()`, `load(tag)`, `Entry(id, usesRemaining)`); `Grimoire` (`SLOTS`, `incantation(slot)`, `known()`, `knows(id)`, `learn(id)`, `learnAll`, `everyOtherSkipAndFlip()`, `clear()`, `copyFrom`, `save()`, `load(tag)`); `IncantationValidator.problems(ids, breath, catalogue)`, `problems(ids, breath, known, catalogue)`, `Finding(problem, index, id)`, `Problem {TOO_LONG, BAD_BREATH, UNKNOWN_VERSE, NOT_KNOWN}`.

- [ ] **Step 1: Write the failing tests**

`IncantationTest.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * An incantation is the authored thing: an order of verses with their uses, and a breath. Writing it
 * re-seeds uses from the catalogue, refuses anything the catalogue does not hold, and survives NBT.
 */
class IncantationTest {

    private static VerseCatalogue catalogue() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(Verse.of("needle", VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (r, rec, it) -> VerseAction.NONE));
        catalogue.register(Verse.of("ember", VerseType.PROJECTILE, 14, 15, VersePrototypes.EMBER, 1,
                Verse.Declared.of(0, 12, 0), (r, rec, it) -> VerseAction.NONE));
        return catalogue;
    }

    @Test
    void writingSeedsUsesFromTheCatalogue() {
        Incantation incantation = new Incantation();
        assertTrue(incantation.write(List.of(VerseIds.of("ember"), VerseIds.of("needle")), 2, catalogue()));
        assertEquals(2, incantation.breath());
        assertEquals(15, incantation.entries().get(0).usesRemaining());
        assertEquals(Verse.UNLIMITED, incantation.entries().get(1).usesRemaining());
    }

    @Test
    void anUnknownVerseRefusesTheWholeWrite() {
        Incantation incantation = new Incantation();
        assertTrue(incantation.write(List.of(VerseIds.of("needle")), 1, catalogue()));
        assertFalse(incantation.write(List.of(VerseIds.of("needle"), VerseIds.of("nothing")), 1, catalogue()));
        assertEquals(1, incantation.size());
    }

    @Test
    void tooLongOrBreathlessIsRefused() {
        Incantation incantation = new Incantation();
        List<ResourceLocation> tooMany = Collections.nCopies(ReciteCaps.MAX_VERSES + 1, VerseIds.of("needle"));
        assertFalse(incantation.write(tooMany, 1, catalogue()));
        assertFalse(incantation.write(List.of(VerseIds.of("needle")), 0, catalogue()));
        assertFalse(incantation.write(List.of(VerseIds.of("needle")), ReciteCaps.MAX_BREATH + 1, catalogue()));
        assertTrue(incantation.isEmpty());
    }

    @Test
    void usesSpentInPlayAreKept() {
        Incantation incantation = new Incantation();
        incantation.write(List.of(VerseIds.of("ember")), 1, catalogue());
        incantation.setUses(0, 3);
        assertEquals(3, incantation.entries().get(0).usesRemaining());
        CompoundTag tag = incantation.save();
        Incantation loaded = new Incantation();
        loaded.load(tag);
        assertEquals(3, loaded.entries().get(0).usesRemaining());
        assertEquals(VerseIds.of("ember"), loaded.entries().get(0).id());
        assertEquals(1, loaded.breath());
    }

    @Test
    void loadingGarbageKeepsWhatItCan() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("breath", 40);
        ListTag verses = new ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putString("id", "not a valid id!");
        bad.putInt("uses", -9);
        verses.add(bad);
        CompoundTag good = new CompoundTag();
        good.putString("id", "magical:needle");
        good.putInt("uses", -9);
        verses.add(good);
        tag.put("verses", verses);
        Incantation loaded = new Incantation();
        loaded.load(tag);
        assertEquals(ReciteCaps.MAX_BREATH, loaded.breath());
        assertEquals(1, loaded.size());
        assertEquals(Verse.UNLIMITED, loaded.entries().get(0).usesRemaining());
    }
}
```

`GrimoireTest.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The Grimoire is the one field the Authority of Mana puts on the player: four incantations, the
 * verses the wielder knows, and the Every Other toggle all four share. It round-trips NBT, copies
 * wholesale (the attachment is copyOnDeath) and clears to nothing when the Authority leaves.
 */
class GrimoireTest {

    private static VerseCatalogue catalogue() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(Verse.of("needle", VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (r, rec, it) -> VerseAction.NONE));
        return catalogue;
    }

    @Test
    void roundTripsNbt() {
        Grimoire grimoire = new Grimoire();
        grimoire.learn(VerseIds.of("needle"));
        grimoire.incantation(2).write(List.of(VerseIds.of("needle"), VerseIds.of("needle")), 3, catalogue());
        assertFalse(grimoire.everyOtherSkipAndFlip());
        CompoundTag tag = grimoire.save();
        Grimoire loaded = new Grimoire();
        loaded.load(tag);
        assertTrue(loaded.knows(VerseIds.of("needle")));
        assertEquals(2, loaded.incantation(2).size());
        assertEquals(3, loaded.incantation(2).breath());
        assertTrue(loaded.incantation(0).isEmpty());
        assertTrue(loaded.everyOtherSkipAndFlip(), "the toggle had been flipped once before saving");
    }

    @Test
    void copyFromReplacesEverything() {
        Grimoire source = new Grimoire();
        source.learn(VerseIds.of("needle"));
        source.incantation(0).write(List.of(VerseIds.of("needle")), 1, catalogue());
        Grimoire target = new Grimoire();
        target.incantation(3).write(List.of(VerseIds.of("needle")), 1, catalogue());
        target.copyFrom(source);
        assertEquals(1, target.incantation(0).size());
        assertTrue(target.incantation(3).isEmpty());
        assertTrue(target.knows(VerseIds.of("needle")));
    }

    @Test
    void clearForgetsTheBook() {
        Grimoire grimoire = new Grimoire();
        grimoire.learn(VerseIds.of("needle"));
        grimoire.incantation(1).write(List.of(VerseIds.of("needle")), 1, catalogue());
        grimoire.clear();
        assertTrue(grimoire.known().isEmpty());
        for (int slot = 0; slot < Grimoire.SLOTS; slot++) {
            assertTrue(grimoire.incantation(slot).isEmpty());
        }
    }

    @Test
    void aSlotOutOfRangeIsClamped() {
        Grimoire grimoire = new Grimoire();
        assertEquals(grimoire.incantation(0), grimoire.incantation(-5));
        assertEquals(grimoire.incantation(Grimoire.SLOTS - 1), grimoire.incantation(99));
    }
}
```

`IncantationValidatorTest.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The validator is the one set of words the server and the future screen both speak: it is pure,
 * it reports every problem with the index it sits at, and it says nothing about a legal tape.
 */
class IncantationValidatorTest {

    private static VerseCatalogue catalogue() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(Verse.of("needle", VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (r, rec, it) -> VerseAction.NONE));
        catalogue.register(Verse.of("weight", VerseType.MODIFIER, 3, Verse.UNLIMITED, null, 1,
                Verse.Declared.of(1, 2, 0), (r, rec, it) -> VerseAction.NONE));
        return catalogue;
    }

    @Test
    void aLegalTapeHasNoProblems() {
        List<ResourceLocation> ids = List.of(VerseIds.of("weight"), VerseIds.of("needle"));
        assertTrue(IncantationValidator.problems(ids, 1, Set.copyOf(ids), catalogue()).isEmpty());
        assertTrue(IncantationValidator.problems(List.of(), 1, Set.of(), catalogue()).isEmpty());
    }

    @Test
    void everyProblemNamesItsIndex() {
        List<ResourceLocation> ids = List.of(VerseIds.of("needle"), VerseIds.of("nothing"), VerseIds.of("weight"));
        List<IncantationValidator.Finding> findings =
                IncantationValidator.problems(ids, 0, Set.of(VerseIds.of("needle")), catalogue());
        assertEquals(3, findings.size());
        assertEquals(new IncantationValidator.Finding(IncantationValidator.Problem.BAD_BREATH, -1, null), findings.get(0));
        assertEquals(new IncantationValidator.Finding(IncantationValidator.Problem.UNKNOWN_VERSE, 1, VerseIds.of("nothing")), findings.get(1));
        assertEquals(new IncantationValidator.Finding(IncantationValidator.Problem.NOT_KNOWN, 2, VerseIds.of("weight")), findings.get(2));
    }

    @Test
    void lengthIsCapped() {
        List<ResourceLocation> ids = Collections.nCopies(ReciteCaps.MAX_VERSES + 1, VerseIds.of("needle"));
        List<IncantationValidator.Finding> findings = IncantationValidator.problems(ids, 1, catalogue());
        assertEquals(1, findings.size());
        assertEquals(IncantationValidator.Problem.TOO_LONG, findings.get(0).problem());
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.IncantationTest" --tests "com.efkrdnz.magical.magic.incantation.GrimoireTest" --tests "com.efkrdnz.magical.magic.incantation.IncantationValidatorTest"`
Expected: compilation failure.

- [ ] **Step 3: Write the caps, the validator, the incantation and the Grimoire**

`ReciteCaps.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/**
 * Noita runs its draw on one player's frame; this runs on a server tick for every wielder, so it is
 * bounded. Past a cap the recite frays: drawing stops, what was planned still fires.
 */
public final class ReciteCaps {

    public static final int MAX_VERSES = 20;
    public static final int MIN_BREATH = 1;
    public static final int MAX_BREATH = 8;
    public static final int MAX_STEPS = 1024;
    public static final int MAX_BODIES = 64;
    public static final int MAX_DEPTH = 4;
    /** Noita's own {@code recursion_limit}. */
    public static final int RECURSION_LIMIT = 2;

    private ReciteCaps() {
    }
}
```

`IncantationValidator.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Pure, so the server and the screen produce the same words for the same tape. */
public final class IncantationValidator {

    public enum Problem {
        TOO_LONG,
        BAD_BREATH,
        UNKNOWN_VERSE,
        NOT_KNOWN
    }

    /** {@code index} is -1 for a problem with the tape as a whole. */
    public record Finding(Problem problem, int index, ResourceLocation id) {
    }

    private IncantationValidator() {
    }

    /** Structure and catalogue only; what a write checks. */
    public static List<Finding> problems(List<ResourceLocation> ids, int breath, VerseCatalogue catalogue) {
        return problems(ids, breath, null, catalogue);
    }

    /** With {@code known} the wielder's set as well; what the server checks on an edit. Null skips that check. */
    public static List<Finding> problems(List<ResourceLocation> ids, int breath, Set<ResourceLocation> known,
                                         VerseCatalogue catalogue) {
        List<Finding> findings = new ArrayList<>();
        if (ids.size() > ReciteCaps.MAX_VERSES) {
            findings.add(new Finding(Problem.TOO_LONG, -1, null));
        }
        if (breath < ReciteCaps.MIN_BREATH || breath > ReciteCaps.MAX_BREATH) {
            findings.add(new Finding(Problem.BAD_BREATH, -1, null));
        }
        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation id = ids.get(i);
            if (id == null || !catalogue.contains(id)) {
                findings.add(new Finding(Problem.UNKNOWN_VERSE, i, id));
            } else if (known != null && !known.contains(id)) {
                findings.add(new Finding(Problem.NOT_KNOWN, i, id));
            }
        }
        return findings;
    }
}
```

`Incantation.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * One incantation: an order of verses, each with its uses left, and a breath (the root draw
 * budget, Noita's spells per cast made a property of the tape). Writing re-seeds uses from the
 * catalogue; play writes spent uses back through {@link #setUses}.
 */
public final class Incantation {

    public record Entry(ResourceLocation id, int usesRemaining) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private int breath = ReciteCaps.MIN_BREATH;

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int breath() {
        return breath;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Replaces everything. False, and nothing changed, when the validator objects. */
    public boolean write(List<ResourceLocation> ids, int breath, VerseCatalogue catalogue) {
        if (!IncantationValidator.problems(ids, breath, catalogue).isEmpty()) {
            return false;
        }
        entries.clear();
        for (ResourceLocation id : ids) {
            entries.add(new Entry(id, catalogue.get(id).maxUses()));
        }
        this.breath = breath;
        return true;
    }

    public void setUses(int index, int uses) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.set(index, new Entry(entries.get(index).id(), Math.max(Verse.UNLIMITED, uses)));
    }

    public void clear() {
        entries.clear();
        breath = ReciteCaps.MIN_BREATH;
    }

    public void copyFrom(Incantation other) {
        entries.clear();
        entries.addAll(other.entries);
        breath = other.breath;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("breath", breath);
        ListTag verses = new ListTag();
        for (Entry entry : entries) {
            CompoundTag verse = new CompoundTag();
            verse.putString("id", entry.id().toString());
            verse.putInt("uses", entry.usesRemaining());
            verses.add(verse);
        }
        tag.put("verses", verses);
        return tag;
    }

    /** Anything unreadable is dropped rather than poisoning the tape; the numbers are clamped. */
    public void load(CompoundTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        breath = Math.max(ReciteCaps.MIN_BREATH, Math.min(ReciteCaps.MAX_BREATH, tag.getInt("breath")));
        ListTag verses = tag.getList("verses", Tag.TAG_COMPOUND);
        for (int i = 0; i < verses.size() && entries.size() < ReciteCaps.MAX_VERSES; i++) {
            CompoundTag verse = verses.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(verse.getString("id"));
            if (id == null) {
                continue;
            }
            entries.add(new Entry(id, Math.max(Verse.UNLIMITED, verse.getInt("uses"))));
        }
    }
}
```

`Grimoire.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The wielder's book: four incantations, the verses they know, and the toggle Clause: Every Other
 * shares across all four. This is the whole of the Authority of Mana's authored state and the data
 * model the Grimoire screen will edit.
 */
public final class Grimoire {

    public static final int SLOTS = 4;

    private final Incantation[] incantations = new Incantation[SLOTS];
    private final Set<ResourceLocation> known = new LinkedHashSet<>();
    private boolean everyOtherSkip;

    public Grimoire() {
        for (int slot = 0; slot < SLOTS; slot++) {
            incantations[slot] = new Incantation();
        }
    }

    public Incantation incantation(int slot) {
        return incantations[Math.max(0, Math.min(SLOTS - 1, slot))];
    }

    public Set<ResourceLocation> known() {
        return Collections.unmodifiableSet(known);
    }

    public boolean knows(ResourceLocation id) {
        return known.contains(id);
    }

    public boolean learn(ResourceLocation id) {
        return known.add(id);
    }

    public void learnAll(Collection<ResourceLocation> ids) {
        known.addAll(ids);
    }

    /** Clause: Every Other's {@code GUN_ACTION_IF_HALF_STATUS}: returns whether to skip, then flips. */
    public boolean everyOtherSkipAndFlip() {
        boolean skip = everyOtherSkip;
        everyOtherSkip = !everyOtherSkip;
        return skip;
    }

    public void clear() {
        for (Incantation incantation : incantations) {
            incantation.clear();
        }
        known.clear();
        everyOtherSkip = false;
    }

    public void copyFrom(Grimoire other) {
        for (int slot = 0; slot < SLOTS; slot++) {
            incantations[slot].copyFrom(other.incantations[slot]);
        }
        known.clear();
        known.addAll(other.known);
        everyOtherSkip = other.everyOtherSkip;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Incantation incantation : incantations) {
            list.add(incantation.save());
        }
        tag.put("incantations", list);
        ListTag knownList = new ListTag();
        for (ResourceLocation id : known) {
            knownList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("known", knownList);
        tag.putBoolean("everyOther", everyOtherSkip);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        ListTag list = tag.getList("incantations", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < Math.min(SLOTS, list.size()); slot++) {
            incantations[slot].load(list.getCompound(slot));
        }
        ListTag knownList = tag.getList("known", Tag.TAG_STRING);
        for (int i = 0; i < knownList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(knownList.getString(i));
            if (id != null) {
                known.add(id);
            }
        }
        everyOtherSkip = tag.getBoolean("everyOther");
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.IncantationTest" --tests "com.efkrdnz.magical.magic.incantation.GrimoireTest" --tests "com.efkrdnz.magical.magic.incantation.IncantationValidatorTest"`
Expected: 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/ReciteCaps.java src/main/java/com/efkrdnz/magical/magic/incantation/Incantation.java src/main/java/com/efkrdnz/magical/magic/incantation/Grimoire.java src/main/java/com/efkrdnz/magical/magic/incantation/IncantationValidator.java src/test/java/com/efkrdnz/magical/magic/incantation/IncantationTest.java src/test/java/com/efkrdnz/magical/magic/incantation/GrimoireTest.java src/test/java/com/efkrdnz/magical/magic/incantation/IncantationValidatorTest.java
git commit -m "feat: the Grimoire, four incantations and a known set, with the validator the screen will share" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 4: The piles, the world, the events and the plan records

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VerseCard.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ReciteSession.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ReciteWorld.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ReciteEvent.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ShotPlan.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ProjectilePlan.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/RecitePlan.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/ReciteSessionTest.java`

**Interfaces:**
- Consumes: `Incantation`, `VerseCatalogue`, `Verse`, `ShotState`, `PayloadKind`, `VersePrototype`.
- Produces: `VerseCard(verse, deckIndex, usesRemaining)` with `verse()`, `id()`, `type()`, `deckIndex()`, `usesRemaining()`, `spent()`, `consumeUse()`; `ReciteSession.of(incantation, catalogue)`, `deck()/hand()/discard()` (live lists), `replaceHand()/replaceDeck()`, `moveDiscardToDeck()`, `orderDeck()`, `firstShot()/firstShotDone()`, `restCarry()/setRestCarry(int)`, `cards()`, `writeBack(incantation)`, `unreadCount()`, `nextUnread()`; `ReciteWorld` interface; `ReciteEvent(kind, verse, depth)` with `Kind {PLAYED, COPIED, FALTER_MANA, FALTER_SPENT, OVERRUN, REST, FRAYED, DISCARDED}`; `ShotPlan(bodies, state)` with `countAll()`; `ProjectilePlan(prototype, verse, stamped, payloadKind, fuseTicks, payload)` with `hasPayload()`; `RecitePlan(root, beatTicks, restTicks, rests, manaSpent, manaLeft, frayed, events)` with `cooldownTicks()`, `bodies()`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A session is the three piles between presses. It is built from the incantation in order, it
 * writes spent uses back into the incantation (the only runtime fact that is saved), and where the
 * Lua reassigns a pile the session replaces the list object, so a verse holding the old list keeps
 * iterating the old contents exactly as {@code ipairs} would.
 */
class ReciteSessionTest {

    private static VerseCatalogue catalogue() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(Verse.of("needle", VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (r, rec, it) -> VerseAction.NONE));
        catalogue.register(Verse.of("ember", VerseType.PROJECTILE, 14, 15, VersePrototypes.EMBER, 1,
                Verse.Declared.of(0, 12, 0), (r, rec, it) -> VerseAction.NONE));
        return catalogue;
    }

    private static Incantation tape() {
        Incantation incantation = new Incantation();
        incantation.write(List.of(VerseIds.of("ember"), VerseIds.of("needle"), VerseIds.of("ember")), 1, catalogue());
        return incantation;
    }

    @Test
    void cardsKeepTheTapeOrderAndTheirIndex() {
        ReciteSession session = ReciteSession.of(tape(), catalogue());
        assertEquals(3, session.deck().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i, session.deck().get(i).deckIndex());
        }
        assertEquals(15, session.deck().get(0).usesRemaining());
        assertEquals(Verse.UNLIMITED, session.deck().get(1).usesRemaining());
        assertTrue(session.firstShot());
        assertEquals(VerseIds.of("ember"), session.nextUnread());
        assertEquals(3, session.unreadCount());
    }

    @Test
    void spentUsesAreWrittenBackEvenForACardThatLeftThePiles() {
        Incantation incantation = tape();
        ReciteSession session = ReciteSession.of(incantation, catalogue());
        VerseCard first = session.deck().remove(0);
        for (int i = 0; i < 15; i++) {
            first.consumeUse();
        }
        assertTrue(first.spent());
        first.consumeUse();
        assertEquals(0, first.usesRemaining(), "a spent card stays at zero");
        session.writeBack(incantation);
        assertEquals(0, incantation.entries().get(0).usesRemaining());
        assertEquals(15, incantation.entries().get(2).usesRemaining());
    }

    @Test
    void replacingAPileLeavesTheOldListToWhoeverHoldsIt() {
        ReciteSession session = ReciteSession.of(tape(), catalogue());
        List<VerseCard> deckBefore = session.deck();
        session.replaceDeck();
        assertNotSame(deckBefore, session.deck());
        assertEquals(3, deckBefore.size());
        assertTrue(session.deck().isEmpty());
        List<VerseCard> discardBefore = session.discard();
        discardBefore.addAll(deckBefore);
        session.moveDiscardToDeck();
        assertEquals(3, session.deck().size());
        assertNotSame(discardBefore, session.discard());
        assertTrue(session.discard().isEmpty());
    }

    @Test
    void orderDeckSortsByIndexNeverShuffles() {
        ReciteSession session = ReciteSession.of(tape(), catalogue());
        VerseCard a = session.deck().remove(0);
        session.deck().add(a);
        session.orderDeck();
        assertSame(a, session.deck().get(0));
        assertEquals(List.of(0, 1, 2), session.deck().stream().map(VerseCard::deckIndex).toList());
    }

    @Test
    void anEntryTheCatalogueLostIsSkippedButKeepsItsIndex() {
        Incantation incantation = tape();
        VerseCatalogue smaller = new VerseCatalogue();
        smaller.register(catalogue().get(VerseIds.of("needle")));
        ReciteSession session = ReciteSession.of(incantation, smaller);
        assertEquals(1, session.deck().size());
        assertEquals(1, session.deck().get(0).deckIndex());
        assertFalse(session.deck().isEmpty());
        session.deck().clear();
        assertNull(session.nextUnread());
    }

    @Test
    void thePlanRecordsAreSelfDescribing() {
        ShotState stamped = new ShotState();
        ProjectilePlan leaf = new ProjectilePlan(VersePrototypes.NEEDLE, VerseIds.of("needle"), stamped, PayloadKind.NONE, 0, null);
        ShotPlan payload = new ShotPlan(List.of(leaf, leaf), new ShotState());
        ProjectilePlan carrier = new ProjectilePlan(VersePrototypes.NEEDLE, VerseIds.of("needle_latch"), stamped, PayloadKind.LATCH, 0, payload);
        ShotPlan root = new ShotPlan(List.of(carrier), new ShotState());
        assertTrue(carrier.hasPayload());
        assertFalse(leaf.hasPayload());
        assertEquals(3, root.countAll());
        RecitePlan plan = new RecitePlan(root, -3, 10, true, 6, 94, false, List.of());
        assertEquals(10, plan.cooldownTicks());
        assertEquals(0, new RecitePlan(root, -3, 10, false, 6, 94, false, List.of()).cooldownTicks());
        assertEquals(1, plan.bodies().size());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ReciteSessionTest"`
Expected: compilation failure.

- [ ] **Step 3: Write the records, the world and the session**

`VerseCard.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/**
 * A verse as it sits in the piles: {@code clone_action} plus {@code deck_index} and
 * {@code uses_remaining}. The uses are the incantation's entry, written back after every press.
 */
public final class VerseCard {

    private final Verse verse;
    private final int deckIndex;
    private int usesRemaining;

    public VerseCard(Verse verse, int deckIndex, int usesRemaining) {
        this.verse = verse;
        this.deckIndex = deckIndex;
        this.usesRemaining = usesRemaining;
    }

    public Verse verse() {
        return verse;
    }

    public ResourceLocation id() {
        return verse.id();
    }

    public VerseType type() {
        return verse.type();
    }

    public int deckIndex() {
        return deckIndex;
    }

    public int usesRemaining() {
        return usesRemaining;
    }

    /** {@code uses_remaining == 0}: written, never castable, skipped by the draw. */
    public boolean spent() {
        return usesRemaining == 0;
    }

    /** Only a limited card with uses left loses one; -1 is unlimited and 0 stays 0. */
    public void consumeUse() {
        if (usesRemaining > 0) {
            usesRemaining--;
        }
    }

    @Override
    public String toString() {
        return verse.path() + "#" + deckIndex + (usesRemaining < 0 ? "" : "(" + usesRemaining + ")");
    }
}
```

`ReciteSession.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The three piles between presses, {@code first_shot} and the rest carried until the next rest.
 * Never saved: rebuilt from the incantation on an edit, login, respawn or a change of dimension.
 *
 * <p>Where {@code gun.lua} reassigns a pile ({@code hand = {}}, {@code deck = {}},
 * {@code discarded = {}}) this replaces the list object; where it inserts or removes, the live list
 * is mutated. A verse that captured a list keeps the old one, which is what {@code ipairs} does.
 */
public final class ReciteSession {

    private List<VerseCard> deck = new ArrayList<>();
    private List<VerseCard> hand = new ArrayList<>();
    private List<VerseCard> discard = new ArrayList<>();
    private final List<VerseCard> all = new ArrayList<>();
    private boolean firstShot = true;
    private int restCarry;

    private ReciteSession() {
    }

    /** {@code _add_card_to_deck} for every entry; an id the catalogue no longer holds is skipped, its index kept. */
    public static ReciteSession of(Incantation incantation, VerseCatalogue catalogue) {
        ReciteSession session = new ReciteSession();
        List<Incantation.Entry> entries = incantation.entries();
        for (int i = 0; i < entries.size(); i++) {
            Verse verse = catalogue.get(entries.get(i).id());
            if (verse == null) {
                continue;
            }
            VerseCard card = new VerseCard(verse, i, entries.get(i).usesRemaining());
            session.deck.add(card);
            session.all.add(card);
        }
        return session;
    }

    public List<VerseCard> deck() {
        return deck;
    }

    public List<VerseCard> hand() {
        return hand;
    }

    public List<VerseCard> discard() {
        return discard;
    }

    /** {@code hand = {}}. */
    public void replaceHand() {
        hand = new ArrayList<>();
    }

    /** {@code deck = {}}. */
    public void replaceDeck() {
        deck = new ArrayList<>();
    }

    /** {@code move_discarded_to_deck}: append every discarded card to the deck, then {@code discarded = {}}. */
    public void moveDiscardToDeck() {
        deck.addAll(discard);
        discard = new ArrayList<>();
    }

    /** {@code order_deck} with shuffling off: a stable sort by deck index. */
    public void orderDeck() {
        deck.sort(Comparator.comparingInt(VerseCard::deckIndex));
    }

    public boolean firstShot() {
        return firstShot;
    }

    public void firstShotDone() {
        firstShot = false;
    }

    public int restCarry() {
        return restCarry;
    }

    public void setRestCarry(int ticks) {
        restCarry = ticks;
    }

    /** Every card ever built for this session, whether or not a pile still holds it. */
    public List<VerseCard> cards() {
        return List.copyOf(all);
    }

    public void writeBack(Incantation incantation) {
        for (VerseCard card : all) {
            incantation.setUses(card.deckIndex(), card.usesRemaining());
        }
    }

    public int unreadCount() {
        return deck.size();
    }

    /** The verse the next press starts with, for the HUD line; null when the deck is empty. */
    public ResourceLocation nextUnread() {
        return deck.isEmpty() ? null : deck.get(0).id();
    }
}
```

`ReciteWorld.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything a verse may ask the world. The server answers over the level and the Grimoire; a test
 * answers with fixed numbers; the future preview answers with assumed ones. Nothing in the evaluator
 * knows which.
 */
public interface ReciteWorld {

    int enemiesWithin(double blocks);

    int projectilesWithin(double blocks);

    /** 0..1. */
    double healthFraction();

    /** Clause: Every Other's shared toggle: whether to skip this time, then flipped. */
    boolean everyOtherSkipAndFlip();

    /** 0 inclusive to {@code bound} exclusive. */
    int random(int bound);

    /** The whole catalogue, in registration order. */
    List<Verse> allVerses();

    boolean isKnown(ResourceLocation id);

    /** Every verse written in the wielder's other incantations, for Wild Recall. */
    List<Verse> otherIncantationVerses();

    /** Blood Toll's price, in half-hearts of true damage. */
    void payHealth(double halfHearts);
}
```

`ReciteEvent.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/** What happened during a recite, in order, with the payload depth it happened at. */
public record ReciteEvent(Kind kind, ResourceLocation verse, int depth) {

    public enum Kind {
        PLAYED,
        COPIED,
        FALTER_MANA,
        FALTER_SPENT,
        OVERRUN,
        REST,
        FRAYED,
        DISCARDED
    }
}
```

`ShotPlan.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.List;

/** One shot: its bodies and the final state the spawner reads the shot-wide numbers from. */
public record ShotPlan(List<ProjectilePlan> bodies, ShotState state) {

    public ShotPlan {
        bodies = List.copyOf(bodies);
    }

    /** Bodies at every depth, payloads included. */
    public int countAll() {
        int count = 0;
        for (ProjectilePlan body : bodies) {
            count += 1 + (body.hasPayload() ? body.payload().countAll() : 0);
        }
        return count;
    }
}
```

`ProjectilePlan.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/**
 * One body: the prototype, the verse that added it, the state it was stamped with at that instant,
 * and the payload it releases, if any, as a whole shot of its own.
 */
public record ProjectilePlan(VersePrototype prototype, ResourceLocation verse, ShotState stamped,
                             PayloadKind payloadKind, int fuseTicks, ShotPlan payload) {

    public boolean hasPayload() {
        return payload != null;
    }
}
```

`RecitePlan.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.List;

/**
 * What one press produced. {@code manaSpent} is negative for a net refund. The cooldown is the
 * larger of beat and rest, never their sum, because that is what Noita waits for.
 */
public record RecitePlan(ShotPlan root, int beatTicks, int restTicks, boolean rests, int manaSpent, int manaLeft,
                         boolean frayed, List<ReciteEvent> events) {

    public RecitePlan {
        events = List.copyOf(events);
    }

    public int cooldownTicks() {
        return Math.max(0, Math.max(beatTicks, rests ? restTicks : 0));
    }

    public List<ProjectilePlan> bodies() {
        return root.bodies();
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ReciteSessionTest"`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/VerseCard.java src/main/java/com/efkrdnz/magical/magic/incantation/ReciteSession.java src/main/java/com/efkrdnz/magical/magic/incantation/ReciteWorld.java src/main/java/com/efkrdnz/magical/magic/incantation/ReciteEvent.java src/main/java/com/efkrdnz/magical/magic/incantation/ShotPlan.java src/main/java/com/efkrdnz/magical/magic/incantation/ProjectilePlan.java src/main/java/com/efkrdnz/magical/magic/incantation/RecitePlan.java src/test/java/com/efkrdnz/magical/magic/incantation/ReciteSessionTest.java
git commit -m "feat: the three piles between presses, and the plan a press returns" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 5: The Recital: the draw, mana, uses, the hand, the rest

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/Recital.java` (replace the forward declaration)
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/Reciter.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/VerseContent.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ProjectileVerses.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/StaticVerses.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ModifierVerses.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/MulticastVerses.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/UtilityVerses.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/FixedWorld.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/ReciteFixtures.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/ReciterTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1–4.
- Produces: `Recital` public surface for verses: `state()`, `deck()`, `hand()`, `discard()`, `world()`, `mana()/setMana(int)`, `rest()/addRest(int)/setRest(int)`, `depth()`, `drawDisabled()/setDrawDisabled(boolean)`, `frayed()`, `drawActions(int)`, `addProjectile(VersePrototype)`, `addProjectileLatch(VersePrototype, int draw)`, `addProjectileFuse(VersePrototype, int fuseTicks, int draw)`, `addProjectileEpitaph(VersePrototype, int draw)`, `call(Verse, int recursion, int iteration)`, `checkRecursion(Verse, int level)`, `discardTop(int)`, `discardAt(int index, int count)`, `refreshPage()`, `event(Kind, ResourceLocation)`; `Reciter.recite(session, breath, mana, costScale, world)`; `VerseContent.CATALOGUE`, `VerseContent.get(id)`; `ProjectileVerses.NEEDLE`, `ProjectileVerses.EMBER`, `ModifierVerses.WEIGHT`, `ModifierVerses.HASTE`, `ModifierVerses.UNDYING`, `MulticastVerses.COUPLET`, `StaticVerses.DETONATION` (ids); the helpers `ProjectileVerses.projectile(...)`, `ProjectileVerses.carrier(...)`, `StaticVerses.stationary(...)`, `StaticVerses.carrier(...)`, `ModifierVerses.modifier(...)`, `MulticastVerses.multicast(...)` that the later tasks add verses through; test fixtures `FixedWorld` and `ReciteFixtures.tape/session/press/bodies/unread/played`.

- [ ] **Step 1: Write the fixtures and the failing test**

`FixedWorld.java` (test source set):

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A world that answers with whatever the test set; {@code random} returns queued rolls, then zeros. */
final class FixedWorld implements ReciteWorld {

    int enemies;
    int projectiles;
    double health = 1.0D;
    boolean everyOtherSkip;
    final Deque<Integer> rolls = new ArrayDeque<>();
    List<Verse> all = List.copyOf(VerseContent.CATALOGUE.all());
    /** Null means every verse is known. */
    Set<ResourceLocation> known;
    List<Verse> others = List.of();
    final List<Double> healthPaid = new ArrayList<>();

    FixedWorld roll(int... values) {
        for (int value : values) {
            rolls.add(value);
        }
        return this;
    }

    @Override
    public int enemiesWithin(double blocks) {
        return enemies;
    }

    @Override
    public int projectilesWithin(double blocks) {
        return projectiles;
    }

    @Override
    public double healthFraction() {
        return health;
    }

    @Override
    public boolean everyOtherSkipAndFlip() {
        boolean skip = everyOtherSkip;
        everyOtherSkip = !everyOtherSkip;
        return skip;
    }

    @Override
    public int random(int bound) {
        Integer next = rolls.poll();
        return next == null ? 0 : Math.floorMod(next, bound);
    }

    @Override
    public List<Verse> allVerses() {
        return all;
    }

    @Override
    public boolean isKnown(ResourceLocation id) {
        return known == null || known.contains(id);
    }

    @Override
    public List<Verse> otherIncantationVerses() {
        return others;
    }

    @Override
    public void payHealth(double halfHearts) {
        healthPaid.add(halfHearts);
    }
}
```

`ReciteFixtures.java` (test source set):

```java
package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Tapes by path, presses with plain numbers, and the paths of what came out. */
final class ReciteFixtures {

    static final int PLENTY = 1000;

    private ReciteFixtures() {
    }

    static ResourceLocation id(String path) {
        return VerseIds.of(path);
    }

    static Incantation tape(int breath, String... paths) {
        Incantation incantation = new Incantation();
        List<ResourceLocation> ids = Arrays.stream(paths).map(ReciteFixtures::id).toList();
        assertTrue(incantation.write(ids, breath, VerseContent.CATALOGUE), "the fixture tape must be valid: " + Arrays.toString(paths));
        return incantation;
    }

    static ReciteSession session(String... paths) {
        return ReciteSession.of(tape(1, paths), VerseContent.CATALOGUE);
    }

    static RecitePlan press(ReciteSession session, int breath, int mana, ReciteWorld world) {
        return Reciter.recite(session, breath, mana, 1.0D, world);
    }

    static RecitePlan press(ReciteSession session) {
        return press(session, 1, PLENTY, new FixedWorld());
    }

    /** The verse paths of the bodies of a shot, in order. */
    static List<String> bodies(ShotPlan plan) {
        return plan.bodies().stream().map(body -> body.verse().getPath()).toList();
    }

    static List<String> unread(ReciteSession session) {
        return session.deck().stream().map(card -> card.id().getPath()).toList();
    }

    static List<String> read(ReciteSession session) {
        return session.discard().stream().map(card -> card.id().getPath()).toList();
    }

    static long played(RecitePlan plan, String path) {
        return plan.events().stream()
                .filter(event -> event.kind() == ReciteEvent.Kind.PLAYED)
                .filter(event -> event.verse() != null && event.verse().getPath().equals(path))
                .count();
    }

    static long count(RecitePlan plan, ReciteEvent.Kind kind) {
        return plan.events().stream().filter(event -> event.kind() == kind).count();
    }
}
```

`ReciterTest.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The draw. A modifier reaches every body drawn after it in the same shot and none before, the
 * breath is the root budget and the verses decide the rest, a verse you cannot afford is skipped
 * rather than failing the press, and uses follow the body.
 */
class ReciterTest {

    @Test
    void aModifierReachesEveryBodyAfterIt() {
        ReciteSession session = session("weight", "couplet", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("needle", "needle"), bodies(plan.root()));
        for (ProjectilePlan body : plan.bodies()) {
            assertEquals(2.5D, body.stamped().damageAdd(), 1e-9);
        }
    }

    @Test
    void aModifierAfterABodyDoesNotReachBack() {
        ReciteSession session = session("needle", "weight", "needle");
        RecitePlan plan = press(session, 3, PLENTY, new FixedWorld());
        assertEquals(List.of("needle", "needle"), bodies(plan.root()));
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(2.5D, plan.bodies().get(1).stamped().damageAdd(), 1e-9);
        assertTrue(plan.rests(), "the root draw ran dry, so the press rests");
    }

    @Test
    void drawCountsByType() {
        assertEquals(1, press(session("needle", "needle")).bodies().size());
        assertEquals(1, press(session("weight", "needle", "needle")).bodies().size());
        assertEquals(2, press(session("couplet", "needle", "needle", "needle")).bodies().size());
    }

    @Test
    void aTapeOfThreePresses() {
        ReciteSession session = session("weight", "couplet", "needle", "needle", "haste", "needle", "detonation");

        RecitePlan first = press(session, 1, 100, new FixedWorld());
        assertEquals(List.of("needle", "needle"), bodies(first.root()));
        assertEquals(2.5D, first.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(5.0D, first.bodies().get(0).stamped().critChance(), 1e-9);
        assertEquals(10.0D, first.bodies().get(1).stamped().critChance(), 1e-9);
        assertEquals(4, first.beatTicks());
        assertEquals(11, first.manaSpent());
        assertFalse(first.rests());
        assertEquals(List.of("haste", "needle", "detonation"), unread(session));

        RecitePlan second = press(session, 1, 100, new FixedWorld());
        assertEquals(List.of("needle"), bodies(second.root()));
        assertEquals(2.5D, second.bodies().get(0).stamped().speedMultiplier(), 1e-9);
        assertEquals(0.0D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(1, second.beatTicks());
        assertEquals(6, second.manaSpent());
        assertEquals(List.of("detonation"), unread(session));

        RecitePlan third = press(session, 1, 100, new FixedWorld());
        assertEquals(List.of("detonation"), bodies(third.root()));
        assertEquals(VersePrototypes.BURST, third.bodies().get(0).prototype());
        assertEquals(20, third.manaSpent());
        assertTrue(third.rests());
        assertEquals(0, third.restTicks());
        assertEquals(1, count(third, ReciteEvent.Kind.REST));
        assertEquals(List.of("weight", "couplet", "needle", "needle", "haste", "needle", "detonation"), unread(session));

        RecitePlan fourth = press(session, 1, 100, new FixedWorld());
        assertEquals(List.of("needle", "needle"), bodies(fourth.root()));
    }

    @Test
    void aVerseYouCannotAffordIsSkipped() {
        ReciteSession session = session("detonation", "needle");
        RecitePlan plan = press(session, 1, 10, new FixedWorld());
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(4, plan.manaSpent());
        assertEquals(6, plan.manaLeft());
        assertEquals(1, count(plan, ReciteEvent.Kind.FALTER_MANA));
        assertEquals(List.of("detonation", "needle"), unread(session), "both went to the read pile and the rest brought them back in order");
    }

    @Test
    void aSpentVerseIsSkipped() {
        Incantation incantation = tape(1, "ember", "needle");
        incantation.setUses(0, 0);
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(1, count(plan, ReciteEvent.Kind.FALTER_SPENT));
        assertEquals(4, plan.manaSpent(), "a spent verse is not billed");
    }

    @Test
    void chargesFollowTheBody() {
        Incantation alone = tape(1, "undying");
        ReciteSession session = ReciteSession.of(alone, VerseContent.CATALOGUE);
        press(session);
        session.writeBack(alone);
        assertEquals(3, alone.entries().get(0).usesRemaining(), "a modifier with nothing after it keeps its use");

        Incantation withBody = tape(1, "undying", "needle");
        session = ReciteSession.of(withBody, VerseContent.CATALOGUE);
        press(session);
        session.writeBack(withBody);
        assertEquals(2, withBody.entries().get(0).usesRemaining());

        Incantation limitedBody = tape(1, "ember");
        session = ReciteSession.of(limitedBody, VerseContent.CATALOGUE);
        press(session);
        session.writeBack(limitedBody);
        assertEquals(14, limitedBody.entries().get(0).usesRemaining());
    }

    @Test
    void costScaleMovesThePrice() {
        ReciteSession session = session("needle");
        RecitePlan plan = Reciter.recite(session, 1, PLENTY, 1.5D, new FixedWorld());
        assertEquals(6, plan.manaSpent());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ReciterTest"`
Expected: compilation failure (`Reciter`, `VerseContent` missing; `Recital` empty).

- [ ] **Step 3: Write the machine**

`Recital.java` (replaces the forward declaration whole):

```java
package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * One press of one incantation: {@code gun.lua}'s globals and helper functions as one object.
 *
 * <p>Verse actions are handed this and call the same helpers the Lua actions call:
 * {@link #drawActions}, {@link #addProjectile}, the three triggered adds, {@link #call},
 * {@link #checkRecursion}, {@link #setDrawDisabled}, the piles. The private half is
 * {@code draw_action}, {@code play_action}, {@code move_hand_to_discarded} and
 * {@code _handle_reload}. Everything past a cap frays rather than throws.
 */
public final class Recital {

    /** {@code create_shot}: a state and a draw budget, and the bodies added while it was current. */
    static final class Frame {
        final ShotState state = new ShotState();
        final List<ProjectilePlan> bodies = new ArrayList<>();
        final int draw;

        Frame(int draw) {
            this.draw = draw;
        }
    }

    private final ReciteSession session;
    private final ReciteWorld world;
    private final double costScale;
    private final int manaAtStart;
    private final List<ReciteEvent> events = new ArrayList<>();
    private final Deque<Frame> parents = new ArrayDeque<>();
    private final Deque<Verse> running = new ArrayDeque<>();
    private Frame frame;
    private int mana;
    private int rest;
    private boolean dontDraw;
    private boolean forceStopDraws;
    private boolean reloading;
    private boolean startReload;
    private boolean gotProjectiles;
    private int steps;
    private int bodies;
    private boolean frayed;

    Recital(ReciteSession session, ReciteWorld world, int mana, double costScale) {
        this.session = session;
        this.world = world;
        this.mana = mana;
        this.manaAtStart = mana;
        this.costScale = costScale;
    }

    // ---- what a verse may touch ------------------------------------------------------------

    /** {@code c}: the state of the shot being written. */
    public ShotState state() {
        return frame.state;
    }

    public List<VerseCard> deck() {
        return session.deck();
    }

    public List<VerseCard> hand() {
        return session.hand();
    }

    public List<VerseCard> discard() {
        return session.discard();
    }

    public ReciteWorld world() {
        return world;
    }

    public int mana() {
        return mana;
    }

    public void setMana(int mana) {
        this.mana = mana;
    }

    /** {@code current_reload_time}: the rest carried until the next rest happens. */
    public int rest() {
        return rest;
    }

    public void addRest(int ticks) {
        rest += ticks;
    }

    public void setRest(int ticks) {
        rest = ticks;
    }

    /** How many payloads deep the current shot is; 0 at the root. */
    public int depth() {
        return parents.size();
    }

    public boolean drawDisabled() {
        return dontDraw;
    }

    /** {@code dont_draw_actions}: a copy that must not draw sets this around the call. */
    public void setDrawDisabled(boolean disabled) {
        dontDraw = disabled;
    }

    public boolean frayed() {
        return frayed;
    }

    /** {@code draw_actions(n, true)}: what a modifier, multicast or control verse calls. */
    public void drawActions(int howMany) {
        drawActions(howMany, true);
    }

    /** {@code add_projectile}: one body stamped with the state as it stands now. */
    public void addProjectile(VersePrototype prototype) {
        if (!countBody()) {
            return;
        }
        frame.bodies.add(new ProjectilePlan(prototype, runningId(), frame.state.copy(), PayloadKind.NONE, 0, null));
    }

    public void addProjectileLatch(VersePrototype prototype, int draw) {
        addTriggered(prototype, PayloadKind.LATCH, 0, draw);
    }

    public void addProjectileFuse(VersePrototype prototype, int fuseTicks, int draw) {
        addTriggered(prototype, PayloadKind.FUSE, fuseTicks, draw);
    }

    public void addProjectileEpitaph(VersePrototype prototype, int draw) {
        addTriggered(prototype, PayloadKind.EPITAPH, 0, draw);
    }

    /** {@code data.action(rec, iter)}: run a verse's function without drawing it. Free. */
    public int call(Verse verse, int recursion, int iteration) {
        event(ReciteEvent.Kind.COPIED, verse.id());
        return run(verse, recursion, iteration);
    }

    /** {@code check_recursion}: -1 refuses; a non-recursive verse passes the level through. */
    public int checkRecursion(Verse verse, int level) {
        if (verse != null && verse.recursive()) {
            return level >= ReciteCaps.RECURSION_LIMIT ? -1 : level + 1;
        }
        return level;
    }

    public void discardTop(int count) {
        discardAt(0, count);
    }

    /** {@code table.remove(deck, index)} into {@code discarded}, {@code count} times. */
    public void discardAt(int index, int count) {
        for (int i = 0; i < count; i++) {
            List<VerseCard> deck = session.deck();
            if (index < 0 || index >= deck.size()) {
                return;
            }
            VerseCard card = deck.remove(index);
            session.discard().add(card);
            event(ReciteEvent.Kind.DISCARDED, card.id());
        }
    }

    /** RESET's body: everything to the read pile; the first time, rebuild the unread pile and forbid overrun. */
    public void refreshPage() {
        session.discard().addAll(session.hand());
        session.discard().addAll(session.deck());
        session.replaceHand();
        session.replaceDeck();
        if (!forceStopDraws) {
            forceStopDraws = true;
            session.moveDiscardToDeck();
            session.orderDeck();
        }
    }

    public void event(ReciteEvent.Kind kind, ResourceLocation verse) {
        events.add(new ReciteEvent(kind, verse, parents.size()));
    }

    // ---- the machine ------------------------------------------------------------------------

    /** {@code _start_shot} + {@code _draw_actions_for_shot(true)}. */
    RecitePlan recite(int breath) {
        Frame root = new Frame(breath);
        frame = root;
        if (session.firstShot()) {
            session.orderDeck();
            session.setRestCarry(0);
            session.firstShotDone();
        }
        rest = session.restCarry();
        drawActions(breath, false);
        moveHandToDiscard();
        boolean rests = false;
        int restOut = 0;
        // The Lua asks "not reloading" here too; that reload is done by the C++ side, which we lack.
        if (session.deck().isEmpty() || startReload) {
            session.moveDiscardToDeck();
            session.orderDeck();
            rests = true;
            restOut = rest;
            rest = 0;
            startReload = false;
            event(ReciteEvent.Kind.REST, null);
        }
        session.setRestCarry(rest);
        return new RecitePlan(new ShotPlan(root.bodies, root.state), root.state.beatTicks(), restOut, rests,
                manaAtStart - mana, mana, frayed, events);
    }

    /** {@code draw_actions}. */
    private void drawActions(int howMany, boolean instantReload) {
        if (dontDraw || frayed) {
            return;
        }
        frame.state.setDrawManyCount(howMany);
        for (int i = 0; i < howMany; i++) {
            boolean ok = drawAction(instantReload);
            if (!ok) {
                while (!session.deck().isEmpty() && !frayed) {
                    if (drawAction(instantReload)) {
                        break;
                    }
                }
            }
            if (reloading || frayed) {
                return;
            }
        }
    }

    /** {@code draw_action}: false means "skipped, try the next"; true means played or nothing to play. */
    private boolean drawAction(boolean instantReload) {
        if (frayed) {
            return true;
        }
        if (session.deck().isEmpty()) {
            if (instantReload && !forceStopDraws) {
                session.moveDiscardToDeck();
                session.orderDeck();
                startReload = true;
                event(ReciteEvent.Kind.OVERRUN, null);
            } else {
                reloading = true;
                return true;
            }
        }
        if (session.deck().isEmpty()) {
            return true;
        }
        VerseCard card = session.deck().remove(0);
        int price = price(card.verse());
        if (price > mana) {
            session.discard().add(card);
            event(ReciteEvent.Kind.FALTER_MANA, card.id());
            return false;
        }
        if (card.spent()) {
            session.discard().add(card);
            event(ReciteEvent.Kind.FALTER_SPENT, card.id());
            return false;
        }
        mana -= price;
        playCard(card);
        return true;
    }

    /** A refund is never scaled; a price is, and rounds to the nearest whole mana. */
    private int price(Verse verse) {
        return verse.mana() > 0 ? (int) Math.round(verse.mana() * costScale) : verse.mana();
    }

    /** {@code play_action}: into the hand, run at level 0, iteration 1; a body-type verse marks the press. */
    private void playCard(VerseCard card) {
        session.hand().add(card);
        event(ReciteEvent.Kind.PLAYED, card.id());
        run(card.verse(), 0, 1);
        if (card.type().spawnsBodies()) {
            gotProjectiles = true;
        }
    }

    private int run(Verse verse, int recursion, int iteration) {
        if (frayed) {
            return VerseAction.NONE;
        }
        if (++steps > ReciteCaps.MAX_STEPS) {
            fray();
            return VerseAction.NONE;
        }
        running.push(verse);
        try {
            return verse.action().run(this, recursion, iteration);
        } finally {
            running.pop();
        }
    }

    private ResourceLocation runningId() {
        Verse verse = running.peek();
        return verse == null ? null : verse.id();
    }

    /** {@code add_projectile_trigger_*}: a fresh shot drawn now, spawned later by the carrier. */
    private void addTriggered(VersePrototype prototype, PayloadKind kind, int fuseTicks, int draw) {
        if (!countBody()) {
            return;
        }
        ShotState stamped = frame.state.copy();
        ResourceLocation verse = runningId();
        if (parents.size() >= ReciteCaps.MAX_DEPTH) {
            fray();
            frame.bodies.add(new ProjectilePlan(prototype, verse, stamped, PayloadKind.NONE, 0, null));
            return;
        }
        Frame parent = frame;
        Frame child = new Frame(draw);
        parents.push(parent);
        frame = child;
        drawActions(draw, true);
        frame = parents.pop();
        parent.bodies.add(new ProjectilePlan(prototype, verse, stamped, kind, fuseTicks,
                new ShotPlan(child.bodies, child.state)));
    }

    private boolean countBody() {
        if (bodies >= ReciteCaps.MAX_BODIES) {
            fray();
            return false;
        }
        bodies++;
        return true;
    }

    private void fray() {
        if (!frayed) {
            frayed = true;
            event(ReciteEvent.Kind.FRAYED, null);
        }
    }

    /** {@code move_hand_to_discarded}: uses are spent by a press with a body, or by a control or utility verse; a spent card leaves the piles. */
    private void moveHandToDiscard() {
        for (VerseCard card : session.hand()) {
            if (gotProjectiles || card.type().spendsUseAlone()) {
                card.consumeUse();
            }
            if (!card.spent()) {
                session.discard().add(card);
            }
        }
        session.replaceHand();
    }
}
```

`Reciter.java`:

```java
package com.efkrdnz.magical.magic.incantation;

/** One press. Pure: the same session, breath, mana, scale and world give the same plan. */
public final class Reciter {

    private Reciter() {
    }

    public static RecitePlan recite(ReciteSession session, int breath, int mana, double costScale, ReciteWorld world) {
        return new Recital(session, world, mana, costScale).recite(breath);
    }
}
```

`VerseContent.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/** The real catalogue, filled by the six verse files in a fixed order so registration order is stable. */
public final class VerseContent {

    public static final VerseCatalogue CATALOGUE = build();

    private VerseContent() {
    }

    private static VerseCatalogue build() {
        VerseCatalogue catalogue = new VerseCatalogue();
        ProjectileVerses.register(catalogue);
        StaticVerses.register(catalogue);
        ModifierVerses.register(catalogue);
        MulticastVerses.register(catalogue);
        UtilityVerses.register(catalogue);
        ControlVerses.register(catalogue);
        return catalogue;
    }

    public static Verse get(ResourceLocation id) {
        return CATALOGUE.get(id);
    }
}
```

`ProjectileVerses.java` (the helpers every later projectile uses, plus the first three):

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Leaves: a verse that adds a body and draws nothing, unless it carries a payload. */
public final class ProjectileVerses {

    public static final ResourceLocation NEEDLE = VerseIds.of("needle");
    public static final ResourceLocation EMBER = VerseIds.of("ember");

    private ProjectileVerses() {
    }

    /** Deltas onto the state, then the body, exactly as Spark Bolt is written. */
    static Verse projectile(String path, int mana, int uses, VersePrototype prototype, int beat, int rest,
                            Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.PROJECTILE, mana, uses, prototype, 1, Declared.of(0, beat, rest), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            r.addRest(rest);
            effect.accept(s);
            r.addProjectile(prototype);
            return VerseAction.NONE;
        });
    }

    /** A body that carries a payload of {@code draw} verses, released by {@code kind}. */
    static Verse carrier(String path, int mana, int uses, VersePrototype prototype, int beat, PayloadKind kind,
                         int fuseTicks, int draw, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.PROJECTILE, mana, uses, prototype, 1, Declared.of(draw, beat, 0), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            effect.accept(s);
            switch (kind) {
                case LATCH -> r.addProjectileLatch(prototype, draw);
                case FUSE -> r.addProjectileFuse(prototype, fuseTicks, draw);
                case EPITAPH -> r.addProjectileEpitaph(prototype, draw);
                default -> r.addProjectile(prototype);
            }
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(projectile("needle", 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, 0, s -> {
            s.addSpread(-1.0D);
            s.addCrit(5.0D);
        }));
        c.register(projectile("ember", 14, 15, VersePrototypes.EMBER, 12, 0, s -> {
            s.addSpread(4.0D);
            s.addRecoil(20.0D);
            s.hitEffect(HitEffect.BURN);
        }));
    }
}
```

`StaticVerses.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Bodies that do not move: placed a block ahead of the hand, or at the release point inside a payload. */
public final class StaticVerses {

    public static final ResourceLocation DETONATION = VerseIds.of("detonation");

    private StaticVerses() {
    }

    static Verse stationary(String path, int mana, int uses, VersePrototype prototype, int beat, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.STATIC, mana, uses, prototype, 1, Declared.of(0, beat, 0), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            effect.accept(s);
            r.addProjectile(prototype);
            return VerseAction.NONE;
        });
    }

    static Verse carrier(String path, int mana, int uses, VersePrototype prototype, int beat, PayloadKind kind, int draw) {
        return Verse.of(path, VerseType.STATIC, mana, uses, prototype, 1, Declared.of(draw, beat, 0), (r, rec, it) -> {
            switch (kind) {
                case LATCH -> r.addProjectileLatch(prototype, draw);
                case FUSE -> r.addProjectileFuse(prototype, 0, draw);
                default -> r.addProjectileEpitaph(prototype, draw);
            }
            r.state().addBeat(beat);
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(stationary("detonation", 20, Verse.UNLIMITED, VersePrototypes.BURST, 1, s -> s.addScreenshake(1.0D)));
    }
}
```

`ModifierVerses.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Every modifier writes the state and draws one: {@code draw_actions(1, true)} is the whole of its grammar. */
public final class ModifierVerses {

    public static final ResourceLocation WEIGHT = VerseIds.of("weight");
    public static final ResourceLocation HASTE = VerseIds.of("haste");
    public static final ResourceLocation UNDYING = VerseIds.of("undying");

    private ModifierVerses() {
    }

    static Verse modifier(String path, int mana, int uses, int beat, int rest, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.MODIFIER, mana, uses, null, 1, Declared.of(1, beat, rest), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            r.addRest(rest);
            effect.accept(s);
            r.drawActions(1);
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(modifier("weight", 3, Verse.UNLIMITED, 2, 0, s -> {
            s.addDamage(2.5D);
            s.addRecoil(10.0D);
        }));
        c.register(modifier("haste", 2, Verse.UNLIMITED, 0, 0, s -> s.multiplySpeed(2.5D)));
        c.register(modifier("undying", 20, 3, 4, 0, s -> s.behaviour(Behaviour.UNDYING)));
    }
}
```

`MulticastVerses.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Draw k, then (for a formation) write the pattern: the Lua draws first and sets the pattern after. */
public final class MulticastVerses {

    public static final ResourceLocation COUPLET = VerseIds.of("couplet");

    private MulticastVerses() {
    }

    static Verse multicast(String path, int mana, int uses, int draw, Consumer<ShotState> after) {
        return Verse.of(path, VerseType.MULTICAST, mana, uses, null, 1, Declared.of(draw, 0, 0), (r, rec, it) -> {
            r.drawActions(draw);
            after.accept(r.state());
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(multicast("couplet", 0, Verse.UNLIMITED, 2, s -> { }));
    }
}
```

`UtilityVerses.java` and `ControlVerses.java` (empty until their tasks):

```java
package com.efkrdnz.magical.magic.incantation;

/** Cast-position verses, Fresh Page and Blood Toll. Filled by the Overrun and catalogue tasks. */
public final class UtilityVerses {

    private UtilityVerses() {
    }

    static void register(VerseCatalogue c) {
    }
}
```

```java
package com.efkrdnz.magical.magic.incantation;

/** The Recall family, the Refrains, the Imposes, the Clauses, the Wild verses. Filled by their tasks. */
public final class ControlVerses {

    private ControlVerses() {
    }

    static void register(VerseCatalogue c) {
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ReciterTest"`
Expected: 8 tests pass. Then run the whole package once: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.*"` — everything from Tasks 1–4 still green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation src/test/java/com/efkrdnz/magical/magic/incantation
git commit -m "feat: the Recital, a press of an incantation as gun.lua reads a wand" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 6: Overrun, the rest carry and Fresh Page

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/UtilityVerses.java` (add `fresh_page`)
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ModifierVerses.java` (add `second_wind`)
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/OverrunTest.java`

**Interfaces:**
- Consumes: `Recital.refreshPage()`, `Recital.addRest`, `ModifierVerses.modifier(...)` (Task 5).
- Produces: `UtilityVerses.FRESH_PAGE`, `ModifierVerses.SECOND_WIND` (ids).

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A draw that runs past the end of the unread pile reads the top of the incantation again (an
 * overrun) and the press ends with a rest; rest accumulates across presses until a rest happens;
 * Fresh Page rebuilds the unread pile from everything, forbids overrun for the rest of the press,
 * spares uses, and a second one in the same press forces a rest.
 */
class OverrunTest {

    @Test
    void theLastModifierOverruns() {
        ReciteSession session = session("needle", "weight");
        press(session);
        RecitePlan second = press(session);
        assertEquals(List.of("needle"), bodies(second.root()));
        assertEquals(2.5D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(1, count(second, ReciteEvent.Kind.OVERRUN));
        assertTrue(second.rests());
        assertEquals(List.of("needle", "weight"), unread(session));
    }

    @Test
    void restCarriesUntilTheRest() {
        ReciteSession session = session("second_wind", "needle", "needle");
        RecitePlan first = press(session);
        assertFalse(first.rests());
        assertEquals(-7, session.restCarry());
        RecitePlan second = press(session);
        assertTrue(second.rests());
        assertEquals(-7, second.restTicks());
        assertEquals(0, session.restCarry());
        assertEquals(1, second.cooldownTicks(), "the needle's beat; a negative rest never becomes a negative cooldown");
    }

    @Test
    void freshPageRebuildsTheUnreadPile() {
        ReciteSession session = session("weight", "fresh_page", "needle");
        RecitePlan plan = press(session);
        assertTrue(plan.bodies().isEmpty());
        assertFalse(plan.rests());
        assertEquals(List.of("weight", "fresh_page", "needle"), unread(session));
        assertEquals(-8, session.restCarry());
        assertEquals(2, count(plan, ReciteEvent.Kind.PLAYED));
    }

    @Test
    void freshPageForbidsOverrunForTheRestOfThePress() {
        ReciteSession session = session("couplet", "fresh_page", "weight");
        RecitePlan plan = press(session);
        assertEquals(0, count(plan, ReciteEvent.Kind.OVERRUN), "an empty pile after a Fresh Page does not wrap");
        assertTrue(plan.rests(), "a second Fresh Page in the same press forces a rest");
        assertEquals(-16, plan.restTicks());
    }

    @Test
    void freshPageSparesUses() {
        Incantation withPage = tape(2, "ember", "couplet", "needle", "fresh_page");
        ReciteSession session = ReciteSession.of(withPage, VerseContent.CATALOGUE);
        RecitePlan plan = press(session, 2, PLENTY, new FixedWorld());
        assertEquals(List.of("ember", "needle"), bodies(plan.root()));
        session.writeBack(withPage);
        assertEquals(15, withPage.entries().get(0).usesRemaining());

        Incantation without = tape(2, "ember", "couplet", "needle", "weight");
        session = ReciteSession.of(without, VerseContent.CATALOGUE);
        press(session, 2, PLENTY, new FixedWorld());
        session.writeBack(without);
        assertEquals(14, without.entries().get(0).usesRemaining());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.OverrunTest"`
Expected: the fixture assertion fails, `fresh_page` and `second_wind` are not in the catalogue.

- [ ] **Step 3: Register the two verses**

In `ModifierVerses.java` add the constant and the registration:

```java
    public static final ResourceLocation SECOND_WIND = VerseIds.of("second_wind");
```

```java
        c.register(modifier("second_wind", 5, Verse.UNLIMITED, -3, -7, s -> { }));
```

Replace `UtilityVerses.java` with:

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import net.minecraft.resources.ResourceLocation;

/** Cast-position verses, Fresh Page and Blood Toll. */
public final class UtilityVerses {

    public static final ResourceLocation FRESH_PAGE = VerseIds.of("fresh_page");

    private UtilityVerses() {
    }

    static void register(VerseCatalogue c) {
        // RESET: recharge -25 frames, hand and deck to discard, and the first time rebuild the deck and forbid wrapping.
        c.register(Verse.of("fresh_page", VerseType.UTILITY, 6, Verse.UNLIMITED, null, 1, Declared.of(0, 0, -8), (r, rec, it) -> {
            r.addRest(-8);
            r.refreshPage();
            return VerseAction.NONE;
        }).asRecursive());
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.OverrunTest"`
Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/UtilityVerses.java src/main/java/com/efkrdnz/magical/magic/incantation/ModifierVerses.java src/test/java/com/efkrdnz/magical/magic/incantation/OverrunTest.java
git commit -m "feat: overrun, the rest carry and Fresh Page" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Payloads: Latch, Fuse, Epitaph

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ProjectileVerses.java` (add the carriers and the orb family)
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/StaticVerses.java` (add `held_word`)
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/UtilityVerses.java` (add `far_word`)
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/PayloadTest.java`

**Interfaces:**
- Consumes: `ProjectileVerses.carrier(...)`, `StaticVerses.carrier(...)`, `Recital.addProjectileEpitaph` (Task 5).
- Produces: ids `needle_latch`, `needle_fuse`, `needle_twin_latch`, `orb`, `orb_latch`, `orb_fuse`, `orb_epitaph`, `held_word`, `far_word`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A payload is a new shot with a fresh state, drawn from the same pile at that moment: nothing set
 * before the carrier reaches it and nothing it sets leaks back out. Payloads nest, can overrun, and
 * past the depth cap a carrier is added bare and the recite frays.
 */
class PayloadTest {

    @Test
    void aPayloadHasItsOwnState() {
        ReciteSession session = session("needle_latch", "weight", "detonation", "haste", "needle");
        RecitePlan first = press(session);
        assertEquals(List.of("needle_latch"), bodies(first.root()));
        ProjectilePlan carrier = first.bodies().get(0);
        assertEquals(PayloadKind.LATCH, carrier.payloadKind());
        assertEquals(0.0D, carrier.stamped().damageAdd(), 1e-9);
        assertEquals(5.0D, carrier.stamped().critChance(), 1e-9);
        assertEquals(List.of("detonation"), bodies(carrier.payload()));
        assertEquals(2.5D, carrier.payload().bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(3, carrier.payload().state().beatTicks());
        assertEquals(1, first.beatTicks());
        assertEquals(29, first.manaSpent());
        assertEquals(List.of("haste", "needle"), unread(session));

        RecitePlan second = press(session);
        assertEquals(2.5D, second.bodies().get(0).stamped().speedMultiplier(), 1e-9);
        assertEquals(0.0D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
    }

    @Test
    void aPayloadInsideAPayload() {
        ReciteSession session = session("needle_latch", "needle_fuse", "weight", "needle");
        RecitePlan plan = press(session);
        ProjectilePlan outer = plan.bodies().get(0);
        ProjectilePlan inner = outer.payload().bodies().get(0);
        assertEquals("needle_fuse", inner.verse().getPath());
        assertEquals(PayloadKind.FUSE, inner.payloadKind());
        assertEquals(4, inner.fuseTicks());
        ProjectilePlan leaf = inner.payload().bodies().get(0);
        assertEquals("needle", leaf.verse().getPath());
        assertEquals(2.5D, leaf.stamped().damageAdd(), 1e-9);
        assertFalse(leaf.hasPayload());
        assertEquals(3, plan.root().countAll());
        assertTrue(plan.events().stream().anyMatch(e -> e.kind() == ReciteEvent.Kind.PLAYED && e.depth() == 2));
    }

    @Test
    void aPayloadOverrunsIntoTheReadPile() {
        ReciteSession session = session("needle", "needle_latch");
        press(session);
        RecitePlan second = press(session);
        assertEquals(List.of("needle"), bodies(second.bodies().get(0).payload()));
        assertEquals(1, count(second, ReciteEvent.Kind.OVERRUN));
        assertTrue(second.rests());
    }

    @Test
    void aTwinLatchDrawsTwo() {
        RecitePlan plan = press(session("needle_twin_latch", "needle", "needle", "needle"));
        assertEquals(List.of("needle", "needle"), bodies(plan.bodies().get(0).payload()));
    }

    @Test
    void anEpitaphCarrierIsStaticAndDrawsThree() {
        RecitePlan plan = press(session("held_word", "needle", "needle", "needle"));
        ProjectilePlan word = plan.bodies().get(0);
        assertEquals(PayloadKind.EPITAPH, word.payloadKind());
        assertTrue(word.prototype().isStatic());
        assertEquals(3, word.payload().bodies().size());
        assertEquals(3, plan.beatTicks());
    }

    @Test
    void farWordIsAUtilityThatCarries() {
        RecitePlan plan = press(session("far_word", "needle"));
        ProjectilePlan word = plan.bodies().get(0);
        assertEquals(VersePrototypes.WORD_FAR, word.prototype());
        assertEquals(PayloadKind.EPITAPH, word.payloadKind());
        assertEquals(List.of("needle"), bodies(word.payload()));
        assertEquals(-2, plan.beatTicks());
    }

    @Test
    void depthIsCapped() {
        RecitePlan plan = press(session("needle_latch", "needle_latch", "needle_latch", "needle_latch", "needle_latch", "needle_latch"));
        assertTrue(plan.frayed());
        assertEquals(5, count(plan, ReciteEvent.Kind.PLAYED));
        ProjectilePlan body = plan.bodies().get(0);
        int depth = 0;
        while (body.hasPayload()) {
            body = body.payload().bodies().get(0);
            depth++;
        }
        assertEquals(ReciteCaps.MAX_DEPTH, depth);
        assertEquals(PayloadKind.NONE, body.payloadKind(), "the carrier past the cap is added bare");
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.PayloadTest"`
Expected: the fixture assertion fails on the missing verses.

- [ ] **Step 3: Register the carriers**

In `ProjectileVerses.register` add, after `ember`:

```java
        c.register(carrier("needle_latch", 6, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.LATCH, 0, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("needle_fuse", 6, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.FUSE, 4, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("needle_twin_latch", 8, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.LATCH, 0, 2, s -> s.addCrit(5.0D)));
        c.register(projectile("orb", 7, Verse.UNLIMITED, VersePrototypes.ORB, 2, 0, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_latch", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.LATCH, 0, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_fuse", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.FUSE, 8, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_epitaph", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.EPITAPH, 0, 1, s -> s.addCrit(5.0D)));
```

In `StaticVerses.register` add:

```java
        c.register(carrier("held_word", 8, Verse.UNLIMITED, VersePrototypes.WORD_HELD, 3, PayloadKind.EPITAPH, 3));
```

In `UtilityVerses.register` add (and `import com.efkrdnz.magical.magic.incantation.Verse.Declared;` is already there):

```java
        // LONG_DISTANCE_CAST: an expiration-trigger utility, -5 frames.
        c.register(Verse.of("far_word", VerseType.UTILITY, 0, Verse.UNLIMITED, VersePrototypes.WORD_FAR, 1, Declared.of(1, -2, 0), (r, rec, it) -> {
            r.addProjectileEpitaph(VersePrototypes.WORD_FAR, 1);
            r.state().addBeat(-2);
            return VerseAction.NONE;
        }));
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.PayloadTest"`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/ProjectileVerses.java src/main/java/com/efkrdnz/magical/magic/incantation/StaticVerses.java src/main/java/com/efkrdnz/magical/magic/incantation/UtilityVerses.java src/test/java/com/efkrdnz/magical/magic/incantation/PayloadTest.java
git commit -m "feat: a payload is a fresh shot drawn now and released by a Latch, a Fuse or an Epitaph" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 8: Multicasts, formations and Epic

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/MulticastVerses.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/MulticastTest.java`

**Interfaces:**
- Consumes: `MulticastVerses.multicast(...)` (Task 5).
- Produces: ids `tercet`, `quatrain`, `octave`, `loose_couplet`, `loose_tercet`, `cleft`, `trident`, `mirror`, `column`, `pentacle`, `hexad`, `epic`; `MulticastVerses.EPIC`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A multicast draws k and nothing else; a formation also writes the pattern and takes spread off
 * the shot; a scatter adds spread; Epic draws whatever is left. Pattern and spread are read from the
 * shot's final state, which is why a formation may set them after the draw, as the Lua does.
 */
class MulticastTest {

    @Test
    void formationsSetThePatternOnTheFinalState() {
        RecitePlan plan = press(session("column", "needle", "needle", "needle"));
        assertEquals(3, plan.bodies().size());
        assertEquals(90.0D, plan.root().state().patternDegrees(), 1e-9);
        assertEquals(-11.0D, plan.root().state().spreadDegrees(), 1e-9);
        assertEquals(0.0D, plan.bodies().get(0).stamped().patternDegrees(), 1e-9, "a stamp is taken before the formation writes");
    }

    @Test
    void scattersAddSpread() {
        RecitePlan plan = press(session("loose_tercet", "needle", "needle", "needle"));
        assertEquals(3, plan.bodies().size());
        assertEquals(17.0D, plan.root().state().spreadDegrees(), 1e-9);
        assertEquals(0.0D, plan.root().state().patternDegrees(), 1e-9);
    }

    @Test
    void plainMulticastsOnlyDraw() {
        ReciteSession session = session("quatrain", "needle", "needle", "needle", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(4, plan.bodies().size());
        assertEquals(List.of("needle"), unread(session));
        assertEquals(0.0D, plan.root().state().patternDegrees(), 1e-9);
    }

    @Test
    void theRingOfFormationsIsComplete() {
        assertEquals(45.0D, patternOf("cleft", 2), 1e-9);
        assertEquals(20.0D, patternOf("trident", 3), 1e-9);
        assertEquals(180.0D, patternOf("mirror", 2), 1e-9);
        assertEquals(180.0D, patternOf("pentacle", 5), 1e-9);
        assertEquals(180.0D, patternOf("hexad", 6), 1e-9);
        assertEquals(8, press(session("octave", "needle", "needle", "needle", "needle", "needle", "needle", "needle", "needle")).bodies().size());
    }

    private static double patternOf(String formation, int draw) {
        String[] paths = new String[draw + 1];
        paths[0] = formation;
        for (int i = 1; i <= draw; i++) {
            paths[i] = "needle";
        }
        RecitePlan plan = press(session(paths));
        assertEquals(draw, plan.bodies().size(), formation);
        return plan.root().state().patternDegrees();
    }

    @Test
    void epicDrawsTheRest() {
        Incantation incantation = tape(1, "epic", "needle", "needle", "needle", "needle", "needle");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(5, plan.bodies().size());
        assertTrue(plan.rests());
        session.writeBack(incantation);
        assertEquals(9, incantation.entries().get(0).usesRemaining());
    }

    @Test
    void aMulticastRunningDryOverrunsOnce() {
        ReciteSession session = session("needle", "tercet");
        press(session);
        RecitePlan second = press(session);
        assertEquals(1, second.bodies().size(), "the one card that wrapped in");
        assertTrue(second.rests());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.MulticastTest"`
Expected: the fixture assertion fails on the missing verses.

- [ ] **Step 3: Register the multicasts**

Replace `MulticastVerses.register` with:

```java
    public static final ResourceLocation EPIC = VerseIds.of("epic");

    static void register(VerseCatalogue c) {
        c.register(multicast("couplet", 0, Verse.UNLIMITED, 2, s -> { }));
        c.register(multicast("tercet", 2, Verse.UNLIMITED, 3, s -> { }));
        c.register(multicast("quatrain", 4, Verse.UNLIMITED, 4, s -> { }));
        c.register(multicast("octave", 12, Verse.UNLIMITED, 8, s -> { }));
        c.register(multicast("loose_couplet", 0, Verse.UNLIMITED, 2, s -> s.addSpread(10.0D)));
        c.register(multicast("loose_tercet", 1, Verse.UNLIMITED, 3, s -> s.addSpread(20.0D)));
        c.register(multicast("cleft", 2, Verse.UNLIMITED, 2, s -> { s.setPattern(45.0D); s.addSpread(-8.0D); }));
        c.register(multicast("trident", 3, Verse.UNLIMITED, 3, s -> { s.setPattern(20.0D); s.addSpread(-5.0D); }));
        c.register(multicast("mirror", 0, Verse.UNLIMITED, 2, s -> { s.setPattern(180.0D); s.addSpread(-5.0D); }));
        c.register(multicast("column", 3, Verse.UNLIMITED, 3, s -> { s.setPattern(90.0D); s.addSpread(-8.0D); }));
        c.register(multicast("pentacle", 5, Verse.UNLIMITED, 5, s -> { s.setPattern(180.0D); s.addSpread(-12.0D); }));
        c.register(multicast("hexad", 6, Verse.UNLIMITED, 6, s -> { s.setPattern(180.0D); s.addSpread(-15.0D); }));
        // BURST_X: draw whatever is left in the unread pile.
        c.register(Verse.of("epic", VerseType.MULTICAST, 20, 10, null, 1, Declared.of(Declared.ALL, 0, 0), (r, rec, it) -> {
            r.drawActions(r.deck().size());
            return VerseAction.NONE;
        }));
    }
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.MulticastTest"`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/MulticastVerses.java src/test/java/com/efkrdnz/magical/magic/incantation/MulticastTest.java
git commit -m "feat: multicasts draw, formations fan, Epic draws the rest" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: The Recall family, Reprise, the Wild and Blind verses

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ProjectileVerses.java` (add `wild_bolt`)
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ModifierVerses.java` (add `wild_mark`)
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/RecallTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/WildTest.java`

**Interfaces:**
- Consumes: `Recital.call`, `checkRecursion`, `setDrawDisabled`, `world()`, the piles.
- Produces: `ControlVerses.control(path, mana, uses, declared, action)`, `ControlVerses.copyQuiet(r, verse, rec)`, `ControlVerses.wild(r, recursion, type)`, `ControlVerses.blindDraw(r, recursion, datasize)`; ids `recall_first`, `recall_last`, `recall_pair`, `recall_all`, `recall_modifiers`, `recall_projectiles`, `recall_statics`, `wild_recall`, `wild_verse`, `blind_draw`, `blind_trio`, `reprise`, `wild_bolt`, `wild_mark`; `ControlVerses.REPRISE`.

- [ ] **Step 1: Write the failing tests**

`RecallTest.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Copies are free: a Recall runs a card's function without drawing it, so no mana, no use, and it
 * works on a card at zero uses. The recursion limit is two. Reprise re-runs the hand, then draws.
 */
class RecallTest {

    @Test
    void recallFirstCopiesTheFirstReadCard() {
        ReciteSession session = session("needle", "weight", "recall_first", "needle");
        press(session);
        RecitePlan second = press(session);
        assertEquals(List.of("needle"), bodies(second.root()));
        assertEquals(2.5D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(15, second.manaSpent(), "weight and the recall; the copied needle is free");
        assertEquals(1, count(second, ReciteEvent.Kind.COPIED));
        assertEquals(List.of("needle"), unread(session));
    }

    @Test
    void recursionStopsAtTwo() {
        // Nothing read yet, so Recall First finds itself at the top of the hand: it runs three times
        // in all (played, copied at level 1, copied at level 2) and the fourth is refused.
        RecitePlan plan = press(session("recall_first", "needle"));
        assertEquals(2, count(plan, ReciteEvent.Kind.COPIED));
        assertEquals(15, plan.beatTicks());
        assertTrue(plan.bodies().isEmpty());
    }

    @Test
    void recallLastCopiesTheLastUnreadCard() {
        ReciteSession session = session("recall_last", "weight", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(List.of("weight", "needle"), unread(session));
        assertEquals(12, plan.manaSpent());
    }

    @Test
    void recallPairMemorisesBothBeforeRunningEither() {
        RecitePlan plan = press(session("recall_pair", "couplet", "needle", "needle"));
        // The copied Couplet draws both needles; the memorised second card, a needle, still runs after.
        assertEquals(3, plan.bodies().size());
        assertEquals(28, plan.manaSpent());
    }

    @Test
    void recallAllSweepsEverythingWithDrawDisabled() {
        ReciteSession session = session("needle", "recall_all", "weight", "needle");
        press(session);
        RecitePlan second = press(session);
        assertEquals(List.of("needle", "needle"), bodies(second.root()));
        assertEquals(0.0D, second.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(2.5D, second.bodies().get(1).stamped().damageAdd(), 1e-9);
        assertEquals(1, count(second, ReciteEvent.Kind.PLAYED));
        assertEquals(List.of("weight", "needle"), unread(session), "nothing was drawn");
        assertEquals(60, second.manaSpent());
    }

    @Test
    void recallAllNeverTurnsThePage() {
        ReciteSession session = session("recall_all", "fresh_page");
        press(session);
        assertEquals(List.of("fresh_page"), unread(session));
    }

    @Test
    void recallModifiersRestoresThenDrawsOne() {
        RecitePlan plan = press(session("recall_modifiers", "weight", "needle"));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(5.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "the copy and the draw both weighed in");
        assertEquals(20, plan.beatTicks(), "the copy's beat was put back; the drawn Weight's and the needle's were not");
        assertEquals(37, plan.manaSpent());
    }

    @Test
    void recallProjectilesSweepsBodiesAndDoesNotDraw() {
        ReciteSession session = session("recall_projectiles", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(2, plan.bodies().size());
        assertEquals(List.of("needle", "needle"), unread(session));
        assertEquals(17, plan.beatTicks());
    }

    @Test
    void recallStaticsSweepsThenDrawsOne() {
        RecitePlan plan = press(session("recall_statics", "detonation"));
        assertEquals(List.of("detonation", "detonation"), bodies(plan.root()));
        assertEquals(50, plan.manaSpent());
    }

    @Test
    void reprise() {
        RecitePlan plan = press(session("weight", "reprise", "needle"));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(5.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "Weight ran twice, the copy with draw enabled");
        assertEquals(1, count(plan, ReciteEvent.Kind.COPIED));
    }

    @Test
    void aCopyWorksAtZeroUses() {
        Incantation incantation = tape(1, "ember", "recall_first");
        incantation.setUses(0, 0);
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session, 1, PLENTY, new FixedWorld());
        assertEquals(List.of("ember"), bodies(plan.root()));
        assertEquals(12, plan.manaSpent());
    }
}
```

`WildTest.java`:

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The Wild verses pick with the world's random: from the known catalogue, from the known verses
 * of a type, from the unread and read piles (spending a use), or from the other incantations.
 * With a fixed world every pick is exact.
 */
class WildTest {

    private static final Verse NEEDLE = VerseContent.get(ProjectileVerses.NEEDLE);
    private static final Verse WEIGHT = VerseContent.get(ModifierVerses.WEIGHT);

    @Test
    void wildVersePicksAKnownVerse() {
        FixedWorld world = new FixedWorld().roll(0, 1);
        world.all = List.of(NEEDLE, WEIGHT);
        world.known = Set.of(WEIGHT.id());
        RecitePlan plan = press(session("wild_verse", "needle"), 1, PLENTY, world);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2.5D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "the first roll was unknown, the second was Weight");
        assertEquals(7, plan.manaSpent());
    }

    @Test
    void wildBoltPicksOnlyABody() {
        FixedWorld world = new FixedWorld().roll(1, 0);
        world.all = List.of(NEEDLE, WEIGHT);
        RecitePlan plan = press(session("wild_bolt"), 1, PLENTY, world);
        assertEquals(List.of("needle"), bodies(plan.root()));
    }

    @Test
    void wildMarkPicksOnlyAModifier() {
        FixedWorld world = new FixedWorld().roll(0, 1);
        world.all = List.of(NEEDLE, WEIGHT);
        RecitePlan plan = press(session("wild_mark", "needle"), 1, PLENTY, world);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2.5D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
    }

    @Test
    void blindDrawSpendsAUse() {
        Incantation incantation = tape(1, "blind_draw", "ember");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session, 1, PLENTY, new FixedWorld().roll(0));
        assertEquals(List.of("ember"), bodies(plan.root()));
        session.writeBack(incantation);
        assertEquals(14, incantation.entries().get(1).usesRemaining());
        assertEquals(6, plan.manaSpent());
    }

    @Test
    void blindTrioDrawsThree() {
        RecitePlan plan = press(session("blind_trio", "needle", "needle", "needle"), 1, PLENTY, new FixedWorld().roll(0, 1, 2));
        assertEquals(3, plan.bodies().size());
    }

    @Test
    void wildRecallReadsTheOtherIncantations() {
        FixedWorld world = new FixedWorld().roll(0);
        world.others = List.of(WEIGHT);
        RecitePlan plan = press(session("wild_recall", "needle"), 1, PLENTY, world);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2.5D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
    }

    @Test
    void wildRecallWithNothingElseJustDraws() {
        RecitePlan plan = press(session("wild_recall", "needle"));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.RecallTest" --tests "com.efkrdnz.magical.magic.incantation.WildTest"`
Expected: the fixture assertion fails on the missing verses.

- [ ] **Step 3: Write the control verses**

Replace `ControlVerses.java` with:

```java
package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The verses that touch the piles or call other verses: the Recall family (the Greek letters),
 * Reprise, the Wild and Blind verses, and, from their own tasks, the Refrains, the Imposes and the
 * Clauses. Each is a line-for-line port; the Lua card is named in the comment above it.
 */
public final class ControlVerses {

    public static final ResourceLocation REPRISE = VerseIds.of("reprise");

    private ControlVerses() {
    }

    static Verse control(String path, int mana, int uses, Declared declared, VerseAction action) {
        return Verse.of(path, VerseType.CONTROL, mana, uses, null, 1, declared, action);
    }

    static void register(VerseCatalogue c) {
        registerRecalls(c);
        registerWild(c);
    }

    // ---- the Recall family ---------------------------------------------------------------------

    private static void registerRecalls(VerseCatalogue c) {
        // ALPHA: the first card of the read pile, else of the hand, else of the unread pile.
        c.register(control("recall_first", 12, Verse.UNLIMITED, Declared.of(0, 5, 0), (r, recursion, it) -> {
            r.state().addBeat(5);
            VerseCard data = null;
            if (!r.discard().isEmpty()) {
                data = r.discard().get(0);
            } else if (!r.hand().isEmpty()) {
                data = r.hand().get(0);
            } else if (!r.deck().isEmpty()) {
                data = r.deck().get(0);
            }
            copy(r, data, recursion);
            return VerseAction.NONE;
        }).asRecursive());
        // GAMMA: the last card of the unread pile, else of the hand.
        c.register(control("recall_last", 12, Verse.UNLIMITED, Declared.of(0, 5, 0), (r, recursion, it) -> {
            r.state().addBeat(5);
            VerseCard data = null;
            if (!r.deck().isEmpty()) {
                data = r.deck().get(r.deck().size() - 1);
            } else if (!r.hand().isEmpty()) {
                data = r.hand().get(r.hand().size() - 1);
            }
            copy(r, data, recursion);
            return VerseAction.NONE;
        }).asRecursive());
        // TAU: deck[1] and deck[2], both looked up before either runs.
        c.register(control("recall_pair", 20, Verse.UNLIMITED, Declared.of(0, 12, 0), (r, recursion, it) -> {
            r.state().addBeat(12);
            List<VerseCard> deck = r.deck();
            VerseCard data1 = deck.isEmpty() ? null : deck.get(0);
            VerseCard data2 = deck.size() > 1 ? deck.get(1) : null;
            int rec1 = data1 == null ? recursion : r.checkRecursion(data1.verse(), recursion);
            int rec2 = data2 == null ? recursion : r.checkRecursion(data2.verse(), recursion);
            if (data1 != null && rec1 > -1) {
                r.call(data1.verse(), rec1, 1);
            }
            if (data2 != null && rec2 > -1) {
                r.call(data2.verse(), rec2, 1);
            }
            return VerseAction.NONE;
        }).asRecursive());
        // OMEGA: every card of read, the non-recursive of hand, every card of unread; quiet; never RESET.
        c.register(control("recall_all", 60, Verse.UNLIMITED, Declared.of(0, 17, 0), (r, recursion, it) -> {
            r.state().addBeat(17);
            List<VerseCard> discard = r.discard();
            for (int i = 0; i < discard.size(); i++) {
                VerseCard data = discard.get(i);
                int rec = r.checkRecursion(data.verse(), recursion);
                if (rec > -1 && !data.id().equals(UtilityVerses.FRESH_PAGE)) {
                    copyQuiet(r, data.verse(), rec);
                }
            }
            List<VerseCard> hand = r.hand();
            for (int i = 0; i < hand.size(); i++) {
                VerseCard data = hand.get(i);
                int rec = r.checkRecursion(data.verse(), recursion);
                if (!data.verse().recursive()) {
                    copyQuiet(r, data.verse(), rec);
                }
            }
            List<VerseCard> deck = r.deck();
            for (int i = 0; i < deck.size(); i++) {
                VerseCard data = deck.get(i);
                int rec = r.checkRecursion(data.verse(), recursion);
                if (rec > -1 && !data.id().equals(UtilityVerses.FRESH_PAGE)) {
                    copyQuiet(r, data.verse(), rec);
                }
            }
            return VerseAction.NONE;
        }).asRecursive());
        c.register(sweep("recall_modifiers", 30, 17, VerseType.MODIFIER, true));
        c.register(sweep("recall_projectiles", 30, 17, VerseType.PROJECTILE, false));
        c.register(sweep("recall_statics", 30, 10, VerseType.STATIC, true));
    }

    /** {@code data.action(rec)} behind {@code check_recursion}; nothing when there is no card. */
    private static void copy(Recital r, VerseCard data, int recursion) {
        if (data == null) {
            return;
        }
        int rec = r.checkRecursion(data.verse(), recursion);
        if (rec > -1) {
            r.call(data.verse(), rec, 1);
        }
    }

    /** A copy with {@code dont_draw_actions} set around it. */
    static void copyQuiet(Recital r, Verse verse, int rec) {
        r.setDrawDisabled(true);
        r.call(verse, rec, 1);
        r.setDrawDisabled(false);
    }

    /** MU / PHI / SIGMA: every card of one type in read, hand, unread, quiet; then beat, rest and mana put back. */
    private static Verse sweep(String path, int mana, int beat, VerseType type, boolean drawAfter) {
        return control(path, mana, Verse.UNLIMITED, Declared.of(drawAfter ? 1 : 0, beat, 0), (r, recursion, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            int beatBefore = s.beatTicks();
            int restBefore = r.rest();
            int manaBefore = r.mana();
            sweepPile(r, r.discard(), type, recursion);
            sweepPile(r, r.hand(), type, recursion);
            sweepPile(r, r.deck(), type, recursion);
            s.setBeat(beatBefore);
            r.setRest(restBefore);
            r.setMana(manaBefore);
            if (drawAfter) {
                r.drawActions(1);
            }
            return VerseAction.NONE;
        }).asRecursive();
    }

    private static void sweepPile(Recital r, List<VerseCard> pile, VerseType type, int recursion) {
        for (int i = 0; i < pile.size(); i++) {
            VerseCard data = pile.get(i);
            if (data.type() != type) {
                continue;
            }
            int rec = r.checkRecursion(data.verse(), recursion);
            if (rec > -1) {
                copyQuiet(r, data.verse(), rec);
            }
        }
    }

    // ---- the Wild and Blind verses, and Reprise ----------------------------------------------

    private static void registerWild(VerseCatalogue c) {
        // RANDOM_SPELL: any known verse, run with draw enabled.
        c.register(control("wild_verse", 3, Verse.UNLIMITED, Declared.NONE, (r, recursion, it) -> {
            wild(r, recursion, null);
            return VerseAction.NONE;
        }).asRecursive());
        // ZETA: a verse from another incantation, quiet, then draw one.
        c.register(control("wild_recall", 4, Verse.UNLIMITED, Declared.of(1, 0, 0), (r, recursion, it) -> {
            List<Verse> options = r.world().otherIncantationVerses();
            if (!options.isEmpty()) {
                Verse data = options.get(r.world().random(options.size()));
                int rec = r.checkRecursion(data, recursion);
                if (rec > -1) {
                    copyQuiet(r, data, rec);
                }
            }
            r.drawActions(1);
            return VerseAction.NONE;
        }).asRecursive());
        // DRAW_RANDOM.
        c.register(control("blind_draw", 6, Verse.UNLIMITED, Declared.NONE, (r, recursion, it) -> {
            blindDraw(r, recursion, r.deck().size() + r.discard().size());
            return VerseAction.NONE;
        }).asRecursive());
        // DRAW_3_RANDOM: three independent picks over the sizes as they were at the start.
        c.register(control("blind_trio", 12, Verse.UNLIMITED, Declared.NONE, (r, recursion, it) -> {
            int datasize = r.deck().size() + r.discard().size();
            for (int i = 0; i < 3; i++) {
                blindDraw(r, recursion, datasize);
            }
            return VerseAction.NONE;
        }).asRecursive());
        // DUPLICATE: every card in hand but itself, draw enabled, bounded by the hand as it was; then draw one.
        c.register(control("reprise", 45, Verse.UNLIMITED, Declared.of(1, 7, 7), (r, recursion, it) -> {
            List<VerseCard> hand = r.hand();
            int handCount = hand.size();
            for (int i = 0; i < handCount && i < hand.size(); i++) {
                VerseCard v = hand.get(i);
                int rec = r.checkRecursion(v.verse(), recursion);
                if (!v.id().equals(REPRISE) && rec > -1) {
                    r.call(v.verse(), rec, 1);
                }
            }
            r.state().addBeat(7);
            r.addRest(7);
            r.drawActions(1);
            return VerseAction.NONE;
        }).asRecursive());
    }

    /**
     * RANDOM_SPELL / RANDOM_PROJECTILE / RANDOM_MODIFIER: up to a hundred rolls for a known verse of
     * the type that the recursion limit allows, then the last roll runs regardless, as the Lua does.
     */
    static void wild(Recital r, int recursion, VerseType type) {
        List<Verse> all = r.world().allVerses();
        if (all.isEmpty()) {
            return;
        }
        Verse data = all.get(r.world().random(all.size()));
        int rec = r.checkRecursion(data, recursion);
        boolean usable = r.world().isKnown(data.id());
        int safety = 0;
        while (safety < 100 && ((type != null && data.type() != type) || rec == -1 || !usable)) {
            data = all.get(r.world().random(all.size()));
            rec = r.checkRecursion(data, recursion);
            usable = r.world().isKnown(data.id());
            safety++;
        }
        r.call(data, rec, 1);
    }

    /**
     * DRAW_RANDOM's body: one card from the unread and read piles laid end to end, walking on past
     * refused or spent cards; the copy runs with draw enabled and the card loses a use.
     */
    static void blindDraw(Recital r, int recursion, int datasize) {
        if (datasize <= 0) {
            return;
        }
        int rnd = r.world().random(datasize);
        VerseCard data = pick(r, rnd);
        if (data == null) {
            return;
        }
        int checks = 0;
        int rec = r.checkRecursion(data.verse(), recursion);
        while (data != null && (rec == -1 || data.spent()) && checks < datasize) {
            rnd = (rnd + 1) % datasize;
            checks++;
            data = pick(r, rnd);
            rec = data == null ? -1 : r.checkRecursion(data.verse(), recursion);
        }
        if (data != null && rec > -1 && !data.spent()) {
            r.call(data.verse(), rec, 1);
            data.consumeUse();
        }
    }

    private static VerseCard pick(Recital r, int index) {
        List<VerseCard> deck = r.deck();
        if (index < deck.size()) {
            return deck.get(index);
        }
        List<VerseCard> discard = r.discard();
        int rest = index - deck.size();
        return rest < discard.size() ? discard.get(rest) : null;
    }
}
```

In `ProjectileVerses.register` add:

```java
        // RANDOM_PROJECTILE: a known projectile verse, run in this one's place.
        c.register(Verse.of("wild_bolt", VerseType.PROJECTILE, 6, Verse.UNLIMITED, null, 1, Declared.NONE, (r, rec, it) -> {
            ControlVerses.wild(r, rec, VerseType.PROJECTILE);
            return VerseAction.NONE;
        }).asRecursive());
```

In `ModifierVerses.register` add:

```java
        // RANDOM_MODIFIER: a known modifier verse, which does the drawing.
        c.register(Verse.of("wild_mark", VerseType.MODIFIER, 6, Verse.UNLIMITED, null, 1, Declared.of(1, 0, 0), (r, rec, it) -> {
            ControlVerses.wild(r, rec, VerseType.MODIFIER);
            return VerseAction.NONE;
        }).asRecursive());
```

- [ ] **Step 4: Run them to see them pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.RecallTest" --tests "com.efkrdnz.magical.magic.incantation.WildTest"`
Expected: 18 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java src/main/java/com/efkrdnz/magical/magic/incantation/ProjectileVerses.java src/main/java/com/efkrdnz/magical/magic/incantation/ModifierVerses.java src/test/java/com/efkrdnz/magical/magic/incantation/RecallTest.java src/test/java/com/efkrdnz/magical/magic/incantation/WildTest.java
git commit -m "feat: copies are free; the Recall family, Reprise and the Wild verses" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 10: The Refrains

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/RefrainTest.java`

**Interfaces:**
- Consumes: `ControlVerses.control(...)`, `Recital.call`, `discardTop`, `rest()/setRest`, `VerseAction` return value.
- Produces: ids `refrain_2`, `refrain_3`, `refrain_4`, `refrain_10`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Refrain of N looks at the card {@code iteration} deep, runs it once quiet and N-1 times with
 * draw enabled at the next iteration, and only the outermost pays: it puts beat and rest back and
 * discards as many top cards as the chain went deep. Every Refrain stamps its penalty after its
 * copies, so it lands on later copies and on everything cast afterwards. The count collapses to one
 * past the iteration limit. One use of the target pays for all the copies.
 */
class RefrainTest {

    @Test
    void refrainCopiesTheNextVerse() {
        ReciteSession session = session("refrain_2", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("needle", "needle"), bodies(plan.root()));
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "the penalty comes after the copies");
        assertEquals(-1.0D, plan.root().state().damageAdd(), 1e-9);
        assertEquals(5.0D, plan.root().state().patternDegrees(), 1e-9);
        assertEquals(7, plan.beatTicks());
        assertEquals(10, plan.manaSpent());
        assertEquals(1, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests(), "the page ran dry");
        assertEquals(List.of("refrain_2", "needle"), unread(session), "and the rest rebuilt it in order");
    }

    @Test
    void nestedRefrainsMultiplyAndPenaliseLaterCopies() {
        ReciteSession session = session("refrain_2", "refrain_2", "needle");
        RecitePlan plan = press(session);
        assertEquals(4, plan.bodies().size());
        assertEquals(0.0D, plan.bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(0.0D, plan.bodies().get(1).stamped().damageAdd(), 1e-9);
        assertEquals(-1.0D, plan.bodies().get(2).stamped().damageAdd(), 1e-9);
        assertEquals(-1.0D, plan.bodies().get(3).stamped().damageAdd(), 1e-9);
        assertEquals(-3.0D, plan.root().state().damageAdd(), 1e-9);
        assertEquals(7, plan.beatTicks(), "only the outermost pays beat");
        assertEquals(10, plan.manaSpent(), "the inner Refrain was never drawn");
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests());
    }

    @Test
    void theCountCollapsesPastTheIterationLimit() {
        RecitePlan plan = press(session("refrain_2", "refrain_2", "refrain_2", "refrain_2", "refrain_2", "needle"));
        assertEquals(16, plan.bodies().size(), "2 x 2 x 2 x 2 x 1: the fifth Refrain is at iteration 5");
        assertEquals(5, count(plan, ReciteEvent.Kind.DISCARDED));
    }

    @Test
    void aRefrainOnAModifierDrawsForEachDrawingCopy() {
        ReciteSession session = session("refrain_2", "weight", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(7.5D, plan.bodies().get(0).stamped().damageAdd(), 1e-9, "quiet copy, drawing copy, then the drawn Weight itself");
        assertEquals(17, plan.manaSpent());
        assertTrue(plan.rests(), "the page ran dry");
        assertEquals(List.of("refrain_2", "weight", "needle", "needle"), unread(session), "and the rest rebuilt it in order");
    }

    @Test
    void oneUsePaysForAllTheCopies() {
        Incantation incantation = tape(1, "refrain_2", "ember");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(2, plan.bodies().size());
        session.writeBack(incantation);
        assertEquals(14, incantation.entries().get(1).usesRemaining());
    }

    @Test
    void refrainOfTenIsLimited() {
        Incantation incantation = tape(1, "refrain_10", "needle");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(10, plan.bodies().size());
        assertEquals(27, plan.beatTicks());
        session.writeBack(incantation);
        assertEquals(4, incantation.entries().get(0).usesRemaining());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.RefrainTest"`
Expected: the fixture assertion fails on the missing verses.

- [ ] **Step 3: Write the Refrains**

In `ControlVerses.register` add `registerRefrains(c);` after `registerWild(c);`, and add:

```java
    // ---- the Refrains ------------------------------------------------------------------------

    private static void registerRefrains(VerseCatalogue c) {
        // DIVIDE_2 / _3 / _4 / _10: count, the iteration the count collapses at, beat, rest, and the penalty.
        c.register(control("refrain_2", 10, Verse.UNLIMITED, Declared.of(0, 7, 0), refrain(2, 5, 7, 0, 1.0D, 1.0D)));
        c.register(control("refrain_3", 20, Verse.UNLIMITED, Declared.of(0, 10, 0), refrain(3, 4, 10, 0, 2.0D, 2.0D)));
        c.register(control("refrain_4", 30, Verse.UNLIMITED, Declared.of(0, 13, 0), refrain(4, 4, 13, 0, 3.0D, 4.0D)));
        c.register(control("refrain_10", 50, 5, Declared.of(0, 27, 7), refrain(10, 3, 27, 7, 7.5D, 8.0D)));
    }

    private static VerseAction refrain(int count, int collapseAt, int beat, int rest, double damagePenalty, double radiusPenalty) {
        return (r, recursion, iteration) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            r.addRest(rest);
            int iter = Math.max(1, iteration);
            int iterMax = iter;
            List<VerseCard> deck = r.deck();
            VerseCard data = deck.size() >= iter ? deck.get(iter - 1) : null;
            int copies = iter >= collapseAt ? 1 : count;
            int rec = data == null ? recursion : r.checkRecursion(data.verse(), recursion);
            if (data != null && rec > -1 && !data.spent()) {
                int beatBefore = s.beatTicks();
                int restBefore = r.rest();
                for (int i = 1; i <= copies; i++) {
                    if (i == 1) {
                        r.setDrawDisabled(true);
                    }
                    int imax = r.call(data.verse(), rec, iter + 1);
                    r.setDrawDisabled(false);
                    if (imax != VerseAction.NONE) {
                        iterMax = imax;
                    }
                }
                data.consumeUse();
                if (iter == 1) {
                    s.setBeat(beatBefore);
                    r.setRest(restBefore);
                    r.discardTop(iterMax);
                }
            }
            s.addDamage(-damagePenalty);
            s.addExplosionRadius(-radiusPenalty);
            s.setPattern(5.0D);
            return iterMax;
        };
    }
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.RefrainTest"`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java src/test/java/com/efkrdnz/magical/magic/incantation/RefrainTest.java
git commit -m "feat: a Refrain copies the card it points at and only the outermost pays" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: The Imposes

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/ImposeTest.java`

**Interfaces:**
- Consumes: `VerseType.imposeScans/payloadWorthy`, `Verse.hasPrototype/bodies`, `Recital.addProjectileLatch/Fuse/Epitaph`, `discardTop`.
- Produces: ids `impose_latch`, `impose_fuse`, `impose_epitaph`; `ControlVerses.isImpose(Verse)`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An Impose scans forward over modifier, passive, control and multicast verses, running each
 * modifier it passes quietly and for free, stops at the first verse with a prototype, discards the
 * scanned verses and the target, and spawns the target as a carrier if any payload-worthy verse is
 * left anywhere in the unread pile; otherwise it runs the target quietly. The target loses a use;
 * the modifiers passed do not. A verse without a prototype ends it with nothing done.
 */
class ImposeTest {

    @Test
    void imposeLatchTurnsTheNextBodyIntoACarrier() {
        ReciteSession session = session("impose_latch", "weight", "needle", "needle");
        RecitePlan plan = press(session);
        assertEquals(1, plan.bodies().size());
        ProjectilePlan carrier = plan.bodies().get(0);
        assertEquals(VersePrototypes.NEEDLE, carrier.prototype());
        assertEquals(PayloadKind.LATCH, carrier.payloadKind());
        assertEquals(2.5D, carrier.stamped().damageAdd(), 1e-9, "the passed modifier lands on the carrier");
        assertEquals(List.of("needle"), bodies(carrier.payload()));
        assertEquals(0.0D, carrier.payload().bodies().get(0).stamped().damageAdd(), 1e-9);
        assertEquals(8, plan.manaSpent(), "the Impose and the payload needle; the passed Weight and the target are free");
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests(), "the page ran dry");
        assertEquals(List.of("impose_latch", "weight", "needle", "needle"), unread(session), "and the rest rebuilt it in order");
    }

    @Test
    void imposeFuseAndEpitaphUseTheirKinds() {
        ProjectilePlan fused = press(session("impose_fuse", "needle", "needle")).bodies().get(0);
        assertEquals(PayloadKind.FUSE, fused.payloadKind());
        assertEquals(7, fused.fuseTicks());
        ProjectilePlan epitaph = press(session("impose_epitaph", "needle", "needle")).bodies().get(0);
        assertEquals(PayloadKind.EPITAPH, epitaph.payloadKind());
    }

    @Test
    void withNoPayloadWorthyVerseLeftTheTargetRunsQuietly() {
        RecitePlan plan = press(session("impose_latch", "needle"));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(PayloadKind.NONE, plan.bodies().get(0).payloadKind());
        assertEquals(4, plan.manaSpent());
    }

    @Test
    void aVerseWithoutAPrototypeEndsItWithNothingDone() {
        ReciteSession session = session("impose_latch", "fresh_page", "needle");
        RecitePlan plan = press(session);
        assertTrue(plan.bodies().isEmpty());
        assertEquals(1, count(plan, ReciteEvent.Kind.PLAYED));
        assertEquals(List.of("fresh_page", "needle"), unread(session));
    }

    @Test
    void theTargetLosesAUseThePassedModifiersDoNot() {
        Incantation incantation = tape(1, "impose_latch", "undying", "ember", "needle");
        ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
        RecitePlan plan = press(session);
        assertEquals(VersePrototypes.EMBER, plan.bodies().get(0).prototype());
        assertTrue(plan.bodies().get(0).stamped().has(Behaviour.UNDYING));
        session.writeBack(incantation);
        assertEquals(3, incantation.entries().get(1).usesRemaining());
        assertEquals(14, incantation.entries().get(2).usesRemaining());
    }

    @Test
    void anImposeIsScannedOverButNeverRun() {
        ReciteSession session = session("impose_latch", "impose_fuse", "needle", "needle");
        RecitePlan plan = press(session);
        ProjectilePlan carrier = plan.bodies().get(0);
        assertEquals(PayloadKind.LATCH, carrier.payloadKind());
        assertEquals(List.of("needle"), bodies(carrier.payload()));
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests(), "the page ran dry");
        assertEquals(List.of("impose_latch", "impose_fuse", "needle", "needle"), unread(session), "and the rest rebuilt it in order");
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ImposeTest"`
Expected: the fixture assertion fails on the missing verses.

- [ ] **Step 3: Write the Imposes**

In `ControlVerses.register` add `registerImposes(c);`, and add:

```java
    public static final ResourceLocation IMPOSE_LATCH = VerseIds.of("impose_latch");
    public static final ResourceLocation IMPOSE_FUSE = VerseIds.of("impose_fuse");
    public static final ResourceLocation IMPOSE_EPITAPH = VerseIds.of("impose_epitaph");

    static boolean isImpose(Verse verse) {
        return verse.id().equals(IMPOSE_LATCH) || verse.id().equals(IMPOSE_FUSE) || verse.id().equals(IMPOSE_EPITAPH);
    }

    // ---- the Imposes -------------------------------------------------------------------------

    private static void registerImposes(VerseCatalogue c) {
        // ADD_TRIGGER / ADD_TIMER (20 frames) / ADD_DEATH_TRIGGER.
        c.register(control("impose_latch", 4, Verse.UNLIMITED, Declared.of(1, 0, 0), impose(PayloadKind.LATCH, 0)));
        c.register(control("impose_fuse", 6, Verse.UNLIMITED, Declared.of(1, 0, 0), impose(PayloadKind.FUSE, 7)));
        c.register(control("impose_epitaph", 6, Verse.UNLIMITED, Declared.of(1, 0, 0), impose(PayloadKind.EPITAPH, 0)));
    }

    private static VerseAction impose(PayloadKind kind, int fuseTicks) {
        return (r, recursion, iteration) -> {
            List<VerseCard> deck = r.deck();
            if (deck.isEmpty()) {
                return VerseAction.NONE;
            }
            int howMany = 1;
            VerseCard data = deck.get(0);
            while (deck.size() >= howMany && data != null && data.type().imposeScans()) {
                if (!data.spent() && !isImpose(data.verse()) && data.type() == VerseType.MODIFIER) {
                    copyQuiet(r, data.verse(), 0);
                }
                howMany++;
                data = deck.size() >= howMany ? deck.get(howMany - 1) : null;
            }
            if (data == null || !data.verse().hasPrototype() || data.spent()) {
                return VerseAction.NONE;
            }
            VersePrototype target = data.verse().prototype();
            int bodies = data.verse().bodies();
            r.discardTop(howMany);
            boolean valid = false;
            for (VerseCard check : deck) {
                if (check.type().payloadWorthy()) {
                    valid = true;
                    break;
                }
            }
            data.consumeUse();
            if (valid) {
                for (int i = 0; i < bodies; i++) {
                    switch (kind) {
                        case LATCH -> r.addProjectileLatch(target, 1);
                        case FUSE -> r.addProjectileFuse(target, fuseTicks, 1);
                        default -> r.addProjectileEpitaph(target, 1);
                    }
                }
            } else {
                copyQuiet(r, data.verse(), 0);
            }
            return VerseAction.NONE;
        };
    }
```

Note `for (VerseCard check : deck)` reads the live unread pile after the discard, exactly as the Lua's `for i=1,#deck` does.

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ImposeTest"`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java src/test/java/com/efkrdnz/magical/magic/incantation/ImposeTest.java
git commit -m "feat: an Impose makes the next body a carrier, and the modifiers it passes ride along free" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 12: The Clauses

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ProjectileVerses.java` (add `balm_dart`)
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/ClauseTest.java`

**Interfaces:**
- Consumes: `ReciteWorld` queries, `Recital.discardTop/discardAt`.
- Produces: ids `clause_outnumbered`, `clause_crowded`, `clause_wounded`, `clause_every_other`, `otherwise`, `end_clause`, `balm_dart`; `ControlVerses.OTHERWISE`, `END_CLAUSE`, `isClause(Verse)`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.unread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Clause branches by discarding. Failing: from the top through the first Otherwise, else through
 * the End Clause, else just the next verse. Passing with an Otherwise: from the Otherwise through the
 * End Clause; with no End Clause only the Otherwise itself; with another Clause before any End,
 * from the Otherwise to the end of the pile. The scan stops at the next Clause. Then draw one.
 */
class ClauseTest {

    private static FixedWorld atHealth(double fraction) {
        FixedWorld world = new FixedWorld();
        world.health = fraction;
        return world;
    }

    @Test
    void aFailingClauseDiscardsThroughTheOtherwise() {
        ReciteSession session = session("clause_wounded", "balm_dart", "otherwise", "needle", "end_clause");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(1.0D));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
        assertEquals(List.of("end_clause"), unread(session));
    }

    @Test
    void aPassingClauseDiscardsFromTheOtherwiseThroughTheEnd() {
        ReciteSession session = session("clause_wounded", "balm_dart", "otherwise", "needle", "end_clause");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(0.2D));
        assertEquals(List.of("balm_dart"), bodies(plan.root()));
        assertEquals(3, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests());
    }

    @Test
    void aPassingClauseWithoutAnEndDiscardsOnlyTheOtherwise() {
        ReciteSession session = session("clause_wounded", "balm_dart", "otherwise", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(0.2D));
        assertEquals(List.of("balm_dart"), bodies(plan.root()));
        assertEquals(1, count(plan, ReciteEvent.Kind.DISCARDED));
        assertEquals(List.of("needle"), unread(session));
    }

    @Test
    void aFailingClauseWithNoMarkSkipsOne() {
        ReciteSession session = session("clause_wounded", "balm_dart", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(1.0D));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(1, count(plan, ReciteEvent.Kind.DISCARDED));
    }

    @Test
    void aFailingClauseWithAnEndButNoOtherwiseDiscardsThroughTheEnd() {
        ReciteSession session = session("clause_wounded", "balm_dart", "end_clause", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(1.0D));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED));
    }

    @Test
    void theScanStopsAtTheNextClause() {
        ReciteSession session = session("clause_wounded", "balm_dart", "clause_outnumbered", "otherwise", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(1.0D));
        assertEquals(List.of("needle"), bodies(plan.root()));
        assertEquals(2, count(plan, ReciteEvent.Kind.DISCARDED), "the first Clause skipped one, the second skipped through its Otherwise");
        assertEquals(3, count(plan, ReciteEvent.Kind.PLAYED));
    }

    @Test
    void aPassingClauseWithALaterClauseDiscardsToTheEndOfThePile() {
        ReciteSession session = session("clause_wounded", "balm_dart", "otherwise", "needle", "clause_outnumbered", "needle");
        RecitePlan plan = press(session, 1, PLENTY, atHealth(0.2D));
        assertEquals(List.of("balm_dart"), bodies(plan.root()));
        assertEquals(4, count(plan, ReciteEvent.Kind.DISCARDED));
        assertTrue(plan.rests());
    }

    @Test
    void everyOtherAlternatesOnTheSharedToggle() {
        FixedWorld world = new FixedWorld();
        ReciteSession session = session("clause_every_other", "needle", "ember");
        assertEquals(List.of("needle"), bodies(press(session, 1, PLENTY, world).root()));
        assertEquals(List.of("ember"), bodies(press(session, 1, PLENTY, world).root()));
        assertEquals(List.of("ember"), bodies(press(session, 1, PLENTY, world).root()), "the second reading skips");
        assertTrue(world.everyOtherSkip == false, "flipped twice");
    }

    @Test
    void outnumberedAndCrowdedReadTheWorld() {
        FixedWorld crowd = new FixedWorld();
        crowd.enemies = 6;
        assertEquals(List.of("needle"), bodies(press(session("clause_outnumbered", "needle", "otherwise", "ember"), 1, PLENTY, crowd).root()));
        crowd.enemies = 5;
        assertEquals(List.of("ember"), bodies(press(session("clause_outnumbered", "needle", "otherwise", "ember"), 1, PLENTY, crowd).root()));
        FixedWorld sky = new FixedWorld();
        sky.projectiles = 12;
        assertEquals(List.of("needle"), bodies(press(session("clause_crowded", "needle", "otherwise", "ember"), 1, PLENTY, sky).root()));
        sky.projectiles = 11;
        assertEquals(List.of("ember"), bodies(press(session("clause_crowded", "needle", "otherwise", "ember"), 1, PLENTY, sky).root()));
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ClauseTest"`
Expected: the fixture assertion fails on the missing verses.

- [ ] **Step 3: Write the Clauses**

In `ProjectileVerses.register` add:

```java
        c.register(projectile("balm_dart", 8, 20, VersePrototypes.DART, 1, 0, s -> s.addSpread(2.0D)));
```

In `ControlVerses` add `import java.util.function.Predicate;`, the constants, `registerClauses(c);` in `register`, and:

```java
    public static final ResourceLocation OTHERWISE = VerseIds.of("otherwise");
    public static final ResourceLocation END_CLAUSE = VerseIds.of("end_clause");

    /** {@code string.sub(v.id, 1, 3) == "IF_"} and not the two markers. */
    static boolean isClause(Verse verse) {
        return verse.id().getPath().startsWith("clause_");
    }

    // ---- the Clauses -------------------------------------------------------------------------

    private static void registerClauses(VerseCatalogue c) {
        // IF_ENEMY (15 within 240 px), IF_PROJECTILE (20 within 160 px), IF_HP (below a quarter), IF_HALF.
        c.register(clause("clause_outnumbered", w -> w.enemiesWithin(16.0D) >= 6));
        c.register(clause("clause_crowded", w -> w.projectilesWithin(16.0D) >= 12));
        c.register(clause("clause_wounded", w -> w.healthFraction() <= 0.25D));
        c.register(clause("clause_every_other", w -> !w.everyOtherSkipAndFlip()));
        // IF_ELSE and IF_END are markers: drawn transparently, they draw one.
        c.register(control("otherwise", 0, Verse.UNLIMITED, Declared.of(1, 0, 0), (r, rec, it) -> {
            r.drawActions(1);
            return VerseAction.NONE;
        }));
        c.register(control("end_clause", 0, Verse.UNLIMITED, Declared.of(1, 0, 0), (r, rec, it) -> {
            r.drawActions(1);
            return VerseAction.NONE;
        }));
    }

    /**
     * The Requirement body, with the Lua's one-based envelope kept in the arithmetic: the condition
     * is asked first (Every Other flips even on an empty pile), then the scan, then the discard.
     */
    private static Verse clause(String path, Predicate<ReciteWorld> passes) {
        return control(path, 0, Verse.UNLIMITED, Declared.of(1, 0, 0), (r, recursion, it) -> {
            boolean doskip = !passes.test(r.world());
            List<VerseCard> deck = r.deck();
            int endpoint = -1;
            int elsepoint = -1;
            for (int i = 0; i < deck.size(); i++) {
                Verse v = deck.get(i).verse();
                if (isClause(v)) {
                    endpoint = -1;
                    break;
                }
                if (v.id().equals(OTHERWISE)) {
                    endpoint = i + 1;
                    elsepoint = i + 1;
                }
                if (v.id().equals(END_CLAUSE)) {
                    endpoint = i + 1;
                    break;
                }
            }
            if (!deck.isEmpty()) {
                if (doskip) {
                    int envelopeMax = 1;
                    if (elsepoint > 0) {
                        envelopeMax = elsepoint;
                    } else if (endpoint > 0) {
                        envelopeMax = endpoint;
                    }
                    r.discardTop(envelopeMax);
                } else if (elsepoint > 0) {
                    int envelopeMax = endpoint > 0 ? endpoint : deck.size();
                    r.discardAt(elsepoint - 1, envelopeMax - elsepoint + 1);
                }
            }
            r.drawActions(1);
            return VerseAction.NONE;
        });
    }
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.ClauseTest"`
Expected: 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation/ControlVerses.java src/main/java/com/efkrdnz/magical/magic/incantation/ProjectileVerses.java src/test/java/com/efkrdnz/magical/magic/incantation/ClauseTest.java
git commit -m "feat: a Clause branches by discarding, and the scan stops at the next Clause" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 13: The caps, and determinism

**Files:**
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/CapsTest.java`

**Interfaces:**
- Consumes: everything so far. No production code should change; if a test here fails, the fix belongs in `Recital` and must keep every earlier test green.

- [ ] **Step 1: Write the test**

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.bodies;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.count;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.session;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Noita runs its draw on one player's frame; this runs on a server tick for every wielder. Past a
 * cap the recite frays, deterministically: drawing stops, what was planned still comes back, and
 * the same tape, mana, breath and world always give the same plan and the same events.
 */
class CapsTest {

    @Test
    void theBodyCapFraysAndKeepsWhatWasPlanned() {
        RecitePlan plan = press(session("refrain_10", "refrain_10", "needle"));
        assertTrue(plan.frayed());
        assertEquals(ReciteCaps.MAX_BODIES, plan.bodies().size());
        assertEquals(1, count(plan, ReciteEvent.Kind.FRAYED));
    }

    @Test
    void theStepCapFrays() {
        // A verse that calls itself without being flagged recursive: the recursion limit never sees
        // it, so the step cap is the only thing that stops it. Wild Verse reaches it through the world.
        Verse[] loop = new Verse[1];
        loop[0] = Verse.of("loop", VerseType.CONTROL, 0, Verse.UNLIMITED, null, 1, Verse.Declared.NONE,
                (r, rec, it) -> r.call(loop[0], rec, 1));
        FixedWorld world = new FixedWorld();
        world.all = List.of(loop[0]);
        RecitePlan plan = press(session("wild_verse"), 1, PLENTY, world);
        assertTrue(plan.frayed());
        assertTrue(plan.bodies().isEmpty());
        assertEquals(1, count(plan, ReciteEvent.Kind.FRAYED));
    }

    @Test
    void aRunawayChainFrays() {
        // 800 copies of one card, the wiki's ceiling: the body cap trips first and the plan keeps 64.
        RecitePlan plan = press(session("refrain_10", "refrain_10", "refrain_4", "refrain_2", "needle"));
        assertTrue(plan.frayed());
        assertEquals(ReciteCaps.MAX_BODIES, plan.bodies().size());
    }

    @Test
    void anOrdinaryTapeNeverFrays() {
        RecitePlan plan = press(session("octave", "needle", "needle", "needle", "needle", "needle", "needle", "needle", "needle"));
        assertFalse(plan.frayed());
        assertEquals(8, plan.bodies().size());
    }

    @Test
    void theSamePressGivesTheSamePlan() {
        String[] tape = {"refrain_2", "impose_latch", "weight", "needle_latch", "couplet", "needle", "wild_verse", "clause_wounded", "needle", "otherwise", "ember"};
        RecitePlan first = press(session(tape), 2, 60, new FixedWorld().roll(3, 1, 4, 1, 5));
        RecitePlan second = press(session(tape), 2, 60, new FixedWorld().roll(3, 1, 4, 1, 5));
        assertEquals(first.events(), second.events());
        assertEquals(bodies(first.root()), bodies(second.root()));
        assertEquals(first.manaSpent(), second.manaSpent());
        assertEquals(first.beatTicks(), second.beatTicks());
        assertEquals(first.root().countAll(), second.root().countAll());
        for (int i = 0; i < first.bodies().size(); i++) {
            assertEquals(first.bodies().get(i).stamped().damageAdd(), second.bodies().get(i).stamped().damageAdd(), 1e-9);
            assertEquals(first.bodies().get(i).payloadKind(), second.bodies().get(i).payloadKind());
        }
    }

    @Test
    void breathIsTheRootBudget() {
        assertEquals(1, press(session("needle", "needle", "needle"), 1, PLENTY, new FixedWorld()).bodies().size());
        assertEquals(3, press(session("needle", "needle", "needle"), 3, PLENTY, new FixedWorld()).bodies().size());
        RecitePlan more = press(session("needle", "needle"), ReciteCaps.MAX_BREATH, PLENTY, new FixedWorld());
        assertEquals(2, more.bodies().size());
        assertTrue(more.rests());
    }
}
```

- [ ] **Step 2: Run it**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.CapsTest"`
Expected: 6 tests pass with no production change. If `theStepCapFrays` reports no fray, the step count is not reaching `run` for copies — check that `Recital.call` goes through `run`, not straight to the action.

- [ ] **Step 3: Run the whole package**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.*"`
Expected: every test from Tasks 1–13 green.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/efkrdnz/magical/magic/incantation/CapsTest.java
git commit -m "test: the caps fray deterministically and a plan is a pure function of its inputs" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 14: The rest of the catalogue, and the declared numbers held to the machine

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ProjectileVerses.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/StaticVerses.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/ModifierVerses.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/incantation/UtilityVerses.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/incantation/VerseContentTest.java`

**Interfaces:**
- Consumes: the helpers of Task 5.
- Produces: the full catalogue of 100 verses (15 projectiles, 7 statics, 35 modifiers, 13 multicasts, 5 utilities, 25 control), the ids `UtilityVerses.BLOOD_TOLL`, `UtilityVerses.NEAR_WORD`, `UtilityVerses.STEP_WORD`.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.magic.incantation;

import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.PLENTY;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.press;
import static com.efkrdnz.magical.magic.incantation.ReciteFixtures.tape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The catalogue is data, and its declared numbers are what a tooltip will show, so they are held
 * to what the machine does: every non-recursive projectile, static, modifier, multicast and utility
 * verse is run at the top of a tape of needles, and the cards it drew, the beat it added at the
 * root and the rest it added must be exactly what it declared. Control verses are exempt because
 * their numbers depend on the piles; the rule tests pin those.
 */
class VerseContentTest {

    @Test
    void theCatalogueIsFull() {
        VerseCatalogue c = VerseContent.CATALOGUE;
        assertEquals(15, c.ofType(VerseType.PROJECTILE).size());
        assertEquals(7, c.ofType(VerseType.STATIC).size());
        assertEquals(35, c.ofType(VerseType.MODIFIER).size());
        assertEquals(13, c.ofType(VerseType.MULTICAST).size());
        assertEquals(5, c.ofType(VerseType.UTILITY).size());
        assertEquals(25, c.ofType(VerseType.CONTROL).size());
        assertEquals(0, c.ofType(VerseType.MATERIAL).size());
        assertEquals(0, c.ofType(VerseType.PASSIVE).size());
        assertEquals(100, c.size());
    }

    @Test
    void everyPrototypeIsInTheTable() {
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            if (verse.hasPrototype()) {
                assertSame(verse.prototype(), VersePrototypes.byId(verse.prototype().id()), verse.id() + " names a body the table does not hold");
            }
        }
    }

    @Test
    void declaredNumbersAreEnacted() {
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            if (verse.recursive() || verse.type() == VerseType.CONTROL || verse.type() == VerseType.MATERIAL || verse.type() == VerseType.PASSIVE) {
                continue;
            }
            int draw = verse.declared().draw() == Verse.Declared.ALL ? 4 : verse.declared().draw();
            List<String> paths = new ArrayList<>();
            paths.add(verse.path());
            for (int i = 0; i < draw; i++) {
                paths.add("needle");
            }
            Incantation incantation = tape(1, paths.toArray(new String[0]));
            ReciteSession session = ReciteSession.of(incantation, VerseContent.CATALOGUE);
            RecitePlan plan = press(session, 1, PLENTY, new FixedWorld());
            List<ReciteEvent> played = plan.events().stream().filter(e -> e.kind() == ReciteEvent.Kind.PLAYED).toList();
            assertEquals(draw, played.size() - 1, verse.id() + " drew");
            long rootNeedles = played.subList(1, played.size()).stream().filter(e -> e.depth() == 0).count();
            assertEquals(verse.declared().beat() + rootNeedles, plan.beatTicks(), verse.id() + " beat");
            int rest = plan.rests() ? plan.restTicks() : session.restCarry();
            assertEquals(verse.declared().rest(), rest, verse.id() + " rest");
        }
    }

    @Test
    void theLimitedVersesAreTheOnesTheDesignLimits() {
        assertEquals(15, VerseContent.get(VerseIds.of("ember")).maxUses());
        assertEquals(20, VerseContent.get(VerseIds.of("balm_dart")).maxUses());
        assertEquals(3, VerseContent.get(VerseIds.of("void_pit")).maxUses());
        assertEquals(15, VerseContent.get(VerseIds.of("rime_ring")).maxUses());
        assertEquals(15, VerseContent.get(VerseIds.of("storm_ring")).maxUses());
        assertEquals(6, VerseContent.get(VerseIds.of("balm_ring")).maxUses());
        assertEquals(3, VerseContent.get(VerseIds.of("undying")).maxUses());
        assertEquals(10, VerseContent.get(VerseIds.of("epic")).maxUses());
        assertEquals(5, VerseContent.get(VerseIds.of("refrain_10")).maxUses());
        long limited = VerseContent.CATALOGUE.all().stream().filter(v -> !v.unlimited()).count();
        assertEquals(9, limited);
    }

    @Test
    void bloodTollPaysInHealthAndRefundsMana() {
        FixedWorld world = new FixedWorld();
        RecitePlan plan = press(ReciteSession.of(tape(1, "blood_toll", "needle"), VerseContent.CATALOGUE), 1, 50, world);
        assertEquals(List.of(4.0D), world.healthPaid);
        assertEquals(-26, plan.manaSpent(), "a 30 refund less the needle");
        assertEquals(76, plan.manaLeft());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.VerseContentTest"`
Expected: `theCatalogueIsFull` fails on the counts.

- [ ] **Step 3: Register everything that is left**

`ProjectileVerses.register`, add:

```java
        c.register(projectile("shard", 12, Verse.UNLIMITED, VersePrototypes.SHARD, 4, 0, s -> s.addKnockback(1.0D)));
        c.register(projectile("arc_bolt", 16, Verse.UNLIMITED, VersePrototypes.ARC, 17, 0, s -> {
            s.addRecoil(60.0D);
            s.hitEffect(HitEffect.SHOCK);
        }));
        // CHAINSAW: the beat is set to nothing, not added to.
        c.register(projectile("whisper", 1, Verse.UNLIMITED, VersePrototypes.WHISPER, 0, -3, s -> {
            s.setBeat(0);
            s.addSpread(6.0D);
        }));
        c.register(projectile("blink_dart", 10, Verse.UNLIMITED, VersePrototypes.BLINK, 3, 0, s -> { }));
```

`StaticVerses.register`, add:

```java
        c.register(stationary("rime_ring", 14, 15, VersePrototypes.RING_RIME, 5, s -> { }));
        c.register(stationary("storm_ring", 18, 15, VersePrototypes.RING_STORM, 5, s -> { }));
        c.register(stationary("balm_ring", 16, 6, VersePrototypes.RING_BALM, 5, s -> { }));
        c.register(stationary("uplift_ring", 10, Verse.UNLIMITED, VersePrototypes.RING_UPLIFT, 5, s -> { }));
        c.register(stationary("void_pit", 40, 3, VersePrototypes.PIT, 27, s -> { }));
```

`ModifierVerses.register`, add:

```java
        c.register(modifier("ballast", 5, Verse.UNLIMITED, 3, 0, s -> {
            s.addDamage(6.0D);
            s.multiplySpeed(0.35D);
            s.addRecoil(50.0D);
        }));
        c.register(modifier("endurance", 6, Verse.UNLIMITED, 4, 0, s -> s.addLifetime(25)));
        c.register(modifier("true_aim", 1, Verse.UNLIMITED, 0, 0, s -> s.addSpread(-60.0D)));
        c.register(modifier("keen_edge", 3, Verse.UNLIMITED, 0, 0, s -> s.addCrit(15.0D)));
        c.register(modifier("ricochet", 2, Verse.UNLIMITED, 0, 0, s -> s.addBounces(10)));
        c.register(modifier("sink", 1, Verse.UNLIMITED, 0, 0, s -> s.addGravity(0.04D)));
        c.register(modifier("loft", 1, Verse.UNLIMITED, 0, 0, s -> s.addGravity(-0.02D)));
        c.register(modifier("volatile", 8, Verse.UNLIMITED, 13, 0, s -> {
            s.addExplosionRadius(1.5D);
            s.addExplosionDamage(1.5D);
            s.multiplySpeed(0.75D);
            s.addRecoil(30.0D);
        }));
        c.register(modifier("blunt", 2, Verse.UNLIMITED, -2, 0, s -> {
            s.nullDamage();
            s.addLifetime(90);
        }));
        c.register(modifier("seeker", 12, Verse.UNLIMITED, 0, 0, s -> s.behaviour(Behaviour.SEEKER)));
        c.register(modifier("sightline", 6, Verse.UNLIMITED, 0, 0, s -> s.behaviour(Behaviour.SIGHTLINE)));
        c.register(modifier("puncture", 16, Verse.UNLIMITED, 0, 0, s -> {
            s.behaviour(Behaviour.PUNCTURE);
            s.addDamage(-1.5D);
            s.allowFriendlyFire();
        }));
        c.register(modifier("serpentine", 2, Verse.UNLIMITED, 0, 0, s -> {
            s.behaviour(Behaviour.SERPENTINE);
            s.multiplySpeed(2.0D);
        }));
        c.register(modifier("gyre", 2, Verse.UNLIMITED, -2, 0, s -> {
            s.behaviour(Behaviour.GYRE);
            s.addDamage(0.5D);
            s.addLifetime(8);
        }));
        c.register(modifier("errant", 2, Verse.UNLIMITED, 0, 0, s -> s.behaviour(Behaviour.ERRANT)));
        c.register(modifier("relay", 12, Verse.UNLIMITED, 0, 0, s -> {
            s.behaviour(Behaviour.RELAY);
            s.addLifetime(-10);
            s.addDamage(-1.0D);
            s.addExplosionRadius(-1.0D);
            s.addSpread(10.0D);
        }));
        c.register(modifier("twin_path", 4, Verse.UNLIMITED, 2, 0, s -> s.behaviour(Behaviour.TWIN_PATH)));
        c.register(modifier("naught", 1, Verse.UNLIMITED, -5, 0, s -> s.behaviour(Behaviour.NAUGHT)));
        c.register(modifier("wellspring", -12, Verse.UNLIMITED, 3, 0, s -> { }));
        c.register(modifier("flame_wreath", 5, Verse.UNLIMITED, 0, 0, s -> {
            s.setSchool(MagicSchool.FIRE);
            s.hitEffect(HitEffect.BURN);
        }));
        c.register(modifier("rime_wreath", 5, Verse.UNLIMITED, 0, 0, s -> {
            s.setSchool(MagicSchool.WATER);
            s.hitEffect(HitEffect.FREEZE);
        }));
        c.register(modifier("storm_wreath", 5, Verse.UNLIMITED, 0, 0, s -> {
            s.hitEffect(HitEffect.SHOCK);
            s.addDamage(0.5D);
        }));
        c.register(modifier("umbral_wreath", 6, Verse.UNLIMITED, 0, 0, s -> {
            s.setSchool(MagicSchool.DARK);
            s.hitEffect(HitEffect.WITHER);
        }));
        c.register(modifier("fire_wake", 4, Verse.UNLIMITED, 0, 0, s -> s.wake(Wake.FIRE, 5)));
        c.register(modifier("water_wake", 3, Verse.UNLIMITED, 0, 0, s -> s.wake(Wake.WATER, 5)));
        c.register(modifier("frost_wake", 4, Verse.UNLIMITED, 0, 0, s -> s.wake(Wake.FROST, 5)));
        c.register(modifier("lantern", 1, Verse.UNLIMITED, 0, 0, s -> {
            s.behaviour(Behaviour.LANTERN);
            s.light(12);
        }));
        c.register(modifier("uplift", 4, Verse.UNLIMITED, 0, 0, s -> s.hitEffect(HitEffect.UPLIFT)));
        c.register(modifier("displace", 6, Verse.UNLIMITED, 0, 0, s -> s.hitEffect(HitEffect.DISPLACE)));
        c.register(modifier("bursting_ricochet", 8, Verse.UNLIMITED, 8, 0, s -> {
            s.addBounces(1);
            s.behaviour(Behaviour.BOUNCE_BURST);
            s.addRecoil(20.0D);
        }));
```

with `import com.efkrdnz.magical.magic.MagicSchool;` added to `ModifierVerses`.

`UtilityVerses`, add the constants and registrations:

```java
    public static final ResourceLocation STEP_WORD = VerseIds.of("step_word");
    public static final ResourceLocation NEAR_WORD = VerseIds.of("near_word");
    public static final ResourceLocation BLOOD_TOLL = VerseIds.of("blood_toll");
```

```java
        // TELEPORT_CAST: the carrier carries the caster to where it dies, then the payload fires there.
        c.register(Verse.of("step_word", VerseType.UTILITY, 18, Verse.UNLIMITED, VersePrototypes.WORD_STEP, 1, Declared.of(1, 7, 0), (r, rec, it) -> {
            r.addProjectileEpitaph(VersePrototypes.WORD_STEP, 1);
            r.state().addBeat(7);
            r.state().addSpread(24.0D);
            return VerseAction.NONE;
        }));
        // CASTER_CAST: the next bodies spawn on the caster.
        c.register(Verse.of("near_word", VerseType.UTILITY, 4, Verse.UNLIMITED, null, 1, Declared.of(1, 0, 0), (r, rec, it) -> {
            r.state().addSpread(-24.0D);
            r.state().behaviour(Behaviour.NEAR_WORD);
            r.drawActions(1);
            return VerseAction.NONE;
        }));
        // BLOOD_MAGIC: a refund of mana paid in health, as true damage, then draw one.
        c.register(Verse.of("blood_toll", VerseType.UTILITY, -30, Verse.UNLIMITED, null, 1, Declared.of(1, -7, -7), (r, rec, it) -> {
            r.state().addBeat(-7);
            r.addRest(-7);
            r.world().payHealth(4.0D);
            r.drawActions(1);
            return VerseAction.NONE;
        }));
```

- [ ] **Step 4: Run it to see it pass**

Run: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.VerseContentTest"`
Expected: 5 tests pass. Then the whole package: `.\gradlew test --tests "com.efkrdnz.magical.magic.incantation.*"` — all green. Then `.\gradlew build` — the rest of the mod is untouched and still compiles.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/incantation src/test/java/com/efkrdnz/magical/magic/incantation
git commit -m "feat: the whole verse catalogue, with every declared number held to the machine" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Self-review

**Spec coverage** (`2026-09-19-authority-of-mana-incantation-design.md`):

| Spec section | Task |
|---|---|
| §4 Grimoire, incantations, validator, uses written back | 3, 4 |
| §5 shot state, stamp-at-add, final state for the group | 1, 5, 8 |
| §6 verses, prototypes, the eight types and their three predicates | 1, 2 |
| §7 the Reciter, every helper by its Lua name, `RecitePlan.cooldownTicks` | 4, 5 |
| §8 rules 1–5, 11 | 5 |
| §8 rules 8–10 | 6 |
| §8 rules 6–7, depth cap | 7 |
| §8 rules 19–20 | 8 |
| §8 rules 12–13, 17–18 | 9 |
| §8 rule 14 | 10 |
| §8 rule 15 | 11 |
| §8 rule 16 | 12 |
| §8 rule 21, all caps | 13 |
| §9 the catalogue, every row | 5–12, 14 |
| §2 deviation 2 (breath), 3 (ticks), 5 (caps), 6 (Wild Recall), 7 (thresholds), 8 (knowledge) | 4, 5, 9, 12, 13 |
| §10–§13 (runtime, removal, screen, fiction) | not this plan: Plans 2, 3, 5 |

Two spec corrections this plan makes, to be carried back into the spec: rule 16's passing case with no End Clause discards only the Otherwise (Task 12 reads the Lua, not the wiki), and §7's reload condition drops the `!reloading` guard (Global Constraints).

**Placeholder scan:** no TBD/TODO; every step has its code; `Recital` in Task 2 is an explicit empty forward declaration that Task 5 replaces whole.

**Type consistency:** `Recital.discardAt(index, count)` (Task 5) is what Task 12 calls; `ControlVerses.copyQuiet(r, verse, rec)` (Task 9) is what Tasks 10 and 11 call; `Verse.Declared.of(draw, beat, rest)` and `Declared.ALL` (Task 2) match every registration; `ReciteFixtures.press(session, breath, mana, world)` (Task 5) matches every test; `FixedWorld.roll(int...)` returns the world for chaining; `VersePrototypes` has exactly the 17 bodies `VerseCatalogueTest` counts and the 17 the catalogue names.

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-19-incantation-core.md`. Two execution options:

1. **Subagent-Driven (recommended)** — a fresh subagent per task, review between tasks, fast iteration (`superpowers:subagent-driven-development`).
2. **Inline Execution** — execute the tasks in this session with `superpowers:executing-plans`, batch execution with checkpoints.
