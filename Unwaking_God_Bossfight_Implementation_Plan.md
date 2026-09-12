# The Unwaking God: Implementation Plan

## Combat contract

The god begins as a normal-sized, pure-white player biped in the actual Overworld. At 70% health it awakens and transports the locked roster of one to four players to Chronos End. This is open-world combat: no arena wall, radius leash, forced return to spawn, terrain clearing, or boundary elimination. The shrine remains only an entry and return point.

Sleeping combat retains moving cuts, pursuit, marked teleports, and its three combination scores. Default health remains 36,000 multiplied by `1 + 0.65 * (players - 1)` at roster lock. Guarded attacks take 50% damage. Three clean combination finishers earn a ten-second opening at 100% damage. Existing B prompts, opposite-attribute counters, Gluttony, shields, and loadouts remain the defense interface.

## Recurring domain assaults

In Awake and Final, complete two normal combinations, honoring any current ordinary guard opening, then dissolve the god into the dimension for a 1,200-tick / 60-second assault. The boss entity remains the damage owner but is invisible, untargetable, and invulnerable. The HUD identifies the assault and displays its remaining survival time.

Each assault contains a 600-tick sky passage and 300-tick vortex and clock passages. Passage boundaries follow this duration table in every order; no fixed division by 300 controls the timeline. Successive assaults rotate their order:

| Assault | Passage order |
|---|---|
| Heaven Unwritten | Sky cut, vortex, clock |
| The World Refuses | Vortex, clock, sky cut |
| No Next Moment | Clock, sky cut, vortex |

Survival guarantees a 400-tick / 20-second reward; no perfect-parry quota blocks progression. Clear all hazards, prompts, and time locks before returning the body approximately three blocks from a surviving participant. Start the reward only when that placement succeeds. If no safe nearby placement can be found for five seconds, abort through the existing safe-return flow.

During the reward the body stays still, casts nothing, and takes 100% incoming damage. Reset guard and the normal-combination count. The Overworld health floor remains 70%; the Chronos 40% threshold is a transition trigger, not a damage clamp. Crossing it during a reward continues accepting damage and defers the transition until all 400 reward ticks finish. Lethal damage immediately starts the ending.

At 40%, use the three-second Unbodying transition and enter Final with an immediate assault. This replaces the normal encounter's old Sky/Breath/Chime trial chain and short reforming reward. Legacy trial definitions remain only for existing diagnostics. Final retains the same durations and damage but changes geometry: tilted converging sun axes, vertical portal exits, alternating tighter beam-shower rings, and alternating elevated/lowered clock targets.

## Attack patterns and responses

All times below are passage-relative ticks at 20 TPS. Anchor each passage to each participant's position and forward direction on entry; announced trajectories never chase the camera. Personal hazards collide only with their recipient. A participant's parry cannot remove another participant's threat.

### Sky cut - The Wound Writes Back

Use the actual `chronoRift` shader with the same proportions and 35-degree roll as `/chronosfx skycut on`. Share these constants through `ChronosSkyCutGeometry`. At the passage anchor, the wound is 96 blocks ahead and about 59 blocks above, with surface half-width 277 and half-height 295 blocks. This preserves the command's enormous angular size while keeping projectile origins fixed in the wound. Fade all ambient monuments and stars completely away as it opens. Open over ticks 0-40. Announce five-lance volleys at 40, 90, 140, and 190, alternating Folded Lances and Returning Verdict. Lanes are 12 blocks apart; collision radius is 1.25 blocks. Give every volley 20 warning ticks.

- **Folded Lances:** travel from the wound into marked entry portals for 12 ticks, appear at the announced exits, then traverse 96-block paths over 28 ticks. Show both mouths and the outgoing paths. The portal jump has no swept collision; only visible motion damages. Contact ends four ticks before removal to allow bounded counter grace.
- **Returning Verdict:** travel along a committed path for 32 ticks, stop visibly for 12 ticks, then retrace it over 32 ticks. Trails are harmless. Each entire volley can damage each participant only once, including its return pass.
- **Response:** move between committed lanes, defend toward the actual incoming segment, or use the standard contextual counter. A parry removes one lance and preserves its siblings.

