package io.github.digitalsmile.goldberry.widgets.panel.list;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;

import java.util.List;
import java.util.Set;

/// The node a stylesheet calls `list`: the column the rows are stacked in.
///
/// [ListView] is stateful and styles nothing, so this carries the CSS type and
/// the `id` and classes the application wrote — the arrangement every stateful
/// widget in this catalog uses.
///
/// [FocusScope#VERTICAL], which is most of §10's keyboard: `Up` and `Down` rove
/// between the rows, and the list is one Tab stop from outside (§7.2).
/// `Home`, `End` and the typeahead are the rows' own for [ListRow]'s reason —
/// each needs rows the focused one cannot see.
///
/// Horizontal roving is absent for `menu`'s reason: a list is one column, and
/// `Left` and `Right` are not its to take. Unlike a `tree`, which navigates
/// *with* them, a list simply has nothing to say to either — so an application
/// that puts something horizontal in a row keeps its own keys.
///
/// @param children the rows, one per item and in the model's order
record ListBox(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "list";
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
