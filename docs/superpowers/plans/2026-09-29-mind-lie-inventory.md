# Authority of Mind: the Lie Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** While Daydream is on, the wielder's hotbar becomes a **belt** of nine lies. The inventory key opens a creative-style **Lexicon inventory**: every learned impression, sorted into the same creative tabs it lives in, with only the tabs that hold something learned. Lies are taken from it onto the belt, held, and placed.

**Architecture:**
- **The server stores names only.** The belt is nine impression keys in `MindState`, saved with it and synced through the state tag. They are set by one validated payload.
- **The client draws everything.** It draws the belt in place of vanilla's hotbar, and the Lexicon inventory as a plain `Screen`.
- **Display-only item stacks.** The screen builds `ItemStack`s purely to draw icons. They are never sent anywhere, never enter a container, and never exist on the server.
- **Tabs.** Tab membership is read from vanilla's own `CreativeModeTab.getDisplayItems()`, so modded tabs appear by themselves. A pure `LexiconShelves` does the filtering, so it is testable.

**Tech Stack:** NeoForge 21.4.157, Minecraft 1.21.4 (Mojmap), Java 21, JUnit 5, NeoForge GameTest.

## Global Constraints

- **Nothing real, ever.**
  - No `ItemStack` built for a lie may reach a `Slot`, a menu, the player's inventory, a payload, or the server.
  - The server sees impression keys (strings) only, and re-validates every one: `Impression.parse(key) != null && lexicon.knows(key)`.
- **The belt has exactly 9 slots**, indexed 0-8. An empty slot is `null` in code and `""` on the wire and in NBT.
- **The real inventory is untouched while Daydream is on.**
  - Number keys and the wheel move the belt's selection, not vanilla's `inventory.selected`.
  - Clicks were already cancelled; that stays.
- **Playbill access is kept.** Vanilla's inventory key now opens the Lexicon inventory, and the Playbill is a tab button on it (waking Daydream only; hidden in your own dream).
- **Payload registrar version** goes `"10"` -> `"11"`.
- **Game tests** use the `unwaking_empty` template with a unique batch per test, and run with `./gradlew runGameTestServer --console=plain`.
- **The peer session's uncommitted files are never staged:**
  - `CLAUDE.md` working-tree hunks, `SKILL_CREATION_NOTES.md`, `gradlew`, `.claude/settings.local.json`
  - `client/SpaceManipulationOverlay.java`, `client/hud/HudDebug.java`
  - the untracked `SpaceManipulationLayout*.java`, `docs/assets/`, `logs/`
- **Stage and commit rules:**
  - Stage with `git add -- <paths>` and commit with no pathspec.
  - End every commit with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Decisions

- **D1. The belt lives on the server, as names.**
  - `MindState.belt()` returns a `Belt`: 9 nullable keys, save/load as a `ListTag` of strings under `"belt"`, copied in `copyFrom`, emptied in `clear`.
  - Load drops any entry that `Impression.parse` refuses.
  - Keeping it on the server means it survives a relog and a machine change. It reaches the client through the existing whole-state sync.
- **D2. One payload, `SetBeltSlotPayload(int slot, String key)`, play-to-server.**
  - The server handler is `MindService.setBeltSlot(ServerPlayer, int, String)`.
  - It refuses without the Mind Authority, a slot outside 0-8, or a non-empty key that fails parse or `knows`.
  - An empty key clears the slot. On success it calls `state.sync(player)`.
  - The key is capped at 256 chars in the codec.
- **D3. Learning offers to the belt.**
  - When an impression becomes known for the first time (`MindGazeService.learn`, `before == 0`), `belt.offer(key)` puts it in the first empty slot. If it is already on the belt, or there is no empty slot, nothing happens.
  - The lexicon commands (`all`, `block`, `creature`) offer each newly known key the same way. That fills the first nine for a capture.
