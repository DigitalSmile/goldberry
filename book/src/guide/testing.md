# Testing an application

<p class="gb-lede">Render a widget tree to pixels with no display, step a virtual clock, drive input through the real router, and compare pictures with a tolerance that absorbs antialiasing and nothing else.</p>

By the end of this chapter you can assert that your documents inflate against
your models, photograph a screen in a JUnit test, step an animation to a
known frame, click a button through the router, answer a dialog, find a
widget by id or role, assert that nothing overruns its box, and skip cleanly
on a machine with no native library.

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
backend, no SDL, no compositor.

## What ships, and what is test-scope

| | Where | An application can use it |
|---|---|---|
| `Offscreen` and its `Session`, `Image`, `Clock.virtual()`, `ElementTree`, `WidgetRenderer`, `RenderTree`, `PointerRouter`, `HitTest`, `Frame.over`, `PixelBuffer.allocate` | `goldberry-core` | yes |
| `Host` | `goldberry-core`, an interface | yes: implement or proxy it for a test |
| `GoldenImage`, `Tolerance`, `ScaleInvariance`, `RendererRequirement`, `TestClock`, `TestFrames` | `core`'s test fixtures | no. The fixtures are not published |
| `TestHost`, `TestLoop`, `CatalogMarkup` | `widgets`' tests | no |
| the `headless` backend | `core`, package-private | no |

Everything below uses only the first two rows.

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
build them. The result is a list of widgets a test can walk to assert which
node types a document holds.

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

One reference set serves every platform when it is compared with a
per-channel tolerance of 2 in 256 and a cap of 2% of pixels allowed to differ
at all. Blend2D
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
goldens in your repository as PNGs, and give the test a system property of
your own that rewrites them instead of asserting. The review is then
`git diff --stat`.

### Determinism rules

A golden is only a golden if the same tree draws the same bytes everywhere.
Embedded fonts only, a fixed scale factor, the virtual clock, a seeded random
source, and no wall-clock or locale dependence. A widget that reads
`System.nanoTime()` or `LocalDate.now()` in `build` is not photographable.
A second check redraws the tree at 2x and 1.5x and asks whether the pictures
describe the same thing, which is a second question rather than a second set
of files.

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
is then the exact frame at 50 ms, on every machine.

## Driving input

A widget test hands an event straight to the widget. A screen test opens a
session and drives it the way a user does: through the router, against the
frame it laid out, which is the shipping path.

```java
try (var session = Offscreen.of(800, 600)
        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
        .session(new SettingsScreen(settings, actions))) {
    session.click("apply");                       // by id
    session.click(session.byRole(Role.BUTTON, "Reset").orElseThrow());
    session.focus("name");
    session.type("Deploy Orc");
    session.key(Key.ENTER);
    session.key("Ctrl+S");                        // as a menu prints it
    assertEquals(1, settings.saves());
}
```

`Offscreen.session(root)` mounts the tree once, as `strip` does, and keeps
it until `close()`. After every piece of input it builds, lays out and
captures the regions, without painting, so the next event is answered
against what the last one changed, as a window answers it between two
frames. `frame()` is when there is a picture.

`click(id)` presses at the centre of what is visible of that node and
**refuses** when something else would take the press: a dialog's scrim, a
toast, a sibling drawn over it. The exception names what is on top. A click
that a user could not make is never quietly delivered somewhere else.
`click(x, y)` presses wherever it is told. `hover`, `wheel`, `type` (text
committed in one piece) and `key` round it out. `router()` is there for a
gesture the session does not spell out, such as a drag.

Input does not move the clock. `advance(Duration)` does, and it stops at
every timer on the way, so a dialog's closing animation ends, and its
handler runs, at the moment it would in a window.

A press on a disabled widget sets nothing. Set
`-Dgoldberry.input.primary=ctrl` in your test task, so a test that presses
`Ctrl+C` is the same test on macOS.

