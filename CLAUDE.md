# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

"The Iceberg of Magic" — a NeoForge mod for Minecraft **1.21.4** (NeoForge 21.4.157, Java 21). Mod id: `magical`, base package `com.efkrdnz.magical`. Design intent in `Iceberg_of_Magic_Design_Document.md` (tiered magic: Arcane → Elemental → hidden Authority endgame) and `SKILL_CREATION_NOTES.md` (visual contract: no reused vanilla projectiles, custom renderers + shaders).

## Commands

```powershell
.\gradlew build                      # compile + assemble mod jar
.\gradlew runClient                  # dev Minecraft client
.\gradlew runServer                  # dev dedicated server (--nogui)
.\gradlew runData                    # run data generators → src/generated/resources/
.\gradlew runGameTestServer          # run registered gametests headlessly
.\gradlew test                       # JUnit 5 unit tests
.\gradlew test --tests "com.efkrdnz.magical.SomeTest"  # single test class
```

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

1. **Client tick** — `MagicalClientEvents.Hud.onClientTick` polls `MagicalKeyMappings` (Z/X/C = 3 loadout slots, B = wheel, V = wheel-cast, K = codex). Hold/charge skills (Barrage, Subspace, Aegis, Black Flames, etc.) are intercepted by per-skill input handlers (`MagicBarrageInput`, `SpaceAuthorityInput`, `SovereignAegisInput`, etc.) before normal cast. Normal press sends a `CastLoadoutSlotPayload`.

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

Arcane content goes in `arcane/ArcaneContent` (rune/shape/modifier records) — separate flow, not the `magic/` system.