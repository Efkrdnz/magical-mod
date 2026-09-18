# The boundary is a vault

*The Authority of Space: what a subspace looks like from the one place anyone ever looks at it from.*

## The complaint

"Make it look nicer, less AI made and easier to see through."

The first answer to that was a wall whose transparency was an inverted Fresnel - almost clear
where it faces you, firm at the limb - on a premultiplied blend that darkens rather than adds.
The arithmetic was correct, the tests were green, and the result on screen was:

> "You gotta be kidding. There is like 2 lines only its not even visible a bit."

They were right, and the fault was not calibration.

## The bug, which is a fact about where the camera is

`DomainEntity` pins the sphere to its owner:

```java
setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ());
```

That is the body centre, feet + 0.90. The first-person camera is the eye, feet + 1.62. So the
eye is **0.72 blocks above the centre of the sphere and displaced nowhere else** - the horizontal
offset is exactly zero. Logged out of the running game to be sure:

```
SUBSPACEPROBE r=16.0 entityY=119.89999997615814 camY=120.61999988555908 d=0.7199999094009399
```

Two things follow, and the first design walked into both.

### One: there is no view angle to be a function of

For an eye at distance *d* from the centre of a sphere of radius *R*, the cosine between the
surface normal and the line of sight has a closed form and a floor:

```
c_min = sqrt(1 - (d/R)^2)
```

At R=16, d=0.72 that is `c` in `[0.99899, 1]`. Feed it through the wall's own curve,
`alpha = 0.070 + 0.51*(1-c)^3`, and the alpha runs from `0.070000000000` to `0.070000000530`.
**The Fresnel term contributed 5.3e-10 of the 0.51 range it was designed for.** The blue-noise
dither the shader adds is +/-1/510, which is three and a half million times larger: the only
spatial structure anywhere on that wall was the noise.

So the wall was a flat 7%, constant over the whole frame, with no gradient anywhere and therefore
no edge for an eye to find. Over a daylight sky it moved the picture by 15 levels of 255. Over the
blackstone that filled most of the capture, by **2**.

Two more terms were dead for the same reason. `NEAR_LIFT` was gated on
`smoothstep(1.0, 4.0, length(viewDir))`, which needs the wall within 4.72 blocks, against a
`BASE_RADIUS` of 5 - identically zero at every radius the skill can create. The limb glow needed
`b/R >= 0.984` against a reachable 0.045, so the membrane emitted **zero light at every hour of
the day** and the sun term was computed, packed into six bits and thrown away.

### Two: a meridian projects to a straight line

Every meridian plane contains the polar axis, the eye is *on* the polar axis, and a planar curve
seen from a point in its own plane projects to a straight line. Measured deviation from perfectly
straight, at R=5 and R=16, at several bearings: **0.0000 px**. Not nearly straight. Exactly.

So any structure built from meridians is drawn as a cage of straight near-vertical bars, and in
Minecraft that vocabulary is already spoken for: a tree trunk, a fence post, a nether portal jamb,
a standing player, an end rod.

### Why the tests did not catch it

Every assertion was about a viewer standing outside: the mean alpha over the projected disc, the
exactness of the two-crossing identity, that a dozen layers cannot clip. **A domain discards
itself when its owner leaves it**, so that viewer cannot exist in single player. Four green tests
certified a wall nobody could see.

## The object

A **vault**, and you are standing under an arch rather than inside a bubble.

- **The plinth** - one ring where the wall meets the floor you are standing on, at latitude
  `asin(-0.62/R)`. It is drawn a third of a block above the floor rather than on it: at sixteen
  blocks the depth buffer cannot separate a surface six hundredths of a block above another, and
  the first version came out patchy and half swallowed. It is the heaviest member in the domain,
  because it is the only one in the lower half of the frame and because a plinth carries a wall.
- **The springing course** - one ring right round the wall, at a constant **apparent elevation of
  14 degrees** rather than a constant latitude. A fixed latitude sinks: the sphere is centred on a
  chest, not on the floor, so a ring at a fixed latitude is progressively buried as the radius
  grows. Solving `atan2(R sinL - d, R cosL) = t` for L is one identity,
  `L = t + asin(d cos t / R)`, and it gives the same composition in a five-block domain and a
  sixteen.
- **Twelve ribs**, springing from that course and climbing to the oculus. Slot *k* is
  `SpaceRuleCategory.values()[k]` and owns bearing 30k for good, gravity due north.
- **Twelve notches** crossing the course at those bearings - the tally, lit in the law's colour.
- **Up to twelve bosses**, each a swelling on a lit rib carrying its `SpaceRuleChange` glyph.
- **The oculus** - the pane is cut away entirely above latitude 78 and the opening is ringed. A
  domain is a wall and not a lid, said as a hole you can see the sky through rather than as an
  alpha nobody could see.

## The twist

```
bearing = 30k + twist * (latitude - springing)
twist   = TWIST_BASE + TWIST_PER_LAW * lawCount        // 0.45 .. 1.35
```

A rib leans `atan(twist * cos(latitude))` off vertical: **23 degrees with nothing written, 52
with all twelve**. One constant, no extra quads, and it answers three separate objections at once.

1. It takes the ribs off their meridians, so they are not straight bars.
2. It destroys the lat/long generator. Every element in the first design was either a circle of
   latitude or a meridian - a twelve-by-two graticule, which is the same object as the sixty-four
   by thirty-two one it was rejected for being, with lines deleted. A helix is neither.
3. **The shape becomes a function of the contents.** How much law is in force is legible from the
   silhouette, at any distance, in any light, before a single glyph has been parsed.

