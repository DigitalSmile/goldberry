package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
            implements Widget.Leaf, Styled, Paints, io.github.digitalsmile.goldberry.input.Handles {

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

        TestApp(Consumer<Host> onStart, Consumer<Host> onStop) {
            this.onStart = onStart;
            this.onStop = onStop;
        }

        private Host host;

        @Override
        public Widget root() {
            return new Plate("content");
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
        Goldberry.launch(new TestApp(
                host -> {
                    opened[0] = host.popup(new Plate("menu"),
                            LogicalPoint.of(40, 60), LogicalSize.of(180, 132)).orElseThrow();
                    // Held now rather than looked up later: by the time `launch`
                    // returns, the launcher has closed every popup and
                    // `windows()` is empty — which the last test here asserts.
                    backing[0] = onlyPopup();
                },
                host -> { }),
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
        Goldberry.launch(new TestApp(
                host -> {
                    opened[0] = host.popup(new Plate("menu"),
                            LogicalPoint.of(40, 60), LogicalSize.of(180, 132)).orElseThrow();
                    backend.post(new BackendEvent.PointerPressed(
                            ownerWindow(), 10, 10, 1, 1, 0));
                },
                host -> { }),
                new String[] {"--frames=3"});

        assertFalse(opened[0].isOpen(), "a press below a menu closes it (§7's light dismissal)");
    }

    @Test
    @Timeout(20)
    @DisplayName("Escape closes it too")
    void lightDismissedByEscape() {
        var opened = new Popup[1];
        Goldberry.launch(new TestApp(
                host -> {
                    opened[0] = host.popup(new Plate("menu"),
                            LogicalPoint.of(40, 60), LogicalSize.of(180, 132)).orElseThrow();
                    backend.post(new BackendEvent.KeyPressed(
                            ownerWindow(), Key.ESCAPE.sdlKeycode(), 0, false));
                },
                host -> { }),
                new String[] {"--frames=3"});

        assertFalse(opened[0].isOpen());
    }

    @Test
    @Timeout(20)
    @DisplayName("a popup that opted out of light dismissal stays open")
    void lightDismissCanBeTurnedOff() {
        var opened = new Popup[1];
        Goldberry.launch(new TestApp(
                host -> {
                    opened[0] = host.popup(new Plate("menu"),
                                    LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                            .orElseThrow()
                            .lightDismiss(false);
                    backend.post(new BackendEvent.PointerPressed(
                            ownerWindow(), 10, 10, 1, 1, 0));
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
                Optional<io.github.digitalsmile.goldberry.input.HitTest.Region>>(Optional.empty());
        var missing = new java.util.concurrent.atomic.AtomicReference<
                Optional<io.github.digitalsmile.goldberry.input.HitTest.Region>>(Optional.empty());
        Goldberry.launch(new TestApp(
                host -> { },
                host -> {
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
        Goldberry.launch(new TestApp(
                host -> {
                    opened[0] = host.popup(new Sized("menu"),
                            io.github.digitalsmile.goldberry.backend.LogicalRect.of(40, 40, 100, 30),
                            Placement.BELOW).orElseThrow();
                    backing[0] = onlyPopup();
                },
                host -> { }),
                new String[] {"--frames=2"});

        assertEquals(LogicalSize.of(200, 80), backing[0].size(),
                "the size came from the content, not from the caller");
        assertEquals(new LogicalPoint(40, 74), backing[0].offset(),
                "4px under a 30px-tall anchor at y=40, left edges together");
    }

    /// The anchor by id, which is what a document-driven application has.
    @Test
    @Timeout(20)
    @DisplayName("a popup can be anchored to a node by id, and refuses when there is none")
    void anchoredById() {
        var backing = new HeadlessPopup[1];
        var missing = new boolean[1];
        Goldberry.launch(new TestApp(
                host -> { },
                host -> {
                    // In `stop()`, because an anchor is a rectangle from a frame
                    // that has been painted and `start()` runs before any have.
                    host.popup(new Sized("menu"), "content", Placement.BELOW)
                            .ifPresent(open -> backing[0] = onlyPopup());
                    missing[0] = host.popup(new Sized("menu"), "no-such-id", Placement.BELOW)
                            .isEmpty();
                }),
                new String[] {"--frames=2"});

        assertEquals(new LogicalPoint(0, 304), backing[0].offset(),
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
        Goldberry.launch(new TestApp(
                host -> {
                    // 300 tall, so its bottom edge is at 1300 on a desktop whose
                    // work area ends at 1040 — the window is hanging off the
                    // bottom, which is where this is interesting.
                    ownerWindow().moveTo(new LogicalPoint(200, 1000));
                    opened(host, 40, 40);
                    backing[0] = onlyPopup();
                },
                host -> { }),
                new String[] {"--frames=2"});

        // Below would be y = 74, and 1000 + 74 + 80 is past the work area's 1040.
        assertEquals(new LogicalPoint(40, -44), backing[0].offset(),
                "4px above a 30px anchor at y=40: the menu flipped, and its offset is"
                        + " negative because it is above the window's own top edge — which is"
                        + " the point of a popup being a window rather than an overlay");
    }

    private static void opened(Host host, float x, float y) {
        host.popup(new Sized("menu"),
                io.github.digitalsmile.goldberry.backend.LogicalRect.of(x, y, 100, 30),
                Placement.BELOW).orElseThrow();
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
        Goldberry.launch(new TestApp(
                host -> {
                    host.popup(new Menu(List.of(
                                    new Item("one", focused), new Item("two", focused))),
                            io.github.digitalsmile.goldberry.backend.LogicalRect.of(0, 0, 10, 10),
                            Placement.BELOW).orElseThrow();
                    // Queued behind the frames the popup needs to build its tree:
                    // focus traversal walks elements, and there are none until it
                    // has been painted once.
                    backend.post(new BackendEvent.KeyPressed(
                            ownerWindow(), Key.DOWN.sdlKeycode(), 0, false));
                },
                host -> { }),
                new String[] {"--frames=4"});

        assertEquals(List.of("one", "two"), focused,
                "the first item takes focus when the menu opens, and Down moves to the second"
                        + " — inside the popup, from a key the owner window received");
    }

    /// A focus scope with items in it — which is what §7 says every overlay
    /// wraps, and what makes `Down` mean "the next item" rather than nothing.
    private record Menu(List<Widget> items)
            implements Widget.Leaf, Styled, Paints, io.github.digitalsmile.goldberry.input.Handles {

        @Override
        public io.github.digitalsmile.goldberry.input.FocusScope focusScope() {
            return io.github.digitalsmile.goldberry.input.FocusScope.VERTICAL;
        }

        @Override
        public String cssType() {
            return "menu";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
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

    /// The platform destroys a popup with its parent. So does the toolkit, and
    /// the reason is the event loop: it runs until `windows()` is empty.
    @Test
    @Timeout(20)
    @DisplayName("shutting the window down takes its popups with it")
    void closedWithTheWindow() {
        var opened = new Popup[1];
        Goldberry.launch(new TestApp(
                host -> opened[0] = host.popup(new Plate("menu"),
                                LogicalPoint.of(40, 60), LogicalSize.of(180, 132))
                        .orElseThrow()
                        .lightDismiss(false),
                host -> { }),
                new String[] {"--frames=2"});

        assertFalse(opened[0].isOpen());
        assertTrue(backend.windows().isEmpty(),
                "a popup left open is an event loop that never finishes");
    }
}
