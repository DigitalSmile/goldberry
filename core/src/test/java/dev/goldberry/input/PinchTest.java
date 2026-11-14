package dev.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.Goldberry;
import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.RendererRequirement;
import dev.goldberry.Window;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.PointerEvent.PinchPhase;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Styled;

/// A two-finger pinch: aimed where the pointer is, bubbled like any pointer
/// event, and carrying the scale since the last change.
@DisplayName("a pinch")
class PinchTest {

    private final List<String> log = new ArrayList<>();
    private final List<PointerEvent> pinches = new ArrayList<>();

    private final class Node implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final List<Widget> children;
        private boolean consume;

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
            if (event.kind() == PointerEvent.Kind.PINCH) {
                pinches.add(event);
                log.add(name + ":" + event.pinchPhase() + ":" + event.scale());
                if (consume) {
                    event.consume();
                }
            }
        }
    }

    private PointerRouter router;
    private Node innerWidget;
    private Element inner;

    @BeforeEach
    void buildTree() {
        router = new PointerRouter();
        innerWidget = new Node("inner");
        var outerWidget = new Node("outer", innerWidget);
        var outer = new ElementTree(outerWidget).root();
        inner = outer.children().getFirst();
        router.updateRegions(
                List.of(HitTest.Region.of(outer, 0, 0, 100, 100), HitTest.Region.of(inner, 20, 20, 40, 40)));
    }

    @Test
    @DisplayName("lands where the pointer last was, and bubbles to the ancestors")
    void reachesWhatIsUnderThePointer() {
        router.pointerMoved(30, 30);
        router.pointerPinch(PinchPhase.BEGAN, 1, Modifiers.NONE);
        router.pointerPinch(PinchPhase.CHANGED, 1.5f, Modifiers.NONE);
        router.pointerPinch(PinchPhase.ENDED, 1, Modifiers.NONE);

        assertEquals(
                List.of(
                        "inner:BEGAN:1.0",
                        "outer:BEGAN:1.0",
                        "inner:CHANGED:1.5",
                        "outer:CHANGED:1.5",
                        "inner:ENDED:1.0",
                        "outer:ENDED:1.0"),
                log);
        var changed = pinches.get(2);
        assertEquals(30, changed.x());
        assertEquals(30, changed.y());
        assertNull(changed.button());
    }

    @Test
    @DisplayName("is reported consumed when a node zoomed, and stops there")
    void consumed() {
        innerWidget.consume = true;
        router.pointerMoved(30, 30);
        assertTrue(router.pointerPinch(PinchPhase.CHANGED, 0.8f, Modifiers.NONE));
        assertEquals(List.of("inner:CHANGED:0.8"), log);

        router.pointerMoved(5, 5);
        assertFalse(router.pointerPinch(PinchPhase.CHANGED, 0.8f, Modifiers.NONE), "the outer node lets it go");
    }

    @Test
    @DisplayName("goes nowhere before the pointer has been over the window")
    void noPointerYet() {
        assertFalse(router.pointerPinch(PinchPhase.CHANGED, 2, Modifiers.NONE));
        assertTrue(log.isEmpty());
    }

    @Test
    @DisplayName("has a scale only while it changes, and refuses one that is no factor")
    void scale() {
        var started = PointerEvent.pinch(0, 0, PinchPhase.BEGAN, 3, Modifiers.NONE, inner);
        assertEquals(1, started.scale(), "a start is no zoom");
        assertEquals(PinchPhase.BEGAN, started.pinchPhase());
        assertEquals(1, PointerEvent.wheel(0, 0, 0, 1, inner).scale(), "every other kind is 1");
        assertNull(PointerEvent.wheel(0, 0, 0, 1, inner).pinchPhase());
        assertThrows(
                IllegalArgumentException.class,
                () -> PointerEvent.pinch(0, 0, PinchPhase.CHANGED, 0, Modifiers.NONE, inner));
        assertThrows(
                IllegalArgumentException.class,
                () -> PointerEvent.pinch(0, 0, PinchPhase.CHANGED, Float.NaN, Modifiers.NONE, inner));
    }

    @Nested
    @DisplayName("through the backend")
    class Plumbing {

        private HeadlessBackend backend;

        @BeforeEach
        void install() {
            RendererRequirement.enforce();
            backend = new HeadlessBackend(new DisplayScale(1f));
            GoldberryTestAccess.install(backend);
        }

        @AfterEach
        void shutdown() {
            Goldberry.shutdown();
        }

        @Test
        @Timeout(10)
        @DisplayName("travels from the backend to the widget under the pointer")
        void reachesTheWidget() {
            var window = Window.open(WindowSpec.of("pinch", LogicalSize.of(100f, 100f)));
            window.pointerRouter(router);
            var backendWindow = (HeadlessWindow) backend.windows().getFirst();

            backendWindow.movePointer(30, 30);
            backendWindow.pinchPointer(PinchPhase.CHANGED, 1.25f, 0);
            backendWindow.requestClose();
            Goldberry.run();

            assertTrue(log.contains("inner:CHANGED:1.25"), () -> "log was " + log);
        }
    }
}
