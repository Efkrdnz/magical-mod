# Tower Dungeon Plan

**Status:** plan only. No code written. Awaiting confirmation.
**Scope:** the Main Tower (8 floors), side towers, and the dimension/instance model behind both.

---

## Verdict on the idea

**The gating loop is the strong part, and I would keep it exactly as described.** Main-tower floors
separated by mandatory side towers solves a real problem rather than an imagined one: an 8-floor
tower with a boss on every floor has to either flatten its power curve into blandness or spike so
hard that floor 5 becomes a wall. Putting the growth *between* floors lets each main floor be a
genuine step up, because the player is measurably stronger when they arrive. It is also the
structure the genre actually uses, so it reads as intentional rather than as padding.

It fits the existing code better than you might expect. `PlayerMagicState.towerClears` is a
`Map<ResourceLocation, Integer>` — progress is already keyed by tower ID, so side towers slot into
the save format with no migration at all. `DungeonTowerReward` already has `MAX_MANA`,
`MAX_BARRIER` and `PROFICIENCY` kinds, which is exactly the "clearing a side tower makes you strong
enough for the next floor" currency. And side towers are the natural home for the rumour system: a
side tower is where you *find out* a school of magic exists, which is what the iceberg needs.

**The part I would push back on is "each floor is another dimension" — as engineering, not as
design.** The intent is right and worth keeping. The literal implementation is a trap:

- Datapack dimensions are **static**. They are declared in `data/magical/dimension/*.json` and
  resolved at world load. There is no supported way to create one at runtime.
- Creating them dynamically means unfreezing `MappedRegistry` by reflection. It works, mods do it,
  and it breaks on version bumps and does not sync cleanly to clients.
- Side towers *spawn*, which is unbounded. Eight main floors is eight `ServerLevel`s and that is
  fine. Eight plus a side tower per clear plus repeats is not a fixed number, and every live
  `ServerLevel` costs memory and tick time whether or not anyone is inside it.

**The fix is cheap and loses nothing.** One dimension per **archetype**, not per instance. The
archetype supplies the identity you actually want — sky, fog, ambient light, ceiling, the whole
liminal-versus-ruins feel. The instance is a *plot*: a region inside that archetype, placed far from
every other plot. The current tower already does this on one axis (`FLOOR_SPACING = 256` along X);
this is the same trick promoted to a 2D grid with allocation and release. Five archetype dimensions
can host unlimited instances.

One thing worth saying plainly: **the current tower has the same disease as the old skill roster.**
`generateFloor` builds a 45×45 deepslate cylinder with an enchanting table and an end rod — and
builds the identical one for all 8 floors. Floor 1 and floor 8 are the same room. Whatever else this
plan does, that is the thing to kill.

---

## Requirements restatement

1. A **Main Tower** of 8 floors, each floor a distinct dungeon experience with its own world-feel.
2. Floor content varies in kind: open dimensions with strange structures (liminal / backrooms), and
   closed procedural structure dungeons whose sole goal is clear-it-and-kill-the-boss.
3. **Side towers** spawn after each main-floor clear.
4. A side tower must be **fully cleared** to unlock the next main floor.
5. Clearing a side tower confers real, permanent power growth — not just a key item.
6. Floor 8 is the Supreme Deity encounter (existing 474-line plan, currently zero code).

---

## Existing code this builds on

| Piece | Where | Reuse |
| --- | --- | --- |
| `DungeonTowerService` | `tower/` (417 lines) | Session lifecycle, entry, reward offers, floor-clear hook |
| `towerClears` map | `PlayerMagicState:667` | Per-tower progress, already keyed by `ResourceLocation` |
| `DungeonTowerReward` | `tower/` | `MAX_MANA` / `MAX_BARRIER` / `PROFICIENCY` — the side-tower payout |
| `grantFloorRumors` | `DungeonTowerService` | Already hooks floor clears into the discovery system |
| `MagicOpponentEntity` | `entity/` | Existing enemy; clones the player at a difficulty tier |
| `magical:dungeon_tower` | `data/magical/dimension/` | Becomes the first archetype rather than the only dimension |

**Patterns to mirror.** No structure or jigsaw infrastructure exists in this mod, so procedural
generation should follow the approach already in use: direct `level.setBlock(...)` writes from a
service class, as both `DungeonTowerService.generateFloor` and `SpacePocketService.buildRoom` do
today. Instance state lives in a `ConcurrentHashMap` keyed by instance, mirroring `SESSIONS`.
Player-facing feedback is `displayClientMessage` with a `message.magical.*` key. Do not invent a
structure pipeline for this.

---

## Architecture

### Archetype dimensions (five, static)

| Dimension | Feel | Generation |
| --- | --- | --- |
| `tower_liminal` | Backrooms. Low ceiling, endless, humming, no skylight, sour light | Grid of near-identical rooms with rare wrongness |
| `tower_ruins` | Closed procedural dungeon. Corridors, locked rooms, boss chamber | Room-graph generator |
| `tower_expanse` | Open void with floating monoliths and strange geometry | Sparse placement over void |
| `tower_sanctum` | Ceremonial arena. Boss only | Hand-authored per boss |
| `tower_hollow` | Cave-like, organic, vertical | Carved volume |

