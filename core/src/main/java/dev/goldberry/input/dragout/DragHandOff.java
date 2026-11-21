package dev.goldberry.input.dragout;

import java.util.Objects;

import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;

/// A pointer drag the router has handed over to be started as the
/// platform's: read by the window with `PointerRouter.takeDragOut()` after the
/// event that crossed the threshold, and given back with
/// `PointerRouter.dragOutRefused` when the platform will not take it.
///
/// @param source  the element that was picked up
/// @param payload what it was picked up with, which in-app targets are handed
/// @param out     what it offers outside and who hears how it ended
/// @param from    where the source was painted when it was picked up, in the window
/// @param grab    where in that rectangle the pointer took hold of it
/// @param pointer where the pointer was when the drag began, in the window
public record DragHandOff(
        Element source, Object payload, DragOut out, LogicalRect from, LogicalPoint grab, LogicalPoint pointer) {

    public DragHandOff {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(grab, "grab");
        Objects.requireNonNull(pointer, "pointer");
    }
}
