package dev.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The axis beside one entry: its marker, and the line running on from it — a
/// **part**, CSS-selectable and not constructible (ADR-0065).
///
/// The rail stretches to the entry's full height, so the line is drawn from
/// this marker to the next whatever the body beside it holds; the line is
/// left out after the last marker unless the timeline is pending.
///
/// The marker sits in a [TimelineMarkerCell] as tall as one line of the head
/// beside it, centred — so the dot is on the label's centre whatever the
/// line-height token is, rather than a padding that assumed one (ADR-0345).
///
/// @param icon      an icon for the marker, or null for a dot
/// @param colour    the dot's colour, or 0 for the stylesheet's
/// @param marker    a widget drawn in place of the dot, or null
/// @param placement where the entry is
record TimelineRail(
        @Nullable Icon icon, int colour, @Nullable Widget marker, Entry.Placement placement)
        implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "timeline-rail";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var cell = new TimelineMarkerCell(new TimelineMarker(icon, colour, marker, placement.pending()));
        return placement.continues() ? List.of(cell, new TimelineLine()) : List.of(cell);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
