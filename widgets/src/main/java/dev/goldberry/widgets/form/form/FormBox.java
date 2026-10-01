package dev.goldberry.widgets.form.form;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

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
record FormBox(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "form";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
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
