# Floor 2 — The Kept Grounds

**Status:** design only. No code written.
**Archetype:** `tower_liminal` — one dimension, one floor, one boss.
**Supersedes:** the `tower_liminal` description in `TOWER_DUNGEON_PLAN.md` ("backrooms, low ceiling,
endless corridors") and Floor 2's role as a navigation-only teaching run. Both are dropped.

---

## The place

Flat mown grass to the horizon, in every direction, forever.

The sky is a blank overcast — no sun, no moon, no stars, no clouds, no movement. Light arrives
without a source. Nothing casts a shadow that points anywhere.

Scattered across the plain are structures at impossible spacing: too far apart to be a settlement,
too evenly placed to be accident. And on the horizon, always at the same distance no matter how far
you walk toward it, a castle.

**Why this is liminal.** Not corridors — *scale and regularity*. The horror is not that the place is
strange; it is that the place is **maintained**. The grass is cut. Something cuts it. Nobody is here.

This is the mod's first statement that reality is not simply *there* — it is *kept*. That is the
thematic seed the whole iceberg grows from, and it is why this floor grants rumours of the tiers
below the waterline.

---

## Progression — the Ender Dragon shape

The Overworld arc has no sub-bosses. It has **four verbs**: explore, gather, ritual, fight. This
floor uses the same shape, which removes an entire tier of bespoke enemies from the build.

### Step 1 — Arrival, and the false horizon

You land on open grass. The castle is plainly visible. Walking toward it does not bring it closer.

The plain loops: cross far enough in any direction and you arrive back where you were, without a
transition and without any cue that it happened. The player is meant to work this out themselves by
noticing a structure they already passed.

### Step 2 — Three sites, three problems

Three structures on the plain, each holding one key. **None is guarded by a monster.** Each is a
different kind of wrong, and each is solved by understanding rather than fighting.

| Site | What it is | The problem |
| --- | --- | --- |
| **The Mown Circle** | A perfect circle where the grass is *un*cut, waist-high | Standing inside it stops the plain looping. It is the only place the horizon behaves. Leaving resumes the loop, so it must be used to fix a bearing before walking. |
| **The Sunk House** | An ordinary house, buried to its eaves | Entry is downward through an upstairs window. Inside it is far larger than outside — several floors below ground that cannot fit in the mound. |
| **The Standing Door** | A doorframe alone in the field, no walls | Walking through moves you elsewhere on the plain. Which elsewhere depends on which side you enter from and which way you are facing. |

### Step 3 — The Presence

From the moment the first key is taken, something is on the plain with you.

It does not chase. It stands very far off, unmoving, roughly human. It never moves while observed.
It is closer every time it is not. Taking each key brings it substantially closer.

This replaces sub-boss combat entirely. It is the floor's pressure, it costs one entity with almost
no animation, and it is the single most on-theme threat available — the dread of open space is that
you can see a long way, and so can it.

If it reaches you, it does not kill you. It puts you back at the arrival point, and takes one key
back.

### Step 4 — The ritual

Three keys placed at the castle gate.

The horizon collapses. The castle is abruptly *here* — enormous, immediate, occupying the sky. The
plain behind you is gone. This is a hard environmental transition, not a cutscene: the sky palette
inverts, the light source finally resolves, and the grass stops being cut.

### Step 5 — The boss

---

## The Groundskeeper

Not a monster. A figure in work clothes, far too tall, carrying a scythe far too long. It has kept
these grounds for longer than the castle has stood. You have been walking on them.

### Phase 1 — Tending (100% → 50%)

It does not fight you. It **maintains** you, the way it maintains everything else here. Every attack
is groundskeeping, and every attack telegraphs as a groundskeeping motion.

| Attack | Motion | Telegraph |
| --- | --- | --- |
| **Mow** | A long flat sweep at ankle height, cutting a line across the arena | The scythe drops and the grass ahead lies flat before the blade arrives |
| **Edge** | Raises a clean vertical wall along a straight line, cutting the arena in half | A crisp seam opens in the turf first |
| **Uproot** | Pulls something out of the ground and throws it | The ground bulges before it gives |
| **Water** | A slow downpour over one region; footing goes | Rain sound precedes the rain |

Deliberately readable and deliberately unhurried. Phase 1 should feel like being *tidied*, not
attacked — the insult is that it is not taking you seriously.

### Transition at 50% — it stops working

It sets the scythe down.

> *"The grounds have been kept longer than the house has stood."*

### Phase 2 — Untended (50% → 0%)

**The boss barely attacks in this phase. The world does.**

With nothing maintaining it, the liminal space fails: turf vanishes in patches leaving void, the sky
tears, structures from elsewhere bleed through and overlap the arena, the loop that held the plain
together comes apart. Every second the collapse advances.

The Groundskeeper mostly stands and watches this happen. It is the safest thing in the room, and it
is the only thing you can kill to stop it — a soft enrage where the pressure is entirely
environmental.

This is the floor's thesis landing: the liminal space was only ever stable because something was
holding it that way.

### On defeat

The collapse stops mid-fall and holds — tiles frozen in the air, the tear in the sky not closing.
Nothing repairs. The exit opens in a place that was not there before.

---

## What the player takes away

Mechanically, floor-clear rumours (depth band 3–5, one rumour) per
`DungeonTowerService.grantFloorRumors`.

Narratively, the thing the floor is actually for: **reality is maintained, and something maintains
it.** That is a Chaos-tier and ultimately an Authority-tier idea arriving on Floor 2 as atmosphere
rather than exposition, which is exactly how the design document wants the iceberg revealed —
"the journey is from ignorant to enlightened."

---

## What to remove first

Per the decision to drop the test scaffolding, before this floor is built:

- `DungeonTowerService.generateFloor` — the 45×45 deepslate cylinder, identical on all 8 floors
- `DungeonTowerService.spawnFloorOpponents` — clone-based opponents
- The Astral Gate right-click entry into the test arena
- `MagicOpponentEntity` usage as dungeon content (the class may stay for other purposes)

Floor-clear detection currently means *"no `MagicOpponentEntity` alive in the arena box"* and must be
replaced with explicit objective state.

---

## Build order

| Phase | Work |
| --- | --- |
| **1** | **Fix the wipe strategy first** — see below. Nothing else can ship without it. |
| **2** | Dimension: `magical:tower_liminal` + `dimension_type` + a `DimensionSpecialEffects` for the blank overcast sky. Model on `ChronosSkyEffects`. |
| **3** | `LiminalLawnGenerator implements ArchetypeGenerator` — the plain, the loop, the three sites, the castle facade. No boss yet; walkable and explorable. |
| **4** | Objective state: keys, the ritual, floor-clear detection that is not "count the mobs". |
| **5** | The Presence — one entity, observation-gated movement. |
| **6** | Boss-bar infrastructure (`ServerBossEvent`) — the mod has none today. |
| **7** | The Groundskeeper: entity, renderer, phase state machine, four phase-1 attacks. |
| **8** | Phase 2 collapse, driven through the existing environment-effect machinery. |

---

## Technical constraints this design must respect

**The 512,000-block wipe cap is the blocker.** `InstanceManager.clearBox` refuses any wipe larger
than that and silently skips cleanup. A plain with monumental structures is far past it — a 200×200
area with a 60-tall castle is roughly 2.4M blocks of bounding box. Left as-is, every run leaks a
permanent castle.

Fix before anything else, in `InstanceManager`:
1. **Track placed positions.** The generator records what it actually wrote; cleanup touches only
   those. A castle is mostly hollow, so real placements are a fraction of the bounding box.
2. **Drain the wipe across ticks** rather than in one call, which removes the cap entirely.

The footprint contract then becomes advisory rather than load-bearing.

**No structure templates exist.** There is no jigsaw, no `StructureTemplate`, no `.nbt` files
anywhere in the mod. The castle and every site must be generated procedurally with direct
`level.setBlock` calls, following the idiom in `SpacePocketService.buildRoom`.

**Reuse the environment machinery.** `ChronosSequenceService` already runs a tick-timeline of
effects (sky cut, theme shift, pulse storm, sky vortex, time freeze, star rain, colour palette),
each ramping smoothly and safely retriggerable. The step-4 horizon collapse and the phase-2 failure
should drive that pattern rather than a second system.

**Plots regenerate per run.** Nothing authored here survives between playthroughs, so all three
sites and the castle must be generated from the seeded `RandomSource`
(`plotIndex * 341873128712L + floor * 132897987541L`).

**Visual contract.** Per `SKILL_CREATION_NOTES.md`: no vanilla projectiles or recoloured explosions
as an identity. Custom renderers, quads, rings, beams, and shaders. Cold palette — Void `0xA57DFF`,
Spatial `0x88DFFF`, Arcane `0x72E4FF`. No warm colours on this floor until the phase-2 sky tears.

**Readability rule**, inherited from `Supreme_Deity_Bossfight_Plan.md`: visual spectacle must never
hide an attack telegraph. Every Groundskeeper attack has a ground-level tell that reads before the
blow lands.

---

## Open questions

1. **How is the loop implemented?** Teleport-on-threshold is simplest and least convincing; a
   genuinely wrapped coordinate space is better and much harder. Worth prototyping in phase 3.
2. **Does the Presence persist across the whole floor, or only between keys?** Constant presence is
   more oppressive; intermittent is kinder and easier to tune.
3. **Castle scale.** "Gigantic" against a flat plain reads at a distance, but every block is wipe
   cost. Scale should be set after the tracked-placement fix lands, with real numbers in hand.
4. **Does this floor need combat before the boss at all?** The current design says no. If it feels
   too quiet in playtest, the Presence becoming briefly attackable is the cheapest place to add
   tension without new entities.
