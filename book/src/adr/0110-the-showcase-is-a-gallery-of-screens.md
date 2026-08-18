# ADR-0110: The showcase is a gallery of screens

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md`'s cross-cutting notes,
  `docs/ARCHITECTURE.md` §14, uses
  [ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md) and
  [ADR-0093](0093-an-application-is-a-root-widget.md)

## Context

`docs/core-widgets.md` asks for a gallery in one sentence, and the sentence has a
rule in it:

> **Gallery app** exercises every widget in every state in both themes;
> golden-image CI runs the gallery matrix. **A widget isn't done until it's in the
> gallery.**

What existed was a *sidebar* — one document holding every control the catalog had
— beside a pane of prose. That worked at four controls and was failing at eleven:
the file had become a list rather than a demonstration, `sidebar.kdl` was the
place a new widget went because there was nowhere else, and nothing in the window
was about anything.

## Decision

**A window is a title bar and a gallery: one tab strip, and a screen behind each
tab.**

| Screen | What it is about | Where it lives |
|---|---|---|
| Controls | §3's controls whose value is a *state* | `controls.kdl` |
| Values | §3's controls whose value is a *number* | `values.kdl` |
| Text | §2's wrapped paragraph, and buttons that act on the model | `Content.java` |
| Overlays | §7's two places something can float | `overlays.kdl` |
| Tabs | §5's strip, gaining and losing tabs | `TabsDemo.java` |

### A screen is a file

Adding a screen is a file and a line. That is the whole of the structural
argument: a document that is about one thing can say what that thing is, and the
gallery's own `Screen` names five of them and knows nothing about what is in any.

### Three documents, two Java panes, and which is which is the point

The split is **not** about appearance — every screen looks the same kind of thing
— it is about what §8's markup cannot say:

- **Text** is Java because Undo and Reset are disabled when the click count is
  zero, and markup has no expressions. `disabled=#true` is a constant, and a
  document that could evaluate `clicks == 0` would be code in a data file with no
  stack trace.
- **Tabs** is Java because its list *changes*: tabs are added and closed while the
  window is open, and KDL is data. It can write three tabs, not "however many the
  model has".

Everything else is a document, because everything else is `bind=` and `change=`.

### The gallery's selection is an ordinary bound value

`tabs` reads which screen is showing through `bind` and reports `change`, like
every other control ([ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)).
So `Ctrl+1`…`Ctrl+5`, the strip itself, and anything else that wants to are three
ways to set one property rather than three copies of a selection.

The gallery's strip is deliberately **fixed** — five screens, none closable, no
`+` — and the Tabs screen is where a strip that gains and loses tabs is
demonstrated. A gallery whose own chrome could be closed would be a gallery you
can break.

### Only the selected screen exists

§5's lazy content ([ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md))
means four of the five screens are not in the element tree at all. That is what
keeps a five-screen window as cheap as the one-pane one it replaced, and it is why
switching screens costs a rebuild of one screen rather than of the window.

The cost is the documented one: a screen is rebuilt when it is selected again, so
anything that must survive belongs in `ShowcaseModel` — which is where all of it
already was.

### The gallery is a golden-image corpus, and the clock has to be frozen

`GalleryGoldenTest` paints one image per screen, plus one on the light theme.
`ShowcaseDocumentsTest` asserts the documents' *shape* — every control present,
every `bind=` reaching the model — and could not tell you whether a screen renders
at all; these can.

Two things had to be true for that to work, and neither was:

- **`:example`'s test JVM did not know where the native library was**, so every
  golden test *skipped*. A green build that checked nothing, which is the failure
  mode a skip always has.
- **The Values screen has a `spinner` on it**, whose rotation is a function of the
  frame clock rather than of a transition
  ([ADR-0081](0081-a-perpetual-loop-has-no-state.md)). Against the system clock
  the image failed by 113 pixels with a channel delta of 144 — a spinner caught a
  few degrees round. The renderer takes `Clock.virtual()`, which is the frame every
  machine gets.

## Alternatives considered

- **Keeping the sidebar and adding tabs beside it.** Two navigation structures in
  one window, and the sidebar would still be the place a widget goes because there
  is nowhere else.
- **One screen per widget.** Thirty tabs is a list again, with a scroll bar the
  toolkit does not have. The five groups are `core-widgets.md`'s own §-boundaries,
  which is the grouping a reader already has.
- **A screen per *document* with no Java screens at all**, by teaching markup an
  expression or two. That is the change §8 exists to refuse, and the two Java
  screens are worth more as the demonstration of *why* it refuses.
- **Building all five screens and hiding four.** It makes switching instant and
  makes a five-screen window cost five subtrees, their subscriptions and their
  animations — including a spinner that would keep the frame loop awake from a
  screen nobody is looking at.

## Consequences

- **`sidebar.kdl` is gone**, and with it the last place a widget could be added
  without deciding what it is about.
- **The gallery has no scroll**, so a screen taller than the window loses its
  bottom — §1's `scroll`, again, which is now the missing widget behind the
  popup work, the menu work and this.
- **Six golden images of the example**, which is the first visual coverage the
  showcase has ever had; before this, a screen that rendered blank passed every
  test it had.
- **A widget is not done until it is on a screen**, and there is now a screen for
  it to be on: `select` belongs on Controls, `text-input` on a Forms screen that
  does not exist yet, `dialog` and `toast` on Overlays.
