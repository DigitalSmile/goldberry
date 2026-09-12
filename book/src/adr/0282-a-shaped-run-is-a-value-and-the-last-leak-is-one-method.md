# 282. A shaped run is a value, and the last leak is one method

Date: 2026-09-12

## Status

Accepted, and **the seal is one method short** — stated here rather than left as
an absence. Closes most of `docs/gaps.md` G14.

## Context

[ADR-0280](0280-natives-exports-to-core-and-to-nobody-else.md) sealed Yoga's
three packages and recorded that `-Xlint:exports` under `-Werror`, with
`requires transitive` removed, names every remaining leak by file and line. It
named eleven sites in three APIs, all in the text stack, none with a consumer
outside `:core`.

## Decision

`GlyphRun` and `TextDirection` are mirrored into `:core` as `text.ShapedRun` and
`text.TextDirection`, and HarfBuzz's two packages are sealed:

```java
exports io.github.digitalsmile.goldberry.natives.harfbuzz to
        io.github.digitalsmile.goldberry.core;
exports io.github.digitalsmile.goldberry.natives.harfbuzz.enums to
        io.github.digitalsmile.goldberry.core;
```

Checked by compiling a module that imports `GlyphRun` and watching javac refuse
it, the same way ADR-0280's Yoga seal was checked.

`ShapedRun` is six `int[]` and nothing else — no foreign memory, no lifetime,
nothing to close. `Font.shape` copies out of the shaper's run in one pass; the
copy is a doubling of something that happens once per *text change* rather than
once per frame, and the alternative is handing an application a `:natives` class.

`TextDirection` keeps only `LTR` and `RTL`. The shaper's vertical directions are
deliberately absent: nothing in the toolkit lays out a vertical line, and an
enumerator that can be named and would then be dropped downstream is worse than
one that cannot be named.

## What is left, and why it is a decision rather than a transcription

One method:

```java
public void drawGlyphs(double x, double baseline, BlendFont font, BlendGlyphBuffer glyphs, int argb);
```

`Font.draw` is its only caller. It stages a run's glyphs into a buffer it owns
and hands both to the frame.

The other two leaks were **values**, and a value can be mirrored. These are
**handles**, and the difficulty is not transcription but ownership:
rasterizing a glyph needs a context, a font and a staged buffer; `paint` owns the
first and `text.font` owns the other two. One of them has to cross, and within a
single module Java offers nothing between package-private and public — so
whichever crosses, crosses in a signature an application can read.

Three ways out, none of them free:

1. **Move the native font into `paint`.** `paint` gains a pen that owns the
   `BlendFont` and the buffer; `text.font` becomes shaping and metrics, and
   `Frame.drawGlyphs` goes package-private. The cleanest end state, and it moves
   font *creation* — which today is `FontFace`'s, and which the fallback chain
   and the paragraph cache are both built on.
2. **Wrap the handle in an exported opaque type.** Does not work: a public method
   of an exported type may not name a type from a package that is not exported
   either, so the wrapper has to be exported and then its accessor cannot be
   package-private. It relocates the warning.
3. **Leave it.** One documented method, `blend2d` stays exported, and `:core`
   keeps `requires transitive`.

This ADR takes (3) for now and records (1) as the answer, because moving font
ownership is a change to the text stack's shape and deserves its own decision
rather than being improvised at the end of a sealing pass.

## Consequences

- **Three of `:natives`' five families are sealed** — `yoga`, `yoga.style`,
  `yoga.measure`, `harfbuzz`, `harfbuzz.enums`. `blend2d` and `sdl` are not:
  `blend2d` for the method above, and `sdl` because nothing has looked at it yet.
- **`:core` still declares `requires transitive`**, and the comment in its
  descriptor says which method is why. Removing the word is how the remaining
  work is enumerated; it costs one build.
- **`ShapedRun` is not a record**, deliberately: a record over six arrays hands
  them out through its accessors for anyone to write into, and a shaped run is a
  value a paragraph cache returns repeatedly. `ShapedRun.of` copies.
- **`Paragraph.glyphs()` and `Font.widthOf` still have no callers.** They were
  public before and stay public; deleting unused API is a separate question from
  sealing a module, and answering both at once would have hidden one in the
  other.
- **No golden image moved.** The shaping is identical; only the type the result
  is carried in changed.

## Alternatives considered

- **Mirror the handles as well.** They are not values. A `BlendFont` is a native
  allocation with a thread and a lifetime, and a "mirror" of it is a wrapper —
  which is option (2), which does not work.
- **Delete `Frame.drawGlyphs` and inline it into `Font`.** `Font` would need the
  `BlendContext`, which is the same crossing in the other direction and a worse
  one: the context is the frame's most dangerous object.
- **Keep `GlyphRun` and seal nothing.** The two mirrors cost one copy per text
  change and bought a whole family. Waiting for the third would have meant
  shipping neither.
