# ADR-0595: A character the face lacks is drawn by a fallback the cmap says has it

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** docs/gaps.md (G57),
  [ADR-0393](0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md)

## Context

A name typed in Han, Arabic or the mathematical alphanumerics (`中文 名字`,
`محمد`, `𝕳𝖊𝖘𝖘𝖆`) drew as `.notdef` boxes, in a chat list, a timeline and an
avatar's initials. `Fonts.of(Typography)` said so on purpose: *"there is no
fallback cascade for glyphs, since a missing glyph is `.notdef`"*. An
application could not work around it by shipping a face, because a shipped
face is matched **by family name**, and no family name says "use this for the
characters Inter lacks".

Half the mechanism existed for one kind of character. ADR-0393 split a
paragraph into TEXT and EMOJI runs, shaped each in its own face, and rescaled
the emoji face's advances into the base font's design units, so one prefix-sum
array still measured the whole paragraph. What was missing was the question
the emoji split never asks: *does this face have this character?* The emoji
run is chosen by what the text says (`Emoji_Presentation`, U+FE0F), and a run
of Han is chosen by what the face holds.

`FaceCoverage` already read a face's `cmap`, but into a sorted array built
through a `TreeSet<Integer>`, once per call, for a picker. Nothing kept an
answer per face, and nothing could ask it per character cheaply.

The entry proposed a service, `FallbackFont { byte[] bytes(); Set<UnicodeScript>
covers(); }`, routed to by the itemizer when the primary face has no glyph.

## Decision

### Coverage is read once per typeface and asked per character

`text.font.Coverage` is a face's characters as merged, sorted ranges with the
first 256 code points also held as a bitmap. `covers(int)` is a shift and a
mask for Latin-1 and a bisection otherwise; `coversAll(text, start, end)` is a
scan that allocates nothing and stops at the first character missing.
Controls, format characters (ZWJ, ZWNJ, the bidi marks, the soft hyphen), the
line and paragraph separators and the variation selectors need no glyph and
count as covered, so a newline does not split a paragraph.

`FontFace` reads it in its constructor, where the bytes are. Neither HarfBuzz
nor Blend2D keeps the file anywhere Java can see it, and `GlyphFace` already
reads its colour tables there, once per typeface, for the same reason.
`FontFace.coverage()` is public.

`FaceCoverage` produces ranges now rather than single code points, and
`codePoints` expands them. A delta segment of format 4 is one range with at
most one code point taken out, rather than a walk. The answers were checked
equal, character for character, to the old reader on all five bundled faces,
the emoji face, and four other faces on the development machine (Noto Sans
Arabic, Noto Sans Math, Droid Sans Fallback, DejaVu Sans). Measured there,
reading a face is 0.02 to 1 ms: Inter 2852 characters in 207 ranges, Droid
Sans Fallback 28 601 in 94.

### An application registers fallbacks, and an artifact may provide them: both

- **`Application.fallbacks()`**, beside `fonts()`, read once when the window's
  book opens. The launcher passes it to `Fonts.bundled(shipped, fallbacks)`.
- **`Fonts.bundled(List<FontSource>, List<FallbackSource>)`** for a book opened
  by hand: a test, an `Offscreen` render given a book, a server.
- **`dev.goldberry.assets.FallbackFont`**, a `ServiceLoader` service beside
  `EmojiFont`, for an artifact that brings faces. Every book searches what
  providers bring after the application's own, so adding such an artifact
  changes nothing else, as `goldberry-emoji` does.

The service alone would have worked, since an application module may provide a
service it uses itself, but that is a `provides` line and a class for what is
otherwise one method beside `fonts()`. The registration alone would have left
no way for an artifact to be added and simply work.

### A fallback is a `FallbackSource`, not bytes

`FallbackSource(FontSource face, Set<Character.UnicodeScript> scripts)`. This
differs from the entry's interface, for three reasons:

- **Bytes are lazy.** A Han face is 19.5 MB as Noto ships it. `byte[] bytes()`
  on a service invites reading it when the book opens. A `FontSource` supplier
  is read when a character first needs the face, and its file is *looked for*
  when the book opens, with the same one warning a missing shipped face gets.
