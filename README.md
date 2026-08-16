<p align="center">
  <img src=".github/assets/goldberry-banner-1600x500.webp"
       alt="Goldberry — Modern Java UI toolkit" width="100%">
</p>

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
- **Cross-platform from the first commit.** Linux (Wayland/X11), Windows and
  macOS are peer platforms behind one SPI.

> **Pre-release.** A window opens, Blend2D rasterizes its frames across worker
> threads, Yoga lays out a tree behind the boundary, HarfBuzz-shaped text takes
> part in that layout, Lucide's icons draw, stylesheets and KDL markup hot-reload,
> pointer, wheel and keyboard input route to a widget tree, and markup wires both
> halves of §9 — an `action` to call and a value to `bind` to. The catalog is
> five primitives and one control — `button`, with variants, icons, a disabled
> state and golden images — so twelve controls are still to come.
> See [Status](book/src/status.md) for what works and what is still open.

## Quick start

Requires a **JDK 25** toolchain. Gradle provisions one if it cannot find one.

```sh
./gradlew build
```

That builds and tests every Java module and, if it is missing or out of date,
the native library `libgoldberry` for this machine. Building the native library
needs CMake ≥ 3.28, Ninja and a C/C++ toolchain.
`./gradlew :natives:checkToolchain` verifies all of it up front, prints the
absolute path and version of each tool it will use, and names the packages to
install if anything is absent.

The tools are searched for on the `PATH` first and then in the usual install
directories, so a Homebrew, MacPorts, `CMake.app` or `pip install --user`
toolchain is found even from a Gradle daemon that an IDE started with a bare
`PATH` (ADR-0040). To point at one somewhere else:

```sh
./gradlew build -Pgoldberry.cmake=/path/to/cmake    # also -Pgoldberry.ninja
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

When the backend lends a surface — SDL does — Blend2D draws straight into it,
so the toolkit never copies a frame. SDL still does: on Wayland it has no
window-surface implementation, so the buffer it lends is its own heap memory and
it copies that into a texture on every present. One copy instead of two, not
zero ([ADR-0046](book/src/adr/0046-what-present-actually-does.md)).

Work that is not instant goes off the UI thread and comes back on it:

```java
Goldberry.async(() -> loadTheThing())
         .thenAccept(thing -> window.title(thing.name()));   // on the UI thread
```

## Layout

Flexbox comes from **Yoga**, bound directly rather than reimplemented. Widgets
sit on top of this — most applications never touch it — but it is what the CSS
subset compiles to, and what a `canvas` widget will hand out
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
try (var face = FontFace.bundled(BundledFont.UI);     // parsed once
        var title = Font.on(face, 24);                // …and shared by
        var body = Font.on(face, 16)) {               //    every size

    var width = body.widthOf("Goldberry");            // what layout will ask

    window.onPaint(frame ->
            body.draw(frame, 16, 16 + body.ascent(), "Goldberry", 0xFFECEFF4));
}
```

The `y` is the **baseline**, not the top of the line: an `a` sits above it and a
`g` hangs below, so the top of a line is `baseline - ascent()`.

A `FontFace` is the typeface and a `Font` is one size of it. The split matters
because each library keeps its own copy of the file — Inter is a megabyte and a
half — so sharing the face makes a second size cost 4 µs and no extra memory
instead of 680 µs and three more megabytes
([ADR-0044](book/src/adr/0044-one-face-many-sizes.md)). `Font.bundled(UI, 16)`
still works and parses a face of its own, which is the right shape for exactly
one size. **The face must outlive every font over it.**

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
within a paragraph.

## Icons

Lucide's 1544 icons ship in `goldberry-core` as path data in a 24×24 box. They
are **strokes**, not fills — 2px round-capped outlines, most of them not closed —
so an `Icon` carries a stroke width as well as a shape
([ADR-0043](book/src/adr/0043-icons-are-stroked-paths.md)):

```java
try (var icon = Icon.bundled("layout-dashboard", 24)) {   // 24 logical points
    window.onPaint(frame -> icon.draw(frame, 16, 16, 0xFFECEFF4));
}
```

Like a `Font`, an `Icon` belongs to a size: the path is built pre-scaled, so
nothing is transformed at draw time and a 48px icon strokes at 4px rather than 2.
`BundledAssets.iconNames()` lists the set.

