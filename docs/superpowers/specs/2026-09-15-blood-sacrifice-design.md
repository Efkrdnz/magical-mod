# Blood Sacrifice: a ritual paid in a full Vessel, for a boon and a price

**Date:** 2026-09-15
**School:** Blood (layer -1)
**Status:** designed

## The idea

Blood Sacrifice is the seventh blood active and the only one that is not a spell. Casting it opens
a screen. On that screen the player buys temporary boons with points and pays for them by choosing
temporary prices worth the same. Sealing the pact drains the whole Crimson Vessel, grants the
chosen boons for a minute and the chosen prices for half again as long, and both show up in the
Magic Codex's Passives tab with a countdown.

Three things make it a ritual rather than a buff button:

1. **It costs a full Vessel and nothing else.** A hundred blood, from the Vessel only. Bleeding for
   it is not allowed, so the ritual is something a blood mage earns by fighting well rather than
   something they can panic-cast at low health.
2. **The price is chosen, not rolled.** The player decides which weakness they can live with for
   the next ninety seconds. A bad choice is their bad choice.
3. **The price outlives the boon.** Prices run 1.5x as long as boons, so the last thirty seconds of
   every ritual are spent paying for a boon that has already gone.

## What the player asked for, and what this spec answers

| Ask | Answer |
| --- | --- |
| Uses 100 blood, and *requires* 100 - health does not count | `BloodService.payFromVesselOnly`, a flat 100 never touched by `costScale` |
| Health -> blood conversion off real health, not barrier | `magical:blood_price`, a true-damage type with an early-out before the barrier |
| True damage, bypassing any reduction | four vanilla bypass tags plus the barrier early-out |
| Ten hearts is about 500 blood | `COST_PER_HEALTH` 8 -> 25; 20 HP = 500 blood exactly |
| Self buff with a curse attached, both chosen, both costing points | the point budget below |
| Curse lasts 1.5x the buff | `curseTicks = round(buffTicks * 1.5)` |
| A cool GUI for picking them | `BloodSacrificeScreen`, three columns, same chrome as the codex |
| Buffs and curses are real temporary passives in the codex Passives tab with a dispel timer | `ritualTicks` on the player state, two pinned groups at the top of the passives column |
| Temporary buffs pinned at the top under "Temporary Buff" | the first pinned group; prices are the second |
| A random curse for medium points, better or worse by RNG | **The Unknown**, 2 points, weighted table, can roll the 4-point one |
| A +25 dark-magic-debt curse that costs a lot | **Blood Debt**, 4 points, permanent corruption |
| A Hellbroker passive: +1 buff point free, and every curse stacks the others | `hellbroker`, amplification `1 + 0.5 * (n - 1)` capped at 2.25 |

## The economy

### Points

```
boonBudget      = 4 + min(4, tuning.size()) + (hellbroker ? 1 : 0)      -> 4 .. 9
boonSpent       = sum of the chosen boons' costs, must be <= boonBudget
priceRequired   = max(0, boonSpent - (hellbroker ? 1 : 0))
priceChosen     = sum of the chosen prices' costs, must be >= priceRequired
```

Prices may overshoot. Costs are lumpy, so a requirement of three with only two- and four-point
prices left has to round up somewhere, and letting it round up is also a build: more prices means
more of them under Hellbroker's amplification, which is exactly the trade a Hellbroker is making.

Boon budget is capped at four points of `size` because the tuning budget climbs to eleven with
proficiency and an unbounded budget would let a maxed player take every boon at once.

### Duration

```
boonTicks  = stats.durationTicks()           base 1200 (60s), raised by the duration tuning
priceTicks = round(boonTicks * 1.5)          base 1800 (90s)
```

### Hellbroker's amplification

Every amplified price is amplified by

```
amplification(n) = min(2.25, 1 + 0.5 * (n - 1))        n = active ritual prices
```

so one price is unchanged, two are 1.5x (the player's own example: a 15% failure chance becomes
22.5%), three are 2x, and four or more stop at 2.25x. The curve the example extrapolates to,
`1.5^(n-1)`, reaches 50.6% spell failure at four prices and 75.9% at five, which is not a build,
it is a disconnected mouse. The cap keeps the worst case at 33.75% - punishing, survivable, and
still the most dangerous thing a blood mage can do to themselves.

