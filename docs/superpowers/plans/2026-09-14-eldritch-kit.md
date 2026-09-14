# Eldritch Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The eldritch school (layer -5): the Notice gauge, six actives that call three modelled creatures (tentacle, eye, maw), a Bedrock geometry loader and procedural animation so the user's Blockbench exports drop in file for file, two passives, HUD and codex readouts, tests and captures - everything except the models themselves, which ship as placeholders.

**Architecture:** Server side mirrors the blood kit: one `SkillModule` per active in `magic/skill/eldritch/`, a price table and a service (`EldritchPrices`, `EldritchService`), a passive handler (`EldritchPassives`), one entity (`EldritchConstructEntity extends SpellEffectEntity`) carrying a model name, an anchor and a scale. Client side: `GeoModelParser` (pure) reads `.geo.json`, `GeoModelBaker` turns it into vanilla `ModelPart`s, `EldritchModels` is the reload listener holding them by name, `EldritchPose` (pure) computes the tentacle chain, the gaze and the jaws each frame, and `EldritchConstructRenderer extends ProfileRendererShell` paints the profile FX and then the model with a lifecycle alpha and an optional glow layer.

**Tech Stack:** NeoForge 21.4.157 / Minecraft 1.21.4 / Java 21, Gradle 9.2 (`./gradlew test -q`, `./gradlew build -q`, `./gradlew runGameTestServer`, `./gradlew runClient -PquickPlay=...`), JUnit 5, Gson (bundled), vanilla `LayerDefinition`/`ModelPart`, NeoForge `AddClientReloadListenersEvent`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-14-eldritch-kit-design.md`. Every number below is copied from it.
- Work only in `E:\magical-port` (linked worktree; the repo lives at `E:\minecraft mods\magical\.git`). Never bare `git stash`. Never commit `gradlew`, `.claude/settings.local.json`, `bash.exe.stackdump`.
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`; one heredoc per commit call, no apostrophes in the message.
- TDD: write the test, watch it fail for the right reason, then the code. Tests using Minecraft classes bootstrap with `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` (and `MagicCastContent.init()` when handlers matter).
- Layer: every eldritch skill is `MagicSchool.ELDRITCH`, tier `-5`, attribute `MagicAttribute.ELDRITCH` (automatic from the school).
- Game tests: template `unwaking_empty` is 5x5x5 air walled in barriers; keep every entity and aim ray inside relative 0..4 (player at (2,2,2), zombie at (4,2,2)).
- Captures: `run/options.txt` has `pauseOnLostFocus:false`; after `magical reset` choose `magical hud race human`; keep screenshot ticks off multiples of ten; equip slots are zero-based, key mappings one-based.
- Bash heredocs choke on apostrophes and long ones on path length: multi-file edits go through a scratchpad python script; big new files through Write.
- Pure classes (`GeoModel`, `GeoModelParser`, `EldritchPose`, `EldritchPrices`) import nothing from `net.minecraft` except `ResourceLocation` where the existing pattern uses it.

## File map

| File | Responsibility |
|---|---|
| `magic/PlayerMagicState.java` (modify) | `notice` 0..100: field, accessors, copy, save, load |
| `magic/eldritch/EldritchPrices.java` (new) | the Notice price table |
| `magic/EldritchService.java` (new) | who is an eldritch mage, cost with points, notice with rung announcements, potency, decay, Deep Bargain |
| `magic/MagicContent.java` (modify) | six definitions |
| `magic/MagicPassiveContent.java` (modify) | `LIDLESS`, `DEEP_BARGAIN` |
| `magic/passive/EldritchPassives.java` (new) | decay, Watched retargeting, Noticed grasp, the Skin wards, the Eye amp, Deep Bargain mana |
| `magic/passive/ClassPassiveEffects.java` (modify) | the handler in `HANDLERS` |
| `magic/cast/MagicCastContentEldritch.java` (new) + `MagicCastContent.java` (modify) | registers the six modules |
| `magic/skill/eldritch/*Skill.java` (new, six) | one module each |
| `entity/fx/EldritchConstructEntity.java` (new) + `registry/MagicalEntities.java` (modify) | the construct entity |
| `magic/visual/EmblemId.java` + `client/renderer/fx/FxTextures.java` (modify) | six emblems |
| `client/model/geo/GeoModel.java`, `GeoModelParser.java`, `GeoFormatException.java` (new) | pure geometry model + parser |
| `client/model/geo/GeoModelBaker.java` (new) | geometry to `ModelPart` |
| `client/model/eldritch/EldritchModels.java` (new) | reload listener and registry |
| `client/renderer/eldritch/EldritchPose.java` (new) | pure motion |
| `client/renderer/eldritch/EldritchConstructRenderer.java` (new) | the renderer |
| `client/MagicalClientEvents.java` (modify) | renderer + reload listener registration |
| `client/hud/HudState.java`, `client/screen/MagicPyramidScreen.java`, `registry/MagicalCommands.java` (modify) | notice line, cost line, `magical hud notice` |
| `scripts/eldritch-placeholders.py` (new) + `assets/magical/models/entity/eldritch/*.geo.json`, `textures/entity/eldritch/*.png` | placeholders |
| `assets/magical/lang/en_us.json` (modify) | keys |
| tests: `magic/EldritchPricesTest`, `magic/EldritchServiceTest`, `magic/EldritchSchoolTest`, `client/model/geo/GeoModelParserTest`, `client/renderer/eldritch/EldritchPoseTest`, `client/hud/HudLangKeysTest` (PRESENT), `magic/passive/EldritchGameTests` (main source set, like the blood game tests) | |

---

### Task 1: Notice on the state, the price table and the service

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java` (next to `corruption`: field line 99, accessors 318-350, copy 1902, save 2002, load 2151)
- Create: `src/main/java/com/efkrdnz/magical/magic/eldritch/EldritchPrices.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/EldritchService.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/EldritchPricesTest.java`, `src/test/java/com/efkrdnz/magical/magic/EldritchServiceTest.java`

**Interfaces:**
- Produces: `PlayerMagicState.MAX_NOTICE = 100`, `int notice()`, `void addNotice(int)`, `void setNotice(int)` (clamped); `EldritchPrices.base(ResourceLocation) : int` (throws `IllegalArgumentException` for unknown ids); `EldritchService.WATCHED_AT = 50`, `NOTICED_AT = 100`, `MAX_POTENCY = 1.5F`, `DECAY_PER_SLOW_TICK = 1`, `WATCHED_RANGE = 24.0D`, `REACH_INTERVAL = 200`, `isEldritchMage(PlayerMagicState)`, `cost(MagicSkillResolvedStats) : int`, `scale(MagicSkillResolvedStats, int) : int`, `notice(ServerPlayer, PlayerMagicState, int) : int`, `potency(PlayerMagicState) : float`, `rung(int) : int`, `manaWaived(PlayerMagicState) : boolean`, `decayStep(PlayerMagicState) : int`.
- Consumes: `DarkService`/`BloodService` shapes (`MagicPrice.waived`, `MagicSkillResolvedStats.costScale()`), `MagicPassiveContent.LIDLESS` / `DEEP_BARGAIN` (Task 2; the three tests that need them are listed here but land with Task 2).

- [ ] **Step 1: Write the failing tests**

`EldritchPricesTest` (plain JUnit, no bootstrap):

```java
package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.efkrdnz.magical.magic.eldritch.EldritchPrices;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** What each call to the deep costs in Notice before the points move it. */
class EldritchPricesTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("magical", path);
    }

    @Test
    void everyCallHasItsPriceAndTheCallIsTheCheapestBecauseItIsPaidPerPulse() {
        assertEquals(12, EldritchPrices.base(id("grasp_of_the_deep")));
        assertEquals(8, EldritchPrices.base(id("unblinking_eye")));
        assertEquals(15, EldritchPrices.base(id("hungering_maw")));
        assertEquals(6, EldritchPrices.base(id("tendril_lash")));
        assertEquals(10, EldritchPrices.base(id("skin_of_the_deep")));
        assertEquals(5, EldritchPrices.base(id("call_of_the_deep")));
    }

    @Test
    void aSkillOutsideTheKitIsAnErrorRatherThanFree() {
        assertThrows(IllegalArgumentException.class, () -> EldritchPrices.base(id("vein_walk")));
        assertThrows(IllegalArgumentException.class, () -> EldritchPrices.base(null));
    }
}
```

`EldritchServiceTest` (bootstrapped):

```java
package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Notice gauge: a heat that rises with every call and cools in silence, and what it buys. */
class EldritchServiceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void noticeIsHeldToItsRange() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(0, state.notice());
        state.addNotice(130);
        assertEquals(PlayerMagicState.MAX_NOTICE, state.notice(), "the ceiling holds");
        state.addNotice(-500);
        assertEquals(0, state.notice(), "and so does the floor");
    }

    @Test
    void noticeSurvivesSaveLoadAndCopy() {
        PlayerMagicState state = new PlayerMagicState();
        state.setNotice(37);
        assertEquals(37, PlayerMagicState.load(state.save()).notice());
        assertEquals(37, state.copy().notice());
    }

    @Test
    void theRungsSitAtFiftyAndTheTop() {
        assertEquals(0, EldritchService.rung(0));
        assertEquals(0, EldritchService.rung(49));
        assertEquals(1, EldritchService.rung(EldritchService.WATCHED_AT));
        assertEquals(1, EldritchService.rung(99));
        assertEquals(2, EldritchService.rung(EldritchService.NOTICED_AT));
    }

    @Test
    void theDeepGivesMoreToTheOnesItWatches() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(1.0F, EldritchService.potency(state), 1.0E-6F, "unwatched, a call is ordinary");
        state.setNotice(PlayerMagicState.MAX_NOTICE);
        assertEquals(EldritchService.MAX_POTENCY, EldritchService.potency(state), 1.0E-6F);
        state.setNotice(50);
        assertEquals(1.25F, EldritchService.potency(state), 1.0E-6F, "and it is linear between");
    }

    // Land with Task 2 (they name the passives):
    @Test
    void noticeCoolsOnePointASlowTickAndHalfAsFastForTheLidless() {
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(1, EldritchService.decayStep(state));
        assertEquals(1, EldritchService.decayStep(state));
        state.unlockPassive(MagicPassiveContent.LIDLESS.id());
        int over = 0;
        for (int i = 0; i < 10; i++) {
            over += EldritchService.decayStep(state);
        }
        assertEquals(5, over, "Lidless cools every other slow tick");
    }

    @Test
    void theBargainWaivesManaOnlyAtTheTop() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.DEEP_BARGAIN.id());
        state.setNotice(99);
        assertFalse(EldritchService.manaWaived(state), "Watched is not Noticed");
        state.setNotice(PlayerMagicState.MAX_NOTICE);
        assertTrue(EldritchService.manaWaived(state));
        PlayerMagicState without = new PlayerMagicState();
        without.setNotice(PlayerMagicState.MAX_NOTICE);
        assertFalse(EldritchService.manaWaived(without), "the top rung alone waives nothing");
    }

    @Test
    void aPlayerWhoHasNeverCalledTheDeepIsNotAnEldritchMage() {
        assertFalse(EldritchService.isEldritchMage(new PlayerMagicState()));
        PlayerMagicState state = new PlayerMagicState();
        state.unlock(MagicContent.TENDRIL_LASH.id());
        assertTrue(EldritchService.isEldritchMage(state));
    }
}
```

`unlockPassive`: use the real passive-unlock method name on `PlayerMagicState` (`grep -n "public .* unlockPassive\|public .* addPassive\|passiveUnlocked" PlayerMagicState.java`); `isPassiveEnabled(id)` is what the service reads.

- [ ] **Step 2: Run to verify RED**

Run: `./gradlew test -q --tests "com.efkrdnz.magical.magic.EldritchPricesTest" --tests "com.efkrdnz.magical.magic.EldritchServiceTest"`
Expected: compilation failure naming `EldritchPrices`, `EldritchService`, `notice()`.

- [ ] **Step 3: The state**

In `PlayerMagicState`: after `private int corruption;` add `private int notice;`. After `setCorruption` add:

```java
    /** The point past which the deep is looking straight at you. A ceiling, and the top rung. */
    public static final int MAX_NOTICE = 100;

    /** How much of the deep's attention you have drawn, 0..{@link #MAX_NOTICE}. A heat, not a debt: it cools. */
    public int notice() {
        return notice;
    }

    public void addNotice(int amount) {
        setNotice(notice + amount);
    }

    public void setNotice(int value) {
        notice = clamp(value, 0, MAX_NOTICE);
    }
```

Copy: `copy.notice = notice;` after `copy.corruption = corruption;`. Save: `tag.putInt("notice", notice);` after the corruption line. Load: `state.notice = clamp(tag.getInt("notice"), 0, MAX_NOTICE);` after the corruption line.

- [ ] **Step 4: The price table**

```java
package com.efkrdnz.magical.magic.eldritch;

import com.efkrdnz.magical.MagicalMod;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * What each call to the deep draws in Notice before the points move it.
 *
 * <p>One table, as {@code BloodPrices}: the codex prints it without loading a handler, and the
 * roster and the list are held together by a test. The points apply through
 * {@code EldritchService.cost}, at the factor mana is billed at.
 */
public final class EldritchPrices {

    private static final Map<String, Integer> BASE = Map.of(
            "grasp_of_the_deep", 12,
            "unblinking_eye", 8,
            "hungering_maw", 15,
            "tendril_lash", 6,
            "skin_of_the_deep", 10,
            // Paid again at every pulse of the call, which is why one pulse is the cheapest line.
            "call_of_the_deep", 5);

    private EldritchPrices() {
    }

    /** @throws IllegalArgumentException outside the kit: unknown is an error, not free. */
    public static int base(ResourceLocation skillId) {
        Integer base = skillId != null && MagicalMod.MODID.equals(skillId.getNamespace())
                ? BASE.get(skillId.getPath()) : null;
        if (base == null) {
            throw new IllegalArgumentException("no notice price for " + skillId);
        }
        return base;
    }
}
```

- [ ] **Step 5: The service**

```java
package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.eldritch.EldritchPrices;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * What eldritch magic costs: being noticed.
 *
 * <p>Every call borrows a piece of something enormous under the world, and every borrowing makes it
 * more aware of the borrower. Notice is that awareness: it rises with each cast, cools in silence,
 * and pays back - the deep gives more to the ones it is watching - until it turns to look, and
 * then everything else does too.
 */
public final class EldritchService {
    /** Watched: hostile things nearby turn to the caster every slow tick. */
    public static final int WATCHED_AT = 50;
    /** Noticed: the deep reaches for the caster themselves. The top of the gauge. */
    public static final int NOTICED_AT = PlayerMagicState.MAX_NOTICE;
    /** A call made under the deep's full attention is this many times as strong. */
    public static final float MAX_POTENCY = 1.5F;
    public static final int DECAY_PER_SLOW_TICK = 1;
    /** How far the Watched rung reaches for things to turn toward the caster. */
    public static final double WATCHED_RANGE = 24.0D;
    /** Ticks between the deep's grasps at the top rung. */
    public static final int REACH_INTERVAL = 200;

    private EldritchService() {
    }

    public static boolean isEldritchMage(PlayerMagicState state) {
        for (ResourceLocation id : state.unlockedSkills()) {
            MagicSkillDefinition skill = MagicContent.get(id);
            if (skill != null && skill.school() == MagicSchool.ELDRITCH) {
                return true;
            }
        }
        return false;
    }

    /** The Notice a cast of this skill draws with the points applied: never below one. */
    public static int cost(MagicSkillResolvedStats stats) {
        return scale(stats, EldritchPrices.base(stats.definition().id()));
    }