- **D4. The belt selection is client-only** (`DaydreamMode.selected`, 0-8).
  - The wheel moves it the way vanilla's hotbar moves: wheel up is `selected - 1`, wrapping.
  - Alt+wheel still turns the brush.
  - Number keys 1-9 set it, taken off `options.keyHotbarSlots[i]` in `ClientTickEvent.Pre`, so vanilla never sees them.
  - `DaydreamMode.impression()` returns the belt key at `selected`, or null. A null key places nothing.
  - `keys()` and the reel index go away.
- **D5. The belt replaces vanilla's hotbar while Daydream is active and no screen is open.**
  - A `RenderGuiLayerEvent.Pre` handler cancels `VanillaGuiLayers.HOTBAR` and `VanillaGuiLayers.SELECTED_ITEM_NAME`.
  - `BeltHotbarOverlay` draws, in the same place: vanilla's `hud/hotbar` sprite, `hud/hotbar_selection` at the selection, the nine icons, and the selected lie's name above the hotbar. The name fades like vanilla's (shown 40 ticks after a selection change, then fading over 10), drawn with vanilla's drop shadow.
  - Hearts, food and XP stay; they are separate layers.
- **D6. The reel row goes.** `ImpressionReelOverlay` keeps only its status line (brush + budget, or the dream status) and its hint line. `ImpressionReelLayout` loses the reel cells, and `statusY` sits one line above `hintY`. Hints change:
  - `mind.magical.daydream.hint` becomes `"1-9 / Scroll: lie   Right: place   Left: erase   Alt+scroll: brush   %s: Lexicon"`.
  - `mind.magical.dream.hint` becomes `"1-9 / Scroll: lie   Alt+scroll: brush   Right: real   Left: unmake   Sneak+right: Flaw"`.
- **D7. The Lexicon inventory screen** (`client/screen/mind/LexiconInventoryScreen`, geometry in `LexiconInventoryLayout`, pinned by `LexiconInventoryLayoutTest`).
  - **What it is:** a plain `Screen` (no menu, no slots), `isPauseScreen()` false.
  - **Look:** vanilla creative. The panel is the 195x136 `textures/gui/container/creative_inventory/tab_items.png` background, with a 9x5 grid of 18px cells and the scroller sprites `container/creative_inventory/scroller` and `scroller_disabled`. The tabs use the `container/creative_inventory/tab_top_selected_N` / `tab_top_unselected_N` sprites along the top. The belt is drawn as the 9-cell row at the panel's bottom, where creative draws the hotbar.
  - **Tabs**, left to right:
    - **Search** (compass icon): all known lies filtered by an `EditBox`.
    - Every vanilla or modded `CreativeModeTab` of type `CATEGORY` that holds at least one known lie, in `CreativeModeTabs.tabs()` order.
    - **Other** (paper icon), when any known lie is in no tab.
    - At most 7 tabs a page, with page arrows when there are more.
    - The Playbill tab (writable book icon) sits at the panel's right edge, like creative's survival-inventory tab, only when `!DaydreamMode.dreaming()`.
  - **Contents per tab:** from `tab.getDisplayItems()`, keep each stack that is a `BlockItem`, keyed `block:<block id>`, or a `SpawnEggItem`, keyed `creature:<entity id>`. Keep it only if the lexicon knows the key. Deduplicate within a tab and keep display order.
    - Before reading, rebuild tab contents exactly as vanilla's creative screen does: `CreativeModeTabs.tryRebuildTabContents(player.connection.enabledFeatures(), player.canUseGameMasterBlocks() && options.operatorItemsTab().get(), level.registryAccess())`.
    - The **Other** tab holds known keys found in no tab. Its icon stack is `block.asItem()` when that is not AIR, else `Items.PAPER` for a block and `Items.NAME_TAG` for a creature without an egg.
  - **Mouse:**
    - Click a grid lie: it goes onto the cursor.
    - Click a belt slot while carrying: put it there. The slot's old lie goes onto the cursor, or the cursor empties.
    - Click a belt slot with an empty cursor: pick it up and clear the slot.
    - Shift-click a grid lie: the first empty belt slot, else the selected slot.
    - Hover a lie or slot and press 1-9: that belt slot takes it.
    - Click outside the panel while carrying: the carried lie is let go.
    - Closing lets go of what is carried.
  - **Wire and tooltips:** every belt change sends `SetBeltSlotPayload`. The tooltip is `MindGazeService.displayName(key)` plus a line of fidelity marks, `◆` per level out of 3 as `◆◆◇`.
