# 382. A GIF has the frames after the first

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "One frame only" for GIF; the animated
WebP half of that entry stays open and is narrower now.

## Context

ADR-0329 wrote a GIF decoder in Java and stopped at the first frame, on purpose:
"an APNG, an animated GIF or an animated WebP is a sequence and a clock, and
would be its own record: a disposal model, a delay per frame and something to
drive it."

That sentence is the design. The reason to build it now is that all three pieces
are cheap where they belong and expensive where they do not: the disposal model
is thirty lines *inside* the decoder and impossible outside it, the delays are in
the file, and the clock is the caller's — which is exactly how every transition
in this toolkit already works.

The disposal model is the part that cannot be skipped. A GIF frame is usually a
**patch**, and what is under it depends on what the frame before it asked for
when it left: keep the canvas, clear the patch to transparent, or put back what
the patch covered. A decoder that ignores it draws every frame at once, which is
the classic way an animated GIF renders wrong.

## Decision

**`GifDecoder.decodeAll` reads the sequence; `Animation` says which frame is
showing; nothing here holds a clock.**

- `GifDecoder.Sequence` is frames, each already composited onto what the frame
  before it left, each with its delay, plus the loop count from the NETSCAPE
  extension — 0 for ever, which is that extension's own spelling.
- All three disposal methods are implemented. "Restore to previous" captures the
  canvas *before* the frame is drawn, because that is what previous means.
- A delay below 20ms becomes 100ms, which is what Firefox and Chromium both do
  with a file that asks to run as fast as possible.
- `image.anim.Animation` is the value: `at(elapsedMillis)` is the only question
  it answers, and the elapsed time belongs to whoever is drawing — a `canvas`
  painter reading the frame clock, an offscreen render stepping a virtual one, a
  test asking for exactly 240ms.
- **Every image is an animation**: `Animation.still(image)`, and
  `Image.decodeAnimation` answers one for a PNG as readily as for a GIF, so a
  caller drawing either needs no branch.
- A finite animation stops on its **last** frame rather than vanishing or
  restarting, which is what a GIF that has played its three loops looks like.

## Consequences

- `Image.decode` is unchanged: it still reads the first frame, which is what a
  picture on a board wants and what most GIFs contain.
- The `image` widget still shows that first frame. Playing one is a widget
  decision — §1's row for `image` asks for fits, DPI variants and states, and
  says nothing about animation — and it now has something to play. Today an
  application animates one on a `canvas` in two lines.
- An **animated WebP** is still one frame, and the reason is unchanged: the
  frames live in a `webpdemux` the superbuild does not build (ADR-0329). The
  entry is narrower rather than closed.
- The decoder is bigger and is still one file with no dependencies. The sequence
  path and the single-frame path share the patch model, so a disposal bug cannot
  hide in one of them.
