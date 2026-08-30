package io.github.digitalsmile.goldberry.widgets.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The module graph, asserted rather than described.
///
/// `docs/ARCHITECTURE.md` §2 draws an arrow per module and §3.1 says a raw
/// `MemorySegment` never escapes `:natives`. Both were prose, and prose is not a
/// boundary: the module graph enforces *visibility* — `:widgets` cannot see a
/// package `:core` does not export — but it says nothing about a `:core` class
/// reaching into `render.backend.sdl3`, because `:core` exports that package to
/// everyone. This is the half `module-info` cannot state
/// (`docs/testing.md` §2).
///
/// ## Why this lives in `:widgets`
///
/// Because it is the module whose test classpath holds the whole graph. A rule
/// saying "`:natives` must not reach `:core`" can only be checked somewhere that
/// can see both, and `:natives` cannot see `:core` by construction — asserting it
/// from inside `:natives` would be asserting something the compiler already
/// refused to let you write.
///
/// `:gpu` is absent, deliberately, and so is any rule about it: `:widgets` has no
/// dependency on it (ADR-0014, so that a published widget library does not drag
/// SDL_GPU into every consumer), and inventing one here to satisfy a test would
/// be the test breaking the architecture it exists to protect.
class BoundaryTest {

    /// Every Goldberry class on the test classpath, tests excluded.
    ///
    /// `DoNotIncludeTests` matters more than it looks: a test *should* be able to
    /// hold a `MemorySegment`, print to stdout and read a clock, and a rule that
    /// forbade it would be a rule everybody switches off.
    ///
    /// Jars are **not** excluded, and that is the whole reason this works: Gradle
    /// puts the sibling modules on the test path as jars, so `DoNotIncludeJars`
    /// left the importer holding `:widgets` and nothing else — and three of these
    /// rules then passed by matching no classes at all, which ArchUnit is right to
    /// treat as a failure. The package filter is what keeps the import to
    /// Goldberry's own code.
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.github.digitalsmile.goldberry");
    }

    // --- the boundary ARCHITECTURE §3.1 draws --------------------------------

    @Test
    @DisplayName("a raw MemorySegment never escapes :natives")
    void memorySegmentStaysInNatives() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..goldberry.natives..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.lang.foreign.MemorySegment")
                .because("§3.1: the FFM boundary is the point of :natives existing."
                        + " Pixels cross as a ByteBuffer, which is what"
                        + " MemorySegment.asByteBuffer() produces without copying")
                .check(classes);
    }

    @Test
    @DisplayName("nothing outside :natives opens an Arena")
    void arenasStayInNatives() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..goldberry.natives..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("java.lang.foreign..")
                .because("an arena outside :natives is native memory with no owner:"
                        + " §3.1 puts every lifetime behind a wrapper that closes it")
                .check(classes);
    }

    // --- the arrows ARCHITECTURE §2 draws ------------------------------------

    @Test
    @DisplayName(":natives depends on nothing of Goldberry's but :common")
    void nativesIsTheBottom() {
        noClasses()
                .that()
                .resideInAPackage("..goldberry.natives..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..goldberry.css..",
                        "..goldberry.widget..",
                        "..goldberry.widgets..",
                        "..goldberry.paint..",
                        "..goldberry.render..",
                        "..goldberry.input..")
                .because("§2: :natives is bindings and a superbuild. It requires"
                        + " :common and slf4j and nothing else, which is what lets"
                        + " :core and a binding generator both use it")
                .check(classes);
    }

    @Test
    @DisplayName(":common is the floor and knows about nobody")
    void commonIsTheFloor() {
        noClasses()
                .that()
                .resideInAPackage("..goldberry.log..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..goldberry.natives..",
                        "..goldberry.css..",
                        "..goldberry.widget..",
                        "..goldberry.widgets..",
                        "..goldberry.paint..",
                        "..goldberry.render..")
                .because("ADR-0174: :common is what both halves need and neither owns."
                        + " It requires nothing of Goldberry's, which is the only"
                        + " reason :natives and :core can both use it")
                .check(classes);
    }

    @Test
    @DisplayName(":core never reaches up into the widget catalog")
    void coreDoesNotKnowItsWidgets() {
        noClasses()
                .that()
                .resideInAPackage("..goldberry.css..")
                .or()
                .resideInAPackage("..goldberry.paint..")
                .or()
                .resideInAPackage("..goldberry.text..")
                .or()
                .resideInAPackage("..goldberry.render..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..goldberry.widgets..")
                .because("§2: the catalog is built on :core and :core knows nothing"
                        + " of it — which is what makes a second catalog possible")
                .check(classes);
    }

    // --- the seam testing.md §2 names ----------------------------------------

    @Test
    @DisplayName("a widget never imports a backend")
    void widgetsDoNotKnowTheBackend() {
        noClasses()
                .that()
                .resideInAPackage("..goldberry.widgets..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..goldberry.render.backend..")
                .because("testing.md §2: a widget is a value. Reaching sdl3 or headless"
                        + " from one would make the catalog untestable without a"
                        + " backend and unusable on a second")
                .check(classes);
    }

    @Test
    @DisplayName("a widget never opens a window")
    void widgetsDoNotOpenWindows() {
        noClasses()
                .that()
                .resideInAPackage("..goldberry.widgets..")
                .should()
                .dependOnClassesThat()
                .haveSimpleName("Window")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleName("Launcher")
                .because("ADR-0121: starting a tour, opening a menu and floating a HUD"
                        + " all need a Host, and a widget has none. That seam is why"
                        + " Menus.open and Tours.start take one")
                .check(classes);
    }
}
