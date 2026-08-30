# 222. A showcase is a window, a bar and seven screens

Date: 2026-08-30

## Status

Accepted. Restructures the gallery
[ADR-0110](0110-the-showcase-is-a-gallery-of-screens.md) created.

## Context

ADR-0110 split one sidebar of every widget in the toolkit into a gallery of
screens, because a single pane stopped being able to hold the catalog somewhere
around the eleventh control. That was right, and the rule it used to decide what
went where — **one screen per widget family** — did not survive the catalog
growing to fifty-one widgets. Twelve screens later it had produced a gallery that
nobody could read:

- **`Controls`, `Values` and `Text` were three tabs** you had to visit in turn to
  see what one screen's worth of chrome looks like. Nobody builds a window of
  only checkboxes.
- **`Overlays` and `Notifications` were the two halves of one comparison** —
  should this float or should it sit in the layout? — with a tab between them, so
  the comparison could not be made.
- **`Tabs`, `Scrolling` and `Choosers` were all "how do I get around"**, filed
  under three different widget names.
- **Twelve screens, ten digits.** `Ctrl+1`… ran out, and ADR-0110's own note
  admitted that the last screen was reachable by the strip and the menu and not
  by a key.

Every screen was also a plain `column`, so each was a single tall stack that the
gallery's viewport scrolled. On a maximized window that is one narrow ribbon of
content down the left of a very wide screen, and `masonry` — built for the Charts
screen, which was the only one using it — was sitting right there.

And the content was `Item 1`, `Row 3`, `Series 1`, `Peak`. A label that is a
placeholder cannot show whether a sortable header, a shared crosshair or a
wrapped paragraph *reads*; only whether it draws.

Finally, the window itself was a title bar with a theme button on it. There was
no application menu anywhere — `menubar` existed and the only one in the
repository was a demo *inside* a screen, which is a widget being shown rather
than a widget being used.

## Decision

**Seven screens, each a question rather than a widget family.** `Basic`,
`Panels`, `Overlays`, `Forms`, `Navigation`, `Collections`, `Charts`. Seven is
inside ten, so every screen has an accelerator — which is the second thing the
restructure bought and the one that closes ADR-0110's open note.

**Every screen is a `Wall`: a heading, a line of prose and a masonry of cards.**
One shape for seven screens, and a *type* rather than a convention because it was
a convention first and four screens drifted off it. A card is as tall as its
contents and no two are the same height, which is the case `masonry` exists for
([ADR-0196](0196-a-masonry-is-a-layout-that-reads-last-frame.md)).

**Every card is a `card` or a `group-box`.** A wall of bare columns is a wall
with no edges in it; the panel is what makes a group of controls one thing.

**A document supplies the cards it can and Java appends the rest to the same
masonry.** Four screens are `basic.kdl`, `panels.kdl`, `overlays.kdl` and
`forms.kdl`, each with a `masonry` at its root, and `Panes.wallOf` refuses a
document whose root is anything else — a `column` wrapped round it during an edit
is a perfectly good document that quietly grows a second wall with different
columns. The Java cards are the ones markup genuinely cannot write: an expression
(`disabled` when the count is zero), a list the application edits (banners), a
channel that hands values *back* (`multiple`, `autocomplete`, `tree`), and series
data.

**The window is three bands: a `menubar`, a bar, and the gallery.** File, Edit
and Help, built in Java because half their rows need a `Host` — opening a dialog,
floating a HUD, closing a window — and a `Runnable` passed to a constructor needs
no name at all. The bar under it is a document: two startup readings, the leagues
counter, and a `toggle` that is the global light switch.

**The theme is one fact in two spellings.** `app.theme` is a name, because a
`radio-group`, a `segmented` and a `select` pick from a list; `app.light` is a
boolean, because a switch's value is a boolean by definition — `Toggle.resolved`
reads `source.get() instanceof Boolean` and falls back to its own flag otherwise,
so binding it to `"light"` gives a switch that never moves. Both are written in
`pickTheme` and nowhere else, which is four lines and the single route every
theme control in the window goes through.

