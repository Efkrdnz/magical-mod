# The Nine Authorities

**Status:** design, not built. Two of the nine exist in code; the other seven are specified here.
**Date:** 2026-09-15, rewritten 2026-09-17

---

## 1. What an Authority is

A school is a set of spells. An Authority is a concept obeying you.

The distinction has to be enforced or it rots: the moment an Authority skill would be at home on a
tier-5 class reward, it is not an Authority skill. The test applied to every entry below is *would a
player describe this as a power, or as being a god of something?*

The framing that ties Authorities to the rest of the mod: **the schools are the scraps mortals stole
of concepts an Authority holds entire.** The Spatial school is what a mage can beg from extension;
the Authority of Space simply tells extension what to do. That is why school names and Authority
names collide (SOUL, SPATIAL, CHAOS) — the collision is the point, not an accident.

### The six rules

1. **One Authority per wielder.** Already true: `PlayerMagicState.authorityId` is a single field, and
   `setAuthority` takes the previous one's skills back.
2. **One wielder per Authority, world-wide.** *Not implemented.* Two players can both hold Space
   today. See §5.1.
3. **Only an Authority answers an Authority** — and only the two the ring says. See §3.
4. **An Authority's skills come with the Authority.** Shipped in `be441d5`: `ALL_SKILLS` now excludes
   `AUTHORITY_SKILLS`, so `/magical unlockall` cannot grant them. `randomProficiencyReward` already
   filtered them and no class grant names one, so that was the only leak.
5. **No Authority of Time.** Time is Space's ("space/time"). `time_flow` is already one of
   `manipulate_space`'s twelve law categories and that is where it stays.
6. **An Authority is a machine, not a skill list.** This is the rule the first draft of this document
   broke, and §4 is written to it.

### Rule 6, stated properly

The two built Authorities are the specification, and they are built the same way:

**Space** is not four spells. It is a *grammar gated behind an anchor*. `create_subspace` raises a
bounded domain; nothing else in the kit works without it. `manipulate_space` then legislates physics
inside that domain from twelve law categories × their operations × five target groups — several
hundred expressible states, authored by the player, persisting on the domain entity. The player is
not casting. They are legislating.

**Soul** is a *persistent graph plus operations on it*. `soul_vow` binds souls and the bonds survive
death, respawn and dimension change; swap, call, sever and step are operations on the graph the
player built. Semi-immortality is not a perk bolted on — it falls out of the fact that a bond exists.

So every Authority below has:

- an **anchor**: the ability that creates the context, which the rest of the kit needs;
- a **grammar**: the ability that only functions inside or against that context, composed from named
  axes with named values;
- **persistent authored state** the player builds up, which is saved, visible and worth protecting;
- an **emergent property** that falls out of the structure rather than being designed as a perk.

The whole kit arrives at once when the Authority does, so the parts may depend on each other freely.

What this rules out, permanently: press-a-button-and-damage-happens, flat percentage buffs, "clear
all" convenience macros, undo buttons, and menus of five effects with an axis label stapled on. Every
one of those appeared in the first design pass for these nine, and every one was cut.

---

## 2. The Nine

| # | Authority | Commands | Colour | School / Attribute | State |
|---|---|---|---|---|---|
| 1 | **Space** | extension, distance, dimension — and time | `0x88DFFF` | SPATIAL (exists) | built |
| 2 | **Soul** | the self that persists: identity, bonds | `0xD8F0FF` | SOUL (exists) | one skill |
| 3 | **Mana** | the substrate magic is made of | `0xF0F4FF` | **new** MANA | design |
| 4 | **Life** | the whole cycle: growth, vitality, decay, death, undeath | `0x66DD66` | **new** LIFE | design |
| 5 | **Chaos** | possibility held open; the breaking of certainty | `0xD946EF` | CHAOS (exists) | design |
| 6 | **The Word** | to name a thing is to make it so | `0xEFE4C0` | **new** WORD | design |
| 7 | **Devouring** | eat a thing and take what it was | `0x8C2F39` | **new** DEVOURING | design |
| 8 | **Dreams** | unreality made real | `0x6E5AA8` | **new** DREAM | design |
| 9 | **Dominion** | every living thing knows where it stands | `0xC0A000` | **new** DOMINION | design |

