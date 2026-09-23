package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.digitalsmile.goldberry.natives.sdl.event.SdlEventType;
import io.github.digitalsmile.goldberry.render.web.BackendWebView;
import io.github.digitalsmile.goldberry.render.web.WebSize;

/// A key typed into an embedded page is the page's — [ADR-0459].
///
/// On macOS SDL handles every key event before the window delivers it to the
/// focused view, so a keystroke typed into a page reaches the backend's queue as
/// well. The backend drops it when a page of that window holds the keyboard.
/// That is two decisions, [Sdl3Backend#isTyping] and
/// [Sdl3Backend#typedIntoAPage], and both are checked here with a fake page and
/// no SDL: whether a real page reports its focus correctly is a platform fact,
/// checked by typing into the showcase's web tab.
@DisplayName("a key typed into an embedded page")
class Sdl3PageKeyboardTest {

    @Nested
    @DisplayName("is recognised as typing")
    class Typing {

        /// All four, not only the presses: a release the application saw for a
        /// press it never did is a stuck key, and text is the same keystrokes
        /// again.
        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = SdlEventType.class,
                names = {"KEY_DOWN", "KEY_UP", "TEXT_INPUT", "TEXT_EDITING"})
        @DisplayName("for every event a keystroke produces")
        void keysAndText(SdlEventType type) {
            assertTrue(Sdl3Backend.isTyping(type.value()), type.name());
        }

        /// The pointer is not the keyboard: a press is what gives the keyboard
        /// back, so it must never be swallowed by the same rule.
        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = SdlEventType.class,
                names = {"MOUSE_BUTTON_DOWN", "MOUSE_BUTTON_UP", "MOUSE_MOTION", "MOUSE_WHEEL"})
        @DisplayName("and for nothing the pointer does")
        void notThePointer(SdlEventType type) {
            assertFalse(Sdl3Backend.isTyping(type.value()), type.name());
        }
    }

    @Nested
    @DisplayName("is dropped")
    class Dropping {

        @Test
        @DisplayName("when a page of the window has the keyboard")
        void whenAPageHasIt() {
            assertTrue(Sdl3Backend.typedIntoAPage(List.of(new FakePage(false, false), new FakePage(true, false))));
        }

        @Test
        @DisplayName("and not when none of them does")
        void notWhenNoneHasIt() {
            assertFalse(Sdl3Backend.typedIntoAPage(List.of(new FakePage(false, false))));
        }

        /// Nearly every window: the lookup answers null, and that must be a
        /// plain no rather than a failure on every keystroke.
        @Test
        @DisplayName("nor for a window with no page at all")
        void notWithoutPages() {
            assertFalse(Sdl3Backend.typedIntoAPage(null));
            assertFalse(Sdl3Backend.typedIntoAPage(List.of()));
        }

        /// A closed page is asked nothing — asking a closed native page throws —
        /// and whatever it last answered no longer holds.
        @Test
        @DisplayName("nor for a page that has been closed")
        void notForAClosedPage() {
            assertFalse(Sdl3Backend.typedIntoAPage(List.of(new FakePage(true, true))));
        }
    }

    /// A page that answers what it is told to, and fails if a closed one is asked.
    private record FakePage(boolean focused, boolean closed) implements BackendWebView {

        @Override
        public boolean hasKeyboardFocus() {
            if (closed) {
                throw new IllegalStateException("a closed page was asked for its focus");
            }
            return focused;
        }

        @Override
        public void navigate(String url) {}

        @Override
        public void html(String html) {}

        @Override
        public void title(String title) {}

        @Override
        public void size(int width, int height, WebSize size) {}

        @Override
        public void bounds(int x, int y, int width, int height) {}

        @Override
        public void eval(String script) {}

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {}
    }
}