An icon is not a `Box` yet — the showcase draws them over its sidebar rather than
laying them out in it — because nothing decides an icon's intrinsic size until
the widget model does
([ADR-0004](book/src/adr/0004-three-tree-retained-declarative-model.md)).

## Widgets

Widgets are immutable records. The element tree behind them persists across
rebuilds, which is what lets `:hover` survive a parent re-describing its child
and what `setState` mutates ([ADR-0052](book/src/adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)).

Every widget exists three ways — a Java record, a KDL node, and a CSS type — and
a test asserts the first two produce equal values
([ADR-0059](book/src/adr/0059-a-control-is-a-record-a-node-and-a-rule.md)):

```java
var ui = new Widgets.Column(
        new Widgets.Text("Delete this file?"),
        new Widgets.Row(
                new Widgets.Spacer(),
                new Button("Cancel", this::dismiss),
                new Button("Delete", this::delete).styled("danger")));
```

```kdl
column {
    text "Delete this file?"
    row {
        spacer
        button press="dismiss" "Cancel"
        button class="danger" press="delete" "Delete"
    }
}
```

Markup **names** an action and cannot be one — `press="delete"` resolves against
an `Actions` registry, which is what keeps a reloaded document wired to the
handlers the old one had:

```java
var actions = Actions.strict()
        .bind("delete", this::delete)
        .bind("dismiss", this::dismiss);

var tree = Controls.inflater(actions).inflateAll(KdlParser.parse(markup));
```

A registry is strict by default: `press="delte"` fails at inflation rather than
producing a button that silently does nothing.

Nothing visual lives in a widget. `:widgets` ships `controls.css` for the
toolkit-base layer, which sets the design system's metrics and reads
`var(--gb-*)` for every colour, so switching a theme restyles controls whose
rules never mention one:

```java
var renderer = new WidgetRenderer(
        List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), appStyles), font);
```

Variants are classes — `primary`, `danger`, `ghost` — because that is the one
spelling Java, KDL and CSS can all use. A button is focusable, activates on a
click and on `Space`/`Enter`, and does **not** activate when a press is dragged
off it and released elsewhere.

A button takes a label, an icon, or both, and can be disabled — which refuses
every route to its action, drops it out of the Tab order, and matches
`:disabled`:

```java
new Button("New", plusIcon, this::create, false, attributes)
new Button("Undo", null, this::undo, clicks == 0, attributes)
```

The icon is **borrowed**, not owned: a widget is a value that gets rebuilt every
frame and must not hold something with a `close()`, so the application builds its
icons once and keeps them, exactly as it keeps a `Font`. In markup an icon is
named rather than built, and resolves against a registry for the same reason:

```java
var icons = Icons.strict().bind("plus", plusIcon);
Controls.inflater(actions, icons).inflateAll(KdlParser.parse(markup));
```

### Values: `bind`

The other half of §9's wiring. `action` names something to do; `bind` names a
value to follow, and resolves against a third registry of the same shape:

```java
var status = Property.of("checking…");
var bindings = Bindings.strict().bind("app.status", status);

Controls.inflater(actions, icons, bindings).inflateAll(KdlParser.parse("""
        text bind="app.status" "…"
        """));

status.set("linux-x64, Wayland");   // every bound node redraws
```

A `Property<T>` is a cell with listeners — `get`, `set`, `subscribe` — and
nothing more: no computed values, no dependency graph, no streams. Setting the
value it already holds notifies nobody, which is what makes two properties
mirroring each other settle rather than recurse.

**A `bind` value is a dotted path and nothing else** — `frost`, `prefs.frost`.
`bind="!prefs.frost"` is refused at inflation with the text quoted, rather than
resolving to nothing and leaving a control that never updates
([ADR-0062](book/src/adr/0062-bind-is-a-path-and-nothing-else.md)); negation and
formatting stay in Java, where they are already testable. The argument stays as
the fallback, so a lenient registry — what a preview or a golden image uses —
draws `text bind="user.name" "Name here"` as *Name here*.

The binding lives on the widget and the subscription on its **element**, so a
bound node introduces no wrapper and `panel > text` styles it exactly like an
unbound one. A change marks that element as needing a build, by the same route
`setState` takes, so three changes in one frame cost one build.

**Binding is one-way, and the types enforce it.** What a widget is handed is an
`Observable` — a `Property` with no `set` on it — so markup can read a value and
watch it and cannot write it. What the user did travels back up the way it
already does, as an action:

