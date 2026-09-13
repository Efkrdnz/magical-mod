# Space rule flash design

Approved 2026-09-13. The implementation plan lives with the session; this is the design it executes.

## Design

### What the player sees

1. Release the skill key on the wheels -> server accepts -> one small packet to that client.
2. A dark plate (HUD chrome, kind-tinted rim) pops in above the crosshair with the formula at 2x
   text size: `F = m·g`. The symbol `g` is drawn in the change kind's colour; the rest is white.
3. Behind the symbol one `RULE_MARK` shader quad plays the kind's mark: rays climb, a strike draws,
   brackets close, the ring bursts, an arrow sweeps in, etc. The symbol itself moves by kind
   (lifts, sinks, turns over, overshoots, fades to a "0", drains back to white).
4. One caption line under the plate, muted, unscaled: `Gravity · Increase · Except User`.
5. A faint full-screen wash from the existing `fp_overlay` shader (bloom rays, vignette, glitch,
   prism ring, hex pulse, shock ring, tunnel, flash), 6-14 ticks, alpha <= 0.35.
6. Two sounds: a shared "stamp" at tick 0 and the kind's cue 3 ticks later.
7. After a hold, plate and text fade and the mark erodes (noise dissolve like `ANNOUNCE`). Total
   48 ticks (32 with `reducedMotion`). Nothing is drawn or allocated when no flash is live.

### Formulas (one per category, the bracketed symbol is the one that changes)

| Category | Formula | Symbol |
|---|---|---|
| GRAVITY | `F = m·[g]` | g |
| VELOCITY | `[v] = Δx/Δt` | v |
| ACCELERATION | `[a] = Δv/Δt` | a |
| AIR_RESISTANCE | `F = ½[ρ]v²` | ρ |
| PRESSURE | `[P] = F/A` | P |
| MASS | `p = [m]·v` | m |
| TIME_FLOW | `dτ/dt = [γ]` | γ |
| VECTOR_FIELD | `F = q·[E]` | E |
| ENTROPY | `[S] = k·ln W` | S |
| FRICTION | `F = [μ]·N` | μ |
| BOUNDARY | `r ≤ [R]` | R |
| COLLISION | `[J] = Δp` | J |

Glyph allowlist (verified in the 1.21.4 default font's bitmap sheets, so every glyph is an 8px
glyph with the normal 1px shadow): ASCII, `Δ ρ τ γ μ · ½ ² ≤`. `∇`, `′`, `₀`, arrows and `↺` are
**not** allowed in formula text (unifont fallback: half shadow, wider advance). Arrows and marks
are drawn by the shader instead.

### Change kinds (8), and every operation's kind

| Kind | Operations | Symbol motion (CPU, pose) | `RULE_MARK` mark (shader) | Wash (`FxKinds.Overlay`, strength, ticks, alpha) | Cue |
|---|---|---|---|---|---|
| RAISE | INCREASE_GRAVITY, ACCELERATE, DENSE_AIR, CRUSH_PRESSURE, WEIGH_DOWN, HASTEN_TIME, DESTABILIZE_ENTROPY, STICKY, REPEL_BOUNDARY, INTENSIFY_COLLISION (10) | lifts 3px | five rays climb through the glyph box, up-arrow above | BLOOM_RAYS 40, 12, 0.22 | two rising notes |
| LOWER | DECREASE_GRAVITY, DECELERATE, THIN_AIR, EXPAND_PRESSURE, LIGHTEN_MASS, SLOW_TIME, STABILIZE_ENTROPY, SLIPPERY, ATTRACT_BOUNDARY (9) | sinks 3px | RAISE mirrored in y, down-arrow below | VIGNETTE 63, 12, 0.28 | two falling notes |
| ZERO | REMOVE_GRAVITY, STOP, REMOVE_ACCELERATION, VACUUM, STASIS_TIME, DISABLE_COLLISION (6) | alpha 1 -> 0.15 while a `0` label fades in on the same spot | strike draws left to right across the glyph, then ember speckles, small ∅ badge | STATIC_GLITCH 30, 8, 0.18 | click + power-down sweep |
| FLIP | REVERSE_GRAVITY, REVERSE_ACCELERATION, BURST_PRESSURE, WRAP_BOUNDARY, RICOCHET_COLLISION (5) | x-scale |cos(t·π)|: squashes to a hairline and grows back, landing upright (the text render type culls a mirrored glyph) | mirror-plane hairline at x=0 pulsing, ⇄ double arrow below | PRISM_RING 40, 12, 0.25 | reversed swell |
| LOCK | CONTROL_GRAVITY, UNIFORM_MOTION, DRAG_LOCK, NORMALIZE_MASS, NORMALIZE_TIME, NORMALIZE_FRICTION, SEAL_BOUNDARY, SELECTIVE_COLLISION (8) | none | `[` `]` slide in from the quad edges to hug the glyph, faint hex lattice inside once landed | HEX_PULSE 35, 12, 0.20 | metallic latch |
| SURGE | IMPLODE_PRESSURE, ANCHOR_MASS, CHAOTIC_MOTION (3) | scale 1 -> 1.8 -> 1.1 | shock ring expands from the glyph box, eight rays, residual pulse | SHOCK_RING 63, 14, 0.35 + shake 6 ticks 0.6 + fovKick 4 | sub boom |
| AIM | PULL_NORTH (0), PULL_SOUTH (1), ORBIT (2), CONVERGE (3) — the number is the `variant` | none | arrow sweeps in (rotates from +90° while fading in): up / down / ¾-arc with head / four inward arrows | TUNNEL 20, 10, 0.15 | sonar ping |
| RESTORE | all 12 `CLEAR_*` (12) | colour drains tint -> white | ring expands outward and fades, small ↺ arc-with-head badge | FLASH (white) 20, 6, 0.10 | clean triad |

