package dev.goldberry.widgets.panel.calendar;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// One row of seven days — `calendar-week`, a **part**.
///
/// A node rather than forty-two cells in one wrapping row, because the CSS subset
/// has `flex-wrap` and no `grid`: a wrapped row would put the week boundary
/// wherever the width happened to fall, and a calendar whose weeks depend on how
/// wide it was drawn is not a calendar. Seven per row, said by the tree.
///
/// @param days the seven [CalendarDay]s, in the locale's order
record CalendarWeek(List<Widget> days) implements Widget.Leaf, Styled, Paints {

    CalendarWeek {
        days = List.copyOf(days);
    }

    @Override
    public String cssType() {
        return "calendar-week";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return days;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
