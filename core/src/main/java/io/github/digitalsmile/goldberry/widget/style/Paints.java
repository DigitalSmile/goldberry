package io.github.digitalsmile.goldberry.widget.style;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.CanvasStyle;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.stats.FrameStats;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.text.Paragraph;
import java.util.List;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;

/// A widget that becomes something on screen.
///
/// A widget that renders answers one question: given the style the cascade
/// resolved for it and the boxes its children produced, what box is it?
///
/// The [Box] it returns is a **value**, and that is what makes ADR-0004's third
/// tree possible without changing anything here. The retained render tree
/// ([RenderTree]) is reconciled *against*
/// this box tree rather than replacing it: an immutable description is the ideal
/// thing to diff, and it keeps a widget's job "describe yourself" rather than
/// "mutate your render object"
/// (ADR-0069).
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
        /// Backed by a [io.github.digitalsmile.goldberry.text.font.Fonts] book, so
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
        /// (ADR-0081).
        ///
        /// Read **once per frame** by the renderer and handed to every node, so
        /// two spinners in one window are on the same tick rather than a few
        /// microseconds apart.
        double nowMillis();

        /// A custom property's value as a colour, resolved for **this node**.
        ///
        /// The theming mechanism (§8) reaching a widget that cannot express what
        /// it draws as CSS properties. A chart needs eight series colours and a
        /// node has one `color`; a stylesheet cannot say "the fourth series" and
        /// a `canvas` has no child nodes to hang classes on. So the values live
        /// in the theme as `--gb-chart-1…8` and are read here
        /// (ADR-0195).
        ///
        /// **Resolved through the cascade, so it inherits and can be overridden.**
        /// `#revenue { --gb-chart-1: #b48ead }` recolours one chart's first
        /// series and nothing else, which is the property a Java palette table
        /// would not have.
        ///
        /// Deliberately narrow. It answers *colours*, not arbitrary values: every
        /// other kind of custom property in the toolkit is consumed by a
        /// declaration the cascade already resolves, and a general
        /// token-returning accessor would invite a widget to reimplement the
        /// parser.
        ///
        /// @param name     the property, `--` included
        /// @param fallback what to answer when it is unset or unreadable — a
        ///                 chart with a missing token should draw in a colour
        ///                 rather than not draw
        int color(String name, int fallback);

        /// A **length** custom property, in logical pixels.
        ///
        /// [#color]'s companion, and the second half of the door ADR-0195 opened
        /// ([ADR-0251]). A metric that §3 ships as a component-token default is a
        /// number the stylesheet knows and the widget needs: `--gb-scroll-line`
        /// is how far one wheel line moves a viewport, and a token no widget can
        /// read is a number an author sets and nothing honours.
        ///
        /// Resolved through the cascade like `color`, so it inherits and can be
        /// overridden per node — `#log { --gb-scroll-line: 40px }` makes one
        /// viewport scroll in bigger steps and nothing else.
        ///
        /// **Still deliberately narrow**, on `color`'s terms: it answers lengths
        /// and colours and nothing else. Both are values the cascade already
        /// parses, and a general token-returning accessor would invite a widget
        /// to reimplement the parser.
        ///
        /// `em` and `rem` resolve against the node's own font size ([ADR-0242]),
        /// because this goes through the same `CssLength` the declarations do. A
        /// **percentage** answers the fallback: a percentage is of something, and
        /// a widget asking for a token has no containing block in hand to be a
        /// percentage of.
        ///
        /// @param name     the property, `--` included
        /// @param fallback what to answer when it is unset, unparseable or a
        ///                 percentage
        double length(String name, double fallback);

        /// What the frame loop has been managing lately.
        ///
        /// The third fact here that is about the frame rather than the node, and
        /// it is on this interface for [#nowMillis]'s reason: a widget that went
        /// looking for it itself would find a different answer than the widget
        /// beside it. Two HUDs in one window report one rate.
        ///
        /// [FrameStats#none()] unless something
        /// told the renderer otherwise — a render into a
        /// [io.github.digitalsmile.goldberry.paint.Layer],
        /// or a test, has no frame loop over it and honestly reports no frames.
        ///
        /// **Read, never recorded.** A widget observes the loop; it does not
        /// contribute to it, and nothing here lets it try.
        default FrameStats frames() {
            return FrameStats.none();
        }

        /// Everything a `canvas` painter needs from the cascade, snapshotted for
        /// one node.
        ///
        /// `docs/gaps.md` G11. A painter is handed a frame and a size and nothing
        /// else, so canvas text had to name a font rather than inherit the one
        /// the cascade resolved. This is the bridge: a widget that draws through
        /// a [io.github.digitalsmile.goldberry.paint.StyledPainter] binds it here
        /// and hands the result to [io.github.digitalsmile.goldberry.paint.Box#painting].
        ///
        /// ```java
        /// return Box.of().style(style).painting(painter.bound(context.canvasStyle(style)));
        /// ```
        ///
        /// **A snapshot, taken now.** Every value is read during `render`, while
        /// this context still knows which node it is answering for — [#color] and
        /// [#length] resolve against the element currently being rendered, so a
        /// context held until paint time would answer for whichever node rendered
        /// last. That is also why the token accessors are not on
        /// [io.github.digitalsmile.goldberry.paint.CanvasStyle]: a painter cannot
        /// name in advance the tokens it will want, and a widget that needs them
        /// is a `Paints` and reads them right here.
        default CanvasStyle canvasStyle(ComputedStyle style) {
            return new CanvasStyle(font(style), style.color(), nowMillis(), reducedMotion());
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

    /// [#isAnimating()], asked with what this widget was rendered against.
    ///
    /// The renderer's own question, and the one it calls — once per frame,
    /// **straight after** [#render], with the style that `render` was handed and
    /// the same [Context], so the answer is about the frame just built rather
    /// than about the next one. The default is the no-argument form, which is all
    /// a spinner needs: whether it loops is a fact about how it was built.
    ///
    /// A widget whose answer depends on the clock overrides this one instead. A
    /// `canvas` whose painter settles after a delay is the case: whether it
    /// wants another frame is "has the last tile landed yet", which is a question
    /// about [Context#nowMillis()] rather than about the description
    /// (`docs/gaps.md` G41, [ADR-0348]).
    ///
    /// @param style   the style `render` was given, animation overlay included
    /// @param context the context `render` was given; its per-node accessors still
    ///                answer for this node here
    default boolean isAnimating(ComputedStyle style, Context context) {
        return isAnimating();
    }

    /// Builds this widget's box.
    ///
    /// @param style    what the cascade resolved for this node
    /// @param children the boxes this widget's children produced, in order
    Box render(ComputedStyle style, List<Box> children, Context context);
}