Six new `MagicSchool` values and six new `MagicAttribute` values. That is the largest single
addition to those enums the mod has ever had, and every `switch` over them has to be revisited —
`MagicAttribute.fromSchool` and `counters` most of all. See §7.2.

The codex is safe: `PyramidLayersTest.eachLayerBelowTheLineHoldsOneSchool` exempts the Authority row
(`authority || schools.size() == 1`), so nine schools sharing tier -6 is already legal.

### Concepts considered and rejected

**Time** — it is Space's. **Motion** — Space owns where things are, and `manipulate_space` already
has velocity and acceleration laws. **Knowledge** and **Mind** — cognition is a small idea next to
the rest of this list, and mind-control is miserable to be on the receiving end of. **Freedom** — the
absence of a thing is not a thing. **Fate** — it is the flip side of Chaos, and this roster does not
carry both faces of one coin. **Threads** — Fate wearing a coat. **Light**, **Storm**, **Judgement**,
**Hunger**, **Order**.

Three more were cut after the first draft of this document, on the mod author's ruling:

- **Form** ("what a thing is made of") — *sounds weak.* It is also three other Authorities wearing a
  coat: shaping matter is Space, remaking a body is Life, and turning a thing into another thing is
  the Word. Nothing was left once those were taken back.
- **Law** ("oath and obligation") — *sounds weak, and law stuff is mostly bounded to space.* That is
  exactly right: every version of it was a zone with rules in it, which is what a subspace already
  is. Its one salvageable idea — rank following a *person* rather than a *place* — became Dominion,
  and Dominion is built specifically so that it has no radius. See §4.9.
- **Death** as its own Authority — *life and death are opposite sides of the same coin.* Folded into
  Life, which now owns endings, decay, undeath and the raising of the dead. That makes Life the
  largest Authority on the roster, so §4.4 gives it a hard economy instead of an arbitrary cap.

**Blood still has no Authority, deliberately.** The Blood school is a whole pillar — seven skills,
the Vessel, the sacrifice pact — and an earlier review flagged it as an unowned concept. It stays
unowned: blood is where the halves of Life meet, and it is the best illustration of the framing
above. A mortal pays in blood precisely because they cannot command the concept outright.

---

## 3. The counter ring

Rule 3 says only an Authority answers an Authority, and only the ones the ring names. The nine sit in
a cycle in which **each counters the two after it**, loses to the two before it, and is a standoff
with the remaining four.

```
Mana -> Space -> Dreams -> Soul -> Dominion -> Life -> Devouring -> Word -> Chaos -> (Mana)
```

Eighteen edges. Every node has out-degree 2 and in-degree 2; nothing is unanswerable, nothing is a
free win, and no pair counters each other.

| Authority | Answers | Loses to | Standoff with |
|---|---|---|---|
| **Mana** | Space, Dreams | Word, Chaos | Soul, Dominion, Life, Devouring |
| **Space** | Dreams, Soul | Mana, Chaos | Dominion, Life, Devouring, Word |
| **Dreams** | Soul, Dominion | Mana, Space | Life, Devouring, Word, Chaos |
| **Soul** | Dominion, Life | Space, Dreams | Devouring, Word, Chaos, Mana |
| **Dominion** | Life, Devouring | Dreams, Soul | Word, Chaos, Mana, Space |
| **Life** | Devouring, Word | Soul, Dominion | Chaos, Mana, Space, Dreams |
| **Devouring** | Word, Chaos | Dominion, Life | Mana, Space, Dreams, Soul |
| **Word** | Chaos, Mana | Life, Devouring | Space, Dreams, Soul, Dominion |
| **Chaos** | Mana, Space | Devouring, Word | Dreams, Soul, Dominion, Life |

