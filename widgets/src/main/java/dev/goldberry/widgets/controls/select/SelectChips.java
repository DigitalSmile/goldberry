package dev.goldberry.widgets.controls.select;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The chips a `select multiple` is holding — a **part**, so it is CSS-selectable
/// and not constructible
/// (ADR-0065).
///
/// It exists because of *where* the wrapping goes. §8's subset gained
/// `flex-wrap` for this control, and putting it on the field was the obvious
/// thing and the wrong one: a field is a row of the chips **and the chevron**, so
/// a row that wraps drops the chevron onto a second line under the chips rather
/// than keeping it at the edge. Which is a worse picture than the shrinking it
/// was meant to fix, and it took a golden image to see it
/// (ADR-0192).
///
/// So the chips get a box of their own that wraps and grows, and the field stays
/// the one-line row it always was: chips, then the mark. It also takes over the
/// spacer the field used to put between them — a box that grows is what pushes
/// the chevron to the edge, and there is now one that does.
record SelectChips(List<Widget> children) implements Widget.Leaf, Styled, Paints {

    /// Written out so that the parameters taking null for a default can say so (ADR-0497).
    SelectChips(@Nullable List<Widget> children) {
        children = List.copyOf(children == null ? List.of() : children);
        this.children = children;
    }

    @Override
    public String cssType() {
        return "select-chips";
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
