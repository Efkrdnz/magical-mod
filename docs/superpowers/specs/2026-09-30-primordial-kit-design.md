# The Primordial kit: catastrophes in miniature

**Date:** 2026-09-30
**Status:** approved by delegation. The user set the goal ("plan and make the most unique set of
Primordial skills for the negative tier ... go as creative and menacing as this is a very rare set
... gigantic effects on earth like big catastrophic events on smaller scale", with the tornado that
takes an element from its caster's own spells and dispels everyone else's) and asked for the
decisions to be made without them. Every open question below took the recommended option.

## What Primordial is

The forbidden schools sit one per negative layer, each defined by what it costs: blood costs the
body, dark a debt, eldritch being noticed. **Primordial costs the world.** A Primordial mage does
not make a disaster; they wake one the ground was already capable of, and **the land where it
breaks decides how bad it gets**. That is the school's shape, and it is the one thing none of the
other layers has: power is a reading of the terrain, so where you stand is the build.

Every catastrophe draws on one feature of the land - its **wellspring** - sampled where it breaks:

| Catastrophe | Wellspring | Sampled | Strong where |
|---|---|---|---|
| Cyclone | **sky** | columns round the eye that see the sky | open plains, peaks |
| Fault Line | **stone** | stone-like blocks under the path | mountains, caves |
| Skyfall | **sky** | the target must see the sky, or the cast refuses | open sky; night |
| Caldera | **heat** | lava, magma, fire, basalt, netherrack round it; the Nether; depth | the Nether, deep caves, beside lava |
| Tsunami | **water** | water blocks round the caster | the coast, rivers, the sea |
| Upheaval | **mass** | solid breakable blocks in the plate torn up | solid ground; nothing over air |

The reading is a factor between 0.5 and 1.5 (`magic/primordial/Wellspring`, pure, pinned by
`WellspringTest`) and is said once per cast on the action bar as a word - *faint, steady, strong,
overwhelming* - so the player learns the land by listening to it. Every terrain change goes through
`ConjuredTerrainService` and comes back: the world is borrowed, never spent for good.

## The six

### 1. Cyclone (`cyclone`) - the storm that eats spells

A funnel touches down where you aim (24 blocks) and lives thirteen seconds. It **drifts toward
whatever ground you are looking at**, so it is steered with the eyes. Hostiles within 1.6 radii
are drawn in, spun round the axis and lifted; one carried to the top is **flung out**. A pulse of
damage lands every 10 ticks on everything inside.

**The eye is a gate for magic** (`CycloneFeed`, pure):
- A spell **in flight** - it moved since last tick and is not riding a body (more than 2 blocks from
  its owner) - that enters the funnel **and was cast by the cyclone's owner** is swallowed, and the
  storm takes its **element**. The same element again deepens it (up to three stacks: 1x, 1.5x, 2x
  potency, 10% wider per stack); a different element replaces it at one stack.
- One cast by **anybody else** is dispelled with a clash: the storm is a wall against magic. A
  stranger's real arrow or trident is somebody's item, so it is not destroyed: the storm turns it
  back once and it is the owner's after.
- The owner's own catastrophes are **met, not eaten**: a lava bomb, a wave or a falling star of
  theirs that crosses the storm colours it once and keeps going (each is remembered by id, the last
  sixteen). Anybody else's Primordial effect is dispelled by being *finished*, never discarded, so
  the ground it holds is given back rather than left open.
- The land imbues it too, but only a calm one: an eye that drifts over lava becomes **Ember**, over
  water **Tide**. A cyclone steered across your own Caldera becomes a firestorm, one that meets your
  Tsunami a waterspout: the kit feeds itself.
- The eye takes a spell anywhere inside the funnel's wall, and never narrower than seven tenths of
  the crown (`CycloneSkill.gateAt`): the foot is exactly where a rolling or low spell travels, and a
  body being dragged in stands in front of it there.

Twelve elements (`StormElement`), each with its colour, its matter and its effect on every pulse:

| Element | Fed by | On every pulse |
|---|---|---|
| Dust | (unfed) | the base hit |
| Ember | Fire, Caldera, lava | sets alight; +25% damage |
| Tide | Water, Tsunami, water | drowns (air), puts out fire, slows; pulls 30% harder |
| Frost | skills with the frost accent | freezes, slows |
| Radiant | Light, Soul | double damage to the undead; glowing |
| Maelstrom | Void, Spatial | pulls 80% harder, reaches 30% further |
| Blight | Dark | withers |
| Crimson | Blood | heals the owner by a fifth of what it dealt |
| Arcane | Arcane | strips every beneficial effect |
| Steel | Sword | +60% damage |
| Stone | Fault Line, Upheaval, Skyfall | +25% damage, flings harder |
| Deep | Eldritch | roots one victim a pulse inside the funnel |

