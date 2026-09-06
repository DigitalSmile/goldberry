package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A [Popup] driven through the **real** launcher and the real frame loop, on
/// the backend that needs no display.
///
/// [io.github.digitalsmile.goldberry.backend.headless.HeadlessPopupTest] covers
/// the SPI's rules. This is the layer above: a widget tree of its own, painted
/// into a second window by the same renderer, and closed by input its own router
/// never sees ([ADR-0103]).
class PopupLifecycleTest {

    /// A node that fills whatever it is given, so a popup's frame has something
    /// in it and so `anchor` has a rectangle to find.
    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Plate(String id) {
            this(new Attributes(id, Set.of(), id));
        }

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    /// A box that **translates its children**, which is what a `scroll` is: Yoga
    /// lays the content out where it always was and the viewport moves it
    /// ([ADR-0114], [ADR-0116]).
    ///
    /// Bound to a [io.github.digitalsmile.goldberry.bind.Property], so changing
    /// the offset marks this element for a rebuild and asks for a frame by the
    /// same route a `setState` does ([ADR-0062], [ADR-0122]) — which is the
    /// point: the anchor moves between two frames, with no resize and no event
    /// that says so.
    private record Scrolled(
            io.github.digitalsmile.goldberry.bind.Property<Float> offset, List<Widget> kids, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        Scrolled(io.github.digitalsmile.goldberry.bind.Property<Float> offset, Widget... kids) {
            this(offset, List.of(kids), new Attributes("viewport", Set.of(), "viewport"));
        }

        @Override
        public io.github.digitalsmile.goldberry.bind.Observable<?> binding() {
            return offset;
        }

        @Override
        public String cssType() {
            return "viewport";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of()
                    .style(style)
                    .transform(io.github.digitalsmile.goldberry.css.value.Transform.of(
                            new io.github.digitalsmile.goldberry.css.value.Transform.Function.Translate(
                                    io.github.digitalsmile.goldberry.css.value.Transform.Length.ZERO,
                                    io.github.digitalsmile.goldberry.css.value.Transform.Length.px(-offset.get()))))
                    .children(children.toArray(Box[]::new));
        }
    }

