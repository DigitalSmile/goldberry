# ADR-0584: A background layer may be a picture, sized and tiled, and the stylesheet reads it through the image cache

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-025, GB-024),
  [ADR-0579](0579-an-image-shows-a-region-of-a-sheet-from-the-sheets-one-decode.md),
  [ADR-0585](0585-a-border-may-be-a-picture-cut-in-nine.md)

## Context

`background` and `background-image` read the four gradient functions and
`none`. A `Background` was a colour, gradient layers and a position: no
`url()`, no `background-size`, no `background-repeat`. The downstream wants a
leather grain and a cloth weave tiled under panels of any size.

A picture in a stylesheet raises a question a gradient does not: where its
pixels come from. The cascade lives in `:core`, and `ImageSource` and the
bounded `ImageCache` that the `image` widget decodes through live in
`:widgets`, which `:core` cannot name. The same question comes back for
`border-image`, so it is answered once here.

## Decision

### A CSS image value and a service for its pixels

**`dev.goldberry.css.image.CssImage`**, a sealed interface over
**`CssImage.Url(href, alpha)`** and the existing **`GradientLayer`**, which
now extends it. The tokenizer already made `url("…")` a function and a
string; `CssImage.url(tokens)` reads that, and only that — the unquoted form
is still not tokenized. The address is checked when the value is made, so a
malformed `#xywh=` drops the declaration rather than failing at paint.

**`StyleImages`**, a service interface: `CompletableFuture<Image>
load(ImageAddress)`. `:core` declares `uses`, and `:widgets` `provides`
`SharedStyleImages`, which hands the address to `ImageSource.parse` and the
shared `ImageLoader`: a `url()` and an `image` of one file share one decode,
and a region of a sheet is cut from the sheet's. `:core` carries a small
provider of its own for an application with no catalogue, which reads
`classpath:` and files and keeps what it decoded.

The interface's static half is what the painter calls:
**`StyleImages.resolve(url, scale)`** answers the decoded picture and its
density, or null while it loads. Above 100% it asks for the `@2x` variant
first (`panel.png` → `panel@2x.png`, with a region doubled) and falls back
to the 1x picture when there is none. A failed address is remembered and
not asked for again. When a load finishes, **`StyleImages.generation()`**
moves on and the **`onArrival`** listeners run; the launcher subscribes
`repaintAll`.

**Relative addresses.** A sheet read with `Stylesheet.resource(owner, name)`
rewrites a `url()` with no scheme into a `classpath:` name beside the sheet,
as `Class.getResource` reads one; `/` is the root. A sheet from text or a
stream has nothing to be beside, and its plain names are files.

### Backgrounds

`Background.layers()` is a `List<CssImage>`. **`background-size`** (`auto`,
`cover`, `contain`, one or two lengths, percentages of the box, `auto` per
axis) and **`background-repeat`** (`repeat`, `no-repeat`, `repeat-x`,
`repeat-y`, and the two-value form of `repeat` and `no-repeat`) are comma
lists matched to the layers, repeated when shorter, as CSS matches them. The
shorthand reads a `url()` layer and its repeat, and resets size and repeat.
`Background.of` keeps the lists when there are no layers yet, so a
`background-size` applied before its `background-image` is not lost.

The painter draws a `url()` layer as tiles, each placed on whole device
pixels so neighbours share their seam. A square box clips to its rectangle.
A rounded one draws the tiles into a layer and keeps only what lies inside a
second layer holding the outline, composited destination-in: the rasterizer
clips to rectangles only, and a fill's compositing operator was found not to
take effect on this build (an even-odd erase drew opaque black), where a
blit's does. A picture so small the box would need more than 1024 tiles is
first repeated into a larger tile, kept weakly beside the picture.

**Drawn when it arrives.** A box names an address, and its value is the
same before and after the pixels arrive, so the retained tree would call it
unchanged. `RenderObject` remembers the generation it last saw, and a box
that names a picture counts as changed when the generation has moved since.

## Alternatives considered

- **A Blend2D pattern** with the repeat as its extend mode. The shim exports
  no `bl_pattern_*` symbol, and adding one is a native change and an ABI
  bump; tiles under a rectangle clip, and a layer under a rounded one, need
  none.
- **Resolving the picture in the cascade**, so the box carries the pixels and
  its value changes on arrival. The cascade would have to know the display
  scale to choose a variant, and a restyle of every window per arrival
  rebuilds renderers for a repaint's worth of change.
- **A static registration** called by `:widgets` at start-up. Nothing in
  `:widgets` runs before the first paint in every application; a service is
  the module system's own answer, and the one the emoji face already uses.

## Consequences

- `background-position` still moves every layer by lengths only; a
  percentage or keyword places a picture at the top left. `background-clip`,
  `-origin`, `-attachment`, `space` and `round` are not read, and a size or
  position in the shorthand is refused.
- A layer whose picture is loading draws nothing: the colour and gradients
  show first and the picture a frame later. Outside a running toolkit the
  load is synchronous, so tests and offscreen renders draw it at once.
- `GradientLayer` is a `CssImage`; `Background.of` takes
  `List<? extends CssImage>`, so callers passing gradient lists compile
  unchanged.
- Tests: `BackgroundPictureParserTest` (images, sizes, repeats, the
  shorthand, the cascade), `StyleImagesTest` (arrival, variants, failures,
  the module's own provider, damage on arrival), `AnchoredUrlTest`, and the
  `css-background-images` golden: a tiled weave, `cover`, a `no-repeat`
  crest under a radius and a `repeat-x` strip over a gradient.
