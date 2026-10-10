# ADR-0594: A Lottie animation is read in Java and drawn through the toolkit's own painter

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** docs/gaps.md (G55)

## Context

A Telegram sticker is one of three formats. A WebP sticker already decodes
(`image.ImageFormat.WEBP`, G35). A `tgs` sticker is **gzipped Lottie JSON**, and
a `webm` sticker is **VP9 video with an alpha channel**. Neither drew.
`image.anim.Animation` is frames and delays, which fits a GIF and not Lottie:
Lottie is vector, a description of shapes and easing curves meant to be drawn
at whatever size the box turns out to be.

The entry asked for Lottie first, with `VectorAnimation.of(ByteBuffer)`,
`totalMillis()`, `isEndless()` and `imageAt(millis, width, height)` as the
floor, and `AnimationView` with `source(...)` and `autoplay(...)`, playing on
the frame loop, above it. The video sticker could wait.

Telegram's rules for a `tgs` (core.telegram.org/stickers): a 512 × 512 canvas,
60 fps, at most 3 seconds, at most 64 KB. The animator must not use
auto-bezier keys, expressions, masks, layer effects, images, solids, texts,
3D layers, merge paths, star shapes, gradient strokes, repeaters, time
stretching, time remapping or auto-oriented layers. A video sticker is VP9 in
WebM, one side exactly 512, up to 30 fps, 3 seconds, 256 KB, no audio.

The toolkit has no JSON reader. `render.web.WebCallback` says so and passes
JSON through unparsed. `gradle/libs.versions.toml` has no JSON or Lottie
library, and a native Lottie renderer (rlottie, ThorVG, Skottie) would be a
native dependency and an ABI change.

## Decision

**Lottie is read and drawn in Java, over the toolkit's own vector painter.**
There is no native change, no new dependency and no new module.

### Where it lives

- **`dev.goldberry.image.anim.VectorAnimation`**, public, beside `Animation`.
  The package's rule fits it: nothing in it holds a clock, and what to draw is
  a function of how long the caller says it has been playing. It is not in
  `dev.goldberry.image`, where the entry suggested "where the image types
  live", because `Animation`, its frame-by-frame counterpart, is here.
- **`dev.goldberry.image.lottie`**, in `:core` and **not exported**, holds the
  document model, its reader and its renderer. It follows `dev.goldberry.frame`,
  another package with public types that is not exported. The records model
  Bodymovin's JSON schema, and an exported promise about them would be a
  promise about a schema someone else changes.
- **`dev.goldberry.widgets.core.image.AnimationView`**, next to `ImageView`.
- **Not a separate artifact.** `goldberry-emoji` is separate because it is 5 MB
  of font. This is under 4,000 lines of Java, comments included, and no data. It needs the painter's
  `Frame`, `Path`, `Gradient` and `Stroke`, the `Affine` matrix and
  `ImageDecodeException`, and a module of its own would only re-export them.

### The API

```java
public final class VectorAnimation {
    public static VectorAnimation of(ByteBuffer lottie);   // tgs or JSON, sniffed by gzip's magic
    public static VectorAnimation of(byte[] lottie);
    public double width(); public double height(); public double frameRate();
    public long durationMillis();                           // one pass: (op - ip) / fr
    public int loopCount(); public VectorAnimation loops(int count);
    public boolean isEndless();                             // loopCount == 0
    public long totalMillis();                              // -1 when endless
    public boolean isDoneAt(long elapsedMillis);
    public double frameAt(long elapsedMillis);
    public Image imageAt(long elapsedMillis, int width, int height);
    public void paint(Frame frame, long elapsedMillis, double x, double y, double width, double height);
}
```

Where it differs from the entry:

- **`totalMillis()` follows `Animation`'s convention**, -1 when endless, and
  **`durationMillis()`** is one pass. The two classes sit in one package, and a
  second meaning for the same name there would be a trap. A Lottie document has
  no loop count, and every player, Telegram's included, loops it. So a
  `VectorAnimation` is endless until `loops(n)` says otherwise. That is how a
  chat that plays a sticker once and holds its last frame asks for it.
