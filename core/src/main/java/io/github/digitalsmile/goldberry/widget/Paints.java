package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.Paragraph;
import java.util.List;

/// A widget that becomes something on screen.
///
/// A widget that renders answers one question: given the style the cascade
/// resolved for it and the boxes its children produced, what box is it?
///
/// The [Box] it returns is a **value**, and that is what makes ADR-0004's third
/// tree possible without changing anything here. The retained render tree
/// ([io.github.digitalsmile.goldberry.layout.RenderTree]) is reconciled *against*
/// this box tree rather than replacing it: an immutable description is the ideal
/// thing to diff, and it keeps a widget's job "describe yourself" rather than
/// "mutate your render object"
/// ([ADR-0069](../../../../../../book/src/adr/0069-the-render-tree-is-retained.md)).
public interface Paints extends Widget {

    /// What a render pass can offer a widget that needs more than its style.
    ///
    /// An interface rather than a parameter precisely so that it can grow, which
    /// ADR-0053 said when there was only a font on it. [#paragraph] is the first
    /// of the growth, and it is not a convenience: shaping is 56 µs and a widget
    /// tree is re-described every frame, so a `text` node that built its own
    /// paragraph would re-shape unchanged text sixty times a second.
    interface Context {

        /// The font for a node's **own** resolved typography.
        ///
        /// Takes the style rather than answering one font for the window, because
        /// `font-family`, `font-size` and `font-weight` are per node and inherit:
        /// a button's label is Inter 600 and the paragraph beside it is Inter 400,
        /// and both are resolved by the cascade rather than chosen by the widget.
        ///
        /// Backed by a [io.github.digitalsmile.goldberry.text.Fonts] book, so
        /// asking again for a size already open is a map lookup and not a parse.
        Font font(ComputedStyle style);

        /// `text`, shaped for a node's own typography.
        ///
        /// **Always call this rather than `Paragraph.of`.** Two things depend on
        /// it, and the second is not obvious:
        ///
        /// 1. Shaping costs 56 µs and a cache hit 0.05 µs (ADR-0037), and a
        ///    widget tree is rebuilt every frame.
        /// 2. The paragraph that comes back is the **same instance** as last
        ///    frame's for the same text and font — and the retained render tree
        ///    uses that identity to decide it can keep the Yoga measure callback
        ///    it already has. Building an equal-but-distinct paragraph would
        ///    bind a fresh upcall stub per text node per frame, which is another
        ///    11 µs each.
        ///
        /// @throws UnsupportedOperationException if the text is right-to-left,
        ///         which [io.github.digitalsmile.goldberry.text.Paragraph] refuses
        Paragraph paragraph(ComputedStyle style, String text);
    }

    /// Builds this widget's box.
    ///
    /// @param style    what the cascade resolved for this node
    /// @param children the boxes this widget's children produced, in order
    Box render(ComputedStyle style, List<Box> children, Context context);
}
