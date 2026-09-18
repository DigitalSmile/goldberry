# 393. An emoji is routed by the text and drawn in layers

Date: 2026-09-18

## Status

Accepted. Closes `docs/gaps.md` G49, and pays off the promise
`docs/ARCHITECTURE.md` §5 made for M2 — *"emoji sequences (ZWJ, VS-16,
modifiers) detected during itemization and routed to the emoji slot"*.

Follows [ADR-0384](0384-the-emoji-face-is-an-artifact-an-application-opts-into.md),
which moved the face into its own artifact, and
[ADR-0386](0386-a-sheet-of-emoji-is-the-fonts-own-contents.md), which put a
sheet of it on screen.

## Context

The toolkit bundled an emoji face, documented an emoji slot, shipped a screen
showing 1205 emoji — and drew every one of them as a `.notdef` box in any text
that was not already in the emoji face. G49 reported it from the first
application that needed it: a chat window, where `👀` in a message body and a
reaction chip that is an emoji and a count are not decoration but content.

Two separate things were missing, and either one alone would have left boxes on
screen.

**Nothing split the text.** `Paragraph` shaped one string with one `Font`.
`Rolling to eu-2 🎉 at 14:00` went to Inter entire, and Inter has no party
popper. An application could not work around it: picking the face for a whole
run is wrong for that sentence and impossible for a chip whose label is a
picture and a number.

**Nothing could have drawn it in colour anyway.** The face that shipped was
OpenMoji's *monochrome* build, and `Asset.OPENMOJI` said why in as many words:
the colour build "is opt-in and not bundled until something can draw layered
outlines". So even a correctly routed emoji would have been a silhouette.

## Decision

### Itemization is a new package, and reads Unicode out of the JDK

`text.itemize` holds `Itemizer`, `TextRun` and `Slot`. `Itemizer.runs(text)`
splits a string into consecutive runs, each labelled `TEXT` or `EMOJI`, covering
the string exactly.

The rules are UTS #51's, reached through `java.lang.Character` —
`isEmoji`, `isEmojiPresentation`, `isEmojiModifier` and their siblings, which
the JDK has carried since 21. That is the whole reason this is thirty lines
rather than a table the toolkit has to re-fetch every time Unicode moves: the
properties come with the JDK and move with it.

What the sequence rules add on top of the per-character properties is where a
naive split goes wrong, so they are each a test:

- **U+FE0F** turns an emoji character into a picture and **U+FE0E** turns it
  back into a glyph. `❤️` and `❤︎` are two strings and two faces.
- **U+200D** joins, so `👨‍👩‍👧` is *one* run and the face gets the chance to
  ligate it into one family rather than three people.
- Skin tones, tag sequences and keycaps belong to the emoji they follow.
- `#` is `Emoji_Component`, so an itemizer that extended a cluster over every
  component would swallow the hash of `🎉#ship`. It does not.

`Slot` is an enum and not a boolean because the list grows: splitting by script
and splitting by direction are the same operation with more answers, and the
bidi approximation `Paragraph` still carries is the next one.

### A `Font` may name a second `Font`, and the book joins them up

`Font.emoji()` is the emoji face at this font's size, or null. It is **set**
rather than passed to a constructor, because the alternative is a constructor
that opens two and a half megabytes of OpenMoji for every font an application
ever makes.

`Fonts` — the per-window book that already opens each face once and keeps it —
is the one place that joins them. Every font it hands out gets the emoji font at
the same size attached, opened lazily on first use like every other face, and
closed with the book. An application that never draws an emoji never parses the
face; one that does gets routing without asking for it. A build with no
`goldberry-emoji` on its module path gets null, silently, because
`BundledAssets.hasEmojiFont()` is how an application that cares asks and a
warning per font opened is not an answer to anything.

There is one real trap in this, and it is recorded in the code: the emoji font
is an entry in the *same* map, so filling it from inside another key's
`computeIfAbsent` would corrupt the `LinkedHashMap`. The lookup is a
get-then-put now.

### A paragraph is one measurement over up to two shapings

`Paragraph` keeps its central promise — shaped once, wrapped many times, every
measurement a prefix sum — and now builds that one array out of two shapings.

The subtlety that makes this work is **units**. A design unit is a fraction of
an em and the fraction differs per face: Inter is 2048 to the em and OpenMoji is
1024. Appending one face's advances to the other's would make an emoji half the
width it is — and half a width is a plausible number, which is exactly what
makes it dangerous. `ColourEmojiTest.theRescaleIsRight` asserts the sum
directly. So the paragraph holds two representations of the same shaping:

- the **concatenated run**, rescaled into the base font's design units with the
  clusters rebased onto the paragraph's offsets, which everything measures
  against — wrapping, carets, hit tests, `text-overflow`;
