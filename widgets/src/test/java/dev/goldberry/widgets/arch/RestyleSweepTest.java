package dev.goldberry.widgets.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.widget.style.Styled;

/// **Every way through `Styled.restyle` is on a list, with its reason.**
///
/// `restyle` is the widget's last word on its own style, applied after the
/// cascade, so what it writes is unthemeable and unoverridable. The
/// rule that keeps it honest is one sentence — *only what a stylesheet could not
/// have written* — and a sentence is not a check. It had one caller when it was
/// written and nine by the time anyone read them against it again, and one of the
/// nine was writing an `opacity` a stylesheet could have written perfectly well
/// once it was told which lines to hide.
///
/// This does not decide whether a reason is good; nothing mechanical can. What it
/// makes impossible is an override **nobody decided on**: a tenth fails here, with
/// the rule in the message, and the only way to make it pass is to write down why
/// a stylesheet could not have written the number — which is the question the rule
/// asks.
///
/// ## Why the classpath and not the source tree
///
/// Because `:core` implements `Styled` too, and the sweep should see every module
/// `:widgets` can see — `BoundaryTest`'s import, for `BoundaryTest`'s reason. The
/// modules downstream of this one (`:html`, `:media`, `:example`) are out of its
/// reach; none of them overrides `restyle`, and the message of
/// [#everyOverrideIsDecided] says what to do if one does.
///
/// Read more:
/// [Writing a widget: styled](https://goldberry.dev/docs/guide/writing-a-widget.html#styled-a-name-for-the-cascade).
class RestyleSweepTest {

    /// What makes a number one a stylesheet could not have written — the kinds
    /// `Styled.restyle`'s note names, and the only reasons an entry may give.
    enum Because {

        /// Derived from **how many** of something there are, which no selector can
        /// count: a fifth of five segments, the end cell of a row.
        COUNT,

        /// The **application's own value** — a colour it chose for a tab or asked a
        /// swatch to show — which no selector can know, any more than a label.
        DATA,

        /// Something only **layout** can say: a length measured against another
        /// box, a rectangle something was painted in, the height of a screen.
        MEASUREMENT,

        /// Where the **pointer or the wheel** has taken the widget, which changes
        /// between two frames of the same description.
        INPUT,

        /// **Arithmetic** over the cascade's own values — a padding plus a gutter —
        /// which a stylesheet cannot write because the toolkit's CSS defers `calc()`.
        ARITHMETIC
    }

    /// One way through: which type, what kind of number, and why.
    ///
    /// @param type    the binary name below `dev.goldberry.`
    /// @param because the kinds of number it writes, at least one
    /// @param why     what it writes and why no stylesheet could have
    record Allowed(String type, Set<Because> because, String why) {

        Allowed {
            if (because.isEmpty() || why.isBlank()) {
                throw new IllegalArgumentException(type + " is on the list without a reason; the reason is the entry");
            }
            because = Set.copyOf(because);
        }
    }

