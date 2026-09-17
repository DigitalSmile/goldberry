# 386. A sheet of emoji is the font's own contents

Date: 2026-09-17

## Status

Accepted. Adds the showcase's thirteenth screen, and the reader-facing half of
ADR-0384.

## Context

ADR-0384 moved the emoji face out of `goldberry-core` and into an artifact an
application opts into, because CC BY-SA asks for attribution where the work is
*seen*. That left two things unsaid. Nothing in the showcase drew an emoji, so
the toolkit shipped a font nobody could look at; and nothing anywhere showed
what taking the obligation on looks like, which is the part an application
author actually has to copy.

The Icons screen is the shape of the answer — a searchable sheet of every
bundled icon — and it is deliberately *not* the same screen. An icon is a
**path** the application holds and hands to a box. An emoji is **text**: a code
point drawn through whichever face the cascade picked, which is §6.1's font
chain and one `font-family` declaration.

## Decision

**A second sheet, beside the first, built from the face's own `cmap`.**

- `FaceCoverage.codePoints(bytes)` reads a TrueType `cmap` — formats 4 and 12 —
  and answers which characters a face has. Java rather than
  `hb_face_collect_unicodes`, for `GifDecoder`'s reason: the table is two arrays
  of ranges, and binding a set object and its iterator across FFM for a question
  asked once per face costs more than owning the format. Unreadable bytes are an
  empty answer, because the caller is asking what is in a face.
- The sheet is filtered to `Character.isEmojiPresentation`, and **not** to
  `Character.isEmoji`: the second is true of `0`, `#` and `↔`, so a sheet built
  on it opens on the ASCII digits. 1205 of OpenMoji's 1845 characters are
  pictures on their own.
- The names are `Character.getName` — Unicode's own, out of the JDK, so a search
  for "cat" works and no second asset is fetched. The hex matches too, because
  the other way somebody looks for an emoji is with a `U+231B` from a bug
  report.
- The screen **carries the credit**: OpenMoji, openmoji.org, CC BY-SA 4.0, in the
  note under the title. That is what an About box would do, and it is the
  demonstration ADR-0384 owed.
- Everything else is the Icons screen's and is shared rather than copied: the
  reflow arithmetic (`IconsScreen.columnsFor`), the row pitch, the measured
  sheet (`IconSheet`) and the virtualized list of padded rows.
- **The gallery golden for it uses a font book**, alone among the gallery
  images: the others are taken with the one-font renderer, which ignores
  `font-family` — and a picture of an emoji sheet drawn in Inter is a picture of
  1205 `.notdef` boxes.
- `Fonts` now **falls back** when the emoji face is absent rather than throwing:
  a stylesheet naming `font-family: OpenMoji` in an application without the
  artifact draws in the UI face and says so once in the log. The cascade runs
  inside a render pass, and a missing optional artifact must not be able to turn
  a window into no text at all.

## Consequences

- Thirteen screens, and **no digit moved**: `Ctrl+1`…`Ctrl+0` still name the
  first ten, and the two sheets are reached by the strip, the arrows and
  Edit ▸ Go to — which is exactly what ADR-0307 decided when the eleventh screen
  arrived.
- Every gallery golden is re-blessed for one more tab in the strip. The change is
  547 pixels wide and is the tab.
- `:example` depends on `:emoji`, which is the first application in this
  repository to opt into an artifact with an obligation attached — and
  `EmojiScreenTest` asserts that the credit is on the screen, not merely that the
  face loaded.
- `FaceCoverage` is public and general: an emoji picker is the first caller, and
  "does this face cover this script" is the same question.