- the **segments**, each keeping its own face's own shaping in its own units,
  which is what is drawn, because the rasterizer scales a run by the face's
  matrix and would place a rescaled one wrongly.

Rounding is to the nearest design unit, a thousandth of a pixel at any size
anybody reads text at.

A paragraph with no emoji in it has one segment and takes exactly the path it
took before — one shaping, one draw call, and an `Itemizer` scan that allocates
nothing. `Paragraph.glyphs()` now says out loud that its glyph ids may belong to
two faces, which is a thing a caller could otherwise only discover by drawing
the wrong pictures.

### COLR version 0, read in Java, drawn a layer at a time

The colour half is `text.font.sfnt`, a new package for OpenType table readers,
holding `TableDirectory` and `ColorLayers`.

`COLR` version 0 is the format worth having because **a colour glyph in it is
not a new kind of thing**: it is a list of ordinary glyphs in the same face, each
filled with one colour from the `CPAL` palette. The outlines are in `glyf`
beside every letter, so the rasterizer that draws an `a` draws these; all that
was missing was somebody to read the list and set the colour between layers.

The same OpenMoji release ships the same pictures four other ways, and each
would have cost more: the two SVG-in-OpenType builds are 10 MB and want an SVG
renderer inside the font pipeline, and the `CBDT` and `sbix` builds are 6 MB of
fixed-resolution strikes that blur at 150% — on a toolkit whose whole claim is
that it is crisp at every scale.

Reading it in Java rather than binding a library is `FaceCoverage`'s argument
and `GifDecoder`'s before it: the table is three flat arrays and the reader fits
on a page.

`GlyphFace` reads it once per **typeface**, not per size — 57,000 layer records
parsed once. `GlyphPen` then has one branch: a face with no colour in it takes
exactly the path it always took, and a face with colour draws each glyph's layers
in order. Every glyph is staged with a zero advance and an absolute offset, which
is what lets the buffer be flushed between two glyphs without the ones after it
losing their place.

Version 1 of `COLR` is read as version 0. Its gradient machinery lives in fields
*after* the version 0 ones, which stay where they are and keep meaning what they
meant, so a version 1 face draws its non-gradient glyphs correctly and its
gradients flat. That is a smaller wrong than refusing the face, and it is written
down rather than discovered.

### The artifact ships the colour build

`goldberry-emoji` now carries `OpenMoji-color-glyf_colr_0.ttf`: 2.5 MB against
1.4 MB for the monochrome build it replaces. The condition the old comment set
has been met, so the trade it deferred is taken. It is paid only by an
application that adds the artifact on purpose, which is ADR-0384's whole shape.

## Consequences

**Emoji are pictures.** In prose, in a `text-input`, in a `markdown-view`, in a
table cell — anywhere a `Paragraph` is drawn through a `Fonts` book, which is
everywhere the cascade resolves a font.

**An application that does not add `goldberry-emoji` sees exactly what it saw
before**, and pays nothing: no itemization split, no second shaping, no table
parsed.

**A colour glyph costs one rasterizer call per layer**, where a line of text
costs one for the whole line. An OpenMoji glyph averages fourteen layers, so a
reaction bar of ten emoji is a hundred and forty calls. They are not a hundred
and forty *passes* — a layer is one small glyph and the work is proportional to
the ink — but a wall of emoji is measurably dearer than a wall of text, and the
pen's javadoc says so. Batching layers by colour is the optimisation if it ever
shows; it costs the ordering guarantee, which is why it is not taken now.

**An emoji is as tall as the emoji face makes it**, and the line box is the base
font's. Measured at 14 logical pixels: Inter ascends 13.563 and OpenMoji ascends
14.287, so an emoji reaches about three quarters of a pixel **above** the line
box it sits in, and its natural line height is 18.047 against Inter's 16.939.

That is not a defect and it is what a browser does too — CSS takes the line box
from the primary font and lets a fallback overhang — but it is not what an
earlier draft of this record claimed, which was that it fits. A box that clips
to its content will clip that sliver on its first line. Scaling the emoji face so
its ascent matches the base face's is the alternative, and it is a separate
decision because it trades an overhang for emoji that are visibly smaller than
the text around them, which is a typographic judgement rather than a fix.

**Kerning across the seam is lost.** Each run is shaped alone, so a kerning pair
spanning the boundary between a word and a picture is not applied. There was
never such a pair.

**Two new packages**, and `paint` now reads `text.font.sfnt`. That is not a new
direction of dependency — `paint` already reads `text.ShapedRun` — but it is
worth naming: the table readers are font-format knowledge, and the pen is the
only thing in `paint` that needs any.