### The eighteen edges

| Edge | Why |
|---|---|
| Mana → Space | A subspace is raised with mana and refreshed with mana. Inside a Weave that has zeroed cost and flow, it cannot be raised at all. |
| Mana → Dreams | Branching is cheap; merging is paid in divergence. Raise the price and the dreamer cannot afford to bring anything back. |
| Space → Dreams | A branch must snapshot a region, and Space owns the region. A sealed boundary gives the merge nowhere to land. |
| Space → Soul | The lattice assumes its nodes are somewhere. Space owns separation — fold, seal, or simply move them, and swap, call and step arrive in the wrong place. |
| Dreams → Soul | Bind a soul inside a branch and you have bonded a copy. Merge with the living withdrawn and the node was never there. |
| Dreams → Dominion | Rank is a fact about the world. A branch is a world you author, so a hierarchy set in a dream comes back without ever passing the Throne. |
| Soul → Dominion | A bonded soul answers along its edge whatever its station. The flow does not care who is above whom. |
| Soul → Life | Life commands bodies. Soul is the part that is not the body: a soul with an outbound death edge does not die when the cycle says Ending. |
| Dominion → Life | Yggdrasil's reserve is taken from the dead. A being stationed above the tree cannot be taken by it. |
| Dominion → Devouring | You cannot eat what outranks you. Station the devourer beneath and its Hollow closes against half the world. |
| Life → Devouring | The Hollow rots and must be refilled. Life decides what is alive to eat, and can hold a whole region at a stage that yields nothing. |
| Life → Word | A name is what a thing has become. Hold it at Seed and there is nothing yet to study and nothing to revoke. |
| Devouring → Word | A spoken name is a construct hanging on a target. The Hollow eats constructs. |
| Devouring → Chaos | A suspension is an unresolved thing sitting in the air. Eat it and it digests: the branch that becomes real is the eater's. |
| Word → Chaos | Bind a name onto an event and it has exactly one outcome, permanently. There are no branches left to choose between. |
| Word → Mana | Name the Weave, then revoke it. To unname a field is to make it never-so. |
| Chaos → Mana | Mana's rules are statements of certainty about what a cast costs. A suspended cast has not cost anything yet, so the Weave never billed it. |
| Chaos → Space | A law delivers its consequence every tick. Suspend the consequence and the law fires into nothing. |

### Making the ring mechanical

None of this is code. `MagicCounterService` knows nothing about Authorities. The work:

1. `AuthorityRing` — a static, tested adjacency table with `counters(a, b)` and `answeredBy(a)`.
   A test pins out-degree 2, in-degree 2, and the absence of any mutual pair.
2. A hook in `MagicCounterService`: an Authority ability aimed at the wielder of an Authority it
   answers resolves in the answerer's favour; one aimed at a wielder whose Authority answers *it* is
   refused outright with a HUD line naming why.
3. The codex Authority row shows the ring for whichever Authority the player holds.

---

## 4. The nine systems

Every entry is **anchor → grammar**, then the rest of the machine. Costs use the existing Authority
band: 8–36 mana for the working parts, 72–84 for anything that ends a fight; cooldowns 8–1600 ticks.

### 4.1 Space — the Subspace (built), and the Fold

**Anchor** `create_subspace` — hold to charge, release to raise a physics domain (radius 5–16,
following or anchored, ten minutes). 22 mana, 40 tick cooldown. Held as
`PlayerMagicState.activeSubspaceEntityId`; the laws live on `SpaceSubspaceEntity` as synced data.

**Grammar** `manipulate_space` — 8 mana, 8 tick cooldown. One law per category, written onto the
active subspace and applied by its tick to everything inside.

| Axis | Values |
|---|---|
| Category (12) | GRAVITY, VELOCITY, ACCELERATION, AIR_RESISTANCE, PRESSURE, MASS, TIME_FLOW, VECTOR_FIELD, ENTROPY, FRICTION, BOUNDARY, COLLISION |
| Operation | per category — REMOVE / DECREASE / INCREASE / REVERSE / CONTROL / STOP / STASIS / SEAL / RICOCHET / … / CLEAR |
| Target group (5) | EVERYTHING, EVERYTHING_EXCEPT_USER, LIVING_ENTITIES, PLAYERS, PROJECTILES |

