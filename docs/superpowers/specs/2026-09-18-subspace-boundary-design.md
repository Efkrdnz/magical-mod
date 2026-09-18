# The boundary wears the law

*The Authority of Space: what a subspace looks like from inside it and from outside it.*

## The complaint, and what it turned out to be

"Make it look nicer, less AI made and easier to see through."

The dome that was there drew six unrelated things on `RenderType.lightning()` and called the sum
of them a boundary: two shell bands whose alpha counted up and down on a sine, a hundred and
twenty-two sphere rings, five orbit arcs spinning at five different rates, three rings of glyph
ticks creeping round, and nine glints wandering the surface. 3123 quads, fixed whatever the
distance, additive, distance-sorted every frame.

The first capture in daylight showed the fault, and it was not the one the complaint named. From
inside, the dome laid a pale cyan wash over the entire sky. From thirty blocks outside, it was not
there at all - no silhouette, nothing to tell an opponent they were walking into a legislated
region.

**Those two failures are the same failure.** A constant alpha spread over a sphere covers the most
screen area exactly where the viewer is trying to see through it, and delivers the least exactly
where a silhouette lives, which is the limb. So there is no value of that alpha that fixes both:
lowering it deepens the invisible case and raising it deepens the milky one. The fix cannot be a
number. It has to be a function of angle.

A second fault was underneath it. `RenderType.lightning()` writes depth (`COLOR_DEPTH_WRITE`), so
water, glass and every other translucent thing behind a domain simply disappeared. That is a
see-through failure no shader change could have reached.

## The wall

One surface, and its transparency is an inverted Fresnel term:

```
alpha = A_FACE + (A_RIM - A_FACE) * (1 - |N . V|)^K
```

Where the wall faces you it is `A_FACE` and all but absent; where it turns away it climbs to
`A_RIM`. That is what a pane of glass does, and it is why glass is both see-through and visibly
present - you look through the middle of a window and you can still find its edges. It is also
the whole of the silhouette: at the limb `N . V` goes to zero and the wall reaches its full
weight, which is the one place a person outside needs it.

It **darkens** rather than adds. The render type is `PREMULTIPLIED_TRANSPARENCY` -
`ONE / ONE_MINUS_SRC_ALPHA` over premultiplied colour - so one fragment can paint over the world
or light it, and the wall chooses to paint with a cold slate. Darkening is the one thing that
reads over daylight sand as well as over a night sky. Additive reads over neither: over sand it
clips to white and over anything bright it is not there.

**The numbers are held by a test, not by taste.** `SubspaceOptics` computes the same curve in
doubles that the shader computes in floats, and `SubspaceOpticsTest` pins the face alpha, the rim
alpha, the area-weighted mean over the whole disc and over the inner three-quarters of it, and the
fact that a dozen layers of it over six different backgrounds cannot escape the channel. Those
ceilings were set from a plan and then moved once, after the first daylight capture said that a
wall the arithmetic called a clean pane was, standing in it, not there.

### One wall from inside, two from outside

Standing in your own domain a sight line crosses the boundary once. Standing outside it crosses
twice, the near wall and the far one. Painting both at the delivered alpha would make the domain
twice as heavy from outside as from in, which is exactly backwards: the person deciding whether to
walk into it is the one who needs to see through it.

So each of a pair paints the root of what the pair owes:

```
alphaPerCrossing = 1 - sqrt(1 - delivered)
```

which composites back to `delivered` exactly, at every angle rather than merely at the centre and
the rim. `SubspaceOpticsTest` walks a hundred and one samples of it.

Culling does the selecting, and it is doing real work rather than saving fill. `FxMesh.globe` is
wound outward and `FxMesh.globeInverted` inward; from inside, the inverted shell survives whole
and is the only thing emitted, and from outside it survives on the far hemisphere while an
ordinary globe supplies the near one. Emission order is far wall then near, so the premultiplied
blend needs no sorting at all.

## The writing

An empty domain is a pane of cold glass with a horizon on it. A fully legislated one carries a
dense, readable band. Everything drawn on the wall is a reading:

- **The horizon** - one level line round the waist, which is also the gravity readout. A level
  line is exactly the thing that says which way is down, so the most consequential law in the
  domain is legible before a single mark has been learned: inverted it doubles, dissolved it
  breaks into dashes.
