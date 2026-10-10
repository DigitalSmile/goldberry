package dev.goldberry.input.drop;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;

/// A drag in progress, as the pointer router sees it: what was picked up,
/// where the pointer is, and which target it is over.
///
/// Read from `PointerRouter.dragging()`, which is how the frame draws the ghost
/// that follows the pointer and how a test asks whether a drag began. It is a
/// snapshot: the next pointer move makes a new one.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
///
/// @param source       the element that was picked up, the one carrying `draggable`
/// @param payload      what it was picked up with
/// @param from         where the source was painted when it was picked up, in the window
/// @param grab         where in that rectangle the pointer took hold of it
/// @param pointer      where the pointer is now, in the window
/// @param target       the accepting target under the pointer, or null over none
/// @param fromKeyboard whether the keyboard picked it up, in which case no ghost follows a pointer
public record Dragging(
        Element source,
        Object payload,
        LogicalRect from,
        LogicalPoint grab,
        LogicalPoint pointer,
        @Nullable Element target,
        boolean fromKeyboard) {

    public Dragging {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(grab, "grab");
        Objects.requireNonNull(pointer, "pointer");
    }
}
