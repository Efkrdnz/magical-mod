# Blood Sacrifice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Blood Sacrifice, a ritual that spends a full Crimson Vessel to grant chosen temporary
boons and matching temporary prices, both visible in the codex with a countdown.

**Architecture:** The ritual's thirty-two boons and prices are ordinary `MagicPassiveDefinition`
registrations carrying a remaining-tick count in a new `ritualTicks` map on `PlayerMagicState`;
their effects live in one new `ClassPassiveHandler`. A new true-damage type, `magical:blood_price`,
carries every health cost the school charges, with an early-out in the incoming-damage chain so the
barrier never pays it. The pick screen is a plain `Screen` with its own layout class, like the
Spell Creator.

**Tech Stack:** NeoForge 21.4.157, Minecraft 1.21.4, Java 21, Gradle 9.2, JUnit 5.

## Global Constraints

- Work only in `E:\magical-port`. Never `git stash` / `git stash pop` bare.
- Never commit `gradlew`, `.claude/settings.local.json`, `bash.exe.stackdump`, or scratch `*.log`.
- Tests first: watch RED, then GREEN. From Bash use `["bash", "./gradlew", ...]`; there is no
  `gradlew.bat`.
- Commit messages end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Package base `com.efkrdnz.magical`, mod id `magical`.
- Java style here: 4-space indent, 130-ish column limit, a javadoc on every public type and on any
  method whose reason is not obvious from its name. Comments say *why*, never *what*.
- Files stay under 800 lines. `MagicPassiveContent` is already long; the thirty-three new
  definitions go in one clearly labelled block at its end.
- Spec: `docs/superpowers/specs/2026-09-15-blood-sacrifice-design.md`. Every number in this plan
  comes from it.

## File Structure

| File | Responsibility |
| --- | --- |
| `magic/blood/BloodDamageTypes.java` | the `magical:blood_price` key and its `DamageSource` factory |
| `data/magical/damage_type/blood_price.json` | the damage type |
| `data/minecraft/tags/damage_type/bypasses_*.json` | five tag files putting it past vanilla mitigation |
| `magic/blood/SacrificeCatalogue.java` | the thirty-two entries: id, point cost, boon-or-price |
| `magic/blood/SacrificeBudget.java` | budget, requirement and amplification arithmetic, pure |
| `magic/blood/BloodSacrificeService.java` | `open`, `seal`, `RITUAL_COST`, the Unknown's roll |
| `magic/skill/blood/BloodSacrificeSkill.java` | the skill module and its visual profile |
| `magic/passive/SacrificePassives.java` | the `ClassPassiveHandler`, claiming all thirty-three |
| `magic/passive/SacrificeBoons.java` | boon effects, called by the handler |
| `magic/passive/SacrificeCurses.java` | price effects, called by the handler, plus `fizzles` |
| `network/OpenBloodSacrificePayload.java` | server to client: open the screen |
| `network/BloodSacrificeSealPayload.java` | client to server: seal this pact |
| `client/screen/sacrifice/BloodSacrificeLayout.java` | every rectangle, pinned by a test |
| `client/screen/sacrifice/BloodSacrificeScreen.java` | the three-column pick screen |

Modified: `BloodService`, `MagicGameplayEvents`, `MagicCastingService`, `PlayerMagicState`,
`MagicContent`, `MagicPassiveContent`, `ClassPassiveEffects`, `MagicCastContentBlood`,
`MagicPyramidScreen`, `CodexLayout`, `MagicalNetwork`, `ClientPayloadHandlers`, `MagicalCommands`,
`en_us.json`, `CLAUDE.md`, and the enumerating tests.

---

## Task 1: The blood price

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/blood/BloodDamageTypes.java`
- Create: `src/main/resources/data/magical/damage_type/blood_price.json`
- Create: `src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`,
  `bypasses_effects.json`, `bypasses_enchantments.json`, `bypasses_resistance.json`
- Modify: `src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`
- Modify: `src/main/java/com/efkrdnz/magical/magic/BloodService.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicGameplayEvents.java:236`
- Test: `src/test/java/com/efkrdnz/magical/magic/BloodPriceTest.java`

**Interfaces:**
- Produces: `BloodDamageTypes.BLOOD_PRICE` (`ResourceKey<DamageType>`),
  `BloodDamageTypes.price(ServerPlayer)` -> `DamageSource`,
  `BloodService.COST_PER_HEALTH == 25`,
  `BloodService.payFromVesselOnly(ServerPlayer, PlayerMagicState, int) -> boolean`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/efkrdnz/magical/magic/BloodPriceTest.java`:

```java
package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The exchange rate, and the four ways the price gets past everything that would otherwise soften
 * it. The rate and the bypasses move together or not at all: a cheaper bill that armour can still
 * absorb would make blood magic free.
 */
class BloodPriceTest {

    private static final Path DATA = Path.of("src/main/resources/data");

    @Test
    void tenHeartsAreFiveHundredBlood() {
        assertEquals(25, BloodService.COST_PER_HEALTH, "the rate the whole school is priced at");
        assertEquals(500, 20 * BloodService.COST_PER_HEALTH, "a full bar, in blood");
    }

    @Test
    void theTypeExistsAndScalesWithNothing() throws IOException {
        String json = Files.readString(DATA.resolve("magical/damage_type/blood_price.json"));
        assertTrue(json.contains("\"scaling\": \"never\""), "difficulty must not discount a price");
        assertTrue(json.contains("magical.blood_price"), "its death message id");
    }

    @Test
    void thePriceIsPastEveryVanillaSoftener() throws IOException {
        for (String tag : new String[] {"bypasses_armor", "bypasses_effects", "bypasses_enchantments",
                "bypasses_resistance", "bypasses_cooldown"}) {
            String json = Files.readString(DATA.resolve("minecraft/tags/damage_type/" + tag + ".json"));
            assertTrue(json.contains("magical:blood_price"), tag + " must list the blood price");
        }
    }

    @Test
    void theRitualIsNotAlsoOnThePriceList() {
        // BloodPrices is scaled by costScale. A ritual that asks for exactly a hundred cannot be
        // on a list that Thrift discounts, so its entry must stay absent.
        assertEquals(0, com.efkrdnz.magical.magic.blood.BloodPrices.base(
                MagicContent.BLOOD_SACRIFICE.id()), "the ritual bills its own flat hundred");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.BloodPriceTest"
```

Expected: compile failure on `MagicContent.BLOOD_SACRIFICE` (Task 6 adds it). Comment out the
fourth test for now, re-run, and expect three failures: `COST_PER_HEALTH` is 8, and neither the
type nor the tags exist.

- [ ] **Step 3: Write the damage type and its tags**

`src/main/resources/data/magical/damage_type/blood_price.json`:

```json
{
  "message_id": "magical.blood_price",
  "exhaustion": 0.0,
  "scaling": "never"
}
```

Each of `bypasses_armor.json`, `bypasses_effects.json`, `bypasses_enchantments.json`,
`bypasses_resistance.json` in `src/main/resources/data/minecraft/tags/damage_type/`:

```json
{
  "replace": false,
  "values": [
    "magical:blood_price"
  ]
}
```

Add `"magical:blood_price"` to the existing `bypasses_cooldown.json` values array, next to
`"magical:forge_strike"`.

- [ ] **Step 4: Write `BloodDamageTypes`**

```java
package com.efkrdnz.magical.magic.blood;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * The price blood magic charges against the body.
 *
 * <p>Its own type rather than {@code minecraft:magic} for two reasons. The bypass tags take it past
 * armour, resistance, enchantments and the hurt cooldown, so the number a skill asks for is the
 * number the caster loses; and {@code PassiveHooks.isSpellDamage} and
 * {@code MagicSinService.isMagicDamage} both hard-check {@code DamageTypes.MAGIC}, so a price no
 * longer feeds Wrath, Gluttony or anything that keys off being hit by a spell. Paying your own bill
 * is not being attacked.
 *
 * <p>Deliberately not tagged {@code bypasses_invulnerability}: creative and genuinely invulnerable
 * players are already handled by {@code MagicPrice.waived}, and a price that ignores that flag
 * could kill someone the game says cannot be killed.
 */
public final class BloodDamageTypes {

    public static final ResourceKey<DamageType> BLOOD_PRICE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("magical", "blood_price"));

    private BloodDamageTypes() {}

    /** No direct entity and no attacker: the caster is both, and neither is an assailant. */
    public static DamageSource price(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BLOOD_PRICE));
    }
}
```

- [ ] **Step 5: Point `BloodService` at it, and raise the rate**

In `BloodService`, change the constant and its javadoc:

```java
    /**
     * How much of a blood cost one point of health buys. Twenty health is five hundred blood, so a
     * heart is worth fifty and the whole bar is worth five full Vessels.
     *
     * <p>It used to be eight, matched to {@code WarPassives.PAYMENT_MANA_PER_HEALTH}. The two are
     * no longer the same number and no longer should be: Red Payment buys mana, this buys blood,
     * and this one is now charged as true damage that no barrier soaks. A cheaper bill nothing can
     * absorb is a harder bill than an expensive one a full barrier pays for you.
     */
    public static final int COST_PER_HEALTH = 25;
```

In `bleed`, replace the source and the comment above it:

```java
        // The bespoke type, not vanilla magic: the bypass tags and the early-out in
        // MagicGameplayEvents mean the caster's own flesh pays, never armour and never the barrier.
        player.hurt(com.efkrdnz.magical.magic.blood.BloodDamageTypes.price(player), healthCost);
```

Add `payFromVesselOnly` next to `payInHealthOnly`:

```java
    /**
     * Charges a cost the body is not allowed to cover: the mirror of {@link #payInHealthOnly}.
     *
     * <p>A ritual wants proof the caster has been taking blood off other people. Letting them open
     * a vein for the difference would turn "a full Vessel" into "two hearts", which is neither the
     * same requirement nor the same fantasy.
     */
    public static boolean payFromVesselOnly(ServerPlayer player, PlayerMagicState state, int cost) {
        if (MagicPrice.waived(player) || cost <= 0) {
            return true;
        }
        if (state.bloodVessel() < cost) {
            player.displayClientMessage(Component.translatable("message.magical.vessel_not_full"), true);
            return false;
        }
        state.drawFromVessel(cost);
        return true;
    }
```

- [ ] **Step 6: Add the early-out**

In `MagicGameplayEvents.onIncomingDamage`, immediately after
`PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);`:

```java
        if (event.getSource().is(com.efkrdnz.magical.magic.blood.BloodDamageTypes.BLOOD_PRICE)) {
            // The price of blood magic is paid in flesh. No barrier, no Mana Skin, no sin soak, no
            // passive reduction: the number the caller asked for is the number that lands.
            return;
        }
```

