# ADR-0044: One face, many sizes

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §6; [ADR-0034](0034-one-size-and-the-design-unit-crossing.md), [ADR-0037](0037-what-the-text-path-costs.md)

## Context

`docs/ARCHITECTURE.md` §6 says "one font buffer feeds both `hb_face_t` and
`BLFontFace`". [ADR-0034](0034-one-size-and-the-design-unit-crossing.md) built
the thing that was supposed to and did not: a `Font` owned a `ShapedFont` and a
`BlendFont` over the same bytes, and **each of them copied those bytes**, because
each library owns its own memory. The guarantee behind the sentence held — the
two copies are byte-identical, so their metrics cannot disagree — but the memory
did not.

Inter is about a megabyte and a half. A `Font` was two copies of it, and there is
a `Font` per size, so the showcase's two sizes cost six megabytes of the same
outlines. A real application with a title, a body, a caption and a code face is
into double figures for a handful of files.

The cost is not only memory. `Font.bundled` measures at **681 µs** — a parse of
the file by each library, plus two copies — and it is paid per size.

Nothing above depended on it staying that way, which is why ADR-0034 wrote it
down and moved on. What makes it worth fixing now is that
[ADR-0043](0043-icons-are-stroked-paths.md) has just added a *second* kind of
per-size object, and the pattern was about to be repeated rather than fixed.

## Decision

Split the typeface out. `FontFace` in `:core` is a typeface — everything about a
font except the size — and `Font.on(face, size)` is a size over one.

The split is not arbitrary; it follows what the two libraries already do. Both
HarfBuzz and Blend2D model a font in three layers — the file's bytes, the
typeface in them, and the typeface at a size — and the size is only ever on the
third. So:

| Object | Where it lives now | Why |
|---|---|---|
| `hb_blob_t`, `hb_face_t`, `hb_font_t` | `FontFace` | All three, not just the face: Goldberry never sets a scale on the shaping font, so a shaping result is in design units and correct at every size (ADR-0034) |
| `BLFontData`, `BLFontFace` | `FontFace`, via the new `BlendFontFace` | Size-independent, and the expensive two |
| `BLFont` | `Font` | This *is* the size — it carries the font matrix |
| `ShapingBuffer`, `BlendGlyphBuffer` | `Font` | Scratch space, cheap, and reused per call |

The whole shaper moving to the face is the part worth noticing. It is only
correct because ADR-0034 put the size on Blend2D's side alone; had the shaper
been scaled, it would have had to be per-size and this would have saved the
Blend2D half only.

**Faces are owned explicitly, not cached globally.** `FontFace.bundled(UI)`
returns a face the caller owns and closes, and the natural scope is the window
that draws with it:

```java
try (var face = FontFace.bundled(BundledFont.UI);
        var title = Font.on(face, 18);
        var body = Font.on(face, 14)) {
```

`Font.bundled(font, size)` and `Font.of(bytes, size)` still exist and still parse
a face of their own, closing it with the font. That is the right shape for one
size and the wrong one for four, and it is said so in their javadoc.

## The numbers

`./gradlew :core:benchmark`, `TextBenchmark`, on linux-x64:

| | median |
|---|---|
| `Font.bundled` — parse and copy, per size, as before | 680.9 µs |
| `FontFace.bundled` — the parse, once | 429.9 µs |
| **`Font.on` — another size over a face that exists** | **4.4 µs** |

A second size went from ~681 µs to 4.4 µs, and from two copies of the file to
none. Four sizes of Inter cost three megabytes rather than twelve.

(The first two rows do not quite add up — 430 + 4.4 is not 681 — and the reason
is measurement, not accounting: they run at 200 iterations against `Font.on`'s
2000, so they are less warmed. The number that matters is the third, and it is
the well-warmed one.)

## Alternatives considered

- **A process-wide cache keyed by the font bytes.** The obvious reading of
  "shared face cache", and rejected on lifetime. These objects are
  thread-confined — `ShapedFont` and `BlendFont` both check their owner — so a
  process-wide cache would have to be per-thread, and a `ThreadLocal` holding
  native memory has no hook that runs when the thread ends. It would be a leak
  per thread that ever built a font, in exchange for saving the caller from
  naming a variable.
- **Reference-count the face**, so `Font.close()` releases it and the last one
  out frees it. Sound, and rejected as the wrong default: it makes the ordering
  invisible rather than correct, and the failure mode of getting it wrong —
  freeing a face another font is reading — becomes a use-after-free instead of a
  compile-time-visible scope. Explicit ownership is what every other
  native-backed object in the toolkit already uses.
- **Key a cache on the `byte[]` by identity.** Cheap and quietly useless:
  `BundledAssets.font()` returns a fresh array each call by design, so the
  bundled faces — the whole point — would never hit.
- **Hash the bytes to key a cache.** A megabyte and a half of hashing per lookup
  to avoid a variable. Rejected on that alone, before the lifetime problem.
- **Leave it.** Defensible while there were two sizes. Rejected because
  ADR-0043 had just added per-size icons, and the shape of "one expensive parse
  per size" was about to become the house style.

## Consequences

- **A second size is effectively free** — 4.4 µs and no copy of the file — which
  is what makes a design system with five text sizes affordable at all.
- **A new ordering rule.** A face must outlive every font over it. Nothing
  enforces it, deliberately (see the alternatives), so it is documented on both
  classes and the scoped form is what makes it automatic. `FontFaceTest` asserts
  both halves: closing one size leaves the others shaping, and a font that
  parsed its own face still closes it.
- **`BlendFont` no longer owns its bytes.** `BlendFontFace` does, and
  `BlendFont.on(face, size)` borrows. `BlendFont.fromBytes` still works and now
  makes a private face it closes — so nothing outside `:core` had to change.
- **`Font.face()` is public**, which means an application can hold a font and
  reach the face it came from. That is deliberate: it is how a caller that was
  given a `Font` adds a second size without being given the face too.
- **The §6 sentence is now true.** "One font buffer feeds both `hb_face_t` and
  `BLFontFace`" is still not literally true — each library still copies — but the
  claim it was making, that a typeface is loaded once, is.
- **Still two copies per face.** HarfBuzz and Blend2D each own their memory and
  neither takes a borrowed buffer for font data the way Blend2D does for pixels
  ([ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)). Halving that would mean
  giving one of them a pointer into the other's arena and reasoning about which
  frees first, for 1.5 MB per family. Not worth it, and written down so it is not
  rediscovered as a bug.