"Amplified by" means two different arithmetics, and `SacrificeBudget` has a method for each,
because getting this wrong silently halves or doubles a price.

- **A magnitude** - a flat number, a chance, a count of ticks - is scaled outright:
  `amplify(m, clamp, n, broker)` is `min(clamp, m * amplification(n))`. A 15% failure chance at two
  prices is 22.5%.
- **A multiplier** - anything expressed as a factor on something else, such as Glass Bones taking
  1.20x damage - is scaled in its **distance from one**, not in itself:
  `amplifyMultiplier(x, clamp, n, broker)` is `1 + (x - 1) * amplification(n)`. Glass Bones at 1.20
  goes to 1.30 at two prices and 1.45 at four. Scaling the multiplier itself would make 1.20 into
  1.80, nearly doubling incoming damage from one extra price that has nothing to do with it.

A multiplier below one amplifies downward the same way, and its clamp is a floor rather than a
ceiling, which is why `amplifyMultiplier` clamps with `max` when the multiplier is below one.

Every amplified price also carries its own absolute clamp, listed in its row, so no combination of
tuning and amplification can push one past the number in the table.

Amplification counts **ritual prices only**. Permanent curses - the sin curses, the mana leak, the
corruption curse - are not part of the pact and do not feed it.

## The price of blood, in blood

### `magical:blood_price`

A new damage type, `src/main/resources/data/magical/damage_type/blood_price.json`:

```json
{ "message_id": "magical.blood_price", "exhaustion": 0.0, "scaling": "never" }
```

tagged into `bypasses_armor`, `bypasses_effects`, `bypasses_enchantments`, `bypasses_resistance`
and `bypasses_cooldown`. Not `bypasses_invulnerability`: creative and genuinely invulnerable
players are already handled by `MagicPrice.waived`, and bypassing it would let the price kill
someone the game says cannot be killed.

`BloodDamageTypes.price(ServerPlayer)` builds the source the way `ForgeDamageTypes.strike` does.

### The barrier early-out

Bypass tags stop vanilla mitigation. They know nothing about this mod's barrier, Mana Skin, sin
soak or passive reductions, all of which live in `MagicGameplayEvents.onIncomingDamage`. A blood
price that reached the barrier would be paid by the barrier, which is the whole thing the player
asked not to happen. So the handler returns early, immediately after the state is fetched and
before any of the reductions:

```java
if (event.getSource().is(BloodDamageTypes.BLOOD_PRICE)) {
    // The price of blood magic is paid in flesh. No barrier, no Mana Skin, no soak: the number
    // the caller asked for is the number that lands.
    return;
}
```

### Consequences, stated rather than discovered

`BloodService.bleed` currently hurts with `damageSources().magic()`, and its comment says a bespoke
type would "silently slip past magic resistance, Gluttony and wrath". That is now the intent, not
an accident:

- **Magic resistance no longer discounts the price.** A price you can resist is not a price.
- **Wrath no longer builds from it.** Wrath is a gauge of being hit by the world. Paying your own
  bill is not being hit.
- **Gluttony no longer feeds on it.** Same reason.
- **`PassiveHooks.isSpellDamage` reads false for it**, so nothing that keys off incoming spell
  damage fires on a self-inflicted price.

`WarPassives.RED_PAYMENT` keeps `damageSources().magic()`. It converts health into *mana*, is not a
blood cost, and changing it would be a balance edit nobody asked for.

### The exchange rate

`BloodService.COST_PER_HEALTH` goes from 8 to 25, so twenty health is five hundred blood and the
full bar buys five rituals' worth of blood if it could be spent that way, which it cannot.

Cheapening health means the six existing blood skills get cheaper to bleed for: Coagulate's
forty-blood shortfall was five health and is now 1.6. That is intended, and the true-damage change
pays for it. At the old rate a barrier mage paid nothing at all, because the price went into the
barrier; at the new rate the price is smaller but it always lands on flesh. A cheaper bill you
cannot dodge is a harder bill than an expensive one you can.

