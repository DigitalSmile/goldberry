package io.github.digitalsmile.goldberry.backend.headless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PopupKind;
import io.github.digitalsmile.goldberry.backend.PopupSpec;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The popup half of the backend SPI, checked where it needs no display.
///
/// Every rule asserted here is one a real driver has to keep, which is the whole
/// reason `headless` has popups at all: a menu that opens in the wrong
/// coordinates should fail on a CI runner with no compositor rather than on the
/// one platform nobody happened to try.
class HeadlessPopupTest {

    private HeadlessBackend backend;
    private HeadlessWindow window;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
        window = (HeadlessWindow) backend.createWindow(
                WindowSpec.of("owner", LogicalSize.of(800, 600)));
    }

    @AfterEach
    void tearDown() {
        backend.close();
    }

    private HeadlessPopup popup(float x, float y, float width, float height) {
        return (HeadlessPopup) backend.createPopup(window,
                        PopupSpec.of(LogicalPoint.of(x, y), LogicalSize.of(width, height)))
                .orElseThrow();
    }

    @Test
    @DisplayName("a popup knows its owner, its kind and where it was put")
    void created() {
        var popup = popup(120, 48, 200, 300);

        assertSame(window, popup.owner());
        assertEquals(PopupKind.MENU, popup.kind());
        assertEquals(new LogicalPoint(120, 48), popup.position());
        assertEquals(LogicalSize.of(200, 300), popup.size());
        assertTrue(popup.isOpen());
    }

    /// A popup is a window, and the one place that matters is shutdown: a caller
    /// enumerating windows to close them must not leave one open because it was
    /// the wrong shape.
    @Test
    @DisplayName("a popup is in the window list")
    void inTheWindowList() {
        var popup = popup(0, 0, 100, 100);

        assertEquals(2, backend.windows().size());
        assertTrue(backend.windows().contains(popup));
        assertInstanceOf(HeadlessWindow.class, popup);
    }

    /// The point of a popup: it is not clipped to its owner. Nothing here refuses
    /// a position outside the owner's bounds, because a dropdown at the bottom of
    /// a window is *supposed* to hang below it.
    @Test
    @DisplayName("a popup may be placed outside its owner")
    void escapesTheOwner() {
        var popup = popup(700, 590, 300, 400);

        assertEquals(new LogicalPoint(700, 590), popup.position());
        assertEquals(LogicalSize.of(800, 600), window.size(),
                "and the owner is unaffected — a popup is a second window, not a child box");
    }

    @Test
    @DisplayName("moving is cheaper than reopening, and reports where it went")
    void moves() {
        var popup = popup(10, 10, 120, 80);

        popup.move(LogicalPoint.of(64, 200));

        assertEquals(new LogicalPoint(64, 200), popup.position());
        assertEquals(1, popup.moveCount());
    }

    /// A filtering autocomplete narrows as the list shortens, so a popup that
    /// could not resize would be a list with blank space at the bottom.
    /// The timing is the assertion. On X11 and Wayland a resize is a request the
    /// window manager grants when it likes, so a caller that measured straight
    /// after the call and drew to the answer would draw at the old size on two of
    /// the three desktops — and would pass here, if this fake applied it at once.
    @Test
    @DisplayName("resizing is a request, and lands when the event does")
    void resizes() {
        var popup = popup(0, 0, 200, 300);
        var events = new ArrayList<BackendEvent>();

        popup.resize(LogicalSize.of(200, 120));

        assertEquals(LogicalSize.of(200, 300), popup.size(),
                "not yet: nothing has told the window system, let alone heard back");

        backend.pumpEvents(events::add, Duration.ZERO);

        assertEquals(LogicalSize.of(200, 120), popup.size());
        assertTrue(events.stream().anyMatch(e -> e instanceof BackendEvent.Resized r
                        && r.window() == popup),
                "and a Resized is what says so — SDL posts one for a popup it resized");
    }

    @Test
    @DisplayName("a popup has no titlebar, and is not quietly pretending otherwise")
    void refusesATitle() {
        var popup = popup(0, 0, 100, 100);

        var refused = assertThrows(UnsupportedOperationException.class,
                () -> popup.setTitle("Edit"));
        assertTrue(refused.getMessage().contains("Edit"));
    }

    /// The platform destroys a popup with its parent, so a call afterwards is
    /// reaching through a pointer the window system has already freed.
    @Test
    @DisplayName("a popup whose owner closed refuses to be used")
    void ownerClosed() {
        var popup = popup(0, 0, 100, 100);
        window.close();

        var refused = assertThrows(IllegalStateException.class,
                () -> popup.move(LogicalPoint.of(1, 1)));
        assertTrue(refused.getMessage().contains("owner"), refused.getMessage());
    }

    @Test
    @DisplayName("a closed popup refuses to be used, like any window")
    void closed() {
        var popup = popup(0, 0, 100, 100);
        popup.close();

        assertFalse(popup.isOpen());
        assertThrows(IllegalStateException.class, () -> popup.resize(LogicalSize.of(10, 10)));
        assertEquals(1, backend.windows().size());
    }

    @Test
    @DisplayName("a popup on a window that has already closed is refused")
    void ownerAlreadyClosed() {
        window.close();

        assertThrows(IllegalStateException.class, () -> backend.createPopup(window,
                PopupSpec.of(LogicalPoint.ZERO, LogicalSize.of(10, 10))));
    }

    @Test
    @DisplayName("a tooltip is a different kind, because every window manager treats it as one")
    void tooltipKind() {
        var tooltip = (HeadlessPopup) backend.createPopup(window,
                        PopupSpec.tooltip(LogicalPoint.of(20, 20), LogicalSize.of(140, 24)))
                .orElseThrow();

        assertEquals(PopupKind.TOOLTIP, tooltip.kind());
    }

    @Test
    @DisplayName("a popup needs a size and a finite position")
    void refusesNonsense() {
        assertThrows(IllegalArgumentException.class,
                () -> PopupSpec.of(LogicalPoint.ZERO, LogicalSize.of(0, 100)));
        assertThrows(IllegalArgumentException.class,
                () -> new LogicalPoint(Float.NaN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> popup(0, 0, 100, 100).resize(LogicalSize.of(100, 0)));
    }

    /// The default on the SPI is "no popups", because a backend that has none
    /// should need no code to say so — and the answer must be a refusal a caller
    /// can handle rather than an exception.
    @Test
    @DisplayName("a backend that says nothing about popups has none")
    void defaultIsNone() {
        var silent = new io.github.digitalsmile.goldberry.backend.Backend() {
            @Override
            public String name() {
                return "silent";
            }

            @Override
            public io.github.digitalsmile.goldberry.backend.BackendWindow createWindow(
                    WindowSpec spec) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.List<io.github.digitalsmile.goldberry.backend.BackendWindow> windows() {
                return java.util.List.of();
            }

            @Override
            public int pumpEvents(io.github.digitalsmile.goldberry.backend.EventSink sink,
                    Duration timeout) {
                return 0;
            }

            @Override
            public void wakeup() {
            }

            @Override
            public void close() {
            }
        };

        assertTrue(silent.createPopup(window,
                PopupSpec.of(LogicalPoint.ZERO, LogicalSize.of(10, 10))).isEmpty());
    }
}
