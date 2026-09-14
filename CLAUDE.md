# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

"The Iceberg of Magic" — a NeoForge mod for Minecraft **1.21.4** (NeoForge 21.4.157, Java 21). Mod id: `magical`, base package `com.efkrdnz.magical`. Design intent in `Iceberg_of_Magic_Design_Document.md` (tiered magic: Arcane → Elemental → hidden Authority endgame) and `SKILL_CREATION_NOTES.md` (visual contract: no reused vanilla projectiles, custom renderers + shaders).

## Commands

```powershell
.\gradlew build                      # compile + assemble mod jar
.\gradlew runClient                  # dev Minecraft client
.\gradlew runClient -PquickPlay="New World" -PautoScreenshot=200   # join a world at launch; screenshot 200 ticks in
.\gradlew runClient -PquickPlay="New World" -PautoScreenshot=300 -PautoCommands="magical unlockall;magical hud equip 1 gabriel" -PautoHold=cast_slot_2   # ... after commands at tick 40, holding X for the last 60 ticks
.\gradlew runClient -PquickPlay="New World" -PautoCommands="magical hud rule gravity increase_gravity;100:magical hud rule velocity stop" -PautoScreenshot=52,112 -PautoExit   # commands at ticks 40 and 100, a capture at each, then the game closes itself
.\gradlew runServer                  # dev dedicated server (--nogui)
.\gradlew runData                    # run data generators → src/generated/resources/
.\gradlew runGameTestServer          # run registered gametests headlessly
.\gradlew test                       # JUnit 5 unit tests
.\gradlew test --tests "com.efkrdnz.magical.SomeTest"  # single test class
```

- On macOS/Linux use `./gradlew`; Gradle 9.2 needs a JDK ≤ 25 as `JAVA_HOME` (the Java 21 toolchain is auto-provisioned).
- `src/main/templates/META-INF/neoforge.mods.toml` is a template; `${mod_id}` etc. are expanded from `gradle.properties`. Edit template + `gradle.properties`, never generated `mods.toml`.
- `src/generated/resources/` is a resources source dir alongside `src/main/resources/`. Datagen output.

## Architecture

### Entry point & registration

`MagicalMod.java` (`@Mod("magical")`) wires all `DeferredRegister` holders from `registry/` onto the mod bus. Each registry class follows the same pattern:
- `registry/MagicalBlocks.java` — `DeferredRegister.Blocks`
- `registry/MagicalItems.java` — `DeferredRegister.Items` (all `BlockItem`s)
- `registry/MagicalEntities.java` — ~40 entities, almost all `MobCategory.MISC`, invisible gameplay entities
- `registry/MagicalMenus.java`, `registry/MagicalAttachments.java`, `registry/MagicalCommands.java`, `registry/MagicalChunkTickets.java`, `registry/MagicalCreativeTabs.java`, `registry/MagicalBlockEntities.java`

Game-bus handlers are `@EventBusSubscriber(modid = MODID)` annotated classes — never manual `NeoForge.EVENT_BUS.register`.

### Three magic systems

1. **Arcane (Tier 1, composable spell-crafting)** — `arcane/` package. Runes + shapes + modifiers defined in `ArcaneContent`, resolved by `ArcaneSpellResolver`, cast via `ArcaneCastingService`. Has its own `AttachmentType` (`ARCANE_DATA`), own sync payload (`ArcanePlayerDataPayload`), own client mirror (`ClientArcaneState`). Separate from `magic/`.

2. **Elemental skill-based (Tiers 0-5)** — `magic/` package. ~80+ skills defined as `MagicSkillDefinition` records in `MagicContent` (static `register(...)` calls with base stats). Skills classified into sets controlling random unlocks/GUI: `SUB_SKILLS`, `AUTHORITY_SKILLS`, `CLASS_REWARD_SKILLS`, `CREATED_SKILLS`. Negative `tier` values mean hidden/authority content, excluded from random rewards.

3. **Authority (hidden endgame)** — `magic/AuthorityContent.java` defines endgame unlocks (Space, Soul, Chronos). Gated behind the Authority dimension progression. Skills like `CREATE_SUBSPACE`, `MANIPULATE_SPACE`, `POCKET_DIMENSION`, `SOUL_VOW`.

