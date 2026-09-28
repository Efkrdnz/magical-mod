# The sigil library — symbols × inks, combined per skill

**Date:** 2026-09-28
**Status:** approved (scope: library only)

## Why

The rune particles the matter layer added (`MagicalParticles.RUNE`: eight pale 8x8 glyphs, one picked
at random per particle, tinted by the spawner) read well. Long Debt's crown of violet runes over the
named thing's head is the example that prompted this. The concept is worth more than one particle:
a **library of symbols**, each available in **several colour variations**, that a skill (and later a
passive) can **combine** - which symbols, which colours, how they move, where they appear - in a
line of code.

## Scope

In: the symbols, the inks, the motions, one particle that draws any combination of them, the
combination recipe, five ready-made placements, a preview command, tests, docs, and Long Debt's
crown moved onto the library as its first user.

Out, deliberately: signatures for passives (which symbol each passive shows and at which moment
it fires is a design pass per passive, to be done later), an ambient passive aura, and moving any
other existing rune user. `MagicalParticles.RUNE` and its eight 8x8 sprites keep working exactly
as they do.

## The pieces

All of it lives in `magic/visual/sigil/` except the particle itself (client) and its options
(`particle/`). The four types a caller touches - `Sigil`, `SigilInk`, `SigilMotion`, `SigilMark` -
are pure: no registry, no level, testable without a bootstrap.

### Sigil — 43 symbols

An enum whose declaration order is the texture order. Five families:

| family | symbols |
|---|---|
| RUNE | `RUNE_0` .. `RUNE_7` (the eight glyphs `MagicalParticles.RUNE` already throws, same drawings) |
| ELEMENT | `FLAME`, `DROP`, `SNOWFLAKE`, `LEAF`, `WAVE`, `SUN`, `MOON`, `STAR`, `SPROUT` |
| CREATURE | `EYE`, `SKULL`, `BONE`, `FANG`, `HEART`, `PAW`, `FEATHER` |
| OBJECT | `KEY`, `HOURGLASS`, `CROWN`, `SHIELD`, `COIN`, `SWORD`, `HAMMER`, `FLASK`, `ANCHOR`, `GEAR`, `LINK` |
| MARK | `PLUS`, `ARROW`, `CHEVRON`, `DIAMOND`, `TRIANGLE`, `RING`, `SPIRAL`, `THORN` |

`Sigil.runes()` is the RUNE family in order; `serializedName()` is the lower-case name;
`byName(String)` reads it back. Where the HUD already has a stamp for a meaning (the sins, the
statuses), the sigil of the same name is the same idea, so a later passive pass can keep one
vocabulary across the HUD and the world.

### Art

`scripts/particle-sprites.py` draws them next to the existing four sets. Each symbol is strokes on
a 7x7 grid drawn at 2x into a **16x16** texture (16, not 9 or 14: a sprite whose side is not a
power of two drops the whole particle atlas to mip level 0). Two textures per symbol:
`sigil_<name>.png`, the strokes, and `sigil_<name>_glow.png`, a one-texel halo round them - opaque
where it touches a stroke edge-on, a little softer on a diagonal. Both are pale greyscale, because
the ink is the only hue. `particles/sigil.json` lists all the cores in `Sigil` order and then all
the glows in the same order; the particle picks core *i* and glow *N + i*. The RUNE family is
drawn from the same table as the 8x8 runes, so the two cannot drift apart.

### SigilInk — two-tone colour variations

A record `SigilInk(int core, int glow)`: the core colours the strokes, the glow colours the halo.
Fifteen named presets (the number is `sqrt(contrast(core, glow))`, see below):

| ink | core | glow | reads | | ink | core | glow | reads |
|---|---|---|---|---|---|---|---|---|
| `VIOLET` | F4E8FF | 985AFA | 1.86 | | `TEAL` | D9FFF4 | 0F947B | 1.88 |
| `GOLD` | FFF6D0 | B86E00 | 1.92 | | `VERDANT` | EAFFDB | 2C993A | 1.86 |
| `EMBER` | FFEFA8 | E64717 | 1.86 | | `LIME` | FBFFD2 | 669000 | 1.92 |
| `CRIMSON` | FFD9DC | E0213B | 1.91 | | `UMBRA` | FFDFFF | C42ADB | 1.92 |
| `ROSE` | FFE4F1 | D64286 | 1.88 | | `SHADOW` | E6DDF2 | 3B2156 | 3.22 |
| `AZURE` | E4F4FF | 2C7EF0 | 1.87 | | `STEEL` | FFFFFF | 7E8C9C | 1.85 |
| `FROST` | FFFFFF | 1E88C8 | 1.97 | | `BONE` | FFFBEA | 90856D | 1.87 |
| | | | | | `NIGHT` | 2A1840 | E2D6FF | 3.43 |

Most are a pale core in a saturated halo (light over dark: the halo outlines it on daylight, the
core carries it at night). `SHADOW` is a pale core in a near-black halo; `NIGHT` inverts the rule,
a dark core in a pale halo. `SigilInk.named()` lists them with their names (the preview command and
`/particle` use the names); `SigilInk.from(rgb)` derives an ink from any one colour - a passive's
own, a profile's `BASE` - for combinations the presets do not cover.

