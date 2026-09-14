# The blood kit, rebuilt on the voxel system

**Date:** 2026-09-14
**Status:** approved 2026-09-14. The user asked me to decide the open questions myself and liked the plan; the recommended option was taken on each (six actives, Rite and Spear pools as batteries, a bleeding enemy as the first Vein Walk destination, class access unchanged).

## The ask

Scrap and redo every blood ability except Blood Manipulation; make the school revolve around the
blood voxel system; keep the teleport-across-blood idea (Vein Walk) if it can be reworked to fit;
make the 3-11 point allocation work with the new set.

## What blood is now

Blood is the forbidden layer -1 school. Today it has seven actives and three passives, one
resource (the Crimson Vessel, 0..100, drawn first, then health at 8 blood per health point), one
price rule (`BloodService`: pay, pay in health only, potency up to 2.2x near death) and, in the
work in progress from the other session, one world object: the **pool** (`BloodHarvestEntity`),
which a kill spills at the corpse and which lifts and streams into the mage's chest when they come
near. Two voxel motions exist, both pure functions of a float age and pinned by tests:

- the **field** (`BloodVoxels` + `VoxelMotion`): a synced polyline (`BloodFieldData`) that forms out
  of the caster as cubes, ranked from the middle out, and erodes from the tips - Blood Manipulation;
- the **flow** (`BloodHarvestMotion`): a disc of cubes on the ground that wells up, lifts, flies a
  bezier into a chest and shrinks in as it lands - the harvest.

The kit below is those two motions, plus their reverse, used for everything.

## The material model

Blood lives in three places and every ability moves it between them:

| Where | What it is | How it is made | How it is spent |
|---|---|---|---|
| **the Vessel** | the reserve you carry, 0..100 | pools landing in you; Blood Rite | every cast, first |
| **pools** | cubes on the ground, owned by you, one entity each | kills, Open Vein, Crimson Spear impacts, Blood Rite, the trace Vein Walk leaves | lift into the Vessel; Coagulate and Spear drink them; Vein Walk lands on them |
| **your body** | health, at 8 blood per health point | - | the shortfall of any cost; Blood Rite on purpose |

Enemies are the fourth place: Open Vein takes blood out of them and Crimson Spear leaves it in
them. The loop is: make blood (Open Vein, kills, Rite), place it (Spear, Rite, the trail a
bleeding enemy leaves), move along it (Vein Walk), wear it (Coagulate), shape it (Manipulation).

Three kinds of pool, one entity with a flag:
- **harvest** pools (kills, Open Vein): lift into the Vessel on their own when you are in pull range;
- **battery** pools (Blood Rite, Spear impacts): never lift on their own - you spend them with
  Coagulate, Spear or by Vein-Walking onto them (which drinks the pool into the Vessel on landing);