- **The meridian** - one upright standing due north, graduated one crossbar per block of radius
  with every fifth cut longer. Reading a wall in blocks is the difference between knowing you can
  clear it and hoping.
- **Three plain cardinals** at east, south and west, stopped lower and drawn quieter. One line
  standing in three hundred and sixty degrees of wall has nothing to be a landmark against; four
  of them are a compass, and a compass is the least decoration that turns a curved surface into a
  place.
- **The law band** - twelve slots on one arc centred north, nine degrees apart, each owned by a
  `SpaceRuleCategory` for good so the band never renumbers itself. A mark's form is its
  `SpaceRuleChange` (up, down, struck out, mirrored, bracketed, overshooting, aimed, plain) and
  its colour is `HudPalette.change`, which is the same colour the rule flash uses. The meridian
  falls in the gap at the middle of the arc, so the marks read as two hands of six either side of
  a known direction and a legislated domain can be counted from outside without knowing what any
  one mark means.
- **The crown** - the zenith thins away to a fifth and is cut by one hard ring. A domain is a wall
  and not a lid: from inside you can always find your own sky straight up.

**All of it lives above the horizon**, and that is not a style choice. A subspace is centred on its
caster's chest and raised where they stand, so a radius-sixteen one has all sixteen of its lower
blocks underground. The band sat below the horizon until the first capture on the ground, where it
was buried by exactly the thickness of the floor. `SubspaceLedgerTest` holds the line now, along
with the corridor either side of north that the graduations and the marks share without touching.

### Two kinds of writing, and why the blend allows both

A line that runs right round the wall must darken: anything covering that much of the frame and
lighting will wash the sky out. A mark five degrees wide may paint, because an over-blend cannot
clip however bright it is - and a law whose colour only lives in the glow is a law daylight takes
the colour off, which it did: the first capture with marks had every one of them the same grey at
noon. So the crown, a crossing ring and a law mark paint their own colour, and everything else is
ink with a trace of the domain's light mixed into it.

### Nothing moves

A subspace at rest is still. The old dome pulsed, counted, spun and glinted without ever meaning
anything by it, which is the surest way to make a thing look generated. The shader has **no clock
at all** - `SubspaceShaderAssetsTest` fails if `GameTime` appears in any of its three files - and
the only animations left are one-shots the CPU drives through the vertex's `phase01`:

- a mark settling onto the wall over ten ticks when its law is written,
- a ring running out from wherever a body crossed the boundary, sixteen ticks, at most four at
  once and nothing at all at rest,
- the wall fading up over twelve ticks when the domain is raised. Its **radius never takes part**,
  because a radius that grows is a bubble and a domain is not a bubble.

## What it costs

Tessellation is a function of apparent size and never of radius. The silhouette error of an n-gon
seen from its own centre is `1 - cos(pi/n)`, which has no radius in it at all, so a five-block
dome and a sixteen-block dome look identical from the middle and cost the same. What matters is
`distance / radius`, one dimensionless ratio, and `SubspaceLod` turns it into four rungs.

| where                    | membrane quads | old dome |
|--------------------------|----------------|----------|
| inside, nearest rung     | 2048           | 3123     |
| point-blank outside      | 2560           | 3123     |
| a hundred blocks off     | 800            | 3123     |

The far wall is drawn two rungs coarser than the near one, because it is read through the near
wall - every pixel of it already multiplied by an alpha in the low tenths - and the two share one
silhouette, which the near wall draws at full detail. `SubspaceGeometryTest` checks the budget
against the meshes that are actually emitted, so the numbers above are about something.

## Two fixes that were not in the renderer

- **Culling.** An entity's bounding box hangs from its feet and a domain is a ball around its
  middle, so the registered 32x32 box covered the whole upper hemisphere and none of the lower
  one: a caster looking down at the floor of their own subspace had it culled out from under them
  the moment the centre left the frustum. `SpaceSubspaceRenderer.getBoundingBoxForCulling` is one
  override and was worth more than any amount of shader work.
- **Following.** `DomainEntity.followOwnerClient` eased a little over half the way to the owner
  each tick. That is invisible on a cloud of particles and very visible on a wall: an instrument
  that swims behind the person holding it reads as a mistake, and the server was setting the
  position outright anyway, so the two sides were disagreeing about where the wall is.

