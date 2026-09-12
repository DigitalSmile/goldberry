# 283. An image is a value, and the decoder is the one thing Blend2D allocates

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G4, and unblocks G5 (offscreen render and PNG
encode) and G7 (clipboard beyond text), both of which named a type that did not
exist.

## Context

G4: *"`Frame` can composite a Goldberry `Layer` and nothing else. `BlendImage` is
in `:natives` and exposes no decode or encode entry point at all, so there is no
supported way to turn PNG bytes into something drawable."*

All of that was true. What it did not say is that the ingredients were already
compiled in: `CMakeLists.txt` builds Blend2D with its **built-in PNG, JPEG and
QOI codecs**, and has since M0. The export list simply never named them, because
nothing had asked.

So the shape of this decision is not "how do we decode an image" but "who owns
the pixels afterwards", and the answer comes from the two leaks closed before it.
ADR-0277 and ADR-0282 both ran into the same wall from opposite sides: a **value**
can be mirrored into the toolkit's own vocabulary, and a **handle** cannot,
because someone has to own it and say when it dies. `Path` and `ShapedRun` became
values and the leak closed; `Frame.drawGlyphs` is still open precisely because a
font and a glyph buffer are handles.

An image could have been either.

## Decision

### An image is a value

```java
package io.github.digitalsmile.goldberry.image;

public final class Image {
    public static Image decode(byte[] bytes);       // PNG, JPEG, QOI
    public static Image decode(ByteBuffer bytes);
    public static Image decode(Path file);
    public static Image ofArgb(int width, int height, int[] argb);

    public PhysicalSize size();
    public PhysicalRect bounds();
    public int argb(int x, int y);
    public PixelBuffer pixels();                    // read-only
    public byte[] encodePng();
}
```

**Not `AutoCloseable`**, which is the part G4's sketch assumed it would have to
be. Decoding allocates through Blend2D, copies the result into a `PixelBuffer`
Java owns, and destroys the handle before `decode` returns. The pixels are a
direct `ByteBuffer` and the collector owns them, like every other buffer here.

The cost is one copy per decode. What it buys is an image that can go in a field,
a record, a cache or a document model with no lifetime travelling beside it —
which is what the showcase does, and what an application holding a board's worth
of images would otherwise have had to get right.

### It is its own package, not a class in `paint`

G4 proposed `paint.Image`. Three parts of the toolkit want this value and only
one of them draws: a frame draws one, an offscreen render produces one (G5), a
clipboard carries one (G7). `paint` already depends on `render`, so `paint.Image`
would have made `render.Clipboard` depend on the paint package in order to name
the thing it holds. `io.github.digitalsmile.goldberry.image` is the neutral home,
and `image.png` beside it holds the encoder for the reason `paint.geom` is its own
package: it is an algorithm over a value, testable without an image in front of
it.

### Drawing it is `Frame`'s, in four overloads

```java
frame.drawImage(image, x, y);                                   // natural size
frame.drawImage(image, x, y, width, height);
frame.drawImage(image, x, y, width, height, alpha);
frame.drawImage(image, source, x, y, width, height, alpha);     // the crop
```

**Natural size means one image pixel per *device* pixel**, not per logical unit:
a 96×64 image covers 96 logical points at 100% and 48 at 200%, and is crisp at
both. That is ADR-0157's arithmetic — the bug that drew every faded subtree at
twice its size on a Mac — applied before it could happen again, and the scale
sweep over the showcase golden is what says it holds.

The crop's source rectangle is `render.model.PhysicalRect`, a new four-int
rectangle beside `PhysicalSize`. Two coordinate spaces meet in that overload and
they are deliberately unrelated: the source is *which pixels*, in the image's own
grid, and the destination is *where they go*, in logical units. Relating them
would decide for the caller whether a crop is stretched or shown at size.

### Decode is Blend2D's; encode is `java.base`'s

The export list gains **three** symbols — `bl_image_init`,
`bl_image_read_from_data` and `bl_image_convert` — and not one more.

Decoding is Blend2D's because a JPEG decoder is thousands of lines nobody should
write twice. Encoding a PNG is a `Deflater`, which is already in `java.base`,
wrapped in four chunks and a CRC — so `image.png.PngEncoder` is 150 lines of Java
and `bl_image_write_to_data`, `bl_image_codec_init_by_name`,
`bl_image_codec_destroy` and the four `bl_array_*` calls an encode would have
needed never cross the boundary at all.

