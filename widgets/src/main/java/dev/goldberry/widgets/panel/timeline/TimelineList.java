package dev.goldberry.widgets.panel.timeline;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// What a [Timeline] draws: the entries, down or along.
///
/// `timeline` as a **CSS type** is this node (ADR-0109). A horizontal one
/// carries the class `horizontal` and an alternating one `alternate`, which is
/// the whole of how the stylesheet lays either out.
///
/// @param children   the placed entries, and the pending marker if any
/// @param direction  which way this runs
/// @param align      whether the entries alternate sides
/// @param attributes the timeline's, verbatim
record TimelineList(List<Widget> children, Timeline.Direction direction, Timeline.Align align, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Semantics {

    @Override
    public String cssType() {
        return "timeline";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        var classes = new HashSet<>(attributes.classes());
        if (direction == Timeline.Direction.HORIZONTAL) {
            classes.add("horizontal");
        }
        if (align == Timeline.Align.ALTERNATE) {
            classes.add("alternate");
        }
        return Set.copyOf(classes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// [Role#GROUP] — §10 asks for an ordered list and [Role] has none; the
    /// entries answer [Role#ROW], and the container's word waits for the
    /// AccessKit bridge, as `breadcrumbs`' and `steps`' do.
    @Override
    public Role role() {
        return Role.GROUP;
    }
}