## The vertex

There is nowhere in the format to put a normal - position, colour, UV0 and two packed integers,
every bit of the integers spoken for - so the shell rebuilds its own normal from the coordinates
the mesh wrote into UV0: bearing across, height up. `FxMesh.sphere` cannot serve, and the
difference is one line: it writes a *mirrored* u that runs 0 to 1 and back so a texture will not
seam, which is right for a texture and fatal for a bearing, since two opposite points on the dome
would claim the same one. `SubspaceGeometryTest` holds the mesh to the reconstruction from the
other end.

Packed per vertex: `kind` (which of the seven elements), `count` (the sky's own darkness, so the
burnish is a function of the light it has to compete with rather than of the clock - a
thunderstorm at noon lifts it exactly as far as the sky it stole), `paramB` (the element's own
parameter: sealed and two-walls flags, a gravity state, a change id) and `phase01` (the one-shot).

## Captures

A subspace ends when its caster leaves it, so **the outside of one cannot be photographed in
single player** - the owner is always inside by construction. The optics of the outside case are
held by `SubspaceOpticsTest` instead.

The world persists between `-PquickPlay` runs and the player keeps whatever position the last run
left them in, so a repeatable stage has to be built at absolute coordinates.

A fully legislated domain raised on the ground, noon then midnight:

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoScreenshot=126,222,236,256,276,296 -PautoExit -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doDaylightCycle false;kill @e[type=!player];magical class unlock mystic;magical unlockall;magical authority set authority_of_space;weather clear;time set noon;40:tp @s 100 200 100 180 0;42:fill 78 118 78 122 130 122 air;44:fill 78 131 78 122 143 122 air;46:fill 78 118 78 122 118 122 smooth_stone;50:tp @s 100 119 100 180 -10;54:fill 82 119 79 118 134 79 polished_blackstone;56:fill 100 119 82 100 133 82 yellow_concrete;140:magical subspace create 16 false;142:magical subspace rule gravity remove_gravity;144:magical subspace rule velocity stop;146:magical subspace rule acceleration accelerate;148:magical subspace rule air_resistance dense_air;150:magical subspace rule pressure crush_pressure;152:magical subspace rule mass anchor_mass;154:magical subspace rule time_flow slow_time;156:magical subspace rule vector_field orbit;158:magical subspace rule entropy chaotic_motion;160:magical subspace rule friction slippery;162:magical subspace rule boundary repel_boundary;164:magical subspace rule collision ricochet_collision;230:tp @s 100 119 100 180 -38;270:time set midnight;272:tp @s 100 119 100 180 -10"
```

Two things about that recipe. The rule flash runs 48 ticks, so screenshots have to sit clear of
the last law by that much or the flash is in the shot. And the whole instrument stands north of
the caster - the meridian, the graduations and the band are all within fifty degrees of it - so a
capture facing anywhere else photographs an empty wall and proves nothing.

To see the whole dome at once, stand it in open sky on a small pad
(`fill 98 139 98 102 139 102 smooth_stone`, `tp @s 100 140 100`) rather than on the ground, where
the lower half is buried.

A crossing ring needs a body that was *seen outside* on the previous tick: something teleported in
from beyond the watch radius has not crossed anything and deliberately does not ripple.

## Rejected

- **Raising the alpha.** The first and most obvious answer, and it is the milky failure.
- **A second, bigger shell.** Two constant-alpha spheres is two of the same mistake.
- **Keeping the orphan `rendertype_subspace_shell` billboard.** A flat quad faking its own depth
  with a square root and summing eight terms into a `max()`. Nothing in Java had ever referenced
  it; it had been shipping in the resource pack as dead weight. Deleted.
- **`additiveOut`.** It returns `vec4(rgb * glow * opacity, glow * opacity)`, written for a
  `ONE/ONE` blend where the alpha channel is discarded. On a premultiplied blend that alpha is how
  much of the world the fragment removes, so the brightest parts of the wall would have cut a hole
  in the frame. `SubspaceShaderAssetsTest` forbids it by name.
- **Graduations hung east of the meridian.** They reached into the innermost pair of law slots,
  and the choice was between graduations too short to count and marks too narrow to read.
  Crossbars on the line fit in the corridor the band already leaves clear, and a crossbar is how a
  ruler is drawn anyway.