- [ ] **Step 7: Run the test and the blood suite**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.BloodPriceTest" --tests "com.efkrdnz.magical.magic.BloodCostTest" --tests "com.efkrdnz.magical.magic.BloodSchoolTest"
```

Expected: three PASS in `BloodPriceTest`, and the other two unchanged. If `BloodCostTest` pins the
old rate, update it to 25 and say in its comment that the rate and the bypasses are one decision.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic src/main/resources/data src/test/java/com/efkrdnz/magical/magic
git commit -m "feat: blood is paid in flesh, at twenty-five to the heart"
```

---

## Task 2: Temporary passives on the player state

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/RitualPassiveTimerTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `PlayerMagicState.ritualRemaining(ResourceLocation) -> int`,
  `grantRitualPassive(ResourceLocation, int)`, `tickRitualPassives() -> boolean`,
  `activeRitualPriceCount() -> int`, `clearRitualPassives()`, `ritualTicks() -> Map<...>`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/efkrdnz/magical/magic/RitualPassiveTimerTest.java`:

```java
package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A ritual passive is an ordinary passive with a clock on it. What is pinned here is the clock:
 * that granting one unlocks it, that ticking it down removes it at zero, that it survives a save,
 * and that re-granting extends rather than shortens.
 */
class RitualPassiveTimerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation boon() {
        return MagicPassiveContent.SANGUINE_MIGHT.id();
    }

    private static ResourceLocation price() {
        return MagicPassiveContent.GLASS_BONES.id();
    }

    @Test
    void grantingOneUnlocksItAndStartsItsClock() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 40);

        assertTrue(state.hasPassive(boon()), "a granted boon is an owned passive");
        assertTrue(state.isPassiveEnabled(boon()), "and it is on: a boon you can switch off is free");
        assertEquals(40, state.ritualRemaining(boon()));
    }

    @Test
    void itIsGoneTheTickItsClockRunsOut() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 3);

        for (int i = 0; i < 3; i++) {
            state.tickRitualPassives();
        }

        assertEquals(0, state.ritualRemaining(boon()));
        assertFalse(state.hasPassive(boon()), "an expired boon is not merely idle, it is removed");
    }

    @Test
    void theLastTickAsksForASyncSoTheCodexStopsCountingToo() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 2);

        assertFalse(state.tickRitualPassives(), "an ordinary tick is not worth a whole state blob");
        assertTrue(state.tickRitualPassives(), "but an expiry is");
    }

    @Test
    void regrantingTakesTheLongerClockRatherThanTheNewer() {
        // The failure this guards: a second ritual with a shorter duration cutting the first one
        // short, which reads as the game taking a boon away that was paid for.
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 100);
        state.grantRitualPassive(boon(), 20);

        assertEquals(100, state.ritualRemaining(boon()));
    }

    @Test
    void onlyPricesAreCounted() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(boon(), 100);
        state.grantRitualPassive(price(), 100);

        assertEquals(1, state.activeRitualPriceCount(), "Hellbroker counts what hurts, not what helps");
    }

    @Test
    void theClockSurvivesARelog() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(price(), 77);

        CompoundTag tag = state.save();
        PlayerMagicState loaded = new PlayerMagicState();
        loaded.load(tag);

        assertEquals(77, loaded.ritualRemaining(price()));
        assertTrue(loaded.hasPassive(price()));
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.RitualPassiveTimerTest"
```

Expected: compile failure, `SANGUINE_MIGHT` and `GLASS_BONES` do not exist. This test cannot go
green before Task 3 registers them. Write it now, leave it red, and do not proceed past Task 3
without it green.

- [ ] **Step 3: Add the field and its API**

Beside the other timer fields in `PlayerMagicState`:

```java
    /**
     * Ritual passives and the ticks they have left.
     *
     * <p>Not {@code passiveCounters}: that map is a general-purpose scratchpad six handlers already
     * write to, and a counter that silently removes its passive at zero would be a trap for every
     * one of them. A ritual clock is its own thing, with its own save key and its own test.
     */
    private final Map<ResourceLocation, Integer> ritualTicks = new LinkedHashMap<>();
```

Methods, next to `passiveCounters()`:

```java
    public Map<ResourceLocation, Integer> ritualTicks() {
        return ritualTicks;
    }

    public int ritualRemaining(ResourceLocation passiveId) {
        return ritualTicks.getOrDefault(passiveId, 0);
    }

    /** Grants a ritual passive, or extends one already running. Never shortens one. */
    public void grantRitualPassive(ResourceLocation passiveId, int ticks) {
        if (ticks <= 0 || MagicPassiveContent.get(passiveId) == null) {
            return;
        }
        unlockPassive(passiveId);
        ritualTicks.merge(passiveId, ticks, Math::max);
    }

    /** How many ritual prices are running: the number Hellbroker's amplification is a function of. */
    public int activeRitualPriceCount() {
        int count = 0;
        for (ResourceLocation id : ritualTicks.keySet()) {
            if (MagicPassiveContent.isRitualPrice(id)) {
                count++;
            }
        }
        return count;
    }

    public void clearRitualPassives() {
        List.copyOf(ritualTicks.keySet()).forEach(this::removePassive);
        ritualTicks.clear();
    }

    /**
     * Runs every tick. Returns true when the client needs the new numbers: on an expiry, because a
     * passive vanished, and once a second otherwise, because the codex is drawing a countdown and a
     * frozen one looks like a bug.
     */
    public boolean tickRitualPassives() {
        if (ritualTicks.isEmpty()) {
            return false;
        }
        List<ResourceLocation> done = new ArrayList<>();
        Iterator<Map.Entry<ResourceLocation, Integer>> entries = ritualTicks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<ResourceLocation, Integer> entry = entries.next();
            int left = entry.getValue() - 1;
            if (left <= 0) {
                entries.remove();
                done.add(entry.getKey());
            } else {
                entry.setValue(left);
            }
        }
        // removePassive touches ritualTicks, so the iteration is finished before anything is removed.
        done.forEach(this::removePassive);
        if (!done.isEmpty()) {
            return true;
        }
        int shortest = ritualTicks.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        return shortest % 20 == 0;
    }
```

`removePassive` already calls `clearPassiveRuntimeState`; add one line to it so a passive pulled by
any route also drops its clock:

```java
        ritualTicks.remove(passiveId);
```

- [ ] **Step 4: Persist it**

In `save()`, beside the `passiveCounters` block:

```java
        CompoundTag ritualTag = new CompoundTag();
        ritualTicks.forEach((id, ticks) -> ritualTag.putInt(id.toString(), ticks));
        tag.put("ritualTicks", ritualTag);
```

In `load()`, beside its counterpart:

```java
        CompoundTag ritualTag = tag.getCompound("ritualTicks");
        for (String key : ritualTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                state.ritualTicks.put(id, ritualTag.getInt(key));
            }
        }
```

The passive itself is already in `unlockedPassives`, which `load` restores, so nothing else is
needed to bring a live ritual back after a relog.

In `copy()`, beside `copy.passiveCounters.putAll(passiveCounters);`:

```java
        copy.ritualTicks.putAll(ritualTicks);
```

- [ ] **Step 5: Tick it**

In `tickServer`, beside the `blackFlamesImbueTicks` block:

```java
        if (tickRitualPassives()) {
            changed = true;
        }
```

- [ ] **Step 6: Leave red, continue to Task 3**

The test still will not compile. That is expected and is the reason Task 3 follows immediately.

---

## Task 3: The catalogue, the budget and thirty-three definitions

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/blood/SacrificeCatalogue.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/blood/SacrificeBudget.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicPassiveContent.java`
- Modify: `src/main/resources/assets/magical/lang/en_us.json`
- Test: `src/test/java/com/efkrdnz/magical/magic/blood/SacrificeBudgetTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/blood/SacrificeCatalogueTest.java`

**Interfaces:**
- Consumes: `PlayerMagicState.activeRitualPriceCount()` from Task 2.
- Produces:
  `SacrificeCatalogue.BOONS` / `PRICES` (`List<ResourceLocation>`, registration order),
  `SacrificeCatalogue.cost(ResourceLocation) -> int`,
  `SacrificeCatalogue.isBoon(ResourceLocation)`, `isPrice(ResourceLocation)`,
  `SacrificeCatalogue.sum(List<ResourceLocation>, boolean boons) -> int`,
  `SacrificeBudget.boonBudget(MagicSkillTuning, boolean hellbroker) -> int`,
  `SacrificeBudget.priceRequired(int boonSpent, boolean hellbroker) -> int`,
  `SacrificeBudget.amplification(int activePrices, boolean hellbroker) -> float`,
  `SacrificeBudget.amplify(float base, float clamp, int activePrices, boolean hellbroker) -> float`,
  `MagicPassiveContent.HELLBROKER` plus the thirty-two entries,
  `MagicPassiveContent.isRitualBoon/isRitualPrice/isRitual(ResourceLocation)`,
  `MagicPassiveContent.ritualBoons()`, `ritualPrices()`.

- [ ] **Step 1: Write the failing budget test**

`src/test/java/com/efkrdnz/magical/magic/blood/SacrificeBudgetTest.java`:

