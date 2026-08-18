package io.github.digitalsmile.goldberry.widgets.panel;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// A surface — `docs/core-widgets.md` §5's `panel`, "plain surface:
/// `--gb-surface`, border, radius tokens. The building block; no elevation."
///
/// ```kdl
/// panel class="sidebar" { text "Settings" }
/// ```
///
/// A container whose **whole** appearance is the stylesheet's: it sets nothing at
/// all, not even a background. That is what separates it from `card`, which will
/// carry elevation, and from `row`/`column`, which own their axis — a panel owns
/// nothing, and is therefore the one container a theme can restyle completely.
public record Panel(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Panel> {

    public Panel(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    public Panel {
        children = List.copyOf(children == null ? List.of() : children);
    }

    @Override
    public Panel withAttributes(Attributes attributes) {
        return new Panel(children, attributes);
    }

    @Override
    public List<Widget> children() {
        return children;
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
    public Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style);
    }
}
