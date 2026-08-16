package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.text.Font;
import java.util.List;

/// A widget that becomes something on screen.
///
/// ADR-0004's third tree is "one render object per visual node, owning a
/// `YGNode`, a `ComputedStyle`, and the paint logic". Today that tree is
/// **materialized as a [Box] tree per frame** rather than retained: `Box` already
/// holds the style and `BoxPainter` already builds the Yoga nodes from it
/// (ADR-0053 explains why, and what it costs).
///
/// So a widget that renders answers one question: given the style the cascade
/// resolved for it and the boxes its children produced, what box is it?
public interface Paints extends Widget {

    /// What a render pass can offer a widget that needs more than its style.
    ///
    /// Currently the font, because text is the only thing that needs one. It is
    /// an interface rather than a parameter so that adding the display scale, an
    /// icon catalog or a text-measurement cache does not change every widget.
    interface Context {

        /// The font to shape text with.
        Font font();
    }

    /// Builds this widget's box.
    ///
    /// @param style    what the cascade resolved for this node
    /// @param children the boxes this widget's children produced, in order
    Box render(ComputedStyle style, List<Box> children, Context context);
}
