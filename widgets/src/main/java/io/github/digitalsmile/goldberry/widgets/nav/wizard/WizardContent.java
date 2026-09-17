package io.github.digitalsmile.goldberry.widgets.nav.wizard;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The current page's content — a **part**, and the node a page change focuses.
///
/// It takes no focus itself: [io.github.digitalsmile.goldberry.Host#focus]
/// resolves its id to the first focusable thing inside, which is what "advancing
/// moves focus to the new step's first control" needs and all it needs. The
/// page's own `id` and classes land here while it is shown, with the wizard's
/// content id written over the page's, because the wizard is what asks for
/// focus and it has to know what to ask for.
///
/// @param children   the page's content
/// @param attributes the page's, with the content id
record WizardContent(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "wizard-content";
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
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
