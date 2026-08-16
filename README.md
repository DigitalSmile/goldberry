# Goldberry

[![Linux](https://github.com/digitalsmile/goldberry/actions/workflows/linux.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/linux.yml)
[![Windows](https://github.com/digitalsmile/goldberry/actions/workflows/windows.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/windows.yml)
[![macOS](https://github.com/digitalsmile/goldberry/actions/workflows/macos.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/macos.yml)
[![Example](https://github.com/digitalsmile/goldberry/actions/workflows/example.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/example.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**A fast and modern UI toolkit for Java.**

Goldberry is a declarative desktop UI toolkit written in pure Java
over a small set of native C libraries bound via the Foreign Function & Memory
API. No JNI, no bundled web engine, no platform widget wrapping.

- **Starts in milliseconds.** CPU rasterization, no GPU context for plain UI,
  GraalVM native-image as a first-class target.
- **Declarative.** Immutable widgets with a pure `build()`, expressed as Java
  records or as KDL markup. Markup and stylesheets hot-reload at runtime.
- **Real layout and real styling.** Flexbox via Yoga, and a genuine CSS subset
  with variables, cascade, and transitions — not a proprietary styling DSL.
- **Cross-platform from the first commit.** Linux (Wayland/X11), Windows, macOS 
  are peer backends behind one SPI.

> **Pre-release.** A window opens, Blend2D rasterizes its frames, Yoga lays out
> a tree behind the boundary, and HarfBuzz-shaped text draws into it; there are
> no widgets yet, and nothing measures a paragraph for layout.
> See [Status](book/src/status.md) for what works and what is still open.

## Quick start

Requires a **JDK 25** toolchain. Gradle provisions one if it cannot find one.

```sh
./gradlew build
```

That builds and tests every Java module and, if it is missing or out of date,
the native library `libgoldberry` for this machine. Building the native library
needs CMake ≥ 3.28, Ninja, a C/C++ toolchain, and — on Linux, for libxkbcommon —
Meson. `./gradlew :natives:checkToolchain` verifies all of it up front, prints
the absolute path and version of each tool it will use, and names the packages to
install if anything is absent.

The tools are searched for on the `PATH` first and then in the usual install
directories, so a Homebrew, MacPorts, `CMake.app` or `pip install --user`
toolchain is found even from a Gradle daemon that an IDE started with a bare
`PATH` (ADR-0040). To point at one somewhere else:

```sh
./gradlew build -Pgoldberry.cmake=/path/to/cmake    # also -Pgoldberry.ninja, -Pgoldberry.meson
```

**The first native build downloads about 330 MB** — Blend2D, AsmJit, Yoga,
HarfBuzz and SDL3, cloned by the superbuild — which typically takes a few
minutes. Compiling them afterwards is comparatively quick, around a minute on a
recent laptop. Git reports its progress as it goes, so a configure step that
looks idle for a long time is a slow connection rather than a stuck build
(ADR-0038).

The clones land in `natives/.deps/<target>`, deliberately outside `build/` so
that `./gradlew clean` does not throw them away. To discard them on purpose:

```sh
./gradlew :natives:cleanNativeDeps
```

For a Java-only build with no native toolchain:

```sh
./gradlew build -Pgoldberry.skipNative=true
```

Tests that need real native code skip when no library is loadable, so this stays
green — it just verifies less.

Released artifacts are built on native runners per platform, so a locally built
library is for development only. To check a library built somewhere else — a CI
artifact, a colleague's build — point the tests at it instead of building one:

```sh
./gradlew :natives:test \
  -Dgoldberry.native.library=/path/to/libgoldberry.so \
  -Dgoldberry.native.required=true
```

Supplying a library is also what tells the build not to build its own. Adding
`goldberry.native.required=true` turns "no library, skip quietly" into a failure,
which is how CI verifies that the artifact it just built actually loads — see
[ADR-0016](book/src/adr/0016-verify-the-artifact-and-never-skip-the-check.md).

## Hello, window

```java
var window = Window.open("Hello", 960, 640);
window.onPaint(frame -> frame.fill(0xFF2E3440));
Goldberry.run();
```

That is the whole API for a window: no backend to name, no event loop to build,
no `switch` over platform events (ADR-0022).

Painting takes **logical** coordinates, and they are not rounded on the way in.
Blend2D's context is scaled once per frame, so a rectangle at `x = 10.5` on a
150% display lands on physical 15.75 and is antialiased across the two pixels it
actually covers — the same code is correct at 100%, 125% and 150%
([ADR-0031](book/src/adr/0031-blend2d-and-the-borrowed-buffer.md)). Colours are
`0xAARRGGBB` and are **not** premultiplied; the rasterizer handles that.

When the platform lends its own surface — SDL does — Blend2D draws straight into
it. A frame costs no copy at all.

Work that is not instant goes off the UI thread and comes back on it:

```java
Goldberry.async(() -> loadTheThing())
         .thenAccept(thing -> window.title(thing.name()));   // on the UI thread
```

## Layout

Flexbox comes from **Yoga**, bound directly rather than reimplemented. There are
no widgets to style yet, so this is the layer beneath them rather than an API to
build against — but it is what the CSS subset compiles to
([ADR-0029](book/src/adr/0029-yogas-node-api-and-who-owns-a-node.md)):

```java
try (var config = YogaConfig.create();          // CSS's defaults, not Yoga's
     var root = YogaNode.create(config)) {

    config.setPointScaleFactor(window.scale().factor());  // snap to real pixels
    root.setFlexDirection(FlexDirection.ROW);
    root.setPadding(Edge.ALL, StyleLength.points(8));

    var sidebar = YogaNode.create(config);
    sidebar.setWidth(StyleLength.percent(25));
    root.addChild(sidebar);                     // the parent owns it from here

    root.calculateLayout(960, 640);
    var box = sidebar.layout();                 // 236.0x624.0 at (8.0, 8.0)
}
```

Text enters layout as a measured leaf: Yoga knows nothing about glyphs, so a
paragraph reports how tall it came out at the width Yoga proposes. That callback
is a Java method called from C returning a struct **by value**, which is the
fiddliest thing the toolkit asks of FFM and the reason
[ADR-0017](book/src/adr/0017-proving-the-struct-by-value-upcall.md) exists.

```java
node.setMeasureFunction(paragraph.measureFunction());   // runs during calculateLayout
```

`Box.text(...)` wires that up for you — see [Text](#text) below.

The point scale factor is what makes fractional DPI land correctly: at 1× a row
of 101 points splits 51/50, and at 2× it splits 50.5/50.5 — the same tree on a
different pixel grid.

## Text

**HarfBuzz** shapes, **Blend2D** draws, and `Font` is what holds the two
together. Inter, JetBrains Mono and OpenMoji ship inside `goldberry-core`, so
text renders identically on every machine without asking what fonts are
installed ([ADR-0033](book/src/adr/0033-assets-are-fetched-and-compiled-not-committed.md)).

```java
try (var font = Font.bundled(BundledFont.UI, 16)) {   // 16 logical points
    var width = font.widthOf("Goldberry");            // what layout will ask

    window.onPaint(frame ->
            font.draw(frame, 16, 16 + font.ascent(), "Goldberry", 0xFFECEFF4));
}
```

The `y` is the **baseline**, not the top of the line: an `a` sits above it and a
`g` hangs below, so the top of a line is `baseline - ascent()`.

A `Paragraph` wraps, and that is what lets text take part in layout rather than
being drawn over it. It is shaped **once**; every re-wrap after that is arithmetic
over the glyphs shaping already produced, which is what makes it affordable to
answer Yoga from inside a layout pass
([ADR-0036](book/src/adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)):

```java
var body = Paragraph.of(font, "…prose that has to fit somewhere…");

Box.of().direction(FlexDirection.COLUMN).children(
        Box.filled(0xFF88C0D0).size(UNDEFINED, StyleLength.points(32)),
        Box.text(body, 0xFFECEFF4),          // as tall as its text wrapped
        Box.filled(0xFF3B4252).grow(1));     // …and this starts below it
```

A box with text is a **measured leaf**: Yoga proposes a width, the paragraph wraps
at it and reports a height, and the flexbox algorithm sizes everything around that
answer. A box may therefore have text or children, not both — Yoga asks a measured
node for its size and never lays its children out.

One thing is worth knowing even from the outside. Shaping happens in the font's
own **design units**, and the size lives on the Blend2D side alone — so a shaped
run is correct at every size, and the font matrix is the only thing that ever
converts. Applying a size on both sides applies it twice, which for Inter at 16
points draws the text 128× too wide and reports no error at all. `Font` exists to
make that unrepresentable
([ADR-0034](book/src/adr/0034-one-size-and-the-design-unit-crossing.md)).

Not yet: bidirectional runs — right-to-left text is refused at construction rather
than wrapped wrongly — fallback between the UI and emoji faces, and style runs
within a paragraph. Icons are in the jar as path data and do not draw; Blend2D's
path API is unbound.

## Run the showcase

`:example` is an ordinary subproject that runs on the module path, which is what
catches an unexported package or a wrong `--enable-native-access` (ADR-0023).

```sh
./gradlew :natives:cmakeBuild     # once, to build libgoldberry
./gradlew run
```

A window opens. `-Pgoldberry.example.frames=3` paints three frames and exits,
which is what CI runs under Xvfb.

**On macOS**, AppKit has to be driven from the process's first thread, so any
Goldberry application needs `-XstartOnFirstThread`. `./gradlew run` passes it for
you; an application of your own has to pass it itself, exactly as it would for
LWJGL or SWT. Without it `SDL_Init` fails with "No available video device", which
says nothing about threads — see [ADR-0039](book/src/adr/0039-macos-needs-the-first-thread.md).

## Logging

Goldberry logs through **SLF4J** and binds no implementation. Add one and the
toolkit's diagnostics appear:

```groovy
runtimeOnly 'ch.qos.logback:logback-classic:1.5.18'
```

Add nothing and you get silence — including from SLF4J itself, which otherwise
prints a "no providers were found" warning to stderr (ADR-0023).

At `TRACE`, Goldberry reports a start-up timeline and per-frame timings — which is
how to find out where a slow start or a slow frame went (ADR-0028):

```text
start-up timeline (866.6ms to here):
     533.8ms    +533.8ms  runtime starting
     559.6ms     +25.8ms  libgoldberry mapped (1.9ms)
     722.6ms    +162.9ms  SDL video subsystem up (99.2ms)
     866.6ms    +117.1ms  first frame presented
```

## Documentation

| Where | What |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | The design: what the system is, layer by layer |
| [`book/`](book/src/introduction.md) | Why each significant choice was made, one decision at a time |
| [`book/src/status.md`](book/src/status.md) | Module layout, milestones, and open questions |

The book is [mdBook](https://rust-lang.github.io/mdBook/):

```sh
mdbook serve book
```

## License

Goldberry is licensed under the [Apache License 2.0](LICENSE).

It bundles third-party software and assets under their own licenses, disclosed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and [`licenses/`](licenses/), and
shipped inside every jar under `META-INF/`. Notably, the native libraries are
statically linked into `libgoldberry`, and the bundled OpenMoji emoji font is a
modified, share-alike (CC BY-SA 4.0) derivative — see
[ADR-0015](book/src/adr/0015-licensing-and-third-party-disclosure.md).

```sh
./gradlew checkLicenses
```
