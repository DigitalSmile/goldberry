package io.github.digitalsmile.goldberry.widgets.core.affix;

import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Located;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/// The hole an [Affix] leaves behind — the CSS type `affix`, and the node that is
/// told where it is.
///
/// It never moves. That is its entire job: it holds the space the child occupied
/// so that nothing below jumps when the child detaches, and it gives the router a
/// rectangle whose position depends on the layout alone. The child slides inside
/// [AffixContent], one level down, which is what stops a widget that reacts to its
/// own position from chasing itself ([ADR-0119]).
record AffixSlot(
        List<Widget> children, Edge edge, double shift, boolean affixed,
        BiConsumer<LogicalRect, LogicalRect> onLocated, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Located {

    @Override
    public String cssType() {
        return "affix";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    /// §1: "`:affixed` is a pseudo-class, so a sticky header can gain a shadow the
    /// moment it lifts."
    @Override
    public boolean isAffixed() {
        return affixed;
    }

    @Override
    public List<Widget> children() {
        return List.of(new AffixContent(children, edge, shift));
    }

    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        onLocated.accept(self, clip);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style)
                .direction(FlexDirection.COLUMN);
    }
}
