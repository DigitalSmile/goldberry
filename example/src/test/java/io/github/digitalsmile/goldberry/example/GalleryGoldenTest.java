package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.bind.Models;
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
import io.github.digitalsmile.goldberry.widgets.Widgets;

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

    private Showcase showcase;
    private ShowcaseModel model;
    private ShowcaseModel.Actions actions;
    private Icon palette;
    private Icon plus;
    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        // The application's own objects, so the gallery is painted against the
        // wiring the window uses rather than a copy of it.
        showcase = new Showcase();
        model = modelOf(ShowcaseModel.class);
        actions = modelOf(ShowcaseModel.Actions.class);
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

    private <T> T modelOf(Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    /// The whole window, on `screen`.
    private void paint(String name, String screen, Theme theme) {
        actions.pickScreen(screen);

        var inflater = Widgets.inflater(
                Icons.strict().bind("palette", palette).bind("plus", plus),
                showcase.models().toArray());
        var tree = new ElementTree(new Screen(model, actions, inflater, plus, () -> { }));

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

    /// §5's containers, and the screen with no value on it at all — so this is
    /// also the image that says a gallery screen can be a document with not one
    /// line of Java behind it.
    ///
    /// The `skeleton`s on it pulse from the frame clock, which is why the clock
    /// this test freezes is load-bearing here in a way it was not for the other
    /// six: without it the placeholders draw a different opacity every run.
    @Test
    @DisplayName("the Panels screen")
    void panels() {
        paint("gallery-panels", "panels", Theme.NORD_DARK);
    }

    /// §4's `text-input`, in the six states one has: bound, filtered, masked,
    /// read-only, disabled, and holding more than it can show.
    ///
    /// **Nothing on it has focus**, so there is no caret in this image — which is
    /// the right thing for a golden to pin. A caret blinks on a timer, so an
    /// image that contained one would be an image of whichever half of the blink
    /// the test happened to catch; what a caret does is [io.github.digitalsmile.goldberry.widgets.form.textinput]'s
    /// unit tests' business, and what a field *looks* like is this one's.
    @Test
    @DisplayName("the Forms screen")
    void forms() {
        paint("gallery-forms", "forms", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("the Tabs screen")
    void tabs() {
        paint("gallery-tabs", "tabs", Theme.NORD_DARK);
    }

    /// The sixth screen, and the only one that is not wrapped in the gallery's
    /// own viewport — it owns one, and §2.4 bans nesting two on an axis.
    ///
    /// It cannot show what it is *for*. A thumb has faded by the time anything is
    /// painted, a sticky header at rest is a header, and a tour has not been
    /// started. What it does prove is that the screen lays out: four sections in a
    /// viewport shorter than they are, with a toolbar above that does not give up
    /// its height to them.
    @Test
    @DisplayName("the Scrolling screen")
    void scrolling() {
        paint("gallery-scrolling", "scrolling", Theme.NORD_DARK);
    }

    /// The Forms screen on the light theme, and the second screen to earn a
    /// light image rather than share the one.
    ///
    /// It earned it by being wrong there and right on the dark theme, which is a
    /// failure a one-screen light corpus cannot catch: a field's fill was
    /// `--gb-surface-2`, one rung off an `--nord6` page, and read as barely
    /// there — the same defect ADR-0166 corrected for `card`, in the same place,
    /// found the same way (ADR-0168). A **light** image of a screen full of
    /// fields is what would have caught it.
    @Test
    @DisplayName("the Forms screen on the light theme")
    void formsLight() {
        paint("gallery-forms-light", "forms", Theme.NORD_LIGHT);
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