**The content is Middle-earth.** Not decoration: a table of nine companions with
a `Kindred` column that repeats and a `Leagues` column that does not shows a
sortable header doing something that `Row 1`…`Row 6` cannot, and real names are
of wildly different lengths, which is what a layout has to survive.

**The window opens maximized** ([ADR-0221](0221-a-window-may-open-maximized.md)),
because a wall of cards is a layout whose subject is how much fits.

## Alternatives considered

- **Keeping twelve screens and adding a second row of tabs.** Two rows of tabs is
  a menu with the wrong widget, and it does not fix the comparison between a
  banner and a toast being a tab apart.
- **Making every screen Java, or every screen a document.** All-Java loses §9's
  whole argument — a designer moving a card without a compiler. All-document is
  impossible: five of the cards need an expression or a channel markup does not
  have. The split is the interesting part, so it stays and each absence is
  documented where it is.
- **One `Widget` per screen that returns a `Masonry`, with the document's cards
  merged inside it.** What this does. The rejected version was a screen that
  returned its document's wall *and* a second wall of its own — simpler to write
  and visibly wrong, because a masonry packs by column height and two walls
  cannot agree on where a column ends.
- **A card per notification kind, sharing one state object.** Rejected in favour
  of a stateful widget per card: the hidden set only affects the resident banners
  and the spawned list only the stack, so two independent states mean neither
  card rebuilds when the other changes — and three cards can go under three
  different columns where one tall widget can only go under one.
- **A platform menu bar.** On three of the four targets there is not one. A
  `menubar` is drawn by this toolkit, styled by the same stylesheet and routed
  through the same router, so `F10`, the arrows and the accelerators are one
  implementation ([ADR-0163](0163-a-menu-bar-owns-its-menus.md)).
  The one menu handed to the desktop is the tray's, which is why that one is an
  ordinary `Menu` value.
- **Dropping `WindowActions` now that the menu holds handlers directly.** Tried,
  and put back: `overlays.kdl` presses `app.open-menu` by *name*, and only a
  registry can turn a string into a call. The two halves of §9 are now visible
  side by side in one window, which is better than either alone.

## Consequences

- **Twelve golden images become eleven, at 1200×900 instead of 900×560.** The old
  size was chosen for a single-column screen; a two- or three-column wall needs
  the width or the picture is of a layout the application never shows.
- **A narrow golden joins them.** `masonry`'s columns are a count and not a media
  query, so two columns at 1200 are two columns at 720 — half as wide and twice
  as tall. `gallery-basic-narrow` asserts they still fit, because a card with a
  minimum width would overflow rather than wrap and §10's `wrap` is not built.
- **`Set.of` is banned from anything a golden image prints.** Its iteration order
  is randomized once per JVM, so a caption built with `String.join(", ", checked)`
  came out one way on one run and the other way on the next. One thousand pixels
  of caption failed the Collections image at random until the tree's initial
  checked set became a `LinkedHashSet`.
- **`SectionHeader` becomes public and becomes the screen title.** A
  `text.screen-title` did the job for eleven screens and stopped being honest: a
  class is something any node can wear and a heading is a *kind* of node. Being
  an element type is what lets the stylesheet say "a heading inside an affixed
  section takes a surface" without a second class travelling beside it.
- **`Scrolling` becomes a card and keeps the nested-scroll ban.** It used to be
  the one screen the gallery did not wrap in a viewport, because §2.4 bans nested
  same-axis scrollers. It still is — but as a card in a two-column wall, so the
  screen fits without needing to scroll at all. The wall is what made the ban
  affordable.
- **Section names are one word each.** A section's name becomes its
  `#section-<name>` and its button's `#jump-<name>`, and `#jump-bag end` is not a
  selector. Lower-casing is the whole of the transformation, which keeps the ids
  something a stylesheet and a test can both write down.
- **The two `statistic` cards carry no card title.** A statistic has its own
  label, so a titled card printed the same words twice and the wall read as
  though two of its nine cards had stuttered.
- **What is now expensive to reverse**: the wall. Seven screens, four documents
  and every test that finds a card by id assume a masonry of cards. Going back to
  a column per screen is a rewrite of the same size as this one.
