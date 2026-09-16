# 329. Two more codecs: one fetched, one written

Date: 2026-09-16

## Status

Accepted. Closes `docs/gaps.md` G35a.

## Context

`Image.decode` handled PNG, JPEG and QOI, and handled them well: the format comes
from the bytes rather than from a name, premultiplied BGRA comes out, and the
decoder's handle is destroyed before it returns so an `Image` is a value that can
go in a cache (ADR-0283).

It handled those three because Blend2D is compiled with those three codecs and no
others. So a **WebP** — which is what most screenshot tools now write — and a
**GIF** both threw `ImageDecodeException`.

That mattered more than a missing format usually does, because it was the one
place a desktop client and a web service had to disagree about what a valid asset
is: a browser decodes all four, so a node written against browsers accepts all
four, and a client that could not had to narrow its intake set and refuse a WebP
with a sentence naming the format. An honest answer, and not the right one.

## Decision

**Sniff the bytes, and route the two the rasterizer does not know to two new
decoders — one linked, one written.**

```
ImageFormat.of(bytes)  ->  GIF   -> GifDecoder   (Java, in :core)
                           WEBP  -> Webp         (libwebp, in :natives)
                           else  -> the rasterizer, as before
```

`Image.decode` is unchanged for callers. The sniff reads at most twelve bytes,
decodes nothing, and returns `UNKNOWN` for anything it does not recognise — which
routes to the rasterizer, so a format Blend2D gains later needs no entry here. It
decides *routing*, not validity.

### Why the two are answered differently

Because they are not the same size of problem.

**WebP is VP8**, a video codec: intra prediction, a bool-coder entropy stage, a
DCT-like transform and a loop filter, plus a second lossless format sharing the
container. There is no version of writing that in Java that is a good idea. So the
superbuild fetches **libwebp** — BSD, pinned in the version catalog like every
other upstream (ADR-0035) — and builds its `webpdecoder` target only: the encoder,
the muxer, the animation demuxer and all nine command-line tools are switched off,
because Goldberry reads one still frame and writes PNG.

Three symbols are bound, with **no C glue**, which is §3.1's rule and what the
export list exists for: `WebPGetInfo`, `WebPDecodeRGBA`, `WebPFree`. Unlike
Blend2D's and HarfBuzz's, libwebp's headers mark the API `visibility("default")`
on GCC and Clang even in a static build, so it needs no entry in the
visibility fix-up ADR-0031 added.

**GIF is nine pages.** A palette, a handful of length-prefixed blocks, and LZW.
Taking a second native dependency for that would cost more than owning it — and
`:core` already owns a format outright: `image.png.PngEncoder`. So
`image.gif.GifDecoder` is Java, and sits beside it as the same kind of thing.

### The GIF decoder reads the first frame

An animated GIF is a still image with more frames after it, and this reads the
first one. That is a stop rather than an oversight: what an application does with
a GIF here is put a picture on a board, and animation is a scheduler, a
frame-disposal model and a clock, none of which belongs in a decoder. A GIF with
one frame — which is most of them — decodes exactly.

The frame is composited onto the **logical screen** the file declares, so an image
whose first frame is smaller than the canvas comes back the size the file says it
is with transparent pixels around it. An optimizer writes a partial first frame
whenever only part of the picture changed, and cropping would hand back an image
of a size the file never claimed.

Interlacing is honoured. A decoder that ignored it produces a picture that is
recognisably the right one with its rows shuffled, which is exactly the kind of
wrong that passes a size check — so there is a fixture for it.

### Both hand back `0xAARRGGBB`

Neither decoder premultiplies. `Image.ofArgb` does that once, for every caller,
and has since ADR-0283 — so the two new paths join the existing one at exactly the
place where "not premultiplied" stops being true, rather than each getting it
right separately.

### The clipboard's offered set moved with them

`Image.CLIPBOARD_MIMES` gains `image/webp` and `image/gif`. That set is a
statement about what can be decoded, so it moves when that does; a codec linked in
and unreachable from the one place a picture usually arrives would be half a
change. `ClipboardDataTest`'s "a type this toolkit cannot decode" case, which used
to be WebP, is now TIFF.

## Consequences

- **The ABI version goes to 11.** Three new exports change the shape of the
  surface, so a `libgoldberry.so` built before this refuses to load against this
  Java rather than failing later at the first `WebPDecodeRGBA`. ADR-0330 lands in
  the same bump.
- The published library grows by libwebp's decoder. `webpdecoder` is the three
  decode-side object libraries and nothing else, which is a few hundred kilobytes
  — the encoder, which is the larger half, is never built.
- `:natives` gains a `webp` package, sealed to `:core` like Blend2D's and Yoga's
  wrappers: an application calls `Image.decode` and names no type of that module.
  The `MemorySegment` never leaves the call, let alone the module.
- A **GIF decoder is now this project's to maintain**. That is the cost of not
  taking a dependency, and it is bounded: the format has not changed since 1989,
  and the test asserts the decoder against a PNG of the same picture rather than
  against a table somebody transcribed.
- An animated WebP does not decode. `WebPDecodeRGBA` answers null for one, which
  becomes an `ImageDecodeException` naming that as the usual reason. Reading the
  first frame of one needs `webpdemux`, which is a second library and is not built.