### Cast flow (keybind → render)

1. **Client tick** — `MagicalClientEvents.Hud.onClientTick` polls `MagicalKeyMappings` (Z/X/C/V = 4 loadout slots, B = loadout switcher / counter answer, K = codex, R = barrier refill). Hold/charge skills (Barrage, Subspace, Aegis, Black Flames, etc.) are intercepted by per-skill input handlers (`MagicBarrageInput`, `SpaceAuthorityInput`, `SovereignAegisInput`, etc.) before normal cast. Normal press sends a `CastLoadoutSlotPayload`.

2. **Server dispatch** — `MagicalNetwork` routes payloads. `MagicCastingService.castResolved` checks cooldown/unlock/mana, then routes via an **`if`-ladder** (special skills first: Vault of Avarice, Subspace, Space Walker, Aegis, etc.), then a `switch` on `MagicSkillType` (`PROJECTILE`/`BURST`/`BARRIER`). That `if`-ladder is the real routing table — every skill with custom behavior has an explicit ID check.

3. **Entity + renderer** — Service spawns a custom entity from `MagicalEntities`. The renderer (bound in `MagicalClientEvents.registerRenderers`) draws it using custom quads/beams/rings via `MagicalRenderTypes`.

### Player data & sync

- Two `AttachmentType`s, both `.copyOnDeath()`, registered in `MagicalAttachments`: `MAGIC_STATE` (`PlayerMagicState`) and `ARCANE_DATA` (`ArcanePlayerData`).
- Sync is copy-based and wholesale: `state.sync(player)` → `PlayerMagicStatePayload` → client; `MagicalNetwork.syncArcaneData(player, data)` → `ArcanePlayerDataPayload`.
- Static client singletons (`ClientMagicState`, `ClientArcaneState`) replace their contents on payload receipt.
- Side safety: `MagicalNetwork.handleClientPayload` uses `FMLEnvironment.dist.isClient()` + reflection into `client/ClientPayloadHandlers` — server never classloads client code.

### Network

All payload records in `network/` (24 payloads). `MagicalNetwork` registers them in `registerPayloads` using `event.registrar("1")`. Play-to-client: state sync, first-person effects, counter prompts. Play-to-server: cast requests, hold/charge state, wheel casts, class actions, space rule applications.

### Classes

`classes/MagicalClasses.java` — 16 classes (Blacksmith → Divinesmith, Warrior → Berserker/Warden, etc.) as `MagicalClassDefinition` records with parent class, XP cost, granted skill IDs. Tree-based progression tracked in `MagicalClassProgress`. `PlayerMagicState.evolveClass` validates prerequisites and grants skills.

### Passives & curses

`magic/MagicPassiveContent.java` — 8 normal passives (Mana Skin, Heat/Poison/Magic Resistance, Mana Flight, etc.) + 7 sin passives + 7 curses + 1 mana leak curse. Normal passives have a disable checkbox. Curses have no checkbox — require a Dispel action with conditions. Passives appear in a separate tab in the Magic Codex.

### Sin system

`magic/MagicSinService.java` — 7 deadly sins track player behavior (Pride from full-barrier casts, Wrath from taking damage, Greed from hoarding, Sloth from idling, etc.). Sin passives boost stats when gauge is high. Sin curses impose penalties if sin gauge reaches max. Accumulated in `PlayerMagicState`.

### Visual conventions (from SKILL_CREATION_NOTES.md)

- No reused vanilla projectiles, fireballs, or particles as main identity.
- Invisible gameplay entities + custom quad/beam/ring renderers.
- Custom core shaders in `assets/magical/shaders/core/` (`.vsh`, `.fsh`, `.json`), registered via `RegisterShadersEvent` → `MagicalRenderTypes`.
- Skills: unique mechanics first, visuals second.

### HUD