- **D8.** `Esc` or the inventory key closes it. `LexiconInventoryScreen.open()` is called from `DaydreamMode.onClientTickPre` in place of `PlaybillScreen.open()`, in both waking and dream Daydream.
- **D9. Nothing renders in the hand.** The only sign of what is held is the belt and the name above it.

---

### Task 1: The belt on the server — `Belt`, `MindState`, the payload, learning offers

**Files:**
- Create: `magic/mind/Belt.java`, `network/SetBeltSlotPayload.java`, `magic/mind/BeltGameTests.java`
- Modify: `magic/mind/MindState.java`, `network/MagicalNetwork.java`, `magic/mind/MindService.java`, `magic/mind/MindGazeService.java`, `magic/mind/MindCommands.java`
- Test: `src/test/java/com/efkrdnz/magical/magic/mind/BeltTest.java`, `src/test/java/com/efkrdnz/magical/network/SetBeltSlotPayloadTest.java`

**Interfaces — produces:**
- `Belt.SIZE = 9`
- `String get(int)`: null when empty or out of range
- `void set(int, String)`: null or blank clears; out of range is ignored
- `boolean offer(String)`: fills the first empty slot unless already present; true if placed
- `int indexOf(String)`: -1 if absent
- `ListTag save()`, `void load(ListTag)`, `void copyFrom(Belt)`, `void clear()`
- `MindState.belt()`
- `SetBeltSlotPayload(int slot, String key)` with `TYPE`, `STREAM_CODEC`
- `MagicalNetwork.sendSetBeltSlot(int, String)`
- `MindService.setBeltSlot(ServerPlayer, int, String) -> boolean`

- [ ] **Step 1: Write the failing `BeltTest`**

```java
package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeltTest {
    @Test
    void offerFillsTheFirstEmptySlotAndNeverTwice() {
        Belt belt = new Belt();
        belt.set(0, "block:minecraft:stone");
        assertTrue(belt.offer("block:minecraft:dirt"));
        assertEquals("block:minecraft:dirt", belt.get(1));
        assertFalse(belt.offer("block:minecraft:dirt"), "an impression already on the belt was offered twice");
        for (int i = 2; i < Belt.SIZE; i++) {
            assertTrue(belt.offer("block:minecraft:b" + i));
        }
        assertFalse(belt.offer("block:minecraft:overflow"), "a full belt took one more");
    }

    @Test
    void blankClearsAndOutOfRangeIsIgnored() {
        Belt belt = new Belt();
        belt.set(3, "creature:minecraft:cow");
        belt.set(3, "");
        assertNull(belt.get(3));
        belt.set(9, "block:minecraft:stone");
        belt.set(-1, "block:minecraft:stone");
        assertNull(belt.get(9));
        assertEquals(-1, belt.indexOf("block:minecraft:stone"));
    }

    @Test
    void saveAndLoadKeepEverySlotAndDropWhatIsNotAnImpression() {
        Belt belt = new Belt();
        belt.set(0, "block:minecraft:stone");
        belt.set(4, "creature:minecraft:cow");
        var tag = belt.save();
        tag.set(8, net.minecraft.nbt.StringTag.valueOf("creature:minecraft:player"));
        Belt back = new Belt();
        back.load(tag);
        assertEquals("block:minecraft:stone", back.get(0));
        assertNull(back.get(1));
        assertEquals("creature:minecraft:cow", back.get(4));
        assertNull(back.get(8), "a player was loaded onto the belt");
    }
}
```

- [ ] **Step 2: Run it and watch it fail** (`./gradlew test --tests "com.efkrdnz.magical.magic.mind.BeltTest"`, which fails to compile)

- [ ] **Step 3: Write `Belt`**

