package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.paint.Box;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessPopup;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A popup whose panel casts a shadow gets a window with room for it, where the
/// platform shows transparency, and nothing outside the popup can tell.
class PopupShadowTest {

    private record Page() implements Widget.Leaf, Styled, Paints {
        @Override
        public String cssType() {
            return "page";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    /// A 100×40 panel casting the themes' level-2 shadow, `0 8px 32px`.
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

    private static final class TestApp implements Application {

        private final Consumer<Host> onStart;

        TestApp(Consumer<Host> onStart) {
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Page();
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    page { flex-grow: 1; background: #204060 }
                    panel { width: 100px; height: 40px; background: #eceff4;
                            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5) }
                    """));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    private static final LogicalRect ANCHOR = LogicalRect.of(40, 40, 80, 20);

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessPopup popupWindow() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .filter(HeadlessPopup::isOpen)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no popup was opened"));
    }

    private static void afterTurns(Host host, int turns, Runnable action) {
        if (turns <= 0) {
            action.run();
            return;
        }
        host.after(Duration.ZERO, () -> afterTurns(host, turns - 1, action));
    }

    /// What one run saw: the popup as the toolkit reports it, the window as the
    /// backend made it, whether the popup was still open after `input`, and the
    /// popup's last frame.
    private record Seen(
            LogicalRect bounds, LogicalPoint window, LogicalSize windowSize, boolean open, PixelBuffer frame) {}

    private Seen open(boolean seeThrough, Consumer<HeadlessPopup> input) {
        var seen = new Seen[1];
        Goldberry.launch(
                new TestApp(host -> {
                    backend.seeThroughPopups(seeThrough);
                    var popup = host.popup(new Panel(), ANCHOR, Placement.BELOW).orElseThrow();
                    afterTurns(host, 3, () -> {
                        var bounds = popup.bounds();
                        var window = popupWindow();
                        var frame = window.lastFrame().orElseThrow();
                        input.accept(window);
                        afterTurns(host, 3, () -> {
                            seen[0] = new Seen(bounds, window.offset(), window.size(), popup.isOpen(), frame);
                            Goldberry.stop();
                        });
                    });
                }),
                new String[] {"--frames=400"});
        return seen[0];
    }

    private static int alphaAt(PixelBuffer frame, int x, int y) {
        return frame.pixels().getInt(y * frame.stride() + x * 4) >>> 24;
    }

    @Test
    @Timeout(20)
    @DisplayName("the window is the panel plus the shadow's reach, and the panel is placed as without one")
    void roomForTheShadow() {
        var plain = new Seen[1];
        var shadowed = new Seen[1];
        Goldberry.launch(
                new TestApp(host -> {
                    var first = host.popup(new Panel(), ANCHOR, Placement.BELOW).orElseThrow();
                    afterTurns(host, 3, () -> {
                        var window = popupWindow();
                        plain[0] = new Seen(first.bounds(), window.offset(), window.size(), true, null);
                        first.close();
                        backend.seeThroughPopups(true);
                        var second =
                                host.popup(new Panel(), ANCHOR, Placement.BELOW).orElseThrow();
                        afterTurns(host, 3, () -> {
                            var again = popupWindow();
                            shadowed[0] = new Seen(second.bounds(), again.offset(), again.size(), true, null);
                            Goldberry.stop();
                        });
                    });
                }),
                new String[] {"--frames=400"});

        assertAll(
                () -> assertEquals(
                        plain[0].bounds(), shadowed[0].bounds(), "placement measures the panel, not the margin"),
                () -> assertEquals(LogicalSize.of(100, 40), shadowed[0].bounds().size()),
                () -> assertEquals(LogicalSize.of(100, 40), plain[0].windowSize(), "no room where nothing shows it"),
                () -> assertEquals(LogicalSize.of(132, 72), shadowed[0].windowSize()),
                () -> assertEquals(
                        new LogicalPoint(
                                shadowed[0].bounds().left() - 16,
                                shadowed[0].bounds().top() - 8),
                        shadowed[0].window()));
    }

    @Test
    @Timeout(20)
    @DisplayName("the shadow is drawn in the room below the panel, and the panel is opaque")
    void theShadowIsPainted() {
        var frame = Objects.requireNonNull(open(true, window -> {}).frame());

        assertAll(
                () -> assertEquals(255, alphaAt(frame, 16 + 50, 8 + 20), "the panel"),
                () -> assertTrue(alphaAt(frame, 16 + 50, 8 + 40 + 8) > 0, "the shadow under the panel"),
                () -> assertEquals(0, alphaAt(frame, 0, 0), "the corner the shadow does not reach"));
    }

    @Test
    @Timeout(20)
    @DisplayName("a press in the room dismisses the popup, as a press outside does")
    void aPressInTheRoomDismisses() {
        var seen = open(true, window -> backend.post(new BackendEvent.PointerPressed(window, 4, 60, 1, 1, 0)));

        assertFalse(seen.open());
    }

    @Test
    @Timeout(20)
    @DisplayName("a press on the panel does not")
    void aPressOnThePanelDoesNot() {
        var seen = open(true, window -> backend.post(new BackendEvent.PointerPressed(window, 66, 28, 1, 1, 0)));

        assertTrue(seen.open());
    }
}
