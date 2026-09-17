# The Nine Authorities

**Status:** design, not built. Two of the nine exist in code; the other seven are specified here.
**Date:** 2026-09-15

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

### The five rules

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

---

## 2. The Nine

| # | Authority | Commands | Colour | School / Attribute | State |
|---|---|---|---|---|---|
| 1 | **Space** | extension, distance, dimension — and time | `0x88DFFF` | SPATIAL (exists) | built |
| 2 | **Soul** | the self that persists: identity, bonds | `0xD8F0FF` | SOUL (exists) | one skill |
| 3 | **Mana** | the substrate magic is made of | `0xF0F4FF` | **new** MANA | design |
| 4 | **Life** | growth, vitality, the making of living things | `0x66DD66` | **new** LIFE | design |
| 5 | **Death** | the ending, decay, the boundary | `0x2F1F3D` | **new** DEATH | design |
| 6 | **Form** | matter and shape: what a thing is made of | `0xA0826D` | **new** FORM | design |
| 7 | **Mind** | cognition: thought, knowing, attention, will | `0x9A7AFF` | **new** MIND | design |
| 8 | **Law** | oath and obligation binding a person | `0xC0A000` | **new** LAW | design |
| 9 | **Chaos** | probability, mutation, change | `0xD946EF` | CHAOS (exists) | design |

Six new `MagicSchool` values and six new `MagicAttribute` values. That is the largest single
addition to those enums the mod has ever had, and every `switch` over them has to be revisited —
`MagicAttribute.fromSchool` and `counters` most of all.

The codex is safe: `PyramidLayersTest.eachLayerBelowTheLineHoldsOneSchool` exempts the Authority row
(`authority || schools.size() == 1`), so nine schools sharing tier -6 is already legal.

### Concepts that were considered and rejected

