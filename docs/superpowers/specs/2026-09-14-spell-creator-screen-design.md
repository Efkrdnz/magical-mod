# The Spell Creator screen

Date: 2026-09-14. Status: approved design, being built.

## Why

The Spell Creator / Magic Originator workshop was a branch inside the Magic Codex: the codex's
container menu carried thirty-six player-inventory slots that only that branch ever painted, the
Create button was a numeric id band (first index times the roster size plus the second index),
the two formula slots were text boxes, and nothing told the player which of the nine formulas
they could make with what they owned. The rework gives the workshop its own screen, removes the
inventory, draws skills with their real emblems, and adds a Formulas tab.

## What the player sees

A 444x340 panel, the same footprint as the codex, so Back and forth does not jump.

- Header: the title, a class chip (Magic Originator when owned, otherwise Spell Creator), a
  bar of Spell Creator XP toward the Magic Originator cost (full and labelled with the class
  once owned), and Back, which reopens the codex.
- Two tabs on the body's top edge: Create and Formulas.
- Create: an Ingredients list on the left (every owned skill that appears in any formula, in
  registration order, each with its emblem, name, school and tier, and a I or II tag when
  loaded); on the right Slot I and Slot II cards (emblem, label, name, a clear cross), a result
  card and a Create button. Clicking an ingredient fills the first empty slot, or replaces Slot
  II when both are full; clicking a loaded one clears it. The result card reads the pair: pick
  two, no known formula, or the formula with its status (Ready, Missing X, Needs Magic
  Originator, Created), its requirement line and its loss line. Create is live only when Ready.
- Formulas: every formula on one row: the output's emblem and name, the two inputs (missing
  ones tinted blood), and a status chip; sorted ready, missing, locked, created, stable by
  recipe order. Clicking a Ready row loads both inputs and switches to Create. The tooltip
  carries the requirement and loss lines.
- Creating: the client sends the pair; the server runs the existing fusion service. When the
  next state sync shows the output unlocked, the result card plays a twenty-tick reveal (the
  emblem pulses, the rim glows gold) and one new cue plays. A consuming formula leaves its
  inputs gone from the list; the card keeps showing what was made.

## Architecture

- `magic/MagicFusionService`: `FusionRecipe` names its inputs by id; `Status`, `FormulaState`,
  `status(state, recipe)` and `formulas(state)` are pure and server-safe; `eligibleInputs` is
  sorted by registration order. The button-id band is gone.
- `network/OpenSpellCreatorPayload` (server to client: tab and optional preloaded inputs) and
  `network/CreateSkillPayload` (client to server: the pair). The class tree sends the first after
  checking the class; the codex Classes tab opens the screen client-side; `/magical creator
  [create|formulas] [first] [second]` and `/magical create <first> <second>` exist for captures.
- `client/screen/creator/SpellCreatorScreen` (plain `Screen`, does not pause), `SpellCreatorLayout`
  (pure geometry, pinned by an overlap and hit-test suite), `FormulaListPanel` (the second tab),
  `EmblemPainter` (queues emblem quads and flushes them in one `drawSpecial` on the HUD sigil
  render type, so fills go under and text goes over).
- Opening from a container: the client closes the container first (which tells the server), then
  sets the screen. Back sends the open-codex request.
- Retired: the codex's pending-view mechanism, its inventory slots, the fusion band, and every
  creator field and method in the codex screen.
- Side fixes carried along: the HUD announcer diffs by membership (a consuming fusion is two
  out, one in, and used to announce nothing) and no longer announces wheel modes; the Black
  Flames description names Wildfire, which is what the recipe uses.

## Verification

Unit: `MagicFusionServiceTest`, `SpellCreatorLayoutTest`, the two payload round trips,
`HudAnnouncerTest`, `MagicalSoundsAssetsTest`, `MagicalTooltipAssetsTest` (the creator package is
scanned for literal lang keys). Visual: one dev-client launch with `-PautoCommands` opening the
screen, creating Black Flames and switching to Formulas, and `-PautoScreenshot` at each state;
the screen carries the `HudDebug.Captured` marker so the auto-closer leaves it alone.
