/// The desktop backend: SDL3 owning the windows, the event queue and presentation
/// on all three desktop platforms (ADR-0003).
///
/// A translation layer. SDL's events become backend events, its failures backend
/// exceptions, and its handles never leave; this is the backend that needs
/// `:natives`, where `render.backend.headless` deliberately does not (ADR-0019).
/// Beside the window, popup, tray, clipboard and file-dialog implementations sit
/// the decisions only a real desktop raises: pacing frames to the display, whether
/// a window presents through the GPU (ADR-0479), and Wayland's missing decorations.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.render.backend.sdl3;

import org.jspecify.annotations.NullMarked;
