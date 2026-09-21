# The Authority of Causality

*2026-09-21*

Source brief: `D:\downbad\blade\authority_of_causality.md`.

## What it is

The wielder controls **cause and effect**. Not a spell list: a **board** of pins and string on
which they write rules of the form *when X happens, Y happens*, and which then runs by itself.

Every other Authority in the mod is **pressed**. Space writes a law and the domain enforces it,
Chaos burdens a site and the pile topples, Mana recites a slot. Causality is the one that runs when
nobody is pressing anything - the work happens at the board, hours before the fight, and what the
wielder spends in the fight is having been right.

## The shape, and why it is a graph

Naming the data structure first is the rule that keeps nine Authorities from being one Authority in
nine coats.

| Authority | The thing it owns |
|---|---|
| Space | a table of twelve laws over a region |
| Chaos | a sandpile: a sparse map of stress, and five faults in an order |
| Mana | a grimoire: a linear sequence of verse cards, read *n* at a time |
| **Causality** | **a directed graph, evaluated reactively on world events** |

A graph is the one shape where **the wielder decides the order of evaluation** rather than the mod.
That is what lets two people holding this Authority build machines with nothing in common, which is
the brief's stated design goal.

Grammar: `CAUSE -> CONDITION* -> EFFECT`. Nothing runs into a cause, nothing runs out of an effect.
That leaves exactly one way to draw a loop - a run of conditions biting its own tail - and
`Weave.connect` refuses it by asking whether the far end can already reach the near one. So the
wielder is told no while drawing rather than after saving, and `Weaver` can walk a chain with a
plain depth counter instead of a visited set.

## Conservation is the balance

**Paradox is charged for breaking conservation and for nothing else.** This one rule replaces a
table of tuned numbers and is the whole of what makes a build clever or clumsy:

- **Moving** a consequence somewhere it can still land - `PASS`, `RETURN`, `STORE` - keeps the
  books straight. The world still receives what it was owed, only not where it expected. Nearly
  free: `0.08` paradox a point, or none at all for a Store.
- **Unmaking** one - `ERASE` - or **inventing** one - `ECHO`, `TURNED`, `GREATER` - is a lie told
  to reality. `0.9` a point for an Erase, so erasing a twenty-damage hit twice puts the wielder
  most of the way to a collapse and passing it on does not.

So the clever build is not the one with the strongest effect on it. It is the one that never needs
to erase anything, because it arranged for the consequence to have somewhere else to go. That is
taught by arithmetic rather than by a tooltip, and `WeaverTest` pins every rate on an exact value.

### The ledger

`Ledger` is consequence the wielder is holding rather than letting happen. **Nothing creates ledger
out of nothing**: every point in it was a point that did not land on somebody. That makes storing a
defence and releasing an attack the same act seen twice, and it makes the number on the board a
debt the world is owed. It leaks a point every 40 ticks, so an afternoon of banking is not a
weapon.

`Recompense` - the press that dumps it all - charges **no paradox at all**, for the same reason.
Every point of it was taken fairly and is only being put back.

### Paradox, and the collapse

`0-39` settled, `40-69` **strained** (double mana), `70-99` **fraying** (everything halved, no
chain longer than three pins, `ERASE` refused outright), `100` **collapse**: the board shuts for
400 ticks and **the ledger falls due on the wielder**, because consequence held is consequence
owed and there is nothing left holding it. The conservation rule keeping its promise at the exact
moment it would have been most convenient to break.

Fraying deliberately makes the Weave *short* rather than merely weak - it takes the elaborate
machinery away first and leaves the plain `cause -> question -> consequence` rules working, which
is a shape of degradation a wielder can plan around.

## The budget

`Weave.CAPACITY` = 24 points of node weight, spent however the wielder likes. Six cheap chains or
two expensive ones, never both. The brief's progression is in the weights and nowhere else:

- your own causality is **1**; somebody else's is **3**, and only through a `Causal Anchor`
- a consequence aimed at yourself or at whoever you are already tangled with is **free**; at the
  nearest body **1**; at the marked **2** and a live mark; at the whole field **4**
- `Take Fire Damage -> Store` weighs **3**. The brief's expensive example -
  *take fire damage, convert, teleport the attacker, transfer the damage* - weighs **fourteen**

A pin that will not fit is refused **while it is being drawn**, because a board that can be drawn
and not kept is a board that lies to the person drawing it.

## The five presses