Roughly 354 expressible states. `SpaceLawPass` splits each law: the server carries the consequences,
each client carries its own player's movement. That split is load-bearing — see CLAUDE.md.

**The gap.** Space today commands *rooms*. It has no way to act on distance itself, and its domain
reaches only its own interior. One addition, in the same idiom:

**`fold_space`** — requires **two** raised subspaces, which is why Space alone may hold a second
domain, at double cost. Folding binds their interiors into one place: stepping into either exits the
other, a law written on one applies to both, and reach and line of sight pass through. It is the only
ability in the mod that needs two instances of its own anchor, and it turns "I made a room" into "I
decide what is far from what".

`pocket_dimension` and `spatial_arsenal` — with `singularity` and `dimensional_guillotine` cascading
beneath it — stay exactly as built.

**Emergent:** flight is not an ability. It is GRAVITY / CONTROL aimed at everything, and it stops
when the domain does.

---

### 4.2 Soul — the Lattice

**Anchor** `soul_vow` — mode 0 (`ACTION_BIND`) resolves to `soul_valley`, 74 mana / 1600 ticks, and
that stays exactly as it is: binding is the expensive opening move. A bond survives death, respawn
and dimension change. **Eight bonds maximum.**

**Grammar** `lattice_weave` — hold and aim at a bonded node to author one **edge**: what runs along
it, which way, and how much. 8–24 mana per write, 20 tick cooldown. Maintaining a written edge costs
nothing, which is what makes the graph a *structure* rather than a channelled spell.

| Axis | Values |
|---|---|
| Flow (8) | LIFE (healing), HARM (damage), **DEATH** (the killing blow itself), MANA, SENSE (what they see and hear), PLACE (where they are), WILL (status effects), SKILL (they may cast what you know) |
| Direction (4) | OUT (you → them), IN (them → you), BOTH (double cost), CHAIN (relays one further hop per bond, weakening each time) |
| Share (4) | a quarter, a half, all, SEALED (locked; neither you nor anyone else may rewrite it for a time) |

128 states per edge, eight edges. The operations that already exist become *reads of the graph*:
`swap` needs a PLACE edge, `call` needs one inbound, `step` moves along one, `sever` deletes a node
and everything incident to it.

**Emergent — semi-immortality, properly.** DEATH is a flow like any other. An outbound DEATH edge
means the killing blow goes *there* instead of to you, at the share you wrote. When every death edge
is spent or severed the blow has nowhere to go, and you enter collapse: `COLLAPSE_TICKS` (1400) in
which you are not yet dead and can still bind. None of that is a perk; it is what an edge does.

**Counterplay:** the carriers are visible and killable, and a CHAIN edge is a map of your allies
drawn for the enemy. Space moves your nodes out from under the lattice; Dreams bonds you to a copy.

---

### 4.3 Mana — the Weave

**Anchor** `claim_weave` — 28 mana, 200 tick cooldown. Claims the local mana field as a bounded
domain (radius 8–24), one per wielder. Everything *magical* inside is governed by what you write on
it. The rules persist on the domain entity and survive logout.

**Grammar** `weave_rules` — the same shape as Space's, over magic instead of physics.

| Axis | Values |
|---|---|
| Aspect (6) | COST, COOLDOWN, DURATION, SCHOOL (whether a school may function at all), FLOW (which way mana moves), MANIFESTATION (whether a cast produces anything) |
| Operation (6) | RAISE, LOWER, ZERO, INVERT, LOCK, RESTORE |
| Whose (3) | ALL, MINE, THEIRS |

