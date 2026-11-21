package dev.goldberry.render.dragout;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.input.dragout.DragPicture;
import dev.goldberry.render.model.LogicalPoint;

/// A drag a window is asked to hand to the platform.
///
/// @param offer   what the drag carries, never empty
/// @param picture what follows the pointer, or null for the platform's default
/// @param pointer where the pointer is in the window, in logical pixels
public record DragRequest(DragOffer offer, @Nullable DragPicture picture, LogicalPoint pointer) {

    public DragRequest {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(pointer, "pointer");
        if (offer.isEmpty()) {
            throw new IllegalArgumentException("an empty offer is not a drag");
        }
    }
}
