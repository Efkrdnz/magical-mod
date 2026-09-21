# The Authority of Mana — Material and Passive verses

> Fills the two slots §9 of `2026-09-19-authority-of-mana-incantation-design.md` left empty
> (deviation 9: "Dropped for now: Material verses, Passive verses"). Nothing in the evaluator
> changes. Both types were in `VerseType` from the first day with their three rules ported - a
> Material marks the press as having produced a body, a Passive is what an Impose scans over - and
> every verse here is added the way deviation 9 promised, as a verse and never as an evaluator
> change. Noita's cards are the shape; the numbers are first-pass tuning.

## 1. Matter: what a material verse owns

> **A material verse puts a block in the world, and the block comes back.**

A wake touches bodies and never a block (`VerseBodyEntity.wake`: fire sets alight, water puts out,
frost slows). A material verse is the other half of that sentence: it touches the world and never
a body. The two families do not overlap, so a Fire Wake behind a Spray of Water is not a
contradiction, it is a hose that puts out what it sets alight.

Rules:

1. **Conjured, never permanent.** Every block a material verse lays goes through
   `ConjuredTerrainService` (the ledger every other temporary edit in the mod uses: a crash or an
   unload restores it on the next load) and is handed to `MatterKeeper` with the matter's lifetime.
   When the clock runs out the keeper restores every position still holding what was laid there,
   or nothing, and **yields any position a player has since built over**
   (`ConjuredTerrainService.restoreUnlessBuiltOver`): a sea you bridged with cobblestone leaves
   your cobblestone. A player who mines a conjured block gets its drop; that is the price of
   honest blocks and it is small.
2. **Four shapes.** A body's prototype names a `Matter` and a `MatterShape`, and the entity does
   the rest without a new behaviour or a new evaluator rule:
   - **SPRAY** - a flying body that lays one block on the floor under its line every
     `VerseBodyEntity.SPRAY_INTERVAL` (2) ticks: a stream on the ground, a line of fire.
   - **FLOOD** - a standing body (placed a block ahead of the hand and dropped to the floor, or at
     the release point in a payload) that fills the air over the floor within its radius: a pool,
     a burning field. Only air is filled, and only where the block below is solid.
   - **TOUCH** - a standing body that converts what is already there within its radius: never air,
     never the unbreakable (bedrock, barriers, the frames), never a block with a block entity
     (a chest, a spawner), never a light. Water becomes stone under a Touch of Stone; the ground
     becomes water under a Touch of Water. A Touch converts at most `VerseMatter.TOUCH_CAP` (160)
     blocks, nearest first.
   - **MOUND** - a flying body that heaps earth where it ends: five blocks on the floor and one on
     top, into air only, never into a body's space.
3. **Fire is real fire.** A Sea of Flame is `minecraft:fire`, placed only where fire can stand,
   and it spreads to what burns exactly as fire does. The keeper takes back the fire it placed and
   nothing it lit; a sea of flame beside a wooden house is the caster's problem, which is Noita's
   first lesson, kept (deviation 10 says the same of explosions).
4. **Water is real water.** A source block flows, so a Sea of Water of radius 3 wets a wider circle
   than it fills; when the sources go the flow drains on its own.
5. **Lava is real lava,** and a Sea of Lava at the caster's feet is the accident it is in Noita.
   Three uses, forty-five mana, the beat of a Void Pit.
6. **A material body carries no damage of its own** (Spray of Flame burns what it strikes, because
   the burn is its prototype's, as an Ember's is). The reading counts nothing for it and says the
   verse's name in the cast line, which is what it does.

| Matter | School (colour) | Block | Lifetime |
|---|---|---|---|
| WATER | WATER | water source | 240 t |
| LAVA | FIRE | lava source | 200 t |
| FLAME | FIRE | fire (soul fire on soul soil) | 240 t |
| STONE | PRIMORDIAL | stone | 400 t |
| GLASS | ARCANE | glass | 400 t |
| ICE | WATER | ice | 300 t |
| EARTH | PRIMORDIAL | dirt | 400 t |

### The catalogue (`MATERIAL`; `MaterialVerses`)

| id | Name | Noita | Mana | Uses | Beat | Prototype and deltas |
|---|---|---|---|---|---|---|
| `spray_water` | Spray of Water | Water | 6 | ∞ | +2 | `spray_water`: WATER SPRAY, 0.9 b/t, 30 t, r 0.3; spread +3, gravity +0.02 |
| `spray_flame` | Spray of Flame | Flamethrower | 9 | 20 | +2 | `spray_flame`: FLAME SPRAY, 0.9 b/t, 30 t, r 0.3, BURN on hit; spread +3, gravity +0.02, recoil +10 |
| `sea_water` | Sea of Water | Sea of Water | 12 | ∞ | +5 | `sea_water`: WATER FLOOD, r 3.0, stands 20 t |
| `sea_flame` | Sea of Flame | Sea of Flames | 16 | 15 | +5 | `sea_flame`: FLAME FLOOD, r 3.0 |
| `sea_lava` | Sea of Lava | Sea of Lava | 45 | 3 | +27 | `sea_lava`: LAVA FLOOD, r 2.5; screenshake |
| `touch_stone` | Touch of Stone | Touch of Gold | 14 | ∞ | +7 | `touch_stone`: STONE TOUCH, r 3.0 |
| `touch_glass` | Touch of Glass | Touch of Grass | 8 | ∞ | +5 | `touch_glass`: GLASS TOUCH, r 3.0 |
| `touch_water` | Touch of Water | Touch of Water | 14 | 10 | +7 | `touch_water`: WATER TOUCH, r 2.5 |
| `touch_ice` | Touch of Ice | (freezing touch) | 12 | ∞ | +7 | `touch_ice`: ICE TOUCH, r 3.0 |
| `clod` | Clod | Chunk of Soil | 5 | ∞ | +3 | `clod`: EARTH MOUND, 0.8 b/t, 40 t, r 0.35; gravity +0.03 |

