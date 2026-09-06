package io.github.digitalsmile.goldberry.widgets.form.parts;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The affordance that opens a picker's popover — `picker-toggle`, a **part**.
///
/// One type and one CSS rule for §4's three pickers, rather than a
/// `date-picker-toggle`, a `time-picker-toggle` and a `color-picker-toggle` that
/// would be the same twenty lines three times and would have to stay alike by
/// hand. It is here beside [Caret] for that reason and not for filing: this
/// package is where a part shared by more than one field-shaped widget lives,
/// which is what it was made for when `text-input` and `text-area` wanted the
/// same caret.
///
/// **Not focusable, and not a `button`.** §4 gives each picker one Tab stop —
/// the field — and the keyboard's way into the popover is `Alt+Down`.
/// `select-chevron` is the same shape for the same reason.
///
/// A chevron rather than a calendar or a clock, which is a decision about what
/// `Box.Mark` is for: a mark is a shape the painter draws, and adding a glyph per
/// picker would be three shapes to keep. A chevron says *this opens something*,
/// which is what all three do.
///
/// @param onPress what to ask when it is clicked
public record PickerToggle(Runnable onPress) implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "picker-toggle";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            onPress.run();
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CHEVRON_DOWN, style.color(), 1.5));
    }
}
