package io.github.digitalsmile.goldberry.render.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a page is opened with — ADR-0441.
///
/// The value half, which is the half a test can reach: opening one needs WebKit
/// and a desktop, and what is decided *here* is what gets asked for.
@DisplayName("a web view spec")
class WebViewSpecTest {

    @Test
    @DisplayName("starts at a URL, or from a document, or blank")
    void theThreeWaysToStart() {
        var url = WebViewSpec.of("https://example.org");
        var html = WebViewSpec.ofHtml("<p>hello</p>");
        var blank = WebViewSpec.blank();

        assertEquals("https://example.org", url.url());
        assertNull(url.html());

        assertEquals("<p>hello</p>", html.html());
        assertNull(html.url());

        // Blank is legitimate rather than a degenerate case: an application that
        // navigates a moment later has nothing to put here.
        assertNull(blank.url());
        assertNull(blank.html());
    }

    @Test
    @DisplayName("refuses to start two ways at once")
    void aUrlAndADocumentAreAlternatives() {
        // Not a style rule. The two would race, and which one the user ended up
        // looking at would be a fact about the engine rather than about the
        // application.
        var both = assertThrows(
                IllegalArgumentException.class,
                () -> new WebViewSpec("https://example.org", "<p>hi</p>", "", 800, 600, WebSize.INITIAL, false));

        assertTrue(both.getMessage().contains("not both"), both.getMessage());
    }

    @Test
    @DisplayName("refuses a window with no area")
    void aWindowHasBothSides() {
        assertThrows(
                IllegalArgumentException.class, () -> new WebViewSpec(null, null, "", 0, 600, WebSize.INITIAL, false));
        assertThrows(
                IllegalArgumentException.class, () -> new WebViewSpec(null, null, "", 800, -1, WebSize.INITIAL, false));
    }

    @Test
    @DisplayName("opens at a browser's size when nothing says otherwise")
    void theDefaultSize() {
        var spec = WebViewSpec.of("https://example.org");

        // A desktop window's size rather than a number of the toolkit's own: what
        // opens is a window on the desktop and should look like the others on it.
        assertEquals(WebViewSpec.DEFAULT_WIDTH, spec.width());
        assertEquals(WebViewSpec.DEFAULT_HEIGHT, spec.height());
        assertEquals(WebSize.INITIAL, spec.size());
        assertFalse(spec.debug());
        assertEquals("", spec.title());
    }

    @Test
    @DisplayName("every wither keeps everything it was not asked to change")
    void withersAreNarrow() {
        var spec = WebViewSpec.of("https://example.org")
                .title("Docs")
                .sized(1280, 800, WebSize.MINIMUM)
                .debug(true);

        assertEquals("https://example.org", spec.url());
        assertEquals("Docs", spec.title());
        assertEquals(1280, spec.width());
        assertEquals(800, spec.height());
        assertEquals(WebSize.MINIMUM, spec.size());
        assertTrue(spec.debug());
    }

    @Test
    @DisplayName("every size mode has a native word, and the compiler is what keeps that true")
    void everySizeTranslates() {
        // The exhaustive switch in WebViewEngine is what fails to compile when a
        // constant is added to one enum and not the other; this is what fails when
        // one is added and mapped to the wrong thing.
        for (var size : WebSize.values()) {
            var hint = WebViewEngine.translate(size);
            assertEquals(size.ordinal(), hint.value(), size + " does not line up with " + hint);
        }
    }
}