    public static int scale(MagicSkillResolvedStats stats, int base) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(base * stats.costScale()));
    }

    /**
     * Draws the deep's attention. Always succeeds - a call that could be refused for being too
     * loud is not the school - and announces each rung as it is crossed.
     *
     * @return how much Notice was actually taken on
     */
    public static int notice(ServerPlayer player, PlayerMagicState state, int amount) {
        if (MagicPrice.waived(player) || amount <= 0) {
            return 0;
        }
        int before = state.notice();
        state.addNotice(amount);
        int taken = state.notice() - before;
        if (taken > 0 && rung(state.notice()) > rung(before)) {
            announce(player, rung(state.notice()));
        }
        return taken;
    }

    public static float potency(PlayerMagicState state) {
        return 1.0F + (MAX_POTENCY - 1.0F) * state.notice() / (float) PlayerMagicState.MAX_NOTICE;
    }

    /** 0 unseen, 1 Watched, 2 Noticed. */
    public static int rung(int notice) {
        return notice >= NOTICED_AT ? 2 : notice >= WATCHED_AT ? 1 : 0;
    }

    /** Deep Bargain: at the top rung, and only there, the calls cost no mana. */
    public static boolean manaWaived(PlayerMagicState state) {
        return state.isPassiveEnabled(MagicPassiveContent.DEEP_BARGAIN.id()) && state.notice() >= NOTICED_AT;
    }

    /**
     * How much Notice cools this slow tick. Lidless halves it by skipping every other tick; the
     * phase is kept in the passive counter so the halving is exact rather than a coin toss.
     */
    public static int decayStep(PlayerMagicState state) {
        if (!state.isPassiveEnabled(MagicPassiveContent.LIDLESS.id())) {
            return DECAY_PER_SLOW_TICK;
        }
        ResourceLocation id = MagicPassiveContent.LIDLESS.id();
        int phase = state.passiveCounter(id);
        state.setPassiveCounter(id, phase == 0 ? 1 : 0);
        return phase == 0 ? 0 : DECAY_PER_SLOW_TICK;
    }

    private static void announce(ServerPlayer player, int rung) {
        String key = rung >= 2 ? "message.magical.deep_noticed" : "message.magical.deep_watched";
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_AQUA), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.7F, rung >= 2 ? 0.5F : 0.7F);
    }
}
```

`passiveCounter(id)` / `setPassiveCounter(id, value)` exist on `PlayerMagicState` (used by Coagulate). Land Tasks 1 and 2 together if the passive constants make Task 1 alone uncompilable.

- [ ] **Step 6: Run to verify GREEN**

Run: `./gradlew test -q --tests "com.efkrdnz.magical.magic.EldritchPricesTest" --tests "com.efkrdnz.magical.magic.EldritchServiceTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/PlayerMagicState.java src/main/java/com/efkrdnz/magical/magic/eldritch/EldritchPrices.java src/main/java/com/efkrdnz/magical/magic/EldritchService.java src/test/java/com/efkrdnz/magical/magic/EldritchPricesTest.java src/test/java/com/efkrdnz/magical/magic/EldritchServiceTest.java
git commit -q -F - <<'EOF'
feat: notice - the eldritch gauge, its price table and its service

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 2: The roster - six definitions, two passives, the handler, the readouts

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicContent.java` (after `BLOOD_RITE`, line 62)
- Modify: `src/main/java/com/efkrdnz/magical/magic/MagicPassiveContent.java` (after `WILLING`)
- Create: `src/main/java/com/efkrdnz/magical/magic/passive/EldritchPassives.java`
- Modify: `src/main/java/com/efkrdnz/magical/magic/passive/ClassPassiveEffects.java:36-44` (`HANDLERS`)
- Modify: `src/main/java/com/efkrdnz/magical/client/hud/HudState.java:405-463` (notice line), `src/main/java/com/efkrdnz/magical/client/screen/MagicPyramidScreen.java:609-627` (cost line), `src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java` (`hud notice`, next to `hud corruption`)
- Modify: `src/main/resources/assets/magical/lang/en_us.json`
- Test: `src/test/java/com/efkrdnz/magical/magic/EldritchSchoolTest.java`, `src/test/java/com/efkrdnz/magical/client/hud/HudLangKeysTest.java` (PRESENT gains `hud.magical.notice_line`)

**Interfaces:**
- Produces: `MagicContent.GRASP_OF_THE_DEEP`, `UNBLINKING_EYE`, `HUNGERING_MAW`, `TENDRIL_LASH`, `SKIN_OF_THE_DEEP`, `CALL_OF_THE_DEEP`; `MagicPassiveContent.LIDLESS`, `DEEP_BARGAIN`; `EldritchPassives` (handles both; `EldritchPassives.LIDLESS_SIZE = 1.2F`; a package-private `reachForTheCaster(ServerPlayer, PlayerMagicState)` that Task 8 fills).
- Consumes: Task 1.

- [ ] **Step 1: Write the failing roster test**

```java
package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.cast.MagicCastContent;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import com.efkrdnz.magical.magic.eldritch.EldritchPrices;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The -5 layer as a whole: six calls, two passives, one price list, one door in. */
class EldritchSchoolTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicCastContent.init();
    }

    private static List<MagicSkillDefinition> eldritchSkills() {
        return MagicContent.orderedSkillIds().stream()
                .map(MagicContent::get)
                .filter(skill -> skill.school() == MagicSchool.ELDRITCH)
                .toList();
    }

    @Test
    void theLayerHoldsExactlySixCallsAndTwoPassives() {
        assertEquals(List.of(MagicContent.GRASP_OF_THE_DEEP, MagicContent.UNBLINKING_EYE,
                        MagicContent.HUNGERING_MAW, MagicContent.TENDRIL_LASH,
                        MagicContent.SKIN_OF_THE_DEEP, MagicContent.CALL_OF_THE_DEEP),
                eldritchSkills(), "the school is budgeted at six actives, and these are the six");
        for (MagicPassiveDefinition passive : List.of(MagicPassiveContent.LIDLESS, MagicPassiveContent.DEEP_BARGAIN)) {
            assertTrue(MagicPassiveContent.isForbiddenPassive(passive.id()), passive.id() + " is one of the two");
        }
    }

    @Test
    void everyCallSitsOnItsOwnLayerWithItsOwnAttributeAndPaysMana() {
        for (MagicSkillDefinition skill : eldritchSkills()) {
            assertEquals(-5, skill.tier(), skill.id() + " belongs on the eldritch layer");
            assertEquals(MagicAttribute.ELDRITCH, skill.attribute(), skill.id() + " attribute");
            // The one forbidden school that pays both: mana in the pipeline, Notice in the handler.
            assertTrue(skill.baseManaCost() > 0, skill.id() + " is paid in mana as well as Notice");
            assertTrue(EldritchPrices.base(skill.id()) > 0, skill.id() + " has no notice price");
        }
    }

    @Test
    void everyCallIsWiredCommandableAndTranslated() {
        List<String> commandable = MagicContent.commandIds();
        for (MagicSkillDefinition skill : eldritchSkills()) {
            assertNotNull(SkillCastRegistry.get(skill.id()), skill.id() + " has no cast handler");
            assertTrue(commandable.contains(skill.id().getPath()), skill.id() + " cannot be unlocked by command");
            assertTrue(skill.nameKey().startsWith("skill.magical."), skill.id() + " name key");
            assertEquals(skill.nameKey() + ".desc", skill.descriptionKey(), skill.id() + " desc key");
        }
    }
}
```

The handler assertion stays RED until Task 13 registers the last module.

- [ ] **Step 2: Run to verify RED**

Run: `./gradlew test -q --tests "com.efkrdnz.magical.magic.EldritchSchoolTest"`
Expected: compilation failure on `GRASP_OF_THE_DEEP` and `LIDLESS`.

- [ ] **Step 3: The definitions** (after the `BLOOD_RITE` line in `MagicContent`; the register signature is `(path, school, type, tier, requiredLevel, damage, speed, size, mana, cooldown, duration, knockback, barrierRestore, color)`):

```java
    // ELDRITCH, layer -5: six calls to something under the world, paid in mana and in being noticed.
    // Stats per docs/superpowers/specs/2026-09-14-eldritch-kit-design.md.
    public static final MagicSkillDefinition GRASP_OF_THE_DEEP = register("grasp_of_the_deep", MagicSchool.ELDRITCH, MagicSkillType.BURST, -5, 0, 4.0F, 1.0F, 1.0F, 22, 200, 80, 0.0F, 0, 0x2FBF9E);
    public static final MagicSkillDefinition UNBLINKING_EYE = register("unblinking_eye", MagicSchool.ELDRITCH, MagicSkillType.BURST, -5, 0, 2.0F, 1.0F, 1.0F, 18, 300, 400, 0.0F, 0, 0x5FEFD0);
    public static final MagicSkillDefinition HUNGERING_MAW = register("hungering_maw", MagicSchool.ELDRITCH, MagicSkillType.BURST, -5, 0, 14.0F, 1.0F, 1.0F, 26, 240, 60, 0.8F, 0, 0x1A7A66);
    public static final MagicSkillDefinition TENDRIL_LASH = register("tendril_lash", MagicSchool.ELDRITCH, MagicSkillType.BURST, -5, 0, 9.0F, 1.4F, 1.0F, 14, 90, 40, 0.6F, 0, 0x3FD9B4);
    public static final MagicSkillDefinition SKIN_OF_THE_DEEP = register("skin_of_the_deep", MagicSchool.ELDRITCH, MagicSkillType.BURST, -5, 0, 5.0F, 1.0F, 1.0F, 30, 600, 400, 0.0F, 0, 0x06322A);
    public static final MagicSkillDefinition CALL_OF_THE_DEEP = register("call_of_the_deep", MagicSchool.ELDRITCH, MagicSkillType.BURST, -5, 0, 6.0F, 1.0F, 1.0F, 12, 500, 200, 0.0F, 0, 0x8FF5E0);
```

Check for a lowest-layer constant: `grep -n "minTier\|-2\b" MagicContent.java CodexLayout.java` - if the pyramid computes its lowest layer from the roster, -5 appears automatically; if a constant caps it, widen it and run `CodexLayoutTest`.

- [ ] **Step 4: The passives** (after `WILLING` in `MagicPassiveContent`):

```java
    // ELDRITCH, layer -5. Two, per the budget of six actives and two passives. Both change what
    // being noticed does to you: one keeps you seen for longer, the other makes the top a home.
    public static final MagicPassiveDefinition LIDLESS = forbiddenPassive("lidless", 0x5FEFD0);
    public static final MagicPassiveDefinition DEEP_BARGAIN = forbiddenPassive("deep_bargain", 0x06322A);
```

- [ ] **Step 5: The handler**

```java
package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastAdjustment;
import com.efkrdnz.magical.magic.service.SkillTargets;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * What Notice does to an eldritch mage, twice a second: it cools, and past each rung it acts.
 *
 * <p>Watched: everything hostile in range turns to look. Noticed: the deep itself reaches up and
 * takes hold of the caster for a moment, every {@link EldritchService#REACH_INTERVAL} ticks.
 * The two passives are read here and by the service: Lidless slows the cooling and grows the
 * calls, Deep Bargain waives their mana at the top.
 */
public final class EldritchPassives implements ClassPassiveHandler {

    /** Lidless: every construct reaches and is drawn this much larger. */
    public static final float LIDLESS_SIZE = 1.2F;

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(MagicPassiveContent.LIDLESS.id(), MagicPassiveContent.DEEP_BARGAIN.id());
    }

    @Override
    public void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, CastAdjustment out) {
        if (definition.school() != MagicSchool.ELDRITCH) {
            return;
        }
        if (EldritchService.manaWaived(state)) {
            out.mana = 0.0F;
        }
        if (state.isPassiveEnabled(MagicPassiveContent.LIDLESS.id())) {
            out.size *= LIDLESS_SIZE;
        }
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        if (!EldritchService.isEldritchMage(state) || state.notice() <= 0) {
            return;
        }
        int rung = EldritchService.rung(state.notice());
        state.addNotice(-EldritchService.decayStep(state));
        if (rung >= 1) {
            for (LivingEntity hostile : SkillTargets.hostilesWithin(player.serverLevel(), player, player.position(), EldritchService.WATCHED_RANGE)) {
                if (hostile instanceof Mob mob && mob.getTarget() == null) {
                    mob.setTarget(player);
                }
            }
        }
        if (rung >= 2 && player.tickCount % EldritchService.REACH_INTERVAL < ClassPassiveEffects.SLOW_TICK_INTERVAL) {
            reachForTheCaster(player, state);
        }
    }

    /** The deep takes hold of what it has seen. Grasp of the Deep supplies the grasp (Task 8). */
    static void reachForTheCaster(ServerPlayer player, PlayerMagicState state) {
        // Task 8 replaces this body with GraspOfTheDeepSkill.reachFor(player, state).
    }

    @Override
    public void forget(UUID playerId) {
    }
}
```

Add `new EldritchPassives()` after `new DarkPassives()` in `ClassPassiveEffects.HANDLERS`. Confirm `adjustCast` runs for every cast and that `out.mana` scales `manaCost()`: `grep -n "adjustCast\|\.mana" MagicCastingService.java`.

- [ ] **Step 6: Readouts and the command**

`HudState`: next to `boolean corruption = DarkService.isDarkMage(state);` add `boolean noticed = EldritchService.isEldritchMage(state);` and after the corruption caption block:

```java
            if (noticed && captions.size() < HudLayout.CAPTIONS_MAX) {
                captions.add(label(font, Component.translatable("hud.magical.notice_line", state.notice(), PlayerMagicState.MAX_NOTICE),
                        HudPalette.textTint(HudPalette.corruption().bright())));
            }
```

`MagicPyramidScreen.costLine`, before the `!= BLOOD` return:

```java
        if (skill.school() == MagicSchool.ELDRITCH) {
            return "Mana " + stats.manaCost() + " + Notice " + EldritchService.cost(stats) + cooldown;
        }
```

`MagicalCommands`: a `hud notice <amount>` literal copied from `hud corruption`, calling `data.setNotice(IntegerArgumentType.getInteger(context, "amount"))` with `IntegerArgumentType.integer(0, PlayerMagicState.MAX_NOTICE)`.

Lang (append near the blood/dark keys):

```json
  "skill.magical.grasp_of_the_deep": "Grasp of the Deep",
  "skill.magical.grasp_of_the_deep.desc": "A tentacle erupts where you looked and takes hold of the nearest thing: rooted for as long as it holds, crushed at every squeeze. With nothing to hold it sways and waits for whatever walks into reach.",
  "skill.magical.unblinking_eye": "Unblinking Eye",
  "skill.magical.unblinking_eye.desc": "A lidless eye hangs where you looked and watches. What it sees is revealed to you, takes a quarter more of your spells, and feels the stare itself.",
  "skill.magical.hungering_maw": "Hungering Maw",
  "skill.magical.hungering_maw.desc": "Jaws open in the ground where you looked and wait. The moment something stands in them they snap shut: a heavy bite, a launch, and every projectile in the bite eaten.",
  "skill.magical.tendril_lash": "Tendril Lash",
  "skill.magical.tendril_lash.desc": "A tentacle grows from your shoulder and whips through everything ahead of you, shoving it aside and leaving it too shaken to run.",
  "skill.magical.skin_of_the_deep": "Skin of the Deep",
  "skill.magical.skin_of_the_deep.desc": "Small tentacles grow from your back and take the hits meant for you, one each, and bite whatever struck. While any stand, nothing moves you.",
  "skill.magical.call_of_the_deep": "Call of the Deep",
  "skill.magical.call_of_the_deep.desc": "Hold to call. A great eye opens above you and, at every pulse, a tentacle erupts under something hostile in reach and holds it. Every pulse is paid again, in mana and in being noticed.",
  "passive.magical.lidless": "Lidless",
  "passive.magical.lidless.desc": "Notice cools half as fast, and every call you make reaches and is drawn a fifth larger.",
  "passive.magical.deep_bargain": "Deep Bargain",
  "passive.magical.deep_bargain.desc": "While the deep is looking straight at you, your calls cost no mana.",
  "message.magical.deep_watched": "Something below has turned to look.",
  "message.magical.deep_noticed": "It has seen you.",
  "hud.magical.notice_line": "%s/%s notice",
```

Add `"hud.magical.notice_line"` to `HudLangKeysTest.PRESENT`.

- [ ] **Step 7: Run the affected tests**

Run: `./gradlew test -q --tests "com.efkrdnz.magical.magic.EldritchSchoolTest" --tests "com.efkrdnz.magical.magic.EldritchServiceTest" --tests "com.efkrdnz.magical.magic.passive.ClassPassiveEffectsTest" --tests "com.efkrdnz.magical.client.hud.HudLangKeysTest" --tests "com.efkrdnz.magical.client.screen.CodexLayoutTest"`
Expected: everything green except `everyCallIsWiredCommandableAndTranslated` (no handlers yet).

- [ ] **Step 8: Commit**

```bash
git add -A src/main/java/com/efkrdnz/magical/magic src/main/java/com/efkrdnz/magical/client/hud/HudState.java src/main/java/com/efkrdnz/magical/client/screen/MagicPyramidScreen.java src/main/java/com/efkrdnz/magical/registry/MagicalCommands.java src/main/resources/assets/magical/lang/en_us.json src/test/java/com/efkrdnz/magical/magic/EldritchSchoolTest.java src/test/java/com/efkrdnz/magical/client/hud/HudLangKeysTest.java
git commit -q -F - <<'EOF'
feat: the eldritch roster - six definitions, two passives, the notice readouts

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 3: Six emblems

**Files:**
- Modify: `src/main/java/com/efkrdnz/magical/magic/visual/EmblemId.java` (after `SPLATTER`)
- Modify: `src/main/java/com/efkrdnz/magical/client/renderer/fx/FxTextures.java` (`emblemStrokes`, after `case SPLATTER`)

**Interfaces:**
- Produces: `EmblemId.TENDRIL`, `LIDLESS_EYE`, `FANGED_MAW`, `LASH`, `SCALES`, `DEEP_CALL`.

- [ ] **Step 1: The enum values** - `SPLATTER;` becomes `SPLATTER,` followed by:

```java
    // ELDRITCH. A curling tendril with suckers, an eye that has no lids, a ring of fangs, a whip
    // mid-crack, three rows of scales, and a call rising out of the deep.
    TENDRIL,
    LIDLESS_EYE,
    FANGED_MAW,
    LASH,
    SCALES,
    DEEP_CALL;
```

- [ ] **Step 2: The strokes** (the switch is exhaustive, so the build fails until these exist; `C cx,cy,r` circle, `L x1,y1,x2,y2` line, `A cx,cy,r,a0,a1` arc, `D cx,cy,r` disc, `R cx,cy,hw,hh` rectangle; coordinates -1..1):

```java
            // ELDRITCH.
            case TENDRIL -> "A-0.35,0.3,0.55,90,270;A0.25,-0.35,0.45,270,450;D-0.55,0.55,0.08;D-0.72,0.2,0.08;D-0.62,-0.15,0.08;D0.4,-0.7,0.08;D0.62,-0.42,0.08";
            case LIDLESS_EYE -> "C0,0,0.85;C0,0,0.55;R0,0,0.1,0.42;L-0.6,-0.6,-0.85,-0.85;L0.6,-0.6,0.85,-0.85;L-0.6,0.6,-0.85,0.85;L0.6,0.6,0.85,0.85";
            case FANGED_MAW -> "C0,0,0.85;D0,0,0.35;L-0.75,0.35,-0.5,0.05;L-0.4,0.7,-0.25,0.35;L0,0.85,0,0.45;L0.4,0.7,0.25,0.35;L0.75,0.35,0.5,0.05;L-0.75,-0.35,-0.5,-0.05;L-0.4,-0.7,-0.25,-0.35;L0,-0.85,0,-0.45;L0.4,-0.7,0.25,-0.35;L0.75,-0.35,0.5,-0.05";
            case LASH -> "A-0.3,0.25,0.6,200,380;A0.35,-0.3,0.4,20,200;L0.7,-0.45,0.9,-0.85;L-0.85,0.45,-0.9,0.85;D-0.9,0.9,0.07";
            case SCALES -> "A-0.5,0.55,0.35,180,360;A0,0.55,0.35,180,360;A0.5,0.55,0.35,180,360;A-0.25,0,0.35,180,360;A0.25,0,0.35,180,360;A-0.5,-0.55,0.35,180,360;A0,-0.55,0.35,180,360;A0.5,-0.55,0.35,180,360";
            case DEEP_CALL -> "D0,-0.6,0.18;A0,-0.6,0.45,200,340;A0,-0.6,0.75,205,335;A0,-0.6,1.05,210,330;L-0.9,-0.6,0.9,-0.6";
```

- [ ] **Step 3: Compile and run the profile lint**

Run: `./gradlew compileJava -q && ./gradlew test -q --tests "com.efkrdnz.magical.visual.VisualProfilesTest"`
Expected: compiles; lint green.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/visual/EmblemId.java src/main/java/com/efkrdnz/magical/client/renderer/fx/FxTextures.java
git commit -q -F - <<'EOF'
feat: six eldritch emblems in the sigil atlas

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---
### Task 4: The construct entity

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/entity/fx/EldritchConstructEntity.java`
- Modify: `src/main/java/com/efkrdnz/magical/registry/MagicalEntities.java` (after `BLOOD_HARVEST`, line 264-270)
- Test: compile only here; behaviour is pinned by the game tests of Tasks 8-13.

**Interfaces:**
- Produces:
  - constants `MODEL_TENTACLE = "tentacle"`, `MODEL_EYE = "eye"`, `MODEL_MAW = "maw"`; `ANCHOR_GROUND = 0`, `ANCHOR_OWNER = 1`, `ANCHOR_TARGET = 2` (bytes);
  - `static EldritchConstructEntity spawn(CastContext ctx, String model, byte anchor, Vec3 pos, int life, float radius, float scale, Vec3 dir)` - adds to the world;
  - `static EldritchConstructEntity spawnChild(SpellEffectEntity parent, String model, byte anchor, Vec3 pos, int life, float radius, float scale, Vec3 dir)` - copies skill, owner, stats, seed from the parent, adds to the world;
  - `String model()`, `byte anchor()`, `float scale()`, `void setScale(float)`;
  - `static List<EldritchConstructEntity> ownedBy(ServerLevel level, Entity owner, ResourceLocation skillId, double range)`;
  - client fields `public float gazeYaw, gazePitch` (renderer memory, like `BloodHarvestEntity.births`).
- Consumes: `SpellEffectEntity.create(...)` and the `SolidConstructEntity.copyFrom` idiom (`addAdditionalSaveData` into a tag, `readAdditionalSaveData` back).

- [ ] **Step 1: The entity**

```java
package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of the deep, called up: a tentacle, an eye or a maw, drawn from the geometry the user
 * modelled and moved by the code.
 *
 * <p>A spell effect with three more synced fields: which creature it is, what it is anchored to
 * (the ground where it erupted, its owner, or its target) and how large it is drawn. Everything
 * else - the owner, the target, the stats, the phase, the life, the scratch tag - is the effect
 * entity's, and the skill behaviours tick it like any other.
 */
