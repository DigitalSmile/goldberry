# ADR-0694: An image decodes into memory the caller owns

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

`Image.decode` always allocates its own premultiplied BGRA buffer. A game
whose atlas pages are `R8G8B8A8_UNORM` textures copied every page into an
RGBA buffer and swapped red and blue texel by texel in Java: 16 MB a 2048 px
page, and the decoder's buffer was garbage straight after (Lantern's G-9,
here L-9). The entry asked for a format argument, or a decode into the
caller's memory.

`PixelFormat` has one entry on purpose: every backend's present reads it, and
an `Image` is what the rasterizer draws. A second format there would be a
second format everywhere.

## Decision

`Image.decodeInto` writes the decoded pixels into memory the caller owns, in
the channel order it names, and returns the size. No `Image` is made.

- **`ChannelOrder`**, `BGRA` or `RGBA`, names the bytes' order in memory.
  Both are premultiplied, eight bits a channel. `PixelFormat` keeps its one
  entry.
- **`PixelTarget(pixels, stride)`** is the memory: a direct buffer, rows from
  its position. `PixelTarget.packed(buffer, width)` for tight rows,
  `bytesFor(size)` for how much to allocate (no padding after the last row).
- **Two forms.** `decodeInto(bytes, order, size -> target)` asks for the
  memory once, with the decoded size, because a file's size is known only
  once it is decoded and a transfer buffer is sized from it.
  `decodeInto(bytes, destination, stride, order)` is the same into memory
  the caller already has. Either way the room is checked before a byte is
  written.
- **Every format.** PNG, JPEG and QOI go from Blend2D's buffer straight to
  the target. GIF and WebP are premultiplied from their decoders' `int[]`
  into the target a row at a time. The routing and the error messages are
  shared with `decode` through one package-private `Decoding`.
- **The swap is Java, in the copy that was already there.** The plan
  expected to bind Blend2D's pixel converter, which would have moved
  `libgoldberry`'s ABI. It is not needed: the Blend2D path already copied row
  by row through `MemorySegment`, and the swap now happens in that pass, over
  one reused `int[]` row that the JIT vectorizes. The native ABI stays 26.

## Consequences

- An RGBA page costs one pass over its pixels and no garbage, against a copy
  plus a texel-by-texel swap.
- `BlendDecodedImage.copyInto` gains a `swapRedAndBlue` overload in
  `:natives`.
- Tested by `DecodeIntoTest` (each format in each order against `decode` of
  the same bytes, padded rows left alone, the buffer's position, refusals
  before anything is written) and `BlendDecodeTest` (the swapped copy).
