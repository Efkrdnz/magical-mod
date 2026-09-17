# The Authority of Chaos — the Pile and the Fracture

> Supersedes §4.5 ("Chaos — Suspension") of `2026-09-15-nine-authorities-design.md`, which is
> withdrawn. That design was `what-was-caught × how-it-resolves × whose` on three dials — the same
> three-wheel grammar the Authority of Space uses and the Authority of Mana used before the Weave
> was torn out. A third copy of it is the thing being rejected, not a variation on it.

## 1. What Chaos owns

> **Chaos owns a Pile in the world, and a Fracture in the hand.**

The **Pile** is a sparse map of *sites* to a single integer of **stress**. A site is a block
position or a living body. Every site has a **capacity** decided by what it *is*. A site holding
more stress than its capacity **gives way**, pushing that stress into its neighbours — and a
neighbour that was already full gives way in turn. That is an avalanche, and its size is not
bounded by anything the wielder spent.

The **Fracture** is the wielder's own answer to one question: *which way does a thing give?* It is
an ordered sequence of up to five **Faults**, and generation *n* of an avalanche uses Fault *n*.
So a cascade **changes character as it grows**, along a curve its owner authored.

**There is no random number anywhere in this Authority.** Self-organized criticality is the
canonical model of a system that is fully deterministic and completely unpredictable, which is the
actual definition of chaos. A design whose answer is "roll a dice, get an effect" is a spell list
with dice in it; this is the opposite of that, and the constraint is load-bearing rather than
decorative.

## 2. Why this is not a shape the mod already owns

