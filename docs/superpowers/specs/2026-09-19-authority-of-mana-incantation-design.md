# The Authority of Mana — the Incantation

> Supersedes §4.3 ("Mana — the Weave") of `2026-09-15-nine-authorities-design.md` and the Ledger
> that replaced it in `4ad9b16`. Both are withdrawn: the Weave was the subspace with the nouns
> changed, and the Ledger owned prices, which is a machine with six dials and no depth. This design
> is a port of Noita's spell-composition grammar, read in
> `docs/research/2026-09-19-noita-spell-composition.md`, with the wand removed. The wand's
> properties are gone by decision; everything the *cards* do is kept exactly, under our names.

## 1. What Mana owns

> **Mana owns a language, and a Grimoire in the hand.**

Every other school hands the player finished spells. The Authority of Mana hands the player the
parts spells are made of, and the rule for reading them. A **Verse** is one card: a function that
writes into a shared **shot state** and may draw more cards. An **Incantation** is an ordered list
of verses the wielder wrote. **Reciting** it is the draw: cards come off the top until a budget runs
out, every body added on the way is stamped with the state as it stood at that instant, and what
comes out is a program's output, not a spell's. There is no binding logic that decides which
modifier belongs to which projectile; the binding is temporal, and that single fact is where all of
Noita's depth lives.

Against Rule 6 of the nine-authorities design:

- **Anchor:** the Grimoire. Nothing in the kit does anything until an incantation is written into
  it; a new holder with an empty book presses Recite and hears silence.
- **Grammar:** the verses and the draw. Eight types, roughly a hundred verses, one evaluator.
- **Persistent authored state:** four incantations and the set of verses the wielder knows, saved
  on `PlayerMagicState`, synced in the state blob, cleared when the Authority leaves.
- **Emergent property:** copies-versus-economy. Verses that call other verses (the Recall family,
  the Refrains, the Imposes) pay nothing for what they copy, and the whole metagame of the kit is
  the interaction between that and mana, charges and the draw. None of it is designed as a perk.

Why this is not a shape the mod already owns:

| Already owned | Why the Incantation is not it |
|---|---|
| Arcane (`arcane/`): one rune, one shape, a few unique modifiers, one `SpellResolution` | Flat. One body, no sequence, no nesting, no state carried between parts. The Incantation is a strict superset; Arcane is the tier-1 scrap of the concept Mana holds entire, which is exactly the framing §1 of the nine-authorities design asks for. |
| Spell Creator: fuse two skills into a third | Two inputs, one output, a fixed recipe table. |
| The Fracture: five faults in an order | A sequence, but one read by the Pile, and five entries from seven. |
| Manipulate Space: category × operation × target | A menu of dials. The Ledger was this with other nouns, and is withdrawn for that reason. |

## 2. The fidelity rule, and the deliberate deviations

**The rule:** the evaluator is a port of `gun.lua`, function for function, with the same piles, the
same draw, the same flags and the same guards, so that any wand trace from Noita is a valid test of
ours once the names are translated. Internal names mirror the Lua (`deck`, `hand`, `discard`,
`drawActions`, `dontDraw`, `forceStopDraws`) so the port can be checked line by line. Only the
surface names differ.

**The deviations, all of them:**

1. **No wand.** No shuffle, no capacity stat, no wand mana, no charge speed, no base spread or
   speed, no always-cast, no perk extra modifiers. The wielder's own mana pool is the pool; the
   incantation's length cap is the capacity; the cards' own delays are the cooldown.
2. **Spells per cast becomes Breath**, a property of the incantation (1..8, default 1). The piles
   persist between presses exactly as they persist between clicks on a wand.
3. **Ticks, not frames.** Every Noita frame count is divided by three and rounded.
4. **One damage number and a school, not ten damage types.** `damageAdd` in half-hearts plus a
   `MagicSchool` the body wears (colour, and the on-hit effect a Wreath adds). No elemental
   resistance exists in the mod, so nothing is lost.
