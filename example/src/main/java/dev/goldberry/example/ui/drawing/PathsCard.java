package dev.goldberry.example.ui.drawing;

import dev.goldberry.css.value.CssColor;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Gradient;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Dash;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.canvas.Canvas;

/// The canvas card that draws every kind of segment a [Path] has and every way a
/// [Stroke] draws one, over a gradient that thins out rather than going through
/// grey.
///
/// Static, because every number in it is written down: the card looks the same in
/// a golden image as on screen.
///
/// Read more: [The painter](https://goldberry.dev/docs/components/drawing.html#the-painter).
final class PathsCard {

    private PathsCard() {}

    static Widget card() {
        return new ShowcaseCard(
                        "paths-card",
                        "Paths, strokes and a gradient",
                        "A canvas hands its painter the frame, clipped to its box. This one fills a curve with a"
                                + " fading gradient, strokes it round, and draws a dashed baseline, a dotted ring, a"
                                + " rounded rectangle and an arc.",
                        DocLink.to("components/drawing", "canvas"))
                .of(new Canvas(PathsCard::paint, Attributes.NONE.id("paths")));
    }

    private static void paint(Frame frame, LogicalSize size) {
        var width = size.width();
        var height = size.height();

        // A filled hill, under a ramp that repeats its colour at the transparent
        // end so it thins out rather than greying.
        var hill = Path.builder()
                .moveTo(0, height)
                .lineTo(0, height * 0.55f)
                .cubicTo(width * 0.25f, height * 0.2f, width * 0.45f, height * 0.9f, width * 0.6f, height * 0.5f)
                .cubicTo(width * 0.75f, height * 0.2f, width * 0.85f, height * 0.35f, width, height * 0.3f)
                .lineTo(width, height)
                .close()
                .build();
        frame.fillPath(hill, Gradient.fade(0, height * 0.2f, 0, height, CssColor.fade(Ink.LINE, 0.55)));

        // The same ridge as a round stroke, the pen a line chart draws with.
        var ridge = Path.builder()
                .moveTo(0, height * 0.55f)
                .cubicTo(width * 0.25f, height * 0.2f, width * 0.45f, height * 0.9f, width * 0.6f, height * 0.5f)
                .cubicTo(width * 0.75f, height * 0.2f, width * 0.85f, height * 0.35f, width, height * 0.3f)
                .build();
        frame.strokePath(ridge, Stroke.round(2), Ink.LINE);

        frame.strokePath(
                Path.line(0, height * 0.82f, width, height * 0.82f),
                Stroke.of(1).dashed(4, 4),
                Ink.MUTED);

        // A ring and a dotted ring round it. The dots are a short dash rather
        // than a zero-length one, which the rasterizer would not ink.
        frame.strokePath(Path.circle(width * 0.16f, height * 0.28f, 14), Stroke.round(2), Ink.ACCENT);
        frame.strokePath(
                Path.circle(width * 0.16f, height * 0.28f, 22), Stroke.round(2).dash(Dash.of(0.5, 5)), Ink.ACCENT);

        frame.strokePath(Path.roundRect(width - 76, 14, 60, 30, 8), Stroke.of(1.5), Ink.WARN);
        frame.strokePath(
                Path.arc(width - 46, height * 0.55f, 18, -Math.PI / 2, Math.PI * 1.4), Stroke.round(3), Ink.WARN);
    }
}
