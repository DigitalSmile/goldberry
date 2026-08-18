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

        /// What time this frame is, on the renderer's clock — §1.7's "animations
        /// are functions of the frame timestamp, not frame counts".
        ///
        /// For the widgets whose motion **cannot be a transition**. A transition
        /// interpolates between two styles the cascade resolved, which is every
        /// state change in the catalog; a spinner and an indeterminate progress
        /// bar have no two states to move between, and §8's subset has no
        /// `@keyframes` to express a loop with. So they are drawn as a function
        /// of this
        /// ([ADR-0081](../../../../../../book/src/adr/0081-a-perpetual-loop-has-no-state.md)).
        ///
        /// Read **once per frame** by the renderer and handed to every node, so
        /// two spinners in one window are on the same tick rather than a few
        /// microseconds apart.
        double nowMillis();

        /// What the frame loop has been managing lately.
        ///
        /// The third fact here that is about the frame rather than the node, and
        /// it is on this interface for [#nowMillis]'s reason: a widget that went
        /// looking for it itself would find a different answer than the widget
        /// beside it. Two HUDs in one window report one rate.
        ///
        /// [io.github.digitalsmile.goldberry.FrameStats#none()] unless something
        /// told the renderer otherwise — a render into a [io.github.digitalsmile.goldberry.Layer],
        /// or a test, has no frame loop over it and honestly reports no frames.
        ///
        /// **Read, never recorded.** A widget observes the loop; it does not
        /// contribute to it, and nothing here lets it try.
        default io.github.digitalsmile.goldberry.FrameStats frames() {
            return io.github.digitalsmile.goldberry.FrameStats.none();
        }

        /// Whether the user asked for less movement (§1.7).
        ///
        /// A widget that animates itself has to ask, because there is no
        /// declaration for the renderer to collapse: `reducedMotion` turns every
        /// *transition* instant, and a loop has no duration to zero. §3.1 gives
        /// both looping controls the same answer — an opacity pulse instead of
        /// movement — which is a different drawing rather than a slower one.
        boolean reducedMotion();
    }

    /// Whether this widget will want another frame after this one.
    ///
    /// False for everything that moves by CSS: a transition is the renderer's to
    /// track, and it already reports itself through
    /// [WidgetRenderer#isAnimating()]. This is for a widget that draws itself
    /// from [Context#nowMillis()] — without it, §1.7's idle frame loop would
    /// paint a spinner once and stop, which is a still picture of a spinner
    /// ([ADR-0081]).
    ///
    /// A **property of the description** rather than a running state: a progress
    /// bar is indeterminate because it was built that way, and one that has been
    /// given a value stops asking. Nothing has to be started or stopped.
    default boolean isAnimating() {
        return false;
    }

    /// Builds this widget's box.
    ///
    /// @param style    what the cascade resolved for this node
    /// @param children the boxes this widget's children produced, in order
    Box render(ComputedStyle style, List<Box> children, Context context);
}
