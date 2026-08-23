package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The node a stylesheet calls `masonry`: the row the columns sit in.
///
/// [Masonry] is stateful and styles nothing, which is the arrangement every
/// stateful widget in this catalog uses — the state builds this, and this carries
/// the CSS type and the document's `id` and classes.
record MasonryBox(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "masonry";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
