# Sword Stance: the Array is chosen, not authored

> Supersedes the authoring half of `2026-09-22-sword-summoner-design.md`. The class, the rite, the
> four rungs, the blade entity, the Duskfall model and the four remaining actives all survive. What
> is removed is the lattice, the budget and the idea that a wielder builds a shape by hand.

## Why

The kit shipped with a thesis: *where you stand and where you point when you call each blade is the
build*. It is a good thesis and it produced a coherent object — 1296 lattice places, one conserved
measure of Edge, four projections of one shape, no per-blade state anywhere. Every rule was
arithmetic and every rule had a test.

And it is unplayable, in the precise sense that the person it was built for could not tell what any
of it did. Their words: *"its all very complicated … None of the abilities are actually
straightforward or clear."* The reason is structural, not cosmetic. **Every interesting thing the
class does is a consequence of a shape the wielder cannot see while they are building it.** Call the
Blade writes one bearing per press from the crosshair, so a formation is twelve presses spread over
a minute of aiming at empty air; half of those bearings are behind the wielder's head and
unreachable by the crosshair at all, which is why The Bearing had to exist; and the payoff — Loose
takes "every blade already facing your target", Below takes "every blade you aimed at the floor" —
is a projection of a thing that was never legible in the first place. The budget compounds it:
`reach * edge <= draw` is a third quantity in tension with the two the wielder is already tracking,
and its refusals (`TOO_DEAR`, `TOO_CLOSE`) are printed in the language of a system nobody has a
picture of.

So the wielder asked for the opposite arrangement, and it is the right one:

> *"swords flying around me with different stances, like above my shoulders and head facing where I
> look or facing vertical or sides behind me in circular pattern with unique sword movements etc.
> … Maybe change it to like "Sword Stance" where you choose a stance for swords for how they stand
> following you and one toggle ability to toggle activate the swords. Different stances do
> different things with abilities or swords doing things by themselves."*

**The formations are designed, not authored.** Six of them, each a named shape with its own
movement, its own autonomous behaviour and its own effect on the four actives. The wielder picks
one and can see it immediately, on themselves, in the world. That is the whole change, and
everything below follows from it.

## The object

```
   Call the Blade  (toggle)      the swords are out, or they are not
   Sword Stance    (hold)        which of the six formations they hold
   ---------------------------------------------------------------------
   The Keel        (press)       ride
   Loose           (press)       send them at what you aim at, in the stance's pattern
   Below           (press)       send them up through the ground, in the stance's pattern
   One Blade       (hold)        fuse everything present into one greatsword
```

Two nouns and one number. **You have N swords** (4 / 7 / 10 / 12 by rung). Some are *present* —
with you, in the stance. Some are *away* — flying, standing in something, or cutting their way home.
A sword that goes away comes back on a clock. That is the entire resource model, it is a small
integer, and it is visible in the world because you can literally count the swords orbiting your
head.

Gone: `Station` (the 24 × 9 × 6 lattice), `Projection`, `PlantResult`, `Settlement`, Edge as a
divisible quantity, `bill` / `draw` / `strain` / `maxEdge` / `maxStations`, the shed, the
overdraw collapse, and both halves of the authoring UI.

### The toggle — Call the Blade

The name survives because it is exactly right for what the skill now is: press and the swords are
there; press again and they are gone.

Off means **gone**: no `SwordArrayEntity`, no blades drawn, no interception, no upkeep, nothing on
the HUD. On summons every sword that is not away into the current stance, in one motion, and costs
mana once. This is the only state an opponent can read at a glance, and that is deliberate — a
Sword Summoner with their steel out is visibly armed.

A press while any sword is away sheathes only the present ones; the away ones dissolve where they
stand rather than flying home, because a dismissal is a dismissal.

### The stance — Sword Stance

Hold the key. The six formations appear in a row, **each drawn as a small diagram of itself** — a
top-down and side-on pictogram showing where the swords actually sit relative to a mark standing
for the wielder. Scroll (or the mouse buttons) walks the focus; release takes the stance. Locked
stances are drawn dim with their rung named.

