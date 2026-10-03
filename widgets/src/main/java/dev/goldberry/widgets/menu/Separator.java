package dev.goldberry.widgets.menu;

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

/// A rule between groups of menu rows.
///
/// ```kdl
/// separator
/// ```
///
/// In Java, `new Separator()`. It takes only `id` and `class`.
///
/// A line and nothing else: not focusable, not activatable, and skipped by the
/// arrow keys for free, because focus traversal collects focusable nodes and
/// this is not one. Its whole appearance is the stylesheet's, a 1px line in
/// `--gb-border`; the widget contributes a box with no content.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#separator).
@Markup("separator")
public record Separator(Attributes attributes) implements Widget.Leaf, Styled, Paints, Attributed<Separator> {

    public Separator() {
        this(Attributes.NONE);
    }

    /// Written out so that the parameters taking null for a default can say so.
    public Separator(@Nullable Attributes attributes) {
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.attributes = attributes;
    }

    @Override
    public String cssType() {
        return "separator";
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
    public Separator withAttributes(Attributes value) {
        return new Separator(value);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }

    /// Builds a `separator` from markup.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Separator(Attributes.of(node));
    }
}
