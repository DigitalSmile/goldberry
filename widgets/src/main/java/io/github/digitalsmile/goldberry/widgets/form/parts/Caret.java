package io.github.digitalsmile.goldberry.widgets.form.parts;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The insertion point — `text-caret`.
///
/// ## Why this package exists
///
/// `text-input` and `text-area` draw the same three parts, and a part is
/// **styleable and not constructible** ([ADR-0065]) — which in this catalog has
/// always meant package-private, because one widget owns its parts. Two widgets
/// own these.
///
/// So they are `public` in a package the module **does not export**. An
/// application cannot see them, which is the whole of what ADR-0065 asks; the two
/// widgets that draw them can; and there is one `text-caret` rather than two that
/// have to be kept looking alike by hand. JPMS is what makes that expressible —
/// the rule was only ever "package-private" because there was no other way to say
/// it.
///
/// A node rather than a `Box.Mark`, because every mark's shape is fixed by its
/// kind and a caret's is not: it is as tall as a line of its field's own text and
/// as wide as the theme says.
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
        // opacity: §1.7's transition whitelist includes `opacity`, and a caret
        // that faded would be wrong for 150 ms of every blink.
        return visible ? Box.of().style(style) : Box.of();
    }
}
