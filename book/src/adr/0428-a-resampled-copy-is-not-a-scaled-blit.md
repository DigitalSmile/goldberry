# 428. A resampled copy is not a scaled blit

Date: 2026-09-19

## Status

Accepted. Adds the one image operation the binding could already perform and did
not expose, under the ownership rule
[ADR-0283](0283-an-image-is-a-value-and-the-decoder-is-the-one-thing-blend2d-allocates.md)
set for the decoder.

## Context

`book/src/TODO.md` recorded the absence and the reasoning for it:

> **No `Image.scaled(...)`.** Scaling happens at the blit, which is where the
> destination size is known. A resampled *copy* — for a thumbnail written to disk
> — is a different operation and would need a filter argument that
> `bl_image_scale` has and nothing has asked for.

The first sentence is still right and is the reason this method is easy to
misuse. Drawing an image smaller does not go through here: `Frame.drawImage`
resamples on its way onto the surface through `bl_context_blit_scaled_image_d`
and keeps nothing, which is what a picture on screen at a display scale wants
(ADR-0157). Going through `scaled(...)` first would allocate a buffer to throw
away a frame later.

What the entry then treats as a reason not to build it — "nothing has asked for
it" — is the argument for building it now. `bl_image_scale` is in the library
that already ships. Every other thing an application might want to do to an
image's pixels is either here (decode, encode, read a pixel, blit) or genuinely
absent from the binding. This is the one operation the rasterizer can do and the
toolkit cannot, and the three cases that want it are ordinary: a thumbnail
written to a file, an over-sized paste cut down before it enters a document, an
icon resampled once and drawn a hundred times.

### The open question was the filter

```c
BL_API BLResult bl_image_scale(BLImageCore* dst, const BLImageCore* src,
                               const BLSizeI* size, BLImageScaleFilter filter);
```

The filter argument is **mandatory in C**. The choice exists whether or not a
caller is offered it; hiding it means making it on their behalf, silently, once,
for every use.

The temptation is to pick one, call it "good", and be done. The reason not to is
that Blend2D's filters are not ordered by quality — they are ordered by what they
assume about the image:

- making a photograph **smaller** wants as much of the source averaged in as
  possible, because a source pixel never consulted is detail discarded. Lanczos
  consults the most;
- making a 16×16 icon **twice as big** wants the opposite: the sixteen pixels it
  already has, doubled, with nothing invented between them. Nearest is the only
  filter here that invents no colours, and it is the only one that is not simply
  a worse version of the others.

No single default is right for both, and the mistake is silent in both
directions: a nearest-neighbour photograph looks like a bug somebody files, and a
Lanczos icon looks like a slightly soft icon that nobody does.

## Decision

**Bind `bl_image_scale`. Expose it as `Image.scaled(width, height)` with a named
default, and `Image.scaled(width, height, Resampling)` for the case that is not
the default.**

**The filter is an enum the caller may pick, not a knob they must.** Both halves
matter. Offering only the default would make the wrong answer unreachable for
upscaled pixel art; requiring the argument would put a decision in front of every
caller whose case is the ordinary one. `Resampling` is a `:core` enum of four
values mapped onto `BlendImageScaleFilter` by a `switch` — not by ordinal, so a
`:core` type is not pinned to the order of a `:natives` one.

**The default is `LANCZOS`,** chosen for the operation the method exists for
rather than as a general "best": a thumbnail is a downscale, and a downscale
wants the widest neighbourhood. `BICUBIC` is in the enum beside it precisely
because that argument reverses when the factor goes the other way.

**`BL_IMAGE_SCALE_FILTER_NONE` is not bound.** It is the enum's zero value —
the absence of a filter — rather than one of them, and a constant nobody can
usefully pass is the same dead weight `BlendStrokeJoin` refuses for the two
miter variants it leaves out.

**The result is a value, like every other `Image`.** This is the second place in
the toolkit where Blend2D owns pixels, after the decoder, and it is held to
ADR-0283's discipline word for word: `BlendScaledImage` destroys the destination
on the call that made it, once its rows are copied into a `PixelBuffer` Java
owns. The exception is weaker than the decoder's — a decode *must* allocate
because the size of a PNG is inside the PNG, whereas a resample's size is the
caller's own argument — but it is forced all the same, because `bl_image_scale`
resizes the destination itself and has no form that writes into a buffer
somebody else owns.

**Asking for the size it already is returns `this`.** An image is a value, so
there is nothing a copy could be used for that the original cannot, and a caller
normalising a batch to one size should not pay a buffer for the ones that
already are it.

### What is resampled is premultiplied

The source is premultiplied BGRA, which is what every buffer in this toolkit is,
and Blend2D gives the destination the source's format — so no conversion happens
and none is asked for. That is also the **correct** space to filter in: averaging
straight alpha weights a fully transparent pixel's colour as though it were
there, which is what puts a dark halo around a resampled cut-out.

The cost is named rather than hidden. A filter with negative lobes — Lanczos,
bicubic — can overshoot at a hard edge and leave a channel above the alpha it is
premultiplied by, which is not a representable colour. `Image.argb` clamps on the
way out, as it already did for the rounding premultiplied storage costs.

## Consequences

- `Image.scaled(int, int)` and `Image.scaled(int, int, Resampling)` exist, with
  `Resampling` and `ImageScaleException` beside them in
  `io.github.digitalsmile.goldberry.image`. The exception is separate from
  `ImageEncodeException` because the two say different things to an application:
  an encode that refuses WebP's size limit will refuse again, and a resample that
  could not allocate may not.
- The ABI is **14**, shared with
  [ADR-0427](0427-the-shadow-is-cut-out-of-its-box.md), which lands in the same
  build. `bl_image_scale` joins the export list and the four
  `BL_IMAGE_SCALE_FILTER_*` enumerators join the layout table — positional
  values, so one inserted upstream shifts the rest and resamples with a filter
  nobody chose while returning `BL_SUCCESS`.
- `BlendScaleTest` pins the binding at the level where it can be wrong silently:
  that the `BLSizeI` crosses the right way round (an 8×2 is not a 2×8), that
  `NEAREST` doubles a checkerboard into exact blocks of four and invents no
  colour, and that `BILINEAR` on the same input does not — which is what proves
  the filter argument reaches the library rather than being ignored.
  `ImageScaledTest` covers the value half: a flat colour survives a downscale
  exactly, alpha survives it, the result encodes to a PNG, and scaling twice
  needs no lifetime management at all.
- **Nothing in the toolkit calls it.** That is the state the entry described and
  it is unchanged: no widget resamples an image, and `Frame.drawImage` still
  should not. This is public API for applications, and the javadoc opens by
  saying which of the two operations a reader probably wants.
- An `Image.cropped(...)` is now conspicuous by its absence in a way it was not
  before. It needs no new symbol — `bl_context_blit_image_d` already takes a
  source rectangle (ADR-0283) — and is a pure-Java copy besides. It is not built
  here because nothing has asked, which is an argument this record has just spent
  four paragraphs declining to accept; the difference is that cropping adds no
  capability the binding uniquely has.

## What to write instead

An image being drawn at a size is `frame.drawImage(image, x, y, width, height)`,
which keeps no pixels. `Image.scaled(...)` is for pixels that outlive the call —
and a caller resampling *up* names `Resampling.NEAREST` or `BICUBIC` rather than
taking the default, which is tuned for the way down.