- **`paint(Frame, …)`** is added. It draws straight onto a frame as vectors,
  under the frame's transform and at its scale. `AnimationView` draws this way,
  and so can a `canvas` painter. It stretches over the rectangle given, as
  `Frame.drawImage` does. `imageAt` fits the canvas inside the size, keeps its
  shape and centres it, as the entry asked.
- **Refusals are `ImageDecodeException`**, not `IllegalArgumentException`.
  Bytes that are not Lottie and a Lottie document that cannot be drawn are both
  "a sticker that cannot be shown", and one `catch` should cover both. That is
  `ImageDecodeException`'s documented job.

### What is drawn

**Layers:** shape layers, null layers, solid layers and precomp layers.
Parenting works through `parent` and `ind`, with a depth bound against loops.
`ip` and `op` bound when a layer shows. `st` offsets a layer's own time. A
precomp runs on `(t − st) / sr`, or on its time remap `tm` (seconds × fr), and
is clipped to its `w` × `h`. A hidden layer is kept, so a child can still ride
on its transform. Solid layers, time stretch and time remap are forbidden in a
sticker and cost a few lines each, so they are drawn.

**Transforms**, on layers and groups: anchor, position (joint or split `x`/`y`),
scale, rotation, opacity, and skew about its axis, applied in After Effects'
order. They are composed with `css.value.Affine` and concatenated onto the
frame, so a stroke is as thick as the document says at any size and a gradient
turns with its group.

**Shapes:** rectangles with roundness, ellipses, stars and polygons (with
roundness), and paths (`sh`: `v`/`i`/`o`, open or closed). Each starts and runs
the way After Effects draws it. That matters only to a trim.

**Paints:** fill, stroke (width, cap, join, miter limit, dash with offset),
gradient fill and gradient stroke, linear and radial. Colour stops and opacity
stops are merged into one stop list. A paint draws every piece of geometry
before it in its group, nested groups included. An earlier item draws over a
later one.

**Trims** (`tm`): start, end, and offset, in both modes. "Simultaneously" cuts
each path by the whole range. "Individually" lays the paths end to end, and
each keeps its share, so a nested group's own paints still draw what is left.
A trim cuts every path before it. Inner groups' trims apply first. A range that
wraps on a closed path is joined into one run, so the seam gets no caps.

**Mattes and masks.** Alpha, inverted alpha, luma and inverted luma mattes, by
`tt` with `td`, or by `tp`. Masks in every mode (add, subtract, intersect,
lighten, darken, difference), inverted or not, with opacity. These and the
gradient stroke need "draw this, keep it where that is". The painter's
compositing operators are package-private, so the two pictures are drawn into
two rasters the size of the box (`lottie.Scratch`). They are multiplied pixel by
pixel in Java and drawn back as an image. That costs two rasters per use, and
only a layer that asks pays it. Telegram forbids masks and gradient strokes, so
a sticker pays only for mattes.

