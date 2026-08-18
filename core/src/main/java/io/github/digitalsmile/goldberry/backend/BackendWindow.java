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

    /// Whether the buffer [#acquireFrame] lends back still holds the pixels of
    /// the frame before it.
    ///
    /// **The precondition for a partial repaint**, and the reason it is a
    /// question the backend answers rather than something the frame loop assumes.
    /// Damage tracking can say precisely which region changed; repainting only
    /// that region is correct *only* if everything outside it is still on the
    /// buffer. Against a backend that hands over a fresh or recycled buffer it
    /// would draw one control on a field of whatever was there before
    /// ([ADR-0072](../../../../../../book/src/adr/0072-a-partial-repaint-needs-a-promise.md)).
    ///
    /// **False by default**, which is the safe answer: a backend that says
    /// nothing gets a full repaint, exactly as every backend did before this
    /// existed. A backend saying `true` is promising something specific — that
    /// consecutive `acquireFrame` calls at an unchanged size return a buffer whose
    /// contents were not disturbed between them.
    ///
    /// It says nothing about the buffer being the *same* buffer: a backend may
    /// legitimately rotate between two and copy. The caller checks identity
    /// separately, because a buffer that changed underneath is a repaint whatever
    /// this returns.
    default boolean retainsFrameContents() {
        return false;
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

    /// Sets the shape the pointer takes over this window (§7.3).
    ///
    /// Called from pointer motion, so it is asked the same question for every
    /// pixel of a drag: an implementation must make repeating a shape free rather
    /// than talking to the platform each time.
    ///
    /// A shape the platform has no cursor for is the implementation's business,
    /// and the answer is to leave the pointer as it is. `grab` has no system
    /// cursor anywhere and falls back to `move`; a stripped-down cursor theme can
    /// be missing others. None of that is a failure worth propagating to a
    /// caller who only wanted a hand instead of an arrow.
    ///
    /// Default: does nothing, which is right for a backend with no pointer at all.
    default void setCursor(Cursor cursor) {
    }

    /// Where this window's top-left is, in the desktop's logical coordinates.
    ///
    /// The origin of [#workArea()]'s coordinate space, and the only thing that
    /// turns a position expressed in *this window's* coordinates — which is how a
    /// popup is placed, and how a hit test reports — into one that can be compared
    /// against the screen's edges.
    ///
    /// Empty when the platform will not say. A backend with no desktop under it
    /// has no answer, and a placement policy that gets none has to fall back to
    /// its preferred side rather than refuse to open a menu.
    default Optional<LogicalPoint> position() {
        return Optional.empty();
    }

    /// The part of this window's display that a window may usefully occupy — the
    /// full bounds less whatever the desktop reserves for a taskbar, a dock or a
    /// panel, in the desktop's logical coordinates.
    ///
    /// **What flip and shift are computed against** (`docs/core-widgets.md` §7:
    /// "placement with flip/shift when near edges"). Not the display's size: a
    /// menu placed against the screen's bottom edge opens underneath the taskbar,
    /// and the difference between the two rectangles is exactly that taskbar.
    ///
    /// Empty when the platform will not say, which some drivers genuinely will
    /// not — see [#position()] for what a caller does about it.
    default Optional<LogicalRect> workArea() {
        return Optional.empty();
    }

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