Chaos feeds nothing: a chaotic spell **cleanses** the storm back to Dust, even from its owner - the
one way to undo an element short of letting it die.

Wellspring: **sky**. Underground it is a dust devil half the size.

### 2. Fault Line (`fault_line`) - the earth opens and bites shut

A crack races from your feet along your gaze at 1.5 blocks a tick for up to 20 blocks, and the
ground along it **falls away**: a chasm two wide and three to six deep (stone makes it deeper). What
stood on it drops in. For three seconds it gapes; then it **slams shut**. Everything still inside -
any hostile overlapping a cell the earth gives back - is **crushed** for triple damage, and every
body is lifted onto the highest column it overlaps, never left in a wall. Only hostiles are yanked
down as the ground falls away; the owner, their allies and passive creatures float down on Slow
Falling and are lifted out unhurt. Climbing out
in time is the counterplay.

Unbreakable blocks, block entities and fluids are never taken, nor anything the give-back could not
return (see *Borrowed ground*). The effect itself stays where the
crack began, beside its caster: walked out to the tip it could stand in a chunk that no longer ticks
entities and never shut the chasm it opened (the game test found exactly that).

### 3. Skyfall (`skyfall`) - call down a star

Aim up to 48 blocks. If the target cannot see the sky the cast refuses and costs nothing. Otherwise
a ring of falling ash and embers **closes on the target** for two and a half seconds, visible to
everyone, while a burning rock (magma at the heart, blackstone and basalt about it, drawn as real
blocks by the `primordial_mass` painter) comes down a slant out of the sky behind the caster. It lands
exactly as the ring closes: a blast (full damage within 2 blocks, falling off to the rim), fire, a
shove out and up, ejecta of the blocks it hit, and a **crater** - a bowl carved into the ground,
lined with blackstone and basalt (never magma: the caster fights in it too), that fills itself
back thirty seconds later.

A star at night is a quarter larger.

### 4. Caldera (`caldera`) - raise a volcano

The ground where you aim **heaves up into a cone** (basalt and blackstone, a magma vent on top),
shoving off whatever stood there. It rumbles for a second, then **erupts** for fifteen seconds:
every so often it lobs a **lava bomb** in a high arc at a hostile within 16 blocks (straight up when
there is none), which bursts where it lands in fire and a small blast. Anything touching the cone's
flanks burns. When it ends the cone sinks back into the ground it came out of.

Bombs are the owner's spells in flight, so a Cyclone that meets them becomes Ember. Heat sets how
often it fires: every 28 ticks faint, every 12 overwhelming.

### 5. Tsunami (`tsunami`) - a wall of the sea

A wave stands up two blocks in front of you and rolls along your gaze for 22 blocks, riding the
terrain. Its width and height are the water around you: a trickle in a desert, a wall on the coast.
Everything hostile in its face is **swept along with it** - carried at the wave's speed, out of
breath, fire put out - and where the wave breaks it **slams them down** for the full damage and
leaves them slowed. Fire it passes over is put out.

### 6. Upheaval (`upheaval`) - tear up the ground and throw it

The ground where you aim - a disc of radius two, two deep - is **torn loose** and rises as one slab
of the real blocks it was, flinging whatever stood on it into the air. It hangs for a heartbeat,
then is **hurled** at wherever you are looking by then (30 blocks) in an arc and bursts on landing
or on the first body it meets: damage and knockback scaled by how much ground it carried, and its
own blocks breaking into rubble. The hole fills back in thirty seconds later. A plate with nothing
solid in it is refused.

## Shapes in code

Pure, no Minecraft, each pinned by its own test:

- `magic/primordial/Wellspring` - the readings and the word for each.
- `magic/primordial/StormElement` - the elements, their colours and multipliers, and which element
  a swallowed spell feeds.
- `magic/primordial/CycloneFeed` - `(element, stacks) + swallowed -> (element, stacks)`.
- `magic/primordial/Funnel` - the cyclone's geometry: radius at a height, inside, the pull on a body
  at an offset from the eye (in, round, up) and the fling at the top.
