package io.github.digitalsmile.goldberry.widgets.form.form;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The node a stylesheet calls `form`.
///
/// [Form] is stateful and styles nothing, so this carries the CSS type, the `id`
/// and the classes — the arrangement every stateful widget in this catalog uses.
///
/// It draws nothing of its own and lays nothing out that a stylesheet could not:
/// a form is a column of fields, and saying so here would be the widget
/// overriding what a document asked for.
///
/// @param children   whatever the document wrote inside
/// @param attributes the `id` and classes it wrote
record FormBox(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "form";
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
    public List<Widget> children() {
        return children;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
