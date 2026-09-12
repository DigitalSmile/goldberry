# 290. The pen belongs to the rasterizer

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G14, and finishes ADR-0280.

## Context

G14: *"One method remains: `public void Frame.drawGlyphs(double x, double
baseline, BlendFont font, BlendGlyphBuffer glyphs, int argb)`. `Font.draw` is its
only caller and nothing outside `:core` touches it. The other two leaks were
**values**, and a value can be mirrored; these are **handles**, and the
difficulty is ownership rather than transcription."*

The entry's own instruction for enumerating the remainder was to delete one word
from a module descriptor and read the compiler's answer. Doing that — dropping
`transitive` from `:core`'s `requires io.github.digitalsmile.goldberry.natives` —
produced exactly two warnings, both on that one line. `Font.shape` and
`Paragraph.measureFunction` had already been fixed by ADR-0282 and ADR-0279; the
descriptor's comment claiming eleven sites was stale.

So the remaining leak was one method, and the obstacle was ownership. Rasterizing
a glyph needs a context, a font and a staged buffer. `paint` owned the first;
`text.font.Font` owned the other two. Within one module Java offers nothing
between package-private and public, so the join had to be public — and being
public, it put two `:natives` types in the toolkit's API and kept `:natives`
readable by every application that requires `:widgets`.

## Decision

### The pen moves to `paint`, as the entry proposed

Two new types, both in `io.github.digitalsmile.goldberry.paint`:

```java
public final class GlyphFace implements AutoCloseable {   // a typeface, to the rasterizer
    public static GlyphFace of(String name, byte[] data);
}

public final class GlyphPen implements AutoCloseable {    // that face at one size
    public static GlyphPen on(GlyphFace face, double size);
    public void draw(Frame frame, double x, double baseline, ShapedRun run, int from, int to, int argb);
    public double ascent(); public double descent(); public double lineHeight(); public double size();
}
```

Neither says anything native. One is made from a `byte[]`, the other takes a
`ShapedRun` — a value, in the face's own design units, which ADR-0282 made. The
`BlendFontFace`, `BlendFont` and `BlendGlyphBuffer` are private fields, and the
handle passes between them through a package-private `GlyphFace.handle()` that
only `GlyphPen` can call.

`Frame.drawGlyphs` is package-private now, with `GlyphPen` as its one caller.

### `text.font` keeps what it is about

`FontFace` holds a `GlyphFace` where it held a `BlendFontFace`; `Font` holds a
`GlyphPen` where it held a `BlendFont` and a `BlendGlyphBuffer`, and `Font.draw`
is three lines of delegation. Everything that makes `text.font` interesting —
shaping, the fallback chain, the paragraph cache, the metrics, the design-unit
invariant — stayed exactly where it was.

The loop that copies a shaped run into the rasterizer's staged buffer moved with
the buffer. It belongs beside the thing it fills.

### `:core` drops `transitive`, and Blend2D is sealed

```java
requires io.github.digitalsmile.goldberry.natives;                      // :core
exports io.github.digitalsmile.goldberry.natives.blend2d to
        io.github.digitalsmile.goldberry.core;                          // :natives
```

Yoga and HarfBuzz were qualified by ADR-0280 and Blend2D was not, for exactly the
reason above. All three are now, which is what that record said it was doing and
could only do two thirds of.

**The compiler is the enforcement, not the comment.** `-Xlint:exports` under
`-Werror` fails the build at the offending method the moment a `:natives` type
reappears in an exported signature, and an application module naming a
`BlendPath` does not compile. `ExportedSurfaceTest` asserts both halves: that
none of the eight wrapped packages is exported unqualified, and that each is
exported to `:core` and to nobody else — so an export silently deleted fails too.

## Consequences

- **The foreign boundary is now the module graph, entirely.** `docs/ARCHITECTURE.md`
  §3.1 said raw `MemorySegment` must never escape `:natives`; ADR-0280 added that
  no *type* of `:natives` may appear in a signature an application can read. That
  is true for the first time.
- **No golden image moved, and no behaviour changed.** The glyph-copy loop runs
  where it always did, one call deeper. `Font.draw`'s signature, `Paragraph`'s
  painting and the metrics are untouched.
- **`GlyphFace` and `GlyphPen` are public**, which is more surface than the leak
  they replaced. That is deliberate: a custom widget that wants to draw a shaped
  run at a size — a terminal, a music stave, a diff view with its own layout —
  now has a supported way to, and it is the way the toolkit's own text stack
  draws. The alternative was package-private types plus a friend accessor, which
  is the same coupling with a worse name.
- **What is still exported unqualified is SDL's wrappers**, and that is right: an
  application legitimately names a `BackendWindow`, a tray and a cursor. What
  keeps those safe is the older check — they traffic in `SdlWindowHandle`, never
  in an address.
- **`:core`'s test fixtures still reach `BlendImage`.** `RendererRequirement`
  probes whether the rasterizer loads at all, and compiles on the class path
  where module rules do not apply. It is a test-only path and not a hole in the
  shipped graph, but it is the one place the seal is a convention again.

## Alternatives considered

- **A friend accessor, the JDK's `SharedSecrets` pattern.** `paint` publishes a
  registration point, `text.font` fills it in at class-init, and the handle
  crosses through an interface of Java types. It works, it is invisible in the
  API, and it replaces a compiler-checked boundary with an initialisation-order
  one. The leak was ownership; moving the owner is the fix for ownership.
- **Leaving `drawGlyphs` public and qualifying Blend2D anyway.** The export would
  compile and the *method* would be uncallable — a public method in a public
  class that no application can name the parameters of. A worse state than the
  one it replaced, because it reads as supported.
- **Moving all of `Font` into `paint`.** More than the leak needed. Shaping is
  not painting, and `text` is where the fallback chain, the cache and the
  design-unit invariant belong.