- `magic/primordial/FaultPath` - the columns a crack covers, in the order it reaches them.
- `magic/primordial/Shapes` - the crater bowl, the caldera cone and the plate, as cells.
- `magic/primordial/Ballistics` - the launch velocity that lands a lob on a point under a gravity.

In the world: `PrimordialService` (sampling the wellsprings, the reading line, the ground at a
point, what a spell caught in a storm is), `PrimordialScars` (craters and holes given back later;
the edits live in the `ConjuredTerrainService` ledger, so a restart gives them back at load) and one
`SkillModule` per catastrophe in `magic/skill/primordial/`. There is no new entity type: a meteor or
a slab is a plain `SpellEffectEntity` carrying up to 48 block states at packed offsets in its synced
data (`MassCodec`), drawn through the block renderer by the one custom painter the school registers,
`primordial_mass` (`PrimordialPainters`). A lava bomb is a `ThrownSpellEntity` on draw mode 1.

The ground is **walked from the aimed block, never scanned down from above it**
(`PrimordialService.surface`/`floor`: climb while buried, else follow down): scanned from above, a
roof, a canopy or the barrier ceiling of a game test template was taken for the ground, which is how
the first game test run found Skyfall falling on a roof and Upheaval tearing up nothing. Whether a
point sees the sky is read off the heightmap (`opensToSky`), which moves the moment a roof is
placed, where sky light waits for a lighting pass.

The matter is particles thrown on the client from the entities' synced state by
`client/fx/PrimordialFx` (its own client-tick subscriber, honouring the particle setting and
keeping off a first-person lens the way `SpellAccents` does): the funnel as a spiral of tinted
wisps in the element's colour with the ground's own dust at the foot, the meteor's fire trail and
the closing ring, the caldera's smoke, the wave's foam sheet riding the wave's velocity.

## Borrowed ground

The world is borrowed, and three rules keep it from being spent:

- **A cut takes what hangs on it.** `PrimordialService.carve` records a torch, a rail, a flower or a
  carpet on the block into the same edit before cutting, so nothing pops off as a drop the give-back
  could never return. It refuses, leaving the world untouched, wherever the ledger cannot undo the
  cut: a block entity, a fluid or a portal beside it, sand or gravel over it, a door or bed half, a
  pocket room's shell, a dreamscape. Upheaval cuts its plate from the top down.
- **Matter is worthless.** A block a catastrophe put down (a crater lining, a cone) is conjured
  matter until it is given back, and broken before then it drops nothing (`BlockDropsEvent`
  cancelled), or mining a cone is free basalt on top of the ground that comes back.
- **Nobody is buried.** `PrimordialService.giveBack` gathers every body standing in a cell before it
  restores the edit and stands each one on the first height it fits at. A block a player put into a
  hole since is theirs and stays.

## Vault of Avarice leaves the Primordial row

It sat alone on layer -4 as a Void skill, so the codex showed Greed's vault under "Primordial". It
is a sin's gift, not a school's, and its attribute is Dark: it moves to layer -2 and rides the Dark
row. `PyramidLayersTest` names sin gifts (`MagicContent.isSinGift`) and holds them to the layer of
their attribute instead of counting them as a second school.

## Numbers

| Skill | Damage | Mana | Cooldown | Duration | Knockback |
|---|---|---|---|---|---|
| Cyclone | 5 a pulse | 45 | 900 | 260 | 0.8 |
| Fault Line | 6 falling in, x3 crushed | 40 | 700 | 60 open | 0.4 |
| Skyfall | 28 | 60 | 1200 | 50 warning | 1.6 |
| Caldera | 7 a bomb | 55 | 1100 | 300 | 0.5 |
| Tsunami | 10 at the break | 50 | 900 | 50 | 1.2 |
| Upheaval | 16 at 20 blocks carried | 45 | 800 | 35 rise and hang | 1.4 |

## Testing

Unit: every pure class above, and `MassCodecTest`. Game tests (`PrimordialGameTests`, each laying
its own stone floor in the template): a cyclone meets its owner's lava bomb and turns Ember without
eating it, then turns back an arrow nobody it knows loosed and makes it the owner's; borrowed ground
refuses a cut under sand, carries a torch with the stone it stood on, drops nothing when its matter
is broken and lifts a husk out of the hole as it closes; a Fault swallows a husk, crushes it as it
shuts and gives back every block; Skyfall refuses a roofed target and bills nothing; an Upheaval
carries the ground it tore up and the hole comes back. `PyramidLayersTest` gains the Primordial six
and holds a sin gift to the layer of its attribute; `ForbiddenMagicTest` pins the vault at -2.
