# The Harvest — blood that comes to you

Approved 2026-09-13. Request: *"make the blood magic passives cooler like sucking blood from nearby
dead monsters as if the blood flying and entering the player with its particle form"* — the
"particle form" being the voxel blood Blood Manipulation forms with, not vanilla particles.

## Today

A blood mage's kill leaves a *mote* at the corpse (`BloodPassives.MOTES`, a static map), drawn
with vanilla `DAMAGE_INDICATOR` particles; the player walks within 1.6 blocks to drink it
(+12 Vessel, doubled by Bloodscent on revealed prey). Vein Walk teleports along the motes.

## Decisions (user)

- Every blood mage gets it; the three passives shape it. Not a fourth passive.
- The Vessel fills **as the blood arrives**, not at the kill.
- Out of range, the blood **pools** at the corpse and lifts when the player comes near.

## Rule (server) — `BloodHarvestEntity` replaces the mote

One entity per corpse, carrying `yield` (12 per drop; 2 drops on Bloodscent-revealed prey).
Not saved to disk. The entity *is* the mote: Vein Walk reads `furthestPool`/`consumePool` on it.

| phase | what | leaves when |
|---|---|---|
| POOLED | waits at the corpse, idle wobble | 300 ticks pass (dries) — or the owner is within pull range: 12 blocks, 20 with Bloodscent → lifts |
| STREAMING | flies to the owner's chest, flight `clamp(10 + 1.2·d, 12, 40)` fixed at lift | flight elapses → `addBloodVessel(yield)`, sync, drink sound |
| OVERFLOW | only with Vessel Overflows on and the Vessel now past 4/5: cubes burst outward from the chest | 10 ticks |

Owner gone while streaming → discarded. At most 32 pools per player (oldest discarded).

## Look (client) — `BloodHarvestRenderer` + `BloodHarvestMotion` + `VoxelStyle.HARVEST`

Same `VoxelEmitter`, `shardBody` render type, `FxKinds.Body.BLOOD`, `0xF23B47`. Four cubes per
Vessel point (48 / 96), claimed from `FxBudget.claimVoxels`. Motion is pure and allocation-free:

- pooled: cubes well up over `materialise` ticks into a disc of radius `burstRadius`, then wobble;
  they dry out (shrink, tips first) over the last `dissolve` ticks of the pool's life;
- streaming: each cube launches from its own pool spot after `hash·jitter` ticks along a lifted
  bezier to the owner's interpolated chest, shrinking over its last `dissolve` ticks so it *enters*;
  the wobble fades with progress, so there is no seam at the lift tick;
- overflow: the same bezier reversed, chest outward, fading.

Invariant a test pins: every cube's `delay + cubeFlight ≤ flight`, so the server's payout never
precedes the last cube visually.

## Debug

`/magical blood harvest [amount]` spawns a pooled harvest six blocks ahead, for `-PautoCommands`.

## Out of scope

Clotting's numbers, the harvest-per-damage economy, wounded *living* enemies.
