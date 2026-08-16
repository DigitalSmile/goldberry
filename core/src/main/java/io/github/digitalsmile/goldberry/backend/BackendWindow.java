package io.github.digitalsmile.goldberry.backend;

import java.util.List;
import java.util.Optional;

/// One window, as the platform sees it.
///
/// Every method is confined to the UI thread (see [Backend]).
public interface BackendWindow extends AutoCloseable {

    /// The size layout works in.
    LogicalSize size();

    /// The size to rasterize at — [#size()] through [#scale()], resolved by the
    /// backend rather than recomputed by callers.
    PhysicalSize physicalSize();

    /// The scale of the display this window is currently on.
    ///
    /// Changes when the window moves between monitors, which arrives as a
    /// [BackendEvent.ScaleChanged].
    DisplayScale scale();

    /// Borrows a frame buffer from the backend to paint into, if it has one to
    /// lend.
    ///
    /// The CPU path otherwise costs a full-frame copy: the toolkit rasterizes into
    /// its own buffer and the backend copies that into the platform's. At 1080p
    /// that copy measured 2–5 ms of every frame — on top of the paint itself and
    /// the platform's own upload. Painting into the buffer the backend hands out
    /// removes that one.
    ///
    /// It does **not** promise the platform's own memory, and on the sdl3 backend
    /// it is not: SDL's Wayland driver has no window-surface implementation, so
    /// `SDL_GetWindowSurface` falls back to a heap buffer that SDL copies into a
    /// texture on every present. What this buys is one copy instead of two, not
    /// zero (ADR-0046). A backend that can lend genuinely mapped memory is
    /// free to, and callers cannot tell the difference.
    ///
    /// The returned buffer is valid until the matching [#present] and must be
    /// handed straight back to it. A backend that has no such memory to lend —
    /// `headless`, or a GPU path — returns empty, and the caller allocates.
    ///
    /// @return the platform's buffer, already the right size, or empty
    default Optional<PixelBuffer> acquireFrame() {
        return Optional.empty();
    }

    /// Hands a rasterized frame to the platform.
    ///
    /// Passing back exactly the buffer [#acquireFrame] returned tells the backend
    /// the pixels are already where they need to be, and the copy is skipped.
    ///
    /// `damage` lists the regions that changed, in physical pixels. An empty list
    /// means nothing changed and the backend may present nothing at all; to
    /// repaint everything, pass [DamageRect#all]. Every rectangle must lie inside
    /// the frame.
    ///
    /// The buffer is borrowed for the duration of the call. Its size must equal
    /// [#physicalSize()] — a mismatch means a resize was processed and the frame
    /// was rasterized against the old size, which is a bug in the frame loop
    /// rather than something a backend can paper over.
    ///
    /// @throws IllegalArgumentException if the buffer does not match the window,
    ///         or if any damage rectangle falls outside it
    void present(PixelBuffer frame, List<DamageRect> damage);

    /// Asks for a [BackendEvent.FrameDue] when the platform is ready to draw.
    ///
    /// Vsync-aligned where the platform offers it. Repeated calls before the
    /// frame arrives coalesce into one — asking twice does not draw twice.
    void requestFrame();

    /// Sets the window title.
    void setTitle(String title);

    /// The current title.
    String title();

    /// Whether this window is still usable. False once [#close()] has run.
    boolean isOpen();

    /// Closes the window. Idempotent.
    ///
    /// Does not throw: a window that is already gone is the normal case during
    /// shutdown, and a `close()` that can fail makes every caller write a
    /// try-catch that does nothing useful.
    @Override
    void close();
}