- **trace** pools (Vein Walk's origin): yield nothing, exist only as a destination, for Return ticks.

## The six actives

Six, not seven: the school's own comment says "six actives and three passives", and six is what
the material model needs. Each is one voxel motion.

### 1. Blood Manipulation - kept

Unchanged in play: draw a shape, it forms out of you and sweeps. It only picks up the shared
tuning fixes below (Thrift does something, no hidden 4 mana).

### 2. Vein Walk - reworked: run down the vein

*Your body comes apart into blood and runs along the vein to wherever your blood already is.*

- **Destinations**, in priority: (a) a bleeding enemy you are looking at within Reach (from Open
  Vein), (b) the far end of your live Blood Manipulation field, (c) the furthest pool of yours
  within Reach - harvest, battery or trace. Nothing in reach: refused outright, no cooldown, as now.
- **Landing** on a battery pool drinks it into the Vessel; on a harvest pool spends it, as now.
- **The trace**: the cast leaves a zero-yield pool at the origin for Return ticks, so a walk can be
  walked back. Yield zero on purpose - a refundable pool would make the walk free.
- **Voxels**: a stream of cubes from the origin chest to the destination chest over ~8 ticks
  (`BloodHarvestMotion.fly` with the two chests as its endpoints; a short-lived stream entity
  carries the endpoints). The teleport itself stays instant on the server.
- **Cost**: 12 blood, scaled by points (see Thrift). Cooldown 160.

### 3. Open Vein - new: make blood out of them

*You open a vein in something else. It bleeds cubes that pool at its feet - and, if it runs, along
the way.*

- BURST, aimed at a living target within Reach (20). For Linger ticks (140) the target takes Bite
  damage every 10 ticks (6 base, x potency) and sheds 4 blood per tick into a harvest pool at its
  feet. A target that moves more than 2 blocks from the last pool starts a new one, so a fleeing
  enemy leaves a **trail** of pools - Bloodscent's name made literal, and a Vein Walk ladder.
- The bleeding target is `REVEALED` for the duration, and is a Vein Walk destination.
- **Voxels**: the reverse of the harvest lift - cubes born inside the target's box, falling in a
  short arc to the disc at its feet, which grows as they land (`BloodBleedMotion`, new, pure).
- **Cost**: 16 blood; the bleed returns up to 56 over its life if you collect it. That is the
  harvest engine; Crimson Tithe, Hemorrhage and Exsanguinate all go into this one verb.

### 4. Crimson Spear - new: put blood over there

*Your blood gathers into a spear in your hand and you throw it. Where it lands, it pools.*

- PROJECTILE. A short straight field (2.5 blocks, formed in 6 ticks at the hand) flies at Flow
  speed; a body hit takes Bite damage (15 base, x potency) and the spear shatters into a battery
  pool at the impact (12 blood, scaled by Mass); a ground hit pools the same. Standing within 2
  blocks of one of your pools when you cast drinks that pool for the cost instead of the Vessel.
- **Voxels**: the existing field, anchored to the spear entity instead of the owner (one new anchor
  mode in `BloodVoxels`); the shatter is the field's own dissolve.
- **Cost**: 24 blood. Cooldown 140. Replaces Scarlet Lance.

### 5. Coagulate - new: wear it

*Every drop of yours within reach flies to you and sets into a shell.*

- BARRIER. Every pool of yours within Draw (8) lifts and flies into you, plus up to 40 from the
  Vessel; the blood sets into a ring of cubes around your body and grants barrier at the Shell
  rate (60 per 100 blood, base). Damage erodes the shell from the top rank; after Set ticks (400)
  or at zero it dissolves, and half of what was left drains back into the Vessel. No blood
  anywhere: refused.
- **Voxels**: the field again, with a generated ring spine centred on the caster (the painter
  already re-anchors on the owner every frame) and one synced `integrity` that drives the erosion
  instead of age; the draw-in and drain-back are the flow motion.
- **Cost**: the blood it gathers. Cooldown 700. Replaces Second Heart.

### 6. Blood Rite - new: bleed on purpose

*You kneel and open your own vein. Your blood pours out in front of you and waits.*

- Hold BURST (the shared hold path, as Exsanguinate used). Every 20 ticks held: 1 health leaves
  you (an open wound, as any health payment) and 8 blood x Thrift pours into a **battery** pool
  Reach blocks (4) ahead, for Linger ticks (300). It refuses below the survival floor. It is the
  one way to make blood where there is none: the setup tool for Spear, Coagulate and Vein Walk,
  and a deliberate health-to-Vessel conversion at the same rate the school charges.
- **Voxels**: `BloodBleedMotion` from your own chest to the disc ahead.
- **Cost**: your health, nothing else. Replaces Crimson Tithe's pay-to-gain role.

### The three passives, kept and retuned

- **Bloodscent**: reveals enemies under 40% within 20 and pulls pools from 20 instead of 12, as
  now; an Open Vein on a revealed target sheds 50% more.
- **Clotting**: heals at half rate through an open wound and trickles 1 Vessel, as now; your pools
  dry half as fast.
- **The Vessel Overflows**: surplus above 80 becomes barrier, as now.

## The 3-11 point allocation

How it works today: `MagicSkillTuning` holds five signed stats per skill id (DAMAGE, SPEED, SIZE,
DURATION, EFFICIENCY, each -11..11); the budget is the sum of the positive ones, 3 at proficiency
0 rising by 2 per level to 11 (4 to 11 with the Human Adaptable passive); negatives are free.
`MagicSkillDefinition.resolve` turns points into scales, and the mana cost and cooldown climb with
points spent on the other stats, so a strong skill is a dear one. Each skill declares which stats
it exposes and how they are labelled (`TuningView`).

Three things stop it working for blood, and the plan fixes all three before any new skill:

1. **A hidden 4 mana.** Blood skills register with 0 mana, but `resolve` floors mana at 4 and the
   cast path spends `stats.manaCost()`, so every blood cast also costs 4 mana. Fix: a base of 0
   resolves to 0. Pinned by a test.
2. **Thrift does nothing.** EFFICIENCY only divides the mana cost, which is 0. Fix: the resolved
   stats expose `costScale()` - the same factor mana uses (points spent raise it, Thrift lowers it,
   floor 0.25) - and every blood cost goes through `BloodService.cost(id, stats)`, which reads a
   per-skill base price table and multiplies. Blood then has the same tension as mana: Bite and
   Reach make a cast dearer, Thrift makes it cheaper, and the number is visible.
3. **The label.** `TuningView` carries labels for four stats and none for EFFICIENCY; it gains
   the fifth, and blood relabels it "Thrift". The codex stat line says "Blood N" for the school
   instead of "Mana 4" (Manipulation: "Blood 12 + 4 per block").

Every blood skill exposes exactly four of the five stats, so eleven points buy about two maxed
stats or four moderate ones - the same shape as the mana schools.

| Skill | DAMAGE | SPEED | SIZE | DURATION | EFFICIENCY |
|---|---|---|---|---|---|
| Blood Manipulation | Bite - sweep damage | - | Blood Reach - clip radius | Linger - how long the field stands | Thrift |
| Vein Walk | - | Haste - cooldown | Reach - 24 base | Return - trace life, 100 base | Thrift |
| Open Vein | Bite - damage and blood per tick | - | Reach - 20 base | Linger - 140 base | Thrift |
| Crimson Spear | Bite | Flow - flight speed | Mass - length, hit radius, pool size | - | Thrift |
| Coagulate | - | Draw - pull range, 8 base | Shell - barrier per blood | Set - hold time, 400 base | Thrift |
| Blood Rite | - | Flow - drain rate | Reach - pool distance, 4 base | Linger - pool life, 300 base | Thrift - blood per heart |

Potency (missing health up to 2.2x) keeps applying to Bite on top of the points, as it does now.

## Client work

- `BloodBleedMotion` (new, pure, tested like the two existing motions): cubes born inside a body,
  arcing to a disc at its feet; every cube lands before the pool's yield ticks up, no seam at
  birth, an even disc.
- A stream between two chests for Vein Walk, Coagulate's draw-in and drain-back: the flow motion
  with arbitrary endpoints (`BloodHarvestMotion.fly` already takes both), fed by a small synced
  `BloodStreamData` on a short-lived effect entity.
- `BloodVoxels`: an anchor mode (owner, as now, or the entity itself, for the spear) and an
  `integrity` field in `BloodFieldData` that drives erosion instead of age (Coagulate). Both are
  additive with defaults, so Manipulation's tag is unchanged.
- `VoxelStyle`: SPEAR (cap ~220, fast launch), SHELL (cap ~700, slow set), BLEED (cap 96); streams
  reuse HARVEST. One painter id per skill in `BloodPainters` (`crimson_spear`, `coagulate`,
  `open_vein`, `blood_rite`, `vein_walk`) - ids are unchecked, a shared one fails silently.
- `FxBudget.VOXEL_FRAME_CAP` (2400) is shared by all voxel drawing; STRIKE alone claims 1800. The
  painters thin rather than truncate on loss, so this is a look check, not a crash - the capture
  step decides whether the cap rises (the 1 MiB body buffer has room).

## Server work

- `BloodHarvestEntity`: a pool kind (harvest / battery / trace), `addYield`, a per-pool lifetime,
  `drink` (discard and return the yield), and `poolsWithin`. Battery pools skip the lift.
- `BloodService`: `cost(id, stats)` over a `BloodPrices` table; `payFromPool` for the Spear.
- Five new skill modules and the reworked Vein Walk, registered from `MagicCastContentBlood`.
- `BloodPassives`: the Crimson Tithe multipliers, its counter decay, Second Heart's cheat-death and
  reserve go; the Open Vein bonus and the Clotting drying rule come in.
- The pool entity is the foundation of all of this and it is uncommitted work from the other
  session; it lands first, on its own.

## Removed

`CrimsonTitheSkill`, `HemorrhageSkill`, `ScarletLanceSkill`, `SecondHeartSkill`,
`ExsanguinateSkill`, their five `register` lines in `MagicContent`, their lang pairs, and
`message.magical.second_heart_already`. `ForgeArt.CRIMSON_TITHE` is an unrelated weapon art and
stays.

## Tests, in the order they are written

1. `MagicSkillTuningTest` (the gap the survey found): budget ladder 3/5/7/9/11/11, positives-only
   spend, refusal over budget, the -11..11 clamp.
2. `resolve` with a zero mana base yields zero; `costScale()` matches the mana factor.
3. `BloodPricesTest`: every blood skill has a price; Thrift lowers it; Bite raises it; the floor.
4. `TuningView` five labels; `BloodSchoolTest` roster of six.
5. `BloodBleedMotionTest`, the stream test, `BloodFieldData` round trip with anchor and integrity.
6. `BloodHarvestRulesTest` additions: a battery never lifts, a trace yields nothing, lifetimes.
7. Game tests: Open Vein bleeds into a pool and trails when the target moves; Coagulate drinks
   pools and grants barrier; a Rite pool waits; Vein Walk lands on a battery and drinks it.

## Captures

`/magical blood harvest` already spills a pool; captures per skill use `-PautoCommands` with
`magical hud vessel`, a spill, then the cast on a hold key where needed, at `-PwindowSize=1280x720`.

## Decisions for the user

1. **Six actives** (recommended) or keep seven with one more.
2. **Rite and Spear pools as batteries** that never lift on their own (recommended), or plain pools.
3. **Vein Walk onto a bleeding enemy** as the top-priority destination (recommended), or pools and
   fields only.
4. **Class access** stays as it is (command-only, no class grants blood); a Blood path is a
   separate piece of work.