Counts sum to 57; a test pins that every op has a kind and every kind has at least one op.

Kind tints (literals belong in `HudPalette`): RAISE `0xFFC76B`, LOWER `0x5EC8FF`, ZERO `0xFF5D6C`,
FLIP `0xC77DFF`, LOCK `0x9FB6D9`, SURGE `0xFF8A5B`, AIM `0x7CFFB2`, RESTORE = the Authority of
Space colour (`0x88DFFF`, from `AuthorityContent`).

### Timeline (fractions of the lifetime, mirrored Java <-> GLSL and pinned by test)

`POP_END = 0.10`, `MARK_END = 0.28`, `OUT_START = 0.72`; lifetime 48 ticks, 32 under
`reducedMotion`. Sub-phases: `pop = clamp(phase/POP_END)`, `mark = clamp((phase-POP_END)/(MARK_END-POP_END))`,
`out = clamp((phase-OUT_START)/(1-OUT_START))`. Plate scale `0.72 + 0.28·easeOutBack(pop)`;
alpha `pop·(1-out)`; the symbol motion runs on `mark`; the shader dissolves on `out` with the
same noise-threshold trick as `ANNOUNCE`. Under `reducedMotion`: no symbol motion, marks appear
static (`MODE_ALT`), no wash, sounds still play.

### Replace policy

A new packet while a flash is live replaces it (`begin` overwrites the current shot; the new pop
covers the old one). The 8-tick cooldown makes queueing pointless.


## Architecture (approach A)

Data flow: `SpaceManipulationOverlay.finish()` -> `ApplySpaceRulePayload` (unchanged) ->
`SpaceAuthorityService.applyRule` succeeds -> **new** `SpaceRuleAppliedPayload(category, operation, target)`
to that player -> `ClientPayloadHandlers.handle` -> `RuleFlash.begin(...)` (shapes labels once,
plays sounds, pushes the wash into `FirstPersonEffects`) -> `HudState.tick` ages it -> the **new**
`magical:rule_flash` GUI layer draws it from `RuleFlashRenderer`, a pure function of the shot and
the clock.

### New files