```java
package com.efkrdnz.magical.magic.blood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicSkillTuning;
import org.junit.jupiter.api.Test;

/** The whole economy is six lines of arithmetic, so all six are pinned. */
class SacrificeBudgetTest {

    private static MagicSkillTuning sized(int size) {
        return new MagicSkillTuning(0, 0, size, 0, 0);
    }

    @Test
    void theBudgetStartsAtFourAndBuysOnePointPerPointOfSize() {
        assertEquals(4, SacrificeBudget.boonBudget(sized(0), false));
        assertEquals(6, SacrificeBudget.boonBudget(sized(2), false));
        assertEquals(8, SacrificeBudget.boonBudget(sized(4), false));
    }

    @Test
    void sizeStopsPayingAfterFour() {
        // The tuning budget climbs to eleven with proficiency. Uncapped, a maxed player buys every
        // boon at once and the choice the screen is for stops being a choice.
        assertEquals(8, SacrificeBudget.boonBudget(sized(11), false), "capped at four points of size");
    }

    @Test
    void hellbrokerIsWorthOneBoonPointAndOneForgivenPrice() {
        assertEquals(9, SacrificeBudget.boonBudget(sized(4), true));
        assertEquals(5, SacrificeBudget.priceRequired(5, false), "without it, you pay for what you spend");
        assertEquals(4, SacrificeBudget.priceRequired(5, true), "with it, one point is on credit");
        assertEquals(0, SacrificeBudget.priceRequired(0, true), "and credit never goes negative");
    }

    @Test
    void amplificationIsOneUntilTheSecondPriceAndCapsAtFour() {
        assertEquals(1.0F, SacrificeBudget.amplification(1, true), 0.0001F);
        assertEquals(1.5F, SacrificeBudget.amplification(2, true), 0.0001F, "the player's own example");
        assertEquals(2.0F, SacrificeBudget.amplification(3, true), 0.0001F);
        assertEquals(2.25F, SacrificeBudget.amplification(4, true), 0.0001F);
        assertEquals(2.25F, SacrificeBudget.amplification(9, true), 0.0001F, "and it stops there");
    }

    @Test
    void withoutHellbrokerNothingStacks() {
        assertEquals(1.0F, SacrificeBudget.amplification(5, false), 0.0001F);
    }

    @Test
    void amplifyNeverPassesTheEntrysOwnCeiling() {
        // 15% spell failure, amplified, must stop at the number in the catalogue rather than
        // wherever the curve happens to land.
        assertEquals(0.3375F, SacrificeBudget.amplify(0.15F, 0.3375F, 4, true), 0.0001F);
        assertEquals(0.3375F, SacrificeBudget.amplify(0.15F, 0.3375F, 20, true), 0.0001F);
        assertEquals(0.15F, SacrificeBudget.amplify(0.15F, 0.3375F, 1, true), 0.0001F,
                "one price is unchanged");
    }

    @Test
    void aMultiplierBelowOneAmplifiesDownwardAndStopsAtItsFloor() {
        // Open Wound scales healing to a half. Amplified, it must fall toward its floor, not rise
        // through one, so the clamp is two-sided.
        assertEquals(0.25F, SacrificeBudget.amplify(0.5F, 0.15F, 2, true), 0.0001F);
        assertEquals(0.15F, SacrificeBudget.amplify(0.5F, 0.15F, 4, true), 0.0001F);
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.blood.SacrificeBudgetTest"
```

Expected: FAIL, `SacrificeBudget` does not exist.

- [ ] **Step 3: Write `SacrificeBudget`**

```java
package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.magic.MagicSkillTuning;

/**
 * What a pact may buy, what it must pay, and what a Hellbroker's interest comes to.
 *
 * <p>Pure arithmetic with no state behind it, so the screen, the service and the tests all read the
 * same numbers and none of them can drift.
 */
public final class SacrificeBudget {

    /** Points a pact starts with before the skill is tuned at all. */
    public static final int BASE_BOON_POINTS = 4;

    /** Points of {@code size} that still buy a boon point. */
    public static final int SIZE_POINTS_CAP = 4;

    /** What one extra price adds to the multiplier, and where the multiplier stops. */
    private static final float AMPLIFICATION_STEP = 0.5F;
    private static final float AMPLIFICATION_CAP = 2.25F;

    private SacrificeBudget() {}

    public static int boonBudget(MagicSkillTuning tuning, boolean hellbroker) {
        return BASE_BOON_POINTS + Math.min(SIZE_POINTS_CAP, tuning.size()) + (hellbroker ? 1 : 0);
    }

    public static int priceRequired(int boonSpent, boolean hellbroker) {
        return Math.max(0, boonSpent - (hellbroker ? 1 : 0));
    }

    /**
     * The multiplier every amplified price wears while a Hellbroker holds the pact.
     *
     * <p>Linear and capped, not the exponential the idea started as. {@code 1.5^(n-1)} turns a 15%
     * spell failure into 50.6% at four prices and 75.9% at five, which is not a build but a
     * disconnected mouse; this reaches the same 22.5% at two and stops at 33.75%.
     */
    public static float amplification(int activePrices, boolean hellbroker) {
        if (!hellbroker || activePrices <= 1) {
            return 1.0F;
        }
        return Math.min(AMPLIFICATION_CAP, 1.0F + AMPLIFICATION_STEP * (activePrices - 1));
    }

    /**
     * One amplified number, held to the entry's own ceiling however the multiplier lands.
     *
     * <p>The clamp is two-sided because not every price is a number that grows. Open Wound scales
     * healing to a half, so amplifying it moves it down and its ceiling is really a floor.
     */
    public static float amplify(float base, float clamp, int activePrices, boolean hellbroker) {
        float scaled = base * amplification(activePrices, hellbroker);
        return base <= clamp ? Math.min(clamp, scaled) : Math.max(clamp, scaled);
    }
}
```

- [ ] **Step 4: Run it and watch it pass**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.blood.SacrificeBudgetTest"
```

Expected: PASS, seven tests.

- [ ] **Step 5: Write the failing catalogue test**

`src/test/java/com/efkrdnz/magical/magic/blood/SacrificeCatalogueTest.java`:

```java
package com.efkrdnz.magical.magic.blood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Sixteen boons, sixteen prices, every one of them registered, priced and sorted exactly once.
 *
 * <p>The cheapest thing to get wrong here is an entry that exists in the catalogue and nowhere
 * else, which would let the screen offer a pact the server cannot grant.
 */
class SacrificeCatalogueTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theCatalogueIsSixteenAndSixteen() {
        assertEquals(16, SacrificeCatalogue.BOONS.size());
        assertEquals(16, SacrificeCatalogue.PRICES.size());
    }

    @Test
    void everyEntryIsARegisteredPassiveThatIsNotACurse() {
        for (ResourceLocation id : SacrificeCatalogue.all()) {
            assertNotNull(MagicPassiveContent.get(id), id + " must be registered");
            // Prices are curses in fiction only. A curse gets a Dispel button, and a price you can
            // dispel is not a price.
            assertFalse(MagicPassiveContent.get(id).curse(), id + " must not carry the curse flag");
        }
    }

    @Test
    void everyEntryCostsBetweenOneAndFourPoints() {
        for (ResourceLocation id : SacrificeCatalogue.all()) {
            int cost = SacrificeCatalogue.cost(id);
            assertTrue(cost >= 1 && cost <= 4, id + " costs " + cost + ", outside 1..4");
        }
    }

    @Test
    void nothingIsBothABoonAndAPrice() {
        Set<ResourceLocation> seen = new HashSet<>();
        for (ResourceLocation id : SacrificeCatalogue.all()) {
            assertTrue(seen.add(id), id + " appears twice");
            assertEquals(SacrificeCatalogue.isBoon(id), !SacrificeCatalogue.isPrice(id),
                    id + " must be exactly one of the two");
        }
    }

    @Test
    void everyRequirementIsPayableWithWhatIsOnTheList() {
        // A budget of nine with only four-point prices left would be unsealable. One-pointers make
        // every requirement from one to nine reachable exactly.
        assertTrue(SacrificeCatalogue.PRICES.stream().anyMatch(id -> SacrificeCatalogue.cost(id) == 1),
                "at least one price must cost a single point");
    }

    @Test
    void theStateAgreesWithTheCatalogueAboutWhichIsWhich() {
        for (ResourceLocation id : SacrificeCatalogue.BOONS) {
            assertTrue(MagicPassiveContent.isRitualBoon(id), id.toString());
            assertFalse(MagicPassiveContent.isRitualPrice(id), id.toString());
        }
        for (ResourceLocation id : SacrificeCatalogue.PRICES) {
            assertTrue(MagicPassiveContent.isRitualPrice(id), id.toString());
        }
    }

    @Test
    void anIdOnNeitherListIsWorthNothing() {
        assertEquals(0, SacrificeCatalogue.cost(MagicPassiveContent.MANA_SKIN.id()),
                "an unknown id must never be spendable");
    }
}
```

- [ ] **Step 6: Run it and watch it fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.blood.SacrificeCatalogueTest"
```

Expected: FAIL, `SacrificeCatalogue` does not exist.

- [ ] **Step 7: Register the thirty-three passives**

Two new sets beside `FORBIDDEN_PASSIVES`, declared *above* the definitions so the field
initialisers can add to them, exactly as the existing three are:

```java
    /** Boons a Blood Sacrifice grants, and the prices it charges. Sorted by these, not by curse(). */
    private static final Set<ResourceLocation> RITUAL_BOONS = new LinkedHashSet<>();
    private static final Set<ResourceLocation> RITUAL_PRICES = new LinkedHashSet<>();
```

At the end of the definition block, before `STARTER_PASSIVES`:

```java
    // ---------------------------------------------------------------------------------------
    // Blood Sacrifice. Thirty-two temporary passives a ritual grants and a clock takes back, plus
    // the Hellbroker that brokers them. Every one is registered as a normal passive: prices are
    // curses in fiction, but the curse column carries a Dispel button and a price you can dispel is
    // not a price. What sorts them is RITUAL_BOONS / RITUAL_PRICES, not the curse flag.
    // Behaviour lives in magic/passive/SacrificeBoons and SacrificeCurses.
    // ---------------------------------------------------------------------------------------

    public static final MagicPassiveDefinition HELLBROKER = forbiddenPassive("hellbroker", 0x7A0E2E);

    // --- boons (crimson) ---
    public static final MagicPassiveDefinition CRIMSON_EDGE = ritualBoon("crimson_edge", 0xE8425E);
    public static final MagicPassiveDefinition LONG_REACH = ritualBoon("long_reach", 0xD4627A);
    public static final MagicPassiveDefinition UNFEELING = ritualBoon("unfeeling", 0xB05C6B);
    public static final MagicPassiveDefinition SURE_FOOTING = ritualBoon("sure_footing", 0xC97F86);
    public static final MagicPassiveDefinition SANGUINE_MIGHT = ritualBoon("sanguine_might", 0xD62839);
    public static final MagicPassiveDefinition QUICKENED_PULSE = ritualBoon("quickened_pulse", 0xFF5C74);
    public static final MagicPassiveDefinition THINNED_BLOOD = ritualBoon("thinned_blood", 0xE07A99);
    public static final MagicPassiveDefinition CLOTTED_HIDE = ritualBoon("clotted_hide", 0x9E3B44);
    public static final MagicPassiveDefinition VESSEL_SIPHON = ritualBoon("vessel_siphon", 0xC4122B);
    public static final MagicPassiveDefinition SCARLET_TIDE = ritualBoon("scarlet_tide", 0xEF4B5C);
    public static final MagicPassiveDefinition BLOOD_SCENT = ritualBoon("blood_scent", 0xFF7286);
    public static final MagicPassiveDefinition SECOND_HEART = ritualBoon("second_heart", 0xA8142E);
    public static final MagicPassiveDefinition HAEMOPHAGE = ritualBoon("haemophage", 0x8A0B1E);
    public static final MagicPassiveDefinition RACING_HEART = ritualBoon("racing_heart", 0xFF3355);
    public static final MagicPassiveDefinition BLOODBORNE_FURY = ritualBoon("bloodborne_fury", 0xB01732);
    public static final MagicPassiveDefinition IRONBLOOD = ritualBoon("ironblood", 0x7E2230);

    // --- prices (bruised) ---
    public static final MagicPassiveDefinition OPEN_WOUND = ritualPrice("open_wound", 0x6B2436);
    public static final MagicPassiveDefinition DULLED_SENSES = ritualPrice("dulled_senses", 0x4F3A55);
    public static final MagicPassiveDefinition THIN_SKIN = ritualPrice("thin_skin", 0x6E4350);
    public static final MagicPassiveDefinition LEADEN_STEP = ritualPrice("leaden_step", 0x40323F);
    public static final MagicPassiveDefinition WEEPING_VESSEL = ritualPrice("weeping_vessel", 0x5A2030);
    public static final MagicPassiveDefinition HEMORRHAGE = ritualPrice("hemorrhage", 0x8B1024);
    public static final MagicPassiveDefinition LIFE_TAX = ritualPrice("life_tax", 0x77132A);
    public static final MagicPassiveDefinition BRITTLE_BARRIER = ritualPrice("brittle_barrier", 0x3F5A70);
    public static final MagicPassiveDefinition SLOW_BLOOD = ritualPrice("slow_blood", 0x4A3550);
    public static final MagicPassiveDefinition GLASS_BONES = ritualPrice("glass_bones", 0x8E6B73);
    public static final MagicPassiveDefinition MANA_DROUGHT = ritualPrice("mana_drought", 0x574B7A);
    public static final MagicPassiveDefinition BINDING_CHAINS = ritualPrice("binding_chains", 0x5C4A3A);
    /** Never granted: {@code BloodSacrificeService.seal} rolls it into one of the five real prices. */
    public static final MagicPassiveDefinition THE_UNKNOWN = ritualPrice("the_unknown", 0x2E2440);
    public static final MagicPassiveDefinition ECHOING_MISERY = ritualPrice("echoing_misery", 0x7A2038);
    public static final MagicPassiveDefinition SPELL_FIZZLE = ritualPrice("spell_fizzle", 0x453A66);
    public static final MagicPassiveDefinition BLOOD_DEBT = ritualPrice("blood_debt", 0x5B3A78);
```

