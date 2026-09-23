# 457. A sheet is grouped by its upstream's own categories

Date: 2026-09-23

## Status

Accepted. Extends [ADR-0386](0386-a-sheet-of-emoji-is-the-fonts-own-contents.md)
and the icon sheet of [ADR-0316](0316-a-grid-is-a-list-of-rows.md);
the virtualized grid of both stands.

## Context

The showcase's Icons and Emoji screens were one alphabetical (or code point)
wall each: 1544 icons, 1212 emoji, a search field. Two things were asked for:
the sheets **grouped by category**, with chips to choose one, and a tile that
**opens a dialog** of the glyph at five sizes — with a line of text at several
sizes for an emoji.

## Decision

### The categories are the upstreams', compiled at build time

Neither list is invented here. Lucide files every icon under one or more of its
own categories in the JSON beside each SVG — already inside the pinned archive
`:assets` fetches. Unicode files every emoji under one of ten groups, in its own
emoji order, in `emoji-test.txt`; the JDK carries every emoji property but that
one. So `:assets` gains `CatalogCompiler` and `PrepareCatalogs`, which compile
both into one line per entry, into `:example`'s own `catalog` package
([ADR-0387](0387-a-resource-directory-is-a-package.md)), exactly as the icons
themselves are compiled ([ADR-0033](0033-assets-are-fetched-and-compiled-not-committed.md)).

`emoji-test.txt` is pinned as a **single file** by URL and checksum
(`Asset.Packaging.FILE`, [ADR-0456](0456-the-emoji-face-is-noto-drawn-from-its-paint-graphs.md)),
and a single-file asset may now extract nothing, since this one is compiled
rather than shipped. The Unicode licence is vendored beside the others.

### A heading is a row of the same pitch

Both sheets are one virtualized `list` of equal-height rows, which is told one
pitch and trusts it ([ADR-0213](0213-a-virtual-list-is-two-spacers-and-a-window.md)). So a
group's heading is a row of that pitch with its label at the bottom, and the
grid stays one list — forty lists in one viewport would each virtualize a window
the others had moved. `CategorySheet` holds what both sheets share: the groups,
the rows, the heading and the chip row.

An icon in three categories is under three headings, as on Lucide's own site;
the count beside the field counts it once. Emoji are shown in **Unicode's
order** within each group — the grinning face before the tears of joy — and code
points Unicode groups nowhere go under "Other", last.

### Chips choose one; pressing it again is All

Single-choice chips, "All" first. Pressing the chosen chip takes the filter off,
which is what a hand expects of a filter row. The choice is the screen's own
state, not the model's: nothing binds to it.

### A tile opens a dialog, and the dialog is the host's

A tile is a `PressableTile` — click, `Space` or `Enter`, focusable, announced as
a button. The screen answers with `Dialogs.show` on the host, because a modal
needs the window and a tile is a value with none (ADR-0106). The icon dialog
builds the icon at 16, 24, 32, 48 and 64 points; the emoji dialog styles the
glyph at the same five sizes and sets a line of ordinary UI-face text at five
text sizes with the emoji in it, which is the routed case of ADR-0393 — the one
an application actually draws.

## Consequences

The grouped icon sheet repeats icons — 3031 tiles for 1544 icons, about twice
the flat sheet — and, being virtualized, builds the same window per frame.

A showcase built without the catalog step shows every icon under "other" and
every emoji under "Other", rather than refusing to open. `CategorySheetTest`
asserts the real tables cover every bundled icon.

A dialog is invisible to a test that walks the element tree, so the screen tests
build their trees with a `RecordingHost` — `TourTestHost` behind a proxy that
records what is filled over the window.