Note what it does *not* do. The bow - deviation from a straight line on screen - is only 3 to 11
pixels, because at eye level you see about 14 degrees of any one rib and a short arc of anything
looks straight. The twist works by *leaning*, not by curving. Looking up, where the whole rib is
in frame, the curvature is the picture: twelve ribs spiralling into the oculus.

## The chamfer

Every stroke in the domain is a moulding: a lip darkening toward `BODY` (0x121A26) at
`A_SHADOW` = 0.62 meeting a lip lightening toward `GLAZE` (0xDCEBFF) at `A_HIGHLIGHT` = 0.52,
across a `ARRIS_PIXELS` = 1.2 arris.

The two lips move a background in **opposite directions**, so whichever one the background
defeats, the other one carries. The hardest background is the one where they are equal and
opposite, and solving for it removes the background from the answer altogether:

```
floor = A_SHADOW * A_HIGHLIGHT * (GLAZE - BODY) / (A_SHADOW + A_HIGHLIGHT)
      = 57.1 / 59.1 / 61.4  of 255, per channel
```

That is a **guaranteed lower bound over every background in the game**, closed-form rather than
sampled, and it is `SubspaceOptics.chamferFloor`. `SubspaceVaultTest.theWallCannotBeInvisible`
asserts it and then walks all 256 levels per channel to check the closed form is really a floor.
The old wall scored 2 against blackstone on the same measure.

Two details that matter. The arris is measured in the same `max(fwidth(across), MIN_GRAD)` the
stroke is, so at grazing incidence both blow up together and the chamfer can never flatten into
one mid-blue line - the one real hazard in the idea, designed out rather than tuned around. And
**the light sits in the groove, not on the lip**: the lit lip has already been pushed toward white
and has no headroom, the shadow lip has just been pushed toward slate and has all of it, and a
dark groove with light in it is what a lit engraving looks like.

`SubspaceOptics.lipCorePixels` is why every stroke is at least 6 pixels: below about a pixel and a
half of solid lip either side, the two average into one line and the entire mechanism is gone.
`SubspaceVaultTest.everyLipResolves` holds all eight widths to it. The first pass specified a
3.0px glyph and a 3.6px keystone, both of which fail it - caught by arithmetic before they were
ever drawn.

## The pane

```
alpha = (sealed ? A_PANE + SEAL_LIFT : A_PANE) * (1 - step(APERTURE_SIN, |sin latitude|))
```

Flat 3%, and exactly 0 above latitude 78. No normal, no dot product, no pow, no smoothstep. It is
a tint - six levels of cold slate over daylight sand - and it carries no reading, which is the
point: the reading is the structure, and **the domain got clearer and far more visible at the same
time, because tint is area times alpha while visibility is local contrast**. 0.070 over 100% of
the frame became 0.030 over 97% of it plus about 2% of stroke at 57 levels or better.

The Fresnel survives on exactly one branch, the outside one, where `b/R` genuinely sweeps [0,1]
and nothing is a constant. The two branches are separated in the fragment shader by two named
comments, and `SubspaceShaderAssetsTest.theInsideMembraneHasNoViewAngle` extracts the text between
them and fails if `dot(`, `viewDir`, `shellNormal` or `pow(` appears. **The bug is now something
that fails a test rather than a screenshot.**

## Captures

- A domain ends when its caster leaves it, so the **outside** case cannot be photographed in
  single player; its optics live in `SubspaceOpticsTest` instead.
- **Laws written at `everything` physically move the caster.** `orbit`, `repel_boundary` and
  `stop` shoved the player off the centre of their own dome, and every frame after that was shot
  off-axis - which is how a whole capture run came back with the springing course sitting on the
  crosshair. Use `everything_except_user`. The five targets are `everything_except_user`,
  `everything`, `living_entities`, `projectiles`, `players`; there is no `mobs`, and naming one
  prints the list and writes nothing, so all twelve ribs come back unlit.
- The dev world persists between runs, so the stage is built at absolute coordinates, and
  `forceload add` plus about twenty ticks is needed before any `fill` or the commands run against
  unloaded chunks and silently do nothing.
- The rule flash lasts 48 ticks, so screenshots sit clear of the last `subspace rule` by that much.
- Judge it at **noon**, over a floor that is half sand and half blackstone with sky above, so one
  frame carries the bright and the dark case together. Pitch -25 is the reading view, -62 shows
  all twelve ribs and the oculus at once, +28 shows the plinth.

## Rejected

- **Turning the alpha up.** The complaint was not subtlety, it was absence, but a heavier pane is
  the milky failure this whole boundary was rebuilt to escape, and it breaks the standing request
  for something easier to see through.
- **A lat/long graticule**, in both the sixty-four-line and the twelve-line form. Same generator,
  fewer lines, and it is the single most template-ish thing that can be drawn on a sphere.
- **A second, inner skin for parallax.** 480 quads spent on a cue that only exists under
  translation and therefore cannot appear in a screenshot - and the review loop is screenshots.
- **Twelve keystones round the oculus rim.** Specified at 3.6px *inside* a 4.0px ring at the same
  latitude, so an unlit one was invisible by construction and the count had no denominator. The
  rib heads already radiate from that ring in the law's own colour.
- **The entire lower hemisphere** - a well, a lower rim, piers below the ground line. On flat
  ground the shell is cut by the floor at latitude -3 degrees, so three quarters of every pier and
  all of the well is underground and only photographable from a tower. The plinth replaced it and
  lifted every downward pitch from nothing to a readable line.
- **A socket pinch on unwritten ribs.** A fourth encoding of a bit already carried by width,
  colour and a boss, and at 55% of a 6.2px rib it fails `everyLipResolves` anyway.
