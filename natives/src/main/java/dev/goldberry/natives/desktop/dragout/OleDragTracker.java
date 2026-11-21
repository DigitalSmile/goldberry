package dev.goldberry.natives.desktop.dragout;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dragout.ole.OleDropSource;
import dev.goldberry.natives.desktop.dragout.ole.OleStructs;
import dev.goldberry.natives.desktop.windows.ComServer;

/// What a drop source does while `DoDragDrop` runs: follows the pointer over
/// the application's own windows, notices the release and Escape, runs a turn
/// of the toolkit's loop each time OLE asks, and says how the drag ended once
/// it returns.
///
/// OLE tells a source nothing about where the pointer is, so it is asked: the
/// [Pointer] answers which of the application's windows it is over, if any,
/// in that window's client pixels. A release over one of them is an in-app
/// drop whatever the target there answered, so the ending comes from where
/// the button came up first and from `DoDragDrop`'s result second.
///
/// A turn is run at most once at a time: application code inside it that
/// makes OLE call back again finds the turn already running and returns.
final class OleDragTracker implements OleDropSource.Callbacks {

    private static final Logger LOG = Logs.of(OleDragTracker.class);

    /// Where the pointer is.
    @FunctionalInterface
    public interface Pointer {

        /// The application's window under the pointer and the point in its
        /// client area, in physical pixels, or null over anything else.
        @Nullable
        Over locate();
    }

    /// The pointer over one of the application's windows.
    ///
    /// @param window the window's `HWND`, as an address
    /// @param x      from the client area's left edge, physical pixels
    /// @param y      from its top edge
    public record Over(long window, int x, int y) {}

    private final Pointer pointer;
    private final DragSourceListener listener;
    private final Runnable turn;

    private @Nullable Over last;
    private @Nullable Over releasedAt;
    private boolean escaped;
    private boolean heard;
    private boolean turning;
    private volatile boolean cancelRequested;

    /// @param pointer  where the pointer is
    /// @param listener told where it is over the application's windows and how
    ///                 the drag ended
    /// @param turn     one turn of the toolkit's loop, run while OLE holds the
    ///                 thread
    public OleDragTracker(Pointer pointer, DragSourceListener listener, Runnable turn) {
        this.pointer = Objects.requireNonNull(pointer, "pointer");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.turn = Objects.requireNonNull(turn, "turn");
    }

    /// Ends the drag at OLE's next question, as cancelled. Safe from inside a
    /// turn, which is where a closing backend asks.
    public void cancel() {
        cancelRequested = true;
    }

    /// Whether OLE asked anything at all: a `DoDragDrop` that failed before
    /// its first question never started.
    public boolean heard() {
        return heard;
    }

    @Override
    public int queryContinueDrag(boolean escape, int keys) {
        heard = true;
        track();
        if (escape || cancelRequested) {
            escaped = true;
            return OleStructs.DRAGDROP_S_CANCEL;
        }
        if ((keys & OleStructs.MOUSE_BUTTONS) == 0) {
            releasedAt = last;
            return OleStructs.DRAGDROP_S_DROP;
        }
        runTurn();
        return cancelRequested ? OleStructs.DRAGDROP_S_CANCEL : ComServer.S_OK;
    }

    @Override
    public int giveFeedback(int effect) {
        heard = true;
        track();
        runTurn();
        return OleStructs.DRAGDROP_S_USEDEFAULTCURSORS;
    }

    /// Tells the listener how the drag ended, from `DoDragDrop`'s result and
    /// the effect it reported.
    ///
    /// @param result `DRAGDROP_S_DROP`, `DRAGDROP_S_CANCEL` or a failure
    /// @param effect the `DROPEFFECT` the target performed
    public void finish(int result, int effect) {
        var where = releasedAt;
        if (where != null && !escaped) {
            listener.releasedOver(where.window(), where.x(), where.y());
        } else if (result == OleStructs.DRAGDROP_S_DROP && !escaped) {
            listener.delivered(action(effect));
        } else {
            listener.cancelled();
        }
    }

    /// The operation a target reported, or null where it reported none (a
    /// move the target finished itself, for one).
    static @Nullable DragAction action(int effect) {
        if ((effect & OleStructs.DROPEFFECT_MOVE) != 0) {
            return DragAction.MOVE;
        }
        if ((effect & OleStructs.DROPEFFECT_COPY) != 0) {
            return DragAction.COPY;
        }
        if ((effect & OleStructs.DROPEFFECT_LINK) != 0) {
            return DragAction.LINK;
        }
        return null;
    }

    private void track() {
        var now = pointer.locate();
        if (Objects.equals(now, last)) {
            return;
        }
        if (now != null) {
            listener.over(now.window(), now.x(), now.y());
        } else {
            listener.outside();
        }
        last = now;
    }

    private void runTurn() {
        if (turning) {
            return;
        }
        turning = true;
        try {
            turn.run();
        } catch (RuntimeException e) {
            LOG.warn("a turn of the loop during a drag failed", e);
        } finally {
            turning = false;
        }
    }
}