public class EldritchConstructEntity extends SpellEffectEntity {
    public static final String MODEL_TENTACLE = "tentacle";
    public static final String MODEL_EYE = "eye";
    public static final String MODEL_MAW = "maw";

    public static final byte ANCHOR_GROUND = 0;
    public static final byte ANCHOR_OWNER = 1;
    public static final byte ANCHOR_TARGET = 2;

    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(EldritchConstructEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> ANCHOR = SynchedEntityData.defineId(EldritchConstructEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(EldritchConstructEntity.class, EntityDataSerializers.FLOAT);

    /** Renderer memory: where the eye is looking now, eased toward where it should. Client only. */
    public float gazeYaw;
    public float gazePitch;

    public EldritchConstructEntity(EntityType<? extends EldritchConstructEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static EldritchConstructEntity spawn(CastContext ctx, String model, byte anchor, Vec3 pos, int life, float radius, float scale, Vec3 dir) {
        SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), pos, life, radius, dir, (int) (ctx.seed() & 63));
        template.setMode(ctx.sneak() ? (byte) 1 : (byte) 0);
        return place(ctx.level(), template, model, anchor, pos, scale);
    }

    /** A construct a running effect calls up beside itself: same skill, owner and stats. */
    public static EldritchConstructEntity spawnChild(SpellEffectEntity parent, String model, byte anchor, Vec3 pos, int life, float radius, float scale, Vec3 dir) {
        SpellEffectEntity template = SpellEffectEntity.create(parent.serverLevel(), parent.definition(), null, parent.owner(), pos, life, radius, dir, parent.seed());
        template.copyStatsFrom(parent);
        // Mode bit 2 marks a child of a controller: never offered as a counter, never sneak-flipped.
        template.setMode((byte) 2);
        return place(parent.serverLevel(), template, model, anchor, pos, scale);
    }

    private static EldritchConstructEntity place(ServerLevel level, SpellEffectEntity template, String model, byte anchor, Vec3 pos, float scale) {
        EldritchConstructEntity entity = new EldritchConstructEntity(MagicalEntities.ELDRITCH_CONSTRUCT.get(), level);
        CompoundTag tag = new CompoundTag();
        template.addAdditionalSaveData(tag);
        entity.readAdditionalSaveData(tag);
        entity.setPos(pos.x, pos.y, pos.z);
        entity.entityData.set(MODEL, model);
        entity.entityData.set(ANCHOR, anchor);
        entity.entityData.set(SCALE, scale);
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODEL, MODEL_TENTACLE);
        builder.define(ANCHOR, ANCHOR_GROUND);
        builder.define(SCALE, 1.0F);
    }

    public String model() {
        return entityData.get(MODEL);
    }

    public byte anchor() {
        return entityData.get(ANCHOR);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public void setScale(float scale) {
        entityData.set(SCALE, scale);
    }

    /** The constructs of one skill this owner has standing within range of them. */
    public static List<EldritchConstructEntity> ownedBy(ServerLevel level, Entity owner, ResourceLocation skillId, double range) {
        return level.getEntities(MagicalEntities.ELDRITCH_CONSTRUCT.get(), owner.getBoundingBox().inflate(range),
                construct -> construct.owner() == owner && skillId.equals(construct.skillId()) && !construct.isRemoved());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Model", model());
        tag.putByte("Anchor", anchor());
        tag.putFloat("Scale", scale());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Model")) {
            entityData.set(MODEL, tag.getString("Model"));
            entityData.set(ANCHOR, tag.getByte("Anchor"));
            entityData.set(SCALE, tag.getFloat("Scale"));
        }
    }
}
```

`addAdditionalSaveData` / `readAdditionalSaveData` are `protected` on `SpellEffectEntity`; `place` calls them on a template of the same package, which Java allows (`SolidConstructEntity.copyFrom` does exactly this). `SpellEffectEntity.create` guards a null `stats`. `readAdditionalSaveData` marks `spawned = true` and calls `onLoad` on the server: acceptable; the behaviours below key on `tickCount` rather than `onSpawn`.

- [ ] **Step 2: Registration** (after `BLOOD_HARVEST` in `MagicalEntities`):

```java
    /** A piece of the deep an eldritch mage called up: a tentacle, an eye or a maw. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.EldritchConstructEntity>> ELDRITCH_CONSTRUCT = ENTITY_TYPES.register(
            "eldritch_construct",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.EldritchConstructEntity>of(com.efkrdnz.magical.entity.fx.EldritchConstructEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.5F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("eldritch_construct")));
```

The renderer binding comes in Task 7; do not run the client between Tasks 4 and 7.

- [ ] **Step 3: Compile, commit**

Run: `./gradlew compileJava -q`
Expected: clean.

```bash
git add src/main/java/com/efkrdnz/magical/entity/fx/EldritchConstructEntity.java src/main/java/com/efkrdnz/magical/registry/MagicalEntities.java
git commit -q -F - <<'EOF'
feat: the eldritch construct entity - a creature, an anchor and a scale on a spell effect

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 5: The geometry model and its parser (pure)

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/model/geo/GeoModel.java`, `GeoFormatException.java`, `GeoModelParser.java`
- Test: `src/test/java/com/efkrdnz/magical/client/model/geo/GeoModelParserTest.java`

**Interfaces:**
- Produces: `record GeoModel(String identifier, int textureWidth, int textureHeight, List<Bone> bones)` with `record Bone(String name, String parent, float[] pivot, float[] rotation, boolean mirror, List<Cube> cubes)` and `record Cube(float[] origin, float[] size, float[] uv, float inflate, boolean mirror, float[] rotation, float[] pivot)` (`rotation` null when the cube has none; `pivot` null unless rotated), `GeoModel.bone(String) : Bone` (null if absent); `GeoModelParser.parse(String json) : GeoModel`, `parse(JsonObject) : GeoModel`; `GeoFormatException extends RuntimeException`.
- The coordinate contract (read by Task 7): values are copied as written in the file (Bedrock space: y up, -Z front, sixteenths).

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.client.model.geo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A Blockbench Bedrock export, read the way Bedrock reads it: bones by name with a parent, a
 * pivot and a rotation; cubes as an origin, a size and a Box UV corner. Anything Blockbench
 * writes that the baker does not use is ignored, and the one thing it cannot bake - per-face UV -
 * is refused by name so the fix (export with Box UV) is in the log.
 */
class GeoModelParserTest {

