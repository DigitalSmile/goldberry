package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// Half a code — `code-group`, a **part**, and the whole of how §2's "group gap
/// 16 at the midpoint when `length` is even" is drawn.
///
/// §8's selector subset has no `:nth-child`, so nothing in a stylesheet can say
/// "a wider gap after the third box". The alternatives were a zero-width spacer
/// between the halves — which turns one 8-point gap into two and arrives at 16 by
/// an arithmetic nobody reading the CSS would see — or this: the boxes are in
/// groups, the row of groups has the 16 and a group has the 8, and both numbers
/// are written where they are read.
///
/// A code with an **odd** length is one group, so the outer gap never applies and
/// the row is the flat one §2 describes. That is the same tree either way, which
/// is what keeps the stylesheet from having to know which case it is looking at.
///
/// @param boxes the boxes in this half, in order
public record CodeGroup(List<Widget> boxes) implements Widget.Leaf, Styled, Paints {

    public CodeGroup {
        boxes = List.copyOf(boxes);
    }

    @Override
    public String cssType() {
        return "code-group";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return boxes;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
