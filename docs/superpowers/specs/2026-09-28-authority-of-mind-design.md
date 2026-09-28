# The Authority of Mind

*2026-09-28*

## What it is

**Reality is whatever enough minds agree on.** The wielder authors lies - walls, floors, creatures,
fire - and shows them to an audience. Every mind that sees a lie decides, second by second, how much
it believes it. A lie that is believed changes how that mind behaves; a lie that is believed hard
enough, by enough minds at once, stops being a lie and becomes real.

The power is the author's imagination, and the mod measures it. A careless lie - a diamond wall
floating in a meadow - is doubted by everything that sees it. A careful one - mossy cobblestone in a
cave, with dripping water and a figment villager wandering behind it - is believed in seconds. The
Playbill says exactly why, line by line, so the wielder learns to lie better.

Three rungs, one rule:

1. **Illusion** - a lie shown to an audience. Believers act on it: mobs path around a wall that is
   not there and chase a villager who is not there; players see it on their own screen only.
2. **Manifestation** - when the believers of one piece of a lie outweigh it, that piece becomes real
   for as long as the belief holds: real blocks, a real creature with real teeth.
3. **The Dream** - a mind that believes you completely can be put to sleep and pulled into a world
   you built in advance, where everything is real and the only way out is to find the one thing you
   got wrong on purpose.

## The shape, and why it is a belief matrix

| Authority | The thing it owns |
|---|---|
| Space | a table of twelve laws over a region |
| Mana | a grimoire: a sequence of verse cards, read *n* at a time |
| Causality | a directed graph, evaluated on world events |
| **Mind** | **a scene, and a matrix of how much every viewer believes each piece of it** |

The wielder authors the scene; the world authors the matrix. That split is the whole design: the
other three Authorities are strong in proportion to what the wielder wrote, and Mind is strong in
proportion to how the world *reacts* to what the wielder wrote. The same scene is a masterpiece in a
cave and a joke in a desert.

## Impressions: you can only imagine what you have seen

The palette is the **Lexicon**, a set of **Impressions** saved on `PlayerMagicState`. The passive
**Gaze** learns one: look at a block for 40 ticks, or a creature for 60, and a small eye beside the
crosshair fills and closes. Gazing again at the same kind deepens it: **fidelity** 1 at the first
gaze, 2 at five, 3 at twenty. Fidelity is worth plausibility (below), so a wielder who has studied
zombies lies about zombies better.

Rare impressions are the prize: a Warden, an End Crystal, a Wither. Nobody gazes at a Warden for
three seconds by accident.

The size of a lie is bounded by the Lexicon: a reverie holds `16 + 2 * impressions` elements, capped
at 128. Imagination grows with experience.

## The Reverie: what the wielder writes

