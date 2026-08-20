package io.github.digitalsmile.goldberry.widgets.panel.collapse;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// **This is the `collapse` a stylesheet selects.**
///
/// [Collapse] is stateful and styles nothing, so this node carries the CSS type
/// and the document's `id` and classes — the split `select`, `tabs` and `menubar`
/// all use.
///
/// `.open` while the body is showing, so a stylesheet can reach the header, the
/// chevron and the body by one class rather than each of them being told.
record CollapseSection(
        String title, boolean open, Runnable onToggle, List<Widget> body,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints {

    CollapseSection {
        body = List.copyOf(body == null ? List.of() : body);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public String cssType() {
        return "collapse";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        if (!open) {
            return attributes.classes();
        }
        var all = new java.util.LinkedHashSet<>(attributes.classes());
        all.add("open");
        return Set.copyOf(all);
    }

    /// The header, and the body only when it is showing.
    ///
    /// The body is a node of its own rather than the children going straight in,
    /// because a `collapse` is a column of two things and the second one needs
    /// padding a stylesheet can set without also indenting the header.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(2);
        parts.add(new CollapseHeader(title, open, onToggle));
        if (open) {
            parts.add(new CollapseBody(body));
        }
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
