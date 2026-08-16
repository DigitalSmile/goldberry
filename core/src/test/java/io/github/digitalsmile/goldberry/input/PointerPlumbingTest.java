package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// A pointer event's whole journey: backend → runtime → window → router →
/// widget.
///
/// [PointerRouterTest] drives the router directly, which says nothing about
/// whether anything reaches it. This drives the *backend*, through the same
/// dispatch a real SDL event takes, and needs no display because the headless
/// backend posts the same [io.github.digitalsmile.goldberry.backend.BackendEvent]
/// types the sdl3 one translates into.
class PointerPlumbingTest {

    private final List<String> log = new ArrayList<>();

    private final class Target implements Widget.Leaf, Styled, Handles {
        @Override
        public String cssType() {
            return "target";
        }

        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public void onPointer(PointerEvent event) {
            log.add(event.kind() + (event.button() == null ? "" : ":" + event.button())
                    + (event.clickCount() > 0 ? ":" + event.clickCount() : ""));
        }
    }

    private HeadlessBackend backend;
    private PointerRouter router;
    private Element element;

    @BeforeEach
    void setUp() {
        // Driving the backend means running the frame loop, and every frame is
        // painted through Blend2D whatever the backend is -- so with no
        // libgoldberry there is nothing to rasterize into. A skip, not a failure:
        // the `java` CI job builds no native library on purpose.
        io.github.digitalsmile.goldberry.RendererRequirement.enforce();
        backend = new HeadlessBackend(new DisplayScale(1.5f));
        io.github.digitalsmile.goldberry.GoldberryTestAccess.install(backend);
        router = new PointerRouter();
        var tree = new ElementTree(new Target());
        element = tree.root();
        router.updateRegions(List.of(HitTest.Region.of(element, 0, 0, 200, 100)));
    }

    @AfterEach
    void tearDown() {
        Goldberry.shutdown();
    }

    /// Opens a window wired to the router and drains whatever the backend has
    /// queued, exactly as the event loop would.
    private HeadlessWindow openWindow() {
        var window = Window.open(WindowSpec.of("pointer", LogicalSize.of(200f, 100f)));
        window.pointerRouter(router);
        return (HeadlessWindow) backend.windows().getFirst();
    }

    @Test
    @Timeout(10)
    @DisplayName("a pointer press from the backend reaches a widget, with its button and click count")
    void pressReachesTheWidget() {
        var backendWindow = openWindow();
        backendWindow.movePointer(30, 30);
        backendWindow.pressPointer(30, 30, 1, 2);
        backendWindow.releasePointer(30, 30, 1, 2);
        backendWindow.requestClose();

        Goldberry.run();

        // SDL numbers buttons from 1 and left-first; the translation to PRIMARY
        // happens in Window, and this is what proves it.
        assertTrue(log.contains("PRESSED:PRIMARY:2"), () -> "log was " + log);
        assertTrue(log.contains("RELEASED:PRIMARY:2"), () -> "log was " + log);
    }

    @Test
    @Timeout(10)
    @DisplayName("a move sets :hover, and leaving the window clears it")
    void hoverThroughTheBackend() {
        var backendWindow = openWindow();
        backendWindow.movePointer(30, 30);
        backendWindow.requestClose();
        Goldberry.run();

        assertTrue(element.hasState(PseudoClass.HOVER));
        assertSame(element, router.hovered());
        assertTrue(log.contains("ENTERED"));
    }

    @Test
    @Timeout(10)
    @DisplayName("the pointer leaving clears the whole chain")
    void exitThroughTheBackend() {
        var backendWindow = openWindow();
        backendWindow.movePointer(30, 30);
        backendWindow.exitPointer();
        backendWindow.requestClose();
        Goldberry.run();

        assertFalse(element.hasState(PseudoClass.HOVER));
        assertTrue(log.contains("EXITED"));
    }

    @Test
    @Timeout(10)
    @DisplayName("a window with no router ignores pointer events rather than failing")
    void noRouterIsHarmless() {
        var window = Window.open(WindowSpec.of("no router", LogicalSize.of(200f, 100f)));
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();
        backendWindow.movePointer(10, 10);
        backendWindow.pressPointer(10, 10, 1, 1);
        backendWindow.requestClose();

        // A window painting through a plain onPaint callback should cost the hit
        // testing work of nothing at all.
        Goldberry.run();
        assertTrue(log.isEmpty());
        assertFalse(window.isOpen());
    }
}
