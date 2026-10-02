package dev.goldberry.widgets.controls.select;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The chips a `select multiple` is holding — a part, styleable as
/// `select-chips` and not constructible from outside the package.
///
/// It exists because of *where* the wrapping goes. The chips wrap with
/// `flex-wrap`, and putting that on the field would be the obvious thing and the
/// wrong one: a field is a row of the chips **and the chevron**, so a row that
/// wraps drops the chevron onto a second line under the chips rather than
/// keeping it at the edge.
///
/// So the chips get a box of their own that wraps and grows, and the field stays
/// a one-line row: chips, then the mark. A box that grows is also what pushes
/// the chevron to the edge, so the field needs no spacer between them.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#select).
record SelectChips(List<Widget> children) implements Widget.Leaf, Styled, Paints {

    /// The canonical constructor, written out so that the parameters taking null for a default can say so.
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
