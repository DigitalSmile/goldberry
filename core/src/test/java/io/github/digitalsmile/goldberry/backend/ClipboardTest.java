package io.github.digitalsmile.goldberry.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The clipboard and text-input halves of the backend SPI, without a platform.
///
/// Both were holes in the SPI that `text-input` is the first consumer of, and
/// what is pinned here is the *contract* rather than either implementation: what
/// a backend with no clipboard reports, what the headless one does instead, and
/// that text input is off until something asks for it.
class ClipboardTest {

    @Nested
    @DisplayName("Clipboard.none()")
    class None {

        @Test
        @DisplayName("reads empty and accepts nothing")
        void acceptsNothing() {
            var clipboard = Clipboard.none();

            assertFalse(clipboard.hasText());
            assertEquals("", clipboard.text());
            assertFalse(clipboard.text("anything"),
                    "a clipboard with nowhere to put text must say so rather than pretend");
            assertEquals("", clipboard.text());
        }

        @Test
        @DisplayName("is what a backend that has not been told about one reports")
        void isTheDefault() {
            // The default method on the interface, not the headless override:
            // an SPI implementation written before the clipboard existed keeps
            // compiling and reports the honest answer.
            var backend = new Backend() {

                @Override
                public String name() {
                    return "bare";
                }

                @Override
                public BackendWindow createWindow(WindowSpec spec) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.util.List<BackendWindow> windows() {
                    return java.util.List.of();
                }

                @Override
                public int pumpEvents(EventSink sink, java.time.Duration timeout) {
                    return 0;
                }

                @Override
                public void wakeup() {
                }

                @Override
                public void close() {
                }
            };

            assertNotNull(backend.clipboard());
            assertFalse(backend.clipboard().hasText());
        }
    }

    @Nested
    @DisplayName("the headless clipboard")
    class Headless {

        @Test
        @DisplayName("is a real one, so a copy/paste test tests the widget")
        void roundTrips() {
            try (var backend = new HeadlessBackend()) {
                var clipboard = backend.clipboard();

                assertFalse(clipboard.hasText(), "a fresh session has an empty clipboard");

                assertTrue(clipboard.text("copied"));
                assertTrue(clipboard.hasText());
                assertEquals("copied", clipboard.text());
            }
        }

        @Test
        @DisplayName("replaces rather than appends")
        void replaces() {
            try (var backend = new HeadlessBackend()) {
                backend.clipboard().text("first");
                backend.clipboard().text("second");

                assertEquals("second", backend.clipboard().text());
            }
        }

        @Test
        @DisplayName("treats a null write as clearing it")
        void nullClears() {
            try (var backend = new HeadlessBackend()) {
                backend.clipboard().text("something");
                backend.clipboard().text(null);

                assertEquals("", backend.clipboard().text());
                assertFalse(backend.clipboard().hasText());
            }
        }

        @Test
        @DisplayName("is the same clipboard for every window in the session")
        void isPerSession() {
            try (var backend = new HeadlessBackend()) {
                backend.createWindow(WindowSpec.of("one", LogicalSize.of(100, 100)));
                backend.createWindow(WindowSpec.of("two", LogicalSize.of(100, 100)));

                backend.clipboard().text("shared");

                // One object, asked twice -- the clipboard belongs to the
                // session, which is why it is on the backend and not the window.
                assertEquals("shared", backend.clipboard().text());
            }
        }
    }

    @Nested
    @DisplayName("text input")
    class TextInput {

        @Test
        @DisplayName("is off until a window is asked for it")
        void offByDefault() {
            try (var backend = new HeadlessBackend()) {
                var window = (HeadlessWindow) backend.createWindow(WindowSpec.of("w", LogicalSize.of(100, 100)));

                // The whole reason the SPI call exists: SDL3 delivers no
                // committed text to a window that has not asked, and asking is
                // what raises an on-screen keyboard.
                assertFalse(window.isTextInputActive());
            }
        }

        @Test
        @DisplayName("turns on and off with focus, not with the window")
        void followsFocus() {
            try (var backend = new HeadlessBackend()) {
                var window = (HeadlessWindow) backend.createWindow(WindowSpec.of("w", LogicalSize.of(100, 100)));

                window.textInput(true);
                assertTrue(window.isTextInputActive());

                window.textInput(false);
                assertFalse(window.isTextInputActive());
            }
        }

        @Test
        @DisplayName("is ignored on a closed window rather than refused")
        void ignoredWhenClosed() {
            try (var backend = new HeadlessBackend()) {
                var window = (HeadlessWindow) backend.createWindow(WindowSpec.of("w", LogicalSize.of(100, 100)));
                window.textInput(true);
                window.close();

                // Focus leaving a field during teardown is the ordinary way this
                // is reached, and the window it would have told is already gone.
                window.textInput(false);

                assertTrue(window.isTextInputActive(),
                        "a closed window keeps whatever it last recorded; nothing is left to tell");
            }
        }
    }
}