A Sea and a Touch stand for 20 ticks with one pulse, so the ripple that marks where the pool was
laid fades over a second; the matter is laid on their first tick, not on the pulse, and a Fuse or
Epitaph on one releases where it stood as on any static. Looks are borrowed: a spray is an orb, a
clod a shard, a sea a ring, a touch a burst, each in its matter's colour.

## 2. Passives: while this verse is written

> **A passive verse does nothing when read and everything while written.**

Rules:

1. **Transparent to composition.** Its action draws the next verse (`draw 1`), it costs no mana and
   has no uses, an Impose walks over it, and it neither produces a body nor spends a charge. A tape
   of `Taper, Needle` is a tape of `Needle`. That is Noita's passive exactly, and it is what lets
   a passive sit anywhere in an incantation without changing what the incantation does.
2. **Written is on.** `PassiveVerses.written(grimoire)` is the set of passive verses in any of the
   four incantations, and `VersePassives` runs each of them every tick the wielder is alive. Written
   in two slots is written once. Struck from the book, it stops on the next tick; the Authority
   leaving clears the Grimoire and so clears them all.
3. **Paid per act, not per press.** A halo drinks mana for every projectile it turns and a familiar
   for every needle it throws, from the wielder's pool, and does nothing on an empty pool; the two
   tapers are free. There is no upkeep, because a wielder who wrote nothing else should be able
   to carry a light.

| id | Name | Noita | Effect while written |
|---|---|---|---|
| `taper` | Taper | Torch | a light of 13 at the wielder's eyes, carried as they move (a `minecraft:light` block in the air of the eye position, moved every 5 ticks through the conjured ledger, never placed into anything but air) |
| `storm_taper` | Storm Taper | Electric Torch | the Taper's light, and every 20 ticks a SHOCK on every hostile within 2.5 blocks |
| `halo` | Halo | Energy Shield | every tick, every arrow, bolt, fireball or other caster's verse within 2.5 blocks and closing is turned: a vanilla projectile is deflected back the way it came and becomes the wielder's (`Projectile.deflect`, REVERSE), a verse body is broken; 3 mana each |
| `half_halo` | Half Halo | Energy Shield Sector | the same, within 4.5 blocks but only within 55 degrees of where the wielder looks; 2 mana each |
| `familiar` | Familiar | Tiny Ghost | every 30 ticks a needle from beside the wielder's shoulder at the nearest hostile in line of sight within 12 blocks; 1 mana each; the needle is a verse body under the Grimoire skill, so wards and counters see it as any recite |

## 3. The rules, each with the test that pins it

1. Every material prototype names a matter and a shape, and no other prototype names either;
   a spray and a clod fly, a sea and a touch stand. *`MatterTest`*
2. A material verse enacts the beat it declares and draws nothing; a passive verse draws exactly
   one, adds no beat and no rest. *`VerseContentTest.declaredNumbersAreEnacted`*, which no longer
   skips the two types.
3. `written` reads all four slots, counts a verse once, and ignores everything that is not a
   passive; every passive is free, unlimited and draws one; a half halo's sector is a function of
   the look and the offset alone. *`PassiveVersesTest`*
4. A sea lays water on the floor within its radius and only there, the keeper knows it, and the
   keeper takes it back. *`VerseBodyGameTests.aSeaLaysWaterOnTheFloorAndTheKeeperTakesItBack`*
5. A touch turns a block of sand on the floor to stone and leaves the barrier shell alone; the
   keeper gives the sand back. *`aTouchTurnsSandToStoneAndGivesItBack`*
6. A spray leaves fire on the floor under its line. *`aSprayLeavesFireUnderItsLine`*
7. A clod heaps dirt where it lands. *`aClodMoundsWhereItLands`*
8. A written taper lights the eye block and a struck one darkens it; a halo turns an arrow flying
   at the wielder and charges for it; a half halo turns the arrow ahead and not the one behind; a
   familiar throws a needle at a hostile and charges for it. *`VersePassiveGameTests`*
9. Every new verse wears a symbol of its own and has a name and a description.
   *`VerseSymbolsTest`*, *`VerseLangKeysTest`*, unchanged.
10. The category row still carries its words at 640 wide with all eight types in it.
    *`GrimoireLayoutTest`*

## 4. Runtime binding