    private static final String EXPORT = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [
                {
                  "description": {"identifier": "geometry.tentacle", "texture_width": 64, "texture_height": 64,
                                  "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
                  "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    {"name": "seg0", "parent": "root", "pivot": [0, 0, 0], "rotation": [-10, 0, 5],
                     "cubes": [{"origin": [-2, 0, -2], "size": [4, 4, 4], "uv": [0, 0], "inflate": 0.25}]},
                    {"name": "seg1", "parent": "seg0", "pivot": [0, 4, 0], "mirror": true,
                     "cubes": [{"origin": [-1.5, 4, -1.5], "size": [3, 4, 3], "uv": [0, 8]},
                               {"origin": [-1, 8, -1], "size": [2, 2, 2], "uv": [16, 8], "rotation": [0, 45, 0], "pivot": [0, 9, 0]}]}
                  ]
                }
              ]
            }
            """;

    @Test
    void bonesComeBackWithTheirParentsPivotsRotationsAndCubes() {
        GeoModel model = GeoModelParser.parse(EXPORT);
        assertEquals("geometry.tentacle", model.identifier());
        assertEquals(64, model.textureWidth());
        assertEquals(3, model.bones().size());
        GeoModel.Bone seg0 = model.bone("seg0");
        assertEquals("root", seg0.parent());
        assertArrayEquals(new float[] {-10, 0, 5}, seg0.rotation());
        assertEquals(1, seg0.cubes().size());
        GeoModel.Cube cube = seg0.cubes().get(0);
        assertArrayEquals(new float[] {-2, 0, -2}, cube.origin());
        assertArrayEquals(new float[] {4, 4, 4}, cube.size());
        assertArrayEquals(new float[] {0, 0}, cube.uv());
        assertEquals(0.25F, cube.inflate());
        assertNull(cube.rotation(), "an unrotated cube has no rotation");
        assertNull(model.bone("root").parent());
        assertNull(model.bone("fin"), "a bone that is not there is null, not an error");
    }

    @Test
    void defaultsAreBedrocksAndARotatedCubeKeepsItsOwnPivot() {
        GeoModel model = GeoModelParser.parse(EXPORT);
        GeoModel.Bone root = model.bone("root");
        assertArrayEquals(new float[] {0, 0, 0}, root.rotation());
        assertTrue(root.cubes().isEmpty());
        assertFalse(root.mirror());
        GeoModel.Bone seg1 = model.bone("seg1");
        assertTrue(seg1.mirror(), "the bone flag");
        assertTrue(seg1.cubes().get(0).mirror(), "is inherited by its cubes");
        GeoModel.Cube rotated = seg1.cubes().get(1);
        assertArrayEquals(new float[] {0, 45, 0}, rotated.rotation());
        assertArrayEquals(new float[] {0, 9, 0}, rotated.pivot());
    }

    @Test
    void aMissingTextureSizeIsSixtyFour() {
        GeoModel model = GeoModelParser.parse("""
                {"minecraft:geometry": [{"description": {"identifier": "geometry.eye"}, "bones": []}]}
                """);
        assertEquals(64, model.textureWidth());
        assertEquals(64, model.textureHeight());
    }

    @Test
    void perFaceUvIsRefusedByBoneName() {
        GeoFormatException refused = assertThrows(GeoFormatException.class, () -> GeoModelParser.parse("""
                {"minecraft:geometry": [{"description": {"identifier": "geometry.maw"}, "bones": [
                  {"name": "jaw_upper", "pivot": [0, 0, 6], "cubes": [{"origin": [0, 0, 0], "size": [1, 1, 1],
                   "uv": {"north": {"uv": [0, 0], "uv_size": [1, 1]}}}]}]}]}
                """));
        assertTrue(refused.getMessage().contains("jaw_upper"), refused.getMessage());
        assertTrue(refused.getMessage().contains("Box UV"), refused.getMessage());
    }

    @Test
    void aFileWithoutGeometryOrABoneWithoutANameIsRefused() {
        assertThrows(GeoFormatException.class, () -> GeoModelParser.parse("{}"));
        assertThrows(GeoFormatException.class, () -> GeoModelParser.parse("""
                {"minecraft:geometry": [{"description": {"identifier": "geometry.x"}, "bones": [{"pivot": [0, 0, 0]}]}]}
                """));
    }
}
```

- [ ] **Step 2: Run to verify RED**

Run: `./gradlew test -q --tests "com.efkrdnz.magical.client.model.geo.GeoModelParserTest"`
Expected: compilation failure (`GeoModel`, `GeoModelParser`, `GeoFormatException` missing).

- [ ] **Step 3: The records and the exception**

```java
package com.efkrdnz.magical.client.model.geo;

import java.util.List;

/**
 * One Bedrock entity geometry as Blockbench exports it, in the file's own space: sixteenths of a
 * block, y up, the -Z face the front. Nothing here is converted; the baker does that once.
 */
public record GeoModel(String identifier, int textureWidth, int textureHeight, List<Bone> bones) {

    /** A bone: its pivot and rest rotation in file space, its parent by name, its cubes. */
    public record Bone(String name, String parent, float[] pivot, float[] rotation, boolean mirror, List<Cube> cubes) {
    }

    /**
     * A box. {@code rotation} and {@code pivot} are null unless the cube is rotated on its own,
     * in which case the baker wraps it in a part of its own at that pivot.
     */
    public record Cube(float[] origin, float[] size, float[] uv, float inflate, boolean mirror, float[] rotation, float[] pivot) {
    }

    /** The bone of that name, or null: extras the code does not know are fine, and so are absences. */
    public Bone bone(String name) {
        for (Bone bone : bones) {
            if (bone.name().equals(name)) {
                return bone;
            }
        }
        return null;
    }
}
```

```java
package com.efkrdnz.magical.client.model.geo;

/** A geometry file the baker cannot use, with the file and the fix in the message. */
public final class GeoFormatException extends RuntimeException {
    public GeoFormatException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: The parser**

```java
package com.efkrdnz.magical.client.model.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the Bedrock entity geometry format ({@code format_version} 1.12 and later as Blockbench
 * writes it): {@code minecraft:geometry[0].description} for the identifier and texture size,
 * {@code bones[]} with {@code name, parent, pivot, rotation, mirror, cubes[]}, each cube with
 * {@code origin, size, uv, inflate, mirror, rotation, pivot}. Box UV only: a per-face {@code uv}
 * object is refused with the bone named, because the fix is in Blockbench, not here.
 */
public final class GeoModelParser {
    private static final int DEFAULT_TEXTURE_SIZE = 64;

    private GeoModelParser() {
    }

    public static GeoModel parse(String json) {
        try {
            return parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new GeoFormatException("not a geometry file: " + e.getMessage());
        }
    }

    public static GeoModel parse(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new GeoFormatException("no minecraft:geometry entry - export a Bedrock entity geometry");
        }
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.has("description") ? geometry.getAsJsonObject("description") : new JsonObject();
        String identifier = description.has("identifier") ? description.get("identifier").getAsString() : "";
        int textureWidth = intOr(description, "texture_width", DEFAULT_TEXTURE_SIZE);
        int textureHeight = intOr(description, "texture_height", DEFAULT_TEXTURE_SIZE);
        List<GeoModel.Bone> bones = new ArrayList<>();
        if (geometry.has("bones")) {
            for (JsonElement element : geometry.getAsJsonArray("bones")) {
                bones.add(bone(element.getAsJsonObject()));
            }
        }
        return new GeoModel(identifier, textureWidth, textureHeight, List.copyOf(bones));
    }

    private static GeoModel.Bone bone(JsonObject json) {
        if (!json.has("name")) {
            throw new GeoFormatException("a bone without a name");
        }
        String name = json.get("name").getAsString();
        String parent = json.has("parent") ? json.get("parent").getAsString() : null;
        boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();
        List<GeoModel.Cube> cubes = new ArrayList<>();
        if (json.has("cubes")) {
            for (JsonElement element : json.getAsJsonArray("cubes")) {
                cubes.add(cube(name, element.getAsJsonObject(), mirror));
            }
        }
        return new GeoModel.Bone(name, parent, floats(json, "pivot", 3, new float[] {0, 0, 0}),
                floats(json, "rotation", 3, new float[] {0, 0, 0}), mirror, List.copyOf(cubes));
    }

    private static GeoModel.Cube cube(String bone, JsonObject json, boolean boneMirror) {
        if (json.has("uv") && !json.get("uv").isJsonArray()) {
            throw new GeoFormatException("bone " + bone + " has a cube with per-face UV; export with Box UV");
        }
        float[] origin = floats(json, "origin", 3, null);
        float[] size = floats(json, "size", 3, null);
        if (origin == null || size == null) {
            throw new GeoFormatException("bone " + bone + " has a cube without an origin or a size");
        }
        float[] rotation = floats(json, "rotation", 3, null);
        float[] pivot = floats(json, "pivot", 3, null);
        if (rotation != null && pivot == null) {
            // Bedrock rotates a pivot-less cube about its centre.
            pivot = new float[] {origin[0] + size[0] / 2.0F, origin[1] + size[1] / 2.0F, origin[2] + size[2] / 2.0F};
        }
        boolean mirror = json.has("mirror") ? json.get("mirror").getAsBoolean() : boneMirror;
        float inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : 0.0F;
        return new GeoModel.Cube(origin, size, floats(json, "uv", 2, new float[] {0, 0}), inflate, mirror, rotation, pivot);
    }

    private static float[] floats(JsonObject json, String key, int count, float[] fallback) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return fallback;
        }
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() != count) {
            throw new GeoFormatException(key + " needs " + count + " numbers, has " + array.size());
        }
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
}
```

- [ ] **Step 5: Run to verify GREEN**

Run: `./gradlew test -q --tests "com.efkrdnz.magical.client.model.geo.GeoModelParserTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/client/model/geo src/test/java/com/efkrdnz/magical/client/model/geo
git commit -q -F - <<'EOF'
feat: a reader for Bedrock entity geometry as Blockbench exports it

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 6: The motion (pure)

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/renderer/eldritch/EldritchPose.java`
- Test: `src/test/java/com/efkrdnz/magical/client/renderer/eldritch/EldritchPoseTest.java`

**Interfaces:**
- Produces (all static, radians; chain space: the chain stands along `up` from its base and bends toward `forward`, the model's front):
  - `int SEGMENTS = 6`, `float MAX_BEND = 2.0F`, `float SWAY_RATE = 0.12F`, `int CCD_PASSES = 6`, `float SNAP_TICKS = 3.0F`, `float MAX_GAPE = 0.9F`;
  - `void chain(int segments, float length, float forward, float up, boolean hasTarget, float age, float sway, float[] outPitch)`;
  - `void tip(float length, float[] pitch, int count, float[] outForwardUp)` (forward kinematics);
  - `float grown(float age, float formTicks, int segments)` - how many segments exist, 0..segments, fractional;
  - `float segmentScale(float grown, int index)` - 0..1;
  - `float dissolve(float age, float life, float ticks)` - 1 while whole, down to 0 at the end;
  - `float ease(float current, float wanted, float lag)` - an angle eased toward another, shortest way round;
  - `float jaws(float age, float windup, float snapAt)` - openness 0..1;
  - `float pupil(boolean fixed, float age)` - pupil scale.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.client.renderer.eldritch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The motion the code owns: a chain that reaches what it grabs, grows from its base and stays
 * within what a tentacle can bend; jaws that open, snap and chatter; a gaze that rolls.
 */
class EldritchPoseTest {

    private static final int N = EldritchPose.SEGMENTS;
    private static final float L = 4.0F;

    private static float[] chainTo(float forward, float up, float age) {
        float[] pitch = new float[N];
        EldritchPose.chain(N, L, forward, up, true, age, 0.0F, pitch);
        return pitch;
    }

    private static float distanceToTarget(float[] pitch, float forward, float up) {
        float[] tip = new float[2];
        EldritchPose.tip(L, pitch, N, tip);
        return (float) Math.hypot(tip[0] - forward, tip[1] - up);
    }

    @Test
    void theTipLandsOnATargetWithinReach() {
        for (float[] target : new float[][] {{10, 8}, {3, 20}, {-6, 12}, {16, 2}}) {
            float[] pitch = chainTo(target[0], target[1], 0.0F);
            assertTrue(distanceToTarget(pitch, target[0], target[1]) < 0.5F,
                    "tip misses (" + target[0] + "," + target[1] + ") by " + distanceToTarget(pitch, target[0], target[1]));
        }
    }

    @Test
    void aTargetBeyondReachIsPointedAtAlongAStraightChain() {
        float[] pitch = chainTo(40.0F, 30.0F, 0.0F);
        float[] tip = new float[2];
        EldritchPose.tip(L, pitch, N, tip);
        float reach = N * L;
        assertEquals(reach, (float) Math.hypot(tip[0], tip[1]), 0.05F, "fully extended");
        assertEquals(Math.atan2(40.0, 30.0), Math.atan2(tip[0], tip[1]), 0.02D, "toward the target");
    }

    @Test
    void noJointBendsFurtherThanATentacleCan() {
        for (float[] target : new float[][] {{2, -5}, {-10, -3}, {0, 1}, {12, 12}}) {
            for (float pitch : chainTo(target[0], target[1], 7.0F)) {
                assertTrue(Math.abs(pitch) <= EldritchPose.MAX_BEND + 1.0E-4F, "bend " + pitch);
            }
        }
    }

    @Test
    void thePoseIsContinuousInTime() {
        float[] before = chainTo(10.0F, 8.0F, 3.0F);
        float[] after = chainTo(10.0F, 8.0F, 3.05F);
        for (int i = 0; i < N; i++) {
            assertTrue(Math.abs(after[i] - before[i]) < 0.02F, "joint " + i + " jumped " + (after[i] - before[i]));
        }
        float[] idle = new float[N];
        EldritchPose.chain(N, L, 0.0F, 0.0F, false, 3.0F, 0.3F, idle);
        float[] idleLater = new float[N];
        EldritchPose.chain(N, L, 0.0F, 0.0F, false, 3.05F, 0.3F, idleLater);
        for (int i = 0; i < N; i++) {
            assertTrue(Math.abs(idleLater[i] - idle[i]) < 0.02F, "idle joint " + i + " jumped");
        }
    }

    @Test
    void aChainGrowsFromItsBaseOneSegmentAfterAnother() {
        assertEquals(0.0F, EldritchPose.grown(0.0F, 12.0F, N));
        assertEquals(N, EldritchPose.grown(12.0F, 12.0F, N));
        assertEquals(N, EldritchPose.grown(40.0F, 12.0F, N), "and stays grown");
        float half = EldritchPose.grown(6.0F, 12.0F, N);
        assertEquals(1.0F, EldritchPose.segmentScale(half, 0), "the base is whole");
        assertEquals(1.0F, EldritchPose.segmentScale(half, 2));
        assertEquals(0.0F, EldritchPose.segmentScale(half, 5), "the tip is not there yet");
        assertEquals(0.5F, EldritchPose.segmentScale(2.5F, 2), 1.0E-6F, "the growing one is partly there");
    }

    @Test
    void aConstructDissolvesOverItsLastTicksAndNotBefore() {
        assertEquals(1.0F, EldritchPose.dissolve(10.0F, 100.0F, 20.0F));
        assertEquals(1.0F, EldritchPose.dissolve(80.0F, 100.0F, 20.0F));
        assertEquals(0.5F, EldritchPose.dissolve(90.0F, 100.0F, 20.0F), 1.0E-6F);
        assertEquals(0.0F, EldritchPose.dissolve(100.0F, 100.0F, 20.0F));
        assertEquals(1.0F, EldritchPose.dissolve(500.0F, 0.0F, 20.0F), "unbounded life never dissolves");
    }

    @Test
    void jawsOpenOverTheWindupSnapShutInThreeTicksAndChatter() {
        assertEquals(0.0F, EldritchPose.jaws(0.0F, 20.0F, 0.0F));
        assertEquals(0.5F, EldritchPose.jaws(10.0F, 20.0F, 0.0F), 1.0E-6F);
        assertEquals(1.0F, EldritchPose.jaws(30.0F, 20.0F, 0.0F), "held open while it waits");
        assertEquals(1.0F, EldritchPose.jaws(30.0F, 20.0F, 30.0F), 1.0E-6F, "the snap begins where it was");
        assertEquals(0.0F, EldritchPose.jaws(33.0F, 20.0F, 30.0F), 1.0E-6F, "and is shut three ticks later");
        float chatter = EldritchPose.jaws(35.0F, 20.0F, 30.0F);
        assertTrue(chatter >= 0.0F && chatter < 0.2F, "a small chatter after: " + chatter);
        assertTrue(EldritchPose.jaws(60.0F, 20.0F, 30.0F) < 0.02F, "which dies away");
    }

    @Test
    void aGazeRollsTheShortWayRoundAndAPupilContractsOnItsMark() {
        assertEquals(0.1F, EldritchPose.ease(0.0F, 1.0F, 0.1F), 1.0E-6F);
        float across = EldritchPose.ease(3.0F, -3.0F, 0.5F);
        assertTrue(across > 3.0F || across < -3.0F, "from 3 to -3 the short way is through pi, not zero: " + across);
        assertTrue(EldritchPose.pupil(true, 5.0F) < EldritchPose.pupil(false, 5.0F));
    }
}
```

- [ ] **Step 2: Run to verify RED**

Run: `./gradlew test -q --tests "com.efkrdnz.magical.client.renderer.eldritch.EldritchPoseTest"`
Expected: compilation failure (`EldritchPose` missing).

- [ ] **Step 3: The motion**

```java
package com.efkrdnz.magical.client.renderer.eldritch;

/**
 * The motion the code owns, as pure functions of a float age and the synced state.
 *
 * <p>Chain space: the chain stands along {@code up} from its base and bends toward
 * {@code forward}, which is the model's front (the -Z face of the geometry). A pitch is the bend
 * of one segment relative to the one below it; positive bends toward the front. The renderer
 * turns the whole construct to face its target and writes these pitches into the segment bones.
 */
public final class EldritchPose {
    public static final int SEGMENTS = 6;
    /** How far one joint may bend, in radians. A tentacle curls; it does not fold in half. */
    public static final float MAX_BEND = 2.0F;
    public static final float SWAY_RATE = 0.12F;
    public static final int CCD_PASSES = 6;
    public static final float SNAP_TICKS = 3.0F;
    /** The jaws' full gape, in radians. */
    public static final float MAX_GAPE = 0.9F;

    private static final float CHATTER = 0.12F;
    private static final float CHATTER_RATE = 1.4F;
    private static final float CHATTER_FADE = 0.06F;
    private static final float SWAY_ON_HOLD = 0.25F;

    private EldritchPose() {
    }

    public static void chain(int segments, float length, float forward, float up, boolean hasTarget, float age, float sway, float[] outPitch) {
        float swayNow = sway * (float) Math.sin(age * SWAY_RATE);
        if (!hasTarget) {
            for (int i = 0; i < segments; i++) {
                outPitch[i] = swayNow * (i + 1) / segments;
            }
            return;
        }
        // Start pointed straight at the target: base bent to the aim, the rest straight.
        float aim = clampBend((float) Math.atan2(forward, up));
        outPitch[0] = aim;
        for (int i = 1; i < segments; i++) {
            outPitch[i] = 0.0F;
        }
        float distance = (float) Math.hypot(forward, up);
        if (distance < segments * length) {
            // Within reach: cyclic coordinate descent from the tip, so the tip lands on the target
            // and the bend spreads down the chain.
            float[] joint = new float[2];
            float[] tip = new float[2];
            for (int pass = 0; pass < CCD_PASSES; pass++) {
                for (int i = segments - 1; i >= 0; i--) {
                    tip(length, outPitch, i, joint);
                    tip(length, outPitch, segments, tip);
                    float toTip = (float) Math.atan2(tip[0] - joint[0], tip[1] - joint[1]);
                    float toTarget = (float) Math.atan2(forward - joint[0], up - joint[1]);
                    outPitch[i] = clampBend(outPitch[i] + wrap(toTarget - toTip));
                }
            }
        }
        for (int i = 0; i < segments; i++) {
            outPitch[i] = clampBend(outPitch[i] + swayNow * SWAY_ON_HOLD * (i + 1) / segments);
        }
    }

    /** Forward kinematics: where the chain is after {@code count} segments, as (forward, up). */
    public static void tip(float length, float[] pitch, int count, float[] out) {
        float heading = 0.0F;
        float forward = 0.0F;
        float up = 0.0F;
        for (int i = 0; i < count; i++) {
            heading += pitch[i];
            forward += length * (float) Math.sin(heading);
            up += length * (float) Math.cos(heading);
        }
        out[0] = forward;
        out[1] = up;
    }

    public static float grown(float age, float formTicks, int segments) {
        if (formTicks <= 0.0F) {
            return segments;
        }
        return segments * Math.max(0.0F, Math.min(1.0F, age / formTicks));
    }

    public static float segmentScale(float grown, int index) {
        return Math.max(0.0F, Math.min(1.0F, grown - index));
    }

    public static float dissolve(float age, float life, float ticks) {
        if (life <= 0.0F || ticks <= 0.0F) {
            return 1.0F;
        }
        float left = life - age;
        return Math.max(0.0F, Math.min(1.0F, left / ticks));
    }

    public static float ease(float current, float wanted, float lag) {
        return current + wrap(wanted - current) * lag;
    }

    public static float jaws(float age, float windup, float snapAt) {
        if (snapAt <= 0.0F || age < snapAt) {
            return windup <= 0.0F ? 1.0F : Math.max(0.0F, Math.min(1.0F, age / windup));
        }
        float since = age - snapAt;
        if (since < SNAP_TICKS) {
            return 1.0F - since / SNAP_TICKS;
        }
        float after = since - SNAP_TICKS;
        return CHATTER * Math.abs((float) Math.sin(after * CHATTER_RATE)) * (float) Math.exp(-after * CHATTER_FADE);
    }

    public static float pupil(boolean fixed, float age) {
        float breath = 0.05F * (float) Math.sin(age * 0.08F);
        return (fixed ? 0.6F : 1.0F) + breath;
    }

    private static float clampBend(float pitch) {
        return Math.max(-MAX_BEND, Math.min(MAX_BEND, pitch));
    }

    /** An angle difference brought into -pi..pi. */
    private static float wrap(float angle) {
        float twoPi = (float) (Math.PI * 2.0D);
        angle %= twoPi;
        if (angle > Math.PI) {
            angle -= twoPi;
        } else if (angle < -Math.PI) {
            angle += twoPi;
        }
        return angle;
    }
}
```

- [ ] **Step 4: Run to verify GREEN**

Run: `./gradlew test -q --tests "com.efkrdnz.magical.client.renderer.eldritch.EldritchPoseTest"`
Expected: PASS. If a reach case misses by more than 0.5, raise `CCD_PASSES` to 10 before touching anything else.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/client/renderer/eldritch/EldritchPose.java src/test/java/com/efkrdnz/magical/client/renderer/eldritch/EldritchPoseTest.java
git commit -q -F - <<'EOF'
feat: the motion of the deep - a chain that reaches, jaws that snap, a gaze that rolls

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 7: Baking, loading, drawing, placeholders

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/model/geo/GeoModelBaker.java`
- Create: `src/main/java/com/efkrdnz/magical/client/model/eldritch/EldritchModels.java`
- Create: `src/main/java/com/efkrdnz/magical/client/renderer/eldritch/EldritchConstructRenderer.java`
- Modify: `src/main/java/com/efkrdnz/magical/client/MagicalClientEvents.java` (`registerRenderers` after the `BLOOD_HARVEST` line; a new `@SubscribeEvent` for `AddClientReloadListenersEvent`)
- Create: `scripts/eldritch-placeholders.py`; generated `src/main/resources/assets/magical/models/entity/eldritch/{tentacle,eye,maw}.geo.json`, `src/main/resources/assets/magical/textures/entity/eldritch/{tentacle,eye,maw}.png` and `{tentacle,eye,maw}_glow.png`
- Test: no unit test can drive GL; the placeholder geometry is parsed by a test added to `GeoModelParserTest` that reads each shipped file and asserts the bones the renderer names exist.

**Interfaces:**
- Produces: `GeoModelBaker.bake(GeoModel) : GeoModelBaker.Baked` with `record Baked(ModelPart root, Map<String, ModelPart> parts)` and `ModelPart part(String name)` (null if absent); `EldritchModels` (`public static final ResourceLocation KEY = magical:eldritch_models`, `static Entry get(String model)` returning `record Entry(GeoModelBaker.Baked baked, ResourceLocation texture, ResourceLocation glow)` or null); `EldritchConstructRenderer`.
- Consumes: Tasks 4-6.

- [ ] **Step 1: The baker** (Bedrock file space to vanilla model space: `x` as written, `y` negated, `z` as written, cubes relative to their own bone's pivot, children relative to their parent's pivot, rotations in degrees with the same sign):

```java
package com.efkrdnz.magical.client.model.geo;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Turns a {@link GeoModel} into vanilla model parts, once, at resource load.
 *
 * <p>The only conversion in the whole pipeline is here: Bedrock is y up and vanilla model space is
 * y down (the renderer's {@code scale(-1, -1, 1)} turns it back), so every y is negated and a
 * cube's box starts at the top of it. Everything else is copied: x, z, the degrees of rotation
 * and their signs, the Box UV corner, inflate, mirror. A cube rotated on its own becomes a part
 * of its own at its pivot, since a vanilla cube cannot be rotated alone.
 */
public final class GeoModelBaker {

    public record Baked(ModelPart root, Map<String, ModelPart> parts) {
        public ModelPart part(String name) {
            return parts.get(name);
        }
    }

    private GeoModelBaker() {
    }

    public static Baked bake(GeoModel model) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> definitions = new HashMap<>();
        Map<String, String> parents = new HashMap<>();
        for (GeoModel.Bone bone : model.bones()) {
            define(model, bone, mesh, definitions, parents);
        }
        ModelPart root = LayerDefinition.create(mesh, model.textureWidth(), model.textureHeight()).bakeRoot();
        Map<String, ModelPart> parts = new HashMap<>();
        for (GeoModel.Bone bone : model.bones()) {
            parts.put(bone.name(), resolve(root, bone.name(), parents));
        }
        return new Baked(root, Map.copyOf(parts));
    }

    private static PartDefinition define(GeoModel model, GeoModel.Bone bone, MeshDefinition mesh,
            Map<String, PartDefinition> definitions, Map<String, String> parents) {
        PartDefinition existing = definitions.get(bone.name());
        if (existing != null) {
            return existing;
        }
        PartDefinition parent = mesh.getRoot();
        float[] parentPivot = {0.0F, 0.0F, 0.0F};
        if (bone.parent() != null) {
            GeoModel.Bone parentBone = model.bone(bone.parent());
            if (parentBone == null) {
                throw new GeoFormatException("bone " + bone.name() + " names a parent that is not there: " + bone.parent());
            }
            parent = define(model, parentBone, mesh, definitions, parents);
            parentPivot = parentBone.pivot();
            parents.put(bone.name(), bone.parent());
        }
        float[] p = bone.pivot();
        CubeListBuilder cubes = CubeListBuilder.create();
        for (GeoModel.Cube cube : bone.cubes()) {
            if (cube.rotation() == null) {
                box(cubes, cube, p);
            }
        }
        PartDefinition definition = parent.addOrReplaceChild(bone.name(), cubes, PartPose.offsetAndRotation(
                p[0] - parentPivot[0], -(p[1] - parentPivot[1]), p[2] - parentPivot[2],
                rad(bone.rotation()[0]), rad(bone.rotation()[1]), rad(bone.rotation()[2])));
        int index = 0;
        for (GeoModel.Cube cube : bone.cubes()) {
            if (cube.rotation() == null) {
                continue;
            }
            float[] c = cube.pivot();
            CubeListBuilder one = CubeListBuilder.create();
            box(one, cube, c);
            definition.addOrReplaceChild(bone.name() + "$cube" + index++, one, PartPose.offsetAndRotation(
                    c[0] - p[0], -(c[1] - p[1]), c[2] - p[2],
                    rad(cube.rotation()[0]), rad(cube.rotation()[1]), rad(cube.rotation()[2])));
        }
        definitions.put(bone.name(), definition);
        return definition;
    }

    /** One box relative to a pivot: the top of the Bedrock box is the bottom of the vanilla one. */
    private static void box(CubeListBuilder cubes, GeoModel.Cube cube, float[] pivot) {
        float[] o = cube.origin();
        float[] s = cube.size();
        cubes.texOffs(Math.round(cube.uv()[0]), Math.round(cube.uv()[1]))
                .mirror(cube.mirror())
                .addBox(o[0] - pivot[0], -(o[1] + s[1]) + pivot[1], o[2] - pivot[2], s[0], s[1], s[2],
                        new CubeDeformation(cube.inflate()));
    }

    private static ModelPart resolve(ModelPart root, String name, Map<String, String> parents) {
        String parent = parents.get(name);
        ModelPart holder = parent == null ? root : resolve(root, parent, parents);
        return holder.getChild(name);
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
```

- [ ] **Step 2: The registry and reload listener**

```java
package com.efkrdnz.magical.client.model.eldritch;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.model.geo.GeoFormatException;
import com.efkrdnz.magical.client.model.geo.GeoModel;
import com.efkrdnz.magical.client.model.geo.GeoModelBaker;
import com.efkrdnz.magical.client.model.geo.GeoModelParser;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The creatures, by name, rebuilt on every resource reload so an export dropped into the folder
 * shows up at the next F3+T.
 *
 * <p>{@code assets/magical/models/entity/eldritch/<name>.geo.json} is the model, {@code
 * textures/entity/eldritch/<name>.png} its texture, {@code <name>_glow.png} the optional layer
 * drawn full bright. A file the parser refuses is logged with the reason and left out; a
 * construct whose creature is missing draws its FX and nothing else, and says so once.
 */
public final class EldritchModels extends SimplePreparableReloadListener<Map<String, GeoModel>> {
    public static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "eldritch_models");
    private static final String FOLDER = "models/entity/eldritch";
    private static final String SUFFIX = ".geo.json";

    public record Entry(GeoModelBaker.Baked baked, ResourceLocation texture, ResourceLocation glow) {
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Set<String> MISSING_LOGGED = new HashSet<>();

    @Override
    protected Map<String, GeoModel> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, GeoModel> models = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> found : resourceManager.listResources(FOLDER,
                location -> MagicalMod.MODID.equals(location.getNamespace()) && location.getPath().endsWith(SUFFIX)).entrySet()) {
            String path = found.getKey().getPath();
            String name = path.substring(FOLDER.length() + 1, path.length() - SUFFIX.length());
            try (Reader reader = found.getValue().openAsReader()) {
                models.put(name, GeoModelParser.parse(readAll(reader)));
            } catch (GeoFormatException | IOException | RuntimeException e) {
                MagicalMod.LOGGER.warn("Eldritch model {} skipped: {}", found.getKey(), e.getMessage());
            }
        }
        return models;
    }

    @Override
    protected void apply(Map<String, GeoModel> models, ResourceManager resourceManager, ProfilerFiller profiler) {
        ENTRIES.clear();
        MISSING_LOGGED.clear();
        for (Map.Entry<String, GeoModel> model : models.entrySet()) {
            String name = model.getKey();
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/entity/eldritch/" + name + ".png");
            ResourceLocation glow = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/entity/eldritch/" + name + "_glow.png");
            try {
                ENTRIES.put(name, new Entry(GeoModelBaker.bake(model.getValue()), texture,
                        resourceManager.getResource(glow).isPresent() ? glow : null));
            } catch (GeoFormatException e) {
                MagicalMod.LOGGER.warn("Eldritch model {} skipped: {}", name, e.getMessage());
            }
        }
        MagicalMod.LOGGER.info("Eldritch models: {} loaded ({})", ENTRIES.size(), ENTRIES.keySet());
    }

    /** The creature by name, or null once the absence has been logged. */
    public static Entry get(String name) {
        Entry entry = ENTRIES.get(name);
        if (entry == null && MISSING_LOGGED.add(name)) {
            MagicalMod.LOGGER.warn("No eldritch model named {}: expected assets/magical/{}/{}{}", name, FOLDER, name, SUFFIX);
        }
        return entry;
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
```

In `MagicalClientEvents` (MOD bus subscriber, next to `registerRenderers`):

```java
    @SubscribeEvent
    public static void addReloadListeners(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) {
        event.addListener(com.efkrdnz.magical.client.model.eldritch.EldritchModels.KEY,
                new com.efkrdnz.magical.client.model.eldritch.EldritchModels());
    }
```

and in `registerRenderers`: `event.registerEntityRenderer(MagicalEntities.ELDRITCH_CONSTRUCT.get(), com.efkrdnz.magical.client.renderer.eldritch.EldritchConstructRenderer::new);`.

- [ ] **Step 3: The renderer**

```java
package com.efkrdnz.magical.client.renderer.eldritch;

import com.efkrdnz.magical.client.model.eldritch.EldritchModels;
import com.efkrdnz.magical.client.model.geo.GeoModelBaker;
import com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell;
import com.efkrdnz.magical.entity.fx.EldritchConstructEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a construct: the profile's FX through the shell, then the creature.
 *
 * <p>The frame is the vanilla entity frame (face the yaw, flip into model space) with no vertical
 * offset, so the geometry's ground is the entity's feet. The pose is written into the bones by
 * name from {@link EldritchPose}; a bone the model does not have is skipped. The body is drawn
 * translucent with the lifecycle alpha, then the glow layer full bright if the model has one.
 */
public final class EldritchConstructRenderer extends ProfileRendererShell<EldritchConstructEntity> {
    private static final float SEGMENT_LENGTH = 4.0F;
    private static final float FORM_TICKS = 12.0F;
    private static final float DISSOLVE_TICKS = 12.0F;
    private static final float GAZE_LAG = 0.15F;
    private static final float TENTACLE_SWAY = 0.35F;
    /** How far the wards on a back sit from the spine, in blocks. */
    private static final double WARD_BACK = 0.3D;
    private static final float WARD_SPREAD_DEGREES = 28.0F;
    private static final String[] SEGMENT_BONES = {"seg0", "seg1", "seg2", "seg3", "seg4", "seg5"};

    public EldritchConstructRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends ProfileRendererShell.State {
        public String model = "";
        public byte anchor;
        public float scale = 1.0F;
        public int extra;
        public float yaw;
        public float ownerYaw;
        public Vec3 ownerOffset = Vec3.ZERO;
        public Vec3 target;
        public boolean hiddenFromWearer;
        public float gazeYaw;
        public float gazePitch;
    }

    @Override
    public ProfileRendererShell.State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EldritchConstructEntity entity, ProfileRendererShell.State base, float partialTick) {
        super.extractRenderState(entity, base, partialTick);
        State state = (State) base;
        state.model = entity.model();
        state.anchor = entity.anchor();
        state.scale = entity.scale();
        state.extra = entity.extra();
        Vec3 pos = entity.getPosition(partialTick);
        Entity owner = entity.owner();
        Minecraft minecraft = Minecraft.getInstance();
        state.hiddenFromWearer = state.anchor == EldritchConstructEntity.ANCHOR_OWNER && owner == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson();
        state.ownerOffset = owner != null ? owner.getPosition(partialTick).subtract(pos) : Vec3.ZERO;
        state.ownerYaw = owner instanceof LivingEntity living ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : 0.0F;
        Entity target = entity.target();
        Vec3 anchorPos = state.anchor == EldritchConstructEntity.ANCHOR_OWNER ? pos.add(state.ownerOffset) : pos;
        state.target = target != null ? target.getBoundingBox().getCenter().subtract(anchorPos) : null;
        Vec3 dir = entity.direction();
        float dirYaw = dir.lengthSqr() > 1.0E-6D ? (float) Math.toDegrees(Mth.atan2(-dir.x, dir.z)) : 0.0F;
        float wantedYaw = state.target != null ? (float) Math.toDegrees(Mth.atan2(-state.target.x, state.target.z)) : dirYaw;
        if (state.anchor == EldritchConstructEntity.ANCHOR_OWNER && !EldritchConstructEntity.MODEL_EYE.equals(state.model)) {
            wantedYaw = state.ownerYaw;
        }
        float wantedPitch = state.target != null ? (float) Math.atan2(state.target.y, Math.hypot(state.target.x, state.target.z)) : 0.0F;
        entity.gazeYaw = (float) Math.toDegrees(EldritchPose.ease((float) Math.toRadians(entity.gazeYaw), (float) Math.toRadians(wantedYaw), GAZE_LAG));
        entity.gazePitch = EldritchPose.ease(entity.gazePitch, wantedPitch, GAZE_LAG);
        state.gazeYaw = entity.gazeYaw;
        state.gazePitch = entity.gazePitch;
        state.yaw = EldritchConstructEntity.MODEL_EYE.equals(state.model) ? state.gazeYaw : wantedYaw;
    }

    @Override
    public void render(ProfileRendererShell.State base, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        State state = (State) base;
        if (state.hiddenFromWearer) {
            return;
        }
        super.render(base, pose, buffers, packedLight);
        EldritchModels.Entry entry = EldritchModels.get(state.model);
        if (entry == null) {
            return;
        }
        float alpha = EldritchPose.dissolve(state.age, state.life, DISSOLVE_TICKS);
        if (alpha <= 0.0F) {
            return;
        }
        pose.pushPose();
        if (state.anchor == EldritchConstructEntity.ANCHOR_OWNER) {
            pose.translate(state.ownerOffset.x, state.ownerOffset.y, state.ownerOffset.z);
        }
        if (EldritchConstructEntity.MODEL_TENTACLE.equals(state.model) && state.anchor == EldritchConstructEntity.ANCHOR_OWNER && state.extra > 0) {
            wards(state, entry, pose, buffers, packedLight, alpha);
        } else {
            creature(state, entry, pose, buffers, packedLight, alpha, state.yaw, state.scale, 0);
        }
        pose.popPose();
    }

    /** Skin of the Deep: {@code extra} small tentacles fanned across the back. */
    private void wards(State state, EldritchModels.Entry entry, PoseStack pose, MultiBufferSource buffers, int packedLight, float alpha) {
        int count = Math.min(state.extra, 8);
        for (int i = 0; i < count; i++) {
            float spread = (i - (count - 1) / 2.0F) * WARD_SPREAD_DEGREES;
            pose.pushPose();
            double back = Math.toRadians(state.ownerYaw);
            // Behind the owner: their facing is (-sin yaw, cos yaw), so the back is the opposite.
            pose.translate(Math.sin(back) * WARD_BACK + Math.cos(back) * Math.sin(Math.toRadians(spread)) * WARD_BACK,
                    0.9D, -Math.cos(back) * WARD_BACK + Math.sin(back) * Math.sin(Math.toRadians(spread)) * WARD_BACK);
            creature(state, entry, pose, buffers, packedLight, alpha, state.ownerYaw + 180.0F + spread, state.scale, i * 11);
            pose.popPose();
        }
    }

    private void creature(State state, EldritchModels.Entry entry, PoseStack pose, MultiBufferSource buffers, int packedLight,
            float alpha, float yaw, float scale, int seedOffset) {
        GeoModelBaker.Baked baked = entry.baked();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-scale, -scale, scale);
        posture(state, baked, seedOffset);
        int color = ((int) (alpha * 255.0F) << 24) | 0xFFFFFF;
        baked.root().render(pose, buffers.getBuffer(RenderType.entityTranslucent(entry.texture())), packedLight, OverlayTexture.NO_OVERLAY, color);
        if (entry.glow() != null) {
            baked.root().render(pose, buffers.getBuffer(RenderType.eyes(entry.glow())), packedLight, OverlayTexture.NO_OVERLAY, color);
        }
        pose.popPose();
    }

    private static void posture(State state, GeoModelBaker.Baked baked, int seedOffset) {
        float age = state.age + seedOffset;
        switch (state.model) {
            case EldritchConstructEntity.MODEL_TENTACLE -> tentacle(state, baked, age);
            case EldritchConstructEntity.MODEL_EYE -> eye(state, baked, age);
            case EldritchConstructEntity.MODEL_MAW -> maw(state, baked);
            default -> { }
        }
    }

    private static void tentacle(State state, GeoModelBaker.Baked baked, float age) {
        float[] pitch = new float[EldritchPose.SEGMENTS];
        boolean hasTarget = state.target != null && state.anchor != EldritchConstructEntity.ANCHOR_OWNER;
        float forward = 0.0F;
        float up = 0.0F;
        if (hasTarget) {
            // Into chain space: blocks to sixteenths, the horizontal distance along the front, the height up.
            forward = (float) Math.hypot(state.target.x, state.target.z) * 16.0F / state.scale;
            up = (float) state.target.y * 16.0F / state.scale;
        }
        EldritchPose.chain(EldritchPose.SEGMENTS, SEGMENT_LENGTH, forward, up, hasTarget, age, TENTACLE_SWAY, pitch);
        float grown = EldritchPose.grown(state.age, FORM_TICKS, EldritchPose.SEGMENTS);
        float lash = lashSweep(state);
        for (int i = 0; i < SEGMENT_BONES.length; i++) {
            ModelPart segment = baked.part(SEGMENT_BONES[i]);
            if (segment == null) {
                continue;
            }
            PartPose rest = segment.getInitialPose();
            segment.xRot = rest.xRot() + pitch[i] + (i == 0 ? lash : 0.0F);
            segment.yRot = rest.yRot();
            segment.zRot = rest.zRot();
            float s = EldritchPose.segmentScale(grown, i);
            segment.xScale = s;
            segment.yScale = s;
            segment.zScale = s;
        }
    }

    /** Tendril Lash: the base bends through a forward arc over the whip ticks the skill synced. */
    private static float lashSweep(State state) {
        if (state.data == null || !state.data.contains("whip")) {
            return 0.0F;
        }
        float whip = Math.max(1.0F, state.data.getInt("whip"));
        float t = Math.max(0.0F, Math.min(1.0F, state.age / whip));
        return 1.3F * (float) Math.sin(t * Math.PI);
    }

    private static void eye(State state, GeoModelBaker.Baked baked, float age) {
        ModelPart body = baked.part("body");
        if (body != null) {
            PartPose rest = body.getInitialPose();
            body.xRot = rest.xRot() - state.gazePitch;
            body.yRot = rest.yRot();
        }
        ModelPart pupil = baked.part("pupil");
        if (pupil != null) {
            float s = EldritchPose.pupil(state.target != null, age);
            pupil.xScale = s;
            pupil.yScale = s;
        }
        // Lids, if the model has them: open over the form ticks, shut again through the dissolve.
        float open = Math.min(EldritchPose.grown(state.age, FORM_TICKS, 1), EldritchPose.dissolve(state.age, state.life, DISSOLVE_TICKS));
        for (String name : new String[] {"lid_upper", "lid_lower"}) {
            ModelPart lid = baked.part(name);
            if (lid != null) {
                lid.xRot = lid.getInitialPose().xRot() * open;
            }
        }
    }

    private static void maw(State state, GeoModelBaker.Baked baked) {
        float windup = state.data != null && state.data.contains("windup") ? state.data.getInt("windup") : 20.0F;
        float snapAt = state.data != null && state.data.contains("snap") ? state.data.getInt("snap") : 0.0F;
        float gape = EldritchPose.jaws(state.age, windup, snapAt) * EldritchPose.MAX_GAPE;
        ModelPart upper = baked.part("jaw_upper");
        if (upper != null) {
            upper.xRot = upper.getInitialPose().xRot() - gape;
        }
        ModelPart lower = baked.part("jaw_lower");
        if (lower != null) {
            lower.xRot = lower.getInitialPose().xRot() + gape;
        }
    }
}
```

Match `createRenderState`'s declared return type to the shell's. `Entity.getPosition(float)` and `LivingEntity.yBodyRotO` exist in 1.21.4. Sign check on the frame: `ModelPart` +xRot bends a segment toward model -Z, which after the flip and the 180 - yaw turn is the direction the construct faces; the eye's `body.xRot = -pitch` because +xRot looks down. Verify both in the captures (Task 15) and flip a sign there if the tentacle curls away from its victim.

- [ ] **Step 4: The placeholders**

`scripts/eldritch-placeholders.py` (python 3, no dependencies: a small PNG writer with zlib, geometry via `json.dump`):

```python
"""Placeholder creatures for the eldritch kit: cubes in the school's teal over ink, one file
per asset in the contract, so the code can be seen moving before the real models exist.
Run from the project root: python scripts/eldritch-placeholders.py"""
import json, struct, zlib, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "magical")
MODELS = os.path.join(ROOT, "models", "entity", "eldritch")
TEXTURES = os.path.join(ROOT, "textures", "entity", "eldritch")
INK, TEAL, BRIGHT, DARK, BLACK = (6, 50, 42), (47, 191, 158), (95, 239, 208), (26, 122, 102), (0, 0, 0)


def png(path, size, pixels):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *pixels[y][x]) for x in range(size)) for y in range(size))
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    with open(path, "wb") as out:
        out.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                  + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def canvas(size, rgb):
    return [[rgb + (255,) for _ in range(size)] for _ in range(size)]


def box_uv(pixels, u, v, w, h, d, body, front, edge):
    """Paint one Box UV layout: top and bottom (d tall) then the four sides (h tall), the front face lit."""
    def fill(x0, y0, x1, y1, rgb):
        for y in range(y0, y1):
            for x in range(x0, x1):
                pixels[y][x] = rgb + (255,)
    fill(u + d, v, u + d + 2 * w, v + d, body)
    fill(u, v + d, u + 2 * d + 2 * w, v + d + h, body)
    fill(u + d, v + d, u + d + w, v + d + h, front)
    for x in (u, u + d, u + d + w, u + 2 * d + w, u + 2 * d + 2 * w - 1):
        fill(x, v + d, x + 1, v + d + h, edge)
    fill(u + d, v, u + d + 2 * w, v + 1, edge)


def write(name, size, bones, paint):
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry." + name, "texture_width": size, "texture_height": size,
        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]}, "bones": bones}]}
    with open(os.path.join(MODELS, name + ".geo.json"), "w", encoding="utf-8", newline="\n") as out:
        json.dump(geo, out, indent=2)
        out.write("\n")
    body, glow = canvas(size, INK), canvas(size, BLACK)
    paint(body, glow)
    png(os.path.join(TEXTURES, name + ".png"), size, body)
    png(os.path.join(TEXTURES, name + "_glow.png"), size, glow)


def tentacle():
    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    for i in range(6):
        half = 2.0 - i * 0.25
        bones.append({"name": "seg%d" % i, "parent": "root" if i == 0 else "seg%d" % (i - 1), "pivot": [0, i * 4, 0],
                      "cubes": [{"origin": [-half, i * 4, -half], "size": [2 * half, 4, 2 * half], "uv": [0, i * 8]}]})
    def paint(body, glow):
        for i in range(6):
            box_uv(body, 0, i * 8, 4, 4, 4, DARK, TEAL, INK)
            box_uv(glow, 0, i * 8, 4, 4, 4, BLACK, BRIGHT if i >= 3 else BLACK, BLACK)
    write("tentacle", 64, bones, paint)


def eye():
    bones = [{"name": "root", "pivot": [0, 0, 0]},
             {"name": "body", "parent": "root", "pivot": [0, 8, 0], "cubes": [{"origin": [-6, 2, -6], "size": [12, 12, 12], "uv": [0, 0]}]},
             {"name": "pupil", "parent": "body", "pivot": [0, 8, -6], "cubes": [{"origin": [-2, 6, -7], "size": [4, 4, 1], "uv": [0, 24]}]},
             {"name": "lid_upper", "parent": "body", "pivot": [0, 14, -6], "rotation": [-100, 0, 0], "cubes": [{"origin": [-6, 8, -7], "size": [12, 6, 1], "uv": [0, 30]}]},
             {"name": "lid_lower", "parent": "body", "pivot": [0, 2, -6], "rotation": [100, 0, 0], "cubes": [{"origin": [-6, 2, -7], "size": [12, 6, 1], "uv": [0, 38]}]}]
    def paint(body, glow):
        box_uv(body, 0, 0, 12, 12, 12, DARK, BRIGHT, INK)
        box_uv(body, 0, 24, 4, 4, 1, INK, INK, INK)
        box_uv(body, 0, 30, 12, 6, 1, DARK, DARK, INK)
        box_uv(body, 0, 38, 12, 6, 1, DARK, DARK, INK)
        box_uv(glow, 0, 0, 12, 12, 12, BLACK, TEAL, BLACK)
    write("eye", 64, bones, paint)


def maw():
    bones = [{"name": "root", "pivot": [0, 0, 0]},
             {"name": "jaw_lower", "parent": "root", "pivot": [0, 3, 6],
              "cubes": [{"origin": [-10, 0, -18], "size": [20, 3, 24], "uv": [0, 0]},
                        {"origin": [-8, 3, -16], "size": [2, 3, 2], "uv": [0, 60]}, {"origin": [6, 3, -16], "size": [2, 3, 2], "uv": [0, 60]}]},
             {"name": "jaw_upper", "parent": "root", "pivot": [0, 3, 6],
              "cubes": [{"origin": [-10, 3, -18], "size": [20, 3, 24], "uv": [0, 28]},
                        {"origin": [-2, 0, -16], "size": [4, 3, 2], "uv": [0, 60]}]}]
    def paint(body, glow):
        box_uv(body, 0, 0, 20, 3, 24, DARK, TEAL, INK)
        box_uv(body, 0, 28, 20, 3, 24, DARK, TEAL, INK)
        box_uv(body, 0, 60, 4, 3, 2, BRIGHT, BRIGHT, INK)
        box_uv(glow, 0, 60, 4, 3, 2, BRIGHT, BRIGHT, BLACK)
    write("maw", 128, bones, paint)


os.makedirs(MODELS, exist_ok=True)
os.makedirs(TEXTURES, exist_ok=True)
tentacle()
eye()
maw()
print("wrote placeholders to", MODELS, "and", TEXTURES)
```

Run it: `python scripts/eldritch-placeholders.py`. The maw's box layout is 88 wide, so its texture is 128. The eye's lids rest swung open (rotation -100 / +100) so the renderer's `open` factor of 0 lays them flat over the front face.

Add to `GeoModelParserTest`:

```java
    @Test
    void theShippedPlaceholdersCarryTheBonesTheRendererNames() throws java.io.IOException {
        java.nio.file.Path folder = java.nio.file.Path.of("src/main/resources/assets/magical/models/entity/eldritch");
        if (!java.nio.file.Files.isDirectory(folder)) {
            folder = java.nio.file.Path.of("../../src/main/resources/assets/magical/models/entity/eldritch");
        }
        java.util.Map<String, java.util.List<String>> named = java.util.Map.of(
                "tentacle", java.util.List.of("root", "seg0", "seg1", "seg2", "seg3", "seg4", "seg5"),
                "eye", java.util.List.of("root", "body", "pupil"),
                "maw", java.util.List.of("root", "jaw_upper", "jaw_lower"));
        for (var entry : named.entrySet()) {
            GeoModel model = GeoModelParser.parse(java.nio.file.Files.readString(folder.resolve(entry.getKey() + ".geo.json")));
            for (String bone : entry.getValue()) {
                assertTrue(model.bone(bone) != null, entry.getKey() + " lacks " + bone);
            }
        }
    }
```

(The junit runner starts in `build/minecraft-junit`, hence the `../../` fallback; copy `sourceRoot()` from `MagicalTooltipAssetsTest` if that path is also wrong.)

- [ ] **Step 5: Compile, run the parser test, commit**

Run: `./gradlew compileJava -q && ./gradlew test -q --tests "com.efkrdnz.magical.client.model.geo.GeoModelParserTest"`
Expected: clean; PASS.

```bash
git add src/main/java/com/efkrdnz/magical/client scripts/eldritch-placeholders.py src/main/resources/assets/magical/models/entity/eldritch src/main/resources/assets/magical/textures/entity/eldritch src/test/java/com/efkrdnz/magical/client/model/geo/GeoModelParserTest.java
git commit -q -F - <<'EOF'
feat: bake, load and draw the creatures - vanilla parts from Bedrock geometry, placeholders in teal

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---
### The skills: shared shape

Each of Tasks 8-13 creates one `SkillModule` in `src/main/java/com/efkrdnz/magical/magic/skill/eldritch/` and adds its line to `MagicCastContentEldritch.register()` (created in Task 8 and called from `MagicCastContent.init()` after `MagicCastContentDark.register();`). Each writes its game test into `src/main/java/com/efkrdnz/magical/magic/passive/EldritchGameTests.java` (created in Task 8 with the helpers below) first, watches it fail on "the cast must take" (no handler yet), then writes the module.

The game test scaffold (Task 8 creates it; later tasks add methods):

```java
package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.fx.EldritchConstructEntity;
import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicCastingService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.HoldService;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The calls to the deep, run against a real level: a grasp that roots and crushes, an eye that
 * reveals, jaws that snap, a lash that harries, wards that take a hit, a call that pulses and is
 * noticed. The rules a unit test can hold are held in {@code EldritchServiceTest}; what only a
 * level shows is that the constructs tick against a player and a victim who are actually there.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class EldritchGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final String BATCH = "eldritch";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);
    private static final BlockPos VICTIM = new BlockPos(4, 2, 2);

    private EldritchGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    private static List<EldritchConstructEntity> constructs(GameTestHelper helper, ServerPlayer player, ResourceLocation skill) {
        return EldritchConstructEntity.ownedBy(helper.getLevel(), player, skill, 32.0D);
    }

    /**
     * A survival player holding one call, standing at {@code at} with full mana: survival so the
     * bill is real and Notice moves, which is half of what these tests are about.
     */
    private static ServerPlayer eldritchMage(GameTestHelper helper, BlockPos at, ResourceLocation skill) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "deep-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        player.teleportTo(stand.x, stand.y, stand.z);
        state(player).unlockAll(Set.of(skill));
        state(player).refillMana();
        return player;
    }

    private static Zombie victim(GameTestHelper helper, ServerPlayer player) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, VICTIM);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
        return zombie;
    }
}
```

If a survival player's mana is not enough for a cast (the dev config's max mana), the cast fails with a mana message: set mana explicitly with `state(player).setMana(200)` after `refillMana` and check the first run's log.

Every `cast` charges Notice after everything that can refuse, with `EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()))`. The dispatcher syncs the state after SUCCESS (confirm with `grep -n "sync(player)" MagicCastingService.java`; if it does not, sync in the handler).

Potency is folded in at spawn: `float potency = EldritchService.potency(ctx.state());` scales the reach (`radius`) and the drawn `scale`; the damage stat is multiplied by it at each hit through a `potency` float in the construct's `serverData()` so the strength of a call is fixed when it is made.

### Task 8: Grasp of the Deep

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/cast/MagicCastContentEldritch.java`; modify `MagicCastContent.java` (`MagicCastContentEldritch.register();` after the dark line)
- Create: `src/main/java/com/efkrdnz/magical/magic/skill/eldritch/GraspOfTheDeepSkill.java`
- Create: `src/main/java/com/efkrdnz/magical/magic/passive/EldritchGameTests.java` (scaffold above + the test below); modify `EldritchPassives.reachForTheCaster` body

**Interfaces:**
- Produces: `GraspOfTheDeepSkill.BASE_REACH = 2.5D`, `AIM_RANGE = 20.0D`; `static EldritchConstructEntity grasp(ServerLevel level, SpellEffectEntity parentOrNull, CastContext ctxOrNull, Vec3 at, LivingEntity target, int life, float potency)` (the shared eruption Call reuses); `static void reachFor(ServerPlayer player, PlayerMagicState state)` (the Noticed rung).
- Keys in `serverData()`: `crush` (next crush tick), `potency`.

- [ ] **Step 1: The failing game test**

```java
    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void aGraspRootsWhatItReachesAndCrushesIt(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.GRASP_OF_THE_DEEP.id());
        Zombie zombie = victim(helper, player);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position());
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> helper.assertTrue(
                MagicCastingService.castById(player, MagicContent.GRASP_OF_THE_DEEP.id(), false), "the cast must take"));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(constructs(helper, player, MagicContent.GRASP_OF_THE_DEEP.id()).size() == 1, "one tentacle must erupt");
            helper.assertTrue(state(player).notice() == 12, "a grasp draws twelve notice, got " + state(player).notice());
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(MagicStatusService.has(zombie, MagicStatus.ROOTED), "the grasped thing must be rooted");
            helper.assertTrue(zombie.getHealth() < health, "and crushed");
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run to verify RED**

Run: `./gradlew runGameTestServer > gametest.log 2>&1; grep -n "eldritch\|FAILED\|passed\|failed" gametest.log | head -20`
Expected: the eldritch test fails at "the cast must take".

- [ ] **Step 3: The module**

```java
package com.efkrdnz.magical.magic.skill.eldritch;

import com.efkrdnz.magical.entity.fx.EldritchConstructEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ELDRITCH T-5 - a tentacle erupts where you looked and takes hold of the nearest thing.
 *
 * <p>Rooted for as long as it holds, crushed at every squeeze; with nothing to hold it sways and
 * takes whatever walks into reach for as long as it stands. The same eruption is what the deep
 * uses on a mage it has Noticed, and what Call of the Deep rains on a crowd.
 */
public final class GraspOfTheDeepSkill implements SkillModule {
    /** How far a grasp reaches for something to hold at one point of Reach, in blocks. */
    public static final double BASE_REACH = 2.5D;
    public static final double AIM_RANGE = 20.0D;
    /** Ticks from eruption to the first squeeze. */
    private static final int FORM_TICKS = 12;
    private static final int BASE_CRUSH_INTERVAL = 20;
    private static final int ROOT_TOP_UP = 15;
    private static final int REACH_HOLD_TICKS = 40;
    private static final String KEY_CRUSH = "crush";
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.GRASP_OF_THE_DEEP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                Vec3 at = ctx.aim() != null ? ctx.aim().point() : ctx.feet().add(ctx.look().scale(4.0D));
                float potency = EldritchService.potency(ctx.state());
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                grasp(ctx.level(), null, ctx, at, null, ctx.duration() + FORM_TICKS, potency);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.crush", "screen.magical.tuning.rise",
                        "screen.magical.tuning.reach", "screen.magical.tuning.hold", "screen.magical.tuning.thrift");
            }
        };
    }

    /**
     * The eruption itself. Either a cast (ctx) or a running effect (parent) supplies the skill,
     * owner and stats; a target given here is held from the first tick, else the grasp looks for
     * one within reach every tick.
     */
    public static EldritchConstructEntity grasp(ServerLevel level, SpellEffectEntity parent, CastContext ctx, Vec3 at,
            LivingEntity target, int life, float potency) {
        float size = ctx != null ? ctx.size() : 1.0F;
        float reach = (float) (BASE_REACH * size * potency);
        Vec3 dir = ctx != null ? ctx.look() : parent.direction();
        EldritchConstructEntity tentacle = ctx != null
                ? EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_TENTACLE, EldritchConstructEntity.ANCHOR_GROUND, at, life, reach, size * potency, dir)
                : EldritchConstructEntity.spawnChild(parent, EldritchConstructEntity.MODEL_TENTACLE, EldritchConstructEntity.ANCHOR_GROUND, at, life, reach, 0.8F * potency, dir);
        tentacle.serverData().putFloat(KEY_POTENCY, potency);
        if (target != null) {
            take(tentacle, target);
        }
        level.playSound(null, tentacle.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.0F, 0.6F);
        return tentacle;
    }

    /** The deep reaches for the mage it has Noticed: a grasp under their own feet, on them. */
    public static void reachFor(ServerPlayer player, PlayerMagicState state) {
        MagicSkillResolvedStats stats = MagicContent.GRASP_OF_THE_DEEP.resolve(MagicSkillTuning.DEFAULT);
        SpellEffectEntity template = SpellEffectEntity.create(player.serverLevel(), MagicContent.GRASP_OF_THE_DEEP, stats, null,
                player.position(), REACH_HOLD_TICKS, 1.0F, player.getLookAngle(), player.tickCount & 63);
        EldritchConstructEntity tentacle = EldritchConstructEntity.spawnChild(template, EldritchConstructEntity.MODEL_TENTACLE,
                EldritchConstructEntity.ANCHOR_GROUND, player.position(), REACH_HOLD_TICKS + FORM_TICKS, 1.0F, 1.2F, player.getLookAngle());
        tentacle.serverData().putFloat(KEY_POTENCY, EldritchService.potency(state));
        take(tentacle, player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.2F, 0.4F);
    }

    private static void take(EldritchConstructEntity tentacle, LivingEntity target) {
        tentacle.setTarget(target);
        tentacle.setPhase(SpellEffectEntity.PHASE_ACTIVE);
        MagicStatusService.apply(target, MagicStatus.ROOTED, Math.max(ROOT_TOP_UP, tentacle.life() - tentacle.tickCount), tentacle.skillId(), tentacle.owner());
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity tentacle) || tentacle.tickCount < FORM_TICKS) {
                    return;
                }
                LivingEntity held = tentacle.livingTarget();
                if (held == null || !held.isAlive() || held.distanceTo(tentacle) > tentacle.radius() * 2.0D + 1.0D) {
                    tentacle.setTarget(null);
                    tentacle.setPhase(SpellEffectEntity.PHASE_WINDUP);
                    if (tentacle.owner() == null) {
                        return;
                    }
                    LivingEntity nearest = null;
                    double best = Double.MAX_VALUE;
                    for (LivingEntity candidate : SkillTargets.hostilesWithin(tentacle.serverLevel(), tentacle.owner(), tentacle.position(), tentacle.radius())) {
                        double distance = candidate.distanceToSqr(tentacle);
                        if (distance < best) {
                            best = distance;
                            nearest = candidate;
                        }
                    }
                    if (nearest == null) {
                        return;
                    }
                    take(tentacle, nearest);
                    held = nearest;
                }
                CompoundTag scratch = tentacle.serverData();
                int interval = Math.max(6, Math.round(BASE_CRUSH_INTERVAL / Math.max(0.35F, tentacle.speed())));
                if (tentacle.tickCount >= scratch.getInt(KEY_CRUSH)) {
                    scratch.putInt(KEY_CRUSH, tentacle.tickCount + interval);
                    float potency = scratch.contains(KEY_POTENCY) ? scratch.getFloat(KEY_POTENCY) : 1.0F;
                    SkillTargets.hurt(tentacle.serverLevel(), tentacle.owner(), held, tentacle.damage() * potency, tentacle.skillId());
                    MagicStatusService.apply(held, MagicStatus.ROOTED, Math.max(ROOT_TOP_UP, tentacle.life() - tentacle.tickCount), tentacle.skillId(), tentacle.owner());
                }
            }

            @Override
            public void onExpire(SpellEffectEntity effect) {
                LivingEntity held = effect.livingTarget();
                if (held != null) {
                    MagicStatusService.clear(held, MagicStatus.ROOTED);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.TENDRIL).frame(3)
                        .band(GlyphKind.FACET_BAND, 12, ColorRole.BRIGHT)
                        .band(GlyphKind.WAVE_BAND, 8, ColorRole.INK)
                        .stamps(StampId.SPIRAL, 6).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.mark(FxKinds.Mark.INK_STAIN, 1.2F).withRole(ColorRole.DIM))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.TUNNEL)
                .budget(3)
                .bounds(3.0F, 3.0F, 1.0F);
    }
}
```

`SkillTargets.hurt(level, attacker, target, amount, skillId)` with a null attacker (the Noticed grasp) - check its body handles null (`MagicDamageService` may need a caster); if not, hurt the player with `player.hurtServer(level, level.damageSources().magic(), amount)` in that branch. `MagicSkillDefinition.resolve(MagicSkillTuning.DEFAULT)` exists (the judgement scenario command uses it). Lang: `crush`, `rise`, `hold` (`reach`, `thrift` exist).

`MagicCastContentEldritch`:

```java
package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.skill.eldritch.GraspOfTheDeepSkill;

/** ELDRITCH, the -5 layer: six calls to the deep, paid in mana and in being noticed. */
public final class MagicCastContentEldritch {
    private MagicCastContentEldritch() {}

    public static void register() {
        new GraspOfTheDeepSkill().register();
    }
}
```

`EldritchPassives.reachForTheCaster` body becomes `GraspOfTheDeepSkill.reachFor(player, state);`.

- [ ] **Step 4: Run to verify GREEN**

Run: `./gradlew runGameTestServer > gametest.log 2>&1; grep -n "aGrasp\|FAILED\|passed\|failed" gametest.log | head` and `./gradlew test -q --tests "com.efkrdnz.magical.visual.VisualProfilesTest"`
Expected: the grasp test passes; the lint is green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic src/main/resources/assets/magical/lang/en_us.json
git commit -q -F - <<'EOF'
feat: grasp of the deep - a tentacle erupts, roots and crushes

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 9: Unblinking Eye

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/skill/eldritch/UnblinkingEyeSkill.java`; register in `MagicCastContentEldritch`
- Modify: `EldritchPassives` (`outgoingSpellDamage`), `EldritchGameTests`

**Interfaces:**
- Produces: `UnblinkingEyeSkill.BASE_SIGHT = 12.0D`, `AIM_RANGE = 12.0D`, `SEEN_AMP = 1.25F`; `static boolean watching(ServerPlayer owner, LivingEntity target)`.

- [ ] **Step 1: The failing game test**

```java
    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void anEyeRevealsWhatItSeesAndTheStareStings(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.UNBLINKING_EYE.id());
        Zombie zombie = victim(helper, player);
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> helper.assertTrue(
                MagicCastingService.castById(player, MagicContent.UNBLINKING_EYE.id(), false), "the cast must take"));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(constructs(helper, player, MagicContent.UNBLINKING_EYE.id()).size() == 1, "one eye must open");
            helper.assertTrue(MagicStatusService.has(zombie, MagicStatus.REVEALED), "what the eye sees is revealed");
            helper.assertTrue(player.getUUID().equals(MagicStatusService.sourceOf(zombie, MagicStatus.REVEALED)), "to its owner");
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(zombie.getHealth() < health, "the stare stings");
            helper.succeed();
        });
    }
```

- [ ] **Step 2: RED** - run the game tests; the new one fails at "the cast must take".

- [ ] **Step 3: The module** (imports as Grasp, plus `com.efkrdnz.magical.magic.cast.AimResolver`, `net.minecraft.world.level.ClipContext`, `net.minecraft.world.phys.HitResult`):

```java
/**
 * ELDRITCH T-5 - a lidless eye hangs where you looked and watches.
 *
 * <p>What it sees is revealed to its owner, takes a quarter more of their spells (read by
 * {@code EldritchPassives.outgoingSpellDamage}) and feels the stare itself every two seconds. It
 * never blinks: the body rolls toward its mark and the pupil contracts on it, and that is all the
 * motion it has.
 */
public final class UnblinkingEyeSkill implements SkillModule {
    public static final double BASE_SIGHT = 12.0D;
    public static final double AIM_RANGE = 12.0D;
    public static final float SEEN_AMP = 1.25F;
    private static final int LOOK_INTERVAL = 5;
    private static final int STING_INTERVAL = 40;
    private static final int REVEAL_TICKS = 12;
    /** How far back from a wall the eye hangs. */
    private static final double WALL_GAP = 0.7D;
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.UNBLINKING_EYE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                AimResolver.Result aim = ctx.aim();
                Vec3 at = aim != null && aim.block() != null && aim.block().getType() == HitResult.Type.BLOCK
                        ? aim.point().add(aim.normal().scale(WALL_GAP))
                        : ctx.eye().add(ctx.look().scale(AIM_RANGE));
                float potency = EldritchService.potency(ctx.state());
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity eye = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_EYE,
                        EldritchConstructEntity.ANCHOR_GROUND, at, ctx.duration(), (float) (BASE_SIGHT * ctx.size() * potency), ctx.size() * potency, ctx.look());
                eye.serverData().putFloat(KEY_POTENCY, potency);
                eye.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, eye.blockPosition(), SoundEvents.SCULK_SENSOR_CLICKING, SoundSource.PLAYERS, 0.8F, 0.5F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED.labels("screen.magical.tuning.sting", null,
                        "screen.magical.tuning.sight", "screen.magical.tuning.watch", "screen.magical.tuning.thrift");
            }
        };
    }

    /** True when one of the owner's eyes has this thing in its stare. */
    public static boolean watching(ServerPlayer owner, LivingEntity target) {
        for (EldritchConstructEntity eye : EldritchConstructEntity.ownedBy(owner.serverLevel(), owner, MagicContent.UNBLINKING_EYE.id(), 64.0D)) {
            if (eye.target() == target) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity eye) || eye.tickCount % LOOK_INTERVAL != 0) {
                    return;
                }
                ServerLevel level = eye.serverLevel();
                LivingEntity seen = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity candidate : SkillTargets.hostilesWithin(level, eye.owner(), eye.position(), eye.radius())) {
                    double distance = candidate.distanceToSqr(eye);
                    if (distance < best && level.clip(new ClipContext(eye.position(), candidate.getEyePosition(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, eye)).getType() == HitResult.Type.MISS) {
                        best = distance;
                        seen = candidate;
                    }
                }
                eye.setTarget(seen);
                if (seen == null) {
                    return;
                }
                MagicStatusService.apply(seen, MagicStatus.REVEALED, REVEAL_TICKS, eye.skillId(), eye.owner());
                if (eye.tickCount % STING_INTERVAL == 0) {
                    float potency = eye.serverData().contains(KEY_POTENCY) ? eye.serverData().getFloat(KEY_POTENCY) : 1.0F;
                    SkillTargets.hurt(level, eye.owner(), seen, eye.damage() * potency, eye.skillId());
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.LIDLESS_EYE).frame(5)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.BRIGHT)
                        .band(GlyphKind.TICK_BAND, 24, ColorRole.INK)
                        .stamps(StampId.EYE, 5).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.THIN_HALO, 0.9F).withRole(ColorRole.BRIGHT))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.SPORE_DOTS, FxKinds.Overlay.IRIS_CLOSE)
                .budget(2)
                .bounds(2.0F, 2.0F, 1.0F);
    }
}
```

`AimResolver.Result.block()` is a `BlockHitResult` (check `resolve` lines 46-99 for how a miss is represented - a MISS-typed result or null). In `EldritchPassives`:

```java
    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target, ResourceLocation skillId, float amount) {
        return UnblinkingEyeSkill.watching(player, target) ? amount * UnblinkingEyeSkill.SEEN_AMP : amount;
    }
```

Lang: `sting`, `sight`, `watch`.

- [ ] **Step 4: GREEN** - game tests pass, lint green. **Step 5: Commit** `feat: unblinking eye - what it sees is revealed, amplified and stung`.

---

### Task 10: Hungering Maw

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/skill/eldritch/HungeringMawSkill.java`; register; `EldritchGameTests`

**Interfaces:**
- Produces: `HungeringMawSkill.BASE_BITE = 1.8D`, `AIM_RANGE = 16.0D`, `BASE_WINDUP = 24`; synced data keys `windup` (int) and `snap` (int tick, 0 until it snaps) the renderer reads.

- [ ] **Step 1: The failing game test**

```java
    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void jawsSnapOnWhatStandsInThem(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.HUNGERING_MAW.id());
        Zombie zombie = victim(helper, player);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position());
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> helper.assertTrue(
                MagicCastingService.castById(player, MagicContent.HUNGERING_MAW.id(), false), "the cast must take"));
        helper.runAtTickTime(10, () -> {
            List<EldritchConstructEntity> maws = constructs(helper, player, MagicContent.HUNGERING_MAW.id());
            helper.assertTrue(maws.size() == 1, "jaws must open");
            helper.assertTrue(maws.get(0).syncedData().getInt("snap") == 0, "and not snap before they are open");
            helper.assertTrue(zombie.getHealth() == health, "nothing bitten while opening");
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(zombie.getHealth() < health, "the jaws must snap on what stands in them");
            helper.succeed();
        });
    }
```

- [ ] **Step 2: RED.** **Step 3: The module** (imports as Grasp, plus `java.util.List`, `net.minecraft.world.entity.projectile.Projectile`):

```java
/**
 * ELDRITCH T-5 - jaws open in the ground where you looked and wait.
 *
 * <p>They open over the windup, hang open for at most the duration, and the moment something
 * hostile stands in them - or when the wait runs out - they snap shut: the bite, an upward launch,
 * and every hostile projectile inside the bite eaten. The renderer reads the windup and the snap
 * tick off the synced tag and does the rest.
 */
public final class HungeringMawSkill implements SkillModule {
    public static final double BASE_BITE = 1.8D;
    public static final double AIM_RANGE = 16.0D;
    public static final int BASE_WINDUP = 24;
    private static final int SNAP_TICKS = 3;
    private static final int LINGER_AFTER_SNAP = 14;
    private static final double BITE_HEIGHT = 2.2D;
    private static final String KEY_WINDUP = "windup";
    private static final String KEY_SNAP = "snap";
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HUNGERING_MAW;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                Vec3 at = ctx.aim() != null ? ctx.aim().point() : ctx.feet().add(ctx.look().scale(4.0D));
                float potency = EldritchService.potency(ctx.state());
                int windup = Math.max(6, Math.round(BASE_WINDUP / Math.max(0.35F, ctx.stats().speed())));
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity maw = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_MAW,
                        EldritchConstructEntity.ANCHOR_GROUND, at, windup + ctx.duration() + SNAP_TICKS + LINGER_AFTER_SNAP,
                        (float) (BASE_BITE * ctx.size() * potency), ctx.size() * potency, ctx.look());
                CompoundTag synced = new CompoundTag();
                synced.putInt(KEY_WINDUP, windup);
                synced.putInt(KEY_SNAP, 0);
                maw.setSyncedData(synced);
                maw.serverData().putFloat(KEY_POTENCY, potency);
                ctx.level().playSound(null, maw.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.5F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.bite", "screen.magical.tuning.snap",
                        "screen.magical.tuning.gape", "screen.magical.tuning.patience", "screen.magical.tuning.thrift");
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity maw)) {
                    return;
                }
                CompoundTag synced = maw.syncedData();
                int windup = synced.getInt(KEY_WINDUP);
                int snap = synced.getInt(KEY_SNAP);
                if (snap > 0) {
                    if (maw.tickCount >= snap + SNAP_TICKS + LINGER_AFTER_SNAP) {
                        maw.finish();
                    }
                    return;
                }
                if (maw.tickCount < windup) {
                    return;
                }
                maw.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ServerLevel level = maw.serverLevel();
                List<LivingEntity> standing = SkillTargets.hostilesInCylinder(level, maw.owner(), maw.position(), maw.radius(), BITE_HEIGHT);
                boolean waited = maw.tickCount >= windup + maw.duration();
                if (standing.isEmpty() && !waited) {
                    return;
                }
                float potency = maw.serverData().contains(KEY_POTENCY) ? maw.serverData().getFloat(KEY_POTENCY) : 1.0F;
                for (LivingEntity bitten : standing) {
                    SkillTargets.hurt(level, maw.owner(), bitten, maw.damage() * potency, maw.skillId());
                    bitten.setDeltaMovement(bitten.getDeltaMovement().add(0.0D, maw.knockback(), 0.0D));
                    bitten.hurtMarked = true;
                }
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, maw.getBoundingBox().inflate(maw.radius(), BITE_HEIGHT, maw.radius()),
                        p -> p.getOwner() != maw.owner())) {
                    projectile.discard();
                }
                CompoundTag shut = synced.copy();
                shut.putInt(KEY_SNAP, maw.tickCount);
                maw.setSyncedData(shut);
                maw.setPhase(SpellEffectEntity.PHASE_CLOSING);
                level.playSound(null, maw.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0F, 0.4F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.FANGED_MAW).frame(6)
                        .band(GlyphKind.TOOTH_BAND, 16, ColorRole.BRIGHT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.TOOTH, 8).core(CoreKind.VOID_PIT, ColorRole.HOT).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.rift(Silhouette.Form.DISC, FxKinds.Rift.IRIS_MOUTH, 1.0F, 1.0F, FxKinds.RiftInterior.VOID_BLACK))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.MAW, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.FLASH)
                .budget(3)
                .bounds(3.0F, 2.5F, 1.0F);
    }
}
```

Lang: `snap`, `gape`, `patience` (`bite` exists).

- [ ] **Step 4: GREEN**, lint. **Step 5: Commit** `feat: hungering maw - jaws that open, wait and snap`.

---

### Task 11: Tendril Lash

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/skill/eldritch/TendrilLashSkill.java`; register; `EldritchGameTests`

**Interfaces:**
- Produces: `TendrilLashSkill.BASE_LENGTH = 5.0D`, `BASE_WHIP = 12`, `ARC_DEGREES = 60.0D`; synced key `whip` (int).

- [ ] **Step 1: The failing game test**

```java
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aLashStingsShovesAndHarriesWhatIsAhead(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.TENDRIL_LASH.id());
        Zombie zombie = victim(helper, player);
        float health = zombie.getHealth();
        helper.runAtTickTime(1, () -> helper.assertTrue(
                MagicCastingService.castById(player, MagicContent.TENDRIL_LASH.id(), false), "the cast must take"));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(zombie.getHealth() < health, "the thing ahead is stung");
            helper.assertTrue(MagicStatusService.has(zombie, MagicStatus.HARRIED), "and harried");
            helper.succeed();
        });
    }
```

- [ ] **Step 2: RED.** **Step 3: The module**

```java
/**
 * ELDRITCH T-5 - a tentacle grows from your shoulder and whips through everything ahead.
 *
 * <p>The whip takes {@code BASE_WHIP / speed} ticks; at its middle everything hostile in a
 * forward arc of Length takes the sting, is shoved out of the arc sideways and is harried for the
 * duration. Then the tendril withdraws. The construct rides its owner (anchor OWNER) and the
 * renderer sweeps the base through the arc over the synced whip ticks.
 */
public final class TendrilLashSkill implements SkillModule {
    public static final double BASE_LENGTH = 5.0D;
    public static final int BASE_WHIP = 12;
    public static final double ARC_DEGREES = 60.0D;
    private static final int WITHDRAW_TICKS = 8;
    private static final String KEY_WHIP = "whip";
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.TENDRIL_LASH;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                float potency = EldritchService.potency(ctx.state());
                int whip = Math.max(4, Math.round(BASE_WHIP / Math.max(0.35F, ctx.stats().speed())));
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity tendril = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_TENTACLE,
                        EldritchConstructEntity.ANCHOR_OWNER, ctx.feet(), whip + WITHDRAW_TICKS,
                        (float) (BASE_LENGTH * ctx.size() * potency), 0.6F * ctx.size() * potency, ctx.look());
                CompoundTag synced = new CompoundTag();
                synced.putInt(KEY_WHIP, whip);
                tendril.setSyncedData(synced);
                tendril.serverData().putFloat(KEY_POTENCY, potency);
                tendril.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 0.9F, 1.1F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.sting", "screen.magical.tuning.snap",
                        "screen.magical.tuning.length", "screen.magical.tuning.stagger", "screen.magical.tuning.thrift");
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity tendril) || !(tendril.owner() instanceof LivingEntity owner) || !owner.isAlive()) {
                    effect.finish();
                    return;
                }
                tendril.setPos(owner.getX(), owner.getY(), owner.getZ());
                int whip = tendril.syncedData().getInt(KEY_WHIP);
                if (tendril.tickCount != Math.max(1, whip / 2)) {
                    return;
                }
                Vec3 look = tendril.direction();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() > 1.0E-6D ? flat.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
                double cos = Math.cos(Math.toRadians(ARC_DEGREES));
                float potency = tendril.serverData().contains(KEY_POTENCY) ? tendril.serverData().getFloat(KEY_POTENCY) : 1.0F;
                ServerLevel level = tendril.serverLevel();
                for (LivingEntity hit : SkillTargets.hostilesWithin(level, owner, owner.position(), tendril.radius())) {
                    Vec3 toward = hit.position().subtract(owner.position());
                    Vec3 towardFlat = new Vec3(toward.x, 0.0D, toward.z);
                    if (towardFlat.lengthSqr() < 1.0E-6D || towardFlat.normalize().dot(flat) < cos) {
                        continue;
                    }
                    SkillTargets.hurt(level, owner, hit, tendril.damage() * potency, tendril.skillId());
                    Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
                    double sign = towardFlat.dot(side) >= 0.0D ? 1.0D : -1.0D;
                    hit.setDeltaMovement(hit.getDeltaMovement().add(side.scale(sign * tendril.knockback()).add(0.0D, 0.15D, 0.0D)));
                    hit.hurtMarked = true;
                    MagicStatusService.apply(hit, MagicStatus.HARRIED, Math.max(20, tendril.duration()), tendril.skillId(), owner);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.LASH).frame(7)
                        .band(GlyphKind.ARC_SWEEP, 3, ColorRole.BRIGHT)
                        .band(GlyphKind.DASHED_RING, 18, ColorRole.INK)
                        .stamps(StampId.WAVE, 7).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.TRAIL, FxKinds.Filament.INK_TENDRIL, 3, 0.05F).withRole(ColorRole.BRIGHT))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.FLASH)
                .budget(2)
                .bounds(6.0F, 2.5F, 1.0F);
    }
}
```

Lang: `length`, `stagger`.

- [ ] **Step 4: GREEN**, lint. **Step 5: Commit** `feat: tendril lash - a whip from the shoulder`.

---

### Task 12: Skin of the Deep

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/skill/eldritch/SkinOfTheDeepSkill.java`; register; `EldritchPassives.incomingDamage`; `EldritchGameTests`

**Interfaces:**
- Produces: `SkinOfTheDeepSkill.WARDS_PER_SIZE = 4`, `MIN_WARDS = 2`, `MAX_WARDS = 8`, `BITE_REACH = 4.0D`; `static EldritchConstructEntity worn(ServerPlayer player)`; `static float absorb(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount)`.

- [ ] **Step 1: The failing game test**

```java
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aWardTakesTheHitAndBitesBack(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.SKIN_OF_THE_DEEP.id());
        Zombie zombie = victim(helper, player);
        float zombieHealth = zombie.getHealth();
        helper.runAtTickTime(1, () -> helper.assertTrue(
                MagicCastingService.castById(player, MagicContent.SKIN_OF_THE_DEEP.id(), false), "the cast must take"));
        helper.runAtTickTime(5, () -> {
            List<EldritchConstructEntity> skins = constructs(helper, player, MagicContent.SKIN_OF_THE_DEEP.id());
            helper.assertTrue(skins.size() == 1 && skins.get(0).extra() == 4, "four wards at one point of size");
            float health = player.getHealth();
            player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), 6.0F);
            helper.assertTrue(player.getHealth() == health, "a ward takes the hit, got " + player.getHealth() + " of " + health);
            helper.assertTrue(skins.get(0).extra() == 3, "and is spent, wards left " + skins.get(0).extra());
            helper.assertTrue(zombie.getHealth() < zombieHealth, "and bites what struck");
            helper.succeed();
        });
    }
```

- [ ] **Step 2: RED.** **Step 3: The module** (imports as Grasp, plus `net.minecraft.world.damagesource.DamageSource`):

```java
/**
 * ELDRITCH T-5 - small tentacles grow from your back and take the hits meant for you.
 *
 * <p>One ward per hit: it absorbs the whole hit, dissolves, and bites the thing that struck if it
 * is within reach. While any ward stands the wearer cannot be moved. The construct rides its
 * owner and its {@code extra} is the count of wards left; the renderer draws that many across the
 * back and hides them all from the wearer in first person, like the coagulate shell.
 */
public final class SkinOfTheDeepSkill implements SkillModule {
    public static final int WARDS_PER_SIZE = 4;
    public static final int MIN_WARDS = 2;
    public static final int MAX_WARDS = 8;
    public static final double BITE_REACH = 4.0D;
    private static final int IMMOVABLE_TOP_UP = 15;
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SKIN_OF_THE_DEEP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                EldritchConstructEntity standing = worn(player);
                if (standing != null) {
                    standing.finish();
                }
                float potency = EldritchService.potency(ctx.state());
                int wards = Math.max(MIN_WARDS, Math.min(MAX_WARDS, Math.round(WARDS_PER_SIZE * ctx.size())));
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity skin = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_TENTACLE,
                        EldritchConstructEntity.ANCHOR_OWNER, ctx.feet(), ctx.duration(), (float) BITE_REACH, 0.35F * potency, ctx.look());
                skin.setExtra(wards);
                skin.serverData().putFloat(KEY_POTENCY, potency);
                skin.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.0F, 0.8F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED.labels("screen.magical.tuning.bite", null,
                        "screen.magical.tuning.wards", "screen.magical.tuning.wear", "screen.magical.tuning.thrift");
            }
        };
    }

    /** The skin this player wears with wards left, or null. */
    public static EldritchConstructEntity worn(ServerPlayer player) {
        for (EldritchConstructEntity skin : EldritchConstructEntity.ownedBy(player.serverLevel(), player, MagicContent.SKIN_OF_THE_DEEP.id(), 4.0D)) {
            if (skin.extra() > 0) {
                return skin;
            }
        }
        return null;
    }

    /** A ward takes the whole hit and bites back. Returns what gets through: nothing, or all of it. */
    public static float absorb(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return amount;
        }
        EldritchConstructEntity skin = worn(player);
        if (skin == null) {
            return amount;
        }
        skin.setExtra(skin.extra() - 1);
        float potency = skin.serverData().contains(KEY_POTENCY) ? skin.serverData().getFloat(KEY_POTENCY) : 1.0F;
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player && attacker.distanceTo(player) <= BITE_REACH) {
            SkillTargets.hurt(player.serverLevel(), player, attacker, skin.damage() * potency, skin.skillId());
        }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_SENSOR_CLICKING, SoundSource.PLAYERS, 0.8F, 0.6F);
        if (skin.extra() <= 0) {
            skin.finish();
        }
        return 0.0F;
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity skin) || !(skin.owner() instanceof ServerPlayer owner) || !owner.isAlive() || skin.extra() <= 0) {
                    effect.finish();
                    return;
                }
                skin.setPos(owner.getX(), owner.getY(), owner.getZ());
                if (skin.tickCount % 10 == 1) {
                    MagicStatusService.apply(owner, MagicStatus.IMMOVABLE, IMMOVABLE_TOP_UP, skin.skillId(), owner);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity effect) {
                if (effect.owner() instanceof LivingEntity owner) {
                    MagicStatusService.clear(owner, MagicStatus.IMMOVABLE);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.SCALES).frame(8)
                        .band(GlyphKind.PETAL_BAND, 12, ColorRole.BRIGHT)
                        .band(GlyphKind.CHAIN_BAND, 10, ColorRole.INK)
                        .stamps(StampId.HEX, 8).core(CoreKind.HEX_LENS, ColorRole.HOT).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.field(Silhouette.Form.DOME, FxKinds.Field.ORGANIC_CELLS, 1.1F, 2.0F).withRole(ColorRole.DIM))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.HEX_CELLS, FxKinds.Smoke.SPORE_DOTS, FxKinds.Overlay.IRIS_CLOSE)
                .budget(3)
                .bounds(2.0F, 2.5F, 1.0F);
    }
}
```

In `EldritchPassives`:

```java
    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        return SkinOfTheDeepSkill.absorb(player, state, source, amount);
    }
```

Lang: `wards`, `wear`. If the game test shows the hit still landing, read `MagicGameplayEvents:240-260`: a zero result may need the event cancelled when the passives absorb everything (`if (damage <= 0.0F) { event.setCanceled(true); return; }`) - add it and say so in the commit.

- [ ] **Step 4: GREEN**, lint. **Step 5: Commit** `feat: skin of the deep - wards on the back that take the hit and bite`.

---

### Task 13: Call of the Deep

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/magic/skill/eldritch/CallOfTheDeepSkill.java`; register (`EldritchSchoolTest.everyCallIsWiredCommandableAndTranslated` goes green); `EldritchGameTests`

**Interfaces:**
- Produces: `CallOfTheDeepSkill.BASE_REACH = 10.0D`, `BASE_PULSE = 30`, `HOLD_GRACE = 5`, `EYE_HEIGHT = 2.6D`, `GRASP_TICKS = 30`.

- [ ] **Step 1: The failing game test**

```java
    @GameTest(template = TEMPLATE, timeoutTicks = 140, batch = BATCH)
    public static void aHeldCallPulsesGraspsAndIsNoticed(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.CALL_OF_THE_DEEP.id());
        Zombie zombie = victim(helper, player);
        state(player).setNotice(EldritchService.WATCHED_AT - 8);
        HoldService.setHeld(player, 0, true);
        helper.runAtTickTime(1, () -> helper.assertTrue(
                MagicCastingService.castById(player, MagicContent.CALL_OF_THE_DEEP.id(), false), "the cast must take"));
        helper.runAtTickTime(3, () -> helper.assertTrue(
                constructs(helper, player, MagicContent.CALL_OF_THE_DEEP.id()).size() >= 1, "the eye must open"));
        helper.runAtTickTime(35, () -> HoldService.setHeld(player, 0, true));
        helper.runAtTickTime(70, () -> {
            HoldService.setHeld(player, 0, true);
            helper.assertTrue(MagicStatusService.has(zombie, MagicStatus.ROOTED), "a pulse must grasp the thing in reach");
            helper.assertTrue(state(player).notice() >= EldritchService.WATCHED_AT, "two pulses past the rung: notice " + state(player).notice());
        });
        helper.runAtTickTime(75, () -> HoldService.setHeld(player, 0, false));
        helper.runAtTickTime(95, () -> {
            boolean eyeGone = constructs(helper, player, MagicContent.CALL_OF_THE_DEEP.id()).stream()
                    .noneMatch(c -> EldritchConstructEntity.MODEL_EYE.equals(c.model()));
            helper.assertTrue(eyeGone, "letting go ends the call");
            helper.succeed();
        });
    }
```

`HoldService.setHeld` has a 40-tick timeout, hence the re-holds. `castById` puts the cast in slot 0, which is what `extra` records.

- [ ] **Step 2: RED.** **Step 3: The module** (imports as Grasp, plus `java.util.List`, `com.efkrdnz.magical.magic.MagicSinService`, `com.efkrdnz.magical.magic.cast.HoldService`, `com.efkrdnz.magical.magic.eldritch.EldritchPrices`, `com.efkrdnz.magical.registry.MagicalAttachments`):

```java
/**
 * ELDRITCH T-5 - hold to call. A great eye opens above you and, at every pulse, a tentacle erupts
 * under something hostile in reach and holds it.
 *
 * <p>Every pulse is paid again - the mana and the Notice - so the longest call is the loudest.
 * The eye is the controller (anchor OWNER, above the head); the grasps are Grasp of the Deep's
 * eruption as children, briefly, with the caller's stats.
 */
public final class CallOfTheDeepSkill implements SkillModule {
    public static final double BASE_REACH = 10.0D;
    public static final int BASE_PULSE = 30;
    public static final int HOLD_GRACE = 5;
    public static final double EYE_HEIGHT = 2.6D;
    public static final int GRASP_TICKS = 30;
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CALL_OF_THE_DEEP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                float potency = EldritchService.potency(ctx.state());
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity eye = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_EYE,
                        EldritchConstructEntity.ANCHOR_OWNER, ctx.feet().add(0.0D, EYE_HEIGHT, 0.0D), ctx.duration(),
                        (float) (BASE_REACH * ctx.size() * potency), 2.5F, ctx.look());
                eye.setExtra(ctx.slot());
                eye.serverData().putFloat(KEY_POTENCY, potency);
                eye.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0F, 0.35F);
                return CastResult.SUCCESS;
            }

            @Override
            public boolean holdable() {
                return true;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.wrath", "screen.magical.tuning.cadence",
                        "screen.magical.tuning.reach", "screen.magical.tuning.call", "screen.magical.tuning.thrift");
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity construct)) {
                    return;
                }
                if (EldritchConstructEntity.MODEL_TENTACLE.equals(construct.model())) {
                    // A child grasp: hold what it took for its short life, one crush in the middle.
                    LivingEntity held = construct.livingTarget();
                    if (held != null && held.isAlive() && construct.tickCount == GRASP_TICKS / 2) {
                        float potency = construct.serverData().contains(KEY_POTENCY) ? construct.serverData().getFloat(KEY_POTENCY) : 1.0F;
                        SkillTargets.hurt(construct.serverLevel(), construct.owner(), held, construct.damage() * potency, construct.skillId());
                    }
                    return;
                }
                if (!(construct.owner() instanceof ServerPlayer player) || !player.isAlive()) {
                    construct.finish();
                    return;
                }
                construct.setPos(player.getX(), player.getY() + EYE_HEIGHT, player.getZ());
                boolean released = construct.tickCount > HOLD_GRACE && !HoldService.isHeld(player, construct.extra());
                if (released) {
                    construct.finish();
                    return;
                }
                int pulse = Math.max(10, Math.round(BASE_PULSE / Math.max(0.35F, construct.speed())));
                if (construct.tickCount % pulse != 1 || construct.tickCount == 1) {
                    return;
                }
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                if (!EldritchService.manaWaived(state) && !MagicSinService.spendManaForSkill(player, state, construct.definition().baseManaCost())) {
                    construct.finish();
                    return;
                }
                EldritchService.notice(player, state, EldritchPrices.base(construct.skillId()));
                state.sync(player);
                List<LivingEntity> hostiles = SkillTargets.hostilesWithin(player.serverLevel(), player, player.position(), construct.radius());
                if (hostiles.isEmpty()) {
                    return;
                }
                LivingEntity chosen = hostiles.get(player.getRandom().nextInt(hostiles.size()));
                construct.setTarget(chosen);
                float potency = construct.serverData().contains(KEY_POTENCY) ? construct.serverData().getFloat(KEY_POTENCY) : 1.0F;
                GraspOfTheDeepSkill.grasp(player.serverLevel(), construct, null, chosen.position(), chosen, GRASP_TICKS, potency);
            }

            @Override
            public void onExpire(SpellEffectEntity effect) {
                if (effect instanceof EldritchConstructEntity construct && EldritchConstructEntity.MODEL_TENTACLE.equals(construct.model())) {
                    LivingEntity held = construct.livingTarget();
                    if (held != null) {
                        MagicStatusService.clear(held, MagicStatus.ROOTED);
                    }
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.DEEP_CALL).frame(9)
                        .band(GlyphKind.RUNE_BAND, 18, ColorRole.BRIGHT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.RING, 9).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.SKY)
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.INK_BLOOM, 24, 1.6F).withRole(ColorRole.DIM))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.VORTEX_SPIRAL, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.TUNNEL)
                .holdable(true)
                .budget(3)
                .bounds(4.0F, 4.0F, 1.0F);
    }
}
```

The child grasps carry the call's skill id, so the call's behaviour ticks them (the `MODEL_TENTACLE` branch); `GraspOfTheDeepSkill.grasp` with a parent and a target sets ROOTED itself. Lang: `wrath`, `cadence`, `call`.

- [ ] **Step 4: GREEN** - all seven game tests, `EldritchSchoolTest` fully green, lint green. **Step 5: Commit** `feat: call of the deep - a held call the deep answers, pulse by pulse`.

---
### Task 14: The Notice game test, the whole suite, the build

**Files:**
- Modify: `EldritchGameTests` (one more test)

- [ ] **Step 1: The decay test**

```java
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void noticeCoolsForAMageWhoStaysQuiet(GameTestHelper helper) {
        ServerPlayer player = eldritchMage(helper, STAND, MagicContent.TENDRIL_LASH.id());
        state(player).setNotice(40);
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(state(player).notice() < 40, "notice must cool, still " + state(player).notice());
            helper.assertTrue(state(player).notice() >= 34, "but not faster than a point a slow tick: " + state(player).notice());
            helper.succeed();
        });
    }
```

If the fake player is never slow-ticked (still 40), drive the tick from the test every 10 ticks with `ClassPassiveEffects.slowTick(player, state(player))` and rename the test to say so.

- [ ] **Step 2: Everything**

Run: `./gradlew runGameTestServer > gametest.log 2>&1; grep -n "eldritch\|GameTest.*passed\|failed" gametest.log | tail -12` then `./gradlew build -q > build.log 2>&1; tail -20 build.log`
Expected: eight eldritch game tests pass (and the thirteen blood ones); the unit suite and the build are green.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/efkrdnz/magical/magic/passive/EldritchGameTests.java
git commit -q -F - <<'EOF'
test: notice cools for a quiet mage

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 15: Captures

**Files:** none new; screenshots land in `run/screenshots/`, are copied to the scratchpad and sent with `SendUserFile`.

- [ ] **Step 1: First person, a golem five blocks ahead - grasp, eye, maw, lash**

```bash
cd E:/magical-port && ./gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;magical reset;magical hud race human;magical unlockall;magical hud notice 60;time set day;tp @s ~ ~ ~ 0 0;summon iron_golem ~ ~ ~5 {NoAI:1b};120:magical-debug skill magical:grasp_of_the_deep;200:magical-debug skill magical:unblinking_eye;270:magical-debug skill magical:hungering_maw;340:magical-debug skill magical:tendril_lash" -PautoScreenshot=127,145,213,283,299,345,351 -PautoExit > launchA.log 2>&1; ls -t run/screenshots | head -8
```

Look at every capture with Read. What must be true: the tentacle erupts at the golem and curls toward it (tip at its chest, suckers facing it); the eye hangs ahead with its pupil toward the golem; the jaws open on the ground then snap; the lash sweeps past the golem from the shoulder; the notice line reads on the HUD; nothing is upside down or mirrored across the front. If a sign is wrong, fix it in `EldritchConstructRenderer` (the `- state.gazePitch`, the jaw `xRot` signs, or the chain pitch sign) and recapture.

- [ ] **Step 2: Third person from behind - the call held, then the skin**

```bash
cd E:/magical-port && ./gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;magical reset;magical hud race human;magical unlockall;magical hud notice 60;time set day;tp @s ~ ~ ~ 0 0;summon zombie ~ ~ ~4 {NoAI:1b};summon zombie ~3 ~ ~3 {NoAI:1b};magical hud equip 1 call_of_the_deep;150:magical-debug skill magical:skin_of_the_deep" -PautoHold=cast_slot_2 -PautoScreenshot=157,171,245,261,275 -PautoCamera=third_back -PautoExit > launchB.log 2>&1; ls -t run/screenshots | head -6
```

(`autoHold` clicks the key at hold start, 60 ticks before the first screenshot, and releases after it: the call is held from tick 97 to 157 and the first shot shows it; the skin is cast at 150 and the later shots show the wards on the back.) Expected: the great eye above the player, a tentacle under a zombie, then four small tentacles fanned across the back.

- [ ] **Step 3: Copy and send** the captures (`cp run/screenshots/<name>.png "$SCRATCHPAD/grasp.png"` etc.) with `SendUserFile`, status proactive, one caption per skill.

---

### Task 16: Ship - docs, memory, push, refresh

**Files:**
- Modify: `CLAUDE.md` (a `### Eldritch school` section after the blood one), `docs/superpowers/specs/2026-09-14-eldritch-kit-design.md` (status: implemented, with any sign fixes the captures forced)
- Memory: `C:\Users\CEO of SEX\.claude\projects\E--minecraft-mods-magical\memory\bedrock-geometry-to-vanilla-model-space.md` + `MEMORY.md` line

- [ ] **Step 1: CLAUDE.md**

Add after the blood section:

```markdown
### Eldritch school

Layer -5, six calls paid in mana and in **Notice** (`PlayerMagicState.notice`, 0..100: `EldritchService.notice` adds the `EldritchPrices` base scaled by `costScale`, `EldritchPassives.slowTick` cools it a point per slow tick, `potency` scales every construct 1..1.5 by it; Watched at 50 turns nearby hostiles on the caster, Noticed at 100 has the deep grasp the caster every 200 ticks). Three creatures, one entity: `entity/fx/EldritchConstructEntity` (a `SpellEffectEntity` with a model name, an anchor - GROUND, OWNER, TARGET - and a scale) drawn by `client/renderer/eldritch/EldritchConstructRenderer` (profile FX through `ProfileRendererShell`, then the model). Models are the Bedrock geometry Blockbench exports: `assets/magical/models/entity/eldritch/<name>.geo.json` + `textures/entity/eldritch/<name>.png` (+ `<name>_glow.png`, drawn full bright), Box UV only, read by `client/model/geo/GeoModelParser` (pure), baked by `GeoModelBaker` (x as written, y negated, z as written, degrees with the same sign; a rotated cube becomes its own part), held by `client/model/eldritch/EldritchModels` (reload listener: F3+T reloads an export). Motion is procedural in `EldritchPose` (chain CCD toward the target, growth from the base, dissolve, gaze ease, jaws): the tentacle bones are `seg0..seg5` under `root`, the eye is `body` + `pupil` (+ optional `lid_upper`/`lid_lower`), the maw is `jaw_upper` + `jaw_lower` hinged at the back. Skills in `magic/skill/eldritch/`: Grasp of the Deep (ground tentacle, roots and crushes; also the Noticed rung's grasp and Call's children), Unblinking Eye (floating eye: reveals, +25% spell damage on what it sees, stings), Hungering Maw (jaws open, wait, snap: `syncedData` `windup`/`snap`), Tendril Lash (owner-anchored whip through a forward arc, `syncedData` `whip`), Skin of the Deep (`extra` wards on the back absorb hits via `EldritchPassives.incomingDamage`, hidden from the wearer in first person), Call of the Deep (held: an eye above, a grasp under a random hostile per pulse, mana and Notice per pulse). Placeholders: `python scripts/eldritch-placeholders.py`. Captures: `magical hud notice <n>` sets the gauge; first person, a golem grasped, watched, bitten and lashed: `.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;magical reset;magical hud race human;magical unlockall;magical hud notice 60;time set day;tp @s ~ ~ ~ 0 0;summon iron_golem ~ ~ ~5 {NoAI:1b};120:magical-debug skill magical:grasp_of_the_deep;200:magical-debug skill magical:unblinking_eye;270:magical-debug skill magical:hungering_maw;340:magical-debug skill magical:tendril_lash" -PautoScreenshot=127,145,213,283,299,345,351 -PautoExit`; third person, the call held then the skin: the same launch with `-PautoCommands="...;summon zombie ~ ~ ~4 {NoAI:1b};magical hud equip 1 call_of_the_deep;150:magical-debug skill magical:skin_of_the_deep" -PautoHold=cast_slot_2 -PautoScreenshot=157,171,245,261 -PautoCamera=third_back -PautoExit`. Design: `docs/superpowers/specs/2026-09-14-eldritch-kit-design.md`.
```

- [ ] **Step 2: Memory** - one `project` memory: the Bedrock-to-vanilla conversion and the bone-name contract, with **Why** (the user models in Blockbench; every future creature school reads the same files) and **How to apply** (new geometry goes through `GeoModelParser`/`GeoModelBaker`; never negate x or z; degrees keep their sign). Add the `MEMORY.md` line.

- [ ] **Step 3: Commit, push, refresh**

```bash
cd E:/magical-port && git add CLAUDE.md docs/superpowers/specs/2026-09-14-eldritch-kit-design.md && git commit -q -F - <<'EOF'
docs: the eldritch school in CLAUDE.md, the spec marked implemented

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
git push origin main && git -C "E:/minecraft mods/magical" switch --detach main && git log --oneline -1
```

- [ ] **Step 4: The recap to the user**: what was built, the asset list (the table from the spec: paths, bone names, rest poses, texture rules, what is optional), what was verified (unit suite, build, eight game tests, the captures), and what to look at first (the placeholder captures show the motion; their models replace the placeholders file for file; F3+T reloads).

## Self-review

- Spec coverage: Notice (Tasks 1, 2), rungs and their effects (2, 8), HUD and codex lines (2), the construct entity (4), parser and the per-face UV refusal (5), motion with tests (6), baker conversion rules, reload listener, renderer with alpha and glow, placeholders and the script (7), six actives with the stated stats, tuning labels, anchors and models (8-13), the two passives (2: Lidless size in `adjustCast`, decay halving in the service), first-person hiding of the skin (7), the asset list handed over (16), game tests (8-14), captures (15), docs (16). Primordial is deferred by the spec.
- Placeholders: none; every step has its code. Names to verify against the tree while executing are called out inline (`unlockPassive`, `hurtServer`, `AimResolver.Result.block()` on a miss, `adjustCast` in the dispatcher, `SkillTargets.hurt` with a null attacker).
- Type consistency: `EldritchConstructEntity.spawn(ctx, model, anchor, pos, life, radius, scale, dir)` and `spawnChild(parent, model, anchor, pos, life, radius, scale, dir)` are used with that argument order in Tasks 8-13; `GraspOfTheDeepSkill.grasp(level, parent, ctx, at, target, life, potency)` is called from Task 8 (`parent = null`) and Task 13 (`ctx = null`) with that order; `EldritchPose.chain(segments, length, forward, up, hasTarget, age, sway, out)` matches Tasks 6 and 7; `EldritchModels.get(name)` returns `Entry(baked, texture, glow)` in both 7 and the renderer; the synced keys `windup`, `snap`, `whip` are the same strings in the skills and the renderer; `EldritchService.notice/cost/potency/manaWaived/decayStep/rung` match Tasks 1, 2, 8-13.
