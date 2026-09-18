package io.github.digitalsmile.goldberry.example;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.AppMenu;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.html.view.HtmlStyles;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownStyles;
import io.github.digitalsmile.goldberry.offscreen.Offscreen;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
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
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    /// The whole window, on `screen`.
    private void paint(String name, String screen, Theme theme) {
        paint(name, screen, theme, 900, 560);
    }

    /// The same, at a chosen size — for a screen with more on it than the
    /// window shows.
    private void paint(String name, String screen, Theme theme, int width, int height) {
        paint(name, screen, theme, width, height, false);
    }

    /// The same, choosing how the text is drawn: with the one-font renderer these
    /// goldens were taken with, or with a **book**, which is what a screen about
    /// `font-family` needs ([ADR-0386]).
    private void paint(String name, String screen, Theme theme, int width, int height, boolean book) {
        actions.pickScreen(screen);

        var inflater = Widgets.inflater(
                // The objects the Forms document names, so this image is the
                // screen the application draws rather than one whose `form` lost
                // its controller to a lenient registry.
                model.named(),
                Icons.strict().bind("palette", palette).bind("plus", plus),
                showcase.models().toArray());
        var root = new Screen(
                model,
                actions,
                inflater,
                plus,
                () -> {},
                new AppMenu(
                        actions,
                        new AppMenu.Handlers(() -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}),
                        plus));

        // **Through the shipped `Offscreen`** (ADR-0284), which is the same
        // sequence this method used to spell out for itself: mount, lay out, feed
        // the regions back, advance a frozen clock, and paint the second frame.
        //
        // Every one of those steps was here as a comment explaining why the naive
        // version was wrong — a `message` photographed at zero opacity, a
        // `text-area` wrapped as though it were narrow, a `spinner` caught at a
        // random angle against a wall clock. They are the API's now, so the next
        // application to render a screen headlessly gets them without having read
        // this file.
        //
        // `font(...)` and not `fonts(...)`: these goldens were taken with the
        // one-font renderer, which ignores `font-family`, `font-size` and
        // `font-weight`. Handing over a book would be a typography change wearing
        // an infrastructure change's clothes.
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(theme, model.density()));
        // The optional module's rules, exactly as `Showcase.stylesheets()` adds
        // them: the Panels wall holds a `markdown-view`, and a golden taken without
        // these would be a picture of a document the application never draws
        // (ADR-0295).
        sheets.add(MarkdownStyles.stylesheet());
        // And the module's other half, which the HTML screen is entirely made of
        // (ADR-0298).
        sheets.add(HtmlStyles.stylesheet());
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));

        // The size and the scale are the harness's rather than captured here,
        // because a golden that matches is then re-rendered at 2x and 1.5x and
        // checked for describing the same picture (ADR-0162). The whole screen
        // goes through that sweep now, which it always did — the difference is
        // that the render it sweeps is one an application could have written.
        if (book) {
            // A **font book** rather than the one-font renderer, for the one
            // screen whose subject is `font-family`: the emoji sheet draws every
            // glyph through the face the cascade picks, and a renderer that
            // ignores the property would photograph a wall of `.notdef`
            // ([ADR-0386]). Opened and closed per picture, because a book owns
            // the faces it opened.
            try (var fonts = Fonts.bundled()) {
                GoldenImage.assertMatches(
                        name,
                        width,
                        height,
                        1.0f,
                        (size, scale) -> Offscreen.of(size)
                                .scale(scale)
                                .stylesheets(sheets)
                                .fonts(fonts)
                                .render(root));
            }
            return;
        }
        GoldenImage.assertMatches(
                name,
                width,
                height,
                1.0f,
                (size, scale) -> Offscreen.of(size)
                        .scale(scale)
                        .stylesheets(sheets)
                        .font(font)
                        .render(root));
    }

    /// 1720 tall rather than 900, for the Forms screen's reason: the shapes card
    /// and the links card sit below the fold at 900, and a golden that stopped
    /// there would not photograph the two things it exists to show (ADR-0346,
    /// ADR-0347).
    @Test
    @DisplayName("the Basic screen")
    void basic() {
        paint("gallery-basic", "basic", Theme.NORD_DARK, 1200, 1720);
    }

    @Test
    @DisplayName("the Panels screen")
    void panels() {
        paint("gallery-panels", "panels", Theme.NORD_DARK, 1200, 900);
    }

    /// The screen an **optional module** draws, and the only golden in the gallery
    /// that needs a stylesheet the toolkit does not ship (ADR-0295).
    ///
    /// Taller than the walls: this screen is a `split-pane` rather than a masonry, so
    /// what it shows is bounded by the window rather than by how many cards fit —
    /// and the preview is worth more than the fold.
    @Test
    @DisplayName("the Markdown screen, with an editor and its live preview")
    void markdown() {
        paint("gallery-markdown", "markdown", Theme.NORD_DARK, 1200, 1000);
    }

    /// The other half of the same optional module, and the screen `docs/gaps.md` G17
    /// asked for.
    ///
    /// Taller than the walls for the Markdown screen's reason — it is a `split-pane`
    /// rather than a masonry, so what it shows is bounded by the window — and worth its
    /// own image rather than being covered by that one: the two screens share an
    /// arrangement and share no code below it, so a defect in either fold is a picture
    /// that changed here and not there.
    @Test
    @DisplayName("the HTML screen, with an editor, a live preview and a link you can press")
    void html() {
        paint("gallery-html", "html", Theme.NORD_DARK, 1200, 1000);
    }

    @Test
    @DisplayName("the Overlays screen")
    void overlays() {
        paint("gallery-overlays", "overlays", Theme.NORD_DARK, 1200, 900);
    }

    @Test
    @DisplayName("the Forms screen")
    void forms() {
        // **Taller than the window**, which is what the four-argument form is for.
        // The gutter card (`docs/gaps.md` G37, ADR-0331) sits in the second half of
        // the wall, and its whole claim is visible only in the picture: the long
        // paragraph takes **one** number and three lines' height, so the numbers
        // below it are where the wrap put them rather than where a column beside
        // the control would have guessed. A golden that stopped at 900 would not
        // photograph the one thing that card exists to show.
        paint("gallery-forms", "forms", Theme.NORD_DARK, 1200, 1500);
    }

    @Test
    @DisplayName("the Navigation screen")
    void navigation() {
        paint("gallery-navigation", "navigation", Theme.NORD_DARK, 1200, 900);
    }

    /// 1040 tall: the timeline card ends below the fold at 900, and Rivendell's
    /// `badge` marker — the one widget marker in the showcase — sat on the last
    /// row, cut in half (ADR-0356).
    @Test
    @DisplayName("the Collections screen")
    void collections() {
        paint("gallery-collections", "collections", Theme.NORD_DARK, 1200, 1040);
    }

    @Test
    @DisplayName("the Charts screen")
    void charts() {
        paint("gallery-charts", "charts", Theme.NORD_DARK, 1200, 900);
    }

    @Test
    @DisplayName("the canvas screen")
    void canvas() {
        // §1's `canvas`, and the one screen whose cards respond to the pointer.
        // The picture is taken **at rest**: neither interactive card draws
        // anything extra until something touches it, which is what makes a
        // surface an application controls photographable at all (ADR-0281).
        paint("gallery-canvas", "canvas", Theme.NORD_DARK, 1200, 900);
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

    /// The eleventh screen, and the only golden in the gallery of a **virtualized**
    /// tree: what is in the picture is the rows the viewport asked for, not the
    /// 193 the sheet holds ([ADR-0307]).
    ///
    /// It is worth a picture for a reason the others are not — the row height in
    /// `IconsScreen` and the tile height in `showcase.css` are two numbers that
    /// have to agree, and nothing can check that but an image: told the wrong one,
    /// a virtualized list scrolls past its own content and every value assertion
    /// still passes.
    @Test
    @DisplayName("the Icons screen, and every tile in the first viewport")
    void icons() {
        paint("gallery-icons", "icons", Theme.NORD_DARK, 1200, 900);
    }

    /// The Emoji sheet, drawn through a **font book** — the one golden in this
    /// file that is not taken with the one-font renderer ([ADR-0386]).
    ///
    /// It has to be. The screen's whole subject is `font-family: OpenMoji`
    /// reaching §6.1's emoji slot, and a renderer that ignores the property would
    /// photograph 1845 tiles of `.notdef` and call it a picture of an emoji
    /// sheet. So this one opens a book, which is also what proves the face is on
    /// the module path: without `goldberry-emoji` the glyphs would fall back to
    /// Inter and this image would move.
    @Test
    @DisplayName("the Emoji screen, through a font book so the face is the one it names")
    void emoji() {
        paint("gallery-emoji", "emoji", Theme.NORD_DARK, 1200, 900, true);
    }

    /// The twelfth screen, 200 ms in on the virtual clock: the tile floor part way
    /// through its ripple, the swatches part way through a breath and the mark
    /// part way round ([ADR-0354], [ADR-0355]).
    ///
    /// Deterministic, which is the whole reason it can be a golden: every one of
    /// the three is a function of the frame time, and the offscreen renderer's
    /// time is not the wall's. Its floor starts on the first render rather than
    /// the first paint, or this picture would have no tiles in it.
    @Test
    @DisplayName("the Motion screen, 200 ms in")
    void motion() {
        paint("gallery-motion", "motion", Theme.NORD_DARK, 1200, 900);
    }

    /// The same sheet in a narrow window, which is the **only** thing that can
    /// show the reflow ([ADR-0309]).
    ///
    /// The column count is `floor((width + gap) / (tile + gap))` over a width no
    /// assertion can know, because it is what Yoga made of the viewport after the
    /// shell, the padding and the scrollbar had taken their share. What this
    /// picture proves is the two halves of that: **fewer columns**, and a last
    /// column that is a whole tile rather than a clipped one.
    ///
    /// It also exercises `Measured`'s one-frame settle. The first frame is drawn
    /// at the default seven; the width arrives; the second frame — which is the
    /// one `Offscreen` photographs — is right.
    @Test
    @DisplayName("the Icons screen in a narrow window, with fewer columns")
    void iconsNarrow() {
        paint("gallery-icons-narrow", "icons", Theme.NORD_DARK, 720, 900);
    }

    @Test
    @DisplayName("the Forms screen on the light theme")
    void formsLight() {
        paint("gallery-forms-light", "forms", Theme.NORD_LIGHT, 1200, 900);
    }

    @Test
    @DisplayName("the Basic screen on the light theme")
    void lightTheme() {
        paint("gallery-basic-light", "basic", Theme.NORD_LIGHT, 1200, 1720);
    }
}
