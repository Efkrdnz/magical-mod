# The Sword Summoner — the Array, and the four rungs to Sword God

> A hidden class line on the empty pyramid layer −3, registered as a new school (`MagicSchool.SWORD`).
> Synthesised from four competing designs. The core is **the Array** (bearings on a movable frame);
> the **Edge** (a conserved integer partitioned across those bearings) is taken from the Sheaf; the
> **bill** (a budget the frame's scale can inflate, and the enforcement pass that *is* the ultimate)
> is taken from the Skein; **spend-and-walk-back** recovery is taken from the Sheaf; the **visible
> thread** that makes the whole resource public is taken from the Skein. The Mound's ripening clock
> is deliberately **not** taken, and §11 says why.

---

## 1. The shape

> **A Sword Summoner owns an Array: a set of authored bearings, rigidly bound to one movable frame,
> with a conserved quantity of Edge partitioned across them. A sword is never anywhere but at its
> station. Every verb in the class is a move of the frame or a pure projection of the bearing set.**

### 1.1 The authored half — a Station

```java
/** yaw 0..23 (15 degrees each), pitch -4..+4 (18 degrees each), reach 1..6 blocks, edge 1..36. */
public record Station(int yaw, int pitch, int reach, int edge) {
    public int packed();                         // yaw<<13 | (pitch+4)<<9 | (reach-1)<<6 | edge
    public static Station unpack(int packed);    // total: clamps rather than throwing
}
```

1296 distinct places, 18 bits with the Edge. `yaw` is a compass bearing about the frame's up axis;
`pitch` is elevation, so `+4` is 72 degrees above the frame plane and `-4` is 72 below. Nothing in
the record is an enum, so nothing in it can be renumbered by a future edit — but `SAVE_VERSION`
rides at index 0 of the tag anyway, and `theLatticeConstantsAreTheOnesTheSaveWasWrittenAgainst`
pins the packing arithmetic.

### 1.2 The structure

```java
public final class SwordArray {
    private final List<Station> stations;   // insertion order, and insertion order is load-bearing
    private int whole;                      // 8 / 16 / 26 / 36, set by the class rung
    private SwordRules rules;               // four booleans and four caps, set by the class rung

    public int bill();                      // sum over stations of reach * edge
    public int bound();                     // sum over stations of edge  ("metal in the air")
    public int loose(int spent);            // whole - bound() - spent
    public PlantResult plant(Station s, int spent);
    public boolean pull(int index);         // returns the station's edge to loose
    public IntArrayTag save();
    public void load(IntArrayTag tag);      // total, never throws, re-runs plant()'s rules
}

public record SwordRules(int maxStations, int draw, int maxEdge,
                         boolean worldOrigin, boolean freeScale,
                         boolean coincidence, boolean overdraw) { }

public enum PlantResult { PLANTED, TOPPED_UP, FULL, TOO_CLOSE, TOO_DEAR, NO_EDGE, OUT_OF_REACH }
```

### 1.3 The live half — a Frame

```java
public record Frame(double x, double y, double z, float yaw, float pitch, float scale) { }
public enum Bind { HELD, SET, RIDDEN, BOUND, SUNK }
```

`HELD` — origin is the wielder's body centre, facing is their look, scale 1. `SET` — origin and
facing frozen where they were. `RIDDEN` — origin moves along the wielder's look and the wielder is
pinned to it. `BOUND` — origin is a living body and scale is driven by its distance. `SUNK` — origin
is a point recorded once, with every pitch reflected.

### 1.4 The invariants, and every one of them is pinned on an exact value

1. **Separation.** `max(circularYawSteps(a,b), abs(a.pitch - b.pitch)) >= SEPARATION_MIN` = 2, at
   every reach, unless `rules.coincidence`. Two blades may not share a bearing. Two yaw steps is 30
   degrees and `360 / 30 = 12`, so `MAX_STATIONS = 12` and `SEPARATION_MIN = 2` are the same fact:
   twelve stations is exactly one full ring and is the densest legal packing.
2. **Conservation.** `bound() + spent + loose == whole`, always, and `loose >= 0`. Nothing creates
   Edge and nothing destroys it. Edge is in exactly one of three places: bound into a station,
   spent and lying in the world, or loose in the wielder.
3. **The bill.** `bill() = Σ reach_i × edge_i <= rules.draw`, enforced by `plant` (which answers
   `TOO_DEAR`) and again by `load`. This is a build constraint on the *authored* shape, so the
   pure core can check it with no world at all.
4. **The strain.** `strain = max(0, ceil(bill() * frame.scale) - draw)`. Scale is 1 for every bind
   but `BOUND`, where it is `max(1.0, distance(origin, wielder) / BIND_REST)` with
   `BIND_REST = 8.0`. So *your opponent running away inflates your bill*, and it is the only number
   in the structure the wielder does not own.
5. **The settle.** `SwordArray.settle(int strain, boolean overdraw) -> Settlement`. Without
   `overdraw`, while `strain > 0` it takes the manned station with the largest `reach × edge` — ties
   broken by **lowest slot index, never by map order** — and sheds it: the blade cuts the line home
   to the wielder and its Edge returns. Repeat until the bill fits. With `overdraw` (Sword God)
   nothing sheds, the strain stands, and at `strain >= draw` every station sheds in one tick.
6. **The collapse.** `frame.scale -> 0` drives the bill to zero, so **fusing is the enforcement pass
   run at its limit**: One Blade is `settle()` with the bill collapsed rather than the budget
   raised, and it is the same arithmetic that has been shedding blades out of an over-stretched
   Array since the wielder first bound a target.

### 1.5 Why this is not one of the mod's structures in a coat

| Already owned | Why the Array is not it |
|---|---|
| **Pile** (sparse site → stress, toppling) | The Pile has no geometry and no owner-relative frame; its elements are places in the world that the wielder does not choose the shape of. The Array's elements are *directions*, and they move rigidly with one transform. The Pile's cascade is local and topological; the Array's settle is a global knapsack eviction with a total order. |
| **Grimoire** (a deck read N-at-a-time) | No position at all, and its composition is a sequence. The Array has no order of reading; it has a shape and four ways of looking at it. |
| **Weave** (a directed graph of typed pins) | A topology with no metric. The Array is a metric with no topology: it has angles and distances and no edges between its elements. |
| **Subspace** (a region, one law per category) | A fixed centre, a boundary, and a per-tick loop over contents. The Array has no volume, no boundary and never enumerates the world; it enumerates itself. |
| **Blood** (one currency in three named places) | The Edge *is* a currency in three places, and this is the closest resemblance. The difference is that Blood's places are named and fixed — the Vessel, a pool, the body — while the Edge's second place is *twelve authored bearings with reaches*, so where the currency sits is itself the build. Blood asks how much; the Array asks how much, pointing where, how far out. |
| **BloodShape** (an authored, saved, pure geometry) | The one the other proposals missed. `magic/blood/shape/BloodShape` is already a player-authored saved geometry in absolute block-sixteenths with a canvas editor and the same server-and-painter-share-the-maths discipline. It is a **stamp**: no frame, no subsets, no live addressable elements, no quantity partitioned across it. The Array is a stamp with an origin, a facing, a scale, four projections and a conserved resource in it. That is a genuine generalisation, not a rename, and the design doc should claim exactly that much and no more. |

The generative test: strip the word *sword* and count how many verbs fall out of the object rather
than being priced by it. Write a station, unbind the frame, re-anchor the origin to a body and take
the forward half, re-anchor to a point and reflect the pitch and take the lower half, ask which
bearing covers an incoming line, drive the scale to zero. Six verbs, six operations, **no per-blade
state machine and no mode enum anywhere in the kit.** A blade is a bearing and a bit.

---

## 2. The pure core

Everything in `magic/sword/` below has **no Minecraft import** except `IntArrayTag` in `save`/`load`,
exactly as `Fracture` and `Grimoire` already do.

```java
// magic/sword/Station.java
public record Station(int yaw, int pitch, int reach, int edge) {
    public static final int YAW_STEPS = 24, PITCH_MIN = -4, PITCH_MAX = 4, REACH_MIN = 1, REACH_MAX = 6;
    public int packed();
    public static Station unpack(int packed);
    public static int circularYawSteps(int a, int b);         // min(|a-b|, 24-|a-b|)
    public int separationFrom(Station other);                  // max(yaw steps, |pitch delta|)
    public double[] unitBearing();                             // {x,y,z}, frame-local, length 1
    public Station withEdge(int edge);
}

// magic/sword/SwordRules.java
public record SwordRules(int maxStations, int draw, int maxEdge, int whole,
                         boolean worldOrigin, boolean freeScale,
                         boolean coincidence, boolean overdraw) {
    public static final SwordRules SUMMONER, RIDER, SAINT, GOD;   // the four rungs, §3.1
    public static SwordRules forRung(int rung);
}

// magic/sword/PlantResult.java   enum, §1.2
// magic/sword/Bind.java          enum, §1.3
// magic/sword/Frame.java         record, §1.3, plus:
//     public Frame withScale(float s); public Frame withOrigin(double x,double y,double z);

// magic/sword/SwordArray.java    §1.2, plus:
public int size();
public Station station(int index);
public List<Station> stations();
public void copyFrom(SwordArray other);
public void clear();
public Settlement settle(int strain, boolean overdraw);

// magic/sword/Settlement.java
public record Settlement(int[] shedSlots, int strainLeft) { }

// magic/sword/ArrayPose.java     the one arithmetic BOTH SIDES RUN
public static double[] worldOffset(Station s, Frame f);   // {dx,dy,dz} from the frame origin
public static double[] worldBearing(Station s, Frame f);  // unit, frame-rotated
public static float bladeYaw(Station s, Frame f);
public static float bladePitch(Station s, Frame f);
public static float boundScale(double distance);          // max(1, distance / 8.0)

// magic/sword/Projection.java    pure, and this is why six verbs and not five skills
public static int[] forward(SwordArray a, Frame f, double[] look);  // dot > 0, descending, no cap
public static int[] below(SwordArray a);                            // pitch < 0, ascending pitch
public static int covers(SwordArray a, Frame f, double[] incoming); // within 30 degrees, else -1
public static List<Station> mirror(SwordArray a);                   // yaw+12, -pitch, edge/2 floor min 1

// magic/sword/SwordMath.java     every damage number in the kit, in one file
public static double bladeDamage(int edge, int strain);       // 2.0 + 2.0*edge + 0.25*strain, cap +24 on the strain term
public static double mirrorDamage(int edge, int strain);      // max(1.0, bladeDamage(edge/2, strain))
public static double shedDamage(double lineLength);           // min(24.0, 3.0 + 0.35*lineLength)
public static double wardAbsorb(int edge);                    // 2.0 + 1.5*edge
public static double oneBladeSlash(int totalEdge, int strain);// min(48.0, 6.0 + 1.5*totalEdge + 0.25*strain)
public static double oneBladeBlast(int totalEdge, int strain);// min(72.0, oneBladeSlash(...) * 1.5)
public static double oneBladeReach(int totalEdge);            // 2.5 + 0.18*totalEdge
public static double oneBladeArc(int totalEdge);              // min(160.0, 60.0 + 3.0*totalEdge), degrees
public static int bonusMaxMana(int mannedStations);           // min(36, 3*mannedStations)
```

### The Level-facing adapter

```java
// magic/sword/SwordWorld.java — the interface, and it asks for as little as possible
public interface SwordWorld {
    double distanceToBound(int entityId, double[] wielder);   // -1 when the body is gone
    boolean bodyAlive(int entityId);
    boolean solidAt(double x, double y, double z);
    double surfaceBelow(double x, double y, double z, int searchBlocks);  // NaN when none
    long now();
}
```

Note what is **not** in it: no per-blade distance query. `settle()` takes one `int strain`, which the
adapter computed from one scalar (`boundScale`). That single decision is why every invariant above
is testable in arithmetic with no world mock at all — the Skein's version of this needed one per
blade and paid for it in every test.

`magic/sword/LevelSwordWorld.java` is **the only class in the package that knows what a `Level` is**,
and it answers in a stable order (entity id ascending) because a tie in a farthest-first shed must
be deterministic. `magic/sword/SwordService.java` is the adapter: one live half per wielder keyed by
UUID **and dimension**, the slow-tick settle, the entity spawning, and the single `payFor`.

### Which class pins which invariant

| Invariant | Pinned by |
|---|---|
| separation, `MAX_STATIONS == 12` is one full ring | `SwordArrayTest.twelveIsExactlyAFullRing`, `.aStationMayNotShareABearing` |
| conservation `bound + spent + loose == whole` | `SwordArrayTest.edgeIsNeverCreatedAndNeverDestroyed` |
| the bill and `TOO_DEAR` | `SwordArrayTest.aPlantThatOverrunsTheDrawIsRefusedByName` |
| the shed order and its tie-break | `SettleTest.theFarthestHeaviestStationShedsFirstAndTiesGoToTheLowestSlot` |
| collapse == settle at the limit | `SettleTest.scaleZeroDrivesTheBillToZero` |
| the packing and `SAVE_VERSION` | `SwordArrayTest.theLatticeConstantsAreTheOnesTheSaveWasWrittenAgainst` |
| projections | `ProjectionTest` (forward ordering, below's pitch filter, covers' 30-degree cone, mirror's dedupe and halving) |
| pose arithmetic both sides run | `ArrayPoseTest.aStationIsWhereBothSidesSayItIs` |
| every damage number | `SwordMathTest` |

---

## 3. The chain

Four rungs, tiers 0 to 3. **Not five.** A tier-4 node is never laid out by `ClassTreeLayout` (its
loop runs `tier <= 3` over length-4 `RING`/`SPAN` arrays), is never granted by
`/magical class unlockall` (`MagicalCommands.java:789` loops `tier <= 3`, silently), and forces
hand-placement forever. The Skein's five-rung chain was the better *design* and the jury said so;
it is not worth three permanent surfaces in the codebase. Four rungs get the same job done because
each one flips exactly one rule of the structure and nothing else is the headline.

### 3.1 The rungs

| id | display | tier | xpCost | whole | stations | draw | maxEdge |
|---|---|---|---|---|---|---|---|
| `magical:sword_summoner` | Sword Summoner | 0 | 0 | 8 | 4 | 24 | 3 |
| `magical:sword_rider` | Sword Rider | 1 | 40 | 16 | 7 | 40 | 5 |
| `magical:sword_saint` | Sword Saint | 2 | 110 | 26 | 10 | 64 | 12 |
| `magical:sword_god` | Sword God | 3 | 220 | 36 | 12 | 84 | 36 |

**Sword Summoner** — the Array exists. `worldOrigin` false, `freeScale` false, `coincidence` false,
`overdraw` false: the frame is your body centre and your look, at scale 1, except while a Loose is
resolving. Grants `call_the_blade`, `the_bearing`, `loose`, and the passive `sword_heart`. Four
stations at two Edge and reach three is `4 × 3 × 2 = 24` — *exactly* the draw, so the tension is
real in the first minute and the game never says so in words.

**Sword Rider** — `worldOrigin = true`: **the origin unbinds from your body.** The frame may be
frozen in the world or may carry you, and it may be sunk under a recorded point. Two skills fall out
and neither is a new mechanism: `the_keel` and `below`. Grants the passive `ward_of_the_array`,
because a shape that stays where you left it is the first shape worth calling a guard. 7 stations,
40 draw.

**Sword Saint** — `coincidence = true` and `freeScale = true`, and these are one rung because they
are the same permission: *the shape may stop being the shape you wrote.* Coincidence is fusion —
`n` stations on one bearing cost one bearing's separation, and their Edge may be merged — so
`one_blade` only becomes arithmetically legal here. Free scale is the live bill: `BOUND` now drives
scale by distance, so from this rung on **your opponent's footwork spends your budget** and an
over-stretched Array sheds itself, farthest-first, cutting home. Grants the passive `returning`.
10 stations, 64 draw.

**Sword God** — `overdraw = true`. This rung **removes a rule and grants no new active**, which is
the only way an apex survives in a mod that already has five Authorities. Exceeding the draw is
legal: every point of strain adds 0.25 to every blade's damage (capped at +24.0), the wielder
bleeds 1.0 of `magical:sword_strain` every 40 ticks per full 20 strain, the Array hums audibly at
32 blocks, and at `strain >= draw` every station sheds in one tick — twelve converging cuts, no
cooldown, not your decision. Grants the passive `mirror_of_the_array`. 12 stations, 84 draw.

The whole ladder is 370 class XP, pooled at `sword_summoner` the way every tree pools at its base.

### 3.2 How it is hidden

`MagicalClassDefinition` gains one component, `boolean secret`, with a **9-argument delegating
constructor** so all 52 existing registrations and all four registration helpers compile untouched.
`MagicalClasses` gains:

```java
/**
 * Visibility cascades from the ROOT, not per node: taking Sword Summoner reveals the whole chain
 * at once. This is deliberately NOT "hidden until owned", which would reveal the ladder one rung
 * at a time and turn the reveal into a drip.
 */
public static boolean isVisible(MagicalClassDefinition d, PlayerMagicState state) {
    return !d.secret() || state.hasClass(MagicalClasses.baseOf(d.id()));
}
```

`startingRoots()` and `isStartingRoot()` change from the hard-coded `!SPELL_CREATOR.equals(id)` to
`!definition.secret() && !SPELL_CREATOR.equals(id)`, so there is one property rather than two ids.

**Sword Summoner is a ROOT that is not a starting root** — the Spell Creator precedent. That single
decision buys, for free: exemption from `everyBaseHasFourDisciplinesThreeMasteriesAndAtMostThreeApexes`,
exemption from `everyApexIsReachableFromMoreThanOneBranch`, absence from `ClassSelectMenu.choices()`
and the first-spawn chooser, absence from `grantTestClassXp`, and **no re-sectoring of the five
existing trees** (a sixth *starting* base turns `360/5` into `360/6` and moves four of five trees for
every player, with a green build, because the overlap test passes at six bases by 3.6 graph units).

It is also the only shape safe against the forged packet: `MagicalNetwork.java:165` hands an
arbitrary client-supplied `ResourceLocation` to `evolveClass`, whose gates are "some parent is owned"
and "the pool pays". `evolveClass` returns early on `isBase()`, so a secret **base** cannot be taken
by a hand-built packet, and the three rungs above it are protected because their parent is itself
secret. **Never graft a secret rung onto a visible tree.**

### 3.3 Every enumeration point that must respect the flag

| # | Site | What happens without the gate |
|---|---|---|
| 1 | `ClassTreeScreen.drawNodes` :194 — `continue` when `!isVisible` | **The requirement.** The radial screen has no filter of any kind today; a new class is a named, hoverable card with a tooltip listing its description, XP cost and every reward from the moment the mod loads. |
| 2 | `ClassTreeScreen.drawEdgePass` :137 — same | Four edges to nowhere. |
| 3 | `DungeonTowerService.addClassRewards` :243 — skip secret roots | **The accident.** The tower offers a `CLASS_UNLOCK` wish for every unowned root with `definition.nameKey()` printed in chat. A player gets "Sword Summoner" as one of three choices on floor 2. |
| 4 | `PlayerMagicState.hasAnyRootClass()` :1002 — exclude secret roots | **The softlock.** It iterates `roots()`, so owning a secret root satisfies onboarding: `:1998` stops reopening the starting-class chooser and `:1033` makes `chooseStartingClass` refuse forever, silently. |
| 5 | `MagicalCommands` :730 (`class unlock`), :746 (`class evolve`), :762 (`class addxp`) | Tab-completion names the class. Permission 2, so this is polish — but in single player the player is an operator. |
| 6 | `MagicalClasses.classGranting` :264 — skip secret classes when building `PASSIVE_SOURCES` | The codex Passives tab's "From %s" line names the class. Moot here, because §4.2 registers all four passives with `forbiddenPassive()` rather than `classPassive()` — but gate it anyway, so the next passive added cannot leak. |
| 7 | `MagicalCommands` :783 `class unlockall` — **deliberately left granting it**, with a comment saying so | Captures need it. Say so in the code or the next reader "fixes" it. |

The discovery rite (§3.4) additionally refuses a player who has not chosen a starting class, so #4
is guarded twice.

**Write this in the design doc and do not soften it:** `MagicalClasses` is common code imported by
`ClassTreeScreen`, and all `class.magical.*` strings ship in the client's `en_us.json` in plaintext.
**"Hidden" means "the stock UI does not draw it", never "the client cannot know."** Nothing in this
design's payoff depends on secrecy holding against a text editor; the value is the rite, not the
ignorance.

### 3.4 How it is discovered — the Answering

No command, no worldgen, no structure, no advancement, no new block, no new item, no new loot table.

**The breadcrumb, live for every player from the day the mod loads:** a sword sneak-dropped at night
under open sky does not tumble. It hangs for 8 ticks with a thin edge of light down its blade before
it falls. One short-lived entity, and it happens to anyone who has ever fumbled Ctrl+Q outdoors.

**The rite.** Sneak-drop four swords (`#minecraft:swords`, or any `magical:forged_weapon`) such that
when the fourth comes to rest:

- all four were thrown by the same player, within `RITE_WINDOW = 400` ticks of the first;
- every one has `level.canSeeSky` above it, `!level.isDay()`, no rain;
- every pair is at least `RITE_MIN_SEPARATION = 2.0` blocks apart;
- all four lie within `RITE_RADIUS = 8.0` blocks of their centroid;
- the thrower is within `RITE_CENTRE = 3.0` blocks of that centroid;
- none has been picked back up;
- the thrower has already chosen a starting class and does not already hold Sword Summoner.

The four lift 1.2 blocks, turn point-down, and hang at the four bearings they actually occupied
relative to the thrower, over 40 ticks. The items are consumed, `state.unlockClass(player,
SWORD_SUMMONER)` fires, and **those four bearings become the wielder's first four stations**,
quantised to the lattice with 2 Edge each — which is `4 × reach × 2` and is why `RITE_RADIUS` is 8
and reach is clamped to 3 on the rite: `4 × 3 × 2 = 24 = draw`, exactly full.

That is the whole point of choosing this rite over the other three proposed. **The unlock ceremony
is the first Call the Blade, four times, performed with your feet** — you planted four bearings by
walking and dropping, and the game turns exactly those bearings into your Array. A player who has
seen it happen has already been taught what the kit is, and they start the class with a build they
made rather than a default.

Refusals are silent: the swords simply fall, and nothing is consumed. Implementation is one
`@EventBusSubscriber` on `ItemTossEvent` that early-outs on its first field read (is it a sword?),
a `SwordRiteService` keyed by UUID and never saved with `forget(UUID)` wired beside
`PileService.forget` in `MagicGameplayEvents.onPlayerLogout`, and a sweep every 20 ticks on
`ServerTickEvent.Post` that only examines players holding four or more live entries. Death drops do
not fire `ItemTossEvent`, which is correct — the rite must be a thing you chose to do.

### 3.5 Where the four nodes sit in the Paths of Power

All four are **hand-placed**, because `ClassTreeTest:102` asserts
`MagicalClasses.all().size() == ClassTreeLayout.nodes().size()` — you cannot hide a class by
refusing to lay it out. Bearing **−54 degrees** (the empty wedge between the Blacksmith sector at
−90 and its neighbour at −18, whose tier-1 spans reach only −68 and −40), radii **210 / 380 / 470 /
545**:

```java
// The Sword Summoner line is a hidden chain rather than a starting tree: it takes the wedge
// between two sectors, inside the outer ring, so extent() does not grow. See ClassTreeExtentTest.
place(MagicalClasses.SWORD_SUMMONER, 210.0F, -54.0F, 0);
place(MagicalClasses.SWORD_RIDER,    380.0F, -54.0F, 1);
place(MagicalClasses.SWORD_SAINT,    470.0F, -54.0F, 2);
place(MagicalClasses.SWORD_GOD,      545.0F, -54.0F, 3);
```

Coordinates: (123.4, −169.9), (223.4, −307.4), (276.3, −380.2), (320.4, −440.9).

**The trap three of the four proposals fell into, and it is the most dangerous thing in this
document.** `ClassTreeLayout.extent()` is `max(|x|, |y|)` over *every* node and it feeds
`ClassTreeScreen.fitZoom()` at :67-71, which is `min(w, h) / ((extent + NODE_WIDTH) * 2)` clamped to
`MIN_ZOOM = 0.28`. And `ClassTreeScreen:227` draws a node's **name** only at `zoom >= 0.40F`. The
current default fit is already about 0.427 — the Paths of Power is **one node-placement away from
losing every label in the game, for every player, with a green build.** A node at radius 660 drops
it to 0.26. The four placements above have `|x| <= 320.4` and `|y| <= 440.9`, both inside the
existing envelope (the outer ring at 545 already yields coordinates above 490 in x and 540 in y), so
`extent()` does not move at all. `ClassTreeExtentTest` (§8) pins that, because nothing else will.

Overlap (the test fails only when `dx < 76` **and** `dy < 22`): the tightest pair is (223.4, −307.4)
against the tier-1 node at −68 degrees on `RING[1] = 330`, at (123.6, −306.0) — `dx = 99.8`, clear
of 76 by 24. The next tightest is the same node against the tier-1 at −40 degrees, (252.8, −212.1) —
`dy = 95.3`, clear of 22 by 73.

**Register all four at the END of the `MagicalClasses` static block**, after the Spell Creator pair.
Class button ids are registration index on both sides (`ClassTreeMenu.nodes()` and
`MagicPyramidMenu:264-275` both walk `all()` positionally), so inserting anywhere else renumbers
every class button after them and nothing checks it. Four new classes takes the registry from 52 to
56; the real dispatch ceiling is 100, not the documented 99, and §8 adds the band that pins it.

---

## 4. The kit

Six actives, four passives. There are four cast keys (Z/X/C/V) and the loadout switcher (B) swaps
between four loadouts; six actives is normal here (Blood ships seven, Eldritch six).

All six skills register on tier **−3**, school `MagicSchool.SWORD`, attribute `MagicAttribute.SWORD`,
and every id goes into `CLASS_REWARD_SKILLS` and into **no other classifier set**. Not `SUB_SKILLS`
(they would vanish from the codex and become un-unlockable by command); not `AUTHORITY_SKILLS`
(`unlockall` would stop granting them and every capture in §9 would break).

**Every one of the six answers `MobCastProfile.NONE`, never `null`.** A null there is a
`NullPointerException` on the server thread inside entity ticking, fired the instant an Ascendant
considers the skill, and it takes the integrated server down. Twelve handlers have already made this
mistake; `MobCastProfileTest` catches it, but only because someone added that test afterwards.

**One billing call for the whole kit.** `selfManaged()` and `holdGated()` handlers both return out of
`castViaRegistry` before stats resolve, before mana is spent, before the aim ray, before the FX and
before any cooldown is written — the entire Authority of Causality shipped free because nobody had
written `payFor`. So:

```java
// SwordService
private static boolean payFor(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition skill, int manaOverride) {
    if (state.isSkillOnCooldown(skill.id())) {
        player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
        return false;
    }
    MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
    int mana = manaOverride >= 0 ? manaOverride : stats.manaCost();
    if (!MagicSinService.spendManaForSkill(player, state, mana)) {
        player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
        return false;
    }
    state.setSkillCooldown(skill.id(), stats.cooldownTicks());
    return true;
}
```

Called **after** the skill establishes it has work to do and **before** it does it, so a press that
refuses itself is never charged. `SwordGameTests.everyPressInTheKitIsPaidFor` pins all six.

Also: **`ctx.aim()` is `null` in a self-managed or hold-gated handler.** A handler copied from a
plain-press skill that dereferences `ctx.aim().point()` throws, `castViaRegistry` converts it to
`CastResult.FAILED`, and the player gets a refund of mana that was never spent.

### 4.1 Actives

---

#### `call_the_blade` — **Call the Blade** — tap

```java
register("call_the_blade", MagicSchool.SWORD, MagicSkillType.BURST, -3, 0,
        0.0F, 1.0F, 6.0F, 6, 10, 0, 0.0F, 0, 0xB9C4CE);
```

**Plain press.** Full pipeline, so the registry bills it and no `payFor` is needed. 6 mana base;
a sneak-press pouring *n* Edge costs `4 + 2n` and is charged by the handler through
`MagicSinService.spendManaForSkill` for the difference. 10-tick cooldown, aim range 6.0 blocks,
`aimTolerance()` 1.6 (blocks *and* bodies — **0 would mean blocks only, silently**),
`aimDropsToGround()` false.

`AimResolver` gives a point within 6 blocks; the handler quantises the offset from the frame origin
into a `Station` — nearest of 24 yaw steps, 9 pitch steps, and `clamp(round(distance), 1, 6)` reach —
and calls `plant`. Press places **1 Edge**; sneak-press places as much as `min(maxEdge, loose)`
allows. Planting onto an occupied bearing **tops it up** rather than refusing, which is how a
station that Ward or a Loose emptied gets re-manned.

Every refusal is printed on the actionbar **by name**: `message.magical.sword_full`,
`sword_too_close`, `sword_too_dear`, `sword_no_edge`, `sword_out_of_reach`. A refusal costs nothing.

`TuningView`: crush (damage per Edge) / rise (unused, labelled *reach*) / reach (aim range) /
hold (unused) / thrift.

> **Reads.** It is the structure's only write operation and the entire authoring surface of the
> class. There is no build screen. Where you stand and where you point when you call each blade *is*
> the build, permanently, and it is saved.
>
> **Counterplay.** A 10-tick press that does no damage. Filling twelve stations from empty is twelve
> presses, at least 72 mana and six seconds of not fighting, in the open, visibly. The Array is its
> own telegraph: an opponent counts your blades from thirty blocks and knows how much Loose, Below
> and Ward you have left, and the threads (§6) tell them how loaded you are.

---

#### `the_bearing` — **The Bearing** — hold

```java
register("the_bearing", MagicSchool.SWORD, MagicSkillType.BURST, -3, 0,
        0.0F, 1.0F, 1.0F, 0, 20, 0, 0.0F, 0, 0xD4DDE4);
SkillCastRegistry.register(MagicContent.THE_BEARING, SkillCastRegistry.holdHint("message.magical.bearing_hold"));
```

0 mana, 20-tick cooldown charged **on release**. The overlay (§6) draws the Array as an azimuthal
plot — a disc where angle is yaw and radius is pitch, centre straight up, rim straight down — with
each station a mark sized by its Edge and brightened by its reach, and the bill written out as
`span 41 / draw 64`. Scroll walks the focus around the marks; right mouse marks the focused station
for pulling; release commits every pull at once through `PullStationsPayload(int mask)`, twelve bits,
re-checked server-side against the live station count.

This overlay exists for one reason and it is not "the kit needs a screen": **six of your stations are
behind your head, and the crosshair cannot reach them.** Reading the shape you authored, and
unwriting a bearing you cannot look at, are two things the rest of the kit genuinely cannot do.
A release that pulls nothing is refused and not charged.

> **Counterplay.** A hold: the wielder is standing still behind a scrim and cannot aim. Pulling a
> station returns its Edge to loose, so the Bearing can dismantle a build but never make one.

---

#### `the_keel` — **The Keel** — tap, with a sneak variant

```java
register("the_keel", MagicSchool.SWORD, MagicSkillType.BURST, -3, 0,
        0.0F, 0.55F, 24.0F, 10, 30, 400, 0.0F, 0, 0xA6B6C4);
SkillCastRegistry.register(MagicContent.THE_KEEL, SkillCastRegistry.selfManaged(SwordService::keel));
```

**Self-managed**, billed by `payFor`. Sword Rider and above. It does one thing — **it takes the
frame's origin off your body** — and the sneak bit says which of the two then moves.

**Press (SET):** origin and facing freeze where they were. You walk out of your own formation and it
stays. 10 mana, 30-tick cooldown. Past `KEEL_LEASH = 24.0` blocks the frame snaps back to `HELD` and
every blade flies home over 20 ticks at 1.2 blocks/tick, visibly. Pressing again returns it to
`HELD`.

**Sneak-press (RIDDEN):** one station's blade slides under your feet and the frame carries you. The
station must have `edge >= 3`. 10 mana on press plus 3 every 10 ticks, `KEEL_RIDE_TICKS = 400`
maximum, 60-tick cooldown. Gravity off, `0.55` blocks/tick (11 blocks/second) along the look vector
while the key is held, no fall damage for 40 ticks after release. The ridden station spends 1 Edge
every 80 ticks and the ride ends the instant it empties. Any hit of 4.0 half-hearts or more on the
wielder ends it.

**The movement half must live on the client and the server must never try.** The server cannot
deliver a velocity to a player's own client: vanilla sends the `hasImpulse` motion packet over
`broadcast`, and `ChunkMap.TrackedEntity.updatePlayer` never puts a player in their own audience;
`hurtMarked` replaces the client's velocity with the server's copy. `client/SwordKeelClient` runs on
`PlayerTickEvent.Pre` against the local player only, exactly as `SpaceLawClient` does; the server
carries the Edge clock, the leash, the fall-damage exemption and the frame's authoritative origin.
`SwordKeelGameTests.theServerNeverPushesTheRider` fails the moment someone puts a
`setDeltaMovement` on the server side.

> **Reads.** `Frame.withOrigin`. One record field, and the difference between "my formation stays
> here" and "I stand on my formation" is which of the two endpoints is moving.
>
> **Counterplay.** A SET array is a fixed object with a known radius that does not turn to follow
> you: walk around it and Ward covers nothing, because `covers()` reads the frozen bearings. A rider
> is a straight line at a fixed speed with no armour bonus and one hit drops them out of the sky; a
> bow answers it completely. 御剑 is fast, not safe.

---

#### `loose` — **Loose** — tap

```java
register("loose", MagicSchool.SWORD, MagicSkillType.BURST, -3, 0,
        2.0F, 1.8F, 28.0F, 14, 60, 24, 0.25F, 0, 0x8FB4CC);
```

**Plain press.** 14 mana, 60-tick cooldown, aim range 28.0 blocks, `aimTolerance()` 1.6, flight
1.8 blocks/tick over a 24-tick budget (`1.8 × 24 = 43.2`, comfortably past the 28-block bind range
even round a corner).

Binds the frame's origin to the body under the crosshair (`Bind.BOUND`) and fires the **forward
projection**: every manned station whose world bearing has `dot(bearing, look) > 0` at the instant
of the press, in descending order of that dot. **There is no blade cap** — the projection is the cap,
which is the whole point: a wielder who planted a forward cone fires all of it, a wielder who planted
a full ring fires exactly half, always.

Each blade is a `SwordBladeEntity` flying by explicit raycast — clip the wall first to bound the
step, then take the first living body on the segment before the wall — not by vanilla `Projectile`
physics, so a blade hits a body standing against a wall. Damage per blade is
`SwordMath.bladeDamage(edge, strain)` = `2.0 + 2.0 × edge + 0.25 × strain`, with `0.25` knockback.
At Sword God with 3 Edge a station and 6 forward blades that is `6 × 8.0 = 48` half-hearts = 24
hearts over 24 ticks of visible travel, and it costs six stations.

`wound()` **clears `target.invulnerableTime` before every hit** — without it, six blades landing in
one tick land as one hit and the skill is quietly a sixth as strong, still with full FX and sound —
**and** carries a per-blade per-victim internal cooldown in server scratch, `"icd_" + slot + "_" +
victimId` set to `tickCount + 10`, because clearing i-frames without that is twelve full hits in one
tick and an instant delete. Both halves are needed, neither is enforced by anything in the build,
and `SwordGameTests` pins both.

A blade that lands is **spent**: its Edge moves to `spent`, and the blade stays in the body as a
`SwordBladeEntity` in state `SPENT` for 600 ticks. A blade that misses or hits a wall returns home
and its Edge returns to `spent` too (to `loose` directly with Returning).

While the frame is `BOUND`, `frame.scale = max(1, distance / 8.0)` and the bill inflates with it.
The bind releases when the body dies, leaves the dimension, or exceeds 40 blocks.

> **Reads.** `Projection.forward()` over a re-anchored origin. The shape does not change; the origin
> moves onto somebody else. And the bind is the leash that makes the enemy's footwork spend your
> budget (§4.3, the shed).
>
> **Counterplay.** A defensive ring halves its own strike by construction. The blades travel 1.8
> blocks/tick, so at 28 blocks that is 0.8 seconds of visible incoming steel you can break line of
> sight against. Every blade that connects removes its Edge from the Array, which removes 3 max mana
> per emptied station through Sword Heart — a Sword God who has just Loosed twice is visibly thinner
> on both counts. And the bind cuts both ways: **run, and his own Array starts tearing itself apart
> at you** (§4.3).

---

#### `below` — **Below** — tap

```java
register("below", MagicSchool.SWORD, MagicSkillType.BURST, -3, 0,
        2.0F, 1.0F, 20.0F, 20, 100, 24, 0.4F, 0, 0x7FA6BE);
```

**Plain press.** Sword Rider and above. 20 mana, 100-tick cooldown, 20.0-block placement range.

**The origin is a POINT, committed at the press, and it is never re-acquired.** The crosshair point,
or the feet of the body under the crosshair *at that instant*. The frame goes `SUNK`: origin at that
point, every station's pitch negated, so the Array hangs beneath it. Fires the **below projection** —
stations with `pitch < 0` — and there is no cap here either.

Exact timeline, in ticks from the press:

| t | |
|---|---|
| 0 | press: mana and cooldown taken, blades leave their bearings and sink |
| 0–6 | `SINK_TICKS` — blades descend to `reach` blocks below the origin |
| 6–16 | `TELEGRAPH_TICKS = 10` — a `groundMarkAdd` ring of radius `ERUPT_RADIUS = 1.6` at the origin, and every blade tip breaking the surface by 0.15 blocks |
| 16 | **COMMIT** — nothing about the strike can change after this tick |
| 16–22 | `RISE_TICKS = 6` — each blade travels from `reach` below to `reach` above the origin |
| 22 | **the hit test, once** |

The hit test is a vertical cylinder at the origin, radius 1.6, from `origin.y - 0.5` to
`origin.y + ERUPT_HEIGHT = 3.0`. Every `LivingEntity` whose bounding box intersects it takes
`SwordMath.bladeDamage(edge, strain)` per risen blade and 0.4 knockback **upward**. A blade that hits
is spent; a blade that hits nothing stays standing in the world at that point in state `PLANTED` for
600 ticks, where the wielder can walk over it to recover its Edge and where **the enemy can break
it**.

**The dodge, as arithmetic, because "dodgeable if moved fast enough" is a number and not a promise.**
22 ticks press to hit, the mark visible from t=6, so 16 ticks of warning to clear 1.6 blocks
laterally:

| | blocks/tick | ticks to clear 1.6 | |
|---|---|---|---|
| sprinting | 0.2806 | **5.7** | clear with 10 ticks to spare |
| walking | 0.2159 | **7.4** | clear with 9 ticks to spare |
| sneaking | 0.06475 | 24.7 | **impossible** |
| standing | 0 | never | eats all of it |

Jumping does not work: a vanilla jump peaks at 1.25 blocks and the cylinder is 3.5 tall. **In the air
the origin is the body's feet in 3D and no floor is consulted** — the same horizontal numbers apply,
so falling does not save you, jumping straight up does not save you, and an elytra, a sprint-jump or
any existing momentum does. *Air is not a refuge; speed is.*

> **Reads.** A pitch reflection of the frame plus a recorded origin. The eruption's strength is
> exactly how much of your authored shape points at the floor — which is precisely the part of your
> shape that is not covering your flanks. A wielder who planted a flat ring at pitch 0 has zero
> stations below and Below is a mark on the ground and nothing else.
>
> **Counterplay.** Above, entirely. Plus: it commits to a *point*, not to you, so the mark and your
> feet must never be drawn in the same place or the whole read collapses into "the eruption is
> buggy" — see §11.

---

#### `one_blade` — **One Blade** — hold

```java
register("one_blade", MagicSchool.SWORD, MagicSkillType.BURST, -3, 0,
        6.0F, 1.0F, 3.0F, 0, 200, 120, 1.2F, 0, 0x6FA0C0);
SkillCastRegistry.register(MagicContent.ONE_BLADE, new SkillCastHandler() { /* holdGated, counterWindowTicks 12 */ });
```

**Hold-gated**, 0 base mana, **billed on release** through `payFor` with
`manaOverride = 6 × stationsFused`. 200-tick cooldown, written on release. Sword Saint and above.

**Hold.** Over `FUSE_TICKS = 12`, `frame.scale` drives 1 → 0 and the origin drives back to the
wielder's right hand. Every manned station's blade converges on that point; if the frame was `BOUND`,
they converge *from around the target, all the way back to your hand, cutting the whole line*. At
scale 0 the bill is zero — **this is `settle()` run at its limit, and it is the same arithmetic that
has been shedding blades out of an over-stretched Array since Sword Saint.** The wielder then holds
one blade carrying the summed Edge:

- length = `SwordMath.oneBladeReach(totalEdge)` = `2.5 + 0.18 × edge` blocks. 8 Edge → 3.9; 36 → 9.0.
- while held: movement speed −20%, no other cast is accepted, 120-tick maximum.

**Press while holding:** a slash. A horizontal arc of `SwordMath.oneBladeArc(totalEdge)` =
`min(160, 60 + 3 × edge)` degrees centred on the look, `length` blocks of reach, 3.0 blocks tall,
`SwordMath.oneBladeSlash` = `min(48.0, 6.0 + 1.5 × edge + 0.25 × strain)` half-hearts, 1.2 knockback.
One slash per 16 ticks, 12 mana each.

**Sneak-press while holding:** the blast. The blade drives point-first and releases everything at
once in a wedge `length × 2` long by `length × 0.5` wide, `oneBladeBlast` = `min(72.0, slash × 1.5)`,
free — and it **ends**, scattering the Edge back to `loose` with every station unmanned.

Hold past 120 ticks without pressing and the fusion collapses, every blade returns to its station,
and nothing is charged.

**The gather is the one counterable thing in the kit**, and it works because of a detail nobody else
noticed: `MagicCounterService.matchesForbiddenDepth` is
`incomingTier >= 0 || counter.tier() >= -incomingTier`, so **a tier −3 skill is answerable by an
existing tier-3 counter**, while nothing at all answers −5 or the Authorities. The gather is a
`SpellEffectEntity` (the only reader of `counterWindowTicks()`) with a 12-tick window and **without**
mode bit 1, so `offerCounters` runs. Countering it shatters the convergence where it stands, sends
every Edge to `spent`, and the 200-tick cooldown still runs.

> **Reads.** `frame.scale -> 0`, and `Sheaf`-style merge of the Edge. The greatsword is not a new
> object: it is the Array collapsed, and its length, arc and damage are functions of the Edge that
> collapsed, because that is literally what it is made of. And because the origin is a variable, one
> skill is two pictures — fused from `HELD` it is a greatsword in your hand; fused from `BOUND` it is
> twelve converging cuts arriving on one body and *then* a greatsword.
>
> **Counterplay.** 12 ticks of gathering with every blade visibly leaving its bearing, during which
> **Ward covers nothing** — the ultimate opens a specific, nameable hole rather than costing a
> number, and killing the wielder during the fuse costs them the entire Array. The greatsword is in
> front: it has no answer to anything behind or above. And a real QTE window, which no Authority has.

### 4.2 Passives

All four are registered with **`forbiddenPassive(...)`, not `classPassive(...)`**, and the reason
goes in a comment beside the registration or the next reader will "fix" it:
`ClassTreeTest.everyNonBaseNodeGrantsSomethingAndNoClassPassiveIsGrantedTwice` skips non-starting-root
trees when *collecting* but closes with a global
`assertEquals(classPassives().size(), seen.size())`, so a class passive granted only by this hidden
chain reads as ungranted and turns the build red for a reason unrelated to the change. It also fixes
the codex leak for free: `CodexPassiveRows.groupKey` falls to `GROUP_GENERAL` when `isClassPassive`
is false, so the Passives tab never prints "Sword Summoner" as a group header. Eldritch's `LIDLESS`
and `DEEP_BARGAIN` are the precedent.

One handler, `magic/sword/SwordPassives.java`, added to the eleven-entry `List.of` in
`ClassPassiveEffects`. It **must declare `forget(UUID)`** even if the body only clears one map —
`handlersDoNotShareScratchAcrossPlayers` reflects over `getDeclaredMethods()` and fails the build
otherwise. And `forget` is wired to **logout only**, not to a dimension change, despite the
interface javadoc, so `SwordService.held()` compares the dimension key on every read and rebuilds.

---

**`sword_heart`** — Sword Summoner — hook `bonusMaxMana`

`bonusMaxMana = min(36, 3 × mannedStations)`. Twelve manned stations is +36 max mana; an empty Array
is +0. Your pool *is* your blades, so every skill that spends steel also lowers your ceiling and
re-manning refills the mage. `ClassPassiveEffects.slowTick` already re-sums `bonusMaxMana` into the
state; `PlayerMagicState` must clamp current mana down when the max shrinks or the HUD ring overruns
its own track.

---

**`ward_of_the_array`** — Sword Rider — hooks `incomingDamage` (melee and magic) and the array
entity's own server tick (projectiles)

The shape is the guard and it has no separate targeting, because the shape already is the choice.
An incoming blow whose approach bearing falls within `WARD_CONE = 30` degrees of a manned station's
*current world bearing* is turned by that station:

- melee and magic: reduce by `SwordMath.wardAbsorb(edge)` = `2.0 + 1.5 × edge`, floor 0, on
  `incomingDamage` — which runs **after** vanilla mitigation and **before** `state.absorbDamage`, so
  a warded hit also saves barrier. That ordering is free and it is the whole defensive identity.
- projectiles: the **array entity's** tick, not the handler, scans `Projectile` within
  `WARD_SCAN = 8.0` blocks whose velocity closes on the wielder and calls `projectile.deflect(REVERSE)`
  exactly as the Halo verse does, with one `SkillClashEffectEntity` flare and one shield sound. The
  arrow is the wielder's afterwards.

Each interception spends **1 Edge** from that station (to `spent`); a station at 0 Edge is unmanned
but the *bearing stays authored* — the shape survives, the metal does not.
`WARD_PER_TICK = 2` stations maximum in any one tick, so an arrow storm cannot empty a twelve-station
Array in a frame. It **cannot** pay `magical:blood_price`: that source sits in four bypass tags and
`MagicGameplayEvents.onIncomingDamage` returns early for it before the passive chain is reached, so a
Sword Summoner who also runs Blood still pays his own hearts. Same for `magical:sword_strain`.

---

**`returning`** — Sword Saint — hook `slowTick`, plus a 2.0-block proximity sweep over this wielder's
`SPENT`/`PLANTED` blades

Base recovery is 1 Edge from `spent` to `loose` every 40 ticks, and it is in `SwordService`'s slow
tick, not in this passive — the class is unplayable without some recovery. **Returning** makes it
1 Edge every 20 ticks, and makes a **shed** blade (one that came home without landing) return its
Edge to `loose` immediately instead of to `spent`. Either way, walking within 2.0 blocks of a blade
in state `SPENT` or `PLANTED` returns its whole Edge at once and consumes it. Those blades live 600
ticks and then dissolve, returning their Edge to `spent`.

So the fast route back is walking to where your swords died, which is by construction the place you
were just losing — and without Returning, every shed is a slow bleed instead of a reset, which is why
the deliberate converging cut only becomes repeatable at the rung that grants this.

---

**`mirror_of_the_array`** — Sword God — no behaviour hook; it is a flag read by
`Projection.mirror()`, and `SwordPassives.handled()` claims the id so the bidirectional exhaustiveness
test is satisfied

Every authored station gains a twin at its antipode: `yaw + 12 mod 24`, pitch negated, reach
unchanged, Edge `max(1, edge / 2)`, deduplicated against any real station within separation 2 of that
bearing. A mirrored station costs **no Edge and no bill** — it is a reflection, not metal.

**It is read by `below()` and `covers()` and never by `forward()` or the fusion.** A reflection
defends and comes up from below; it does not fly and it carries nothing into a greatsword. That rule
is what stops the apex being a free doubling of the kit's damage, and it still makes the sentence
true: the strike cone you spent four rungs authoring now guards your back, and the guard ring you
built now finally has something to erupt with.

---

## 5. Where it sits

### 5.1 The codex, and the collision argument

**Tier −3, as a new school, `MagicSchool.SWORD`, layer name "Sword".**

Tier −3 is measurably, completely **empty**: zero `register(...)` calls sit there. CLAUDE.md's
"−1 Blood, −2 Dark, −3 Chaos, −4 Primordial, −5 Eldritch" describes lang strings, not the roster —
Chaos is an Authority and all four of its skills are at −6, and no `MagicSchool.PRIMORDIAL` skill
exists anywhere (−4 holds exactly one VOID skill, `vault_of_avarice`). Occupancy today: −1 seven
BLOOD, −2 nine DARK, **−3 nothing**, −4 one VOID, −5 six ELDRITCH, −6 twenty-two Authority skills.

- `PyramidLayersTest.eachLayerBelowTheLineHoldsOneSchool` passes: SWORD is alone on −3.
- `PyramidLayersTest.theAuthorityRowSitsBelowEverySchoolLayer` is untouched: `minTier()` stays −5
  (Eldritch), so `-6 < -5` still holds. Anything at −7 inverts that assertion and
  `CodexLayout.PYRAMID_MAX_ROWS = 6` would clip the Authority row off the bottom.
- `PyramidLayersTest.everyOccupiedLayerIsNamed` is satisfied by renaming the existing key
  `tier.magical.below.3` from **"Chaos"** to **"Sword"**. `MagicPyramidScreen.NAMED_BELOW_LAYERS = 5`
  already covers layer 3. That rename retires the Chaos label from the pyramid permanently; it was
  labelling an empty row for a school that lives three floors down, so it was never true of any
  registered skill. **Put it in the commit message.**

**Why a school and not positive-tier class rewards**, which is the zero-risk answer and would move no
test. Three reasons, in order of weight. (1) `budgetClass <= tier + 1` is enforced only for
`tier >= 0`, so a twelve-blade Array on a positive row would be strangled by a rule that exists to
stop tier-1 skills looking like tier-4 ones. (2) A negative-tier skill can never be rolled by
`randomProficiencyReward` (the weight table only runs 0..maxTier), which is a *stronger* leak
guarantee than `CLASS_REWARD_SKILLS` membership — that is one forgotten `.add` away from handing a
secret class's kit to a player who never found the rite. (3) **The reveal.** When you take the class,
the codex grows a layer. That is worth more than any tooltip and it is only possible below the line,
where a row is a school.

**Why not a sixth Authority.** Authority skills are filtered out of `ALL_SKILLS`, out of
`/magical unlockall` and out of class-reward grants, which fights "unlock the class, get the kit"
head-on. And an Authority is something you *hold*; this is a chain you climb. The anti-climax worry
("is Sword God smaller than an Authority?") is answered by the codex geometry rather than by prose:
the Authority row sits **alone at −6 below every school**, and Sword sits **at −3 among them**. An
Authority is cosmological; a Sword God is the best swordsman in the world, four rungs deep and
hidden, whose last rung *removes a rule*.

**What the new school costs, all in one commit or the build is green and the kit is wrong:**

- `MagicSchool.SWORD(0xB9C4CE)` — a near-neutral pewter, and the palette is the point: all twelve
  existing schools are saturated, so a grey-steel row reads as *steel* on sight.
- **`MagicSchool.isForbidden()` must gain SWORD.** It is an `==` chain, not a switch, so this is the
  one new-school step with **no compile error to catch it**, and a forbidden school that answers
  false there is treated as an ordinary positive school by every caller.
- `MagicAttribute.SWORD`. `fromSchool` and `counters` are both exhaustive switches, so those are safe
  compile errors. The ruling on the arms, since three proposals disagreed:
  `SWORD -> incoming == ELDRITCH || incoming == CHAOS` (an edge answers the formless and the
  unmade); `SPATIAL` gains `|| incoming == SWORD` (you cannot cut what is not where you swung);
  `DIVINE` gains `|| incoming == SWORD`, because Light is the one answer that must reach every
  forbidden school.
- **`SchoolMaterial.SWORD` — the silent one.** `SchoolMaterial.of()` falls back to `ARCANE` with no
  warning, so a missing row ships the entire kit rendering arcane-blue with a fully green build.
  Row: `SWORD(MagicSchool.SWORD, new int[] {0xB9C4CE, 0xE9F1F6, 0x6F7D8A, 0x2A323A}, 14,
  GlyphKind.TICK_BAND, StampId.EDGE, CoreKind.CROSS, SpinSignature.ONE_WAY_FAST, false)`. Frame sides
  14 is free — 3,4,5,6,7,8,9,10,11,12,13 are taken and 14,15,16 are not. `StampId.EDGE` is new.
- `school.magical.sword` in `en_us.json`.

### 5.2 Loadout

Six actives across four slots (Z/X/C/V) and four loadouts swapped with B. The natural first loadout
is Call the Blade / The Bearing / Loose / Below; the fusion loadout swaps Below and The Bearing for
The Keel and One Blade. Nothing about the kit needs a fifth key: the Bearing and One Blade are the
only holds and they sit in different loadouts in practice.

### 5.3 HUD

**One new `HudKind` quad and one string, and only while the Array is non-empty.** A draw arc outside
the barrier ring, filled to `bill × scale / draw`, in the school's pewter up to 1.0 and in cinnabar
`0xD4402F` past it; the string is `41/64`. `HudBudget.MAX_QUADS` is 120 and `IDLE_QUADS` 24, and this
is +1 non-idle and +0 idle. `HudSnapshotBudgetTest` takes the new number. No `GuiGraphics.fill`
anywhere under `client/hud` — a test forbids it. Everything else is automatic: each skill's HUD card
icon is its own cast circle's emblem, and the cooldown sweep and seconds numeral come from
`CooldownSyncPayload`.

---

## 6. What is drawn

**No new core shader and no new `.geo.json`.** Twelve shader programs and nineteen render types
already exist and they cover every part of this. A Blockbench model is refused on one measurement:
`ModelPart.render` is **invisible to `FxBudget`** — only painters call `countQuads` — so twelve blade
models at two passes and ~180 quads each would be ~4,300 quads the throttle cannot see, will not
demote, and will report as headroom to everything else. That is the one failure here that would never
appear in a test, a log line or a screenshot.

**The blade.** `FxMesh.prism(4)` scaled to 0.15 × 0.15 × 1.6 — a diamond-section blade — on
`MagicalFxRenderTypes.shardBody()`, the only depth-writing render type, so a blade occludes the glow
behind it. Over its spine, one camera-facing glow band through `FilamentPainter` in the shape of
`ForgeRibbon.sheath`, drawn once per blade and never per trail copy. About 32 quads a blade; twelve
blades is 384 against a 60000-quad frame target, and because it goes through the painters
`FxBudget.countQuads()` actually sees it. Edge is 3 synced bits of palette ramp index, so a 12-Edge
blade reads dark and heavy and a 1-Edge blade reads thin, without syncing anything.

**The cant, and it is load-bearing.** A blade flown point-first along its own flight vector is a
one-to-two-pixel vertical line to the person who threw it, and *worse* than a bolt because a plate
has no symmetry to collapse into a point — it flickers as the perspective divide fights its
thickness. This is `VerseBodyRenderer`'s documented bolt problem and `ForgeWaveFront`'s written
verdict ("a swing may lean; a thrown thing may not") arriving together. **Every flying blade is
yawed `BLADE_CANT_YAW = 18` degrees and rolled `BLADE_CANT_ROLL = 25` degrees off its flight line**,
applied after `FilamentPainter.orientAlong`, plus an `OrbPainter.billboard(FxKinds.Orb.PLASMA,
size × 1.3, ..., 0.75, ...)` head emitted **outside** the orient push/pop so it is camera-facing in
world space. The cant is the fix *and* the genre: 飞剑 travel canted and broadside, never nose-on
like arrows. `SwordSilhouetteTest` pins the drawn plate's normal against the flight vector and
measures the drawn extent against the swept hit box, the way `WaveSilhouetteTest` does.

**The thread — the class's one new image, and it needs no new code.**
`FilamentPainter.beam` from the wielder's chest to every manned blade, with
`BEAM_WHOLE = 0.5F` (the beam shader's reveal is a *window*: 0.5 is a whole beam and 1.0 has receded
to nothing), opacity `0.10 + 0.75 × min(1, bill × scale / draw)`. **The threads brighten as you load
up and go cinnabar `0xD4402F` when there is strain**, so the screen carries the resource without any
HUD at all and the opponent reads a Sword God's entire state from across the arena. Twelve pewter
threads from a chest into the dark, going red, is the screenshot; the frame after it — twelve lines
of light converging inward on one figure as the Array sheds — is the other. At Sword God a strained
Array also **hums audibly at 32 blocks**, so the biggest number in the kit has a tell on a second
channel.

*The first-person trap for the thread:* a thread to a station directly ahead is end-on and invisible,
which is fine — it is the ones to the sides and behind that carry the reading, and in first person
those are exactly the ones in frame at the edges. Do not "fix" it by billboarding the thread; that
would make it a ribbon and lose the direction.

**The Array at rest.** N blade bodies placed by the pure `ArrayPose.worldOffset(station, frame)`
called by the server behaviour and the client painter with identical arguments — the Gravemoons
discipline, so a blade cannot desync and **zero bytes are spent telling the client where twelve
objects are.** `extractRenderState` must **zero `state.radius`**: the radius a sword entity carries
is the frame scale's gameplay reach, not the size of its drawing, and `ProfileRendererShell` inflates
MARK/SWARM/FIELD-dome silhouettes to the synced radius. `EldritchConstructRenderer:73` fixes the same
thing with the reason written down.

**Call the Blade.** One `GlyphCirclePainter` cast circle at the new station plus a 6-tick
`FilamentPainter.beam` from the hand to it.

**Below.** The telegraph is deliberately a **horizontal** element — `MarkPainter.mark` on
`groundMarkAdd()`, a 1.6-block ring with an inner cross — because the caster is looking *down* at the
eruption and a vertical blade is end-on from above too; that is the same trap pointed at the floor.
`phase = 0.5F`, never 1.0: `rendertype_glyph_ink.fsh` reads 0.75..1 as burning away, so the innocent
1.0 means *finished* and `dissolve` erases every pixel (the `AnchorMarkRenderer` scar). The rising
blades are the same canted bodies with `SwarmPainter` grit at the soil line.

**One Blade.** The twelve blades converge visibly on the hand over 12 ticks — the renderer needs no
special case, it just reads `frame.scale`, which is the whole argument for scale being a frame field.
The held greatsword is one `FxMesh.prism(4)` at `length` scale on `shardBody()` with `count = n` ribs,
so the blades that went into it are still visible inside it rather than welded into a featureless
slab; `FxBudget.countQuads` is called explicitly after it, because it is large. The **release** is a
real `ForgeStrikeEntity` through `ForgeStrikeMath.resolve` / `ForgeStrikeEntity.spawn`, form
**CLEAVE**, with a new `ForgeElementAccent` row for SWORD — eight arc geometries, a tuned additive
shader with its `CEILING` already set for the layer stack, `ForgeAura`, `ForgeRibbon.sheath` and
`ForgeSparks`, for one spawn call. **Form CLEAVE, never SLAM:** SLAM spans two render types and is
handed the source, because `ForgeBuffers` forbids two live consumers on the shared buffer. If the
weapon plumbing (grade, quality, temper) proves awkward for a weaponless strike, the fallback is the
array entity's own renderer drawing a `ForgeRibbon` arc through `ForgeMotion.opening`, which is the
same picture — but note `forge/ForgeStrikeBench` already spawns strikes with no weapon through the
real `ForgeStrikeMath.resolve`, so the precedent exists.

**Ward.** One `SkillClashEffectEntity` at the intercept, at the synced scale the Halo verse already
uses.

**The shed.** A shedding blade is a `FilamentPainter.beam` along the line it cuts, at
`BEAM_WHOLE = 0.5`, fading over 8 ticks, plus the blade body travelling it. Twelve of those at once
is the Sword God frame.

**The Bearing overlay.** Frameless the way this mod means it: **a scrim at 0x8C over the dimmed
world**, never bare text on terrain — measured against real noon sand (220,209,165), `TEXT_PRIMARY`
lands at 1.45:1 and the highlight inverts; at 0x8C the same sand goes to (71,86,89) and the three
inks land at 7.23 / 5.44 / 4.71:1, in the right order. No panel, plate or frame; every string carries
vanilla's drop shadow. The plot is an azimuthal disc — angle is yaw, radius is pitch, centre straight
up, rim straight down — with each station a mark, and it is deliberately neither the Fracture's five
gates in a row nor Manipulate Space's three columns, because what it has to show is a *direction*.
`HudLayers.renderSigil` stands the sigil layer down while it is open. Geometry in
`client/SwordBearingLayout` with no `Font` and no `GuiGraphics`, exposing `List<CodexLayout.Rect>` —
**do not define a new `Rect`.**

**Six distinct `EmblemId` constants** with atlas cells and six non-colliding circle signatures, plus
frame sides and stamps unique *within* `MagicSchool.SWORD` (which is empty, so 3..8 are all free).
Emblems: a single vertical edge (`call_the_blade`), a marked disc (`the_bearing`), a horizon line
with a blade on it (`the_keel`), a leaning blade with a trailing tick (`loose`), four risers under a
bar (`below`), a bundle of lines meeting at a point (`one_blade`). Each profile needs exactly one
EMBLEM layer, exactly one HOT layer and 3..14 layers. A **missing** explicit profile is only a
`soft:` advisory that `hardProblems()` filters out, so a forgotten one ships wearing the school
default circle, passes every test, and is exactly the reused-visuals outcome
`SKILL_CREATION_NOTES.md` forbids.

**Judge every capture at midnight.** The additive types blend `ONE, ONE` and clip to white over
daylight sand by construction, and a cold pewter palette suffers most.

---

## 7. Persistence and sync

### 7.1 Saved — the authored half, and only that

`PlayerMagicState` gains one field, through all five sites or it half-works:

```java
// The wielder half of the Sword Summoner: the bearings they authored and the Edge they put on
// each. Saved, because it is the only part of this class they actually own. The blades in the air,
// the frame, the spent metal lying in the world and every recovery clock are held by SwordService
// and are never saved, the way PileService drops its Piles.
private final com.efkrdnz.magical.magic.sword.SwordArray swordArray = new ...SwordArray();
```

| site | line |
|---|---|
| accessor | `public SwordArray swordArray() { return swordArray; }` |
| `clearAuthority()` / `/magical reset` | `swordArray.clear();` |
| `copy()` | `copy.swordArray.copyFrom(swordArray);` |
| `save()` | `if (swordArray.size() > 0) { tag.put("swordArray", swordArray.save()); }` |
| `load()` | `state.swordArray.load(tag.getList("swordArray", SwordArray.tagType()));` |

The key is **omitted entirely while the Array is empty** — the bloodShapes precedent — because that
tag rides every sync for every player and the overwhelming majority will never find the rite.

**Miss `copy()` and it saves and loads perfectly on the server and arrives empty on the client, in
multiplayer only, after a resync, with nothing logged.** The client path is `load(tag)` then `copy()`
(`ClientPayloadHandlers` into `ClientMagicState.set`). Miss `save()` and it never reaches the wire or
the disk. `PlayerMagicStateSwordArrayTest` (§8) is the only thing that will ever notice.

Stations are saved as an `IntArrayTag`: index 0 is `SAVE_VERSION`, then one packed int per station.
No enum ordinal is written anywhere in this kit — there is no enum in the saved half at all, which
is stronger than `Weave`'s name-writing rule and is why the packing test exists instead.

### 7.2 Deliberately not saved

The frame, the bind, the bound entity id, the manned bitmask, `spent`, every recovery clock, every
blade entity, every per-victim internal cooldown. `SwordService` holds a
`Map<UUID, Held>` where `Held` carries the dimension key, and `forget(UUID)` is wired next to
`PileService.forget` and `IncantationService.forget` in `MagicGameplayEvents.onPlayerLogout`; every
read re-checks the dimension and rebuilds rather than reusing, **because `forget` is not called on a
dimension change despite the interface javadoc saying it is.**

The reasoning is the Pile's, verbatim in spirit: the blades are matter out in the world and the enemy
can break them, a wielder must not be able to leave a forest of planted swords on a shared server,
the save format never has to learn what a blade is, and nothing can grow without bound across
sessions. What the wielder keeps is the shape.

**Nothing that changes every tick may enter `save()`.** A value that moves every tick defeats the
change-gated sync for all 135 call sites — which is precisely why cooldowns have their own payload.
`sync()` serialises the *entire* state on every call and the `lastSyncedTag` equality check
suppresses the packet, not the work. The settle therefore runs on `slowTick` (every 10 ticks) plus on
the discrete events (a blade spawns, lands, dies, is warded) and calls `state.sync(player)` **once at
the end behind a boolean**, the `VersePassives.turn` shape.

### 7.3 Payloads

One new payload. `MagicalNetwork.registerPayloads` uses `event.registrar("9")` — **not** the `"1"`
CLAUDE.md documents, and there are 47 payload records, not 24. Copying the doc's string splits the
protocol and every client is rejected with no useful message.

```java
// network/PullStationsPayload.java
/** A twelve-bit mask of the stations the Bearing pulled, committed on release. Every bit is
 *  re-checked against the live station count on the server, so a forged packet writes nothing. */
public record PullStationsPayload(int mask) { }
```

`.playToServer(PullStationsPayload.TYPE, ..., (payload, context) -> context.enqueueWork(() -> {
if (context.player() instanceof ServerPlayer player) { SwordService.pull(player, payload.mask()); } }))`.

One Blade's hold reuses `CastHoldPayload` and `HoldService` — and the handler's `holdable()` is
**dead code**; `GenericHoldInput` polls `VisualProfiles.of(skill).holdable()`, so
`.holdable(true)` must be on the `VisualProfile.Builder` or the skill compiles, registers, casts on
press and never receives a hold. Set both, as all four existing holdables do. `HoldService` entries
expire after 40 ticks with no refresh and `GenericHoldInput` refreshes every 10, so the fuse must
tolerate a 40-tick stale window.

The Bearing needs the standard hold-overlay wiring: `client/SwordBearingInput.tickSlot(Minecraft,
int)` with its own `WAS_DOWN[]`, an early-return arm in `MagicalClientEvents.Hud.onClientTick` **with
the `while (CAST_SLOTS[i].consumeClick()) {}` drain** — returning true from `tickSlot` is the only
thing that suppresses the normal cast request, and the drain is a separate line that is easy to omit,
which opens the overlay *and* casts on the same press — a render call in `HudLayers.renderSelector`,
scroll and mouse-button arms, and the screen-open `finish()` arm.

### 7.4 Entity budget

Two entity types. **The resting case is one entity for twelve swords.**

**`SwordArrayEntity extends SpellEffectEntity`** — one per wielder, every manned station that has not
detached. It adds **zero new `EntityDataAccessor`s**, riding the 14 slots `SpellEffectEntity` already
has: `EXTRA` the 24-bit manned mask (12 real + 12 mirrored), `VALUE` the frame scale, `DIR_X/Y/Z` the
frame facing, `RADIUS` held at 0 and zeroed again in `extractRenderState`, `MODE` with bit 1 set, and
`DATA` a small `CompoundTag` `{"Shape": int[<=12 packed], "Bind": byte, "Bound": int, "Strain": int}`
— **replaced, never mutated** (mutating the tag returned by `syncedData()` does not dirty the
accessor and desyncs silently, appearing to work whenever some other field changes in the same tick)
— and rewritten only when the shape, the bind or the strain rung changes, never per tick.
`.sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1)`, `shouldBeSaved()` returning **false**
(an override, not `.noSave()` — only `UNWAKING_COUNTER` and `TRAINING_THREAT` use the builder flag,
so copying a neighbouring registration gets you saving by default).

Twelve blade positions cost **zero bytes**, because `ArrayPose.worldOffset` is pure and both sides run
it. The naive alternative — twelve entities at the standard `clientTrackingRange(16).updateInterval(1)`
— is twelve movement packets a tick to every observer within 256 blocks for a picture that is one
rigid formation, and four players with Arrays would be 48 entities.

**`SwordBladeEntity`** — one per **detached** blade, in state `FLYING`, `STUCK`, `PLANTED`, `SPENT` or
`RECALLING`. Modelled on `VerseBodyEntity`: `noPhysics`, no gravity, explicit raycast per step, and
`wound()` with both halves from §4.1. `.sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1)`
for the moving states, `shouldBeSaved()` false, `setMode((byte) 2)`. `MAX_IN_FLIGHT = 12` per wielder
enforced at spawn; `MAX_LYING = 16` `SPENT`/`PLANTED` blades per wielder with the **oldest discarded
at spawn** (the `BloodHarvestEntity` rule, so the newest action always shows) and its Edge returned
to `spent`.

**`setMode((byte) 2)` on both types** so `SpellEffectEntity.offerCounters` returns immediately —
otherwise twelve blades each run a `getEntitiesOfClass(ServerPlayer.class, box)` every tick and throw
twelve competing counter prompts at one defender. The consequence to remember: draw mode is
`mode >> 1`, so mode 2 makes `effectDrawMode()` equal 1, and **no sword silhouette may ever be
narrowed with `forModes(0)`.** The One Blade gather entity is the deliberate exception: it is an
ordinary `SpellEffectEntity` with mode bit 1 **clear**, because it is the one counterable thing here.

**Worst case per wielder: 1 + 12 + 16 = 29**, and conservation makes that unreachable — every manned
station, every flying blade and every lying blade holds at least 1 Edge and `whole` is 36, so the
three populations cannot all be full at once. Realistic steady state is 1 + 3 + 2. The only 20 Hz box
query in the kit is Ward's projectile scan, and it lives **on the array entity**, gated on
`EXTRA != 0`, so a player with no Array and a player who does not own the class both pay nothing.

**Named caps, written as constants before any code:** `MAX_STATIONS = 12`, `MAX_IN_FLIGHT = 12`,
`MAX_LYING = 16`, `WARD_PER_TICK = 2`, `SWORD_HEART_CAP = 36`, `SEPARATION_MIN = 2`. Twelve is under
the 32 the blood pools already allow per owner and well under the **63** ceiling that
`MagicVertex.pack`'s 6-bit `count` field would impose if a station index ever rode in a shader packet
— which it will not, and `paramB` is 5 bits, so 31 is its silent ceiling too.

---

## 8. Tests, in the order they are written

**Pure core first, and every one of these is written before the class it tests exists.**

1. **`SwordArrayTest`** — the packing round-trip and `SAVE_VERSION`; `twelveIsExactlyAFullRing`;
   `aStationMayNotShareABearing` at every reach; each of the seven `PlantResult` values on exact
   inputs; `aPlantThatOverrunsTheDrawIsRefusedByName`; `edgeIsNeverCreatedAndNeverDestroyed` across
   plant → pull → plant; `coincidenceIsRefusedBelowSaintAndAllowedAtIt`;
   `loadReRunsThePlantRulesSoAHandEditedSaveCannotHoldAnIllegalShape`.
2. **`SwordMathTest`** — every formula in §2 on exact values, including both caps and the strain
   term's own cap.
3. **`ArrayPoseTest`** — `aStationIsWhereBothSidesSayItIs` (the same arguments give the same doubles);
   the frame rotation composes correctly at yaw 0/90/180/270 and pitch ±45;
   `scaleZeroPutsEveryStationOnTheOrigin`; `boundScaleIsOneInsideEightBlocks`.
4. **`ProjectionTest`** — forward's `dot > 0` filter and descending order; below's `pitch < 0`;
   covers' 30-degree cone and its −1; `mirrorHalvesTheEdgeAndDedupesAgainstRealStations`;
   `aMirroredStationIsNeverInForward`.
5. **`SettleTest`** — `theFarthestHeaviestStationShedsFirst`; `tiesGoToTheLowestSlotAndNeverToMapOrder`;
   `withoutOverdrawTheSettleRunsUntilTheBillFits`; `withOverdrawNothingShedsUntilStrainReachesTheDraw`;
   `scaleZeroDrivesTheBillToZero`.
6. **`PlayerMagicStateSwordArrayTest`** — copied wholesale from `PlayerMagicStateGrimoireTest`:
   `aCopyKeepsTheArray`, `aSaveAndLoadKeepsTheArray`, `clearingWipesTheArray`,
   `anEmptyArrayIsNotOnTheWire` (the tag key is absent).

**Registry and content next.**

7. **`SwordSchoolTest`**, copied from `EldritchSchoolTest` — the exact list of six ids; every one at
   tier −3; every one `MagicAttribute.SWORD`; every one priced (`baseManaCost > 0` or billed by
   `payFor`, asserted by id); every one has a cast handler in `SkillCastRegistry`; every one
   commandable; the `nameKey + ".desc"` shape; every one in `CLASS_REWARD_SKILLS` and in none of
   `SUB_SKILLS` / `CREATED_SKILLS` / `AUTHORITY_SKILLS`; `MagicSchool.SWORD.isForbidden()` is true;
   `SchoolMaterial.of(SWORD) != SchoolMaterial.ARCANE`.
8. **`ClassVisibilityTest`** — the highest-value test in this document. Against a `PlayerMagicState`
   that owns nothing, assert the four sword ids appear in **none** of: `ClassTreeScreen`'s visible
   node set (via `MagicalClasses.isVisible`), the tower's `CLASS_UNLOCK` candidate list,
   `rootCommandIds()`, `commandIds()`, `ClassSelectMenu.choices()`, `startingRoots()`,
   `PASSIVE_SOURCES`, and `hasAnyRootClass()`. Then, owning only `sword_summoner`, assert **all
   four** are visible. Seven call sites, one test, and it is the only thing that will stop the eighth
   call site somebody adds next month.
9. **`ClassTreeExtentTest`** — `assertEquals(541.0F, ClassTreeLayout.extent(), 1.0F)` with a comment
   naming `fitZoom()` and the `zoom >= 0.40F` label threshold at `ClassTreeScreen:227`. **Nothing
   else in the build will tell you that a node placement silently stripped the name off every class
   node in the game.**
10. **`ClassLangKeysTest`** — walks `MagicalClasses.all()` against `en_us.json` for
    `class.magical.<path>` and `.desc`. Nothing checks these today; `ContentLangKeysTest` walks skills
    and authorities only and `MagicalTooltipAssetsTest` explicitly skips concatenated keys and never
    reads the classes package. Eight lines, and it closes a hole that predates this feature.
11. **`ContentLangKeysTest.everyPassiveHasANameAndADescription`** — the same hole for
    `passive.magical.<path>`. Four lines.
12. **`CodexButtonRangeTest`** — add `bands.add(new Band("evolve selected", MagicPyramidMenu.BUTTON_EVOLVE_SELECTED_CLASS, 1))`.
    The test does not model id 800 as a band at all, so it currently permits the class-select range to
    grow to 200 entries while `MagicPyramidMenu`'s dispatch order silently swallows 800 at 101
    classes. The real ceiling is 100, not the documented 99.
13. Existing tests that must stay green and will move: `ClassTreeTest.noTwoNodesOverlapOnScreen`
    (four new laid-out nodes), `PyramidLayersTest` (all three), `ContentLangKeysTest`,
    `ClassPassiveEffectsTest` (both directions over the four new forbidden passives plus the
    `forget()` reflection check), `MobCastProfileTest`, `VisualProfilesTest.rosterHasNoVisualCollisions`,
    `NewContentIntegrationTest` (extend `newSkills()`), `HudSnapshotBudgetTest`.

**Client geometry.**

14. **`SwordBearingLayoutTest`** — the seven assertions of the family, at all eight GUI sizes
    (320×240, 427×240, 432×243, 480×270, 560×315, 640×360, 854×480, 960×540): nothing overlaps,
    everything is inside the block, the block is inside the screen, every hit-test answers to its own
    centre and to nothing in the gaps, the text budgets are pinned as ints, the ease curves land on
    exactly 1.0 and never leave 0..1, and **`nothingReachesTheCrosshair`** — because a hold is open
    while the wielder is still aiming. Use `CodexLayout.Rect`.
15. **`SwordSilhouetteTest`** — the drawn blade's normal is not within 5 degrees of the flight vector
    at any cant, and the drawn extent sits inside the swept hit box, measured the way
    `WaveSilhouetteTest` measures the wave.

**Game tests last**, in `src/main/java` (not `src/test/java`), on the shared `unwaking_empty`
template, one batch per method, through `GameTestPlayers.survival`. Every one needs
`setBarrier(0)` (a fresh state stands up with a full barrier that quietly eats the hit) and drives
`tickServer` by hand (a fake player never receives `PlayerTickEvent`, so no cooldown ticks down and
no player-tick passive runs). Run with `--console=plain` or Gradle's progress bar erases the
assertion message and the log files do not have it either.

16. **`SwordGameTests`**:
    - `everyPressInTheKitIsPaidFor` — all six, mana and cooldown taken exactly once.
    - `aVolleyThatLandsOnOneBodyLandsWhole` — six blades, six wounds in one tick.
    - `oneBladeCannotBeReLandedInsideTenTicks` — the internal cooldown holds.
    - `anEruptionFiresAtTheRecordedPointAndNotAtTheBody` — move the target 3 blocks during the window
      and it takes nothing; leave it and it takes everything.
    - `anEruptionWorksInTheAirWithNoFloorBelow`.
    - `aBoundTargetThatRunsMakesTheArrayShedFarthestFirst`.
    - `atSwordGodTheArrayDoesNotShedAndTheWielderBleedsInstead`.
    - `aStationEmptiedByWardKeepsItsBearing`.
    - `theFusionGatherOffersACounterAndTheKitOffersNothingElse`.
    - `theServerNeverPushesTheRider` — the Keel's `setDeltaMovement` guard.
    - `spentEdgeComesBackWhenTheWielderWalksOverIt`.

---

## 9. Capture plan

`run/options.txt` must carry `pauseOnLostFocus:false` or every unattended screenshot is the pause
menu, and `gamma:0.0` for the midnight runs. Debug commands added for these:
`/magical array plant <yaw> <pitch> <reach> <edge>`, `/magical array clear`, `/magical array show`,
`/magical array bind <selector>`, `/magical array strain <n>` (Sword God only, forces the gauge),
and the existing `magical-debug skill magical:<id>`. `magical class unlock sword_summoner` then
`magical class evolve sword_rider|sword_saint|sword_god` walks the chain; `magical class unlockall`
takes the whole line in one command and is deliberately left doing so.

**1 — the Array authored and at rest, first person, midnight.** Four bearings planted by command, the
threads lit, then two more so the bill climbs.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlock sword_summoner;magical class unlockall;magical unlockall;time set midnight;weather clear;tp @s ~ ~ ~ 0 0;magical array clear;120:magical array plant 0 1 3 3;126:magical array plant 4 0 4 3;132:magical array plant 12 -1 3 3;138:magical array plant 18 2 5 3;200:magical array plant 8 -2 6 6;206:magical array plant 20 -3 6 6" -PautoScreenshot=145,150,212,218 -PautoExit
```

**2 — the same Array from outside, third person, so the lattice reads as a shape.** Add
`-PautoCamera=third_back` to run 1 and shift the screenshots two ticks later.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCamera=third_back -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlockall;magical unlockall;time set midnight;weather clear;tp @s ~ ~ ~ 0 0;magical array clear;120:magical array plant 0 1 3 3;126:magical array plant 4 0 4 3;132:magical array plant 12 -1 3 3;138:magical array plant 18 2 5 3;144:magical array plant 8 -2 6 6;150:magical array plant 20 -3 6 6" -PautoScreenshot=147,153,160,175 -PautoExit
```

**3 — Loose, first person, a golem thirty blocks out**, so the blades are canted in flight and the
threads stretch. Not within three ticks of the press: a command tick reaches the client two or three
ticks later.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doMobLoot false;kill @e[type=!player];magical reset;magical hud race human;magical class unlockall;magical unlockall;time set midnight;weather clear;tp @s ~ ~ ~ 0 0;summon iron_golem ~ ~ ~30 {NoAI:1b};magical array clear;110:magical array plant 0 0 4 4;114:magical array plant 2 1 4 4;118:magical array plant 22 -1 4 4;122:magical array plant 4 0 3 3;160:magical-debug skill magical:loose" -PautoScreenshot=166,172,180,195,240 -PautoExit
```

**4 — Below, third person, the telegraph and the eruption, with the dodge made visible.** The first
golem stands still and is hit; the second is teleported 3 blocks at t=10 of its window and is missed.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCamera=third_back -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doMobLoot false;kill @e[type=!player];magical reset;magical hud race human;magical class unlockall;magical unlockall;time set midnight;weather clear;tp @s ~ ~ ~ 0 0;summon iron_golem ~-2 ~ ~8 {NoAI:1b,Tags:[still]};summon iron_golem ~3 ~ ~8 {NoAI:1b,Tags:[runner]};magical array clear;110:magical array plant 6 -2 4 4;114:magical array plant 12 -3 5 4;118:magical array plant 18 -1 4 4;150:tp @s ~ ~ ~ -14 22;160:magical-debug skill magical:below;230:tp @s ~ ~ ~ 20 22;240:magical-debug skill magical:below;250:tp @e[tag=runner] ~3 ~ ~" -PautoScreenshot=168,176,183,188,248,256,263,268 -PautoExit
```

**5 — One Blade: the fuse, the greatsword, the slash.** Held on slot 2, with named `-PautoClick`
actions because a bare `-PautoHold` releases at the first screenshot.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doMobLoot false;kill @e[type=!player];magical reset;magical hud race human;magical class unlockall;magical unlockall;time set midnight;weather clear;tp @s ~ ~ ~ 0 0;summon iron_golem ~ ~ ~6 {NoAI:1b};magical hud equip 1 one_blade;magical array clear;110:magical array plant 0 0 4 4;114:magical array plant 3 1 4 4;118:magical array plant 6 -1 5 4;122:magical array plant 9 0 4 4;126:magical array plant 15 -2 5 4;130:magical array plant 21 1 4 4" -PautoHold=cast_slot_2 -PautoClick="180:hold;215:click-left;245:click-right;260:release" -PautoScreenshot=184,188,196,206,218,224,248,252 -PautoExit
```

**6 — One Blade fused from a BOUND frame: the converging cut.** Same launch, with a Loose to set the
bind and the target 22 blocks out so every line is long.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCamera=third_back -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doMobLoot false;kill @e[type=!player];magical reset;magical hud race human;magical class unlockall;magical unlockall;time set midnight;weather clear;tp @s ~ ~ ~ 0 0;summon iron_golem ~ ~ ~22 {NoAI:1b,Health:300f};magical hud equip 1 one_blade;magical array clear;110:magical array plant 0 0 4 4;114:magical array plant 3 1 4 4;118:magical array plant 6 -1 5 4;122:magical array plant 21 1 4 4;150:magical array bind @e[type=iron_golem,limit=1]" -PautoHold=cast_slot_2 -PautoClick="190:hold;215:release" -PautoScreenshot=195,199,203,207,212,220 -PautoExit
```

**7 — the shed, and the strain gauge.** Sword God, a bound target dragged out to 30 blocks so the
scale inflates, the threads going cinnabar, then the whole-Array shed.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCamera=third_back -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doMobLoot false;kill @e[type=!player];magical reset;magical hud race human;magical class unlockall;magical unlockall;time set midnight;weather clear;tp @s ~ ~ ~ 0 0;summon iron_golem ~ ~ ~8 {NoAI:1b,Health:300f,Tags:[mark]};magical array clear;110:magical array plant 0 0 5 4;114:magical array plant 4 1 5 4;118:magical array plant 8 -1 6 4;122:magical array plant 12 0 5 4;126:magical array plant 16 -2 6 4;130:magical array plant 20 1 5 4;160:magical array bind @e[tag=mark,limit=1];200:tp @e[tag=mark] ~ ~ ~22;260:magical array strain 84" -PautoScreenshot=170,205,212,222,235,266,270,276 -PautoExit
```

**8 — the Bearing overlay.** A hold, so named `-PautoClick` actions drive it; a screenshot one tick
after an action catches the glide mid-flight.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlockall;magical unlockall;time set day;weather clear;tp @s ~ ~ ~ 0 0;magical hud equip 1 the_bearing;magical array clear;110:magical array plant 0 1 3 3;114:magical array plant 4 0 4 3;118:magical array plant 8 -2 6 5;122:magical array plant 12 -1 3 3;126:magical array plant 18 2 5 4;130:magical array plant 21 -3 4 3" -PautoHold=cast_slot_2 -PautoClick="180:hold;192:scroll-down;200:scroll-down;212:click-right;224:scroll-down;238:click-right;252:release" -PautoScreenshot=186,193,201,213,225,239,258 -PautoExit
```

The Bearing is the one capture taken at **day**, deliberately: the frameless scrim's whole
justification is that unbacked text on noon sand reads at 1.45:1, and a midnight screenshot would
hide the failure the scrim exists to prevent.

**9 — the Keel, ridden, third person.** Sword Rider and above.

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCamera=third_back -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;kill @e[type=!player];magical reset;magical hud race human;magical class unlockall;magical unlockall;time set midnight;weather clear;forceload add 68 68 132 132;tp @s 100 200 100 0 0;76:fill 68 118 132 132 118 68 smooth_stone;90:tp @s 100 119 80 0 -8;magical array clear;110:magical array plant 0 0 3 4;114:magical array plant 8 -3 4 4;118:magical array plant 16 -3 4 4;160:magical-debug skill magical:the_keel" -PautoScreenshot=166,176,192,216,248 -PautoExit
```

The dev world persists between runs, so `forceload add` plus twenty ticks must precede any `fill` or
the commands run on unloaded chunks, and `gamerule commandModificationBlockLimit 1000000` is needed
if a run ever clears the air above.

---

## 10. File manifest

### Pure core — new

| File | |
|---|---|
| `magic/sword/Station.java` | the 18-bit record, packing, separation, unit bearing |
| `magic/sword/SwordRules.java` | the four rungs' caps and four booleans |
| `magic/sword/PlantResult.java` | the seven named refusals |
| `magic/sword/Bind.java` | HELD / SET / RIDDEN / BOUND / SUNK |
| `magic/sword/Frame.java` | origin, yaw, pitch, scale |
| `magic/sword/SwordArray.java` | the structure, plant/pull/bill/settle/save/load |
| `magic/sword/Settlement.java` | shed slots and the strain left |
| `magic/sword/ArrayPose.java` | the one arithmetic both sides run |
| `magic/sword/Projection.java` | forward / below / covers / mirror |
| `magic/sword/SwordMath.java` | every damage number in the kit |
| `magic/sword/SwordWorld.java` | the adapter interface, five methods |

### Server — new

| File | |
|---|---|
| `magic/sword/LevelSwordWorld.java` | the only class here that knows what a `Level` is |
| `magic/sword/SwordService.java` | live half per UUID+dimension, slow-tick settle, `payFor`, `forget` |
| `magic/sword/SwordPassives.java` | the one `ClassPassiveHandler`, four ids, `forget(UUID)` |
| `magic/sword/SwordRiteService.java` | the Answering: the toss map and the 20-tick sweep |
| `magic/sword/SwordRiteEvents.java` | `@EventBusSubscriber` on `ItemTossEvent`, early-out on the first field read |
| `magic/sword/SwordDamageTypes.java` | `magical:sword_strain`, in the four bypass tags |
| `magic/skill/sword/CallTheBladeSkill.java` | `SkillModule` |
| `magic/skill/sword/TheBearingSkill.java` | `SkillModule`, `holdHint` |
| `magic/skill/sword/TheKeelSkill.java` | `SkillModule`, `selfManaged` |
| `magic/skill/sword/LooseSkill.java` | `SkillModule`, plain press |
| `magic/skill/sword/BelowSkill.java` | `SkillModule`, plain press |
| `magic/skill/sword/OneBladeSkill.java` | `SkillModule`, `holdGated`, `counterWindowTicks() 12` |
| `magic/cast/MagicCastContentSword.java` | the six `register()` calls |
| `entity/sword/SwordArrayEntity.java` | one per wielder, zero new accessors |
| `entity/sword/SwordBladeEntity.java` | one per detached blade, `VerseBodyEntity`-shaped flight |
| `network/PullStationsPayload.java` | one int mask |
| `magic/sword/SwordGameTests.java` | eleven `@GameTest` methods, one batch each |

### Client — new

| File | |
|---|---|
| `client/SwordBearingOverlay.java` | scrim 0x8C, the azimuthal plot, scroll and mouse arms |
| `client/SwordBearingLayout.java` | pure geometry, no `Font`, no `GuiGraphics`, `List<Rect>` |
| `client/SwordBearingInput.java` | `tickSlot` with its own `WAS_DOWN[]` |
| `client/SwordKeelClient.java` | the ride's movement, `PlayerTickEvent.Pre`, local player only |
| `client/renderer/sword/SwordArrayRenderer.java` | stations, threads, strain tint, `state.radius = 0` |
| `client/renderer/sword/SwordBladeRenderer.java` | the canted blade, the billboarded head |

### Modified

| File | Change |
|---|---|
| `classes/MagicalClassDefinition.java` | `boolean secret` plus a 9-arg delegating constructor |
| `classes/MagicalClasses.java` | four ids and four `register(...)` calls **at the end** of the static block; `isVisible`; `startingRoots`/`isStartingRoot` on the property; `classGranting` skips secret |
| `classes/ClassTreeLayout.java` | four `place(...)` calls at bearing −54, radii 210/380/470/545 |
| `client/screen/ClassTreeScreen.java` | `isVisible` gate in `drawNodes` :194 and `drawEdgePass` :137 |
| `tower/DungeonTowerService.java` | skip secret roots in `addClassRewards` :243 |
| `magic/PlayerMagicState.java` | the `swordArray` field through five sites; `hasAnyRootClass()` excludes secret roots; clamp mana when `maxMana` shrinks |
| `magic/MagicContent.java` | six `register(...)` lines; six ids into `CLASS_REWARD_SKILLS` |
| `magic/MagicSchool.java` | `SWORD(0xB9C4CE)`; **`isForbidden()` gains SWORD** |
| `magic/MagicAttribute.java` | `SWORD`; `fromSchool` arm; three `counters()` arms |
| `magic/visual/SchoolMaterial.java` | the `SWORD` row, frame sides 14 |
| `magic/visual/StampId.java` | `EDGE` |
| `magic/visual/EmblemId.java` | six constants with atlas cells |
| `magic/MagicPassiveContent.java` | four `forbiddenPassive(...)` registrations with the comment saying why |
| `magic/passive/ClassPassiveEffects.java` | `SwordPassives` into the `List.of` |
| `magic/MagicGameplayEvents.java` | early return for `magical:sword_strain`; `SwordService.forget` and `SwordRiteService.forget` in `onPlayerLogout` |
| `magic/cast/MagicCastContent.java` | `MagicCastContentSword.register()` in `init()` |
| `registry/MagicalEntities.java` | `sword_array`, `sword_blade` |
| `registry/MagicalCommands.java` | `/magical array …`; suggestion filters at :730/:746/:762; a comment on `unlockall` |
| `network/MagicalNetwork.java` | one `.playToServer` arm on the `"9"` registrar |
| `client/MagicalClientEvents.java` | `SwordBearingInput.tickSlot` arm **with the `consumeClick` drain**; the screen-open finish arm; scroll and mouse-button arms; two `registerEntityRenderer` calls |
| `client/hud/HudLayers.java` | `SwordBearingOverlay.render` in `renderSelector` |
| `client/hud/HudState.java`, `HudLayout.java`, `HudPalette.java`, `HudKind.java` | the draw arc and its string |
| `client/renderer/forge/ForgeElementAccent.java` | a SWORD row for the One Blade release |
| `assets/magical/lang/en_us.json` | 6 skills × 2, 4 passives × 2, 4 classes × 2, `school.magical.sword`, `tier.magical.below.3` **renamed to "Sword"**, five refusal messages, two hold hints |
| `data/magical/damage_type/sword_strain.json` + four tag files | the bypass, mirroring `blood_price` |

### Tests

`SwordArrayTest`, `SwordMathTest`, `ArrayPoseTest`, `ProjectionTest`, `SettleTest`,
`PlayerMagicStateSwordArrayTest`, `SwordSchoolTest`, `ClassVisibilityTest`, `ClassTreeExtentTest`,
`ClassLangKeysTest`, `SwordBearingLayoutTest`, `SwordSilhouetteTest` (all new); plus the edits to
`ContentLangKeysTest`, `CodexButtonRangeTest`, `NewContentIntegrationTest` and
`HudSnapshotBudgetTest`.

---

## 11. Risks, and what we are deliberately not building

**`MagicSchool.isForbidden()` is an `==` chain and adding a school there fails silently.**
`MagicAttribute.fromSchool` and `counters()` are exhaustive switches and will refuse to compile,
which is the safe half. `isForbidden()` and `SchoolMaterial.of()` are the unsafe half: one makes the
kit behave as a positive school to every caller, the other renders every one of its six skills
arcane-blue with a fully green build and no log line. Both ship in the same commit as the enum
constant or neither does.

**`ClassTreeLayout.extent()` is a global, invisible regression surface.** It feeds `fitZoom()`, and
`ClassTreeScreen:227` only draws node names at `zoom >= 0.40F` against a current fit of about 0.427.
A node placed outside the outer ring drops every class label in the Paths of Power for every player,
owned or not, and nothing fails. `ClassTreeExtentTest` is the only defence and it must land before
the `place()` calls do.

**A self-managed or hold-gated handler is free unless the service bills.** `castViaRegistry` returns
before stats, mana, aim, windup, fizzle and cooldown for both. The entire Authority of Causality
shipped costing nothing because nobody had written `payFor`, and nothing in the build said so — a
cost that is never taken looks exactly like a cost that is never needed.
`everyPressInTheKitIsPaidFor` is not optional.

**`wound()` needs both halves and fails in opposite directions.** Without clearing
`target.invulnerableTime`, a six-blade Loose lands one blade of damage and looks like a mechanics
bug. With it cleared and no per-blade per-victim internal cooldown, twelve blades in one tick is an
instant delete. One game test, both assertions.

**The Sword God volley must be measured and signed off, not discovered in play.** Twelve stations at
3 Edge with 60 strain is `12 × (2.0 + 6.0 + 15.0)` = 276 half-hearts if every blade lands, and it
will not — the forward projection is at most half, the fuse caps at 48 and the blast at 72, the strain
term caps at +24, and the wielder bleeds the whole time. But a game test must put a real number on a
full volley and the number goes in the commit message.

**`class.magical.*` and `passive.magical.*` are untested by anything in the repo today.** Eight class
strings and eight passive strings can ship as raw keys drawn on every tree node and every codex row
with a green build. Two small tests, written with the feature, not after it.

**A class passive on a hidden non-starting-root tree turns `ClassTreeTest` red for an unrelated
reason.** All four passives use `forbiddenPassive()`. If anyone later "fixes" one to `classPassive()`,
the build fails with a message about a passive nobody grants and about a class they were not editing.

**Below committing to a point rather than to a body will read as a bug to players trained on homing
AoEs.** The telegraph mark must be drawn at the committed point and must never be drawn under the
target once the target has moved, or the whole counterplay reads as "the eruption is broken" instead
of "you dodged". This is a playtest gate, not a code gate.

**The rite could fire by accident on a shared server** — four swords thrown into a mob farm, walked
into by somebody who has no idea. That is mostly the point, and the guards (night, open sky, one
thrower, 2-block separation, 8-block radius, the thrower at the centroid, a 400-tick window, an
already-chosen starting class) make it deliberate. If playtesting shows it firing too often, the
honest lever is `RITE_RADIUS`, not a rarity roll: the Array has no random number in it and its unlock
should not either.

**`MagicalNetwork.registerPayloads` uses `event.registrar("9")`**, not the `"1"` CLAUDE.md documents,
and the package holds 47 payload records, not 24. Copying the doc's string splits the protocol and
every client is rejected with no useful message. There are also three `AttachmentType`s, not two.

### What we are deliberately not building

**Not a fifth rung, and not tier 4.** The Skein's five-rung ladder was the better design on the
jury's own reading, and it costs three permanent surfaces: `ClassTreeLayout`'s `tier <= 3` loop with
its length-4 `RING`/`SPAN` arrays, `MagicalCommands:789`'s identical bound (which would silently make
Sword God unreachable by the one command every capture script uses), and a class that can never be
auto-placed again. Four rungs flipping four rules delivers the same "each rung changes how it plays"
with none of that.

**Not a ripening clock.** The Mound's best idea — an element's worth being a function of how long it
has sat — is genuinely new to this mod, and it is refused for two reasons. It makes the class's
primary expression a 1200-tick timer the player cannot influence, and it makes the biggest number in
the kit completely invisible to the person being hit. The Array's equivalent legibility is carried by
the threads and by the blade count, both of which the opponent reads for free.

**Not a Blockbench model.** `ModelPart.render` is invisible to `FxBudget`, `EldritchModels`'
folder and texture paths are hardcoded string literals, and a `.geo.json` dropped anywhere else loads
nothing while the entity still spawns, ticks and damages. Painter geometry through `FxMesh.prism(4)`
on `shardBody()` costs 32 quads a blade and the throttle can see every one.

**Not a synced `ItemStack` per blade.** The Sheaf's "your blades are twelve of your own Duskfall" is
the best single image anyone proposed and it costs nothing in assets — but an `ItemStack` is the
heaviest thing in the mod to sync, twelve of them is ~1,600 quads the budget cannot see, and the
Array's blades are conjured steel rather than borrowed weapons. It stays on the shelf as a cosmetic
option if the palette ever proves too plain.

**Not a new core shader.** Twelve exist. Every look here is `shardBody`, `filamentBeam`, `plasmaOrb`,
`groundMarkAdd`, `glyphInk` or `rendertype_forge_edge`, and if a genuinely new look is wanted later
it is a `CustomPainters.register(id, painter)` entry, which is how all seven schools already do their
bespoke work.

**Not an Order menu, a posture wheel, or a station mode enum.** The single sharpest finding across
all four proposals is that a per-object mode field is where a flying-sword kit turns back into five
projectile skills with a dropdown. A station carries a bearing, a reach and an Edge. There is nowhere
in the Array to put a mode, and that is the design.