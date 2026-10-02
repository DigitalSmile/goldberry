package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.Ownership;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A second top-level window with a tree of its own, through the real
/// launcher: what it shares with the first window, what it does not, and how
/// it closes.
class SecondWindowTest {

    /// Fills what it is given and records each press under its name.
    private record Plate(String name, List<String> pressed, Attributes attributes)
            implements Widget.Leaf, Styled, Paints, Handles {

        Plate(String name, List<String> pressed) {
            this(name, pressed, new Attributes(name, Set.of(), name));
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
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.PRESSED) {
                pressed.add(name);
            }
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    private static final class TestApp implements Application {

        final List<String> pressed = new ArrayList<>();
        String colour = "#204060";
        private final Consumer<Host> onStart;

        TestApp(Consumer<Host> onStart) {
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Plate("main", pressed);
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "plate { background: " + colour + " }"));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    private HeadlessBackend backend;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessWindow named(String title) {
        return backend.windows().stream()
                .filter(HeadlessWindow.class::isInstance)
                .map(HeadlessWindow.class::cast)
                .filter(window -> window.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no window called " + title));
    }

    /// The colour at the middle of the last frame `window` presented, as
    /// `0xAARRGGBB`.
    private static int middle(HeadlessWindow window) {
        var frame = window.lastFrame().orElseThrow();
        var pixels = frame.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        var x = frame.size().width() / 2;
        var y = frame.size().height() / 2;
        return pixels.getInt(y * frame.stride() + x * 4);
    }

    /// Runs `steps` one after another, a little apart, so each sees the frames
    /// and events the one before it caused.
    @SafeVarargs
    private static void steps(Host host, Consumer<Host>... steps) {
        var list = new ArrayList<Consumer<Host>>();
        for (var each : steps) {
            list.add(each);
        }
        step(host, list, 0);
    }

    private static void step(Host host, List<Consumer<Host>> steps, int index) {
        if (index == steps.size()) {
            host.after(Duration.ofMillis(60), Goldberry::stop);
            return;
        }
        host.after(Duration.ofMillis(60), () -> {
            steps.get(index).accept(host);
            step(host, steps, index + 1);
        });
    }

    private void press(HeadlessWindow window) {
        backend.post(new BackendEvent.PointerMoved(window, 50, 50, 0));
        backend.post(new BackendEvent.PointerPressed(window, 50, 50, 1, 1, 0));
        backend.post(new BackendEvent.PointerReleased(window, 50, 50, 1, 1, 0));
    }

    private static WindowSpec spec(String title) {
        return WindowSpec.of(title, LogicalSize.of(200, 100));
    }

    @Test
    @Timeout(20)
    @DisplayName("a second window paints its own root with the application's stylesheet")
    void paintsWithTheSharedStylesheet() {
        var colours = new AtomicReference<int[]>();
        Goldberry.launch(new TestApp(host -> {
            host.openWindow(spec("second"), new Plate("second", new ArrayList<>()))
                    .orElseThrow();
            steps(host, h -> colours.set(new int[] {middle(named("Goldberry")), middle(named("second"))}));
        }));

        assertEquals(0xFF204060, colours.get()[0]);
        assertEquals(0xFF204060, colours.get()[1], "the second window did not paint with the shared sheet");
    }

    @Test
    @Timeout(20)
    @DisplayName("a press in the second window reaches the second window's tree and nothing else")
    void inputIsPerWindow() {
        var pressed = new ArrayList<String>();
        var app = new TestApp(host -> {
            host.openWindow(spec("second"), new Plate("second", pressed)).orElseThrow();
            steps(host, h -> press(named("second")));
        });
        Goldberry.launch(app);

        assertEquals(List.of("second"), pressed);
        assertEquals(List.of(), app.pressed);
    }

    @Test
    @Timeout(20)
    @DisplayName("a restyle asked of the second window restyles the first as well")
    void restyleIsEveryWindows() {
        var colours = new ArrayList<Integer>();
        var holder = new AtomicReference<TestApp>();
        var app = new TestApp(host -> {
            var second = host.openWindow(spec("second"), new Plate("second", new ArrayList<>()))
                    .orElseThrow();
            steps(
                    host,
                    h -> {
                        holder.get().colour = "#a03020";
                        second.restyle();
                    },
                    h -> {
                        colours.add(middle(named("Goldberry")));
                        colours.add(middle(named("second")));
                    });
        });
        holder.set(app);
        Goldberry.launch(app);

        assertEquals(List.of(0xFFA03020, 0xFFA03020), colours);
    }

    @Test
    @Timeout(20)
    @DisplayName("the fonts, the clock and the displays are the application's, and every window has the same")
    void sharesTheApplicationsThings() {
        var same = new ArrayList<Boolean>();
        Goldberry.launch(new TestApp(host -> {
            var second = host.openWindow(spec("second"), new Plate("second", new ArrayList<>()))
                    .orElseThrow();
            assertSame(host.fonts(), second.fonts());
            assertSame(host.clock(), second.clock());
            assertEquals(host.displays(), second.displays());
            same.add(host.window() != second.window());
            steps(host);
        }));

        assertEquals(List.of(true), same, "a second window is a second window");
    }

    @Test
    @Timeout(20)
    @DisplayName("closing the second window takes it down and tells onClose, and the application runs on")
    void closingTheSecond() {
        var closed = new AtomicInteger();
        var seen = new ArrayList<Boolean>();
        Goldberry.launch(new TestApp(host -> {
            var second = host.openWindow(spec("second"), new Plate("second", new ArrayList<>()))
                    .orElseThrow();
            second.onClose(closed::incrementAndGet);
            steps(host, h -> second.close(), h -> {
                seen.add(h.window().isOpen());
                seen.add(second.isOpen());
                seen.add(backend.windows().stream().anyMatch(w -> w.title().equals("second")));
            });
        }));

        assertEquals(1, closed.get());
        assertEquals(List.of(true, false, false), seen, "the first open, the second closed and gone");
    }

    @Test
    @Timeout(20)
    @DisplayName("the user closing the second window is the same close")
    void theUserClosesIt() {
        var closed = new AtomicInteger();
        Goldberry.launch(new TestApp(host -> {
            var second = host.openWindow(spec("second"), new Plate("second", new ArrayList<>()))
                    .orElseThrow();
            second.onClose(closed::incrementAndGet);
            steps(host, h -> named("second").requestClose(), h -> {});
        }));

        assertEquals(1, closed.get());
    }

    @Test
    @Timeout(20)
    @DisplayName("closing the first window closes every other and ends the application")
    void closingTheFirstEndsIt() {
        var closed = new AtomicInteger();
        Goldberry.launch(new TestApp(host -> {
            host.openWindow(spec("second"), new Plate("second", new ArrayList<>()))
                    .orElseThrow()
                    .onClose(closed::incrementAndGet);
            host.openWindow(spec("third"), new Plate("third", new ArrayList<>()))
                    .orElseThrow()
                    .onClose(closed::incrementAndGet);
            host.after(Duration.ofMillis(60), () -> named("Goldberry").requestClose());
        }));

        assertEquals(2, closed.get());
        assertTrue(backend.windows().isEmpty());
    }

    @Test
    @Timeout(20)
    @DisplayName("a window that belongs to a second window closes with it, and the first stays")
    void ownedClosesWithItsOwner() {
        var closed = new ArrayList<String>();
        Goldberry.launch(new TestApp(host -> {
            var second = host.openWindow(spec("second"), new Plate("second", new ArrayList<>()))
                    .orElseThrow();
            second.onClose(() -> closed.add("second"));
            second.openWindow(spec("palette").withOwnership(Ownership.OWNED), new Plate("palette", new ArrayList<>()))
                    .orElseThrow()
                    .onClose(() -> closed.add("palette"));
            steps(
                    host,
                    h -> closed.add("owner "
                            + named("palette").parentWindow().orElseThrow().title()),
                    h -> second.close());
        }));

        // The owned window goes before the window it belongs to.
        assertEquals(List.of("owner second", "palette", "second"), closed);
    }

    @Test
    @Timeout(20)
    @DisplayName("a modal window takes its owner's input until it closes, and a press there raises it")
    void modalBlocksItsOwner() {
        var raised = new AtomicInteger();
        var app = new TestApp(host -> {
            var dialog = host.openWindow(
                            spec("dialog").withOwnership(Ownership.MODAL), new Plate("dialog", new ArrayList<>()))
                    .orElseThrow();
            steps(
                    host,
                    h -> press(named("Goldberry")),
                    h -> {
                        // Negative when the platform was not told it is modal.
                        raised.set(
                                named("dialog").raiseCount() * (named("dialog").isModal() ? 1 : -1));
                        dialog.close();
                    },
                    h -> press(named("Goldberry")));
        });
        Goldberry.launch(app);

        assertEquals(1, raised.get(), "the press on the blocked window did not bring the dialog forward");
        assertEquals(List.of("main"), app.pressed, "the first press reached a window under a modal one");
    }
}
