package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.example.ui.AppMenu;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import java.util.ArrayList;

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
        paint(name, screen, theme, 900, 560);
    }

    /// The same, at a chosen size — for a screen with more on it than the
    /// window shows.
    private void paint(String name, String screen, Theme theme, int width, int height) {
        actions.pickScreen(screen);

        var inflater = Widgets.inflater(
                // The objects the Forms document names, so this image is the
                // screen the application draws rather than one whose `form` lost
                // its controller to a lenient registry.
                model.named(),
                Icons.strict().bind("palette", palette).bind("plus", plus),
                showcase.models().toArray());
        var tree = new ElementTree(new Screen(model, actions, inflater, plus, () -> { },
                new AppMenu(actions,
                        new AppMenu.Handlers(() -> { }, () -> { }, () -> { }, () -> { }),
                        plus)));

        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(theme, model.density()));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
        // A **frozen** clock, and the Values screen is why: it has a `spinner` on
        // it, whose rotation is a function of the frame clock rather than of a
        // transition (ADR-0081). Against `Clock.system()` this image is a lottery
        // — it failed by 113 pixels and a channel delta of 144, which is a
        // spinner caught a few degrees round. A virtual clock at zero is the
        // frame every machine gets.
        var clock = io.github.digitalsmile.goldberry.motion.Clock.virtual();
        var renderer = new WidgetRenderer(sheets, font).clock(clock);

        // **Two frames, not one.** The first mounts the tree; the second is the
        // one that is drawn. A newly mounted element deliberately starts no
        // transition and a clock-driven arrival has no beginning until something
        // reads the clock, so a screen painted once shows every arriving widget
        // at the *start* of its entrance — which for §7's `message` means four
        // banners at zero opacity, holding their space and drawing nothing. The
        // real loop paints the second frame 16ms later and nobody ever sees the
        // first.
        //
        // 200ms is past `Phase.DURATION_MILLIS`, so everything that arrives has
        // arrived. It is still a frozen clock and still deterministic: the
        // `spinner` on the Values screen and the `skeleton`s on Panels are at
        // whatever they are at 200ms, on every machine.
        //
        // **And the regions are fed back**, which is the other half of the entry
        // TODO.md filed under `text-area` and was open until `masonry` became the
        // second widget to need it. `Measured` is delivered by the *router*, from
        // the rectangles a laid-out frame produced — so a harness that only
        // rendered gave every self-measuring widget a first-frame answer for
        // ever: a `text-area` that wrapped as though it were narrow, and a
        // `masonry` photographed mid-settle.
        //
        // A real window does render → lay out → hand the router the regions, so
        // that is what this does, twice.
        var target = io.github.digitalsmile.goldberry.paint.TestFrames.of(width, height, 1.0f);
        try (var render = io.github.digitalsmile.goldberry.paint.tree.RenderTree.create()) {
            var router = new io.github.digitalsmile.goldberry.input.PointerRouter();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(
                    io.github.digitalsmile.goldberry.input.hit.HitTest.capture(render));
            clock.advance(200);
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(
                    io.github.digitalsmile.goldberry.input.hit.HitTest.capture(render));
        } finally {
            target.end();
        }

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    @Test
    @DisplayName("the Basic screen")
    void basic() {
        paint("gallery-basic", "basic", Theme.NORD_DARK, 1200, 900);
    }

    @Test
    @DisplayName("the Panels screen")
    void panels() {
        paint("gallery-panels", "panels", Theme.NORD_DARK, 1200, 900);
    }

    @Test
    @DisplayName("the Overlays screen")
    void overlays() {
        paint("gallery-overlays", "overlays", Theme.NORD_DARK, 1200, 900);
    }

    @Test
    @DisplayName("the Forms screen")
    void forms() {
        paint("gallery-forms", "forms", Theme.NORD_DARK, 1200, 900);
    }

    @Test
    @DisplayName("the Navigation screen")
    void navigation() {
        paint("gallery-navigation", "navigation", Theme.NORD_DARK, 1200, 900);
    }

    @Test
    @DisplayName("the Collections screen")
    void collections() {
        paint("gallery-collections", "collections", Theme.NORD_DARK, 1200, 900);
    }

    @Test
    @DisplayName("the Charts screen")
    void charts() {
        paint("gallery-charts", "charts", Theme.NORD_DARK, 1200, 900);
    }

    /// The same screen at the size a small window gives it.
    ///
    /// Worth a picture of its own because a masonry's columns are a *count* and
    /// not a media query: two columns of cards at 1200 are two columns at 720 as
    /// well, half as wide and twice as tall. What this asserts is that they still
    /// fit -- a card whose contents had a minimum width would overflow rather than
    /// wrap, and §10's `wrap` is not built (ADR-0196).
    @Test
    @DisplayName("the Basic screen in a narrow window")
    void basicNarrow() {
        paint("gallery-basic-narrow", "basic", Theme.NORD_DARK, 720, 900);
    }

    @Test
    @DisplayName("the Forms screen on the light theme")
    void formsLight() {
        paint("gallery-forms-light", "forms", Theme.NORD_LIGHT, 1200, 900);
    }

    @Test
    @DisplayName("the Basic screen on the light theme")
    void lightTheme() {
        paint("gallery-basic-light", "basic", Theme.NORD_LIGHT, 1200, 900);
    }
}
