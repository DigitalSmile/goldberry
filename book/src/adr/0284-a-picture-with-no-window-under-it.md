# 284. A picture with no window under it

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G5, whose other half — `Image.encodePng()` —
landed with [ADR-0283](0283-an-image-is-a-value-and-the-decoder-is-the-one-thing-blend2d-allocates.md).

## Context

G5: *"The pieces exist and none of them is a supported entry point:
`render.PixelBuffer.allocate`, `Frame.over(PixelBuffer, DisplayScale)`, a
`render.backend.headless` backend — and the golden-image harness lives in
`:core`'s testFixtures, which is not published to applications."*

That is exactly right, and it understates the problem. The pieces are public; the
**sequence** is not, and the sequence is the part that is hard. Painting a
painter into a buffer is four lines. Rendering a *widget tree* is:

```
prepare → flush → render → update → capture the regions → advance the clock
→ prepare → flush → render → update → capture the regions
→ prepare → flush → render → update → paint
```

and every one of those steps is there because leaving it out produced a picture
that was wrong in a way nobody would notice for weeks. The only two places that
knew it were `Launcher`, where it is private and interleaved with damage
tracking, frame statistics and a window; and `GalleryGoldenTest`, where it was
copied, with thirty lines of comments explaining what each step was protecting
against.

A third copy was going to be written by the first application that wanted a
server-side preview, and it was going to leave steps out.

## Decision

```java
var png = Offscreen.of(1200, 900)
        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
        .render(new BoardPreview(document))
        .encodePng();
```

`io.github.digitalsmile.goldberry.offscreen.Offscreen`, a builder with two
terminals: `paint(Painter)` runs a painter over the whole buffer, and
`render(Widget)` runs the window's own sequence. Both return an
`image.Image`, so a PNG is one more call and a crop or a composite is
`Frame.drawImage`.

**Its own package, not `render.Offscreen` as G5 proposed.** `render` is the
backend SPI — what a platform implements, underneath everything else — and this
composes the layers *above* it: the element tree, the cascade, the render tree,
the paint pipeline. A widget renderer inside the backend package would point the
toolkit's own layering at itself. `image.Image` had the same question and the
same answer (ADR-0283).

### Three passes, and the third one is not a rounding of the second

`render(Widget)` lays the tree out twice, drawing nothing, and then builds,
lays out and paints it once:

1. **Pass one mounts and measures.** A newly mounted element starts no
   transition, and a clock-driven arrival has no beginning until something reads
   the clock — so a tree painted here shows every arriving widget at the *start*
   of its entrance, which for a `message` is a banner at zero opacity holding its
   space and drawing nothing.
2. **The clock advances** past the transition duration. It is **virtual**: a
   preview that depended on when it was taken would differ between two requests
   for the same document, and one `spinner` is enough to make that happen.
3. **Pass two measures again**, and the regions from pass one have by now been
   delivered — `Measured`, through the router, from the rectangles a laid-out
   frame produced. That is how a `text-area` learns how wide it really is and how
   a `masonry` learns how tall its columns came out.
4. **The third pass is the picture**, and it is a third pass rather than the
   second one painted because a widget told its size may **rebuild in response**.
   Painting pass two photographs every self-arranging widget one move from
   settled.

The measuring passes rasterize nothing at all, so the cost is one paint and three
layouts rather than three of each.

### The golden harness is now a consumer of it

`GoldenImage` renders through `Offscreen` instead of opening its own frame, and
so does the scale sweep beside it. Every golden image in this repository — around
a hundred of them, at three scales — is therefore a test of the API an
application would use to take the same picture. If `Offscreen` and a window ever
disagree about how a scene is drawn, a golden moves.

`GalleryGoldenTest` lost its copy of the sequence and its thirty lines of
commentary along with it.

## What the goldens said when they moved

**Nine gallery images changed, and the reason is a bug this found.**

The old harness never called `ElementTree.flush()`. `Launcher.paint()` calls it
on **every frame**, so a `setState` triggered by the region feedback — a
`masonry` rearranging its columns when it is told how wide they came out — was
applied in a window and never applied in a golden. The images that were committed
showed an arrangement the application does not draw.

The evidence is exact: removing *only* the `flush()` from `Offscreen` reproduces
all nine old goldens byte for byte, with every other difference between the two
implementations still in place. It is that one call.

So the nine images were re-blessed. Card distribution across masonry columns is
what changed in them; no colour, no size, no text and no control state did.

## Consequences

- **`Image.of(PixelBuffer)`** is new: an image over pixels somebody else
  rasterized, handed over rather than copied. An offscreen render would otherwise
  copy a megabyte to say what it had just drawn. The buffer is the caller's to
  stop writing to, which is `PixelBuffer`'s existing doctrine — and
  `asReadOnly()` is how it can be made impossible rather than agreed.
- **The teardown order is load-bearing.** A frame's Blend2D context is
  asynchronous, so `end()` is what joins the workers — and they are still holding
  the fonts, paths and layer rasters the tree lent them. `Offscreen` therefore
  ends the frame, *then* closes the render tree, *then* unmounts. Getting it wrong
  is not an exception: the first widget whose `State` owned a `Font` and closed it
  in `dispose` (the showcase's sticky, ADR-0285) turned a wrong order into a
  SIGSEGV in Blend2D's command processor, in a worker thread, intermittently.
- **A font book is opened and closed per render** unless one is given. A server
  rendering many previews should hand over a `Fonts` and keep it; the javadoc
  says so at the method that costs it.
- **`settle(int)`** is the one knob on the clock, and there is no way to ask for a
  system clock. A preview that is not reproducible is not a preview.
- **No `Offscreen` reuse across calls.** Each render builds a fresh element tree
  and unmounts it, so two renders cannot share state through one and a `State`'s
  `dispose` runs. It also means the builder is not a cache: rendering the same
  document twice does the work twice.
- **The headless backend is still not involved.** It never was: `Frame` paints
  into memory, and a backend is about presenting. G5 named it because it is what a
  reader expects to need — see `GoldenImage`'s own note, which has said "no
  window, no compositor and no `xvfb`" since M1.
- **An animation strip is not supported.** One call, one picture: a caller
  wanting frame 3 of a transition would want to drive the clock between paints,
  which is a different object with a lifetime — and nothing has asked for it.

## Alternatives considered

- **`render.Offscreen`, as G5 proposed.** The layering above; and `render` is the
  package an application is *least* expected to reach into.
- **Extract `Launcher`'s loop and have both call it.** The right answer on paper
  and a refactor of the hot path — damage, statistics, the HUD, the frame ring and
  the models' refresh are all woven through those forty lines. The duplication is
  real and is now *two* copies rather than three, with the goldens holding them
  together: every one of them goes through `Offscreen`, and `Offscreen` does what
  `Launcher` does.
- **Paint the second pass and skip the third.** One less layout, and every
  self-arranging widget photographed mid-settle. This is what the first
  implementation did, and the gallery's Basic screen is what caught it.
- **A system clock with an option for a virtual one.** Backwards: the default has
  to be the reproducible one, because the caller who does not think about it is
  the caller writing a cache key.
- **Return a `PixelBuffer` rather than an `Image`.** It is what the render
  produces, and it is the type with a borrowed-buffer doctrine attached. `Image`
  is the value that can be drawn, encoded, cached and put in a field — which is
  what every caller of this does next.
