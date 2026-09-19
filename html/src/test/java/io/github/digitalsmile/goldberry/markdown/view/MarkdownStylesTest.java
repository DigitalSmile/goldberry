package io.github.digitalsmile.goldberry.markdown.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;

/// The stylesheet, and the descriptor that decides whether anything can read it.
///
/// ## Why the second half is a test at all
///
/// `MarkdownStyles.stylesheet()` passed every test in this module and then threw on
/// the showcase's **first frame**, because these tests run on the class path and the
/// showcase runs on the module path. JPMS encapsulates resources as well as classes:
/// a `.css` beside a class in a named module is invisible to the module that reads
/// it unless the package is **open**, and `exports` governs types rather than bytes
/// (ADR-0093). On the class path there are no modules and no encapsulation, so the
/// call works and says nothing.
///
/// So the check is on the **descriptor**, read from the compiled `module-info.class`
/// the way `ExportedSurfaceTest` reads `:natives`'. That is a fact about the shipped
/// artifact rather than about the JVM this test happens to be running in.
@DisplayName("the Markdown stylesheet")
class MarkdownStylesTest {

    /// The one module allowed to read the rules: whoever calls
    /// `Class.getResourceAsStream` is the module the package has to be open to, and
    /// that is `Stylesheet.resource` in `:core`.
    private static final String READER = "io.github.digitalsmile.goldberry.core";

    private static final String OWN_PACKAGE = "io.github.digitalsmile.goldberry.markdown.view";

    @Test
    @DisplayName("parses into the layer an application can override")
    void parses() {
        var stylesheet = MarkdownStyles.stylesheet();
        assertEquals(CascadeLayer.TOOLKIT_BASE, stylesheet.layer(), "`controls.css`'s layer, for its reason");
        assertFalse(stylesheet.rules().isEmpty(), "the resource is there but held no rules");
    }

    @Test
    @DisplayName("styles the classes the widget actually builds")
    void stylesWhatIsBuilt() {
        var css = MarkdownStyles.source();
        // Not every class -- that list belongs in `MarkdownWidgets` -- but the ones
        // whose absence would be invisible: a document that rendered as a column of
        // unstyled words looks like a document somebody wrote badly.
        //
        // `.md-line` and `.md-lines` rather than `.md-prose`, which carries no
        // declarations since ADR-0426: a paragraph's geometry moved to the class that
        // means a line of words, and `.md-prose` is the hook the paragraph's box keeps.
        for (var name : new String[] {
            ".markdown", ".md-line", ".md-lines", ".md-h1", ".md-strong", ".md-code-block", ".md-table", "task-mark"
        }) {
            assertTrue(css.contains(name), () -> "markdown.css has no rule for " + name);
        }
    }

    @Test
    @DisplayName("is readable from :core on the module path, which no class-path test can show")
    void thePackageIsOpenToCore() {
        var descriptor = descriptor();
        var opens = descriptor.opens().stream()
                .filter(o -> o.source().equals(OWN_PACKAGE))
                .toList();

        assertFalse(
                opens.isEmpty(),
                "this package holds markdown.css, and JPMS hides a resource in a package that is "
                        + "not open -- an application on the module path would fail at its first frame");
        assertTrue(
                opens.getFirst().targets().contains(READER),
                () -> OWN_PACKAGE + " is open to " + opens.getFirst().targets() + " and not to " + READER
                        + ", which is the module that reads the stylesheet");
    }

    @Test
    @DisplayName("opens nothing but the two packages that keep a stylesheet")
    void nothingElseIsOpen() {
        // The HTML half brought `html.css` and a second `opens` with it (ADR-0298), and
        // that is the whole of the list: opening a package hands its private types out
        // as well, so a third entry here should be something somebody argued for.
        var allowed = java.util.Set.of(OWN_PACKAGE, "io.github.digitalsmile.goldberry.html.view");
        var open = descriptor().opens().stream()
                .map(ModuleDescriptor.Opens::source)
                .filter(name -> !allowed.contains(name))
                .toList();
        assertTrue(open.isEmpty(), "opening a package hands its private types out as well: " + open);
    }

    private static ModuleDescriptor descriptor() {
        var source = MarkdownStylesTest.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            fail("no code source for the test classes; cannot locate the module's own descriptor");
        }
        // `.../build/classes/java/test` -> `.../build/classes/java/main`. Through
        // the URI: `getPath()` keeps the leading slash of `file:/D:/...`, which
        // `Path.of` refuses on Windows (ADR-0338).
        Path main;
        try {
            main = Path.of(source.getLocation().toURI()).resolveSibling("main");
        } catch (URISyntaxException e) {
            throw new AssertionError("the test classes' code source is not a file URI: " + source.getLocation(), e);
        }
        var moduleInfo = main.resolve("module-info.class");
        assertTrue(Files.isRegularFile(moduleInfo), "expected the compiled descriptor at " + moduleInfo);
        try (var in = Files.newInputStream(moduleInfo)) {
            return ModuleDescriptor.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message);
    }
}
