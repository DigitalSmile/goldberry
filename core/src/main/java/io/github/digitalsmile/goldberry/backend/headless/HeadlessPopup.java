package io.github.digitalsmile.goldberry.backend.headless;

import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.BackendPopup;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PopupKind;
import io.github.digitalsmile.goldberry.backend.PopupSpec;
import java.util.Objects;

/// A popup that exists only as state.
///
/// The reason `headless` has one at all: every rule the popup SPI states is a
/// rule some real backend has to keep, and this is where they are checked
/// without a display. A `select` whose list opens in the wrong place fails here
/// on a CI runner with no compositor, rather than on the one platform whose
/// driver nobody happened to test.
public final class HeadlessPopup extends HeadlessWindow implements BackendPopup {

    private final HeadlessWindow owner;
    private final PopupKind kind;

    private LogicalPoint position;
    private int moves;
    private int resizes;

    /// The size asked for and not yet in force. See [#resize].
    private LogicalSize requestedSize;

    HeadlessPopup(HeadlessBackend backend, HeadlessWindow owner, PopupSpec spec,
            DisplayScale scale) {
        super(backend, spec.size(), scale, "");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.kind = spec.kind();
        this.position = spec.position();
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
    public LogicalPoint position() {
        return position;
    }

    @Override
    public void move(LogicalPoint next) {
        Objects.requireNonNull(next, "position");
        backend().requireUiThread();
        requireUsable();
        position = next;
        moves++;
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
        // **Not applied here.** On X11 and Wayland the window manager decides
        // when a resize happens, and `size()` keeps reporting the old one until
        // it has — which a caller that measures straight after this call gets
        // wrong on two of the three desktops. Applying it instantly would make
        // this fake the one place that bug passes, so the size lands when the
        // event is delivered, exactly as it does through SDL.
        requestedSize = size;
        backend().post(new BackendEvent.Resized(this, size, scale().toPhysical(size)));
        resizes++;
    }

    /// A popup has no titlebar to put a title in — refused rather than ignored,
    /// so a caller confusing a popup with a window hears about it.
    @Override
    public void setTitle(String title) {
        throw new UnsupportedOperationException(
                "a popup has no titlebar to put \"" + title + "\" in");
    }

    /// Called by the backend as the resize event is handed over: the point at
    /// which a real platform's new size becomes visible to a caller.
    void resizeDelivered() {
        if (requestedSize != null) {
            applySize(requestedSize);
            requestedSize = null;
        }
    }

    /// How many times this popup has been moved. For tests, like every other
    /// counter on this backend.
    public int moveCount() {
        return moves;
    }

    /// How many times it has been resized.
    public int resizeCount() {
        return resizes;
    }

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

    @Override
    public String toString() {
        return "HeadlessPopup[" + kind + " at " + position + "]";
    }
}
