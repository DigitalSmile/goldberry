# 307. The eleventh screen has no digit

Date: 2026-09-13

## Status

Accepted. Adds the **Icons** screen to the showcase gallery, and settles the
question [ADR-0110](0110-the-showcase-is-a-gallery-of-screens.md) left open about
what happens when there are more screens than digits.

## Context

The gallery had ten screens and ten accelerators, and `Screen.GALLERY`'s comment
called that "exactly the digits a keyboard has: `Ctrl+0` is the tenth, and the
eleventh would be a screen no key could reach". `ShowcaseShellTest` asserted
`GALLERY.size() <= 10` and said in a comment that "the eleventh screen is a
decision about which one loses its key".

The eleventh screen turned up for a reason none of the ten shares. Every existing
screen answers *what does this widget do*. This one answers a question a reader
has while writing a **document**: `icon="…"` takes a name from a set of 1544, and
until now the only way to find one was to read Lucide's website. A sheet of them
beside the gallery is the difference between a bundled asset and a usable one.

The prior art in this repository is on the other side: ADR-0110 cut twelve screens
to ten *because* two of them had no key, and the bug it was written about was a
`Ctrl+8` that selected the ninth tab. So "more screens than digits" is the exact
shape of a defect this gallery has already had.

## Decision

**Nothing loses its key. The eleventh screen is reached by the strip.**

`Ctrl+1`…`Ctrl+0` keep meaning exactly what they have always meant, and `icons`
is reached three other ways: the strip itself, the arrow keys roving inside it,
and Edit ▸ Go to.

The reason is what the two kinds of screen are *for*. The first ten are galleries
a reader moves **between** — the comparison is the point, so the digit earns its
keep. An icon sheet is a reference opened once and searched; the field inside it
is where the reader's hands go, not a shortcut. Re-pointing an accelerator
somebody already knows, in order to give one to a screen that does not want it,
would cost more than it bought.

### The machinery already allowed it, and that is the interesting half

`Showcase.screenShortcuts` loops to `Math.min(GALLERY.size(), digits.size())`,
and `GalleryOrderTest` has asserted **"ten digits, however many screens there
are"** since the bug that produced it. Neither needed changing. What needed
changing was a *comment* claiming a limit and one assertion encoding it — the
limit was written down as a fact about the gallery when it was only ever a fact
about keyboards.

`ShowcaseShellTest` now asserts the property that is actually load-bearing:
**the screens with keys are the first ten in strip order**, and `Ctrl+0` is
`canvas`. Inserting a screen above the tenth would move every digit, and that is
what a test should refuse.

### The sheet virtualizes, over rows

1544 tiles is 1544 elements with three boxes and a paragraph each. So the sheet is
a `list` with `virtualized(ROW_HEIGHT)` over **runs of seven names** rather than
over icons, and only the rows in the viewport are built
([ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).

Because it owns a viewport, the gallery does **not** wrap it in one — §2.4's ban
on nested same-axis scrollers, which is the Navigation and Markdown screens'
reason.

The icons are cached and built **lazily**: one `Icon` per name on the frame a row
first needs it, kept for the life of the screen. Parsing all 1544 up front is
221 KiB of path data for a screen a reader may never open; parsing them per frame
would be that much per frame. An icon is a value since
[ADR-0277](0277-a-path-is-a-value-and-the-rasterizers-is-package-private.md), so the cache
holds no native memory and needs no closing.

### The application declares a widget, and that is worth showing

There is no `icon` widget in the catalog — an icon reaches the screen as a
`Box.icon` inside whatever draws it. That is right for the toolkit and wrong for a
sheet of a thousand cells, so the showcase writes `IconTile` itself:
`Widget.Leaf` plus `Styled` plus `Paints`, three methods, no permission asked.
The gallery has not shown that before.

## Consequences

**An eleventh screen is now possible in general**, and a twelfth. What constrains
the list is no longer the digits — it is that the first ten must stay the first
ten, which is now asserted.

**Two numbers have to agree**, and the record exists partly to name them:
`IconsScreen.ROW_HEIGHT` and `#icon-sheet list-row`'s height in `showcase.css`.
A `list-row` is `--gb-list-row-height` — 32 — which is right for a list of names
and half the height of a tile, so without the override every row of the sheet
overlapped the one below it. That is what the first golden showed, and it is why
the screen has a golden.

**`BundledAssets.iconNames()` has no order**, which this found: it is the key set
of a `Map.copyOf`, so the first drawing of the sheet opened on `book-lock,
calendar-off, badge, list-start`. The screen sorts, and the method's contract is
unchanged — it never promised an order and this is the first caller that needed
one.

**The sheet is a real test of the frame budget** in a way the other screens are
not: it is the only golden in the gallery of a virtualized tree.

## Alternatives considered

**Take a digit from `canvas` or `html`.** Rejected: both are screens a reader
moves between while comparing things, and a shortcut that changed meaning between
releases is worse than a screen without one.

**Bind `Ctrl+I`.** Rejected, and it is the tempting near-miss. The gallery's
accelerators are *positional* — `Ctrl+<n>` is "the nth tab" — and a mnemonic key
in the middle of that is two schemes in one strip. `Ctrl+I` is also italic in
every editor a reader has used, and the showcase has a `text-area`.

**Put the icon browser in a dialog off the Help menu.** Rejected: it is a
reference a reader wants *beside* the document they are writing, and a modal is
the one shape that guarantees it cannot be. It is also the gallery's own subject —
the icons are a bundled asset of this toolkit, not a utility bolted onto it.

**Show all 1544 without virtualizing and let the frame budget say.** Rejected
without measuring, which is unusual for this repository and is the honest call
here: 1544 tiles is roughly 6000 elements and 1544 shaped paragraphs, against a
`FrameBudgetTest` that already treats 866 elements as the large case
([ADR-0299](0299-a-cache-smaller-than-one-frame-is-worse-than-no-cache.md)). The
answer was not in doubt and the measurement would have been a formality.
