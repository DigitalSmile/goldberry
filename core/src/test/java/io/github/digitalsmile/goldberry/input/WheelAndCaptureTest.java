package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// The wheel, and the pointer capture a drag needs (§7.1).
///
/// Two features that share a tree: a scroll view is the thing that consumes a
/// wheel event, and its scrollbar is the thing that captures the pointer.
class WheelAndCaptureTest {

    private final List<String> log = new ArrayList<>();

    private class Node implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final List<Widget> children;
        private boolean consumeWheel;

        Node(String name, Widget... children) {
            this.name = name;
            this.children = List.of(children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.WHEEL) {
                log.add(name + ":wheel:" + event.deltaX() + "," + event.deltaY());
                if (consumeWheel) {
                    event.consume();
                }
            } else {
                log.add(name + ":" + event.kind() + " at " + event.x() + "," + event.y());
            }
        }
    }

    private PointerRouter router;
    private Element outer;
    private Element inner;
    private Node innerWidget;
    private Node outerWidget;

    @BeforeEach
    void buildTree() {
        router = new PointerRouter();
        innerWidget = new Node("inner");
        outerWidget = new Node("outer", innerWidget);
        var tree = new ElementTree(outerWidget);
        outer = tree.root();
        inner = outer.children().getFirst();
        router.updateRegions(List.of(
                HitTest.Region.of(outer, 0, 0, 100, 100),
                HitTest.Region.of(inner, 20, 20, 40, 40)));
    }

    @Nested
    @DisplayName("wheel")
    class Wheel {

        @Test
        @DisplayName("a wheel event reaches the node under the pointer, with its deltas")
        void reachesTheTarget() {
            router.pointerWheel(30, 30, 0, 3);

            assertTrue(log.contains("inner:wheel:0.0,3.0"), () -> "log was " + log);
        }

        @Test
        @DisplayName("it bubbles, so an inner scroll view can hand it to an outer one")
        void bubbles() {
            router.pointerWheel(30, 30, 0, 1);

            assertEquals(List.of("inner:wheel:0.0,1.0", "outer:wheel:0.0,1.0"), log);
        }

        @Test
        @DisplayName("consuming it stops the ancestor scrolling too")
        void consumeStopsIt() {
            innerWidget.consumeWheel = true;
            router.pointerWheel(30, 30, 0, 1);

            // The nested-scroll bug: an inner list reaches its end and the page
            // behind it lurches. Consuming is what a scroll view does while it
            // still has somewhere to go.
            assertEquals(List.of("inner:wheel:0.0,1.0"), log);
        }

        @Test
        @DisplayName("a wheel over nothing is dropped rather than broadcast")
        void overNothing() {
            router.pointerWheel(500, 500, 0, 1);

            assertTrue(log.isEmpty(), () -> "log was " + log);
        }

        @Test
        @DisplayName("a fractional delta arrives intact")
        void fractional() {
            // A touchpad sends fractions of a detent, and rounding them is what
            // makes a trackpad scroll in jerks.
            router.pointerWheel(30, 30, 0, 0.125f);

            assertTrue(log.contains("inner:wheel:0.0,0.125"), () -> "log was " + log);
        }

        @Test
        @DisplayName("a wheel does not move :hover, because the pointer did not move")
        void doesNotHover() {
            router.pointerWheel(30, 30, 0, 1);

            assertNull(router.hovered());
        }
    }

    @Nested
    @DisplayName("capture")
    class Capture {

        @Test
        @DisplayName("a press captures the pointer, so a drag that leaves still arrives")
        void dragOutside() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            log.clear();

            router.pointerMoved(90, 90);

            // 90,90 is outside inner's 20,20 40x40 rectangle. Without capture
            // this move would go to outer, and a slider's thumb would stop
            // following the pointer the moment it wandered off the track.
            assertSame(inner, router.captured());
            assertTrue(log.contains("inner:MOVED at 90.0,90.0"), () -> "log was " + log);
        }

        @Test
        @DisplayName("the release goes to whoever captured the press, not to what is under it")
        void releaseGoesToTheCaptor() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            log.clear();

            router.pointerReleased(90, 90, PointerEvent.Button.PRIMARY, 1);

            assertTrue(log.contains("inner:RELEASED at 90.0,90.0"), () -> "log was " + log);
            assertNull(router.captured(), "an implicit capture ends with the release");
        }

        @Test
        @DisplayName(":active clears on release even when the pointer left")
        void activeClears() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            assertTrue(inner.hasState(PseudoClass.ACTIVE));

            router.pointerReleased(90, 90, PointerEvent.Button.PRIMARY, 1);

            assertTrue(!inner.hasState(PseudoClass.ACTIVE), "a stuck :active would look pressed forever");
        }

        @Test
        @DisplayName("hover still follows the pointer during a drag")
        void hoverStillMoves() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            router.pointerMoved(90, 90);

            // What the user can see is still what the pointer is over -- capture
            // decides who gets told, not what is highlighted.
            assertSame(outer, router.hovered());
        }

        @Test
        @DisplayName("an explicit capture outlives the release that would end an implicit one")
        void explicitCaptureSurvives() {
            router.capturePointer(inner);
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(90, 90, PointerEvent.Button.PRIMARY, 1);

            assertSame(inner, router.captured(),
                    "only the widget that asked for it knows when its gesture is over");

            router.releasePointer();
            assertNull(router.captured());
        }

        @Test
        @DisplayName("the wheel goes to the captor as well")
        void wheelFollowsCapture() {
            router.capturePointer(inner);
            router.pointerWheel(90, 90, 0, 1);

            assertTrue(log.contains("inner:wheel:0.0,1.0"), () -> "log was " + log);
        }
    }

    @Nested
    @DisplayName("click")
    class Click {

        @Test
        @DisplayName("a press and a release on the same node is a click")
        void click() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(30, 30, PointerEvent.Button.PRIMARY, 1);

            assertTrue(log.contains("inner:CLICKED at 30.0,30.0"), () -> "log was " + log);
        }

        @Test
        @DisplayName("dragging off and letting go is not a click")
        void draggedOff() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(90, 90, PointerEvent.Button.PRIMARY, 1);

            // Cancelling a click by dragging off the button is a gesture people
            // rely on, and a control that fired on release could not tell.
            assertTrue(log.stream().noneMatch(entry -> entry.contains("CLICKED")),
                    () -> "log was " + log);
            assertTrue(log.contains("inner:RELEASED at 90.0,90.0"),
                    () -> "but the release still arrives: " + log);
        }

        @Test
        @DisplayName("releasing on a child counts as a click on the parent that was pressed")
        void releaseOnDescendant() {
            // Pressing a button's own label and releasing on its padding is one
            // click, not none.
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            log.clear();
            router.pointerReleased(30, 30, PointerEvent.Button.PRIMARY, 1);

            assertTrue(log.contains("outer:CLICKED at 30.0,30.0"),
                    () -> "the click should bubble to the ancestor too: " + log);
        }

        @Test
        @DisplayName("a click bubbles, and the target is what was pressed")
        void bubbles() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            log.clear();
            router.pointerReleased(30, 30, PointerEvent.Button.PRIMARY, 1);

            var clicks = log.stream().filter(entry -> entry.contains("CLICKED")).toList();
            assertEquals(List.of("inner:CLICKED at 30.0,30.0", "outer:CLICKED at 30.0,30.0"), clicks);
        }

        @Test
        @DisplayName("only the primary button clicks")
        void secondaryDoesNotClick() {
            // A right-click opens a context menu; it is not an activation, and a
            // button that fired on one would be a menu that also pressed itself.
            router.pointerPressed(30, 30, PointerEvent.Button.SECONDARY, 1);
            router.pointerReleased(30, 30, PointerEvent.Button.SECONDARY, 1);

            assertTrue(log.stream().noneMatch(entry -> entry.contains("CLICKED")),
                    () -> "log was " + log);
        }

        @Test
        @DisplayName("a release with no press before it is not a click")
        void releaseWithoutPress() {
            router.pointerReleased(30, 30, PointerEvent.Button.PRIMARY, 1);

            assertTrue(log.stream().noneMatch(entry -> entry.contains("CLICKED")),
                    () -> "log was " + log);
        }
    }

    @Nested
    @DisplayName("through the backend")
    class Plumbing {

        private HeadlessBackend backend;

        @BeforeEach
        void install() {
            // The frame loop paints, and painting is Blend2D even on the
            // headless backend — see [PointerPlumbingTest].
            io.github.digitalsmile.goldberry.RendererRequirement.enforce();
            backend = new HeadlessBackend(new DisplayScale(1f));
            io.github.digitalsmile.goldberry.GoldberryTestAccess.install(backend);
        }

        @AfterEach
        void shutdown() {
            Goldberry.shutdown();
        }

        @Test
        @Timeout(10)
        @DisplayName("a wheel event travels backend to widget")
        void wheelReachesTheWidget() {
            var window = Window.open(WindowSpec.of("wheel", LogicalSize.of(100f, 100f)));
            window.pointerRouter(router);
            var backendWindow = (HeadlessWindow) backend.windows().getFirst();

            backendWindow.scrollPointer(30, 30, 0, 2.5f);
            backendWindow.requestClose();
            Goldberry.run();

            assertTrue(log.contains("inner:wheel:0.0,2.5"), () -> "log was " + log);
        }
    }
}