`client/hud/` — the in-game overlay, "the sigil": concentric ring meters (XP, mana, barrier, the charge halo) round a core that carries the numerals (level, mana, barrier, vault), a crown of sin satellites, a fan of cast cards on spokes with key tags and a seconds numeral while cooling, text readouts for the lit sins beyond the tags, captions under the fan, a reflection of the pools under a waterline for blood/dark mages, status chips under the crosshair, and announcements of what the player just gained. The hold overlays (sub-skill wheels, space dials, switcher, blood strip), the counter box and the combo readout are the original `GuiGraphics` code, untouched. Rules:
- **Layers, not a render event.** `HudLayers` registers `magical:first_person` / `sigil` / `rule_flash` / `encounter` / `selector` on `RegisterGuiLayersEvent`; the sigil layer also calls the original combo and counter renderers, the selector layer the original hold overlays. Modded layers are not gated by `hideGui` (`Gui.java:265` wraps only vanilla's groups); `HudLayers.gated` does it.
- **Tick builds, render reads.** `HudState.tick` (end of `Hud.onClientTick`, both branches) rebuilds an immutable `HudSnapshot` only when a version changed (`ClientMagicState`, `ClientCooldowns`, `ClientStatusState`, `HudAnnouncer`, keybinds, window, language, options). Renderers are pure functions of the snapshot + partial tick: no allocation, no `PlayerMagicState` calls, no registry lookups. Moving values are `HudTween`s; the only per-tick string is a cooling card's seconds, reshaped when the second changes.
- **One batch.** Everything is a quad on `MagicalFxRenderTypes.hudSigil()` (`rendertype_hud_sigil.fsh`, eleven kinds mirrored in `HudKind`) emitted through `HudBatch` inside one `drawSpecial`; text goes after it through `HudText` with vanilla's drop shadow. Never `GuiGraphics.fill` under `client/hud` — a test forbids it. `HudSnapshotBudgetTest` pins the quad count.
- **Geometry in `HudLayout`, colours in `HudPalette`, glyphs in `HudGlyphs`.** `HudLayoutTest` sweeps every anchor × scale × state for overlap and fit at 480×270, and checks the core holds its numerals. A skill's icon is its cast circle's emblem; sins and statuses are stamps.
- **Cooldowns** are not in the state blob; `CooldownSyncPayload` (dirty-set flushed from `PlayerMagicState.sync`/`tickServer`, full list on login/respawn/dimension change) feeds `ClientCooldowns`, which extrapolates. The original wheels read it too.
- **Options** in `MagicalClientConfig` (`config/magical-client.toml`): anchor, scale, opacity, sins, statuses, compact, reducedMotion, hudDebug. `/magical hud …` forces every HUD state (sins, charge, gluttony, corruption, vessel, status, cooldown, equip, race); `-PautoHold` captures a hold overlay.
- **The rule flash** (`magical:rule_flash` layer, `RuleFlash` + `RuleFlashRenderer`): when `SpaceAuthorityService.applyRule` accepts a Manipulate Space rule it sends `SpaceRuleAppliedPayload` to the caster, and the client pops the category's formula (`magic/SpaceRuleNotation`, one bracketed symbol per category) above the crosshair for 48 ticks with the symbol tinted and moved by the operation's `SpaceRuleChange` (RAISE / LOWER / ZERO / FLIP / LOCK / SURGE / AIM / RESTORE), one `RULE_MARK` quad playing the kind's mark behind it, a caption, a faint `fp_overlay` wash, and two synthesized cues (`assets/magical/sounds/rule/*.ogg`, from `scripts/synth-rule-cues.py`; `MagicalSounds.cue`). Three quads and at most five strings, only while live (`RuleFlashBudgetTest`); nothing when idle; `reducedMotion` shortens it to 32 ticks with static marks and no wash. `/magical hud rule <category> <operation> [target]` fires one without a subspace: `.\gradlew runClient -PquickPlay="New World" -PautoCommands="magical hud rule gravity increase_gravity everything" -PautoScreenshot=52`.

### Magic Codex screen

`client/screen/MagicPyramidScreen` (its menu, `magic/menu/MagicPyramidMenu`, carries only button ids and data slots) wears the same frame as the creator: `client/screen/ScreenChrome` owns the 444x340 panel, the top strip (title, chip, XP bar, corner button), the hairline, the tab row and the body panel, and paints them for both screens. Four tabs - Skills (the pyramid, the owned-skill emblem rows, the active loadout's four key cards, the skill detail with its tuning), Loadouts, Passives, Classes - replace the old Back-button sub-views. Every rectangle is in `CodexLayout`, and `CodexLayoutTest` pins per tab that nothing overlaps, everything sits in the body, and each hit-test answers to its centre and to nothing in the gaps. Emblems come from `client/screen/EmblemPainter` (shared with the creator) in one `drawSpecial` before the tooltips. `-PautoClick="tick:x,y;..."` presses the open screen for captures and the codex is `HudDebug.Captured`, so one launch walks the tabs: `.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="magical unlockall;101:magical codex" -PautoClick="118:247,45;132:339,45;146:431,45" -PautoScreenshot=112,126,140,154 -PautoExit`.

### Spell Creator screen

`client/screen/creator/` - the workshop where the Spell Creator / Magic Originator fuses two owned skills into a third. A plain `Screen` with no container menu behind it (no inventory, no button ids): `SpellCreatorLayout` is pure geometry pinned by `SpellCreatorLayoutTest` (nothing overlaps, every hit-test is a function of the same numbers); `SpellCreatorScreen` paints chrome, then every skill emblem in one `drawSpecial` through `EmblemPainter` (the HUD sigil atlas: `HudGlyphs.skillCell` + `HudPalette.cardTint`), then text; `FormulaListPanel` is the Formulas tab. Status comes from `MagicFusionService.status` / `formulas` (`READY`, `MISSING` inputs, `LOCKED` by class, `CREATED`; sorted in that order, stable by recipe). Opened by `OpenSpellCreatorPayload` (sent by `ClassTreeMenu` and `/magical creator [formulas | create <first> <second>]`) or straight from the codex Classes tab; the one thing it sends is `CreateSkillPayload` -> `MagicFusionService.create` (`/magical create <first> <second>` is the same call, for captures). Back sends the codex request. `/magical reset` wipes the magic state for a fresh start. `isPauseScreen` is false so the integrated server answers Create; it implements `HudDebug.Captured` so the auto-closer leaves it up: `.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="magical reset;magical class unlock spell_creator;magical unlockall;101:magical creator create wildfire abyssal_discharge;121:magical create wildfire abyssal_discharge;141:magical creator formulas" -PautoScreenshot=112,128,152 -PautoExit`.

### Custom dimensions

Three datapack dimensions under `data/magical/dimension{,_type}/`:
- `chronos_end` — the Chronos (Authority of Time) dimension
- `dungeon_tower` — the Dungeon Tower dimension (with `tower/DungeonTowerService.java`)
- `pocket_space` — personal pocket dimension (managed by `SpacePocketService`)

### Adding a new skill

1. `MagicContent` — add `register(...)` line; add id to relevant classifier set (`SUB_SKILLS`, `AUTHORITY_SKILLS`, `CLASS_REWARD_SKILLS`).
2. `MagicCastingService.castResolved` — add behavior (id branch in `if`-ladder, or generic type-based cast).
3. New entity → `entity/` class + `registry/MagicalEntities` + renderer in `client/renderer/` + bind in `MagicalClientEvents.registerRenderers`.
4. Hold/charge/multi-mode → payload in `network/` + handler in `MagicalNetwork` + client input handler wired in `MagicalClientEvents.Hud.onClientTick`.
5. Lang keys in `assets/magical/lang/en_us.json` (`skill.magical.<path>` + `.desc`).
6. Class grant lists in `MagicalClasses` if class-rewarded. Passives in `MagicPassiveContent` if passive-gated.
7. The HUD needs nothing: its card icon is the cast circle's emblem from the visual profile, and the cooldown sweep and seconds are automatic. Only a skill with no emblem needs a stamp in `client/hud/HudGlyphs`.

Arcane content goes in `arcane/ArcaneContent` (rune/shape/modifier records) — separate flow, not the `magic/` system.