| File | Responsibility |
|---|---|
| `magic/SpaceRuleChange.java` | `enum {RAISE, LOWER, ZERO, FLIP, LOCK, SURGE, AIM, RESTORE}`, `id()` = ordinal. No client imports. |
| `magic/SpaceRuleNotation.java` | `record Formula(String before, String symbol, String after)`; `formula(SpaceRuleCategory)` from the table above (strings written `"F = m·[g]"`, split once at class init); `change(SpaceRuleOperation)` (exhaustive `switch`, `RESTORE` when `operation.clear()`); `variant(SpaceRuleOperation)` (AIM direction 0-3, else 0). No client imports. |
| `network/SpaceRuleAppliedPayload.java` | `record (int category, int operation, int targetGroup)`, id `magical:space_rule_applied`, composite `StreamCodec` copied from `ApplySpaceRulePayload`. |
| `client/hud/RuleFlash.java` | Client state. `record Shot(SpaceRuleChange change, int variant, int tint, Label before, Label symbol, Label after, Label zero, Label caption, long startTick, int lifetime, int seed)`; constants `LIFETIME_TICKS=48`, `REDUCED_LIFETIME_TICKS=32`, `POP_END`, `MARK_END`, `OUT_START`, `FORMULA_SCALE=2`; `begin(SpaceRuleCategory, SpaceRuleOperation, SpaceTargetGroup, long now, Font, HudOptions)`, `tick(long now)`, `current()`, `reset()`. `begin` shapes the five labels (`Component.getVisualOrderText()` + `font.width`, like `HudState.label`), plays the stamp + kind cue via `Minecraft.getInstance().getSoundManager().play/playDelayed(SimpleSoundInstance.forUI(event, pitch, volume))`, and unless `reducedMotion` calls `FirstPersonEffects.apply(new FirstPersonEffectPayload(color, ticks, alpha, shakeTicks, shakeStrength, 0, fovKick).withOverlay(kind.id(), strength, FirstPersonEffectPayload.OMNI))`. |
| `client/hud/RuleFlashRenderer.java` | `render(GuiGraphics, DeltaTracker)` (layer entry; returns at once when `current()` is null or `!options.enabled()`); `static void emit(HudBatch, Shot, HudLayout, float now, HudOptions)` and `static void text(HudText, Shot, HudLayout, float now, HudOptions)` as pure statics for the test; reusable static `HudBatch`, `HudText.OnGraphics` and `Frame` callback like `SigilRenderer`. Quads: caption plate (`PLATE`), formula plate (`PLATE`, `MODE_INK`, corner 8, accent underline), the `RULE_MARK` quad (square, half 20 GUI px, centred on the symbol). Text: before / symbol / after at `FORMULA_SCALE` via the new `HudText.drawScaled`, the `0` label for ZERO, the caption at 1x. Max 3 quads, 5 text draws. |
| `scripts/synth-rule-cues.py` | Deterministic numpy synth writing nine mono 24 kHz Vorbis files to `src/main/resources/assets/magical/sounds/rule/`: `stamp` (60 ms noise burst + 180 Hz thump), `raise` (sine 440->660 Hz, 300 ms), `lower` (660->440, low-passed), `zero` (click + square-ish sweep 900->80 Hz, 250 ms, then silence), `flip` (swelling tone with reversed envelope, ring-mod tail, 350 ms), `lock` (two clicks + 1760 Hz sine 120 ms fast decay), `surge` (55 Hz sub + noise whoosh, 400 ms), `aim` (1200 Hz ping, exp decay 300 ms, echo at 150 ms), `restore` (523/659/784 Hz triad, 500 ms decay). Peak-normalised to 0.5, 5 ms fades. `sf.write(path, data, 24000, format="OGG", subtype="VORBIS")`. |
| `src/test/.../magic/SpaceRuleNotationTest.java` | See tests. |
| `src/test/.../network/SpaceRuleAppliedPayloadTest.java` | Round trip like `CooldownSyncPayloadTest`. |
| `src/test/.../client/hud/RuleFlashBudgetTest.java` | See tests. |
| `src/test/.../client/MagicalSoundsAssetsTest.java` | Every `MagicalSounds` holder has a `sounds.json` entry whose `.ogg` exists on the classpath. |
| `docs/superpowers/specs/2026-09-13-space-rule-flash-design.md` | The Design section above, committed first. |

### Modified files

- `assets/magical/shaders/core/rendertype_hud_sigil.fsh`: `const int RULE_MARK = 10;`, the three
  `const float FLASH_POP_END/FLASH_MARK_END/FLASH_OUT_START` constants, and a `kind == RULE_MARK`
  branch (spec below).
- `client/hud/HudKind.java`: `RULE_MARK(10)` with a javadoc of its packing.
- `client/hud/HudBudget.java`: `FLASH_QUADS = 6`, `FLASH_TEXT_DRAWS = 5`.
- `client/hud/HudText.java`: add `void drawScaled(FormattedCharSequence text, float x, float y, float scale, int argb)`;
  `OnGraphics` pushes the pose, scales, draws at `x/scale, y/scale` (NeoForge's float `drawString`
  overload), pops; `Counting` just counts.