Helpers beside `forbiddenPassive`:

```java
    private static MagicPassiveDefinition ritualBoon(String path, int color) {
        MagicPassiveDefinition definition = register(path, false, 1, 0.0F, 0, 0, color);
        RITUAL_BOONS.add(definition.id());
        return definition;
    }

    private static MagicPassiveDefinition ritualPrice(String path, int color) {
        MagicPassiveDefinition definition = register(path, false, 1, 0.0F, 0, 0, color);
        RITUAL_PRICES.add(definition.id());
        return definition;
    }
```

Accessors beside `forbiddenPassives()`:

```java
    public static Set<ResourceLocation> ritualBoons() {
        return Set.copyOf(RITUAL_BOONS);
    }

    public static Set<ResourceLocation> ritualPrices() {
        return Set.copyOf(RITUAL_PRICES);
    }

    public static boolean isRitualBoon(ResourceLocation id) {
        return RITUAL_BOONS.contains(id);
    }

    public static boolean isRitualPrice(ResourceLocation id) {
        return RITUAL_PRICES.contains(id);
    }

    /** True for anything a ritual grants: what the codex pins at the top and refuses to toggle. */
    public static boolean isRitual(ResourceLocation id) {
        return isRitualBoon(id) || isRitualPrice(id);
    }
```

- [ ] **Step 8: Write `SacrificeCatalogue`**

```java
package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/**
 * What a pact may be built from, and what each piece costs.
 *
 * <p>The point cost is the only thing the screen and the service both have to agree on, so it lives
 * here and nowhere else. Order is the order the screen lists them in: cheapest first, so the top of
 * each column is where a small pact is built.
 */
public final class SacrificeCatalogue {

    private static final Map<ResourceLocation, Integer> COSTS = new LinkedHashMap<>();

    public static final List<ResourceLocation> BOONS = List.of(
            priced(MagicPassiveContent.CRIMSON_EDGE.id(), 1),
            priced(MagicPassiveContent.LONG_REACH.id(), 1),
            priced(MagicPassiveContent.UNFEELING.id(), 1),
            priced(MagicPassiveContent.SURE_FOOTING.id(), 1),
            priced(MagicPassiveContent.SANGUINE_MIGHT.id(), 2),
            priced(MagicPassiveContent.QUICKENED_PULSE.id(), 2),
            priced(MagicPassiveContent.THINNED_BLOOD.id(), 2),
            priced(MagicPassiveContent.CLOTTED_HIDE.id(), 2),
            priced(MagicPassiveContent.VESSEL_SIPHON.id(), 2),
            priced(MagicPassiveContent.SCARLET_TIDE.id(), 2),
            priced(MagicPassiveContent.BLOOD_SCENT.id(), 2),
            priced(MagicPassiveContent.SECOND_HEART.id(), 3),
            priced(MagicPassiveContent.HAEMOPHAGE.id(), 3),
            priced(MagicPassiveContent.RACING_HEART.id(), 3),
            priced(MagicPassiveContent.BLOODBORNE_FURY.id(), 3),
            priced(MagicPassiveContent.IRONBLOOD.id(), 4));

    public static final List<ResourceLocation> PRICES = List.of(
            priced(MagicPassiveContent.OPEN_WOUND.id(), 1),
            priced(MagicPassiveContent.DULLED_SENSES.id(), 1),
            priced(MagicPassiveContent.THIN_SKIN.id(), 1),
            priced(MagicPassiveContent.LEADEN_STEP.id(), 1),
            priced(MagicPassiveContent.WEEPING_VESSEL.id(), 1),
            priced(MagicPassiveContent.HEMORRHAGE.id(), 2),
            priced(MagicPassiveContent.LIFE_TAX.id(), 2),
            priced(MagicPassiveContent.BRITTLE_BARRIER.id(), 2),
            priced(MagicPassiveContent.SLOW_BLOOD.id(), 2),
            priced(MagicPassiveContent.GLASS_BONES.id(), 2),
            priced(MagicPassiveContent.MANA_DROUGHT.id(), 2),
            priced(MagicPassiveContent.BINDING_CHAINS.id(), 2),
            priced(MagicPassiveContent.THE_UNKNOWN.id(), 2),
            priced(MagicPassiveContent.ECHOING_MISERY.id(), 3),
            priced(MagicPassiveContent.SPELL_FIZZLE.id(), 3),
            priced(MagicPassiveContent.BLOOD_DEBT.id(), 4));

    private SacrificeCatalogue() {}

    private static ResourceLocation priced(ResourceLocation id, int cost) {
        COSTS.put(id, cost);
        return id;
    }

    public static List<ResourceLocation> all() {
        return Stream.concat(BOONS.stream(), PRICES.stream()).toList();
    }

    /** Zero for anything not on either list, so an unknown id can never be spent. */
    public static int cost(ResourceLocation id) {
        return COSTS.getOrDefault(id, 0);
    }

    public static boolean isBoon(ResourceLocation id) {
        return BOONS.contains(id);
    }

    public static boolean isPrice(ResourceLocation id) {
        return PRICES.contains(id);
    }

    /** The sum of a chosen list, ignoring anything that is not on the list being summed. */
    public static int sum(List<ResourceLocation> chosen, boolean boons) {
        int total = 0;
        for (ResourceLocation id : chosen) {
            if (boons ? isBoon(id) : isPrice(id)) {
                total += cost(id);
            }
        }
        return total;
    }
}
```

- [ ] **Step 9: Lang keys**

Add `passive.magical.<path>` and `passive.magical.<path>.desc` for all thirty-three. The
descriptions are the effect sentences from the spec's two tables, verbatim, so the tooltip and the
design document say the same thing. The shape:

```json
  "passive.magical.hellbroker": "Hellbroker",
  "passive.magical.hellbroker.desc": "A Blood Sacrifice grants one more boon point than it charges for. In exchange every price in the pact feeds the others: two prices bite half again as hard, three twice, four and beyond two and a quarter times.",
  "passive.magical.sanguine_might": "Sanguine Might",
  "passive.magical.sanguine_might.desc": "Temporary. Your spells deal a fifth more damage.",
  "passive.magical.glass_bones": "Glass Bones",
  "passive.magical.glass_bones.desc": "Temporary. Everything that hits you hits a fifth harder.",
```

Also add:

```json
  "screen.magical.passive_group_ritual_boon": "Temporary Buff",
  "screen.magical.passive_group_ritual_price": "Temporary Curse",
  "screen.magical.sacrifice_title": "Blood Sacrifice",
  "screen.magical.sacrifice_boons": "Boons",
  "screen.magical.sacrifice_prices": "Prices",
  "screen.magical.sacrifice_pact": "The Pact",
  "screen.magical.sacrifice_seal": "Seal the Pact",
  "screen.magical.sacrifice_short": "short %s",
  "screen.magical.sacrifice_temporary": "Temporary - %s left",
  "screen.magical.sacrifice_amplified": "Hellbroker: x%s",
  "message.magical.vessel_not_full": "The Vessel is not full.",
  "message.magical.spell_fizzled": "The cast dies in your throat.",
  "death.attack.magical.blood_price": "%1$s paid in blood",
```

- [ ] **Step 10: Run both catalogue tests and the timer test**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.blood.SacrificeCatalogueTest" --tests "com.efkrdnz.magical.magic.RitualPassiveTimerTest"
```

Expected: PASS, seven plus six. The timer test from Task 2 now compiles and goes green.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/efkrdnz/magical src/main/resources/assets/magical/lang src/test/java/com/efkrdnz/magical
git commit -m "feat: thirty-two ritual passives, a clock to take them back, and the arithmetic between"
```

---

## Task 4: The effects

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/passive/SacrificeBoons.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/passive/SacrificeCurses.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/passive/SacrificePassives.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/passive/ClassPassiveEffects.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/passive/SacrificeEffectsTest.java`

**Interfaces:**
- Consumes: `SacrificeBudget.amplify`, `SacrificeCatalogue.all`, `MagicPassiveContent.*`,
  `PlayerMagicState.activeRitualPriceCount`.
- Produces: `SacrificePassives implements ClassPassiveHandler` claiming all thirty-three;
  `SacrificeCurses.fizzles(ServerPlayer, PlayerMagicState, MagicSkillDefinition, long seed) -> boolean`;
  `SacrificeCurses.fizzleChance(PlayerMagicState, boolean hellbroker) -> float`;
  `SacrificeBoons.furyMultiplier(PlayerMagicState) -> float`;
  `ClassPassiveEffects.handlers() -> List<ClassPassiveHandler>` (package-visible, for the test).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/efkrdnz/magical/magic/passive/SacrificeEffectsTest.java`:

