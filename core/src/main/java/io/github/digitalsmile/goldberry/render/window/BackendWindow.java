package io.github.digitalsmile.goldberry.render.window;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.Backend;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.*;

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
    /// (ADR-0072).
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
    default void setCursor(Cursor cursor) {}

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
    /// How many times a second the display this window is on refreshes, or **0**
    /// if the platform will not say.
    ///
    /// The one honest rate a platform can give. SDL has no notion of an achieved
    /// frame rate — `SDL_GetCurrentDisplayMode` reports what the *display* does,
    /// and what a loop managed can only be counted by the loop
    /// (ADR-0153).
    ///
    /// Zero is a legitimate answer rather than a failure: a headless backend has
    /// no display, and SDL documents `refresh_rate` as 0 for a mode it cannot
    /// describe. A caller reads it as "assume nothing".
    default float refreshRate() {
        return 0f;
    }

    default Optional<LogicalRect> workArea() {
        return Optional.empty();
    }

    /// How many of the display's refreshes have gone by, since this window
    /// opened, with a frame **asked for and not yet delivered**.
    ///
    /// Monotonic, like a frame count and for the same reason: a caller reads it
    /// twice and takes the difference, so nothing here has to know what window
    /// of frames anybody is averaging over.
    ///
    /// **The half of a dropped frame only the backend can see.** What the frame
    /// loop records is the frames it painted, and a frame that was never painted
    /// leaves no record at all — so a loop delivering every other refresh looks
    /// exactly like a loop delivering every one, only slower. The pacer knows the
    /// difference, because it knows both when the request arrived and when the
    /// display could have taken it ([ADR-0271]).
    ///
    /// Zero on a backend that does not pace — which is the headless one, where
    /// there is no display to be late for.
    default long lateFrames() {
        return 0L;
    }

    /// Asks the platform to start or stop delivering committed text to this
    /// window as [BackendEvent.TextInput].
    ///
    /// **Off by default, and this is not an optimisation.** SDL3 delivers no
    /// `TEXT_INPUT` event until a window asks for one, because asking is what
    /// raises an on-screen keyboard on a tablet and what tells an IME where its
    /// candidate window belongs. A toolkit that turned it on at window creation
    /// would put a keyboard over every phone screen showing a button.
    ///
    /// So it follows **focus, not the window**: a field turns it on when focus
    /// arrives and off when focus leaves, and a window with nothing editable in
    /// it never asks at all. Repeating the current state is harmless.
    ///
    /// Failure is not reported. A platform that will not start text input is one
    /// whose key events still arrive, and there is nothing a caller could
    /// usefully do about it that it is not already doing.
    ///
    /// @param active whether committed text should be delivered
    default void textInput(boolean active) {}

    /// Tells the platform where the text being typed is, so an input method can
    /// put its candidate window beside it — `docs/gaps.md` G15.
    ///
    /// Without it the list opens wherever the compositor guesses, which on a
    /// large window is routinely over the very text being composed. With it, a
    /// CJK user sees the same arrangement a native application gives them.
    ///
    /// Called by whatever owns the caret, whenever the caret moves — the cost is
    /// one platform call and the alternative is a list that sits still while the
    /// text scrolls out from under it. Null clears the area, which is what focus
    /// leaving an editable field does.
    ///
    /// The rectangle is the **line** being typed on, in this window's logical
    /// coordinates, and `cursor` is the caret's offset from its left edge: an
    /// input method uses the first to keep its list clear of the text and the
    /// second to align it under the insertion point.
    ///
    /// Failure is not reported, on [#textInput]'s reasoning: a platform that will
    /// not place a candidate window is one that still delivers the text.
    ///
    /// @param area   the line being typed on, or null to clear it
    /// @param cursor the caret's x offset from `area`'s left edge
    default void textInputArea(@Nullable LogicalRect area, double cursor) {}

    /// Asks the platform to resize this window, in logical pixels.
    ///
    /// **A request, not a change.** The window manager decides when — and
    /// whether — it happens, so [#size()] keeps reporting the old size until a
    /// [BackendEvent.Resized] arrives, exactly as it would for a drag. A backend
    /// with a window manager to ask hands the request over and reports nothing
    /// itself; the headless one plays the manager and delivers the event, so a
    /// test can drive the whole path.
    ///
    /// This is what lets a frame loop be measured under a resize with no hand on
    /// the window: `--resize=WxH` walks the size a pixel a frame, which is what a
    /// drag produces (ADR-0342). It is also the one way an application can size
    /// its own window after opening it.
    ///
    /// Default: does nothing, for a backend with no window manager to ask.
    ///
    /// @param size the size asked for, in logical pixels; both sides positive
    default void resize(LogicalSize size) {}

    /// Sets the smallest size the **user** may drag this window down to.
    ///
    /// A constraint on the window manager, not a clamp the toolkit applies after
    /// the fact: the platform stops the drag at the edge, so the window never
    /// becomes a size the application cannot lay out (ADR-0304). A backend that
    /// cannot ask its platform for this does nothing, which is the honest answer
    /// — reporting a size it did not enforce would be worse than the constraint
    /// being absent.
    ///
    /// A zero width or height removes the constraint on that axis, which is what
    /// [WindowSpec#NO_MINIMUM] means and what SDL reads `0` as.
    ///
    /// Default: does nothing, for a backend with no window manager to ask.
    ///
    /// @param minimum the floor, in logical pixels
    default void setMinimumSize(LogicalSize minimum) {}

    /// The floor [#setMinimumSize] last set, or a zero size for "no minimum".
    ///
    /// Answered from what the backend was told rather than from the platform:
    /// SDL has `SDL_GetWindowMinimumSize`, and a second native call to read back
    /// a number this process just wrote is a round trip for nothing.
    default LogicalSize minimumSize() {
        return WindowSpec.NO_MINIMUM;
    }

    /// Sets the window title.
    void setTitle(String title);

    /// Asks the platform to maximize this window, or to give its ordinary size
    /// back.
    ///
    /// **A request rather than a setter** (ADR-0252). Every platform routes this
    /// through a window manager that may refuse it, so nothing here returns
    /// whether it happened — that arrives as
    /// [BackendEvent.MaximizedChanged].
    ///
    /// A default no-op, because a backend with no window manager has nothing to
    /// ask: the headless one overrides it to report the change itself, which is
    /// what lets a test drive the whole path.
    default void setMaximized(boolean maximized) {}

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
