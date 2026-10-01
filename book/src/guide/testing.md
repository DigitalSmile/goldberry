# Testing an application

<p class="gb-lede">Render a widget tree to pixels with no display, step a virtual clock, drive input through the real router, and compare pictures with a tolerance that absorbs antialiasing and nothing else.</p>

By the end of this chapter you can assert that your documents inflate against
your models, photograph a screen in a JUnit test, step an animation to a
known frame, click a button through the router, find a widget by id or role,
and skip cleanly on a machine with no native library.

```java
@Test
void theSettingsScreenRenders() {
    byte[] png = Offscreen.of(800, 600)
            .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
            .render(new SettingsScreen(settings, actions))
            .encodePng();
    assertArrayEquals(Files.readAllBytes(golden("settings.png")), png);   // or a tolerant compare
}
```

Nothing opens. `Offscreen` runs the window's own sequence into memory: no
backend, no SDL, no compositor
([ADR-0284](../adr/0284-a-picture-with-no-window-under-it.md)).

## What ships, and what is test-scope

| | Where | An application can use it |
|---|---|---|
| `Offscreen`, `Image`, `Clock.virtual()`, `ElementTree`, `WidgetRenderer`, `RenderTree`, `PointerRouter`, `HitTest`, `Frame.over`, `PixelBuffer.allocate` | `goldberry-core` | yes |
| `Host` | `goldberry-core`, an interface | yes: implement or proxy it for a test |
| `GoldenImage`, `Tolerance`, `ScaleInvariance`, `RendererRequirement`, `TestClock`, `TestFrames` | `core`'s test fixtures | no. The fixtures are shared inside this repository and are deliberately not published |
| `TestHost`, `TestLoop`, `CatalogMarkup` | `widgets`' tests | no |
| the `headless` backend | `core`, package-private | no. It is how the toolkit tests its own front door |

Everything below uses only the first two rows, and says so where the
toolkit's own tests use a fixture instead.

## Documents inflate against the real models

```java
@Test
void everyDocumentResolvesItsNames() {
    var app = new Hello();
    var inflater = Widgets.inflater(Icons.lenient(), app.models().toArray());
    for (var name : List.of("window.kdl", "settings.kdl")) {
        var nodes = KdlParser.resource(Hello.class, name);
        assertDoesNotThrow(() -> inflater.inflateAll(nodes), name);
    }
}
```

The registries a model publishes are strict, so a `press=` or a `bind=`
that names nothing fails here with the text quoted, rather than on the
window's first frame. Icons are lenient because a test has no reason to
build them. The showcase's `ShowcaseDocumentsTest` does exactly this over
its five documents, and walks the result to assert which node types each
one holds.

## Pictures

`Offscreen.of(width, height)` is in physical pixels. `.scale(1.5f)` changes
how much content fits in them, `.stylesheets(...)` sets the cascade,
`.settle(ms)` moves the virtual clock between the two passes, 200 ms by
default, and `.background(argb)` fills under a tree whose root paints
nothing. `.render(widget)` runs three passes, because a widget is told what
size it came out as after layout and may rebuild in response. `.paint(painter)`
draws a painter instead.

The clock is frozen, so two renders of one tree are the same bytes. A
spinner is at whatever it is at 200 ms, on every machine. That is what makes
a picture cacheable and a golden image possible.

### Comparing with a tolerance

Goldberry's own goldens are one reference set shared by every platform, and
they are compared with a per-channel tolerance of 2 in 256 and a cap of 2% of
pixels allowed to differ at all
([ADR-0050](../adr/0050-golden-images-have-a-tolerance.md)). Blend2D
compiles its pipelines for the CPU it finds, and AVX2, SSE2 and NEON agree on
what they draw and not on the last bit of a blended edge. The tolerance
absorbs antialiased edges and nothing else: a colour that changed or a box in
the wrong place moves thousands of pixels by tens of levels.

`GoldenImage` is a test fixture, so an application writes the comparison:

```java
static void assertClose(Image expected, Image actual) {
    assertEquals(expected.width(), actual.width());
    assertEquals(expected.height(), actual.height());
    int differing = 0;
    for (var y = 0; y < actual.height(); y++) {
        for (var x = 0; x < actual.width(); x++) {
            if (channelsApart(expected.argb(x, y), actual.argb(x, y)) > 2) {
                differing++;
            }
        }
    }
    assertTrue(differing <= 0.02 * actual.width() * actual.height(), differing + " pixels differ");
}
```

`Image.decode(Files.readAllBytes(golden))` reads the reference back. Keep
goldens in the repository as PNGs, and give the test a system property of
your own that rewrites them instead of asserting. This repository's is
`-Dgoldberry.golden.update=true`, and `./gradlew blessGoldens` runs every
module's goldens with it set. The review is then `git diff --stat`.

### Determinism rules

A golden is only a golden if the same tree draws the same bytes everywhere.
Embedded fonts only, a fixed scale factor, the virtual clock, a seeded random
source, and no wall-clock or locale dependence. A widget that reads
`System.nanoTime()` or `LocalDate.now()` in `build` is not photographable.
Goldberry's own goldens are also redrawn at 2x and 1.5x and checked for
describing the same picture, which is a second question rather than a
second set of files ([ADR-0162](../adr/0162-a-golden-is-checked-at-every-scale.md)).

## The virtual clock

```java
var clock = Clock.virtual();
var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), fonts).clock(clock);

renderer.render(tree);          // frame 0: the transition starts
clock.advance(50);              // exactly 50 ms of a 100 ms transition
renderer.render(tree);
```