```java
package com.efkrdnz.magical.magic.passive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.SacrificeCatalogue;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The parts of the ritual's effects that need no live player: who claims what, and the numbers the
 * pure helpers produce. Anything that deals damage is a game test instead, because damage needs a
 * world.
 */
class SacrificeEffectsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void oneHandlerClaimsAllThirtyThree() {
        Set<ResourceLocation> handled = new SacrificePassives().handled();
        assertEquals(33, handled.size(), "thirty-two entries and the broker");
        assertTrue(handled.contains(MagicPassiveContent.HELLBROKER.id()));
        for (ResourceLocation id : SacrificeCatalogue.all()) {
            assertTrue(handled.contains(id), id + " has no handler");
        }
    }

    @Test
    void noOtherHandlerClaimsAnyOfThem() {
        // The coverage test asserts every passive has a handler; this asserts none has two, which
        // would mean an effect applied twice with nothing on screen to say so.
        Set<ResourceLocation> ours = new SacrificePassives().handled();
        for (ClassPassiveHandler handler : ClassPassiveEffects.handlers()) {
            if (handler instanceof SacrificePassives) {
                continue;
            }
            Set<ResourceLocation> overlap = new HashSet<>(handler.handled());
            overlap.retainAll(ours);
            assertTrue(overlap.isEmpty(), handler.getClass().getSimpleName() + " also claims " + overlap);
        }
    }

    @Test
    void spellFizzleIsFifteenPercentAloneAndNeverPastAThird() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.SPELL_FIZZLE.id(), 100);

        assertEquals(0.15F, SacrificeCurses.fizzleChance(state, false), 0.0001F);

        for (ResourceLocation id : new ResourceLocation[] {MagicPassiveContent.GLASS_BONES.id(),
                MagicPassiveContent.SLOW_BLOOD.id(), MagicPassiveContent.THIN_SKIN.id()}) {
            state.grantRitualPassive(id, 100);
        }

        assertEquals(0.3375F, SacrificeCurses.fizzleChance(state, true), 0.0001F,
                "four prices under a Hellbroker, and not one point past the ceiling");
    }

    @Test
    void bloodborneFuryPaysPerPriceAndOnlyForPrices() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.BLOODBORNE_FURY.id(), 100);
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 100);

        assertEquals(1.0F, SacrificeBoons.furyMultiplier(state), 0.0001F, "a boon is not a price");

        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 100);
        state.grantRitualPassive(MagicPassiveContent.SLOW_BLOOD.id(), 100);

        assertEquals(1.16F, SacrificeBoons.furyMultiplier(state), 0.0001F, "eight percent each");
    }

    @Test
    void nothingFiresWhileNoRitualIsRunning() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(0.0F, SacrificeCurses.fizzleChance(state, true), 0.0001F);
        assertEquals(1.0F, SacrificeBoons.furyMultiplier(state), 0.0001F);
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.passive.SacrificeEffectsTest"
```

Expected: compile failure, none of the three classes exist.

- [ ] **Step 3: Write `SacrificeBoons`**

One static, package-visible method per hook, each folding in every boon that touches it, all gated
on `ClassPassiveEffects.on(state, id)`. Attribute-driven boons go through a private `applyOrClear`
copied from `WildPassives:238` (four lines; coupling two unrelated handlers to share them would be
worse than the copy).

```java
package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What the sixteen boons of a Blood Sacrifice actually do.
 *
 * <p>Split from {@link SacrificeCurses} because between them they are more than a file's worth, and
 * the two halves are read for different reasons: this one when a boon feels weak, that one when a
 * price feels unfair.
 */
final class SacrificeBoons {

    static final float MIGHT = 1.20F;
    static final double EDGE = 0.20D;
    static final double PULSE = 0.15D;
    static final float THINNED_MANA = 0.65F;
    static final float HIDE = 0.85F;
    static final float TIDE = 1.25F;
    static final float SCENT = 1.18F;
    static final float SCENT_BELOW = 0.40F;
    static final int SECOND_HEART_BARRIER = 40;
    static final float HAEMOPHAGE_SHARE = 0.10F;
    static final float HAEMOPHAGE_CAP = 4.0F;
    static final float RACING_COOLDOWN = 0.70F;
    static final float FURY_PER_PRICE = 0.08F;
    static final int VESSEL_SIPHON_BLOOD = 12;
    static final double UNFEELING_KNOCKBACK = 0.6D;
    static final float UNFEELING_STATUS = 0.60F;
    static final double REACH_ENTITY = 1.5D;
    static final double REACH_BLOCK = 1.0D;
    static final double FOOTING_STEP = 0.6D;
    static final double FOOTING_SAFE_FALL = 3.0D;

    private SacrificeBoons() {}

    /** Spell damage the player is about to deal, with every damage boon folded in. */
    static float outgoing(PlayerMagicState state, LivingEntity target, float amount) {
        float scaled = amount;
        if (ClassPassiveEffects.on(state, MagicPassiveContent.SANGUINE_MIGHT.id())) {
            scaled *= MIGHT;
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BLOOD_SCENT.id())
                && target.getHealth() / Math.max(1.0F, target.getMaxHealth()) < SCENT_BELOW) {
            scaled *= SCENT;
        }
        return scaled * furyMultiplier(state);
    }

    /** Bloodborne Fury: eight percent per ritual price, and a boon is never a price. */
    static float furyMultiplier(PlayerMagicState state) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.BLOODBORNE_FURY.id())) {
            return 1.0F;
        }
        return 1.0F + FURY_PER_PRICE * state.activeRitualPriceCount();
    }
```

Then, in the same shape and reading the spec's boon table row by row:

- `incoming(state, amount)` - Clotted Hide.
- `adjustCast(state, out)` - Thinned Blood on `out.mana`, Scarlet Tide on `out.size`, Racing Heart
  on `out.cooldown`.
- `drink(player, state, dealt)` - Haemophage, `player.heal(Math.min(HAEMOPHAGE_CAP, dealt * HAEMOPHAGE_SHARE))`.
- `onKill(player, state, source)` - Vessel Siphon, `state.addBloodVessel(VESSEL_SIPHON_BLOOD)` when
  `PassiveHooks.isSpellKill(source)`.
- `statusScale(state)` - Unfeeling.
- `bonusBarrier(state)` - Second Heart.
- `cheatDeath(player, state)` - Ironblood: set health to 1, `state.setBloodVessel(0)`,
  `state.removePassive(IRONBLOOD)` so the boon is spent, return true.
- `attributes(player, state)` - Crimson Edge, Quickened Pulse, Long Reach, Unfeeling, Sure Footing,
  each through `applyOrClear` with its own `ResourceLocation` modifier id.

- [ ] **Step 4: Write `SacrificeCurses`**

Same shape for the price table. Everything amplified goes through
`SacrificeBudget.amplify(base, clamp, state.activeRitualPriceCount(), hellbroker)` with
`hellbroker` read once per call as `ClassPassiveEffects.on(state, MagicPassiveContent.HELLBROKER.id())`.

```java
    /** The chance a cast dies after paying for itself. Public: the cast pipeline asks. */
    public static float fizzleChance(PlayerMagicState state, boolean hellbroker) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.SPELL_FIZZLE.id())) {
            return 0.0F;
        }
        return SacrificeBudget.amplify(FIZZLE, FIZZLE_CAP, state.activeRitualPriceCount(), hellbroker);
    }

    /**
     * Whether this cast dies. Seeded from the cast's own seed rather than a fresh Random, so a game
     * test can force either outcome and a lag spike cannot reroll it.
     */
    public static boolean fizzles(ServerPlayer player, PlayerMagicState state,
            MagicSkillDefinition definition, long seed) {
        if (MagicContent.BLOOD_SACRIFICE.id().equals(definition.id())) {
            // The ritual is exempt. A ritual that fails to open its own screen after taking a
            // hundred blood is indistinguishable from a crash.
            return false;
        }
        float chance = fizzleChance(state, broker(state));
        return chance > 0.0F && new java.util.Random(seed).nextFloat() < chance;
    }
```

plus `incoming` (Glass Bones, and Brittle Barrier only while `state.barrier() > 0`), `adjustCast`
(Mana Drought, Binding Chains), `afterCast` (Life Tax, one health as a blood price), `recoil`
(Echoing Misery), `heal` (Open Wound), `statusScale` (Dulled Senses), `onKill` (Weeping Vessel
returns before any blood is credited), `hemorrhage(player, state)` on the slow tick, and
`attributes` (Thin Skin, Slow Blood, Leaden Step).

- [ ] **Step 5: Write `SacrificePassives`**

```java
/**
 * Every effect a Blood Sacrifice grants or charges, under one handler.
 *
 * <p>One handler rather than two because the coverage test wants exactly one owner per passive and
 * the two halves interact: Bloodborne Fury reads the price count, and Hellbroker scales the prices.
 * The effects themselves live in {@link SacrificeBoons} and {@link SacrificeCurses}.
 */
public final class SacrificePassives implements ClassPassiveHandler {

    private static final Set<ResourceLocation> HANDLED = buildHandled();

    @Override
    public Set<ResourceLocation> handled() {
        return HANDLED;
    }

    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        float dealt = SacrificeBoons.outgoing(state, target, amount);
        SacrificeBoons.drink(player, state, dealt);
        SacrificeCurses.recoil(player, state, dealt);
        return dealt;
    }

    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        return SacrificeCurses.incoming(state, SacrificeBoons.incoming(state, amount));
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        SacrificeBoons.attributes(player, state);
        SacrificeCurses.attributes(player, state);
        SacrificeCurses.hemorrhage(player, state);
    }

    private static Set<ResourceLocation> buildHandled() {
        Set<ResourceLocation> handled = new LinkedHashSet<>(SacrificeCatalogue.all());
        handled.add(MagicPassiveContent.HELLBROKER.id());
        return Set.copyOf(handled);
    }

    // ... adjustCast, afterCast, onKill, adjustHeal, statusDurationScale, cheatDeath, bonusMaxBarrier
}
```

Add it to the end of `ClassPassiveEffects.HANDLERS`, and add:

```java
    /** The handler list, for the test that asserts no passive is claimed twice. */
    static List<ClassPassiveHandler> handlers() {
        return HANDLERS;
    }
```

- [ ] **Step 6: Run the passive suite**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.passive.*"
```

Expected: PASS, including the existing test that every passive is claimed by exactly one handler.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/passive src/test/java/com/efkrdnz/magical/magic/passive
git commit -m "feat: the boons pay out, the prices come due"
```

---

