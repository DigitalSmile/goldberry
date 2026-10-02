package dev.goldberry.example.book.pictures;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.html.view.HtmlStyles;
import dev.goldberry.image.Image;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.markdown.view.MarkdownStyles;
import dev.goldberry.media.view.MediaStyles;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.Widgets;

/// Takes a widget's picture for the guide: its sample inflated against
/// [PreviewValues], laid out in a viewport, drawn at twice the detail in one of
/// the two shades, and cropped to the widget.
///
/// **Through the shipped `Offscreen`**, with the real font book and every
/// stylesheet the showcase loads, so the picture is what an application draws
/// rather than what a test fixture approximates. A `press=`
/// resolves to no action, as in `BookMarkupTest`; a `bind=` resolves to the
/// value [PreviewValues] gives the path, so a slider is drawn at a number and a
/// list with rows in it.
///
/// Confined to the thread that opened it, because the font book is.
public final class PictureCamera implements AutoCloseable {

    /// Twice the detail of a 1&times; display — the picture a retina screen
    /// shows at the widget's own size, and a sharp one on any other.
    public static final float SCALE = 2.0f;

    /// The viewport the sample is laid out in, in **logical** pixels: the width
    /// of the guide's text column, and tall enough for a list.
    public static final int VIEWPORT_WIDTH = 640;

    public static final int VIEWPORT_HEIGHT = 480;

    /// How much page is left around the widget, in logical pixels.
    public static final int MARGIN = 16;

    private final Fonts fonts = Fonts.bundled();
    private final PreviewValues values = new PreviewValues();

    /// The viewport in physical pixels, which is what a render is sized in.
    public static PhysicalSize viewport() {
        return new PhysicalSize(Math.round(VIEWPORT_WIDTH * SCALE), Math.round(VIEWPORT_HEIGHT * SCALE));
    }

    /// The picture of `picture`'s sample in `shade`, cropped.
    ///
    /// @throws IllegalStateException if the sample drew nothing, which is a
    ///         widget whose whole appearance comes from something bound
    public Image shoot(WidgetPicture picture, Shade shade) {
        return scene(picture, shade).render(viewport(), new DisplayScale(SCALE));
    }

    /// The same as a scene the golden harness can render and compare.
    public GoldenImage.Scene scene(WidgetPicture picture, Shade shade) {
        return (size, scale) -> {
            var rendered = Offscreen.of(size)
                    .scale(scale)
                    .background(shade.page())
                    .stylesheets(stylesheets(shade))
                    .fonts(fonts)
                    .render(root(picture));
            var margin = Math.round(MARGIN * scale.factor());
            var crop = Crop.around(rendered, shade.page(), margin)
                    .orElseThrow(() -> new IllegalStateException(picture.name() + " at " + picture.where()
                            + " drew nothing in the " + shade.suffix()
                            + " shade: its whole appearance comes from something bound, so its"
                            + " sample wants a value a preview can show, or the heading a kdl,ignore"
                            + " fence and a picture taken another way"));
            return crop.apply(rendered);
        };
    }

    /// The sample as one tree: its nodes inside the `#gb-picture` column, so
    /// several top-level nodes stack the way they would in a document, and one
    /// fills the viewport the way it would in a window.
    Widget root(WidgetPicture picture) {
        var document = "column id=\"gb-picture\" {\n" + picture.markup() + "\n}";
        return Widgets.inflater(values.wiring())
                .inflateAll(KdlParser.parse(document))
                .getFirst();
    }

    /// The toolkit's rules for the shade's theme, then the content modules' —
    /// the same list `Showcase.stylesheets()` loads, so a `markdown-view` in a
    /// sample is drawn with its rules — and then the page's
    /// own, `pictures.css`, which is what an application's stylesheet would add.
    static List<Stylesheet> stylesheets(Shade shade) {
        var sheets = new ArrayList<>(Controls.stylesheets(shade.theme(), Density.REGULAR));
        sheets.add(MarkdownStyles.stylesheet());
        sheets.add(HtmlStyles.stylesheet());
        sheets.add(MediaStyles.stylesheet());
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, PictureCamera.class, "pictures.css"));
        return sheets;
    }

    @Override
    public void close() {
        values.close();
        fonts.close();
    }
}
