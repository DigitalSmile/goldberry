package io.github.digitalsmile.goldberry.backend.sdl3;

import io.github.digitalsmile.goldberry.backend.BackendPopup;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PopupKind;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import java.util.Objects;

/// An SDL popup window: a menu, a dropdown or a tooltip, parented to another
/// window and free of its bounds.
///
/// It is an [Sdl3Window] and adds three things — an owner, a kind, and a position
/// that means something. Everything else, including the frame pacing and the
/// present path, is inherited unchanged, because a popup's pixels reach the
/// screen by exactly the route a window's do.
final class Sdl3Popup extends Sdl3Window implements BackendPopup {

    private final Sdl3Window owner;
    private final PopupKind kind;

    /// Remembered rather than read back from SDL: `SDL_GetWindowPosition` on a
    /// popup reports the *display's* coordinates on some drivers and the parent's
    /// on others, and this SPI promises an offset from the parent. What was asked
    /// for is the one answer that is the same everywhere.
    private LogicalPoint offset;

    Sdl3Popup(Sdl3Backend backend, SdlWindowHandle handle, Sdl3Window owner,
            PopupKind kind, LogicalPoint offset) {
        super(backend, handle, "");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.offset = Objects.requireNonNull(offset, "offset");
    }

    @Override
    public BackendWindow owner() {
        return owner;
    }

    @Override
    public PopupKind kind() {
        return kind;
    }

    @Override
    public LogicalPoint offset() {
        return offset;
    }

    @Override
    public void move(LogicalPoint next) {
        Objects.requireNonNull(next, "position");
        backend().requireUiThread();
        requireUsable();
        video().setWindowPosition(handle(), Math.round(next.x()), Math.round(next.y()));
        offset = next;
    }

    @Override
    public void resize(LogicalSize size) {
        Objects.requireNonNull(size, "size");
        backend().requireUiThread();
        requireUsable();
        if (size.width() <= 0 || size.height() <= 0) {
            throw new IllegalArgumentException(
                    "a popup needs a positive size, and " + size + " has none");
        }
        video().setWindowSize(handle(), Math.round(size.width()), Math.round(size.height()));
    }

    /// A popup outlives nothing: the platform destroys it with its parent, so a
    /// call on one whose owner has gone is reaching through a dangling pointer
    /// rather than merely being late.
    private void requireUsable() {
        if (!isOpen()) {
            throw new IllegalStateException("this popup has been closed");
        }
        if (!owner.isOpen()) {
            throw new IllegalStateException(
                    "this popup's owner window has closed, and the platform took the popup"
                            + " with it");
        }
    }

    /// A popup has no title, and setting one is a caller confusing it with a
    /// window. Refused rather than ignored: nothing would show it, so a silent
    /// no-op would be a call that appears to work forever.
    @Override
    public void setTitle(String title) {
        throw new UnsupportedOperationException(
                "a popup has no titlebar to put \"" + title + "\" in");
    }

    @Override
    public String toString() {
        return "Sdl3Popup[" + kind + " at " + offset + " on " + owner + "]";
    }
}