## Task 5: Spell Fizzle in the cast pipeline

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicCastingService.java:362`

No unit test: the insert needs a live cast, and the game test in Task 10 covers it.

- [ ] **Step 1: Insert the check**

In `castViaRegistry`, immediately after the `spendManaForSkill` guard and before the `CastContext`
is built:

```java
        if (com.efkrdnz.magical.magic.passive.SacrificeCurses.fizzles(player, state, definition, seed)) {
            // Paid for, cooled down, and nothing happened. That is the whole curse. The windup
            // still plays, so a fizzle looks like a cast that went wrong rather than a dropped key.
            state.setSkillCooldown(definition.id(), stats.cooldownTicks());
            com.efkrdnz.magical.magic.visual.SpellFx.windup(player, definition, aim.point(), player.getLookAngle(), sneak);
            player.displayClientMessage(Component.translatable("message.magical.spell_fizzled"), true);
            state.sync(player);
            return;
        }
```

Self-managed and hold-gated handlers return before the mana spend and so are already exempt. Add a
sentence to the method's javadoc saying so, because it is a deliberate hole and not an oversight.

- [ ] **Step 2: Build**

```bash
./gradlew build -q
```

Expected: no output, exit 0.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/MagicCastingService.java
git commit -m "feat: a cursed cast pays its price and dies anyway"
```

---

## Task 6: The skill and the service

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicContent.java:62`
- Create: `src/main/java/com/efkrdnz/magical/magic/blood/BloodSacrificeService.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/skill/blood/BloodSacrificeSkill.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentBlood.java`
- Modify: `src/test/java/com/efkrdnz/magical/magic/BloodSchoolTest.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/blood/BloodSacrificeServiceTest.java`

**Interfaces:**
- Produces: `MagicContent.BLOOD_SACRIFICE`, `BloodSacrificeService.RITUAL_COST == 100`,
  `BloodSacrificeService.Refusal` (enum: `VESSEL_NOT_FULL`, `UNKNOWN_ENTRY`, `DUPLICATE`,
  `OVER_BUDGET`, `PRICES_TOO_CHEAP`, `NOTHING_CHOSEN`),
  `BloodSacrificeService.validate(PlayerMagicState, List, List) -> Refusal` (null when fine),
  `BloodSacrificeService.seal(ServerPlayer, PlayerMagicState, List, List) -> boolean`,
  `BloodSacrificeService.open(ServerPlayer, PlayerMagicState)`,
  `BloodSacrificeService.rollUnknown(long seed) -> ResourceLocation`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/efkrdnz/magical/magic/blood/BloodSacrificeServiceTest.java`. `seal` needs a live
player, so what is pinned is `validate` and `rollUnknown`, which do not. `fullVessel()` is a helper
returning a fresh state with `addBloodVessel(BloodSacrificeService.RITUAL_COST)`.

```java
    @Test
    void aPactMustBePaidFor() {
        assertEquals(BloodSacrificeService.Refusal.PRICES_TOO_CHEAP, BloodSacrificeService.validate(
                fullVessel(), List.of(MagicPassiveContent.SANGUINE_MIGHT.id()), List.of()));
    }

    @Test
    void aPactMayNotSpendMoreThanItHas() {
        assertEquals(BloodSacrificeService.Refusal.OVER_BUDGET, BloodSacrificeService.validate(
                fullVessel(), SacrificeCatalogue.BOONS, SacrificeCatalogue.PRICES));
    }

    @Test
    void anEmptyVesselIsNoPactAtAll() {
        assertEquals(BloodSacrificeService.Refusal.VESSEL_NOT_FULL, BloodSacrificeService.validate(
                new PlayerMagicState(), List.of(MagicPassiveContent.CRIMSON_EDGE.id()),
                List.of(MagicPassiveContent.THIN_SKIN.id())));
    }

    @Test
    void theSameBoonTwiceIsOneBoonAndFiveForgedPoints() {
        assertEquals(BloodSacrificeService.Refusal.DUPLICATE, BloodSacrificeService.validate(fullVessel(),
                List.of(MagicPassiveContent.CRIMSON_EDGE.id(), MagicPassiveContent.CRIMSON_EDGE.id()),
                List.of(MagicPassiveContent.THIN_SKIN.id(), MagicPassiveContent.OPEN_WOUND.id())));
    }

    @Test
    void anIdThatIsOnNeitherListIsRefused() {
        assertEquals(BloodSacrificeService.Refusal.UNKNOWN_ENTRY, BloodSacrificeService.validate(
                fullVessel(), List.of(MagicPassiveContent.MANA_SKIN.id()),
                List.of(MagicPassiveContent.THIN_SKIN.id())));
    }

    @Test
    void anEmptyPactIsNotAPact() {
        assertEquals(BloodSacrificeService.Refusal.NOTHING_CHOSEN,
                BloodSacrificeService.validate(fullVessel(), List.of(), List.of()));
    }

    @Test
    void aFairPactIsAccepted() {
        assertNull(BloodSacrificeService.validate(fullVessel(),
                List.of(MagicPassiveContent.SANGUINE_MIGHT.id()),
                List.of(MagicPassiveContent.GLASS_BONES.id())));
    }

    @Test
    void overshootingOnPricesIsAllowed() {
        // Costs are lumpy. A requirement of three with a four-point price left has to round up, and
        // rounding up is also a build: more prices is more for a Hellbroker to amplify.
        assertNull(BloodSacrificeService.validate(fullVessel(),
                List.of(MagicPassiveContent.SECOND_HEART.id()),
                List.of(MagicPassiveContent.BLOOD_DEBT.id())));
    }

    @Test
    void theUnknownRollsTheWholeTableAndNothingElse() {
        Set<ResourceLocation> rolled = new HashSet<>();
        for (long seed = 0; seed < 4000; seed++) {
            rolled.add(BloodSacrificeService.rollUnknown(seed));
        }
        assertEquals(Set.of(MagicPassiveContent.SLOW_BLOOD.id(), MagicPassiveContent.MANA_DROUGHT.id(),
                MagicPassiveContent.GLASS_BONES.id(), MagicPassiveContent.SPELL_FIZZLE.id(),
                MagicPassiveContent.BLOOD_DEBT.id()), rolled);
    }

    @Test
    void theUnknownNeverRollsItself() {
        for (long seed = 0; seed < 4000; seed++) {
            assertNotEquals(MagicPassiveContent.THE_UNKNOWN.id(), BloodSacrificeService.rollUnknown(seed));
        }
    }
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.blood.BloodSacrificeServiceTest"
```

Expected: compile failure, the service does not exist.

- [ ] **Step 3: Register the skill**

In `MagicContent`, after `BLOOD_RITE`:

```java
    public static final MagicSkillDefinition BLOOD_SACRIFICE = register("blood_sacrifice", MagicSchool.BLOOD, MagicSkillType.BURST, -1, 0, 0.0F, 1.0F, 1.0F, 0, 1800, 1200, 0.0F, 0, 0x6E0B18);
```

Lang: `skill.magical.blood_sacrifice` = "Blood Sacrifice", and a `.desc` naming the full Vessel, the
chosen boon, the chosen price and the one-and-a-half.

- [ ] **Step 4: Write `BloodSacrificeService`**

```java
    /** A full Vessel, and nothing less. Flat, because a discounted ritual is a different ritual. */
    public static final int RITUAL_COST = 100;

    /** How much longer a price runs than the boon it paid for. */
    public static final float PRICE_DURATION_FACTOR = 1.5F;

    /** What a Blood Debt adds, and never gives back. */
    public static final int BLOOD_DEBT_CORRUPTION = 25;
```

`validate` in order: nothing chosen, an unknown id on either list, a duplicate within either list,
the Vessel, the budget, the requirement. `seal` re-runs `validate`, then:

```java
        if (!BloodService.payFromVesselOnly(player, state, RITUAL_COST)) {
            return false;
        }
        MagicSkillResolvedStats stats = MagicContent.BLOOD_SACRIFICE.resolve(
                state.tuningFor(MagicContent.BLOOD_SACRIFICE.id()));
        int boonTicks = stats.durationTicks();
        int priceTicks = Math.round(boonTicks * PRICE_DURATION_FACTOR);
        boons.forEach(id -> state.grantRitualPassive(id, boonTicks));
        for (ResourceLocation id : prices) {
            ResourceLocation granted = MagicPassiveContent.THE_UNKNOWN.id().equals(id)
                    ? rollUnknown(player.serverLevel().getGameTime() + player.getId())
                    : id;
            if (MagicPassiveContent.BLOOD_DEBT.id().equals(granted)) {
                // The one price with no clock: corruption is permanent and Purification is the only
                // way out, which is the whole reason it costs the top of the range.
                state.addCorruption(BLOOD_DEBT_CORRUPTION);
                continue;
            }
            state.grantRitualPassive(granted, priceTicks);
        }
        state.setSkillCooldown(MagicContent.BLOOD_SACRIFICE.id(), stats.cooldownTicks());
        state.sync(player);
        return true;
```

`rollUnknown` walks a cumulative weight array over the spec's five entries at 30/25/20/15/10,
driven by `new Random(seed).nextInt(100)`.

`open(player, state)` resolves the same stats and sends `OpenBloodSacrificePayload` (Task 7); until
that exists, leave it as a `TODO`-free stub that only resolves the ticks, and wire the send in
Task 7's step 4.

- [ ] **Step 5: Write `BloodSacrificeSkill`**

```java
    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                if (ctx.state().bloodVessel() < BloodSacrificeService.RITUAL_COST) {
                    player.displayClientMessage(Component.translatable("message.magical.vessel_not_full"), true);
                    return CastResult.FAILED;
                }
                BloodSacrificeService.open(player, ctx.state());
                // HANDLED, not SUCCESS: opening the screen and closing it again must cost nothing,
                // so the cooldown and the hundred are both charged by seal.
                return CastResult.HANDLED;
            }

            @Override
            public double aimRange() {
                return 0.0D;
            }
        };
    }
```

Visual profile: `SchoolMaterial.BLOOD`, `EmblemId.THORN_CROWN` (unused elsewhere, and a crown of
thorns is the pact), a `SOLID_RING` and a `CHAIN_BAND`, `StampId.RING`, `CoreKind.IRIS`,
`SpinSignature.SLOW`, `CircleAnchor.GROUND`, `ReleaseMode.LIFT` with
`ProfileCues.FirstPersonPreset.CASTER_LIGHT`, budget 2, bounds `2.0F, 2.0F, 1.0F`.

Register it in `MagicCastContentBlood` and change that class's javadoc from six skills to seven.

- [ ] **Step 6: Update `BloodSchoolTest`**

Add `MagicContent.BLOOD_SACRIFICE` to the expected skill list, `MagicPassiveContent.HELLBROKER` to
the named forbidden passives, and change the class javadoc and the test name from six to seven and
three to four. Add:

