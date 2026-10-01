package dev.goldberry.widgets.panel.calendar;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// One column heading — `calendar-weekday`, a **part**.
///
/// §2: "header row `caption` in `--gb-text-muted`". The name is the locale's
/// narrow form, which is one letter in English and is not in every language —
/// see [CalendarMonth#weekdayNames].
///
/// @param name what to draw
record CalendarWeekday(String name) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "calendar-weekday";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(Box.text(context.paragraph(style, name), style.color()));
    }
}