Lances deal raw `20 * max(32 HP, 25% of maximum health)` at the default damage setting. The existing four volleys finish before passage tick 300. Then add three distinct attacks, in order:

| Start | Attack | Behavior and response |
|---|---|---|
| 300 | Firmament Guillotine | Rotate the sky wound horizontally over twenty ticks. A 384-block blade, radius 4.5, travels from the wound through the announced target and 64 blocks beyond it. Warn for 32 ticks; sweep during local ticks 32-72. Dodge off its path, shield toward the nearest approaching blade section, or aim and counter. Clear at 384. |
| 390 | Sixfold Burial | Crossfade to white sky with dark attacks. Six 24-block-diameter void suns appear eighty blocks along the six cardinal axes around the target. After forty warning ticks, converge through the target and 24 blocks beyond it over 36 ticks. Final rotates these axes by 35 degrees. Escape diagonally between committed paths or counter one sun to make an opening. One shared volley contact prevents simultaneous six-hit damage. Clear at 474. |
| 490 | Null Horizon | Crossfade back to black sky and restore the upright wound. A hollow ring approaches along a committed 192-block path after forty warning ticks. Its radius is 48, radial half-width five, and axial half-depth four. Fly through the actual central opening, escape outside, shield, or counter the approaching ring section. Contact ends at 578 and the ring clears at 590. |

Keep the surrounding monuments absent throughout the sky passage. All new hazards are recipient-local and use shared swept collision/render geometry. No arena wall or movement pull is introduced.

### Vortex - Heaven's Beam Shower

Keep the actual `chronoVortex` shader overhead, forming over ticks 0-40. The vortex never applies velocity, reverses controls, or schedules world-cut sheets. Movement and rotation remain free throughout this passage.

Announce seven waves at 40, 72, 104, 136, 168, 200, and 232. Each wave locks seven beam paths: one toward the participant's predicted center (eight-tick lead, capped at twelve blocks), plus six around a fourteen-block ring. Rotate the ring thirty degrees per wave. Final alternates fourteen- and ten-block rings. Every path starts inside the overhead vortex and extends sixty-four blocks beyond its announced target, covering flight as well as ground movement.

- **Warning:** thin full-length paths and rings mark the actual 2.75-block collision radius for twenty-four ticks. Announced paths never retarget.
- **Contact:** full beams fire for eight ticks, followed by eight harmless fade ticks. No lingering particles, paired sheets, or pull effects. Lower the visible opacity when the camera is inside a beam so a hit does not obscure the next dodge. One wave can damage a participant only once, even if multiple beams overlap.
- **Response:** move out of the marked paths, block toward their overhead source for the existing 80% raw-damage reduction, or use the usual contextual parry. A successful counter removes that beam; other beams continue.
- **Damage:** raw `20 * max(48 HP, 35% of maximum health)`, with the existing apex defense rules. All beams expire by passage tick 272. At most fourteen beam records coexist per recipient, within the sixteen-record snapshot limit.

### Frozen clock - There Is No Next Second

Use the actual `chronoClock` shader beneath the battlefield. Form over ticks 0-40. Lock position during 40-120 and 160-240; strike at 60/84/108 and 180/204/228. Release movement between bursts and finish with one folded volley at 240. Clear other passage hazards before locking; no movement-only contact overlaps a lock.

Each clock strike reveals one fracture twelve blocks from the player's eyes twenty ticks before impact. Successive targets alternate left, right, and above, with at most 75 degrees between adjacent yaw targets. Final targets alternate pitch +/-35 degrees.

- **Parry:** aim within twelve degrees of the fracture and answer the eight-tick QTE. Use measured latency grace capped at four ticks, included in the visible countdown. Validate aim before spending mana, cooldowns, or Gluttony resources. Wrong aim leaves the strike alive; successful parries remove only that strike.
- **Block:** raise a shield facing the fracture to reduce raw damage by 80%. Clock strikes use `20 * max(48 HP, 35% of maximum health)` before normal defenses.
- **Movement:** pin translation, jumping, flight motion, knockback, and displacement skills. Preserve yaw, pitch, defensive actions, items, and loadouts. Reject movement casts before resource expenditure. Clear the lock on its deadline, death, departure, abort, or return.