A **Reverie** is a saved scene, relative to an anchor and a facing. There are three reverie slots and
one Dreamscape (the Dream's own, below). Its elements:

- **Imagined blocks** - any block impression, at a position. Adjacent imagined blocks form a
  **cluster**, and a cluster is the unit that is believed, doubted and manifested.
- **Figments** - any creature impression, with a **script**: a *stance* (Idle, Wander, Patrol A->B,
  Guard, Follow me, Mimic me), a *reaction* to viewers (Ignore, Approach, Chase, Flee, Stare) and a
  *voice* (silent, its kind's ambient, a chosen sound).
- **Senses** - per element, layers beyond sight: **Sound** (ambient of the material or voice of the
  figment), **Shadow** (footstep and ground-contact particles, a dark disc), **Scent** (smoke over fire
  and lava, dust over gravel, the drip under water). Each layer multiplies the rate at which the
  element is believed and costs mana.
- **The Flaw** - Dreamscape only: exactly one element marked as the thing that is wrong.

## Authoring: Daydream and the Playbill

**Daydream** (a toggle skill) steps the wielder half out of the world. For them alone the world
desaturates, the hotbar is replaced by the **Impression Reel**, and the active reverie is drawn in
place as lilac-rimmed ghosts. Drafting costs nothing - the lie is free to imagine and only costs when
told.

| Input | In Daydream |
|---|---|
| scroll | choose an impression on the Reel |
| left click | paint with the brush (up to 24 blocks away) |
| right click | erase the element under the crosshair |
| middle click | pick the impression of what you are looking at, if known |
| sneak + scroll | cycle the brush: Point, Line, Wall, Box, Figment |
| inventory key | open the Playbill |
| the Daydream key | leave Daydream, the draft saved |

The first element placed is the anchor, and **Unveil** puts the anchor on the aimed block turned to
the wielder's facing, so a reverie is a reusable blueprint. It is also a trap for the lazy: a scene
drafted in a forest and unveiled in a desert brings the forest's materials with it, and the desert
doubts them.

**The Playbill** is the reverie editor, frameless over a dimmed world like the Grimoire: tabs for the
three reveries and the Dreamscape; the elements down the left (clusters by material, figments by
glyph); the selected element's script and senses in the middle as cards; and down the right the
**Forecast**, which is the teacher. It runs the pure `Plausibility` on the draft *as it would land
where the wielder is looking now*, and prints every term with its sign:

```
Plausibility                 0.82
  on the ground              +0.00
  matches the stone nearby   +0.27
  zombie, well studied       +0.10
  villager in a cave         -0.05
Senses       sound, scent     x1.38
Convinced in   zombie 2.7s   skeleton 3.5s   player 3.5s   enderman 8.9s
Becomes real   wall: 6 zombies or 2 players   villager: 6 zombies
Cost           46 mana
```

## Belief

Everything in this section is the pure core (`magic/mind/`), pinned on exact values.

**Plausibility** `p` of an element where it stands, from 0.5, clamped to 0.05..1:

| Term | Value |
|---|---|
| unsupported (no solid below, not attached to a supported block) | -0.40 x the unsupported fraction |
| context: its material occurs in the real blocks within 8 | +0.30 x the matching fraction |
| alien: its material occurs nowhere within 8 | -0.20 x the alien fraction |
| fidelity | +0.10 x (mean fidelity - 1) |
| figment habitat (water kind on land, undead in sunlight not burning) | -0.30 |
| figment script unlike its kind (a cow that chases) | -0.20 |
| figment script like its kind (a zombie that chases) | +0.10 |
| size | -0.10 per doubling of the scene above 32 elements |

**Senses** multiply the gain rate: sound x1.25, shadow x1.15, scent x1.10. The **Warden** is blind:
it believes sound and nothing else.

**Susceptibility** per viewer: zombie, husk, drowned 1.3; villager 1.2; creeper 1.1; skeleton,
player 1.0; spider 0.9; witch 0.5; enderman 0.4; bosses 0.2; the wielder 0 - you cannot believe your
own lie.

**Gain.** Each tick a viewer perceives an element (line of sight, within 32 blocks),
`b += 0.02 * p * senses * susceptibility * novelty * (1 - b)`. At `p = 0.8` with sound and scent a
zombie crosses 0.8 in about three seconds. **Decay** when unseen: `b -= 0.002` a tick.

**Contradiction** is the only fast way down:

| Event | Belief |
|---|---|
| walks into or through it, strikes it, breaks it | -0.60 |
| a projectile passes through it | -0.35 |
| watches another viewer pass through it | -0.20 (doubt is contagious) |
| a figment strikes a viewer below 0.5 and nothing happens | -0.25 |

**A lie that hurts is believed more.** Imagined harm - lava, fire, magma, cactus, berry bush - and
figment attacks land as *phantom harm* on a viewer at or above 0.5: magic damage times belief, and
the touch *raises* belief by 0.10 instead of breaking it. Below 0.5 the same touch is a contradiction.
So the first moments decide everything: a lie that is doubted early is tested and falls; a lie that is
believed early confirms itself.

**Shatter and scepticism.** A viewer whose belief falls below 0.1 stops seeing the element, and
remembers the trick: every impression in it gets `novelty = 0.5^n` for that viewer for 6000 ticks,
where `n` is how many times they have seen through it. The same wall twice is half a wall. The
wielder is paid for inventing, not for repeating.

## How a lie reaches each kind of mind

**Players.** A viewer's client is sent the scene (`IllusionScenePayload`) and draws imagined blocks
itself, render-only, through the block renderer on `RenderLevelStageEvent`, and figments as
`FigmentEntity`s the server tracks only to believers (`Entity.broadcastToPlayer`). Imagined blocks
**do not collide**, so the server sees everything: a player whose box meets an imagined solid has
walked through it. A believer who stops at a wall they could have walked through has been fooled by
their own decision, which is the purest form of the power. A floor over a pit drops them.

**Mobs.** Belief is held on the server. Hostile believers target a figment of anything they would
target (a player, a villager, a golem) and flee one they would flee (a creeper from a cat, a skeleton
from a wolf) through goals added on `EntityJoinLevelEvent`. Walls and floors need the pathfinder:
**one mixin**, the mod's first, on `WalkNodeEvaluator`, answers BLOCKED for a believed imagined solid
and WALKABLE for a believed imagined floor, so a zombie routes round a wall that is not there and
straight onto a floor that is not there.

**The wielder** sees the draft in lilac and, while a scene is live, **Belief Sight**: a small ring of
pips over every viewer's head filling with that viewer's belief, the ring turning solid when they
are convinced. Nobody else sees it.

## Manifestation

A cluster or figment has a **weight**: 0.5 a block, a figment its kind's max health / 4 (zombie 5,
iron golem 25, Warden 125). Its **consensus** is `sum(w * b)` over viewers **convinced** of it
(b >= 0.5), with `w` = 1 for a mob, 3 for a player, 5 for a boss.

When consensus reaches the weight, the element **manifests**:

- a cluster is placed as real blocks through `ConjuredTerrainService`, so it collides for everyone and
  is given back;
- a figment becomes real for everyone, with health `kind * min(1, consensus / weight)` and its kind's
  attack;
- the transition is drawn: the lilac rim hardens and cracks into the material.

It stays real while consensus holds above half the weight, and reverts to an illusion (the blocks
given back, the creature a ghost again) when it falls below. Striking a manifested thing is not a
contradiction - it is really there.

## Skills

| Skill | Kind | Cost | Clock |
|---|---|---|---|
| **Gaze** | passive | - | - |
| **Daydream** | toggle, self-managed | - | - |
| **Unveil** | press | 10 + 1 a block + 5 a figment + 10 a sense layer | 200 |
| **Insist** | hold on an element | 3 a tick | - |
| **Lull** | press on a viewer | 60 | 1200 |

At most two scenes live at once; an illusion lasts 1200 ticks unless it manifests.

**Insist** pours conviction into the element under the crosshair: every viewer perceiving it at 0.3 or
above gains 0.01 a tick, and every viewer below 0.3 *loses* 0.01 - protesting too much. It is how a
scene is pushed over the line into real, and it is useless on an audience that already doubts.

Like the other Authorities these are `selfManaged`, so each calls `MindService.payFor` after
establishing it has something to do and before doing it.

## The Dream

**Lull** targets a viewer whose belief in any live element of yours is at least 0.8.

- **A mob** falls asleep where it stands for up to 600 ticks, or until it is hurt.
- **A player** is pulled into the wielder's **Dreamscape** in the `magical:dream` dimension, one plot
  per wielder like `pocket_space`. Their body stays behind as a **Sleeper** (an entity wearing their
  skin, lying down); damage to the Sleeper wakes them.

The Dreamscape is built in advance by Lulling yourself: inside your own dream, Daydream places real
blocks and real figments, free, because belief there is total. It must contain exactly one **Flaw** -
a block or figment the wielder marks as wrong - or Lull refuses. A dreamer wakes by touching or
striking the Flaw, after 1200 ticks, or when their Sleeper is hurt. The craft is the Flaw: an
upside-down torch in a library, a cow with a villager's voice, a door that opens onto its own
hinge. It must be findable and it must not look like it.

**A dream cannot kill.** Harm inside it is real, but a dreamer brought to one heart wakes at one heart.

## Worked examples

- **The lazy lie.** A diamond wall in a plains field: alien material -0.20, `p` 0.30, silent.
  Zombies take ten seconds; a player has time to walk up and test it.
- **The good lie.** The same wall in mossy cobblestone at the mouth of a cave, a figment villager
  Wandering behind it with its voice on: `p` 0.82, zombies convinced in three seconds, and they
  route round it into the corridor you dug for them.
- **The creeper stopper.** A figment cat on Guard at the door, voice on. Creepers flee cats; nothing
  was ever there.
- **The pit.** Grass imagined over a two-deep pit with a figment villager Wandering on it. The zombies
  path onto the floor they believe in and fall into the one they did not.
- **The siege.** Three figment iron golems on Guard. Skeletons shoot them and the arrows pass through -
  unless the golems stand behind a real fence, where the arrows hit the fence and nobody learns
  anything.
- **The bridge.** A lava gap, a bridge imagined across it, and four friends looking at it from the far
  side. Four players convinced is 12 against a weight of 10: the bridge is real, and stays real as
  long as they keep believing in it while you cross.
- **The corridor.** In PvP, imagined lava across a corridor with scent and sound. An opponent who
  believes it and steps in anyway is burned by it, and believes it more.

## Code

Pure (`magic/mind/`, no Minecraft): `Impression`, `Lexicon`, `Reverie`, `SceneElement` (`ImaginedBlock`,
`Figment`, `Flaw`), `Script`, `Sense`, `Plausibility` (with its `Forecast` lines), `Susceptibility`,
`Belief` (the per-viewer ledger, gain, decay, contradiction, novelty), `Consensus`, `MindWorld`
(the interface the core asks about blocks, light and neighbours).

Runtime: `MindService` (live scenes per wielder, the tick, `payFor`), `LevelMindWorld`, `LiveScene`,
`entity/mind/FigmentEntity`, `entity/mind/SleeperEntity`, `DreamService`, `mixin/WalkNodeEvaluatorMixin`
with `magical.mixins.json` (the `[[mixins]]` block in the mods.toml template is already there,
commented out).

Client: `client/mind/DaydreamInput`, `DraftRenderer`, `IllusionRenderer`, `BeliefSightRenderer`,
`ImpressionReelOverlay` + `ImpressionReelLayout`, `client/screen/mind/PlaybillScreen` + `PlaybillLayout`.

Network: `DaydreamTogglePayload`, `SaveReveriePayload`, `UnveilPayload`, `InsistPayload` (to server);
`IllusionScenePayload`, `IllusionEndPayload`, `BeliefSyncPayload` (to client - each viewer its own
belief, the wielder everybody's).

Commands: `/magical mind lexicon all|<id>`, `mind reverie show <slot>`, `mind unveil <slot>`,
`mind belief` (reads the matrix back), `mind dream`.

## Testing

- `PlausibilityTest` - every term on exact values, and the worked examples' numbers.
- `BeliefTest` - gain, decay, each contradiction, phantom harm raising belief, novelty halving.
- `ConsensusTest` - weights, the manifest threshold and the half-weight hysteresis.
- `ReverieTest` - the budget, anchor rotation, one Flaw only in a Dreamscape.
- `PlaybillLayoutTest`, `ImpressionReelLayoutTest` - nothing overlaps from 320x240 up.
- Game tests: a zombie targets a believed figment villager; a zombie paths round a believed wall; a
  believed floor over a pit drops a zombie and its belief breaks; a manifested cluster is real to a
  non-believer; a player walking through a wall contradicts it; the Flaw wakes a dreamer; a dream
  leaves one heart.

## Build order

Three plans, each a playable Authority on its own:

1. **Illusion** - the pure core, Gaze and the Lexicon, Daydream and the Reel, the Playbill with the
   Forecast, Unveil, players and mobs believing and doubting, figments, the mixin, Belief Sight.
2. **Manifestation** - consensus, real clusters and figments, Insist, phantom harm.
3. **The Dream** - the dimension, the Dreamscape, the Flaw, the Sleeper, Lull.

## Out of scope

Hiding real blocks (an imagined *absence*) needs chunk geometry culled per viewer and is left out.
So is the wielder entering another player's dream alongside them.
