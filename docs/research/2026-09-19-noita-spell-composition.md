# Noita's spell composition, read for the Authority of Mana

*Research notes, 2026-09-19. Wand properties are out of scope by decision: the wielder casts the sequence directly, so shuffle, capacity, the wand's mana pool, its recharge speed and always-cast are treated as gone. What is left is the spell grammar itself, which is the part worth taking.*

## Sources

- `data/scripts/gun/gun.lua` (671 lines), the evaluator, and `data/scripts/gun/gun_actions.lua` (11,125 lines), every spell written as a Lua function over one shared shot state. Read from the mirror at github.com/NathanSnail/noitadata.
- The salinecitrine wand simulator (github.com/salinecitrine/noita-wand-simulator), a TypeScript port of those two files; its `types.ts` carries the full shot-state field list the game keeps in C++.
- Noita wiki (noita.wiki.gg): Expert Guide: Draw; Advanced Guide: Wand Mechanics; Expert Guide: Divide By Spells; Expert Guide: Advanced Spell Specifics; Expert Guide: Discards from the Hand; Greek Spells; Add Trigger; Requirement; Multicast; Spells.

The Lua is the truth and the wiki explains it. Every number below is from the Lua.

## 1. The model in one paragraph

A spell is a **program**. The wand holds an ordered list of **cards**; a click starts a **draw** that pulls cards off the top of that list until a draw budget runs out; every card is a **function** that mutates one shared **shot state** and may draw more cards; every projectile added while the program runs is stamped with the shot state as it stood at that instant. That is the whole machine. Modifiers, multicasts, triggers, Greek letters, Divide By, Requirements and Wand Refresh are all just cards that do something to the state, to the draw budget, or to the piles the cards sit in. There is no binding logic anywhere that decides which modifier belongs to which projectile. The binding is purely temporal.

## 2. The machine, exactly (gun.lua)

### Piles

Three lists: **deck** (cards still to draw, in wand order), **hand** (cards drawn this click), **discard** (cards spent). A fresh wand starts with everything in the deck.

### A click