## Damage and stricter counters

Use `unwakingDamageMultiplier` (default 20, range 1-100) once on every attack's existing base/maximum-health damage formula. At ordinary maximum health, sleeping cuts now start at 480 raw damage, lances at 640, clock and beam attacks at 960, and guillotine/ring attacks at 1,120. Health remains 36,000 solo. Preserve the ten-second ordinary full-damage opening and twenty-second assault reward.

Open contextual counter prompts up to eight ticks (0.4 seconds) before predicted contact, with up to four ticks of measured latency grace. Only clock fractures require aim, within twelve degrees. Ordinary attacks and the new sky finales use the original contextual B response, without extra aim or server key-edge anti-spam requirements. B remains available for loadouts between prompts.

Right-click shields remain a separate defense. Sample shield use and direction at contact, reduce raw damage by 80%, and prevent vanilla from cancelling the already-reduced hit a second time using the distant boss body's location. Damage still goes through the normal magic-barrier and enchantment pipeline. Explicitly test Protection IV netherite with a full barrier and a fresh survival player; creative test survival does not prove damage effectiveness.

## Implementation ownership and presentation

The server assault controller integrates with the existing combination controller, hazard map, swept collision, contact protection, and counter threat entities. Shared assault geometry supplies both projectile rendering and continuous collision, including discontinuous portal exits. Ten ticks of contact protection remain between damaging contacts. Every damaging god attack now carries apex metadata, including sleeping-phase cuts. Ultimate Protection can no longer cancel the personal cuts as lower-tier spells.

The snapshot carries assault order/start, recipient-local anchors, forward direction, and lock anchor/deadline. Obsolete vortex force fields are removed. Network protocol version is 9; server and client must use the matching build. Keep at most sixteen active hazard records per participant; the beam shower requires at most fourteen. Response-time aim validation is a default-allow counter interface hook, leaving ordinary spells unchanged.

Use black scenery with pale attacks for sky/clock passages and white scenery with dark silhouettes for vortex passages. Crossfade over forty ticks. Dim unrelated scenery to 15%, fading it to zero during the open sky-cut passage; render only one dominant environmental shader. Reuse existing shader assets; standalone `/chronosfx` remains a visual preview and never damages or immobilizes players. Reduced-effects mode preserves collision, paths, targets, and deadlines. Never force camera rotation or add full-screen shake. Give assault HUD instructions compact contrasting backgrounds so both monochrome palettes remain readable over the sky shaders.

`/unwaking debug assault heaven|world|moment` starts a selected assault inside an existing debug domain encounter. `/unwaking status` includes rewards, locks, aim failures, recovery time, contacts, blocks, and parries. Keep existing reduced-effects and hitbox diagnostics, durable returns, moving chunk tickets, and normal victory rewards.

## Verification and delivery

Run `.\gradlew.bat test`, `scripts/test-unwaking-domain.ps1`, and `scripts/test-unwaking-client.ps1`. Tests cover portal discontinuities, moving-target collision, beam warning/contact boundaries, swept whole-body beam collision, shower spacing and snapshot limits, command sky-cut proportions, complete scenery suppression, exact clock deadlines, aim tolerance, and snapshot round trips. The real-dimension fixture exercises two participants, distant pursuit, all three assaults, resource validation, full reward damage through 40%, lethal completion, advancement, return records, and ticket cleanup.

The live client probe checks standard B/loadout priority, free rotation during a movement lock, loadout switching between clock prompts, and server acceptance of the aimed clock counter. Capture twelve views: the original five attacks, sky wound, active vortex beams, frozen dial, aimed clock strike, guillotine, converging suns, and hollow ring. Verify that a stationary player is not displaced by the vortex, that the shower has no sheet, and that the open sky cut suppresses ambient shapes. Probe code is compiled only into its isolated development run and must not appear in the distributable.