```kdl
checkbox bind="prefs.frost" change="toggleFrost" "Enable frost"
```

The value flows down, the intent flows up, and the one line that mutates anything
is Java the application wrote. A control is therefore *controlled*: clicking a
checkbox does not move the tick, it raises a change, and the tick moves when the
handler sets the property — so a control that will not move means the state did
not change, which is where the bug is
([ADR-0063](book/src/adr/0063-data-flows-down-events-flow-up.md)).

So far the catalog is the five primitives (`text`, `row`, `column`, `panel`,
`spacer`) and `button`. Radii, borders, font weights and the focus ring are not
drawable yet, so `controls.css` states what it cannot express rather than
approximating it.

## Input

Pointer, wheel and keyboard events route through a `PointerRouter`, which holds
what input needs to remember between frames — who is hovered, who is pressed, who
has focus — and holds it against **elements**, because a widget is rebuilt
constantly and could not remember any of it.

Hit testing runs against the **snapshot taken while painting**, not a fresh
layout pass. A pointer event is about what the user can see, and what they can
see is the last frame that was drawn
([ADR-0054](book/src/adr/0054-hit-testing-runs-against-the-painted-frame.md)).

```java
var router = new PointerRouter();
window.pointerRouter(router);

window.onPaint(frame -> {
    var boxes = renderer.render(tree);
    BoxPainter.paint(frame, boxes);
    router.updateRegions(HitTest.capture(frame, boxes));   // what was just drawn
});
```

Dispatch is capture → target → bubble with `consume()`. A **press captures the
pointer** until the release, so a drag that leaves a widget still reaches it and
`:active` cannot get stuck — which is what makes a slider work
([ADR-0058](book/src/adr/0058-a-press-captures-the-pointer.md)).

Wheel deltas are in **lines**, fractional, positive down and right. SDL exposes
no pixel-precise axis; what a touchpad sends is a fraction of a detent per frame,
and that fraction is preserved. "Natural scrolling" and SDL's away-from-the-user
sign are both undone at the boundary, so a widget never sees either
([ADR-0056](book/src/adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md)):

```java
@Override
public void onPointer(PointerEvent event) {
    if (event.kind() == PointerEvent.Kind.WHEEL) {
        scrollBy(event.deltaY() * lineHeight);
        event.consume();          // and the page behind does not lurch
    }
}
```

That path is driven end to end in CI on all three platforms, under SDL's `dummy`
video driver: a test cannot turn a wheel, so a fabricated `SDL_MouseWheelEvent` is
pushed onto SDL's own queue with `SDL_PushEvent` and comes back out of the
ordinary pump
([ADR-0061](book/src/adr/0061-the-events-a-test-cannot-produce-are-pushed.md)).

Keys and committed text are separate, per §7.1 — one character can take several
keystrokes, and the platform's own compose and IME handling is what produces it
(ADR-0055). Accelerators are per window and fire **after** the focused widget has
declined the key, so a text field keeps its own `Ctrl+A`:

```java
router.shortcut("Ctrl+S", this::save)
      .shortcut("Ctrl+Shift+Z", this::redo);
```

The cursor is a property of the painted rectangle, set from CSS or from code, and
inherits down the stack of rectangles — so `cursor: pointer` on a button covers
the label inside it ([ADR-0057](book/src/adr/0057-the-cursor-rides-on-the-painted-box.md)):

```css
button { cursor: pointer; }
.splitter { cursor: ew-resize; }
```

```java
window.cursor(Cursor.WAIT);       // or decide it yourself
```

Not yet: arrow-key group navigation inside composites, custom image cursors
(`grab` and `grabbing` fall back to `move`, which no platform provides), and
menu items registering their own accelerators — that one waits for menus.

## Painting across threads

Blend2D rasterizes a frame by splitting it into horizontal bands, and Goldberry
uses up to four workers on any surface bigger than 400×300
([ADR-0042](book/src/adr/0042-blend2ds-workers-and-how-many.md)). Nothing in an
application changes: `Frame.end()` already ran before the frame was presented,
and that is where the bands are waited for.

On a real 960×640 frame that is 2.86 ms down to 2.15 ms; at 3840×2160 the
benchmark shows 6.0 ms down to 2.3 ms. `-Dgoldberry.paint.threads=0` restores
synchronous painting, and any other number pins the worker count.

