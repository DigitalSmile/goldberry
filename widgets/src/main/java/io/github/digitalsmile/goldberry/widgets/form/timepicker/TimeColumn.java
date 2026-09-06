package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One of §4's "hour/minute/second" columns — `time-column`, a **part**.
///
/// ## A wheel and not a list, which is the decision worth writing down
///
/// §4 says "an hour/minute/second column set" and says nothing about how a column
/// is drawn. Sixty minutes in a scrolling viewport is the other answer, and it
/// costs a `scroll` per column, a `ScrollController` per column and a `Located`
/// cell to reveal — a popover whose height depends on how much of a list it
/// decided to show, and a first frame that has to scroll before it is right.
///
/// This is the arrangement every platform's own time picker uses instead: a fixed
/// number of rows centred on the current value, with the neighbours drawn
/// quieter, **wrapping** at both ends. The popover is one height, always; there is
/// nothing to scroll into view because the value is already in the middle; and
/// `23 → 00` is one press of `Down` rather than a journey back up sixty rows.
///
/// The wrap is what makes the quiet neighbours honest, too: a column showing
/// `58 59 00 01 02` is telling the truth about what comes next, where a list
/// clamped at `59` would stop.
///
/// @param cells the visible rows, the middle one being the current value
record TimeColumn(List<Widget> cells) implements Widget.Leaf, Styled, Paints {

    TimeColumn {
        cells = List.copyOf(cells);
    }

    @Override
    public String cssType() {
        return "time-column";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return cells;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