### `payFromVesselOnly`

```java
/**
 * Charges a cost the body is not allowed to cover, the mirror of {@link #payInHealthOnly}.
 *
 * <p>A ritual wants proof the caster has been taking blood off other people. Letting them open a
 * vein to make up the difference would turn "a full Vessel" into "two hearts", which is not the
 * same requirement and not the same fantasy.
 */
public static boolean payFromVesselOnly(ServerPlayer player, PlayerMagicState state, int cost)
```

Returns false and takes nothing when the Vessel is short, with `message.magical.vessel_not_full`.

`RITUAL_COST = 100` is a constant on `BloodSacrificeService`, deliberately not a `BloodPrices`
entry: `BloodPrices` is scaled by `costScale`, and a Thrift-tuned ritual costing sixty blood would
break the requirement the ritual is built on. `BloodPrices.base(blood_sacrifice)` stays zero and a
test pins it there so nothing ever bills the ritual twice.

## Temporary passives

### Storage

A new map on `PlayerMagicState`, beside the other timers:

```java
private final Map<ResourceLocation, Integer> ritualTicks = new LinkedHashMap<>();

public int  ritualRemaining(ResourceLocation id);
public void grantRitualPassive(ResourceLocation id, int ticks);   // unlocks, then takes the longer of old and new
public boolean tickRitualPassives();                              // true when a sync is due
public int  activeRitualCurseCount();
public void clearRitualPassives();
```

`passiveCounters` was the obvious home and is the wrong one: it is a general-purpose counter map
that six handlers already write to, and a countdown that silently removes a passive when it hits
zero would be a trap for all of them. A dedicated map makes the timer a first-class thing with its
own save key, its own test, and no chance of a stray `bumpPassiveCounter` expiring a boon.

Ritual passives are registered as ordinary passives (`curse = false`) and granted through
`unlockPassive`, so `hasPassive`, `isPassiveEnabled` and `ClassPassiveEffects.on` all work with no
special cases in the twenty-odd places that call them. Prices are *not* registered as curses even
though they are curses in fiction: the curse column carries a Dispel button, and a price you can
dispel is not a price. They are classified instead by two sets on `MagicPassiveContent`,
`ritualBoons()` and `ritualPrices()`.

### Ticking

`tickRitualPassives()` is called from `PlayerMagicState.tickServer` next to `anchorSigilTicks` and
`blackFlamesImbueTicks`, and feeds the same `changed` flag. It decrements every tick, calls
`removePassive` at zero, and asks for a sync on expiry and once a second otherwise, so the codex
countdown moves without a whole-state blob every tick.

The client needs no new plumbing at all: `PlayerMagicStatePayload` carries the entire `save()` tag,
`ritualTicks` is in it, and `ClientMagicState` already replaces its contents wholesale.

### The codex

`MagicPyramidScreen.passiveRows` emits two pinned groups before its existing walk:

1. `screen.magical.passive_group_ritual_boon` - **"Temporary Buff"**
2. `screen.magical.passive_group_ritual_price` - **"Temporary Curse"**

then walks the normal passives as it does today, skipping any ritual passive so nothing appears
twice. A ritual row uses the same card, with three differences: an hourglass stamp where the
checkbox goes, the countdown `m:ss` on the second line instead of Enabled/Disabled, and a thin
progress bar along the bottom edge showing the fraction left. Clicking one does nothing - the
toggle handler returns early for ritual passives, because a boon you can switch off is a boon you
took for free.

The tooltip gains a "Temporary - 0:47 left" line, and an amplified price under Hellbroker gains
"Hellbroker: x1.5".

## The catalogue

Sixteen boons and sixteen prices. Every row names the hook it is implemented through, and every
hook in that column already exists on `ClassPassiveHandler` - nothing here needs a new interface
method. `attr` means a transient attribute modifier re-applied on the slow tick through the
`applyOrClear` pattern `WildPassives` already uses.

### Boons