- `client/hud/HudLayout.java`: constants `RULE_FLASH_DY = 52` (plate centre this far above the
  crosshair), `RULE_FLASH_PAD = 5`, `RULE_FLASH_MAX_W = 220`, `RULE_FLASH_CAPTION_GAP = 3`;
  `Rect ruleFlashPlate(int contentWidth)` (centred on `centreX()`, height `2*TEXT_H + 2*PAD`, does
  **not** scale with the HUD scale, like text boxes) and `Rect ruleFlashCaption()` (`TEXT_H` tall,
  `RULE_FLASH_MAX_W` wide, under the plate); both added to `centreShapes(...)` with the widest
  plate so `theCentreGroupIsDisjointAndInsideTheScreen` covers them.
- `client/hud/HudPalette.java`: `static int change(SpaceRuleChange)` with the tints above.
- `client/hud/HudLayers.java`: `RULE_FLASH = id("rule_flash")`; register order becomes
  `SIGIL` above `BOSS_OVERLAY`, `RULE_FLASH` above `SIGIL`, `ENCOUNTER` above `RULE_FLASH`,
  `SELECTOR` above `ENCOUNTER` (the dials close on release, so the flash never fights them);
  `gated("magical_hud_rule_flash", RuleFlashRenderer::render)`. `Session` also calls `RuleFlash.reset()`.
- `client/hud/HudState.java`: `RuleFlash.tick(now)` next to `HudAnnouncer.tick(now)`;
  `RuleFlash.reset()` in `reset()`; a package-private `options()` accessor if none exists.
- `client/ClientPayloadHandlers.java`: `handle(SpaceRuleAppliedPayload)` -> bounds-check the
  ordinals, then `RuleFlash.begin(category, operation, target, HudState.nowTicks(), minecraft.font, HudState.options())`.
- `network/MagicalNetwork.java`: `.playToClient(SpaceRuleAppliedPayload.TYPE, ...)` in the
  registrar chain; `sendSpaceRuleApplied(ServerPlayer, SpaceRuleAppliedPayload)` via `PacketDistributor.sendToPlayer`.
- `magic/SpaceAuthorityService.applyRule`: after `state.sync(player)` send the payload; change
  `playSound(null, ...)` to `playSound(player, ...)` so the caster is excluded from the world chime;
  delete the `displayClientMessage(... "message.magical.space_rule_applied" ...)` line.
- `registry/MagicalSounds.java`: nine `DeferredHolder`s `RULE_STAMP, RULE_RAISE, RULE_LOWER, RULE_ZERO, RULE_FLIP, RULE_LOCK, RULE_SURGE, RULE_AIM, RULE_RESTORE`
  (`rule.stamp`, `rule.raise`, ... `createVariableRangeEvent`), plus `static DeferredHolder<..> cue(SpaceRuleChange)`.
- `assets/magical/sounds.json`: nine entries, `"category": "player"`, `"subtitle": "subtitles.magical.rule_flash"`, `"name": "magical:rule/<name>"`.
- `assets/magical/lang/en_us.json`: add `hud.magical.rule.caption: "%s · %s · %s"` and
  `subtitles.magical.rule_flash: "Space rule rewritten"`; remove `message.magical.space_rule_applied`.
- `registry/MagicalCommands.java`: under `hud`, `rule <category> <operation> [target]`
  (`StringArgumentType.word()`, matched case-insensitively against enum names; fails with the
  valid list on a bad name or a category/operation mismatch; target defaults to
  `EVERYTHING_EXCEPT_USER`); sends `SpaceRuleAppliedPayload` only, no state mutation, no subspace needed.
- `CLAUDE.md` HUD section: one bullet for the rule flash layer, its files, the `/magical hud rule` command and the screenshot recipe.
- Tests touched: `HudShaderAssetsTest` (flash constants), `HudLangKeysTest` (new key present, retired key absent), `HudLayoutTest` (extra assertion, see below).

### `RULE_MARK` shader spec (`rendertype_hud_sigil.fsh`)

Packing (fits `MagicVertex.pack`): `count` (6 bits) = the symbol's width at 2x in GUI px, so the
glyph half-width in quad units is `gw = count / 40.0` (quad half = 20 px); glyph half-height is
the constant `gh = 0.4` (16 px tall at 2x); `paramB` (5 bits) = `change.id() | variant << 3`;
`phase` = lifetime fraction; `seed` = per-flash random; `mode & MODE_ALT` = reduced motion
(marks drawn at their final state, no sweep). Lit register (never `MODE_INK`).