1. `_start_shot`: make a root shot with a fresh state `c` (every field at its default, then the wand's base stats copied in), a draw budget equal to the wand's *spells/cast*, and the wand's current mana.
2. `draw_shot(root)`: `draw_actions(budget, instant_reload = false)`.
3. `register_action(c)`: hand the root state to the game. Its `fire_rate_wait` becomes this click's **cast delay**, `current_reload_time` its **recharge**.
4. `_handle_reload`: hand goes to discard (consuming charges, see below). If the deck is now empty, or a wrap happened, discard goes back into the deck, is re-ordered, and a **recharge** starts. Otherwise the next click continues from where the deck was left.

### draw_actions(n, instant_reload)

For i in 1..n: `draw_action()`. If a draw *fails* (no mana, no charges), keep drawing until one succeeds or the deck is empty: a dead card is skipped, never a dead click. If the deck ran out and `reloading` was set, stop. If the global `dont_draw_actions` is raised, do nothing at all. That one switch is how every copy in the game is made without drawing (section 5).

### draw_action()

- Deck empty? If this draw came from *inside a card* (`instant_reload` is true for every card's own draws and false only for the root budget) and no Wand Refresh has happened this click: move the discard back into the deck, re-order, and flag `start_reload`. **This is wrapping.** The program continues from the top of the wand and the click ends with a recharge. For the root budget an empty deck simply ends the click.
- Pop the top card. Mana short? The card goes straight to discard and the draw returns *failed*. Charges at zero? Same. Otherwise pay the mana. A negative cost is a refund: Add Mana is -30, Blood Magic is -100 and takes health instead.
- `play_action`: push to hand, set `current_action`, **run the card's function**. If the card was a Projectile, Static Projectile or Material, note `got_projectiles` and run the perk-granted extra modifiers on top.

### What a card's function can do

- `add_projectile(xml)`: `BeginProjectile; EndProjectile`. The engine spawns the entity and copies the *current* `c` onto it.
- Mutate `c`: every numeric field, the comma-joined `extra_entities` and `game_effect_entities` strings, the material and trail strings.
- `draw_actions(k, true)`: pull k more cards into this same shot. A modifier is exactly "mutate, then draw 1". A multicast is "draw k, then set a pattern".
- `add_projectile_trigger_hit_world(xml, k)` / `_timer(xml, frames, k)` / `_death(xml, k)`: `BeginProjectile; BeginTrigger; draw_shot(create_shot(k)); EndTrigger; EndProjectile`. **The payload is a new shot with a fresh `c`**, drawn from the same deck at that moment, so nothing set before the trigger reaches the payload and nothing the payload sets leaks back out. Recharge (`current_reload_time`) is a global and does leak.
- Call another card's function directly (`data.action(rec)`), usually with `dont_draw_actions` raised. This is a **copy**. A copy never goes through `draw_action`, so it pays no mana and consumes no charge. The Greek letters, Divide By, Add Trigger, Random Spell and Spell Duplication all work this way.
- Move cards between piles by hand. Requirements, Divide By and Add Trigger **discard** cards off the top of the deck to skip them; Wand Refresh dumps everything into the discard and rebuilds the deck.

### Cost, delay, recharge, charges

- Mana is paid per card as it is drawn, before it runs. Copies are free.
- Each card adds to `c.fire_rate_wait` (frames, 60 per second), the cast delay of *this* shot; a payload's delay stays inside its own shot. Each card may add to `current_reload_time`, the recharge, which is global for the click. At the end the game waits for the larger of the two, not their sum. Chainsaw and Digger *set* `fire_rate_wait` to 0 rather than adding, so where they sit in a block matters.
- Charges (`max_uses`) are consumed in `move_hand_to_discarded`, and only if the block produced a projectile (`got_projectiles`) or the card is of type Other or Utility. A limited modifier with no projectile after it keeps its charge. A card at zero charges is skipped at draw but copied freely by the Greek letters. Divide By consumes one charge of its target for all N copies.

### Guards against infinity

- `recursion_limit = 2`, checked only for cards flagged `recursive` (the Greek letters, Random Spell / Projectile / Modifier, Draw Random, Spell Duplication, Wand Refresh, All Spells). A recursive card called at level 2 is refused. A non-recursive card passes its caller's level through unchanged. Alpha copying an Alpha copying an Alpha runs three times and the fourth is refused.
- Divide By's own **iteration** counter: nested Divides target successive cards and collapse to one copy past a per-spell depth (section 5).
- The draw budget itself. Everything that draws is bounded by the deck.

## 3. The shot state (the data structure everything hangs on)

`GunActionState`, with defaults in brackets, grouped by what the fields are rather than by name:

| Group | Fields |
|---|---|
| Timing | `fire_rate_wait` [0], cast delay in frames; `reload_time` [0] |
| Motion | `speed_multiplier` [1, clamped to 0..20 by every card that touches it], `child_speed_multiplier` [1], `dampening` [1], `gravity` [0], `bounces` [0], `lifetime_add` [0 frames], `spread_degrees` [0], `pattern_degrees` [0] |
| Damage | `damage_projectile_add`, `_melee_`, `_electricity_`, `_fire_`, `_explosion_`, `_ice_`, `_slice_`, `_healing_`, `_curse_`, `_drill_` [all 0; the UI shows them times 25], `damage_critical_chance` [0], `damage_critical_multiplier` [0], `explosion_radius` [0], `explosion_damage_to_materials`, `knockback_force`, `lightning_count`, `damage_null_all` (Zero Damage) |
| Matter | `material` and `material_amount` (what the projectile spawns), `trail_material` and `trail_material_amount` (a comma list) |
| Bundles | `extra_entities`: a comma list of entity files attached to *every* projectile spawned in this shot (homing, piercing, sinewave, orbit, chain shot, quantum split, rays, arcs, larpa, hit effects, colours, light, nolla, infinite lifetime, spells-to-power). `game_effect_entities`: statuses applied on hit (necromancy, teleportation, wet, curse) |
| Rules | `friendly_fire` [false], `physics_impulse_coeff` |
| Presentation | `screenshake`, `recoil`, `gore_particles`, `ragdoll_fx`, `blood_count_multiplier`, `light`, `sprite` |
| Bookkeeping | `action_draw_many_count`, `state_cards_drawn`, `action_type`, `action_mana_drain`, and the card's own metadata |

Plus `shot_effects.recoil_knockback`, a per-click number rather than a per-projectile one.

Three consequences of one shared state:

1. A modifier applies to **every projectile drawn after it in the same shot**, not to "the next spell". Damage Plus, Double Spell, Spark, Spark boosts both sparks. Only a new click or a trigger payload starts clean.
2. Order is arithmetic. Speed Up then Heavy Shot is times 2.5 then times 0.3. Chainsaw after Fireball zeroes the delay Fireball added.
3. Behaviour is data, not code. Homing is nothing but a string appended to `extra_entities`. This is what makes 179 modifiers cheap: a new one is a stat delta or a bundle name, and the evaluator never changes.

## 4. The eight card types and what each does to the machine

Counts are from `gun_actions.lua` (about 490 definitions, a few disabled inside comment blocks): Projectile 144, Static Projectile 46, Modifier 179, Draw-Many 14, Material 35, Other 43, Utility 25, Passive 5.

### Projectile (a leaf)

`add_projectile` plus its own state deltas: a cast delay, some spread, crit, recoil. Draws nothing, so it **ends a chain**. Spark Bolt: 5 mana, +3 delay, -1 spread, +5 crit. Fireball: 70 mana, 15 charges, +50 delay. Black Hole: 180 mana, 3 charges, +80 delay. Nuke: 200 mana, 1 charge, +600 recharge. The trigger variants (section 5) are projectiles that draw.

### Static projectile

Same shape, spawns in place: fields (freeze, electrocution, levitation, shield, regeneration), explosions, walls, swarms (four to six creatures from one card), rains, holes. Delayed Spellcast is a death trigger with draw 3.

### Material

Same shape again: sprays, seas, circles, Touch of Gold (300 mana, 1 charge, flagged never-unlimited).

### Modifier (mutate, then draw 1)

Every one ends in `draw_actions(1, true)`. Six mechanisms hide under the one type:

1. **Stat deltas.** Damage Plus (+0.4, shown as 10, +5 delay), Heavy Shot (+1.75 damage, speed times 0.3, +10 delay), Speed Up (times 2.5), Increase Lifetime (+75 frames, 40 mana), Reduce Spread (-60 degrees, 1 mana), Critical Plus (+15%), Bounce (+10 bounces, 0 mana), Gravity (+600), Explosive Projectile (+15 radius, +0.2 explosion damage, speed times 0.75, +40 delay), Reduce Recharge (-10 delay, -20 recharge), Zero Damage (nulls every damage field, +280 lifetime).
2. **Bundles** via `extra_entities`. Homing (70 mana), Autoaim, Piercing (140 mana, -0.6 damage, friendly fire on), Sinewave (speed times 2), Orbit, Chaotic Path, Ping-Pong, Quantum Split, Chain Spell (70 mana, -30 lifetime, +10 spread), Fireball Thrower (110 mana, 16 charges), Electric Arc, the Larpas (copies peel off the projectile in flight), Bounce-explosion, Nolla (lifetime zero, for death-trigger tricks), Infinite Lifetime (3 charges), Spells to Power, Light, the colours, and the hit effects (petrify, freeze, burning crit).
3. **On-hit statuses** via `game_effect_entities`. Necromancy, Teleportation of the target, Curse.
4. **Trails** via `trail_material`. Acid, oil, water, blood, gunpowder, fire.
5. **Economy.** Add Mana (cost -30, a refund, +10 delay), Blood Magic (Utility type, -100 mana, takes health), Money Magic (5% of gold into damage), Blood to Power (44% of health into damage, 20% of health as the price).
6. **Meta.** Random Modifier is recursive: pick any modifier card in the game and run it.

### Draw-many (multicast)

`draw_actions(k, true)` then a pattern. Double, Triple, Quadruple and Octuple Spell draw 2, 3, 4 and 8 at 0, 2, 5 and 30 mana with no other effect. The Scatter variants add spread (+10, +20 degrees). Formations set `pattern_degrees`, the arc the shot's projectiles are fanned across, and *reduce* spread: Bifurcated (2 cards, 45 degrees), Trifurcated (3, 20), Behind Your Back (2, 180), Above and Below (3, 90), Pentagon (5, 180), Hexagon (6, 180). Myriad Spell draws the entire remaining deck (50 mana, 30 charges). Nothing in a multicast knows what it drew: a Triple Spell in front of three modifiers just runs three modifiers, each of which draws one more.

### Utility

The cast-position spells are the interesting ones. Long-Distance Cast, Teleport Cast and Super Teleport Cast are **death triggers with draw 1 on a fast, short-lived carrier**, so "cast the next spell from over there" is the trigger mechanism reused; Long-Distance Cast even *reduces* cast delay by 5. Caster Cast is a bundle that makes the next projectile spawn on the caster. Wand Refresh (section 5), Temporary Wall and Platform, X-Ray, the All-X spells (every nuke in the world at once) and Summon Wand Ghost round it out.

### Other (control flow)

Add Trigger, Add Timer, Add Expiration Trigger; Divide By 2, 3, 4, 10; the Greek letters; the Requirement family; Random Spell; Draw Random; Spell Duplication; Summon Portal; Cessation (stops time, +600 delay and recharge); the Ocarina and Kantele notes, which are musical projectiles. Section 5 takes them one by one.

### Passive

Torch, Electric Torch, Energy Shield, Energy Shield Sector, Tiny Ghost. Their function is `draw_actions(1, true)` and nothing else. **A passive is transparent to composition**; its effect is "while this card is on the wand".

## 5. The control-flow cards, one by one

**Triggers** (Projectile type). Spark Bolt with Trigger: on collision, draw 1 into a fresh shot at the impact point (10 mana). Spark Bolt with Timer: after 10 frames, or on collision (10 mana). Spark Bolt with Double Trigger: draw 2 (15 mana). Delayed Spellcast: static, draw 3 on expiry. The payload is drawn from the same deck *during the click*, at the moment the trigger card runs; only its release is deferred. Payloads can hold triggers, so programs nest as deep as the deck allows, and a payload's draw can wrap.

**Add Trigger / Add Timer / Add Expiration Trigger** (Other; 10, 20, 20 mana). Turns any projectile-ish card into a trigger. Scan forward from the top of the deck over Modifier, Passive, Other and Draw-Many cards, **running each modifier it passes with draw disabled** (which is why they are free), and stop at the first Projectile, Static, Material or Utility card that has a `related_projectiles` entry (Wand Refresh and Blood Magic have none, so they are never targets). Discard everything scanned plus that target. If any valid payload card remains anywhere in the deck, spawn the target's projectile(s) as a collision trigger (timer: 20 frames) with draw 1 each; a card that fires several projectiles (Buckshot 3, the swarms 4 to 6, Infestation 10) gets one trigger per projectile. Otherwise run the target normally with draw disabled. The target loses a charge; the modifiers passed do not.

**Divide By N** (Other; D2 is 35 mana and +20 delay, D10 is 200 mana, +80 delay, +20 recharge and 5 charges). Look at `deck[iteration]`: the first card for a Divide drawn normally, the second for a Divide copied by a Divide, and so on. Run it once with draw disabled, then N-1 times with draw enabled, passing `iteration + 1` down so a nested Divide targets the next card along. Only the outermost Divide pays: it restores cast delay and recharge to what they were before the copies ran, then discards as many top cards as the chain went deep, so the real cards are never drawn. Each Divide stamps a penalty on the state *after* its copies have run, so it hits later copies and everything cast afterwards: -0.2 projectile damage and -5 explosion radius for D2, -1.5 and -40 for D10, and `pattern_degrees = 5` so the copies fan. Past the iteration limit the count collapses to 1: D2 at iteration 5, D3 and D4 at 4, D10 at 3. The practical ceiling is D10, D10, D4, D2 = 800 copies of one card, half of them with draw enabled, which is why dividing a modifier corrupts the deck (each drawing copy pulls another card) and why the wiki's fix is to end a chain in a collapsed Divide so nothing draws.

**Greek letters** (Other, all `recursive`). Each *calls a card's function* without drawing it: no mana for the copy, no charge, and it works on a card at zero charges.

- Alpha (40 mana, +15 delay): the first card, looking in discard, then hand, then deck. No draw.
- Gamma (40, +15): the last card, looking in deck, then hand. No draw.
- Tau (90, +35): `deck[1]` then `deck[2]`, both memorised before either runs. No draw of its own, but the copies draw if they would.
- Omega (320, +50): every card in the discard, then every *non-recursive* card in the hand, then every card in the deck, all with draw disabled, never Wand Refresh. It copies itself only out of the discard or the deck.
- Mu (120, +50): every Modifier in discard, hand and deck with draw disabled, then **restores mana, cast delay and recharge to what they were** (the whole sweep is free), then draws 1. Sigma (120, +30) is the same over Static Projectiles. Phi (120, +50) is the same over Projectiles, without the draw after.
- Zeta (10): a random card from another wand in the inventory, draw disabled, then draws 1.

**Requirements** (Other, 0 mana; draw 1 after). Compute a condition: Enemies (15 or more `homing_target` entities within 240 px), Projectiles (20 or more within 160 px), Low Health (25% or less), Every Other (a global toggle flipped on each cast and shared by every wand). Then scan the deck for the branch markers, stopping at the next Requirement: note the first *Otherwise* (`IF_ELSE`) and the first *Endpoint* (`IF_END`). If the condition fails, **discard from the top through the Otherwise** (or through the Endpoint, or just the next card if neither exists). If it passes and an Otherwise exists, discard from the Otherwise through the Endpoint, or to the end of the deck. Branching is done by throwing cards away, so there is no jump and no nesting: the scan halts at the next Requirement, and Otherwise and Endpoint belong to whichever Requirement reaches them first. Otherwise and Endpoint are themselves transparent (draw 1).

**Wand Refresh** (Utility, `recursive`; 20 mana, -25 recharge). Move hand and deck into the discard, then, the first time in a click, move the whole discard back into the deck in order and raise `force_stop_draws`, which forbids wrapping for the rest of the click. Three things fall out. The cards that were in hand never pass through the normal hand-to-discard step, so their charges are not consumed. The deck is full again at the end of the click, so no recharge starts. A second Refresh in the same click empties the deck without rebuilding it and forces a normal recharge.

**Random Spell, Random Projectile, Random Modifier** (`recursive`): pick a random unlocked card of the whole game, of the right type, and run it; no draw of their own. **Draw Random and Draw Three Random**: pick from this wand's deck plus discard and run, consuming a charge. **Spell Duplication** (250 mana, `recursive`, +20 delay and recharge): re-run every card currently in hand except itself, then draw 1. **Myriad**: draw the rest of the deck.

## 6. Where the depth comes from

Six properties, each cheap alone, that multiply:

1. **One shared mutable state with temporal binding.** Modifiers stack by arithmetic and apply to everything after them.
2. **Draw is the only sequencing primitive.** Modifiers draw 1, multicasts draw k, triggers draw into a child state. The deck is a tape, and how far a click reads is a function of the cards, not of a rule.
3. **Copies are function calls, not draws.** The Greek letters and Divide By multiply a card without paying for it, and the recursion and iteration counters are the only things standing between the player and infinity.
4. **Payloads are nested programs with clean state.** A trigger is a program that runs later, somewhere else, and knows nothing of the shot that carried it. The cast-position utilities are the same mechanism.
5. **Piles persist across clicks and can be edited by cards.** Skipping, discarding, refreshing and wrapping turn the deck into a machine with memory: a Requirement decides per click, Alpha reads the discard, Every Other alternates.
6. **The economy is mana per card plus charges per card**, both checked at draw time, both skipped over rather than failed on, both bypassed by copies. Every exploit the community has found (free modifiers under Add Trigger, free charges under Refresh, Mu's free sweep, 800-copy Divides) is an interaction between copies and the economy.

The design lesson: the richness is in the **evaluator and the state**, not in the card list. A port that gets sections 2 and 3 right with twenty cards will feel like Noita. A port with three hundred cards over a flat "pick a projectile, pick modifiers" resolver will not.

## 7. What the wand contributed, and what stands in for it

| Wand property | Role in composition | Without a wand |
|---|---|---|
| Shuffle | Randomises deck order | Gone. The sequence is what the wielder wrote. |
| Capacity | Maximum cards | Still needed as a length limit, for the editor and as the bound on evaluation cost. |
| Spells/cast | Root draw budget: how many cards a click reads before the cards themselves decide | The one property that shapes the grammar. See the fork below. |
| Mana max, charge speed | Per-wand pool and regen | The wielder's own mana pool and regen. |
| Cast delay, recharge (base) | Added to what the cards add | Base of 0. The cards' own delay and recharge become the cast's cooldown. |
| Spread, speed multiplier (base) | Base accuracy and speed | Cards only. |
| Always cast | A card outside the deck, run first every click | Unneeded; a passive slot if ever wanted. |
| Perk extra modifiers | Global modifiers applied to every projectile card | The mod's passives already fill this role. |

**The spells/cast fork.** With no wand there is no root budget, so the port has to choose:

- (a) **One press runs the whole tape.** Root budget equals the length of the sequence (Myriad at the root). Simplest to explain: one cast per press, no piles between presses. It loses wrapping, Every Other, Alpha reading the discard, and the "tape of blocks" play where a six-card wand is three different casts on three presses.
- (b) **The tape survives between presses.** Root budget 1 (or a number the wielder sets *on the spell*), the deck, hand and discard persist, and the tape recharges when it runs out. This is Noita's actual grammar, blocks and all, and it costs one extra concept in the UI: where in the tape the next press starts.

Noita's own fixed-budget wands show that (b) is the source of most of the depth, and (a) is a strict subset of it. Recommendation: (b), with the budget authored on the spell and defaulting to 1.

## 8. What this mod already has that fits

- **A cast pipeline that resolves numbers once.** `MagicSinService.adjustStatsBeforeCast` is where every cast's numbers resolve and `MagicCastingService.castResolved` is the dispatch. A program of this kind needs its own evaluator that emits a list of projectile specs, then hands each to a spawner. The nearest existing "state, then entity, then renderer" split is the forged strike: `ForgeStrikeMath.resolve`, `ForgeStrikeEntity.spawn`, synced data, `ForgeStrikeRenderer`.
- **A composable system at tier 1.** `arcane/` fuses one rune, one shape and a few unique modifiers into one `SpellResolution`, with rule-based rejections (a barrier cannot split). It is flat: one projectile, no sequence, no nesting, no state carried between cards. Noita's grammar is a strict superset, so the Authority can be the deep end of the same idea rather than a rival to it.
- **Per-player authored state with a save format.** The Fracture (five faults as a `ListTag`) is the precedent for a sequence authored by the player and carried on `PlayerMagicState`. The Ledger's witnessed list is the precedent for "you may only use what you have been shown". Both are cleared by `clearAuthority` and copied by `copyFrom`, and a program store would take the slot the Ledger has now.
- **Editors.** The Fracture overlay is five gates read left to right, which is exactly a tape, but a hold overlay is the wrong size for a program of a dozen cards with nested payloads. The codex-style screen (`ScreenChrome`, `CodexLayout` with its overlap tests) is the right size.
- **Bounded evaluation is not optional.** Noita runs on one player's frame; this runs on a server tick for every wielder. The evaluator needs hard caps: cards run per cast, projectiles per cast, payload depth, copies per Divide-like card. Noita's recursion limit of 2 is the model, not the ceiling.
- **Visuals.** The mod's contract forbids reused vanilla projectiles. One projectile entity whose look is a function of its shot state (element from the damage fields, bundle glyphs from the behaviour list, size from damage, trail from the trail field) scales to any number of cards. One renderer per card does not.

## 9. Design forks to settle before any code

1. One press per tape, or a persistent tape with a root budget (section 7).
2. The length limit and the economy: mana per card only, or charges too, or a third resource the Authority owns.
3. Which classes to port first. The minimum that feels like Noita is projectiles, modifiers, multicasts with formations, and triggers and timers with isolated payloads. The Greek letters, Divide By, Requirements, Add Trigger and Refresh are the second layer, and each needs its own guard.
4. Where cards come from: found in the world, granted with the Authority, or witnessed like the Ledger.
5. The shot-state fields a Minecraft projectile actually has (an element instead of Noita's ten damage types; speed, gravity, bounces, lifetime, homing, piercing, trails, on-hit statuses, spread, pattern) and how modifiers map onto the mod's elements and schools.
6. The fiction. The Authority of Mana currently reads "the right to price magic"; this kit makes it "the right to compose magic from nothing", and the lang line, the codex row and the tagline follow the decision.

## Appendix: worked traces

Deck `Damage Plus, Double Spell, Spark, Spark, Speed Up, Spark, Nuke`, budget 1.

- Click 1: Damage Plus (+0.4 damage, +5 delay) draws 1: Double Spell draws 2: Spark (projectile A, stamped +0.4), Spark (projectile B, the same). Budget spent. Hand to discard. Deck is now `Speed Up, Spark, Nuke`. Cast delay 5+3+3 frames.
- Click 2: Speed Up (times 2.5) draws 1: Spark (projectile C, fast, no damage bonus). Deck is `Nuke`.
- Click 3: Nuke. Deck empty, so a recharge of 600 frames starts.

Deck `Spark with Trigger, Damage Plus, Explosion, Speed Up, Spark`, budget 1.

- Click 1: Spark with Trigger spawns the carrier and opens a payload shot with a fresh state, drawing 1: Damage Plus (+0.4 into the payload state) draws 1: Explosion (stamped with the payload state). The payload closes. Budget spent. Deck is `Speed Up, Spark`. The carrier flies unmodified, the explosion on impact carries Damage Plus, and Speed Up never touched either.

Deck `Divide By 2, Divide By 2, Spark`, budget 1.

- Click 1: the outer D2 targets `deck[1]`, the inner D2, and runs it once with draw off and once with draw on, at iteration 2. Each inner run targets `deck[2]`, Spark, and runs it twice, then stamps its penalty. Four sparks: the first two carry no penalty, the next two carry -0.2, and the state ends at -0.6 for anything cast after. The outer D2 restores delay and recharge, then discards two cards (the inner D2 and the Spark). Mana paid: 35, once.

Deck `Requirement Low Health, Healing Bolt, Otherwise, Spark, Endpoint`, budget 1, at full health.

- The condition fails (health above 25%): discard from the top through Otherwise (Healing Bolt, Otherwise). Draw 1: Spark. Endpoint remains and is drawn transparently on the next click.
