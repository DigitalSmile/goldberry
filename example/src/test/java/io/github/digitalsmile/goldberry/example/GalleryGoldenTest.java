package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The gallery, one image per screen (§14: "golden-image CI runs the gallery
/// matrix").
///
/// [ShowcaseDocumentsTest] asserts the *shape* of the documents — that every
/// control is there and every `bind=` reaches the model — and could not tell you
/// whether a screen renders at all. These can: an empty screen, a strip that
/// forgot its rule, or a heading in the wrong colour is a picture that changed
/// ([ADR-0110]).
///
/// `./gradlew :example:test -Dgoldberry.golden.update=true` rewrites them.
class GalleryGoldenTest {

    private ShowcaseModel model;
    private Icon palette;
    private Icon plus;
    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        model = new ShowcaseModel();
        palette = Icon.bundled("palette", 16);
        plus = Icon.bundled("plus", 16);
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        // Null-safe: `setUp` can stop at the renderer requirement on a machine
        // with no native library, and a teardown that assumed otherwise would
        // report its own NPE instead of the skip.
        if (palette != null) {
            palette.close();
        }
        if (plus != null) {
            plus.close();
        }
        if (font != null) {
            font.close();
        }
    }

    /// The whole window, on `screen`.
    private void paint(String name, String screen, Theme theme) {
        model.pickScreen(screen);

        var inflater = Controls.inflater(
                Showcase.actions(model, () -> { }, () -> { }),
                Icons.strict().bind("palette", palette).bind("plus", plus),
                ShowcaseModelRegistry.bindings(model));
        var tree = new ElementTree(new Screen(model, inflater, plus));

        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(theme, model.density()));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
        // A **frozen** clock, and the Values screen is why: it has a `spinner` on
        // it, whose rotation is a function of the frame clock rather than of a
        // transition (ADR-0081). Against `Clock.system()` this image is a lottery
        // — it failed by 113 pixels and a channel delta of 144, which is a
        // spinner caught a few degrees round. A virtual clock at zero is the
        // frame every machine gets.
        var renderer = new WidgetRenderer(sheets, font)
                .clock(io.github.digitalsmile.goldberry.motion.Clock.virtual());

        GoldenImage.assertMatches(name, 900, 560, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    @Test
    @DisplayName("the Controls screen")
    void controls() {
        paint("gallery-controls", "controls", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("the Values screen")
    void values() {
        paint("gallery-values", "values", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("the Text screen")
    void text() {
        paint("gallery-text", "text", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("the Overlays screen")
    void overlays() {
        paint("gallery-overlays", "overlays", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("the Tabs screen")
    void tabs() {
        paint("gallery-tabs", "tabs", Theme.NORD_DARK);
    }

    /// One screen on the light theme, because a gallery that only ever proves
    /// itself on one is half a corpus — and the theme is a stylesheet swap, so one
    /// screen is enough to say the swap works.
    @Test
    @DisplayName("the Controls screen on the light theme")
    void lightTheme() {
        paint("gallery-controls-light", "controls", Theme.NORD_LIGHT);
    }
}