| # | Name | Pts | Effect | Hook |
| --- | --- | --- | --- | --- |
| 1 | **Crimson Edge** | 1 | Melee damage +20% | attr `ATTACK_DAMAGE` |
| 2 | **Long Reach** | 1 | Entity reach +1.5, block reach +1.0 | attr `ENTITY_INTERACTION_RANGE`, `BLOCK_INTERACTION_RANGE` |
| 3 | **Unfeeling** | 1 | Knockback resistance +0.6; statuses on you last 40% less | attr `KNOCKBACK_RESISTANCE` + `statusDurationScale` |
| 4 | **Sure Footing** | 1 | Step height +0.6, no fall damage under 6 blocks | attr `STEP_HEIGHT`, `SAFE_FALL_DISTANCE` |
| 5 | **Sanguine Might** | 2 | Spell damage +20% | `outgoingSpellDamage` |
| 6 | **Quickened Pulse** | 2 | Move speed +15%, attack speed +15% | attr |
| 7 | **Thinned Blood** | 2 | Mana costs x0.65 | `adjustCast.mana` |
| 8 | **Clotted Hide** | 2 | Incoming damage x0.85 | `incomingDamage` |
| 9 | **Vessel Siphon** | 2 | Every spell kill credits +12 blood | `onKill` |
| 10 | **Scarlet Tide** | 2 | Spell size x1.25 | `adjustCast.size` |
| 11 | **Blood Scent** | 2 | +18% spell damage to anything under 40% health | `outgoingSpellDamage` |
| 12 | **Second Heart** | 3 | Maximum barrier +40 | `bonusMaxBarrier` |
| 13 | **Haemophage** | 3 | Spell damage heals you for 10% of it, at most 4 HP per cast | `outgoingSpellDamage` |
| 14 | **Racing Heart** | 3 | Cooldowns x0.70 | `adjustCast.cooldown` |
| 15 | **Bloodborne Fury** | 3 | +8% spell damage per active ritual price | `outgoingSpellDamage` |
| 16 | **Ironblood** | 4 | The first killing blow is refused: you drop to 1 HP, the Vessel empties, this boon ends | `cheatDeath` |

Bloodborne Fury is the one boon that rewards a heavy pact, and the one that makes Hellbroker a
build rather than a discount. Four prices under Hellbroker is +32% spell damage bought with a
2.25x amplification on everything that hurts.

### Prices

The clamp column is the absolute ceiling after amplification.

| # | Name | Pts | Effect | Clamp | Hook |
| --- | --- | --- | --- | --- | --- |
| 1 | **Open Wound** | 1 | Healing you receive x0.5 | x0.15 | `adjustHeal` |
| 2 | **Dulled Senses** | 1 | Statuses on you last 60% longer | x2.5 | `statusDurationScale` |
| 3 | **Thin Skin** | 1 | Armour -6 | -16 | attr `ARMOR` |
| 4 | **Leaden Step** | 1 | Jump strength -40%, fall damage x2 | -70% / x3 | attr `JUMP_STRENGTH`, `FALL_DAMAGE_MULTIPLIER` |
| 5 | **Weeping Vessel** | 1 | Kills credit no blood at all | none (binary) | `onKill` |
| 6 | **Hemorrhage** | 2 | Every half second, 4 blood out of the Vessel; on an empty Vessel, 0.5 HP as blood price | 9 / 1.1 HP | `slowTick` |
| 7 | **Life Tax** | 2 | Every cast costs 1 HP as blood price | 2.25 HP | `afterCast` |
| 8 | **Brittle Barrier** | 2 | While the barrier holds, blows against it count for 1.35x | x1.8 | `incomingDamage` |
| 9 | **Slow Blood** | 2 | Move speed -20%, attack speed -20% | -45% | attr |
| 10 | **Glass Bones** | 2 | Incoming damage x1.20 | x1.45 | `incomingDamage` |
| 11 | **Mana Drought** | 2 | Mana costs x1.5 | x2.1 | `adjustCast.mana` |
| 12 | **Binding Chains** | 2 | Cooldowns x1.5 | x2.1 | `adjustCast.cooldown` |
| 13 | **The Unknown** | 2 | One price from the table below, rolled on sealing, its identity hidden until it lands | as rolled | (whichever is rolled) |
| 14 | **Echoing Misery** | 3 | 15% of the spell damage you deal comes back as blood price | 33% | `outgoingSpellDamage` |
| 15 | **Spell Fizzle** | 3 | 15% of casts fail after paying mana and cooldown | 33.75% | cast pipeline |
| 16 | **Blood Debt** | 4 | +25 Corruption, immediately and permanently | none (one-shot) | on sealing |

