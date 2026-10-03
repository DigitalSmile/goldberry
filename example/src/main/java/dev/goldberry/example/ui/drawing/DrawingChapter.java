package dev.goldberry.example.ui.drawing;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Drawing** screen: the canvas and what it hears, the `image` widget and
/// the `Image` value under it, QR codes, standalone icons, the clipboard's
/// picture half, and a picture taken with no window.
///
/// Every drawing on it is written down, and the cards that answer the pointer
/// draw nothing extra until it touches them, so the picture at rest is the same
/// on every machine.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html).
///
/// @param context what the screen is built from
public record DrawingChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/drawing");

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "drawing",
                "Drawing",
                "A canvas is the surface an application draws on itself, and hears the pointer on. An image"
                        + " shows a decoded picture, a QR code is a payload in whole device pixels, and an icon is"
                        + " one outline at the size of its box.",
                CHAPTER,
                List.of(
                        PathsCard.card(),
                        new PointerCard(),
                        new PlanCard(),
                        new StickyCard(),
                        ImageCards.fits(),
                        ImageCards.codecs(),
                        FigureCards.qrCodes(),
                        FigureCards.icons(),
                        ImageCards.draws(),
                        new ClipboardCard(),
                        ImageCards.rendered()));
    }
}