| Already owned | Why the Pile is not it |
|---|---|
| **volume + per-tick laws** (Space) | No boundary, no radius, no anchor entity, no tick loop over an area. Nothing is declared and nothing is enforced. The Pile is matter lying on the world, touched only when something lands on it. |
| **a book of prices** (Mana) | Nothing *consults* the Pile. A writ is read at the instant some other thing resolves its numbers; the Pile **is** the thing that happens. |
| **a contract** (Soul) | No terms, no later payment. |
| **three-wheel grammar** (Space, and the torn-out Weave) | There is no pick-category × pick-operation × pick-target anywhere. At cast time the only choice is *where the crosshair is*. The Fracture is authored once and carried, not dialled per cast. |
| **composable recipe** (Arcane) | You never compose a cast. The Fracture composes the *propagation*, not the spell. |
| **point-buy pact** (Blood Sacrifice) | No budget, no boons-and-prices columns. |
| **a harvested currency** (Blood's Vessel) | **This is the one the first draft failed.** A "Measure" of grains milled off things that die near you, kept in a jar and spent, is the Vessel with a type tag. It is deleted outright: there is no jar, no milling, no grain types, and no inventory. Stress is paid for in mana, the currency every other skill in the mod already uses. |
| **a rising gauge** (Sin, Notice) | Nothing accumulates on the wielder. |
| **contact-spread infection** (`ContagionSkill`) | Grains do not reproduce. They are conserved, they move only when a capacity is exceeded, and they move through terrain as much as through bodies. Contagion spreads because it is alive; the Pile spreads because it is heavy. |

The Chaos *school* (pyramid layer −3) is left empty and untouched. None of this is random elemental
damage, and none of the four abilities would be at home as a tier-5 class reward.

## 3. The law with a direction

Entropy is the one law that knows which way time runs, so this Authority has no CLEAR:

> **Nothing here can be undone, and no ability removes stress from a site.**

A law has RESTORE and the Weave had it too. Chaos must not. Stress leaves a site by exactly two
routes: the site gives way, or it bleeds off slowly on its own. There is no verb for taking it back.

The same rule settles the balance problem that would otherwise sink preparation-as-a-weapon:

> **A site that has given way goes SLACK, and a slack site refuses stress for 600 ticks.**

Ground you have already avalanched is dead ground. You cannot dig the same trap twice, an hour of
seeding cannot be re-used on the next opponent, and the wielder who prepared for a week does not
simply beat the wielder who arrived five minutes ago.

## 4. The abilities

Four, and the middle two are nothing apart.

### 4.1 `burden` — **Burden** (anchor: creates the Pile)
Tap: one unit of stress onto whatever the crosshair is on — a block face, a creature's shoulder, an
item, another Authority's construct. **It does nothing at all, visibly.** 2 mana, 4 tick cooldown,
because the design needs you to place dozens. Sneak-tap burdens yourself.

There is no hold-to-reveal on this skill, and it does not need one: stress is painted as motes for
*everyone* who can see it, all the time, which is what makes the counterplay in section 6 real. A
reveal only its owner could press would have made the ground secret, which is the opposite.

### 4.2 `fracture` — **The Fracture** (the toolkit: authored once, carried always)
Hold the slot and the Fracture opens: your ordered sequence of Faults, one per generation. Scroll
turns the Fault at the focused position; left/right mouse walks the sequence; release commits.
100 tick cooldown, so it cannot be re-authored inside a cascade.

This is the customizable toolkit and it is **portable** — it lives on the wielder, not on the
ground. Seven Faults across five positions is 16 807 distinct machines, available immediately, with
no grind, and two Chaos wielders' avalanches behave nothing alike.

| Fault | Which way a site gives |
|---|---|
| **SLUMP** | all of it to the *lowest* neighbour — a landslide that runs downhill and pools |
| **HEAP** | all of it to the neighbour that already holds *most* — rich-get-richer, converges to one point |
| **BLOOM** | split evenly among every neighbour — a spherical front |
| **HUNT** | only to *living* neighbours, ignoring blocks — a cascade that seeks bodies |
| **RECOIL** | back the way it came — the cascade eats its own source |
| **SHED** | passes nothing on; spends the stress as force and harm where it stands — a violent terminator |
| **ROOT** | keeps the stress and raises its own capacity — a sink that swallows a cascade |

SLUMP and HEAP are the two that make long cascades; BLOOM widens; HUNT aims; RECOIL turns one
around; SHED and ROOT end one, loudly or quietly. A Fracture is read as a sentence:
*SLUMP → SLUMP → HEAP → SHED* is a landslide that gathers, converges, and detonates.

### 4.3 `last_grain` — **The Last Grain** (inert without a Pile)
Aimed at a site holding stress: adds exactly **one** unit of pressure, from nowhere, not from
anything you are carrying. 4 mana. On a site below capacity it produces a click and literally
nothing else. On a site at capacity it starts the avalanche.

Its power is entirely borrowed from state you built, and it is deliberately, insultingly small.
The whole skill expression is reading the ground and knowing which shoulder is the one.

### 4.4 `criticality` — **Criticality** (escalation; inert without a Pile)
Stops adding and lowers the ground instead: **every capacity in the loaded Pile drops by one** for
200 ticks. Everything that was stable is now at or over, and the whole thing lets go at once, in an
order nobody chose, caring about nobody's side. 72 mana, 1200 tick cooldown.

Its magnitude cannot be balanced by numbers, because its magnitude is the work behind it. On virgin
ground it is 72 mana for a shimmer.

## 5. Capacity, and the counter-ring made physical

Capacity is a property of what a site **is**, never of who owns it:

| Site | Capacity |
|---|---|
| stone-like block | 4 |
| dirt, sand, wood | 2 |
| glass, leaves, anything fragile | 1 |
| a living body | 2 + armour |
| a player | 3 |
| **another Authority's construct** | **1** |

That last row is the counter-ring expressed as a physical property rather than a table lookup: a
subspace boundary, a soul lattice, an eldritch construct is the most fragile thing on the field.

**The wielder is a site.** You hold stress like any other body, and an avalanche spills into
whatever it touches, and you are a thing that touches the ground. There is no except-the-caster
clause, because the Pile is matter and matter does not know who made it.

## 6. What emerges rather than being written

- **Cascades produce compounds nobody authored.** Stress you placed meets stress you did not,
  because a third site gave way into both. This is the one good idea in the withdrawn Suspension
  design — held things interacting with *each other* rather than only with the anchor — done
  physically instead of grammatically.
- **The topology is alive.** A mob walking past a loaded block becomes its neighbour for as long as
  it is touching. The graph moves while you are looking at it.
- **Counterplay is physical and available to everyone.** Stress is visible. Break the block it sits
  on and it falls. Bodies shed it when struck. Deny the ground.

## 7. Scope

**Pure and unit-tested** (no server needed): `Pile` — the sparse map, capacity lookup through an
injected function, `giveWay` per Fault, the cascade with a per-tick budget, slack, decay.
`Fracture` — the ordered sequence and its editing.

**Server:** `PileService` binds `Pile` to a level, resolves neighbours, applies force and harm on
SHED, and runs the budget on `ServerTickEvent.Post`. `ChaosAuthorityService` owns the four casts.

**Client:** `FractureOverlay` (hold-to-author, the `WritOverlay` idiom), `ChaosAuthorityInput`,
stress shown as world-space motes so everyone can see it, and an aimed-site readout.

**The Pile is volatile and the Fracture is persistent.** Stress lives in memory per level and is
not saved; the Fracture lives on `PlayerMagicState`. That is thermodynamically apt — order decays —
and it sidesteps the grief, save-format and unbounded-growth problems in one decision. The kit the
player authored survives; the battlefield does not.

### Known gaps, stated rather than hidden
- The aimed-site readout is an actionbar line in v1. A proper crosshair pip belongs on a HUD layer
  through `HudBatch`, which means touching `HudSnapshotBudgetTest`; deferred.
- Topples do not break blocks. They move and harm. Terrain destruction is a server-admin problem
  and is not worth buying with a config flag on day one.
- A purely kinetic topple cannot push a *player* from the server (`DomainPass` documents why), so
  player displacement rides the damage path's knockback.