#### The Unknown

Two points - the middle of the range - for a price the player does not choose. Rolled once when the
pact is sealed, from the level's game time so it is reproducible in a test:

| Weight | Rolls | Worth |
| --- | --- | --- |
| 30% | Slow Blood | 2 pts - what you paid |
| 25% | Mana Drought | 2 pts - what you paid |
| 20% | Glass Bones | 2 pts - what you paid |
| 15% | Spell Fizzle | 3 pts - a point more than you paid |
| 10% | Blood Debt | 4 pts - twice what you paid, and it never leaves |

Seventy-five percent of the time it is a fair two-point price. A quarter of the time it is worse,
and one roll in ten is the corruption debt the player would never have chosen.

The rolled price is granted under its own id, so it appears in the codex as itself. There is no
"The Unknown" passive - the point of the gamble is that you find out what you agreed to by living
through it.

#### Blood Debt

The only entry with no timer. `state.addCorruption(25)` on sealing, and that is it: corruption is
removed by Purification and by nothing else, `MAX_CORRUPTION` is 100, and the corruption curse
lands at whatever threshold `DarkService` sets. Four points, the top of the range, because a price
that outlives the pact by several hours is not really a price for the pact.

Amplification does not touch it. It fires once, at sealing, before the count of active prices means
anything.

### What was cut, and why

The design pass produced thirty-six entries. These did not survive contact with the code:

- **Critical Fortune** (+40% crit chance) - `MagicSkillResolvedStats` has no crit field and spells
  do not crit. It would have been a new system, not a buff.
- **Temporal Edge** as written (cooldowns tick at 1.25x) - cooldowns are stored per skill and
  decremented by the state; a rate multiplier would need a second clock. Racing Heart does the same
  job through `adjustCast.cooldown`, which already exists.
- **Death Mark Prey**, **Hexed Touch** - both wanted a `DEATH_MARKED` / `HEXED` status.
  `MagicStatus` has eighteen values and neither is among them. Blood Scent does the first one's job
  by reading the target's health directly.
- **Exsanguinate Refund**, **Appetite Echo**, **Jackpot**, **Fortune Favours** - all of them wanted
  the damage a cast dealt inside `afterCast`, which takes a player, a state and a definition and no
  damage at all.
- **Greed Magnified** (kills grant 3x blood) and **Vessel Overflow** variants - `MAX_BLOOD_VESSEL`
  is 100 and the ritual needs all of it, so a boon that fills it faster is a boon that shortens its
  own cooldown. Vessel Siphon's flat +12 is the same idea without the loop.
- **Arcane Rebound** (casts fire backwards) - reversing an aim vector after `AimResolver` has run
  means re-resolving the aim inside the failure path of every handler. Spell Fizzle is the honest
  version of the same misery.
- **Vulnerability Bloom**, **Lingering Debt**, **Fractured Mind**, **Shattered Focus** - four
  separate stacking counters, all of them variations on Glass Bones and Spell Fizzle, none of them
  adding a decision the player makes.

## The cast, and the fizzle

### The skill

```java
BLOOD_SACRIFICE = register("blood_sacrifice", MagicSchool.BLOOD, MagicSkillType.BURST,
        -1, 0, 0.0F, 1.0F, 1.0F, 0, 1800, 1200, 0.0F, 0, 0x6E0B18);
```

Layer -1 with the rest of Blood, no mana, ninety-second cooldown, sixty-second base duration. Its
handler checks the Vessel, sends `OpenBloodSacrificePayload` and returns `CastResult.HANDLED`,
which syncs and does nothing else - so opening the screen and closing it again costs nothing. The
blood and the cooldown are both charged by `BloodSacrificeService.seal`, on confirm.