The picker draws the formation because the complaint was that nothing is clear, and the one thing a
name cannot say is *shape*. Frameless the way every in-game overlay in this mod is — one scrim over
the world at `0x8C`, drop shadows on every string, `HudLayers` standing the sigil down — and the
three readings on three independent channels the loadout switcher settled: **which** stance is
focused is its position on the row plus the only white text on screen, **what is worn** is gold with
a hairline under it, and **whether a release will do anything** is the one reserved line at the
bottom (a locked stance names the rung it wants).

Taking a new stance re-flies the present swords into the new shape over ~10 ticks. It costs nothing
and has a short cooldown, because a stance is a posture and not an attack.

### The six stances

Every formation is a pure function `Formation.place(stance, index, count, phase) -> Slot`, where a
`Slot` is a frame-local position and a frame-local unit direction the blade points. Frame-local
axes are Minecraft's own, stated once in `ArrayPose`: **+Z forward, +Y up, +X to the wielder's
left**. `phase` is a continuous tick count, so the client interpolates and the server samples the
same function at the same argument — the Gravemoons discipline the old kit already had, kept
because it is the reason twelve blade positions cost zero bytes on the wire.

Two properties are separate and must stay separate, because collapsing them is the obvious mistake:

- **`Anchor`** — whether the formation's *position* follows the wielder's look (`LOOK`) or only
  their body yaw (`BODY`). A Crown anchored to the look swings under your feet when you look down.
- **`Facing`** — where the blades *point*: `LOOK`, `OUTWARD` from the formation's own axis, `DOWN`,
  or `ALONG` the slot's own sweep.

| Stance | Anchor | Facing | Shape | Watch | Pattern | Rung |
|---|---|---|---|---|---|---|
| **Guard** | BODY | LOOK | a shallow arc across the shoulders and behind the head, points forward, slow bob | `INTERCEPT` | `LINE` | Summoner |
| **Vanguard** | LOOK | LOOK | a tight cluster in front at eye level, stacked in depth, drifting forward | `STAB` | `COLUMN` | Summoner |
| **Crown** | BODY | OUTWARD | a level ring above the head, points outward, turning | `SHEAR` | `RING` | Rider |
| **Wings** | BODY | ALONG | two swept-back fans at the shoulders, angled up, slow flap | `GLIDE` | `FAN` | Rider |
| **Coil** | BODY | OUTWARD | a fast level orbit at the waist, points outward | `SHRED` | `SPRAY` | Saint |
| **Rain** | BODY | DOWN | high overhead on a scattered disc, points straight down, drifting | `DROP` | `FALL` | Saint |

The scatter in Rain is a golden-angle spiral (`i * 2.39996`, `r = R * sqrt((i + 0.5) / count)`), not
a random number — it looks scattered, it is deterministic, and both sides compute it.

### What the swords do by themselves — `Watch`

This is the half the wielder asked for by name (*"swords doing things by themselves"*) and it is
what makes a stance a decision rather than a skin. One behaviour per stance, each with an interval
and a price of **one sword going away**:

