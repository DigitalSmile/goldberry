package io.github.digitalsmile.goldberry.widgets.form.field;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The control and the message under it — a [Field]'s second half, and a
/// **part**.
///
/// ## Why a field is two boxes and not four
///
/// §4 wants "consistent label column … control slot, message slot **below**", and
/// those two words are in tension: a label column is a *row* and a message below
/// is a *column*, and a field whose children were label, control and message flat
/// in one box can only have one direction. Laid out as a row — which is what a
/// label column means — the message goes **beside** the control instead of under
/// it, which is what the first version did and what looked wrong the moment
/// somebody put a form on a screen.
///
/// §8's subset has no grid and no wrapping, so the shape has to come from the
/// tree: a field is a row of two, and this is the second one, itself a column.
///
/// ```
/// field           row when horizontal, column when stacked
/// ├── field-label the label and its required marker
/// └── field-body  always a column
///     ├── …       the control slot
///     └── field-message
/// ```
///
/// It costs one node and buys both layouts from one structure — and it is what an
/// action row aligns against, because a `field` with no label is a body in the
/// control's column with nothing beside it.
///
/// @param children the control slot, as the document wrote it, and the message
record FieldBody(List<Widget> children) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "field-body";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