| Skill | What it does |
|---|---|
| **The Causal Board** | opens the board. Free, no cooldown, like the Grimoire |
| **Causal Anchor** | marks what you look at, for 900 ticks. The only door out of your own causality |
| **Decree** | fires the one cause you trigger by hand, so a board can have a trigger of its own |
| **Recompense** | spends the whole ledger at once on what you look at. No paradox |
| **Suspend** | every rule off, or on again. Paradox cools three times as fast while it is quiet |

`Suspend` matters more than it looks: a board that returns consequence to its author is a liability
inside a friend's area of effect, and one that stores half of everything is a liability when you
want a heal to land whole. It turns the gauge from a thing that is waited out into a thing that is
decided about.

## The split

`Weaver` is **pure**: given a board, an event and a reading of the world it produces a
`Resolution` - the rewritten magnitude and a list of `CausalAction`s - and does none of it.
`CausalityService` does as it is told and decides nothing. The same split `Pile`/`PileService`
makes, and worth as much: the grammar, the prices, the order two branches fire in and the whole
conservation rule are unit tests rather than something somebody watched happen once in a dev
client.

Two details that carry weight:

- **The signal is live.** A chain reads the consequence as it stands when the signal reaches the
  pin, so two Stores at half take half and then half of the rest, never half twice. Conservation
  cannot be broken by drawing a second wire.
- **Order is the wielder's.** Branches fire in the order the string was drawn, causes in the order
  they were pinned. Not an implementation detail leaking out - it is the one thing a graph offers
  that a list and a rule table cannot.

`HURT`, `STRIKE` and `BREAK` are spliced into the exact rung of
`MagicGameplayEvents.onIncomingDamage` where they belong: a Store must run *before* the barrier or
storing a hit would not save the barrier from it, and a Break must run *after*, because it is the
barrier emptying that it is about. A subscriber of its own could not say where it sat in that
ladder.

`BRIM` is fired a tick behind the store that fed it rather than inside it: a board that banks a hit
and answers on the same tick would re-enter the engine from inside itself for the most ordinary
thing anyone will build, and the cascade guard (`MAX_CASCADE` = 3, then paradox) exists for genuine
recursion.

## The board

Frameless, like the Grimoire and the Manipulate Space selector: the world is dimmed with one scrim
and everything is glyphs, string and text on it. A pin is a **9x9 glyph** in its kind's colour -
amber for what happened, pale blue for what is asked, crimson for what follows - with its name and
number beside it. A wire is a **bowed run of one-pixel dots** with a one-pixel shadow down and
right, which is exactly what vanilla does to every letter on the screen: a line over daylight
terrain needs that contrast for the same reason a letter does, and it is why the scrim does not
have to be heavier than the Grimoire's.

Every gesture is one gesture. Drag a word out of the palette onto the board to pin it; drag a pin
to move it; drag from a pin's right-hand mark to another pin to run string; right-click a pin to
take it down, or the middle of a piece of string to cut it. A selected pin's number, scope and
tools are on the inspector line; the **reading** under that says the chain in words and names the
first thing wrong with the board.

`WeaveReview` is as load-bearing as the engine. Every way a board can be drawn and still say
nothing - an orphaned pin, a chain that trails off, a question about a blow on a cause that carries
no blow, an effect that reaches back into an event already over, a chain deeper than a signal can
travel - is silent at runtime, and a wielder who cannot tell a board that is *waiting* from a board
that is *broken* will stop trusting the Authority.

## Captures

```
.\gradlew runClient -PquickPlay="New World" -PwindowSize=1280x720 -PautoCommands="gamerule sendCommandFeedback false;magical reset;magical hud race human;magical class unlock mystic;magical authority set authority_of_causality;magical causality preset counter;time set day;141:magical causality board" -PautoScreenshot=152,176 -PautoExit
```

`/magical causality preset counter|thorns|bank` loads a worked board; `show` prints every chain and
every issue; `ledger <n>` and `paradox <n>` force the two gauges; `anchor` and `decree` fire the
skills without binding a key.

## Files

Pure core (`magic/causality/`): `Cause`, `Condition`, `Effect`, `Modifier`, `Scope`, `NodeKind`,
`CausalNode`, `Weave`, `Ledger`, `Paradox`, `Anchor`, `CausalEvent`, `CausalWorld`, `CausalAction`,
`Resolution`, `Weaver`, `WeaveReview`, `WeaveText`, `WeavePresets`.
Runtime: `CausalityService`, `CausalityEvents`, `LevelCausalWorld`.
Client: `client/screen/causality/CausalBoardLayout`, `CausalBoardScreen`, `CausalGlyphs`.
Tests: `WeaveTest`, `WeaverTest`, `WeaveReviewTest`, `WeavePresetsTest`, `CausalGlyphsTest`,
`CausalBoardLayoutTest`.
