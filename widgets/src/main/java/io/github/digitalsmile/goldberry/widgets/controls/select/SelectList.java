package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The open half of a [Select]: the panel of options, in a popup window of its
/// own.
///
/// A **part** — CSS-selectable and not constructible
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md))
/// — and a sibling of `menu` rather than a use of it: the two are the same
/// drawing and different meanings, and §3's list is a set of *values* where §8's
/// is a set of *commands*. Sharing the type would mean a stylesheet could only
/// tell a dropdown from a menu by its ancestor, and there is no ancestor —
/// each is the root of its own tree ([ADR-0103]).
///
/// [FocusScope#VERTICAL], which is the whole of its keyboard: `Up` and `Down`
/// rove between the rows, and an [io.github.digitalsmile.goldberry.widgets.controls.option.Option]
/// selects when the keyboard lands on it, exactly as it does inside a
/// `radio-group` or a `segmented`. So arrows move the *value* rather than a
/// highlight that has to be committed — the behaviour a GTK or macOS dropdown
/// has, and the one that needs no second notion of "pending" anywhere in the
/// toolkit ([ADR-0141]).
///
/// Horizontal roving is absent for `menu`'s reason: a list is one column, and
/// `Left` and `Right` are not its to take.
///
/// @param children the rows — the options, already told what they are
record SelectList(List<Widget> children) implements Widget.Leaf, Styled, Paints, Handles {

    SelectList {
        children = List.copyOf(children == null ? List.of() : children);
    }

    @Override
    public String cssType() {
        return "select-list";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public FocusScope focusScope() {
        return FocusScope.VERTICAL;
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
