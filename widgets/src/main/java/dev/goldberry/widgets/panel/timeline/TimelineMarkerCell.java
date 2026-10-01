package dev.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The cell the marker is centred in — a **part** one line tall, so the dot
/// sits on the centre of the head beside it at every density (ADR-0345).
///
/// @param marker the dot, the icon disc, the pending ring or a widget holder
record TimelineMarkerCell(TimelineMarker marker) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "timeline-marker-cell";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(marker);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
