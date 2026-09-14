# The Magic Codex in the creator's theme

**Date:** 2026-09-14
**Status:** approved (the user asked for the creator's theme on the codex)

## Goal

The Spell Creator screen set a look: a 444x340 panel with a top strip (title, identity chip, XP
bar, one corner button), a hairline, a row of tabs, and one body panel whose contents are insets,
section labels, emblem rows and cards. The Magic Codex keeps every function it has and takes that
look, so Back and forth between the two moves nothing but the contents.

## Shared chrome

`client/screen/ScreenChrome` owns the frame geometry (panel, title, chip, XP bar, corner button,
tabs, body) and the paint passes (`paintHeader`, `paintTabs`, `paintBody`, `textHeader`,
`textTabs`). `SpellCreatorLayout` delegates its header, tab and body constants to it and
`SpellCreatorScreen` paints its strip through it, so the two screens cannot drift apart.
`EmblemPainter` moves to `client/screen` and turns public for the same reason.

## The codex

- **Top strip:** title `Magic Codex`; chip `Level N` (proficiency); XP bar of the XP into the
  current level over the XP the next level needs (full, with `Max level`, at the cap); `Close`.
- **Tabs:** Skills, Loadouts, Passives, Classes. The three views that used to be entered by buttons
  and left by Back are tabs now; nothing is more than one click away and the Back buttons go.
- **Skills tab:** left column - the `Pyramid` / `Below` section label with the Below/Above toggle
  beside it, the tier blocks (20 high, 24 apart, narrowing by 14), then the owned-skill list as
  20-pixel emblem rows (emblem, name, school; a diamond when bound) in an inset with the scrollbar
  outside it. Right column - `Loadout` label, the active loadout name, four key cards (key tag,
  emblem, name, tint-coloured foot), Equip and Clear Slot; `Skill Detail` label over an inset with
  a fixed header (emblem, name in its tint, school and kind) and the scrolling description, stats
  and tuning below it.
- **Loadouts tab:** the editor re-based into the body: loadout rows, New / Delete, the rename
  field; wide key cards on the right with the emblem, Bind and Clear.
- **Passives tab:** the two columns re-based into the body under section labels; the animated
  passive tooltip stays.
- **Classes tab:** root class rows, `Paths of Power` in the top-right of the body, the Forge and
  Create Skills workshop buttons along the bottom.
- **Tooltips:** hovering a skill row or a key card shows the creator's skill tooltip (name in its
  tint, school and kind, description).
- **Paint order:** chrome fills, the view's fills and text, one emblem flush, then tooltips.

## Geometry and tests

Every rectangle the codex draws or hit-tests lives in `CodexLayout` (screen-local), and
`CodexLayoutTest` asserts per tab that nothing overlaps and everything sits inside the body, that
the header and tab rectangles are disjoint and above the body, and that the hit-tests answer to
row centres and to nothing in the gaps. `SpellCreatorLayoutTest` keeps covering the creator's use
of the shared chrome.

## Captures

`HudDebug` gains `-PautoClick="tick:x,y;..."`, which presses the open screen at GUI coordinates,
and leaves any screen marked `Captured` alone (the codex is one now), so one launch can walk the
four tabs: open the codex at tick 101, capture at 112, click each tab and capture again.
