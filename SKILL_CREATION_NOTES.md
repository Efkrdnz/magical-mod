# Skill Creation Notes

## Visual Direction

- Do not reuse vanilla projectiles, fireballs, explosions, or particles as the main identity of a skill.
  As the matter a spell is made of they are welcome: see *The matter layer* below.
- Prefer invisible gameplay entities with custom renderers.
- Render spell visuals with quads, billboards, rings, ribbons, beams, or layered planes.
- Use custom shader files where needed: `.vsh`, `.fsh`, and shader `.json`.
- Skills should feel custom in behavior, not just recolored versions of the same projectile.

### The matter layer — shader for light, particles for stuff

A shader is additive light. Drawn over daylight it clips toward white, it never occludes, and a few
of them on one spot stack into a white sun — which is what a capture of the whole roster showed
almost every skill doing: a starburst on every hit, a second glyph circle stamped on top of it, sixty
glowing motes pretending to be debris, and the caster's own cast circle filling half or all of a
first-person frame. So a skill's visual is now two halves:

- **Light** stays shader: the cast circle, beams, fields, flashes, ground marks, rifts, auras.
- **Matter** is real particles: embers, smoke, spray, drips, snow, ash, shards, dust, crumbs of what
  was hit, an explosion puff on a heavy hit. Vanilla particles are welcome here — `FLAME`, `SMOKE`,
  `SPLASH`, `SNOWFLAKE`, `END_ROD`, `REVERSE_PORTAL`, `EXPLOSION`, `POOF`, `DUST_PLUME`,
  `BlockParticleOption(BLOCK, state)` — and where vanilla has no sprite in the colour you need, the
  mod has four of its own, pale pixel art tinted per spawn: `MagicalParticles.RUNE` (a lifting
  glyph), `SHARD` (a lit fragment that falls and lands), `MOTE` (a twinkle) and `WISP` (coloured
  smoke), each spawned as `new TintedParticleOptions(type, rgb, scale)`. Their art is drawn by
  `scripts/particle-sprites.py`; keep new sprites pale, because the tint is their only hue.

Vanilla particles are still never the *identity* of a skill: no reused projectiles or fireballs, and
no skill that is only a vanilla particle effect. They are the stuff the spell is made of, thrown at
the moments the spell already has. A solid thing the spell *is* — a slab, a wolf, a blade — wants a
model rather than either half; this layer is what such a thing throws off, sheds or breaks into.