108 states. **Anti-magic is not a skill.** It is SCHOOL / ZERO / THEIRS — the extreme setting of an
ordinary axis. That is what makes it terrifying rather than a button: it sits in the same menu as
COST / RAISE / THEIRS and COOLDOWN / LOCK / ALL, and the wielder chose it.

**Pure mana form** — `mana_form`, 36 mana, and it **only functions inside your own Weave**. You put
the body down and *are* the field: nothing physical can find you, you pass through blocks, you cannot
be targeted by anything that needs a target. Two constraints make it a system rather than
invulnerability. At the shell of the Weave you are forced back into a body, so the domain is now your
cage as much as your throne. And **every rule you wrote applies to you** — a wielder who inverted the
flow of everything has to live inside that.

**Emergent:** two overlapping Weaves cancel to neutral across the overlap — a dead zone where magic
behaves normally and neither wielder can legislate. It is also the cleanest in-fiction argument for
§5.1: two Authorities of Mana would produce a world-sized dead zone.

**Counterplay:** walk out. And the mod already has two schools that do not pay in mana — Blood pays
in the Vessel and in health, Eldritch pays in Notice — so a Weave that has zeroed every cost still
does not stop a Crimson Spear. That is not a balance patch; it is `BloodService.pay` and
`EldritchService.notice` working as they already do.

---

### 4.4 Life — Yggdrasil and the Cycle

**Anchor** `yggdrasil` — 84 mana, 1600 tick cooldown. A tree, and a **root network** that spreads
over the terrain from it as a visible border. The network is the domain, and it grows as the tree is
fed.

**Grammar** `weave_the_cycle` — commands the stage of anything inside the roots. **Outside the roots
nothing has a stage at all**, which is what makes this interlock real rather than decorative: there
is no natural lifecycle running in the world that the grammar merely accelerates.

| Axis | Values |
|---|---|
| Stage (8) | SEED, SPROUT, GROWTH, VIGOUR, FRUITING, WANING, ENDING, HOLLOW |
| Operation (6) | SET, HOLD, REVERSE, HASTEN, GRAFT (move a stage from one thing onto another), TAKE (end it and bank it) |
| Scope (3) | one creature, a kind, everything within |

144 states.

**The economy is the design.** `weave_the_cycle` is **not paid in mana. It is paid in Vitality**, and
Vitality has exactly one source: a thing that dies inside your roots does not simply die — it feeds
the tree. The reserve sits on the tree entity, visible, and whoever kills the tree takes it.

This is what lets Life hold death without a second Authority and without an arbitrary healing cap.
Raising the dead is SET HOLLOW → SEED. It is not gated by a cooldown; it is gated by having farmed
enough death to pay for it, out of a bank that is a target standing in the open.

**Emergent:** the wielder stops seeing deaths as losses. A battlefield inside your roots is income,
and the tree is the reason you will hold ground you would otherwise abandon.

**Counterplay:** kill the tree — everything in the network drops to HOLLOW at once and the reserve
spends itself into the ground. Fight outside the roots. Deny it deaths.

---

### 4.5 Chaos — Suspension

Chaos does not randomise; randomising is a spell list with dice in it. Chaos **holds possibility
open**.

**Anchor** `suspend_outcome` — 18 mana, 60 tick cooldown, **four held at once**. Catches an event
before it resolves: the blow has not landed, the block has not broken, the death has not happened,
the cast has not finished. It hangs in the air, unresolved and **visible to everyone**, holding the
outcomes that were actually possible for it.

**Grammar** `collapse_outcome` — operates only on what you are holding. 8–32 mana.