That is ADR-0278's reasoning a second time. A dash was Goldberry's arithmetic
rather than the rasterizer's; so is a chunked, deflated byte stream. It also means
the offscreen render this unblocks can turn a frame into a PNG with no native
call in the encode.

### The decoder is the one exception to "Blend2D never allocates our pixels"

ADR-0031 established the rule and `BlendImage`'s javadoc states it: Goldberry
hands Blend2D the buffer, never the other way round, which is why the only
constructor bound was the external-data one.

A decoder cannot work that way. **The size of a PNG is inside the PNG**, so
nothing on the Java side could have allocated before the decoder said how much.
`BlendDecodedImage` is where the rule bends, and it bends for exactly the length
of one `try` block: init, read, convert to premultiplied BGRA, copy the rows out,
destroy. Nothing outside that class ever holds a Blend2D allocation, and
`Image.decode` is the only caller.

Converting at decode rather than asking at the blit matters more than it looks: a
PNG with no alpha channel decodes to `XRGB32`, whose alpha byte is undefined
rather than opaque. An unconverted image blits as whatever that byte happened to
be, which on the test image was invisible.

### A failed decode is `ImageDecodeException`

Bytes that are not an image are a *normal branch* — a pasted screenshot, a
dropped file, a field written by an older version of an application — so it is
catchable, and the type caught must be Goldberry's. An application catching
`BlendException` would be the `:natives` boundary leaking through a `catch`
clause instead of a signature, which is the whole subject of ADR-0280. The
rasterizer's report is kept as the cause. A file that cannot be *read* is a
different question and is `UncheckedIOException`.

## Consequences

- **The export list is three symbols longer**, and `Layouts` has a `BL_RECT_I`
  row — the first `BLRectI` to cross in either direction, for the crop's
  `img_area`. The C probe had reported that struct since M0, so the row was
  verified on all four targets before any Java named it.
- **`Blend2dImage.ImageData` carries the size now**, because an image the decoder
  filled in was never told one.
- **`Image.pixels()` hands out a read-only `PixelBuffer`**, and blitting from it
  works: a read-only direct buffer still has an address, and Blend2D only reads.
  The alternative was handing out the writable buffer and asking callers not to
  write to it, which makes "an image is a value" a convention rather than a fact.
- **`ofArgb` is unpremultiplied and so is `argb(x, y)`**, matching every other
  colour in the toolkit. The round trip is exact for opaque and fully transparent
  pixels and within a level otherwise — a property of premultiplied storage, not
  of the methods.
- **The showcase's Canvas screen has a fourth card** drawing one decoded PNG four
  ways from a single static field, which is the claim "an image is a value" made
  visible. Its golden moved; nothing else's did.
- **The golden harness keeps its own PNG writer** in `core/src/testFixtures`
  rather than calling the shipped one. A golden image written *and* read by the
  code under test proves nothing about either half; the harness's reader is the
  independent check on the encoder, and `PngEncoderTest` is the round trip
  between them.
- **No widget draws an image yet.** An `img` widget — with `object-fit`, a
  loading state and a cache — is a catalogue entry and is not this. What exists
  is the primitive, and a `canvas` is how an application uses it today.
- **Animated formats are absent, not forgotten.** `bl_image_read_from_data`
  decodes one frame; an APNG or a GIF is a sequence and a clock, and would be its
  own decision.

## Alternatives considered

- **`Image` as a handle over a `BLImage`.** No copy per decode, and every image in
  an application acquires a lifetime: closed twice, closed too early, or held open
  by a document that outlives the window. ADR-0282 is the record of how much
  harder a handle is than a value, and nothing about an image requires it.
- **Encode through Blend2D's codecs too.** One mechanism instead of two, and JPEG
  and QOI encoding for free — at the cost of seven more exported symbols, a
  `BLArray` and a `BLImageCodec` object family in `:natives`, to write bytes
  `java.util.zip` already writes. If JPEG encoding is ever wanted, that is a
  decision with a reason behind it rather than a side effect of this one.
- **Promote the golden harness's PNG writer instead of writing a second one.** It
  would have removed a duplication and removed the harness's independence with it
  — see the consequence above.
- **Decode lazily inside the first draw.** Then a decode failure surfaces during
  a paint pass, where there is nothing sensible to do with it, on a thread that is
  trying to hit a frame budget.
- **Let the crop clamp silently, as Blend2D does.** A source rectangle that runs
  off the image draws a smaller picture in the wrong place and reports nothing.
  Crops come from documents edited elsewhere, which is exactly the case that
  should be told rather than approximated.