**Readability is a number, not a taste call.** A mark outlined in a second colour reads over *any*
background at least as well as the square root of the contrast between its two colours: wherever
the background defeats one layer, the other carries it (the same argument `HudPalette` makes for a
glyph over its drop shadow). So every named ink holds `sqrt(contrast(core, glow)) >= READABLE` = **1.8**
(a core-to-glow contrast of 3.24:1, WCAG relative luminance). That is background-independent - no
list of reference grounds to go stale - and it is what failed in the first mockup for a white core
in a pale cyan halo (1.23), which disappeared into a daylight sky, and for gold (1.31), whose
orange read against blue sky only by hue and would have vanished on noon sand. The presets were
tuned to it by deepening the halo, never by darkening the core. `from(rgb)` takes its core as
`rgb` mixed 85% toward white and its glow as `rgb` itself, mixed toward black in steps until it
holds; since that core is always light, black always gets there.

### SigilMotion — how one moves

| motion | behaviour | use |
|---|---|---|
| `RISE` | lifts, slows, holds, fades, shrinks a little (the rune's motion today) | the default; runes off a circle, a crown |
| `HOVER` | stays where it is written, fades in and out | a mark that must hold its shape |
| `DRIFT` | carries the velocity it is given, barely slowing | bursts, throws |
| `FALL` | sinks and sways, settles on what it lands on | curses, things shed |

Each carries its friction, gravity (vanilla's field: spent as `0.04 * gravity` a tick), life and
jitter, and two pure curves, `alpha(t)` and `size(t)` over its life `t` in 0..1, so a test can hold
that every motion ends invisible.

### The particle

`MagicalParticles.SIGIL`, options `SigilParticleOptions(Sigil sigil, int core, int glow, float
scale, SigilMotion motion)` with a map codec (names, so `/particle magical:sigil{sigil:"eye",
core:..., glow:...}` works by hand) and a stream codec (ordinals as var-ints). The client particle
`client/particle/SigilParticle` is full bright on the translucent particle sheet and draws **two
quads**: the glow sprite in the glow colour, then the core sprite in the core colour, both at the
same billboard. Velocity is kept exactly as sent (no vanilla re-roll), and it keeps off the lens
exactly as the tinted sprites do: never born within `AccentPlan.SPRITE_NEAR_CAMERA` of a
first-person camera, removed if it drifts there.

### SigilMark — a combination

```java
SigilMark.of(Sigil.HOURGLASS).ink(SigilInk.GOLD)
SigilMark.of(Sigil.runes()).ink(SigilInk.VIOLET).scale(1.5F)
SigilMark.of(Sigil.EYE, Sigil.KEY).inks(SigilInk.GOLD, SigilInk.VIOLET).motion(SigilMotion.DRIFT)
SigilMark.of(Sigil.FLAME).ink(SigilInk.from(0xFF7A45))
```

An immutable record of symbols, inks, a motion and a scale. The *i*-th particle of a placement
takes `sigilAt(i)` and `inkAt(i)`, each cycling through its list, so a crown of eight runes in
two inks alternates, and slot *i* always gets the same symbol - a reprinted crown lands the same
glyph in the same place. Defaults: `VIOLET`, `RISE`, scale 1.

### SigilPlacement and Sigils — where they appear

`SigilPlacement` is pure geometry, a list of `Spawn(x, y, z, vx, vy, vz)` for:

- **crown** - a level ring just over a body's head, `slots` evenly spaced, the first `filled` of
  them written (Long Debt's ring, lifted out of the skill);
- **pop** - one symbol just over the head, drifting up: *this just happened to me*;
- **halo** - a ring round the body at chest height;
- **burst** - out from a point in evenly spread directions;
- **rise** - up out of the ground at random points in a disc.

`Sigils` sends them from the server: `Sigils.crown(level, body, mark, slots, filled)`,
`pop(level, body, mark)`, `halo(level, body, mark, count)`, `burst(level, at, mark, count,
speed)`, `rise(level, centre, radius, mark, count)`. One `sendParticles` with a count of zero
per symbol, so each travels exactly its own velocity.

### Preview

`/magical-debug sigils [ink]`: every symbol hung in front of the player as `HOVER` sigils - one ink
as a block of symbols, or with no ink every ink as a row - sent past the player's particle setting
so a capture always shows it. The capture that judges the library shoots it at noon and at
midnight.

## Long Debt

`LongDebtSkill.crown` becomes `Sigils.crown(level, named, CROWN, CROWN_GLYPHS, glyphs)` with
`CROWN = SigilMark.of(Sigil.runes()).ink(SigilInk.from(base)).scale(1.5F)`: the same ring, the same
height, the same rise and fade, the skill's own colour, and now a fixed rune per slot, where the
random pick used to change every glyph each time the ring was reprinted.

## Tests

- `SigilTest` - 43 symbols, names unique and round-tripping, eight runes in order.
- `SigilSpritesTest` - `sigil.json` lists exactly the cores then the glows in `Sigil` order; every
  texture is there and 16x16; no core is empty; no glow overlaps its core; no two symbols are the
  same drawing or within two glyph pixels of it.
- `SigilInkTest` - every named ink and `from(rgb)` for a sweep of colours holds `READABLE`; the
  names are unique.
- `SigilMotionTest` - every motion has a life, starts visible and ends invisible.
- `SigilMarkTest` - cycling by index, defaults, the lists copied rather than shared.
- `SigilPlacementTest` - crown radius, height and slots; pop above the head; halo at the chest;
  burst directions evenly spread; rise inside its disc and upward.

## Docs

`SKILL_CREATION_NOTES.md` gets a *Sigils* part in the matter-layer section (how to pick and combine,
the readability rule, when a sigil is right and when a vanilla particle is); `CLAUDE.md` gets the
architecture paragraph and the preview capture line.
