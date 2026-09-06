package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One month's six weeks, as a single node — `calendar-month`, a **part**.
///
/// It exists so that [CalendarGrid]'s cross-fade has **one** thing to pin and one
/// thing to fade. Without it the outgoing month would be six absolutely
/// positioned rows, each needing a top computed from a row height that `render`
/// cannot measure — which is the shape ADR-0196 built the last-frame read to
/// avoid, over a number that does not have to be read at all.
///
/// @param weeks the six [CalendarWeek]s, in order
record CalendarMonthLayer(List<Widget> weeks) implements Widget.Leaf, Styled, Paints {

    CalendarMonthLayer {
        weeks = List.copyOf(weeks);
    }

    @Override
    public String cssType() {
        return "calendar-month";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return weeks;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
