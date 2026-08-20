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
/// frames took this long between them. The min and the max either side of it are
/// what a median would have been reached for, and better: they say where the
/// spread actually is rather than hiding it in the middle (ADR-0154).
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
        // The unit and the shape, which is everything the rows leave unsaid: each
        // is three numbers and a label, and without this line a reader has to
        // guess whether they are milliseconds, whether they are this frame's, and
        // which of the three is which (ADR-0154).
        return frames.capacity() > 0
                ? "ms/frame · min / mean / max · last " + frames.capacity()
                : "ms/frame · min / mean / max";
    }
}
