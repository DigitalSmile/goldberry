package dev.goldberry.widgets.core.affix;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.handler.Located;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.paint.Box;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The hole an [Affix] leaves behind — the CSS type `affix`, and the node that is
/// told where it is.
///
/// It never moves. That is its entire job: it holds the space the child occupied
/// so that nothing below jumps when the child detaches, and it gives the router a
/// rectangle whose position depends on the layout alone. The child slides inside
/// [AffixContent], one level down, which is what stops a widget that reacts to its
/// own position from chasing itself.
record AffixSlot(
        List<Widget> children, double shiftX, double shiftY, boolean affixed, Located3 onLocated, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Located {

    @Override
    public String cssType() {
        return "affix";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    /// Whether the content has lifted, for `:affixed`: a pseudo-class, so a sticky
    /// header can gain a shadow the moment it lifts.
    @Override
    public boolean isAffixed() {
        return affixed;
    }

    @Override
    public List<Widget> children() {
        return List.of(new AffixContent(children, shiftX, shiftY));
    }

    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        onLocated.accept(self, clip, clip);
    }

    @Override
    public void located(LogicalRect self, LogicalRect clip, LogicalRect container) {
        onLocated.accept(self, clip, container);
    }

    /// Told the hole, the viewport, and the box the affix must stay inside.
    @FunctionalInterface
    interface Located3 {
        void accept(LogicalRect self, LogicalRect clip, LogicalRect container);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of()
                .children(boxes.toArray(Box[]::new))
                .style(style)
                .direction(FlexDirection.COLUMN)
                // While pinned, this paints after its siblings — and only while
                // pinned. Document order is paint order, so a header sitting
                // where the layout put it would have the rows below it drawn
                // afterwards, straight over the top of it: a sticky header you
                // can read the list through.
                .elevated(affixed);
    }
}