- **A family is several files.** A source has a weight and a style, and one
  family registered at 400 and 700 is one fallback with two weights.
- **`covers()` becomes `scripts`, and it is a hint.** The `cmap` is the truth.
  The hint is read before the face is opened: an Arabic name does not parse a
  Han face to learn it has no Arabic. A character Unicode puts in no script of
  its own (`COMMON`, `INHERITED`) is never ruled out by a hint, which matters
  because the mathematical alphanumerics are `COMMON`. An empty set rules out
  nothing.

The service answers `List<FallbackSource>` for the same reasons.

### Order, weight and style

Search order is the application's fallbacks in the order given, then each
provider's in its own order. Providers are ordered by class name, so the order
does not depend on the module path. Several sources with one family are one
fallback, at the position of the family's first source.

For a base face at a weight and style, each fallback family answers with its
face nearest that weight and style by `Face.match`, the rule a stylesheet's
family is matched by. Nothing is synthesized: bold text in a family shipped
only in regular falls back to the regular. The toolkit draws no synthetic
oblique anywhere (`BundledFont`), and synthetic bold would be a first.

### The book attaches a chain to every font it opens

`Font.fallbacks()` / `Font.fallbacks(Fallbacks)` are the emoji face's pattern:
set, not constructed, because the book opens faces lazily. A book with
fallbacks attaches a private `Chain` to every font it opens (not to the emoji
font). The chain opens nothing until asked. `Fallbacks.fontFor(text, start,
end)` answers the first fallback whose face has every character of the
cluster that needs a glyph, failing that the first with its first character,
or null. The fallback font is opened at the same size, through the same
`fontOf` and the same get-then-put as the emoji font, so it is an ordinary
entry in the book and is closed with it. `Fallbacks.of(List<Font>)` is the
form for fonts opened by hand, and `Fallbacks.NONE` is the default. A fallback
whose coverage is empty, such as a `.ttc` collection, which `TableDirectory`
refuses, is warned about once and skipped.

### The itemizer splits by coverage, and the faces are the caller's

`Itemizer.byCoverage(text, start, end, base, FaceChoice<F>)` returns
`FaceRun<F>`s. It is generic in the face, so the rules are tested with names
for faces (`ItemizerCoverageTest`). The itemizer still decides from the text
where runs may start, and asks a `FaceChoice` which faces have what:

1. The unit is the grapheme cluster (`BreakIterator`), so a letter and its
   marks, a surrogate pair and jamo are never split between two faces.
2. A cluster goes to the base face when it has all of it. Otherwise it goes to
   the fallback the previous cluster went to, when that has it, so an Arabic
   word stays in one face and joins even where an earlier fallback has one of
   its letters. Otherwise it goes to what `FaceChoice.fallback` answers.
   Otherwise it stays in the base face, as `.notdef`.
3. A stretch of script-less clusters (spaces, digits, punctuation) that went
   to the base face joins the fallback run on both sides when that run's face
   has them. So `中文 名字` and `محمد علي` are one shaping each, and `Ann 中文`
   keeps its space in Inter.

A character with a script of its own never leaves the base face when the base
face has it. Every real Han face has Latin in it, and `Ann` beside a Han name
stays in Inter. This is CSS's per-character rule: the first font that has a
character draws it.

`Slot` did not grow. Its comment expected script splitting to be "the same
operation with more answers", but the answer here is a face, not a category,
so it is a `FaceRun` beside `TextRun`.

### A paragraph scans once and otherwise takes today's path

`Paragraph.shapeSegments`:

- No emoji face and no fallbacks: one shaping, unchanged.
- Fallbacks, and the face covers the whole text: one `coversAll` scan with no
  allocation, then one shaping, unchanged. A face whose `cmap` could not be
  read counts as covering everything, so it draws what it drew before.
- With an emoji face, `Itemizer.runs` first, unchanged. Each TEXT run the face
  does not cover is then split by coverage. Emoji are routed by what the text
  says and the rest by what the faces hold, in that order.