The visual profile is a ground-anchored circle in the blood material with the vessel emblem, a slow
spin and a `LIFT` release - the ritual's only spectacle, since everything else it does happens in a
menu.

### Spell Fizzle's insert point

In `MagicCastingService.castViaRegistry`, between the mana spend and the context:

```java
if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) { ... return; }
if (SacrificeCurses.fizzles(player, state, definition, seed)) {
    // Paid for, cooled down, and nothing happened. That is the whole curse.
    state.setSkillCooldown(definition.id(), stats.cooldownTicks());
    SpellFx.windup(player, definition, aim.point(), player.getLookAngle(), sneak);
    player.displayClientMessage(Component.translatable("message.magical.spell_fizzled"), true);
    state.sync(player);
    return;
}
```

Two deliberate holes in it. Self-managed and hold-gated handlers return before the mana spend and
so never fizzle - a charge that dies halfway through a hold is a bug report, not a curse. And Blood
Sacrifice itself is exempt, because a ritual that fails to open its own screen after taking a
hundred blood would be indistinguishable from a crash.

## The screen

`client/screen/sacrifice/BloodSacrificeScreen`, a plain `Screen` with no container menu, wearing
`ScreenChrome`'s 444x340 frame exactly as the Spell Creator does. `isPauseScreen()` is false so the
integrated server answers the confirm, and it implements `HudDebug.Captured` so one launch can
capture it.

```
+--------------------------------------------------------------------------+
| Blood Sacrifice        [Vessel 100/100]   [xp bar]              [ Back ]  |
|--------------------------------------------------------------------------|
| +------------------+ +------------------+ +----------------------------+ |
| | BOONS            | | PRICES           | | THE PACT                   | |
| | [x] Sanguine..  2| | [x] Glass Bones 2| |  Boons    5 / 7            | |
| | [ ] Quickened.. 2| | [ ] Slow Blood  2| |  Prices   4 / 5   short 1  | |
| | [x] Second He.. 3| | [x] Open Wound  1| |  --------------------------| |
| | [ ] Racing He.. 3| | [ ] The Unknown 2| |  Sanguine Might            | |
| | ...      scroll  | | ...      scroll  | |  Second Heart              | |
| |                  | |                  | |  --------------------------| |
| |                  | |                  | |  Glass Bones               | |
| |                  | |                  | |  Open Wound                | |
| |                  | |                  | |  --------------------------| |
| |                  | |                  | |  Hellbroker: +1 boon,      | |
| |                  | |                  | |  prices at x1.5            | |
| |                  | |                  | |  Boons 60s - Prices 90s    | |
| |                  | |                  | |    [ SEAL THE PACT ]       | |
| +------------------+ +------------------+ +----------------------------+ |
+--------------------------------------------------------------------------+
```

Three columns rather than two tabs: the whole decision is the relationship between the two lists
and the counters, and a tab that hides half of it would make the player click back and forth to do
arithmetic the screen should be doing for them.

- **Rows** are 22 tall: a tick box, the name, and a cost chip on the right in the entry's colour.
  Hovering shows the full description as a tooltip, through `font.split` and `renderTooltip` like
  every other screen here. Clicking toggles. A boon that would put the spend over budget is drawn
  dimmed and refuses the click.
- **The pact panel** carries the two counters, the two chosen lists, the Hellbroker line when the
  passive is owned, the two durations, and the seal button. "short 1" appears in blood red while
  the prices do not cover the boons; the seal button is enabled only when they do and at least one
  boon is chosen.
- **Sealing** sends the two id lists. Everything is re-validated server side - the client's lists
  are untrusted input, so `seal` re-reads the tuning, re-computes the budget, rejects unknown ids,
  duplicates, an overspend, a shortfall and an under-full Vessel, and only then charges.
- Paint order is the house order: fills, then `emblems.flush()`, then text, then the tooltip.

Geometry lives in `BloodSacrificeLayout` and is pinned by `BloodSacrificeLayoutTest` the way every
other screen here is: nothing overlaps, everything sits inside `ScreenChrome.body()`, and each hit
test answers to its own centre and to nothing in the gaps.

