package io.github.digitalsmile.goldberry.widgets.controls.select;

import java.util.List;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The chips a `select multiple` is holding — a **part**, so it is CSS-selectable
/// and not constructible
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// It exists because of *where* the wrapping goes. §8's subset gained
/// `flex-wrap` for this control, and putting it on the field was the obvious
/// thing and the wrong one: a field is a row of the chips **and the chevron**, so
/// a row that wraps drops the chevron onto a second line under the chips rather
/// than keeping it at the edge. Which is a worse picture than the shrinking it
/// was meant to fix, and it took a golden image to see it
/// ([ADR-0192](../../../../../../../../book/src/adr/0192-a-row-of-chips-wraps-and-the-chevron-does-not.md)).
///
/// So the chips get a box of their own that wraps and grows, and the field stays
/// the one-line row it always was: chips, then the mark. It also takes over the
/// spacer the field used to put between them — a box that grows is what pushes
/// the chevron to the edge, and there is now one that does.
record SelectChips(List<Widget> children) implements Widget.Leaf, Styled, Paints {

    SelectChips {
        children = List.copyOf(children == null ? List.of() : children);
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
