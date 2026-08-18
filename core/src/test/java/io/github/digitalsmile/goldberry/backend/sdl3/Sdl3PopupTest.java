package io.github.digitalsmile.goldberry.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.backend.BackendPopup;
import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PopupKind;
import io.github.digitalsmile.goldberry.backend.PopupSpec;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Popups against the **real** SDL, in both of the two states a driver can be in.
///
/// The interesting half is the refusal. `SDL_CreatePopupWindow` fails with
/// `SDL_Unsupported` on a driver without `VIDEO_DEVICE_CAPS_HAS_POPUP_WINDOW_SUPPORT`,
/// and SDL's `dummy` — the driver every headless test here runs under — is one.
/// That is not a defect to work around: it is the reason the SPI returns an
/// `Optional`, and it is the branch a caller has to have an answer for.
///
/// The other half needs a window system, so it runs only where there is one.
class Sdl3PopupTest {

    private static final LogicalSize SIZE = LogicalSize.of(320, 240);

    @BeforeAll
    static void requireLibrary() {
        RendererRequirement.enforce();
    }

    /// SDL's `dummy` driver has no popup support at all, and says so through
    /// `SDL_Unsupported` rather than through a crash.
    @Test
    @DisplayName("a driver with no popups refuses, and the refusal is a value")
    void unsupportedDriverReturnsEmpty() {
        withBackend("dummy", (backend, window) -> {
            var popup = backend.createPopup(window,
                    PopupSpec.of(LogicalPoint.of(10, 10), LogicalSize.of(120, 90)));

            assertTrue(popup.isEmpty(),
                    "the dummy driver has no VIDEO_DEVICE_CAPS_HAS_POPUP_WINDOW_SUPPORT,"
                            + " so this is the branch a menu has to fall back from");
            assertEquals(1, backend.windows().size(),
                    "and nothing was left half-created");
        });
    }

    @Test
    @DisplayName("a popup's owner has to be this backend's window, and has to be open")
    void refusesABadOwner() {
        withBackend("dummy", (backend, window) -> {
            var spec = PopupSpec.of(LogicalPoint.ZERO, LogicalSize.of(10, 10));

            assertThrows(IllegalArgumentException.class,
                    () -> backend.createPopup(new NotOurs(), spec));

            window.close();
            assertThrows(IllegalStateException.class, () -> backend.createPopup(window, spec));
        });
    }

    /// The real thing: a platform window parented to another, positioned in its
    /// coordinates, moved and resized. Needs a window system, so it is skipped
    /// where there is none — which is most of CI, and is why the headless backend
    /// carries the same rules.
    @Test
    @DisplayName("on a real driver a popup opens, moves, resizes and closes")
    void realPopup() {
        assumeADisplay();
        withBackend(null, (backend, window) -> {
            var opened = backend.createPopup(window,
                    PopupSpec.of(LogicalPoint.of(40, 60), LogicalSize.of(200, 150)));
            Assumptions.assumeTrue(opened.isPresent(),
                    "this video driver has no popup windows");

            BackendPopup popup = opened.get();
            assertSame(window, popup.owner());
            assertEquals(PopupKind.MENU, popup.kind());
            assertEquals(new LogicalPoint(40, 60), popup.offset());
            assertTrue(popup.isOpen());
            assertTrue(backend.windows().contains(popup),
                    "a popup is a window, and shutdown enumerates windows");
            assertNotEquals(window.handleId(), ((Sdl3Popup) popup).handleId(),
                    "it is a second platform window with its own id, which is how its"
                            + " events find their way back to it");

            popup.move(LogicalPoint.of(64, 200));
            assertEquals(new LogicalPoint(64, 200), popup.offset());

            // A **request**: on X11 and Wayland the window manager decides when
            // the resize happens, and until it has, `size()` is honestly still
            // the old one. Measuring straight after this call and drawing to the
            // answer is the bug the SPI's wording — and the headless backend's
            // matching delay — exist to stop.
            popup.resize(LogicalSize.of(200, 90));
            var settled = false;
            for (var i = 0; i < 40 && !settled; i++) {
                backend.pumpEvents(e -> { }, java.time.Duration.ofMillis(25));
                settled = popup.size().equals(LogicalSize.of(200, 90));
            }
            assertTrue(settled, "the resize never reached the window system; it was "
                    + popup.size());

            assertThrows(UnsupportedOperationException.class, () -> popup.setTitle("Edit"));

            popup.close();
            assertFalse(popup.isOpen());
            assertEquals(1, backend.windows().size());
        });
    }

    @Test
    @DisplayName("a tooltip is created as one, and is not focusable")
    void realTooltip() {
        assumeADisplay();
        withBackend(null, (backend, window) -> {
            var opened = backend.createPopup(window,
                    PopupSpec.tooltip(LogicalPoint.of(20, 20), LogicalSize.of(140, 24)));
            Assumptions.assumeTrue(opened.isPresent(),
                    "this video driver has no popup windows");

            // The flags are SDL's business once set; what this asserts is that
            // asking for a tooltip produces something SDL accepted, which it does
            // not if TOOLTIP and POPUP_MENU are both set or neither is.
            assertEquals(PopupKind.TOOLTIP, opened.get().kind());
            opened.get().close();
        });
    }

    private static void assumeADisplay() {
        var x11 = System.getenv("DISPLAY");
        var wayland = System.getenv("WAYLAND_DISPLAY");
        Assumptions.assumeTrue(
                (x11 != null && !x11.isBlank()) || (wayland != null && !wayland.isBlank()),
                "no window system, so there is no driver that can make a popup");
    }

    /// `driver` null means "whatever the backend would pick", which is what the
    /// real-popup tests want.
    private static void withBackend(String driver, BiConsumer<Sdl3Backend, Sdl3Window> body) {
        var previousDriver = System.getProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY);
        var previousRate = System.getProperty("goldberry.frame.rate");
        if (driver == null) {
            System.clearProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY);
        } else {
            System.setProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY, driver);
        }
        System.setProperty("goldberry.frame.rate", "0");
        try (var backend = new Sdl3Backend()) {
            var window = (Sdl3Window) backend.createWindow(WindowSpec.of("owner", SIZE));
            body.accept(backend, window);
        } finally {
            restore(Sdl3Backend.VIDEO_DRIVER_PROPERTY, previousDriver);
            restore("goldberry.frame.rate", previousRate);
        }
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    /// A window from no backend at all.
    private static final class NotOurs implements io.github.digitalsmile.goldberry.backend.BackendWindow {

        @Override
        public LogicalSize size() {
            return SIZE;
        }

        @Override
        public io.github.digitalsmile.goldberry.backend.PhysicalSize physicalSize() {
            return new io.github.digitalsmile.goldberry.backend.PhysicalSize(320, 240);
        }

        @Override
        public io.github.digitalsmile.goldberry.backend.DisplayScale scale() {
            return io.github.digitalsmile.goldberry.backend.DisplayScale.ONE;
        }

        @Override
        public void present(io.github.digitalsmile.goldberry.backend.PixelBuffer frame,
                java.util.List<io.github.digitalsmile.goldberry.backend.DamageRect> damage) {
        }

        @Override
        public void requestFrame() {
        }

        @Override
        public void setTitle(String title) {
        }

        @Override
        public String title() {
            return "";
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
