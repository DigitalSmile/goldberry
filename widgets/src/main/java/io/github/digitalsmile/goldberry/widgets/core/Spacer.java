package io.github.digitalsmile.goldberry.widgets.core;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// Empty space that takes what is left over — `docs/core-widgets.md` §1's
/// `spacer`, "a `flex-grow: 1` shorthand widget".
///
/// ```kdl
/// row { text "Goldberry"; spacer; button "Theme" }
/// ```
public record Spacer(Attributes attributes) implements Widget.Leaf, Styled, Paints, Attributed<Spacer> {

    public Spacer() {
        this(Attributes.NONE);
    }

    @Override
    public Spacer withAttributes(Attributes attributes) {
        return new Spacer(attributes);
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
        // grow(1) unless the stylesheet said otherwise: taking the free space is
        // what a spacer is for, and having to write `spacer { flex-grow: 1 }` in
        // every stylesheet would make the widget pointless.
        var box = Box.of().style(style);
        return style.flexGrow() == 0 ? box.grow(1) : box;
    }
}
