package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// A rule between groups of menu items — `docs/core-widgets.md` §8's
/// `separator`.
///
/// A line and nothing else: not focusable, not activatable, and skipped by the
/// arrow keys for free, because focus traversal collects focusable nodes and this
/// is not one.
///
/// Its whole appearance is `controls.css`'s. The widget contributes a box with no
/// content, which is what a 1px rule is.
public record Separator(Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Separator> {

    public Separator() {
        this(Attributes.NONE);
    }

    public Separator {
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public String cssType() {
        return "separator";
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
    public Separator withAttributes(Attributes value) {
        return new Separator(value);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