Finally run `.\gradlew.bat build`, verify the current distributable, archive it with build metadata under the configured Google Drive timestamp folder, verify size and SHA-256, and send the completion notification. Human survival difficulty and endgame clear times remain calibration work; creative fixtures verify mechanics, not difficulty.

### Previous implementation verification: 2026-09-09 (before beam-shower revision)

- Final wrapper build passed: `build/unwaking-assault-build.log`; 556 JUnit tests, zero failures/errors/skips.
- `build/unwaking-assault-integration.log`: all four required GameTests passed; two participants completed three assaults, six shield blocks, thirty counters, and verified damage through the full reward below 40%.
- `build/unwaking-assault-client.log`: live client passed B priority, server-confirmed loadout changes during a lock, free rotation, aimed counters, vortex displacement, and all nine captures.
- Screenshots inspected under `build/unwaking-client-20260909-170206/screenshots/`; a final small-window HUD spacing adjustment separates the direction cue and counter timer from text.
- The final distributable includes the assault controller, renderer, and white texture; test-only client probe classes are absent.
- Survival difficulty and endgame clear-time calibration remain manual work.

### Beam-shower revision verification: 2026-09-09

- `build/unwaking-beam-build.log`: final wrapper build succeeded; 559 JUnit tests with zero failures, errors, or skips.
- `build/unwaking-beam-integration.log`: all four required GameTests passed, including two recipients, real beam scheduling without sheets or movement locks, three assaults, six shield blocks, fifty-eight counters, complete reward windows, and return cleanup.
- `build/unwaking-beam-client-final.log`: all nine live views passed with stationary vortex movement, actual active beams, no vortex sheets, complete sky-cut scenery suppression, contextual B/loadouts, and clock rotation/counters checked.
- Final screenshots inspected: `build/unwaking-client-20260909-190204/screenshots/06-sky-wound.png` and `07-vortex.png`. Assault text has contrasting backgrounds; a beam containing the camera no longer obscures the battlefield.
- Protocol 6 distributable verified; client probe classes excluded. Google Drive archive size and SHA-256 matched the current build.

### Sky-cut escalation verification: 2026-09-11

- `build`: all 710 JUnit tests passed, including new swept blade/sun/ring geometry, passage durations, twentyfold damage, anti-spam input, and expanded snapshot round trips.
- `build/unwaking-hardening-domain-final.log`: all five required GameTests passed. Two participants completed three assaults, six shield blocks, ninety counters, the full reward below 40%, and return cleanup. Fixture players now stand inside chunks instead of on edges, preventing asynchronous chunk activation from delaying the boss's vanilla hurt cooldown during accelerated tests.
- The real damage pipeline test used full Protection IV netherite and a full magic barrier: a 960-raw clock hit removed 324 health unblocked and 47.52 health after encounter shield reduction. The fixture has 1,000 maximum health only to keep it alive; the attack amount uses the ordinary 20-health baseline.
- `build/unwaking-hardening-client.log`: the live probe completed all twelve views and confirmed a server-accepted aimed clock counter, free rotation while frozen, loadout switching between prompts, and no vortex displacement. The initial movement assertion did not recur on the diagnostic run.
- New attack screenshots inspected in `build/unwaking-client-20260911-160605/screenshots/`: `10-firmament-guillotine.png`, `11-sixfold-burial.png`, and `12-null-horizon.png`. Equivalent first-run captures were also inspected.
- Protocol 8 release JAR verified with new combat classes and without client-probe classes. Human survival balance remains a manual playtest.
- Health remains 36,000 solo. Tower progression and survival clear-time calibration are separate follow-up work.

### Parry rollback: 2026-09-11

Restored the pre-escalation eight-tick prompt, four-tick latency grace, and twelve-degree clock aim. Removed ordinary-attack aim checks and the key-edge anti-spam gate from the counter path. New attacks and twentyfold damage remain.
