# ADR-0513: The guide's pictures are taken from its own samples, in both themes

- **Status:** Accepted
- **Date:** 2026-10-01
- **Relates to:** [ADR-0511](0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md),
  [ADR-0284](0284-a-picture-with-no-window-under-it.md),
  [ADR-0110](0110-the-showcase-is-a-gallery-of-screens.md), `docs/book.md`

## Context

The guide's first pictures were copies of golden images: one widget per
picture, at one scale, in whichever theme the golden happened to be taken in,
for the seventeen widgets somebody had a golden of. Sixty-two of the
seventy-nine markup names had no picture. Every picture was 1×, so on a dense
display it was soft, and a reader on the light theme looked at dark pictures.
Nothing held a picture to the code: a stylesheet change moved the golden and
left the copy in the guide behind.

Every widget's section already carries a `kdl` sample that `BookMarkupTest`
inflates against the real catalogue (ADR-0511). The sample is the widget as the
guide describes it, and the shipped `Offscreen` renders any tree at any scale
without a window (ADR-0284).

## Decision

- **A widget's picture is a render of its own sample.** `PicturePlan` reads
  every `` ## `name` `` heading in Layout and Components and takes the first
  fenced `kdl` block under it. `PictureCamera` inflates it inside a column the
  width of the guide's text, draws it at **2×** through `Offscreen` with the
  bundled font book and the showcase's stylesheets, and crops to the widget.
  The file is `book/src/images/<name>-light.webp` and `-dark.webp`, lossless.
- **Both themes, always.** A picture is a pair, and the page shows the one that
  matches: `goldberry.css` hides `.gb-light` under `html.navy` and `.gb-dark`
  otherwise. No script is involved, and a picture carries the logical width it
  was taken at so it is drawn at the widget's size.
- **The samples are shown holding values.** `PreviewValues` binds every path
  the catalogue's samples name to a value of the type the widget reads, so a
  slider is at a number, a list has rows and a date picker a date. Every other
  `bind=` and every `press=` resolves to nothing, as in `BookMarkupTest`. The
  icons a sample names are bound to bundled ones.
- **A picture is held like a golden.** `BookPicturesTest` renders every pair
  and compares it against the committed file with `GoldenImage`'s tolerance;
  `-Dgoldberry.golden.update=true` retakes them. A new widget's chapter fails
  the test until its picture is taken, and a heading the guide cannot picture
  — five whose sample needs a player or a renderer, three a parent draws — is
  listed there with its reason, so the list cannot grow quietly.
- **The showcase screens are taken the same way.** `ScreenPicturesTest`
  renders nine screens through `ShowcaseScene`, the wiring `GalleryGoldenTest`
  now shares, at 2× in both themes. Five pictures stay in one shade at 1×
  because they are states no sample reaches: a scroll mid-wheel, a player
  mid-stream, three toasts in flight, the GPU lane, the canvas pinned at one scale. `BookTest` names
  them and refuses any other.
- **`BookTest` holds the shape**: every shot is a light and a dark picture with
  the same alt text and width, every file under `images/` is shown and every
  shown file exists, a widget's picture sits under that widget's heading, and
  a widget picture's width is half the file's pixels.

## Alternatives considered

- **Exporting the goldens at 2×.** `ScaleInvariance` already renders every
  golden at 2× and throws the result away. Keeping it would have covered the
  seventeen widgets with a golden, in one theme, with a picture of whatever
  state the test wanted rather than of what the chapter shows.
- **Rendering the pictures when the site is built.** The Pages workflow would
  need the native library and a JDK, and a reader of the Markdown on GitHub
  would see no picture at all. The pictures are committed, and the test is
  what keeps them current.
- **One theme, inverted with a CSS filter for the other.** The themes are not
  inverses of each other: the light theme reaches for darker fills where the
  dark one lifts them (ADR-0293). A filtered picture would be a picture of a
  theme that does not exist.
- **Lossy WebP for the screens.** A third of the size, and then the comparison
  against the renderer would have to tolerate the codec's error on top of the
  rasterizer's. The screens are lossless and 4 MB, retaken when a screen
  changes; if that cost grows, they are the first to go lossy.

## Consequences

- Every widget with a sample has a picture, and the picture cannot be stale:
  `./gradlew :example:test` fails when the code draws something else.
- A stylesheet change that moves a widget retakes its picture in the same
  change, which is a review of the picture as well as of the rule. The retake
  is one command and a `git add`.
- `book/src/images` is 7 MB where it was 1 MB, most of it the eighteen screens.
  Each retake of a screen adds its size to the history.
- The sample values in `PreviewValues` are named for the paths the chapters
  write. A chapter that renames a path gets a picture of a widget with nothing
  in it, which the author sees in the retake.
- The five media and GPU widgets keep the pictures their own tests take; a
  reader of those chapters still sees a player and a cube.
