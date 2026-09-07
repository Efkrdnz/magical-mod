# Simple Progress Notes

## Magic Progression

- Added magic proficiency XP and levels.
- New players start with one skill: Arcane Bolt.
- Each proficiency level-up unlocks one random skill.
- Higher-tier skills are rarer from random unlocks.
- Added a stronger final skill tier.
- Locked skills no longer appear in the skill GUI.

## Skills

- Added many test skills across arcane, fire, water, light, and void.
- Added class-specific skills.
- Class-specific skills use different colors.
- Class-specific skills are unlocked through classes, not random proficiency rewards.
- Skills can be equipped to Z, X, and C.
- Extra skills can be added to the ability wheel.

## Ability Wheel

- Added a hold wheel on B.
- Releasing B closes the wheel and keeps the selected ability.
- V casts the selected wheel ability.
- Mouse wheel can rotate the ability wheel.
- Camera rotation is locked while holding the wheel.
- Wheel visuals were changed toward a circular/pizza-slice style.

## GUI

- Added Magic Codex GUI for skills, loadout, wheel editing, classes, and blacksmithing.
- Made GUI sections larger and reduced text overlap.
- Added class tree/progression display.
- Class list stays empty until classes are unlocked.
- Added a blacksmith forge view inside the codex.

## Classes

- Added multiple class roots and evolutions.
- Classes can unlock class-specific skills.
- Class evolutions can unlock stronger class-specific skills.
- Blacksmith and Divinesmith are used for weapon forging.

## Blacksmithing

- Reworked blacksmithing into a single-canvas rune chain: you draw a grade sigil, an element core, one or more form glyphs, and optionally a temper mark and modifier runes, all in one chain.
- Six weapon grades from Crude to Divine set how many form and modifier slots a chain gets, how much mana it costs, how neat the drawing has to be, and the material the weapon has to be made of.
- A forged sword or axe fights on the left click: each press steps through the chain's forms as a combo, and holding the button charges a heavier version of the next one.
- Every element and form pair has a named Art with its own effect, listed in the forge preview, the glyph codex and the weapon tooltip.
- Divine is now a weapon grade, not an effect.
- Removed item requirements from forging.
- Added a real weapon slot in the forge GUI.
- Player puts a sword or axe in the forge slot, forges it, then takes it back.

## Items

- Removed all custom mod items.
- Removed old item models and recipes.
- Magic no longer requires Arcane Focus or any other custom item.

## Magic Health

- Added custom permanent magic barrier/health.
- Barrier can absorb damage.
- Barrier can be refilled using mana.

## Testing

- Debug commands exist for unlocking skills, classes, XP, mana, barrier, and opening the codex.
- Recent builds compile successfully with `./gradlew build`.