**Every profile has an `Accent`** (`magic/visual/Accent`) and takes its school's by default: Arcane
`RUNE`, Fire `EMBER`, Water `SPLASH`, Light `RADIANT`, Void `UMBRA`, Spatial `RIFT`, Soul `SOUL`,
Dark `GLOOM`, Chaos `CHAOS`, Primordial `EARTH`, Eldritch `DEEP`. Name another with
`.accent(...)` on the profile builder when the school's is wrong for the skill — an ice skill in the
Water school is `FROST`, a skill that is the ground moving is `EARTH`, and three belong to no school
at all: `FORGE` (hammer sparks, slag, heat haze) for struck and heated metal, `BREW` (potion swirl in
the spell's colour, bubbles, the glass of a splash potion) for anything thrown in a vial, and `BLOOM`
(petals, green growth sparks) for anything that grows. With an accent, every generic
cue throws its matter through `client/fx/SpellAccents` and draws a lighter shader pass, every factor
of which lives in `AccentPlan` and is pinned by `AccentPlanTest`:

- the impact flash is a spark at the point (`FLASH_SCALE` 0.7 of `flashSize`, six ticks, not
  opaque, in the school's `BASE` colour so a pale palette does not clip to white), with no
  delivery-circle stamp over the mark, and no ground mark on a hit with no ground behind it; the
  mark there is is 0.6 the size at half the opacity (`MARK_SCALE`, `MARK_OPACITY`), because the
  matter carries the hit and the mark only says where it landed, and a stain a skill leaves
  (`SpellFx.decal`) is cut the same way;
- a hit on a body throws its matter off the side of the body the viewer sees, at its middle height,
  outward (`BODY_FACE_GAP`): thrown from its middle, the spray was inside a golem and every school's
  hit read as the same lone flash on its chest;
- a heavy hit (tier 3+, or scale 1.5+) gets vanilla's explosion sprite and a ring of puffs instead
  of a bigger flash — but once per spot a second (`AccentPlan.blastEchoes`: a skill that bites one
  body every ten ticks, or a chain of detonations in one place, blasts once), never within three
  blocks of a first-person camera, and never for a splash, a shatter of ice or a fold of space,
  which ring as what they are;
- a hit on terrain throws crumbs of the block it hit;
- **the caster's own view**: everything a cast sends starts at the caster, so in their own
  first-person camera — asked every frame, because the camera can change while a circle is up —
  their circle is drawn small and faint: for `EYE_FORWARD` a sigil under an eighth of the frame,
  moved off the line of sight to the hand's corner and never over the crosshair; for `GROUND`,
  `BOTH` and `TARGET_FOLLOW` a dimmed ring at the feet. Their muzzle flash is a spark at the hand, a
  `SLAM` release lays its flash on the floor rather than at the hand it is sent from, and the
  release throws half its matter 1.5 blocks further along the aim (`OWN_RELEASE_PUSH`) with no
  shader burst, so the cast still reads as leaving the hand without landing on the crosshair. The
  legacy casting circle hung in front of every caster's face is the same sigil at the hand
  (`MagicCircleEffectEntity.heldBy`). Everybody else still sees all of it whole;
- nothing of the matter layer is born within 1.25 blocks of a first-person camera
  (`SPRITE_NEAR_CAMERA`), and what drifts that near later goes — the mod's sprites check themselves
  every tick, and the vanilla particles the layer threw near the lens are swept
  (`SpellAccents.sweepLens`). A sprite a tenth of a block across fills a fifth of the view from half
  a block away;
- a light sprite (`RUNE`, `MOTE`) is never darker than `LIGHT_SPRITE_FLOOR` and a `SHARD` never
  darker than `SHARD_FLOOR`, whatever colour the palette hands it — the Void palette's motes were
  black specks. A `WISP` is smoke and keeps its colour;
- the shader draws only the matter that is light — sparks, glints, embers (`FxKinds.Smoke.glint`),
  30% of the old count; smoke, ash and ink were black cards over daylight and fragments, glyphs,
  mist and droplets were white dice, and the accent throws all of those as real particles;
- the windup lifts a few textured runes off its band instead of shader motes;
- moving profile entities trail their accent's particles by themselves
  (`SpellAccents.tickTrails`), so a projectile needs no trail code.

`Accent.NONE` keeps the old all-shader look, and four kinds of skill use it on purpose: the
**Authorities**, whose visuals are their own design (forced, whatever the profile names — held by
`AccentResolutionTest`); the **Blood school**, whose blood is its own voxel system (`BloodVoxels`,
the pools, the streams) and is not to be touched by a polish pass; the **Sword school**, which
already throws its steel's impacts as vanilla particles (`SwordImpactParticles`); and skills that
are **light all the way through** — a beam, a ray, a jet, a sighted line — which say so in a
comment beside `.accent(Accent.NONE)`. `GORE` (red dust) is for a cut that bleeds in some other
school.

When building a skill's own visuals on top of the cues:

- Do not draw matter as a `Silhouette.swarm(...)` of glowing smoke, ash or splinters with a big
  count. Throw it from the behaviour at the skill's own beats with `ServerLevel.sendParticles(...)`
  (the client's particle setting still applies) or `SpellFx.burst(...)` for the accent's burst.
  The polish pass is full of worked examples: Smokestack is sixteen faint shader puffs and real
  `LARGE_SMOKE` rising through the column, Magma Vent kicks crumbs of the real ground out of its
  crack during the warning and drops magma crumbs when the column goes, Crucible pours smoke over
  its rim, Slag Roller sheds crumbs where it rolls. A beat that must travel in a direction is a loop
  of `count = 0` sends, each with its own velocity; `count > 0` scatters.
- The mod's sprites travel over the wire too: `new TintedParticleOptions(MagicalParticles.SHARD.get(),
  rgb, scale)` works in `sendParticles`. A sprite's `gravity` is vanilla's field, spent as
  `0.04 * gravity` a tick, so a falling crumb is about 1 and a rising wisp is a small negative.
- Size a zone's pulse to the ground it hits with `SpellFx.zoneTickWithin(level, definition, pos,
  blocks)`. A plain `zoneTick` ripple is the tier's radius, which on a skill that hurts within three
  blocks drew a ring twice the size of what it hits.
- Keep particle counts tasteful: a beat is 4–30 particles, a per-tick emitter at most 3 a tick.
  Mind how long a particle lives when the beat repeats: an `END_ROD` lasts three seconds, cherry
  leaves fifteen, and a `REVERSE_PORTAL` hardly moves for its first thirty ticks, so on a body
  struck every half second or a zone that pulses they pile up into standing clumps. Use fewer, or a
  `MOTE`. And mind how dark: `SQUID_INK` is a black blot at any size — right for a splash of ink at
  someone's feet, wrong as a hit's debris.
- Mind the caster's own view for the beats a skill throws itself, because the lens guard covers
  only the matter layer's own particles, never a skill's `sendParticles`: something sent from the
  hand lands on the caster's crosshair, and something sent up from the feet or round the body rises
  through their eyes. Send it from the far end, small, or out of frame — Living Bulwark's dust
  pillars go up only outside a 150-degree gap in front of the caster, which is wider than the widest
  field of view there is.
- No additive orb larger than the thing it lights. A white disc over daylight reads as an error.
- Judge in first person, which is how it is played, and capture before and after: the skill gallery
  (every skill cast at a golem on a clean stage, three frames each) is what found every one of the
  problems above.

### Sigils — a symbol in an ink

When a skill or a passive has to *say* something rather than throw something — this body is named,
that hit was banked, a curse is falling, a debt is running — draw a sigil: one of 43 pixel symbols in
a two-tone ink, sent as one particle. `magic/visual/sigil/` is the whole library, and a combination
is one line:

```java
Sigils.crown(level, target, SigilMark.of(Sigil.runes()).ink(SigilInk.from(base)).scale(1.5F), 8, 8);
Sigils.pop(level, player, SigilMark.of(Sigil.HOURGLASS).ink(SigilInk.GOLD));
Sigils.burst(level, at, SigilMark.of(Sigil.EYE, Sigil.KEY).inks(SigilInk.GOLD, SigilInk.NIGHT)
        .motion(SigilMotion.DRIFT), 12, 0.15D);
```

- **`Sigil`**, the symbols, in five families: the eight runes `RUNE_0`..`RUNE_7` (the drawings
  `MagicalParticles.RUNE` throws), ELEMENT (`FLAME`, `DROP`, `SNOWFLAKE`, `LEAF`, `WAVE`, `SUN`,
  `MOON`, `STAR`, `SPROUT`), CREATURE (`EYE`, `SKULL`, `BONE`, `FANG`, `HEART`, `PAW`, `FEATHER`),
  OBJECT (`KEY`, `HOURGLASS`, `CROWN`, `SHIELD`, `COIN`, `SWORD`, `HAMMER`, `FLASK`, `ANCHOR`,
  `GEAR`, `LINK`) and MARK (`PLUS`, `ARROW`, `CHEVRON`, `DIAMOND`, `TRIANGLE`, `RING`, `SPIRAL`,
  `THORN`). Where the HUD already has a stamp for an idea — a sin, a status — use the sigil of the
  same name, so the HUD and the world speak one vocabulary.
- **`SigilInk`**, a core colour for the strokes and a glow colour for the one-texel halo round
  them. Fifteen presets — `VIOLET`, `GOLD`, `EMBER`, `CRIMSON`, `ROSE`, `AZURE`, `FROST`, `TEAL`,
  `VERDANT`, `LIME`, `UMBRA`, `SHADOW` (a near-black halo), `STEEL`, `BONE`, and `NIGHT`, the one
  dark core in a pale halo — or `SigilInk.from(rgb)` for any colour: a passive's own, a profile's
  `BASE`.
- **`SigilMotion`**: `RISE` (lifts, holds, fades — the default, the rune's own motion), `HOVER`
  (stays where it is written), `DRIFT` (carries the velocity it is sent with: bursts, throws),
  `FALL` (sinks, sways and settles: a thing shed, a curse).
- **`SigilMark`**, the combination: symbols, inks, a motion, a scale. The *i*-th sigil a placement
  writes takes the *i*-th symbol and the *i*-th ink, each list cycling, so a ring in two inks
  alternates and slot *i* always wears the same symbol.
- **`Sigils`**, the placements, sent from the server: `crown(level, body, mark, slots, filled)` (a
  level ring over the head with the first `filled` of `slots` written — Long Debt's tally), `pop`
  (one sigil drifting up off the head: *this just happened to me*), `halo` (a ring at chest height),
  `burst` (out in every direction), `rise` (up out of the ground over a disc). The geometry is
  `SigilPlacement`, pure and pinned by `SigilPlacementTest`.

**Readability is a number.** A two-tone mark reads over any background at least as well as the
square root of the contrast between its two colours — wherever the ground defeats one layer the
other carries it — so every preset holds `sqrt(Palette.contrast(core, glow)) >= SigilInk.READABLE`
(1.8), and `from(rgb)` deepens its glow toward black until it does. A white core in a pale cyan halo
scored 1.23 and vanished into a daylight sky; gold in an orange halo, 1.31, read on blue only by hue
and would have gone on noon sand. Tune an ink by deepening its halo, never by darkening its core,
and never lower the threshold: `SigilInkTest` holds every preset and a sweep of derived inks to it.

A sigil is a sign, not a swarm: a crown is eight, a pop is one, a burst a dozen. It is full bright,
never lit by the world, and kept off the caster's lens like the tinted sprites. Look at the whole
library with `/magical-debug sigils` (every ink a row of all 43; look up to hang it on the sky) or
`/magical-debug sigils <ink>` (one ink as a block up close; look down to lay it over the ground). A
new symbol goes at the end of its family in `Sigil` and as a 7x7 grid in `SIGILS` in
`scripts/particle-sprites.py`; rerun the script, and `SigilSpritesTest` holds the texture list to the
enum and every drawing at least two strokes from every other. Two strokes is the floor, not the aim:
the first flame was sixteen texels from the drop, passed, and read as a second drop in the capture.

## Skill Design

- Build skills as unique mechanics first, visuals second.
- Elemental and holy/arcane skills are allowed, but not every skill must fit a basic element.
- Add strange unique skills that do their own thing.
- Avoid making every skill a basic projectile.
- Skills should have distinct use cases, timing, targeting, and risk.

## Passive Skills

- Add passive skills as their own system.
- Passive skills should appear in a separate tab in the Magic Codex.
- Passive skills should be enabled by default.
- Player can enable or disable normal passives any time with a checkbox.
- Passive skills can include immunities, resistances, mana effects, movement effects, or class traits.

## Curses

- Curses should appear in the passive tab.
- Curses should not have a disable checkbox.
- Curses should have a `Dispel` option.
- Dispelling should require conditions such as proficiency level, mana, class, item, ritual, or boss kill.

## Build Order

1. Build the passive skill system and passive GUI tab.
2. Add starter passives and immunity support.
3. Add curse display and dispel conditions.
4. Replace placeholder active skills with unique mechanics.
5. Replace placeholder visuals with custom entity renderers and shaders.