```java
    @Test
    void theRitualIsTheOnlyBloodSkillNotOnThePriceList() {
        // Every other blood skill is billed through BloodPrices and scaled by costScale. The ritual
        // asks for exactly a full Vessel, which a discount would make a lie.
        assertEquals(0, BloodPrices.base(MagicContent.BLOOD_SACRIFICE.id()));
        assertEquals(PlayerMagicState.MAX_BLOOD_VESSEL, BloodSacrificeService.RITUAL_COST,
                "a ritual that wants less than a full Vessel is not a ritual");
    }
```

- [ ] **Step 7: Run the blood suite**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.magic.blood.*" --tests "com.efkrdnz.magical.magic.Blood*"
```

Expected: PASS. Re-enable the fourth test in `BloodPriceTest` from Task 1 and confirm it passes.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/efkrdnz/magical src/main/resources/assets/magical/lang src/test/java/com/efkrdnz/magical
git commit -m "feat: the seventh blood skill asks for a full Vessel and offers a pact"
```

---

## Task 7: The payloads

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/network/OpenBloodSacrificePayload.java`
- Create: `src/main/java/com/efkrdnz/magical/network/BloodSacrificeSealPayload.java`
- Modify: `src/main/java/com/efkrdnz/magical/network/MagicalNetwork.java:47`
- Modify: `src/main/java/com/efkrdnz/magical/client/ClientPayloadHandlers.java:46`
- Modify: `src/main/java/com/efkrdnz/magical/magic/blood/BloodSacrificeService.java` (`open` sends)
- Test: `src/test/java/com/efkrdnz/magical/network/BloodSacrificePayloadTest.java`

- [ ] **Step 1: Write the failing round-trip test**

Follow whatever round-trip helper the existing payload tests use; if there is none, build a
`RegistryFriendlyByteBuf` over a bootstrapped `RegistryAccess`, encode, decode, assert equality.
Two cases: the open payload's two tick counts, and a seal payload carrying three boons and two
prices **in order**, because the screen's list order is what the player sees on the pact panel.

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.network.BloodSacrificePayloadTest"
```

- [ ] **Step 3: Write the payloads**

```java
/**
 * Server to client: open the pact screen, with the two durations the current tuning resolves to.
 *
 * <p>Only the durations. Everything else the screen needs - the Vessel, the tuning, whether
 * Hellbroker is owned - is already in the synced state, and these two are the one thing that would
 * otherwise mean duplicating {@code MagicSkillDefinition.resolve} on the client.
 */
public record OpenBloodSacrificePayload(int boonTicks, int priceTicks) implements CustomPacketPayload
```

with `ByteBufCodecs.VAR_INT` twice.

```java
/**
 * Client to server: seal this pact.
 *
 * <p>The lists are untrusted. {@code BloodSacrificeService.seal} re-reads the tuning, recomputes the
 * budget and rejects unknown ids, duplicates, an overspend, a shortfall and an under-full Vessel
 * before it charges anything, so a forged packet does nothing but print a refusal.
 */
public record BloodSacrificeSealPayload(List<ResourceLocation> boons, List<ResourceLocation> prices)
        implements CustomPacketPayload
```

with `ByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC)` for each list.

- [ ] **Step 4: Register them and wire `open`**

In `MagicalNetwork.registerPayloads`, beside the Spell Creator pair: `playToClient` for the open
payload routing through `handleClientPayload`, and `playToServer` for the seal payload calling
`BloodSacrificeService.seal(player, player.getData(MagicalAttachments.MAGIC_STATE), payload.boons(),
payload.prices())`.

Add to `ClientPayloadHandlers`:

```java
    public static void handle(OpenBloodSacrificePayload payload) {
        BloodSacrificeScreen.open(payload.boonTicks(), payload.priceTicks());
    }
```

and complete `BloodSacrificeService.open` with the send. Leave the screen call as a compile error
until Task 8; finish Task 8 before running the full build.

- [ ] **Step 5: Run the payload tests and commit**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.network.*"
git add src/main/java/com/efkrdnz/magical src/test/java/com/efkrdnz/magical/network
git commit -m "feat: two packets for a pact, and the server checks both ends of it"
```

---

## Task 8: The screen

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/screen/sacrifice/BloodSacrificeLayout.java`
- Create: `src/main/java/com/efkrdnz/magical/client/screen/sacrifice/BloodSacrificeScreen.java`
- Modify: `src/test/java/com/efkrdnz/magical/client/screen/MagicalTooltipAssetsTest.java`
  (add `client/screen/sacrifice` to `sources()`)
- Test: `src/test/java/com/efkrdnz/magical/client/screen/sacrifice/BloodSacrificeLayoutTest.java`

- [ ] **Step 1: Write the failing layout test**

Model it on `SpellCreatorLayoutTest`: collect every rect the screen draws, assert pairwise
disjointness, assert each sits inside `ScreenChrome.body()` (header rects excepted), and assert each
hit test answers to its own centre and to no other rect's centre.

```java
    @Test
    void thePactPanelHoldsEverythingItDraws() {
        Rect pact = BloodSacrificeLayout.pactPanel();
        for (Rect inner : List.of(BloodSacrificeLayout.boonCounter(), BloodSacrificeLayout.priceCounter(),
                BloodSacrificeLayout.chosenBoons(), BloodSacrificeLayout.chosenPrices(),
                BloodSacrificeLayout.brokerNote(), BloodSacrificeLayout.durations(),
                BloodSacrificeLayout.seal())) {
            assertInside(pact, inner);
        }
    }

    @Test
    void theTwoListsAndTheirScrollbarsNeverTouch() {
        assertDisjoint(List.of(BloodSacrificeLayout.boonRow(0), BloodSacrificeLayout.boonScrollbar(),
                BloodSacrificeLayout.priceRow(0), BloodSacrificeLayout.priceScrollbar(),
                BloodSacrificeLayout.pactPanel()));
    }

    @Test
    void theLastVisibleRowIsStillOnTheBoard() {
        assertInside(ScreenChrome.body(),
                BloodSacrificeLayout.boonRow(BloodSacrificeLayout.VISIBLE_ROWS - 1));
    }

    @Test
    void aRowAnswersToItsOwnCentreAndNothingElse() {
        for (int row = 0; row < BloodSacrificeLayout.VISIBLE_ROWS; row++) {
            Rect rect = BloodSacrificeLayout.boonRow(row);
            assertEquals(row, BloodSacrificeLayout.boonAt(rect.x() + rect.w() / 2, rect.y() + rect.h() / 2));
            // The gap between two rows belongs to neither.
            assertEquals(-1, BloodSacrificeLayout.boonAt(rect.x() + rect.w() / 2, rect.bottom() + 1));
        }
    }
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayoutTest"
```

- [ ] **Step 3: Write `BloodSacrificeLayout`**

The body is `x 10..434, y 44..328`. Three columns, the same header constants the creator borrows
from `ScreenChrome`:

```java
    public static final int LABEL_Y = 50;

    public static final int BOON_X = 18;
    public static final int PRICE_X = 166;
    public static final int LIST_W = 140;
    public static final int BOON_SCROLL_X = 159;
    public static final int PRICE_SCROLL_X = 307;
    public static final int SCROLLBAR_W = 5;

    public static final int LIST_Y = 64;
    public static final int ROW_H = 20;
    public static final int ROW_STRIDE = 22;
    public static final int VISIBLE_ROWS = 11;

    public static final int TICK_DX = 5;
    public static final int TICK_SIZE = 10;
    public static final int COST_CHIP_W = 14;
    public static final int COST_CHIP_INSET = 5;

    public static final int PACT_X = 314;
    public static final int PACT_Y = 50;
    public static final int PACT_W = 112;
    public static final int PACT_H = 270;

    public static final int SEAL_W = 100;
    public static final int SEAL_H = 20;
    public static final int TOOLTIP_W = 220;
```

One `Rect` function per element, and `boonAt(lx, ly)` / `priceAt(lx, ly)` returning the visible row
index or `-1`, computed from the same constants so a row can never be drawn in one place and
clicked in another.

- [ ] **Step 4: Run it and watch it pass**

- [ ] **Step 5: Write `BloodSacrificeScreen`**

A plain `Screen implements HudDebug.Captured`, `isPauseScreen()` false, a static
`open(int boonTicks, int priceTicks)` calling `minecraft.player.closeContainer()` then `setScreen`.
State: two `LinkedHashSet<ResourceLocation>` of picks and two scroll offsets, plus the two tick
counts from the payload.

- Paint order: chrome fills, the tick boxes and chips, `emblems.flush()`, text, tooltip.
- `mouseClicked` toggles a pick; a boon whose cost would push the spend past
  `SacrificeBudget.boonBudget` is drawn dimmed and refuses.
- The pact panel's counters come from `SacrificeBudget` and `SacrificeCatalogue.sum` directly, so
  the screen and the server compute the same numbers from the same code.
- Seal sends `BloodSacrificeSealPayload` and closes; Escape sends the codex request the way the
  creator's Back does.
- Every tooltip line goes through `font.split(line, TOOLTIP_W)` then
  `g.renderTooltip(font, lines, mouseX, mouseY)`.

- [ ] **Step 6: Build and commit**

```bash
./gradlew build -q
git add src/main/java/com/efkrdnz/magical/client src/test/java/com/efkrdnz/magical/client
git commit -m "feat: three columns to weigh a boon against a price"
```

---

## Task 9: The codex countdown

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/client/screen/MagicPyramidScreen.java` - `passiveRows`
  (:1142), `paintPassives` (:855), `textPassives` (:901), `passiveTooltipStyle` (:1127), the passive
  click handler (:1577)
- Modify: `src/main/java/com/efkrdnz/magical/client/screen/CodexLayout.java`
- Modify: `src/test/java/com/efkrdnz/magical/client/screen/CodexLayoutTest.java`
- Test: `src/test/java/com/efkrdnz/magical/client/screen/CodexRitualRowsTest.java`

- [ ] **Step 1: Write the failing tests**

Into `CodexLayoutTest`:

```java
    @Test
    void aRitualRowsCountdownBarStaysOnItsCard() {
        Rect card = CodexLayout.passiveCard(CodexLayout.LISTS_Y);
        assertInside(card, CodexLayout.ritualCountdownBar(CodexLayout.LISTS_Y));
    }
```

and a new `CodexRitualRowsTest`:

```java
    @Test
    void theTemporaryGroupsArePinnedAboveEverythingOwned() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.MANA_SKIN.id());
        state.grantRitualPassive(MagicPassiveContent.SANGUINE_MIGHT.id(), 400);
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 600);

        List<MagicPassiveDefinition> listed = MagicPyramidScreen.passiveRowsForTest(state);

        // Header, boon, header, price, header, mana skin. The two groups are above the owned one
        // because a ritual runs for a minute and the player is watching it run out.
        assertEquals(List.of(MagicPassiveContent.SANGUINE_MIGHT.id(), MagicPassiveContent.GLASS_BONES.id(),
                MagicPassiveContent.MANA_SKIN.id()), listed.stream().map(MagicPassiveDefinition::id).toList());
    }

    @Test
    void aRitualPassiveIsNeverListedTwice() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.SANGUINE_MIGHT.id(), 400);

        List<MagicPassiveDefinition> listed = MagicPyramidScreen.passiveRowsForTest(state);

        assertEquals(1, listed.size(), "the general walk must skip what the pinned groups already drew");
    }

    @Test
    void anExpiredRitualLeavesTheListAltogether() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.SANGUINE_MIGHT.id(), 1);
        state.tickRitualPassives();

        assertTrue(MagicPyramidScreen.passiveRowsForTest(state).isEmpty());
    }