`Clock.virtual()` is a clock that moves only when told to, in milliseconds.
A `WidgetRenderer` takes one with `.clock(clock)`, and a `Host` answers one
from `clock()`, so a widget that asks how long ago something happened can be
asked what happens after a timeout without sleeping. A mid-transition frame
is then the exact frame at 50 ms, on every machine
([ADR-0067](../adr/0067-motion-is-an-overlay-on-a-frame-clock.md)).

## Driving input

A widget test hands an event straight to the widget. A screen test goes
through the router, against the frame it painted, which is the shipping
path.

```java
var fonts    = Fonts.bundled();
var tree     = new ElementTree(new SettingsScreen(settings, actions));
var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), fonts);
var render   = RenderTree.create();
var router   = new PointerRouter();
var buffer   = PixelBuffer.allocate(PhysicalSize.of(800, 600), PixelFormat.BGRA32_PREMULTIPLIED);

Runnable frame = () -> {
    var surface = Frame.over(buffer, DisplayScale.ONE);
    renderer.prepare(tree);
    render.update(surface, renderer.render(tree));
    render.paint(surface);
    surface.end();
    router.updateRegions(HitTest.capture(render));
};

frame.run();
router.pointerMoved(120, 48);
router.pointerPressed(120, 48, PointerEvent.Button.PRIMARY, 1);
router.pointerReleased(120, 48, PointerEvent.Button.PRIMARY, 1);
frame.run();

assertEquals(1, settings.clicks());
```

`pointerMoved`, `pointerPressed`, `pointerReleased` and `pointerWheel` take
window coordinates. `keyPressed(Key.TAB, Modifiers.NONE, false)` and
`keyReleased` move focus and fire accelerators; `textInput("a")` is committed
text; `moveFocus(1)` is `Tab` without the key. The router remembers who is
hovered, pressed and focused between calls, and `router.focused()`,
`hovered()` and `pressed()` read it back.

A press on a disabled widget sets nothing, and a popup that a test opens
through a `Host` of its own is a widget tree it can inflate again. The
toolkit's own tests run with `-Dgoldberry.input.primary=ctrl` on every
runner, so a test that presses `Ctrl+C` is the same test on macOS
([ADR-0396](../adr/0396-a-test-presses-the-same-modifier-on-every-desktop.md)).
Set the same property in your test task.

### A host for a test

`Host` is an interface, and `ElementTree(root, host)` takes one. A test
implements the methods its widgets call and records what it was asked for.
The showcase's `RecordingHost` is a `Proxy` over `Host` that keeps every
widget handed to `fill`, which is how a test sees a dialog that is not in
the tree that opened it. Answer `clock()` with `Clock.virtual()`, and
`popup(...)` with `Optional.empty()`, which is the real answer under SDL's
`dummy` driver.

## Finding a widget

There is no query API. An element has `id()`, `type()`, `classes()`,
`widget()` and `children()`, and a walk is a few lines:

```java
static Optional<Element> byId(Element from, String id) {
    if (id.equals(from.id())) return Optional.of(from);
    for (var child : from.children()) {
        var found = byId(child, id);
        if (found.isPresent()) return found;
    }
    return Optional.empty();
}

static Stream<Element> byRole(Element from, Role role) {
    var own = from.widget() instanceof Semantics s && s.role() == role ? Stream.of(from) : Stream.<Element>empty();
    return Stream.concat(own, from.children().stream().flatMap(child -> byRole(child, role)));
}
```

Every focusable widget in the catalogue implements `Semantics` with a `Role`
and an accessible name, so `byRole(root, Role.BUTTON)` and a match on
`accessibleName()` finds "Apply" without a pixel. `type()` is the CSS type,
which is also the markup name.

## Running the launcher without a display

`Goldberry.launch(app, new String[] {"--frames=3"})` paints three frames and
exits. With `-Dgoldberry.backend.videoDriver=dummy` SDL opens no window, so
that is a smoke test of the whole front door: the window spec, `start`,
`root`, the first frames and `stop`. It needs the native library, and under
`dummy` every `host.popup(...)` answers empty
([ADR-0102](../adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)).

## Skipping without the native library

A test that rasterizes needs `libgoldberry`. On a machine without it, the
first paint throws `UnsatisfiedLinkError`, and a missing library is an
ordinary state on a contributor's machine where a broken one is not
([ADR-0357](../adr/0357-a-test-that-paints-asks-for-the-library-and-a-download-asks-twice.md)).
This repository's tests call `RendererRequirement.enforce()`, which tries a
two-by-two raster and aborts the test on `UnsatisfiedLinkError`,
`NoClassDefFoundError` or `ExceptionInInitializerError`. It is a fixture, so
an application writes the same five lines:

```java
@BeforeEach
void needsARasterizer() {
    try {
        Offscreen.of(2, 2).paint((frame, size) -> frame.fill(0xFF000000));
    } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
        Assumptions.abort("no libgoldberry here: " + e);
    }
}
```

`-Dgoldberry.native.library=/path/to/libgoldberry.so` points a test JVM at
a library that is not in a jar on its path.

## Read more

- [ADR-0050](../adr/0050-golden-images-have-a-tolerance.md): the tolerance
- [ADR-0067](../adr/0067-motion-is-an-overlay-on-a-frame-clock.md): the virtual clock
- [ADR-0284](../adr/0284-a-picture-with-no-window-under-it.md): `Offscreen`
- [ADR-0357](../adr/0357-a-test-that-paints-asks-for-the-library-and-a-download-asks-twice.md): skipping without the library
- [Tests and gates](../contributing/testing.md): how this repository tests itself
