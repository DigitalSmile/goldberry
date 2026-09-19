package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet calls `masonry`: the row the columns sit in, and the
/// thing that reports **how wide** the wall turned out.
///
/// [Masonry] is stateful and styles nothing, which is the arrangement every
/// stateful widget in this catalog uses — the state builds this, and this carries
/// the CSS type and the document's `id` and classes. Which is exactly why the
/// width is read here and not anywhere else: this is the one node in the subtree
/// whose rectangle *is* the masonry's, so no wrapper had to be invented to ask
/// the question. `IconsScreen` invented one (`icon-sheet`) before `masonry` could
/// answer it, and ADR-0436 is that wrapper turning out to have been the widget's
/// job.
///
/// ## Two readings, two moments
///
/// A responsive wall needs both of these and can take neither in the same place,
/// which is `toaster`'s shape (ADR-0178) rather than a new one:
///
///   - the **gap**, which only `render` is handed, because only `render` is
///     handed the style the cascade resolved. Read rather than assumed: `n`
///     columns fit only if `n` minimums *and* `n - 1` gaps fit, so a stylesheet
///     that changed `masonry { gap }` and nothing else would otherwise leave
///     every wall counting against the wrong pitch;
///   - the **width**, which only the router can give, from the rectangle last
///     frame produced.
///
/// The gap never asks for a frame. It is a constant of the sheet, not of the
/// layout, and it is banked plainly so that a reading taken during `render` can
/// never turn into a `setState` during `render`. The width is what reflows the
/// wall, and it does so only when it changes the *count* — see [MasonryState].
///
/// @param children   the columns
/// @param attributes the document's `id` and classes
/// @param ruler      told the two numbers only this node can take
record MasonryBox(List<Widget> children, Attributes attributes, Ruler ruler)
        implements Widget.Leaf, Styled, Paints, Measured {

    /// What the wall is told about itself.
    interface Ruler {

        /// @param gap `masonry`'s resolved `gap`, in logical pixels
        void gap(double gap);

        /// @param width the wall's own width, in logical pixels
        void width(double width);
    }

    @Override
    public String cssType() {
        return "masonry";
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
    public void measured(Extent bounds, Extent part) {
        ruler.width(bounds.width());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // Points or nothing. A percentage gap on a row is a fraction of the row's
        // own width, which is the number being counted against -- so reading one
        // as a guess would make the count a function of itself, and `toaster`
        // made the same call for the same reason.
        ruler.gap(style.gap() instanceof Length.Points(var points) ? points : 0);
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