```java
package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Arrays;

/**
 * The nine lies at hand while Daydreaming, in place of the hotbar. Names only: a lie is an
 * impression key, never an item, so nothing on the belt can ever be carried out as matter.
 */
public final class Belt {
    public static final int SIZE = 9;

    private final String[] keys = new String[SIZE];

    public String get(int slot) {
        return slot < 0 || slot >= SIZE ? null : keys[slot];
    }

    public void set(int slot, String key) {
        if (slot >= 0 && slot < SIZE) {
            keys[slot] = key == null || key.isBlank() ? null : key;
        }
    }

    /** A newly learned lie goes to the first empty slot, as a picked-up item goes to the hotbar. */
    public boolean offer(String key) {
        if (key == null || indexOf(key) >= 0) {
            return false;
        }
        for (int i = 0; i < SIZE; i++) {
            if (keys[i] == null) {
                keys[i] = key;
                return true;
            }
        }
        return false;
    }

    public int indexOf(String key) {
        for (int i = 0; i < SIZE; i++) {
            if (key != null && key.equals(keys[i])) {
                return i;
            }
        }
        return -1;
    }

    public ListTag save() {
        ListTag tag = new ListTag();
        for (String key : keys) {
            tag.add(StringTag.valueOf(key == null ? "" : key));
        }
        return tag;
    }

    public void load(ListTag tag) {
        clear();
        for (int i = 0; i < SIZE && i < tag.size(); i++) {
            if (tag.get(i).getId() == Tag.TAG_STRING) {
                String key = tag.getString(i);
                keys[i] = Impression.parse(key) == null ? null : key;
            }
        }
    }

    public void copyFrom(Belt other) {
        System.arraycopy(other.keys, 0, keys, 0, SIZE);
    }

    public void clear() {
        Arrays.fill(keys, null);
    }
}
```

- [ ] **Step 4: Put it on `MindState`.**
  - Add the field `private final Belt belt = new Belt();` and the accessor `public Belt belt()`.
  - In `save`, add `tag.put("belt", belt.save());`.
  - In `load`, add `belt.load(tag.getList("belt", Tag.TAG_STRING));`.
  - In `copyFrom`, add `belt.copyFrom(other.belt);`. In `clear`, add `belt.clear();`.
  - Then run `BeltTest` green.

- [ ] **Step 5: The payload.** Write `SetBeltSlotPayload` in the style of `SaveReveriePayload`:
  - It is a record `(int slot, String key)`.
  - `TYPE` = `magical:set_belt_slot`.
  - The codec is `ByteBufCodecs.VAR_INT` for the slot and `ByteBufCodecs.stringUtf8(256)` for the key.
  - Write `SetBeltSlotPayloadTest` first. It round-trips `(4, "block:minecraft:stone")` and `(0, "")` through the codec; copy how an existing test in `src/test/java/com/efkrdnz/magical/network/` round-trips a payload.
  - Register it in `MagicalNetwork.registerPayloads` beside `SaveReveriePayload`, as `playToServer`, with the handler running `MindService.setBeltSlot((ServerPlayer) context.player(), payload.slot(), payload.key())` on the main thread, following the exact form the neighbouring registrations use.
  - Bump `registrar("10")` to `registrar("11")`.
  - Add `public static void sendSetBeltSlot(int slot, String key)`, which sends `new SetBeltSlotPayload(slot, key == null ? "" : key)` to the server.

- [ ] **Step 6: The server handler** in `MindService`. Copy the authority check `saveReverie` uses:

```java
    /** A lie put on the belt, or taken off it. Names only, and only names the wielder has learned. */
    public static boolean setBeltSlot(ServerPlayer player, int slot, String key) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(AuthorityContent.MIND) || slot < 0 || slot >= Belt.SIZE) {
            return false;
        }
        String clean = key == null || key.isBlank() ? null : key;
        if (clean != null && (Impression.parse(clean) == null || !state.mind().lexicon().knows(clean))) {
            return false;
        }
        state.mind().belt().set(slot, clean);
        state.sync(player);
        return true;
    }
```