Those are two different numbers on purpose. A frame painted in a benchmark loop
costs about a quarter of the same frame painted in a running window, because
`present` leaves the next paint's caches cold — so a figure from
`./gradlew :core:benchmark` compares options against each other, and only a
figure from a live window says what a frame costs
([ADR-0045](book/src/adr/0045-a-frame-is-not-a-benchmark-iteration.md)).

`present` is the larger half of a frame, and most of it is not Goldberry's. At
960×640 it is ~6.4 ms: 43 µs of this repo's code, ~1.05 ms of SDL copying the
frame into a texture, ~0.7 ms of render-and-present, and ~4.8 ms of blocking on
the swapchain ([ADR-0046](book/src/adr/0046-what-present-actually-does.md)).

Most of that block is the loop running ahead of the display. Goldberry asks SDL
to hold each present until vertical blank, which is the whole fix wherever the
GL stack honours it; `-Dgoldberry.backend.vsync=false` turns the request off.
Where it is ignored — a virtualized driver, `llvmpipe`, a deep swapchain — the
loop paces itself to the refresh rate it reads off the window's current display,
re-reading it whenever the window changes monitor and taking the fastest display
when several windows are open. Paced, present falls from 5.51 ms to 1.20 ms,
paint follows it down from 2.25 ms to 1.61 ms, and the UI thread spends 165 ms of
each second in the frame path instead of 862 — a fifth of the work, showing the
user the same frames, because the ~50 fps that vanished were never scanned out
([ADR-0047](book/src/adr/0047-a-frame-nobody-sees-costs-full-price.md)).

A display that will not report a rate leaves the loop unpaced rather than
guessing — capping a 144 Hz panel at an assumed 60 is worse than painting too
many frames. `-Dgoldberry.frame.rate=N` overrides, and `0` measures the
unthrottled loop.

### Resizing, where the platform takes the thread

Windows and macOS run a **modal loop** while a window is being dragged by its
edge: the platform keeps pumping events and does not return from the pump until
the drag ends, so a frame loop built around `SDL_WaitEventTimeout` does not
iterate and the window shows stale content for the length of the gesture. Wayland
and X11 have no such loop, which is why this is invisible on the platform the
toolkit is developed on.

SDL calls an **event watch** from inside whatever pump is running, so that is
where Goldberry draws: a resize arriving during a drag is translated, dispatched
and painted before the callback returns, and the copy the queue delivers when the
drag ends is coalesced away rather than laid out twice
([ADR-0060](book/src/adr/0060-a-resize-draws-from-inside-sdls-event-watch.md)).
Nothing in an application changes, and a `libgoldberry` built before the two
symbols were exported loses live resize rather than the ability to open a window.

## Run the showcase

`:example` is an ordinary subproject that runs on the module path, which is what
catches an unexported package or a wrong `--enable-native-access` (ADR-0023).

```sh
./gradlew :natives:cmakeBuild     # once, to build libgoldberry
./gradlew run
```

A window opens. It is a widget tree — a bar, a sidebar, wrapped prose, and a row
of buttons — styled by the cascade and driven by the input router, so hovering,
clicking, `Tab`, `Space` and `Ctrl+T` all do what they should.
`-Pgoldberry.example.frames=3` paints three frames and exits, which is what CI
runs under Xvfb.

### A self-contained image

For handing to someone with no JDK, no Gradle and nothing to remember
([ADR-0048](book/src/adr/0048-the-showcase-ships-as-a-runtime-image.md)):

```sh
./gradlew :example:showcaseImage
./example/build/jlink/goldberry-showcase-linux-x64/bin/showcase
```

That is a trimmed JDK from jlink, the application modules, `libgoldberry` and a
launcher — about 31 MB, and it runs from wherever it is unpacked. The directory
is named for the host target, so it is `goldberry-showcase-macos-aarch64` on a
Mac and `…-windows-x64\bin\showcase.bat` on Windows.

CI builds the same image on all three platforms and attaches it to the run: open
the **Showcase image** workflow, pick a run, and the artifacts are
`goldberry-showcase-linux-x64.tar.gz`, `-macos-aarch64.tar.gz` and
`-windows-x64.zip`. Unpack and run `bin/showcase`. (A tarball on the two Unix
platforms rather than a zip, because the zip format `upload-artifact` writes does
not carry the executable bit — an image whose `bin/java` cannot be run is not an
image.)

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
