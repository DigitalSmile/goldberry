package dev.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.webview.WebviewRequirement.Fail;
import dev.goldberry.natives.webview.WebviewRequirement.Run;
import dev.goldberry.natives.webview.WebviewRequirement.Skip;
import dev.goldberry.natives.webview.calls.WebviewCalls;

/// The shim binds: the library loads, reports the ABI this build binds, and has
/// every symbol the bindings name.
///
/// No page is opened, so no display is needed — binding is `dlopen` and symbol
/// lookups, which is everything a natives jar has to get right for
/// `WebViews.isAvailable()` to be true on a user's machine. A macOS natives jar
/// once shipped without the library at all, and every test here skipped over it,
/// because nothing made the web view required anywhere.
@DisplayName("libgoldberry-webview")
class WebviewBindingTest {

    @Test
    @DisplayName("loads and binds every call, where this build has it")
    void binds() {
        WebviewRequirement.enforce();

        assertTrue(WebviewLibrary.isAvailable(), "the library bound but WebviewLibrary says it is absent");
        assertTrue(Webview.isAvailable(), "the ABI or a symbol disagreed; see the log for which");
    }

    @Test
    @DisplayName("reports the ABI this build binds")
    void agreesAboutTheAbi() {
        WebviewRequirement.enforce();

        var lookup = WebviewLibrary.get().orElseThrow().lookup();
        assertEquals(Webview.ABI, WebviewCalls.bindAbi(lookup).call());
    }

    @Nested
    @DisplayName("when it is missing")
    class WhenMissing {

        @Test
        @DisplayName("runs when the shim is there, required or not")
        void runs() {
            assertInstanceOf(Run.class, WebviewRequirement.decide(true, false, null));
            assertInstanceOf(Run.class, WebviewRequirement.decide(true, true, "/lib/libgoldberry-webview.dylib"));
        }

        @Test
        @DisplayName("skips where nobody said it was required")
        void skips() {
            var decision = WebviewRequirement.decide(false, false, null);

            var skip = assertInstanceOf(Skip.class, decision);
            assertTrue(skip.reason().contains(WebviewLibrary.LIBRARY_STEM), skip.reason());
        }

        @Test
        @DisplayName("fails where it was, saying the jar would ship without it")
        void fails() {
            var decision = WebviewRequirement.decide(false, true, null);

            var fail = assertInstanceOf(Fail.class, decision);
            assertTrue(fail.reason().contains("without web-view"), fail.reason());
        }

        @Test
        @DisplayName("names a library that loaded and would not bind")
        void namesTheFile() {
            var decision = WebviewRequirement.decide(false, true, "/tmp/libgoldberry-webview.dylib");

            var fail = assertInstanceOf(Fail.class, decision);
            assertTrue(
                    fail.reason().contains("/tmp/libgoldberry-webview.dylib loaded but did not bind"), fail.reason());
        }
    }
}
