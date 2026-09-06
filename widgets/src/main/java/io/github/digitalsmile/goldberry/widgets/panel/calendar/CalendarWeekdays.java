package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The column headings — `calendar-weekdays`, a **part**, and §2's "header row
/// `caption` in `--gb-text-muted`".
///
/// Outside [CalendarGrid] rather than inside it, which is what stops the day
/// names cross-fading with the month: they are the same seven letters in January
/// and in February, and fading them would be motion that says something changed
/// when nothing did.
///
/// @param names the seven narrow weekday names, starting on the locale's first
///              day of the week
record CalendarWeekdays(List<String> names) implements Widget.Leaf, Styled, Paints {

    CalendarWeekdays {
        names = List.copyOf(names);
    }

    @Override
    public String cssType() {
        return "calendar-weekdays";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var cells = new ArrayList<Widget>(names.size());
        for (var name : names) {
            cells.add(new CalendarWeekday(name));
        }
        return List.copyOf(cells);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
