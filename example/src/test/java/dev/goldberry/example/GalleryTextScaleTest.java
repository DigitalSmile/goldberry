package dev.goldberry.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ui.AppMenu;
import dev.goldberry.example.ui.Screen;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.golden.ScaleInvariance;
import dev.goldberry.golden.TextScaleAudit;
import dev.goldberry.html.view.HtmlStyles;
import dev.goldberry.icon.Icon;
import dev.goldberry.markdown.view.MarkdownStyles;
import dev.goldberry.media.view.MediaStyles;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;

/// Text at 150% over the whole gallery, checked as a rule and not as a picture.
///
/// Read more: [Tests and gates](https://goldberry.dev/docs/contributing/testing.html#goldens).
///
/// [GalleryGoldenTest] photographs the screens and cannot see this: it
/// builds its renderer with the single-font constructor, whose paint context is
/// `style -> font`, so `textScale` would be applied to a style that is thrown
/// away. These lay the same screens out through a **book**, at 100% and at 150%,
/// and assert a rule rather than taking a picture.
///
/// ## The rule, and why it is not "no text is clipped"
///
/// Since `text-overflow: ellipsis` shipped, some cutting is correct: a `nowrap`
/// label with a mark on it is *meant* to run out of room. So what is asserted is
/// the differential — **growing the text adds no cut nobody asked for, and pushes
/// no box off the edge.** A new `…` at 150% is the feature working; a label that
/// simply stops is not, and neither is a control laid out past the window.
/// [TextScaleAudit] is the rule; this file is every screen it is pointed at,
/// plus the narrow window.
///
/// ## Why there is no picture
///
/// A golden of every screen at 150% would pin every one of those per-label
/// decisions at once, in a form nobody reviews, before anybody had taken one —
/// which is exactly what `TODO.md` warned against. It would also cost a PNG per
/// screen swept at every display scale, which is a third axis not worth paying
/// for twice.
class GalleryTextScaleTest {

    /// Prints what every screen measured at both scales, passing or not.
    ///
    /// [ScaleInvariance]'s switch rather than one of its own, and not only to save
    /// a name: `example/build.gradle` forwards a fixed list of properties into the
    /// test JVM, a new one would need a line there, and what both of these print
    /// is the same thing — what a check measured, on the runs where it passed.
    private static final String REPORT_PROPERTY = "goldberry.golden.scales.report";

    private Showcase showcase;
    private ShowcaseModel model;
    private ShowcaseModel.Actions actions;
    private Icon palette;
    private Icon plus;
    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        showcase = new Showcase();
        model = modelOf(ShowcaseModel.class);
        actions = modelOf(ShowcaseModel.Actions.class);
        palette = Icon.bundled("palette", 16);
        plus = Icon.bundled("plus", 16);
        // A book, and the whole test depends on it being one: `textScale` is
        // applied where a `ComputedStyle` becomes a `Font`, and the one-font
        // renderer the goldens use never asks the style.
        fonts = Fonts.bundled();
    }

    @AfterEach
    void tearDown() {
        // Null-safe for `RendererRequirement`'s sake: `setUp` stops at the first
        // line on a machine with no native library, and a teardown that assumed
        // otherwise would report its own NPE instead of the skip.
        if (fonts != null) {
            fonts.close();
        }
        if (palette != null) {
            palette.close();
        }
        if (plus != null) {
            plus.close();
        }
    }

    private <T> T modelOf(Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    /// The application's own objects, built fresh per layout: a tree is mounted
    /// and unmounted inside an audit, so the 100% run and the 150% run cannot
    /// share one.
    private Widget screenOf(String screen) {
        actions.pickScreen(screen);
        var inflater = Widgets.inflater(
                model.named(),
                Icons.strict().bind("palette", palette).bind("plus", plus),
                showcase.models().toArray());
        return new Screen(
                model,
                actions,
                inflater,
                plus,
                () -> {},
                new AppMenu(
                        actions,
                        new AppMenu.Handlers(() -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}),
                        plus));
    }

    /// The sheets `Showcase.stylesheets()` adds, so a screen is audited against
    /// the cascade the application runs rather than a subset of it: the optional
    /// modules' sheets, `showcase.css` and every screen package's own sheet.
    private List<Stylesheet> sheets() {
        var all = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, model.density()));
        all.add(MarkdownStyles.stylesheet());
        all.add(HtmlStyles.stylesheet());
        all.add(MediaStyles.stylesheet());
        all.addAll(ShowcaseStyles.sheets());
        return all;
    }

    /// Lays `screen` out twice and asserts the larger text breaks nothing the
    /// ordinary text did not.
    ///
    /// @param accepted `container > child` for the overruns this screen already
    ///                 has at 150%, each one a defect recorded rather than fixed
    private void check(String screen, int width, int height, String... accepted) {
        var sheets = sheets();
        var normal = TextScaleAudit.of(width, height)
                .stylesheets(sheets)
                .fonts(fonts)
                .audit(screenOf(screen));
        var large = TextScaleAudit.of(width, height)
                .stylesheets(sheets)
                .fonts(fonts)
                .textScale(TextScaleAudit.LARGE)
                .audit(screenOf(screen));

        if (Boolean.getBoolean(REPORT_PROPERTY)) {
            System.out.println("text-scale " + screen + ": " + normal.paragraphs() + " paragraphs, silent "
                    + normal.silent().size() + " -> " + large.silent().size() + ", marked "
                    + normal.marked().size() + " -> " + large.marked().size() + ", overruns "
                    + normal.overruns().size() + " -> " + large.overruns().size() + ", spills "
                    + normal.spills().size() + " -> " + large.spills().size());
            large.overruns().forEach(overrun -> System.out.println("  overrun " + TextScaleAudit.key(overrun)));
        }
        TextScaleAudit.assertSurvivesLargeText("the " + screen + " screen", normal, large, List.of(accepted));
    }

    /// Overruns a screen already has at 150%, as `container > child`, each one a
    /// defect recorded rather than fixed. Empty: the next row that stops fitting
    /// fails here, and an entry that stops happening fails too.
    private static final Map<String, List<String>> ACCEPTED = Map.of();

    /// Every screen of the gallery, at the size the goldens are taken at.
    @TestFactory
    @DisplayName("every screen survives 150%")
    Stream<DynamicTest> everyScreen() {
        return Gallery.TABS.stream()
                .map(tab -> DynamicTest.dynamicTest(
                        tab.title(),
                        () -> check(
                                tab.name(),
                                1200,
                                900,
                                ACCEPTED.getOrDefault(tab.name(), List.of()).toArray(String[]::new))));
    }

    /// The narrow window, which is where a text scale is most likely to break
    /// something: the cards are as narrow as a column may get, and the labels in
    /// them are not narrower. The check is differential, so it forgives what is
    /// already wrong at 100% and catches only what 150% adds.
    @Test
    @DisplayName("the Buttons screen survives 150% in a narrow window too")
    void buttonsNarrow() {
        check("buttons", 720, 900);
    }
}
