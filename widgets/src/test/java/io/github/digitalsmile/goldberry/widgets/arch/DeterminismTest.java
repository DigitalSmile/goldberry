package io.github.digitalsmile.goldberry.widgets.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `docs/testing.md` §0.1, enforced: **determinism is a feature under test**.
///
/// The suite asserts exact results — images compared pixel by pixel, a virtual
/// clock stepped in whole milliseconds, every label in the root locale — and each
/// of those assertions is only as good as the code's refusal to read something a
/// test cannot control. One `System.nanoTime()` under a painter turns a golden
/// into a coin toss that lands heads for months.
///
/// ## What these rules cover, and what they deliberately do not
///
/// The **deterministic layer** only: `css`, `paint`, `text` and the widget
/// catalog. Those are the packages whose output is asserted exactly, and the ones
/// where a clock read is always a bug.
///
/// Everything above them reads the real clock because that is its job, and a rule
/// spanning the whole toolkit would have thirty-eight exceptions and mean nothing.
/// `Launcher` and `EventLoop` run the frame loop; `Window` and `WidgetRenderer`
/// time their own phases, which is what feeds the HUD; `Clock` is the real-clock
/// implementation a virtual one is swapped in *for*. None of that is a violation
/// of §0.1 — it is what §0.1 is built on.
///
/// ## Why there is an allowlist rather than a green build
///
/// Because three of these are real, none is a one-line fix, and a rule switched
/// off is a rule that never comes back on. It is the shape `ContrastTest` already
/// uses for the same reason: each exception is named, the cost of fixing it is
/// written down, and the list is short enough that adding to it is an argument
/// rather than a habit.
class DeterminismTest {

    /// Where the assertions are exact, and therefore where a clock is a bug.
    private static final String[] DETERMINISTIC_LAYER = {
        "..goldberry.css..", "..goldberry.paint..", "..goldberry.text..",
        "..goldberry.widgets..",
    };

    /// The clock reads that are known, deliberate, and not yet fixed.
    ///
    /// All three are `typeahead`: a select, a list and a tree each decide whether
    /// the keystroke that just arrived continues the previous search or starts a
    /// new one, and they measure that gap against a real clock. None is on a paint
    /// path and no golden depends on one — but it does mean the typeahead timeout
    /// is the one behaviour in the catalog a test cannot drive, which §0.1 calls a
    /// bug rather than a limitation.
    ///
    /// Fixing it is threading the frame clock into three `State` subclasses that
    /// already hold a `host`. That is a change worth making on its own rather than
    /// as a side-effect of adding this rule.
    private static final List<String> TYPEAHEAD = List.of(
            "io.github.digitalsmile.goldberry.widgets.controls.select.SelectState",
            "io.github.digitalsmile.goldberry.widgets.panel.list.ListState",
            "io.github.digitalsmile.goldberry.widgets.panel.tree.TreeState");

    /// The one door the machine's time zone comes through.
    ///
    /// `TimeAxis.times(list)` reads it, and that is correct: an axis of instants
    /// has to be drawn in *some* zone and only the application knows which. What
    /// matters is that there is exactly one such call in the catalog, so the
    /// Charts screen passing `ZoneOffset.UTC` is enough to make its picture the
    /// same picture in every time zone (ADR-0203).
    private static final String TIME_AXIS =
            "io.github.digitalsmile.goldberry.widgets.data.TimeAxis";

    /// `src/testFixtures` is not `src/test`, so ArchUnit's test filter does not
    /// reach it — and a fixture that prints a diff to the console is doing its
    /// job.
    private static final String FIXTURES = "..goldberry.golden..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.github.digitalsmile.goldberry");
    }

    @Test
    @DisplayName("nothing in the deterministic layer reads a clock, bar three typeaheads")
    void noClockUnderAnAssertion() {
        noClasses()
                .that().resideInAnyPackage(DETERMINISTIC_LAYER)
                .and().haveNameNotMatching(String.join("|", TYPEAHEAD))
                .should().callMethod(System.class, "currentTimeMillis")
                .orShould().callMethod(System.class, "nanoTime")
                .because("§0.1: a virtual clock is what lets a motion test assert a"
                        + " mid-transition frame. A painter that read the real one would"
                        + " draw whatever the machine happened to be doing")
                .check(classes);
    }

    @Test
    @DisplayName("the machine's time zone enters through exactly one door")
    void oneTimeZoneSeam() {
        noClasses()
                .that().resideInAnyPackage(DETERMINISTIC_LAYER)
                .and().haveNameNotMatching(TIME_AXIS)
                .should().callMethod(java.time.ZoneId.class, "systemDefault")
                .because("ADR-0203: a time axis is time, and which zone it is drawn in is"
                        + " the application's answer. One seam is what makes passing UTC"
                        + " enough to pin a chart's picture")
                .check(classes);
    }

    @Test
    @DisplayName("nothing in the deterministic layer invents a random number")
    void noRandomness() {
        noClasses()
                .that().resideInAnyPackage(DETERMINISTIC_LAYER)
                .or().resideInAPackage("..goldberry.motion..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Random")
                .orShould().callMethod(Math.class, "random")
                .because("§0.1: seeded randomness or none. An unseeded source anywhere"
                        + " under a golden makes the image a different image each run")
                .check(classes);
    }

    @Test
    @DisplayName("nothing in the deterministic layer reads the machine's locale")
    void noDefaultLocale() {
        noClasses()
                .that().resideInAnyPackage(DETERMINISTIC_LAYER)
                .should().callMethod(java.util.Locale.class, "getDefault")
                .because("§1.3: no locale dependence. Every label this toolkit formats is"
                        + " in the root locale, so a golden taken in Istanbul matches one"
                        + " taken in Reykjavik — the dotted capital I is not hypothetical")
                .check(classes);
    }

    @Test
    @DisplayName("the toolkit logs through the facade and never through stdout")
    void noPrinting() {
        noClasses()
                .that().resideOutsideOfPackage("..goldberry.assets..")
                .and().resideOutsideOfPackage(FIXTURES)
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .orShould().callMethod(Throwable.class, "printStackTrace")
                .because("ADR-0023: a library that writes to stdout has taken a decision"
                        + " belonging to the application. :assets is a build-time tool"
                        + " whose console output IS its product")
                .check(classes);
    }
}
