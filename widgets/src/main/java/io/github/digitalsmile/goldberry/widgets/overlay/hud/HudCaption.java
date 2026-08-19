package io.github.digitalsmile.goldberry.widgets.overlay.hud;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The line under a [Hud] that says what its numbers are — a **part**, so it is
/// CSS-selectable and not constructible (ADR-0065).
///
/// It exists because every number above it is a **mean over the last sixty
/// frames** and nothing said so. `paint 2.1 ms` reads as "this frame" and is not:
/// a spike looks like a plateau on the way in and a plateau looks like a spike on
/// the way out, and a reader with the wrong model of the number draws the wrong
/// conclusion from every one of them
/// ([ADR-0150](../../../../../../../../book/src/adr/0150-a-hud-reads-itself-against-a-budget.md)).
///
/// **A mean and not a median**, which is worth saying because the benchmarks in
/// this repository report medians and a reader who knows that would otherwise
/// assume it here. A ring of sixty is a window, not a sample: it has no outliers
/// to discard, and a mean over it is the thing a budget is actually about — sixty
/// frames took this long between them.
record HudCaption() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "hud-caption";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, text(context.frames())), style.color())
                .style(style);
    }

    /// What the numbers are, in the fewest words that are still true.
    private static String text(io.github.digitalsmile.goldberry.FrameStats frames) {
        if (frames == null || frames.isEmpty()) {
            return "no frames measured";
        }
        // The capacity and not a literal 60: a source that keeps a different
        // window would otherwise be described by this line as though it kept
        // ours. A fixed source reports zero, and "per frame, mean" is all that
        // can honestly be said about numbers somebody chose.
        // **"this hud included"**, because it is. A HUD is a node in the window's
        // own tree: it walks with everything else, and its readings are strings
        // that change every frame, so it shapes text no cache can hold. Measured
        // in the showcase, three paragraphs a frame. ADR-0101's rule is that a
        // diagnostic must not be the thing it measures; it cannot be taken out of
        // this measurement without lying about the frame the window actually
        // painted, so it says so instead (ADR-0152).
        return frames.capacity() > 0
                ? "per frame · mean of last " + frames.capacity() + " · this hud included"
                : "per frame · mean · this hud included";
    }
}
