package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.input.dragout.DragOperation;
import dev.goldberry.input.dragout.DragOutcome;
import dev.goldberry.input.drop.DropTarget;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.junit.DrivenRuntime;
import dev.goldberry.paint.Box;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessDrag;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The window's half of a drag out, through the real launcher on the headless
/// backend: the hand-off becomes `BackendWindow.startDrag`, the platform's
/// reports (played here by [HeadlessDrag]) drive the in-app targets, the
/// source hears one outcome, and the drop SDL would echo back is swallowed.
@DisplayName("a drag out, as the window sees it")
@Timeout(30)
class DragOutWindowTest {

    /// `#a` at 0..100, `#b` at 100..200, both 100 tall.
    private static final String SHEET = """
            #root { flex-direction: row; width: 400px; height: 300px; }
            .tile { width: 100px; height: 100px; background: #204060; }
            """;

    private static final Path OFFERED = Path.of("/tmp/goldberry-drag-out/frame.png");

    private record Plate(List<Widget> kids, Attributes attributes)
            implements Widget.Leaf, Styled, Paints, Handles, Attributed<Plate> {

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
        public Plate withAttributes(Attributes value) {
            return new Plate(kids, value);
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    private record TestApp(Widget root, Consumer<Host> onStart) implements Application {

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    private HeadlessBackend backend;
    private DrivenRuntime runtime;

    private final List<String> log = new ArrayList<>();
    private final List<DragOutcome> outcomes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        runtime = DrivenRuntime.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessWindow window() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    private Widget board(DragOffer offer) {
        var a = new Plate(List.of(), Attributes.NONE.id("a").classes("tile"))
                .draggable("card")
                .dragOut(() -> offer)
                .onDragOutEnd(outcomes::add);
        var b = new Plate(List.of(), Attributes.NONE.id("b").classes("tile"))
                .dropTarget(DropTarget.of(_ -> true, drop -> log.add("drop:" + drop.payload()))
                        .whileOver(_ -> log.add("over"))
                        .onLeave(() -> log.add("leave")));
        return new Plate(List.of(a, b), Attributes.NONE.id("root"));
    }

    /// Launches `root`, presses on `#a` and moves past the threshold, then runs
    /// `then` once the hand-off has been taken, and stops.
    private void dragFromA(Widget root, Consumer<Host> beforePress, Consumer<Host> then) {
        Goldberry.launch(new TestApp(
                root,
                host -> runtime.afterTheFirstFrame(host, () -> {
                    beforePress.accept(host);
                    backend.post(new BackendEvent.PointerMoved(window(), 50, 50, 0));
                    backend.post(new BackendEvent.PointerPressed(window(), 50, 50, 1, 1, 0));
                    backend.post(new BackendEvent.PointerMoved(window(), 60, 50, 0));
                    runtime.afterTheNextPump(() -> {
                        try {
                            then.accept(host);
                        } finally {
                            Goldberry.stop();
                        }
                    });
                })));
    }

    private static void nothing(Host host) {}

    @Test
    @DisplayName("crossing the threshold asks the backend to start the platform's drag, with the ghost as its picture")
    void starts() {
        var drags = new ArrayList<HeadlessDrag>();
        dragFromA(board(DragOffer.text("card a")), DragOutWindowTest::nothing, host -> drags.addAll(window().drags()));

        assertEquals(1, drags.size());
        var request = drags.getFirst().request();
        assertEquals("card a", request.offer().text().get());
        assertEquals(60, request.pointer().x(), 0.01);
        var picture = request.picture();
        assertNotNull(picture, "the source as it was last presented");
        assertEquals(100, picture.pixels().size().width());
        assertEquals(50, picture.hotspotX(), "where the press took hold");
        assertEquals(50, picture.hotspotY());
    }

    @Test
    @DisplayName("the platform's reports over the window light its target, and a release there lands the payload")
    void landsInApp() {
        dragFromA(board(DragOffer.text("card a")), DragOutWindowTest::nothing, host -> {
            var drag = window().drags().getFirst();
            drag.over(window(), 150, 50);
            drag.releaseOver(window(), 150, 50, false);
        });

        assertTrue(log.contains("over"), log::toString);
        assertEquals("drop:card", log.getLast(), log::toString);
        assertEquals(List.of(new DragOutcome.Landed(true)), outcomes);
    }

    @Test
    @DisplayName("refused by the backend, it is an in-app drag, and the source hears no outcome")
    void refused() {
        var started = new ArrayList<Integer>();
        Goldberry.launch(new TestApp(
                board(DragOffer.text("card a")),
                host -> runtime.afterTheFirstFrame(host, () -> {
                    window().refuseDrags(true);
                    backend.post(new BackendEvent.PointerMoved(window(), 50, 50, 0));
                    backend.post(new BackendEvent.PointerPressed(window(), 50, 50, 1, 1, 0));
                    backend.post(new BackendEvent.PointerMoved(window(), 60, 50, 0));
                    backend.post(new BackendEvent.PointerMoved(window(), 150, 50, 0));
                    backend.post(new BackendEvent.PointerReleased(window(), 150, 50, 1, 1, 0));
                    runtime.afterTheNextPump(() -> {
                        started.add(window().drags().size());
                        Goldberry.stop();
                    });
                })));

        assertEquals(List.of(0), started);
        assertEquals("drop:card", log.getLast(), log::toString);
        assertEquals(List.of(), outcomes);
    }

    @Test
    @DisplayName("taken by another application, the source hears what it did, and nothing lands")
    void delivered() {
        dragFromA(
                board(DragOffer.text("card a").allowing(DragOperation.COPY, DragOperation.MOVE)),
                DragOutWindowTest::nothing,
                host -> {
                    var drag = window().drags().getFirst();
                    drag.over(window(), 150, 50);
                    drag.outside();
                    drag.deliver(DragOperation.MOVE);
                });

        assertEquals(List.of(new DragOutcome.Delivered(DragOperation.MOVE)), outcomes);
        assertFalse(log.stream().anyMatch(entry -> entry.startsWith("drop:")), log::toString);
        assertTrue(log.contains("leave"), "leaving the window took it off the target: " + log);
    }

    @Test
    @DisplayName("cancelled over the window, the target hears it leave and the source hears Cancelled")
    void cancelled() {
        dragFromA(board(DragOffer.text("card a")), DragOutWindowTest::nothing, host -> {
            var drag = window().drags().getFirst();
            drag.over(window(), 150, 50);
            drag.cancel();
        });

        assertEquals("leave", log.getLast(), log::toString);
        assertEquals(List.of(new DragOutcome.Cancelled()), outcomes);
    }

    @Test
    @DisplayName("the drop SDL echoes back for the application's own offer is swallowed; the next real one is not")
    void echoSwallowed() {
        var dropped = new ArrayList<List<Path>>();
        dragFromA(
                board(DragOffer.files(OFFERED)),
                host -> host.window().onFileDrop(drop -> dropped.add(drop.paths())),
                host -> {
                    var drag = window().drags().getFirst();
                    drag.over(window(), 150, 50);
                    drag.releaseOver(window(), 150, 50, true);
                    var window = window();
                    host.window().handleFileDropped(OFFERED.toString(), 150, 50);
                    host.window().handleDropCompleted(150, 50);
                    host.window().handleFileDropped("/tmp/elsewhere.txt", 150, 50);
                    host.window().handleDropCompleted(150, 50);
                    assertEquals(window, window());
                });

        assertEquals(List.of(List.of(Path.of("/tmp/elsewhere.txt"))), dropped);
        assertEquals(List.of(new DragOutcome.Landed(true)), outcomes);
    }
}