**Keyframes:** hold (`h`); cubic-Bézier easing per component (`o`/`i`),
**solved for x → t** by Newton's method with a bisection fallback before `y` is
read; spatial tangents (`to`/`ti`) on positions, walked at even speed through a
table of arc lengths measured when the document is read; both the current form
(next key's `s`) and the old one (`e`); and path keyframes blended vertex by
vertex, holding when the vertex count changes.

**Time is continuous.** A frame number is `ip + elapsed × fr / 1000`, modulo
the pass, and it is not rounded. So a 30 fps document moves smoothly at 120 Hz.

### What is refused, and what is passed over

`VectorAnimation.of` **refuses**, with an `ImageDecodeException` that names
the cause:

- an **expression** (`x` on any property), which is code for a player to run;
- an **image layer** (`ty: 2`), which is a picture rather than a description;
- bytes that are not gzip-or-UTF-8 JSON;
- JSON without a positive `w`, `h` and `fr`, or with `op` not after `ip`;
- a gzip that inflates past 16 MB, as a stop for a compression bomb.

Everything else unknown is **passed over**, and the rest is drawn. That covers
text layers, layer effects, merge paths, repeaters, rounded corners, and shape
types newer than this reader. Refusing whatever it had not met would refuse
next year's exporter. A missing ornament leaves the picture recognisable.

The JSON reader, `lottie.Json`, is strict RFC 8259. Its values are records, its
nesting is bounded at 512, and it has no lenient mode.

### `AnimationView`

`AnimationView(VectorAnimation source, String alt, boolean decorative, boolean
autoplay, Fit fit, Attributes)` has the convenience constructor `(source, alt)`,
`decorative(source)`, and the withers `source(...)`, `autoplay(...)` and
`fit(...)`. It is a stateful widget whose state keeps a **playhead**: the frame
clock's time when it was first drawn. It builds `AnimationFigure`
(`Role.FIGURE`, named by its alt text) or, decorative, `AnimationBox`, as
`ImageView` builds `ImageFigure` and `ImageBox`. Both draw through
`AnimationPaint.Moment(animation, elapsed, fit)`, a record `Painter`, so two
frames showing the same moment are the same painting.

- **On the frame loop.** The part answers `Paints.isAnimating(style,
  context)`, the hook `canvas` uses (`Canvas.animating`, G41). It answers true
  while the animation plays and has not finished, and the picture is drawn for
  `Paints.Context.nowMillis()`. There is no timer, every animation in a window
  draws on one tick, and a finite animation leaves the loop idle when it ends.
- **Reduced motion and `autoplay(false)`** both show the first frame and ask
  for no frames. The platform's setting reaches the widget as
  `Paints.Context.reducedMotion()`. Turning either back starts the animation
  from the beginning. A new source also starts from the beginning.
- **Sized as an image.** The CSS type is `image`, and the box is sized by
  `ImagePaint.intrinsic` from the canvas's `w` × `h` in logical pixels.
  `CONTAIN`, `COVER`, `FILL` and `NONE` place the canvas, and the content box
  clips it.
- **Rasterised at the view's physical size.** It is drawn as vectors into the
  window's frame, at its scale and under its transform, with no intermediate
  image. A matte or mask raster is the box's size times the frame's scale.
- **Off-screen and unmounted.** A view scrolled out of sight is culled, so its
  painter does not run and nothing is rasterised. An unmounted view leaves
  nothing behind.
- **No markup node.** The document is bytes the application has, so there is
  no `@Markup`. That also keeps `BookTest`'s heading-per-markup rule out of a
  frozen book.

## The video sticker

Not implemented. What exists and what it would take, as found in `:media`
today:

- **Demux.** The bundled FFmpeg (n8.1.3, built with `--disable-everything`
  and the Matroska demuxer) does read `BlockAdditional` as
  `AV_PKT_DATA_MATROSKA_BLOCKADDITIONAL` side data. But `ffi.Demuxer.read`
  copies only data, size, timestamps and the key flag into `codec.Packet`. The
  `AVPacket` layout in `FfmpegStructs` pads over `side_data`, and nothing binds
  `av_packet_get_side_data`. **The alpha is dropped before any decoder sees it.**
- **Decoder.** Decoders are found by codec id (`avcodec_find_decoder`), so VP9
  is FFmpeg's native `vp9`, which ignores the alpha stream. libvpx is not in the
  build.
- **Pixels.** `codec.PixelFormat` is NV12, I420, P010 and I010. Anything else,
  `yuva420p` included, is swscaled to I420. `VideoConverter.toBgra` writes
  opaque BGRA. The GPU path's `YuvLayout` has the same four layouts, `yuv.hlsli`
  returns alpha 1, and `VideoLayer` blends with `REPLACE`.
- **GStreamer** covers H.264, HEVC and AAC/AC-3, not VP9, with sink caps that
  have no alpha format.

It would take four things:

1. Carry the side data: bind it, put the BlockAdditional bytes on `Packet`, and
   attach them in `FfmpegDecoder.send`. The side-data constant needs a line in
   `ffmpeg_layout.c`, which is a C change.
2. Decode the alpha, in one of two ways. Build libvpx into the FFmpeg
   superbuild and select `libvpx-vp9` by name when the stream has
   `alpha_mode`, under the 7 MB size gate. Or, in Java, run a second native
   `vp9` decoder over the BlockAdditional bytes (they are a VP9 stream of
   their own) and use its luma as the alpha plane.
3. Add a four-plane `I420A` through `PixelFormat`, the layout probe and
   `convertedFrame`.
4. Carry alpha in the picture: premultiplied alpha from `toBgra`, a fourth
   texture and a `yuv4` shader on the GPU path, and a premultiplied blend in
   `VideoLayer`. A GStreamer VP9 path with `vp9alphadecodebin` and `A420` caps
   would be optional.

Step 2's libvpx option and step 1's constant are native changes to the shared
FFmpeg build. The rest is Java and shaders. It is a player change, out of scope
here, and the entry already said the video sticker waits on one.

## Consequences

- A `tgs` sticker draws, at any size, through the same rasteriser, colour
  handling and frame loop as everything else.
- The cost is per frame: every visible animation re-evaluates its properties
  and re-rasterises its paths. The model is parsed once. A fixed property hands
  back the same array every frame, and keyframe search and path building
  allocate small arrays. A matte allocates two box-sized rasters per frame.
  Nothing is cached between frames, because a moving sticker's frames all
  differ.
- **Not done, and why:**
  - An **even-odd fill** (`r: 2`) is drawn non-zero, because the painter's
    even-odd fill is package-private. Stickers rarely depend on it.
  - The **radial gradient's highlight** (`h`, `a`), After Effects' focal point,
    is not drawn: `paint.Gradient.Radial` has one centre. Blend2D supports a
    focal point, and the public value would have to grow one.
  - **Group opacity is per paint, not per group**, as in rlottie, Telegram's
    renderer. Where two shapes in a translucent group overlap, the lower one
    shows through.
  - **Mask expansion** (`x`) and **mask feather** are not drawn, and a precomp's
    clip under a rotation is its bounding box.
  - **Text layers, effects, merge paths, repeaters and rounded corners** are
    passed over. Telegram forbids all but rounded corners.
  - **How many animations may run at once is not bounded.** The frame loop has
    no budget hook a widget can consult. `Paints.Context.frames()` reports what
    the loop managed and nothing more. A dozen stickers in a timeline each
    redraw every frame while they are on screen. Off-screen ones are culled
    from paint but keep the loop awake, because whether something animates is
    asked during render, before layout knows what is visible. Bounding it means
    a frame-budget decision in the loop, which deserves its own entry.
  - **No showcase card.** The Drawing chapter's cards are pinned by
    `GalleryGoldenTest`, and a moving card would change that golden and the
    book's picture, which is frozen until the release.
- **Tests.** `image.lottie.LottieTest`: JSON strictness and depth, easing solved
  numerically (CSS's `ease-in-out` at 0.25 is 0.129), hold, eased, spatial and
  old-shape keyframes, path blending, trims in both modes including the wrap,
  refusals, and unknown shapes passed over. `image.anim.VectorAnimationTest`:
  duration, endless and `loops`, frame numbers; pixel checks at two sizes for a
  still rectangle, a `tgs` copy, aspect fitting, a moving ellipse, parenting with
  a scaled group, linear, rotated and radial gradients, a trim, a mask, a matte
  and a precomp with a start time; and the golden `lottie-sticker` (every
  feature at once, checked at 1x and through the harness's sweep at 2x, 1.5x and
  1.25x). `widgets.core.image.AnimationViewTest`: advancing on a `Filmstrip`'s
  virtual clock, standing still with `autoplay(false)`, stopping after a finite
  pass, reduced motion, semantics, sizing, and restarting on a new source.
  The fixtures are hand-written, under
  `core/src/test/resources/dev/goldberry/image/lottie/`.
- The guide section is parked in
  `docs/snapshot/components-drawing-vector-animation.md`.
