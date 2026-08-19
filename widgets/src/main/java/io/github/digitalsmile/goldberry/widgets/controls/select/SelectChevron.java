package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The `v` at the end of a closed [Select] — a **part**, so it is CSS-selectable
/// and not constructible
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// It is the only thing that says a field is a dropdown rather than a label with
/// a border, which is the same job the chevron on a menu row does — and it is a
/// painter mark for the same reason: an icon owns native memory that must be
/// closed exactly once (ADR-0043), and a widget is rebuilt every frame.
///
/// [io.github.digitalsmile.goldberry.layout.Box.Mark.Kind#CHEVRON_DOWN] and not
/// `CHEVRON_END` turned: §8's subset has no transform on a mark, and the two
/// point at different places anyway — one says *beside*, the other says *below*.
record SelectChevron() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "select-chevron";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style)
                .mark(new Box.Mark(Box.Mark.Kind.CHEVRON_DOWN, style.color(), 1.5));
    }
}
