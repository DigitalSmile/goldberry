package dev.goldberry.example.ui.drawing;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.paint.CanvasStyle;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Dash;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.text.Paragraph;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.image.Fit;
import dev.goldberry.widgets.core.image.ImageView;

/// The Drawing screen's cards about pictures: the `image` widget at its fits, the
/// five codecs, an `Image` drawn four ways on a canvas, and a picture taken with
/// no window.
///
/// The painters are static and every number in them is written down, so each
/// card is the same picture in a golden image as on screen.
///
/// Read more: [`image`](https://goldberry.dev/docs/components/drawing.html#image).
final class ImageCards {

    private ImageCards() {}

    /// The `image` widget, contained, covering, filling, and at its own size.
    static Widget fits() {
        return new ShowcaseCard(
                        "image-widget-card",
                        "The image widget",
                        "An image loads off the frame and is its own size until a stylesheet sizes it. In a box"
                                + " of another shape, fit decides: contain, cover, fill, or none. The first three"
                                + " boxes here are square.",
                        DocLink.to("components/drawing", "image"))
                .of(new Row(
                        List.of(
                                new ImageView(Samples.JPEG, "The sample, whole")
                                        .withAttributes(Attributes.NONE
                                                .id("image-contain")
                                                .classes("framed")),
                                new ImageView(Samples.JPEG, "The sample, cropped to a square")
                                        .fit(Fit.COVER)
                                        .withAttributes(Attributes.NONE
                                                .id("image-cover")
                                                .classes("framed")),
                                new ImageView(Samples.JPEG, "The sample, stretched")
                                        .fit(Fit.FILL)
                                        .withAttributes(
                                                Attributes.NONE.id("image-fill").classes("framed")),
                                new ImageView(Samples.JPEG, "The sample, at its own size")
                                        .withAttributes(Attributes.NONE.id("image-natural"))),
                        Attributes.NONE.id("image-row")));
    }

    /// The same picture from PNG, QOI, WebP, GIF and JPEG.
    static Widget codecs() {
        return new ShowcaseCard(
                        "codecs-card",
                        "Five formats, one picture",
                        "PNG, JPEG, QOI, WebP and GIF decode, and the format comes from the bytes, never the file"
                                + " name. The label under each tile is what the first bytes of its file said.",
                        DocLink.to("components/drawing", "image"))
                .of(new Canvas(ImageCards::paintCodecs, Attributes.NONE.id("codecs")));
    }

    /// One decoded image drawn four ways on a canvas.
    static Widget draws() {
        return new ShowcaseCard(
                        "images-card",
                        "An image is a value",
                        "Image.decode turns bytes into a picture with nothing to close. A frame draws it at its"
                                + " natural size, scaled into a rectangle, cropped to a piece of it, or faded.",
                        DocLink.to("guide/text", "images"))
                .of(new Canvas(ImageCards::paintDraws, Attributes.NONE.id("images")));
    }

    /// A widget tree rendered with no window, encoded and decoded back.
    static Widget rendered() {
        return new ShowcaseCard(
                        "rendered-card",
                        "A picture with no window",
                        "Offscreen mounts a tree, lays it out and paints it with no display: a card, two lines and"
                                + " a button, encoded as a PNG and drawn here whole, magnified and faded.",
                        DocLink.to("guide/text", "a-picture-with-no-window"))
                .of(new Canvas(ImageCards::paintRendered, Attributes.NONE.id("rendered")));
    }

    private static void paintDraws(Frame frame, LogicalSize size) {
        var image = Samples.image();
        var width = size.width();

        // Natural size: one image pixel per device pixel, so 96 points wide at
        // 100% and 48 at 200%, crisp on both.
        frame.drawImage(image, 8, 8);

        // Into a rectangle the card chose, which scales without keeping the
        // shape: a caller that wants contain or cover knows both sizes.
        frame.drawImage(image, 116, 8, 128, 64);

        // A crop, in the image's own pixels, drawn into a larger square: a crop
        // and a scale are separate decisions.
        frame.drawImage(image, PhysicalRect.of(13, 11, 26, 26), 256, 8, 64, 64, 1);
        frame.strokePath(Path.roundRect(256, 8, 64, 64, 4), Stroke.of(1), Ink.MUTED);

        // Faded and stretched across the card. The fade is applied to the blit
        // and put back, so the ring over it is at full strength.
        var strip = Math.max(96, width - 16);
        frame.drawImage(image, 8, 96, strip, 56, 0.35);
        frame.strokePath(
                Path.circle(8 + strip * 0.5f, 124, 18), Stroke.round(1.5).dash(Dash.of(0.5, 6)), Ink.ACCENT);
    }

    private static void paintCodecs(Frame frame, LogicalSize size, CanvasStyle style) {
        var all = Samples.codecs();
        var gap = 8f;
        // Shared out of the card's width, at the picture's own 96 by 64 shape.
        var tile = Math.max(40, (size.width() - 16 - gap * (all.size() - 1)) / all.size());
        var tall = tile * 64 / 96;
        var top = 8f;

        for (var i = 0; i < all.size(); i++) {
            var coded = all.get(i);
            var x = 8 + i * (tile + gap);
            frame.drawImage(coded.image(), x, top, tile, tall);
            frame.strokePath(Path.roundRect(x, top, tile, tall, 3), Stroke.of(1), Ink.MUTED);
            Paragraph.of(style.font(), coded.format().name()).paint(frame, x, top + tall + 6, tile, Ink.LINE);
            Paragraph.of(style.font(), coded.bytes() / 1000 + "." + coded.bytes() / 100 % 10 + " kB")
                    .paint(frame, x, top + tall + 22, tile, style.ink());
        }

        Paragraph.of(style.font(), "GIF quantizes to 255 colours and JPEG is lossy; the other three are exact.")
                .paint(frame, 8, top + tall + 46, size.width() - 16, style.ink());
    }

    private static void paintRendered(Frame frame, LogicalSize size) {
        var image = Samples.rendered();
        var width = size.width();

        // Into a rectangle rather than at natural size: the raster is twice the
        // box it was composed in, so natural size would draw it twice as large.
        var boxWidth = Math.min(280f, width - 180);
        var boxHeight = boxWidth * 240 / 560;
        frame.drawImage(image, 8, 8, boxWidth, boxHeight);

        // A piece of it, magnified: its heading at one image pixel per point.
        var right = width - 8 - 160;
        frame.drawImage(image, PhysicalRect.of(24, 16, 320, 56), right, 8, 160, 28, 1);
        frame.strokePath(Path.roundRect(right, 8, 160, 28, 3), Stroke.of(1), Ink.MUTED);

        // And the whole of it again, smaller and faded: a value is drawn as often
        // as a frame likes.
        frame.drawImage(image, right, 48, 160, 160 * 240 / 560f, 0.45);
    }
}
