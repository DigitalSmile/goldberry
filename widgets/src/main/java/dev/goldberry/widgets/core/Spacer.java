package dev.goldberry.widgets.core;

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

/// Empty space that takes what is left over — `docs/core-widgets.md` §1's
/// `spacer`, "a `flex-grow: 1` shorthand widget".
///
/// ```kdl
/// row { text "Goldberry"; spacer; button "Theme" }
/// ```
@Markup("spacer")
public record Spacer(Attributes attributes) implements Widget.Leaf, Styled, Paints, Attributed<Spacer> {

    public Spacer() {
        this(Attributes.NONE);
    }

    @Override
    public Spacer withAttributes(Attributes attributes) {
        return new Spacer(attributes);
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
        // grow(1) unless the stylesheet said otherwise: taking the free space is
        // what a spacer is for, and having to write `spacer { flex-grow: 1 }` in
        // every stylesheet would make the widget pointless.
        var box = Box.of().style(style);
        return style.flexGrow() == 0 ? box.grow(1) : box;
    }

    /// Builds a `spacer` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Spacer(Attributes.of(node));
    }
}
