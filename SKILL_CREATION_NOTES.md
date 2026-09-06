# Skill Creation Notes

## Visual Direction

- Do not reuse vanilla projectiles, fireballs, explosions, or particles as the main identity of a skill.
- Prefer invisible gameplay entities with custom renderers.
- Render spell visuals with quads, billboards, rings, ribbons, beams, or layered planes.
- Use custom shader files where needed: `.vsh`, `.fsh`, and shader `.json`.
- Skills should feel custom in behavior, not just recolored versions of the same projectile.

## Skill Design

- Build skills as unique mechanics first, visuals second.
- Elemental and holy/arcane skills are allowed, but not every skill must fit a basic element.
- Add strange unique skills that do their own thing.
- Avoid making every skill a basic projectile.
- Skills should have distinct use cases, timing, targeting, and risk.

## Passive Skills

- Add passive skills as their own system.
- Passive skills should appear in a separate tab in the Magic Codex.
- Passive skills should be enabled by default.
- Player can enable or disable normal passives any time with a checkbox.
- Passive skills can include immunities, resistances, mana effects, movement effects, or class traits.

## Curses

- Curses should appear in the passive tab.
- Curses should not have a disable checkbox.
- Curses should have a `Dispel` option.
- Dispelling should require conditions such as proficiency level, mana, class, item, ritual, or boss kill.

## Build Order

1. Build the passive skill system and passive GUI tab.
2. Add starter passives and immunity support.
3. Add curse display and dispel conditions.
4. Replace placeholder active skills with unique mechanics.
5. Replace placeholder visuals with custom entity renderers and shaders.
