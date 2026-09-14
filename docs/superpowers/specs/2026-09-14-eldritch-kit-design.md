# The eldritch kit: modelled constructs, coded motion

**Date:** 2026-09-14
**Status:** implemented 2026-09-15 (see the implementation notes at the end). Approved by
delegation 2026-09-14. The user asked for the plan to be made without
them ("Make a plan yourself for set of eldrich skills ... make everything else except the models
part") and for the list of assets they will model. Every open question below was decided the same
way the blood kit's were: the recommended option was taken.

## The ask

An eldritch + primordial skill set idea. Eldritch first, primordial later. The user models the
creatures themselves, in Blockbench, and wants them to look good; the code owns the motion, the
flashes, the light and the rules. Deliver the eldritch school complete except for the models, and a
precise list of what to model.

## What eldritch is

The forbidden schools sit one per negative layer of the pyramid, each defined by what it costs the
caster: blood costs the body, dark costs decay, chaos costs certainty, primordial costs the world,
and eldritch costs **being noticed**. So the school is built round one gauge, **Notice**, and one
idea: every eldritch cast borrows a piece of something enormous that lives under the world, and
every borrowing makes it more aware of you.

The pieces are three creatures, modelled once and used everywhere: a **tentacle**, an **eye** and
a **maw**. Six actives are six ways of calling one of them up. The code does everything but the
mesh: it reads the Bedrock geometry the user exports, bakes it into vanilla model parts, bends the
tentacle along a curve it computes each frame, rolls the eye toward what it watches, opens and
snaps the jaws, grows each construct out of the ground and dissolves it away, lights the glow layer,
and paints the school's ink and cell effects round it with the existing FX vocabulary.

## Notice

`PlayerMagicState.notice`, 0..100, saved and synced with the rest of the state.

- Every eldritch cast **adds** its price from `EldritchPrices`, scaled by
  `MagicSkillResolvedStats.costScale()` exactly as blood and mana are (Thrift lowers it, points
  elsewhere raise it, never below one). Mana is charged too, normally: eldritch is the only
  forbidden school that pays both.
- Notice **decays** one point per slow tick (every ten ticks) for eldritch mages, so a full gauge
  empties in fifty seconds of silence. It is a heat, not a debt: the dark school owns the debt.
- Notice **pays back**: `EldritchService.potency(state) = 1 + 0.5 * notice / 100`, applied to the
  damage and the size of every construct. The deep gives more to the ones it watches.
- Two rungs, read off `DarkService`'s ladder shape so players count them the same way:
  - **Watched** at 50: every slow tick, hostile mobs within 24 blocks turn to the caster
    (`Mob.setTarget`). Being noticed means being found.
  - **Noticed** at 100: every 200 ticks while it stays at 100, a tentacle erupts under the caster
    and grasps *them* - the Grasp construct with the caster as its target, 40 ticks of ROOTED and
    the crush damage. The deep reaches for what it has seen. Survivable, on purpose: it is a tax on
    staying loud, not a death.
  - Each crossing prints one line (`message.magical.deep_watched`, `message.magical.deep_noticed`).
- The HUD shows a `hud.magical.notice_line` caption for eldritch mages, next to where the corruption
  line goes for dark mages. The codex cost line reads "Mana N + Notice K  Cooldown M".
- `EldritchService.isEldritchMage(state)` gates the decay, the rungs and the HUD line, like
  `isBloodMage` and `isDarkMage`.

## The three creatures

One entity class for all of them: `EldritchConstructEntity extends SpellEffectEntity`, so damage,
duration, owner, target, seed, phase, direction, end point and the synced scratch tag come for
free and `SpellBehavior`s tick it. It adds three synced fields: the **model** (`tentacle`, `eye`
or `maw`), the **anchor** (`GROUND`: it stands where it was spawned; `OWNER`: it rides its owner;
`TARGET`: it rides its target) and the **scale**. Registered as `magical:eldritch_construct`,
tracked like the other spell entities.

The renderer, `EldritchConstructRenderer`, extends `ProfileRendererShell` so the profile's
silhouettes (the ink stain, the halo, the rift, the cells) are painted at the construct exactly as
they are for any effect, and then draws the model on top:

1. the vanilla entity frame: yaw, `scale(-1, -1, 1)`, no vertical offset - the geometry's ground is
   the entity's feet;
2. the pose from `EldritchPose` (below), written into the baked `ModelPart`s by bone name;
3. the body on `RenderType.entityTranslucent(texture)` with the alpha of the lifecycle: nothing
   before it forms, opaque while it stands, fading through the last quarter;
4. the glow layer, if the texture exists, on `RenderType.eyes(glow)`: full bright, blended over
   the body (the eyes render type is translucent in 1.21.4, not additive), so the file is
   transparent wherever the creature does not glow: the bioluminescence.

Materialise and dissolve are pose as well as alpha: a construct grows out of its anchor along the
chain (segments extend one after another over the form ticks) and sinks back the same way at the
end. That is what "flash effects" means here: the cast circle, the release cue, the impact mark and
the victim overlay all come from the visual profile, as for every skill.

### Models: the loader

`assets/magical/models/entity/eldritch/<name>.geo.json`, the Bedrock entity geometry Blockbench
exports (`format_version`, `minecraft:geometry[0].description.{identifier,texture_width,
texture_height}`, `bones[].{name,parent,pivot,rotation,cubes[].{origin,size,uv,inflate,mirror,
rotation,pivot}}`). Read the way Bedrock reads it: x as written, y up, z as written, the model's
**-Z face is its front** (Blockbench's north), pivots in the same space, rotations in degrees with
the same signs Blockbench shows. `GeoModelParser` (pure, unit-tested on strings) turns the JSON
into a `GeoModel` record; `GeoModelBaker` (client) turns that into a `MeshDefinition` -
`PartDefinition` per bone (`offsetAndRotation(px, -py, pz, rx, ry, rz)` in radians),
`CubeListBuilder.addBox(ox - px, -(oy + h) + py, oz - pz, w, h, d, inflate)` with Box UV
`texOffs(u, v)` and the mirror flag, a cube with its own rotation wrapped in a synthetic child part
at its pivot - baked with `LayerDefinition.create(mesh, texW, texH).bakeRoot()`, plus a name to
part map. Per-face UV is refused with one log line naming the file and the fix (export with Box
UV). `EldritchModels` is the client reload listener (`AddClientReloadListenersEvent`,
`magical:eldritch_models`) that lists the folder and rebuilds the map on every resource reload, so
the user presses F3+T and sees their new export; a construct whose model is missing draws its FX
and nothing else, and logs once per name.

Placeholders ship at the same paths, built from cubes by `scripts/eldritch-placeholders.py`
(geometry and 64x64 PNGs in the school's teal over ink, with the box layout drawn on the texture
so the layout contract is visible), and the user's exports replace them file for file.

### Motion: `EldritchPose`

Pure functions of the entity's synced state and a float age, pinned by unit tests, no Minecraft
classes:

- **chain**: `n` segments of length `L`, a root yaw and pitch, a target point in the root's space,
  and a sway amplitude and rate. Returns per-segment (pitch, yaw) such that the chain follows a
  quadratic curve from the base toward the target with a sinusoidal sway along it, and the tip
  lands on the target when it is within reach (a cyclic-coordinate-descent pass after the curve
  when a target is set). Tests: the tip reaches a reachable target within a tolerance; every joint
  angle is bounded; the pose is continuous in age; nothing moves before birth.
- **grow**: how much of the chain exists at a given age over the form ticks (segments extend from
  the base one after another; a segment not yet born has scale zero), and the reverse for the
  dissolve.
- **gaze**: the body rotation that points the -Z face at a target, with a lag so the eye rolls
  rather than snaps, and the pupil scale (contracts when it fixes on something).
- **jaws**: the opening angle from the phase (open over the windup, hang, snap shut in three ticks,
  a short chatter after).

### The three assets (the list for the user)

All three: Blockbench, **Bedrock Entity** project, exported as Bedrock geometry, **Box UV**,
texture size declared in the file (64x64 is plenty; 128x128 if the tentacle needs it), one PNG
each, optional glow PNG with the same layout where only the parts that should shine are painted
and everything else is fully transparent (an opaque black pixel there paints the body black;
`EldritchGlowTexturesTest` holds the shipped placeholders to this). Units are the usual sixteenth
of a block; the ground is y = 0; the front is the north (-Z) face in Blockbench.

| Asset | Path | Bones (parent) | Rest pose | Drawn at |
|---|---|---|---|---|
| **tentacle** | `models/entity/eldritch/tentacle.geo.json`, `textures/entity/eldritch/tentacle.png` (+ `tentacle_glow.png`) | `root` (pivot 0,0,0) > `seg0` > `seg1` > ... > `seg5`, each the child of the one below, each pivot at the joint with the one below, cubes running +Y from the pivot; suckers on the -Z face | standing straight up, about 24 units tall (six segments of four); tapering | Grasp 1.0, Lash 0.6, Skin 0.35, the Noticed rung 1.2, Call 0.8 |
| **eye** | `eye.geo.json`, `eye.png` (+ `eye_glow.png`) | `root` (0,0,0) > `body` (pivot at the centre, 0,8,0) > `pupil` (on the -Z face of the body); optional `lid_upper` and `lid_lower` under `body`, pivots at the front rim | a sphere-ish eyeball of diameter 12 centred 8 up, lids open | Unblinking Eye 1.0, Call 2.5 |
| **maw** | `maw.geo.json`, `maw.png` (+ `maw_glow.png`) | `root` (0,0,0) > `jaw_lower` and `jaw_upper`, both pivots at the hinge (0, 0, 6: the back); optional `tongue` under `jaw_lower` | jaws closed and flat on the ground, a fanged oval about 20 wide, 24 long, 6 tall shut; the upper jaw opens by rotating up about X, the lower down | Hungering Maw 1.0, Call 1.5 |

The names matter: the code finds bones by these names and ignores extras, so more detail (fins,
teeth as their own bones, a `tip` bone) is welcome but only the named ones move. A missing named
bone is skipped, not fatal. The eye's lids are the only optional motion: with them the eye opens on
spawn and closes on dissolve; without them it fades. Keyframed `.animation.json` files are not
read in this pass: the motion is procedural, which is what makes a grasp reach the thing it grabs.

## The six actives

Six, like blood: the school's budget. Every one calls one creature, and the number in brackets is
the Notice it costs before the points move it.

### 1. Grasp of the Deep [12] - tentacle, ground

Aimed (drops to the ground, 20 blocks). A tentacle erupts from the aim point, grows up over the
form ticks, and grabs the nearest hostile within Reach (2.5 x size): ROOTED for the hold, and the
crush every 20 / speed ticks. The chain's tip tracks the victim's chest; with nobody to grab it
sways and takes whoever walks into reach for as long as it stands. Tuning: Crush (damage), Rise
(speed), Reach (size), Hold (duration), Thrift. Stats: dmg 4, spd 1, size 1, mana 22, cd 200,
dur 80.

### 2. Unblinking Eye [8] - eye, floating at the aim point

Aimed (12 blocks, eye height). A lidless eye hangs where you looked for Watch ticks and looks at
the nearest hostile it can see within Sight (12 x size): that thing is REVEALED to you while seen,
takes 25% more of your spell damage while seen, and the stare itself stings (damage every 40
ticks). The body rolls toward its mark, the pupil contracts on it. Tuning: Sting (damage), Sight
(size), Watch (duration), Thrift; no speed. Stats: dmg 2, size 1, mana 18, cd 300, dur 400.

### 3. Hungering Maw [15] - maw, ground

Aimed (drops to the ground, 16 blocks). Jaws open in the ground at the aim point over 24 / speed
ticks and hang open for at most the duration; the moment a hostile stands above them, or when the
wait runs out, they snap shut: heavy damage in Bite (1.8 x size) and an upward launch, and every
hostile projectile inside the bite is eaten. Tuning: Bite (damage), Snap (speed), Gape (size),
Patience (duration), Thrift. Stats: dmg 14, spd 1, size 1, mana 26, cd 240, dur 60, kb 0.8.

### 4. Tendril Lash [6] - tentacle, on the caster

No aim. A tentacle grows from the caster's shoulder and whips through a forward arc of Length
(5 x size) in 12 / speed ticks, then withdraws: every hostile in the arc takes the sting, is shoved
sideways out of it, and is HARRIED for the duration. Fast, cheap, the school's bread and butter.
Tuning: Sting (damage), Snap (speed), Length (size), Stagger (duration), Thrift. Stats: dmg 9,
spd 1.4, size 1, mana 14, cd 90, dur 40, kb 0.6.

### 5. Skin of the Deep [10] - tentacles, worn

Self. Wards = round(4 x size), clamped 2..8, small tentacles grow from the caster's back and stand
for Wear ticks. Each hit the caster takes is taken by one ward instead - it absorbs the whole hit
and dissolves - and the ward that took it bites the attacker back (the damage stat) if they are
within 4 blocks. While any ward stands the caster is IMMOVABLE. The construct's `extra` is the
count of wards left, and the renderer draws that many; hidden from its own wearer in first person,
like the coagulate shell. Tuning: Bite (damage), Wards (size), Wear (duration), Thrift; no speed.
Stats: dmg 5, size 1, mana 30, cd 600, dur 400.

### 6. Call of the Deep [5 per pulse] - eye above, tentacles everywhere

Held. A great eye opens above the caster and, every 30 / speed ticks while the key is held and the
duration lasts, a tentacle erupts under a random hostile within Reach (10 x size) and grasps it
briefly (20 ticks of ROOTED and one crush). Each pulse pays the mana and the Notice again, so the
longest call is the loudest: a full call from clean lands you Watched, and a second one Noticed.
Tuning: Wrath (damage), Cadence (speed), Reach (size), Call (duration), Thrift. Stats: dmg 6,
spd 1, size 1, mana 12, cd 500, dur 200.

## Passives

Two, like dark. Both change what Notice does to you.

- **Lidless**: Notice decays half as fast, and every construct is drawn and reaches 20% larger. You
  stay seen longer and are given more for it.
- **Deep Bargain**: while Noticed (100), eldritch skills cost no mana. The top rung becomes a mode
  you can choose to live in, tentacle and all.

## Visual profiles

Each active gets its own emblem (six new `EmblemId` cells with strokes in `FxTextures`: TENDRIL,
LIDLESS_EYE, FANGED_MAW, LASH, SCALES, DEEP_CALL), its own stamp within the school (SPIRAL, EYE,
TOOTH, WAVE, HEX, RING), its own frame side count (3, 5, 6, 7, 8, 9), and its own silhouette
family (MARK ink stain, ORB thin halo, RIFT iris mouth on the ground, FILAMENT ink tendril trail,
FIELD organic-cell dome, SWARM cloud) painted round the model, so `VisualProfiles.hardProblems()`
stays empty. Material `SchoolMaterial.ELDRITCH` (teal over ink, IRIS core, counter-fast spin),
which already exists, as does the school's sculk shriek cast cue.

## Testing

- Unit: `GeoModelParserTest` (a Blockbench export string parses to the right bones, pivots,
  cubes, UVs, parents; per-face UV is refused; a missing texture size defaults to 64),
  `EldritchPoseTest` (chain reach, bounds, continuity, growth order; gaze lag; jaw timing),
  `EldritchPricesTest` / `EldritchServiceTest` (every skill priced, potency curve, rung
  thresholds, decay, Lidless halves it, Deep Bargain waives mana at 100 only),
  `EldritchSchoolTest` (six actives on tier -5 with attribute ELDRITCH, two passives, every one
  wired, commandable, translated), `VisualProfilesTest` unchanged and green, the lang key test.
- Game tests (`EldritchGameTests`, template `unwaking_empty`, everything inside relative 0..4):
  a grasp erupts beside the victim on the side of the caster, roots it and crushes it; an eye hangs
  just off what it looks at and reveals it; a maw opens under what the caster looks at and snaps;
  a lash harries the thing in front; the skin takes a hit and loses a ward; a held call pulses and
  raises Notice past Watched; Notice decays for an eldritch mage. Victims are husks (a zombie burns
  in the daylight of the test world); each test runs in a batch of its own and starts by removing
  the fake players left by the ones before; a fake player is set client-loaded, since a player is
  invulnerable until their client reports the world loaded.
- Captures with the placeholder models: first person aimed at a golem (grasp, eye, maw, lash);
  third person from behind (the skin, the call held).

## Implementation notes (2026-09-15)

- The aim resolver skips the entity search when the tolerance is zero, so a handler that returns
  zero never sees what the caster looks at: the ray runs on to the wall or the ground behind it.
  The six handlers keep the default tolerance; the grasp erupts `beside()` a struck entity toward
  the caster, the eye hangs half a width plus the wall gap off it, the maw opens under its feet.
- The call eye keeps its height above the owner between ticks; the lash grows from the right
  shoulder; the wards hide from their wearer in first person, the lash does not.
- A construct carries its reach as its radius; the renderer zeroes the FX radius so the profile
  silhouettes keep their written sizes instead of growing to it as a burst would.
- Placeholders are teal cubes with bright fronts (`scripts/eldritch-placeholders.py`); their glow
  layers are transparent except the glowing pixels.

## Primordial, later

Layer -4, priced in the world: every cast scars the land where it was cast and the scars do not
heal on their own. Stone raised from the ground, water parted, weather called, a thing petrified
where it stands, the caster's own age spent on a great working. It needs no creature models: its
vocabulary is the terrain (real blocks, moved and placed) and the existing body and field
silhouettes, with one modelled asset at most - a titan's hand for the raising. Its own spec when
its turn comes.
