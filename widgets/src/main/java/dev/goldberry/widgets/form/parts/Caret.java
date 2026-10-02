package dev.goldberry.widgets.form.parts;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The insertion point — `text-caret`.
///
/// ## Why this package exists
///
/// `text-input` and `text-area` draw the same three parts, and a part is
/// **styleable and not constructible** — which in this catalogue has
/// always meant package-private, because one widget owns its parts. Two widgets
/// own these.
///
/// So they are `public` in a package the module **does not export**. An
/// application cannot see them, which is all a part asks; the two
/// widgets that draw them can; and there is one `text-caret` rather than two that
/// have to be kept looking alike by hand. JPMS is what makes that expressible —
/// the rule was only ever "package-private" because there was no other way to say
/// it.
///
/// A node rather than a `Box.Mark`, because every mark's shape is fixed by its
/// kind and a caret's is not: it is as tall as a line of its field's own text and
/// as wide as the theme says.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
///
/// @param visible whether this is the lit half of the blink
public record Caret(boolean visible) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "text-caret";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // An invisible caret is a box with **no background**, not one at zero
        // opacity: `opacity` is a property that transitions, and a caret
        // that faded would be wrong for 150 ms of every blink.
        return visible ? Box.of().style(style) : Box.of();
    }
}