- [ ] **Step 7: Learning offers.**
  - In `MindGazeService.learn`, inside `if (before == 0)`, add `state.mind().belt().offer(key);` before the message.
  - In `MindCommands`, after each `lexicon().learn(...)` in `all`, `block` and `creature`, call `state.mind().belt().offer(<that key>)`. Only the first nine land, which is intended.

- [ ] **Step 8: `BeltGameTests`** (in `magic/mind/`, template `unwaking_empty`, batches `belt_1`..`belt_4`). Get a player the way `LullGameTests.wielder` does.
  - `belt_1`: a known key is set on slot 2 through `MindService.setBeltSlot`; it returns true and the belt holds it.
  - `belt_2`: an unknown key, slot 9 and `"creature:minecraft:player"` are each refused, and the belt is unchanged.
  - `belt_3`: a player without the Mind Authority is refused.
  - `belt_4`: `MindGazeService.learn` of a new key puts it on the first empty slot. Learning it a second time does not add it twice.

- [ ] **Step 9: Run** `./gradlew test`, `./gradlew build` and `./gradlew runGameTestServer --console=plain`, all green, then **commit** `feat(mind): a belt of nine lies, kept as names on the server`.

---

### Task 2: `LexiconShelves` — the creative tabs, filtered to what is learned (pure)

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/mind/LexiconShelves.java`
- Test: `src/test/java/com/efkrdnz/magical/client/mind/LexiconShelvesTest.java`

**Interfaces — produces:**
- `record Shelf(String id, List<String> keys)`, where `id` is the tab's registry id string, `"search"` or `"other"`.
- `static List<Shelf> shelve(List<Shelf> tabs, Set<String> known)`:
  - Keeps each tab's known keys, deduplicated and in order.
  - Drops tabs left empty.
  - Appends `Shelf("other", …)` with the known keys in no tab, sorted, when there are any.
- `static List<String> search(Collection<String> known, Function<String,String> nameOf, String query)`: known keys whose `nameOf` contains `query` case-insensitively; all of them when the query is blank; sorted by name, then by key.

- [ ] **Step 1: Write the failing test**

```java
package com.efkrdnz.magical.client.mind;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LexiconShelvesTest {
    private static final LexiconShelves.Shelf BUILDING = new LexiconShelves.Shelf("minecraft:building_blocks",
            List.of("block:minecraft:stone", "block:minecraft:dirt", "block:minecraft:stone"));
    private static final LexiconShelves.Shelf EGGS = new LexiconShelves.Shelf("minecraft:spawn_eggs",
            List.of("creature:minecraft:cow", "creature:minecraft:pig"));
    private static final LexiconShelves.Shelf MODDED = new LexiconShelves.Shelf("othermod:gadgets",
            List.of("block:othermod:widget"));

    @Test
    void onlyLearnedLiesAreShelvedInTheirTabsOrder() {
        var shelves = LexiconShelves.shelve(List.of(BUILDING, EGGS, MODDED),
                Set.of("block:minecraft:stone", "creature:minecraft:pig"));
        assertEquals(List.of("minecraft:building_blocks", "minecraft:spawn_eggs"), shelves.stream().map(LexiconShelves.Shelf::id).toList());
        assertEquals(List.of("block:minecraft:stone"), shelves.get(0).keys(), "a tab kept a duplicate or an unlearned lie");
        assertEquals(List.of("creature:minecraft:pig"), shelves.get(1).keys());
    }

    @Test
    void aModdedTabAppearsOnceSomethingInItIsLearned() {
        var shelves = LexiconShelves.shelve(List.of(BUILDING, MODDED), Set.of("block:othermod:widget"));
        assertEquals(List.of("othermod:gadgets"), shelves.stream().map(LexiconShelves.Shelf::id).toList());
    }

    @Test
    void whatNoTabHoldsGoesOnTheOtherShelf() {
        var shelves = LexiconShelves.shelve(List.of(BUILDING),
                Set.of("block:minecraft:water", "block:minecraft:stone", "creature:minecraft:iron_golem"));
        var other = shelves.get(shelves.size() - 1);
        assertEquals("other", other.id());
        assertEquals(List.of("block:minecraft:water", "creature:minecraft:iron_golem"), other.keys());
    }

    @Test
    void searchMatchesNamesAnyCaseAndBlankIsEverything() {
        var known = Set.of("block:minecraft:stone", "block:minecraft:dirt");
        Function<String, String> name = k -> k.endsWith("stone") ? "Stone" : "Dirt";
        assertEquals(List.of("block:minecraft:stone"), LexiconShelves.search(known, name, "sTo"));
        assertEquals(List.of("block:minecraft:dirt", "block:minecraft:stone"), LexiconShelves.search(known, name, " "));
    }
}
```

- [ ] **Step 2: Watch it fail.**
- [ ] **Step 3: Implement it** plainly: `LinkedHashSet` for per-tab dedupe, a `HashSet` of every key seen in any tab for Other, sorted order for Other, and a name-then-key sort for search. No Minecraft imports.
- [ ] **Step 4: Green, then commit:** `feat(mind): shelve learned lies into the creative tabs they live in`.

---

### Task 3: The belt in the hand — selection, the hotbar overlay, the reel's end

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/mind/BeltHotbarOverlay.java`, `src/main/java/com/efkrdnz/magical/client/mind/LieIcons.java`
- Modify: `client/mind/DaydreamMode.java`, `client/mind/ImpressionReelOverlay.java`, `client/mind/ImpressionReelLayout.java`, `client/hud/HudLayers.java` (one render call), `en_us.json`
- Test: `src/test/java/com/efkrdnz/magical/client/mind/ImpressionReelLayoutTest.java`, updated.