Each gets a `dimension_type` with genuinely different `ambient_light`, `has_ceiling`, `effects` and
fog — unlike the current three, which differ only in `ambient_light` and are otherwise identical.

### Instances as plots

```
plotOrigin(index) = ( (index % GRID) * PLOT_SPACING , 0 , (index / GRID) * PLOT_SPACING )
```

An `InstanceManager` allocates a free plot index in the requested archetype on entry, records owner
and expiry, and releases it on clear, failure, or timeout. Release wipes the plot so the next tenant
gets clean ground.

This is the single most important new component. Without release-and-wipe the archetype dimensions
accumulate abandoned rooms forever and the region files grow without bound.

### Progression

- Main floor `N+1` requires main floor `N` cleared **and** side tower `N` fully cleared.
- Side tower IDs are `magical:side_<n>` — new keys in the existing `towerClears` map, no migration.
- A side tower is a short run (3 floors) themed to one magic domain, with an escalating archetype.
- Completion grants a permanent bundle: max mana, max barrier, proficiency, and rumours weighted to
  that tower's domain. That *is* the strength gate; no separate key item is needed.

### Floor assignment (main tower)

| Floor | Archetype | Shape |
| --- | --- | --- |
| 1 | `tower_ruins` | Small dungeon, teaching run |
| 2 | `tower_liminal` | First wrongness. Navigation, not combat |
| 3 | `tower_ruins` | Larger dungeon, first real boss |
| 4 | `tower_expanse` | Open, vertical, environmental threat |
| 5 | `tower_hollow` | Descent. Enclosed, high pressure |
| 6 | `tower_liminal` | Liminal returns, hostile this time |
| 7 | `tower_expanse` | Wide approach to the summit |
| 8 | `tower_sanctum` | Supreme Deity |

---

## Phases

**Phase 1 — Instance substrate.** `InstanceManager` (allocate / release / wipe), plot geometry, a
chunk ticket controller so a live instance keeps ticking, and instance-scoped session state. Port
the existing main tower onto it unchanged, so behaviour is identical and the substrate is proven
before any content depends on it.

**Phase 2 — Archetype dimensions.** Five `dimension` + `dimension_type` pairs with genuinely
distinct settings, plus `DimensionSpecialEffects` registrations for those needing custom sky and
fog. `magical:chronos_end` already demonstrates that registration working.

**Phase 3 — Generators.** One generator per archetype behind a common interface. Room-graph for
`tower_ruins`, grid-with-wrongness for `tower_liminal`, sparse monoliths for `tower_expanse`, carved
volume for `tower_hollow`, hand-authored for `tower_sanctum`. Largest phase; ship one generator at a
time.

**Phase 4 — Side towers.** Spawn on main-floor clear, 3 floors each, domain-themed, with the
completion payout and the main-floor gate. Reuses phases 1–3 entirely.

**Phase 5 — Bosses.** Real boss entities per floor rather than reskinned `MagicOpponentEntity`
clones. Floor 8 is the Supreme Deity, which is its own project — see
`Supreme_Deity_Bossfight_Plan.md`.

**Phase 6 — Retire the old arena.** Delete the 45×45 deepslate cylinder once every floor has a real
generator behind it.

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Plot leak — instances never released | High | Region files grow without bound | Release-and-wipe lands in phase 1, not later; hard expiry on inactive instances |
| Procedural generation underestimated | High | Phase 3 slips badly | One generator at a time; `tower_ruins` first and alone |
| Five dimensions of memory and tick cost | Medium | Server strain | Archetypes are shared, not per-instance; unload when no instance is live |
| Chunk loading during a run | Medium | Instance stops ticking when the player steps away | Ticket controller in phase 1 — only `sovereign_seals` exists today |
| Supreme Deity blocks floor 8 | Medium | Tower has no ending | Floor 8 stays stubbed until its own plan is built |
| Side towers feel like filler | Medium | The gate reads as a chore | Each is domain-themed and is the primary rumour source for that domain |
| Multiplayer instance ownership | Low | Two parties collide on one plot | Plot allocation is per-party, keyed on the entering player |

**Complexity: LARGE.** Phase 1 is a few days. Phase 3 dominates and is open-ended. This is the
biggest single system in the mod — larger than the skill roster rework.

---

## Open questions

1. **Is the skill rework a prerequisite?** Side towers exist to make you stronger, but the roster
   they draw from is mid-rewrite. Building towers first means tuning their payouts twice.
2. **What does "fully cleared" mean for a side tower** — all 3 floors, or floors plus an optional
   challenge? Determines whether side towers have replay value.
3. **Do spawned side towers persist or expire?** One that waits forever is a checklist; one that
   expires creates pressure but can strand a player who logs off.
4. **Party play.** Instance per player or per party? Changes the allocation key and the reward split.
5. **Entry point.** Does the tower keep its own command entry, or does the Astral Gate block become
   the way in?

---

## Recommendation

Do phases 1 and 2 before committing to the full design. The instance substrate plus one archetype
dimension is enough to feel whether "each floor is its own world" actually lands in play. If it
does, everything after is content work on a proven base. If it does not, you have lost a few days
rather than a month.

**WAITING FOR CONFIRMATION** — proceed, modify, or different approach?