The router underneath is public too. `pointerMoved`, `pointerPressed`,
`pointerReleased` and `pointerWheel` take window coordinates;
`keyPressed(Key.TAB, Modifiers.NONE, false)` and `keyReleased` move focus
and fire accelerators; `textInput("a")` is committed text. A test of one
widget with no window around it can drive a `PointerRouter` over its own
`ElementTree`, calling `router.updateRegions(HitTest.capture(render))`
after each layout.

### A host for a test

A session's tree is built with a host, so `BuildContext.host()` answers and
an application's own `Dialogs.show(host, dialog)` works unchanged:

```java
session.click("delete");                          // the screen opens a dialog
assertEquals(1, session.overlays().size());
session.click(session.byRole(Role.BUTTON, "Delete").orElseThrow());
session.advance(Duration.ofMillis(300));          // past the closing animation
assertEquals("deleted", screen.answer());
```

`fill` and `overlay` put the widget on the overlay layer the window root
draws, so the dialog is painted over the content, takes the pointer, and is
taken off by its own handle. `after` is a timer on the session's virtual
clock. Focus by id, accelerators and `isModal()` are the router's.
There are no popup windows, which is the real answer under SDL's `dummy`
driver, and no tray, web view, clipboard or file dialogs. `window()`
throws. `session.host()` is the same host, for a test that opens something
itself.

A test of one widget can implement `Host` itself and hand it to
`ElementTree(root, host)`, recording what it was asked for. Answer `clock()`
with `Clock.virtual()` and `popup(...)` with `Optional.empty()`.
`OverlayLayer` is the list a `WindowRoot` draws and the door overlays go
through, so such a host's `fill` can return an overlay whose `remove()`
works.

## Finding a widget

```java
session.byId("apply");                            // Optional<Element>, drawn or not
session.byRole(Role.BUTTON);                      // every button, in tree order
session.byRole(Role.BUTTON, "Apply");             // the one a reader calls Apply
session.elementAt(120, 48);                       // the topmost node drawn there
session.regions();                                // every rectangle, in paint order
session.focused();                                // and hovered()
```

Every focusable widget in the catalogue implements `Semantics` with a `Role`
and an accessible name, so `byRole(Role.BUTTON, "Apply")` finds the button
without a pixel. The queries search the overlays as well as the content. An
element has `id()`, `type()`, `classes()`, `widget()` and `children()`, and
`type()` is the CSS type, which is also the markup name.

## Overruns

```java
assertEquals(List.of(), session.overruns());
```

An overrun is a box laid out past the box it is in: a status line pushed out
of a 600-pixel dialog, a button off the end of its row. The layout pass
checks every subtree it lays out and logs each new shape once, as a
warning, through `OverflowLog`. That log is process-wide and says each
shape only once, so whether it is empty depends on every test before
yours.

`session.overruns()`, and `RenderTree.overruns()` under it, walk the whole
tree as the last frame laid it out, log nothing, and give the same answer
every time. An empty list is evidence. The exemptions are the log's: a box
that clips on purpose (`overflow` other than `visible`), a child placed by
insets, and a pixel or two of rounding.

## Running the launcher without a display

`Goldberry.launch(app, new String[] {"--frames=3"})` paints three frames and
exits. With `-Dgoldberry.backend.videoDriver=dummy` SDL opens no window, so
that is a smoke test of the whole front door: the window spec, `start`,
`root`, the first frames and `stop`. It needs the native library, and under
`dummy` every `host.popup(...)` answers empty.

## Skipping without the native library

A test that rasterizes needs `libgoldberry`. On a machine without it, the
first paint throws `UnsatisfiedLinkError`, and a missing library is an
ordinary state on a developer's machine where a broken one is not.
`RendererRequirement` is a fixture, so an application writes the check
itself: try a two-by-two raster and abort the test on `UnsatisfiedLinkError`,
`NoClassDefFoundError` or `ExceptionInInitializerError`.

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

- [Tests and gates](../contributing/testing.md): how the toolkit tests itself