### Network

| Payload | Direction | Carries |
| --- | --- | --- |
| `OpenBloodSacrificePayload` | to client | `boonTicks`, `priceTicks` |
| `BloodSacrificeSealPayload` | to server | `List<ResourceLocation> boons`, `List<ResourceLocation> prices` |

The open payload carries only the two durations because everything else the screen needs - the
Vessel, the tuning, whether Hellbroker is owned - is already in the synced state. The durations are
the one thing that would otherwise mean duplicating `resolve` on the client.

## Hellbroker

A fourth forbidden blood passive, `hellbroker`, unlocked the way Bloodscent, Clotting and Vessel
Overflows are. Three effects, all in `SacrificeBudget`:

1. `boonBudget` gains a point.
2. `priceRequired` loses a point, so that point is genuinely free rather than needing a matching
   price.
3. Every amplified price is amplified by `amplification(activeRitualPrices)`, through `amplify`
   if it is a magnitude and `amplifyMultiplier` if it is a factor.

The name is the deal: the broker gives you a point on credit and charges compound interest on
everything you already owe. One price, and Hellbroker is a straight discount. Four, and every one
of them bites more than twice as hard - which is why Bloodborne Fury exists to make that a choice
rather than a mistake.

## Files

**New:**

- `magic/blood/BloodDamageTypes.java`, `src/main/resources/data/magical/damage_type/blood_price.json`,
  and five entries in `data/minecraft/tags/damage_type/`
- `magic/blood/SacrificeCatalogue.java` - the thirty-two entries, their point costs and their sets
- `magic/blood/SacrificeBudget.java` - the arithmetic above, pure and testable
- `magic/blood/BloodSacrificeService.java` - `open`, `seal`, `RITUAL_COST`
- `magic/skill/blood/BloodSacrificeSkill.java` - the skill module and its visual profile
- `magic/passive/SacrificePassives.java` - the `ClassPassiveHandler` claiming all thirty-three
- `magic/passive/SacrificeBoons.java`, `magic/passive/SacrificeCurses.java` - the effects
- `network/OpenBloodSacrificePayload.java`, `network/BloodSacrificeSealPayload.java`
- `client/screen/sacrifice/BloodSacrificeScreen.java`, `BloodSacrificeLayout.java`

**Modified:** `BloodService` (rate, `payFromVesselOnly`, `bleed`), `MagicGameplayEvents` (the
early-out), `MagicCastingService` (the fizzle), `PlayerMagicState` (`ritualTicks`, save, load,
copy, tick), `MagicContent` (the skill), `MagicPassiveContent` (thirty-three definitions and two
sets), `ClassPassiveEffects` (the new handler in `HANDLERS`), `MagicPyramidScreen` +
`CodexLayout` (the two pinned groups and the countdown row), `MagicalNetwork`,
`ClientPayloadHandlers`, `MagicalCommands` (a `/magical sacrifice` for captures), `en_us.json`,
`CLAUDE.md`.

**Tests:** `SacrificeBudgetTest`, `SacrificeCatalogueTest`, `RitualPassiveTimerTest`,
`BloodSacrificeServiceTest`, `BloodPriceTest`, `BloodSacrificeLayoutTest`, two payload round-trips,
a `BloodSacrificeGameTest`, plus updates to `BloodSchoolTest` (seven actives, four passives),
`CodexLayoutTest`, `NewContentIntegrationTest`, `HudLangKeysTest`, `MagicalTooltipAssetsTest` and
`VisualProfilesTest`.

## Risks

- **Thirty-three new passives is a third again as many as the codex has today**, and the passives
  column is already 69 rows for a fully built player. The two pinned groups mitigate it: ritual
  entries only exist while they are running, so the tab is unchanged between rituals.
- **The blood price early-out is a new branch in the hottest damage path in the mod.** It is a
  single tag check against a source that is only ever constructed by this school.
- **`COST_PER_HEALTH` is a live balance number** touching six shipped skills. The true-damage change
  is what pays for it, and `BloodCostTest` pins both halves so the pair moves together or not at all.
