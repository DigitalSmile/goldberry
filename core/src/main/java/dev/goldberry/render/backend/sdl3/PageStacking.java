package dev.goldberry.render.backend.sdl3;

import dev.goldberry.render.window.NativeHandle;

/// Whether a window with a page embedded in it can go on presenting through
/// the GPU, by the window system the page was embedded under (ADR-0491).
///
/// ADR-0479 kept every such window on the CPU because nobody had looked at
/// which of the two shows on top: the page's native view or the swapchain the
/// GPU presents into. That answer differs per window system, so it is given
/// per window system here:
///
///   - **X11: the page.** It is a child X window of SDL's, and the server
///     stacks a child above its parent's contents and clips the parent's
///     presents against it, the Vulkan swapchain's included. Measured on this
///     project's Linux machine under XWayland (ADR-0491).
///   - **Cocoa: unmeasured.** The page is a `WKWebView` subview of the content
///     view, and SDL's Metal claim adds a view of its own there. Which is on top
///     is subview order, and a claim made after the page arrives would put the
///     swapchain over it.
///   - **Win32: unmeasured**, like the rest of the Win32 embedding: a WebView2
///     child `HWND` over a flip-model swapchain.
///
/// An unmeasured platform stays on the CPU, where the answer is known.
sealed interface PageStacking {

    /// The page shows above the swapchain, so the window keeps the GPU.
    record AboveTheSwapchain() implements PageStacking {}

    /// Not known to, so the window presents on the CPU from now on.
    ///
    /// @param reason why, phrased for the log
    record NeedsTheCpu(String reason) implements PageStacking {}

    /// How a page embedded under `kind` stacks against a swapchain.
    static PageStacking of(NativeHandle.Kind kind) {
        return switch (kind) {
            case X11 -> new AboveTheSwapchain();
            case COCOA ->
                new NeedsTheCpu("a page is embedded in it, and a Metal view's stacking against it is unmeasured");
            case WIN32 ->
                new NeedsTheCpu("a page is embedded in it, and a swapchain's stacking against it is unmeasured");
        };
    }
}
