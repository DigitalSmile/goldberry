package io.github.digitalsmile.goldberry.widgets.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/// What a `web-view` with no page says, per platform — [WebViewRefusal].
///
/// The defect this is for: the widget had one message, the Wayland one, and a
/// Mac showed it. Each case is keyed on the two facts the widget can see — is
/// the library loaded, and which platform is this — so every case is checked
/// here with neither a library nor the platform it describes.
@DisplayName("a web-view with no page")
class WebViewRefusalTest {

    @Nested
    @DisplayName("is told apart by")
    class Choosing {

        /// With no library nothing below it was asked, so no platform-specific
        /// advice can be right.
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Linux", "Mac OS X", "Windows 11", ""})
        @DisplayName("the library first, on every platform")
        void noLibraryWinsEverywhere(String osName) {
            assertEquals(WebViewRefusal.NO_LIBRARY, WebViewRefusal.of(false, osName));
        }

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
            "Linux, WAYLAND",
            "FreeBSD, WAYLAND",
            "Mac OS X, ENGINE_FAILED",
            "Darwin, ENGINE_FAILED",
            "Windows 11, ENGINE_FAILED",
            "Windows Server 2025, ENGINE_FAILED",
        })
        @DisplayName("then the platform, by os.name")
        void thenThePlatform(String osName, WebViewRefusal expected) {
            assertEquals(expected, WebViewRefusal.of(true, osName));
        }

        @Test
        @DisplayName("ignoring the case os.name happens to be in")
        void caseInsensitive() {
            assertEquals(WebViewRefusal.ENGINE_FAILED, WebViewRefusal.of(true, "MAC OS X"));
        }
    }

    @Nested
    @DisplayName("says")
    class Saying {

        @ParameterizedTest(name = "{0}")
        @EnumSource(WebViewRefusal.class)
        @DisplayName("something, in the log and in the box")
        void neverBlank(WebViewRefusal refusal) {
            assertFalse(refusal.log().isBlank(), refusal.name());
            assertFalse(refusal.notice().isBlank(), refusal.name());
        }

        /// The regression itself, and its Windows twin.
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Mac OS X", "Windows 11"})
        @DisplayName("nothing about Wayland or X11 off Linux")
        void onlyLinuxIsSentToXWayland(String osName) {
            var refusal = WebViewRefusal.of(true, osName);

            for (var text : new String[] {refusal.log(), refusal.notice()}) {
                assertFalse(text.contains("Wayland"), text);
                assertFalse(text.contains("X11"), text);
            }
        }

        @Test
        @DisplayName("how to get X11 on Linux, where that is the fix")
        void linuxGetsTheFix() {
            assertTrue(WebViewRefusal.WAYLAND.notice().contains("-Dgoldberry.backend.videoDriver=x11"));
        }

        @Test
        @DisplayName("which library is missing, when one is")
        void namesTheLibrary() {
            assertTrue(WebViewRefusal.NO_LIBRARY.notice().contains("libgoldberry-webview"));
        }
    }
}