| Axis | Values |
|---|---|
| What was caught (6) | A BLOW, A BREAKING, A DEATH, A CASTING, A MOVEMENT, AN OPERATION (another Authority's) |
| How it resolves (6) | AS IT WAS, REVERSED (onto its source), ELSEWHERE (onto another target), DOUBLED, NEVER, **SPLICED** |
| Whose (3) | mine, theirs, the world's |

108 states. **SPLICED is the keystone**: a suspension collapses into an outcome that belonged to a
*different* suspension you are holding. It exists only because you hold several at once, so the
suspensions interlock with each other and not merely with the anchor. Someone's death resolves as
someone else's block breaking.

**The pressure.** An uncollapsed suspension resolves itself when its clock runs out, and it does not
resolve kindly. Holding four is holding four clocks, in public, where the enemy can count them.

**Emergent:** you can suspend your own death and simply decline to collapse it for a while. Everyone
watching sees it hanging over you and cannot know which way you will take it.

**Counterplay:** make him hold more than he can collapse, and wait. Word can BIND an event so it has
one outcome and nothing to branch into. Devouring eats the suspension itself.

---

### 4.6 The Word — the Lexicon

**Anchor** `study_name` — hold focus on a thing until its true name is learned. It costs **time and
exposure**, not a button press: common things take seconds, a person's name takes a long and obvious
while, and they feel it happening. The name enters your **Lexicon** permanently, saved across
sessions.

**Grammar** `speak_name` — [a name you hold] × [operation] × [target]. 12–36 mana.

| Axis | Values |
|---|---|
| Name (7) | a creature's, a block's, an item's, an effect's, a skill's, a place's, **a person's** |
| Operation (4) | SPEAK (the target takes on what the name means, for a while), BESTOW (the target *is* that thing), REVOKE, BIND (permanent and unrevokable — **including by you**) |
| Target (5) | a creature, a block, an item, an effect hanging on something, **a construct another Authority raised** |

140 states. Two cells carry the whole Authority:

- **REVOKE on a thing that has no bestowed name strips its own.** A thing with no name is not that
  thing any more. That is the "to unname is to make never-so" half, and it is why there is no
  separate unname ability — it is a cell in the table, not a button beside it.
- **BIND is a commitment, not an upgrade.** Once bound, nobody may revoke it, and that includes the
  speaker. Binding a wolf as a boulder means you have a boulder now, for good.

**Emergent:** the Lexicon becomes a record of everything you have ever properly looked at. Power is
gated by *having been there* rather than by levels, and it is inspectable, so a Lexicon is a
biography.

**Counterplay:** stay out of sight — study is slow and visible. Life holds a thing at Seed so there
is nothing yet to study. Devouring eats the name off the target.

---

### 4.7 Devouring — the Hollow

**Anchor** `devour` — eat a thing into the Hollow. The entry carries **what it was**. Eight entries.

**Grammar** `wear` — put on up to **three** entries at once: a loadout built out of your own kills.
Swapping is free but slow enough to be a decision.

| Axis | Values |
|---|---|
| Family (10) | PREDATION, HIDE, FLAME, VENOM, VOICE, STEP, FLIGHT, STONE, ROT, **AUTHORITY** |
| Potency (4) | thin, whole, rich, sovereign |
| Slot (3) | first, second, third |

`digest` destroys an entry for permanent growth — a real choice, because the entry is then gone.

**The pressure.** Entries **rot**, and the rich ones rot fastest. You are never full; an empty Hollow
is simply weakness, not a hidden bonus. That is what makes Devouring a character rather than a
toolbox: it has to keep hunting.

**AUTHORITY is the cross-authority hook.** You can eat another Authority's *construct* — a subspace,
a stretch of root network, a held suspension, a bestowed name, an open dream branch — and wear what
it was. It is the only Authority whose power is other Authorities' work.

**Emergent:** you look like what you have eaten. Worn families bend the player model, so an opponent
reads your loadout off your silhouette before you are in range of them.

**Counterplay:** do not die near him. And the general counter is structural — a wielder who raises no
constructs gives Devouring nothing to take.

---

### 4.8 Dreams — the Dreaming

**Anchor** `branch_reality` — snapshot a region of the real world into the Dreaming, a fourth
datapack dimension following `pocket_space`, `dungeon_tower` and `chronos_end`. One branch at a time.

**Inside the branch there is no grammar, and that is the point.** You act freely: build, break, kill,
burn it down. Nothing you do there is real yet, so nothing there needs a rule. Unreality is free.

**Grammar** `merge_waking` — the machine is the *merge*. Category by category, you rule what crosses
back.

| Axis | Values |
|---|---|
| Category (8) | the ground, what was built, the living, the dead, what was carried, your body, your wounds, the hours |
| Verdict (3) | IMPOSE (the dream overwrites the waking world), WITHDRAW (the waking world is undone to match what the dream left untouched), KEEP (both stand) |

3⁸ = **6,561 distinct merges**, and every one of them means something. "The dead: impose. Everything
else: withdraw" is *I fought you for an hour and brought back only your death.* "The hours: withdraw"
is *I did not spend them.* "My wounds: withdraw; my body: impose" is the reason this Authority
frightens people.

**The cost is divergence:** mana in proportion to how far what you impose has drifted from the
snapshot. Rewriting a hillside is ruinous; bringing back a single death is cheap. The player prices
their own ambition, which is a better limiter than a cooldown.

**Border with Space, policed:** `pocket_dimension` is an *empty private room*. A branch is an
*editable copy of a real place, with a way back*. They are not the same feature and must not drift
into each other.

**Counterplay:** the merge point is fixed and public — kill him as he wakes. Mana prices the merge
out of reach and strands him. Space seals the region the merge has to land on.

---

### 4.9 Dominion — the Throne

This replaces the rejected Law, and it is built specifically so that it cannot fail the way Law
failed. Law was always a zone with rules in it, which is what a subspace already is. **Dominion has
no radius.** It rules people, anywhere, for good.

**Anchor** `raise_throne` — a seat, placed in the world. You must be **sitting on it** to legislate.
That is the entire constraint: rank costs nothing to *hold* and requires you to come home to
*change*.

**Regard** — looking at a being enters it into the hierarchy. Permanently. Globally. A being you have
never looked at is not in the hierarchy at all, and is untouched by every ruling you have ever made.

**Grammar** `set_station` — [a being you have regarded] × [station] × [what the station governs].

| Axis | Values |
|---|---|
| Station (5) | above you, beside you, beneath you, beneath all, beneath notice |
| Bearing (5) | STRIKING (raising a hand to those above), SPEAKING (obeying a command), FLEEING (leaving), TAKING (taking from those above), BEING SEEN (being perceived at all) |
| Verdict (3) | MAY, MUST, MAY NOT |

75 rulings per regarded being, and the hierarchy is world-wide and permanent.

**Emergent — this is what the whole Authority exists for.** Anything beneath you whose STRIKING is
MAY NOT *cannot start a fight with you*. Not "takes reduced damage": cannot begin. Anywhere on the
map, at any distance, with no zone existing anywhere at all. That is what being the god of rank
means, and it falls out of the table rather than being written as a perk.

**Counterplay:** the Throne is a place, and you must return to it to change anything — an enemy who
finds it knows where you must eventually be. A being you have not regarded is outside the system
entirely. Soul's flows answer along their edges whatever the station; Dreams sets a hierarchy inside
a branch that never passed the Throne.

---

## 5. Cross-cutting mechanics

### 5.1 Exclusivity — the ledger (not built)

Rule 2 needs world state, and the mod has no `SavedData` precedent yet.

`AuthorityLedger extends SavedData` — `Map<ResourceLocation, UUID>`, one holder per Authority.
`PlayerMagicState.setAuthority` consults it and refuses if the Authority is taken; `clearAuthority`
releases it. An offline holder still holds. Taking an Authority from someone therefore requires them
to lose it first, which is what §5.2 is for.

### 5.2 Acquisition (not built)

Today the only path is `/magical authority <id>`. Nine Authorities want nine acquisitions, and they
should be the concept's own test rather than nine identical pedestals — study a name nobody offered
you to earn the Word, be eaten and come back to earn Devouring, sit a throne nobody granted you to
earn Dominion. Out of scope here beyond the note that the ledger has to exist first.

### 5.3 One domain entity, not three

Space's subspace, Mana's Weave and Life's root network are the same thing three times over: a bounded
entity carrying synced rule data, ticking over whatever is inside, following or anchored, with the
server/client split that `SpaceLawPass` already defines.

**Consolidate before the third one is written, not after.** A `DomainEntity` parameterised by kind,
with a resolver per Authority (`SpaceLawResolver`, `WeaveRuleResolver`, `CycleResolver`), all sharing
the pass split and the chunk-ticket handling. Writing Mana's Weave as a copy of
`SpaceSubspaceEntity` would guarantee that every physics bug gets fixed twice and the second fix gets
forgotten.

### 5.4 State, and where it lives

`PlayerMagicState` syncs wholesale on every change and is already large. The rule for these nine:

- **On the attachment:** ids and small authored graphs only — `activeSubspaceEntityId`, the Soul
  lattice (8 nodes), the Hollow (8 entries), held suspensions (4), the active branch id, the Throne's
  position. Each gets its own dirty flag and its own payload, the way `CooldownSyncPayload` already
  works, rather than riding the whole-state blob.
- **On the domain entity:** every rule set — Space's laws, Mana's rules, Life's stages and Vitality.
- **On `SavedData`:** the exclusivity ledger, the Word's global name registry, and Dominion's
  hierarchy, which is world-wide and permanent and therefore not player state at all.

### 5.5 What is already done

- `be441d5` — `unlockall` no longer grants Authority skills (rule 4).
- `AuthorityGrantTest` — six tests pinning grant, revoke, swap and the `unlockall` exclusion.
- `AuthorityContent` maps an Authority to its skill ids; `setAuthority` / `clearAuthority` move the
  whole kit at once, which is the mechanism rule 6 depends on.

---

## 6. What this costs, and the build order

Seven Authorities, each a domain or a graph plus a screen. Honest ordering — cheapest and least
entangled first, each one proving something the next needs:

1. **Space** ✓ built — the grammar template.
2. **Soul** ✓ bound — needs the lattice grammar (§4.2) to stop being a third of an Authority.
3. **Devouring** — no new entity, small state, a familiar inventory-and-loadout screen. Proves the
   authored-loadout shape at the lowest risk.
4. **Mana** — the second domain, and therefore the one that forces §5.3's consolidation. Do not
   attempt it before the `DomainEntity` refactor.
5. **The Word** — no new entity, a `SavedData` registry, and a lexicon screen much like the codex.
   Proves world state ahead of the ledger.
6. **Life** — the third domain, cheap once §5.3 exists; its real work is the Vitality economy.
7. **Chaos** — event interception is the fragile part (`LivingDamageEvent` and its neighbours). It
   needs the rest stable first.
8. **Dreams** — a new dimension, snapshot NBT, and a merge screen. The most expensive single item.
9. **Dominion** — a permanent global hierarchy touching targeting, AI and damage. Last, because it is
   the one that can break everything else.

---

## 7. Open questions — the mod author's, not mine

1. **Dominion, or the Abyss.** §4.9 is the second attempt at this slot; the first was Law and it was
   rejected. The Throne fixes the specific complaint — rank follows a person, nothing has a radius —
   but if it still reads as bureaucracy rather than godhood, the standing alternative is an
   **Authority of the Abyss**: depth, pressure, what is beneath, and what should not have surfaced.
   Swapping it changes no ring topology, only that node's eighteen-edge justifications.
2. **Six new schools, or fewer.** Every new `MagicSchool` and `MagicAttribute` means revisiting
   `counters()` and every switch over them. Mana, Life, Word, Devouring, Dream and Dominion could
   borrow ARCANE / PRIMORDIAL / VOID instead of minting their own.
3. **Does Sovereign Aegis still answer `singularity`, `dimensional_guillotine` and `soul_valley`?**
   Rule 3 says only an Authority answers an Authority, and Aegis is a class reward.
4. **Eight bonds, four suspensions, eight Hollow entries, three worn, one branch.** These caps are
   the balance surface of four whole Authorities, and they are guesses until something is playable.