**Time** (it is Space's), **Motion** (Space owns where things are and `manipulate_space` already has
velocity and acceleration laws), **Knowledge** (folded into Mind), **Freedom** (it is the absence of
Law, not a concept of its own — an authority defined as *not another one* has no identity),
**Order** (renamed to Law, because "order" invites zone rules and those are Space's), **Light**,
**Storm**, **Judgement**, **Fate**, **Hunger**, **True Name**.

**Blood has no Authority, deliberately.** The Blood school is a whole pillar — seven skills, the
Vessel, the sacrifice pact — and the red team flagged it as an unowned concept. It stays unowned:
blood is where Life and Death overlap, and it is the best possible illustration of the framing
above. A mortal pays in blood because they cannot command either concept outright.

---

## 3. The counter ring

Your rule — only an Authority answers an Authority — means the nine *are* a standoff, so the graph
is the design, not decoration.

They sit in a ring. **Each counters the two after it, loses to the two before it, and is neutral
with the remaining four.**

```
        Mana → Space → Form → Life → Death → Soul → Mind → Law → Chaos ──┐
          ↑                                                             │
          └─────────────────────────────────────────────────────────────┘
```

| Authority | Counters | Loses to | Standoff with |
|---|---|---|---|
| **Mana** | Space, Form | Law, Chaos | Life, Death, Soul, Mind |
| **Space** | Form, Life | Mana, Chaos | Death, Soul, Mind, Law |
| **Form** | Life, Death | Mana, Space | Soul, Mind, Law, Chaos |
| **Life** | Death, Soul | Space, Form | Mind, Law, Chaos, Mana |
| **Death** | Soul, Mind | Form, Life | Law, Chaos, Mana, Space |
| **Soul** | Mind, Law | Life, Death | Chaos, Mana, Space, Form |
| **Mind** | Law, Chaos | Death, Soul | Mana, Space, Form, Life |
| **Law** | Chaos, Mana | Soul, Mind | Space, Form, Life, Death |
| **Chaos** | Mana, Space | Mind, Law | Form, Life, Death, Soul |

**Verified regular:** 9 nodes, 18 edges, every node out-degree 2 and in-degree 2. Count the columns.
(An earlier draft of this shipped a ten-row table for a nine-Authority roster and called it balanced;
this one is checked.)

The four standoffs per Authority carry as much weight as the wins. Most matchups are not decided by
the concepts at all — they come down to the two people holding them.

### The eighteen edges

| Edge | Why |
|---|---|
| Mana → Space | Folding space is magic. Magic is mana. Nullify it and the fold never opens. |
| Mana → Form | Matter is mana that stopped moving. Unmake the one and the other has nothing to be. |
| Space → Form | A thing cut out of space has nowhere to be, whatever it is made of. |
| Space → Life | Seal it away from sun, soil and air. A pocket dimension starves what grows. |
| Form → Life | Refuse flesh its shape. Growth has nothing to grow into. |
| Form → Death | Death acts on a body; Form decides what a body *is*. Turn flesh to crystal and rot has nothing to eat. |
| Life → Death | Regrowth outlasts decay. The mark runs out before the body does. |
| Life → Soul | The vessel will not let go. A body Life holds open cannot be vacated — sever, swap and step all fail. |
| Death → Soul | The mark is on the self, not the body. Step into a new vessel and it steps with you. |
| Death → Mind | Thought ends. |
| Soul → Mind | The self overrules what it merely thinks. |
| Soul → Law | An oath binds a self. Swap selves and it binds nobody. |
| Mind → Law | Know the letter, escape the spirit. |
| Mind → Chaos | Someone who knows every outcome is not gambling. |
| Law → Chaos | Law binds the wild. |
| **Law → Mana** | **Law is the one Authority not made of mana.** A geas runs on your word, so a null field has nothing to grip. |
| Chaos → Mana | A null field is a precise, uniform volume. Chaos is the end of uniformity: it leaks. |
| Chaos → Space | Destinations stop being reliable. The fold opens somewhere else. |

**Law → Mana is the keystone.** You asked for anti-magic that nullifies *everything*, and this is
what keeps that from being an auto-win: Law's power is not a spell, so there is nothing in it for
Mana to switch off. It is also the answer to "is anti-magic too strong" — it is, against eight of
them, and that is fine, because the ninth ignores it completely.

**The weakest edge is Chaos → Mana.** It is the one story I would not defend hard. Flagged rather
than hidden.

### Making the ring mechanical

A ring is only worth having if it is code. The rule, extending `MagicCounterService`:

- non-Authority vs non-Authority → the existing `MagicAttribute.counters()` lattice, unchanged;
- non-Authority vs Authority → **always refused**, whatever the attribute says;
- Authority vs Authority → counters **only if the ring says so**, looked up from a single
  `AuthorityRing` table.

That last clause is what stops the ring being lore. It also settles the open question at
`MagicCounterService:421`: Sovereign Aegis can currently counter `singularity`,
`dimensional_guillotine` and `soul_valley`, because those sit outside `AUTHORITY_SKILLS`. Under rule
3 that is a violation — they are Authority powers. Fixing it means deriving "is this an Authority
skill" transitively through sub-skills rather than from a hand-written set. **This is a balance
change and needs your say-so.**

---

## 4. The nine kits

Costs are in the mod's own band — existing Authority skills run 8–36 mana for utility and 72–84 for
an ultimate, with cooldowns from 8 to 1600 ticks.

### 4.1 Space — built, plus one gap

`create_subspace` · `manipulate_space` · `spatial_arsenal` (→ `singularity`,
`dimensional_guillotine`) · `pocket_dimension`.

Nothing to redesign. The one thing missing for it to read as omnipotence over *extension* rather than
over *rooms*: Space cannot presently move anything but the caster. A `fold_distance` — collapse the
gap between two points you can see, for everything, not just yourself — would close it.

### 4.2 Soul — the kit you asked for

Today Soul is `soul_vow` and nothing else. That is the entire Authority, against Space's four skills,
twelve law categories, a dimension and a storage screen. It is the least-built thing in the mod
relative to its billing.

**`soul_valley` is not spare — it is the bind.** `SoulAuthorityService:58` resolves mode 0
(`ACTION_BIND`) to `SOUL_VALLEY`, which carries that action's cost (74 mana, 1600 ticks), its magic
circle (`MagicCircleEffectEntity.STYLE_SOUL_VALLEY`) and a `MagicSkillTuningView` case. The other
four modes — swap, call, sever, step — bill against `soul_vow` itself. So the sub-skill is the
expensive opening move, and repurposing it would gut the Authority's one working skill.

Keep `soul_vow` (bind / swap / call / sever / step) and add:

| Skill | id | Shape | Cost / CD | What |
|---|---|---|---|---|
| Spirit Walk | `spirit_walk` | press | 30 / 400 | 30s incorporeal: cannot damage or be damaged, hostiles lose you, only Soul skills work. A new skill of its own — *not* a repurposing of `soul_valley`. |
| Soul Echo | `soul_echo` | press | 20 / 400 | Leave an invulnerable, immobile echo of yourself for 60s. Recast to swap places with it. One at a time. |
| Soul Anchor | `soul_anchor` | hold | 1/tick | While held you and allies within 8 blocks cannot be moved — knockback, pull, push, all refused. |
| Soul Strike | `soul_strike` | press | 24 / 120 | A beam that ignores armour entirely. Low base damage; the point is that shells do not matter. |
| Self Affirm | `self_affirm` | passive | — | Resistance to effects that rewrite what you are: petrification, transmutation, domination. Not damage — identity. |

**Signature moment:** stepping through a bond to a partner half a world away. There is no distance
between selves that know each other.

**Border:** Soul owns the self that *persists*. Mind owns what it currently *thinks*. Life owns the
body's *continuing*. Soul does not heal, does not command, does not end.

### 4.3 Mana — "nullify everything", and pure-mana form

| Skill | id | Shape | Cost / CD | What |
|---|---|---|---|---|
| Nullification Aura | `nullification_aura` | world-altering | 40 / 240 | A 12-block sphere for 240 ticks. Spell entities inside cease — not deflected, *unmade*. New casts inside fail. Persistent effects freeze rather than end. |
| Arcane Ascension | `arcane_ascension` | hold-modes | 2/tick | Become pure mana: no block collision, half damage from magic, every attack becomes magic damage. The "pure mana form" you asked for. |
| Spell Rejection | `spell_rejection` | press | 18 / 600 | 10s where every hostile projectile within 30 blocks is turned back on its caster. |
| Spell Unraveling | `spell_unraveling` | channelled | 1/tick | Channel to pick apart another caster's active spells one at a time, nearest first. |
| Mana Convergence | `mana_convergence` | world-altering | 30 / 400 | A zone where mana regenerates double and spells cost a fifth less — for everyone, including them. |

**Signature moment:** a caster watches their spell simply stop existing in mid-air.

**Border:** Mana commands the *engine*, never an output. It is not a fire school with a coat of
paint. Nothing in this kit deals elemental damage.

**The risk, stated plainly:** the pre-cast nullification hook is the single most invasive change in
this whole document. It has to sit in `MagicCastingService.castResolved` before the if-ladder, and it
must not nullify the Mana wielder's own skills or Law's.

### 4.4 Life — Yggdrasil

| Skill | id | Shape | Cost / CD | What |
|---|---|---|---|---|
| Yggdrasil Anchor | `yggdrasil_anchor` | world-altering | 84 / 1600 | A persistent world-tree entity. Ten minutes or until starved. Pulses healing in a wide radius, and it *grows* — feed it and it strengthens. The centrepiece, not a buff totem. |
| Life Weave | `life_weave` | channelled | 30 / 300 | A bidirectional bond: shared healing, and if one dies the other can spend the bond to bring them back. |
| Symbiosis | `symbiosis` | hold-modes | 2/tick | Everyone in 16 blocks shares all damage and all healing. Turns a group into one organism. |
| Ancestral Bloom | `ancestral_bloom` | press | 36 / 400 | Summon a grove guardian that fights for you. **This is "creating life"** and it should mean a creature with a mind, not a spawned mob. |
| Verdant Grasp | `verdant_grasp` | press | 24 / 200 | Roots erupt; anything held is also healed while held. Life restrains by *nurturing*, which is the whole joke. |

**Cut from the panel's proposal:** `renewal` and `regeneration_surge`. Four separate healing skills
made Life the strongest kit in the roster by a distance and made three of them redundant with the
tree. A healing cap per tick is mandatory or Yggdrasil plus Symbiosis is unkillable.

### 4.5 Death — inevitability, not damage

| Skill | id | Shape | Cost / CD | What |
|---|---|---|---|---|
| Lifespan | `lifespan` | hold-modes | 36 / 600 | A visible countdown over the target. At zero they die, regardless of health, shields or healing. Delay it by paying mana; escape it by outrunning the wielder's range. **Cannot be healed off.** |
| Mortality Mark | `mortality_mark` | press | 18 / 120 | The marked take more from every source. Death does not deal damage; it makes damage matter. |
| The Boundary | `the_boundary` | channelled | 40 / 800 | Channel, rooted, to open a zone nothing may *leave* — including by teleport, portal or soul-step. |
| Inevitable Chains | `inevitable_chains` | hold-modes | 24 / 300 | Spectral chains. Breaking them costs the target health, not time. |
| Entropic Aura | `entropic_aura` | passive | — | Hostiles near you are weaker for being near you. Always on. |

**Border:** not a damage kit. Every entry above is about *certainty*, and none of it out-damages a
tier-4 spell.

### 4.6 Form — matter and shape

| Skill | id | Shape | Cost / CD | What |
|---|---|---|---|---|
| Petrify | `petrify` | press | 24 / 200 | The target becomes stone: cannot act, cannot be hurt, cannot be healed. A pause button on a person. |
| Transmute | `transmute` | press | 12 / 40 | Change what a volume of the world is made of. |
| Unmaking | `unmaking` | press | 36 / 400 | A burst that reduces everything in it to its base material — structures included. |
| Morigelem | `morigelem` | press | 30 / 600 | Reshape a corpse into a golem that fights for you. Not necromancy: the body is *material*, and the thing that stands up was never alive. |
| Transmute Vitality | `transmute_vitality` | press | 30 / 300 | Change what healing *is* for one target: their incoming healing becomes damage. This is Form's counter to Life made mechanical. |

**Form was the weakest kit in the panel** — pure utility, no scaling, loses every duel.
`transmute_vitality` is the fix and it is deliberately the edge the ring already promised.

**Border:** Space owns *where* a thing is. Form owns *what it is made of*. Life owns the animate.

### 4.7 Mind — cognition

| Skill | id | Shape | Cost / CD | What |
|---|---|---|---|---|
| Omniscience | `omniscience` | press | 30 / 600 | For 45s every creature in 32 blocks is revealed through everything, with health and intent. No stealth survives. |
| Compelled Obedience | `compelled_obedience` | press | 36 / 400 | Give a creature one order it must carry out for 60s: attack its allies, flee, stand, follow. |
| Conviction Lock | `conviction_lock` | press | 30 / 500 | Freeze creatures into their current behaviour. Not paralysis — they keep doing whatever they were doing, forever. |
| Phantom Guise | `phantom_guise` | channelled | 2/tick | You and nearby allies stop being perceivable. Nothing targets you; damage does not draw attention. |
| Whispered Doubt | `whispered_doubt` | channelled | 2/tick | Channelled uncertainty: aim degrades, movement stutters. |

**Border:** Mind owns the *current contents* of a head. Soul owns the self that has the head. Mind
can make you forget your name; Soul decides whether it is still yours.

**The risk:** this is the most invasive kit for vanilla AI. Goal injection and targeting suppression
are where cascading bugs live. Build it late.

### 4.8 Law — oath and obligation

| Skill | id | Shape | Cost / CD | What |
|---|---|---|---|---|
| Geas | `geas` | hold-modes | 36 / 400 | Bind one creature to a command — obey, be still, do not cast, do not leave. **Reality enforces it.** It is not a status effect and resisting is not a stat check. The centrepiece. |
| Vow of Service | `vow_of_service` | press | 30 / 600 | Bind a creature as a servant. It is stronger for serving you, and it suffers for leaving. |
| Oath of Binding | `oath_of_binding` | hold-modes | 24 / 300 | A mutual oath with an ally: both stronger, both unable to harm the other, both punished if either breaks it. |
| Hierarchy | `hierarchy` | press | 36 / 500 | Assert rank. Anything weaker than a threshold simply may not raise a hand to you. |
| Oath Severance | `oath_severance` | press | 40 / 400 | Break any binding on a target — including another Authority's — and the breaking costs them. |

**Cut:** `word_is_law`. Its Silence mode was zone anti-magic, which is Mana's, and its shape was a
zone rule, which is Space's. Law binds *people*, and the moment it binds a *region* it has stopped
being Law. This is the sharpest border in the roster and the one most likely to erode.

### 4.9 Chaos — change

| Skill | id | Shape | Cost / CD | What |
|---|---|---|---|---|
| Probability Cascade | `probability_cascade` | press | 24 / 200 | A projectile that branches on every hit, each child weaker, chaining through a crowd. |
| Mutation Field | `mutation_field` | world-altering | 30 / 400 | A zone where every creature is rerolled every few ticks from a weighted table of effects. Including you. |
| Quantum Split | `quantum_split` | hold-modes | 30 / 300 | Mark up to three and rotate their positions. |
| Paradox Echo | `paradox_echo` | press | 36 / 500 | Spawn a duplicate of what you hit; the two split every blow meant for either. |
| Certainty Inversion | `certainty_inversion` | passive | — | Near you, accuracy drops and criticals stop happening. Always on. |

**The trap, and it was flagged:** Chaos must not be defined as *not-Order*. Its identity is "I
command change", positively — it is the only Authority whose power applies to the wielder too, and it
should feel like that is a price worth paying rather than a drawback.

**Chaos is the weakest of the nine** and the honest candidate for replacement if one has to go.

---

## 5. Cross-cutting mechanics

### 5.1 Exclusivity — the ledger (not built)

Your rule "one Authority can be wielded by one person at a time" is currently unimplemented; there
is no world-level state at all.

- **`magic/AuthorityLedger.java`** — a `SavedData` on the overworld holding
  `Map<ResourceLocation, UUID> heldBy`. `claim`, `release`, `holderOf`, `isFree`.
- **`PlayerMagicState.setAuthority`** gains a failure mode: it returns false when the ledger says
  someone else holds it. Today it can only fail on an unknown id.
- **`clearAuthority`** releases the claim.
- **Login revalidation** — if a player's saved `authorityId` disagrees with the ledger, the ledger
  wins and the player is told.
- **Tests** to pin: a second claimant is refused; death does not release; logout does not release;
  a cleared Authority becomes claimable; two players can hold two *different* Authorities.

Open: does an Authority ever come free on its own? A holder who never logs in again otherwise locks
a ninth of the endgame forever.

### 5.2 Acquisition (not built)

There is no in-game way to get an Authority — `/magical authority <id>` is the only path in the
codebase, and the "Authority dimension progression" the docs mention is not wired to anything.

Proposal: one **pedestal** per Authority, each behind its own trial. Right-click a free pedestal to
claim; right-click a held one and it names the holder. That last part is the important one — it turns
the ledger into *information*, and the nine wielders into named figures on a server.

### 5.3 What is already done

`be441d5`, pushed: `ALL_SKILLS` excludes `AUTHORITY_SKILLS`, so `/magical unlockall` cannot grant an
Authority skill. `AuthorityGrantTest` pins six rules, including five that were true but untested.

---

## 6. What this costs

Space took a bespoke entity with twelve law categories, a server/client split, a dimension, storage,
screens, inputs, payloads and renderers. Assume each new Authority is a comparable piece of work.
Nine Authorities at 5–7 skills each is roughly **fifty skills, six new schools, six new attributes,
and on the order of twenty new entities**.

**Build order, cheapest and safest first:**

1. **Soul** — you asked for it, it reuses `SoulBondEntity` and the existing input handler, and it is
   the most embarrassing gap. Lowest risk, highest immediate payoff.
2. **Form** — self-contained, no hooks into casting or AI.
3. **Chaos** — effects and positions, few new systems.
4. **Death** — countdown state and networking.
5. **Mana** — the pre-cast nullification hook is a landmine; do it once you have the pattern.
6. **Life** — Yggdrasil persistence plus bond sync; healing must be capped.
7. **Mind** — mob AI goal injection, the most invasive to vanilla.
8. **Law** — oath state tracked across dimensions; the most complex state in the design.

Before any of them: **the ledger (§5.1) and the ring lookup (§3)**, because every Authority after
them depends on both.

---

## 7. Open questions — these are yours, not mine

1. **Six new schools and attributes.** `MagicAttribute.counters()` is a hand-written lattice and
   every `switch` over these enums has to grow. Do Authorities get their own schools at all, or do
   they borrow existing ones (Life→WATER, Death→DARK, Mind→ARCANE…)? Borrowing is far cheaper and
   slightly wrong.
2. **Does Sovereign Aegis lose its answer to `singularity`, `dimensional_guillotine` and
   `soul_valley`?** Rule 3 says yes. `MagicCounterService:421` currently says no, deliberately. This
   is a balance change either way.
3. **Can a wielder give an Authority up?** `clearAuthority` exists but nothing calls it for a player.
   If Authorities are permanent, the ledger never frees; if they are droppable, they can be traded.
4. **Is this PvE or PvP?** Geas, Compelled Obedience and Symbiosis are all far more dangerous aimed
   at a player than a mob. If PvP matters, several need separate tuning.
5. **Corruption.** Blood Sacrifice charges corruption. Should Mind or Chaos? Or is corruption
   orthogonal to Authorities entirely?
6. **Fusion.** The Spell Creator fuses two skills. Can it touch Authority skills? If yes, a wielder
   can manufacture powers no design here anticipated.
7. **Chaos.** It is the weakest of the nine and the only one defined partly by opposition. Keep it,
   or replace it — Hunger was the red team's suggestion, as the one concept that fills a real gap
   (appetite, striving, the drive to want).
