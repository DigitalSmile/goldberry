package io.github.digitalsmile.goldberry.widgets.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
/// ## There is no allowlist
///
/// There was one, briefly: `SelectState`, `ListState` and `TreeState` each
/// measured typeahead against `System.nanoTime()`, which made the typeahead
/// timeout the one behaviour in the catalog a test could not drive. They now ask
/// [io.github.digitalsmile.goldberry.Host#clock()], so a test advances a virtual
/// clock and asserts what happens after the window closes rather than sleeping
/// for it — see `TypeaheadClockTest`.
///
/// The rule is better for having no exceptions, and that is the point of writing
/// the cost of each one down while it exists: an exception with a price on it
/// gets paid.
class DeterminismTest {

    /// Where the assertions are exact, and therefore where a clock is a bug.
    private static final String[] DETERMINISTIC_LAYER = {
        "..goldberry.css..", "..goldberry.paint..", "..goldberry.text..",
        "..goldberry.widgets..",
    };

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
    @DisplayName("nothing in the deterministic layer reads a clock")
    void noClockUnderAnAssertion() {
        noClasses()
                .that().resideInAnyPackage(DETERMINISTIC_LAYER)
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