**Interfaces:**
- `DaydreamMode.selected()` returns 0-8.
- `DaydreamMode.impression()` returns `ClientMagicState.get().mind().belt().get(selected)`.
- `LieIcons.stack(String key) -> ItemStack`: display only. The block's item; for a creature, its spawn egg's stack; else `Items.PAPER` for a block or `Items.NAME_TAG` for a creature; `ItemStack.EMPTY` for null.
- `BeltHotbarOverlay.render(GuiGraphics, Minecraft)`, `BeltHotbarOverlay.selectionChanged()`, and a `@SubscribeEvent onLayer(RenderGuiLayerEvent.Pre)`.

- [ ] **Step 1: Update the layout test first.** In `ImpressionReelLayoutTest`, remove every assertion on `cell(...)`, `cellWidth()` and `reelY()`, and assert `statusY() == hintY() - LINE - 1`. The existing scrim and action-bar clearance assertions stay and must still hold. Run it red.
- [ ] **Step 2: Change `ImpressionReelLayout`.**
  - Delete `SIDE`, `GAP`, `MAX_CELL`, `MIN_CELL`, `cellWidth`, `cell` and `reelY`.
  - `statusY()` returns `hintY() - LINE - 1`.
  - `contentWidth(textWidth)` returns `Math.min(guiWidth, textWidth)`.
  - Run it green.
- [ ] **Step 3: `ImpressionReelOverlay` draws the status line and the hint line only.** Delete the reel loop.
- [ ] **Step 4: `DaydreamMode` works over the belt** (D4).
  - Replace `impression` (int) and `keys()` with `private static int selected;` and `public static int selected()`.
  - `impression()` returns the belt key at `selected`.
  - In `handleScroll`, the non-Alt branch sets `selected = Math.floorMod(selected + (delta > 0 ? -1 : 1), Belt.SIZE);` and calls `BeltHotbarOverlay.selectionChanged()`.
  - In `begin`, drop the `impression = ...` line.
  - In `onClientTickPre`, while `active && minecraft.screen == null`, for `i` in 0-8: if `minecraft.options.keyHotbarSlots[i].consumeClick()`, drain it, set `selected = i` and call `selectionChanged()`. The inventory-key branch stays as it is in this task (Task 4 changes it).
  - Grep for other callers of `DaydreamMode.keys()`, and point them at `ClientMagicState.get().mind().lexicon().keys()`.
