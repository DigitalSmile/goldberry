package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.paint.Box;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The window's selectors reach into a popup from the widget it was opened
/// from, and follow that widget's chain while the popup is open.
///
/// The panel's width says which rules matched: 100px on its own, 160px under
/// `aside.wide`, and 40px more for being the opener's direct child.
@DisplayName("a popup is styled from where it was opened")
class PopupOpenedFromTest {

    private static final String SHEET = """
            page { flex-grow: 1 }
            aside { width: 200px; height: 100px }
            opener { width: 80px; height: 20px }
            panel { width: 100px; height: 40px }
            aside.wide panel { width: 160px }
            aside.wide > opener > panel { width: 200px }
            """;

    private static final LogicalRect RECT = LogicalRect.of(40, 40, 80, 20);

    private record Panel() implements Widget.Leaf, Styled, Paints {
        @Override
        public String cssType() {
            return "panel";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }

    private record Opener() implements Widget.Leaf, Styled, Paints {
        @Override
        public String cssType() {
            return "opener";
        }

        @Override
        public @Nullable String id() {
            return "opener";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }

    private record Aside(Set<String> classes, List<Widget> children) implements Widget.Leaf, Styled, Paints {
        @Override
        public String cssType() {
            return "aside";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// The page, whose aside's classes a test can change while a popup is open.
    private record Page(Set<String> initial, AtomicReference<PageState> state) implements Widget.Stateful {
        @Override
        public State<?> createState() {
            var created = new PageState();
            state.set(created);
            return created;
        }
    }

    private static final class PageState extends State<Page> {

        private @Nullable Set<String> classes;

        void classes(Set<String> value) {
            setState(() -> classes = value);
        }

        @Override
        public Widget build(BuildContext context) {
            var shown = classes == null ? widget().initial() : classes;
            return new Aside(shown, List.of(new Opener()));
        }
    }

    private static final class TestApp implements Application {

        private final Page page;
        private final Consumer<Host> onStart;

        TestApp(Page page, Consumer<Host> onStart) {
            this.page = page;
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return page;
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        /// The application's sheet, and a toolkit-layer one whose rule would make
        /// the panel taller if the toolkit's base sheet reached in.
        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(
                    Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, "aside panel { height: 80px }"),
                    Stylesheet.parse(CascadeLayer.APPLICATION, SHEET));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        GoldberryRuntime.install(new HeadlessBackend());
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    private static void afterTurns(Host host, int turns, Runnable action) {
        if (turns <= 0) {
            action.run();
            return;
        }
        host.after(Duration.ZERO, () -> afterTurns(host, turns - 1, action));
    }

    private static void launch(Set<String> classes, AtomicReference<PageState> state, Consumer<Host> onStart) {
        Goldberry.launch(new TestApp(new Page(classes, state), onStart), new String[] {"--frames=400"});
    }

    @Test
    @Timeout(20)
    @DisplayName("against an id it is opened from that widget, and both combinators reach in")
    void byId() {
        var width = new float[2];
        var height = new float[1];
        launch(
                Set.of("wide"),
                new AtomicReference<>(),
                host -> afterTurns(host, 2, () -> {
                    var byId =
                            host.popup(new Panel(), "opener", Placement.BELOW).orElseThrow();
                    var byRect = host.popup(new Panel(), RECT, Placement.BELOW).orElseThrow();
                    afterTurns(host, 3, () -> {
                        width[0] = byId.bounds().size().width();
                        width[1] = byRect.bounds().size().width();
                        height[0] = byId.bounds().size().height();
                        Goldberry.stop();
                    });
                }));

        assertAll(
                () -> assertEquals(200, width[0], "`aside.wide > opener > panel` matched through the opener"),
                () -> assertEquals(100, width[1], "a rectangle names no widget, so nothing reaches in"),
                () -> assertEquals(40, height[0], "the toolkit's base sheet stays in its own tree"));
    }

    @Test
    @Timeout(20)
    @DisplayName("against a rectangle, OpenedFrom names the widget")
    void namedForARectangle() {
        var width = new float[1];
        launch(
                Set.of("wide"),
                new AtomicReference<>(),
                host -> afterTurns(host, 2, () -> {
                    var opener =
                            (BuildContext) host.anchor("opener").orElseThrow().owner();
                    var popup = OpenedFrom.call(opener, () -> host.popup(new Panel(), RECT, Placement.BELOW))
                            .orElseThrow();
                    afterTurns(host, 3, () -> {
                        width[0] = popup.bounds().size().width();
                        Goldberry.stop();
                    });
                }));

        assertEquals(200, width[0]);
    }

    @Test
    @Timeout(20)
    @DisplayName("a class that arrives on the opener's ancestor while it is open restyles it")
    void followsTheChain() {
        var state = new AtomicReference<PageState>();
        var widths = new float[3];
        launch(
                Set.of(),
                state,
                host -> afterTurns(host, 2, () -> {
                    var popup =
                            host.popup(new Panel(), "opener", Placement.BELOW).orElseThrow();
                    afterTurns(host, 3, () -> {
                        widths[0] = popup.bounds().size().width();
                        state.get().classes(Set.of("wide"));
                        afterTurns(host, 6, () -> {
                            widths[1] = popup.bounds().size().width();
                            state.get().classes(Set.of());
                            afterTurns(host, 6, () -> {
                                widths[2] = popup.bounds().size().width();
                                Goldberry.stop();
                            });
                        });
                    });
                }));

        assertAll(
                () -> assertEquals(100, widths[0], "nothing reaches in before the class"),
                () -> assertEquals(200, widths[1], "the class arrived, and the popup heard it"),
                () -> assertEquals(100, widths[2], "and heard it go"));
    }
}
