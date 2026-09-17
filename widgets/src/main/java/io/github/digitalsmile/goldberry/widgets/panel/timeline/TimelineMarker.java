package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The dot on the axis — a **part**. Twelve across with a `full` radius; a
/// larger disc with the icon inside when there is one (`timeline-marker.icon`),
/// and a ring with nothing in it for the pending one (`timeline-marker.pending`).
///
/// The colour is the entry's when it gave one and the stylesheet's otherwise
/// — a dot is data when a timeline colours its kinds, which is `chip`'s rule
/// for the same dot (ADR-0328).
///
/// @param icon    the icon, or null
/// @param colour  the fill, or 0 for the stylesheet's
/// @param pending whether this is the trailing unfilled marker
record TimelineMarker(@Nullable Icon icon, int colour, boolean pending) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "timeline-marker";
    }

    @Override
    public Set<String> classes() {
        if (pending) {
            return Set.of("pending");
        }
        return icon == null ? Set.of() : Set.of("icon");
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var box = Box.of().style(style);
        if (colour != 0 && !pending) {
            box = box.background(colour);
        }
        if (icon != null && !pending) {
            box = box.children(Box.icon(icon, style.color()));
        }
        return box;
    }
}
