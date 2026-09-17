package io.github.digitalsmile.goldberry.html.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;

/// The stylesheet, and the descriptor that decides whether anything can read it.
///
/// `MarkdownStylesTest`'s twin, and it exists for the same reason that one does:
/// `MarkdownStyles.stylesheet()` passed every class-path test in this module and then
/// threw on the showcase's **first frame**, because JPMS encapsulates resources as
/// well as classes and `exports` governs types rather than bytes (ADR-0093). The
/// second half of this file is therefore about the compiled `module-info.class`
/// rather than about the JVM these tests run in.
@DisplayName("the HTML stylesheet")
class HtmlStylesTest {

    /// The one module allowed to read the rules: whoever calls
    /// `Class.getResourceAsStream` is the module the package has to be open to, and
    /// that is `Stylesheet.resource` in `:core`.
    private static final String READER = "io.github.digitalsmile.goldberry.core";

    private static final String OWN_PACKAGE = "io.github.digitalsmile.goldberry.html.view";

    @Test
    @DisplayName("parses into the layer an application can override")
    void parses() {
        var stylesheet = HtmlStyles.stylesheet();

        assertEquals(CascadeLayer.TOOLKIT_BASE, stylesheet.layer(), "`controls.css`'s layer, for its reason");
        assertFalse(stylesheet.rules().isEmpty(), "the resource is there but held no rules");
    }

    @Test
    @DisplayName("styles the classes the widget actually builds")
    void stylesWhatIsBuilt() {
        var css = HtmlStyles.source();
        // Not every class -- that list belongs in `HtmlWidgets` -- but the ones whose
        // absence would be invisible: a page that rendered as a column of unstyled
        // words looks like a page somebody wrote badly.
        for (var name : List.of(
                ".html",
                ".html-prose",
                ".html-h1",
                ".html-strong",
                ".html-em",
                ".html-pre",
                ".html-table",
                ".html-quote-bar",
                "button.html-a")) {
            assertTrue(css.contains(name), () -> "html.css has no rule for " + name);
        }
    }

    @Test
    @DisplayName("is a stylesheet of its own rather than a copy of the Markdown one")
    void notACopy() {
        // The two documents must not look as though they were designed by different
        // people -- which is why the sizes and the gaps agree -- but they are two
        // sheets an application adds separately, and a `md-` selector in here would
        // mean one of them was restyling the other.
        assertFalse(HtmlStyles.source().contains(".md-"), "html.css should not name the Markdown view's classes");
    }

    @Test
    @DisplayName("is readable from :core on the module path, which no class-path test can show")
    void thePackageIsOpenToCore() {
        var opens = descriptor().opens().stream()
                .filter(o -> o.source().equals(OWN_PACKAGE))
                .toList();

        assertFalse(
                opens.isEmpty(),
                "this package holds html.css, and JPMS hides a resource in a package that is not open"
                        + " -- an application on the module path would fail at its first frame");
        assertTrue(
                opens.getFirst().targets().contains(READER),
                () -> OWN_PACKAGE + " is open to " + opens.getFirst().targets() + " and not to " + READER
                        + ", which is the module that reads the stylesheet");
    }

    @Test
    @DisplayName("and the view is exported, so an application can add the sheet at all")
    void thePackageIsExported() {
        assertTrue(
                descriptor().exports().stream().anyMatch(e -> e.source().equals(OWN_PACKAGE)),
                "HtmlStyles.stylesheet() is what an application calls beside Controls.stylesheets(theme)");
    }

    private static ModuleDescriptor descriptor() {
        var source = HtmlStylesTest.class.getProtectionDomain().getCodeSource();
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
}
