package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.lint.Finding;
import dev.goldberry.css.lint.StyleLint;
import dev.goldberry.html.view.HtmlStyles;
import dev.goldberry.markdown.view.MarkdownStyles;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;

/// Every declaration the toolkit's own stylesheets write is one the engine
/// applies — the property exists, **and** the value parses.
///
/// ## Why this exists
///
/// The CSS subset is deliberately small and **an unsupported declaration is not
/// an error**: a stylesheet naming `backdrop-filter` before it is implemented
/// should not stop a window opening. So the engine drops it and carries on,
/// which is the
/// right behaviour for an *application's* stylesheet and the wrong signal for the
/// toolkit's own — `border-bottom` shipped in `table-head`, drew nothing, and the
/// only trace was one debug line among thousands.
///
/// It began as a check on the property name alone, and half a year of
/// stylesheets later two rules were found doing nothing for the *other* reason:
/// `group-box-title` wrote `border-radius: 7px 7px 0 0` and `select text-input`
/// wrote `background: none`, both properties the engine implements and neither a
/// value it took. A lint reads values too.
///
/// ## What changed, and what did not
///
/// The **machinery moved into `:core`** and is
/// [StyleLint](dev.goldberry.css.lint.StyleLint) now, because
/// an application writing its own stylesheet needed the same answer and had no
/// way to ask for it: a diagnostic is asked for, not logged. What is left here
/// is the *policy*: the toolkit's
/// sheets and the showcase's are held to zero findings, and an application's are
/// its own business.
///
/// That took a hundred and sixty lines of log capture with it. This test used to
/// install a logback appender on `ComputedStyle`, set it to `DEBUG`, resolve
/// every rule, and read the sentences back out — so it depended on the wording
/// of two log lines, and its own guard tests existed because a change to either
/// would have made it pass by seeing nothing at all. Findings are values now, so
/// the guards are `StyleLintTest`'s and are assertions about behaviour rather
/// than about strings.
///
/// It stays in `:example` rather than moving to `:widgets` because the gallery
/// is the visual regression corpus, which is where a declaration that draws
/// nothing is a screen photographed wrong — and because the showcase's own sheet
/// is here.
///
/// It is `TokenClosureTest`'s argument applied to the other half of a
/// declaration. That one checks the `var()`s resolve; this one checks the engine
/// then does something with what they resolved to.
class SupportedPropertyTest {

    /// Everything the engine would apply nothing from.
    ///
    /// [Finding.Kind#UNTYPED_RULE] is deliberately not here: that is
    /// `RuleBucketTest`'s subject, it is a cost rather than a defect, and the
    /// toolkit ships eight of them on purpose with a reason written beside each.
    private static List<String> dead(List<Stylesheet> inForce, List<Stylesheet> linted) {
        return new StyleLint(inForce)
                .check(linted).stream()
                        .filter(finding -> finding.kind() == Finding.Kind.DEAD_DECLARATION)
                        .map(Finding::toString)
                        .sorted()
                        .toList();
    }

    @Test
    @DisplayName("no rule the catalog ships is one the engine drops, by name or by value")
    void theCatalogWritesOnlyDeclarationsTheEngineApplies() {
        var sheets = List.copyOf(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));

        var dead = dead(sheets, sheets);

        assertTrue(
                dead.isEmpty(),
                () -> "the toolkit's stylesheets write " + dead.size()
                        + " declaration(s) the engine drops on the floor, so the rule"
                        + " does nothing and nothing reads the line that says so: " + dead);
    }

    @Test
    @DisplayName("and the light theme's rules are checked too, since a theme is half of a value")
    void theLightThemeIsCheckedAsWell() {
        // The dark theme is what the sweep above runs under, and a value is only
        // half a declaration: a `var()` that resolves to something legal in one
        // theme and to nothing in the other is a rule that draws on one and not
        // the other, which no golden of the dark theme could show.
        var sheets = List.copyOf(Controls.stylesheets(Theme.NORD_LIGHT, Density.REGULAR));

        var dead = dead(sheets, sheets);

        assertTrue(dead.isEmpty(), () -> "on the light theme: " + dead);
    }

    @Test
    @DisplayName("and the compact density's, which swaps the tokens the metrics are written in")
    void theCompactDensityIsCheckedAsWell() {
        var sheets = List.copyOf(Controls.stylesheets(Theme.NORD_DARK, Density.COMPACT));

        var dead = dead(sheets, sheets);

        assertTrue(dead.isEmpty(), () -> "at compact density: " + dead);
    }

    @Test
    @DisplayName("and the showcase's own stylesheet does not either")
    void theShowcaseWritesOnlySupportedProperties() {
        // The gallery is the visual regression corpus, so a dead
        // declaration in it is a screen that has been photographed wrong.
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css");
        // Under the toolkit's sheets, which is where the showcase runs: its own
        // rules read the theme's custom properties like everybody else's.
        var inForce = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        inForce.add(sheet);

        var dead = dead(List.copyOf(inForce), List.of(sheet));

        assertTrue(dead.isEmpty(), () -> "the showcase writes " + dead);
    }

    @Test
    @DisplayName("nor do the two content modules' stylesheets, which this application also loads")
    void theContentStylesheetsWriteOnlySupportedProperties() {
        // `markdown.css` and `html.css` were outside this sweep until selection
        // geometry added rules to both — and the sweep is exactly what says whether a rule in them
        // does anything. A dropped declaration in a document's stylesheet is invisible
        // in a way a control's is not: nobody has a second document to compare it
        // against.
        var markdown = MarkdownStyles.stylesheet();
        var html = HtmlStyles.stylesheet();
        var inForce = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        inForce.add(markdown);
        inForce.add(html);

        var dead = dead(List.copyOf(inForce), List.of(markdown, html));

        assertTrue(dead.isEmpty(), () -> "the content stylesheets write " + dead);
    }
}
