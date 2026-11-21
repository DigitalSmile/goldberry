package dev.goldberry.input.dragout;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/// What a draggable offers outside the application, and who hears how that
/// ended: the value `Attributes.dragOut` carries.
///
/// The offer is asked for once, when a pointer drag of the widget starts. A
/// supplier rather than a value, because a widget is described on every
/// build and most builds are never dragged: an offer that writes a picture or
/// lists a folder would otherwise be paid for each time.
///
/// @param offer what the drag carries; asked for on the UI thread when the drag starts
/// @param onEnd told how the drag ended, or null to hear nothing
public record DragOut(Supplier<DragOffer> offer, @Nullable Consumer<DragOutcome> onEnd) {

    public DragOut {
        Objects.requireNonNull(offer, "offer");
    }

    /// An offer whose ending nobody hears.
    public static DragOut of(Supplier<DragOffer> offer) {
        return new DragOut(offer, null);
    }

    /// This, told how the drag ended.
    public DragOut onEnd(@Nullable Consumer<DragOutcome> listener) {
        return new DragOut(offer, listener);
    }

    /// This, with a different offer and the same listener.
    public DragOut offer(Supplier<DragOffer> value) {
        return new DragOut(value, onEnd);
    }
}
