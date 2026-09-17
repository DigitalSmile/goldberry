package io.github.digitalsmile.goldberry.widgets.controls.button;

import java.util.HashSet;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;

/// What a [Floated] puts in the overlay layer: its button, and a switch that
/// sends the button out (ADR-0355).
///
/// An overlay's widget is fixed when the overlay is made, so the button cannot
/// be swapped for a leaving one. What can change is a property the slot is
/// bound to. Setting it rebuilds the slot with `leaving` on the button, and the
/// stylesheet's `button.float.leaving` rule is the exit: §3.1's "out: reverse,
/// fast". The overlay itself is removed once the exit has had its time, by
/// [FloatedState].
///
/// @param button  the floating button, `float` class and all
/// @param leaving whether it is on its way out
record FloatSlot(Button button, Property<Boolean> leaving) implements Widget.Stateless {

    /// The class the exit rule selects on.
    static final String LEAVING = "leaving";

    FloatSlot {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(leaving, "leaving");
    }

    /// The switch, so flipping it rebuilds this slot (ADR-0062).
    @Override
    public @Nullable Observable<?> binding() {
        return leaving;
    }

    /// Whether the button has been sent out.
    boolean isLeaving() {
        return Boolean.TRUE.equals(leaving.get());
    }

    @Override
    public Widget build(BuildContext context) {
        if (!isLeaving()) {
            return button;
        }
        var classes = new HashSet<>(button.attributes().classes());
        classes.add(LEAVING);
        return button.withAttributes(button.attributes().classes(classes.toArray(String[]::new)));
    }
}