- `magic/incantation/Matter`, `MatterShape` (pure), `VersePrototype` gains `matter` and `shape`
  (`NONE` and null on everything that was there before), `VersePrototypes` gains the ten bodies,
  `MaterialVerses` and `PassiveVerses` register after `ControlVerses`, so nothing already in the
  grid moves.
- `entity/verse/VerseMatter` does the world work (the four shapes, the block per matter, the
  guards) and `entity/verse/MatterKeeper` holds the clocks, ticked from
  `MagicGameplayEvents.onServerTick` like the Pile. `ConjuredTerrainService.restoreUnlessBuiltOver`
  is the one addition to the ledger.
- `VerseBodyEntity`: a standing body lays on its first tick, a flying spray every
  `SPRAY_INTERVAL`, a mound at its end. Three hooks, no new synced data: the prototype is synced
  already and the matter is the prototype's.
- `ClassPassiveHandler.tick` is a new per-tick hook (the slow tick is every ten, and an arrow
  crosses a halo in one), called once from `MagicGameplayEvents.onPlayerTick`;
  `VersePassives` is the handler, registered in `ClassPassiveEffects.HANDLERS`, and restores a
  taper's light on `forget`.
- `VerseSymbols`: eight glyphs (SPRAY, SEA, HAND, CLOD, CANDLE, HALO, HALF_HALO, GHOST) and three
  badges (MOLTEN, BRICK, PANE); fifteen rows.
- Lang: `verse.magical.<path>` and `.desc` for the fifteen.

## 5. Captures

The stage is built, not trusted: the dev world keeps every earlier run, so a launch fills its own
floor (`forceload add` and twenty ticks first; `gamerule commandModificationBlockLimit` lifted so
one `fill` clears the air above; the floor laid as sandstone first, because a `fill` that changes
nothing is a failure and a failure prints over `sendCommandFeedback`). Hiding the chat in
`run/options.txt` is no help: with it hidden the auto-commands are never sent.

Materials, first person at noon, from a two-block pillar (a sea at the feet floods the caster and
a touch from the ground entombs them): a Clod aimed at the sand and its mound, a Spray of Flame
fired level and then seen broadside from a second pillar (end-on, the fires hide behind the nearest
one), a Sea of Water round the golem, a Touch of Stone over it:

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doDaylightCycle false;gamerule commandModificationBlockLimit 1000000;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical unlockall;magical authority set authority_of_mana;magical incantation know all;magical incantation set 1 1 clod;magical incantation set 2 1 spray_flame;magical incantation set 3 1 sea_water;magical incantation set 4 1 touch_stone;time set noon;weather clear;forceload add 70 70 130 130;tp @s 100 200 100 0 0;76:fill 70 117 70 130 118 130 sandstone;78:fill 70 117 70 130 117 130 stone;80:fill 70 118 70 130 118 130 sand;82:fill 100 119 98 100 121 98 smooth_stone;84:fill 70 119 70 130 140 130 air;86:fill 100 119 98 100 120 98 smooth_stone;87:fill 108 119 90 108 121 90 smooth_stone;90:tp @s 100.5 121 98.5 140 25;92:summon iron_golem 100.5 119 101.5 {NoAI:1b};351:magical-debug recite 1;425:tp @s 100.5 121 98.5 180 0;431:magical-debug recite 2;445:tp @s 108.5 122 90.5 90 30;505:tp @s 100.5 121 98.5 0 25;511:magical-debug recite 3;591:magical-debug recite 4" -PautoScreenshot=357,367,381,437,441,451,461,517,531,597,611 -PautoExit
```

Passives, first person at midnight with `gamma:0.0` in `run/options.txt` (at the dev gamma the
taper's light is invisible on a beach), a golem ahead because a familiar wants a hostile and a husk
is dead by the seventh needle: a Taper written and the sand lit round the wielder, a Halo written and an arrow summoned flying in, turned
at the ring with the flare, a Familiar written and its needle on its way to the golem:

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;gamerule doMobSpawning false;gamerule doMobLoot false;gamerule doDaylightCycle false;gamerule commandModificationBlockLimit 1000000;kill @e[type=!player];magical reset;magical hud race human;magical class unlock mystic;magical unlockall;magical authority set authority_of_mana;magical incantation know all;time set midnight;weather clear;forceload add 70 70 130 130;tp @s 100 200 100 0 0;76:fill 70 117 70 130 118 130 sandstone;78:fill 70 117 70 130 117 130 stone;80:fill 70 118 70 130 118 130 sand;82:fill 100 119 98 100 121 98 smooth_stone;84:fill 70 119 70 130 140 130 air;90:tp @s 100.5 119 100.5 0 0;92:summon iron_golem 100.5 119 107.5 {NoAI:1b};351:magical incantation set 1 1 taper;561:magical incantation set 2 1 halo;771:summon arrow 102 120.6 104.5 {Motion:[-0.4,0.0,-1.0],NoGravity:1b};791:magical incantation set 3 1 familiar" -PautoScreenshot=345,557,772,774,777,997,1001,1005,1009,1013,1017,1021,1025 -PautoExit
```