- [ ] **Step 5: `LieIcons`**, as in the interfaces. For a creature, check the 1.21.4 signature of `SpawnEggItem.byId`.
- [ ] **Step 6: `BeltHotbarOverlay`** (D5).
  - `onLayer` cancels `VanillaGuiLayers.HOTBAR` and `VanillaGuiLayers.SELECTED_ITEM_NAME` when `DaydreamMode.active() && Minecraft.getInstance().screen == null`. It is registered with `@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)`.
  - `render` is called from `HudLayers` right beside `ImpressionReelOverlay.render`, and draws nothing unless `DaydreamMode.active() && screen == null`. It reproduces vanilla's `Gui.renderItemHotbar` geometry:
    - blit the sprite `hud/hotbar` at `(w/2 - 91, h - 22)`, 182x22;
    - blit `hud/hotbar_selection` at `(w/2 - 91 - 1 + selected*20, h - 23)`, 24x23;
    - draw each icon with `graphics.renderItem(stack, w/2 - 90 + i*20 + 2, h - 19)`, and no decorations;
    - draw the name popup where vanilla draws the selected item's name. Read the 1.21.4 `Gui.renderSelectedItemName` and copy its y, including its lift when the game mode cannot hurt the player. Show it for 40 ticks after `selectionChanged()`, fading over the last 10; draw it with shadow; skip it when alpha is under 4 (a string with alpha under 4 renders opaque).
  - Check the existing `ImpressionReelLayoutTest` bounds still pass, so the reel's lines stay out of the popup's band.
- [ ] **Step 7: Lang.** Replace the two hint strings with D6's text. Add `"mind.magical.lexicon.title": "Lexicon"`, `"mind.magical.lexicon.search": "Search"`, `"mind.magical.lexicon.other": "Other"`, `"mind.magical.lexicon.playbill": "Playbill"`, `"mind.magical.lexicon.empty": "Nothing learned yet: look at something for a while."`.
- [ ] **Step 8: Run** `./gradlew test`, `./gradlew build` and `./gradlew runGameTestServer --console=plain`, then **commit** `feat(mind): Daydream holds a belt of lies where the hotbar was`.

---

### Task 4: The Lexicon inventory screen

**Files:**
- Create: `src/main/java/com/efkrdnz/magical/client/screen/mind/LexiconInventoryScreen.java`, `LexiconInventoryLayout.java`
- Modify: `client/mind/DaydreamMode.java` (open it), and a debug command path (below)
- Test: `src/test/java/com/efkrdnz/magical/client/screen/mind/LexiconInventoryLayoutTest.java`

**Layout (pure record `LexiconInventoryLayout(int guiWidth, int guiHeight, boolean playbill)`), in vanilla creative's numbers:**
- `PANEL_W = 195`, `PANEL_H = 136`, with the panel centred: `left = (guiWidth - 195) / 2` and `top = (guiHeight - 136) / 2`.
- `grid(index)` for index 0-44 (row = index / 9, col = index % 9) is `Rect(left + 9 + col*18, top + 18 + row*18, 16, 16)`. `gridAt(mx, my)` maps a point to its index or -1.
- `belt(i)` is `Rect(left + 9 + i*18, top + 112, 16, 16)`. `beltAt(mx, my)` maps a point to its index or -1.
- `scroller()` is `Rect(left + 175, top + 18, 14, 112)`, and the thumb is 12x15 inside it.
- `search()` is `Rect(left + 82, top + 6, 80, 9)`.
- `tabsPerPage()` is 6 when `playbill`, else 7. `tab(i)` for i in range is `Rect(left + i*27, top - 28, 26, 32)`.
- `playbillTab()` is `Rect(left + 195 - 26, top - 28, 26, 32)`.
- `pagePrev()` is `Rect(left - 16, top - 22, 12, 12)` and `pageNext()` is `Rect(left + 195 + 4, top - 22, 12, 12)`.
- `LexiconInventoryLayoutTest` pins all of this at 320x240, 427x240, 480x270 and 640x360, both with and without the Playbill tab:
  - every rect lies within the gui;
  - grid cells, belt cells and tabs do not overlap one another, and no page tab overlaps the Playbill tab;
  - `gridAt` and `beltAt` answer to each cell's centre and to nothing in the 2px gap between cells.