| Watch | What it does |
|---|---|
| `INTERCEPT` | A closing projectile or an incoming melee blow within the guarded arc is met by the nearest present sword: the projectile is reversed (`Projectile.deflect`, the wielder's afterwards), a blow is reduced. That sword goes away. This is Ward of the Array, moved off the lattice and onto the formation. |
| `STAB` | A hostile the wielder has held in the crosshair for `STAB_FOCUS` ticks is darted at by one present sword, which returns. |
| `SHEAR` | Everything hostile inside the ring's radius is cut on an interval. Costs no sword — the ring is the attack. |
| `GLIDE` | Fall damage is cancelled and descent slowed while the fans are out; a melee attack by the wielder is followed by a scissor from the two innermost fan swords. |
| `SHRED` | Anything that touches the wielder is cut and thrown back. Costs no sword. |
| `DROP` | A body the wielder damages has one overhead sword fall on it. |

`INTERCEPT`, `STAB` and `DROP` spend a sword; `SHEAR`, `SHRED` and `GLIDE` do not, and are priced
lower for it. The Sword God rung halves every interval and halves the return clock.

### What a stance does to the four actives — `Pattern`

One pattern per stance, read by both **Loose** (forward, from the wielder) and **Below** (upward,
from under the target). One implementation each, six of them, all pure geometry:

| Pattern | Loose | Below |
|---|---|---|
| `LINE` | abreast across the aim, a wall that plants as cover | a fence across the target's path |
| `COLUMN` | one behind another down the aim line, deep and narrow | a single deep skewer |
| `RING` | outward in every direction at once | a ring round the target |
| `FAN` | two arcs converging on the aim point | two flanking lines |
| `SPRAY` | wide, short and fast | a tight cluster under the target |
| `FALL` | from above onto the aim point | from above rather than below |

Neither skill takes a cap. **How many swords are present is the cap**, which is the one good idea
the old `Projection` had and is kept: a wielder who has just spent six swords Looses with six, and
the number is on screen the whole time.

**One Blade** fuses however many are present; **The Keel** needs one present sword and takes it.

## The rungs

Each rung is still one clause, and every clause is now about the swords rather than about a budget.

| Rung | Swords | Clause |
|---|---|---|
| Sword Summoner | 4 | You have swords, a stance, and they act on their own. Guard and Vanguard. |
| Sword Rider | 7 | The frame comes off your body — the Keel. Crown and Wings. |
| Sword Saint | 10 | Coil and Rain, and every stance is open. |
| Sword God | 12 | Relentless: every Watch interval halved, every return clock halved. |

`SwordRules` becomes `record SwordRules(int swords, int stances, boolean worldOrigin, boolean
relentless)`, where `stances` is how many of the six, in declaration order, are unlocked (2 / 4 /
6 / 6). Class grants are unchanged apart from the renamed skill.

## Persistence and sync

`SwordArray` keeps its name and its place on `PlayerMagicState`, and holds two things: the chosen
stance and whether the steel is out. Saved as the same `IntArrayTag` with `SAVE_VERSION` bumped to
**2**, so the existing rule — *a version this build does not know is dropped whole rather than
guessed at* — migrates every v1 save for free, to the default stance with the swords sheathed.

Everything else stays where it was: the present/away bookkeeping, the return clocks and the blades
in the world live on `SwordService`, keyed by UUID, never saved, dropped on logout and on a change
of dimension. Same decision as `PileService`, same reasons.

`SwordArrayEntity` keeps its single-entity-for-N-swords arrangement and its zero new
`EntityDataAccessor`s. `EXTRA` becomes the **present mask** (bit *i* set means sword *i* is with
you), `DATA` carries the stance ordinal and the bind, `VALUE` the frame scale and `DIR` the frame
facing exactly as now. The positions remain arithmetic on both sides: the client is told the stance,
the mask and the frame, and runs `Formation.place` itself.

## What is deleted

```
magic/sword/Station.java              magic/sword/Projection.java
magic/sword/PlantResult.java          magic/sword/Settlement.java
client/SwordBearingOverlay.java       client/SwordBearingLayout.java
client/SwordBearingInput.java         network/PullStationsPayload.java
magic/skill/sword/TheBearingSkill.java
src/test/.../ProjectionTest.java      src/test/.../SettleTest.java
src/test/.../SwordBillTest.java       src/test/.../SwordBearingLayoutTest.java
src/test/.../PullStationsPayloadTest.java
```

`CallTheBladeSkill` keeps its file, its id and its name and becomes the toggle. The skill id
`magical:the_bearing` is retired and replaced by `magical:sword_stance`; the old lang keys go on
`HudLangKeysTest.RETIRED` and the five `message.magical.sword_*` refusals about bearings and draw go
with them.

## What is added

```
magic/sword/stance/SwordStance.java      the six, with anchor, facing, watch, pattern, rung
magic/sword/stance/Slot.java             a frame-local position and unit direction
magic/sword/stance/Formation.java        place(stance, index, count, phase) -> Slot   [pure]
magic/sword/stance/Watch.java            the six autonomous behaviours and their intervals
magic/sword/stance/Pattern.java          the six volley geometries                     [pure]
magic/sword/stance/StanceWatchService.java   the Watch half, server-side
magic/skill/sword/SwordStanceSkill.java  the hold that opens the picker
client/SwordStanceOverlay.java           the picker
client/SwordStanceLayout.java            its geometry                                  [pure]
client/SwordStanceInput.java             the hold arm
network/SetStancePayload.java            one ordinal, re-checked server-side
```

`magic/sword/stance/` is pure but for nothing at all — `Formation`, `Pattern`, `Slot`, `Watch` and
`SwordStance` have no Minecraft in them, the same split as `Pile`/`PileService` and
`Weave`/`LevelCausalWorld`, for the same reason: a formation that is wrong is a *silent mirror*, and
the only way to catch one is an exact test with no world under it.

## Tests

The old kit's lesson — *a green suite certified an invisible wall* — applies directly, so the
formation tests measure the view the player actually has rather than the property that is easy to
assert.

**`FormationTest`** — for all six stances × every count 1..12 × a spread of phases:
- every slot's direction is a unit vector (a zero direction is a blade drawn with no rotation, which
  looks like a bug in the model and reads as a bug in the export);