5. **Bounded evaluation.** Noita runs on one player's frame; this runs on a server tick for every
   wielder. Caps in `ReciteCaps`: 20 verses, breath 8, 1024 verse executions per recite, 64 bodies
   per recite, payload depth 4, recursion limit 2 (Noita's own). Past a cap the recite **frays**:
   drawing stops, what was planned still fires.
6. **Zeta reads the other incantations.** Noita's Zeta copies a random spell from another held
   wand; ours (Wild Recall) copies a random verse from one of the wielder's other three
   incantations.
7. **Clause thresholds are Minecraft-sized**: Outnumbered is 6 hostiles within 16 blocks (Noita 15
   within 240 px), Crowded Sky is 12 projectiles within 16 blocks (Noita 20 within 160 px).
8. **Unlock flags become knowledge.** Where Noita gates a random pick on a persistent unlock flag,
   we gate it on whether the wielder *knows* the verse.
9. **Dropped for now:** Material verses (world-modifying), Passive verses (the type exists and is
   transparent to composition, as in Noita), Cessation, the Ocarina and Kantele notes, Summon
   Portal, the Larpa and Ray families, Necromancy, walls and platforms, Meteor Rain, the Spells to
   Power family, Blood to Power, Money Magic, and terrain-digging bodies. Each has a slot in the
   taxonomy and can be added as a verse without touching the evaluator.
10. **Explosions hurt the caster.** A Detonation with no Latch in front of it goes off in the hand.
    That is Noita's first lesson and it is kept.
11. **A body is stamped with its own verse's deltas.** Noita's actions call `add_projectile` first and
    write their numbers after, because one config is applied to the whole shot at the end; here every
    body is stamped with a copy of the state the moment it is added, so a verse writes its deltas and
    *then* adds its body, and a Needle's own crit lands on that Needle. Every helper keeps that one
    order (`ProjectileVerses.projectile`/`carrier`, `StaticVerses.stationary`/`carrier`, the Word
    utilities), and a body's own on-hit effect is the prototype's (`VersePrototype.hit`: Ember burns,
    Arc Bolt shocks), never a delta on the state, so it cannot leak onto the bodies drawn after it.

## 3. Vocabulary

| Noita | Ours, on the surface | In code |
|---|---|---|
| Spell (a card) | **Verse** | `Verse`, `VerseCard` (an instance in the piles) |
| Wand's spell list | **Incantation** (four per wielder, I..IV) | `Incantation` |
| Casting (a click) | **Recite** | `Reciter.recite`, skills `incantation_1..4` |
| Spells per cast | **Breath** | `Incantation.breath()` |
| Deck / hand / discard | **unread / in hand / read** | `deck`, `hand`, `discard` (mirroring the Lua) |
| Shot state | shot state | `ShotState` |
| Draw | draw | `Recital.drawActions` |
| Cast delay | **Beat** | `beatTicks` |
| Recharge | **Rest** | `restTicks` |
| Wrapping | **Overrun** | event `OVERRUN` |
| Skipped for mana or charges | **Falter** | events `FALTER_MANA`, `FALTER_SPENT` |
| Trigger (collision) | **Latch** | `PayloadKind.LATCH` |
| Timer | **Fuse** | `PayloadKind.FUSE` |
| Expiration trigger | **Epitaph** | `PayloadKind.EPITAPH` |
| Add Trigger / Timer / Expiration | **Impose Latch / Fuse / Epitaph** | `impose_*` |
| Divide By N | **Refrain of N** | `refrain_*` |
| Greek letters | **Recall …** | `recall_*` |
| Requirement spells | **Clause …**, **Otherwise**, **End Clause** | `clause_*`, `otherwise`, `end_clause` |
| Wand Refresh | **Fresh Page** | `fresh_page` |
| Random Spell / Draw Random | **Wild Verse**, **Blind Draw** | `wild_*`, `blind_*` |
| Spell Duplication | **Reprise** | `reprise` |
| Myriad | **Epic** | `epic` |
| Charges / uses | **uses** | `maxUses`, `usesRemaining` (-1 unlimited) |
| The whole per-player store | **Grimoire** | `Grimoire` |

## 4. The Grimoire: the authored state, and the base the editor stands on

The Grimoire is the one new field on `PlayerMagicState`, in the slot the Ledger holds today
(declared at line 47, copied at 2056, saved at 2167, loaded at 2330, cleared in `clearAuthority`
at 1129). It is the data model the future screen edits, so it is designed now and the screen is
not.

```
Grimoire
  incantations[4] : Incantation
      entries      : List<Entry(id: ResourceLocation, usesRemaining: int)>   // ordered, ≤ 20
      breath       : int                                                        // 1..8
  known            : LinkedHashSet<ResourceLocation>                            // verses this wielder may use
  everyOtherSkip   : boolean                                                    // Clause: Every Other's shared toggle
```

Save format, on the state's tag under `"grimoire"`:

```
{ incantations: [ { breath: 1, verses: [ { id: "magical:needle", uses: -1 }, ... ] } × 4 ],
  known: [ "magical:needle", ... ],
  everyOther: 0b }
```

Rules:

- **Editing is one payload.** `SetIncantationPayload(slot, breath, List<String> ids)`, capped on the
  wire (slot 0..3, breath 1..8, at most 20 ids of at most 64 characters), handled by
  `IncantationService.setIncantation`, which re-runs `IncantationValidator` server-side (every id
  must exist in the catalogue and be known to the wielder; length and breath within caps) and
  rejects the whole edit on any problem, exactly as `ChaosAuthorityService.setFracture` re-checks
  every ordinal. Uses are re-seeded from `Verse.maxUses` on every edit. That is the only write path
  the screen will need, and `/magical incantation set` is the same call for captures.
- **The validator is shared.** `IncantationValidator.problems(ids, breath, known, catalogue)` is
  pure, so the screen can run it on every keystroke and show the same words the server would.
- **Previews are the real evaluator.** `Reciter` is pure and takes a `ReciteWorld`; the screen will
  run it on a *copy* of an incantation with a `PreviewWorld` (assumed conditions, fixed random) and
  draw the resulting `RecitePlan`, exactly as the community wand simulator does. Nothing in the
  evaluator knows whether it is on the server.
- **Every verse declares its numbers.** `Verse.Declared(draw, beat, rest)` is what a tooltip shows;
  `VerseContentTest` runs every verse on a stub deck and fails if the declared numbers and the
  enacted ones differ, so the tooltip cannot drift from the machine.
- **Runtime piles are not authored state.** A `ReciteSession` (deck, hand, discard, first-shot
  flag, the rest carried between presses) lives per wielder per slot in `IncantationService`, is
  never saved, and is rebuilt from the incantation on: an edit of that slot, login, respawn, a
  change of dimension, or the Authority leaving. Uses are the exception: a card's `usesRemaining`
  *is* the entry's, and `Reciter.recite` writes them back to the incantation the session was built
  from when it returns, so charges spent in play are saved without a second call.

## 5. The shot state

`ShotState` is the bag every verse of a shot writes into. A body added while the shot runs is
stamped with a **copy** of the state at that instant; the shot's **final** state carries the
shot-wide numbers (spread, pattern, beat) the spawner reads for the group. That split is what
`gun.lua` does with `EndProjectile` and `register_action`, and it is pinned by a test: a modifier
after a body does not touch that body, a pattern set after a body still fans it.

| Group | Field | Unit / default | Written by |
|---|---|---|---|
| Timing | `beatTicks` | ticks, 0 | every verse; Whisper *sets* it to 0 |
| Motion | `speedMultiplier` | ×, 1.0, clamped 0..20 on every write | Haste ×2.5, Ballast ×0.35, Volatile ×0.75, Serpentine ×2 |
| | `childSpeedMultiplier`, `dampening` | ×, 1.0 | reserved, mirrored from Noita |
| | `gravity` | blocks/tick², 0 | Sink +0.04, Loft -0.02 |
| | `bounces` | count, 0 | Ricochet +10, Bursting Ricochet +1 |
| | `lifetimeAddTicks` | ticks, 0 | Endurance +25, Blunt +90, Relay -10, Gyre +8 |
| | `spreadDegrees` | degrees, 0 | bodies add, True Aim -60, formations subtract |
| | `patternDegrees` | degrees, 0 | formations; Refrain sets 5 |
| Damage | `damageAdd` | half-hearts, 0 | Weight +2.5, Ballast +6, Puncture -1.5, Refrains subtract |
| | `healingAdd` | half-hearts, 0 | Balm bodies |
| | `explosionRadius`, `explosionDamageAdd` | blocks, half-hearts | Volatile, Refrains subtract radius |
| | `critChance` | percent, 0 | bodies +5, Keen Edge +15 |
| | `knockback` | blocks/tick, 0 | Shard, Ballast |
| | `nullAllDamage` | false | Blunt |
| Element | `school` | `MagicSchool`, null = the body's own | Wreaths |
| Bundles | `behaviours` | list of `Behaviour` | Seeker, Sightline, Puncture, Serpentine, Gyre, Errant, Relay, Twin Path, Naught, Undying, Lantern, Near Word, Bursting Ricochet |
| | `hitEffects` | list of `HitEffect` | Wreaths, Uplift, Displace |
| | `wakes`, `wakeAmount` | list of `Wake`, count | the Wake verses |
| Rules | `friendlyFire` | false | Puncture |
| Feel | `recoil`, `screenshake`, `lightLevel` | per shot | bodies, Lantern |
| Bookkeeping | `drawManyCount` | | `drawActions` |

`Behaviour` is the port of `extra_entities`: a behaviour is a name the body's entity looks up, so a
new one is an enum constant and a tick function, never an evaluator change. `HitEffect` is the port
of `game_effect_entities`. `Wake` is the port of `trail_material`.

## 6. Verses

```java
record Verse(ResourceLocation id, VerseType type, int mana, int maxUses, boolean recursive,
             ResourceLocation prototype, int bodies, Declared declared, VerseAction action)
interface VerseAction { int run(Recital recital, int recursion, int iteration); }
enum VerseType { PROJECTILE, STATIC, MODIFIER, MULTICAST, MATERIAL, CONTROL, UTILITY, PASSIVE }
```

The eight types are Noita's eight (`OTHER` is `CONTROL`). Three things the type decides, all
ported: `PROJECTILE`, `STATIC` and `MATERIAL` mark the click as having produced a body, which is
what consumes charges; `MODIFIER`, `PASSIVE`, `CONTROL` and `MULTICAST` are what an Impose scans
over; `PROJECTILE`, `STATIC`, `MATERIAL` and `UTILITY` are what it may target and what makes a
payload valid.

A **prototype** (`VersePrototype`) is the port of the projectile XML a verse names: the body's own
numbers (`school`, `damage`, `speed` in blocks per tick, `lifetimeTicks`, `radius`, `gravity`,
`isStatic`, `durationTicks`, `explosionRadius`, `explosionDamage`, `pulse`, `look`, and `hit`, the
body's own on-hit effect (Ember's BURN, Arc Bolt's SHOCK), which is not a delta on the state and so
cannot leak onto the bodies drawn after it). A verse adds its deltas
to the state and names its prototype; the spawner adds the two together. `bodies` is how many bodies
the prototype fires at once (Noita's `related_projectiles[2]`), which an Impose honours.

`VerseCatalogue` is an instance registry (not static, so a test can build a small one), and
`VerseContent.CATALOGUE` is the real one, filled by `ProjectileVerses`, `StaticVerses`,
`ModifierVerses`, `MulticastVerses`, `UtilityVerses` and `ControlVerses` in that order. Lang keys are
`verse.magical.<path>` and `.desc`, pinned by `VerseLangKeysTest` over `VerseContent.CATALOGUE`.

## 7. The Reciter

One press runs `Reciter.recite(session, breath, mana, costScale, world)` and returns a `RecitePlan`.
The entry point clamps the breath to `ReciteCaps.MIN_BREATH..MAX_BREATH`, floors the mana at zero, and
when the recital returns writes every card's remaining uses back to the incantation the session was
built from, so a press never needs a second call to save what it spent (a preview runs on a copy, §4).
The machine is `Recital`, a port of `gun.lua`'s globals and helpers; verse actions are handed the
`Recital` and call the same helpers the Lua actions call. The names below are the code's.

```
recite(session, breath, mana, costScale, world):
  dontDraw = false; forceStopDraws = false; reloading = false; startReload = false; gotProjectiles = false
  root = Frame(new ShotState(), draw = breath)                         -- _start_shot
  if session.firstShot: session.orderDeck(); session.restCarry = 0; firstShot = false
  drawActions(breath, instantReload = false)                            -- draw_shot(root)
  moveHandToDiscard()                                                   -- _handle_reload
  if deck empty or startReload:       -- the Lua also asks "not reloading"; that reload is the C++ side's, which we lack
      moveDiscardToDeck(); orderDeck(); rests = true; rest = session.restCarry; session.restCarry = 0
  reloading = false
  return RecitePlan(root.plan, beat = root.state.beatTicks, rest, rests, manaSpent, manaLeft, frayed, events)

drawActions(n, instantReload):                                          -- draw_actions
  if dontDraw or frayed: return
  state.drawManyCount = n
  repeat n times:
      ok = drawAction(instantReload)
      if !ok: while deck not empty: if drawAction(instantReload): break
      if reloading or frayed: return

drawAction(instantReload):                                              -- draw_action
  if deck empty:
      if instantReload and !forceStopDraws: moveDiscardToDeck(); orderDeck(); startReload = true; event OVERRUN
      else: reloading = true; return true
  card = pop top of deck
  price = card.mana > 0 ? round(card.mana * costScale) : card.mana
  if price > mana: discard card; event FALTER_MANA; return false
  if card.usesRemaining == 0: discard card; event FALTER_SPENT; return false
  mana -= price
  playCard(card); return true

playCard(card):                                                         -- play_action
  hand += card; steps++ (fray past the cap); card.action.run(this, recursion 0, iteration 1)
  if card.type spawns bodies: gotProjectiles = true

addProjectile(proto):                                                   -- add_projectile
  bodies++ (fray past the cap); frame.bodies += ProjectilePlan(proto, verse, state.copy(), NONE)

addProjectileLatch/Fuse/Epitaph(proto, [ticks], draw):                  -- add_projectile_trigger_*
  stamped = state.copy(); child = Frame(new ShotState(), draw)          -- draw_shot(create_shot(draw), true)
  push frame; frame = child; drawActions(draw, true); pop
  frame.bodies += ProjectilePlan(proto, verse, stamped, kind, ticks, ShotPlan(child.bodies, child.state))
  (past MAX_DEPTH the body is added with no payload and the recite frays)

call(card, recursion, iteration):                                       -- data.action(rec, iter)
  steps++ (fray past the cap); event COPIED; return card.action.run(this, recursion, iteration)

checkRecursion(card, level):                                            -- check_recursion
  if card.recursive: return level >= 2 ? -1 : level + 1
  return level

moveHandToDiscard():                                                    -- move_hand_to_discarded
  for card in hand:
      if gotProjectiles or card.type in (CONTROL, UTILITY): if usesRemaining > 0: usesRemaining--
      if usesRemaining != 0: discard += card                             -- a spent card leaves the piles
  hand = []

refreshPage():                                                          -- RESET's body
  discard += hand; discard += deck; hand = []; deck = []
  if !forceStopDraws: forceStopDraws = true; moveDiscardToDeck(); orderDeck()
```

Verse helpers exposed on `Recital`: `state()`, `deck()`, `hand()`, `discard()` (the live lists, so
control verses index them as the Lua does), `mana()/setMana()`, `rest()/addRest()/setRest()`,
`drawActions(n)`, `addProjectile*`, `call`, `checkRecursion`, `setDrawDisabled(boolean)`,
`discardTop()`, `discardAt(i)`, `consumeUse(card)`, `refreshPage()`, `world()`, `event(kind, id)`.

`RecitePlan(root: ShotPlan, beatTicks, restTicks, rests, manaSpent, manaLeft, frayed, events)`;
`cooldownTicks() = max(beatTicks, rests ? restTicks : 0)`, because Noita waits for the larger of
cast delay and recharge, not their sum. `ShotPlan(bodies: List<ProjectilePlan>, state: ShotState)`.
`ProjectilePlan(prototype, verse, stamped: ShotState, payloadKind, fuseTicks, payload: ShotPlan|null)`.
`ReciteEvent(kind, verse, depth)` with kinds `PLAYED, COPIED, FALTER_MANA, FALTER_SPENT, OVERRUN,
REST, FRAYED, DISCARDED` — the raw material of a preview pane and of the HUD line.

`ReciteWorld` is everything a verse may ask the world: `enemiesWithin(blocks)`,
`projectilesWithin(blocks)`, `healthFraction()`, `everyOtherSkipAndFlip()`, `random(bound)`,
`allVerses()`, `isKnown(id)`, `otherIncantationVerses()`, `payHealth(halfHearts)`. The server
implements it over the level and the Grimoire; the tests and the future preview implement it as a
fixture.

## 8. The interaction rules, each with the test that pins it

Every rule is a `gun.lua` behaviour, restated. The test names are the plan's.

1. **A modifier applies to every body drawn after it in the same shot**, not to "the next verse".
   `Weight, Couplet, Needle, Needle` stamps both needles. *`ReciterTest.aModifierReachesEveryBodyAfterIt`*
2. **A modifier after a body does not touch it.** `Needle, Weight, Needle` stamps only the second.
   *`ReciterTest.aModifierAfterABodyDoesNotReachBack`*
3. **A body ends a chain; a multicast draws k; a modifier draws 1; a payload draws its count into a
   fresh state.** *`ReciterTest.drawCountsByType`*
4. **The breath is the root budget and the verses decide the rest.** With breath 1, `Weight,
   Couplet, Needle, Needle, Haste, Needle, Detonation` is three presses: two heavy needles, one fast
   needle, one detonation, then a rest. *`ReciterTest.aTapeOfThreePresses`* (the research appendix's
   first trace)
5. **Faltering skips, never fails.** A verse the wielder cannot afford goes to the read pile and the
   draw moves on; a verse at zero uses does the same. *`ReciterTest.aVerseYouCannotAffordIsSkipped`*
6. **Payloads are isolated.** `Needle with Latch, Weight, Detonation, Haste, Needle`: the needle is
   unmodified, the detonation carries Weight, the second needle carries Haste only.
   *`PayloadTest.aPayloadHasItsOwnState`* (second trace)
7. **Payloads nest and can overrun.** *`PayloadTest.aPayloadInsideAPayload`*,
   *`PayloadTest.aPayloadOverrunsIntoTheReadPile`*
8. **Overrun reloads mid-press and rests at the end.** A modifier as the last verse reads the top of
   the incantation again and the press ends with a rest. *`OverrunTest.theLastModifierOverruns`*
9. **Rest accumulates across presses until a rest happens, then resets.**
   *`OverrunTest.restCarriesUntilTheRest`*
10. **Fresh Page** rebuilds the unread pile from everything, forbids overrun for the rest of the
    press, spares charges, and a second one in the same press forces a rest.
    *`OverrunTest.freshPageRebuildsTheUnreadPile`*, *`freshPageForbidsOverrunForTheRestOfThePress`*,
    *`freshPageSparesUses`*
11. **Charges are spent only by a press that produced a body, or by a Control or Utility verse.** A
    limited modifier with nothing after it keeps its charge. *`ReciterTest.chargesFollowTheBody`*
12. **Copies are free.** Recall First copies the first verse of read, then hand, then unread;
    Recall Last the last of unread, then hand; Recall Pair `deck[1]` and `deck[2]`, memorised
    first; Recall All every verse of read, then the non-recursive of hand, then unread, all with
    draw disabled and never Fresh Page; Recall Modifiers / Projectiles / Statics sweep by type,
    restore mana, beat and rest afterwards, and the first and last of the three draw one after. No
    mana, no charge, works at zero charges. *`RecallTest.*`*
13. **Recursion limit 2.** A recursive verse called at level 2 is refused; a non-recursive verse
    passes its caller's level through. Recall First copying Recall First copying Recall First runs
    three times and the fourth is refused. *`RecallTest.recursionStopsAtTwo`*
14. **Refrain of N** targets `deck[iteration]`, runs it once with draw disabled then N-1 times with
    draw enabled at `iteration + 1`, only the outermost pays beat and rest and discards as many top
    verses as the chain went deep, every Refrain stamps its penalty *after* its copies, the count
    collapses to 1 past the iteration limit (×2 at 5, ×3 and ×4 at 4, ×10 at 3), and one charge of
    the target is spent for all the copies. *`RefrainTest.*`* (third trace)
15. **Impose Latch / Fuse / Epitaph** scans over Modifier, Passive, Control and Multicast verses
    (running each modifier it passes with draw disabled, free), stops at the first verse with a
    prototype among Projectile, Static, Material and Utility, discards the scanned verses and the
    target, spawns one Latch per body of the target if any valid payload verse remains anywhere in
    unread, otherwise runs the target with draw disabled; the target loses a charge, the passed
    modifiers do not. *`ImposeTest.*`*
16. **Clauses branch by discarding.** A failing Clause discards from the top through the first
    Otherwise (else through the End Clause, else just the next verse); a passing Clause with an
    Otherwise discards from the Otherwise through the End Clause, or only the Otherwise itself when
    there is no End Clause, or from the Otherwise to the end of the pile when another Clause comes
    before any End Clause (the Lua's `endpoint` arithmetic, not the wiki's summary). The scan stops
    at the next Clause, so nothing nests, and Otherwise and End Clause belong to whichever Clause
    reaches them first. Every Other flips a toggle shared by all four incantations.
    *`ClauseTest.*`* (fourth trace)
17. **Wild verses** pick with `world.random`: Wild Verse from every known verse, Wild Bolt / Wild
    Mark from known verses of the type, Blind Draw and Blind Trio from unread plus read (spending a
    charge), Wild Recall from the other three incantations. *`WildTest.*`*
18. **Reprise** re-runs every verse in hand except itself, then draws one. *`RecallTest.reprise`*
19. **Epic** draws the rest of the unread pile. *`MulticastTest.epicDrawsTheRest`*
20. **Formations** set the pattern and reduce spread; scatters add spread; the plain multicasts do
    nothing but draw. *`MulticastTest.*`*
21. **The caps fray, deterministically.** The same incantation, mana, breath and world produce the
    same plan and the same events, every time. *`CapsTest.*`*

## 9. The catalogue

Costs are in the mod's mana (pool 100 by default, 3 per second), beats and rests in ticks, damage
in half-hearts, speed in blocks per tick. Every number is first-pass tuning except the *shape* of
each verse, which is Noita's. Draw is what the verse pulls after itself.

### Projectiles (leaves; `PROJECTILE`)

| id | Name | Noita | Mana | Uses | Beat | Prototype and deltas |
|---|---|---|---|---|---|---|
| `needle` | Needle | Spark Bolt | 4 | ∞ | +1 | `needle`: 3.0 dmg, 1.6 b/t, 40 t, r 0.15; spread -1, crit +5 |
| `needle_latch` | Needle with Latch | Spark Bolt with Trigger | 6 | ∞ | +1 | `needle`, Latch draw 1 |
| `needle_fuse` | Needle with Fuse | Spark Bolt with Timer | 6 | ∞ | +1 | `needle`, Fuse 4 t draw 1 |
| `needle_twin_latch` | Twin-Latch Needle | Spark Bolt with Double Trigger | 8 | ∞ | +1 | `needle`, Latch draw 2 |
| `orb` | Orb | Magic Bolt | 7 | ∞ | +2 | `orb`: 5.0 dmg, 1.0 b/t, 60 t, r 0.3; crit +5 |
| `orb_latch` | Orb with Latch | Magic Bolt with Trigger | 9 | ∞ | +2 | `orb`, Latch draw 1 |
| `orb_fuse` | Orb with Fuse | Magic Bolt with Timer | 9 | ∞ | +2 | `orb`, Fuse 8 t draw 1 |
| `orb_epitaph` | Orb with Epitaph | (expiration-trigger bolt) | 9 | ∞ | +2 | `orb`, Epitaph draw 1 |
| `shard` | Shard | Heavy bolt | 12 | ∞ | +4 | `shard`: 9.0 dmg, 0.7 b/t, 60 t, r 0.35; knockback +1.0 |
| `ember` | Ember | Fireball | 14 | 15 | +12 | `ember`: FIRE, 6.0 dmg, 0.9 b/t, 50 t, explosion r 2.0 / 2.5, BURN on hit; spread +4, recoil +20 |
| `arc_bolt` | Arc Bolt | Lightning Bolt | 16 | ∞ | +17 | `arc`: 7.0 dmg, 2.0 b/t, 20 t, r 0.2, SHOCK on hit; recoil +60 |
| `balm_dart` | Balm Dart | Healing Bolt | 8 | 20 | +1 | `dart`: heals 4.0, 1.0 b/t, 50 t; spread +2 |
| `whisper` | Whisper | Chainsaw | 1 | ∞ | =0 | `whisper`: 2.5 dmg, 0.6 b/t, 4 t, r 0.25; spread +6, rest -3; **sets** beat to 0 |
| `blink_dart` | Blink Dart | Teleport bolt | 10 | ∞ | +3 | `blink`: 1.0 dmg, 1.5 b/t, 30 t; carries the caster to the impact |
| `wild_bolt` | Wild Bolt | Random Projectile | 6 | ∞ | — | `recursive`; runs a random known Projectile verse |

### Statics (`STATIC`; spawn a block ahead of the hand, or at the trigger point inside a payload)

| id | Name | Noita | Mana | Uses | Beat | Prototype and deltas |
|---|---|---|---|---|---|---|
| `detonation` | Detonation | Explosion | 20 | ∞ | +1 | `burst`: explosion r 3.0 / 8.0, instant; screenshake |
| `rime_ring` | Rime Ring | Freeze Field | 14 | 15 | +5 | `ring`: r 3.0, 100 t, FREEZE pulse every 10 t |
| `storm_ring` | Storm Ring | Electrocution Field | 18 | 15 | +5 | `ring`: r 3.0, 100 t, SHOCK pulse, 1.0 dmg |
| `balm_ring` | Balm Ring | Regeneration Field | 16 | 6 | +5 | `ring`: r 3.0, 100 t, heals 1.0 every 20 t |
| `uplift_ring` | Uplift Ring | Levitation Field | 10 | ∞ | +5 | `ring`: r 3.0, 100 t, UPLIFT |
| `void_pit` | Void Pit | Black Hole | 40 | 3 | +27 | `pit`: r 2.5, 80 t, pulls bodies and beings in, 1.0 dmg every 10 t; never unlimited |
| `held_word` | Held Word | Delayed Spellcast | 8 | ∞ | +3 | `word_held`: static, 20 t; Epitaph draw 3 |

### Modifiers (`MODIFIER`; every one draws 1)

| id | Name | Noita | Mana | Beat | Effect |
|---|---|---|---|---|---|
| `weight` | Weight | Damage Plus | 3 | +2 | damage +2.5, recoil +10 |
| `ballast` | Ballast | Heavy Shot | 5 | +3 | damage +6.0, speed ×0.35, recoil +50 |
| `haste` | Haste | Speed Up | 2 | 0 | speed ×2.5 |
| `endurance` | Endurance | Increase Lifetime | 6 | +4 | lifetime +25 t |
| `true_aim` | True Aim | Reduce Spread | 1 | 0 | spread -60 |
| `keen_edge` | Keen Edge | Critical Plus | 3 | 0 | crit +15 |
| `ricochet` | Ricochet | Bounce | 2 | 0 | bounces +10 |
| `sink` | Sink | Gravity | 1 | 0 | gravity +0.04 |
| `loft` | Loft | Anti-Gravity | 1 | 0 | gravity -0.02 |
| `volatile` | Volatile | Explosive Projectile | 8 | +13 | explosion r +1.5, explosion dmg +1.5, speed ×0.75, recoil +30 |
| `second_wind` | Second Wind | Reduce Recharge | 5 | -3 | rest -7 |
| `blunt` | Blunt | Zero Damage | 2 | -2 | nulls every damage number, lifetime +90 |
| `seeker` | Seeker | Homing | 12 | 0 | SEEKER |
| `sightline` | Sightline | Autoaim | 6 | 0 | SIGHTLINE |
| `puncture` | Puncture | Piercing Shot | 16 | 0 | PUNCTURE, damage -1.5, friendly fire on |
| `serpentine` | Serpentine | Sinewave | 2 | 0 | SERPENTINE, speed ×2 |
| `gyre` | Gyre | Orbit | 2 | -2 | GYRE, damage +0.5, lifetime +8 |
| `errant` | Errant | Chaotic Path | 2 | 0 | ERRANT |
| `relay` | Relay | Chain Spell | 12 | 0 | RELAY, lifetime -10, damage -1.0, explosion r -1.0, spread +10 |
| `twin_path` | Twin Path | Quantum Split | 4 | +2 | TWIN_PATH |
| `naught` | Naught | Nolla | 1 | -5 | NAUGHT (lifetime becomes 0) |
| `undying` | Undying | Infinite Lifetime | 20, 3 uses | +4 | UNDYING |
| `wellspring` | Wellspring | Add Mana | -12 | +3 | a refund |
| `flame_wreath` | Flame Wreath | (fire typing) | 5 | 0 | school FIRE, BURN on hit |
| `rime_wreath` | Rime Wreath | Freeze Charge | 5 | 0 | school WATER, FREEZE on hit |
| `storm_wreath` | Storm Wreath | Electric Charge | 5 | 0 | SHOCK on hit, damage +0.5 |
| `umbral_wreath` | Umbral Wreath | Curse | 6 | 0 | school DARK, WITHER on hit |
| `fire_wake` | Fire Wake | Fire Trail | 4 | 0 | wake FIRE, amount +5 |
| `water_wake` | Water Wake | Water Trail | 3 | 0 | wake WATER, amount +5 |
| `frost_wake` | Frost Wake | (freezing trail) | 4 | 0 | wake FROST, amount +5 |
| `lantern` | Lantern | Light | 1 | 0 | LANTERN, light 12 |
| `uplift` | Uplift | (levitation on hit) | 4 | 0 | UPLIFT on hit |
| `displace` | Displace | Teleportation | 6 | 0 | DISPLACE on hit |
| `bursting_ricochet` | Bursting Ricochet | Bounce-explosion | 8 | +8 | bounces +1, BOUNCE_BURST, recoil +20 |
| `wild_mark` | Wild Mark | Random Modifier | 6 | — | `recursive`; runs a random known Modifier verse |

### Multicasts (`MULTICAST`)

| id | Name | Noita | Mana | Uses | Draw | Effect |
|---|---|---|---|---|---|---|
| `couplet` | Couplet | Double Spell | 0 | ∞ | 2 | — |
| `tercet` | Tercet | Triple Spell | 2 | ∞ | 3 | — |
| `quatrain` | Quatrain | Quadruple Spell | 4 | ∞ | 4 | — |
| `octave` | Octave | Octuple Spell | 12 | ∞ | 8 | — |
| `loose_couplet` | Loose Couplet | Double Scatter | 0 | ∞ | 2 | spread +10 |
| `loose_tercet` | Loose Tercet | Triple Scatter | 1 | ∞ | 3 | spread +20 |
| `cleft` | Cleft | Formation: Bifurcated | 2 | ∞ | 2 | pattern 45, spread -8 |
| `trident` | Trident | Formation: Trifurcated | 3 | ∞ | 3 | pattern 20, spread -5 |
| `mirror` | Mirror | Formation: Behind Your Back | 0 | ∞ | 2 | pattern 180, spread -5 |
| `column` | Column | Formation: Above and Below | 3 | ∞ | 3 | pattern 90, spread -8 |
| `pentacle` | Pentacle | Formation: Pentagon | 5 | ∞ | 5 | pattern 180, spread -12 |
| `hexad` | Hexad | Formation: Hexagon | 6 | ∞ | 6 | pattern 180, spread -15 |
| `epic` | Epic | Myriad Spell | 20 | 10 | all | draws the rest of the unread pile |

Pattern: bodies of one shot are fanned across the pattern. Below 180 the fan is inclusive
(`-p .. +p` in N-1 steps, so Column is -90, 0, +90); at 180 it is the full circle exclusive
(Mirror is 0 and 180, Hexad every 60). One body gets 0. Spread is a random deviation per body.

### Utilities (`UTILITY`)

| id | Name | Noita | Mana | Beat | Rest | Effect |
|---|---|---|---|---|---|---|
| `far_word` | Far Word | Long-Distance Cast | 0 | -2 | 0 | Epitaph draw 1 on `word_far` (2.0 b/t, 10 t, no damage) |
| `step_word` | Step Word | Teleport Cast | 18 | +7 | 0 | spread +24; Epitaph draw 1 on `word_step`, which carries the caster |
| `near_word` | Near Word | Caster Cast | 4 | 0 | 0 | spread -24, NEAR_WORD (the next bodies spawn on the caster), draw 1 |
| `fresh_page` | Fresh Page | Wand Refresh | 6 | 0 | -8 | `recursive`; see rule 10 |
| `blood_toll` | Blood Toll | Blood Magic | -30 | -7 | -7 | pays 4.0 as `magical:blood_price` true damage, draw 1 |

### Control (`CONTROL`)

| id | Name | Noita | Mana | Uses | Beat / Rest | Rule |
|---|---|---|---|---|---|---|
| `refrain_2` | Refrain of Two | Divide By 2 | 10 | ∞ | +7 / 0 | penalty damage -1.0, explosion r -1.0; collapses at iteration 5 |
| `refrain_3` | Refrain of Three | Divide By 3 | 20 | ∞ | +10 / 0 | -2.0, -2.0; collapses at 4 |
| `refrain_4` | Refrain of Four | Divide By 4 | 30 | ∞ | +13 / 0 | -3.0, -4.0; collapses at 4 |
| `refrain_10` | Refrain of Ten | Divide By 10 | 50 | 5 | +27 / +7 | -7.5, -8.0; collapses at 3 |
| `impose_latch` | Impose Latch | Add Trigger | 4 | ∞ | 0 | rule 15, Latch |
| `impose_fuse` | Impose Fuse | Add Timer | 6 | ∞ | 0 | rule 15, Fuse 7 t |
| `impose_epitaph` | Impose Epitaph | Add Expiration Trigger | 6 | ∞ | 0 | rule 15, Epitaph |
| `recall_first` | Recall First | Alpha | 12 | ∞ | +5 | `recursive`; read, then hand, then unread |
| `recall_last` | Recall Last | Gamma | 12 | ∞ | +5 | `recursive`; unread, then hand |
| `recall_pair` | Recall Pair | Tau | 20 | ∞ | +12 | `recursive`; `deck[1]`, `deck[2]` |
| `recall_all` | Recall All | Omega | 60 | ∞ | +17 | `recursive`; everything, draw disabled |
| `recall_modifiers` | Recall Modifiers | Mu | 30 | ∞ | +17 | `recursive`; sweep, restore, draw 1 |
| `recall_projectiles` | Recall Projectiles | Phi | 30 | ∞ | +17 | `recursive`; sweep, restore |
| `recall_statics` | Recall Statics | Sigma | 30 | ∞ | +10 | `recursive`; sweep, restore, draw 1 |
| `wild_recall` | Wild Recall | Zeta | 4 | ∞ | 0 | `recursive`; a verse from another incantation, draw disabled, then draw 1 |
| `clause_outnumbered` | Clause: Outnumbered | Requirement: Enemies | 0 | ∞ | 0 | passes with 6+ hostiles within 16 |
| `clause_crowded` | Clause: Crowded Sky | Requirement: Projectiles | 0 | ∞ | 0 | passes with 12+ projectiles within 16 |
| `clause_wounded` | Clause: Wounded | Requirement: Low Health | 0 | ∞ | 0 | passes at 25% health or less |
| `clause_every_other` | Clause: Every Other | Requirement: Every Other | 0 | ∞ | 0 | alternates on a shared toggle |
| `otherwise` | Otherwise | Requirement: Otherwise | 0 | ∞ | 0 | marker; draws 1 |
| `end_clause` | End Clause | Requirement: Endpoint | 0 | ∞ | 0 | marker; draws 1 |
| `wild_verse` | Wild Verse | Random Spell | 3 | ∞ | 0 | `recursive`; any known verse |
| `blind_draw` | Blind Draw | Draw Random | 6 | ∞ | 0 | `recursive`; from unread plus read, spends a charge |
| `blind_trio` | Blind Trio | Draw Three Random | 12 | ∞ | 0 | `recursive`; three of the above |
| `reprise` | Reprise | Spell Duplication | 45 | ∞ | +7 / +7 | `recursive`; rule 18 |

### Deferred (types exist, no verses yet)

`MATERIAL` (sprays, seas, touches: world-modifying, needs its own design), `PASSIVE` (transparent
to composition; a passive is "while this verse is written", which needs the Grimoire to be read
by the passive system). Plus the Noita cards listed under deviation 9.

## 10. Runtime binding

**Skills.** Four authority skills, `incantation_1..4` ("Incantation I..IV"), `MagicSchool.ARCANE`,
`MagicAttribute.ARCANE`, tier -6, base mana 0 and cooldown 0 because both are self-managed, colour
`0xF0F4FF`, registered in `MagicContent`, listed in `AUTHORITY_SKILLS` and in
`AuthorityContent.AUTHORITY_OF_MANA`. Each is `SkillCastRegistry.selfManaged(ctx ->
IncantationService.recite(ctx, k))`. Because they are authority skills their bodies are refused by
Sovereign Aegis automatically (`MagicCounterService.canAegisCounterIncoming`), so the body entity
reports the recite skill as its `CounterableSkillThreat` id.

**The service.** `IncantationService.recite(ctx, slot)`: refuse with a message if the incantation
is empty or the skill cools; `stats = MagicSinService.adjustStatsBeforeCast(player, state,
ctx.stats())` for `costScale`; `session = sessions(player)[slot]`, rebuilt from the Grimoire if
absent; `plan = Reciter.recite(session, breath, state.mana(), stats.costScale(), new
LevelReciteWorld(player, state, slot))`; bill through `MagicSinService.spendManaForSkill(player,
state, plan.manaSpent())` when positive and `state.addMana(-plan.manaSpent())` when a refund;
`VerseBodySpawner.spawn(level, player, plan.root(), hand, aim, skillId)`;
`state.setSkillCooldown(skillId, plan.cooldownTicks())`; `state.sync(player)`; an actionbar line
with the next unread verse and the pile size, the way the Pile reports `n/cap`. Sessions are keyed
by UUID and dropped on logout, respawn, dimension change and `clearAuthority`, exactly as
`PileService` drops the Pile.

**The body.** One entity, `VerseBodyEntity extends Entity implements CounterableSkillThreat`,
registered as `verse_body`, `.sized(0.4, 0.4)`, tracking range as `SPELL_EFFECT`. Synced:
prototype index, school ordinal, radius, life, a behaviour bitmask, a wake bitmask, the direction.
Server-only NBT: the full stamped `ShotState` and the payload `ShotPlan` (through `ShotPlanCodec`),
so a body in an unloaded chunk keeps its payload. Its tick: advance by `speed × speedMultiplier`
along a ray (`level().clip`, so no speed is too fast for collision, as `BlackFlameProjectileEntity`
does), apply gravity, then the behaviours in a fixed order (Seeker steers toward the nearest
hostile in 12 blocks; Sightline aims once at spawn; Serpentine offsets laterally on a sine; Gyre
spirals around the caster; Errant turns by a seeded random each 5 ticks; Twin Path spawns its
sibling at spawn; Relay re-casts the body from the impact point; Naught expires on the first tick;
Undying never expires; Lantern lights; Near Word is read by the spawner). On an entity hit:
`MagicDamageService.hurt(target, damageSources().indirectMagic(this, caster), damage, skillId)` with
a crit roll, the hit effects, the explosion if any, the Latch payload, then discard unless Puncture.
On a block hit: bounce if bounces remain, else explosion, Latch payload, discard. On the Fuse's
tick or on expiry: the Fuse or Epitaph payload. Statics do not move: they pulse their effect over
their radius every 10 ticks for their duration, and Detonation is a static with duration 1.

**Damage** is one number stamped from `prototype.damage + damageAdd` (or zero under Blunt), typed
by the school only for colour and the hit effects: the body's own (`prototype.hit()`: Ember's BURN,
Arc Bolt's SHOCK) and the ones a Wreath, Uplift or Displace stamped on it (`stamped().hitEffects()`),
and the spawner applies both. BURN sets fire, FREEZE applies slowness, SHOCK a short stun through
knockback, WITHER the wither effect, UPLIFT levitation, DISPLACE moves the target a short random
distance. Healing bodies heal instead. Explosions damage everything in
radius including the caster.

**The spawner.** `VerseBodySpawner.spawn(level, caster, ShotPlan, origin, aim, skillId)`: fan
angles from `pattern` and body count as §9 describes, a random deviation within `spread` per body,
speed from the prototype times the stamped multiplier, statics placed one block ahead of the hand
(or at the release point), Near Word bodies placed on the caster. `release(level, caster, payload,
at, travelDirection)` is the same call from a body's Latch, Fuse or Epitaph. Bodies per release
are capped at `ReciteCaps.MAX_BODIES` too.

**Rendering.** `VerseBodyRenderer` draws one look per prototype (`Look`: NEEDLE, ORB, SHARD,
EMBER, ARC, DART, WHISPER, BLINK, RING, BURST, PIT, WORD), coloured by the school's
`SchoolMaterial` ramp, sized by the radius, with a glyph per behaviour on the body and a wake per
`Wake`, through the existing FX painters on `MagicalFxRenderTypes`. One renderer whose picture is a
function of the synced state, never one renderer per verse, per the visual contract.

**Commands.** `/magical incantation set <slot> <breath> <verses...>` (the payload's path, for
captures), `/magical incantation know all|<id>`, `/magical incantation show <slot>`,
`/magical incantation preview <slot>` (runs the Reciter on a copy and prints the plan),
`magical-debug recite <slot>`.

## 11. What is removed

The Ledger kit in full, in one commit series inside Plan 3, after the new kit exists so the build is
never red:

- `magic/mana/` (eight files) and `src/test/.../magic/mana/ManaLedgerTest.java`.
- `client/ManaAuthorityInput.java`, `client/WritOverlay.java`, their ladder entry at
  `MagicalClientEvents.java:251`, the scroll and mouse hooks at 423 and 430, the finish at 216, the
  layer call at `HudLayers.java:139`.
- `network/ApplyWritPayload.java`, its registration at `MagicalNetwork.java:211-217` and
  `sendWrit` at 397.
- `MagicCastContentKept.java:56-63`; `MagicCastingService.java:318` (the silence check);
  `MagicSinService.java:70` (the `WritLaw.apply` wrap) and `:111` (`witnessCast`);
  `MagicGameplayEvents.java:62` and `:246-247` (Mana Form).
- `PlayerMagicState`: the `manaLedger` field, `manaLedger()`, `inManaForm()`,
  `setManaFormTicks`, their copy, save, load and clear lines.
- `MagicContent.OPEN_LEDGER`, `WRIT`, `MANA_FORM` and their `AUTHORITY_SKILLS` entries;
  `AuthorityContent.AUTHORITY_OF_MANA`'s list.
- `MagicalCommands.java:228-260` (the `ledger` literal) and its handler at 1110-1153.
- The lang keys `skill.magical.open_ledger*`, `skill.magical.writ*`, `skill.magical.mana_form*`,
  `message.magical.writ_*`, `message.magical.ledger_*`, `message.magical.mana_form_*`.
- `RuleWheelPainter` stays (the Space Writ overlay uses it); `SubspaceLedger` stays (a space
  renderer, unrelated); the `Ledger` and `Gilded Ledger` passives stay.

## 12. Build order

Five plans, each producing working, tested software on its own.

| Plan | Delivers | File map |
|---|---|---|
| **1. The core** (`docs/superpowers/plans/2026-09-19-incantation-core.md`) | The pure evaluator and every verse's rule, under JUnit, with the full catalogue as data. No Minecraft beyond `ResourceLocation` and NBT. | `magic/incantation/`: `VerseType`, `PayloadKind`, `Behaviour`, `HitEffect`, `Wake`, `ShotState`, `Verse`, `VerseAction`, `VerseCatalogue`, `VersePrototype`, `VersePrototypes`, `Incantation`, `Grimoire`, `IncantationValidator`, `VerseCard`, `ReciteSession`, `ReciteWorld`, `ReciteEvent`, `ShotPlan`, `ProjectilePlan`, `RecitePlan`, `ReciteCaps`, `Recital`, `Reciter`, `VerseContent`, `ProjectileVerses`, `StaticVerses`, `ModifierVerses`, `MulticastVerses`, `UtilityVerses`, `ControlVerses`; tests `ShotStateTest`, `GrimoireTest`, `IncantationValidatorTest`, `ReciteSessionTest`, `ReciterTest`, `OverrunTest`, `PayloadTest`, `MulticastTest`, `RecallTest`, `RefrainTest`, `ImposeTest`, `ClauseTest`, `WildTest`, `CapsTest`, `VerseContentTest` |
| **2. The body** (`docs/superpowers/plans/2026-09-19-incantation-runtime.md`, Phase A, Tasks 1-6) | The entity, its behaviours, hit effects, wakes, statics, explosions, payload release, the spawner and the renderer, exercised by game tests with a hand-built `ShotPlan`. | `entity/verse/VerseFan`, `ShotPlanCodec`, `VerseHitEffects`, `VerseBehaviours`, `VerseBodyEntity`, `VerseBodySpawner`; `registry/MagicalEntities` (+1); `client/renderer/verse/VerseLooks`, `VerseBodyRenderer`; `MagicalClientEvents.registerRenderers` (+1); tests `VerseFanTest`, `ShotPlanCodecTest`, `VerseHitEffectsTest`, `VerseBehavioursTest`, `VerseLooksTest`, `magic/incantation/VerseBodyGameTests` |
| **3. The Authority** (the same plan, Phase B, Tasks 7-12) | The four skills, the service, sessions, `LevelReciteWorld`, the Grimoire on the state, the payload, commands, HUD glyphs, lang, the codex row label, the Ledger's removal, `AuthorityGrantTest` green. | `magic/incantation/IncantationService`, `LevelReciteWorld`, `SetIncantationPayloadCaps`; `network/SetIncantationPayload`, `MagicalNetwork` (+1 handler, +1 sender); `MagicContent` (+4, -3); `AuthorityContent`; `PlayerMagicState` (the slot); `MagicGameplayEvents` (three drop hooks); `MagicCastContentKept`; `registry/MagicalCommands`; `client/hud/HudGlyphs` (four stamps); `magic/menu/MagicPyramidMenu` (+1), `MagicPyramidScreen` (the row label); `MagicalClientEvents` (ladder entry removed); `en_us.json`; `CLAUDE.md`; tests `PlayerMagicStateGrimoireTest`, `VerseLangKeysTest`, `IncantationServiceTest`, `AuthorityRowLabelTest`, `IncantationGameTests` |
| **4. The content pass** | Numbers tuned in play, the deferred behaviours that need art (Relay, Twin Path, Gyre), captures in `CLAUDE.md`. | the `*Verses` files, `VerseLooks`, `CLAUDE.md` |
| **5. The Grimoire screen** | The editor: a codex-style tab with the verse shelf, the four incantations, the preview pane fed by the Reciter, and the payload. Its own design doc first. | `client/screen/grimoire/` |

## 13. Fiction

`authority.magical.authority_of_mana.desc`: *"The right to compose magic from nothing. Not to cast a
spell someone else finished, but to write one, verse by verse, and have the world read it back."*

The four skills read "Incantation I" to "Incantation IV". The codex row label at
`MagicPyramidScreen.java:426` is hard-coded to the Authority of Space and is fixed in Plan 3 to read
the held Authority's name key.

The counter ring's two Mana edges (Mana answers Space and Dreams; Word and Chaos answer Mana) were
written for the Weave and are re-worded when the ring is built: Mana → Space is a Clause-gated
Refrain that empties a subspace's mana refresh; Chaos → Mana stays as written, a suspended recite
has not been billed.

## 14. Open items

1. Acquisition: how a verse becomes *known*. Granted with the Authority, found, or witnessed like
   the Ledger did. The Grimoire has the set; nothing fills it yet except the command.
2. Whether uses regenerate. Noita's do not; a verse at zero uses stays written and is skipped until
   the incantation is edited. Kept as Noita's for now.
3. Blood Toll's price against the Blood school's own vocabulary (`BloodService`), so two systems do
   not name the same true damage differently.
4. The HUD line for the next unread verse: a card annotation on the sigil rather than an actionbar
   string, once the Grimoire screen exists.
5. Hand-offs from the core's review, for Plan 2 and Plan 3:
   - The spawner applies `prototype.hit()` (Ember's BURN, Arc Bolt's SHOCK) as well as the stamped
     `hitEffects`, or those two bodies silently lose their effect.
   - An Impose's declared "draw 1" is its carrier's payload draw, and a Clause's holds in both
     branches; the tooltip should say so ("payload: 1").
   - `Reciter.recite` writes uses back to the incantation it was built from and calls
     `ReciteWorld.payHealth` mid-recital (Blood Toll draws first, then pays, as the Lua does); a
     preview session is built from a copy, and the service clamps `manaLeft` to the pool, because a
     refund (Wellspring, Blood Toll) can push it past.
   - `MAX_STEPS` bounds work, not stack depth: a chain of drawing modifiers nests about six frames a
     step, so the service guards the call or the constant is justified in a comment.
6. Known minor gaps left after review, none load-bearing: `ShotPlan`/`ProjectilePlan` hand out the
   live `ShotState` (document it read-only, or freeze it); `Grimoire.incantation(slot)` clamps instead
   of rejecting an out-of-range slot; `Verse.bodies()` above 1 and the Refrain collapses at 4 and 3 are
   untested; the Wild knowledge gate is pinned only for Wild Verse; `copyQuiet` restores `dontDraw` to
   false and a Wild pick past 100 rolls restarts the recursion budget, both Lua-identical;
   `ControlVerses.impose` keeps a `default` arm the other two carrier switches spell out;
   `Recital.Frame.draw`, `depth()` and `drawDisabled()` have no caller yet.