**Screen behaviour (D7, D8):**
- Build the shelves in `init()`:
  - Rebuild the creative tabs as described in D7.
  - For each `CreativeModeTab` in `CreativeModeTabs.tabs()` with `getType() == CreativeModeTab.Type.CATEGORY`, map `getDisplayItems()` to keys (`BlockItem` gives `block:<id>`, `SpawnEggItem` gives `creature:<id>`, anything else is skipped) into a `Shelf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString(), keys)`.
  - Call `LexiconShelves.shelve(tabs, lexicon.keys())`.
  - Put Search first. The tab icon is `tab.getIconItem()`, Other is paper, Search is a compass. The tab title is `tab.getDisplayName()`, `mind.magical.lexicon.search` or `mind.magical.lexicon.other`.
- Draw it the vanilla creative way:
  - the unselected tabs, then the panel background (`textures/gui/container/creative_inventory/tab_items.png`, or `tab_item_search.png` on Search);
  - then the selected tab, the tab icons, the grid icons (`LieIcons.stack`) for the current scroll row, the belt icons with a white 1px frame round `DaydreamMode.selected()`, the scroller, the title (colour 0x404040 no shadow, at `left + 8, top + 6`), the carried icon at the cursor, and finally the tooltip.
  - Read 1.21.4 `CreativeModeInventoryScreen` for the exact sprite names and blit calls, and match them.
- Scroll: the wheel over the panel scrolls rows, and the thumb drags.
- Mouse and keys follow D7. Every belt change:
  - calls `MagicalNetwork.sendSetBeltSlot`;
  - sets the client's own `ClientMagicState.get().mind().belt()` at once, so it redraws. The server's sync then replaces it with the truth.
- Tooltip: `MindGazeService.displayName(key)`, then fidelity marks from `lexicon.fidelity(key)`.
- With nothing learned, show `mind.magical.lexicon.empty` centred in the grid.
- Playbill tab: shown when `!DaydreamMode.dreaming()`. A click closes this screen and calls `PlaybillScreen.open()`.
- `isPauseScreen()` returns false, and `keyPressed` closes on the inventory key as well as Esc.
- `public static void open()` sets the screen only while `DaydreamMode.active()`.
- In `DaydreamMode.onClientTickPre`, the inventory key now calls `LexiconInventoryScreen.open()`, in both waking and dream Daydream.
- **Debug path for captures.** `/magical mind lexicon open` opens it on the client the same way `/magical mind playbill` reaches the client; read that path and copy it. It opens only while Daydream is active. If the screen needs the `HudDebug.Captured` marker to survive the capture auto-closer, implement that interface.

Steps:
- [ ] **Step 1:** Write `LexiconInventoryLayoutTest` against the spec above and watch it fail.
- [ ] **Step 2:** Write `LexiconInventoryLayout` and get the test green.
- [ ] **Step 3:** Write `LexiconInventoryScreen`.
- [ ] **Step 4:** Wire `DaydreamMode` and the debug command.
- [ ] **Step 5:** Run `./gradlew test`, `./gradlew build` and `./gradlew runGameTestServer --console=plain`, then commit `feat(mind): the Lexicon inventory, the creative tabs of what you have learned`.

---

### Task 5: Captures, guide, delivery (the controller does this)

- [ ] **Step 1: Captures of the belt and the Lexicon.** Daydream is toggled by `-PautoClick=240:hold;243:release`, and the screen is opened by `/magical mind lexicon open`. Look at every frame, fix what is wrong, and send the frames.
- [ ] **Step 2: Update the guide.** Put a paragraph in `CLAUDE.md` through the index only, with a scratchpad python script.
- [ ] **Step 3: Verify and deliver.** Run the full suite in a clean worktree, then push and refresh the play checkout.