```

`passiveRows` is private and returns a private record, so add a package-visible
`passiveRowsForTest(PlayerMagicState)` returning just the definitions in row order, with a comment
saying it exists for the test and nothing else calls it.

- [ ] **Step 2: Run them and watch them fail**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.client.screen.*"
```

- [ ] **Step 3: Pin the two groups**

In `passiveRows`, before the existing loop:

```java
        // Pinned above everything owned: a ritual lasts a minute and the player is watching it run
        // out, so it must never be somewhere they have to scroll to.
        appendRitualGroup(rows, state, MagicPassiveContent.ritualBoons(),
                "screen.magical.passive_group_ritual_boon");
        appendRitualGroup(rows, state, MagicPassiveContent.ritualPrices(),
                "screen.magical.passive_group_ritual_price");
```

`appendRitualGroup` walks `MagicPassiveContent.normalPassives()` in registration order, keeps those
in the given set with `state.ritualRemaining(id) > 0`, and emits a header only when at least one
survives. In the existing loop, skip anything `MagicPassiveContent.isRitual(definition.id())`.

- [ ] **Step 4: Draw the countdown**

Add to `CodexLayout`:

```java
    public static final int RITUAL_BAR_H = 2;

    /** The sliver of a ritual card that shows how much of its clock is left. */
    public static Rect ritualCountdownBar(int cardY) {
        Rect card = passiveCard(cardY);
        return new Rect("ritual countdown", card.x() + 2, card.bottom() - 2 - RITUAL_BAR_H,
                card.w() - 4, RITUAL_BAR_H);
    }
```

In `paintPassives`, for a ritual row draw `HudGlyphs`' hourglass stamp where
`MagicalGuiStyle.checkbox` would go, and fill the countdown bar to
`remaining / (float) fullDuration`. The granted total is not stored, so `fullDuration` is
`MagicContent.BLOOD_SACRIFICE.baseDurationTicks()` for a boon and 1.5x that for a price - close
enough for a two-pixel bar, and it saves a field that would have to be saved, synced and copied.

In `textPassives`, replace the Enabled/Disabled line for a ritual row with the countdown:

```java
    /** A countdown, the way a clock reads it: 1:07, not 67 ticks and not 3.35 seconds. */
    private static String mmss(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }
```

In `passiveTooltipStyle`, return `PassiveTooltipStyle.CURSE` for a ritual price. In the tooltip
body, add `screen.magical.sacrifice_temporary` with the countdown, and
`screen.magical.sacrifice_amplified` with `SacrificeBudget.amplification(...)` when Hellbroker is on
and the entry is one of the amplified prices.

- [ ] **Step 5: Refuse the toggle**

In the passive click handler, before the toggle:

```java
            if (MagicPassiveContent.isRitual(definition.id())) {
                // A boon you can switch off is a boon you took for free, and a price you can switch
                // off is not a price.
                return true;
            }
```

- [ ] **Step 6: Run the screen and layer tests**

```bash
./gradlew test -q --tests "com.efkrdnz.magical.client.screen.*" --tests "com.efkrdnz.magical.magic.PyramidLayersTest"
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/client src/test/java/com/efkrdnz/magical
git commit -m "feat: the codex pins what a ritual gave you and counts it down"
```

---

## Task 10: Command, game test, captures, docs

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/passive/BloodSacrificeGameTests.java`
- Modify: `CLAUDE.md`
- Modify: `NewContentIntegrationTest`, `HudLangKeysTest`, `MagicalTooltipAssetsTest`,
  `VisualProfilesTest` as their assertions demand

- [ ] **Step 1: Write the failing game test**

`BloodSacrificeGameTests`, modelled on `EldritchGameTests`:

```java
    @GameTest(template = OPEN_FIELD)
    public static void aSealedPactOutlivesItsBoon(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInCorner();
        PlayerMagicState state = state(player);
        state.addBloodVessel(BloodSacrificeService.RITUAL_COST);

        helper.assertTrue(BloodSacrificeService.seal(player, state,
                List.of(MagicPassiveContent.SANGUINE_MIGHT.id()),
                List.of(MagicPassiveContent.GLASS_BONES.id())), "a fair pact must seal");
        helper.assertTrue(state.bloodVessel() == 0, "the ritual takes the whole Vessel");

        int boonTicks = state.ritualRemaining(MagicPassiveContent.SANGUINE_MIGHT.id());
        int priceTicks = state.ritualRemaining(MagicPassiveContent.GLASS_BONES.id());
        helper.assertTrue(priceTicks == Math.round(boonTicks * BloodSacrificeService.PRICE_DURATION_FACTOR),
                "the price runs half again as long");

        for (int i = 0; i < boonTicks; i++) {
            state.tickRitualPassives();
        }
        helper.assertFalse(state.hasPassive(MagicPassiveContent.SANGUINE_MIGHT.id()), "the boon is spent");
        helper.assertTrue(state.hasPassive(MagicPassiveContent.GLASS_BONES.id()), "the price is not");
        helper.succeed();
    }

    @GameTest(template = OPEN_FIELD)
    public static void anEmptyVesselSealsNothingAndTakesNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInCorner();
        PlayerMagicState state = state(player);
        state.addBloodVessel(BloodSacrificeService.RITUAL_COST - 1);

        helper.assertFalse(BloodSacrificeService.seal(player, state,
                List.of(MagicPassiveContent.SANGUINE_MIGHT.id()),
                List.of(MagicPassiveContent.GLASS_BONES.id())), "one short is short");
        helper.assertTrue(state.bloodVessel() == BloodSacrificeService.RITUAL_COST - 1,
                "a refused pact leaves the Vessel exactly as full as it was");
        helper.succeed();
    }

    @GameTest(template = OPEN_FIELD)
    public static void aFizzledCastPaysAndDoesNothing(GameTestHelper helper) {
        // Spell Fizzle at its ceiling, so the roll is certain enough to assert on.
        ServerPlayer player = helper.makeMockServerPlayerInCorner();
        PlayerMagicState state = state(player);
        state.unlock(MagicContent.MANA_BOLT.id());
        state.grantRitualPassive(MagicPassiveContent.SPELL_FIZZLE.id(), 400);
        // ... cast repeatedly, assert at least one cast spent mana and started a cooldown with no
        //     entity spawned.
        helper.succeed();
    }
```

- [ ] **Step 2: Run it and watch it fail, then pass**

```bash
./gradlew runGameTestServer
```

- [ ] **Step 3: Add the command**

`/magical sacrifice` opens the screen without a cast, and
`/magical sacrifice seal <boon> <price>` seals one directly, both for captures. Follow the
`creator` / `create` pair in `MagicalCommands`; suggest passive ids from
`SacrificeCatalogue.BOONS` and `PRICES`.

- [ ] **Step 4: Full build and game tests**

```bash
./gradlew build -q
./gradlew runGameTestServer
```

Expected: zero failures from both. Fix whatever the enumerating tests demand:
`NewContentIntegrationTest.newSkills()` gains `blood_sacrifice`; `HudLangKeysTest` may want the new
message keys; `VisualProfilesTest.rosterHasNoVisualCollisions` fails if `THORN_CROWN` turns out to
be taken, in which case pick another unused emblem (`WING`, `GEAR`, `COMPASS` and `VOID_RING` were
free at the time of writing).

- [ ] **Step 5: Captures**

`run/options.txt` must have `pauseOnLostFocus:false`. One launch for the screen, one for the codex
with a pact running:

```bash
./gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;magical reset;magical hud race human;magical unlockall;magical hud vessel 100;time set day;101:magical sacrifice" -PautoScreenshot=112,126 -PautoExit
```

```bash
./gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;magical reset;magical hud race human;magical unlockall;magical hud vessel 100;101:magical sacrifice seal sanguine_might glass_bones;141:magical codex" -PautoClick="158:431,45" -PautoScreenshot=152,166 -PautoExit
```

`-PautoClick` coordinates are GUI units, not window pixels: divide the window position by the GUI
scale of 2. The Passives tab is the fourth, at `431,45`. Crop the results and send them with
`SendUserFile`.

- [ ] **Step 6: `CLAUDE.md`**

Extend the Blood school paragraph: seven actives rather than six, the ritual and its full-Vessel
price, `magical:blood_price` and what it bypasses, the two pinned codex groups, Hellbroker's capped
amplification, the capture commands above, and a link to the spec.

- [ ] **Step 7: Commit, push, refresh the peer checkout**

```bash
git add -A ':!gradlew' ':!.claude/settings.local.json' ':!*.log' ':!bash.exe.stackdump'
git commit -m "feat: blood sacrifice, end to end"
git push origin main
git -C "E:/minecraft mods/magical" switch --detach main
```

---

## Self-review

**Spec coverage.** Every section of the spec maps to a task: the price to 1, the timer to 2, the
catalogue and budget to 3, the effects to 4, the fizzle to 5, the skill and service to 6, the
network to 7, the screen to 8, the codex to 9, and the command, game test, captures and docs to 10.
Hellbroker is spread across 3 (the budget, the registration) and 4 (the amplification each price
wears).

**Placeholders.** Tasks 4 and 8 name the remaining methods rather than writing all thirty-three
bodies and the whole screen inline. That is deliberate and bounded: each is "one method per row of
the spec's table, in the shape of the three shown", and the spec's tables carry every number.
Nothing else in the plan defers a decision.

**Type consistency.** `activeRitualPriceCount` is the name in Tasks 2, 3 and 4 - the spec's prose
says "ritual curse count", and the code name wins. `SacrificeCatalogue.BOONS`/`PRICES` are
`List<ResourceLocation>` everywhere. `SacrificeBudget.amplify(base, clamp, activePrices, hellbroker)`
takes the same four arguments in Tasks 3 and 4. `BloodSacrificeService.Refusal` is the enum
`validate` returns in Task 6 and the screen never sees, because the screen refuses the same cases
before it can send them.

**Ordering.** Task 2's test cannot compile until Task 3 registers the two passives it names, and
Task 7's client handler cannot compile until Task 8 writes the screen. Both are called out in the
step that leaves them red.