Common: `mk`, `out` from the shared constants; `box = sdBox(p, vec2(gw, gh))`; everything
multiplies by the `ANNOUNCE`-style `keep = step(out * 1.05, nz(Sampler0, texCoord0*3 + seed*5))`
once `out > 0`. Strokes via `strokeAA` and `sdSegment`; glow via `exp`/`pow` falloffs; time
via `tSlow`. Per kind (`paramB & 7`):

- RAISE (0): five vertical rays at `x_i = mix(-gw, gw, (i+0.5)/5) + 0.05*(magicHash(seed+i)-0.5)`,
  each drawn from `y = gh + 0.05` up to `y = mix(gh, -1.0, mk)`; an up-arrow (stem `(0,-gh-0.2)->(0,-gh-0.55)` + two head segments) fading in with `mk`; halo under the box `pow(max(0, 1-box*2), 2) * mk * 0.6`.
- LOWER (1): RAISE with `p.y = -p.y`.
- ZERO (2): strike `strokeAA(p.y, 0.05) * step(p.x, mix(-gw-0.1, gw+0.1, mk))`; embers after
  `mk > 0.6`: `step(0.88, nz(Sampler0, p*3.0 + seed)) * step(box, 0.0)`; a ∅ badge at
  `(gw+0.3, -gh-0.2)`: ring r 0.12 width 0.03 plus a 45° slash.
- FLIP (3): mirror plane `strokeAA(p.x, 0.03) * step(abs(p.y), gh+0.15) * sin(mk*π)`;
  ⇄ below the box at `y = gh + 0.3`: two horizontal segments with heads pointing opposite ways,
  fading in with `mk`.
- LOCK (4): brackets at `x = ±mix(1.0, gw + 0.18, easeInOut(mk))`, each a vertical segment
  `(x, -gh-0.1)->(x, gh+0.1)` plus 0.12-long horizontals at both ends, pointing inward; once
  `mk >= 1` a faint hex lattice inside the box (the `HEX_PULSE` lattice from `rendertype_fp_overlay.fsh` at `p*5`) at 0.25 intensity.
- SURGE (5): ring `exp(-pow((r - mix(gw, 1.3, mk)) / 0.08, 2.0)) * (1-mk*0.6)`; eight rays
  `pow(abs(sin(ang*4.0)), 12.0) * (1-r) * (1-mk)`; residual `halo += (1-r) * 0.25 * (0.5+0.5*sin(tSlow))`.
- AIM (6): `variant = paramB >> 3`. 0: up-arrow; 1: down-arrow; 2: ¾ arc `band(r, 0.85, 0.06) * step(a, 0.75)` with a head at its end; 3: four inward arrows via `foldAngle(p, 4.0)` on a stem at r 0.8->0.55. Sweep in: rotate `p` by `(1-easeInOut(mk)) * π/2` and multiply by `mk`.
- RESTORE (7): ring `exp(-pow((r - mix(gw, 1.2, mk)) / 0.06, 2.0)) * (1-mk)`; ↺ badge at
  `(gw+0.3, -gh-0.2)`: arc `band(length(p-c), 0.14, 0.03) * step(0.15, angle01(p-c))` with a small head; box glow settling `(1-mk)*0.4`.

### CPU per-frame math (allocation-free; in `RuleFlashRenderer`)

`phase = (now - startTick) / lifetime`; alpha `= easeOut(pop) * (1-out) * options.opacity() * HudState.fade().sample(partial)`;
plate scale `= 0.72 + 0.28 * easeOutBack(pop)` applied to the plate rect about its centre (CPU
geometry, no pose); formula text drawn through `pose` translate/scale about the plate centre for
the pop, then the symbol's own transform: RAISE/LOWER `dy = ∓3 * easeOut(mark)`; SURGE
`s = 1 + 0.8*sin(mark*π) + 0.1*mark`; FLIP `sx = |cos(mark*π)|` about the symbol centre (never negative: the text render type culls a mirrored glyph); ZERO symbol
alpha `1 - 0.85*mark`, `0` label alpha `mark`; RESTORE colour `HudPalette.lift(tint, mark)`.
Caption alpha `0.85 * alpha`. Text is drawn after the `drawSpecial` block so it lands on the
quads (same as `SigilRenderer`).