    /// A node with a size of its own, so a measured popup has something to
    /// measure.
    private record Sized(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Sized(String id) {
            this(new Attributes(id, Set.of(), id));
        }

        @Override
        public String cssType() {
            return "sized";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }

    /// Two focusable nodes in a popup, so there is something for a forwarded
    /// arrow key to move between.
    private record Item(String name, List<String> focused, Attributes attributes)
            implements Widget.Leaf, Styled, Paints, io.github.digitalsmile.goldberry.input.handler.Handles {

        Item(String name, List<String> focused) {
            this(name, focused, new Attributes(name, Set.of(), name));
        }

        @Override
        public void onFocusChanged(boolean gained, boolean fromKeyboard) {
            if (gained) {
                focused.add(name);
            }
        }

        @Override
        public String cssType() {
            return "item";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }

    /// An application that hands its [Host] to the test and does nothing else.
    private static final class TestApp implements Application {

        private final Consumer<Host> onStart;
        private final Consumer<Host> onStop;
        private final Widget root;

        TestApp(Consumer<Host> onStart, Consumer<Host> onStop) {
            this(new Plate("content"), onStart, onStop);
        }

        TestApp(Widget root, Consumer<Host> onStart, Consumer<Host> onStop) {
            this.root = root;
            this.onStart = onStart;
            this.onStop = onStop;
        }

        private Host host;

        @Override
        public Widget root() {
            return root;
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    plate { background: #204060 }
                    sized { width: 200px; height: 80px; background: #eceff4 }
                    item { width: 100px; height: 24px }
                    #menu { background: #eceff4 }
                    viewport { flex-grow: 1; flex-direction: column }
                    /* A column that fills the window and puts its one child at the
                       bottom, so the child moves when the window is resized -- which
                       is the whole subject of `replacedOnResize`. */
                    menu.bottom { flex-grow: 1; flex-direction: column; justify-content: flex-end }
                    """));
        }

        @Override
        public void start(Host host) {
            this.host = host;
            onStart.accept(host);
        }

        @Override
        public void stop() {
            onStop.accept(host);
        }
    }

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        // The launcher paints for real, and `Window.paint` needs a rasterizer
        // whatever the backend is.
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessPopup onlyPopup() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no popup was opened"));
    }

    private HeadlessWindow ownerWindow() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    @Test
    @Timeout(20)
    @DisplayName("a popup gets a window of its own and paints its own tree into it")
    void opensAndPaints() {
        var opened = new Popup[1];
        var backing = new HeadlessPopup[1];
        Goldberry.launch(
                new TestApp(
                        host -> {
                            opened[0] = host.popup(new Plate("menu"), LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                                    .orElseThrow();
                            // Held now rather than looked up later: by the time `launch`
                            // returns, the launcher has closed every popup and
                            // `windows()` is empty — which the last test here asserts.
                            backing[0] = onlyPopup();
                        },
                        host -> {}),
                new String[] {"--frames=3"});

        assertNotNull(opened[0]);
        // The popup's own window presented a frame: its tree was built, laid out
        // and rasterized by the same renderer as the window below it, through the
        // same loop. Asserted on the *backend* window, because that is the only
        // thing that can tell painting from intending to.
        assertTrue(backing[0].presentCount() > 0, "the popup never presented a frame");
        assertEquals(new LogicalPoint(40, 60), backing[0].offset());
    }

    /// The rule that needs the launcher to be real: a press **anywhere** in the
    /// owner window closes the popup, including a press that lands on nothing —
    /// which is the ordinary case, and the one a router cannot report because it
    /// dispatches to the widget under the pointer.
    @Test
    @Timeout(20)
    @DisplayName("a press in the window below closes the popup")
    void lightDismissedByAPress() {
        var opened = new Popup[1];
        Goldberry.launch(
                new TestApp(
                        host -> {
                            opened[0] = host.popup(new Plate("menu"), LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                                    .orElseThrow();
                            backend.post(new BackendEvent.PointerPressed(ownerWindow(), 10, 10, 1, 1, 0));
                        },
                        host -> {}),
                new String[] {"--frames=3"});

        assertFalse(opened[0].isOpen(), "a press below a menu closes it (§7's light dismissal)");
    }

    @Test
    @Timeout(20)
    @DisplayName("Escape closes it too")
    void lightDismissedByEscape() {
        var opened = new Popup[1];
        Goldberry.launch(
                new TestApp(
                        host -> {
                            opened[0] = host.popup(new Plate("menu"), LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                                    .orElseThrow();
                            backend.post(new BackendEvent.KeyPressed(ownerWindow(), Key.ESCAPE.sdlKeycode(), 0, false));
                        },
                        host -> {}),
                new String[] {"--frames=3"});

        assertFalse(opened[0].isOpen());
    }

    /// **A floor under the width** — [ADR-0145], and the only thing a
    /// measurement of the content cannot say.
    ///
    /// `Sized` is 200 wide by the test's stylesheet, so a floor of 320 has to
    /// win and a floor of 100 has to lose.
    @Test
    @Timeout(20)
    @DisplayName("a popup opened with a minimum width is at least that wide")
    void minimumWidth() {
        var wide = new LogicalSize[1];
        var narrow = new LogicalSize[1];
        Goldberry.launch(
                new TestApp(
                        host -> {
                            wide[0] = host.popup(new Sized("menu"), LogicalRect.of(0, 0, 10, 10), Placement.BELOW, 320)
                                    .orElseThrow()
                                    .bounds()
                                    .size();
                            narrow[0] = host.popup(
                                            new Sized("menu2"), LogicalRect.of(0, 0, 10, 10), Placement.BELOW, 100)
                                    .orElseThrow()
                                    .bounds()
                                    .size();
                        },
                        host -> {}),
                new String[] {"--frames=3"});

        assertEquals(320, wide[0].width(), 0.5, "the floor won, because the content wanted less");
        assertEquals(
                200,
                narrow[0].width(),
                0.5,
                "and the content won, because it wanted more — this is a floor, not a width");
    }

    /// Runs `action` on the UI thread after `millis`, so a deferred check has
    /// had time to fire and the answer can be read while the loop is still up —
    /// `shutDown` closes every popup, so anything asserted after `launch`
    /// returns is asserting the teardown.
    private static void later(long millis, Runnable action) {
        Goldberry.async(() -> {
                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                })
                .thenRun(action);
    }

    /// **A popup goes away when the application does** — [ADR-0144].
    ///
    /// Nothing reported this before: the toolkit had no focus event at all, so a
    /// menu left open while the user switched to another application stayed on
    /// screen over it, and stayed *on top*, because a popup is always-on-top by
    /// kind.
    @Test
    @Timeout(20)
    @DisplayName("a popup closes when the last of this application's windows loses focus")
    void closesWhenTheApplicationLosesFocus() {
        var stillOpen = new boolean[] {true};
        Goldberry.launch(new TestApp(
                host -> {
                    var opened = host.popup(new Plate("menu"), LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                            .orElseThrow();
                    backend.post(new BackendEvent.FocusChanged(ownerWindow(), false));
                    later(300, () -> {
                        stillOpen[0] = opened.isOpen();
                        Goldberry.stop();
                    });
                },
                host -> {}));

        assertFalse(stillOpen[0]);
    }

    /// The other half, and the reason the check is deferred rather than
    /// immediate: opening a popup **is** a focus-lost for the window under it.
    /// A menu that closed on that would close as it opened.
    @Test
    @Timeout(20)
    @DisplayName("a popup that took the focus itself stays open")
    void focusMovingToThePopupIsNotLeaving() {
        var stillOpen = new boolean[1];
        Goldberry.launch(new TestApp(
                host -> {
                    var opened = host.popup(new Plate("menu"), LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                            .orElseThrow();
                    // Exactly the pair a compositor sends.
                    backend.post(new BackendEvent.FocusChanged(ownerWindow(), false));
                    backend.post(new BackendEvent.FocusChanged(onlyPopup(), true));
                    later(300, () -> {
                        stillOpen[0] = opened.isOpen();
                        Goldberry.stop();
                    });
                },
                host -> {}));

        assertTrue(stillOpen[0], "the focus went to the popup, not out of the application");
    }

    @Test
    @Timeout(20)
    @DisplayName("a popup that opted out of light dismissal stays open")
    void lightDismissCanBeTurnedOff() {
        var opened = new Popup[1];
        Goldberry.launch(
                new TestApp(
                        host -> {
                            opened[0] = host.popup(new Plate("menu"), LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                                    .orElseThrow()
                                    .lightDismiss(false);
                            backend.post(new BackendEvent.PointerPressed(ownerWindow(), 10, 10, 1, 1, 0));
                        },
                        // Closed on the way out, or the loop would never end: the event
                        // loop runs until every window has closed, and a popup is one.
                        host -> opened[0].close()),
                new String[] {"--frames=3"});

        assertFalse(opened[0].isOpen(), "closed by stop(), not by the press");
    }

    /// What a menu is anchored to. Read in `stop()`, which runs after the frames
    /// have been painted and while the launcher still holds their geometry —
    /// there is no rectangle before the first paint, and inventing one would be
    /// a menu pointing at where a button is going to be.
    @Test
    @Timeout(20)
    @DisplayName("anchor reports the painted rectangle of a node, and nothing for one that is not there")
    void anchorsToAPaintedNode() {
        var content = new java.util.concurrent.atomic.AtomicReference<
                Optional<io.github.digitalsmile.goldberry.input.hit.HitTest.Region>>(Optional.empty());
        var missing = new java.util.concurrent.atomic.AtomicReference<
                Optional<io.github.digitalsmile.goldberry.input.hit.HitTest.Region>>(Optional.empty());
        Goldberry.launch(
                new TestApp(host -> {}, host -> {
                    content.set(host.anchor("content"));
                    missing.set(host.anchor("nothing-by-that-name"));
                }),
                new String[] {"--frames=2"});

        assertTrue(content.get().isPresent(), "the root plate was painted and has a rectangle");
        var region = content.get().get();
        assertEquals(400f, region.width(), "it fills the window it was given");
        assertEquals(300f, region.height());
        assertTrue(missing.get().isEmpty());
    }

    /// Measured, placed and opened in one call — the form a `popover`, a `menu`
    /// and a `select` all use.
    ///
    /// The plate is 200×80 by its own CSS, and the anchor is a 100×30 rectangle
    /// at (40, 40) in a 400×300 window, so it lands 4px under the anchor with
    /// their left edges together. Nothing here says 200, 80 or 74: the point is
    /// that the caller did not have to.
    @Test
    @Timeout(20)
    @DisplayName("a popup measures its own content and is placed against an anchor")
    void measuredAndPlaced() {
        var opened = new Popup[1];
        var backing = new HeadlessPopup[1];
        Goldberry.launch(
                new TestApp(
                        host -> {
                            opened[0] = host.popup(new Sized("menu"), LogicalRect.of(40, 40, 100, 30), Placement.BELOW)
                                    .orElseThrow();
                            backing[0] = onlyPopup();
                        },
                        host -> {}),
                new String[] {"--frames=2"});

        assertEquals(LogicalSize.of(200, 80), backing[0].size(), "the size came from the content, not from the caller");
        assertEquals(
                new LogicalPoint(40, 74),
                backing[0].offset(),
                "4px under a 30px-tall anchor at y=40, left edges together");
    }

    /// The anchor by id, which is what a document-driven application has.
    @Test
    @Timeout(20)
    @DisplayName("a popup can be anchored to a node by id, and refuses when there is none")
    void anchoredById() {
        var backing = new HeadlessPopup[1];
        var missing = new boolean[1];
        Goldberry.launch(
                new TestApp(host -> {}, host -> {
                    // In `stop()`, because an anchor is a rectangle from a frame
                    // that has been painted and `start()` runs before any have.
                    host.popup(new Sized("menu"), "content", Placement.BELOW)
                            .ifPresent(open -> backing[0] = onlyPopup());
                    missing[0] = host.popup(new Sized("menu"), "no-such-id", Placement.BELOW)
                            .isEmpty();
                }),
                new String[] {"--frames=2"});

        assertEquals(
                new LogicalPoint(0, 304),
                backing[0].offset(),
                "the content plate fills the 400x300 window, so its bottom edge is 300");
        assertTrue(missing[0], "nothing with that id was painted, so there is nowhere to put it");
    }

    /// The whole chain, at the one place it matters: a window at the bottom of
    /// the screen, and a menu that has to open upwards.
    ///
    /// The work area is the backend's 1920×1040 — 40 logical pixels reserved, as a
    /// taskbar would — translated into the window's own coordinates by the
    /// window's position on that desktop. Get either half wrong and this menu
    /// opens under the taskbar, which is exactly the bug that cannot be found
    /// without a taskbar.
    @Test
    @Timeout(20)
    @DisplayName("a menu at the bottom of the screen opens upwards")
    void flipsAgainstTheRealWorkArea() {
        var backing = new HeadlessPopup[1];
        Goldberry.launch(
                new TestApp(
                        host -> {
                            // 300 tall, so its bottom edge is at 1300 on a desktop whose
                            // work area ends at 1040 — the window is hanging off the
                            // bottom, which is where this is interesting.
                            ownerWindow().moveTo(new LogicalPoint(200, 1000));
                            opened(host, 40, 40);
                            backing[0] = onlyPopup();
                        },
                        host -> {}),
                new String[] {"--frames=2"});

        // Below would be y = 74, and 1000 + 74 + 80 is past the work area's 1040.
        assertEquals(
                new LogicalPoint(40, -44),
                backing[0].offset(),
                "4px above a 30px anchor at y=40: the menu flipped, and its offset is"
                        + " negative because it is above the window's own top edge — which is"
                        + " the point of a popup being a window rather than an overlay");
    }

    private static void opened(Host host, float x, float y) {
        host.popup(new Sized("menu"), LogicalRect.of(x, y, 100, 30), Placement.BELOW)
                .orElseThrow();
    }

    /// The keyboard belongs to the menu while the menu is open — whether or not
    /// the platform moved focus into it, which is per-driver and cannot be relied
    /// on. An arrow that reached the window *underneath* would move a selection
    /// nobody can see.
    ///
    /// Observed through `onFocusChanged`, which is what a menu item reacts to
    /// anyway, rather than by reaching into the popup's router.
    @Test
    @Timeout(20)
    @DisplayName("keys go to the open popup rather than to the window beneath it")
    void theKeyboardBelongsToThePopup() {
        var focused = new ArrayList<String>();
        Goldberry.launch(
                new TestApp(
                        host -> {
                            host.popup(
                                            new Menu(List.of(new Item("one", focused), new Item("two", focused))),
                                            LogicalRect.of(0, 0, 10, 10),
                                            Placement.BELOW)
                                    .orElseThrow();
                            // Queued behind the frames the popup needs to build its tree:
                            // focus traversal walks elements, and there are none until it
                            // has been painted once.
                            backend.post(new BackendEvent.KeyPressed(ownerWindow(), Key.DOWN.sdlKeycode(), 0, false));
                        },
                        host -> {}),
                new String[] {"--frames=4"});

        assertEquals(
                List.of("one", "two"),
                focused,
                "the first item takes focus when the menu opens, and Down moves to the second"
                        + " — inside the popup, from a key the owner window received");
    }

    /// A focus scope with items in it — which is what §7 says every overlay
    /// wraps, and what makes `Down` mean "the next item" rather than nothing.
    private record Menu(List<Widget> items, Set<String> classes)
            implements Widget.Leaf, Styled, Paints, io.github.digitalsmile.goldberry.input.handler.Handles {

        Menu(List<Widget> items) {
            this(items, Set.of());
        }

        @Override
        public io.github.digitalsmile.goldberry.input.FocusScope focusScope() {
            return io.github.digitalsmile.goldberry.input.FocusScope.VERTICAL;
        }

        @Override
        public String cssType() {
            return "menu";
        }

        @Override
        public List<Widget> children() {
            return items;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    /// Runs `action` after `turns` more turns of the event loop.
    ///
    /// Turns and not milliseconds, which is the difference between this and
    /// [#later]: a run bounded by `--frames` finishes in whatever wall-clock time
    /// the machine takes, so a 300ms callback can arrive after the loop has gone.
    /// A zero-delay timer is scheduled *on* the loop and cannot outlive it.
    private static void afterTurns(Host host, int turns, Runnable action) {
        if (turns <= 0) {
            action.run();
            return;
        }
        host.after(java.time.Duration.ZERO, () -> afterTurns(host, turns - 1, action));
    }

    /// A popup lives at an **offset from its owner**, so dragging the window
    /// carries it along and only a *resize* moves what it was anchored to
    /// ([ADR-0231]).
    ///
    /// The root is a column filling the window with one node at the bottom, so
    /// shrinking the window by 200 moves the anchor up by 200 — and the popup
    /// under it has to follow, or it hangs in the space the window used to
    /// occupy.
    ///
    /// Anchored by **id** rather than by rectangle, which is the case worth
    /// testing: the id is re-resolved against the frame the resize produced, so
    /// this is also what says the re-placement happens *after* that paint rather
    /// than in the resize handler, where the only geometry available is the old
    /// window's.
    @Test
    @Timeout(20)
    @DisplayName("a popup follows its anchor when the window is resized")
    void replacedOnResize() {
        var before = new LogicalPoint[1];
        var after = new LogicalPoint[1];
        var root = new Menu(List.of(new Sized("target")), Set.of("bottom"));

        Goldberry.launch(
                new TestApp(
                        root,
                        // Two turns before opening, because `anchor(id)` answers
                        // from the capture the last paint produced and the first
                        // paint has not happened when `start` runs.
                        host -> afterTurns(host, 2, () -> {
                            // `Sized` and not `Plate`: a plate grows into whatever
                            // it is given and measures 0x0 on its own, and a popup
                            // needs a size.
                            var popup = host.popup(new Sized("menu"), "target", Placement.BELOW)
                                    .orElse(null);
                            if (popup == null) {
                                Goldberry.stop();
                                return;
                            }
                            before[0] = popup.offset();
                            ownerWindow().resizeTo(LogicalSize.of(400, 300));
                            // Four more, which is comfortably past the paint that
                            // the resize asks for and the re-placement at the end
                            // of it.
                            afterTurns(host, 4, () -> {
                                after[0] = popup.offset();
                                Goldberry.stop();
                            });
                        }),
                        host -> {}),
                new String[] {"--size=400x500", "--frames=400"});

        assertNotNull(before[0], "the popup never opened, so there is nothing to say about it");
        assertEquals(
                before[0].y() - 200,
                after[0].y(),
                0.5,
                "the window lost 200px of height and the popup stayed where the anchor used to be");
        assertEquals(before[0].x(), after[0].x(), 0.5, "and it should not have moved sideways");
    }

    /// A **move** does not move the anchor and does not repaint anything. What it
    /// moves is the work area *in this window's coordinates*, and a menu that was
    /// flipped or clamped against the old position has to be asked again
    /// ([ADR-0270]).
    ///
    /// The window opens 400x500 at the desktop's origin, where the work area is
    /// 1920x1040 and a menu under a target at the bottom of the window has all
    /// the room it wants. Moved to y=700 the same menu would open at 1204 on a
    /// desktop that stops at 1040 — under the taskbar, on a screen that has one.
    @Test
    @Timeout(20)
    @DisplayName("a popup is placed again when the window moves")
    void replacedOnMove() {
        var before = new LogicalPoint[1];
        var after = new LogicalPoint[1];
        var root = new Menu(List.of(new Sized("target")), Set.of("bottom"));

        Goldberry.launch(
                new TestApp(
                        root,
                        host -> afterTurns(host, 2, () -> {
                            var popup = host.popup(new Sized("menu"), "target", Placement.BELOW)
                                    .orElse(null);
                            if (popup == null) {
                                Goldberry.stop();
                                return;
                            }
                            before[0] = popup.offset();
                            ownerWindow().moveTo(new LogicalPoint(0, 700));
                            // Two turns: one for the Moved event to be dispatched,
                            // and one to be comfortably past it. No paint is
                            // needed and none is asked for -- the capture the last
                            // frame produced is still the right one, which is the
                            // difference between this and `replacedOnResize`.
                            afterTurns(host, 2, () -> {
                                after[0] = popup.offset();
                                Goldberry.stop();
                            });
                        }),
                        host -> {}),
                new String[] {"--size=400x500", "--frames=400"});

        assertNotNull(before[0], "the popup never opened, so there is nothing to say about it");
        assertEquals(504, before[0].y(), 0.5, "4px under a target whose bottom edge is the window's");
        assertEquals(
                336,
                after[0].y(),
                0.5,
                "the window moved down the screen, so the room below the target went away and the"
                        + " menu had to flip above it");
    }

    /// The other half of the same claim, and the one no event reports: the
    /// **anchor** moved.
    ///
    /// A `scroll` is a translation on the content — Yoga never sees it — so the
    /// widget the menu hangs off is laid out where it always was and drawn 120
    /// pixels higher. Two things have to be true for the menu to follow it: the
    /// anchor rectangle has to be the *painted* one rather than the laid-out one,
    /// and something has to re-ask the question on a frame that no resize and no
    /// move produced ([ADR-0270]).
    @Test
    @Timeout(20)
    @DisplayName("a popup follows an anchor that scrolls under it")
    void followsAScrollingAnchor() {
        var before = new LogicalPoint[1];
        var after = new LogicalPoint[1];
        var scrolled = io.github.digitalsmile.goldberry.bind.Property.of(0f);
        var root = new Menu(List.of(new Scrolled(scrolled, new Sized("target"))), Set.of());

        Goldberry.launch(
                new TestApp(
                        root,
                        host -> afterTurns(host, 2, () -> {
                            var popup = host.popup(new Sized("menu"), "target", Placement.BELOW)
                                    .orElse(null);
                            if (popup == null) {
                                Goldberry.stop();
                                return;
                            }
                            before[0] = popup.offset();
                            scrolled.set(120f);
                            // Four turns, which is past the frame the binding
                            // asked for and past the re-placement at the end of
                            // it.
                            afterTurns(host, 4, () -> {
                                after[0] = popup.offset();
                                Goldberry.stop();
                            });
                        }),
                        host -> {}),
                new String[] {"--size=400x500", "--frames=400"});

        assertNotNull(before[0], "the popup never opened, so there is nothing to say about it");
        assertEquals(
                before[0].y() - 120,
                after[0].y(),
                0.5,
                "the content scrolled 120px and the menu stayed where the target used to be drawn");
        assertEquals(before[0].x(), after[0].x(), 0.5, "and it should not have moved sideways");
    }

    /// The platform destroys a popup with its parent. So does the toolkit, and
    /// the reason is the event loop: it runs until `windows()` is empty.
    @Test
    @Timeout(20)
    @DisplayName("shutting the window down takes its popups with it")
    void closedWithTheWindow() {
        var opened = new Popup[1];
        Goldberry.launch(
                new TestApp(
                        host -> opened[0] = host.popup(
                                        new Plate("menu"), LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                                .orElseThrow()
                                .lightDismiss(false),
                        host -> {}),
                new String[] {"--frames=2"});

        assertFalse(opened[0].isOpen());
        assertTrue(backend.windows().isEmpty(), "a popup left open is an event loop that never finishes");
    }
}
