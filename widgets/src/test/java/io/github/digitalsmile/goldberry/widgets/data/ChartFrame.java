package io.github.digitalsmile.goldberry.widgets.data;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;

import java.util.List;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;

/// The 320×180 frame the chart tests read their pixels out of.
///
/// ## Why the four pieces are one class
///
/// A chart test asserts about *colour in a region*: how many rows a series
/// covers, whether a gap is blank, which side a threshold line fell. That answer
/// only means anything if four things agree — the plot is 296×156, it sits in a
/// `#frame` with 12px of padding, the frame is painted into a 320×180 target, and
/// the array is read back row-major at that width.
///
/// Seven tests each kept their own copy of all four, and the copies were
/// identical because they had to be: a test whose `#plot` was a different size
/// from its neighbour's would be counting rows against a different picture and
/// would still pass, silently answering a question nobody asked. They are one
/// class so that the four cannot drift apart — [#WIDTH] and [#HEIGHT] are what
/// [#renderer]'s stylesheet was sized for, and [#pixels] is the only reader that
/// has to know the stride.
///
/// The frame size stays reachable because the tests count in it: `rowsWithData`
/// and its neighbours walk the array themselves, and they walk it at the
/// dimensions the picture was painted at, not at dimensions of their own.
public final class ChartFrame {

    private ChartFrame() {}

    /// The painted frame's width in pixels, and the stride of what [#pixels]
    /// returns.
    public static final int WIDTH = 320;

    /// The painted frame's height in pixels.
    public static final int HEIGHT = 180;

    /// The chart itself — `#plot`, which [#renderer]'s stylesheet sizes.
    public static Attributes plot() {
        return id("plot");
    }

    /// The renderer these tests paint through: the shipped controls sheet, one
    /// theme, and the two rules that give the picture its size.
    ///
    /// One theme rather than a choice of theme, because what is asserted is
    /// geometry and series colour, and both are the same in either.
    public static WidgetRenderer renderer() {
        return new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #frame { padding: 12px; background: var(--gb-bg) }
                                #plot  { width: 296px; height: 156px }
                                """)),
                TestFont.get());
    }

    /// `chart` in the padded `#frame` the stylesheet expects.
    ///
    /// The padding is the point: a chart painted flush to the edge of the target
    /// gives every "is this row blank" assertion a border to trip over.
    public static Widget framed(Widget chart) {
        return new Column(List.of(chart), id("frame"));
    }

    /// `chart`, framed and painted, as one row-major array of ARGB.
    ///
    /// An array rather than the target, because the target's frame is released
    /// when painting ends and every assertion here is made after that.
    public static int[] pixels(Widget chart) {
        var render = renderer();
        var tree = new ElementTree(framed(chart));
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try {
            BoxPainter.paint(target.frame(), render.render(tree));
        } finally {
            target.end();
        }
        var out = new int[WIDTH * HEIGHT];
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                out[y * WIDTH + x] = target.pixel(x, y);
            }
        }
        return out;
    }
}
