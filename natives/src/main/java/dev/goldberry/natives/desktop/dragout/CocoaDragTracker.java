package dev.goldberry.natives.desktop.dragout;

import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.macos.ObjC;

/// What a macOS drag source does with AppKit's reports: follows the pointer
/// over the application's own windows, and says how the drag ended.
///
/// A release over one of the application's windows lands there whatever
/// SDL's drop target answered, as on the other platforms: SDL's view takes only
/// files and text, and declines everything else. Escape ends a session with no
/// operation wherever the pointer is, so a drag ended by a key is cancelled
/// even over the application's own window.
final class CocoaDragTracker {

    /// `NSDragOperationCopy`.
    static final long COPY = 1;

    /// `NSDragOperationLink`.
    static final long LINK = 2;

    /// `NSDragOperationGeneric`.
    static final long GENERIC = 4;

    /// `NSDragOperationMove`.
    static final long MOVE = 16;

    /// The pointer over one of the application's windows.
    ///
    /// @param window      the `NSWindow`, as an address
    /// @param x           points from the window's left edge
    /// @param yFromBottom points from its bottom edge
    record Over(long window, double x, double yFromBottom) {}

    private final CocoaDragListener listener;
    private @Nullable Over last;
    private boolean ended;

    CocoaDragTracker(CocoaDragListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /// Whether the drag has ended.
    boolean isEnded() {
        return ended;
    }

    /// The pointer is at `now`, or over no window of the application.
    void moved(@Nullable Over now) {
        if (ended || Objects.equals(now, last)) {
            return;
        }
        if (now != null) {
            listener.over(now.window(), now.x(), now.yFromBottom());
        } else {
            listener.outside();
        }
        last = now;
    }

    /// The session ended at `at`, the target doing `operation`.
    ///
    /// @param escaped whether a key ended it rather than the button
    void ended(@Nullable Over at, long operation, boolean escaped) {
        if (ended) {
            return;
        }
        ended = true;
        if (escaped) {
            listener.cancelled();
        } else if (at != null) {
            listener.releasedOver(at.window(), at.x(), at.yFromBottom());
        } else if (operation != 0) {
            listener.delivered(action(operation));
        } else {
            listener.cancelled();
        }
    }

    /// The `NSDragOperation` mask for `actions`. Copy brings `Generic` with
    /// it, which is what a target asks for when it does not care.
    static long mask(Set<DragAction> actions) {
        var mask = 0L;
        for (var action : actions) {
            mask |= switch (action) {
                case COPY -> COPY | GENERIC;
                case MOVE -> MOVE;
                case LINK -> LINK;
            };
        }
        return mask;
    }

    /// The action a target performed, or null where it said only `Generic`.
    static @Nullable DragAction action(long operation) {
        if ((operation & MOVE) != 0) {
            return DragAction.MOVE;
        }
        if ((operation & COPY) != 0) {
            return DragAction.COPY;
        }
        if ((operation & LINK) != 0) {
            return DragAction.LINK;
        }
        return null;
    }

    /// Where the picture goes in the content view: the pointer at the
    /// picture's hotspot, in the view's own coordinates.
    ///
    /// @param pointerX      the pointer, points from the view's left edge
    /// @param pointerYTop   the pointer, points from the view's top edge
    /// @param viewHeight    the view's height in points
    /// @param flipped       whether the view's origin is its top-left corner
    /// @param width         the picture's width in points
    /// @param height        its height in points
    /// @param hotspotX      the pointer in the picture, points from its left
    /// @param hotspotY      the pointer in the picture, points from its top
    static ObjC.Rect frame(
            double pointerX,
            double pointerYTop,
            double viewHeight,
            boolean flipped,
            double width,
            double height,
            double hotspotX,
            double hotspotY) {
        var x = pointerX - hotspotX;
        var y = flipped ? pointerYTop - hotspotY : viewHeight - pointerYTop - (height - hotspotY);
        return new ObjC.Rect(x, y, width, height);
    }
}
