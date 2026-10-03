package dev.goldberry.widgets.panel;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A plain surface: a container that takes its background, border and radius
/// from the stylesheet and nothing else.
///
/// ```kdl
/// panel class="sidebar" { text "Settings" }
/// ```
///
/// ```java
/// new Panel(new Text("Settings"));
/// ```
///
/// A panel sets nothing itself, not even a background: its whole appearance is
/// the stylesheet's, through `--gb-surface` and the border and radius tokens.
/// That is what separates it from `card`, which carries elevation, and from
/// `row` and `column`, which own their axis. A panel owns nothing, so it is the
/// one container a theme can restyle completely. Its children are laid out by
/// the stylesheet's `flex-direction`, as in any box.
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html#panel).
@Markup("panel")
public record Panel(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Panel> {

    public Panel(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    /// Written out so that the parameters taking null for a default can say so.
    public Panel(@Nullable List<Widget> children, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        // As `card` and `group-box` already did. Without it `new Panel(children,
        // null)` built without complaint and threw from `id()` instead — a null
        // dereference a frame later, in the cascade, about a widget the stack
        // trace does not name.
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.attributes = attributes;
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
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style);
    }

    /// Builds a `panel` from markup.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Panel(children, Attributes.of(node));
    }
}
