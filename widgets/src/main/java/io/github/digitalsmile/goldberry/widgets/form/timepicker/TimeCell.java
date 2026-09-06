package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One number in a column — `time-cell`, a **part**.
///
/// **Not focusable**, and it says so by saying nothing: `Handles.isFocusable`
/// already answers false, and an override is what makes a type owe a role it has
/// no honest answer for. §4 gives a picker one Tab stop, which is its field; the
/// popover's keyboard is [TimeColumnsBox]'s.
///
/// `selected` is a class rather than `:checked`, unlike a `calendar-day`. The
/// difference is what the two mean: a chosen date is one of a *set* a user picked
/// from, which is what `:checked` says everywhere else in this catalog, where an
/// hour is one *digit of one value* — a column always has exactly one, nobody
/// chose it, and it changes when a neighbouring column does not.
///
/// @param label    what to draw — two digits
/// @param value    what pressing it means
/// @param selected whether this is the column's current value
/// @param roving   whether the keyboard is on this column, and this is its value
/// @param onPress  told `value` when it is pressed
record TimeCell(String label, int value, boolean selected, boolean roving, IntConsumer onPress)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "time-cell";
    }

    @Override
    public Set<String> classes() {
        var classes = new ArrayList<String>(2);
        if (selected) {
            classes.add("selected");
        }
        if (roving) {
            classes.add("roving");
        }
        return Set.copyOf(classes);
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.PRESSED && event.button() == PointerEvent.Button.PRIMARY) {
            onPress.accept(value);
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(Box.text(context.paragraph(style, label), style.color()));
    }
}
