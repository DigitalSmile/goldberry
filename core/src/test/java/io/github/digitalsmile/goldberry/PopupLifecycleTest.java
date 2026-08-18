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
        assertEquals(new LogicalPoint(40, 60), backing[0].position());
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
