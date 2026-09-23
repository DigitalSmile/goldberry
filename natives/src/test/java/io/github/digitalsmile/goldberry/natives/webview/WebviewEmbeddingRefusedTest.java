package io.github.digitalsmile.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.digitalsmile.goldberry.natives.sdl.window.NativeWindowHandle;

/// What the log says when the shim will not embed a page, per window system.
///
/// The defect this is for: there used to be one message, the X11 one, so a Mac
/// whose page did not open was told to run under XWayland. The message is keyed
/// on the parent handle's kind because that is what the shim branches on, and
/// every kind gets an answer that belongs to it.
///
/// No library is needed: [Webview#embeddingRefused] is a pure function, and
/// reaching it does not touch the lazily loaded shim.
@DisplayName("a refused embedding explains itself")
class WebviewEmbeddingRefusedTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(NativeWindowHandle.Kind.class)
    @DisplayName("with a sentence for every window system")
    void everyKindHasAnAnswer(NativeWindowHandle.Kind kind) {
        assertFalse(Webview.embeddingRefused(kind).isBlank(), kind.name());
    }

    /// The one platform where XWayland is the fix is the one that mentions it.
    @Test
    @DisplayName("sending X11 to XWayland")
    void x11MentionsXWayland() {
        assertTrue(Webview.embeddingRefused(NativeWindowHandle.Kind.X11).contains("XWayland"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = NativeWindowHandle.Kind.class,
            names = {"COCOA", "WIN32"})
    @DisplayName("and nobody else to Wayland, which they do not have")
    void othersDoNotMentionWayland(NativeWindowHandle.Kind kind) {
        var message = Webview.embeddingRefused(kind);

        assertFalse(message.contains("Wayland"), message);
        assertFalse(message.contains("GTK"), message);
    }

    /// Cocoa's failure is the engine's, since nothing about a macOS session
    /// forbids a subview, so that is what it names.
    @Test
    @DisplayName("naming WKWebView on macOS")
    void cocoaNamesItsEngine() {
        assertTrue(Webview.embeddingRefused(NativeWindowHandle.Kind.COCOA).contains("WKWebView"));
    }

    /// Win32 embedding is written now, so its refusal names the two things that
    /// stop WebView2 rather than saying it is not implemented.
    @Test
    @DisplayName("and the runtime on Windows, now that embedding is written there")
    void win32NamesItsRuntime() {
        var message = Webview.embeddingRefused(NativeWindowHandle.Kind.WIN32);

        assertTrue(message.contains("WebView2 Runtime"), message);
        assertFalse(message.contains("not implemented"), message);
    }
}
