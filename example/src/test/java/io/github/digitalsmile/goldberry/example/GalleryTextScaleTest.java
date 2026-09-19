package io.github.digitalsmile.goldberry.example;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.AppMenu;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.golden.ScaleInvariance;
import io.github.digitalsmile.goldberry.golden.TextScaleAudit;
import io.github.digitalsmile.goldberry.html.view.HtmlStyles;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownStyles;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// §1.4's 150% over the whole gallery — the half of `TODO.md`'s typography entry
/// that was waiting on a decision rather than on a mechanism ([ADR-0435]).
///
/// [GalleryGoldenTest] photographs these eleven screens and cannot see this: it
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
/// [TextScaleAudit] is the rule; this file is the eleven screens it is pointed
/// at, plus the narrow window, plus the two screens an optional module draws.
///
/// ## Why there is no picture
///
/// A golden of eleven screens at 150% would pin every one of those per-label
/// decisions at once, in a form nobody reviews, before anybody had taken one —
/// which is exactly what `TODO.md` warned against. It would also cost eleven more
/// PNGs swept at every display scale [ADR-0434] adds, which is the third axis
/// that ADR argues against paying for twice.
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
    /// the cascade the application runs rather than a subset of it — the optional
    /// module's two included, because the Panels wall holds a `markdown-view` and
    /// the HTML screen is made entirely of the other half.
    private List<Stylesheet> sheets() {
        var all = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, model.density()));
        all.add(MarkdownStyles.stylesheet());
        all.add(HtmlStyles.stylesheet());
        all.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
        return all;
    }

    /// Lays `screen` out twice and asserts the larger text breaks nothing the
    /// ordinary text did not.
    ///
    /// @param accepted `container > child` for the overruns this screen already
    ///                 has at 150%, each one a defect recorded rather than fixed —
    ///                 see the note on [#basicNarrow()]
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

    @Test
    @DisplayName("the Basic screen survives 150%")
    void basic() {
        check("basic", 1200, 1720);
    }

    @Test
    @DisplayName("the Panels screen survives 150%")
    void panels() {
        check("panels", 1200, 900);
    }

    @Test
    @DisplayName("the Markdown screen survives 150%")
    void markdown() {
        check("markdown", 1200, 1000);
    }

    @Test
    @DisplayName("the HTML screen survives 150%")
    void html() {
        check("html", 1200, 1000);
    }

    @Test
    @DisplayName("the Overlays screen survives 150%")
    void overlays() {
        check("overlays", 1200, 900);
    }

    @Test
    @DisplayName("the Forms screen survives 150%")
    void forms() {
        check("forms", 1200, 1500);
    }

    /// One accepted overrun, and it is the clearest §1.4 failure in the showcase.
    ///
    /// The navigation wall is a masonry in a column with `overflow: visible` and
    /// no scroller around it, so at 100% it happens to end inside a 900-point
    /// window and at 150% it is 556 points taller than one. Nothing is cut — it is
    /// simply below the fold, and a window has no fold. The answer is a `scroll`
    /// around the wall or a wall that reflows, both of which are changes to
    /// `Screen` and `showcase.css` rather than to a check, so this records it.
    @Test
    @DisplayName("the Navigation screen survives 150%")
    void navigation() {
        check("navigation", 1200, 900, "`column#screen-navigation` > `masonry#navigation-wall`");
    }

    @Test
    @DisplayName("the Collections screen survives 150%")
    void collections() {
        check("collections", 1200, 1040);
    }

    @Test
    @DisplayName("the Charts screen survives 150%")
    void charts() {
        check("charts", 1200, 900);
    }

    @Test
    @DisplayName("the canvas screen survives 150%")
    void canvas() {
        check("canvas", 1200, 900);
    }

    @Test
    @DisplayName("the Icons screen survives 150%")
    void icons() {
        check("icons", 1200, 900);
    }

    @Test
    @DisplayName("the Motion screen survives 150%")
    void motion() {
        check("motion", 1200, 900);
    }

    @Test
    @DisplayName("the Emoji screen survives 150%")
    void emoji() {
        check("emoji", 1200, 900);
    }

    /// The narrow window, which is where a text scale is most likely to break
    /// something: a masonry's columns are a count rather than a media query, so
    /// the cards are half as wide and the labels in them are not.
    ///
    /// ## The five accepted overruns, which are five real defects
    ///
    /// **The five this ratchet was written for are gone, and nothing here fixed
    /// them.** They were five rows of buttons that did not fit 720 points once
    /// their labels were half again as wide — a `row` of unshrinkable children
    /// with nothing telling it what to do when they will not fit — and what
    /// removed them was `masonry`'s responsive column count
    /// ([ADR-0436](../../../../../../../book/src/adr/0436-a-column-count-is-a-width-the-window-does.md)).
    /// At `min-column-width: 560` the narrow Basic screen reflows to **one**
    /// column of 688 points where it used to pack two of 338, and the rows have
    /// room.
    ///
    /// They are deleted rather than kept as forgiven, which is the ratchet doing
    /// the job it exists for: an accepted failure that stops happening is
    /// reported just as loudly as a new one, because a list of exceptions nobody
    /// prunes is a list that stops meaning anything. The two that overrun at
    /// **100%** as well — `button#reset` in `row#actions` and
    /// `button#dialog-folder` in `row#dialog-actions` — were never on this list:
    /// the check is differential, so it forgives what is already wrong at 100%
    /// and catches only what 150% adds. Those two are still wrong at both scales
    /// and are the showcase's to fix.
    ///
    /// So the list is empty, and the next row that stops fitting fails here.
    @Test
    @DisplayName("the Basic screen survives 150% in a narrow window too")
    void basicNarrow() {
        check("basic", 720, 900);
    }
}