- no two slots are closer than `MIN_SEPARATION` (1.1 blocks — Duskfall is 1.96 blocks long, so
  anything tighter is two swords occupying one another);
- no slot is inside the wielder: nothing within 0.55 of the frame origin, and nothing within 0.45 of
  the eye point at `+0.72`;
- **nothing occludes the crosshair**: no slot within 8 degrees of the forward axis closer than 2.5
  blocks. This is the Bearing's own 73-degree cast circle stated as a rule, and Vanguard is the
  stance that will break it first;
- every slot is inside a 4-block box, so a formation cannot quietly reach further than the renderer
  claims;
- `place` is a pure function of its arguments — called twice with the same arguments it answers
  equal slots, and nothing about it reads a clock;
- **each stance is distinguishable from every other**: at a common count and phase, the two slot
  sets differ by more than `1.0` in Hausdorff distance. Six formations that look alike is the exact
  failure this whole redesign exists to prevent, and it is the one thing no other test would catch.

**`PatternTest`** — every pattern at every count: the aim points are distinct, all within the
skill's stated range of the target, and `COLUMN` is deeper than it is wide while `LINE` is wider
than it is deep (the two that would be identical if a sign were wrong).

**`SwordStanceLayoutTest`** — the frameless-overlay checklist the other three layouts already pass:
nothing overlaps, every row is inside the block, every hit-test answers to its own centre and to
nothing in the gaps, the text limit is a function of `guiWidth`, and nothing reaches the crosshair.

**`SwordArrayTest`** — reduced to what is left: the stance round-trips through save/load, a v1 tag
loads as the default stance sheathed, an out-of-range ordinal in a hand-edited save clamps rather
than throwing, and a stance above the rung's `stances` is refused.

**`StanceGameTests`** — the two halves that only a level can answer: `INTERCEPT` reverses a real
arrow and spends exactly one sword, and the toggle leaves **no entity at all** behind when it is
switched off (the user's own requirement, and the kind of thing that is silently wrong forever).

## Captures

`/magical sword stance <name>` sets it, `/magical sword draw|sheathe` works the toggle, and
`/magical sword away <n>` forces *n* swords away so the present count can be photographed at
something other than full. Judge at **midnight** as with every additive effect in this mod, and
judge the formation in **third person** (`-PautoCamera=third_back`) because five of the six stances
are behind or above the wielder and first person shows none of them — then check Vanguard and Guard
in first person, because those two are the ones the crosshair rule is about.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCamera=third_back \
  -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock sword_god;magical unlockall;time set midnight;tp @s ~ ~ ~ 0 0;120:magical sword draw;160:magical sword stance guard;200:magical sword stance vanguard;240:magical sword stance crown;280:magical sword stance wings;320:magical sword stance coil;360:magical sword stance rain" \
  -PautoScreenshot=130,175,215,255,295,335,375 -PautoExit
```

The picker is a hold overlay, so `-PautoHold` releases at the first screenshot and the named
`-PautoClick` actions have to drive it instead, exactly as the Manipulate Space selector's capture
does.