    /// The overrides that were read against the rule and kept.
    private static final List<Allowed> ALLOWED = List.of(
            new Allowed(
                    "widgets.form.colorpicker.ColorSwatch",
                    Set.of(Because.DATA),
                    "background: the colour the swatch shows, which is the application's value;"
                            + " here rather than painted so the closed control fades between colours"),
            new Allowed(
                    "widgets.controls.segmented.SegmentedDivider",
                    Set.of(Because.COUNT),
                    "inset: the line's place, boundary / count of the track. Whether it shows is a class"
                            + " and the stylesheet's"),
            new Allowed(
                    "widgets.controls.segmented.SegmentedIndicator",
                    Set.of(Because.COUNT),
                    "width 1/count, a translation of index cells, and which corners of the stylesheet's"
                            + " radius stay round, all from which cell of how many"),
            new Allowed(
                    "widgets.panel.tabs.Tab",
                    Set.of(Because.DATA, Because.INPUT),
                    "color: the tab's own colour, application data; transform: where a drag has taken it"),
            new Allowed(
                    "widgets.panel.tabs.TabIndicator",
                    Set.of(Because.DATA, Because.MEASUREMENT),
                    "background: the tab's own colour; transform: the displacement from the header it"
                            + " left, a difference between two painted rectangles"),
            new Allowed(
                    "widgets.core.scroll.ScrollContent",
                    Set.of(Because.INPUT, Because.ARITHMETIC),
                    "transform: the scroll offset; padding: the author's padding plus the always-shown"
                            + " bar's gutter. Its flex-shrink pin lives in render, not here"),
            new Allowed(
                    "widgets.core.scroll.ScrollThumb",
                    Set.of(Because.MEASUREMENT),
                    "length and travel: what proportion of the content is on screen, from the viewport's"
                            + " and the content's measured extents"),
            new Allowed(
                    "widgets.core.scroll.ScrollViewport",
                    Set.of(Because.MEASUREMENT),
                    "height: the caller's, which is a menu capped at the screen it opens on"),
            new Allowed(
                    "widgets.core.affix.AffixContent",
                    Set.of(Because.MEASUREMENT),
                    "transform: how far the pinned box is from its hole, from the rectangles it and its"
                            + " scroll container were painted in"));

    private static final String ROOT = "dev.goldberry.";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("dev.goldberry");
    }

    /// Every concrete `Styled` that declares `restyle` itself, by its name below
    /// [#ROOT].
    private static Set<String> overrides() {
        var found = new TreeSet<String>();
        for (var type : classes) {
            if (type.isInterface() || !type.isAssignableTo(Styled.class)) {
                continue;
            }
            for (var method : type.getMethods()) {
                if (isRestyle(method)) {
                    found.add(type.getName().substring(ROOT.length()));
                }
            }
        }
        return found;
    }

    private static boolean isRestyle(JavaMethod method) {
        var parameters = method.getRawParameterTypes();
        return method.getName().equals("restyle")
                && parameters.size() == 1
                && parameters.getFirst().isEquivalentTo(ComputedStyle.class)
                && !method.getModifiers().contains(JavaModifier.BRIDGE)
                && !method.getModifiers().contains(JavaModifier.SYNTHETIC);
    }

    private static Set<String> listed() {
        var names = new TreeSet<String>();
        for (var allowed : ALLOWED) {
            names.add(allowed.type());
        }
        return names;
    }

    @Test
    @DisplayName("the sweep finds the overrides at all")
    void theSweepSeesThem() {
        // A sweep that matched nothing would pass the rule below vacuously, which
        // is BoundaryTest's lesson about importing jars.
        assertTrue(
                overrides().contains("widgets.controls.segmented.SegmentedIndicator"),
                () -> "the sweep found no restyle in SegmentedIndicator, which has always had one;"
                        + " it is looking at the wrong classes: " + overrides());
    }

    @Test
    @DisplayName("every restyle override is on the list, with a reason")
    void everyOverrideIsDecided() {
        var unlisted = new TreeSet<>(overrides());
        unlisted.removeAll(listed());

        assertTrue(
                unlisted.isEmpty(),
                () -> "these override Styled.restyle and are not on RestyleSweepTest's list: " + unlisted
                        + ". What restyle writes runs after the cascade, so no theme can change it and no"
                        + " rule can override it; the rule is \"only what a stylesheet could not have"
                        + " written\". If a stylesheet could have written it once"
                        + " told a fact, give the widget a class and write the rule in the sheet; if it"
                        + " is a pin against the stylesheet, put it in render after .style(style)."
                        + " Otherwise add an entry here saying which kind of number it is and why, and"
                        + " record the decision.");
    }

    @Test
    @DisplayName("nothing on the list has stopped overriding restyle")
    void theListIsNotStale() {
        var stale = listed();
        stale.removeAll(overrides());

        assertTrue(
                stale.isEmpty(),
                () -> "these are on RestyleSweepTest's list and no longer override Styled.restyle: " + stale
                        + ". Take them off, so the list stays the set of ways through and not a history"
                        + " of them.");
    }
}