Each run becomes a `Segment` in its own font, and `concatenate` rescales it into
the base font's units, as ADR-0393 does for emoji. A fallback is opened at the
base font's size, so only the units-per-em ratio applies. Carets, selection,
hit testing, wrapping and `text-overflow` read the concatenated run and work
across a fallback run unchanged. `ParagraphFallbackTest.carets` checks every
offset of `Ann 中文名字` against a face on a 1024 grid, so a width that forgot
to rescale is off by half.

**Measurements stay the base font's.** Line height, ascent and decorations
come from the base font, as for emoji: `fontAt` treats a fallback run as part
of the text around it. A fallback glyph taller than the base face reaches
outside the line box. CSS does the same: the line box comes from the primary
font and a fallback may overhang.

**Bidi is the approximation it was.** Text that needs bidi is shaped with the
direction forced to LTR, every segment included. An Arabic run in a fallback
face is one shaping, in logical order. HarfBuzz still applies Arabic joining
in that direction (`Font.shape`), and the run is drawn mirrored, as before.
`isBidiApproximate()` still says so.

### No Noto artifact in this change

The toolkit does not ship fallback faces. Noto is SIL OFL 1.1, so the licence
is not the obstacle: an artifact would carry `OFL.txt` and a credit line as
`goldberry-emoji` does. The obstacle is size. On the development machine,
Noto Sans CJK Regular is a 19.5 MB `.ttc` per weight (and a collection is not
read; one region's `.otf` must be extracted), Noto Sans Arabic Regular is
244 KB, and Noto Sans Math Regular is 591 KB. A Han face is four times the
emoji artifact, and the right subset (SC, TC, JP, KR) depends on the
application's readers. Tessera's entry says it would ship its faces itself;
the API above is what it needs to. An opt-in `goldberry-fallback-arabic` or
`-math` artifact would be cheap, and it is left for when a second application
asks.

## Consequences

- **Names draw.** With a fallback registered, a name in Han, Arabic or Fraktur
  is letters, in every `Paragraph` a `Fonts` book shapes, which is every one the
  cascade resolves.
- **No fallback, no change.** An application that registers none, with no
  provider on the path, has `Fallbacks.NONE` on every font. Its paragraphs take
  the old path and shape the same glyphs. The one new cost is the `cmap` read
  when a face opens, about a millisecond or less per face per book.
- **A paragraph that does need fallbacks pays** a `BreakIterator` pass over
  the uncovered runs, an itemizer list, and one more shaping per run. This
  happens once per distinct string, since `ParagraphCache` holds the result.
- **Kerning across a seam is lost**, as between a word and an emoji. The
  shaper also sees no context across a seam. That is harmless between scripts
  and is why runs are kept whole.
- **A base face with part of a script keeps that part.** A face with some
  Arabic letters draws those, and the fallback draws the rest, so joining
  breaks at the seam. That is the per-character rule. The stickiness above
  only keeps a word in the fallback it started in.
- **A text-presentation emoji without U+FE0F** (`❤`) is not sent to the emoji
  face by coverage. It goes through the fallbacks like any other character,
  and the emoji slot is unchanged. Making the emoji face the last coverage
  candidate would change what such text draws today. That is a separate
  decision.
- **`Offscreen` and `Studio` are not given a fallback list of their own.** A
  render that wants fallbacks passes a book opened with them,
  `Offscreen.fonts(Fonts)`.
- **The test face is a stand-in.** `:core` ships no Han and must not read
  system fonts, so `StandInFace` is Inter with a spliced `cmap` that claims
  Han, Arabic and Fraktur and draws them with Inter's letters, on a 1024 grid
  where the rescale is tested. The golden `fallback-names.png` therefore
  pins the routing and each segment's pen position, not what Han looks like:
  boxes on the first line, letters on the second.
- **Where the entry was wrong:** the itemizer does not route to a face. It
  splits, and the font's holder answers which face has what, as ADR-0393
  already divided the work. `covers()` is a hint and not the decision. A
  service of bytes became a service of `FallbackSource`s.
