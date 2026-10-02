/// The desktop backend: SDL3 owning the windows, the event queue and presentation
/// on all three desktop platforms.
///
/// A translation layer. SDL's events become backend events, its failures backend
/// exceptions, and its handles never leave; this is the backend that needs
/// `:natives`, where `render.backend.headless` deliberately does not.
/// Beside the window, popup, tray, clipboard and file-dialog implementations sit
/// the decisions only a real desktop raises: pacing frames to the display, whether
/// a window presents through the GPU, and Wayland's missing decorations.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Architecture](https://goldberry.dev/docs/overview/architecture.html#the-backend-spi).
@NullMarked
package dev.goldberry.render.backend.sdl3;

import org.jspecify.annotations.NullMarked;
