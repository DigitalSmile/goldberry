package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Located;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Set;

/// One section's heading inside [Scrolling]'s list, and the application's half of
/// `scrollIntoView`.
///
/// It implements [Located] rather than being wrapped in something that does,
/// which is the shape ADR-0120 settled on: a wrapper is a box, and a box in a
/// flex column changes how that column is sized. A widget that wants to know
/// where it is implements the interface on a node that is already there.
///
/// `wanted` is a request the screen above sets and this clears — a header that
/// asked to be revealed on every frame would hold the list against a user trying
/// to scroll away from it.
record SectionHeader(String title, ScrollController list, boolean wanted, Runnable onRevealed)
        implements Widget.Leaf, Styled, Paints, Located {

    @Override
    public String cssType() {
        return "section-header";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(new Text(title));
    }

    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        if (!wanted) {
            return;
        }
        // The controller turns two rectangles into a distance and the viewport
        // clamps it; nothing here does arithmetic, which is the point of
        // `reveal` taking the pair rather than a number.
        list.reveal(self, clip);
        onRevealed.run();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new))
                .direction(FlexDirection.ROW);
    }
}
