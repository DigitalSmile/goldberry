# Architecture

<p class="gb-lede">Five layers, three trees and one native boundary. This page is the map an application developer needs. The design document in the repository has the rest.</p>

The authority on the design is
[`docs/ARCHITECTURE.md`](https://github.com/DigitalSmile/goldberry/blob/master/docs/ARCHITECTURE.md),
with the design system and the per-widget contracts beside it. This page
summarises what is built. Where the two differ, the design document says so
inline and [Status](../status.md) records what shipped.

## The layers

```
┌────────────────────────────────────────────────────────┐
│  Application        Java records, KDL documents, CSS   │
├────────────────────────────────────────────────────────┤
│  Widget layer       widgets → elements → render objects│
├──────────────┬──────────────┬──────────────────────────┤
│  Style        │  Layout      │  Text                   │
│  CSS engine   │  Yoga        │  HarfBuzz + JDK Bidi    │
│  (pure Java)  │  (flexbox)   │  and BreakIterator      │
├──────────────┴──────────────┴──────────────────────────┤
│  Paint and raster   box painter, layers, damage,       │
│                     Blend2D on the CPU, banded threads │
├────────────────────────────────────────────────────────┤
│  Backend SPI        window, present, input, clipboard, │
│                     cursor, tray, popup, GPU surface   │
├────────────────────────────┬───────────────────────────┤
│  SDL3                      │  Headless                 │
│  Linux, Windows, macOS     │  tests and servers        │
└────────────────────────────┴───────────────────────────┘
```

The application writes the top row. Everything below it is the toolkit, and
only the bottom row touches the platform.

## The three trees

The widget layer follows Flutter's model
([ADR-0004](../adr/0004-three-tree-retained-declarative-model.md)):

| Tree | Owned by | Lifetime | Holds |
|---|---|---|---|
| Widgets | the application | one build | an immutable description: a record with a pure `build()` |
| Elements | the toolkit | across rebuilds | state, the subscription a `bind=` made, who is hovered, focused or pressed |
| Render objects | the toolkit | across frames | a Yoga node, the box last applied to it, where it was painted |

A rebuild produces a new widget tree. The element tree is reconciled against
it by type and key, so a parent re-describing its child does not lose the
child's state. The render tree is reconciled against the boxes the elements
produce, and every Yoga setter is guarded by a comparison, so a frame in which
nothing changed costs a few microseconds
([ADR-0069](../adr/0069-the-render-tree-is-retained.md)).

## The frame loop

One UI thread runs the loop. Blend2D's workers rasterize in bands beside it.

```
input events → dispatch (hit-test on the painted frame)
→ rebuild dirty widgets → diff → update elements and render objects
→ style resolution (invalidated nodes only) → Yoga layout (incremental)
→ paint recording → Blend2D raster (banded) → present(buffer, damage)
```

Three properties of the loop shape what an application sees:

- **It is idle when nothing moves.** A frame is asked for by a value that
  changed, a `setState`, a transition in flight, or a widget that says it is
  animating. Nothing polls
  ([ADR-0128](../adr/0128-a-change-is-its-own-frame-request.md)).
- **Hit testing reads the painted frame.** A pointer event is about what the
  user can see, so dispatch runs against the snapshot taken while painting
  rather than a fresh layout ([ADR-0054](../adr/0054-hit-testing-runs-against-the-painted-frame.md)).
- **Only the damage is repainted**, where the backend promises the buffer it
  lends back still holds the last frame
  ([ADR-0072](../adr/0072-a-partial-repaint-needs-a-promise.md)).

[Performance](../performance/index.md) has the numbers for each stage.

## The native boundary

Every C function the toolkit calls is bound by hand in one module,
`dev.goldberry.natives`, and no raw `MemorySegment` leaves it. A bound function
is a holder class whose method handle is a compile-time constant, which is what
makes a call cheap on the JVM and in a native image alike
([ADR-0173](../adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)).
A layout probe checks every struct layout and constant against the compiled
library at run time, on every platform, so a `long` that is 32 bits on Windows
is caught before it is read ([ADR-0029](../adr/0029-yogas-node-api-and-who-owns-a-node.md)).

The libraries are statically linked into one `libgoldberry` per platform by a
CMake superbuild. Four artifacts exist: `linux-x64`, `linux-aarch64`,
`macos-aarch64` and `windows-x64` ([ADR-0041](../adr/0041-three-platforms-four-artifacts-two-backends.md)).

`goldberry-media` is the one exception. FFmpeg is LGPL and must stay a set of
replaceable shared libraries, so the media module binds it itself, under the
same rules ([ADR-0461](../adr/0461-a-media-engine-binds-its-own-libraries.md)).

## The backend SPI

The SPI is the only platform-facing interface. Two implementations exist and
the list is closed: `sdl3` for every desktop, and `headless`, which renders to
memory for tests and servers
([ADR-0041](../adr/0041-three-platforms-four-artifacts-two-backends.md)).

| The SPI answers | How |
|---|---|
| a window | logical pixels in, physical raster out, per-monitor fractional scale |
| present | the platform lends a surface and Blend2D draws straight into it ([ADR-0046](../adr/0046-what-present-actually-does.md)) |
| input | pointer, wheel in lines, keys and committed text as separate events |
| a popup | a second window with an owner, which a video driver may refuse ([ADR-0102](../adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)) |
| the clipboard | text as a value, everything else as an offer by MIME type ([ADR-0286](../adr/0286-a-clipboard-write-is-an-offer.md)) |
| the desktop's theme | light, dark, or the desktop does not say ([ADR-0322](../adr/0322-the-desktop-says-light-or-dark-or-says-nothing.md)) |
| a tray icon, file dialogs, a GPU surface | optional, and absent where the platform has none |

## Binding and weaving

A model is a class with `@Bind` fields and `@Action` methods. Two mechanisms
make an assignment observable, and they are interchangeable:

| | On the JVM | In a native image |
|---|---|---|
| mechanism | reflection over the annotations at run time | the compiled class rewritten at build time |
| build step | none | the weaver, one Gradle plugin or one Maven execution |
| a change notifies | at the next sweep | inside the assignment |

The same model, the same paths and the same refusals both ways
([ADR-0155](../adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)).
[Model weaving](../weaving.md) explains the mechanism and
[Native image](../native.md) what an image needs.

## The modules

| Module | Artifact | What it holds |
|---|---|---|
| `:common` | `goldberry-common` | logging and the start-up timeline, the lowest module |
| `:natives` | `goldberry-natives` and four classifier jars | the FFM bindings and `libgoldberry` |
| `:core` | `goldberry-core` | the engines and the contracts: the three trees, style, layout, text, icons, paint, the backend SPI |
| `:widgets` | `goldberry-widgets` | the widget catalogue, charts included |
| `:html` | `goldberry-html` | optional: Markdown and HTML as widgets |
| `:emoji` | `goldberry-emoji` | optional: the Noto Color Emoji face |
| `:media` | `goldberry-media` and `ffmpeg-<target>` classifiers | optional: audio and video |
| `:gpu` | `goldberry-gpu` | optional: `canvas3d` and GPU composition |
| `:toolkit` | `goldberry` | the umbrella an application depends on |
| `:bom` | `goldberry-bom` | the version table |

`:assets`, `:weaver` and `:example` are build-time tools and the showcase. They
are not published. The module graph is enforced by JPMS and the modules
`:common ← :natives ← :core ← :widgets` are the spine
([ADR-0007](../adr/0007-jpms-modules-enforce-the-native-boundary.md),
[ADR-0174](../adr/0174-what-both-halves-need-is-its-own-module.md)).
[Repository layout](../contributing/repository.md) has the packages.

## Read next

<div class="gb-cards">
<a class="gb-card" href="limitations.html"><strong>Limitations</strong><span>What the architecture leaves out, and where each gap is tracked.</span></a>
<a class="gb-card" href="../applications.html"><strong>Building an application</strong><span>The four kinds of class an application is made of.</span></a>
<a class="gb-card" href="../adr/index.html"><strong>Decision log</strong><span>Every choice above, with its alternatives and its costs.</span></a>
</div>
